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
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.logging.Level;
import java.util.logging.LogManager;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.startup.Tomcat;
import org.apache.coyote.http11.AbstractHttp11Protocol;

public class TestCancelledUpload extends Http2TestBase {

    @Test
    public void testCancelledRequest() throws Exception {
        http2Connect();

        LogManager.getLogManager().getLogger("org.apache.coyote.http2").setLevel(Level.ALL);
        try {
            ((AbstractHttp11Protocol<?>) http2Protocol.getHttp11Protocol()).setAllowedTrailerHeaders(TRAILER_HEADER_NAME);

            int bodySize = 8192;
            int bodyCount = 20;

            byte[] headersFrameHeader = new byte[9];
            ByteBuffer headersPayload = ByteBuffer.allocate(128);
            byte[] dataFrameHeader = new byte[9];
            ByteBuffer dataPayload = ByteBuffer.allocate(bodySize);
            byte[] trailerFrameHeader = new byte[9];
            ByteBuffer trailerPayload = ByteBuffer.allocate(256);

            buildPostRequest(headersFrameHeader, headersPayload, false, dataFrameHeader, dataPayload,
                    null, trailerFrameHeader, trailerPayload, 3);

            // Write the headers
            writeFrame(headersFrameHeader, headersPayload);
            // Body
            for (int i = 0; i < bodyCount; i++) {
                writeFrame(dataFrameHeader, dataPayload);
            }

            // Trailers
            writeFrame(trailerFrameHeader, trailerPayload);

            // The Server will process the request on a separate thread to the
            // incoming frames.
            // The request processing thread will:
            // - read up to 128 bytes of request body
            //   (and issue a window update for bytes read)
            // - write a 403 response with no response body
            // The connection processing thread will:
            // - read the request body until the flow control window is exhausted
            // - reset the stream if further DATA frames are received
            parser.readFrame();

            // Check for reset and exit if found
            if (checkReset()) {
                return;
            }

            // Not window update, not reset, must be the headers
            Assert.assertEquals("3-HeadersStart\n" +
                    "3-Header-[:status]-[403]\n" +
                    "3-Header-[content-length]-[0]\n" +
                    "3-Header-[date]-[Wed, 11 Nov 2015 19:18:42 GMT]\n" +
                    "3-HeadersEnd\n",
                    output.getTrace());
            output.clearTrace();
            parser.readFrame();

            // Check for reset and exit if found
            if (checkReset()) {
                return;
            }

            // Not window update, not reset, must be the response body
            Assert.assertEquals("3-Body-0\n" +
                    "3-EndOfStream\n",
                    output.getTrace());
            output.clearTrace();
            parser.readFrame();

            Assert.assertTrue(checkReset());

            // If there are any more frames after this, ignore them
        } finally {
            LogManager.getLogManager().getLogger("org.apache.coyote.http2").setLevel(Level.INFO);
        }
    }


    /*
     * Looking for a RST frame with error type 3 (flow control error).
     *
     * If there is a flow control window update for stream 0 it may be followed
     * by one for stream 3.
     *
     * If there is a flow control window update for stream 3 it will always be
     * preceded by one for stream 0.
     */
    private boolean checkReset() throws IOException, Http2Exception {
        int lastConnectionFlowControlWindowUpdate = -1;
        while (true) {
            String trace = output.getTrace();
            if (trace.startsWith("3-RST-[3]\n")) {
                // This is the reset we are looking for
                return true;
            } else if (trace.startsWith("3-RST-[")) {
                // Probably error type 8 (cancel) - ignore
            } else if (trace.startsWith("0-WindowSize-[")) {
                // Connection flow control window update
                lastConnectionFlowControlWindowUpdate = Integer.parseInt(trace.substring(14, trace.length() - 2));
            } else if (trace.startsWith("3-WindowSize-[")) {
                // Stream flow control window update
                // Must be same size as last connection flow control window
                // update. True for Tomcat anyway.
                Assert.assertEquals("3-WindowSize-[" + lastConnectionFlowControlWindowUpdate + "]\n", trace);
            } else {
                return false;
            }
            output.clearTrace();
            parser.readFrame();
        }
    }


    @Override
    protected void configureAndStartWebApplication() throws LifecycleException {
        Tomcat tomcat = getTomcatInstance();

        // Retain '/simple' url-pattern since it enables code re-use
        Context ctxt = tomcat.addContext("", null);
        Tomcat.addServlet(ctxt, "cancel", new CancelServlet());
        ctxt.addServletMappingDecoded("/simple", "cancel");

        tomcat.start();
    }


    private static class CancelServlet extends SimpleServlet {

        private static final long serialVersionUID = 1L;

        @Override
        protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            // Read up to 128 bytes and then return a 403 response

            InputStream is = req.getInputStream();
            byte[] buf = new byte[128];
            int toRead = 128;

            int read = is.read(buf);
            while (read != -1 && toRead > 0) {
                toRead -= read;
                read = is.read(buf);
            }

            if (toRead == 0) {
                resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
            } else {
                resp.setStatus(HttpServletResponse.SC_OK);
            }
        }
    }
}
