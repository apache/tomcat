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

import jakarta.servlet.http.Cookie;

import org.junit.Assert;
import org.junit.Test;

public class TestCookieProcessorGeneration {

    @Test
    public void simpleCookie() {
        doTest(new Cookie("foo", "bar"), "foo=bar");
    }

    @Test
    public void nullValue() {
        doTest(new Cookie("foo", null), "foo=");
    }

    @Test
    public void quotedValue() {
        doTest(new Cookie("foo", "\"bar\""), "foo=\"bar\"");
    }

    @Test
    public void valueContainsSemicolon() {
        doTest(new Cookie("foo", "a;b"), null);
    }

    @Test
    public void valueContainsComma() {
        doTest(new Cookie("foo", "a,b"), null);
    }

    @Test
    public void valueContainsSpace() {
        doTest(new Cookie("foo", "a b"), null);
    }

    @Test
    public void valueContainsEquals() {
        doTest(new Cookie("foo", "a=b"), "foo=a=b");
    }

    @Test
    public void valueContainsQuote() {
        Cookie cookie = new Cookie("foo", "a\"b");
        doTest(cookie, null);
    }

    @Test
    public void valueContainsNonV0Separator() {
        Cookie cookie = new Cookie("foo", "a()<>@:\\\"/[]?={}b");
        doTest(cookie, null);
    }

    @Test
    public void valueContainsBackslash() {
        Cookie cookie = new Cookie("foo", "a\\b");
        doTest(cookie, null);
    }

    @Test
    public void valueContainsBackslashAtEnd() {
        Cookie cookie = new Cookie("foo", "a\\");
        doTest(cookie, null);
    }

    @Test
    public void valueContainsBackslashAndQuote() {
        Cookie cookie = new Cookie("foo", "a\"b\\c");
        doTest(cookie, null);
    }

    @Test
    public void valueUTF8() {
        String value = "\u2300";
        Cookie cookie = new Cookie("foo", value);
        doTest(cookie, "foo=" + value);
    }

    @Test
    public void testMaxAgePositive() {
        doTestMaxAge(100, "foo=bar; Max-Age=100");
    }

    @Test
    public void testMaxAgeZero() {
        doTestMaxAge(0, "foo=bar; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:10 GMT");
    }

    @Test
    public void testMaxAgeNegative() {
        doTestMaxAge(-100, "foo=bar");
    }

    @Test
    public void testDomainValid01() {
        doTestDomain("example.com", "foo=bar; Domain=example.com");
    }

    @Test
    public void testDomainValid02() {
        doTestDomain("exa-mple.com", "foo=bar; Domain=exa-mple.com");
    }

    @Test
    public void testDomainInvalid01() {
        doTestDomain("example.com.", null);
    }

    @Test
    public void testDomainInvalid02() {
        doTestDomain("example.com-", null);
    }

    @Test
    public void testDomainInvalid03() {
        doTestDomain(".example.com.", null);
    }

    @Test
    public void testDomainInvalid04() {
        doTestDomain("-example.com.", null);
    }

    @Test
    public void testDomainInvalid05() {
        doTestDomain("example..com.", null);
    }

    @Test
    public void testDomainInvalid06() {
        doTestDomain("example-.com.", null);
    }

    @Test
    public void testDomainInvalid07() {
        doTestDomain("exam$ple.com.", null);
    }

    @Test
    public void testPathValid() {
        doTestPath("/example", "foo=bar; Path=/example");
    }

    @Test
    public void testPathInvalid01() {
        doTestPath("exa\tmple", null);
    }

    @Test
    public void testSameSiteCookies() {
        Rfc6265CookieProcessor rfc6265 = new Rfc6265CookieProcessor();

        Cookie cookie = new Cookie("foo", "bar");

        Assert.assertEquals("foo=bar", rfc6265.generateHeader(cookie, null));

        rfc6265.setSameSiteCookies("unset");

        Assert.assertEquals("foo=bar", rfc6265.generateHeader(cookie, null));

        rfc6265.setSameSiteCookies("none");

        Assert.assertEquals("foo=bar; SameSite=None", rfc6265.generateHeader(cookie, null));

        rfc6265.setSameSiteCookies("lax");

        Assert.assertEquals("foo=bar; SameSite=Lax", rfc6265.generateHeader(cookie, null));

        rfc6265.setSameSiteCookies("strict");

        Assert.assertEquals("foo=bar; SameSite=Strict", rfc6265.generateHeader(cookie, null));

        cookie.setSecure(true);
        cookie.setHttpOnly(true);

        rfc6265.setSameSiteCookies("unset");

        Assert.assertEquals("foo=bar; Secure; HttpOnly", rfc6265.generateHeader(cookie, null));

        rfc6265.setSameSiteCookies("none");

        Assert.assertEquals("foo=bar; Secure; HttpOnly; SameSite=None", rfc6265.generateHeader(cookie, null));

        rfc6265.setSameSiteCookies("lax");

        Assert.assertEquals("foo=bar; Secure; HttpOnly; SameSite=Lax", rfc6265.generateHeader(cookie, null));

        rfc6265.setSameSiteCookies("strict");

        Assert.assertEquals("foo=bar; Secure; HttpOnly; SameSite=Strict", rfc6265.generateHeader(cookie, null));
    }

    private void doTest(Cookie cookie, String expectedRfc6265) {
        CookieProcessor rfc6265 = new Rfc6265CookieProcessor();
        doTest(cookie, rfc6265, expectedRfc6265);
    }


    private void doTest(Cookie cookie, CookieProcessor cookieProcessor, String expected) {
        if (expected == null) {
            IllegalArgumentException e = null;
            try {
                cookieProcessor.generateHeader(cookie, null);
            } catch (IllegalArgumentException iae) {
                e = iae;
            }
            Assert.assertNotNull("Failed to throw IAE", e);
        } else {
            if (cookieProcessor instanceof Rfc6265CookieProcessor &&
                    cookie.getMaxAge() > 0) {
                // Expires attribute will depend on time cookie is generated so
                // use a modified test
                Assert.assertTrue(cookieProcessor.generateHeader(cookie, null).startsWith(expected));
            } else {
                Assert.assertEquals(expected, cookieProcessor.generateHeader(cookie, null));
            }
        }
    }


    private void doTestMaxAge(int age, String expectedRfc6265) {
        Cookie cookie = new Cookie("foo", "bar");
        cookie.setMaxAge(age);
        doTest(cookie, new Rfc6265CookieProcessor(), expectedRfc6265);
    }


    private void doTestDomain(String domain, String expectedRfc6265) {
        Cookie cookie = new Cookie("foo", "bar");
        cookie.setDomain(domain);
        doTest(cookie, new Rfc6265CookieProcessor(), expectedRfc6265);
    }


    private void doTestPath(String path, String expectedRfc6265) {
        Cookie cookie = new Cookie("foo", "bar");
        cookie.setPath(path);
        doTest(cookie, new Rfc6265CookieProcessor(), expectedRfc6265);
    }
}
