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
import org.apache.catalina.startup.TesterMapRealm;
import org.apache.catalina.startup.TesterServlet;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.buf.HexUtils;
import org.apache.tomcat.util.descriptor.web.LoginConfig;
import org.apache.tomcat.util.descriptor.web.SecurityCollection;
import org.apache.tomcat.util.descriptor.web.SecurityConstraint;
import org.apache.tomcat.util.security.ConcurrentMessageDigest;

@RunWith(Parameterized.class)
public class TestDigestAuthenticatorB extends TomcatBaseTest {

    private static final String targetURI = "/test";
    private static final String validUser = "user";
    private static final String validPassword = "password";
    private static final String validRole = "role";
    private static final String realmName = "realm";
    private static final String clientNonce = "cnonce";


    @Parameterized.Parameters(name = "{index}")
    public static Collection<Object[]> parameters() {
        List<Object[]> parameterSets = new ArrayList<>();
        parameterSets.add(new Object[] { validRole, validUser, validPassword });
        parameterSets.add(new Object[] { "**", validUser, validPassword });
        return parameterSets;
    }

    @Parameter(0)
    public String serverPermittedRole;

    @Parameter(1)
    public String clientUserName;

    @Parameter(2)
    public String clientPassword;


    @Test
    public void testDigestAuthentication() throws Exception {
        // Configure a context with digest authentication and a single protected resource
        Tomcat tomcat = getTomcatInstance();

        // No file system docBase required
        Context ctxt = getProgrammaticRootContext();

        // Add protected servlet
        Tomcat.addServlet(ctxt, "TesterServlet", new TesterServlet());
        ctxt.addServletMappingDecoded(targetURI, "TesterServlet");
        SecurityCollection collection = new SecurityCollection();
        collection.addPatternDecoded(targetURI);
        SecurityConstraint sc = new SecurityConstraint();
        sc.addAuthRole(serverPermittedRole);
        sc.addCollection(collection);
        ctxt.addConstraint(sc);

        // Configure the Realm
        TesterMapRealm realm = new TesterMapRealm();
        realm.addUser(validUser, validPassword);
        realm.addUserRole(validUser, validRole);

        ctxt.setRealm(realm);

        // Configure the authenticator
        LoginConfig lc = new LoginConfig();
        lc.setAuthMethod("DIGEST");
        lc.setRealmName(realmName);
        ctxt.setLoginConfig(lc);
        DigestAuthenticator digestAuthenticator = new DigestAuthenticator();
        ctxt.getPipeline().addValve(digestAuthenticator);

        tomcat.start();

        // The first request will always fail - but we need the challenge
        Map<String,List<String>> respHeaders = new HashMap<>();
        ByteChunk bc = new ByteChunk();
        int rc = getUrl("http://localhost:" + getPort() + targetURI, bc, respHeaders);
        Assert.assertEquals(401, rc);
        Assert.assertTrue(bc.getLength() > 0);
        bc.recycle();

        // Second request should
        List<String> auth = new ArrayList<>();
        auth.add(buildDigestResponse(clientUserName, clientPassword, targetURI, realmName, AuthDigest.SHA_256,
                respHeaders.get(AuthenticatorBase.AUTH_HEADER_NAME), "00000001", clientNonce, DigestAuthenticator.QOP));
        Map<String,List<String>> reqHeaders = new HashMap<>();
        reqHeaders.put("authorization", auth);
        rc = getUrl("http://localhost:" + getPort() + targetURI, bc, reqHeaders, null);

        Assert.assertEquals(200, rc);
        Assert.assertEquals("OK", bc.toString());
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
