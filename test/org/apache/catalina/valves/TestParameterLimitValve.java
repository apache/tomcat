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
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.ServletRequestParametersBaseTest;

import org.junit.Assert;
import org.junit.Test;

import static org.apache.catalina.startup.SimpleHttpClient.CRLF;
import org.apache.catalina.Context;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.startup.Tomcat;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.scan.StandardJarScanner;


public class TestParameterLimitValve extends ServletRequestParametersBaseTest {

    @Test
    public void testSpecificUrlPatternLimit() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        StandardContext ctx = (StandardContext) getProgrammaticRootContext();

        ParameterLimitValve parameterLimitValve = new ParameterLimitValve();
        ctx.getPipeline().addValve(parameterLimitValve);
        parameterLimitValve.setUrlPatternLimits("/special/.*=2");

        Tomcat.addServlet(ctx, "snoop", new SnoopServlet());
        ctx.addServletMappingDecoded("/special/endpoint", "snoop");

        tomcat.start();

        int rc = getUrl("http://localhost:" + getPort() +
                "/special/endpoint?param1=value1&param2=value2&param3=value3",
            new ByteChunk(), null);
        Assert.assertEquals(400, rc);

        rc = getUrl("http://localhost:" + getPort() + "/special/endpoint?param1=value1&param2=value2",
            new ByteChunk(), null);
        Assert.assertEquals(200, rc);

        byte[] body = ("POST / HTTP/1.1" + CRLF +
            "Host: localhost:" + getPort() + CRLF +
            "Connection: close" + CRLF +
            "Transfer-Encoding: chunked" + CRLF +
            "Content-Type: application/x-www-form-urlencoded" + CRLF +
            CRLF +
            "param1=value1&param2=value2&param3=value3" + CRLF).getBytes(StandardCharsets.UTF_8);

        rc = postUrl(body,"http://localhost:" + getPort() + "/special/endpoint",
            new ByteChunk(), null);

        Assert.assertEquals(400, rc);

        body = ("POST / HTTP/1.1" + CRLF +
            "Host: localhost:" + getPort() + CRLF +
            "Connection: close" + CRLF +
            "Transfer-Encoding: chunked" + CRLF +
            "Content-Type: application/x-www-form-urlencoded" + CRLF +
            CRLF +
            "param1=value1&param2=value2" + CRLF).getBytes(StandardCharsets.UTF_8);

        rc = postUrl(body, "http://localhost:" + getPort() + "/special/endpoint",
            new ByteChunk(), null);

        Assert.assertEquals(200, rc);

        body = ("POST / HTTP/1.1" + CRLF +
            "Host: localhost:" + getPort() + CRLF +
            "Connection: close" + CRLF +
            "Transfer-Encoding: chunked" + CRLF +
            "Content-Type: application/x-www-form-urlencoded" + CRLF +
            CRLF +
            "param1=value1&param2=value2" + CRLF).getBytes(StandardCharsets.UTF_8);

        rc = postUrl(body, "http://localhost:" + getPort() + "/special/endpoint?param3=value3",
            new ByteChunk(), null);

        Assert.assertEquals(400, rc);

        body = ("POST / HTTP/1.1" + CRLF +
            "Host: localhost:" + getPort() + CRLF +
            "Connection: close" + CRLF +
            "Transfer-Encoding: chunked" + CRLF +
            "Content-Type: application/x-www-form-urlencoded" + CRLF +
            CRLF +
            "param1=value1" + CRLF).getBytes(StandardCharsets.UTF_8);

        rc = postUrl(body, "http://localhost:" + getPort() + "/special/endpoint?param2=value2",
            new ByteChunk(), null);

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

        tomcat.start();

        int rc = getUrl("http://localhost:" + getPort() +
                "/special===?param1=value1&param2=value2",
            new ByteChunk(), null);

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

        tomcat.start();

