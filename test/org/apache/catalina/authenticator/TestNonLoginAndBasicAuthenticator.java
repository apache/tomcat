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

import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.deploy.LoginConfig;
import org.apache.catalina.deploy.SecurityCollection;
import org.apache.catalina.deploy.SecurityConstraint;
import org.apache.catalina.startup.TesterServlet;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.catalina.util.Base64;
import org.apache.tomcat.util.buf.ByteChunk;

/**
 * Test BasicAuthenticator and NonLoginAuthenticator when a
 * SingleSignOn Valve is not active.
 *
 * <p>
 * In the absence of SSO support, these two authenticator classes
 * both have quite simple behaviour. By testing them together, we
 * can make sure they operate independently and confirm that no
 * SSO logic has been accidentally triggered.
 */
public class TestNonLoginAndBasicAuthenticator extends TomcatBaseTest {

    private static final String USER = "user";
    private static final String PWD = "pwd";
    private static final String ROLE = "role";

    private static final String HTTP_PREFIX = "http://localhost:";
    private static final String CONTEXT_PATH_NOLOGIN = "/nologin";
    private static final String CONTEXT_PATH_LOGIN = "/login";
    private static final String URI_PROTECTED = "/protected";
    private static final String URI_PUBLIC = "/anyoneCanAccess";

    private static final int SHORT_TIMEOUT_SECS = 4;
    private static final int LONG_TIMEOUT_SECS = 10;
    private static final long LONG_TIMEOUT_DELAY_MSECS =
                                    ((LONG_TIMEOUT_SECS + 2) * 1000);

    private static String CLIENT_AUTH_HEADER = "authorization";

    /*
     * Try to access an unprotected resource in a webapp that
     * does not have a login method defined.
     * This should be permitted.
     */
    @Test
    public void testAcceptPublicNonLogin() throws Exception {
        doTestNonLogin(CONTEXT_PATH_NOLOGIN + URI_PUBLIC, false, 200);
    }

    /*
     * Try to access a protected resource in a webapp that
     * does not have a login method defined.
     * This should be rejected with SC_FORBIDDEN 403 status.
     */
    @Test
    public void testRejectProtectedNonLogin() throws Exception {
        doTestNonLogin(CONTEXT_PATH_NOLOGIN + URI_PROTECTED, true, 403);
    }

    /*
     * Try to access an unprotected resource in a webapp that
     * has a BASIC login method defined.
     * This should be permitted without a challenge.
     */
    @Test
    public void testAcceptPublicBasic() throws Exception {
        doTestBasic(USER, PWD, CONTEXT_PATH_LOGIN + URI_PUBLIC,
                false, 200, false, 200);
    }

    /*
     * Try to access a protected resource in a webapp that
     * has a BASIC login method defined. The access will be
     * challenged, authenticated and then permitted.
     */
    @Test
    public void testAcceptProtectedBasic() throws Exception {
        doTestBasic(USER, PWD, CONTEXT_PATH_LOGIN + URI_PROTECTED,
                true, 401, false, 200);
    }

    /*
     * Logon to access a protected resource in a webapp that uses
     * BASIC authentication. Wait until that session times-out,
     * then re-access the resource.
     * This should be rejected with SC_FORBIDDEN 401 status, which
     * can be followed by successful re-authentication.
     */
    @Test
    public void testBasicLoginSessionTimeout() throws Exception {
        doTestBasic(USER, PWD, CONTEXT_PATH_LOGIN + URI_PROTECTED,
                true, 401, false, 200);
        // wait long enough for the session above to expire
        Thread.sleep(LONG_TIMEOUT_DELAY_MSECS);
        doTestBasic(USER, PWD, CONTEXT_PATH_LOGIN + URI_PROTECTED,
                true, 401, false, 200);
    }

    /*
     * Logon to access a protected resource in a webapp that uses
     * BASIC authentication. Then try to access a protected resource
     * in a different webapp that does not have a login method.
     * This should be rejected with SC_FORBIDDEN 403 status, confirming
     * there has been no cross-authentication between the webapps.
     */
    @Test
    public void testBasicLoginRejectProtected() throws Exception {
        doTestBasic(USER, PWD, CONTEXT_PATH_LOGIN + URI_PROTECTED,
                true, 401, false, 200);
        doTestNonLogin(CONTEXT_PATH_NOLOGIN + URI_PROTECTED,
                true, 403);
    }


    private void doTestNonLogin(String uri, boolean expectedReject,
            int expectedRC) throws Exception {

        Map<String,List<String>> reqHeaders =
                new HashMap<String,List<String>>();
        Map<String,List<String>> respHeaders =
                new HashMap<String,List<String>>();

        ByteChunk bc = new ByteChunk();
        int rc = getUrl(HTTP_PREFIX + getPort() + uri, bc, reqHeaders,
                respHeaders);

        if (expectedReject) {
            assertEquals(expectedRC, rc);
            assertNull(bc.toString());
        }
        else {
            assertEquals(200, rc);
            assertEquals("OK", bc.toString());
        }
    }

    private void doTestBasic(String user, String pwd, String uri,
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
            assertNull(bc.toString());
        }
        else {
            assertEquals(200, rc);
            assertEquals("OK", bc.toString());
            return;
        }

        // the second access attempt should be sucessful
        String credentials = user + ":" + pwd;
        byte[] credentialsBytes = ByteChunk.convertToBytes(credentials);
        String base64auth = Base64.encode(credentialsBytes);
        String authLine = "Basic " + base64auth;

        List<String> auth = new ArrayList<String>();
        auth.add(authLine);
        Map<String,List<String>> reqHeaders2 = new HashMap<String,List<String>>();
        reqHeaders2.put(CLIENT_AUTH_HEADER, auth);

        Map<String,List<String>> respHeaders2 =
            new HashMap<String,List<String>>();

        bc.reset();
        rc = getUrl(HTTP_PREFIX + getPort() + uri, bc, reqHeaders2,
                respHeaders2);

        if (expectedReject2) {
            assertEquals(expectedRC2, rc);
            assertNull(bc.toString());
        }
        else {
            assertEquals(200, rc);
            assertEquals("OK", bc.toString());
        }
    }


    @Override
    public void setUp() throws Exception {

        super.setUp();

        // create a tomcat server using the default in-memory Realm
        Tomcat tomcat = getTomcatInstance();

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

        // Add unprotected servlet
        Tomcat.addServlet(ctxt, "TesterServlet4", new TesterServlet());
        ctxt.addServletMapping(URI_PUBLIC, "TesterServlet4");

        SecurityCollection collection2 = new SecurityCollection();
        collection2.addPattern(URI_PUBLIC);
        SecurityConstraint sc2 = new SecurityConstraint();
        // do not add a role - which signals access permitted without one
        sc2.addCollection(collection2);
        ctxt.addConstraint(sc2);

        // Configure the authenticator and inherit the Realm from Engine
        LoginConfig lc = new LoginConfig();
        lc.setAuthMethod("BASIC");
        ctxt.setLoginConfig(lc);
        ctxt.getPipeline().addValve(new BasicAuthenticator());
    }

}