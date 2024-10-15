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
package org.apache.catalina.mapper;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.core.StandardContext;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.catalina.valves.RemoteAddrValve;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.descriptor.web.SecurityCollection;
import org.apache.tomcat.util.descriptor.web.SecurityConstraint;
import org.apache.tomcat.websocket.server.WsContextListener;

/**
 * Mapper tests that use web applications on a running Tomcat instance.
 */
public class TestMapperWebapps extends TomcatBaseTest {

    @Test
    public void testContextRootMapping01() throws Exception {
        doTestContextRootMapping("", false, false);
    }


    @Test
    public void testContextRootMapping02() throws Exception {
        doTestContextRootMapping("", true, false);
    }


    @Test
    public void testContextRootMapping03() throws Exception {
        doTestContextRootMapping("", false, true);
    }


    @Test
    public void testContextRootMapping04() throws Exception {
        doTestContextRootMapping("", true, true);
    }


    @Test
    public void testContextRootMapping05() throws Exception {
        doTestContextRootMapping("/somepath", false, false);
    }


    @Test
    public void testContextRootMapping06() throws Exception {
        doTestContextRootMapping("/somepath", true, false);
    }


    @Test
    public void testContextRootMapping07() throws Exception {
        doTestContextRootMapping("/somepath", false, true);
    }


    @Test
    public void testContextRootMapping08() throws Exception {
        doTestContextRootMapping("/somepath", true, true);
    }


    @Test
    public void testContextRootMapping09() throws Exception {
        doTestContextRootMapping("/some/other/path", false, false);
    }


    @Test
    public void testContextRootMapping10() throws Exception {
        doTestContextRootMapping("/some/other/path", true, false);
    }


    @Test
    public void testContextRootMapping11() throws Exception {
        doTestContextRootMapping("/some/other/path", false, true);
    }


    @Test
    public void testContextRootMapping12() throws Exception {
        doTestContextRootMapping("/some/other/path", true, true);
    }


    private void doTestContextRootMapping(String contextPath, boolean trailingSlash, boolean rootRedirect)
            throws Exception {
        Tomcat tomcat = getTomcatInstance();
        tomcat.enableNaming();

        // No file system docBase required
        StandardContext ctx = (StandardContext) tomcat.addContext(contextPath, null);
        ctx.setMapperContextRootRedirectEnabled(rootRedirect);

        Tomcat.addServlet(ctx, "ReportMapping", new ReportMappingServlet());
        ctx.addServletMappingDecoded("", "ReportMapping");

        tomcat.start();

        ByteChunk bc = getUrl("http://localhost:" + getPort() + contextPath + (trailingSlash ? "/" : ""));

        String body = bc.toString();
        System.out.println(body);
        Assert.assertTrue(body, body.contains("ContextPath: [" + contextPath + "]"));
        Assert.assertTrue(body, body.contains("ServletPath: []"));
        Assert.assertTrue(body, body.contains("PathInfo: [/]"));
    }


    private static class ReportMappingServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            resp.setCharacterEncoding(StandardCharsets.UTF_8);
            resp.setContentType("text/plain");
            PrintWriter pw = resp.getWriter();

