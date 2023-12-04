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
import java.nio.charset.StandardCharsets;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.descriptor.web.ErrorPage;


public class TestApplicationDispatcher extends TomcatBaseTest {

    @Test
    public void testDispatchErrorToServlet() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        // No file system docBase required
        Context ctx = getProgrammaticRootContext();

        Tomcat.addServlet(ctx, "errorTrigger", new ErrorTriggerServlet());
        ctx.addServletMappingDecoded("/trigger", "errorTrigger");

        Tomcat.addServlet(ctx, "errorHandling", new ErrorHandlingServlet());
        ctx.addServletMappingDecoded("/error", "errorHandling");

        ErrorPage ep = new ErrorPage();
        ep.setExceptionType(Throwable.class.getName());
        ep.setLocation("/error");
        ctx.addErrorPage(ep);

        tomcat.start();

        ByteChunk response = new ByteChunk();
        int rc = postUrl(new byte[0], "http://localhost:" + getPort() + "/trigger", response, null);

        Assert.assertEquals(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, rc);
        Assert.assertEquals("OK", response.toString());
    }


    @Test
    public void testDispatchErrorToJspWithErrorPage() throws Exception {
        getTomcatInstanceTestWebapp(false, true);

        ByteChunk response = new ByteChunk();
        int rc = postUrl(new byte[0], "http://localhost:" + getPort() + "/test/dispatch/trigger-with-error-page.jsp",
                response, null);

        Assert.assertEquals(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, rc);
        Assert.assertEquals("OK", response.toString().trim());
    }


    @Test
    public void testDispatchErrorToJspWithoutErrorPage() throws Exception {
        Tomcat tomcat = getTomcatInstanceTestWebapp(false, false);
        Context ctx = (Context) tomcat.getHost().findChildren()[0];

        Tomcat.addServlet(ctx, "errorHandling", new ErrorHandlingServlet());
        ctx.addServletMappingDecoded("/error", "errorHandling");

        ErrorPage ep = new ErrorPage();
        ep.setExceptionType(Throwable.class.getName());
        ep.setLocation("/error");
        ctx.addErrorPage(ep);

        tomcat.start();

        ByteChunk response = new ByteChunk();
        int rc = postUrl(new byte[0], "http://localhost:" + getPort() + "/test/dispatch/trigger-without-error-page.jsp",
                response, null);

        Assert.assertEquals(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, rc);
        Assert.assertEquals("OK", response.toString().trim());
    }


    private static class ErrorTriggerServlet extends HttpServlet {
        private static final long serialVersionUID = 1L;

        @Override
        protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            throw new RuntimeException("Forced failure");
        }
    }


    private static class ErrorHandlingServlet extends HttpServlet {
        private static final long serialVersionUID = 1L;

        @Override
        protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            resp.setContentType("text/plain");
            resp.setCharacterEncoding(StandardCharsets.UTF_8);
            PrintWriter pw = resp.getWriter();

            if ("POST".equals(req.getAttribute(RequestDispatcher.ERROR_METHOD)) &&
                    "GET".equals(req.getMethod())) {
                pw.print("OK");
            } else {
                pw.print("FAIL");
            }
        }
    }
}
