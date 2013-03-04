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

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.buf.ByteChunk;

public class TestCoyoteAdapter extends TomcatBaseTest {

    @Test
    public void testPathParmsRootNone() throws Exception {
        pathParamTest("/", "none");
    }

    @Test
    public void testPathParmsFooNone() throws Exception {
        pathParamTest("/foo", "none");
    }

    @Test
    public void testPathParmsRootSessionOnly() throws Exception {
        pathParamTest("/;jsessionid=1234", "1234");
    }

    @Test
    public void testPathParmsFooSessionOnly() throws Exception {
        pathParamTest("/foo;jsessionid=1234", "1234");
    }

    @Test
    public void testPathParmsFooSessionDummy() throws Exception {
        pathParamTest("/foo;jsessionid=1234;dummy", "1234");
    }

    @Test
    public void testPathParmsFooSessionDummyValue() throws Exception {
        pathParamTest("/foo;jsessionid=1234;dummy=5678", "1234");
    }

    @Test
    public void testPathParmsFooSessionValue() throws Exception {
        pathParamTest("/foo;jsessionid=1234;=5678", "1234");
    }

    @Test
    public void testPathParmsFooSessionBar() throws Exception {
        pathParamTest("/foo;jsessionid=1234/bar", "1234");
    }

    @Test
    public void testPathParamsRedirect() throws Exception {
        // Setup Tomcat instance
        Tomcat tomcat = getTomcatInstance();

        // Must have a real docBase - just use temp
        File docBase = new File(System.getProperty("java.io.tmpdir"));

        // Create the folder that will trigger the redirect
        File foo = new File(docBase, "foo");
        addDeleteOnTearDown(foo);
        if (!foo.mkdirs() && !foo.isDirectory()) {
            Assert.fail("Unable to create foo directory in docBase");
        }

        Context ctx = tomcat.addContext("", docBase.getAbsolutePath());

        Tomcat.addServlet(ctx, "servlet", new PathParamServlet());
        ctx.addServletMapping("/", "servlet");

        tomcat.start();

        testPath("/", "none");
        testPath("/;jsessionid=1234", "1234");
        testPath("/foo;jsessionid=1234", "1234");
        testPath("/foo;jsessionid=1234;dummy", "1234");
        testPath("/foo;jsessionid=1234;dummy=5678", "1234");
        testPath("/foo;jsessionid=1234;=5678", "1234");
        testPath("/foo;jsessionid=1234/bar", "1234");
    }

    private void pathParamTest(String path, String expected) throws Exception {
        // Setup Tomcat instance
        Tomcat tomcat = getTomcatInstance();

        // Must have a real docBase - just use temp
        Context ctx =
            tomcat.addContext("", System.getProperty("java.io.tmpdir"));

        Tomcat.addServlet(ctx, "servlet", new PathParamServlet());
        ctx.addServletMapping("/", "servlet");

        tomcat.start();

        ByteChunk res = getUrl("http://localhost:" + getPort() + path);
        Assert.assertEquals(expected, res.toString());
    }

    private void testPath(String path, String expected) throws Exception {
        ByteChunk res = getUrl("http://localhost:" + getPort() + path);
        Assert.assertEquals(expected, res.toString());
    }

    private static class PathParamServlet extends HttpServlet {
        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {
            resp.setContentType("text/plain");
            PrintWriter pw = resp.getWriter();
            String sessionId = req.getRequestedSessionId();
            if (sessionId == null) {
                sessionId = "none";
            }
            pw.write(sessionId);
        }
    }

    @Test
    public void testPathParamExtRootNoParam() throws Exception {
        pathParamExtenionTest("/testapp/blah.txt", "none");
    }

    @Test
    public void testPathParamExtLevel1NoParam() throws Exception {
        pathParamExtenionTest("/testapp/blah/blah.txt", "none");
    }

    @Test
    public void testPathParamExtLevel1WithParam() throws Exception {
        pathParamExtenionTest("/testapp/blah;x=y/blah.txt", "none");
    }

    private void pathParamExtenionTest(String path, String expected)
            throws Exception {
        // Setup Tomcat instance
        Tomcat tomcat = getTomcatInstance();

        // Must have a real docBase - just use temp
        Context ctx =
            tomcat.addContext("/testapp", System.getProperty("java.io.tmpdir"));

        Tomcat.addServlet(ctx, "servlet", new PathParamServlet());
        ctx.addServletMapping("*.txt", "servlet");

        tomcat.start();

        ByteChunk res = getUrl("http://localhost:" + getPort() + path);
        Assert.assertEquals(expected, res.toString());
    }

    @Test
    public void testBug54602a() throws Exception {
        // No UTF-8
        doTestUriDecoding("/foo", "UTF-8", "/foo");
    }

    @Test
    public void testBug54602b() throws Exception {
        // Valid UTF-8
        doTestUriDecoding("/foo%c4%87", "UTF-8", "/foo\u0107");
    }

    @Test
    public void testBug54602c() throws Exception {
        // Partial UTF-8
        doTestUriDecoding("/foo%c4", "UTF-8", "/foo\uFFFD");
    }

    @Test
    public void testBug54602d() throws Exception {
        // Invalid UTF-8
        doTestUriDecoding("/foo%ff", "UTF-8", "/foo\uFFFD");
    }

    @Test
    public void testBug54602e() throws Exception {
        // Invalid UTF-8
        doTestUriDecoding("/foo%ed%a0%80", "UTF-8", "/foo\uFFFD\uFFFD\uFFFD");
    }

    private void doTestUriDecoding(String path, String encoding,
            String expectedPathInfo) throws Exception{

        // Setup Tomcat instance
        Tomcat tomcat = getTomcatInstance();

        tomcat.getConnector().setURIEncoding(encoding);

        // Must have a real docBase - just use temp
        Context ctx =
            tomcat.addContext("/", System.getProperty("java.io.tmpdir"));

        PathInfoServlet servlet = new PathInfoServlet();
        Tomcat.addServlet(ctx, "servlet", servlet);
        ctx.addServletMapping("/*", "servlet");

        tomcat.start();

        int rc = getUrl("http://localhost:" + getPort() + path,
                new ByteChunk(), null);
        Assert.assertEquals(HttpServletResponse.SC_OK, rc);

        Assert.assertEquals(expectedPathInfo, servlet.getPathInfo());
    }

    private static class PathInfoServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        private String pathInfo = null;

        public String getPathInfo() {
            return pathInfo;
        }

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {

            // Not thread safe
            pathInfo = req.getPathInfo();
        }
    }
}
