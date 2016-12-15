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
package org.apache.coyote.http2;

import java.io.IOException;
import java.nio.ByteBuffer;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;

public class TestStream extends Http2TestBase {

    @Test
    public void testQueryString() throws Exception {
        enableHttp2();

        Tomcat tomcat = getTomcatInstance();

        // Map the async servlet to /simple so we can re-use the HTTP/2 handling
        // logic from the super class.
        Context ctxt = tomcat.addContext("", null);
        Tomcat.addServlet(ctxt, "simple", new SimpleServlet());
        ctxt.addServletMappingDecoded("/simple", "simple");
        Tomcat.addServlet(ctxt, "query", new QueryStringServlet());
        ctxt.addServletMappingDecoded("/query", "query");

        tomcat.start();

        openClientConnection();
        doHttpUpgrade();
        sendClientPreface();
        validateHttp2InitialResponse();

        byte[] frameHeader = new byte[9];
        ByteBuffer headersPayload = ByteBuffer.allocate(128);
        buildGetRequest(frameHeader, headersPayload, null, 3, "/query?foo=b%26a");
        writeFrame(frameHeader, headersPayload);

        readSimpleGetResponse();

        Assert.assertEquals(
                "3-HeadersStart\n" +
                "3-Header-[:status]-[200]\n" +
                "3-Header-[query]-[foo=b%26a]\n" +
                "3-Header-[foo]-[b&a]\n" +
                "3-Header-[date]-[Wed, 11 Nov 2015 19:18:42 GMT]\n" +
                "3-HeadersEnd\n" +
                "3-Body-0\n" +
                "3-EndOfStream\n", output.getTrace());
    }

    private static class QueryStringServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {
            resp.setHeader("query", req.getQueryString());
            resp.setHeader("foo", req.getParameter("foo"));
        }

    }

}
