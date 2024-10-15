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
package org.apache.catalina.connector;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.unittest.TesterContext;
import org.apache.tomcat.unittest.TesterRequest;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.collections.CaseInsensitiveKeyMap;
import org.apache.tomcat.util.descriptor.web.ErrorPage;

/**
 * Test case for {@link Request}.
 */
public class TestResponse extends TomcatBaseTest {

    @Test
    public void testBug49598() throws Exception {
        // Setup Tomcat instance
        Tomcat tomcat = getTomcatInstance();

        // No file system docBase required
        Context ctx = getProgrammaticRootContext();

        Tomcat.addServlet(ctx, "servlet", new Bug49598Servlet());
        ctx.addServletMappingDecoded("/", "servlet");

        tomcat.start();

        Map<String,List<String>> headers = new CaseInsensitiveKeyMap<>();
        getUrl("http://localhost:" + getPort() + "/", new ByteChunk(), headers);

        // Check for headers without a name
        for (Map.Entry<String,List<String>> header : headers.entrySet()) {
            if (header.getKey() == null) {
                // Expected if this is the response line
                List<String> values = header.getValue();
                if (values.size() == 1 &&
                        values.get(0).startsWith("HTTP/1.1")) {
                    continue;
                }
                Assert.fail("Null header name detected for value " + values);
            }
        }

        // Check for exactly one Set-Cookie header
        int count = 0;
        for (String headerName : headers.keySet()) {
            if ("Set-Cookie".equals(headerName)) {
                count ++;
            }
        }
        Assert.assertEquals(1, count);
    }

    private static final class Bug49598Servlet extends HttpServlet {
        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {
            HttpSession session = req.getSession(true);
            session.invalidate();
            req.getSession(true);
        }

    }


    /*
     * Tests an issue noticed during the investigation of BZ 52811.
     */
    @Test
    public void testCharset() throws Exception {
        // Setup Tomcat instance
        Tomcat tomcat = getTomcatInstance();

        // No file system docBase required
        Context ctx = getProgrammaticRootContext();

        Tomcat.addServlet(ctx, "servlet", new CharsetServlet());
        ctx.addServletMappingDecoded("/", "servlet");

        tomcat.start();

        ByteChunk bc = getUrl("http://localhost:" + getPort() + "/");

        Assert.assertEquals("OK", bc.toString());
    }

    private static final class CharsetServlet extends HttpServlet {
        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {
            PrintWriter pw = resp.getWriter();
            resp.setHeader("Content-Type", "text/plain;charset=UTF-8");

            // Should be ISO-8859-1 because getWriter() was called before
            // setHeader()
            if (resp.getCharacterEncoding().equals("ISO-8859-1")) {
                pw.print("OK");
            } else {
                pw.print("FAIL: " + resp.getCharacterEncoding());
            }
        }

    }


    @Test
    public void testBug52811() throws Exception {
        // Setup Tomcat instance
        Tomcat tomcat = getTomcatInstance();

        // No file system docBase required
        Context ctx = getProgrammaticRootContext();

        Tomcat.addServlet(ctx, "servlet", new Bug52811Servlet());
        ctx.addServletMappingDecoded("/", "servlet");

        tomcat.start();

        ByteChunk bc = getUrl("http://localhost:" + getPort() + "/");

        Assert.assertEquals("OK", bc.toString());
    }


    @Test
    public void testBug53062a() throws Exception {
        Request req = new TesterRequest();
        Response resp = new Response(null);
        resp.setRequest(req);

        String result = resp.toAbsolute("./bar.html");

        Assert.assertEquals("http://localhost:8080/level1/level2/bar.html",
                result);
    }


    @Test
    public void testBug53062b() throws Exception {
        Request req = new TesterRequest();
        Response resp = new Response(null);
        resp.setRequest(req);

        String result = resp.toAbsolute(".");

        Assert.assertEquals("http://localhost:8080/level1/level2/", result);
    }


    @Test
    public void testBug53062c() throws Exception {
        Request req = new TesterRequest();
        Response resp = new Response(null);
        resp.setRequest(req);

        String result = resp.toAbsolute("..");

        Assert.assertEquals("http://localhost:8080/level1/", result);
    }


