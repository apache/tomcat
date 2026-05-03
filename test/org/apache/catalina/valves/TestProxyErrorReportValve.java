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

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.Valve;
import org.apache.catalina.core.StandardHost;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.buf.ByteChunk;

public class TestProxyErrorReportValve extends TomcatBaseTest {

    private static final String PROXY_VALVE =
            "org.apache.catalina.valves.ProxyErrorReportValve";


    @Test
    public void testRedirectMode() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        StandardHost host = (StandardHost) tomcat.getHost();
        host.setErrorReportValveClass(PROXY_VALVE);

        Context ctx = getProgrammaticRootContext();

        Tomcat.addServlet(ctx, "error", new SendErrorServlet(
                HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Server broke"));
        ctx.addServletMappingDecoded("/", "error");

        // Register an error page at the Host's error report valve level
        // so findErrorPage() returns a URL for the redirect
        Tomcat.addServlet(ctx, "errorPage", new ErrorPageServlet());
        ctx.addServletMappingDecoded("/error-page", "errorPage");

        tomcat.start();

        ProxyErrorReportValve valve = null;
        Valve[] valves = host.getPipeline().getValves();
        for (Valve valveCandidate : valves) {
            if (PROXY_VALVE.equals(valveCandidate.getClass().getName())) {
                valve = (ProxyErrorReportValve)valveCandidate;
                break;
            }
        }
        Assert.assertNotNull(valve);
        valve.setProperty("errorCode." + HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                "http://localhost:" + getPort() + "/error-page");

        int rc = getUrl("http://localhost:" + getPort(), new ByteChunk(), false);

        Assert.assertEquals(HttpServletResponse.SC_FOUND, rc);
    }

    @Test
    public void testProxyMode() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        StandardHost host = (StandardHost) tomcat.getHost();
        host.setErrorReportValveClass(PROXY_VALVE);

        Context ctx = getProgrammaticRootContext();

        Tomcat.addServlet(ctx, "error", new SendErrorServlet(
                HttpServletResponse.SC_NOT_FOUND, "Not found"));
        ctx.addServletMappingDecoded("/", "error");

        Tomcat.addServlet(ctx, "errorPage", new ErrorPageServlet());
        ctx.addServletMappingDecoded("/error-page", "errorPage");

        tomcat.start();

        ProxyErrorReportValve valve = null;
        Valve[] valves = host.getPipeline().getValves();
        for (Valve valveCandidate : valves) {
            if (PROXY_VALVE.equals(valveCandidate.getClass().getName())) {
                valve = (ProxyErrorReportValve)valveCandidate;
                break;
            }
        }
        Assert.assertNotNull(valve);
        valve.setUseRedirect(false);
        valve.setProperty("errorCode." + HttpServletResponse.SC_NOT_FOUND,
                "http://localhost:" + getPort() + "/error-page");

        ByteChunk res = new ByteChunk();
        int rc = getUrl("http://localhost:" + getPort(), res, null);

        Assert.assertEquals(HttpServletResponse.SC_NOT_FOUND, rc);
        Assert.assertTrue(res.toString().contains("ERROR_PAGE_OK"));
    }


    @Test
    public void testNoErrorPageFallsBackToSuper() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        ((StandardHost) tomcat.getHost()).setErrorReportValveClass(PROXY_VALVE);

        Context ctx = getProgrammaticRootContext();

        Tomcat.addServlet(ctx, "error", new SendErrorServlet(
                HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "No page configured"));
        ctx.addServletMappingDecoded("/", "error");

        tomcat.start();

        ByteChunk res = new ByteChunk();
        int rc = getUrl("http://localhost:" + getPort(), res, null);

        Assert.assertEquals(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, rc);

