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
package org.apache.catalina.valves.rewrite;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.servlets.DefaultServlet;
import org.apache.catalina.startup.TesterServlet;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.catalina.valves.ValveBase;
import org.apache.tomcat.util.buf.ByteChunk;

/*
 * Implementation note:
 *
 * A number of these tests involve the rewrite valve returning an HTTP Location
 * header that include un-encoded UTF-8 bytes. How the HTTP client handles these
 * depends on the default character encoding configured for the JVM running the
 * test. The tests expect the client to be configured with UTF-8 as the default
 * encoding. Use of any other encoding is likely to lead to test failures.
 */
public class TestRewriteValve extends TomcatBaseTest {

    @Test
    public void testNoRewrite() throws Exception {
        doTestRewrite("", "/a/%255A", "/a/%255A");
    }

    @Test
    public void testBackslashPercentSign() throws Exception {
        doTestRewrite("RewriteRule ^(.*) /a/\\%5A", "/", "/a/%255A");
    }

    @Test
    public void testNoopRewrite() throws Exception {
        doTestRewrite("RewriteRule ^(.*) $1", "/a/%255A", "/a/%255A");
    }

    @Test
    public void testNoopValveSkipRewrite() throws Exception {
        doTestRewrite("RewriteRule ^(.*) $1 [VS]", "/a/%255A", "/a/%255A", null, null, true);
    }

    @Test
    public void testPathRewrite() throws Exception {
        doTestRewrite("RewriteRule ^/b(.*) /a$1", "/b/%255A", "/a/%255A");
    }

    @Test
    public void testNonNormalizedPathRewrite() throws Exception {
        doTestRewrite("RewriteRule ^/b/(.*) /b/../a/$1", "/b/%255A", "/b/../a/%255A");
    }

    // BZ 57863
    @Test
    public void testRewriteMap01() throws Exception {
        doTestRewrite("RewriteMap mapa org.apache.catalina.valves.rewrite.TesterRewriteMapA\n" +
                "RewriteRule /b/(.*).html$ /c/${mapa:$1}", "/b/a.html", "/c/aa");
    }

    @Test
    public void testRewriteMap02() throws Exception {
        doTestRewrite("RewriteMap mapa org.apache.catalina.valves.rewrite.TesterRewriteMapA\n" +
                "RewriteRule /b/(.*).html$ /c/${mapa:$1|dd}", "/b/x.html", "/c/dd");
    }

    // BZ 62667
    @Test
    public void testRewriteMap03() throws Exception {
        doTestRewrite("RewriteMap mapa org.apache.catalina.valves.rewrite.TesterRewriteMapA\n" +
                "RewriteRule /b/(.*).html$ /c/${mapa:$1|d$1d}", "/b/x.html", "/c/dxd");
    }

    @Test
    public void testRewriteMap04() throws Exception {
        doTestRewrite("RewriteMap mapa org.apache.catalina.valves.rewrite.TesterRewriteMapA\n" +
                "RewriteRule /b/(.*).html$ /c/${mapa:a$1|dd}", "/b/a.html", "/c/aaaa");
    }

    @Test
    public void testRewriteMap05() throws Exception {
        doTestRewrite("RewriteMap mapa org.apache.catalina.valves.rewrite.TesterRewriteMapA\n" +
                "RewriteRule /b/.* /c/${mapa:a}", "/b/a.html", "/c/aa");
    }

    @Test
    public void testRewriteMap06() throws Exception {
        doTestRewrite("RewriteMap mapa org.apache.catalina.valves.rewrite.TesterRewriteMapA\n" +
                "RewriteRule /b/.* /c/${mapa:${mapa:a}}", "/b/a.html", "/c/aaaa");
    }

    @Test
    public void testRewriteMap07() throws Exception {
        doTestRewrite("RewriteMap mapa org.apache.catalina.valves.rewrite.TesterRewriteMapA foo bar\n" +
                "RewriteRule /b/.* /c/${mapa:${mapa:a}}", "/b/a.html", "/c/aaaa");
    }

    @Test
    public void testRewriteMap08() throws Exception {
        doTestRewrite("RewriteMap lc int:tolower\n" + "RewriteRule ^(.*) ${lc:$1}", "/C/AaA", "/c/aaa");
    }

    @Test
    public void testRewriteMap09() throws Exception {
        doTestRewrite("RewriteMap lc int:toupper\n" + "RewriteRule ^(.*) ${lc:$1}", "/w/aAa", "/W/AAA");
    }

    @Test
    public void testRewriteMap10() throws Exception {
        doTestRewrite("RewriteMap lc int:escape\n" + "RewriteRule ^(.*) ${lc:$1}", "/c/a%20aa", "/c/a%2520aa");
    }

    @Test
    public void testRewriteMap11() throws Exception {
        doTestRewrite("RewriteMap lc int:unescape\n" + "RewriteRule ^(.*) ${lc:$1}", "/c/a%2520aa", "/c/a%20aa");
    }