    @Test
    public void testBug53062d() throws Exception {
        Request req = new TesterRequest();
        Response resp = new Response(null);
        resp.setRequest(req);

        String result = resp.toAbsolute(".././..");

        Assert.assertEquals("http://localhost:8080/", result);
    }


    @Test(expected=IllegalArgumentException.class)
    public void testBug53062e() throws Exception {
        Request req = new TesterRequest();
        Response resp = new Response(null);
        resp.setRequest(req);

        resp.toAbsolute("../../..");
    }


    @Test
    public void testBug53062f() throws Exception {
        Request req = new TesterRequest();
        Response resp = new Response(null);
        resp.setRequest(req);

        String result = resp.toAbsolute("bar.html");

        Assert.assertEquals(
                "http://localhost:8080/level1/level2/bar.html", result);
    }


    @Test
    public void testBug53062g() throws Exception {
        Request req = new TesterRequest();
        Response resp = new Response(null);
        resp.setRequest(req);

        String result = resp.toAbsolute("bar.html?x=/../");

        Assert.assertEquals(
                "http://localhost:8080/level1/level2/bar.html?x=/../", result);
    }


    @Test
    public void testBug53062h() throws Exception {
        Request req = new TesterRequest();
        Response resp = new Response(null);
        resp.setRequest(req);

        String result = resp.toAbsolute("bar.html?x=/../../");

        Assert.assertEquals(
                "http://localhost:8080/level1/level2/bar.html?x=/../../",
                result);
    }


    @Test
    public void testBug53062i() throws Exception {
        Request req = new TesterRequest();
        Response resp = new Response(null);
        resp.setRequest(req);

        String result = resp.toAbsolute("./.?x=/../../");

        Assert.assertEquals(
                "http://localhost:8080/level1/level2/?x=/../../", result);
    }


    @Test
    public void testBug53062j() throws Exception {
        Request req = new TesterRequest();
        Response resp = new Response(null);
        resp.setRequest(req);

        String result = resp.toAbsolute("./..?x=/../../");

        Assert.assertEquals("http://localhost:8080/level1/?x=/../../", result);
    }


    @Test
    public void testBug53062k() throws Exception {
        Request req = new TesterRequest();
        Response resp = new Response(null);
        resp.setRequest(req);

        String result = resp.toAbsolute("./..?x=/../..");

        Assert.assertEquals(
                "http://localhost:8080/level1/?x=/../..",
                result);
    }


    @Test
    public void testBug53062l() throws Exception {
        Request req = new TesterRequest();
        Response resp = new Response(null);
        resp.setRequest(req);

        String result = resp.toAbsolute("bar.html#/../");

        Assert.assertEquals(
                "http://localhost:8080/level1/level2/bar.html#/../", result);
    }


    @Test
    public void testBug53062m() throws Exception {
        Request req = new TesterRequest();
        Response resp = new Response(null);
        resp.setRequest(req);

        String result = resp.toAbsolute("bar.html#/../../");

        Assert.assertEquals(
                "http://localhost:8080/level1/level2/bar.html#/../../", result);
    }


    @Test
    public void testBug53062n() throws Exception {
        Request req = new TesterRequest();
        Response resp = new Response(null);
        resp.setRequest(req);

        String result = resp.toAbsolute("./.#/../../");

        Assert.assertEquals(
                "http://localhost:8080/level1/level2/#/../../", result);
    }


    @Test
    public void testBug53062o() throws Exception {
        Request req = new TesterRequest();
        Response resp = new Response(null);
        resp.setRequest(req);

        String result = resp.toAbsolute("./..#/../../");

        Assert.assertEquals("http://localhost:8080/level1/#/../../", result);
    }


    @Test
    public void testBug53062p() throws Exception {
        Request req = new TesterRequest();
        Response resp = new Response(null);
        resp.setRequest(req);

        String result = resp.toAbsolute("./..#/../..");

        Assert.assertEquals("http://localhost:8080/level1/#/../..", result);
    }


    private void doTestEncodeURL(String location, String expected) {
        Request req = new TesterRequest(true);
        req.setRequestedSessionId("1234");
        req.setRequestedSessionURL(true);
        Response resp = new Response(null);
        resp.setRequest(req);

        String result = resp.encodeURL(location);
        Assert.assertEquals(expected, result);
    }


