/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.apache.catalina.connector;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Enumeration;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.Context;
import org.apache.catalina.authenticator.BasicAuthenticator;
import org.apache.catalina.deploy.LoginConfig;
import org.apache.catalina.startup.SimpleHttpClient;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TestTomcat.MapRealm;
import org.apache.tomcat.util.buf.ByteChunk;

/**
 * Test case for {@link Request}. 
 */
public class TestRequest extends TomcatBaseTest {

    /**
     * Test case for https://issues.apache.org/bugzilla/show_bug.cgi?id=37794
     * POST parameters are not returned from a call to 
     * any of the {@link HttpServletRequest} getParameterXXX() methods if the
     * request is chunked.
     */
    public void testBug37794() {
        Bug37794Client client = new Bug37794Client();
        client.setPort(getPort());

        // Edge cases around zero
        client.doRequest(-1, false); // Unlimited
        assertTrue(client.isResponse200());
        assertTrue(client.isResponseBodyOK());
        client.reset();
        client.doRequest(0, false); // Unlimited
        assertTrue(client.isResponse200());
        assertTrue(client.isResponseBodyOK());
        client.reset();
        client.doRequest(1, false); // 1 byte - too small should fail
        assertTrue(client.isResponse500());
        
        client.reset();
        
        // Edge cases around actual content length
        client.reset();
        client.doRequest(6, false); // Too small should fail
        assertTrue(client.isResponse500());
        client.reset();
        client.doRequest(7, false); // Just enough should pass
        assertTrue(client.isResponse200());
        assertTrue(client.isResponseBodyOK());
        client.reset();
        client.doRequest(8, false); // 1 extra - should pass
        assertTrue(client.isResponse200());
        assertTrue(client.isResponseBodyOK());
        
        // Much larger
        client.reset();
        client.doRequest(8096, false); // Plenty of space - should pass
        assertTrue(client.isResponse200());
        assertTrue(client.isResponseBodyOK());

        // Check for case insensitivity
        client.reset();
        client.doRequest(8096, true); // Plenty of space - should pass
        assertTrue(client.isResponse200());
        assertTrue(client.isResponseBodyOK());
    }
    
    private static class Bug37794Servlet extends HttpServlet {
        
        private static final long serialVersionUID = 1L;

        /**
         * Only interested in the parameters and values for POST requests.
         */
        @Override
        protected void doPost(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {
            // Just echo the parameters and values back as plain text
            resp.setContentType("text/plain");
            
            PrintWriter out = resp.getWriter();
            
            // Assume one value per attribute
            Enumeration<String> names = req.getParameterNames();
            while (names.hasMoreElements()) {
                String name = names.nextElement();
                out.println(name + "=" + req.getParameter(name));
            }
        }
    }
    
    /**
     * Bug 37794 test client.
     */
    private class Bug37794Client extends SimpleHttpClient {
        
        private boolean init;
        
        private synchronized void init() throws Exception {
            if (init) return;
            
            Tomcat tomcat = getTomcatInstance();
            Context root = tomcat.addContext("", TEMP_DIR);
            Tomcat.addServlet(root, "Bug37794", new Bug37794Servlet());
            root.addServletMapping("/test", "Bug37794");
            tomcat.start();
            
            init = true;
        }
        
        private Exception doRequest(int postLimit, boolean ucChunkedHead) {
            Tomcat tomcat = getTomcatInstance();
            
            try {
                init();
                tomcat.getConnector().setMaxPostSize(postLimit);
                
                // Open connection
                connect();
                
                // Send request in two parts
                String[] request = new String[2];
                if (ucChunkedHead) {
                    request[0] =
                        "POST http://localhost:8080/test HTTP/1.1" + CRLF +
                        "content-type: application/x-www-form-urlencoded" + CRLF +
                        "Transfer-Encoding: CHUNKED" + CRLF +
                        "Connection: close" + CRLF +
                        CRLF +
                        "3" + CRLF +
                        "a=1" + CRLF;
                } else {
                    request[0] =
                        "POST http://localhost:8080/test HTTP/1.1" + CRLF +
                        "content-type: application/x-www-form-urlencoded" + CRLF +
                        "Transfer-Encoding: chunked" + CRLF +
                        "Connection: close" + CRLF +
                        CRLF +
                        "3" + CRLF +
                        "a=1" + CRLF;
                }
                request[1] =
                    "4" + CRLF +
                    "&b=2" + CRLF +
                    "0" + CRLF +
                    CRLF;
                
                setRequest(request);
                processRequest(); // blocks until response has been read
                
                // Close the connection
                disconnect();
            } catch (Exception e) {
                return e;
            }
            return null;
        }

