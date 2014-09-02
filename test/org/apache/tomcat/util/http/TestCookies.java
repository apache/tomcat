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

import javax.servlet.http.Cookie;

import org.junit.Assert;
import org.junit.Test;

import org.apache.tomcat.util.buf.MessageBytes;

public class TestCookies {
    private final Cookie FOO = new Cookie("foo", "bar");
    private final Cookie FOO_EMPTY = new Cookie("foo", "");
    private final Cookie BAR = new Cookie("bar", "rab");
    private final Cookie BAR_EMPTY = new Cookie("bar", "");
    private final Cookie A = new Cookie("a", "b");
    private final Cookie HASH_EMPTY = new Cookie("#", "");

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
        doV1DQuoteInValue(false);
    }

    @Test
    public void v1DQuoteInValueRfc6265() {
        doV1DQuoteInValue(true);
    }

    private void doV1DQuoteInValue(boolean useRfc6265) {
        FOO.setValue("b");
        FOO.setVersion(1);
        A.setVersion(1);
        test(useRfc6265, "$Version=1;foo=\"b\"ar\";a=b", FOO, A); // Incorrectly escaped.
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
        doV1PortIsIgnored(false);
    }

    @Test
    public void v1PortIsIgnoredRfc6265() {
        doV1PortIsIgnored(true);
    }

    private void doV1PortIsIgnored(boolean useRfc6265) {
        FOO.setVersion(1);
        FOO.setDomain("apache.org");
        A.setVersion(1);
        test(useRfc6265, "$Version=1;foo=\"bar\";$Domain=apache.org;$Port=8080;a=b", FOO, A);
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
        doRfc2109Version0(false);
    }

    @Test
    public void rfc2109Version0Rfc6265() {
        doRfc2109Version0(true);
    }

    private void doRfc2109Version0(boolean useRfc6265) {
        // rfc2109 semantically does not allow $Version to be 0 but it is valid syntax
        test(useRfc6265, "$Version=0;foo=bar", FOO);
    }

    private void test(boolean useRfc6265, String header, Cookie... expected) {
        MimeHeaders mimeHeaders = new MimeHeaders();
        Cookies cookies = new Cookies(mimeHeaders);
        cookies.setUseRfc6265(useRfc6265);
        MessageBytes cookieHeaderValue = mimeHeaders.addValue("Cookie");
        byte[] bytes = header.getBytes(StandardCharsets.UTF_8);
        cookieHeaderValue.setBytes(bytes, 0, bytes.length);
        // Calling getCookieCount() triggers parsing
        Assert.assertEquals(expected.length, cookies.getCookieCount());
        for (int i = 0; i < expected.length; i++) {
            Cookie cookie = expected[i];
            ServerCookie actual = cookies.getCookie(i);
            Assert.assertEquals(cookie.getVersion(), actual.getVersion());
            Assert.assertEquals(cookie.getName(), actual.getName().toString());
            Assert.assertEquals(cookie.getValue(), actual.getValue().toString());
            if (cookie.getVersion() == 1) {
                Assert.assertEquals(cookie.getDomain(), actual.getDomain().toString());
                Assert.assertEquals(cookie.getPath(), actual.getPath().toString());
            }
        }
    }
}