    @Test
    public void testEncodeURL01() throws Exception {
        doTestEncodeURL("./bar.html", "./bar.html;jsessionid=1234");
    }


    @Test
    public void testEncodeURL02() throws Exception {
        doTestEncodeURL(".", ".;jsessionid=1234");
    }


    @Test
    public void testEncodeURL03() throws Exception {
        doTestEncodeURL("..", "..;jsessionid=1234");
    }


    @Test
    public void testEncodeURL04() throws Exception {
        doTestEncodeURL(".././..", ".././..;jsessionid=1234");
    }


    public void testEncodeURL05() throws Exception {
        doTestEncodeURL("../../..", "../../..");
    }


    @Test
    public void testEncodeURL06() throws Exception {
        doTestEncodeURL("bar.html", "bar.html;jsessionid=1234");
    }


    @Test
    public void testEncodeURL07() throws Exception {
        doTestEncodeURL("bar.html?x=/../", "bar.html;jsessionid=1234?x=/../");
    }


    @Test
    public void testEncodeURL08() throws Exception {
        doTestEncodeURL("bar.html?x=/../../", "bar.html;jsessionid=1234?x=/../../");
    }


    @Test
    public void testEncodeURL09() throws Exception {
        doTestEncodeURL("./.?x=/../../", "./.;jsessionid=1234?x=/../../");
    }


    @Test
    public void testEncodeURL10() throws Exception {
        doTestEncodeURL("./..?x=/../../", "./..;jsessionid=1234?x=/../../");
    }


    @Test
    public void testEncodeURL11() throws Exception {
        doTestEncodeURL("./..?x=/../..", "./..;jsessionid=1234?x=/../..");
    }


    @Test
    public void testEncodeURL12() throws Exception {
        doTestEncodeURL("bar.html#/../", "bar.html;jsessionid=1234#/../");
    }


    @Test
    public void testEncodeURL13() throws Exception {
        doTestEncodeURL("bar.html#/../../", "bar.html;jsessionid=1234#/../../");
    }


    @Test
    public void testEncodeURL14() throws Exception {
        doTestEncodeURL("./.#/../../", "./.;jsessionid=1234#/../../");
    }


    @Test
    public void testEncodeURL15() throws Exception {
        doTestEncodeURL("./..#/../../", "./..;jsessionid=1234#/../../");
    }


    @Test
    public void testEncodeURL16() throws Exception {
        doTestEncodeURL("./..#/../..", "./..;jsessionid=1234#/../..");
    }


    private void doTestEncodeRedirectURL(String location, String expected) {
        Request req = new TesterRequest(true);
        req.setRequestedSessionId("1234");
        req.setRequestedSessionURL(true);
        Response resp = new Response(null);
        resp.setRequest(req);

        String result = resp.encodeRedirectURL(location);
        Assert.assertEquals(expected, result);
    }


    @Test
    public void testEncodeRedirectURL01() throws Exception {
        doTestEncodeRedirectURL("./bar.html", "./bar.html;jsessionid=1234");
    }


    @Test
    public void testEncodeRedirectURL02() throws Exception {
        doTestEncodeRedirectURL(".", ".;jsessionid=1234");
    }


    @Test
    public void testEncodeRedirectURL03() throws Exception {
        doTestEncodeRedirectURL("..", "..;jsessionid=1234");
    }


    @Test
    public void testEncodeRedirectURL04() throws Exception {
        doTestEncodeRedirectURL(".././..", ".././..;jsessionid=1234");
    }


    @Test(expected=IllegalArgumentException.class)
    public void testEncodeRedirectURL05() throws Exception {
        doTestEncodeRedirectURL("../../..", "throws IAE");
    }


    @Test
    public void testEncodeRedirectURL06() throws Exception {
        doTestEncodeRedirectURL("bar.html", "bar.html;jsessionid=1234");
    }


    @Test
    public void testEncodeRedirectURL07() throws Exception {
        doTestEncodeRedirectURL("bar.html?x=/../", "bar.html;jsessionid=1234?x=/../");
    }


    @Test
    public void testEncodeRedirectURL08() throws Exception {
        doTestEncodeRedirectURL("bar.html?x=/../../", "bar.html;jsessionid=1234?x=/../../");
    }


