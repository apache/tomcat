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

public class TestCookies {
    private Cookie FOO = new Cookie("foo", "bar");
    private Cookie BAR = new Cookie("bar", "rab");
    private Cookie A = new Cookie("a", "b");

    @Test
    public void testBasicCookie() {
        test("foo=bar; a=b", FOO, A);
        test("foo=bar;a=b", FOO, A);
        test("foo=bar;a=b;", FOO, A);
        test("foo=bar;a=b; ", FOO, A);
        test("foo=bar;a=b; ;", FOO, A);
    }

    @Test
    public void testNameOnlyAreDropped() {
        test("foo=;a=b; ;", A);
        test("foo;a=b; ;", A);
        test("foo;a=b;bar", A);
        test("foo;a=b;bar;", A);
        test("foo;a=b;bar ", A);
        test("foo;a=b;bar ;", A);

        // Bug 49000
        Cookie fred = new Cookie("fred", "1");
        Cookie jim = new Cookie("jim", "2");
        Cookie george = new Cookie("george", "3");
        test("fred=1; jim=2; bob", fred, jim);
        test("fred=1; jim=2; bob; george=3", fred, jim, george);
        test("fred=1; jim=2; bob=; george=3", fred, jim, george);
        test("fred=1; jim=2; bob=", fred, jim);
    }

    @Test
    public void testQuotedValue() {
        test("foo=bar;a=\"b\"", FOO, A);
        test("foo=bar;a=\"b\";", FOO, A);
    }

    @Test
    public void testEmptyPairs() {
        test("foo;a=b; ;bar", A);
        test("foo;a=b;;bar", A);
        test("foo;a=b; ;;bar=rab", A, BAR);
        test("foo;a=b;; ;bar=rab", A, BAR);
        test("foo;a=b;;#;bar=rab", A, BAR);
        test("foo;a=b;;\\;bar=rab", A, BAR);
    }

    @Test
    public void testSeparatorsInValue() {
        test("a=()<>@:\\\"/[]?={}\t; foo=bar", FOO);
    }


    @Test
    public void v1TokenValue() {
        FOO.setVersion(1);
        A.setVersion(1);
        test("$Version=1; foo=bar;a=b", FOO, A);
        test("$Version=1;foo=bar;a=b; ; ", FOO, A);
    }

    @Test
    public void v1NameOnlyIsDropped() {
        A.setVersion(1);
        test("$Version=1;foo=;a=b; ; ", A);
        test("$Version=1;foo= ;a=b; ; ", A);
        test("$Version=1;foo;a=b; ; ", A);
    }

    @Test
    public void v1QuotedValue() {
        FOO.setVersion(1);
        A.setVersion(1);
        // presumes quotes are removed
        test("$Version=1;foo=\"bar\";a=b; ; ", FOO, A);
    }

    @Test
    public void v1DQuoteInValue() {
        FOO.setValue("b");
        FOO.setVersion(1);
        A.setVersion(1);
        test("$Version=1;foo=\"b\"ar\";a=b", FOO, A); // Incorrectly escaped.
    }

    @Test
    public void v1QuoteInValue() {
        FOO.setValue("b'ar");
        FOO.setVersion(1);
        A.setVersion(1);
        test("$Version=1;foo=b'ar;a=b", FOO, A);
    }


    @Test
    public void v1QuoteInQuotedValue() {
        FOO.setValue("b'ar");
        FOO.setVersion(1);
        A.setVersion(1);
        test("$Version=1;foo=\"b'ar\";a=b", FOO, A);
    }

    @Test
    public void v1EscapedDQuoteInValue() {
        FOO.setValue("b\"ar");
        FOO.setVersion(1);
        A.setVersion(1);
        test("$Version=1;foo=\"b\\\"ar\";a=b", FOO, A); // correctly escaped.
    }

    @Test
    public void v1QuotedValueEndsInBackslash() {
        FOO.setVersion(1);
        test("$Version=1;foo=bar;a=\"b\\\"", FOO);
    }

    @Test
    public void v1MismatchedQuotes() {
        FOO.setVersion(1);
        test("$Version=1;foo=bar;a=\"b\\", FOO);
    }

    @Test
    public void v1SingleQuotesAreValidTokenCharacters() {
        FOO.setVersion(1);
        FOO.setValue("'bar'");
        test("$Version=1; foo='bar'", FOO);
    }

    @Test
    public void v1DomainIsParsed() {
        FOO.setVersion(1);
        FOO.setDomain("apache.org");
        A.setVersion(1);
        A.setDomain("yahoo.com");
        test("$Version=1;foo=\"bar\";$Domain=apache.org;a=b;$Domain=yahoo.com", FOO, A);
    }

    @Test
    public void v1DomainOnlyAffectsPrecedingCookie() {
        FOO.setVersion(1);
        FOO.setDomain("apache.org");
        A.setVersion(1);
        test("$Version=1;foo=\"bar\";$Domain=apache.org;a=b", FOO, A);
    }

    @Test
    public void v1PortIsIgnored() {
        FOO.setVersion(1);
        FOO.setDomain("apache.org");
        A.setVersion(1);
        test("$Version=1;foo=\"bar\";$Domain=apache.org;$Port=8080;a=b", FOO, A);
    }

    @Test
    public void v1PathAffectsPrecedingCookie() {
        FOO.setVersion(1);
        FOO.setPath("/examples");
        A.setVersion(1);
        test("$Version=1;foo=\"bar\";$Path=/examples;a=b; ; ", FOO, A);
    }

    @Test
    public void rfc2109Version0() {
        // rfc2109 semantically does not allow $Version to be 0 but it is valid syntax
        test("$Version=0;foo=bar", FOO);
    }

    private void test(String header, Cookie... expected) {
        Cookies cookies = new Cookies(null);
        byte[] bytes = header.getBytes(StandardCharsets.UTF_8);
        cookies.processCookieHeader(bytes, 0, bytes.length);
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
