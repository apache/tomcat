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
package org.apache.catalina.valves;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.core.StandardHost;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.json.JSONParser;

public class TestJsonErrorReportValve extends TomcatBaseTest {

    private static final String JSON_VALVE = "org.apache.catalina.valves.JsonErrorReportValve";


    @Test
    public void testJsonErrorResponse500() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        ((StandardHost) tomcat.getHost()).setErrorReportValveClass(JSON_VALVE);

        Context ctx = getProgrammaticRootContext();

        Tomcat.addServlet(ctx, "sendError", new SendErrorServlet(
                HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Server broke"));
        ctx.addServletMappingDecoded("/", "sendError");

        tomcat.start();

        ByteChunk res = new ByteChunk();
        Map<String, List<String>> resHead = new HashMap<>();
        int rc = getUrl("http://localhost:" + getPort(), res, resHead);

        Assert.assertEquals(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, rc);

        // Verify Content-Type
        List<String> contentType = resHead.get("Content-Type");
        Assert.assertNotNull("Content-Type header should be present", contentType);
        Assert.assertTrue("Content-Type should be application/json",
                contentType.get(0).contains("application/json"));

        // Parse and verify JSON
        String body = res.toString();
        JSONParser parser = new JSONParser(body);
        LinkedHashMap<String, Object> json = parser.parseObject();

        Assert.assertEquals("Status Report", json.get("type"));
        Assert.assertEquals(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, ((Number) json.get("status")).intValue());
        Assert.assertEquals("Server broke", json.get("message"));
        Assert.assertNotNull(json.get("description"));
    }


    @Test
    public void testJsonErrorWithThrowable() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        ((StandardHost) tomcat.getHost()).setErrorReportValveClass(JSON_VALVE);

        Context ctx = getProgrammaticRootContext();

        Tomcat.addServlet(ctx, "exception",
                new ExceptionServlet("Something went wrong"));
        ctx.addServletMappingDecoded("/", "exception");

        tomcat.start();

        ByteChunk res = new ByteChunk();
        int rc = getUrl("http://localhost:" + getPort(), res, null);

        Assert.assertEquals(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, rc);

        // Parse and verify JSON
        String body = res.toString();
        JSONParser parser = new JSONParser(body);
        LinkedHashMap<String, Object> json = parser.parseObject();

        Assert.assertEquals("Exception Report", json.get("type"));
        Assert.assertEquals(500,
                ((Number) json.get("status")).intValue());
        Assert.assertNotNull(json.get("throwable"));

        // throwable should be a list containing exception strings
        @SuppressWarnings("unchecked")
        ArrayList<Object> throwableList = (ArrayList<Object>) json.get("throwable");
        Assert.assertFalse("throwable array should not be empty",
                throwableList.isEmpty());

        String throwableStr = throwableList.toString();
        Assert.assertTrue("Response should contain exception class name",
                throwableStr.contains("RuntimeException"));
        Assert.assertTrue("Response should contain exception message",
                throwableStr.contains("Something went wrong"));
    }


    @Test
    public void testJsonErrorWithSpecialChars() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        ((StandardHost) tomcat.getHost()).setErrorReportValveClass(JSON_VALVE);

        Context ctx = getProgrammaticRootContext();

        // Characters that require JSON escaping: quotes and backslashes
        String specialMessage = "Error with \"quotes\" and \\backslash\\";
        Tomcat.addServlet(ctx, "specialChars", new SendErrorServlet(
                HttpServletResponse.SC_INTERNAL_SERVER_ERROR, specialMessage));
        ctx.addServletMappingDecoded("/", "specialChars");

        tomcat.start();

        ByteChunk res = new ByteChunk();
        int rc = getUrl("http://localhost:" + getPort(), res, null);

        Assert.assertEquals(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, rc);

        // Parse JSON - if escaping is broken, the parser will throw
        String body = res.toString();
        JSONParser parser = new JSONParser(body);
        LinkedHashMap<String, Object> json = parser.parseObject();

        Assert.assertEquals("Status Report", json.get("type"));
        Assert.assertEquals(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, ((Number) json.get("status")).intValue());