    @Test
    public void testEncodeRedirectURL09() throws Exception {
        doTestEncodeRedirectURL("./.?x=/../../", "./.;jsessionid=1234?x=/../../");
    }


    @Test
    public void testEncodeRedirectURL10() throws Exception {
        doTestEncodeRedirectURL("./..?x=/../../", "./..;jsessionid=1234?x=/../../");
    }


    @Test
    public void testEncodeRedirectURL11() throws Exception {
        doTestEncodeRedirectURL("./..?x=/../..", "./..;jsessionid=1234?x=/../..");
    }


    @Test
    public void testEncodeRedirectURL12() throws Exception {
        doTestEncodeRedirectURL("bar.html#/../", "bar.html;jsessionid=1234#/../");
    }


    @Test
    public void testEncodeRedirectURL13() throws Exception {
        doTestEncodeRedirectURL("bar.html#/../../", "bar.html;jsessionid=1234#/../../");
    }


    @Test
    public void testEncodeRedirectURL14() throws Exception {
        doTestEncodeRedirectURL("./.#/../../", "./.;jsessionid=1234#/../../");
    }


    @Test
    public void testEncodeRedirectURL15() throws Exception {
        doTestEncodeRedirectURL("./..#/../../", "./..;jsessionid=1234#/../../");
    }


    @Test
    public void testEncodeRedirectURL16() throws Exception {
        doTestEncodeURL("./..#/../..", "./..;jsessionid=1234#/../..");
    }


    @Test
    public void testSendRedirect01() throws Exception {
        doTestSendRedirect("../foo", "../foo");
    }


    @Test
    public void testSendRedirect02() throws Exception {
        doTestSendRedirect("../foo bar", "../foo bar");
    }


    @Test
    public void testSendRedirect03() throws Exception {
        doTestSendRedirect("../foo%20bar", "../foo%20bar");
    }


    private void doTestSendRedirect(String input, String expectedLocation) throws Exception {
        // Set-up.
        // Note: Not sufficient for testing relative -> absolute
        Connector connector = new Connector();
        org.apache.coyote.Response cResponse = new org.apache.coyote.Response();
        Response response = new Response(cResponse);
        org.apache.coyote.Request cRequest = new org.apache.coyote.Request();
        Request request = new Request(connector, cRequest);
        Context context = new TesterContext();
        request.getMappingData().context = context;
        response.setRequest(request);
        // Do test
        response.sendRedirect(input);
        String location = response.getHeader("Location");
        Assert.assertEquals(expectedLocation,  location);
    }


    @Test
    public void testBug53469a() throws Exception {
        Request req = new TesterRequest();
        Response resp = new Response(null);
        resp.setRequest(req);

        String result = resp.encodeURL("../bar.html");

        Assert.assertEquals("../bar.html", result);
    }


    @Test
    public void testBug53469b() throws Exception {
        Request req = new TesterRequest();
        Response resp = new Response(null);
        resp.setRequest(req);

        String result = resp.encodeURL("../../../../bar.html");

        Assert.assertEquals("../../../../bar.html", result);
    }


    private static final String ISO_8859_1 = StandardCharsets.ISO_8859_1.name();
    private static final String UTF_8 = StandardCharsets.UTF_8.name();
    private static final String UNKNOWN = "unknown";
    private static final String TEXT = "text/plain";
    private static final String TEXT_ISO_8859_1 = TEXT + ";charset=" + ISO_8859_1;
    private static final String TEXT_UTF_8 = TEXT + ";charset=" + UTF_8;
    private static final String TEXT_UNKNOWN = TEXT + ";charset=" + UNKNOWN;
    private static final Locale UNDETERMINED = Locale.forLanguageTag("xxx");

    @Test
    public void testSetCharacterEncoding01() {
        Response response = setupResponse();

        // Check default
        Assert.assertEquals(ISO_8859_1, response.getCharacterEncoding());
    }


    @Test
    public void testSetCharacterEncoding02() {
        Response response = setupResponse();

        // Check multiple calls
        Assert.assertEquals(ISO_8859_1, response.getCharacterEncoding());
        response.setCharacterEncoding(UTF_8);
        Assert.assertEquals(UTF_8, response.getCharacterEncoding());
        response.setCharacterEncoding(ISO_8859_1);
        Assert.assertEquals(ISO_8859_1, response.getCharacterEncoding());
    }


