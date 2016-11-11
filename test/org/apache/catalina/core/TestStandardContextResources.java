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

package org.apache.catalina.core;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.Arrays;
import java.util.List;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.startup.Constants;
import org.apache.catalina.startup.ContextConfig;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.catalina.util.IOTools;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.descriptor.web.WebXml;

public class TestStandardContextResources extends TomcatBaseTest {

    @Override
    public void setUp() throws Exception {
        super.setUp();

        Tomcat tomcat = getTomcatInstance();

        // BZ 49218: The test fails if JreMemoryLeakPreventionListener is not
        // present. The listener affects the JVM, and thus not only the current,
        // but also the subsequent tests that are run in the same JVM. So it is
        // fair to add it in every test.
        tomcat.getServer().addLifecycleListener(
                new JreMemoryLeakPreventionListener());
    }

    @Test
    public void testResources() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        File appDir = new File("test/webapp-fragments");
        // app dir is relative to server home
        Context ctx = tomcat.addWebapp(null, "/test", appDir.getAbsolutePath());

        tomcat.start();

        assertPageContains("/test/resourceA.jsp",
                "<p>resourceA.jsp in the web application</p>");
        assertPageContains("/test/resourceB.jsp",
                "<p>resourceB.jsp in resources.jar</p>");
        assertPageContains("/test/folder/resourceC.jsp",
                "<p>resourceC.jsp in the web application</p>");
        assertPageContains("/test/folder/resourceD.jsp",
                "<p>resourceD.jsp in resources.jar</p>");
        assertPageContains("/test/folder/resourceE.jsp",
                "<p>resourceE.jsp in the web application</p>");
        assertPageContains("/test/resourceG.jsp",
                "<p>resourceG.jsp in WEB-INF/classes</p>", 404);