        int rc = getUrl("http://localhost:" + getPort() +
                "/special%20endpoint?param1=value1&param2=value2&param3=value3",
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
        parameterLimitValve.setUrlPatternLimits("/special/.*=2" + CRLF + "/special2/.*=3" + CRLF + "/my/special/url1=1");

        Tomcat.addServlet(ctx, "snoop", new SnoopServlet());
        ctx.addServletMappingDecoded("/special/endpoint", "snoop");
        ctx.addServletMappingDecoded("/special2/endpoint", "snoop");
        ctx.addServletMappingDecoded("/my/special/url1", "snoop");
        ctx.addServletMappingDecoded("/my/special/url2", "snoop");

        tomcat.start();

        int rc = getUrl("http://localhost:" + getPort() +
                "/special/endpoint?param1=value1&param2=value2&param3=value3",
            new ByteChunk(), null);
        Assert.assertEquals(400, rc);

        rc = getUrl("http://localhost:" + getPort() +
                "/special2/endpoint?param1=value1&param2=value2&param3=value3&param4=value4",
            new ByteChunk(), null);
        Assert.assertEquals(400, rc);

        rc = getUrl("http://localhost:" + getPort() +
                "/my/special/url1?param1=value1&param2=value2",
            new ByteChunk(), null);
        Assert.assertEquals(400, rc);

        rc = getUrl("http://localhost:" + getPort() +
                "/my/special/url2?param1=value1&param2=value2&param3=value3",
            new ByteChunk(), null);
        Assert.assertEquals(400, rc);

        rc = getUrl("http://localhost:" + getPort() +
                "/special/endpoint?param1=value1&param2=value2",
            new ByteChunk(), null);
        Assert.assertEquals(200, rc);

        rc = getUrl("http://localhost:" + getPort() +
                "/special2/endpoint?param1=value1&param2=value2&param3=value3",
            new ByteChunk(), null);
        Assert.assertEquals(200, rc);

        rc = getUrl("http://localhost:" + getPort() +
                "/my/special/url1?param1=value1",
            new ByteChunk(), null);
        Assert.assertEquals(200, rc);

        rc = getUrl("http://localhost:" + getPort() +
                "/my/special/url2?param1=value1&param2=value2",
            new ByteChunk(), null);
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

        tomcat.start();

        int rc = getUrl("http://localhost:" + getPort() + "/other/endpoint?param1=value1&param2=value2",
            new ByteChunk(), null);

        Assert.assertEquals(400, rc);
    }

    @Test
    public void testUrlPatternLimitsFromFile() throws Exception {
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

        try (BufferedReader reader = new BufferedReader(new FileReader(configFile))) {
            parameterLimitValve.setUrlPatternLimits(reader);
        }

        Tomcat.addServlet(ctx, "snoop", new SnoopServlet());
        ctx.addServletMappingDecoded("/api/test", "snoop");

        tomcat.start();

        int rc = getUrl("http://localhost:" + getPort() + "/api/test?param1=value1&param2=value2", new ByteChunk(), null);
        Assert.assertEquals(200, rc);

        rc = getUrl("http://localhost:" + getPort() + "/api/test?param1=value1&param2=value2&param3=value3", new ByteChunk(), null);
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

        tomcat.start();

        int rc = getUrl("http://localhost:" + getPort() + "/api/test?param1=value1&param2=value2", new ByteChunk(), null);
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

        tomcat.start();

        int rc = getUrl("http://localhost:" + getPort() + "/api/test?param1=value1&param2=value2", new ByteChunk(), null);
        Assert.assertEquals(200, rc);

        rc = getUrl("http://localhost:" + getPort() + "/admin/test?param1=value1&param2=value2", new ByteChunk(), null);
        Assert.assertEquals(200, rc);

        rc = getUrl("http://localhost:" + getPort() + "/api/test?param1=value1&param2=value2&param3=value3", new ByteChunk(), null);
        Assert.assertEquals(400, rc);

        rc = getUrl("http://localhost:" + getPort() + "/admin/test?param1=value1&param2=value2&param3=value3", new ByteChunk(), null);
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

        tomcat.start();

        int rc = getUrl("http://localhost:" + getPort() +
                "/special/endpoint?param1=value1&param2=value2&param3=value3",
            new ByteChunk(), null);

        Assert.assertEquals(400, rc);

        rc = getUrl("http://localhost:" + getPort() +
                "/special/endpoint?param1=value1&param2=value2",
            new ByteChunk(), null);

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

        tomcat.start();

        int rc = getUrl("http://localhost:" + getPort() +
                "/context1/special/endpoint?param1=value1&param2=value2&param3=value3",
            new ByteChunk(), null);

        Assert.assertEquals(200, rc);

        rc = getUrl("http://localhost:" + getPort() +
                "/context2/special/endpoint?param1=value1&param2=value2&param3=value3",
            new ByteChunk(), null);

        Assert.assertEquals(400, rc);

        rc = getUrl("http://localhost:" + getPort() +
                "/context3/special/endpoint?param1=value1&param2=value2",
            new ByteChunk(), null);

        Assert.assertEquals(400, rc);
    }

}
