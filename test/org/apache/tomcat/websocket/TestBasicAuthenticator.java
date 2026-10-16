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

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.Assert;
import org.junit.Test;

public class TestBasicAuthenticator {

    @Test
    public void testAuthorizationNoCharset() throws Exception {
        // UTF-8 is used by default if no charset is specified. The pound
        // sign is a single byte in ISO-8859-1 and two bytes in UTF-8 so the
        // two encodings can be distinguished.
        String authorization = getAuthorization("Basic realm=\"realm\"", "user", "pass\u00A3word");
        Assert.assertEquals(" Basic " + encode("user:pass\u00A3word", StandardCharsets.UTF_8), authorization);
    }

    @Test
    public void testAuthorizationCharsetUtf8() throws Exception {
        String authorization = getAuthorization("Basic realm=\"realm\", charset=UTF-8");
        Assert.assertEquals(" Basic " + encode("user:password", StandardCharsets.UTF_8), authorization);
    }

    @Test
    public void testAuthorizationCharsetUtf8NonLatin1() throws Exception {
        // The UTF-8 bytes of characters that are not present in ISO-8859-1
        // must be used rather than ISO-8859-1's '?' replacements
        String authorization = getAuthorization("Basic realm=\"realm\", charset=UTF-8", "user", "\u7528\u6237");
        Assert.assertEquals(" Basic " + encode("user:\u7528\u6237", StandardCharsets.UTF_8), authorization);
    }

    @Test
    public void testAuthorizationCharsetUtf8LowerCase() throws Exception {
        String authorization = getAuthorization("Basic realm=\"realm\", charset=utf-8");
        Assert.assertEquals(" Basic " + encode("user:password", StandardCharsets.UTF_8), authorization);
    }

    @Test(expected = AuthenticationException.class)
    public void testAuthorizationCharsetIso88592() throws Exception {
        // RFC 7617 section 2.1: UTF-8 is the only permitted value
        getAuthorization("Basic realm=\"realm\", charset=ISO-8859-2");
    }

    @Test(expected = AuthenticationException.class)
    public void testAuthorizationCharsetInvalid() throws Exception {
        // RFC 7617 section 2.1: UTF-8 is the only permitted value
        getAuthorization("Basic realm=\"realm\", charset=garbage");
    }

    private static String getAuthorization(String authenticateHeader) throws AuthenticationException {
        return getAuthorization(authenticateHeader, "user", "password");
    }

    private static String getAuthorization(String authenticateHeader, String userName, String userPassword)
            throws AuthenticationException {
        BasicAuthenticator authenticator = new BasicAuthenticator();
        return authenticator.getAuthorization("GET", "/ws/echo", authenticateHeader, userName, userPassword, null);
    }

    private static String encode(String value, Charset charset) {
        return Base64.getEncoder().encodeToString(value.getBytes(charset));
    }
}
