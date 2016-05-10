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
package org.apache.tomcat.util.http;

import javax.servlet.http.Cookie;

import org.junit.Assert;
import org.junit.Test;

public class TestCookieProcessorGeneration {

    @Test
    public void v0SimpleCookie() {
        doTest(new Cookie("foo", "bar"), "foo=bar");
    }

    @Test
    public void v0NullValue() {
        doTest(new Cookie("foo", null), "foo=\"\"", "foo=");
    }

    @Test
    public void v0QuotedValue() {
        doTest(new Cookie("foo", "\"bar\""), "foo=\"bar\"");
    }

    @Test
    public void v0ValueContainsSemicolon() {
        doTest(new Cookie("foo", "a;b"), "foo=\"a;b\"; Version=1", null);
    }

    @Test
    public void v0ValueContainsComma() {
        doTest(new Cookie("foo", "a,b"), "foo=\"a,b\"; Version=1", null);
    }

    @Test
    public void v0ValueContainsSpace() {
        doTest(new Cookie("foo", "a b"), "foo=\"a b\"; Version=1", null);
    }

    @Test
    public void v0ValueContainsEquals() {
        Cookie cookie = new Cookie("foo", "a=b");
        doTestDefaults(cookie, "foo=\"a=b\"; Version=1", "foo=a=b");
        doTestAllowSeparators(cookie, "foo=a=b", "foo=a=b");
    }

    @Test
    public void v0ValueContainsQuote() {
        Cookie cookie = new Cookie("foo", "a\"b");
        doTestDefaults(cookie,"foo=\"a\\\"b\"; Version=1", null);
        doTestAllowSeparators(cookie,"foo=a\"b", null);
    }

    @Test
    public void v0ValueContainsNonV0Separator() {
        Cookie cookie = new Cookie("foo", "a()<>@:\\\"/[]?={}b");
        doTestDefaults(cookie,"foo=\"a()<>@:\\\\\\\"/[]?={}b\"; Version=1", null);
        doTestAllowSeparators(cookie,"foo=a()<>@:\\\"/[]?={}b", null);
    }

    @Test
    public void v0ValueContainsBackslash() {
        Cookie cookie = new Cookie("foo", "a\\b");
        doTestDefaults(cookie, "foo=\"a\\\\b\"; Version=1", null);
        doTestAllowSeparators(cookie, "foo=a\\b", null);
    }

    @Test
    public void v0ValueContainsBackslashAtEnd() {
        Cookie cookie = new Cookie("foo", "a\\");
        doTestDefaults(cookie, "foo=\"a\\\\\"; Version=1", null);
        doTestAllowSeparators(cookie, "foo=a\\", null);
    }

    @Test
    public void v0ValueContainsBackslashAndQuote() {
        Cookie cookie = new Cookie("foo", "a\"b\\c");
        doTestDefaults(cookie, "foo=\"a\\\"b\\\\c\"; Version=1", null);
        doTestAllowSeparators(cookie, "foo=a\"b\\c", null);
    }

    @Test
    public void v1simpleCookie() {
        Cookie cookie = new Cookie("foo", "bar");
        cookie.setVersion(1);
        doTest(cookie, "foo=bar; Version=1", "foo=bar");
    }

    @Test
    public void v1NullValue() {
        Cookie cookie = new Cookie("foo", null);
        cookie.setVersion(1);
        doTest(cookie, "foo=\"\"; Version=1", "foo=");
    }

    @Test
    public void v1QuotedValue() {
        Cookie cookie = new Cookie("foo", "\"bar\"");
        cookie.setVersion(1);
        doTest(cookie, "foo=\"bar\"; Version=1", "foo=\"bar\"");
    }

    @Test
    public void v1ValueContainsSemicolon() {
        Cookie cookie = new Cookie("foo", "a;b");
        cookie.setVersion(1);
        doTest(cookie, "foo=\"a;b\"; Version=1", null);
    }

