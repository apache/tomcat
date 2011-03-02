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

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

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
 * The keys and certificates used in this file are all available in svn and were
 * generated using a test CA the files for which are in the Tomcat PMC private
 * repository since not all of them are AL2 licensed.
 */
public class TestClientCert extends TomcatBaseTest {
    public static final byte DATA = (byte)33;
    
    public void testClientCertGet() throws Exception {
        if (!TesterSupport.isRenegotiationSupported(getTomcatInstance())) {
            return;
        }

        // Unprotected resource
        ByteChunk res =
                getUrl("https://localhost:" + getPort() + "/unprotected");
        assertEquals("OK", res.toString());
        
        // Protected resource
        res = getUrl("https://localhost:" + getPort() + "/protected");
        assertEquals("OK", res.toString());
    }

    public void testClientCertPostSmaller() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        int bodySize = tomcat.getConnector().getMaxSavePostSize() / 2; 
        doTestClientCertPost(bodySize, false);
    }

    public void testClientCertPostSame() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        int bodySize = tomcat.getConnector().getMaxSavePostSize(); 
        doTestClientCertPost(bodySize, false);
    }

    public void testClientCertPostLarger() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        int bodySize = tomcat.getConnector().getMaxSavePostSize() * 2; 
        doTestClientCertPost(bodySize, true);
    }

    public void doTestClientCertPost(int bodySize, boolean expectProtectedFail)
            throws Exception {
        if (!TesterSupport.isRenegotiationSupported(getTomcatInstance())) {
            return;
        }

        byte[] body = new byte[bodySize];
        Arrays.fill(body, DATA);

        // Unprotected resource
        ByteChunk res = postUrl(body,
                "https://localhost:" + getPort() + "/unprotected");
        assertEquals("OK-" + bodySize, res.toString());
        
        // Protected resource
        res.recycle();
        int rc = postUrl(body, "https://localhost:" + getPort() + "/protected",
                res, null);
        if (expectProtectedFail) {
            assertEquals(401, rc);
        } else {
            assertEquals("OK-" + bodySize, res.toString());
        }
    }

    @Override
    public void setUp() throws Exception {
        if (!TesterSupport.RFC_5746_SUPPORTED) {
            // Make sure SSL renegotiation is not disabled in the JVM
            System.setProperty("sun.security.ssl.allowUnsafeRenegotiation", "true");
        }

        super.setUp();

        Tomcat tomcat = getTomcatInstance();

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
        
        TesterSupport.configureClientSsl();
    }

    public static class SimpleServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;
        
        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {
            resp.setContentType("text/plain");
            resp.getWriter().print("OK");
        }
        
        @Override
        protected void doPost(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {
            // Swallow any request body
            int read = 0;
            int len = 0;
            byte[] buffer = new byte[4096];
            InputStream is = req.getInputStream();
            boolean contentOK = true;
            while (len > -1) {
                len = is.read(buffer);
                read = read + len;
                for (int i=0; i<len && contentOK; i++) {
                    contentOK = (buffer[i] == DATA);
                }
            }
            // len will have been -1 on last iteration
            read++;
            
            // Report the number of bytes read
            resp.setContentType("text/plain");
            if (contentOK) 
                resp.getWriter().print("OK-" + read);
            else
                resp.getWriter().print("CONTENT-MISMATCH-" + read);
        }
    }
}
