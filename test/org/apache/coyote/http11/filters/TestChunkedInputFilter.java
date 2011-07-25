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

package org.apache.coyote.http11.filters;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.startup.SimpleHttpClient;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;

public class TestChunkedInputFilter extends TomcatBaseTest {

    @Test
    public void testTrailingHeaders() throws Exception {
        // Setup Tomcat instance
        Tomcat tomcat = getTomcatInstance();

        // Must have a real docBase - just use temp
        Context ctx = 
            tomcat.addContext("", System.getProperty("java.io.tmpdir"));

        Tomcat.addServlet(ctx, "servlet", new EchoHeaderServlet());
        ctx.addServletMapping("/", "servlet");

        tomcat.start();

        String[] request = new String[]{
            "POST /echo-params.jsp HTTP/1.1" + SimpleHttpClient.CRLF +
            "Host: any" + SimpleHttpClient.CRLF +
            "Transfer-encoding: chunked" + SimpleHttpClient.CRLF +
            "Content-Type: application/x-www-form-urlencoded" +
                    SimpleHttpClient.CRLF +
            "Connection: close" + SimpleHttpClient.CRLF +
            SimpleHttpClient.CRLF +
            "3" + SimpleHttpClient.CRLF +
            "a=0" + SimpleHttpClient.CRLF +
            "4" + SimpleHttpClient.CRLF +
            "&b=1" + SimpleHttpClient.CRLF +
            "0" + SimpleHttpClient.CRLF +
            "x-trailer: Test", "TestTest0123456789abcdefghijABCDEFGHIJopqrstuvwxyz" + SimpleHttpClient.CRLF +
            SimpleHttpClient.CRLF };

        TrailerClient client = new TrailerClient();
        client.setPort(getPort());
        client.setRequest(request);

        client.connect();
        client.processRequest();
        assertEquals("null7TestTestTest0123456789abcdefghijABCDEFGHIJopqrstuvwxyz", client.getResponseBody());
    }

    @Test
    public void testTrailingHeadersSizeLimit() throws Exception {
        // Setup Tomcat instance
        Tomcat tomcat = getTomcatInstance();

        // Must have a real docBase - just use temp
        Context ctx = 
            tomcat.addContext("", System.getProperty("java.io.tmpdir"));

        Tomcat.addServlet(ctx, "servlet", new EchoHeaderServlet());
        ctx.addServletMapping("/", "servlet");

        // Limit the size of the trailing header
        tomcat.getConnector().setProperty("maxTrailerSize", "10");
        tomcat.start();

        String[] request = new String[]{
            "POST /echo-params.jsp HTTP/1.1" + SimpleHttpClient.CRLF +
            "Host: any" + SimpleHttpClient.CRLF +
            "Transfer-encoding: chunked" + SimpleHttpClient.CRLF +
            "Content-Type: application/x-www-form-urlencoded" +
                    SimpleHttpClient.CRLF +
            "Connection: close" + SimpleHttpClient.CRLF +
            SimpleHttpClient.CRLF +
            "3" + SimpleHttpClient.CRLF +
            "a=0" + SimpleHttpClient.CRLF +
            "4" + SimpleHttpClient.CRLF +
            "&b=1" + SimpleHttpClient.CRLF +
            "0" + SimpleHttpClient.CRLF +
            "x-trailer: Test" + SimpleHttpClient.CRLF +
            SimpleHttpClient.CRLF };

        TrailerClient client = new TrailerClient();
        client.setPort(getPort());
        client.setRequest(request);

        client.connect();
        client.processRequest();
        // Expected to fail because the trailers are longer
        // than the default limit of 8Kb
        assertTrue(client.isResponse500());
    }

    @Test
    public void testNoTrailingHeaders() throws Exception {
        // Setup Tomcat instance
        Tomcat tomcat = getTomcatInstance();

        // Must have a real docBase - just use temp
        Context ctx = 
            tomcat.addContext("", System.getProperty("java.io.tmpdir"));

        Tomcat.addServlet(ctx, "servlet", new EchoHeaderServlet());
        ctx.addServletMapping("/", "servlet");

        tomcat.start();

        String request =
            "POST /echo-params.jsp HTTP/1.1" + SimpleHttpClient.CRLF +
            "Host: any" + SimpleHttpClient.CRLF +
            "Transfer-encoding: chunked" + SimpleHttpClient.CRLF +
            "Content-Type: application/x-www-form-urlencoded" +
                    SimpleHttpClient.CRLF +
            "Connection: close" + SimpleHttpClient.CRLF +
            SimpleHttpClient.CRLF +
            "3" + SimpleHttpClient.CRLF +
            "a=0" + SimpleHttpClient.CRLF +
            "4" + SimpleHttpClient.CRLF +
            "&b=1" + SimpleHttpClient.CRLF +
            "0" + SimpleHttpClient.CRLF +
            SimpleHttpClient.CRLF;

        TrailerClient client = new TrailerClient();
        client.setPort(getPort());
        client.setRequest(new String[] {request});

        client.connect();
        client.processRequest();
        assertEquals("null7null", client.getResponseBody());
    }
    
    private static class EchoHeaderServlet extends HttpServlet {
        private static final long serialVersionUID = 1L;

        @Override
        protected void doPost(HttpServletRequest req, HttpServletResponse resp) 
                throws ServletException, IOException {
            resp.setContentType("text/plain");
            PrintWriter pw = resp.getWriter();
            // Header not visible yet, body not processed
            String value = req.getHeader("x-trailer");
            if (value == null) {
                value = "null";
            }
            pw.write(value);

            // Read the body - quick and dirty
            InputStream is = req.getInputStream();
            int count = 0;
            while (is.read() > -1) {
                count++;
            }
            
            pw.write(Integer.valueOf(count).toString());
            
            // Header should be visible now
            value = req.getHeader("x-trailer");
            if (value == null) {
                value = "null";
            }
            pw.write(value);
        }
    }
    
    private static class TrailerClient extends SimpleHttpClient {

        @Override
        public boolean isResponseBodyOK() {
            return getResponseBody().contains("TestTestTest");
        }
    }
}
