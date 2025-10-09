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
package org.apache.catalina.authenticator;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.Manager;
import org.apache.catalina.Session;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.startup.TesterServlet;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.catalina.valves.ValveBase;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.descriptor.web.LoginConfig;
import org.apache.tomcat.util.descriptor.web.SecurityCollection;
import org.apache.tomcat.util.descriptor.web.SecurityConstraint;

public class TestSSOChangeSessionId extends TomcatBaseTest {

    protected static final boolean USE_COOKIES = true;

    private static final String NOTSORANDOMID = "NOTSORANNDOMID";
    private static final String USER = "user";
    private static final String PWD = "pwd";
    private static final String ROLE = "role";

    private static final String HTTP_PREFIX = "http://localhost:";
    private static final String CONTEXT_PATH = "/test";
    private static final String URI_PROTECTED = "/protected";
    private static final String URI_AUTHENTICATION = "/authentication";

    private Context testContext;
    private SingleSignOn singleSignOn;

    private static final String SERVER_COOKIE_HEADER = "Set-Cookie";
    private static final String CLIENT_COOKIE_HEADER = "Cookie";

    private List<String> cookies;

    /*
     * setup two webapps for every test
     *
     * note: the super class tearDown method will stop tomcat
     */
    @Override
    public void setUp() throws Exception {
        super.setUp();

        // create a tomcat server using the default in-memory Realm
        final Tomcat tomcat = getTomcatInstance();

        // Must have a real docBase for webapps - just use temp
        testContext = tomcat.addContext(CONTEXT_PATH, System.getProperty("java.io.tmpdir"));

        singleSignOn = new SingleSignOn();
        tomcat.getHost().getPipeline().addValve(singleSignOn);

        testContext.getPipeline().addValve(new SessionManipulationFilter());

        // Add protected servlet to the context
        Tomcat.addServlet(testContext, "TesterServlet1", new TesterServlet());
        testContext.addServletMappingDecoded(URI_PROTECTED, "TesterServlet1");

        SecurityCollection collection1 = new SecurityCollection();
        collection1.addPatternDecoded(URI_PROTECTED);
        SecurityConstraint sc1 = new SecurityConstraint();
        sc1.addAuthRole(ROLE);
        sc1.addCollection(collection1);
        testContext.addConstraint(sc1);

        // Add Authenticator
        Tomcat.addServlet(testContext, "LoginServlet", new LoginServlet());
        testContext.addServletMappingDecoded(URI_AUTHENTICATION, "LoginServlet");

        SecurityCollection collection2 = new SecurityCollection();
        collection2.addPatternDecoded(URI_AUTHENTICATION);
        SecurityConstraint sc2 = new SecurityConstraint();
        // do not add a role - which signals access permitted without one
        sc2.addCollection(collection2);
        testContext.addConstraint(sc2);

        // Configure the authenticator and inherit the Realm from Engine
        LoginConfig lc = new LoginConfig();
        lc.setAuthMethod("NONE");
        testContext.setLoginConfig(lc);
        AuthenticatorBase nonloginAuthenticator = new NonLoginAuthenticator();
        testContext.getPipeline().addValve(nonloginAuthenticator);

        // add the test user and role to the Realm
        tomcat.addUser(USER, PWD);
        tomcat.addRole(USER, ROLE);

        tomcat.start();
    }

    private static class SessionManipulationFilter extends ValveBase {
        @Override
        public void invoke(Request request, Response response) throws IOException, ServletException {

            if (request.getPrincipal() != null) {
                final Manager manager = request.getContext().getManager();
                final Session session = manager.findSession(request.getSession().getId());
                manager.changeSessionId(session, NOTSORANDOMID);
            }

            getNext().invoke(request, response);
        }
    }

    private static class LoginServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {

