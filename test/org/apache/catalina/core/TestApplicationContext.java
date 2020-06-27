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
package org.apache.catalina.core;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collection;

import jakarta.servlet.Filter;
import jakarta.servlet.Servlet;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.descriptor.JspConfigDescriptor;
import jakarta.servlet.descriptor.JspPropertyGroupDescriptor;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleEvent;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.Tomcat.FixContextListener;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.buf.ByteChunk;

public class TestApplicationContext extends TomcatBaseTest {

    @Test
    public void testBug53257() throws Exception {
        getTomcatInstanceTestWebapp(false, true);

        ByteChunk res = getUrl("http://localhost:" + getPort() +
                "/test/bug53257/index.jsp");

        String result = res.toString();
        String[] lines = result.split("\n");
        for (String line : lines) {
            if (line.startsWith("FAIL")) {
                Assert.fail(line);
            }
        }
    }


    @Test
    public void testBug53467() throws Exception {
        getTomcatInstanceTestWebapp(false, true);

        ByteChunk res = new ByteChunk();
        int rc = getUrl("http://localhost:" + getPort() +
                "/test/bug5nnnn/bug53467%5D.jsp", res, null);

        Assert.assertEquals(HttpServletResponse.SC_OK, rc);
        Assert.assertTrue(res.toString().contains("<p>OK</p>"));
    }


    @Test(expected = IllegalArgumentException.class)
    public void testAddFilterWithFilterNameNull() throws LifecycleException {
        getServletContext().addFilter(null, (Filter) null);
    }


    @Test(expected = IllegalArgumentException.class)
    public void testAddFilterWithFilterNameEmptyString() throws LifecycleException {
        getServletContext().addFilter("", (Filter) null);
    }


    @Test(expected = IllegalArgumentException.class)
    public void testAddServletWithServletNameNull() throws LifecycleException {
        getServletContext().addServlet(null, (Servlet) null);
    }


    @Test(expected = IllegalArgumentException.class)
    public void testAddServletWithServletNameEmptyString() throws LifecycleException {
        getServletContext().addServlet("", (Servlet) null);
    }


    @Test
    public void testGetJspConfigDescriptor() throws Exception {
        Tomcat tomcat = getTomcatInstanceTestWebapp(false, false);

        StandardContext standardContext =
                (StandardContext) tomcat.getHost().findChildren()[0];

        ServletContext servletContext = standardContext.getServletContext();

        Assert.assertNull(servletContext.getJspConfigDescriptor());

        tomcat.start();

        Assert.assertNotNull(servletContext.getJspConfigDescriptor());
    }

    @Test
    public void testJspPropertyGroupsAreIsolated() throws Exception {
        Tomcat tomcat = getTomcatInstanceTestWebapp(false, false);

        StandardContext standardContext =
                (StandardContext) tomcat.getHost().findChildren()[0];

        ServletContext servletContext = standardContext.getServletContext();

        Assert.assertNull(servletContext.getJspConfigDescriptor());

        tomcat.start();

        JspConfigDescriptor jspConfigDescriptor =
                servletContext.getJspConfigDescriptor();
        Collection<JspPropertyGroupDescriptor> propertyGroups =
                jspConfigDescriptor.getJspPropertyGroups();
        Assert.assertFalse(propertyGroups.isEmpty());
        propertyGroups.clear();

        jspConfigDescriptor = servletContext.getJspConfigDescriptor();
        propertyGroups = jspConfigDescriptor.getJspPropertyGroups();
        Assert.assertFalse(propertyGroups.isEmpty());
    }


    private ServletContext getServletContext() throws LifecycleException {
        Tomcat tomcat = getTomcatInstanceTestWebapp(false, false);

        StandardContext standardContext =
                (StandardContext) tomcat.getHost().findChildren()[0];

        return standardContext.getServletContext();
    }


    @Test(expected = IllegalStateException.class)
    public void testSetInitParameter() throws Exception {
        getTomcatInstance().start();
        getServletContext().setInitParameter("name", "value");
    }


    /*
     * Cross-context requests with parallel deployment
     */
    @Test
    public void testBug57190() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        Context foo1 = new StandardContext();
        foo1.setName("/foo##1");
        foo1.setPath("/foo");
        foo1.setWebappVersion("1");
        foo1.addLifecycleListener(new FixContextListener());
        foo1.addLifecycleListener(new SetIdListener("foo1"));
        tomcat.getHost().addChild(foo1);

        Context foo2 = new StandardContext();
        foo2.setName("/foo##2");
        foo2.setPath("/foo");
        foo2.setWebappVersion("2");
        foo2.addLifecycleListener(new FixContextListener());
        foo2.addLifecycleListener(new SetIdListener("foo2"));
        tomcat.getHost().addChild(foo2);

        Context bar = tomcat.addContext("/bar", null);
        bar.addLifecycleListener(new SetIdListener("bar"));

