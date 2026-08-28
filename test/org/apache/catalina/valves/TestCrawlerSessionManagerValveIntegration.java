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
package org.apache.catalina.valves;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.authenticator.BasicAuthenticator;
import org.apache.catalina.startup.TesterMapRealm;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.descriptor.web.LoginConfig;
import org.apache.tomcat.util.descriptor.web.SecurityCollection;
import org.apache.tomcat.util.descriptor.web.SecurityConstraint;

public class TestCrawlerSessionManagerValveIntegration extends TomcatBaseTest {

    private static final String USER = "alice";
    private static final String PASSWORD = "alice-password";
    private static final String ROLE = "account-holder";
    private static final String SECRET = "alice-private-token";

    /**
     * Test for handling of Valve mis-configuration that results in one of the clients identified as a crawler creating
     * an authenticated session. The Valve tries (but does not guarantee) to protect against this.
     * <p>
     * Alice authenticates with a crawler-classified User-Agent. A separate HTTP request with no Authorization or Cookie
     * header uses a different crawler-classified User-Agent from the same apparent address.
     * <p>
     * The separate request should not see Alice's authenticated session.
     *
     * @throws Exception If the test experiences an unexpected error
     */
    @Test
    public void testUnauthenticatedCrawlerMustNotReuseAuthenticatedSession() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        Context context = tomcat.addContext("", null);

        TesterMapRealm realm = new TesterMapRealm();
        realm.addUser(USER, PASSWORD);
        realm.addUserRole(USER, ROLE);
        context.setRealm(realm);
        context.setLoginConfig(new LoginConfig(HttpServletRequest.BASIC_AUTH, "crawler-test", null, null));

        context.getPipeline().addValve(new CrawlerSessionManagerValve());
        BasicAuthenticator authenticator = new BasicAuthenticator();
        authenticator.setAlwaysUseSession(true);
        context.getPipeline().addValve(authenticator);

        Tomcat.addServlet(context, "account", new HttpServlet() {
            @Override
            protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
                Principal principal = request.getUserPrincipal();
                response.setContentType("text/plain");
                response.getWriter().print("principal=" + principal.getName() + " secret=" + SECRET + " session=" +
                        request.getSession(true).getId());
            }
        });
        context.addServletMapping("/account", "account");
        context.addSecurityRole(ROLE);
        SecurityCollection collection = new SecurityCollection();
        collection.addPattern("/account");
        SecurityConstraint constraint = new SecurityConstraint();
        constraint.addCollection(collection);
        constraint.addAuthRole(ROLE);
        context.addConstraint(constraint);

        tomcat.start();

        ByteChunk baselineBody = new ByteChunk();
        int baselineStatus = getUrl(url(), baselineBody, headers("ordinary-browser/1.0", null), null);
        Assert.assertEquals("protected resource must reject an unauthenticated ordinary client", 401, baselineStatus);

        String credentials = "Basic " +
                Base64.getEncoder().encodeToString((USER + ":" + PASSWORD).getBytes(StandardCharsets.ISO_8859_1));
        ByteChunk aliceBody = new ByteChunk();
        int aliceStatus = getUrl(url(), aliceBody, headers("aliceBot/1.0", credentials), null);
        Assert.assertEquals(200, aliceStatus);
        Assert.assertTrue(aliceBody.toString().contains("principal=alice secret=" + SECRET));

        // New client request: no Cookie and no Authorization. It only shares
        // the server-observed address/Host/Context and matches the bot pattern.
        ByteChunk malloryBody = new ByteChunk();
        int malloryStatus = getUrl(url(), malloryBody, headers("malloryBot/9.9", null), null);

        Assert.assertEquals("unauthenticated crawler inherited Alice's session and response: " + malloryBody, 401,
                malloryStatus);
        Assert.assertFalse(malloryBody.toString().contains(SECRET));
    }

    private String url() {
        return "http://localhost:" + getPort() + "/account";
    }

    private static Map<String,List<String>> headers(String userAgent, String authorization) {
        Map<String,List<String>> headers = new HashMap<>();
        headers.put("user-agent", Arrays.asList(userAgent));
        if (authorization != null) {
            headers.put("authorization", Arrays.asList(authorization));
        }
        return headers;
    }
}