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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.deploy.LoginConfig;
import org.apache.catalina.deploy.SecurityCollection;
import org.apache.catalina.deploy.SecurityConstraint;
import org.apache.catalina.startup.TesterServlet;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.buf.B2CConverter;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.codec.binary.Base64;

/**
 * Test BasicAuthenticator and NonLoginAuthenticator when a
 * SingleSignOn Valve is active.
 *
 * <p>
 * In the absence of SSO support, a webapp using NonLoginAuthenticator
 * simply cannot access protected resources. These tests exercise the
 * the way successfully authenticating a different webapp under the
 * BasicAuthenticator triggers the additional SSO logic for both webapps.
 */
public class TestSSOnonLoginAndBasicAuthenticator extends TomcatBaseTest {

    private static final String USER = "user";
    private static final String PWD = "pwd";
    private static final String ROLE = "role";

    private static final String HTTP_PREFIX = "http://localhost:";
    private static final String CONTEXT_PATH_NOLOGIN = "/nologin";
    private static final String CONTEXT_PATH_LOGIN = "/login";
    private static final String URI_PROTECTED = "/protected";
    private static final String URI_PUBLIC = "/anyoneCanAccess";

    private static final int SHORT_TIMEOUT_SECS = 4;
    private static final long SHORT_TIMEOUT_DELAY_MSECS =
                                    ((SHORT_TIMEOUT_SECS + 3) * 1000);
    private static final int LONG_TIMEOUT_SECS = 10;
    private static final long LONG_TIMEOUT_DELAY_MSECS =
                                    ((LONG_TIMEOUT_SECS + 5) * 1000);

    private static String CLIENT_AUTH_HEADER = "authorization";
    private static String SERVER_COOKIES = "Set-Cookie";
    private static String BROWSER_COOKIES = "Cookie";

    private List<String> cookies;

    /*
     * Try to access an unprotected resource without an established
     * SSO session.
     * This should be permitted.
     */
    @Test
    public void testAcceptPublicNonLogin() throws Exception {
        doTestNonLogin(CONTEXT_PATH_NOLOGIN + URI_PUBLIC,
                        false, false, 200);
    }

    /*
     * Try to access a protected resource without an established
     * SSO session.
     * This should be rejected with SC_FORBIDDEN 403 status.
     */
    @Test
    public void testRejectProtectedNonLogin() throws Exception {
        doTestNonLogin(CONTEXT_PATH_NOLOGIN + URI_PROTECTED,
                        false, true, 403);
    }

    /*
     * Logon to access a protected resource using BASIC authentication,
     * which will establish an SSO session.
     * Wait until the SSO session times-out, then try to re-access
     * the resource.
     * This should be rejected with SC_FORBIDDEN 401 status, which
     * will then be followed by successful re-authentication.
     */
    @Test
    public void testBasicLoginSessionTimeout() throws Exception {
        doTestBasic(USER, PWD, CONTEXT_PATH_LOGIN + URI_PROTECTED,
                true, 401, false, 200);
        // wait long enough for my session to expire
        Thread.sleep(SHORT_TIMEOUT_DELAY_MSECS);
        doTestBasic(USER, PWD, CONTEXT_PATH_LOGIN + URI_PROTECTED,
                true, 401, false, 200);
    }

    /*
     * Logon to access a protected resource using BASIC authentication,
     * which will establish an SSO session.
     * Immediately try to access a protected resource in the NonLogin
     * webapp, but without sending the SSO session cookie.
     * This should be rejected with SC_FORBIDDEN 403 status.
     */
    @Test
    public void testBasicLoginRejectProtectedWithoutCookies() throws Exception {
        doTestBasic(USER, PWD, CONTEXT_PATH_LOGIN + URI_PROTECTED,
                true, 401, false, 200);
        doTestNonLogin(CONTEXT_PATH_NOLOGIN + URI_PROTECTED,
                        false, true, 403);
    }

    /*
     * Logon to access a protected resource using BASIC authentication,
     * which will establish an SSO session.
     * Immediately try to access a protected resource in the NonLogin
     * webapp while sending the SSO session cookie provided by the
     * first webapp.
     * This should be successful with SC_OK 200 status.
     */
    @Test
    public void testBasicLoginAcceptProtectedWithCookies() throws Exception {
        doTestBasic(USER, PWD, CONTEXT_PATH_LOGIN + URI_PROTECTED,
                true, 401, false, 200);
        doTestNonLogin(CONTEXT_PATH_NOLOGIN + URI_PROTECTED,
                        true, false, 200);
    }

