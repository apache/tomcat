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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.MultipartConfigElement;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Test;

import static org.apache.catalina.startup.SimpleHttpClient.CRLF;
import org.apache.catalina.Context;
import org.apache.catalina.Wrapper;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.filters.FailedRequestFilter;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.descriptor.web.FilterDef;
import org.apache.tomcat.util.descriptor.web.FilterMap;
import org.apache.tomcat.util.scan.StandardJarScanner;


public class TestParameterLimitValve extends TomcatBaseTest {

    @Test
    public void testSpecificUrlPatternLimit() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        StandardContext ctx = (StandardContext) getProgrammaticRootContext();

        ParameterLimitValve parameterLimitValve = new ParameterLimitValve();
        ctx.getPipeline().addValve(parameterLimitValve);
        parameterLimitValve.setUrlPatternLimits("/special/.*=2");

        Tomcat.addServlet(ctx, "snoop", new SnoopServlet());
        ctx.addServletMappingDecoded("/special/endpoint", "snoop");

        addFailedRequestFilter(ctx);

        tomcat.start();

        int rc = getUrl("http://localhost:" + getPort() + "/special/endpoint?param1=value1&param2=value2&param3=value3",
                new ByteChunk(), null);
        Assert.assertEquals(400, rc);

        rc = getUrl("http://localhost:" + getPort() + "/special/endpoint?param1=value1&param2=value2", new ByteChunk(),
                null);
        Assert.assertEquals(200, rc);

        byte[] body = ("POST / HTTP/1.1" + CRLF + "Host: localhost:" + getPort() + CRLF + "Connection: close" + CRLF +
                "Transfer-Encoding: chunked" + CRLF + "Content-Type: application/x-www-form-urlencoded" + CRLF + CRLF +
                "param1=value1&param2=value2&param3=value3" + CRLF).getBytes(StandardCharsets.UTF_8);

        rc = postUrl(body, "http://localhost:" + getPort() + "/special/endpoint", new ByteChunk(), null);

        Assert.assertEquals(400, rc);

        body = ("POST / HTTP/1.1" + CRLF + "Host: localhost:" + getPort() + CRLF + "Connection: close" + CRLF +
                "Transfer-Encoding: chunked" + CRLF + "Content-Type: application/x-www-form-urlencoded" + CRLF + CRLF +
                "param1=value1&param2=value2" + CRLF).getBytes(StandardCharsets.UTF_8);

        rc = postUrl(body, "http://localhost:" + getPort() + "/special/endpoint", new ByteChunk(), null);

        Assert.assertEquals(200, rc);

        body = ("POST / HTTP/1.1" + CRLF + "Host: localhost:" + getPort() + CRLF + "Connection: close" + CRLF +
                "Transfer-Encoding: chunked" + CRLF + "Content-Type: application/x-www-form-urlencoded" + CRLF + CRLF +
                "param1=value1&param2=value2" + CRLF).getBytes(StandardCharsets.UTF_8);

        rc = postUrl(body, "http://localhost:" + getPort() + "/special/endpoint?param3=value3", new ByteChunk(), null);

        Assert.assertEquals(400, rc);

        body = ("POST / HTTP/1.1" + CRLF + "Host: localhost:" + getPort() + CRLF + "Connection: close" + CRLF +
                "Transfer-Encoding: chunked" + CRLF + "Content-Type: application/x-www-form-urlencoded" + CRLF + CRLF +
                "param1=value1" + CRLF).getBytes(StandardCharsets.UTF_8);

        rc = postUrl(body, "http://localhost:" + getPort() + "/special/endpoint?param2=value2", new ByteChunk(), null);

        Assert.assertEquals(200, rc);

        rc = getUrl("http://localhost:" + getPort() + "/special/endpoint", new ByteChunk(), null);

