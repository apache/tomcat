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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;

import org.apache.catalina.Context;
import org.apache.catalina.authenticator.DigestAuthenticator.AuthDigest;
import org.apache.catalina.realm.LockOutRealm;
import org.apache.catalina.realm.MessageDigestCredentialHandler;
import org.apache.catalina.startup.TesterMapRealm;
import org.apache.catalina.startup.TesterServlet;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.buf.HexUtils;
import org.apache.tomcat.util.buf.StringUtils;
import org.apache.tomcat.util.descriptor.web.LoginConfig;
import org.apache.tomcat.util.descriptor.web.SecurityCollection;
import org.apache.tomcat.util.descriptor.web.SecurityConstraint;
import org.apache.tomcat.util.security.ConcurrentMessageDigest;

@RunWith(Parameterized.class)
public class TestDigestAuthenticatorAlgorithms extends TomcatBaseTest {

    private static final String USER = "user";
    private static final String PASSWORD = "password";

    private static final String URI = "/protected";

    private static String REALM_NAME = "TestRealm";
    private static String CNONCE = "cnonce";

    private static final List<List<AuthDigest>> ALGORITHM_PERMUTATIONS = new ArrayList<>();
    static {
        ALGORITHM_PERMUTATIONS.add(Arrays.asList(AuthDigest.MD5));
        ALGORITHM_PERMUTATIONS.add(Arrays.asList(AuthDigest.MD5, AuthDigest.SHA_256));
        ALGORITHM_PERMUTATIONS.add(Arrays.asList(AuthDigest.MD5, AuthDigest.SHA_256, AuthDigest.SHA_512_256));
        ALGORITHM_PERMUTATIONS.add(Arrays.asList(AuthDigest.MD5, AuthDigest.SHA_512_256));
        ALGORITHM_PERMUTATIONS.add(Arrays.asList(AuthDigest.MD5, AuthDigest.SHA_512_256, AuthDigest.SHA_256));

        ALGORITHM_PERMUTATIONS.add(Arrays.asList(AuthDigest.SHA_256));
        ALGORITHM_PERMUTATIONS.add(Arrays.asList(AuthDigest.SHA_256, AuthDigest.MD5));
        ALGORITHM_PERMUTATIONS.add(Arrays.asList(AuthDigest.SHA_256, AuthDigest.MD5, AuthDigest.SHA_512_256));
        ALGORITHM_PERMUTATIONS.add(Arrays.asList(AuthDigest.SHA_256, AuthDigest.SHA_512_256));
        ALGORITHM_PERMUTATIONS.add(Arrays.asList(AuthDigest.SHA_256, AuthDigest.SHA_512_256, AuthDigest.MD5));

        ALGORITHM_PERMUTATIONS.add(Arrays.asList(AuthDigest.SHA_512_256));
        ALGORITHM_PERMUTATIONS.add(Arrays.asList(AuthDigest.SHA_512_256, AuthDigest.MD5));
        ALGORITHM_PERMUTATIONS.add(Arrays.asList(AuthDigest.SHA_512_256, AuthDigest.MD5, AuthDigest.SHA_256));
        ALGORITHM_PERMUTATIONS.add(Arrays.asList(AuthDigest.SHA_512_256, AuthDigest.SHA_256));
        ALGORITHM_PERMUTATIONS.add(Arrays.asList(AuthDigest.SHA_512_256, AuthDigest.SHA_256, AuthDigest.MD5));
    }

    @Parameterized.Parameters(name = "{index}: Algorithms[{0}], Algorithm[{1}], PwdDigest[{2}], AuthExpected[{3}]")
    public static Collection<Object[]> parameters() {
        List<Object[]> parameterSets = new ArrayList<>();

        for (List<AuthDigest> algorithmPermutation : ALGORITHM_PERMUTATIONS) {
            StringBuilder algorithms = new StringBuilder();
            StringUtils.join(algorithmPermutation, ',', (x) -> x.getRfcName(), algorithms);
            for (AuthDigest algorithm : AuthDigest.values()) {
                boolean authExpected = algorithmPermutation.contains(algorithm);
                for (Boolean digestPassword : booleans) {
                    String user;
                    if (digestPassword.booleanValue()) {
                        user = USER + "-" + algorithm;
                    } else {
                        user = USER;
                    }
                    parameterSets.add(new Object[] { algorithms.toString(), algorithm, digestPassword, user, Boolean.valueOf(authExpected) });
                }
            }
        }

        return parameterSets;
    }

    @Parameter(0)
    public String serverAlgorithms;

    @Parameter(1)
    public AuthDigest clientAlgorithm;

    @Parameter(2)
    public boolean digestPassword;

    @Parameter(3)
    public String user;

    @Parameter(4)
    public boolean authExpected;