    /*
     * Logon to access a protected resource using BASIC authentication,
     * which will establish an SSO session.
     * Immediately try to access a protected resource in the NonLogin
     * webapp while sending the SSO session cookie provided by the
     * first webapp.
     * This should be successful with SC_OK 200 status.
     *
     * Then, wait long enough for the BASIC session to expire. (The SSO
     * session should remain active because the NonLogin session has
     * not yet expired).
     *
     * Try to access the protected resource again, before the SSO session
     * has expired.
     * This should be successful with SC_OK 200 status.
     *
     * Finally, wait for the non-login session to expire and try again..
     * This should be rejected with SC_FORBIDDEN 403 status.
     *
     * (see bugfix https://issues.apache.org/bugzilla/show_bug.cgi?id=52303)
     */
    @Test
    public void testBasicExpiredAcceptProtectedWithCookies() throws Exception {
        doTestBasic(USER, PWD, CONTEXT_PATH_LOGIN + URI_PROTECTED,
                true, 401, false, 200);
        doTestNonLogin(CONTEXT_PATH_NOLOGIN + URI_PROTECTED,
                        true, false, 200);

        // wait long enough for the BASIC session to expire,
        // but not long enough for NonLogin session expiry
        Thread.sleep(SHORT_TIMEOUT_DELAY_MSECS);
        doTestNonLogin(CONTEXT_PATH_NOLOGIN + URI_PROTECTED,
                        true, false, 200);

        // wait long enough for my NonLogin session to expire
        // and tear down the SSO session at the same time.
        Thread.sleep(LONG_TIMEOUT_DELAY_MSECS);
        doTestNonLogin(CONTEXT_PATH_NOLOGIN + URI_PROTECTED,
                        false, true, 403);
    }


    public void doTestNonLogin(String uri, boolean addCookies,
            boolean expectedReject, int expectedRC)
            throws Exception {

        Map<String,List<String>> reqHeaders =
                new HashMap<String,List<String>>();
        if (addCookies) {
            addCookies(reqHeaders);
        }
        Map<String,List<String>> respHeaders =
                new HashMap<String,List<String>>();

        ByteChunk bc = new ByteChunk();
        int rc = getUrl(HTTP_PREFIX + getPort() + uri, bc, reqHeaders,
                respHeaders);

        if (expectedReject) {
            assertEquals(expectedRC, rc);
            assertTrue(bc.getLength() > 0);
        }
        else {
            assertEquals(200, rc);
            assertEquals("OK", bc.toString());
            saveCookies(respHeaders);
        }
}

    public void doTestBasic(String user, String pwd, String uri,
            boolean expectedReject1, int expectedRC1,
            boolean expectedReject2, int expectedRC2) throws Exception {

        // the first access attempt should be challenged
        Map<String,List<String>> reqHeaders1 =
                new HashMap<String,List<String>>();
        Map<String,List<String>> respHeaders1 =
                new HashMap<String,List<String>>();

        ByteChunk bc = new ByteChunk();
        int rc = getUrl(HTTP_PREFIX + getPort() + uri, bc, reqHeaders1,
                respHeaders1);

        if (expectedReject1) {
            assertEquals(expectedRC1, rc);
            assertTrue(bc.getLength() > 0);
        }
        else {
            assertEquals(200, rc);
            assertEquals("OK", bc.toString());
            return;
        }

        // the second access attempt should be successful
        String credentials = user + ":" + pwd;

        String base64auth = Base64.encodeBase64String(
                credentials.getBytes(B2CConverter.ISO_8859_1));
        String authLine = "Basic " + base64auth;

        List<String> auth = new ArrayList<String>();
        auth.add(authLine);
        Map<String,List<String>> reqHeaders2 = new HashMap<String,List<String>>();
        reqHeaders2.put(CLIENT_AUTH_HEADER, auth);

        Map<String,List<String>> respHeaders2 =
            new HashMap<String,List<String>>();

        bc.recycle();
        rc = getUrl(HTTP_PREFIX + getPort() + uri, bc, reqHeaders2,
                respHeaders2);

        if (expectedReject2) {
            assertEquals(expectedRC2, rc);
            assertNull(bc.toString());
        }
        else {
            assertEquals(200, rc);
            assertEquals("OK", bc.toString());
            saveCookies(respHeaders2);
        }
    }


