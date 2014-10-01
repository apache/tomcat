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
        doTest(new Cookie("foo", "a=b"),"foo=\"a=b\"; Version=1", "foo=a=b");
    }

    @Test
    public void v0ValueContainsQuote() {
        doTest(new Cookie("foo", "a\"b"),"foo=\"a\\\"b\"; Version=1", null);
    }

    @Test
    public void v0ValueContainsNonV0Separator() {
        doTest(new Cookie("foo", "a()<>@:\\\"/[]?={}b"),
                "foo=\"a()<>@:\\\\\\\"/[]?={}b\"; Version=1", null);
    }

    @Test
    public void v0ValueContainsBackslash() {
        doTest(new Cookie("foo", "a\\b"), "foo=\"a\\\\b\"; Version=1", null);
    }

    @Test
    public void v0ValueContainsBackslashAtEnd() {
        doTest(new Cookie("foo", "a\\"), "foo=\"a\\\\\"; Version=1", null);
    }

    @Test
    public void v0ValueContainsBackslashAndQuote() {
        doTest(new Cookie("foo", "a\"b\\c"), "foo=\"a\\\"b\\\\c\"; Version=1", null);
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
        Assert.assertEquals(expectedLegacy, legacy.generateHeader(cookie));
        if (expectedRfc6265 == null) {
            IllegalArgumentException e = null;
            try {
                rfc6265.generateHeader(cookie);
            } catch (IllegalArgumentException iae) {
                e = iae;
            }
            Assert.assertNotNull("Failed to throw IAE", e);
        } else {
            Assert.assertEquals(expectedRfc6265, rfc6265.generateHeader(cookie));
        }
    }
}