    private static String getTestConfDirectory() {
        File f = new File("test/conf");
        return f.getAbsolutePath() + File.separator;
    }


    @Test
    public void testRewriteMap12() throws Exception {
        doTestRewrite("RewriteMap mapb txt:" + getTestConfDirectory() + "TesterRewriteMapB.txt\n" +
                "RewriteRule /b/(.*).html$ /c/${mapb:$1}", "/b/a.html", "/c/aa");
    }

    @Test
    public void testRewriteMap13() throws Exception {
        doTestRewrite("RewriteMap mapb txt:" + getTestConfDirectory() + "TesterRewriteMapB.txt\n" +
                "RewriteRule /b/(.*).html$ /c/${mapb:$1|dd}", "/b/x.html", "/c/dd");
    }

    // BZ 62667
    @Test
    public void testRewriteMap14() throws Exception {
        doTestRewrite("RewriteMap mapb txt:" + getTestConfDirectory() + "TesterRewriteMapB.txt\n" +
                "RewriteRule /b/(.*).html$ /c/${mapb:$1|d$1d}", "/b/x.html", "/c/dxd");
    }

    @Test
    public void testRewriteMap15() throws Exception {
        doTestRewrite("RewriteMap mapb txt:" + getTestConfDirectory() + "TesterRewriteMapB.txt\n" +
                "RewriteRule /b/(.*).html$ /c/${mapb:a$1|dd}", "/b/a.html", "/c/aaaa");
    }

    @Test
    public void testRewriteMap16() throws Exception {
        doTestRewrite("RewriteMap mapb txt:" + getTestConfDirectory() + "TesterRewriteMapB.txt\n" +
                "RewriteRule /b/.* /c/${mapb:a}", "/b/a.html", "/c/aa");
    }

    @Test
    public void testRewriteMap17() throws Exception {
        doTestRewrite("RewriteMap mapb txt:" + getTestConfDirectory() + "TesterRewriteMapB.txt\n" +
                "RewriteRule /b/.* /c/${mapb:${mapb:a}}", "/b/a.html", "/c/aaaa");
    }