    @Override
    public void setUp() throws Exception {

        super.setUp();

        // create a tomcat server using the default in-memory Realm
        Tomcat tomcat = getTomcatInstance();

        // associate the SingeSignOn Valve before the Contexts
        SingleSignOn sso = new SingleSignOn();
        tomcat.getHost().getPipeline().addValve(sso);

        // add the test user and role to the Realm
        tomcat.addUser(USER, PWD);
        tomcat.addRole(USER, ROLE);

        // setup both NonLogin and Login webapps
        setUpNonLogin(tomcat);
        setUpLogin(tomcat);

        tomcat.start();
    }

    private void setUpNonLogin(Tomcat tomcat) throws Exception {

        // Must have a real docBase for webapps - just use temp
        Context ctxt = tomcat.addContext(CONTEXT_PATH_NOLOGIN,
                System.getProperty("java.io.tmpdir"));
        ctxt.setSessionTimeout(LONG_TIMEOUT_SECS);

        // Add protected servlet
        Tomcat.addServlet(ctxt, "TesterServlet1", new TesterServlet());
        ctxt.addServletMapping(URI_PROTECTED, "TesterServlet1");

        SecurityCollection collection1 = new SecurityCollection();
        collection1.addPattern(URI_PROTECTED);
        SecurityConstraint sc1 = new SecurityConstraint();
        sc1.addAuthRole(ROLE);
        sc1.addCollection(collection1);
        ctxt.addConstraint(sc1);

        // Add unprotected servlet
        Tomcat.addServlet(ctxt, "TesterServlet2", new TesterServlet());
        ctxt.addServletMapping(URI_PUBLIC, "TesterServlet2");

        SecurityCollection collection2 = new SecurityCollection();
        collection2.addPattern(URI_PUBLIC);
        SecurityConstraint sc2 = new SecurityConstraint();
        // do not add a role - which signals access permitted without one
        sc2.addCollection(collection2);
        ctxt.addConstraint(sc2);

        // Configure the authenticator and inherit the Realm from Engine
        LoginConfig lc = new LoginConfig();
        lc.setAuthMethod("NONE");
        ctxt.setLoginConfig(lc);
        ctxt.getPipeline().addValve(new NonLoginAuthenticator());
    }

    private void setUpLogin(Tomcat tomcat) throws Exception {

        // Must have a real docBase for webapps - just use temp
        Context ctxt = tomcat.addContext(CONTEXT_PATH_LOGIN,
                System.getProperty("java.io.tmpdir"));
        ctxt.setSessionTimeout(SHORT_TIMEOUT_SECS);

        // Add protected servlet
        Tomcat.addServlet(ctxt, "TesterServlet3", new TesterServlet());
        ctxt.addServletMapping(URI_PROTECTED, "TesterServlet3");

        SecurityCollection collection = new SecurityCollection();
        collection.addPattern(URI_PROTECTED);
        SecurityConstraint sc = new SecurityConstraint();
        sc.addAuthRole(ROLE);
        sc.addCollection(collection);
        ctxt.addConstraint(sc);

        // Configure the appropriate authenticator
        LoginConfig lc = new LoginConfig();
        lc.setAuthMethod("BASIC");
        ctxt.setLoginConfig(lc);
        ctxt.getPipeline().addValve(new BasicAuthenticator());
    }

    /*
     * extract and save the server cookies from the incoming response
     */
    protected void saveCookies(Map<String,List<String>> respHeaders) {

        // we only save the Cookie values, not header prefix
        cookies = respHeaders.get(SERVER_COOKIES);
    }

    /*
     * add all saved cookies to the outgoing request
     */
    protected void addCookies(Map<String,List<String>> reqHeaders) {

        if ((cookies != null) && (cookies.size() > 0)) {
            reqHeaders.put(BROWSER_COOKIES + ":", cookies);
        }
    }
}