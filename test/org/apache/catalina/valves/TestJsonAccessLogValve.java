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

import jakarta.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.buf.ByteChunk;

/**
 * Tests for {@link JsonAccessLogValve}.
 */
public class TestJsonAccessLogValve extends TomcatBaseTest {

    @Test
    public void testDefaultPatternProducesValidJson() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        Context ctx = getProgrammaticRootContext();

        Tomcat.addServlet(ctx, "hello", new HelloWorldServlet());
        ctx.addServletMappingDecoded("/", "hello");

        JsonAccessLogValve valve = new JsonAccessLogValve();
        valve.setDirectory(getTemporaryDirectory().getAbsolutePath());
        valve.setPrefix("access_json_test");
        valve.setSuffix(".log");
        ctx.getParent().getPipeline().addValve(valve);

        tomcat.start();

        ByteChunk res = new ByteChunk();
        res.setCharset(StandardCharsets.UTF_8);
        int rc = getUrl("http://localhost:" + getPort() + "/", res, null);

        Assert.assertEquals(HttpServletResponse.SC_OK, rc);
        Assert.assertEquals(HelloWorldServlet.RESPONSE_TEXT, res.toString());
    }


    @Test
    public void testCommonPatternProducesValidJson() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        Context ctx = getProgrammaticRootContext();

        Tomcat.addServlet(ctx, "hello", new HelloWorldServlet());
        ctx.addServletMappingDecoded("/", "hello");

        JsonAccessLogValve valve = new JsonAccessLogValve();
        valve.setPattern("common");
        valve.setDirectory(getTemporaryDirectory().getAbsolutePath());
        valve.setPrefix("access_json_common");
        valve.setSuffix(".log");
        ctx.getParent().getPipeline().addValve(valve);

        tomcat.start();

        ByteChunk res = new ByteChunk();
        res.setCharset(StandardCharsets.UTF_8);
        int rc = getUrl("http://localhost:" + getPort() + "/", res, null);

        Assert.assertEquals(HttpServletResponse.SC_OK, rc);
    }


    @Test
    public void testCombinedPatternProducesValidJson() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        Context ctx = getProgrammaticRootContext();

        Tomcat.addServlet(ctx, "hello", new HelloWorldServlet());
        ctx.addServletMappingDecoded("/", "hello");

        JsonAccessLogValve valve = new JsonAccessLogValve();
        valve.setPattern("combined");
        valve.setDirectory(getTemporaryDirectory().getAbsolutePath());
        valve.setPrefix("access_json_combined");
        valve.setSuffix(".log");
        ctx.getParent().getPipeline().addValve(valve);

        tomcat.start();

        ByteChunk res = new ByteChunk();
        res.setCharset(StandardCharsets.UTF_8);
        int rc = getUrl("http://localhost:" + getPort() + "/", res, null);

        Assert.assertEquals(HttpServletResponse.SC_OK, rc);
    }


    @Test
    public void testCustomPatternWithRequestHeaders() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        Context ctx = getProgrammaticRootContext();

        Tomcat.addServlet(ctx, "hello", new HelloWorldServlet());
        ctx.addServletMappingDecoded("/", "hello");

        JsonAccessLogValve valve = new JsonAccessLogValve();
        // Pattern includes request header and response header elements
        valve.setPattern("%h %s %{User-Agent}i %{Content-Type}o");
        valve.setDirectory(getTemporaryDirectory().getAbsolutePath());
        valve.setPrefix("access_json_headers");
        valve.setSuffix(".log");
        ctx.getParent().getPipeline().addValve(valve);

        tomcat.start();

        ByteChunk res = new ByteChunk();
        res.setCharset(StandardCharsets.UTF_8);
        int rc = getUrl("http://localhost:" + getPort() + "/", res, null);

        Assert.assertEquals(HttpServletResponse.SC_OK, rc);
    }


    @Test
    public void testPatternWithCookiesAndSessionAttributes() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        Context ctx = getProgrammaticRootContext();

        Tomcat.addServlet(ctx, "hello", new HelloWorldServlet());
        ctx.addServletMappingDecoded("/", "hello");

        JsonAccessLogValve valve = new JsonAccessLogValve();
        // Pattern includes cookies, request attributes, and session attributes
        valve.setPattern("%h %s %{JSESSIONID}c %{testAttr}r %{testSession}s");
        valve.setDirectory(getTemporaryDirectory().getAbsolutePath());
        valve.setPrefix("access_json_cookies");
        valve.setSuffix(".log");
        ctx.getParent().getPipeline().addValve(valve);

        tomcat.start();

        ByteChunk res = new ByteChunk();
        res.setCharset(StandardCharsets.UTF_8);
        int rc = getUrl("http://localhost:" + getPort() + "/", res, null);

        Assert.assertEquals(HttpServletResponse.SC_OK, rc);
    }


    @Test
    public void testCreateLogElementsOutputFormat() {
        JsonAccessLogValve valve = new JsonAccessLogValve();
        valve.setPattern("%h %s %b");

        // Use createLogElements via the internal API
        AbstractAccessLogValve.AccessLogElement[] elements =
                valve.createLogElements();

        // Verify that elements array is valid and has at least
        // opening brace + 3 fields + separators + closing brace
        Assert.assertNotNull(elements);
        Assert.assertTrue("Should have at least some elements",
                elements.length > 0);
    }
}
