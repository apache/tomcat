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
package org.apache.tomcat.util.http.parser;

import java.io.StringReader;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

public class TestAuthorizationDigest {

    @Test
    public void testBug54060a() throws Exception {
        String header = "Digest username=\"mthornton\", " +
                "realm=\"optrak.com\", " +
                "nonce=\"1351427243671:c1d6360150712149bae931a3ed7cb498\", " +
                "uri=\"/files/junk.txt\", " +
                "response=\"c5c2410bfc46753e83a8f007888b0d2e\", " +
                "opaque=\"DB85C1A73933A7EB586D10E4BF2924EF\", " +
                "qop=auth, " +
                "nc=00000001, " +
                "cnonce=\"9926cb3c334ede11\"";

        StringReader input = new StringReader(header);

        Map<String,String> result = Authorization.parseAuthorizationDigest(input);

        Assert.assertEquals("mthornton", result.get("username"));
        Assert.assertEquals("optrak.com", result.get("realm"));
        Assert.assertEquals("1351427243671:c1d6360150712149bae931a3ed7cb498",
                result.get("nonce"));
        Assert.assertEquals("/files/junk.txt", result.get("uri"));
        Assert.assertEquals("c5c2410bfc46753e83a8f007888b0d2e",
                result.get("response"));
        Assert.assertEquals("DB85C1A73933A7EB586D10E4BF2924EF",
                result.get("opaque"));
        Assert.assertEquals("auth", result.get("qop"));
        Assert.assertEquals("00000001", result.get("nc"));
        Assert.assertEquals("9926cb3c334ede11", result.get("cnonce"));
    }

    @Test
    public void testBug54060b() throws Exception {
        String header = "Digest username=\"mthornton\", " +
                "realm=\"optrak.com\", " +
                "nonce=\"1351427480964:a01c16fed5168d72a2b5267395a2022e\", " +
                "uri=\"/files\", " +
                "algorithm=MD5, " +
                "response=\"f310c44b87efc0bc0a7aab7096fd36b6\", " +
                "opaque=\"DB85C1A73933A7EB586D10E4BF2924EF\", " +
                "cnonce=\"MHg3ZjA3ZGMwMTUwMTA6NzI2OToxMzUxNDI3NDgw\", " +
                "nc=00000001, " +
                "qop=auth";

        StringReader input = new StringReader(header);

        Map<String,String> result = Authorization.parseAuthorizationDigest(input);

        Assert.assertEquals("mthornton", result.get("username"));
        Assert.assertEquals("optrak.com", result.get("realm"));
        Assert.assertEquals("1351427480964:a01c16fed5168d72a2b5267395a2022e",
                result.get("nonce"));
        Assert.assertEquals("/files", result.get("uri"));
        Assert.assertEquals("MD5", result.get("algorithm"));
        Assert.assertEquals("f310c44b87efc0bc0a7aab7096fd36b6",
                result.get("response"));
        Assert.assertEquals("DB85C1A73933A7EB586D10E4BF2924EF",
                result.get("opaque"));
        Assert.assertEquals("MHg3ZjA3ZGMwMTUwMTA6NzI2OToxMzUxNDI3NDgw",
                result.get("cnonce"));
        Assert.assertEquals("00000001", result.get("nc"));
        Assert.assertEquals("auth", result.get("qop"));
    }

    @Test
    public void testBug54060c() throws Exception {
        String header = "Digest username=\"mthornton\", qop=auth";

        StringReader input = new StringReader(header);

        Map<String,String> result = Authorization.parseAuthorizationDigest(input);

        Assert.assertEquals("mthornton", result.get("username"));
        Assert.assertEquals("auth", result.get("qop"));
    }

    @Test
    public void testBug54060d() throws Exception {
        String header = "Digest username=\"mthornton\"," +
                "qop=auth," +
                "cnonce=\"9926cb3c334ede11\"";

        StringReader input = new StringReader(header);

        Map<String,String> result = Authorization.parseAuthorizationDigest(input);

        Assert.assertEquals("mthornton", result.get("username"));
        Assert.assertEquals("auth", result.get("qop"));
        Assert.assertEquals("9926cb3c334ede11", result.get("cnonce"));
    }

