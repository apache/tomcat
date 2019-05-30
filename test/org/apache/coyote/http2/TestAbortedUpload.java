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

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.startup.Tomcat;

public class TestAbortedUpload extends Http2TestBase {

    @Test
    public void testAbortedRequest() throws Exception {
        http2Connect();

        http2Protocol.setAllowedTrailerHeaders(TRAILER_HEADER_NAME);

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

        // The actual response depends on timing issues. Particularly how much
        // data is transferred in StreamInputBuffer inBuffer to outBuffer on the
        // first read.
        while (output.getTrace().length() == 0) {
            try {
                parser.readFrame(true);
                if ("3-RST-[3]\n".equals(output.getTrace())) {
                    output.clearTrace();
                }
            } catch (IOException ioe) {
                // Might not be any further frames after the reset
                break;
            }
        }

        if (output.getTrace().startsWith("0-WindowSize-[")) {
            String trace = output.getTrace();
            int size = Integer.parseInt(trace.substring(14, trace.length() - 2));
            output.clearTrace();
            // Window updates always come in pairs
            parser.readFrame(true);
            Assert.assertEquals("3-WindowSize-[" + size + "]\n", output.getTrace());
        }
    }


    @Override
    protected void configureAndStartWebApplication() throws LifecycleException {
        Tomcat tomcat = getTomcatInstance();

        // Retain '/simple' url-pattern since it enables code re-use
        Context ctxt = tomcat.addContext("", null);
        Tomcat.addServlet(ctxt, "abort", new AbortServlet());
        ctxt.addServletMappingDecoded("/simple", "abort");

        tomcat.start();
    }


    private static class AbortServlet extends SimpleServlet {

        private static final long serialVersionUID = 1L;

        @Override
        protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            // Read upto 128 bytes and then return a 403 response

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