            if (request.getUserPrincipal() == null) {
                request.login(USER, PWD);

                response.setContentType("text/plain");
                PrintWriter out = response.getWriter();
                out.print("OK");

            } else {
                response.setStatus(HttpServletResponse.SC_CONFLICT);
            }
        }
    }

    @Test
    public void testChangeSessionId() throws Exception {
        final Map<String,SingleSignOnEntry> cache = singleSignOn.cache;
        Assert.assertTrue("No SSO entries should be present at startup", cache.isEmpty());

        // Authenticate a session
        doTest(CONTEXT_PATH + URI_AUTHENTICATION, USE_COOKIES, HttpServletResponse.SC_OK);

        Assert.assertFalse("SSO must now be present", cache.isEmpty());

        Assert.assertEquals("Only one SSO entry must be present", 1, cache.size());
        final Collection<SingleSignOnEntry> ssoEntries = cache.values();
        Assert.assertEquals("Only one session should be present, as there is only one context in the test", 1,
                ssoEntries.size());

        final SingleSignOnEntry singleSignOnEntry = ssoEntries.iterator().next();
        final Set<SingleSignOnSessionKey> sessions = singleSignOnEntry.findSessions();

        Assert.assertEquals(1, sessions.size());

        final SingleSignOnSessionKey singleSignOnSessionKey1 = sessions.iterator().next();
        Assert.assertNotEquals("A random SessionId is expected", NOTSORANDOMID, singleSignOnSessionKey1.getSessionId());

        // Perform the request that changes the session id:
        doTest(CONTEXT_PATH + URI_PROTECTED, USE_COOKIES, HttpServletResponse.SC_OK);

        Assert.assertEquals("2 sessions means we have a zombie SingleSignOnSessionKey", 1, sessions.size());

        final SingleSignOnSessionKey singleSignOnSessionKey2 = sessions.iterator().next();
        Assert.assertEquals(NOTSORANDOMID, singleSignOnSessionKey2.getSessionId());

        final Session session = testContext.getManager().findSession(NOTSORANDOMID);
        Assert.assertNotNull("We need a session in order to test expiry", session);

        session.expire();

        Assert.assertTrue("No SSO entries should be present after expiration of the session", cache.isEmpty());

        // Shouldn't this return 401?
        doTest(CONTEXT_PATH + URI_PROTECTED, USE_COOKIES, HttpServletResponse.SC_FORBIDDEN);
    }

    @After
    @Override
    public void tearDown() throws Exception {
        super.tearDown();
    }

    public void doTest(String uri, boolean useCookie, int expectedRC) throws Exception {

        Map<String,List<String>> reqHeaders = new HashMap<>();
        Map<String,List<String>> respHeaders = new HashMap<>();

        if (useCookie && (cookies != null)) {
            addCookies(reqHeaders);
        }

        ByteChunk bc = new ByteChunk();
        int rc = getUrl(HTTP_PREFIX + getPort() + uri, bc, reqHeaders, respHeaders);
        if (expectedRC != HttpServletResponse.SC_OK) {
            Assert.assertEquals(expectedRC, rc);
            Assert.assertTrue(bc.getLength() > 0);
        } else {
            Assert.assertEquals("OK", bc.toString());
            saveCookies(respHeaders);
        }
    }

    /*
     * add all saved cookies to the outgoing request
     */
    protected void addCookies(Map<String,List<String>> reqHeaders) {
        if ((cookies != null) && (cookies.size() > 0)) {
            StringBuilder cookieHeader = new StringBuilder();
            boolean first = true;
            for (String cookie : cookies) {
                if (!first) {
                    cookieHeader.append(';');
                } else {
                    first = false;
                }
                cookieHeader.append(cookie);
            }
            List<String> cookieHeaderList = new ArrayList<>(1);
            cookieHeaderList.add(cookieHeader.toString());
            reqHeaders.put(CLIENT_COOKIE_HEADER, cookieHeaderList);
        }
    }

    /*
     * extract and save the server cookies from the incoming response
     */
    protected void saveCookies(Map<String,List<String>> respHeaders) {
        // we only save the Cookie values, not header prefix
        List<String> cookieHeaders = respHeaders.get(SERVER_COOKIE_HEADER);
        if (cookieHeaders == null) {
            cookies = null;
        } else {
            cookies = new ArrayList<>(cookieHeaders.size());
            for (String cookieHeader : cookieHeaders) {
                cookies.add(cookieHeader.substring(0, cookieHeader.indexOf(';')));
            }
        }
    }
}