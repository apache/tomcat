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

import org.apache.catalina.Context;
import org.apache.catalina.startup.SimpleHttpClient;
import org.apache.catalina.startup.TestTomcat.MapRealm;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;

public class TestFormAuthenticator extends TomcatBaseTest {

    public void testExpect100Continue() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        File appDir = new File(getBuildDirectory(), "webapps/examples");
        Context ctx =
            tomcat.addWebapp(null, "/examples", appDir.getAbsolutePath());
        
        MapRealm realm = new MapRealm();
        realm.addUser("tomcat", "tomcat");
        realm.addUserRole("tomcat", "tomcat");
        ctx.setRealm(realm);

        tomcat.start();

        Expect100ContinueClient client = new Expect100ContinueClient();
        client.setPort(getPort());
        
        // First request for authenticated resource 
        Exception e = client.doRequest(null);
        assertNull(e);
        assertTrue(client.isResponse200());
        assertTrue(client.isResponseBodyOK());
        
        String sessionID = client.getSessionId();
        
        // Second request for the login page
        client.reset();
        e = client.doRequest(sessionID);
        assertNull(e);
        assertTrue(client.isResponse302());
        assertTrue(client.isResponseBodyOK());

        // Third request - follow the redirect
        client.reset();
        e = client.doRequest(sessionID);
        assertNull(e);
        assertTrue(client.isResponse200());
        assertTrue(client.isResponseBodyOK());

        // Session ID changes after successful authentication
        sessionID = client.getSessionId();

        // Subsequent requests - direct to the resource
        for (int i = 0; i < 5; i++) {
            client.reset();
            e = client.doRequest(sessionID);
            assertNull(e);
            assertTrue(client.isResponse200());
            assertTrue(client.isResponseBodyOK());
        }
    }
    
    private final class Expect100ContinueClient extends SimpleHttpClient {

        private int requestCount = 0;

        private Exception doRequest(String sessionId) {
            try {
                String request[] = new String[2];
                if (requestCount == 1) {
                    request[0] =
                            "POST /examples/jsp/security/protected/j_security_check HTTP/1.1" + CRLF;
                } else if (requestCount == 2) {
                    request[0] =
                            "GET /examples/jsp/security/protected/index.jsp HTTP/1.1" + CRLF;
                } else {
                    request[0] =
                            "POST /examples/jsp/security/protected/index.jsp HTTP/1.1" + CRLF;
                }

                request[0] = request[0] + 
                        "Host: localhost" + CRLF +
                        "Expect: 100-continue" + CRLF +
                        "Connection: close" + CRLF;
                
                if (sessionId != null) {
                    request[0] = request[0] +
                            "Cookie: JSESSIONID=" + sessionId + CRLF;
                }
                
                if (requestCount == 1) {
                    request[0] = request[0] +
                            "Content-Type: application/x-www-form-urlencoded" + CRLF +
                            "Content-length: 35" + CRLF +
                            CRLF;
                    request[1] = "j_username=tomcat&j_password=tomcat";
                } else if (requestCount ==2) {
                    request[1] = CRLF;
                } else {
                    request[0] = request[0] +
                            "Content-Type: application/x-www-form-urlencoded" + CRLF +
                            "Content-length: 7" + CRLF +
                            CRLF;
                    request[1] = "foo=bar";
                }

                setRequest(request);
                if (requestCount != 2) {
                    setUseContinue(true);
                }
                
                connect();
                processRequest();
                disconnect();
                
                requestCount++;
            } catch (Exception e) {
                e.printStackTrace();
                return e;
            }
            return null;
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