        // For BZ 54391. Relative ordering is specified in resources2.jar.
        // It is not absolute-ordering, so there may be other jars in the list
        @SuppressWarnings("unchecked")
        List<String> orderedLibs = (List<String>) ctx.getServletContext()
                .getAttribute(ServletContext.ORDERED_LIBS);
        if (orderedLibs.size() > 2) {
            log.warn("testResources(): orderedLibs: " + orderedLibs);
        }
        int index = orderedLibs.indexOf("resources.jar");
        int index2 = orderedLibs.indexOf("resources2.jar");
        assertTrue(orderedLibs.toString(), index >= 0 && index2 >= 0
                && index < index2);
    }

    @Test
    public void testResourcesWebInfClasses() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        // app dir is relative to server home
        File appDir = new File("test/webapp-fragments");

        // Need to cast to be able to set StandardContext specific attribute
        StandardContext ctxt = (StandardContext)
            tomcat.addWebapp(null, "/test", appDir.getAbsolutePath());
        ctxt.setAddWebinfClassesResources(true);

        tomcat.start();

        assertPageContains("/test/resourceA.jsp",
                "<p>resourceA.jsp in the web application</p>");
        assertPageContains("/test/resourceB.jsp",
                "<p>resourceB.jsp in resources.jar</p>");
        assertPageContains("/test/folder/resourceC.jsp",
                "<p>resourceC.jsp in the web application</p>");
        assertPageContains("/test/folder/resourceD.jsp",
                "<p>resourceD.jsp in resources.jar</p>");
        assertPageContains("/test/folder/resourceE.jsp",
                "<p>resourceE.jsp in the web application</p>");
        assertPageContains("/test/resourceG.jsp",
                "<p>resourceG.jsp in WEB-INF/classes</p>");
    }

    @Test
    public void testResourcesAbsoluteOrdering() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        File appDir = new File("test/webapp-fragments");

        AbsoluteOrderContextConfig absoluteOrderConfig = new AbsoluteOrderContextConfig();

        // app dir is relative to server home
        StandardContext ctx = (StandardContext) tomcat.addWebapp(null, "/test",
                appDir.getAbsolutePath(), absoluteOrderConfig);

        Tomcat.addServlet(ctx, "getresource", new GetResourceServlet());
        ctx.addServletMappingDecoded("/getresource", "getresource");

        tomcat.start();
        assertPageContains("/test/getresource?path=/resourceF.jsp",
        "<p>resourceF.jsp in resources2.jar</p>");
        assertPageContains("/test/getresource?path=/resourceB.jsp",
        "<p>resourceB.jsp in resources.jar</p>");

        // Check ordering, for BZ 54391
        assertEquals(Arrays.asList("resources.jar", "resources2.jar"), ctx
                .getServletContext().getAttribute(ServletContext.ORDERED_LIBS));

        tomcat.getHost().removeChild(ctx);
        tomcat.getHost().stop();

        // change ordering
        absoluteOrderConfig.swap();

        ctx = (StandardContext) tomcat.addWebapp(null, "/test",
                appDir.getAbsolutePath(), absoluteOrderConfig);
        Tomcat.addServlet(ctx, "getresource", new GetResourceServlet());
        ctx.addServletMappingDecoded("/getresource", "getresource");

        tomcat.getHost().start();

        assertPageContains("/test/getresource?path=/resourceF.jsp",
        "<p>resourceF.jsp in resources2.jar</p>");
        assertPageContains("/test/getresource?path=/resourceB.jsp",
        "<p>resourceB.jsp in resources2.jar</p>");

        // Check ordering, for BZ 54391
        assertEquals(Arrays.asList("resources2.jar", "resources.jar"), ctx
                .getServletContext().getAttribute(ServletContext.ORDERED_LIBS));
    }


    public static class AbsoluteOrderContextConfig extends ContextConfig {

        private boolean swap = false;

        public AbsoluteOrderContextConfig() {
            super();
            // Prevent it from looking (if it finds one - it'll have dup error)
            setDefaultWebXml(Constants.NoDefaultWebXml);
        }

        @Override
        protected WebXml createWebXml() {
            WebXml wxml = new WebXml();
            if (swap) {
                wxml.addAbsoluteOrdering("resources2");
                wxml.addAbsoluteOrdering("resources");
            } else {
                wxml.addAbsoluteOrdering("resources");
                wxml.addAbsoluteOrdering("resources2");
            }
            return wxml;
        }

        protected void swap() {
            swap = !swap;
        }
    }


    @Test
    public void testResources2() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        File appDir = new File("test/webapp-fragments");
        // app dir is relative to server home
        StandardContext ctx = (StandardContext) tomcat.addWebapp(null, "/test",
                appDir.getAbsolutePath());
        skipTldsForResourceJars(ctx);

        Tomcat.addServlet(ctx, "getresource", new GetResourceServlet());
        ctx.addServletMappingDecoded("/getresource", "getresource");

        tomcat.start();

        assertPageContains("/test/getresource?path=/resourceF.jsp",
                "<p>resourceF.jsp in resources2.jar</p>");
        assertPageContains("/test/getresource?path=/resourceA.jsp",
                "<p>resourceA.jsp in the web application</p>");
        assertPageContains("/test/getresource?path=/resourceB.jsp",
                "<p>resourceB.jsp in resources.jar</p>");
        assertPageContains("/test/getresource?path=/folder/resourceC.jsp",
                "<p>resourceC.jsp in the web application</p>");
        assertPageContains("/test/getresource?path=/folder/resourceD.jsp",
                "<p>resourceD.jsp in resources.jar</p>");
        assertPageContains("/test/getresource?path=/folder/resourceE.jsp",
                "<p>resourceE.jsp in the web application</p>");
    }

    /**
     * A servlet that prints the requested resource. The path to the requested
     * resource is passed as a parameter, <code>path</code>.
     */
    public static class GetResourceServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {

            resp.setContentType("text/plain");

            ServletContext context = getServletContext();

            // Check resources individually
            URL url = context.getResource(req.getParameter("path"));
            if (url == null) {
                resp.getWriter().println("Not found");
                return;
            }

            try (InputStream input = url.openStream();
                    OutputStream output = resp.getOutputStream()) {
                IOTools.flow(input, output);
            }
        }
    }

    private void assertPageContains(String pageUrl, String expectedBody)
            throws IOException {

        assertPageContains(pageUrl, expectedBody, 200);
    }

    private void assertPageContains(String pageUrl, String expectedBody,
            int expectedStatus) throws IOException {
        ByteChunk res = new ByteChunk();
        int sc = getUrl("http://localhost:" + getPort() + pageUrl, res, null);

        assertEquals(expectedStatus, sc);

        if (expectedStatus == 200) {
            String result = res.toString();
            assertTrue(result, result.indexOf(expectedBody) > 0);
        }
    }
}