    @Test
    public void v1ValueContainsComma() {
        Cookie cookie = new Cookie("foo", "a,b");
        cookie.setVersion(1);
        doTest(cookie, "foo=\"a,b\"; Version=1", null);
    }

    @Test
    public void v1ValueContainsSpace() {
        Cookie cookie = new Cookie("foo", "a b");
        cookie.setVersion(1);
        doTest(cookie, "foo=\"a b\"; Version=1", null);
    }

    @Test
    public void v1ValueContainsEquals() {
        Cookie cookie = new Cookie("foo", "a=b");
        cookie.setVersion(1);
        doTest(cookie, "foo=\"a=b\"; Version=1", "foo=a=b");
    }

    @Test
    public void v1ValueContainsQuote() {
        Cookie cookie = new Cookie("foo", "a\"b");
        cookie.setVersion(1);
        doTest(cookie, "foo=\"a\\\"b\"; Version=1", null);
    }

    @Test
    public void v1ValueContainsNonV0Separator() {
        Cookie cookie = new Cookie("foo", "a()<>@,;:\\\"/[]?={}b");
        cookie.setVersion(1);
        doTest(cookie, "foo=\"a()<>@,;:\\\\\\\"/[]?={}b\"; Version=1", null);
    }

    @Test
    public void v1ValueContainsBackslash() {
        Cookie cookie = new Cookie("foo", "a\\b");
        cookie.setVersion(1);
        doTest(cookie, "foo=\"a\\\\b\"; Version=1", null);
    }

    @Test
    public void v1ValueContainsBackslashAndQuote() {
        Cookie cookie = new Cookie("foo", "a\"b\\c");
        cookie.setVersion(1);
        doTest(cookie, "foo=\"a\\\"b\\\\c\"; Version=1", null);
    }

    @Test
    public void v1ValueUTF8() {
        String value = "\u2300";
        Cookie cookie = new Cookie("foo", value);
        cookie.setVersion(1);
        doTest(cookie, (String) null, "foo=" + value);
    }

    @Test
    public void v1TestMaxAgePositive() {
        doV1TestMaxAge(100, "foo=bar; Version=1; Max-Age=100", "foo=bar;Max-Age=100");
    }

    @Test
    public void v1TestMaxAgeZero() {
        doV1TestMaxAge(0, "foo=bar; Version=1; Max-Age=0", "foo=bar;Max-Age=0");
    }

    @Test
    public void v1TestMaxAgeNegative() {
        doV1TestMaxAge(-100, "foo=bar; Version=1", "foo=bar");
    }

    @Test
    public void v1TestDomainValid01() {
        doV1TestDomain("example.com", "foo=bar; Version=1; Domain=example.com",
                "foo=bar;domain=example.com");
    }

    @Test
    public void v1TestDomainValid02() {
        doV1TestDomain("exa-mple.com", "foo=bar; Version=1; Domain=exa-mple.com",
                "foo=bar;domain=exa-mple.com");
    }

    @Test
    public void v1TestDomainInvalid01() {
        doV1TestDomain("example.com.", "foo=bar; Version=1; Domain=example.com.", null);
    }

    @Test
    public void v1TestDomainInvalid02() {
        doV1TestDomain("example.com-", "foo=bar; Version=1; Domain=example.com-", null);
    }

    @Test
    public void v1TestDomainInvalid03() {
        doV1TestDomain(".example.com.", "foo=bar; Version=1; Domain=.example.com.", null);
    }

    @Test
    public void v1TestDomainInvalid04() {
        doV1TestDomain("-example.com.", "foo=bar; Version=1; Domain=-example.com.", null);
    }

    @Test
    public void v1TestDomainInvalid05() {
        doV1TestDomain("example..com.", "foo=bar; Version=1; Domain=example..com.", null);
    }

    @Test
    public void v1TestDomainInvalid06() {
        doV1TestDomain("example-.com.", "foo=bar; Version=1; Domain=example-.com.", null);
    }

