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
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;

public class TestInvalidHeader extends Http2TestBase {

    /*
     * @see org.apache.coyote.Response#checkSpecialHeaders()
     */
//    @Test
//    public void testInvalidHeader() throws Exception {
//
//        enableHttp2();
//        configureAndStartWebApplication();
//
//        openClientConnection();
//        doHttpUpgrade();
//        sendClientPreface();
//        validateHttp2InitialResponse();
//
//        byte[] frameHeader = new byte[9];
//        ByteBuffer headersPayload = ByteBuffer.allocate(128);
//        List<Header> headers = new ArrayList<>(3);
//        headers.add(new Header(":method", "GET"));
//        headers.add(new Header(":scheme", "http"));
//        headers.add(new Header(":path", "/simple"));
//        headers.add(new Header(":authority", "localhost:" + getPort()));
//        headers.add(new Header("Connection", "keep-alive"));
//
//        buildGetRequest(frameHeader, headersPayload, null, headers, 3);
//
//        writeFrame(frameHeader, headersPayload);
//
//        readSimpleGetResponse();
//
//        Assert.assertEquals(getSimpleResponseTrace(3), output.getTrace());
//    }

    protected static class FaultyServlet extends SimpleServlet
    {

        private static final long serialVersionUID = 1L;

        public static final int CONTENT_LENGTH = 8192;

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException
        {
            // Add faulty header
            resp.addHeader("Connection", "keep-alive");
            super.doGet(req, resp);
        }
    }


    @Test
    public void testInvalidHeader() throws Exception {

        enableHttp2();

        Tomcat tomcat = getTomcatInstance();

        Context ctxt = tomcat.addContext("", null);
        Tomcat.addServlet(ctxt, "simple", new FaultyServlet());
        ctxt.addServletMappingDecoded("/simple", "simple");

        tomcat.start();

        openClientConnection();
        doHttpUpgrade();
        sendClientPreface();
        validateHttp2InitialResponse();

        byte[] frameHeader = new byte[9];
        ByteBuffer headersPayload = ByteBuffer.allocate(128);
        buildGetRequest(frameHeader, headersPayload, null, 3, "/simple");

        writeFrame(frameHeader, headersPayload);

        readSimpleGetResponse();

        Assert.assertEquals(getSimpleResponseTrace(3), output.getTrace());
    }

}