    @Test
    public void testSetCharacterEncoding03() throws IOException {
        Response response = setupResponse();

        // Check after getWriter()
        Assert.assertEquals(ISO_8859_1, response.getCharacterEncoding());
        response.setCharacterEncoding(UTF_8);
        Assert.assertEquals(UTF_8, response.getCharacterEncoding());
        response.getWriter();
        response.setCharacterEncoding(ISO_8859_1);
        Assert.assertEquals(UTF_8, response.getCharacterEncoding());
    }


    @Test
    public void testSetCharacterEncoding04() throws IOException {
        Response response = setupResponse();

        // Check after commit
        Assert.assertEquals(ISO_8859_1, response.getCharacterEncoding());
        response.setCharacterEncoding(UTF_8);
        Assert.assertEquals(UTF_8, response.getCharacterEncoding());
        response.flushBuffer();
        response.setCharacterEncoding(ISO_8859_1);
        Assert.assertEquals(UTF_8, response.getCharacterEncoding());
    }


    @Test
    public void testSetCharacterEncoding05() {
        Response response = setupResponse();

        // Check calling with null
        Assert.assertEquals(ISO_8859_1, response.getCharacterEncoding());
        response.setCharacterEncoding(UTF_8);
        Assert.assertEquals(UTF_8, response.getCharacterEncoding());
        response.setCharacterEncoding((String) null);
        Assert.assertEquals(ISO_8859_1, response.getCharacterEncoding());
    }


    @Test(expected =  UnsupportedEncodingException.class)
    public void testSetCharacterEncoding06() throws IOException {
        Response response = setupResponse();

        // Check calling with an unknown character set and writer
        Assert.assertEquals(ISO_8859_1, response.getCharacterEncoding());
        response.setCharacterEncoding(UNKNOWN);
        Assert.assertEquals(UNKNOWN, response.getCharacterEncoding());
        response.getWriter();
    }


    @Test
    public void testSetCharacterEncoding07() throws IOException {
        Response response = setupResponse();

        // Check calling with an unknown character set
        Assert.assertEquals(ISO_8859_1, response.getCharacterEncoding());
        response.setCharacterEncoding(UNKNOWN);
        Assert.assertEquals(UNKNOWN, response.getCharacterEncoding());
        response.getOutputStream();
    }


    @Test
    public void testSetCharacterEncoding08() {
        Response response = setupResponse();

        // Check multiple calls with different methods
        Assert.assertEquals(ISO_8859_1, response.getCharacterEncoding());
        response.setCharacterEncoding(UTF_8);
        Assert.assertEquals(UTF_8, response.getCharacterEncoding());
        response.setContentType(TEXT_ISO_8859_1);
        Assert.assertEquals(ISO_8859_1, response.getCharacterEncoding());
        response.setContentType(TEXT_UTF_8);
        Assert.assertEquals(UTF_8, response.getCharacterEncoding());
        response.setCharacterEncoding(ISO_8859_1);
        Assert.assertEquals(ISO_8859_1, response.getCharacterEncoding());
    }


    @Test
    public void testSetContentType01() {
        Response response = setupResponse();

        // Check multiple calls
        Assert.assertEquals(ISO_8859_1, response.getCharacterEncoding());
        response.setContentType(TEXT_UTF_8);
        Assert.assertEquals(UTF_8, response.getCharacterEncoding());
        response.setContentType(TEXT_ISO_8859_1);
        Assert.assertEquals(ISO_8859_1, response.getCharacterEncoding());
    }


    @Test
    public void testSetContentType02() throws IOException {
        Response response = setupResponse();

        // Check after getWriter()
        Assert.assertEquals(ISO_8859_1, response.getCharacterEncoding());
        response.setContentType(TEXT_UTF_8);
        Assert.assertEquals(UTF_8, response.getCharacterEncoding());
        response.getWriter();
        response.setContentType(TEXT_ISO_8859_1);
        Assert.assertEquals(UTF_8, response.getCharacterEncoding());
    }


    @Test
    public void testSetContentType03() throws IOException {
        Response response = setupResponse();

        // Check after commit
        Assert.assertEquals(ISO_8859_1, response.getCharacterEncoding());
        response.setContentType(TEXT_UTF_8);
        Assert.assertEquals(UTF_8, response.getCharacterEncoding());
        response.flushBuffer();
        response.setContentType(TEXT_ISO_8859_1);
        Assert.assertEquals(UTF_8, response.getCharacterEncoding());
    }


