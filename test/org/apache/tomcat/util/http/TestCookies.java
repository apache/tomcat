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
package org.apache.tomcat.util.http;

import java.nio.charset.StandardCharsets;

import jakarta.servlet.http.Cookie;

import org.junit.Assert;
import org.junit.Test;

import org.apache.tomcat.util.buf.MessageBytes;

public class TestCookies {
    private final Cookie FOO = new Cookie("foo", "bar");
    private final Cookie FOO_EMPTY = new Cookie("foo", "");
    private final Cookie FOO_CONTROL = new Cookie("foo", "b\u00e1r");
    private final Cookie BAR = new Cookie("bar", "rab");
    private final Cookie BAR_EMPTY = new Cookie("bar", "");
    private final Cookie A = new Cookie("a", "b");
    private final Cookie HASH_EMPTY = new Cookie("#", "");
    private final Cookie $PORT = new Cookie("$Port", "8080");
    // RFC 2109 attributes are generally interpreted as additional cookies by
    // RFC 6265
    private final Cookie $VERSION_0 = new Cookie("$Version", "0");
    private final Cookie $VERSION_1 = new Cookie("$Version", "1");
    private final Cookie $DOMAIN_ASF = new Cookie("$Domain", "apache.org");
    private final Cookie $DOMAIN_YAHOO = new Cookie("$Domain", "yahoo.com");
    private final Cookie $PATH = new Cookie("$Path", "/examples");

    @Test
    public void testBasicCookieRfc6265() {
        test("foo=bar; a=b", FOO, A);
        test("foo=bar;a=b", FOO, A);
        test("foo=bar;a=b;", FOO, A);
        test("foo=bar;a=b; ", FOO, A);
        test("foo=bar;a=b; ;", FOO, A);
    }

    @Test
    public void testNameOnlyAreDroppedRfc6265() {
        // Name only cookies are not dropped in RFC6265
        test("foo=;a=b; ;", FOO_EMPTY, A);
        test("foo;a=b; ;", FOO_EMPTY, A);
        test("foo;a=b;bar", FOO_EMPTY, A, BAR_EMPTY);
        test("foo;a=b;bar;", FOO_EMPTY, A, BAR_EMPTY);
        test("foo;a=b;bar ", FOO_EMPTY, A, BAR_EMPTY);
        test("foo;a=b;bar ;", FOO_EMPTY, A, BAR_EMPTY);

        // Bug 49000
        Cookie fred = new Cookie("fred", "1");
        Cookie jim = new Cookie("jim", "2");
        Cookie bobEmpty = new Cookie("bob", "");
        Cookie george = new Cookie("george", "3");
        test("fred=1; jim=2; bob", fred, jim, bobEmpty);
        test("fred=1; jim=2; bob; george=3", fred, jim, bobEmpty, george);
        test("fred=1; jim=2; bob=; george=3", fred, jim, bobEmpty, george);
        test("fred=1; jim=2; bob=", fred, jim, bobEmpty);
    }

    @Test
    public void testQuotedValueRfc6265() {
        test("foo=bar;a=\"b\"", FOO, A);
        test("foo=bar;a=\"b\";", FOO, A);
    }

    @Test
    public void testEmptyPairsRfc6265() {
        test("foo;a=b; ;bar", FOO_EMPTY, A, BAR_EMPTY);
        test("foo;a=b;;bar", FOO_EMPTY, A, BAR_EMPTY);
        test("foo;a=b; ;;bar=rab", FOO_EMPTY, A, BAR);
        test("foo;a=b;; ;bar=rab", FOO_EMPTY, A, BAR);
        test("foo;a=b;;#;bar=rab", FOO_EMPTY, A, HASH_EMPTY, BAR);
        test("foo;a=b;;\\;bar=rab", FOO_EMPTY, A, BAR);
    }

    @Test
    public void testSeparatorsInValueRfc6265() {
        test("a=()<>@:\\\"/[]?={}\t; foo=bar", FOO);
    }


    @Test
    public void v1TokenValueRfc6265() {
        test("$Version=1; foo=bar;a=b", $VERSION_1, FOO, A);
        test("$Version=1;foo=bar;a=b; ; ", $VERSION_1, FOO, A);
    }

    @Test
    public void v1NameOnlyRfc6265() {
        test("$Version=1;foo=;a=b; ; ", $VERSION_1, FOO_EMPTY, A);
        test("$Version=1;foo= ;a=b; ; ", $VERSION_1, FOO_EMPTY, A);
        test("$Version=1;foo;a=b; ; ", $VERSION_1, FOO_EMPTY, A);
    }

    @Test
    public void v1QuotedValueRfc6265() {
        test("$Version=1;foo=\"bar\";a=b; ; ", $VERSION_1, FOO, A);
    }

    @Test
    public void v1DQuoteInValueRfc6265() {
        test("$Version=1;foo=\"b\"ar\";a=b", $VERSION_1, A); // Incorrectly escaped.
    }

    @Test
    public void v1QuoteInValueRfc6265() {
        FOO.setValue("b'ar");
        test("$Version=1;foo=b'ar;a=b", $VERSION_1, FOO, A);
    }


    @Test
    public void v1QuoteInQuotedValueRfc6265() {
        FOO.setValue("b'ar");
        test("$Version=1;foo=\"b'ar\";a=b", $VERSION_1, FOO, A);
    }

    @Test
    public void v1EscapedDQuoteInValueRfc6265() {
        // RFC 2109 considers the 2nd cookie to be correctly escaped.
        // RFC 6265 considers the 2nd cookie to be invalid
        test("$Version=1;foo=\"b\\\"ar\";a=b", $VERSION_1,  A);
    }

