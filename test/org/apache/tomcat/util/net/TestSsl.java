/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tomcat.util.net;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.net.ssl.HandshakeCompletedEvent;
import javax.net.ssl.HandshakeCompletedListener;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.Context;
import org.apache.catalina.authenticator.SSLAuthenticator;
import org.apache.catalina.deploy.LoginConfig;
import org.apache.catalina.deploy.SecurityCollection;
import org.apache.catalina.deploy.SecurityConstraint;
import org.apache.catalina.startup.TestTomcat.MapRealm;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.buf.ByteChunk;

/**
 * Requires test.keystore (checked in), generated with:
 *  keytool -genkey -alias tomcat -keyalg RSA
 *  pass: changeit 
 *  CN: localhost ( for hostname validation )
 */
public class TestSsl extends TomcatBaseTest {

    public void testSimpleSsl() throws Exception {
        configureClientSsl();
        
        Tomcat tomcat = getTomcatInstance();

        File appDir = new File(getBuildDirectory(), "webapps/examples");
        tomcat.addWebapp(null, "/examples", appDir.getAbsolutePath());
        
        TesterSupport.initSsl(tomcat);

        tomcat.start();
        ByteChunk res = getUrl("https://localhost:" + getPort() +
            "/examples/servlets/servlet/HelloWorldExample");
        assertTrue(res.toString().indexOf("<h1>Hello World!</h1>") > 0);
    }

    boolean handshakeDone = false;
    
    public void testRenegotiateFail() throws Exception {
        
        // If RFC5746 is supported, renegotiation will always will (and will
        // always be secure)
        if (TesterSupport.RFC_5746_SUPPORTED) {
            return;
        }

        Tomcat tomcat = getTomcatInstance();

        File appDir = new File(getBuildDirectory(), "webapps/examples");
        // app dir is relative to server home
        tomcat.addWebapp(null, "/examples", appDir.getAbsolutePath());

        TesterSupport.initSsl(tomcat);

        // Default - MITM attack prevented
        
        tomcat.start();
        SSLContext sslCtx = SSLContext.getInstance("TLS");
        sslCtx.init(null, TesterSupport.getTrustManagers(),
                new java.security.SecureRandom());
        SSLSocketFactory socketFactory = sslCtx.getSocketFactory();
        SSLSocket socket = (SSLSocket) socketFactory.createSocket("localhost", getPort());

        socket.addHandshakeCompletedListener(new HandshakeCompletedListener() {
            @Override
            public void handshakeCompleted(HandshakeCompletedEvent event) {
                handshakeDone = true;
            }
        });
        
        OutputStream os = socket.getOutputStream();
        os.write("GET /examples/servlets/servlet/HelloWorldExample HTTP/1.0\n".getBytes());
        os.flush();

        
        InputStream is = socket.getInputStream();

        // Make sure the NIO connector has read the request before the handshake
        Thread.sleep(100);
        socket.startHandshake();
        handshakeDone = false;
        byte[] b = new byte[0];
        int maxTries = 5;  // 5 sec should be enough - in NIO we'll timeout
        socket.setSoTimeout(1000);
        for (int i = 0; i < maxTries; i++) {
            try {
                is.read(b);
            } catch (IOException e) {
                // timeout
            }
            if (handshakeDone) {
                break;
            }
        }
        os = socket.getOutputStream();
        if (!handshakeDone) {
            // success - we timedout without handshake
            return;
        }
        try {
            os.write("Host: localhost\n\n".getBytes());
        } catch (IOException ex) {
            // success - connection closed
            return;
        }
        
        fail("Re-negotiation worked");
        
    }
    
    public void testRenegotiateWorks() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        File appDir = new File(getBuildDirectory(), "webapps/examples");
        // app dir is relative to server home
        tomcat.addWebapp(null, "/examples", appDir.getAbsolutePath());

        TesterSupport.initSsl(tomcat);
        
        // Enable MITM attack
        tomcat.getConnector().setAttribute("allowUnsafeLegacyRenegotiation", "true");

        tomcat.start();

        String protocol = tomcat.getConnector().getProtocolHandlerClassName();
        if (protocol.indexOf("Nio") != -1) {
            return; // Not supported yet (2010-07-22)
        }
        if (protocol.indexOf("Apr") != -1) {
            return; // Disabled by default in 1.1.20 windows binary (2010-07-27)
        }