    @Test
    public void testEndWithLhex() throws Exception {
        String header = "Digest nc=00000001";

        StringReader input = new StringReader(header);

        Map<String,String> result = Authorization.parseAuthorizationDigest(input);

        Assert.assertEquals("00000001", result.get("nc"));
    }

    @Test
    public void testEndWithLhexReverse() throws Exception {
        String header = "Digest nc=10000000";

        StringReader input = new StringReader(header);

        Map<String,String> result = Authorization.parseAuthorizationDigest(input);

        Assert.assertEquals("10000000", result.get("nc"));
    }

    @Test
    public void testQuotedLhex() throws Exception {
        String header = "Digest nc=\"09abcdef\"";

        StringReader input = new StringReader(header);

        Map<String,String> result = Authorization.parseAuthorizationDigest(input);

        Assert.assertEquals("09abcdef", result.get("nc"));
    }

    @Test
    public void testQuotedLhexReverse() throws Exception {
        String header = "Digest nc=\"fedcba90\"";

        StringReader input = new StringReader(header);

        Map<String,String> result = Authorization.parseAuthorizationDigest(input);

        Assert.assertEquals("fedcba90", result.get("nc"));
    }

    @Test
    public void testLhex() throws Exception {
        String header = "Digest nc=09abcdef";

        StringReader input = new StringReader(header);

        Map<String,String> result = Authorization.parseAuthorizationDigest(input);

        Assert.assertEquals("09abcdef", result.get("nc"));
    }

    @Test
    public void testLhexReverse() throws Exception {
        String header = "Digest nc=fedcba90";

        StringReader input = new StringReader(header);

        Map<String,String> result = Authorization.parseAuthorizationDigest(input);

        Assert.assertEquals("fedcba90", result.get("nc"));
    }

    @Test
    public void testQuotedLhexUppercase() throws Exception {
        String header = "Digest nc=\"00ABCDEF\"";

        StringReader input = new StringReader(header);

        Map<String,String> result = Authorization.parseAuthorizationDigest(input);

        Assert.assertEquals("00abcdef", result.get("nc"));
    }

    @Test
    public void testQuotedLhexUppercaseReverse() throws Exception {
        String header = "Digest nc=\"FEDCBA00\"";

        StringReader input = new StringReader(header);

        Map<String,String> result = Authorization.parseAuthorizationDigest(input);

        Assert.assertEquals("fedcba00", result.get("nc"));
    }

    @Test
    public void testLhexUppercase() throws Exception {
        String header = "Digest nc=00ABCDEF";

        StringReader input = new StringReader(header);

        Map<String,String> result = Authorization.parseAuthorizationDigest(input);

        Assert.assertEquals("00abcdef", result.get("nc"));
    }

    @Test
    public void testLhexUppercaseReverse() throws Exception {
        String header = "Digest nc=FEDCBA00";

        StringReader input = new StringReader(header);

        Map<String,String> result = Authorization.parseAuthorizationDigest(input);

        Assert.assertEquals("fedcba00", result.get("nc"));
    }

    @Test
    public void testUnclosedQuotedLhex() throws Exception {
        String header = "Digest nc=\"00000001";

        StringReader input = new StringReader(header);

        Map<String,String> result = Authorization.parseAuthorizationDigest(input);

        Assert.assertNull(result);
    }

    @Test
    public void testEmptyLhex() throws Exception {
        String header = "Digest nc=";

        StringReader input = new StringReader(header);

        Map<String,String> result = Authorization.parseAuthorizationDigest(input);

        Assert.assertNull(result);
    }

    @Test
    public void testQuotedEmptyLhex() throws Exception {
        String header = "Digest nc=\"\"";

        StringReader input = new StringReader(header);

        Map<String,String> result = Authorization.parseAuthorizationDigest(input);

        Assert.assertNull(result);
    }

    @Test
    public void testUnclosedQuotedString1() throws Exception {
        String header = "Digest username=\"test";

        StringReader input = new StringReader(header);

        Map<String,String> result = Authorization.parseAuthorizationDigest(input);
        Assert.assertNull(result);
    }

    @Test
    public void testUnclosedQuotedString2() throws Exception {
        String header = "Digest username=\"test\\";

        StringReader input = new StringReader(header);

        Map<String,String> result = Authorization.parseAuthorizationDigest(input);
        Assert.assertNull(result);
    }

