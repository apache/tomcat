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
package org.apache.catalina.authenticator;

import java.io.File;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.startup.SimpleHttpClient;
import org.apache.catalina.startup.TestTomcat.MapRealm;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTestJUnit4;

public class TestFormAuthenticator extends TomcatBaseTestJUnit4 {

    @Test
    public void testGet() throws Exception {
        doTest("GET", "GET", false);
    }

    @Test
    public void testPostNoContinue() throws Exception {
        doTest("POST", "GET", false);
    }

    @Test
    public void testPostWithContinue() throws Exception {
        doTest("POST", "GET", true);
    }

    // Bug 49779
    @Test
    public void testPostNoContinuePostRedirect() throws Exception {
        doTest("POST", "POST", false);
    }

    // Bug 49779
    @Test
    public void testPostWithContinuePostRedirect() throws Exception {
        doTest("POST", "POST", true);
    }


    private void doTest(String resourceMethod, String redirectMethod,
            boolean useContinue) throws Exception {
        FormAuthClient client = new FormAuthClient();

        // First request for authenticated resource 
        client.setUseContinue(useContinue);
        client.doResourceRequest(resourceMethod);
        assertTrue(client.isResponse200());
        assertTrue(client.isResponseBodyOK());
        client.reset();
        
        // Second request for the login page
        client.setUseContinue(useContinue);
        client.doLoginRequest();
        assertTrue(client.isResponse302());
        assertTrue(client.isResponseBodyOK());
        client.reset();

        // Third request - follow the redirect
        client.doResourceRequest(redirectMethod);
        if ("POST".equals(redirectMethod)) {
            client.setUseContinue(useContinue);
        }
        assertTrue(client.isResponse200());
        assertTrue(client.isResponseBodyOK());
        client.reset();

        // Subsequent requests - direct to the resource
        for (int i = 0; i < 5; i++) {
            client.setUseContinue(useContinue);
            client.doResourceRequest(resourceMethod);
            assertTrue(client.isResponse200());
            assertTrue(client.isResponseBodyOK());
            client.reset();
        }
    }


    private final class FormAuthClient extends SimpleHttpClient {

        private static final String LOGIN_PAGE = "j_security_check";

        private String protectedPage = "index.jsp";
        private String protectedLocation = "/examples/jsp/security/protected/";
        private int requestCount = 0;
        private String sessionId = null;

        private FormAuthClient() throws Exception {
            Tomcat tomcat = getTomcatInstance();
            File appDir = new File(getBuildDirectory(), "webapps/examples");
            Context ctx =
                tomcat.addWebapp(null, "/examples", appDir.getAbsolutePath());
            
            MapRealm realm = new MapRealm();
            realm.addUser("tomcat", "tomcat");
            realm.addUserRole("tomcat", "tomcat");
            ctx.setRealm(realm);

            setPort(getPort());

            tomcat.start();
        }
        
        private void doResourceRequest(String method) throws Exception {
            String request[] = new String[2];
            request [0] = method + " " + protectedLocation + protectedPage + 
                    " HTTP/1.1" + CRLF;
            request[0] = request[0] + 
                    "Host: localhost" + CRLF +
                    "Connection: close" + CRLF;
            if (getUseContinue()) {
                request[0] = request[0] + 
                        "Expect: 100-continue" + CRLF;
            }
            if (sessionId != null) {
                request[0] = request[0] +
                        "Cookie: JSESSIONID=" + sessionId + CRLF;
            }
            if ("POST".equals(method)) {
                request[0] = request[0] +
                        "Content-Type: application/x-www-form-urlencoded" + CRLF +
                        "Content-length: 7" + CRLF +
                        CRLF;
                request[1] = "foo=bar";
            } else {
                request[1] = CRLF;
            }
            doRequest(request);
        }

        private void doLoginRequest() throws Exception {
            String request[] = new String[2];
            request [0] = "POST " + protectedLocation + LOGIN_PAGE + 
                    " HTTP/1.1" + CRLF;
            request[0] = request[0] + 
                    "Host: localhost" + CRLF +
                    "Connection: close" + CRLF;
            if (getUseContinue()) {
                request[0] = request[0] + 
                        "Expect: 100-continue" + CRLF;
            }
            if (sessionId != null) {
                request[0] = request[0] +
                        "Cookie: JSESSIONID=" + sessionId + CRLF;
            }
            request[0] = request[0] +
                    "Content-Type: application/x-www-form-urlencoded" + CRLF +
                    "Content-length: 35" + CRLF +
                    CRLF;
            request[1] = "j_username=tomcat&j_password=tomcat";
            
            doRequest(request);
        }

        private void doRequest(String request[]) throws Exception {
            setRequest(request);
            
            connect();
            processRequest();
            String newSessionId = getSessionId();
            if (newSessionId != null) {
                sessionId = newSessionId;
            }
            disconnect();
            
            requestCount++;
        }

        @Override
        public boolean isResponseBodyOK() {
            String expected;
            
            if (requestCount == 1) {
                // First request should result in the login page
                expected = "<title>Login Page for Examples</title>";
            } else if (requestCount == 2) {
                // Second request should result in a redirect
                return true;
            } else {
                // Subsequent requests should result in the protected page 
                expected = "<title>Protected Page for Examples</title>";
            }
            return getResponseBody().contains(expected);
        }
        
    }
}
