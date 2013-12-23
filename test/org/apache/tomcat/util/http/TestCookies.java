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
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

public class TestCookies {
    private Cookies cookies;

    @Before
    public void init() {
        this.cookies = new Cookies(null);
    }

    @Test
    public void skipJsonInV0Value() {
        process("bad={\"v\":1,\"x\":2}; a=b");
        expect(makeCookie("a", "b", 0));
    }

    @Test(expected = IllegalArgumentException.class)
    public void disallow8bitInName() {
        process("f\u00f6o=bar");
    }

    @Test(expected = IllegalArgumentException.class)
    public void disallowControlInName() {
        process("f\010o=bar");
    }

    @Test(expected = IllegalArgumentException.class)
    public void disallow8BitControlInName() {
        process("f\210o=bar");
    }

    @Test
    public void allow8BitInV0Value() {
        process("foo=b\u00e1r");
        expect(makeCookie("foo", "b\u00e1r", 0));
    }

    @Test(expected = IllegalArgumentException.class)
    public void disallow8bitInV1UnquotedValue() {
        process("$Version=1; foo=b\u00e1r");
    }

    @Test
    public void allow8bitInV1QuotedValue() {
        process("$Version=1; foo=\"b\u00e1r\"");
        expect(makeCookie("foo", "b\u00e1r", 1));
    }

    @Test(expected = IllegalArgumentException.class)
    public void disallowControlInV0Value() {
        process("foo=b\010r");
    }

    @Test(expected = IllegalArgumentException.class)
    public void disallow8BitControlInV0Value() {
        process("foo=b\210r");
    }

    @Test(expected = IllegalArgumentException.class)
    public void disallowControlInV1UnquotedValue() {
        process("$Version=1; foo=b\010r");
    }

    @Ignore
    @Test(expected = IllegalArgumentException.class)
    public void disallowControlInV1QuotedValue() {
        process("$Version=1; foo=\"b\010r\"");
    }

    @Test(expected = IllegalArgumentException.class)
    public void disallow8BitControlInV1UnquotedValue() {
        process("$Version=1; foo=b\210r");
    }

    @Ignore
    @Test
    public void allow8BitControlInV1QuotedValue() {
        process("$Version=1; foo=\"b\210r\"");
        expect(makeCookie("foo", "b\210r", 1));
    }

    private void process(String header) {
        byte[] bytes = header.getBytes(StandardCharsets.ISO_8859_1);
        cookies.processCookieHeader(bytes, 0, bytes.length);
    }

    private void expect(Cookie... expected) {
        Assert.assertEquals(expected.length, cookies.getCookieCount());
        for (int i = 0; i < expected.length; i++) {
            ServerCookie actual = cookies.getCookie(i);
            Assert.assertEquals(expected[i].getName(), actual.getName().toString());
            Assert.assertEquals(expected[i].getValue(), actual.getValue().toString());
        }
    }

    private static Cookie makeCookie(String name, String value, int version) {
        Cookie cookie = new Cookie(name, value);
        cookie.setVersion(version);
        return cookie;
    }

