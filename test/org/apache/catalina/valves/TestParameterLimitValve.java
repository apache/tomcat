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

import java.nio.charset.StandardCharsets;

import jakarta.servlet.ServletRequestParametersBaseTest;

import org.junit.Assert;
import org.junit.Test;

import static org.apache.catalina.startup.SimpleHttpClient.CRLF;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.startup.Tomcat;
import org.apache.tomcat.util.buf.ByteChunk;

public class TestParameterLimitValve extends ServletRequestParametersBaseTest {

    @Test
    public void testSpecificUrlPatternLimitExceeded() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        StandardContext ctx = (StandardContext) getProgrammaticRootContext();

        ParameterLimitValve parameterLimitValve = new ParameterLimitValve();
        parameterLimitValve.setUrlPatternLimits("/special/.*=2");
        ctx.getPipeline().addValve(parameterLimitValve);

        Tomcat.addServlet(ctx, "snoop", new SnoopServlet());
        ctx.addServletMappingDecoded("/special/endpoint", "snoop");

        tomcat.start();

        int rc = getUrl("http://localhost:" + getPort() +
            "/special/endpoint?param1=value1&param2=value2&param3=value3",
            new ByteChunk(), null);

        Assert.assertEquals(400, rc);
    }

    @Test
    public void testSpecificUrlPatternLimitNotExceeded() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        StandardContext ctx = (StandardContext) getProgrammaticRootContext();

        ParameterLimitValve parameterLimitValve = new ParameterLimitValve();
        parameterLimitValve.setUrlPatternLimits("/special/.*=2");
        ctx.getPipeline().addValve(parameterLimitValve);

        Tomcat.addServlet(ctx, "snoop", new SnoopServlet());
        ctx.addServletMappingDecoded("/special/endpoint", "snoop");

        tomcat.start();

        int rc = getUrl("http://localhost:" + getPort() + "/special/endpoint?param1=value1&param2=value2",
            new ByteChunk(), null);

        Assert.assertEquals(200, rc);
    }

    @Test
    public void testSpecificUrlPatternLimitExceededPostMethod() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        StandardContext ctx = (StandardContext) getProgrammaticRootContext();

        ParameterLimitValve parameterLimitValve = new ParameterLimitValve();
        parameterLimitValve.setUrlPatternLimits("/special/.*=2");
        ctx.getPipeline().addValve(parameterLimitValve);

        Tomcat.addServlet(ctx, "snoop", new SnoopServlet());
        ctx.addServletMappingDecoded("/special/endpoint", "snoop");

        tomcat.start();

        byte[] body = ("POST / HTTP/1.1" + CRLF +
            "Host: localhost:" + getPort() + CRLF +
            "Connection: close" + CRLF +
            "Transfer-Encoding: chunked" + CRLF +
            "Content-Type: application/x-www-form-urlencoded" + CRLF +
            CRLF +
            "param1=value1&param2=value2&param3=value3" + CRLF).getBytes(StandardCharsets.UTF_8);

        int rc = postUrl(body,"http://localhost:" + getPort() + "/special/endpoint",
            new ByteChunk(), null);

        Assert.assertEquals(400, rc);
    }

    @Test
    public void testSpecificUrlPatternLimitNotExceededPostMethod() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        StandardContext ctx = (StandardContext) getProgrammaticRootContext();

        ParameterLimitValve parameterLimitValve = new ParameterLimitValve();
        parameterLimitValve.setUrlPatternLimits("/special/.*=2");
        ctx.getPipeline().addValve(parameterLimitValve);

        Tomcat.addServlet(ctx, "snoop", new SnoopServlet());
        ctx.addServletMappingDecoded("/special/endpoint", "snoop");

        tomcat.start();

        byte[] body = ("POST / HTTP/1.1" + CRLF +
            "Host: localhost:" + getPort() + CRLF +
            "Connection: close" + CRLF +
            "Transfer-Encoding: chunked" + CRLF +
            "Content-Type: application/x-www-form-urlencoded" + CRLF +
            CRLF +
            "param1=value1&param2=value2" + CRLF).getBytes(StandardCharsets.UTF_8);

        int rc = postUrl(body, "http://localhost:" + getPort() + "/special/endpoint",
            new ByteChunk(), null);

        Assert.assertEquals(200, rc);
    }

    @Test
    public void testMultipleSpecificUrlPatternsLimitExceeded() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        tomcat.getConnector().setMaxParameterCount(2);

        StandardContext ctx = (StandardContext) getProgrammaticRootContext();

        ParameterLimitValve parameterLimitValve = new ParameterLimitValve();
        parameterLimitValve.setUrlPatternLimits("/special/.*=2,/special2/.*=3,/my/special/url1=1");
        ctx.getPipeline().addValve(parameterLimitValve);

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
    }

    @Test
    public void testMultipleSpecificUrlPatternsLimitNotExceeded() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        tomcat.getConnector().setMaxParameterCount(2);

        StandardContext ctx = (StandardContext) getProgrammaticRootContext();

        ParameterLimitValve parameterLimitValve = new ParameterLimitValve();
        parameterLimitValve.setUrlPatternLimits("/special/.*=2,/special2/.*=3,/my/special/url1=1");
        ctx.getPipeline().addValve(parameterLimitValve);

        Tomcat.addServlet(ctx, "snoop", new SnoopServlet());
        ctx.addServletMappingDecoded("/special/endpoint", "snoop");
        ctx.addServletMappingDecoded("/special2/endpoint", "snoop");
        ctx.addServletMappingDecoded("/my/special/url1", "snoop");
        ctx.addServletMappingDecoded("/my/special/url2", "snoop");

        tomcat.start();

        int rc = getUrl("http://localhost:" + getPort() +
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
    public void testSpecificUrlPatternLimitExceededWithBothQueryParamsAndPostMethod() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        StandardContext ctx = (StandardContext) getProgrammaticRootContext();

        ParameterLimitValve parameterLimitValve = new ParameterLimitValve();
        parameterLimitValve.setUrlPatternLimits("/special/.*=2");
        ctx.getPipeline().addValve(parameterLimitValve);

        Tomcat.addServlet(ctx, "snoop", new SnoopServlet());
        ctx.addServletMappingDecoded("/special/endpoint", "snoop");

        tomcat.start();

        byte[] body = ("POST / HTTP/1.1" + CRLF +
            "Host: localhost:" + getPort() + CRLF +
            "Connection: close" + CRLF +
            "Transfer-Encoding: chunked" + CRLF +
            "Content-Type: application/x-www-form-urlencoded" + CRLF +
            CRLF +
            "param1=value1&param2=value2" + CRLF).getBytes(StandardCharsets.UTF_8);

        int rc = postUrl(body, "http://localhost:" + getPort() + "/special/endpoint?param3=value3",
            new ByteChunk(), null);

        Assert.assertEquals(400, rc);
    }

    @Test
    public void testSpecificUrlPatternLimitNotExceededWithBothQueryParamsAndPostMethod() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        StandardContext ctx = (StandardContext) getProgrammaticRootContext();

        ParameterLimitValve parameterLimitValve = new ParameterLimitValve();
        parameterLimitValve.setUrlPatternLimits("/special/.*=2");
        ctx.getPipeline().addValve(parameterLimitValve);

        Tomcat.addServlet(ctx, "snoop", new SnoopServlet());
        ctx.addServletMappingDecoded("/special/endpoint", "snoop");

        tomcat.start();

        byte[] body = ("POST / HTTP/1.1" + CRLF +
            "Host: localhost:" + getPort() + CRLF +
            "Connection: close" + CRLF +
            "Transfer-Encoding: chunked" + CRLF +
            "Content-Type: application/x-www-form-urlencoded" + CRLF +
            CRLF +
            "param1=value1" + CRLF).getBytes(StandardCharsets.UTF_8);

        int rc = postUrl(body, "http://localhost:" + getPort() + "/special/endpoint?param2=value2",
            new ByteChunk(), null);

        Assert.assertEquals(200, rc);
    }

    @Test
    public void testNoMatchingPatternWithConnectorLimit() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        tomcat.getConnector().setMaxParameterCount(1);

        StandardContext ctx = (StandardContext) getProgrammaticRootContext();

        ParameterLimitValve parameterLimitValve = new ParameterLimitValve();
        parameterLimitValve.setUrlPatternLimits("/special/.*=2");
        ctx.getPipeline().addValve(parameterLimitValve);

        Tomcat.addServlet(ctx, "snoop", new SnoopServlet());
        ctx.addServletMappingDecoded("/other/endpoint", "snoop");

        tomcat.start();

        int rc = getUrl("http://localhost:" + getPort() + "/other/endpoint?param1=value1&param2=value2",
            new ByteChunk(), null);

        Assert.assertEquals(400, rc);
    }

    @Test
    public void testEmptyParameters() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        StandardContext ctx = (StandardContext) getProgrammaticRootContext();

        ParameterLimitValve parameterLimitValve = new ParameterLimitValve();
        parameterLimitValve.setUrlPatternLimits("/special/.*=2");
        ctx.getPipeline().addValve(parameterLimitValve);

        Tomcat.addServlet(ctx, "snoop", new SnoopServlet());
        ctx.addServletMappingDecoded("/special/endpoint", "snoop");

        tomcat.start();

        int rc = getUrl("http://localhost:" + getPort() + "/special/endpoint", new ByteChunk(), null);

        Assert.assertEquals(200, rc);
    }
}