    @Test
    public void testNonTokenDirective1() throws Exception {
        String header = "Digest user{name=\"test\"";

        StringReader input = new StringReader(header);

        Map<String,String> result = Authorization.parseAuthorizationDigest(input);
        Assert.assertNull(result);
    }

    @Test
    public void testNonTokenDirective2() throws Exception {
        String header = "Digest a=b,{name=test";

        StringReader input = new StringReader(header);

        Map<String,String> result = Authorization.parseAuthorizationDigest(input);
        Assert.assertNull(result);
    }

    @Test
    public void testTokenQop() throws Exception {
        String header = "Digest qop=auth";

        StringReader input = new StringReader(header);

        Map<String,String> result = Authorization.parseAuthorizationDigest(input);
        Assert.assertEquals("auth", result.get("qop"));
    }

    @Test
    public void testQuotedTokenQop() throws Exception {
        String header = "Digest qop=\"auth\"";

        StringReader input = new StringReader(header);

        Map<String,String> result = Authorization.parseAuthorizationDigest(input);
        Assert.assertEquals("auth", result.get("qop"));
    }

    @Test
    public void testEmptyQop() throws Exception {
        String header = "Digest qop=";

        StringReader input = new StringReader(header);

        Map<String,String> result = Authorization.parseAuthorizationDigest(input);
        Assert.assertNull(result);
    }

    @Test
    public void testEmptyQuotedTokenQop() throws Exception {
        String header = "Digest qop=\"\"";

        StringReader input = new StringReader(header);

        Map<String,String> result = Authorization.parseAuthorizationDigest(input);
        Assert.assertNull(result);
    }

    @Test
    public void testNonTokenQop01() throws Exception {
        String header = "Digest qop=au{th";

        StringReader input = new StringReader(header);

        Map<String,String> result = Authorization.parseAuthorizationDigest(input);
        Assert.assertNull(result);
    }

    @Test
    public void testNonTokenQop02() throws Exception {
        String header = "Digest qop=auth{";

        StringReader input = new StringReader(header);

        Map<String,String> result = Authorization.parseAuthorizationDigest(input);
        Assert.assertNull(result);
    }

    @Test
    public void testQuotedNonTokenQop() throws Exception {
        String header = "Digest qop=\"au{th\"";

        StringReader input = new StringReader(header);

        Map<String,String> result = Authorization.parseAuthorizationDigest(input);
        Assert.assertNull(result);
    }

    @Test
    public void testQuotedNonTokenQop2() throws Exception {
        String header = "Digest qop=\"{auth\"";

        StringReader input = new StringReader(header);

        Map<String,String> result = Authorization.parseAuthorizationDigest(input);
        Assert.assertNull(result);
    }

    @Test
    public void testUnclosedQuotedTokenQop() throws Exception {
        String header = "Digest qop=\"auth";

        StringReader input = new StringReader(header);

        Map<String,String> result = Authorization.parseAuthorizationDigest(input);
        Assert.assertNull(result);
    }

    @Test
    public void testWrongCharacterInToken() throws Exception {
        String header = "Digest \u044f";

        StringReader input = new StringReader(header);

        Map<String,String> result = Authorization.parseAuthorizationDigest(input);
        Assert.assertNull(result);
    }

    @Test
    public void testWrongCharacterInToken2() throws Exception {
        String header = "Digest qop=\u044f";

        StringReader input = new StringReader(header);

        Map<String,String> result = Authorization.parseAuthorizationDigest(input);
        Assert.assertNull(result);
    }

    @Test
    public void testWrongCharacterInQuotedToken() throws Exception {
        String header = "Digest qop=\"\u044f\"";

        StringReader input = new StringReader(header);

        Map<String,String> result = Authorization.parseAuthorizationDigest(input);
        Assert.assertNull(result);
    }

    @Test
    public void testWrongCharacterInHex01() throws Exception {
        String header = "Digest nc=\u044f";

        StringReader input = new StringReader(header);

        Map<String,String> result = Authorization.parseAuthorizationDigest(input);
        Assert.assertNull(result);
    }

    @Test
    public void testWrongCharacterInHex02() throws Exception {
        String header = "Digest nc=aaa\u044f";

        StringReader input = new StringReader(header);

        Map<String,String> result = Authorization.parseAuthorizationDigest(input);
        Assert.assertNull(result);
    }