    @Test
    public void testRewriteMap18() throws Exception {
        doTestRewrite("RewriteMap mapb txt:" + getTestConfDirectory() + "TesterRewriteMapB.txt\n" +
                "RewriteRule /b/.* /c/${mapb:${mapb:a}}", "/b/a.html", "/c/aaaa");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRewriteMap19() throws Exception {
        doTestRewrite("RewriteMap mapb txt:" + getTestConfDirectory() + "TesterRewriteMapB.txt first\n" +
                "RewriteRule /b/(.*).html$ /c/${mapb:$1}", "/b/aa.html", "/c/aaaa");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRewriteMap20() throws Exception {
        doTestRewrite("RewriteMap mapb txt:" + getTestConfDirectory() + "TesterRewriteMapB.txt first second\n" +
                "RewriteRule /b/(.*).html$ /c/${mapb:$1}", "/b/aa.html", "/c/aaaa");
    }

    @Test
    public void testRewriteMap21() throws Exception {
        doTestRewrite("RewriteMap mapb rnd:" + getTestConfDirectory() + "TesterRewriteMapC.txt\n" +
                "RewriteRule /b/(.*).html$ /c/${mapb:$1}", "/b/a.html", "/c/aa");
    }

    // This test should succeed 50% of the runs as it depends on a random choice
    public void testRewriteMap22() throws Exception {
        doTestRewrite("RewriteMap mapb rnd:" + getTestConfDirectory() + "TesterRewriteMapC.txt\n" +
                "RewriteRule /b/(.*).html$ /c/${mapb:$1}", "/b/b.html", "/c/bb");
    }

    @Test
    public void testRewriteMap23() throws Exception {
        doTestRewrite("RewriteMap mapb rnd:" + getTestConfDirectory() + "TesterRewriteMapC.txt\n" +
                "RewriteRule /b/(.*).html$ /c/${mapb:$1}", "/b/aa.html", "/c/aaaa");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRewriteMap24() throws Exception {
        doTestRewrite("RewriteMap mapb rnd:" + getTestConfDirectory() + "TesterRewriteMapC.txt first\n" +
                "RewriteRule /b/(.*).html$ /c/${mapb:$1}", "/b/aa.html", "/c/aaaa");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRewriteMap25() throws Exception {
        doTestRewrite("RewriteMap mapb rnd:" + getTestConfDirectory() + "TesterRewriteMapC.txt first second\n" +
                "RewriteRule /b/(.*).html$ /c/${mapb:$1}", "/b/aa.html", "/c/aaaa");
    }

    @Test
    public void testRewriteServerVar() throws Exception {
        doTestRewrite("RewriteRule /b/(.*).html$ /c%{SERVLET_PATH}", "/b/x.html", "/c/b/x.html");
    }

    @Test
    public void testRewriteEnvVarAndServerVar() throws Exception {
        System.setProperty("some_variable", "something");
        doTestRewrite("RewriteRule /b/(.*).html$ /c/%{ENV:some_variable}%{SERVLET_PATH}", "/b/x.html",
                "/c/something/b/x.html");
    }

    @Test
    public void testRewriteServerVarAndEnvVar() throws Exception {
        System.setProperty("some_variable", "something");
        doTestRewrite("RewriteRule /b/(.*).html$ /c%{SERVLET_PATH}/%{ENV:some_variable}", "/b/x.html",
                "/c/b/x.html/something");
    }

    @Test
    public void testRewriteMissingCurlyBraceOnVar() throws Exception {
        try {
            doTestRewrite("RewriteRule /b/(.*).html$ /c%_{SERVLET_PATH}", "/b/x.html", "/c");
            Assert.fail("IAE expected.");
        } catch (IllegalArgumentException e) {
            // expected as %_{ is invalid
        }
    }

    @Test
    public void testRewriteMissingCurlyBraceOnMapper() throws Exception {
        try {
            doTestRewrite("RewriteRule /b/(.*).html$ /c$_{SERVLET_PATH}", "/b/x.html", "/c");
            Assert.fail("IAE expected.");
        } catch (IllegalArgumentException e) {
            // expected as $_{ is invalid
        }
    }

    // https://bz.apache.org/bugzilla/show_bug.cgi?id=60013
    public void testRewriteWithEncoding02() throws Exception {
        doTestRewrite("RewriteRule ^/b/(.*)$ /c/?param=$1 [L]", "/b/%E5%9C%A8%E7%BA%BF%E6%B5%8B%E8%AF%95", "/c/",
                "param=\u5728\u7EBF\u6D4B\u8BD5");
    }

    @Test
    public void testNonAsciiPath() throws Exception {
        doTestRewrite("RewriteRule ^/b/(.*) /c/$1", "/b/%E5%9C%A8%E7%BA%BF%E6%B5%8B%E8%AF%95",
                "/c/%E5%9C%A8%E7%BA%BF%E6%B5%8B%E8%AF%95");
    }

    @Test
    public void testNonAsciiPathRedirect() throws Exception {
        doTestRewrite("RewriteRule ^/b/(.*) /c/$1 [R]", "/b/%E5%9C%A8%E7%BA%BF%E6%B5%8B%E8%AF%95",
                "/c/%E5%9C%A8%E7%BA%BF%E6%B5%8B%E8%AF%95");
    }

    @Test
    public void testQueryString() throws Exception {
        doTestRewrite("RewriteRule ^/b/(.*) /c?$1", "/b/id=1", "/c", "id=1");
    }

    @Test
    public void testQueryStringRemove() throws Exception {
        doTestRewrite("RewriteRule ^/b/(.*) /c/$1?", "/b/d?=1", "/c/d", null);
    }

    @Test
    public void testQueryStringRemove02() throws Exception {
        doTestRewrite("RewriteRule ^/b/(.*) /c/$1 [QSD]", "/b/d?=1", "/c/d", null);
    }

    @Test
    public void testNonAsciiQueryString() throws Exception {
        doTestRewrite("RewriteRule ^/b/(.*) /c?$1", "/b/id=%E5%9C%A8%E7%BA%BF%E6%B5%8B%E8%AF%95", "/c",
                "id=%E5%9C%A8%E7%BA%BF%E6%B5%8B%E8%AF%95");
    }


    @Test
    public void testNonAsciiQueryStringAndPath() throws Exception {
        doTestRewrite("RewriteRule ^/b/(.*)/(.*) /c/$1?$2", "/b/%E5%9C%A8%E7%BA%BF/id=%E6%B5%8B%E8%AF%95",
                "/c/%E5%9C%A8%E7%BA%BF", "id=%E6%B5%8B%E8%AF%95");
    }


    @Test
    public void testNonAsciiQueryStringAndRedirect() throws Exception {
        doTestRewrite("RewriteRule ^/b/(.*) /c?$1 [R]", "/b/id=%E5%9C%A8%E7%BA%BF%E6%B5%8B%E8%AF%95", "/c",
                "id=%E5%9C%A8%E7%BA%BF%E6%B5%8B%E8%AF%95");
    }


    @Test
    public void testNonAsciiQueryStringAndPathAndRedirect() throws Exception {
        doTestRewrite("RewriteRule ^/b/(.*)/(.*) /c/$1?$2 [R]", "/b/%E5%9C%A8%E7%BA%BF/id=%E6%B5%8B%E8%AF%95",
                "/c/%E5%9C%A8%E7%BA%BF", "id=%E6%B5%8B%E8%AF%95");
    }


    @Test
    public void testNonAsciiQueryStringWithB() throws Exception {
        doTestRewrite("RewriteRule ^/b/(.*)/id=(.*) /c?filename=$1&id=$2 [B]",
                "/b/file01/id=%E5%9C%A8%E7%BA%BF%E6%B5%8B%E8%AF%95", "/c",
                "filename=file01&id=%25E5%259C%25A8%25E7%25BA%25BF%25E6%25B5%258B%25E8%25AF%2595");
    }


    @Test
    public void testNonAsciiQueryStringAndPathAndRedirectWithB() throws Exception {
        // Note the double encoding of the result (httpd produces the same result)
        doTestRewrite("RewriteRule ^/b/(.*)/(.*)/id=(.*) /c/$1?filename=$2&id=$3 [B,R]",
                "/b/%E5%9C%A8%E7%BA%BF/file01/id=%E6%B5%8B%E8%AF%95", "/c/%25E5%259C%25A8%25E7%25BA%25BF",
                "filename=file01&id=%25E6%25B5%258B%25E8%25AF%2595");
    }


    @Test
    public void testUtf8WithBothQsFlagsNone() throws Exception {
        // Note %C2%A1 == \u00A1
        doTestRewrite("RewriteRule ^/b/(.*)/(.*) /c/\u00A1$1?$2", "/b/%C2%A1/id=%C2%A1?di=%C2%AE", "/c/%C2%A1%C2%A1",
                "id=%C2%A1");
    }


    @Test
    public void testUtf8WithBothQsFlagsB() throws Exception {
        // Note %C2%A1 == \u00A1
        doTestRewrite("RewriteRule ^/b/(.*)/(.*) /c/\u00A1$1?$2 [B]", "/b/%C2%A1/id=%C2%A1?di=%C2%AE",
                "/c/%C2%A1%25C2%25A1", "id=%25C2%25A1");
    }


    @Test
    public void testUtf8WithBothQsFlagsR() throws Exception {
        // Note %C2%A1 == \u00A1
        doTestRewrite("RewriteRule ^/b/(.*)/(.*) /c/\u00A1$1?$2 [R]", "/b/%C2%A1/id=%C2%A1?di=%C2%AE",
                "/c/%C2%A1%C2%A1", "id=%C2%A1");
    }


    @Test
    public void testUtf8WithBothQsFlagsRB() throws Exception {
        // Note %C2%A1 == \u00A1
        doTestRewrite("RewriteRule ^/b/(.*)/(.*) /c/\u00A1$1?$2 [R,B]", "/b/%C2%A1/id=%C2%A1?di=%C2%AE",
                "/c/%C2%A1%25C2%25A1", "id=%25C2%25A1");
    }


    @Test
    public void testUtf8WithBothQsFlagsRNE() throws Exception {
        // Note %C2%A1 == \u00A1
        // Failing to escape the redirect means UTF-8 bytes in the Location
        // header which will be treated as if they are ISO-8859-1
        doTestRewrite("RewriteRule ^/b/(.*)/(.*) /c/\u00A1$1?$2 [R,NE]", "/b/%C2%A1/id=%C2%A1?di=%C2%AE", null);
    }


    @Test
    public void testUtf8WithBothQsFlagsRBNE() throws Exception {
        // Note %C2%A1 == \u00A1
        // Failing to escape the redirect means UTF-8 bytes in the Location
        // header which will be treated as if they are ISO-8859-1
        doTestRewrite("RewriteRule ^/b/(.*)/(.*) /c/\u00A1$1?$2 [R,B,NE]", "/b/%C2%A1/id=%C2%A1?di=%C2%AE", null);
    }


    @Test
    public void testUtf8WithBothQsFlagsBQSA() throws Exception {
        // Note %C2%A1 == \u00A1
        doTestRewrite("RewriteRule ^/b/(.*)/(.*) /c/\u00A1$1?$2 [B,QSA]", "/b/%C2%A1/id=%C2%A1?di=%C2%AE",
                "/c/%C2%A1%25C2%25A1", "id=%25C2%25A1&di=%C2%AE");
    }


    @Test
    public void testUtf8WithBothQsFlagsRQSA() throws Exception {
        // Note %C2%A1 == \u00A1
        doTestRewrite("RewriteRule ^/b/(.*)/(.*) /c/\u00A1$1?$2 [R,QSA]", "/b/%C2%A1/id=%C2%A1?di=%C2%AE",
                "/c/%C2%A1%C2%A1", "id=%C2%A1&di=%C2%AE");
    }


    @Test
    public void testUtf8WithBothQsFlagsRBQSA() throws Exception {
        // Note %C2%A1 == \u00A1
        doTestRewrite("RewriteRule ^/b/(.*)/(.*) /c/\u00A1$1?$2 [R,B,QSA]", "/b/%C2%A1/id=%C2%A1?di=%C2%AE",
                "/c/%C2%A1%25C2%25A1", "id=%25C2%25A1&di=%C2%AE");
    }


    @Test
    public void testUtf8WithBothQsFlagsRNEQSA() throws Exception {
        // Note %C2%A1 == \u00A1
        // Failing to escape the redirect means UTF-8 bytes in the Location
        // header which will be treated as if they are ISO-8859-1
        doTestRewrite("RewriteRule ^/b/(.*)/(.*) /c/\u00A1$1?$2 [R,NE,QSA]", "/b/%C2%A1/id=%C2%A1?di=%C2%AE", null);
    }


    @Test
    public void testUtf8WithBothQsFlagsRBNEQSA() throws Exception {
        // Note %C2%A1 == \u00A1
        // Failing to escape the redirect means UTF-8 bytes in the Location
        // header which will be treated as if they are ISO-8859-1
        doTestRewrite("RewriteRule ^/b/(.*)/(.*) /c/\u00A1$1?$2 [R,B,NE,QSA]", "/b/%C2%A1/id=%C2%A1?di=%C2%AE", null);
    }


    @Test
    public void testUtf8WithOriginalQsFlagsNone() throws Exception {
        // Note %C2%A1 == \u00A1
        doTestRewrite("RewriteRule ^/b/(.*) /c/\u00A1$1", "/b/%C2%A1?id=%C2%A1", "/c/%C2%A1%C2%A1", "id=%C2%A1");
    }


    @Test
    public void testUtf8WithOriginalQsFlagsB() throws Exception {
        // Note %C2%A1 == \u00A1
        doTestRewrite("RewriteRule ^/b/(.*) /c/\u00A1$1 [B]", "/b/%C2%A1?id=%C2%A1", "/c/%C2%A1%25C2%25A1",
                "id=%C2%A1");
    }


    @Test
    public void testUtf8WithOriginalQsFlagsR() throws Exception {
        // Note %C2%A1 == \u00A1
        doTestRewrite("RewriteRule ^/b/(.*) /c/\u00A1$1 [R]", "/b/%C2%A1?id=%C2%A1", "/c/%C2%A1%C2%A1", "id=%C2%A1");
    }


    @Test
    public void testUtf8WithOriginalQsFlagsRB() throws Exception {
        // Note %C2%A1 == \u00A1
        doTestRewrite("RewriteRule ^/b/(.*) /c/\u00A1$1 [R,B]", "/b/%C2%A1?id=%C2%A1", "/c/%C2%A1%25C2%25A1",
                "id=%C2%A1");
    }


    @Test
    public void testUtf8WithOriginalQsFlagsRNE() throws Exception {
        // Note %C2%A1 == \u00A1
        // Failing to escape the redirect means UTF-8 bytes in the Location
        // header which will be treated as if they are ISO-8859-1
        doTestRewrite("RewriteRule ^/b/(.*) /c/\u00A1$1 [R,NE]", "/b/%C2%A1?id=%C2%A1", null);
    }


    @Test
    public void testUtf8WithOriginalQsFlagsRBNE() throws Exception {
        // Note %C2%A1 == \u00A1
        // Failing to escape the redirect means UTF-8 bytes in the Location
        // header which will be treated as if they are ISO-8859-1
        doTestRewrite("RewriteRule ^/b/(.*) /c/\u00A1$1 [R,B,NE]", "/b/%C2%A1?id=%C2%A1", null);
    }


    @Test
    public void testUtf8WithRewriteQsFlagsNone() throws Exception {
        // Note %C2%A1 == \u00A1
        doTestRewrite("RewriteRule ^/b/(.*)/(.*) /c/\u00A1$1?$2", "/b/%C2%A1/id=%C2%A1", "/c/%C2%A1%C2%A1",
                "id=%C2%A1");
    }


    @Test
    public void testUtf8WithRewriteQsFlagsB() throws Exception {
        // Note %C2%A1 == \u00A1
        doTestRewrite("RewriteRule ^/b/(.*)/(.*) /c/\u00A1$1?$2 [B]", "/b/%C2%A1/id=%C2%A1", "/c/%C2%A1%25C2%25A1",
                "id=%25C2%25A1");
    }


    @Test
    public void testUtf8WithRewriteQsFlagsR() throws Exception {
        // Note %C2%A1 == \u00A1
        doTestRewrite("RewriteRule ^/b/(.*)/(.*) /c/\u00A1$1?$2 [R]", "/b/%C2%A1/id=%C2%A1", "/c/%C2%A1%C2%A1",
                "id=%C2%A1");
    }


    @Test
    public void testUtf8WithBothQsFlagsQSA() throws Exception {
        // Note %C2%A1 == \u00A1
        doTestRewrite("RewriteRule ^/b/(.*)/(.*) /c/\u00A1$1?$2 [QSA]", "/b/%C2%A1/id=%C2%A1?di=%C2%AE",
                "/c/%C2%A1%C2%A1", "id=%C2%A1&di=%C2%AE");
    }


    @Test
    public void testUtf8WithRewriteQsFlagsRB() throws Exception {
        // Note %C2%A1 == \u00A1
        doTestRewrite("RewriteRule ^/b/(.*)/(.*) /c/\u00A1$1?$2 [R,B]", "/b/%C2%A1/id=%C2%A1", "/c/%C2%A1%25C2%25A1",
                "id=%25C2%25A1");
    }


    @Test
    public void testUtf8WithRewriteQsFlagsRNE() throws Exception {
        // Note %C2%A1 == \u00A1
        // Failing to escape the redirect means UTF-8 bytes in the Location
        // header which will be treated as if they are ISO-8859-1
        doTestRewrite("RewriteRule ^/b/(.*)/(.*) /c/\u00A1$1?$2 [R,NE]", "/b/%C2%A1/id=%C2%A1", null);
    }


    @Test
    public void testUtf8WithRewriteQsFlagsRBNE() throws Exception {
        // Note %C2%A1 == \u00A1
        // Failing to escape the redirect means UTF-8 bytes in the Location
        // header which will be treated as if they are ISO-8859-1
        doTestRewrite("RewriteRule ^/b/(.*)/(.*) /c/\u00A1$1?$2 [R,B,NE]", "/b/%C2%A1/id=%C2%A1", null);
    }


    @Test
    public void testUtf8WithRewriteQsFlagsQSA() throws Exception {
        // Note %C2%A1 == \u00A1
        doTestRewrite("RewriteRule ^/b/(.*)/(.*) /c/\u00A1$1?$2 [QSA]", "/b/%C2%A1/id=%C2%A1", "/c/%C2%A1%C2%A1",
                "id=%C2%A1");
    }


    @Test
    public void testUtf8FlagsNone() throws Exception {
        // Note %C2%A1 == \u00A1
        doTestRewrite("RewriteRule ^/b/(.*) /c/\u00A1$1", "/b/%C2%A1", "/c/%C2%A1%C2%A1");
    }


    @Test
    public void testUtf8FlagsB() throws Exception {
        // Note %C2%A1 == \u00A1
        doTestRewrite("RewriteRule ^/b/(.*) /c/\u00A1$1 [B]", "/b/%C2%A1", "/c/%C2%A1%25C2%25A1");
    }


    @Test
    public void testUtf8FlagsR() throws Exception {
        // Note %C2%A1 == \u00A1
        doTestRewrite("RewriteRule ^/b/(.*) /c/\u00A1$1 [R]", "/b/%C2%A1", "/c/%C2%A1%C2%A1");
    }


    @Test
    public void testUtf8FlagsRB() throws Exception {
        // Note %C2%A1 == \u00A1
        doTestRewrite("RewriteRule ^/b/(.*) /c/\u00A1$1 [R,B]", "/b/%C2%A1", "/c/%C2%A1%25C2%25A1");
    }


    @Test
    public void testUtf8FlagsRNE() throws Exception {
        // Note %C2%A1 == \u00A1
        // Failing to escape the redirect means UTF-8 bytes in the Location
        // header which will be treated as if they are ISO-8859-1
        doTestRewrite("RewriteRule ^/b/(.*) /c/\u00A1$1 [R,NE]", "/b/%C2%A1", null);
    }


    @Test
    public void testUtf8FlagsRBNE() throws Exception {
        // Note %C2%A1 == \u00A1
        // Failing to escape the redirect means UTF-8 bytes in the Location
        // header which will be treated as if they are ISO-8859-1
        doTestRewrite("RewriteRule ^/b/(.*) /c/\u00A1$1 [R,B,NE]", "/b/%C2%A1", null);
    }


    @Test
    public void testFlagsNC() throws Exception {
        // https://bz.apache.org/bugzilla/show_bug.cgi?id=60116
        doTestRewrite("RewriteCond %{QUERY_STRING} a=([a-z]*) [NC]\n" + "RewriteRule .* - [E=X-Test:%1]", "/c?a=aAa",
                "/c", null, "aAa");
    }

    @Test
    public void testRewriteEmptyHeader() throws Exception {

        // Disable the following of redirects for this test only
        boolean originalValue = HttpURLConnection.getFollowRedirects();
        HttpURLConnection.setFollowRedirects(false);
        try {
            Map<String, List<String>> resHead = new HashMap<>();
            Map<String, List<String>> reqHead = new HashMap<>();
            reqHead.put("\"\"", Arrays.asList(new String[]{"Test"}));
            doTestRewriteEx("RewriteCond %{HTTP:} .+\nRewriteRule .* - [F]", "",
                null, null, null, false, resHead, reqHead);
        } finally {
            HttpURLConnection.setFollowRedirects(originalValue);
        }
    }


    @Test
    public void testHostRewrite() throws Exception {
        // Based on report from users list that ':' was encoded and breaking
        // the redirect
        doTestRewrite("RewriteRule ^/b(.*) http://%{HTTP_HOST}:%{SERVER_PORT}/a$1 [R]", "/b/%255A", "/a/%255A");
    }


    @Test
    public void testDefaultRedirect() throws Exception {
        doTestRedirect("RewriteRule ^/from/a$ /to/b [R]", "/redirect/from/a", "/redirect/to/b", 302);
    }


    @Test
    public void testTempRedirect() throws Exception {
        doTestRedirect("RewriteRule ^/from/a$ /to/b [R=temp]", "/redirect/from/a", "/redirect/to/b", 302);
    }


    @Test
    public void testPermanentRedirect() throws Exception {
        // Disable the following of redirects for this test only
        boolean originalValue = HttpURLConnection.getFollowRedirects();
        HttpURLConnection.setFollowRedirects(false);
        try {
            doTestRedirect("RewriteRule ^/from/a$ /to/b [R=permanent]", "/redirect/from/a", "/redirect/to/b", 301);
        } finally {
            HttpURLConnection.setFollowRedirects(originalValue);
        }
    }


    @Test
    public void testSeeotherRedirect() throws Exception {
        // Disable the following of redirects for this test only
        boolean originalValue = HttpURLConnection.getFollowRedirects();
        HttpURLConnection.setFollowRedirects(false);
        try {
            doTestRedirect("RewriteRule ^/from/a$ /to/b [R=seeother]", "/redirect/from/a", "/redirect/to/b", 303);
        } finally {
            HttpURLConnection.setFollowRedirects(originalValue);
        }
    }


    @Test
    public void test307Redirect() throws Exception {
        // Disable the following of redirects for this test only
        boolean originalValue = HttpURLConnection.getFollowRedirects();
        HttpURLConnection.setFollowRedirects(false);
        try {
            doTestRedirect("RewriteRule ^/from/a$ /to/b [R=307]", "/redirect/from/a", "/redirect/to/b", 307);
        } finally {
            HttpURLConnection.setFollowRedirects(originalValue);
        }
    }


    @Test
    public void testBackReferenceRewrite() throws Exception {
        doTestRewrite("RewriteRule ^/b/(rest)?$ /c/$1", "/b/rest", "/c/rest");
    }


    @Test
    public void testEmptyBackReferenceRewrite() throws Exception {
        doTestRewrite("RewriteRule ^/b/(rest)?$ /c/$1", "/b/", "/c/");
    }


    @Test
    public void testNegativePattern01() throws Exception {
        doTestRewrite("RewriteRule !^/b/.* /c/", "/b", "/c/");
    }


    @Test
    public void testNegativePattern02() throws Exception {
        doTestRewrite("RewriteRule !^/b/.* /c/", "/d/e/f", "/c/");
    }


    @Test
    public void testNegativePattern03() throws Exception {
        doTestRewrite("RewriteRule !^/c/.* /b/", "/c/", "/c/");
    }


    @Test
    public void testNegativePattern04() throws Exception {
        doTestRewrite("RewriteRule !^/c/.* /b/", "/c/d", "/c/d");
    }

    @Test
    public void testMultiLine001() throws Exception {
        doTestRewrite("RewriteRule /dummy /anotherDummy [L]\nRewriteRule ^/a /c [L]", "/a", "/c");
    }

    @Test
    public void testMultiLine002() throws Exception {
        doTestRewrite("RewriteRule /dummy /a\nRewriteRule /a /c [L]", "/dummy", "/c");
    }

    private void doTestRewrite(String config, String request, String expectedURI) throws Exception {
        doTestRewrite(config, request, expectedURI, null);
    }


    private void doTestRewrite(String config, String request, String expectedURI, String expectedQueryString)
            throws Exception {
        doTestRewrite(config, request, expectedURI, expectedQueryString, null);
    }


    private void doTestRewrite(String config, String request, String expectedURI, String expectedQueryString,
            String expectedAttributeValue) throws Exception {
        doTestRewrite(config, request, expectedURI, expectedQueryString, expectedAttributeValue, false);
    }

    private void doTestRewrite(String config, String request, String expectedURI, String expectedQueryString,
            String expectedAttributeValue, boolean valveSkip) throws Exception {
        doTestRewriteEx(config, request, expectedURI, expectedQueryString,
                expectedAttributeValue, valveSkip, null, null);
    }

    private void doTestRewriteEx(String config, String request, String expectedURI, String expectedQueryString,
            String expectedAttributeValue, boolean valveSkip, Map<String, List<String>> resHead, Map<String, List<String>> reqHead ) throws Exception {

        Tomcat tomcat = getTomcatInstance();

        // No file system docBase required
        Context ctx = getProgrammaticRootContext();

        RewriteValve rewriteValve = new RewriteValve();
        ctx.getPipeline().addValve(rewriteValve);
        if (valveSkip) {
            ctx.getPipeline().addValve(new ValveBase() {
                @Override
                public void invoke(Request request, Response response) throws IOException, ServletException {
                    throw new IllegalStateException();
                }
            });
        }

        rewriteValve.setConfiguration(config);

        Tomcat.addServlet(ctx, "snoop", new SnoopServlet());
        ctx.addServletMappingDecoded("/a/%5A", "snoop");
        ctx.addServletMappingDecoded("/c/*", "snoop");
        ctx.addServletMappingDecoded("/W/*", "snoop");
        Tomcat.addServlet(ctx, "default", new DefaultServlet());
        ctx.addServletMappingDecoded("/", "default");

        tomcat.start();

        ByteChunk res = new ByteChunk();
        int rc = methodUrl("http://localhost:" + getPort() + request, res, DEFAULT_CLIENT_TIMEOUT_MS,
            reqHead,
            resHead,
            "GET", true);
        res.setCharset(StandardCharsets.UTF_8);

        if (expectedURI == null) {
            // Rewrite is expected to fail. Probably because invalid characters
            // were written into the request target
            Assert.assertEquals(400, rc);
        } else {
            String body = res.toString();
            RequestDescriptor requestDesc = SnoopResult.parse(body);
            String requestURI = requestDesc.getRequestInfo("REQUEST-URI");
            Assert.assertEquals(expectedURI, requestURI);

            if (expectedQueryString != null) {
                String queryString = requestDesc.getRequestInfo("REQUEST-QUERY-STRING");
                Assert.assertEquals(expectedQueryString, queryString);
            }

            if (expectedAttributeValue != null) {
                String attributeValue = requestDesc.getAttribute("X-Test");
                Assert.assertEquals(expectedAttributeValue, attributeValue);
            }
        }
    }

    private void doTestRedirect(String config, String request, String expectedURI, int expectedStatusCode)
            throws Exception {

        Tomcat tomcat = getTomcatInstance();

        // No file system docBase required
        Context ctx = tomcat.addContext("redirect", null);

        RewriteValve rewriteValve = new RewriteValve();
        ctx.getPipeline().addValve(rewriteValve);

        rewriteValve.setConfiguration(config);

        Tomcat.addServlet(ctx, "tester", new TesterServlet());
        ctx.addServletMappingDecoded("/from/a", "tester");
        ctx.addServletMappingDecoded("/to/b", "tester");

        tomcat.start();

        ByteChunk res = new ByteChunk();
        Map<String, List<String>> resHead = new HashMap<>();
        int rc = methodUrl("http://localhost:" + getPort() + request, res, DEFAULT_CLIENT_TIMEOUT_MS, null, resHead,
                "GET", false);
        res.setCharset(StandardCharsets.UTF_8);

        if (expectedURI == null) {
            // Rewrite is expected to fail. Probably because invalid characters
            // were written into the request target
            Assert.assertEquals(400, rc);
        } else {
            List<String> locations = resHead.get("Location");
            Assert.assertFalse(locations.isEmpty());
            String redirectURI = locations.get(0);
            Assert.assertEquals(expectedURI, redirectURI);
            Assert.assertEquals(expectedStatusCode, rc);
        }
    }


    @Test
    public void testCookie() throws Exception {

        Tomcat tomcat = getTomcatInstance();

        // No file system docBase required
        Context ctx = tomcat.addContext("redirect", null);

        CookieTestValve cookieTestValve = new CookieTestValve();
        tomcat.getHost().getPipeline().addValve(cookieTestValve);
        RewriteValve rewriteValve = new RewriteValve();
        tomcat.getHost().getPipeline().addValve(rewriteValve);

        rewriteValve.setConfiguration("RewriteRule ^/source/(.*) /redirect/$1");

        Tomcat.addServlet(ctx, "cookieTest", new CookieTestServlet());

        ctx.addServletMappingDecoded("/", "cookieTest");

        tomcat.start();

        Map<String, List<String>> reqHead = new HashMap<>();
        reqHead.put("cookie", Arrays.asList("test=data"));
        ByteChunk res = new ByteChunk();
        int rc = methodUrl("http://localhost:" + getPort() + "/source/cookieTest", res, DEFAULT_CLIENT_TIMEOUT_MS,
                reqHead, null, "GET", false);

        Assert.assertEquals(HttpServletResponse.SC_OK, rc);

        res.setCharset(StandardCharsets.UTF_8);
        Assert.assertEquals("PASS", res.toString());
    }


    public static class CookieTestValve extends ValveBase {

        @Override
        public void invoke(Request request, Response response) throws IOException, ServletException {
            Cookie[] cookies = request.getCookies();
            if (cookies != null) {
                for (Cookie cookie : cookies) {
                    if ("test".equals(cookie.getName())) {
                        request.setAttribute("cookieTest", cookie.getValue());
                        break;
                    }
                }
            }
            getNext().invoke(request, response);
        }
    }


    public static class CookieTestServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            resp.setContentType("text/plain");
            resp.setCharacterEncoding("UTF-8");

            PrintWriter pw = resp.getWriter();
            if (req.getAttribute("cookieTest") != null) {
                pw.print("PASS");
            } else {
                pw.print("FAIL");
            }
        }
    }
}