    @Test
    public void testCookies() throws Exception {
        test("foo=bar; a=b", "foo", "bar", "a", "b");
        test("foo=bar;a=b", "foo", "bar", "a", "b");
        test("foo=bar;a=b;", "foo", "bar", "a", "b");
        test("foo=bar;a=b; ", "foo", "bar", "a", "b");
        test("foo=bar;a=b; ;", "foo", "bar", "a", "b");
        test("foo=;a=b; ;",  "a", "b");
        test("foo;a=b; ;", "a", "b");
        // v1
        test("$Version=1; foo=bar;a=b", "foo", "bar", "a", "b");

        // OK
        test("$Version=1;foo=bar;a=b; ; ",  "foo", "bar", "a", "b");
        test("$Version=1;foo=;a=b; ; ",  "a", "b");
        test("$Version=1;foo= ;a=b; ; ",  "a", "b");
        test("$Version=1;foo;a=b; ; ", "a", "b");
        test("$Version=1;foo=\"bar\";a=b; ; ", "foo", "bar", "a", "b");

        test("$Version=1;foo=\"bar\";$Domain=apache.org;a=b", "foo", "bar", "a", "b");
        test("$Version=1;foo=\"bar\";$Domain=apache.org;a=b;$Domain=yahoo.com", "foo", "bar", "a", "b");
        // rfc2965
        test("$Version=1;foo=\"bar\";$Domain=apache.org;$Port=8080;a=b", "foo", "bar", "a", "b");

        // make sure these never split into two cookies - JVK
        test("$Version=1;foo=\"b\"ar\";$Domain=apache.org;$Port=8080;a=b",  "foo", "b", "a", "b"); // Incorrectly escaped.
        test("$Version=1;foo=\"b\\\"ar\";$Domain=apache.org;$Port=8080;a=b", "foo", "b\"ar", "a", "b"); // correctly escaped.
        test("$Version=1;foo=\"b'ar\";$Domain=apache.org;$Port=8080;a=b", "foo", "b'ar", "a", "b");
        // ba'r is OK - ' is not a separator
        test("$Version=1;foo=b'ar;$Domain=apache.org;$Port=8080;a=b", "foo", "b'ar", "a", "b");

        // Ends in quoted value
        test("foo=bar;a=\"b\"",  "foo", "bar", "a", "b");
        test("foo=bar;a=\"b\";",  "foo", "bar", "a", "b");

        // Last character is an escape character
        test("$Version=1;foo=b'ar;$Domain=\"apache.org\";$Port=8080;a=\"b\\\"", "foo", "b'ar");
        test("$Version=1;foo=b'ar;$Domain=\"apache.org\";$Port=8080;a=\"b\\",  "foo", "b'ar");

        // A token cannot be quoted with ' chars - they should be treated as part of the value
        test("$Version=\"1\"; foo='bar'; $Path=/path; $Domain=\"localhost\"", "foo", "'bar'");

        // wrong, path should not have '/' JVK
        test("$Version=1;foo=\"bar\";$Path=/examples;a=b; ; ", "foo", "bar", "a", "b");

        // wrong
        test("$Version=1;foo=\"bar\";$Domain=apache.org;$Port=8080;a=b", "foo", "bar", "a", "b");

        // Test name-only at the end of the header
        test("foo;a=b;bar", "a", "b");
        test("foo;a=b;bar;", "a", "b");
        test("foo;a=b;bar ", "a", "b");
        test("foo;a=b;bar ;", "a", "b");

        // Multiple delimiters next to each other

        // BUG -- the ' ' needs to be skipped.
        test("foo;a=b; ;bar", "a", "b");
        // BUG -- ';' needs skipping
        test("foo;a=b;;bar", "a", "b");
        test("foo;a=b; ;;bar=rab", "a", "b", "bar", "rab");
        // These pass currently
        test("foo;a=b;; ;bar=rab", "a", "b", "bar", "rab");

        // '#' is a valid cookie name (not a separator)
        test("foo;a=b;;#;bar=rab","a", "b", "bar", "rab");


        test("foo;a=b;;\\;bar=rab", "a", "b", "bar", "rab");

        // Try all the separators of version1 in version0 cookie.
        // Won't work we only parse version1 cookie result 1 cookie.
        test("a=()<>@:\\\"/[]?={}\t; foo=bar", "foo", "bar");

        // Test the version.
        test("$Version=1;foo=bar", 1);
        test("$Version=0;foo=bar", 0);
    }

    @Test
    public void testNameOnlyCookies() throws Exception {
        // Bug 49000
        test("fred=1; jim=2; bob", "fred", "1", "jim", "2");
        test("fred=1; jim=2; bob; george=3", "fred", "1", "jim", "2",
                "george", "3");
        test("fred=1; jim=2; bob=; george=3", "fred", "1", "jim", "2",
                "george", "3");
        test("fred=1; jim=2; bob=", "fred", "1", "jim", "2");
    }


