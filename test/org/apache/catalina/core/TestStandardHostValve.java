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

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequestEvent;
import jakarta.servlet.ServletRequestListener;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.connector.Response;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.descriptor.web.ErrorPage;

public class TestStandardHostValve extends TomcatBaseTest {

    private static final String FAIL_NO_MESSAGE = "FAIL - NO MESSAGE";
    private static final String EMPTY_NO_MESSAGE = "EMPTY - NO MESSAGE";

    @Test
    public void testErrorPageHandling400() throws Exception {
        doTestErrorPageHandling(400, "", "/400");
    }


    @Test
    public void testErrorPageHandling400WithException() throws Exception {
        doTestErrorPageHandling(400, "java.lang.IllegalStateException", "/400");
    }


    @Test
    public void testErrorPageHandling500() throws Exception {
        doTestErrorPageHandling(500, "", "/500");
    }


    @Test
    public void testErrorPageHandlingDefault() throws Exception {
        doTestErrorPageHandling(501, "", "/default");
    }


    @Test
    public void testErrorPageHandlingException() throws Exception {
        doTestErrorPageHandling(0, IOException.class.getCanonicalName(), "/IOE");
    }


    private void doTestErrorPageHandling(int error, String exception, String report)
            throws Exception {

        // Set up a container
        Tomcat tomcat = getTomcatInstance();

        // No file system docBase required
        Context ctx = getProgrammaticRootContext();

        // Add the error page
        Tomcat.addServlet(ctx, "error", new ErrorServlet());
        ctx.addServletMappingDecoded("/error", "error");

        // Add the error handling page
        Tomcat.addServlet(ctx, "report", new ReportServlet());
        ctx.addServletMappingDecoded("/report/*", "report");

        // Add the handling for 400 responses
        ErrorPage errorPage400 = new ErrorPage();
        errorPage400.setErrorCode(Response.SC_BAD_REQUEST);
        errorPage400.setLocation("/report/400");
        ctx.addErrorPage(errorPage400);

        // And the handling for 500 responses
        ErrorPage errorPage500 = new ErrorPage();
        errorPage500.setErrorCode(Response.SC_INTERNAL_SERVER_ERROR);
        errorPage500.setLocation("/report/500");
        ctx.addErrorPage(errorPage500);

        // And the handling for IOEs
        ErrorPage errorPageIOE = new ErrorPage();
        errorPageIOE.setExceptionType(IOException.class.getCanonicalName());
        errorPageIOE.setLocation("/report/IOE");
        ctx.addErrorPage(errorPageIOE);

        // And the default error handling
        ErrorPage errorPageDefault = new ErrorPage();
        errorPageDefault.setLocation("/report/default");
        ctx.addErrorPage(errorPageDefault);

        tomcat.start();

        // Request a page that triggers an error
        ByteChunk bc = new ByteChunk();
        int rc = getUrl("http://localhost:" + getPort() + "/error?errorCode=" + error + "&exception=" + exception,
                bc, null);

        if (error > 399) {
            // Specific status code expected
            Assert.assertEquals(error, rc);
        } else {
            // Default error status code expected
            Assert.assertEquals(500, rc);
        }
        String[] responseLines = (bc.toString().split(System.lineSeparator()));
        // First line should be the path
        Assert.assertEquals(report, responseLines[0]);
        // Second line should not be the null message warning
        Assert.assertNotEquals(FAIL_NO_MESSAGE, responseLines[1]);
    }


    @Test(expected=IllegalArgumentException.class)
    public void testInvalidErrorPage() throws Exception {
        // No file system docBase required
        Context ctx = getProgrammaticRootContext();

        // Add a broken error page configuration
        ErrorPage errorPage500 = new ErrorPage();
        errorPage500.setErrorCode("java.lang.Exception");
        errorPage500.setLocation("/report/500");
        ctx.addErrorPage(errorPage500);
    }