        String body = res.toString();
        Assert.assertNotNull(body);
        Assert.assertTrue("Should contain HTML error report",
                body.contains("html") &&
                    body.contains(String.valueOf(HttpServletResponse.SC_INTERNAL_SERVER_ERROR)));
    }


    @Test
    public void testStatusBelow400Ignored() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        ((StandardHost) tomcat.getHost()).setErrorReportValveClass(PROXY_VALVE);

        Context ctx = getProgrammaticRootContext();

        Tomcat.addServlet(ctx, "hello", new HelloWorldServlet());
        ctx.addServletMappingDecoded("/", "hello");

        tomcat.start();

        ByteChunk res = new ByteChunk();
        int rc = getUrl("http://localhost:" + getPort(), res, null);

        Assert.assertEquals(HttpServletResponse.SC_OK, rc);
        Assert.assertEquals(HelloWorldServlet.RESPONSE_TEXT, res.toString());
    }


    @Test
    public void testStatusNotFound() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        ((StandardHost) tomcat.getHost()).setErrorReportValveClass(PROXY_VALVE);

        Context ctx = getProgrammaticRootContext();

        Tomcat.addServlet(ctx, "notFound", new SendErrorServlet(
                HttpServletResponse.SC_NOT_FOUND, "Resource not found"));
        ctx.addServletMappingDecoded("/", "notFound");

        tomcat.start();

        ByteChunk res = new ByteChunk();
        int rc = getUrl("http://localhost:" + getPort(), res, null);

        Assert.assertEquals(HttpServletResponse.SC_NOT_FOUND, rc);

        String body = res.toString();
        Assert.assertNotNull(body);
        Assert.assertTrue("Should contain error report",
                body.contains(String.valueOf(HttpServletResponse.SC_NOT_FOUND)));
    }


    @Test
    public void testGetSetProperties() {
        ProxyErrorReportValve valve = new ProxyErrorReportValve();

        Assert.assertTrue(valve.getUseRedirect());
        Assert.assertFalse(valve.getUsePropertiesFile());

        valve.setUseRedirect(false);
        Assert.assertFalse(valve.getUseRedirect());

        valve.setUsePropertiesFile(true);
        Assert.assertTrue(valve.getUsePropertiesFile());
    }


    @Test
    public void testMessageInErrorReport() throws Exception {
        final String customErrorMessage = "Custom error message";
        Tomcat tomcat = getTomcatInstance();
        ((StandardHost) tomcat.getHost()).setErrorReportValveClass(PROXY_VALVE);

        Context ctx = getProgrammaticRootContext();

        Tomcat.addServlet(ctx, "error", new SendErrorServlet(
                HttpServletResponse.SC_INTERNAL_SERVER_ERROR, customErrorMessage));
        ctx.addServletMappingDecoded("/", "error");

        tomcat.start();

        ByteChunk res = new ByteChunk();
        int rc = getUrl("http://localhost:" + getPort(), res, null);

        Assert.assertEquals(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, rc);

        String body = res.toString();
        Assert.assertNotNull(body);
        // Falls back to super.report() which includes the message
        Assert.assertTrue(body.contains(customErrorMessage));
    }


    @Test
    public void testExceptionErrorReport() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        ((StandardHost) tomcat.getHost()).setErrorReportValveClass(PROXY_VALVE);

        Context ctx = getProgrammaticRootContext();

        Tomcat.addServlet(ctx, "exception", new ExceptionServlet());
        ctx.addServletMappingDecoded("/", "exception");

        tomcat.start();

        ByteChunk res = new ByteChunk();
        int rc = getUrl("http://localhost:" + getPort(), res, null);

        Assert.assertEquals(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, rc);

        String body = res.toString();
        Assert.assertNotNull(body);
        Assert.assertTrue(body.contains("RuntimeException"));
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
            resp.sendError(statusCode, message);
        }
    }

    private static final class ErrorPageServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws IOException {
            resp.getWriter().print("ERROR_PAGE_OK");
        }
    }


    private static final class ExceptionServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
            throw new RuntimeException("Test exception");
        }
    }
}