        Context ctx = tomcat.addContext("", null);
        ctx.addLifecycleListener(new SetIdListener("ROOT"));
        ctx.setCrossContext(true);

        Tomcat.addServlet(ctx, "Bug57190Servlet", new Bug57190Servlet());
        ctx.addServletMappingDecoded("/", "Bug57190Servlet");

        tomcat.start();

        ByteChunk res = getUrl("http://localhost:" + getPort() + "/");
        String body = res.toString();

        Assert.assertTrue(body, body.contains("01-bar"));
        Assert.assertTrue(body, body.contains("02-foo2"));
        Assert.assertTrue(body, body.contains("03-foo1"));
        Assert.assertTrue(body, body.contains("04-foo2"));
        Assert.assertTrue(body, body.contains("05-foo2"));
        Assert.assertTrue(body, body.contains("06-ROOT"));
        Assert.assertTrue(body, body.contains("07-ROOT"));
        Assert.assertTrue(body, body.contains("08-foo2"));
        Assert.assertTrue(body, body.contains("09-ROOT"));
    }


    private static class Bug57190Servlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {
            resp.setContentType("text/plain");
            PrintWriter pw = resp.getWriter();
            ServletContext sc = req.getServletContext();
            pw.println("01-" + sc.getContext("/bar").getInitParameter("id"));
            pw.println("02-" + sc.getContext("/foo").getInitParameter("id"));
            pw.println("03-" + sc.getContext("/foo##1").getInitParameter("id"));
            pw.println("04-" + sc.getContext("/foo##2").getInitParameter("id"));
            pw.println("05-" + sc.getContext("/foo##3").getInitParameter("id"));
            pw.println("06-" + sc.getContext("/unknown").getInitParameter("id"));
            pw.println("07-" + sc.getContext("/").getInitParameter("id"));
            pw.println("08-" + sc.getContext("/foo/bar").getInitParameter("id"));
            pw.println("09-" + sc.getContext("/football").getInitParameter("id"));
        }
    }


    private static class SetIdListener implements LifecycleListener {

        private final String id;

        public SetIdListener(String id) {
            this.id = id;
        }

        @Override
        public void lifecycleEvent(LifecycleEvent event) {
            if (Lifecycle.CONFIGURE_START_EVENT.equals(event.getType())) {
                ((Context) event.getSource()).getServletContext().setInitParameter("id", id);
            }
        }
    }


    /*
     * The expectation is that you can set a context attribute on
     * ServletContextB from ServletContextA and then access that attribute via
     * a cross-context dispatch to ServletContextB.
     */
    @Test
    public void testCrossContextSetAttribute() throws Exception {
        // Setup Tomcat instance
        Tomcat tomcat = getTomcatInstance();

        // No file system docBase required
        Context ctx2 = tomcat.addContext("/second", null);
        GetAttributeServlet getAttributeServlet = new GetAttributeServlet();
        Tomcat.addServlet(ctx2, "getAttributeServlet", getAttributeServlet);
        ctx2.addServletMappingDecoded("/test", "getAttributeServlet");

        // No file system docBase required
        Context ctx1 = tomcat.addContext("/first", null);
        ctx1.setCrossContext(true);
        SetAttributeServlet setAttributeServlet = new SetAttributeServlet("/test", "/second");
        Tomcat.addServlet(ctx1, "setAttributeServlet", setAttributeServlet);
        ctx1.addServletMappingDecoded("/test", "setAttributeServlet");

        tomcat.start();

        ByteChunk bc = new ByteChunk();
        int rc = getUrl("http://localhost:" + getPort() + "/first/test", bc, null);

        Assert.assertEquals(200, rc);
        Assert.assertEquals("01-PASS", bc.toString());
    }


    private static class SetAttributeServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;
        private static final String ATTRIBUTE_NAME = "setAttributeTest";
        private static final String ATTRIBUTE_VALUE = "abcde";

        private final String targetContextPath;
        private final String targetPath;

        public SetAttributeServlet(String targetPath, String targetContextPath) {
            this.targetPath = targetPath;
            this.targetContextPath = targetContextPath;
        }

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {
            ServletContext sc;
            if (targetContextPath == null) {
                sc = req.getServletContext();
            } else {
                sc = req.getServletContext().getContext(targetContextPath);
            }
            sc.setAttribute(ATTRIBUTE_NAME, ATTRIBUTE_VALUE);
            sc.getRequestDispatcher(targetPath).forward(req, resp);
        }
    }


    private static class GetAttributeServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {
            resp.setContentType("text/plain");
            PrintWriter pw = resp.getWriter();
            String value = (String) req.getServletContext().getAttribute(
                    SetAttributeServlet.ATTRIBUTE_NAME);
            if (SetAttributeServlet.ATTRIBUTE_VALUE.equals(value)) {
                pw.print("01-PASS");
            } else {
                pw.print("01-FAIL");
            }
        }
    }
}