    @Test
    public void testWrongCharacterInHex03() throws Exception {
        String header = "Digest nc=\u044faaa";

        StringReader input = new StringReader(header);

        Map<String,String> result = Authorization.parseAuthorizationDigest(input);
        Assert.assertNull(result);
    }

    @Test
    public void testWrongCharacterInQuotedHex() throws Exception {
        String header = "Digest nc=\"\u044f\"";

        StringReader input = new StringReader(header);

        Map<String,String> result = Authorization.parseAuthorizationDigest(input);
        Assert.assertNull(result);
    }

    @Test
    public void testParseAuthParamA() throws Exception {
        // Test for HttpParser.readTokenOrQuotedString()
        // auth-param = token "=" ( token | quoted-string )
        String header = "Digest a=b";

        StringReader input = new StringReader(header);

        Map<String,String> result = Authorization.parseAuthorizationDigest(input);
        Assert.assertEquals("b", result.get("a"));
    }

    @Test
    public void testParseAuthParamB() throws Exception {
        // Test for HttpParser.readTokenOrQuotedString()
        // auth-param = token "=" ( token | quoted-string )
        String header = "Digest a=\"b\"";

        StringReader input = new StringReader(header);

        Map<String,String> result = Authorization.parseAuthorizationDigest(input);
        Assert.assertEquals("b", result.get("a"));
    }

    @Test
    public void testParseAuthParamBEscaped() throws Exception {
        // Test for HttpParser.readTokenOrQuotedString()
        // auth-param = token "=" ( token | quoted-string )
        String header = "Digest a=\"b\\\"b\"";

        StringReader input = new StringReader(header);

        Map<String,String> result = Authorization.parseAuthorizationDigest(input);
        Assert.assertEquals("b\"b", result.get("a"));
    }

    @Test
    public void testQuotedStringNoQuotes() throws Exception {
        String header = "Digest username=a";

        StringReader input = new StringReader(header);

        Map<String,String> result = Authorization.parseAuthorizationDigest(input);
        Assert.assertNull(result);
    }

    @Test
    public void testNotDigest() throws Exception {
        String header = "SomethingElse a=b";

        StringReader input = new StringReader(header);

        Map<String,String> result = Authorization.parseAuthorizationDigest(input);
        Assert.assertNull(result);
    }

    @Test
    public void testSchemeCaseInsensitive() throws Exception {
        // RFC 9110, section 11.1: the authentication scheme is case-insensitive
        for (String scheme : new String[] { "digest", "DIGEST", "dIgEsT" }) {
            Map<String,String> result =
                    Authorization.parseAuthorizationDigest(new StringReader(scheme + " username=\"a\""));
            Assert.assertNotNull(scheme, result);
            Assert.assertEquals(scheme, "a", result.get("username"));
        }
    }

    @Test
    public void testSchemeMustBeFollowedBySpace() throws Exception {
        Map<String,String> result = Authorization.parseAuthorizationDigest(new StringReader("Digestusername=\"a\""));
        Assert.assertNull(result);
    }

    @Test
    public void testParameterNamesCaseInsensitive() throws Exception {
        // RFC 9110, section 11.2: parameter names are matched case-insensitively
        String header = "Digest Username=\"a\", REALM=\"r\", Nc=00000001, QoP=auth";

        Map<String,String> result = Authorization.parseAuthorizationDigest(new StringReader(header));

        Assert.assertEquals("a", result.get("username"));
        Assert.assertEquals("r", result.get("realm"));
        Assert.assertEquals("00000001", result.get("nc"));
        Assert.assertEquals("auth", result.get("qop"));
    }

    @Test
    public void testEmptyListElements() throws Exception {
        // RFC 9110, section 5.6.1.2: empty list elements must be ignored
        String[] headers = new String[] { "Digest username=\"a\",, realm=\"r\"",
                "Digest username=\"a\", , realm=\"r\"", "Digest ,username=\"a\", realm=\"r\"",
                "Digest username=\"a\", realm=\"r\",,", "Digest , username=\"a\" ,, , realm=\"r\" , " };
        for (String header : headers) {
            Map<String,String> result = Authorization.parseAuthorizationDigest(new StringReader(header));
            Assert.assertNotNull(header, result);
            Assert.assertEquals(header, 2, result.size());
            Assert.assertEquals(header, "a", result.get("username"));
            Assert.assertEquals(header, "r", result.get("realm"));
        }
    }
}
