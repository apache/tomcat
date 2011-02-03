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
import java.security.cert.X509Certificate;

import javax.net.ssl.HandshakeCompletedEvent;
import javax.net.ssl.HandshakeCompletedListener;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

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
    static TrustManager[] trustAllCerts = new TrustManager[] { 
        new X509TrustManager() { 
            @Override
            public X509Certificate[] getAcceptedIssuers() { 
                return null;
            }
            @Override
            public void checkClientTrusted(X509Certificate[] certs,
                    String authType) {
                // NOOP - Trust everything
            }
            @Override
            public void checkServerTrusted(X509Certificate[] certs,
                    String authType) {
                // NOOP - Trust everything
            }
        }
    };

    private void initSsl(Tomcat tomcat) {
        String protocol = tomcat.getConnector().getProtocolHandlerClassName();
        if (protocol.indexOf("Apr") == -1) {
            tomcat.getConnector().setProperty("sslProtocol", "tls");
            File keystoreFile = new File(
                    "test/org/apache/catalina/startup/test.keystore");
            tomcat.getConnector().setAttribute("keystoreFile",
                    keystoreFile.getAbsolutePath());
        } else {
            File keystoreFile = new File(
                    "test/org/apache/catalina/startup/test-cert.pem");
            tomcat.getConnector().setAttribute("SSLCertificateFile",
                    keystoreFile.getAbsolutePath());
            keystoreFile = new File(
                    "test/org/apache/catalina/startup/test-key.pem");
            tomcat.getConnector().setAttribute("SSLCertificateKeyFile",
                    keystoreFile.getAbsolutePath());
        }
        tomcat.getConnector().setSecure(true);            
        tomcat.getConnector().setProperty("SSLEnabled", "true");

    }
    
    public void testSimpleSsl() throws Exception {
        // Install the all-trusting trust manager so https:// works 
        // with unsigned certs. 

        try {
            SSLContext sc = SSLContext.getInstance("SSL");
            sc.init(null, trustAllCerts, new java.security.SecureRandom());
            javax.net.ssl.HttpsURLConnection.setDefaultSSLSocketFactory(
                    sc.getSocketFactory());
        } catch (Exception e) {
            e.printStackTrace();
        } 
        
        Tomcat tomcat = getTomcatInstance();

        File appDir = new File(getBuildDirectory(), "webapps/examples");
        tomcat.addWebapp(null, "/examples", appDir.getAbsolutePath());
        
        initSsl(tomcat);

        tomcat.start();
        ByteChunk res = getUrl("https://localhost:" + getPort() +
            "/examples/servlets/servlet/HelloWorldExample");
        assertTrue(res.toString().indexOf("<h1>Hello World!</h1>") > 0);
    }

    boolean handshakeDone = false;
    
    public void testRenegotiateFail() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        File appDir = new File(getBuildDirectory(), "webapps/examples");
        // app dir is relative to server home
        tomcat.addWebapp(null, "/examples", appDir.getAbsolutePath());

        initSsl(tomcat);

        // Default - MITM attack prevented
        
        tomcat.start();
        SSLContext sslCtx = SSLContext.getInstance("TLS");
        sslCtx.init(null, trustAllCerts, new java.security.SecureRandom());
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

        initSsl(tomcat);
        
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
        sslCtx.init(null, trustAllCerts, new java.security.SecureRandom());
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

    @Override
    public void setUp() throws Exception {
        // Make sure SSL renegotiation is not disabled in the JVM
        System.setProperty("sun.security.ssl.allowUnsafeRenegotiation", "true");
        super.setUp();
    }
}
