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

    @Test
    public void testBasicCookieOld() {
        doTestBasicCookie(false);
    }

    @Test
    public void testBasicCookieRfc6265() {
        doTestBasicCookie(true);
    }

    private void doTestBasicCookie(boolean useRfc6265) {
        test(useRfc6265, "foo=bar; a=b", FOO, A);
        test(useRfc6265, "foo=bar;a=b", FOO, A);
        test(useRfc6265, "foo=bar;a=b;", FOO, A);
        test(useRfc6265, "foo=bar;a=b; ", FOO, A);
        test(useRfc6265, "foo=bar;a=b; ;", FOO, A);
    }

    @Test
    public void testNameOnlyAreDroppedOld() {
        test(false, "foo=;a=b; ;", A);
        test(false, "foo;a=b; ;", A);
        test(false, "foo;a=b;bar", A);
        test(false, "foo;a=b;bar;", A);
        test(false, "foo;a=b;bar ", A);
        test(false, "foo;a=b;bar ;", A);

        // Bug 49000
        Cookie fred = new Cookie("fred", "1");
        Cookie jim = new Cookie("jim", "2");
        Cookie george = new Cookie("george", "3");
        test(false, "fred=1; jim=2; bob", fred, jim);
        test(false, "fred=1; jim=2; bob; george=3", fred, jim, george);
        test(false, "fred=1; jim=2; bob=; george=3", fred, jim, george);
        test(false, "fred=1; jim=2; bob=", fred, jim);
    }

    @Test
    public void testNameOnlyAreDroppedRfc6265() {
        // Name only cookies are not dropped in RFC6265
        test(true, "foo=;a=b; ;", FOO_EMPTY, A);
        test(true, "foo;a=b; ;", FOO_EMPTY, A);
        test(true, "foo;a=b;bar", FOO_EMPTY, A, BAR_EMPTY);
        test(true, "foo;a=b;bar;", FOO_EMPTY, A, BAR_EMPTY);
        test(true, "foo;a=b;bar ", FOO_EMPTY, A, BAR_EMPTY);
        test(true, "foo;a=b;bar ;", FOO_EMPTY, A, BAR_EMPTY);

        // Bug 49000
        Cookie fred = new Cookie("fred", "1");
        Cookie jim = new Cookie("jim", "2");
        Cookie bobEmpty = new Cookie("bob", "");
        Cookie george = new Cookie("george", "3");
        test(true, "fred=1; jim=2; bob", fred, jim, bobEmpty);
        test(true, "fred=1; jim=2; bob; george=3", fred, jim, bobEmpty, george);
        test(true, "fred=1; jim=2; bob=; george=3", fred, jim, bobEmpty, george);
        test(true, "fred=1; jim=2; bob=", fred, jim, bobEmpty);
    }

    @Test
    public void testQuotedValueOld() {
        doTestQuotedValue(false);
    }

    @Test
    public void testQuotedValueRfc6265() {
        doTestQuotedValue(true);
    }

    private void doTestQuotedValue(boolean useRfc6265) {
        test(useRfc6265, "foo=bar;a=\"b\"", FOO, A);
        test(useRfc6265, "foo=bar;a=\"b\";", FOO, A);
    }

    @Test
    public void testEmptyPairsOld() {
        test(false, "foo;a=b; ;bar", A);
        test(false, "foo;a=b;;bar", A);
        test(false, "foo;a=b; ;;bar=rab", A, BAR);
        test(false, "foo;a=b;; ;bar=rab", A, BAR);
        test(false, "foo;a=b;;#;bar=rab", A, BAR);
        test(false, "foo;a=b;;\\;bar=rab", A, BAR);
    }

    @Test
    public void testEmptyPairsRfc6265() {
        test(true, "foo;a=b; ;bar", FOO_EMPTY, A, BAR_EMPTY);
        test(true, "foo;a=b;;bar", FOO_EMPTY, A, BAR_EMPTY);
        test(true, "foo;a=b; ;;bar=rab", FOO_EMPTY, A, BAR);
        test(true, "foo;a=b;; ;bar=rab", FOO_EMPTY, A, BAR);
        test(true, "foo;a=b;;#;bar=rab", FOO_EMPTY, A, HASH_EMPTY, BAR);
        test(true, "foo;a=b;;\\;bar=rab", FOO_EMPTY, A, BAR);
    }

    @Test
    public void testSeparatorsInValueOld() {
        doTestSeparatorsInValue(false);
    }

    @Test
    public void testSeparatorsInValueRfc6265() {
        doTestSeparatorsInValue(true);
    }

    private void doTestSeparatorsInValue(boolean useRfc6265) {
        test(useRfc6265, "a=()<>@:\\\"/[]?={}\t; foo=bar", FOO);
    }


    @Test
    public void v1TokenValueOld() {
        doV1TokenValue(false);
    }

    @Test
    public void v1TokenValueRfc6265() {
        doV1TokenValue(true);
    }

    private void doV1TokenValue(boolean useRfc6265) {
        FOO.setVersion(1);
        A.setVersion(1);
        test(useRfc6265, "$Version=1; foo=bar;a=b", FOO, A);
        test(useRfc6265, "$Version=1;foo=bar;a=b; ; ", FOO, A);
    }

    @Test
    public void v1NameOnlyIsDroppedOld() {
        doV1NameOnlyIsDropped(false);
    }

    @Test
    public void v1NameOnlyIsDroppedRfc6265() {
        doV1NameOnlyIsDropped(true);
    }

    private void doV1NameOnlyIsDropped(boolean useRfc6265) {
        A.setVersion(1);
        test(useRfc6265, "$Version=1;foo=;a=b; ; ", A);
        test(useRfc6265, "$Version=1;foo= ;a=b; ; ", A);
        test(useRfc6265, "$Version=1;foo;a=b; ; ", A);
    }

    @Test
    public void v1QuotedValueOld() {
        doV1QuotedValue(false);
    }

    @Test
    public void v1QuotedValueRfc6265() {
        doV1QuotedValue(true);
    }

    private void doV1QuotedValue(boolean useRfc6265) {
        FOO.setVersion(1);
        A.setVersion(1);
        // presumes quotes are removed
        test(useRfc6265, "$Version=1;foo=\"bar\";a=b; ; ", FOO, A);
    }

    @Test
    public void v1DQuoteInValueOld() {
        FOO.setValue("b");
        FOO.setVersion(1);
        A.setVersion(1);
        test(false, "$Version=1;foo=\"b\"ar\";a=b", FOO, A); // Incorrectly escaped.
    }

    @Test
    public void v1DQuoteInValueRfc6265() {
        A.setVersion(1);
        test(true, "$Version=1;foo=\"b\"ar\";a=b", A); // Incorrectly escaped.
    }

    @Test
    public void v1QuoteInValueOld() {
        doV1QuoteInValue(false);
    }

    @Test
    public void v1QuoteInValueRfc6265() {
        doV1QuoteInValue(true);
    }

    private void doV1QuoteInValue(boolean useRfc6265) {
        FOO.setValue("b'ar");
        FOO.setVersion(1);
        A.setVersion(1);
        test(useRfc6265, "$Version=1;foo=b'ar;a=b", FOO, A);
    }


    @Test
    public void v1QuoteInQuotedValueOld() {
        doV1QuoteInQuotedValue(false);
    }

    @Test
    public void v1QuoteInQuotedValueRfc6265() {
        doV1QuoteInQuotedValue(true);
    }

    private void doV1QuoteInQuotedValue(boolean useRfc6265) {
        FOO.setValue("b'ar");
        FOO.setVersion(1);
        A.setVersion(1);
        test(useRfc6265, "$Version=1;foo=\"b'ar\";a=b", FOO, A);
    }

    @Test
    public void v1EscapedDQuoteInValueOld() {
        doV1EscapedDQuoteInValue(false);
    }

    @Test
    public void v1EscapedDQuoteInValueRfc6265() {
        doV1EscapedDQuoteInValue(true);
    }

    private void doV1EscapedDQuoteInValue(boolean useRfc6265) {
        FOO.setValue("b\"ar");
        FOO.setVersion(1);
        A.setVersion(1);
        test(useRfc6265, "$Version=1;foo=\"b\\\"ar\";a=b", FOO, A); // correctly escaped.
    }

    @Test
    public void v1QuotedValueEndsInBackslashOld() {
        doV1QuotedValueEndsInBackslash(false);
    }

    @Test
    public void v1QuotedValueEndsInBackslashRfc6265() {
        doV1QuotedValueEndsInBackslash(true);
    }

    private void doV1QuotedValueEndsInBackslash(boolean useRfc6265) {
        FOO.setVersion(1);
        test(useRfc6265, "$Version=1;foo=bar;a=\"b\\\"", FOO);
    }

    @Test
    public void v1MismatchedQuotesOld() {
        doV1MismatchedQuotes(false);
    }

    @Test
    public void v1MismatchedQuotesRfc6265() {
        doV1MismatchedQuotes(true);
    }

    private void doV1MismatchedQuotes(boolean useRfc6265) {
        FOO.setVersion(1);
        test(useRfc6265, "$Version=1;foo=bar;a=\"b\\", FOO);
    }

    @Test
    public void v1SingleQuotesAreValidTokenCharactersOld() {
        doV1SingleQuotesAreValidTokenCharacters(false);
    }

    @Test
    public void v1SingleQuotesAreValidTokenCharactersRfc6265() {
        doV1SingleQuotesAreValidTokenCharacters(true);
    }

    private void doV1SingleQuotesAreValidTokenCharacters(boolean useRfc6265) {
        FOO.setVersion(1);
        FOO.setValue("'bar'");
        test(useRfc6265, "$Version=1; foo='bar'", FOO);
    }

    @Test
    public void v1DomainIsParsedOld() {
        doV1DomainIsParsed(false);
    }

    @Test
    public void v1DomainIsParsedRfc6265() {
        doV1DomainIsParsed(true);
    }

    private void doV1DomainIsParsed(boolean useRfc6265) {
        FOO.setVersion(1);
        FOO.setDomain("apache.org");
        A.setVersion(1);
        A.setDomain("yahoo.com");
        test(useRfc6265, "$Version=1;foo=\"bar\";$Domain=apache.org;a=b;$Domain=yahoo.com", FOO, A);
    }

    @Test
    public void v1DomainOnlyAffectsPrecedingCookieOld() {
        doV1DomainOnlyAffectsPrecedingCookie(false);
    }

    @Test
    public void v1DomainOnlyAffectsPrecedingCookieRfc6265() {
        doV1DomainOnlyAffectsPrecedingCookie(true);
    }

    private void doV1DomainOnlyAffectsPrecedingCookie(boolean useRfc6265) {
        FOO.setVersion(1);
        FOO.setDomain("apache.org");
        A.setVersion(1);
        test(useRfc6265, "$Version=1;foo=\"bar\";$Domain=apache.org;a=b", FOO, A);
    }

    @Test
    public void v1PortIsIgnoredOld() {
        FOO.setVersion(1);
        FOO.setDomain("apache.org");
        A.setVersion(1);
        test(false, "$Version=1;foo=\"bar\";$Domain=apache.org;$Port=8080;a=b", FOO, A);
    }

    @Test
    public void v1PortIsIgnoredRfc6265() {
        FOO.setVersion(1);
        FOO.setDomain("apache.org");
        $PORT.setVersion(1);
        A.setVersion(1);
        test(true, "$Version=1;foo=\"bar\";$Domain=apache.org;$Port=8080;a=b", FOO, $PORT, A);
    }

    @Test
    public void v1PathAffectsPrecedingCookieOld() {
        doV1PathAffectsPrecedingCookie(false);
    }

    @Test
    public void v1PathAffectsPrecedingCookieRfc6265() {
        doV1PathAffectsPrecedingCookie(true);
    }

    private void doV1PathAffectsPrecedingCookie(boolean useRfc6265) {
        FOO.setVersion(1);
        FOO.setPath("/examples");
        A.setVersion(1);
        test(useRfc6265, "$Version=1;foo=\"bar\";$Path=/examples;a=b; ; ", FOO, A);
    }

    @Test
    public void rfc2109Version0Old() {
        // rfc2109 semantically does not allow $Version to be 0 but it is valid syntax
        test(false, "$Version=0;foo=bar", FOO);
    }

    @Test
    public void rfc2109Version0Rfc6265() {
        // RFC6265 will parse explicit version 0 using RFC2109
        test(true, "$Version=0;foo=bar", FOO);
    }

    @Test
    public void disallow8bitInName() {
        // Bug 55917
        test(true, "f\u00f6o=bar");
    }

    @Test
    public void disallowControlInName() {
        // Bug 55917
        test(true, "f\010o=bar");
    }

    @Test
    public void disallow8BitControlInName() {
        // Bug 55917
        test(true, "f\210o=bar");
    }

    @Test
    public void allow8BitInV0Value() {
        // Bug 55917
        test(true, "foo=b\u00e1r", FOO_CONTROL);
    }

    @Test
    public void disallow8bitInV1UnquotedValue() {
        // Bug 55917
        test(true, "$Version=1; foo=b\u00e1r");
    }

    @Test
    public void allow8bitInV1QuotedValue() {
        // Bug 55917
        FOO_CONTROL.setVersion(1);
        test(true, "$Version=1; foo=\"b\u00e1r\"", FOO_CONTROL);
    }

    @Test
    public void disallowControlInV0Value() {
        // Bug 55917
        test(true, "foo=b\010r");
    }

    @Test
    public void disallowControlInV1UnquotedValue() {
        // Bug 55917
        test(true, "$Version=1; foo=b\010r");
    }

    @Test
    public void disallowControlInV1QuotedValue() {
        // Bug 55917 / Bug 55918
        test(true, "$Version=1; foo=\"b\010r\"");
    }

    @Test
    public void disallow8BitControlInV1UnquotedValue() {
        // Bug 55917
        test(true, "$Version=1; foo=b\210r");
    }

    @Test
    public void testJsonInV0() {
        // Bug 55921
        test(true, "{\"a\":true, \"b\":false};a=b", A);
    }

    @Test
    public void testJsonInV1() {
        // Bug 55921
        A.setVersion(1);
        test(true, "$Version=1;{\"a\":true, \"b\":false};a=b", A);
    }

    @Test
    public void testSkipSemicolonOrComma() {
        // V1 cookies can also use commas to separate cookies
        FOO.setVersion(1);
        A.setVersion(1);
        test(true, "$Version=1;x\tx=yyy,foo=bar;a=b", FOO, A);
    }

    @Test
    public void testBug60788Rfc6265() {
        doTestBug60788(true);
    }

    @Test
    public void testBug60788Rfc2109() {
        doTestBug60788(false);
    }

    private void doTestBug60788(boolean useRfc6265) {
        Cookie expected = new Cookie("userId", "foo");
        expected.setVersion(1);
        if (useRfc6265) {
            expected.setDomain("\"www.example.org\"");
            expected.setPath("\"/\"");
        } else {
            // The legacy processor removes the quotes for domain and path
            expected.setDomain("www.example.org");
            expected.setPath("/");
        }

        test(useRfc6265, "$Version=\"1\"; userId=\"foo\";$Path=\"/\";$Domain=\"www.example.org\"",
                expected);
    }


    private void test(boolean useRfc6265, String header, Cookie... expected) {
        MimeHeaders mimeHeaders = new MimeHeaders();
        ServerCookies serverCookies = new ServerCookies(4);
        CookieProcessor cookieProcessor;

        if (useRfc6265) {
            cookieProcessor = new Rfc6265CookieProcessor();
        } else {
            cookieProcessor = new LegacyCookieProcessor();
        }
        MessageBytes cookieHeaderValue = mimeHeaders.addValue("Cookie");
        byte[] bytes = header.getBytes(StandardCharsets.UTF_8);
        cookieHeaderValue.setBytes(bytes, 0, bytes.length);
        cookieProcessor.parseCookieHeader(mimeHeaders, serverCookies);
        Assert.assertEquals(expected.length, serverCookies.getCookieCount());
        for (int i = 0; i < expected.length; i++) {
            Cookie cookie = expected[i];
            ServerCookie actual = serverCookies.getCookie(i);
            Assert.assertEquals(cookie.getVersion(), actual.getVersion());
            Assert.assertEquals(cookie.getName(), actual.getName().toString());
            actual.getValue().getByteChunk().setCharset(StandardCharsets.UTF_8);
            Assert.assertEquals(cookie.getValue(),
                    org.apache.tomcat.util.http.parser.Cookie.unescapeCookieValueRfc2109(
                            actual.getValue().toString()));
            if (cookie.getVersion() == 1) {
                Assert.assertEquals(cookie.getDomain(), actual.getDomain().toString());
                Assert.assertEquals(cookie.getPath(), actual.getPath().toString());
            }
        }
    }
}
