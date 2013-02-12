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

import javax.servlet.RequestDispatcher;
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

public class TestErrorReportValve extends TomcatBaseTest {

    @Test
    public void testBug53071() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        // Must have a real docBase - just use temp
        Context ctx =
            tomcat.addContext("", System.getProperty("java.io.tmpdir"));

        Tomcat.addServlet(ctx, "errorServlet", new ErrorServlet());
        ctx.addServletMapping("/", "errorServlet");

        tomcat.start();

        ByteChunk res = getUrl("http://localhost:" + getPort());

        Assert.assertTrue(res.toString().contains("<p><b>message</b> <u>" +
                ErrorServlet.ERROR_TEXT + "</u></p>"));
    }


    private static final class ErrorServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;
        private static final String ERROR_TEXT = "The wheels fell off.";
        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {
            req.setAttribute(RequestDispatcher.ERROR_EXCEPTION,
                    new Throwable(ERROR_TEXT));
            resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }


    @Test
    public void testBug54220DoNotSetNotFound() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        // Must have a real docBase - just use temp
        Context ctx =
            tomcat.addContext("", System.getProperty("java.io.tmpdir"));

        Tomcat.addServlet(ctx, "bug54220", new Bug54220Servlet(false));
        ctx.addServletMapping("/", "bug54220");

        tomcat.start();

        ByteChunk res = new ByteChunk();
        int rc = getUrl("http://localhost:" + getPort(), res, null);

        Assert.assertNull(res.toString());
        Assert.assertEquals(HttpServletResponse.SC_OK, rc);
    }


    @Test
    public void testBug54220SetNotFound() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        // Must have a real docBase - just use temp
        Context ctx =
            tomcat.addContext("", System.getProperty("java.io.tmpdir"));

        Tomcat.addServlet(ctx, "bug54220", new Bug54220Servlet(true));
        ctx.addServletMapping("/", "bug54220");

        tomcat.start();

        ByteChunk res = new ByteChunk();
        int rc = getUrl("http://localhost:" + getPort(), res, null);

        Assert.assertNull(res.toString());
        Assert.assertEquals(HttpServletResponse.SC_NOT_FOUND, rc);
    }


    private static final class Bug54220Servlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        private boolean setNotFound;

        private Bug54220Servlet(boolean setNotFound) {
            this.setNotFound = setNotFound;
        }

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {

            if (setNotFound) {
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            }
        }
    }


    /**
     * Custom error/status codes should not result in a blank response.
     */
    @Test
    public void testBug54536() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        // Must have a real docBase - just use temp
        Context ctx =
            tomcat.addContext("", System.getProperty("java.io.tmpdir"));

        Tomcat.addServlet(ctx, "bug54536", new Bug54536Servlet());
        ctx.addServletMapping("/", "bug54536");

        tomcat.start();

        ByteChunk res = new ByteChunk();
        int rc = getUrl("http://localhost:" + getPort(), res, null);

        Assert.assertEquals(Bug54536Servlet.ERROR_STATUS, rc);
        String body = res.toString();
        Assert.assertNotNull(body);
        Assert.assertTrue(body, body.contains(Bug54536Servlet.ERROR_MESSAGE));
    }


    private static final class Bug54536Servlet extends HttpServlet {

        private static final long serialVersionUID = 1L;
        private static final int ERROR_STATUS = 999;
        private static final String ERROR_MESSAGE = "The sky is falling";

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {
            resp.sendError(ERROR_STATUS, ERROR_MESSAGE);
        }
    }
}
