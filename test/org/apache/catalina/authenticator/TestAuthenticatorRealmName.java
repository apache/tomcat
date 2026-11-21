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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.Valve;
import org.apache.catalina.startup.TesterMapRealm;
import org.apache.catalina.startup.TesterServlet;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.descriptor.web.LoginConfig;
import org.apache.tomcat.util.descriptor.web.SecurityCollection;
import org.apache.tomcat.util.descriptor.web.SecurityConstraint;

/*
 * The realm in a WWW-Authenticate challenge is a quoted-string (RFC 9110, section 5.6.4). DQUOTE and backslash in the
 * configured realm name have to be escaped.
 */
public class TestAuthenticatorRealmName extends TomcatBaseTest {

    // Note: Java escaped
    private static final String REALM_NAME = "CORP\\Intranet \"Test\"";
    private static final String QUOTED_REALM_NAME = "realm=\"CORP\\\\Intranet \\\"Test\\\"\"";

    @Test
    public void testBasic() throws Exception {
        doTest(HttpServletRequest.BASIC_AUTH, new BasicAuthenticator(), "Basic " + QUOTED_REALM_NAME);
    }


    @Test
    public void testDigest() throws Exception {
        doTest(HttpServletRequest.DIGEST_AUTH, new DigestAuthenticator(), "Digest " + QUOTED_REALM_NAME + ",");
    }


    private void doTest(String authMethod, Valve authenticator, String expectedStart) throws Exception {
        Tomcat tomcat = getTomcatInstance();

        // No file system docBase required
        Context ctxt = getProgrammaticRootContext();

        Tomcat.addServlet(ctxt, "TesterServlet", new TesterServlet());
        ctxt.addServletMapping("/protected", "TesterServlet");
        SecurityCollection collection = new SecurityCollection();
        collection.addPattern("/protected");
        SecurityConstraint sc = new SecurityConstraint();
        sc.addAuthRole("role");
        sc.addCollection(collection);
        ctxt.addConstraint(sc);

        TesterMapRealm realm = new TesterMapRealm();
        realm.addUser("user", "pwd");
        realm.addUserRole("user", "role");
        ctxt.setRealm(realm);

        LoginConfig lc = new LoginConfig();
        lc.setAuthMethod(authMethod);
        lc.setRealmName(REALM_NAME);
        ctxt.setLoginConfig(lc);
        ctxt.getPipeline().addValve(authenticator);

        tomcat.start();

        Map<String,List<String>> respHeaders = new HashMap<>();
        int rc = getUrl("http://localhost:" + getPort() + "/protected", new ByteChunk(), null, respHeaders);
        Assert.assertEquals(HttpServletResponse.SC_UNAUTHORIZED, rc);

        List<String> challenges = respHeaders.get(AuthenticatorBase.AUTH_HEADER_NAME);
        Assert.assertNotNull(challenges);
        for (String challenge : challenges) {
            Assert.assertTrue(challenge, challenge.startsWith(expectedStart));
        }
    }
}
