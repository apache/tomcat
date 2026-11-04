/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tomcat.websocket;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Assert;
import org.junit.Test;

import org.apache.tomcat.util.buf.HexUtils;

public class TestDigestAuthenticator {

    private static final Pattern RESPONSE_PATTERN = Pattern.compile("response=\"([^\"]+)\"");
    private static final Pattern CNONCE_PATTERN = Pattern.compile("cnonce=\"([^\"]+)\"");
    private static final Pattern NC_PATTERN = Pattern.compile("nc=([0-9a-fA-F]{8})");

    @Test
    public void testAuthorizationQopAuth() throws Exception {
        doTestAuthorization("auth");
    }

    @Test
    public void testAuthorizationQopAuthInt() throws Exception {
        doTestAuthorization("auth-int");
    }

    @Test(expected = AuthenticationException.class)
    public void testAuthorizationMissingRealm() throws Exception {
        String authenticateHeader =
                "Digest qop=\"auth\", nonce=\"0000f844a605d5c0cb0270f1e2f10ca0\", algorithm=MD5";

        DigestAuthenticator authenticator = new DigestAuthenticator();
        authenticator.getAuthorization("GET", "/ws/echo", authenticateHeader, "user", "password", null);
    }

    private void doTestAuthorization(String qop) throws Exception {
        String method = "GET";
        String requestUri = "/ws/echo";
        String userName = "user";
        String password = "password";
        String realm = "realm";
        String nonce = "0000f844a605d5c0cb0270f1e2f10ca0";
        String algorithm = "MD5";

        String authenticateHeader = "Digest realm=\"" + realm + "\", qop=\"" + qop + "\", nonce=\"" + nonce +
                "\", algorithm=" + algorithm;

        DigestAuthenticator authenticator = new DigestAuthenticator();
        String authorization =
                authenticator.getAuthorization(method, requestUri, authenticateHeader, userName, password, null);

        String response = extract(RESPONSE_PATTERN, authorization);
        String cnonce = extract(CNONCE_PATTERN, authorization);
        String nc = extract(NC_PATTERN, authorization);
        Assert.assertEquals("00000001", nc);

        // Calculate the expected response independently of the code under
        // test, in the same way as a server would, per RFC 2617 section
        // 3.2.2.1 and RFC 7616 section 3.4.2.
        String a1 = userName + ":" + realm + ":" + password;
        String a2 = method + ":" + requestUri;
        if ("auth-int".equals(qop)) {
            // RFC 7616: A2 includes H(entity-body). A WebSocket handshake
            // does not have an entity body so use the hash of the empty
            // string.
            a2 = a2 + ":" + encode("");
        }
        String expected = encode(encode(a1) + ":" + nonce + ":" + nc + ":" + cnonce + ":" + qop + ":" + encode(a2));

        Assert.assertEquals(expected, response);
    }

    private static String extract(Pattern pattern, String authorization) {
        Matcher m = pattern.matcher(authorization);
        Assert.assertTrue(m.find());
        return m.group(1);
    }

    private static String encode(String value) throws Exception {
        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] digest = md.digest(value.getBytes(StandardCharsets.ISO_8859_1));
        return HexUtils.toHexString(digest);
    }
}