    @Test
    public void testDigestAuthentication() throws Exception {
        // Make sure client algorithm is available for digests
        ConcurrentMessageDigest.init(clientAlgorithm.getJavaName());

        // Configure a context with digest authentication and a single protected resource
        Tomcat tomcat = getTomcatInstance();

        // No file system docBase required
        Context ctxt = getProgrammaticRootContext();

        // Add protected servlet
        Tomcat.addServlet(ctxt, "TesterServlet", new TesterServlet());
        ctxt.addServletMappingDecoded(URI, "TesterServlet");
        SecurityCollection collection = new SecurityCollection();
        collection.addPatternDecoded(URI);
        SecurityConstraint sc = new SecurityConstraint();
        sc.addAuthRole("role");
        sc.addCollection(collection);
        ctxt.addConstraint(sc);

        // Configure the Realm
        TesterMapRealm realm = new TesterMapRealm();
        String password;
        if (digestPassword) {
            MessageDigestCredentialHandler mdch = new MessageDigestCredentialHandler();
            mdch.setAlgorithm(clientAlgorithm.getJavaName());
            mdch.setSaltLength(0);
            realm.setCredentialHandler(mdch);
            password = mdch.mutate(user + ":" + REALM_NAME + ":" + PASSWORD);
        } else {
            password = PASSWORD;
        }
        realm.addUser(user, password);
        realm.addUserRole(user, "role");

        LockOutRealm lockOutRealm = new LockOutRealm();
        lockOutRealm.addRealm(realm);
        ctxt.setRealm(lockOutRealm);

        // Configure the authenticator
        LoginConfig lc = new LoginConfig();
        lc.setAuthMethod("DIGEST");
        lc.setRealmName(REALM_NAME);
        ctxt.setLoginConfig(lc);
        DigestAuthenticator digestAuthenticator = new DigestAuthenticator();
        digestAuthenticator.setAlgorithms(serverAlgorithms);
        ctxt.getPipeline().addValve(digestAuthenticator);

        tomcat.start();

        // The first request will always fail - but we need the challenge
        Map<String, List<String>> respHeaders = new HashMap<>();
        ByteChunk bc = new ByteChunk();
        int rc = getUrl("http://localhost:" + getPort() + URI, bc, respHeaders);
        Assert.assertEquals(401, rc);
        Assert.assertTrue(bc.getLength() > 0);
        bc.recycle();

        // Second request will succeed depending on client and server algorithms
        List<String> auth = new ArrayList<>();
        auth.add(buildDigestResponse(user, PASSWORD, URI, REALM_NAME, clientAlgorithm,
                respHeaders.get(AuthenticatorBase.AUTH_HEADER_NAME), "00000001", CNONCE, DigestAuthenticator.QOP));
        Map<String, List<String>> reqHeaders = new HashMap<>();
        reqHeaders.put("authorization", auth);
        rc = getUrl("http://localhost:" + getPort() + URI, bc, reqHeaders, null);

        if (authExpected) {
            Assert.assertEquals(200, rc);
            Assert.assertEquals("OK", bc.toString());
        } else {
            Assert.assertEquals(401, rc);
        }
    }


    protected static String getNonce(String authHeader) {
        int start = authHeader.indexOf("nonce=\"") + 7;
        int end = authHeader.indexOf('\"', start);
        return authHeader.substring(start, end);
    }


    protected static String getOpaque(String authHeader) {
        int start = authHeader.indexOf("opaque=\"") + 8;
        int end = authHeader.indexOf('\"', start);
        return authHeader.substring(start, end);
    }


    private static String buildDigestResponse(String user, String pwd, String uri, String realm, AuthDigest algorithm,
            List<String> authHeaders, String nc, String cnonce, String qop) {

        // Find auth header with correct algorithm
        String nonce = null;
        String opaque = null;
        for (String authHeader : authHeaders) {
            nonce = getNonce(authHeader);
            opaque = getOpaque(authHeader);
            if (authHeader.contains("algorithm=" + algorithm.getRfcName())) {
                break;
            }
        }
        if (nonce == null || opaque == null) {
            Assert.fail();
        }

        String a1 = user + ":" + realm + ":" + pwd;
        String a2 = "GET:" + uri;

        String digestA1 = digest(algorithm.getJavaName(), a1);
        String digestA2 = digest(algorithm.getJavaName(), a2);

        String response;
        if (qop == null) {
            response = digestA1 + ":" + nonce + ":" + digestA2;
        } else {
            response = digestA1 + ":" + nonce + ":" + nc + ":" + cnonce + ":" + qop + ":" + digestA2;
        }

        String digestResponse = digest(algorithm.getJavaName(), response);

        StringBuilder auth = new StringBuilder();
        auth.append("Digest username=\"");
        auth.append(user);
        auth.append("\", realm=\"");
        auth.append(realm);
        auth.append("\", algorithm=");
        auth.append(algorithm.getRfcName());
        auth.append(", nonce=\"");
        auth.append(nonce);
        auth.append("\", uri=\"");
        auth.append(uri);
        auth.append("\", opaque=\"");
        auth.append(opaque);
        auth.append("\", response=\"");
        auth.append(digestResponse);
        auth.append("\"");
        if (qop != null) {
            auth.append(", qop=");
            auth.append(qop);
            auth.append("");
        }
        if (nc != null) {
            auth.append(", nc=");
            auth.append(nc);
        }
        if (cnonce != null) {
            auth.append(", cnonce=\"");
            auth.append(cnonce);
            auth.append("\"");
        }

        return auth.toString();
    }

    private static String digest(String algorithm, String input) {
        return HexUtils.toHexString(ConcurrentMessageDigest.digest(algorithm, input.getBytes()));
    }
}