            pw.print("ContextPath: [" + req.getContextPath() + "]\n");
            pw.print("ServletPath: [" + req.getServletPath() + "]\n");
            pw.print("PathInfo: [" + req.getPathInfo() + "]\n");
        }
    }

    @Test
    public void testContextReload_Bug56658_Bug56882() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        File appDir = new File(getBuildDirectory(), "webapps/examples");
        // app dir is relative to server home
        org.apache.catalina.Context ctxt = tomcat.addWebapp(null, "/examples", appDir.getAbsolutePath());
        ctxt.addApplicationListener(WsContextListener.class.getName());
        tomcat.start();

        // The tests are from TestTomcat#testSingleWebapp(), #testJsps()
        // We reload the context and verify that the pages are still accessible
        ByteChunk res;
        String text;

        res = getUrl("http://localhost:" + getPort() + "/examples/servlets/servlet/HelloWorldExample");
        text = res.toString();
        Assert.assertTrue(text, text.contains("<a href=\"../helloworld.html\">"));

        res = getUrl("http://localhost:" + getPort() + "/examples/jsp/jsp2/el/basic-arithmetic.jsp");
        text = res.toString();
        Assert.assertTrue(text, text.contains("<td>${(1==2) ? 3 : 4}</td>"));

        res = getUrl("http://localhost:" + getPort() + "/examples/index.html");
        text = res.toString();
        Assert.assertTrue(text, text.contains("<title>Apache Tomcat Examples</title>"));

        long timeA = System.currentTimeMillis();
        res = getUrl("http://localhost:" + getPort() + "/examples/jsp/include/include.jsp");
        String timestamp = findCommonPrefix(timeA, System.currentTimeMillis());
        text = res.toString();
        Assert.assertTrue(text,
                text.contains("In place evaluation of another JSP which gives you the current time: " + timestamp));
        Assert.assertTrue(text, text.contains("To get the current time in ms"));
        Assert.assertTrue(text, text.contains("by including the output of another JSP: " + timestamp));
        Assert.assertTrue(text, text.contains(":-)"));

        res = getUrl("http://localhost:" + getPort() + "/examples/jsp/forward/forward.jsp");
        text = res.toString();
        Assert.assertTrue(text, text.contains("VM Memory usage"));

        ctxt.reload();

        res = getUrl("http://localhost:" + getPort() + "/examples/servlets/servlet/HelloWorldExample");
        text = res.toString();
        Assert.assertTrue(text, text.contains("<a href=\"../helloworld.html\">"));

        res = getUrl("http://localhost:" + getPort() + "/examples/jsp/jsp2/el/basic-arithmetic.jsp");
        text = res.toString();
        Assert.assertTrue(text, text.contains("<td>${(1==2) ? 3 : 4}</td>"));

        res = getUrl("http://localhost:" + getPort() + "/examples/index.html");
        text = res.toString();
        Assert.assertTrue(text, text.contains("<title>Apache Tomcat Examples</title>"));

        timeA = System.currentTimeMillis();
        res = getUrl("http://localhost:" + getPort() + "/examples/jsp/include/include.jsp");
        timestamp = findCommonPrefix(timeA, System.currentTimeMillis());
        text = res.toString();
        Assert.assertTrue(text,
                text.contains("In place evaluation of another JSP which gives you the current time: " + timestamp));
        Assert.assertTrue(text, text.contains("To get the current time in ms"));
        Assert.assertTrue(text, text.contains("by including the output of another JSP: " + timestamp));
        Assert.assertTrue(text, text.contains(":-)"));

        res = getUrl("http://localhost:" + getPort() + "/examples/jsp/forward/forward.jsp");
        text = res.toString();
        Assert.assertTrue(text, text.contains("VM Memory usage"));
    }

    @Test
    public void testWelcomeFileNotStrict() throws Exception {

        Tomcat tomcat = getTomcatInstance();

        File appDir = new File("test/webapp");

        StandardContext ctxt = (StandardContext) tomcat.addWebapp(null, "/test", appDir.getAbsolutePath());
        ctxt.setReplaceWelcomeFiles(true);
        ctxt.addWelcomeFile("index.jsp");
        // Mapping for *.do is defined in web.xml
        ctxt.addWelcomeFile("index.do");

        tomcat.start();
        ByteChunk bc = new ByteChunk();
        int rc = getUrl("http://localhost:" + getPort() + "/test/welcome-files", bc, null);
        Assert.assertEquals(HttpServletResponse.SC_OK, rc);
        Assert.assertTrue(bc.toString().contains("JSP"));

        rc = getUrl("http://localhost:" + getPort() + "/test/welcome-files/sub", bc, null);
        Assert.assertEquals(HttpServletResponse.SC_OK, rc);
        Assert.assertTrue(bc.toString().contains("Servlet"));
    }

    @Test
    public void testWelcomeFileStrict() throws Exception {

        Tomcat tomcat = getTomcatInstance();

        File appDir = new File("test/webapp");

        StandardContext ctxt = (StandardContext) tomcat.addWebapp(null, "/test", appDir.getAbsolutePath());
        ctxt.setReplaceWelcomeFiles(true);
        ctxt.addWelcomeFile("index.jsp");
        // Mapping for *.do is defined in web.xml
        ctxt.addWelcomeFile("index.do");

        // Simulate STRICT_SERVLET_COMPLIANCE
        ctxt.setResourceOnlyServlets("");

        tomcat.start();
        ByteChunk bc = new ByteChunk();
        int rc = getUrl("http://localhost:" + getPort() + "/test/welcome-files", bc, null);
        Assert.assertEquals(HttpServletResponse.SC_OK, rc);
        Assert.assertTrue(bc.toString().contains("JSP"));

        rc = getUrl("http://localhost:" + getPort() + "/test/welcome-files/sub", bc, null);
        Assert.assertEquals(HttpServletResponse.SC_NOT_FOUND, rc);
    }

    @Test
    public void testRedirect() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        // Use standard test webapp as ROOT
        File rootDir = new File("test/webapp");
        org.apache.catalina.Context root = tomcat.addWebapp(null, "", rootDir.getAbsolutePath());

        // Add a security constraint
        SecurityConstraint constraint = new SecurityConstraint();
        SecurityCollection collection = new SecurityCollection();
        collection.addPatternDecoded("/welcome-files/*");
        collection.addPatternDecoded("/welcome-files");
        constraint.addCollection(collection);
        constraint.addAuthRole("foo");
        root.addConstraint(constraint);

        // Also make examples available
        File examplesDir = new File(getBuildDirectory(), "webapps/examples");
        org.apache.catalina.Context examples = tomcat.addWebapp(null, "/examples", examplesDir.getAbsolutePath());
        examples.setMapperContextRootRedirectEnabled(false);
        // Then block access to the examples to test redirection
        RemoteAddrValve rav = new RemoteAddrValve();
        rav.setDeny(".*");
        rav.setDenyStatus(404);
        examples.getPipeline().addValve(rav);

        tomcat.start();

        // Redirects within a web application
        doRedirectTest("/welcome-files", 401);
        doRedirectTest("/welcome-files/", 401);

        doRedirectTest("/jsp", 302);
        doRedirectTest("/jsp/", 404);

        doRedirectTest("/WEB-INF", 404);
        doRedirectTest("/WEB-INF/", 404);

        // Redirects between web applications
        doRedirectTest("/examples", 404);
        doRedirectTest("/examples/", 404);
    }


    private void doRedirectTest(String path, int expected) throws IOException {
        ByteChunk bc = new ByteChunk();
        int rc = getUrl("http://localhost:" + getPort() + path, bc, false);
        Assert.assertEquals(expected, rc);
    }


    /**
     * Prepare a string to search in messages that contain a timestamp, when it is known that the timestamp was printed
     * between {@code timeA} and {@code timeB}.
     */
    private static String findCommonPrefix(long timeA, long timeB) {
        while ((timeA != timeB) && timeA > 0) {
            timeA /= 10;
            timeB /= 10;
        }
        return String.valueOf(timeA);
    }
}