    @Test
    public void testSetContentType04() {
        Response response = setupResponse();

        // Check calling with null
        Assert.assertEquals(ISO_8859_1, response.getCharacterEncoding());
        response.setContentType(TEXT_UTF_8);
        Assert.assertEquals(UTF_8, response.getCharacterEncoding());
        response.setContentType(null);
        Assert.assertEquals(ISO_8859_1, response.getCharacterEncoding());
    }


    @Test(expected =  UnsupportedEncodingException.class)
    public void testSetContentType05() throws IOException {
        Response response = setupResponse();
        response.getContext().addLocaleEncodingMappingParameter(Locale.UK.toLanguageTag(), UNKNOWN);

        // Check calling with an unknown character set and writer
        Assert.assertEquals(ISO_8859_1, response.getCharacterEncoding());
        response.setContentType(TEXT_UNKNOWN);
        Assert.assertEquals(UNKNOWN, response.getCharacterEncoding());
        response.getWriter();
    }


    @Test
    public void testSetContentType06() throws IOException {
        Response response = setupResponse();

        // Check calling with an unknown character set
        Assert.assertEquals(ISO_8859_1, response.getCharacterEncoding());
        response.setContentType(TEXT_UNKNOWN);
        Assert.assertEquals(UNKNOWN, response.getCharacterEncoding());
        response.getOutputStream();
    }


    @Test
    public void testSetLocale01() {
        Response response = setupResponse();

        // Check multiple calls
        Assert.assertEquals(ISO_8859_1, response.getCharacterEncoding());
        response.setLocale(Locale.CHINESE);
        Assert.assertEquals(UTF_8, response.getCharacterEncoding());
        response.setLocale(Locale.ENGLISH);
        Assert.assertEquals(ISO_8859_1, response.getCharacterEncoding());
    }


    @Test
    public void testSetLocale02() throws IOException {
        Response response = setupResponse();

        // Check after getWriter()
        Assert.assertEquals(ISO_8859_1, response.getCharacterEncoding());
        response.setLocale(Locale.CHINESE);
        Assert.assertEquals(UTF_8, response.getCharacterEncoding());
        response.getWriter();
        response.setLocale(Locale.ENGLISH);
        Assert.assertEquals(UTF_8, response.getCharacterEncoding());
    }


    @Test
    public void testSetLocale03() throws IOException {
        Response response = setupResponse();

        // Check after commit
        Assert.assertEquals(ISO_8859_1, response.getCharacterEncoding());
        response.setLocale(Locale.CHINESE);
        Assert.assertEquals(UTF_8, response.getCharacterEncoding());
        response.flushBuffer();
        response.setLocale(Locale.ENGLISH);
        Assert.assertEquals(UTF_8, response.getCharacterEncoding());
    }


    @Test
    public void testSetLocale04() {
        Response response = setupResponse();

        // Check calling with null
        Assert.assertEquals(ISO_8859_1, response.getCharacterEncoding());
        response.setLocale(Locale.CHINESE);
        Assert.assertEquals(UTF_8, response.getCharacterEncoding());
        response.setLocale(null);
        Assert.assertEquals(ISO_8859_1, response.getCharacterEncoding());
    }


    @Test(expected =  UnsupportedEncodingException.class)
    public void testSetLocale05() throws IOException {
        Response response = setupResponse();

        // Check calling with an unknown character set and writer
        Assert.assertEquals(ISO_8859_1, response.getCharacterEncoding());
        response.setLocale(UNDETERMINED);
        Assert.assertEquals(UNKNOWN, response.getCharacterEncoding());
        response.getWriter();
    }


    @Test
    public void testSetLocale06() throws IOException {
        Response response = setupResponse();

        // Check calling with an unknown character set
        Assert.assertEquals(ISO_8859_1, response.getCharacterEncoding());
        response.setLocale(UNDETERMINED);
        Assert.assertEquals(UNKNOWN, response.getCharacterEncoding());
        response.getOutputStream();
    }