        Assert.assertEquals(200, rc);
    }

    @Test
    public void testMultipleEqualsPatternLimit() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        StandardContext ctx = (StandardContext) getProgrammaticRootContext();

        ParameterLimitValve parameterLimitValve = new ParameterLimitValve();
        ctx.getPipeline().addValve(parameterLimitValve);
        parameterLimitValve.setUrlPatternLimits("/special====2");

        Tomcat.addServlet(ctx, "snoop", new SnoopServlet());
        ctx.addServletMappingDecoded("/special===", "snoop");

        addFailedRequestFilter(ctx);

        tomcat.start();

        int rc = getUrl("http://localhost:" + getPort() + "/special===?param1=value1&param2=value2", new ByteChunk(),
                null);

        Assert.assertEquals(200, rc);
    }

    @Test
    public void testEncodedUrlPatternLimit() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        StandardContext ctx = (StandardContext) getProgrammaticRootContext();

        ParameterLimitValve parameterLimitValve = new ParameterLimitValve();
        ctx.getPipeline().addValve(parameterLimitValve);
        parameterLimitValve.setUrlPatternLimits("/special%20endpoint=2");

        Tomcat.addServlet(ctx, "snoop", new SnoopServlet());
        ctx.addServletMappingDecoded("/special endpoint", "snoop");

        addFailedRequestFilter(ctx);

        tomcat.start();

        int rc = getUrl(
                "http://localhost:" + getPort() + "/special%20endpoint?param1=value1&param2=value2&param3=value3",
                new ByteChunk(), null);

        Assert.assertEquals(400, rc);
    }

    @Test
    public void testMultipleSpecificUrlPatternsLimit() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        tomcat.getConnector().setMaxParameterCount(2);

        StandardContext ctx = (StandardContext) getProgrammaticRootContext();

        ParameterLimitValve parameterLimitValve = new ParameterLimitValve();
        ctx.getPipeline().addValve(parameterLimitValve);
        parameterLimitValve
                .setUrlPatternLimits("/special/.*=2" + CRLF + "/special2/.*=3" + CRLF + "/my/special/url1=1");

        Tomcat.addServlet(ctx, "snoop", new SnoopServlet());
        ctx.addServletMappingDecoded("/special/endpoint", "snoop");
        ctx.addServletMappingDecoded("/special2/endpoint", "snoop");
        ctx.addServletMappingDecoded("/my/special/url1", "snoop");
        ctx.addServletMappingDecoded("/my/special/url2", "snoop");

        addFailedRequestFilter(ctx);

        tomcat.start();

        int rc = getUrl("http://localhost:" + getPort() + "/special/endpoint?param1=value1&param2=value2&param3=value3",
                new ByteChunk(), null);
        Assert.assertEquals(400, rc);

        rc = getUrl(
                "http://localhost:" + getPort() +
                        "/special2/endpoint?param1=value1&param2=value2&param3=value3&param4=value4",
                new ByteChunk(), null);
        Assert.assertEquals(400, rc);

        rc = getUrl("http://localhost:" + getPort() + "/my/special/url1?param1=value1&param2=value2", new ByteChunk(),
                null);
        Assert.assertEquals(400, rc);

        rc = getUrl("http://localhost:" + getPort() + "/my/special/url2?param1=value1&param2=value2&param3=value3",
                new ByteChunk(), null);
        Assert.assertEquals(400, rc);

        rc = getUrl("http://localhost:" + getPort() + "/special/endpoint?param1=value1&param2=value2", new ByteChunk(),
                null);
        Assert.assertEquals(200, rc);

        rc = getUrl("http://localhost:" + getPort() + "/special2/endpoint?param1=value1&param2=value2&param3=value3",
                new ByteChunk(), null);
        Assert.assertEquals(200, rc);

        rc = getUrl("http://localhost:" + getPort() + "/my/special/url1?param1=value1", new ByteChunk(), null);
        Assert.assertEquals(200, rc);

        rc = getUrl("http://localhost:" + getPort() + "/my/special/url2?param1=value1&param2=value2", new ByteChunk(),
                null);
        Assert.assertEquals(200, rc);
    }

    @Test
    public void testNoMatchingPatternWithConnectorLimit() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        tomcat.getConnector().setMaxParameterCount(1);

        StandardContext ctx = (StandardContext) getProgrammaticRootContext();

        ParameterLimitValve parameterLimitValve = new ParameterLimitValve();
        ctx.getPipeline().addValve(parameterLimitValve);
        parameterLimitValve.setUrlPatternLimits("/special/.*=2");

        Tomcat.addServlet(ctx, "snoop", new SnoopServlet());
        ctx.addServletMappingDecoded("/other/endpoint", "snoop");

        addFailedRequestFilter(ctx);

        tomcat.start();

        int rc = getUrl("http://localhost:" + getPort() + "/other/endpoint?param1=value1&param2=value2",
                new ByteChunk(), null);

        Assert.assertEquals(400, rc);
    }

    @Test
    public void testUrlPatternLimitsFromFile() throws Exception {
        File configFile = File.createTempFile("parameter_limit", ".config");
        try (PrintWriter writer = new PrintWriter(new FileWriter(configFile))) {
            writer.println("# Commented line - empty line follows");
            writer.println("");
            writer.println("/api/.*=2");
            writer.println("# Commented line");
        }

        Tomcat tomcat = getTomcatInstance();
        StandardContext ctx = (StandardContext) getProgrammaticRootContext();

        ParameterLimitValve parameterLimitValve = new ParameterLimitValve();
        ctx.getPipeline().addValve(parameterLimitValve);

        try (BufferedReader reader = new BufferedReader(new FileReader(configFile))) {
            parameterLimitValve.setUrlPatternLimits(reader);
        }

        Tomcat.addServlet(ctx, "snoop", new SnoopServlet());
        ctx.addServletMappingDecoded("/api/test", "snoop");

        addFailedRequestFilter(ctx);

        tomcat.start();

        int rc = getUrl("http://localhost:" + getPort() + "/api/test?param1=value1&param2=value2", new ByteChunk(),
                null);
        Assert.assertEquals(200, rc);

        rc = getUrl("http://localhost:" + getPort() + "/api/test?param1=value1&param2=value2&param3=value3",
                new ByteChunk(), null);
        Assert.assertEquals(400, rc);
    }

    @Test
    public void testUrlPatternLimitsWithEmptyFile() throws Exception {
        File configFile = File.createTempFile("parameter_limit", ".config");

        Tomcat tomcat = getTomcatInstance();
        StandardContext ctx = (StandardContext) getProgrammaticRootContext();

        ParameterLimitValve parameterLimitValve = new ParameterLimitValve();
        ctx.getPipeline().addValve(parameterLimitValve);

        try (BufferedReader reader = new BufferedReader(new FileReader(configFile))) {
            parameterLimitValve.setUrlPatternLimits(reader);
        }

        Tomcat.addServlet(ctx, "snoop", new SnoopServlet());
        ctx.addServletMappingDecoded("/api/test", "snoop");

        addFailedRequestFilter(ctx);

        tomcat.start();

        int rc = getUrl("http://localhost:" + getPort() + "/api/test?param1=value1&param2=value2", new ByteChunk(),
                null);
        Assert.assertEquals(200, rc);
    }

    @Test
    public void testUrlPatternLimitsFromFileAndProperty() throws Exception {
        File configFile = File.createTempFile("parameter_limit", ".config");
        try (PrintWriter writer = new PrintWriter(new FileWriter(configFile))) {
            writer.println("# Commented line");
            writer.println("/api/.*=2");
            writer.println("# Commented line");
        }

        Tomcat tomcat = getTomcatInstance();
        StandardContext ctx = (StandardContext) getProgrammaticRootContext();

        ParameterLimitValve parameterLimitValve = new ParameterLimitValve();
        ctx.getPipeline().addValve(parameterLimitValve);

        parameterLimitValve.setUrlPatternLimits("/admin/.*=2");

        try (BufferedReader reader = new BufferedReader(new FileReader(configFile))) {
            parameterLimitValve.setUrlPatternLimits(reader);
        }

        Tomcat.addServlet(ctx, "snoop", new SnoopServlet());
        ctx.addServletMappingDecoded("/api/test", "snoop");
        ctx.addServletMappingDecoded("/admin/test", "snoop");

        addFailedRequestFilter(ctx);

        tomcat.start();

        int rc = getUrl("http://localhost:" + getPort() + "/api/test?param1=value1&param2=value2", new ByteChunk(),
                null);
        Assert.assertEquals(200, rc);

        rc = getUrl("http://localhost:" + getPort() + "/admin/test?param1=value1&param2=value2", new ByteChunk(), null);
        Assert.assertEquals(200, rc);

        rc = getUrl("http://localhost:" + getPort() + "/api/test?param1=value1&param2=value2&param3=value3",
                new ByteChunk(), null);
        Assert.assertEquals(400, rc);

        rc = getUrl("http://localhost:" + getPort() + "/admin/test?param1=value1&param2=value2&param3=value3",
                new ByteChunk(), null);
        Assert.assertEquals(400, rc);
    }

    @Test
    public void testServerUrlPatternLimit() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        StandardContext ctx = (StandardContext) getProgrammaticRootContext();

        ParameterLimitValve parameterLimitValve = new ParameterLimitValve();
        ctx.getParent().getPipeline().addValve(parameterLimitValve);
        parameterLimitValve.setUrlPatternLimits("/.*=2");

        Tomcat.addServlet(ctx, "snoop", new SnoopServlet());
        ctx.addServletMappingDecoded("/special/endpoint", "snoop");

        addFailedRequestFilter(ctx);

        tomcat.start();

        int rc = getUrl("http://localhost:" + getPort() + "/special/endpoint?param1=value1&param2=value2&param3=value3",
                new ByteChunk(), null);

        Assert.assertEquals(400, rc);

        rc = getUrl("http://localhost:" + getPort() + "/special/endpoint?param1=value1&param2=value2", new ByteChunk(),
                null);

        Assert.assertEquals(200, rc);
    }

    @Test
    public void testServerAndContextUrlPatternLimit() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        Context ctx1 = tomcat.addContext("context1", null);
        ((StandardJarScanner) ctx1.getJarScanner()).setScanClassPath(false);

        Context ctx2 = tomcat.addContext("context2", null);
        ((StandardJarScanner) ctx2.getJarScanner()).setScanClassPath(false);

        Context ctx3 = tomcat.addContext("context3", null);
        ((StandardJarScanner) ctx2.getJarScanner()).setScanClassPath(false);

        ParameterLimitValve serverParameterLimitValve = new ParameterLimitValve();
        ParameterLimitValve contextParameterLimitValve = new ParameterLimitValve();
        ParameterLimitValve context3ParameterLimitValve = new ParameterLimitValve();

        ctx1.getParent().getPipeline().addValve(serverParameterLimitValve);

        ctx1.getPipeline().addValve(contextParameterLimitValve);
        ctx3.getPipeline().addValve(context3ParameterLimitValve);

        serverParameterLimitValve.setUrlPatternLimits("/.*=2");
        contextParameterLimitValve.setUrlPatternLimits("/special/.*=3");
        context3ParameterLimitValve.setUrlPatternLimits("/special/.*=1");

        Tomcat.addServlet(ctx1, "snoop", new SnoopServlet());
        ctx1.addServletMappingDecoded("/special/endpoint", "snoop");

        Tomcat.addServlet(ctx2, "snoop", new SnoopServlet());
        ctx2.addServletMappingDecoded("/special/endpoint", "snoop");

        Tomcat.addServlet(ctx3, "snoop", new SnoopServlet());
        ctx3.addServletMappingDecoded("/special/endpoint", "snoop");

        addFailedRequestFilter(ctx1);
        addFailedRequestFilter(ctx2);
        addFailedRequestFilter(ctx3);

        tomcat.start();

        int rc = getUrl("http://localhost:" + getPort() +
                "/context1/special/endpoint?param1=value1&param2=value2&param3=value3", new ByteChunk(), null);

        Assert.assertEquals(200, rc);

        rc = getUrl("http://localhost:" + getPort() +
                "/context2/special/endpoint?param1=value1&param2=value2&param3=value3", new ByteChunk(), null);

        Assert.assertEquals(400, rc);

        rc = getUrl("http://localhost:" + getPort() + "/context3/special/endpoint?param1=value1&param2=value2",
                new ByteChunk(), null);

        Assert.assertEquals(400, rc);
    }


    @Test
    public void testMultipart() throws Exception {
        doTestMultipart(50,  10,  512, true);
    }


    @Test
    public void testMultipartParameterLimitExceeded01() throws Exception {
        doTestMultipart(1,  10,  512, false);
    }


    @Test
    public void testMultipartParameterLimitExceeded02() throws Exception {
        doTestMultipart(5,  10,  512, false);
    }


    @Test
    public void testMultipartPartLimitExceeded() throws Exception {
        doTestMultipart(50,  1,  512, false);
    }


    @Test
    public void testMultipartPartHeaderSizeLimitExceeded() throws Exception {
        doTestMultipart(50,  10,  1, false);
    }


    private void doTestMultipart(int maxParameterCount, int maxPartCount, int maxPartHeaderSize, boolean okExpected) throws Exception {

        Tomcat tomcat = getTomcatInstance();
        StandardContext ctx = (StandardContext) getProgrammaticRootContext();

        ParameterLimitValve parameterLimitValve = new ParameterLimitValve();
        ctx.getPipeline().addValve(parameterLimitValve);
        parameterLimitValve.setUrlPatternLimits("/upload/.*=" + Integer.toString(maxParameterCount) + "," +
                Integer.toString(maxPartCount) + "," + Integer.toString(maxPartHeaderSize));

        Wrapper w = Tomcat.addServlet(ctx, "multipart", new MultipartServlet());
        // Use defaults for Multipart
        w.setMultipartConfigElement(new MultipartConfigElement(""));
        ctx.addServletMappingDecoded("/upload/*", "multipart");

        addFailedRequestFilter(ctx);

        tomcat.start();

        // Construct a simple multipart body with two parts
        String boundary = "--simpleBoundary";

        String content = "--" + boundary + CRLF +
                "Content-Disposition: form-data; name=\"part1\"" + CRLF + CRLF +
                "part value 1" + CRLF +
                "--" + boundary + CRLF +
                "Content-Disposition: form-data; name=\"part2\"" + CRLF + CRLF +
                "part value 2" + CRLF +                "--" + boundary + "--" + CRLF;

        Map<String,List<String>> reqHeaders = new HashMap<>();
        reqHeaders.put("Content-Type", Arrays.asList(new String[] { "multipart/form-data; boundary=" + boundary }));
        reqHeaders.put("Content-Length", Arrays.asList(new String[] { Integer.toString(content.length())}));

        int rc = postUrl(content.getBytes(), "http://localhost:" + getPort() + "/upload/endpoint?" +
                "param1=value1&param2=value2&param3=value3&param4=value4",
                new ByteChunk(), reqHeaders, null);

        if (okExpected) {
            Assert.assertEquals(HttpServletResponse.SC_OK, rc);
        } else {
            Assert.assertTrue(Integer.toString(rc),
                    rc == HttpServletResponse.SC_BAD_REQUEST || rc == HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE ||
                    rc == HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }


    @Test
    public void testMaxParameterCountLimitExceeded01_02_00_00() throws Exception {
        doTestMaxParameterCountLimitExceeded(1, 2, 0, 0, false);
    }


    @Test
    public void testMaxParameterCountLimitExceeded01_00_02_00() throws Exception {
        doTestMaxParameterCountLimitExceeded(1, 0, 2, 0, false);
    }


    @Test
    public void testMaxParameterCountLimitExceeded01_00_00_02() throws Exception {
        doTestMaxParameterCountLimitExceeded(1, 0, 0, 2, false);
    }


    @Test
    public void testMaxParameterCountLimitExceeded01_01_00_00() throws Exception {
        doTestMaxParameterCountLimitExceeded(1, 1, 0, 0, true);
    }


    @Test
    public void testMaxParameterCountLimitExceeded01_00_01_00() throws Exception {
        doTestMaxParameterCountLimitExceeded(1, 0, 1, 0, true);
    }


    @Test
    public void testMaxParameterCountLimitExceeded01_00_00_01() throws Exception {
        doTestMaxParameterCountLimitExceeded(1, 0, 0, 1, true);
    }


    @Test
    public void testMaxParameterCountLimitExceeded02_01_01_00() throws Exception {
        doTestMaxParameterCountLimitExceeded(2, 1, 1, 0, true);
    }


    @Test
    public void testMaxParameterCountLimitExceeded02_01_0_01() throws Exception {
        doTestMaxParameterCountLimitExceeded(2, 1, 0, 1, true);
    }


    @Test
    public void testMaxParameterCountLimitExceeded02_00_01_01() throws Exception {
        doTestMaxParameterCountLimitExceeded(2, 0, 1, 1, true);
    }


    @Test
    public void testMaxParameterCountLimitExceeded03_01_01_01() throws Exception {
        doTestMaxParameterCountLimitExceeded(3, 1, 1, 1, true);
    }


    private void doTestMaxParameterCountLimitExceeded(int maxParameterCount, int textPartCount, int filePartCount,
            int queryStringCount, boolean okExpected) throws Exception {

        Tomcat tomcat = getTomcatInstance();
        StandardContext ctx = (StandardContext) getProgrammaticRootContext();

        ParameterLimitValve parameterLimitValve = new ParameterLimitValve();
        ctx.getPipeline().addValve(parameterLimitValve);
        // Only looking to test maxParameterCount
        parameterLimitValve.setUrlPatternLimits("/upload/.*=" + Integer.toString(maxParameterCount) + ",-1,-1");

        Wrapper w = Tomcat.addServlet(ctx, "multipart", new MultipartServlet());
        // Use defaults for Multipart
        w.setMultipartConfigElement(new MultipartConfigElement(""));
        ctx.addServletMappingDecoded("/upload/*", "multipart");

        addFailedRequestFilter(ctx);

        tomcat.start();

        // Construct a simple multi-part body
        String boundary = "--simpleBoundary";

        StringBuilder content = new StringBuilder();
        int part = 1;

        for (int i = 0; i < textPartCount; i++) {
            content.append("--").append(boundary).append(CRLF);
            content.append("Content-Disposition: form-data; name=\"part").append(part).append("\"").append(CRLF);
            content.append(CRLF);
            content.append("part value ").append(part).append(CRLF);
            part++;
        }

        for (int i = 0; i < filePartCount; i++) {
            content.append("--").append(boundary).append(CRLF);
            content.append("Content-Disposition: form-data; name=\"part").append(part).append("\"; filename=\"part")
                    .append(part).append("\"").append(CRLF);
            content.append("Content-Type: text/plain").append(CRLF);
            content.append(CRLF);
            content.append("part value ").append(part).append(CRLF);
            part++;
        }

        content.append("--").append(boundary).append("--").append(CRLF);

        StringBuilder queryString = new StringBuilder();
        for (int i = 0; i < queryStringCount; i++) {
            if (i > 0) {
                queryString.append("&");
            }
            queryString.append("param");
            queryString.append(part);
            queryString.append("=value");
            queryString.append(part);
            part++;
        }


        Map<String,List<String>> reqHeaders = new HashMap<>();
        reqHeaders.put("Content-Type", List.of("multipart/form-data; boundary=" + boundary));
        reqHeaders.put("Content-Length", List.of(Integer.toString(content.length())));

        int rc = postUrl(content.toString().getBytes(), "http://localhost:" + getPort() + "/upload/endpoint?" +
                queryString.toString(), new ByteChunk(), reqHeaders, null);

        if (okExpected) {
            Assert.assertEquals(HttpServletResponse.SC_OK, rc);
        } else {
            Assert.assertTrue(Integer.toString(rc),
                    rc == HttpServletResponse.SC_BAD_REQUEST || rc == HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE ||
                    rc == HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    private static class MultipartServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        @Override
        protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            resp.setContentType("text/plain");
            resp.setCharacterEncoding("UTF-8");
            PrintWriter pw = resp.getWriter();
            pw.println("Parts: " + req.getParts().size());
            pw.println("Parameters: " + req.getParameterMap().size());
        }
    }


    private static void addFailedRequestFilter(Context context) {
        FilterDef failedRequestFilter = new FilterDef();
        failedRequestFilter.setFilterName("failedRequestFilter");
        failedRequestFilter.setFilterClass(FailedRequestFilter.class.getName());
        FilterMap failedRequestFilterMap = new FilterMap();
        failedRequestFilterMap.setFilterName("failedRequestFilter");
        failedRequestFilterMap.addURLPatternDecoded("/*");
        context.addFilterDef(failedRequestFilter);
        context.addFilterMap(failedRequestFilterMap);
    }
}
