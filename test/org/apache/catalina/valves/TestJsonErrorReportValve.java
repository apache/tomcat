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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.core.StandardHost;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.buf.ByteChunk;

public class TestJsonErrorReportValve extends TomcatBaseTest {

    private static final String JSON_VALVE =
            "org.apache.catalina.valves.JsonErrorReportValve";


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
        res.setCharset(StandardCharsets.UTF_8);
        Map<String, List<String>> resHead = new HashMap<>();
        int rc = getUrl("http://localhost:" + getPort(), res, resHead);

        Assert.assertEquals(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, rc);

        String body = res.toString();
        Assert.assertNotNull(body);
        // Verify JSON structure
        Assert.assertTrue("Response should contain type field",
                body.contains("\"type\":"));
        Assert.assertTrue("Response should contain status 500",
                body.contains("\"status\": 500"));
        Assert.assertTrue("Response should contain message",
                body.contains("\"message\": \"Server broke\""));
        Assert.assertTrue("Response should contain description field",
                body.contains("\"description\":"));

        // Verify Content-Type
        List<String> contentType = resHead.get("Content-Type");
        Assert.assertNotNull("Content-Type header should be present", contentType);
        Assert.assertTrue("Content-Type should be application/json",
                contentType.get(0).contains("application/json"));
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
        res.setCharset(StandardCharsets.UTF_8);
        int rc = getUrl("http://localhost:" + getPort(), res, null);

        Assert.assertEquals(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, rc);

        String body = res.toString();
        Assert.assertNotNull(body);
        Assert.assertTrue("Response should contain throwable field",
                body.contains("\"throwable\":"));
        Assert.assertTrue("Response should contain exception class name",
                body.contains("RuntimeException"));
        Assert.assertTrue("Response should contain exception message",
                body.contains("Something went wrong"));
    }


    @Test
    public void testJsonErrorWithSpecialChars() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        ((StandardHost) tomcat.getHost()).setErrorReportValveClass(JSON_VALVE);

        Context ctx = getProgrammaticRootContext();

        // Characters that require JSON escaping: quotes and backslashes
        String specialMessage =
                "Error with \"quotes\" and \\backslash\\ and <angle>";
        Tomcat.addServlet(ctx, "specialChars", new SendErrorServlet(
                HttpServletResponse.SC_INTERNAL_SERVER_ERROR, specialMessage));
        ctx.addServletMappingDecoded("/", "specialChars");

        tomcat.start();

        ByteChunk res = new ByteChunk();
        res.setCharset(StandardCharsets.UTF_8);
        int rc = getUrl("http://localhost:" + getPort(), res, null);

        Assert.assertEquals(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, rc);

        String body = res.toString();
        Assert.assertNotNull(body);
        // Verify that quotes and backslashes are escaped in the JSON output
        Assert.assertTrue("Double quotes should be escaped",
                body.contains("\\\"quotes\\\""));
        Assert.assertTrue("Backslashes should be escaped",
                body.contains("\\\\backslash\\\\"));
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
        res.setCharset(StandardCharsets.UTF_8);
        int rc = getUrl("http://localhost:" + getPort(), res, null);

        Assert.assertEquals(999, rc);

        String body = res.toString();
        Assert.assertNotNull(body);
        Assert.assertTrue("Response should contain custom status code",
                body.contains("\"status\": 999"));
        Assert.assertTrue("Response should contain custom message",
                body.contains("The sky is falling"));
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

        String body = res.toString();
        Assert.assertNotNull(body);
        Assert.assertTrue("Response should contain status 404",
                body.contains("\"status\": 404"));
        Assert.assertTrue("Response should contain message",
                body.contains("Resource not found"));
        Assert.assertTrue("Response should contain description",
                body.contains("\"description\":"));
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

        String body = res.toString();
        Assert.assertNotNull(body);
        Assert.assertTrue("Response should contain throwable field",
                body.contains("\"throwable\":"));
        // The throwable array should contain both the outer and inner exceptions
        Assert.assertTrue("Response should contain outer exception",
                body.contains("RuntimeException"));
        Assert.assertTrue("Response should contain root cause",
                body.contains("IllegalStateException"));
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
                throws ServletException, IOException {
            resp.sendError(statusCode, message);
        }
    }


    private static final class ExceptionServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;
        private final String message;

        private ExceptionServlet(String message) {
            this.message = message;
        }

        @Override
        public void service(ServletRequest request, ServletResponse response)
                throws IOException {
            throw new RuntimeException(message);
        }
    }


    private static final class ChainedExceptionServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        @Override
        public void service(ServletRequest request, ServletResponse response)
                throws IOException {
            throw new RuntimeException("Outer exception",
                    new IllegalStateException("Root cause"));
        }
    }
}