    @Test
    public void testSRLAfterError() throws Exception {
        // Set up a container
        Tomcat tomcat = getTomcatInstance();

        // No file system docBase required
        Context ctx = getProgrammaticRootContext();

        // Add the error page
        Tomcat.addServlet(ctx, "error", new ErrorServlet());
        ctx.addServletMappingDecoded("/error", "error");

        final List<String> result = new ArrayList<>();

        // Add the request listener
        ServletRequestListener servletRequestListener = new ServletRequestListener() {

            @Override
            public void requestDestroyed(ServletRequestEvent sre) {
                result.add("Visit requestDestroyed");
            }

            @Override
            public void requestInitialized(ServletRequestEvent sre) {
                result.add("Visit requestInitialized");
            }

        };
        ((StandardContext) ctx).addApplicationEventListener(servletRequestListener);

        tomcat.start();

        // Request a page that triggers an error
        ByteChunk bc = new ByteChunk();
        int rc = getUrl("http://localhost:" + getPort() + "/error?errorCode=400", bc, null);

        Assert.assertEquals(400, rc);
        Assert.assertTrue(result.contains("Visit requestInitialized"));
        Assert.assertTrue(result.contains("Visit requestDestroyed"));
    }


    @Test
    public void testIncompleteResponse() throws Exception {
        // Set up a container
        Tomcat tomcat = getTomcatInstance();

        // No file system docBase required
        Context ctx = getProgrammaticRootContext();

        // Add the error page
        Tomcat.addServlet(ctx, "error", new ExceptionServlet());
        ctx.addServletMappingDecoded("/error", "error");

        // Add the error handling page
        Tomcat.addServlet(ctx, "report", new ReportServlet());
        ctx.addServletMappingDecoded("/report/*", "report");

        // Add the handling for 500 responses
        ErrorPage errorPage500 = new ErrorPage();
        errorPage500.setErrorCode(Response.SC_INTERNAL_SERVER_ERROR);
        errorPage500.setLocation("/report/500");
        ctx.addErrorPage(errorPage500);

        // Add the default error handling
        ErrorPage errorPageDefault = new ErrorPage();
        errorPageDefault.setLocation("/report/default");
        ctx.addErrorPage(errorPageDefault);

        tomcat.start();

        // Request a page that triggers an error
        ByteChunk bc = new ByteChunk();
        Throwable t = null;
        try {
            getUrl("http://localhost:" + getPort() + "/error", bc, null);
            System.out.println(bc.toString());
        } catch (IOException ioe) {
            t = ioe;
        }
        Assert.assertNotNull(t);
    }


    private static class ErrorServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {
            int error = Integer.parseInt(req.getParameter("errorCode"));
            if (error > 399) {
                resp.sendError(error);
            }

            Throwable t = null;
            String exception = req.getParameter("exception");
            if (exception != null && exception.length() > 0) {
                try {
                    t = (Throwable) Class.forName(exception).getConstructor().newInstance();
                } catch (ReflectiveOperationException e) {
                    // Should never happen but in case it does...
                    t = new IllegalArgumentException();
                }
                if (error < 400) {
                    if (t instanceof RuntimeException) {
                        throw (RuntimeException) t;
                    } else if (t instanceof IOException) {
                        throw (IOException) t;
                    } else if (t instanceof ServletException) {
                        throw (ServletException) t;
                    }
                }
            }
        }
    }


    private static class ExceptionServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {
            resp.flushBuffer();
            throw new IOException();
        }
    }


    private static class ReportServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {
            String pathInfo = req.getPathInfo();
            resp.setContentType("text/plain");
            PrintWriter pw = resp.getWriter();
            pw.println(pathInfo);

            String message = (String) req.getAttribute(RequestDispatcher.ERROR_MESSAGE);
            if (message == null) {
                message = FAIL_NO_MESSAGE;
            } else if (message.length() == 0) {
                message = EMPTY_NO_MESSAGE;
            }
            pw.println(message);
        }
    }
}
