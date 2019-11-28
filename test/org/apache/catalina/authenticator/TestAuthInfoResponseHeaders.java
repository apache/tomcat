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

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.startup.TesterMapRealm;
import org.apache.catalina.startup.TesterServlet;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.catalina.valves.RemoteIpValve;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.codec.binary.Base64;
import org.apache.tomcat.util.descriptor.web.LoginConfig;
import org.apache.tomcat.util.descriptor.web.SecurityCollection;
import org.apache.tomcat.util.descriptor.web.SecurityConstraint;

public class TestAuthInfoResponseHeaders extends TomcatBaseTest {

    private static String USER = "user";
    private static String PWD = "pwd";
    private static String ROLE = "role";
    private static String URI = "/protected";
    private static String CONTEXT_PATH = "/foo";
    private static String CLIENT_AUTH_HEADER = "authorization";

    /*
     * Encapsulate the logic to generate an HTTP header
     * for BASIC Authentication.
     * Note: only used internally, so no need to validate arguments.
     */
    private static final class BasicCredentials {

        private final String method;
        private final String username;
        private final String password;
        private final String credentials;

        private BasicCredentials(String aMethod,
                String aUsername, String aPassword) {
            method = aMethod;
            username = aUsername;
            password = aPassword;
            String userCredentials = username + ":" + password;
            byte[] credentialsBytes =
                    userCredentials.getBytes(StandardCharsets.ISO_8859_1);
            String base64auth = Base64.encodeBase64String(credentialsBytes);
            credentials= method + " " + base64auth;
        }

        private String getCredentials() {
            return credentials;
        }
    }

    @Test
    public void testNoHeaders() throws Exception {
        doTest(USER, PWD, CONTEXT_PATH + URI, false);
    }

    @Test
    public void testWithHeaders() throws Exception {
        doTest(USER, PWD, CONTEXT_PATH + URI, true);
    }

    public void doTest(String user, String pwd, String uri, boolean expectResponseAuthHeaders)
            throws Exception {

        if (expectResponseAuthHeaders) {
            BasicAuthenticator auth =
                (BasicAuthenticator) getTomcatInstance().getHost().findChild(
                        CONTEXT_PATH).getPipeline().getFirst();
            auth.setSendAuthInfoResponseHeaders(true);
        }
        getTomcatInstance().start();

        Map<String,List<String>> reqHeaders = new HashMap<>();

        List<String> auth = new ArrayList<>();
        auth.add(new BasicCredentials("Basic", user, pwd).getCredentials());
        reqHeaders.put(CLIENT_AUTH_HEADER, auth);

        List<String> forwardedFor = new ArrayList<>();
        forwardedFor.add("192.168.0.10");
        List<String> forwardedHost = new ArrayList<>();
        forwardedHost.add("localhost");
        reqHeaders.put("X-Forwarded-For", forwardedFor);
        reqHeaders.put("X-Forwarded-Host", forwardedHost);

        Map<String,List<String>> respHeaders = new HashMap<>();

        ByteChunk bc = new ByteChunk();
        int rc = getUrl("http://localhost:" + getPort() + uri, bc, reqHeaders,
                respHeaders);
        Assert.assertEquals(200, rc);
        Assert.assertEquals("OK", bc.toString());

        if (expectResponseAuthHeaders) {
            List<String> remoteUsers = respHeaders.get("remote-user");
            Assert.assertNotNull(remoteUsers);
            Assert.assertEquals(USER, remoteUsers.get(0));
            List<String> authTypes = respHeaders.get("auth-type");
            Assert.assertNotNull(authTypes);
            Assert.assertEquals(HttpServletRequest.BASIC_AUTH, authTypes.get(0));
        } else {
            Assert.assertFalse(respHeaders.containsKey("remote-user"));
            Assert.assertFalse(respHeaders.containsKey("auth-type"));
        }

        bc.recycle();
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();

        // Configure a context with digest auth and a single protected resource
        Tomcat tomcat = getTomcatInstance();
        tomcat.getHost().getPipeline().addValve(new RemoteIpValve());

        // No file system docBase required
        Context ctxt = tomcat.addContext(CONTEXT_PATH, null);

        // Add protected servlet
        Tomcat.addServlet(ctxt, "TesterServlet", new TesterServlet());
        ctxt.addServletMappingDecoded(URI, "TesterServlet");
        SecurityCollection collection = new SecurityCollection();
        collection.addPatternDecoded(URI);
        SecurityConstraint sc = new SecurityConstraint();
        sc.addAuthRole(ROLE);
        sc.addCollection(collection);
        ctxt.addConstraint(sc);

        // Configure the Realm
        TesterMapRealm realm = new TesterMapRealm();
        realm.addUser(USER, PWD);
        realm.addUserRole(USER, ROLE);
        ctxt.setRealm(realm);

        // Configure the authenticator
        LoginConfig lc = new LoginConfig();
        lc.setAuthMethod(HttpServletRequest.BASIC_AUTH);
        ctxt.setLoginConfig(lc);
        ctxt.getPipeline().addValve(new BasicAuthenticator());
    }
}