    @Test
    public void v1TestDomainInvalid07() {
        doV1TestDomain("exam$ple.com.", "foo=bar; Version=1; Domain=exam$ple.com.", null);
    }

    @Test
    public void v1TestPathValid() {
        doV1TestPath("/example", "foo=bar; Version=1; Path=/example",
                "foo=bar;path=/example");
    }

    @Test
    public void v1TestPathInvalid01() {
        doV1TestPath("exa\tmple", "foo=bar; Version=1; Path=\"exa\tmple\"", null);
    }


    private void doTest(Cookie cookie, String expected) {
        doTest(cookie, expected, expected);
    }


    private void doTest(Cookie cookie,
            String expectedLegacy, String expectedRfc6265) {
        doTestDefaults(cookie, expectedLegacy, expectedRfc6265);
        doTestAllowSeparators(cookie, expectedLegacy, expectedRfc6265);
    }


    private void doTestDefaults(Cookie cookie,
            String expectedLegacy, String expectedRfc6265) {
        CookieProcessor legacy = new LegacyCookieProcessor();
        CookieProcessor rfc6265 = new Rfc6265CookieProcessor();
        doTest(cookie, legacy, expectedLegacy, rfc6265, expectedRfc6265);
    }


    private void doTestAllowSeparators(Cookie cookie,
            String expectedLegacy, String expectedRfc6265) {
        LegacyCookieProcessor legacy = new LegacyCookieProcessor();
        legacy.setAllowHttpSepsInV0(true);
        legacy.setForwardSlashIsSeparator(true);
        CookieProcessor rfc6265 = new Rfc6265CookieProcessor();
        doTest(cookie, legacy, expectedLegacy, rfc6265, expectedRfc6265);
    }


    private void doTest(Cookie cookie,
            CookieProcessor legacy, String expectedLegacy,
            CookieProcessor rfc6265, String expectedRfc6265) {
        doTest(cookie, legacy, expectedLegacy);
        doTest(cookie, rfc6265, expectedRfc6265);
    }


    private void doTest(Cookie cookie, CookieProcessor cookieProcessor, String expected) {
        if (expected == null) {
            IllegalArgumentException e = null;
            try {
                cookieProcessor.generateHeader(cookie);
            } catch (IllegalArgumentException iae) {
                e = iae;
            }
            Assert.assertNotNull("Failed to throw IAE", e);
        } else {
            Assert.assertEquals(expected, cookieProcessor.generateHeader(cookie));
        }
    }


    private void doV1TestMaxAge(int age, String expectedLegacy, String expectedRfc6265) {
        LegacyCookieProcessor legacy = new LegacyCookieProcessor();
        legacy.setAlwaysAddExpires(false);
        Cookie cookie = new Cookie("foo", "bar");
        cookie.setVersion(1);
        cookie.setMaxAge(age);
        doTest(cookie, legacy, expectedLegacy, new Rfc6265CookieProcessor(), expectedRfc6265);
    }


    private void doV1TestDomain(String domain, String expectedLegacy, String expectedRfc6265) {
        LegacyCookieProcessor legacy = new LegacyCookieProcessor();
        legacy.setAlwaysAddExpires(false);
        Cookie cookie = new Cookie("foo", "bar");
        cookie.setVersion(1);
        cookie.setDomain(domain);
        doTest(cookie, legacy, expectedLegacy, new Rfc6265CookieProcessor(), expectedRfc6265);
    }


    private void doV1TestPath(String path, String expectedLegacy, String expectedRfc6265) {
        LegacyCookieProcessor legacy = new LegacyCookieProcessor();
        legacy.setAlwaysAddExpires(false);
        Cookie cookie = new Cookie("foo", "bar");
        cookie.setVersion(1);
        cookie.setPath(path);
        doTest(cookie, legacy, expectedLegacy, new Rfc6265CookieProcessor(), expectedRfc6265);
    }
}