        @Override
        public boolean isResponseBodyOK() {
            if (getResponseBody() == null) {
                return false;
            }
            if (!getResponseBody().contains("a=1")) {
                return false;
            }
            if (!getResponseBody().contains("b=2")) {
                return false;
            }
            return true;
        }
        
    }

    /**
     * Test case for {@link Request#login(String, String)} and
     * {@link Request#logout()}.
     */
    public void testLoginLogout() throws Exception{
        // Setup Tomcat instance
        Tomcat tomcat = getTomcatInstance();
        
        // Must have a real docBase - just use temp
        Context ctx = 
            tomcat.addContext("/", System.getProperty("java.io.tmpdir"));

        LoginConfig config = new LoginConfig();
        config.setAuthMethod("BASIC");
        ctx.setLoginConfig(config);
        ctx.getPipeline().addValve(new BasicAuthenticator());
        
        Tomcat.addServlet(ctx, "servlet", new LoginLogoutServlet());
        ctx.addServletMapping("/", "servlet");
        
        MapRealm realm = new MapRealm();
        realm.addUser(LoginLogoutServlet.USER, LoginLogoutServlet.PWD);
        ctx.setRealm(realm);
        
        tomcat.start();
        
        ByteChunk res = getUrl("http://localhost:" + getPort() + "/");

        assertEquals(LoginLogoutServlet.OK, res.toString());
    }
    
    private static final class LoginLogoutServlet extends HttpServlet {
        private static final long serialVersionUID = 1L;
        private static final String USER = "user";
        private static final String PWD = "pwd";
        private static final String OK = "OK";
        
        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {
            
            req.login(USER, PWD);
            
            if (!req.getRemoteUser().equals(USER))
                throw new ServletException();
            if (!req.getUserPrincipal().getName().equals(USER))
                throw new ServletException();
            
            req.logout();
            
            if (req.getRemoteUser() != null)
                throw new ServletException();
            if (req.getUserPrincipal() != null)
                throw new ServletException();
            
            resp.getWriter().write(OK);
        }
        
    }
    
    public void testBug49424NoChunking() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        Context root = tomcat.addContext("",
                System.getProperty("java.io.tmpdir"));
        Tomcat.addServlet(root, "Bug37794", new Bug37794Servlet());
        root.addServletMapping("/", "Bug37794");
        tomcat.start();

        HttpURLConnection conn = getConnection();
        InputStream is = conn.getInputStream();
        assertNotNull(is);
    }

    public void testBug49424WithChunking() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        Context root = tomcat.addContext("",
                System.getProperty("java.io.tmpdir"));
        Tomcat.addServlet(root, "Bug37794", new Bug37794Servlet());
        root.addServletMapping("/", "Bug37794");
        tomcat.start();
        
        HttpURLConnection conn = getConnection();
        conn.setChunkedStreamingMode(8 * 1024);
        InputStream is = conn.getInputStream();
        assertNotNull(is);
    }

    private HttpURLConnection getConnection() throws IOException {
        final String query = "http://localhost:" + getPort() + "/";
        URL postURL;
        postURL = new URL(query);
        HttpURLConnection conn = (HttpURLConnection) postURL.openConnection();
        conn.setRequestMethod("POST");

        conn.setDoInput(true);
        conn.setDoOutput(true);
        conn.setUseCaches(false);
        conn.setAllowUserInteraction(false);

        return conn;
    }
}