        SSLContext sslCtx = SSLContext.getInstance("TLS");
        sslCtx.init(null, TesterSupport.getTrustManagers(),
                new java.security.SecureRandom());
        SSLSocketFactory socketFactory = sslCtx.getSocketFactory();
        SSLSocket socket = (SSLSocket) socketFactory.createSocket("localhost", getPort());

        socket.addHandshakeCompletedListener(new HandshakeCompletedListener() {
            @Override
            public void handshakeCompleted(HandshakeCompletedEvent event) {
                handshakeDone = true;
            }
        });
        
        OutputStream os = socket.getOutputStream();
        os.write("GET /examples/servlets/servlet/HelloWorldExample HTTP/1.0\n".getBytes());
        os.flush();

        InputStream is = socket.getInputStream();

        socket.startHandshake();
        handshakeDone = false;
        byte[] b = new byte[0];
        int maxTries = 5; 
        socket.setSoTimeout(1000);
        for (int i = 0; i < maxTries; i++) {
            try {
                is.read(b);
            } catch (IOException e) {
                // timeout
            }
            if (handshakeDone) {
                break;
            }
        }
        os = socket.getOutputStream();
        
        try {
            os.write("Host: localhost\n\n".getBytes());
        } catch (IOException ex) {
            fail("Re-negotiation failed");
        }
        
    }

    public void testClientCert() throws Exception {
        
        Tomcat tomcat = getTomcatInstance();

        String protocol = tomcat.getConnector().getProtocolHandlerClassName();
        if (protocol.indexOf("Nio") != -1) {
            return; // Not supported yet (2011-03-01)
        }
        if (protocol.indexOf("Apr") != -1) {
            return; // Disabled by default in 1.1.20 windows binary (2010-07-27)
        }

        TesterSupport.initSsl(tomcat);
        
        // Need a web application with a protected and unprotected URL
        // Must have a real docBase - just use temp
        Context ctx =
            tomcat.addContext("", System.getProperty("java.io.tmpdir"));

        Tomcat.addServlet(ctx, "simple", new SimpleServlet());
        ctx.addServletMapping("/unprotected", "simple");
        ctx.addServletMapping("/protected", "simple");

        // Security constraints
        SecurityCollection collection = new SecurityCollection();
        collection.addPattern("/protected");
        SecurityConstraint sc = new SecurityConstraint();
        sc.addAuthRole("testrole");
        sc.addCollection(collection);
        ctx.addConstraint(sc);

        // Configure the Realm
        MapRealm realm = new MapRealm();
        realm.addUser("CN=user1, C=US", "not used");
        realm.addUserRole("CN=user1, C=US", "testrole");
        ctx.setRealm(realm);
        
        // Configure the authenticator
        LoginConfig lc = new LoginConfig();
        lc.setAuthMethod("CLIENT-CERT");
        ctx.setLoginConfig(lc);
        ctx.getPipeline().addValve(new SSLAuthenticator());
        
        // Start Tomcat
        tomcat.start();
        
        configureClientSsl();
        
        // Get the unprotected resource
        ByteChunk res =
                getUrl("https://localhost:" + getPort() + "/unprotected");
        assertEquals("OK", res.toString());
        
        // Get the protected resource
        res = getUrl("https://localhost:" + getPort() + "/protected");
        assertEquals("OK", res.toString());
    }

    @Override
    public void setUp() throws Exception {
        if (!TesterSupport.RFC_5746_SUPPORTED) {
            // Make sure SSL renegotiation is not disabled in the JVM
            System.setProperty("sun.security.ssl.allowUnsafeRenegotiation", "true");
        }
        super.setUp();
    }

    private void configureClientSsl() {
        try {
            SSLContext sc = SSLContext.getInstance("SSL");
            sc.init(TesterSupport.getUser1KeyManagers(),
                    TesterSupport.getTrustManagers(),
                    new java.security.SecureRandom());     
            javax.net.ssl.HttpsURLConnection.setDefaultSSLSocketFactory(
                    sc.getSocketFactory());
        } catch (Exception e) {
            e.printStackTrace();
        } 
    }

    public static class SimpleServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;
        
        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {
            resp.setContentType("text/plain");
            resp.getWriter().print("OK");
        }
    }
}