    @Test
    public void testSetLocale07() {
        Response response = setupResponse();

        // Check setLocale() is over-ridden by setCharacterEncoding

        Assert.assertEquals(ISO_8859_1, response.getCharacterEncoding());

        // setLocale doesn't change previous value
        response.setCharacterEncoding(UTF_8);
        Assert.assertEquals(UTF_8, response.getCharacterEncoding());
        response.setLocale(Locale.ENGLISH);
        Assert.assertEquals(UTF_8, response.getCharacterEncoding());

        // Reset
        response.setCharacterEncoding((String) null);
        Assert.assertEquals(ISO_8859_1, response.getCharacterEncoding());

        // setLocale is over-ridden by setCharacterEncoding
        response.setLocale(Locale.CHINESE);
        Assert.assertEquals(UTF_8, response.getCharacterEncoding());
        response.setCharacterEncoding(ISO_8859_1);
        Assert.assertEquals(ISO_8859_1, response.getCharacterEncoding());
    }


    @Test
    public void testSetLocale08() {
        Response response = setupResponse();

        // Check setLocale() is over-ridden by setContentType

        Assert.assertEquals(ISO_8859_1, response.getCharacterEncoding());

        // setLocale doesn't change previous value
        response.setContentType(TEXT_UTF_8);
        Assert.assertEquals(UTF_8, response.getCharacterEncoding());
        response.setLocale(Locale.ENGLISH);
        Assert.assertEquals(UTF_8, response.getCharacterEncoding());

        // Reset
        response.setContentType(null);
        Assert.assertEquals(ISO_8859_1, response.getCharacterEncoding());

        // setLocale is over-ridden by setContentTpe
        response.setLocale(Locale.CHINESE);
        Assert.assertEquals(UTF_8, response.getCharacterEncoding());
        response.setContentType(TEXT_ISO_8859_1);
        Assert.assertEquals(ISO_8859_1, response.getCharacterEncoding());
    }

    @Test
    public void testStatusChange() throws Exception {
        // Setup Tomcat instance
        Tomcat tomcat = getTomcatInstance();

        // No file system docBase required
        Context ctx = getProgrammaticRootContext();

        Tomcat.addServlet(ctx, "servlet", new ErrorPageServlet());
        ctx.addServletMappingDecoded("/error", "servlet");
        ErrorPage servletErrorPage = new ErrorPage();
        servletErrorPage.setErrorCode(404);
        servletErrorPage.setLocation("/error");
        ctx.addErrorPage(servletErrorPage);

        tomcat.start();

        int rc = getUrl("http://localhost:" + getPort() + "/missing", new ByteChunk(), null);
        Assert.assertEquals(202, rc);
    }

    private Response setupResponse() {
        Connector connector = new Connector();
        org.apache.coyote.Response cResponse = new org.apache.coyote.Response();
        Response response = new Response(cResponse);
        org.apache.coyote.Request cRequest = new org.apache.coyote.Request();
        Request request = new Request(connector, cRequest);
        Context context = new TesterContext();
        request.getMappingData().context = context;
        response.setRequest(request);
        context.addLocaleEncodingMappingParameter(Locale.ENGLISH.getLanguage(), ISO_8859_1);
        context.addLocaleEncodingMappingParameter(Locale.CHINESE.getLanguage(), UTF_8);
        context.addLocaleEncodingMappingParameter(UNDETERMINED.toLanguageTag(), UNKNOWN);
        return response;
    }


    private static final class Bug52811Servlet extends HttpServlet {
        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {

            resp.setContentType("multipart/related;" +
                    "boundary=1_4F50BD36_CDF8C28;" +
                    "Start=\"<31671603.smil>\";" +
                    "Type=\"application/smil;charset=UTF-8\"");

            // Should be ISO-8859-1 because the charset in the above is part
            // of the Type parameter
            PrintWriter pw = resp.getWriter();
            if (resp.getCharacterEncoding().equals("ISO-8859-1")) {
                pw.print("OK");
            } else {
                pw.print("FAIL: " + resp.getCharacterEncoding());
            }
        }

    }

    private static final class ErrorPageServlet extends HttpServlet {
        private static final long serialVersionUID = 1L;
        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {
            if (resp.getStatus() == 404) {
                resp.setStatus(202);
            } else {
                resp.setStatus(500);
            }
        }
    }
}
