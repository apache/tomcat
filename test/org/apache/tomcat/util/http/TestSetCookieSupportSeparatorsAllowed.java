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
import org.junit.Ignore;
import org.junit.Test;

public class TestSetCookieSupportSeparatorsAllowed {

    static {
        System.setProperty("org.apache.tomcat.util.http.ServerCookie.ALLOW_HTTP_SEPARATORS_IN_V0", "true");
        System.setProperty("org.apache.tomcat.util.http.ServerCookie.FWD_SLASH_IS_SEPARATOR", "true");
    }
    @Test
    public void v0simpleCookie() {
        Cookie cookie = new Cookie("foo", "bar");
        Assert.assertEquals("foo=bar", SetCookieSupport.generateHeader(cookie));
    }

    @Test
    public void v0NullValue() {
        Cookie cookie = new Cookie("foo", null);
//        Assert.assertEquals("foo=", SetCookieSupport.generateHeader(cookie));
        Assert.assertEquals("foo=\"\"", SetCookieSupport.generateHeader(cookie));
    }

    @Test
    public void v0QuotedValue() {
        Cookie cookie = new Cookie("foo", "\"bar\"");
        Assert.assertEquals("foo=\"bar\"", SetCookieSupport.generateHeader(cookie));
    }

    @Test
    public void v0ValueContainsSemicolon() {
        Cookie cookie = new Cookie("foo", "a;b");
        // should probably throw IAE?
        Assert.assertEquals("foo=\"a;b\"; Version=1", SetCookieSupport.generateHeader(cookie));
    }

    @Test
    public void v0ValueContainsComma() {
        Cookie cookie = new Cookie("foo", "a,b");
        // should probably throw IAE?
        Assert.assertEquals("foo=\"a,b\"; Version=1", SetCookieSupport.generateHeader(cookie));
    }

    @Test
    public void v0ValueContainsSpace() {
        Cookie cookie = new Cookie("foo", "a b");
        // should probably throw IAE?
        Assert.assertEquals("foo=\"a b\"; Version=1", SetCookieSupport.generateHeader(cookie));
    }

    @Test
    public void v0ValueContainsQuote() {
        Cookie cookie = new Cookie("foo", "a\"b");
        Assert.assertEquals("foo=a\"b", SetCookieSupport.generateHeader(cookie));
    }

    @Test
    public void v0ValueContainsNonV0Separator() {
        Cookie cookie = new Cookie("foo", "a()<>@:\\\"/[]?={}b");
        Assert.assertEquals("foo=a()<>@:\\\"/[]?={}b", SetCookieSupport.generateHeader(cookie));
    }

    @Test
    public void v0ValueContainsBackslash() {
        Cookie cookie = new Cookie("foo", "a\\b");
        Assert.assertEquals("foo=a\\b", SetCookieSupport.generateHeader(cookie));
    }


    @Test
    public void v0ValueContainsBackslashAtEnd() {
        Cookie cookie = new Cookie("foo", "a\\");
        Assert.assertEquals("foo=a\\", SetCookieSupport.generateHeader(cookie));
    }

    @Test
    public void v0ValueContainsBackslashAndQuote() {
        Cookie cookie = new Cookie("foo", "a\"b\\c");
        Assert.assertEquals("foo=a\"b\\c", SetCookieSupport.generateHeader(cookie));
    }

    @Test
    public void v1simpleCookie() {
        Cookie cookie = new Cookie("foo", "bar");
        cookie.setVersion(1);
        Assert.assertEquals("foo=bar; Version=1", SetCookieSupport.generateHeader(cookie));
    }

    @Test
    public void v1NullValue() {
        Cookie cookie = new Cookie("foo", null);
        cookie.setVersion(1);
        // should this throw an IAE?
        Assert.assertEquals("foo=\"\"; Version=1", SetCookieSupport.generateHeader(cookie));
    }

    @Test
    public void v1QuotedValue() {
        Cookie cookie = new Cookie("foo", "\"bar\"");
        cookie.setVersion(1);
        // should this be escaping the quotes rather than passing through?
        Assert.assertEquals("foo=\"bar\"; Version=1", SetCookieSupport.generateHeader(cookie));
    }

    @Test
    public void v1ValueContainsSemicolon() {
        Cookie cookie = new Cookie("foo", "a;b");
        cookie.setVersion(1);
        // should this be throwing IAE rather than adding quotes?
        Assert.assertEquals("foo=\"a;b\"; Version=1", SetCookieSupport.generateHeader(cookie));
    }

    @Test
    public void v1ValueContainsComma() {
        Cookie cookie = new Cookie("foo", "a,b");
        cookie.setVersion(1);
        // should this be throwing IAE rather than adding quotes?
        Assert.assertEquals("foo=\"a,b\"; Version=1", SetCookieSupport.generateHeader(cookie));
    }

    @Test
    public void v1ValueContainsSpace() {
        Cookie cookie = new Cookie("foo", "a b");
        cookie.setVersion(1);
        // should this be throwing IAE rather than adding quotes?
        Assert.assertEquals("foo=\"a b\"; Version=1", SetCookieSupport.generateHeader(cookie));
    }

    @Ignore("bug 55984")
    @Test
    public void v1ValueContainsEquals() {
        Cookie cookie = new Cookie("foo", "a=b");
        cookie.setVersion(1);
        // should this be throwing IAE rather than adding quotes?
        Assert.assertEquals("foo=\"a=b\"; Version=1", SetCookieSupport.generateHeader(cookie));
    }

    @Ignore("bug 55984")
    @Test
    public void v1ValueContainsQuote() {
        Cookie cookie = new Cookie("foo", "a\"b");
        cookie.setVersion(1);
        // should this be throwing IAE rather than adding quotes?
        Assert.assertEquals("foo=\"a\\\"b\"; Version=1", SetCookieSupport.generateHeader(cookie));
    }

    @Ignore("bug 55984")
    @Test
    public void v1ValueContainsNonV0Separator() {
        Cookie cookie = new Cookie("foo", "a()<>@,;:\\\"/[]?={}b");
        cookie.setVersion(1);
        // should this be throwing IAE rather than adding quotes?
        Assert.assertEquals("foo=\"a()<>@,;:\\\\\\\"/[]?={}b\"; Version=1", SetCookieSupport.generateHeader(cookie));
    }

    @Ignore("bug 55984")
    @Test
    public void v1ValueContainsBackslash() {
        Cookie cookie = new Cookie("foo", "a\\b");
        cookie.setVersion(1);
        // should this be throwing IAE rather than adding quotes?
        Assert.assertEquals("foo=\"a\\\\b\"; Version=1", SetCookieSupport.generateHeader(cookie));
    }


    @Ignore("bug 55984")
    @Test
    public void v1ValueContainsBackslashAndQuote() {
        Cookie cookie = new Cookie("foo", "a\"b\\c");
        cookie.setVersion(1);
        // should this be throwing IAE rather than adding quotes?
        Assert.assertEquals("foo=\"a\\\"b\\\\c\"; Version=1", SetCookieSupport.generateHeader(cookie));
    }
}