    @Test
    public void v1QuotedValueEndsInBackslashRfc6265() {
        test("$Version=1;foo=bar;a=\"b\\\"", $VERSION_1, FOO);
    }

    @Test
    public void v1MismatchedQuotesRfc6265() {
        test("$Version=1;foo=bar;a=\"b\\", $VERSION_1, FOO);
    }

    @Test
    public void v1SingleQuotesAreValidTokenCharactersRfc6265() {
        FOO.setValue("'bar'");
        test("$Version=1; foo='bar'", $VERSION_1, FOO);
    }

    @Test
    public void v1DomainIsParsedRfc6265() {
        FOO.setDomain("apache.org");
        A.setDomain("yahoo.com");
        test("$Version=1;foo=\"bar\";$Domain=apache.org;a=b;$Domain=yahoo.com",
                $VERSION_1, FOO, $DOMAIN_ASF, A, $DOMAIN_YAHOO);
    }

    @Test
    public void v1DomainOnlyAffectsPrecedingCookieRfc6265() {
        FOO.setDomain("apache.org");
        test("$Version=1;foo=\"bar\";$Domain=apache.org;a=b", $VERSION_1, FOO, $DOMAIN_ASF, A);
    }

    @Test
    public void v1PortIsIgnoredRfc6265() {
        FOO.setDomain("apache.org");
        test("$Version=1;foo=\"bar\";$Domain=apache.org;$Port=8080;a=b", $VERSION_1, FOO, $DOMAIN_ASF, $PORT, A);
    }

    @Test
    public void v1PathAffectsPrecedingCookieRfc6265() {
        FOO.setPath("/examples");
        test("$Version=1;foo=\"bar\";$Path=/examples;a=b; ; ", $VERSION_1, FOO, $PATH, A);
    }

    @Test
    public void rfc2109Version0Rfc6265() {
        test("$Version=0;foo=bar", $VERSION_0, FOO);
    }

    @Test
    public void disallow8bitInName() {
        // Bug 55917
        test("f\u00f6o=bar");
    }

    @Test
    public void disallowControlInName() {
        // Bug 55917
        test("f\010o=bar");
    }

    @Test
    public void disallow8BitControlInName() {
        // Bug 55917
        test("f\210o=bar");
    }

    @Test
    public void allow8BitInV0Value() {
        // Bug 55917
        test("foo=b\u00e1r", FOO_CONTROL);
    }

    @Test
    public void eightBitInV1UnquotedValue() {
        // Bug 55917
        // RFC 6265 considers this valid UTF-8
        FOO.setValue("b\u00e1r");
        test("$Version=1; foo=b\u00e1r", $VERSION_1, FOO);
    }

    @Test
    public void allow8bitInV1QuotedValue() {
        // Bug 55917
        test("$Version=1; foo=\"b\u00e1r\"", $VERSION_1, FOO_CONTROL);
    }

    @Test
    public void disallowControlInV0Value() {
        // Bug 55917
        test("foo=b\010r");
    }

    @Test
    public void disallowControlInV1UnquotedValue() {
        // Bug 55917
        test("$Version=1; foo=b\010r", $VERSION_1);
    }

    @Test
    public void disallowControlInV1QuotedValue() {
        // Bug 55917 / Bug 55918
        test("$Version=1; foo=\"b\u0008r\"", $VERSION_1);
    }

    @Test
    public void eightBitControlInV1UnquotedValue() {
        // Bug 55917
        // RFC 6265 considers this to be a valid UTF-8 value
        test("$Version=1; foo=b\u0088r", $VERSION_1, new Cookie("foo", "b\u0088r"));
    }

    @Test
    public void testJsonInV0() {
        // Bug 55921
        test("{\"a\":true, \"b\":false};a=b", A);
    }

    @Test
    public void testJsonInV1() {
        // Bug 55921
        test("$Version=1;{\"a\":true, \"b\":false};a=b", $VERSION_1, A);
    }

    @Test
    public void testSkipSemicolonOrComma() {
        // RFC 2109 cookies can also use commas to separate cookies
        // RFC 6265 considers the second cookie invalid and skips it
        test("$Version=1;x\tx=yyy,foo=bar;a=b", $VERSION_1, A);
    }

    @Test
    public void testBug60788Rfc6265() {
        Cookie c1 = new Cookie("userId", "foo");
        Cookie c2 = new Cookie("$Path", "/");
        Cookie c3 = new Cookie("$Domain", "www.example.org");

        test("$Version=\"1\"; userId=\"foo\";$Path=\"/\";$Domain=\"www.example.org\"",
                $VERSION_1, c1, c2, c3);
    }


    private void test(String header, Cookie... expected) {
        MimeHeaders mimeHeaders = new MimeHeaders();
        ServerCookies serverCookies = new ServerCookies(4);
        CookieProcessor cookieProcessor = new Rfc6265CookieProcessor();
        MessageBytes cookieHeaderValue = mimeHeaders.addValue("Cookie");
        byte[] bytes = header.getBytes(StandardCharsets.UTF_8);
        cookieHeaderValue.setBytes(bytes, 0, bytes.length);
        cookieProcessor.parseCookieHeader(mimeHeaders, serverCookies);
        Assert.assertEquals(expected.length, serverCookies.getCookieCount());
        for (int i = 0; i < expected.length; i++) {
            Cookie cookie = expected[i];
            ServerCookie actual = serverCookies.getCookie(i);
            Assert.assertEquals(cookie.getName(), actual.getName().toString());
            actual.getValue().getByteChunk().setCharset(StandardCharsets.UTF_8);
            Assert.assertEquals(cookie.getValue(), actual.getValue().toString());
        }
    }
}
