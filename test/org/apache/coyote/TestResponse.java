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
package org.apache.coyote;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.buf.ByteChunk;

public class TestResponse extends TomcatBaseTest {

    private static final String CSP = "Content-Security-Policy";
    private static final String CSP_REPORT_ONLY = "Content-Security-Policy-Report-Only";


    /*
     * https://bz.apache.org/bugzilla/show_bug.cgi?id=61197
     */
    @Test
    public void testUserCharsetIsRetained() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        // No file system docBase required
        Context ctx = getProgrammaticRootContext();

        // Add  servlet
        Tomcat.addServlet(ctx, "CharsetServlet", new CharsetServlet());
        ctx.addServletMapping("/*", "CharsetServlet");

        tomcat.start();

        ByteChunk responseBody = new ByteChunk();
        Map<String,List<String>> responseHeaders = new HashMap<>();
        int rc = getUrl("http://localhost:" + getPort() + "/test?charset=uTf-8", responseBody,
                responseHeaders);

        Assert.assertEquals(HttpServletResponse.SC_OK, rc);

        String contentType = getSingleHeader("Content-Type", responseHeaders);
        Assert.assertEquals("text/plain;charset=uTf-8", contentType);
    }


    private static class CharsetServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {
            resp.setContentType("text/plain");
            resp.setCharacterEncoding(req.getParameter("charset"));

            resp.getWriter().print("OK");
        }
    }


    @Test
    public void testContentTypeWithSpace() throws Exception {
        doTestContentTypeSpacing(true);
    }


    @Test
    public void testContentTypeWithoutSpace() throws Exception {
        doTestContentTypeSpacing(false);
    }


    private void doTestContentTypeSpacing(boolean withSpace) throws Exception {
        Tomcat tomcat = getTomcatInstance();

        // No file system docBase required
        Context ctx = getProgrammaticRootContext();

        // Add  servlet
        Tomcat.addServlet(ctx, "ContentTypeServlet", new ContentTypeServlet());
        ctx.addServletMapping("/*", "ContentTypeServlet");

        tomcat.start();

        ByteChunk responseBody = new ByteChunk();
        Map<String,List<String>> responseHeaders = new HashMap<>();
        StringBuilder uri = new StringBuilder("http://localhost:");
        uri.append(getPort());
        uri.append("/test");
        if (withSpace) {
            uri.append("?withSpace=true");
        }
        int rc = getUrl(uri.toString(), responseBody, responseHeaders);

        Assert.assertEquals(HttpServletResponse.SC_OK, rc);

        String contentType = getSingleHeader("Content-Type", responseHeaders);
        StringBuilder expected = new StringBuilder("text/plain;");
        if (withSpace) {
            expected.append(' ');
        }
        expected.append("v=1;charset=UTF-8");
        Assert.assertEquals(expected.toString() , contentType);
    }


    @Test
    public void testGetContentLengthIntegerMaxValue() {
        Response response = new Response();

        Assert.assertEquals(-1, response.getContentLength());

        response.setContentLength(Integer.MAX_VALUE);
        Assert.assertEquals(Integer.MAX_VALUE, response.getContentLength());

        response.setContentLength(Integer.MAX_VALUE + 1L);
        Assert.assertEquals(-1, response.getContentLength());
    }


    @Test
    public void testNotModifiedExcludedHeaderRemoved() {
        Response response = createNotModifiedResponse(304, List.of(Map.entry(CSP, "script-src 'nonce-a'")));
        response.addHeader(CSP, "script-src 'nonce-a'");
        response.addHeader("X-Kept", "kept");

        response.removeNotModifiedExcludedHeaders();

        Assert.assertEquals(List.of(), getHeaderValues(response, CSP));
        Assert.assertEquals(List.of("kept"), getHeaderValues(response, "X-Kept"));
    }


    @Test
    public void testNotModifiedExcludedHeaderKeptForOtherStatus() {
        Response response = createNotModifiedResponse(200, List.of(Map.entry(CSP, "script-src 'nonce-a'")));
        response.addHeader(CSP, "script-src 'nonce-a'");

        response.removeNotModifiedExcludedHeaders();

        Assert.assertEquals(List.of("script-src 'nonce-a'"), getHeaderValues(response, CSP));
    }


    @Test
    public void testNotModifiedExcludedHeaderNoExactMatch() {
        Response response = createNotModifiedResponse(304, List.of(Map.entry(CSP, "script-src 'nonce-a'")));
        response.addHeader(CSP, "img-src 'self'");
        response.addHeader(CSP, "script-src 'nonce-b'");

        response.removeNotModifiedExcludedHeaders();

        Assert.assertEquals(List.of("img-src 'self'", "script-src 'nonce-b'"), getHeaderValues(response, CSP));
    }


    @Test
    public void testNotModifiedExcludedHeaderAllFieldsRemoved() {
        Response response = createNotModifiedResponse(304,
                List.of(Map.entry("content-security-policy", "script-src 'nonce-a'")));
        response.addHeader(CSP, "img-src 'self'");
        response.addHeader(CSP, "script-src 'nonce-a'");
        response.addHeader(CSP, "script-src 'nonce-b'");
        response.addHeader("X-Kept", "kept");

        response.removeNotModifiedExcludedHeaders();

        Assert.assertEquals(List.of(), getHeaderValues(response, CSP));
        Assert.assertEquals(List.of("kept"), getHeaderValues(response, "X-Kept"));
    }


    @Test
    public void testNotModifiedExcludedHeaderEntriesForTwoNames() {
        Response response = createNotModifiedResponse(304, List.of(Map.entry(CSP, "default-src 'none'"),
                Map.entry(CSP_REPORT_ONLY, "default-src 'none'"), Map.entry("X-Other", "a")));
        response.addHeader(CSP, "default-src 'none'");
        response.addHeader(CSP, "default-src 'none'");
        response.addHeader(CSP_REPORT_ONLY, "default-src 'none'");
        response.addHeader("X-Other", "b");

        response.removeNotModifiedExcludedHeaders();

        Assert.assertEquals(List.of(), getHeaderValues(response, CSP));
        Assert.assertEquals(List.of(), getHeaderValues(response, CSP_REPORT_ONLY));
        Assert.assertEquals(List.of("b"), getHeaderValues(response, "X-Other"));
    }


    @Test
    public void testNotModifiedExcludedHeaderMalformedNoteIgnored() {
        // Not a collection
        Response response = createNotModifiedResponse(304, "default-src 'none'");
        response.addHeader(CSP, "default-src 'none'");
        response.removeNotModifiedExcludedHeaders();
        Assert.assertEquals(List.of("default-src 'none'"), getHeaderValues(response, CSP));

        // Elements that are not a Map.Entry of a String name and value
        response = createNotModifiedResponse(304, List.of("default-src 'none'", Map.entry(CSP, Integer.valueOf(1))));
        response.addHeader(CSP, "default-src 'none'");
        response.removeNotModifiedExcludedHeaders();
        Assert.assertEquals(List.of("default-src 'none'"), getHeaderValues(response, CSP));
    }


    @Test
    public void testNotModifiedExcludedHeaderWithoutNote() {
        Response response = createNotModifiedResponse(304, null);
        response.addHeader(CSP, "default-src 'none'");

        response.removeNotModifiedExcludedHeaders();

        Assert.assertEquals(List.of("default-src 'none'"), getHeaderValues(response, CSP));
    }


    private static Response createNotModifiedResponse(int status, Object note) {
        Request request = new Request();
        if (note != null) {
            request.setAttribute(Constants.NOT_MODIFIED_EXCLUDED_HEADERS_ATTR, note);
        }
        Response response = new Response();
        response.setRequest(request);
        response.setStatus(status);
        return response;
    }


    private static List<String> getHeaderValues(Response response, String name) {
        return Collections.list(response.getMimeHeaders().values(name));
    }


    private static class ContentTypeServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {

            if (req.getParameter("withSpace") == null) {
                resp.setContentType("text/plain;v=1");
            } else {
                resp.setContentType("text/plain; v=1");
            }
            resp.setCharacterEncoding("UTF-8");

            resp.getWriter().print("OK");
        }
    }
}
