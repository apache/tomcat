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
import org.apache.tomcat.util.descriptor.web.LoginConfig;
import org.apache.tomcat.util.descriptor.web.SecurityCollection;
import org.apache.tomcat.util.descriptor.web.SecurityConstraint;

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
        parameterSets.add(new Object[] { validRole, validUser, validPassword, Boolean.TRUE });
        parameterSets.add(new Object[] { "**", validUser, validPassword, Boolean.TRUE });
        parameterSets.add(new Object[] { "**", validUser, "null", Boolean.FALSE });
        parameterSets.add(new Object[] { "**", "invalid", "null", Boolean.FALSE });
        return parameterSets;
    }

    @Parameter(0)
    public String serverPermittedRole;

    @Parameter(1)
    public String clientUserName;

    @Parameter(2)
    public String clientPassword;

    @Parameter(3)
    public boolean validCredentials;


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
        auth.add(TestDigestAuthenticatorAlgorithms.buildDigestResponse(clientUserName, clientPassword, targetURI,
                realmName, AuthDigest.SHA_256, respHeaders.get(AuthenticatorBase.AUTH_HEADER_NAME), "00000001",
                clientNonce, DigestAuthenticator.QOP));
        Map<String,List<String>> reqHeaders = new HashMap<>();
        reqHeaders.put("authorization", auth);
        rc = getUrl("http://localhost:" + getPort() + targetURI, bc, reqHeaders, null);

        if (validCredentials) {
            Assert.assertEquals(200, rc);
            Assert.assertEquals("OK", bc.toString());
        } else {
            Assert.assertEquals(401, rc);
        }
    }
}