        // Verify the message field is present and contains the
        // expected substrings (the parser returns raw escaped values)
        String message = (String) json.get("message");
        Assert.assertNotNull("message should be present", message);
        Assert.assertTrue("message should contain quotes", message.contains("quotes"));
        Assert.assertTrue("message should contain backslash", message.contains("backslash"));
    }


    @Test
    public void testJsonCustomStatusCode() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        ((StandardHost) tomcat.getHost()).setErrorReportValveClass(JSON_VALVE);

        Context ctx = getProgrammaticRootContext();

        Tomcat.addServlet(ctx, "customError",
                new SendErrorServlet(999, "The sky is falling"));
        ctx.addServletMappingDecoded("/", "customError");

        tomcat.start();

        ByteChunk res = new ByteChunk();
        int rc = getUrl("http://localhost:" + getPort(), res, null);

        Assert.assertEquals(999, rc);

        // Parse and verify JSON
        String body = res.toString();
        JSONParser parser = new JSONParser(body);
        LinkedHashMap<String, Object> json = parser.parseObject();

        Assert.assertEquals(999, ((Number) json.get("status")).intValue());
        Assert.assertEquals("The sky is falling", json.get("message"));
        Assert.assertNotNull(json.get("description"));
    }


    @Test
    public void testJsonError404() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        ((StandardHost) tomcat.getHost()).setErrorReportValveClass(JSON_VALVE);

        Context ctx = getProgrammaticRootContext();

        Tomcat.addServlet(ctx, "notFound", new SendErrorServlet(
                HttpServletResponse.SC_NOT_FOUND, "Resource not found"));
        ctx.addServletMappingDecoded("/", "notFound");

        tomcat.start();

        ByteChunk res = new ByteChunk();
        res.setCharset(StandardCharsets.UTF_8);
        int rc = getUrl("http://localhost:" + getPort(), res, null);

        Assert.assertEquals(HttpServletResponse.SC_NOT_FOUND, rc);

        // Parse and verify JSON
        String body = res.toString();
        JSONParser parser = new JSONParser(body);
        LinkedHashMap<String, Object> json = parser.parseObject();

        Assert.assertEquals("Status Report", json.get("type"));
        Assert.assertEquals(404,
                ((Number) json.get("status")).intValue());
        Assert.assertEquals("Resource not found", json.get("message"));
        Assert.assertNotNull(json.get("description"));
    }


    @Test
    public void testJsonErrorWithChainedExceptions() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        ((StandardHost) tomcat.getHost()).setErrorReportValveClass(JSON_VALVE);

        Context ctx = getProgrammaticRootContext();

        Tomcat.addServlet(ctx, "chained", new ChainedExceptionServlet());
        ctx.addServletMappingDecoded("/", "chained");

        tomcat.start();

        ByteChunk res = new ByteChunk();
        res.setCharset(StandardCharsets.UTF_8);
        int rc = getUrl("http://localhost:" + getPort(), res, null);

        Assert.assertEquals(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, rc);

        // Parse and verify JSON
        String body = res.toString();
        JSONParser parser = new JSONParser(body);
        LinkedHashMap<String, Object> json = parser.parseObject();

        Assert.assertEquals("Exception Report", json.get("type"));
        Assert.assertNotNull(json.get("throwable"));

        // The throwable array should contain both the outer and inner exceptions
        @SuppressWarnings("unchecked")
        ArrayList<Object> throwableList = (ArrayList<Object>) json.get("throwable");
        String throwableStr = throwableList.toString();
        Assert.assertTrue("Response should contain outer exception",
                throwableStr.contains("RuntimeException"));
        Assert.assertTrue("Response should contain root cause",
                throwableStr.contains("IllegalStateException"));
        Assert.assertFalse("Catalina core classes should be filtered",
                throwableStr.contains("org.apache.catalina.core."));
    }

    @Test
    public void testJsonErrorWithoutMessage() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        ((StandardHost) tomcat.getHost()).setErrorReportValveClass(JSON_VALVE);

        Context ctx = getProgrammaticRootContext();

        Tomcat.addServlet(ctx, "noMessage", new SendErrorServlet(
                HttpServletResponse.SC_INTERNAL_SERVER_ERROR, null));
        ctx.addServletMappingDecoded("/", "noMessage");

        tomcat.start();

        ByteChunk res = new ByteChunk();
        int rc = getUrl("http://localhost:" + getPort(), res, null);

        Assert.assertEquals(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, rc);

        String body = res.toString();
        JSONParser parser = new JSONParser(body);
        LinkedHashMap<String, Object> json = parser.parseObject();

        Assert.assertEquals("Status Report", json.get("type"));
        Assert.assertEquals(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, ((Number) json.get("status")).intValue());
        Assert.assertEquals("", json.get("message"));
        Assert.assertNotNull(json.get("description"));
    }

    @Test
    public void testNoJsonBodyForNonErrorStatus() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        ((StandardHost) tomcat.getHost()).setErrorReportValveClass(JSON_VALVE);

        Context ctx = getProgrammaticRootContext();

        Tomcat.addServlet(ctx, "hello", new HelloWorldServlet());
        ctx.addServletMappingDecoded("/", "hello");

        tomcat.start();

        ByteChunk res = new ByteChunk();
        Map<String, List<String>> resHead = new HashMap<>();
        int rc = getUrl("http://localhost:" + getPort(), res, resHead);

        Assert.assertEquals(HttpServletResponse.SC_OK, rc);

        String body = res.toString();
        Assert.assertEquals(HelloWorldServlet.RESPONSE_TEXT, body);
    }

    @Test
    public void testJsonErrorWithUnicodeMessage() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        ((StandardHost) tomcat.getHost()).setErrorReportValveClass(JSON_VALVE);

        Context ctx = getProgrammaticRootContext();

        String unicodeMessage = "Error: \u00e9\u00e8\u00ea \u4e2d\u6587 \u00f1";
        Tomcat.addServlet(ctx, "unicode", new SendErrorServlet(
                HttpServletResponse.SC_INTERNAL_SERVER_ERROR, unicodeMessage));
        ctx.addServletMappingDecoded("/", "unicode");

        tomcat.start();

        ByteChunk res = new ByteChunk();
        res.setCharset(StandardCharsets.UTF_8);
        int rc = getUrl("http://localhost:" + getPort(), res, null);

        Assert.assertEquals(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, rc);

        String body = res.toString();
        JSONParser parser = new JSONParser(body);
        LinkedHashMap<String, Object> json = parser.parseObject();

        Assert.assertEquals(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, ((Number) json.get("status")).intValue());
        Assert.assertEquals(unicodeMessage, json.get("message"));
    }

    @Test
    public void testNoJsonForUnknownStatusWithoutMessage() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        ((StandardHost) tomcat.getHost()).setErrorReportValveClass(JSON_VALVE);

        Context ctx = getProgrammaticRootContext();

        Tomcat.addServlet(ctx, "unknownNoMessage", new SendErrorServlet(999, null));
        ctx.addServletMappingDecoded("/", "unknownNoMessage");

        tomcat.start();

        ByteChunk res = new ByteChunk();
        int rc = getUrl("http://localhost:" + getPort(), res, null);

        Assert.assertEquals(999, rc);

        String body = res.toString();
        Assert.assertTrue(body == null || body.isEmpty());
    }


    private static final class SendErrorServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;
        private final int statusCode;
        private final String message;

        private SendErrorServlet(int statusCode, String message) {
            this.statusCode = statusCode;
            this.message = message;
        }

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws IOException {
            if (message != null) {
                resp.sendError(statusCode, message);
            } else {
                resp.sendError(statusCode);
            }
        }
    }


    private static final class ExceptionServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;
        private final String message;

        private ExceptionServlet(String message) {
            this.message = message;
        }

        @Override
        public void service(ServletRequest request, ServletResponse response) {
            throw new RuntimeException(message);
        }
    }


    private static final class ChainedExceptionServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        @Override
        public void service(ServletRequest request, ServletResponse response) {
            throw new RuntimeException("Outer exception",
                    new IllegalStateException("Root cause"));
        }
    }
}