    public static void test( String s, int val ) throws Exception {
        System.out.println("Processing [" + s + "]");
        Cookies cs=new Cookies(null);
        cs.processCookieHeader( s.getBytes(), 0, s.length());
        int num = cs.getCookieCount();
        if (num != 1)
          throw new Exception("wrong number of cookies " + num);
        ServerCookie co = cs.getCookie(0);
        System.out.println("One Cookie: " + co);
        if (co.getVersion() != val)
          throw new Exception("wrong version " + co.getVersion() + " != " + val);
    }
    public static void test( String s ) throws Exception {
        System.out.println("Processing [" + s + "]");
        Cookies cs=new Cookies(null);
        cs.processCookieHeader( s.getBytes(), 0, s.length());

        int num = cs.getCookieCount();
        for( int i=0; i< num ; i++ ) {
            System.out.println("Cookie: " + cs.getCookie( i ));
        }
        if (num != 0)
          throw new Exception("wrong number of cookies " + num);
    }
    public static void test( String s, String name, String val ) throws Exception {
        System.out.println("Processing [" + s + "]");
        Cookies cs=new Cookies(null);
        cs.processCookieHeader( s.getBytes(), 0, s.length());

        int num = cs.getCookieCount();
        if (num != 1)
          throw new Exception("wrong number of cookies " + num);
        ServerCookie co = cs.getCookie(0);
        System.out.println("One Cookie: " + co);
        String coname = co.getName().toString();
        String coval  = co.getValue().toString();
        if ( ! name.equals(coname))
          throw new Exception("wrong name " + coname + " != " + name);
        if ( ! val.equals(coval))
          throw new Exception("wrong value " + coval + " != " + val);
    }
    public static void test( String s, String name, String val, String name2, String val2 ) throws Exception {
        System.out.println("Processing [" + s + "]");
        Cookies cs=new Cookies(null);
        cs.processCookieHeader( s.getBytes(), 0, s.length());

        int num = cs.getCookieCount();
        if (num != 2)
          throw new Exception("wrong number of cookies " + num);
        ServerCookie co = cs.getCookie(0);
        System.out.println("1 - Cookie: " + co);
        ServerCookie co2 = cs.getCookie(1);
        System.out.println("2 - Cookie: " + co2);

        String coname = co.getName().toString();
        String coval  = co.getValue().toString();
        if ( ! name.equals(coname))
          throw new Exception("1 - wrong name " + coname + " != " + name);
        if ( ! val.equals(coval))
          throw new Exception("1 - wrong value " + coval + " != " + val);

        String coname2 = co2.getName().toString();
        String coval2  = co2.getValue().toString();
        if ( ! name2.equals(coname2))
          throw new Exception("2 - wrong name " + coname2 + " != " + name2);
        if ( ! val2.equals(coval2))
          throw new Exception("2 - wrong value " + coval2 + " != " + val2);
    }
    public static void test( String s, String name, String val, String name2,
                             String val2, String name3, String val3 ) throws Exception {
        System.out.println("Processing [" + s + "]");
        Cookies cs=new Cookies(null);
        cs.processCookieHeader( s.getBytes(), 0, s.length());

        int num = cs.getCookieCount();
        if (num != 3)
          throw new Exception("wrong number of cookies " + num);
        ServerCookie co = cs.getCookie(0);
        System.out.println("1 - Cookie: " + co);
        ServerCookie co2 = cs.getCookie(1);
        System.out.println("2 - Cookie: " + co2);
        ServerCookie co3 = cs.getCookie(2);
        System.out.println("3 - Cookie: " + co3);

        String coname = co.getName().toString();
        String coval  = co.getValue().toString();
        if ( ! name.equals(coname))
          throw new Exception("1 - wrong name " + coname + " != " + name);
        if ( ! val.equals(coval))
          throw new Exception("1 - wrong value " + coval + " != " + val);

        String coname2 = co2.getName().toString();
        String coval2  = co2.getValue().toString();
        if ( ! name2.equals(coname2))
          throw new Exception("2 - wrong name " + coname2 + " != " + name2);
        if ( ! val2.equals(coval2))
          throw new Exception("2 - wrong value " + coval2 + " != " + val2);

        String coname3 = co3.getName().toString();
        String coval3  = co3.getValue().toString();
        if ( ! name3.equals(coname3))
          throw new Exception("3 - wrong name " + coname3 + " != " + name3);
        if ( ! val2.equals(coval2))
          throw new Exception("3 - wrong value " + coval3 + " != " + val3);
    }
    public static void test( String s, String name, String val, String name2,
                             String val2, String name3, String val3,
                             String name4, String val4 ) throws Exception {
        System.out.println("Processing [" + s + "]");
        Cookies cs=new Cookies(null);
        cs.processCookieHeader( s.getBytes(), 0, s.length());

        int num = cs.getCookieCount();
        if (num != 4)
          throw new Exception("wrong number of cookies " + num);
        ServerCookie co = cs.getCookie(0);
        System.out.println("1 - Cookie: " + co);
        ServerCookie co2 = cs.getCookie(1);
        System.out.println("2 - Cookie: " + co2);
        ServerCookie co3 = cs.getCookie(2);
        System.out.println("3 - Cookie: " + co3);
        ServerCookie co4 = cs.getCookie(3);
        System.out.println("4 - Cookie: " + co4);

        String coname = co.getName().toString();
        String coval  = co.getValue().toString();
        if ( ! name.equals(coname))
          throw new Exception("1 - wrong name " + coname + " != " + name);
        if ( ! val.equals(coval))
          throw new Exception("1 - wrong value " + coval + " != " + val);

        String coname2 = co2.getName().toString();
        String coval2  = co2.getValue().toString();
        if ( ! name2.equals(coname2))
          throw new Exception("2 - wrong name " + coname2 + " != " + name2);
        if ( ! val2.equals(coval2))
          throw new Exception("2 - wrong value " + coval2 + " != " + val2);

        String coname3 = co3.getName().toString();
        String coval3  = co3.getValue().toString();
        if ( ! name3.equals(coname3))
          throw new Exception("3 - wrong name " + coname3 + " != " + name3);
        if ( ! val3.equals(coval3))
          throw new Exception("3 - wrong value " + coval3 + " != " + val3);

        String coname4 = co4.getName().toString();
        String coval4  = co4.getValue().toString();
        if ( ! name4.equals(coname4))
          throw new Exception("4 - wrong name " + coname4 + " != " + name4);
        if ( ! val4.equals(coval4))
          throw new Exception("4 - wrong value " + coval4 + " != " + val4);
    }
}
