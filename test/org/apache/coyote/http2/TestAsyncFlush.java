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

import jakarta.servlet.AsyncContext;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.Wrapper;
import org.apache.catalina.startup.Tomcat;

/*
 * Based on
 * https://bz.apache.org/bugzilla/show_bug.cgi?id=62635
 *
 * Note: Calling blocking I/O methods (such as flushBuffer()) during
 *       non-blocking I/O is explicitly called out as illegal in the Servlet
 *       specification but also goes on to say the behaviour if such a call is
 *       made is undefined. Which means it is OK if the call works as expected
 *       (a non-blocking flush is triggered) :).
 *       If any of these tests fail, that should not block a release since -
 *       while the specification allows this to work - it doesn't require that
 *       it does work.
 */
public class TestAsyncFlush extends Http2TestBase {

    private static final int BLOCK_SIZE = 1024;

    @Test
    public void testFlush() throws Exception {
        int blockCount = 2048;

        int targetSize = BLOCK_SIZE * blockCount;

        int totalWindow = ConnectionSettingsBase.DEFAULT_INITIAL_WINDOW_SIZE;

        enableHttp2();

        Tomcat tomcat = getTomcatInstance();

        Context ctxt = tomcat.addContext("", null);
        Tomcat.addServlet(ctxt, "simple", new SimpleServlet());
        ctxt.addServletMappingDecoded("/simple", "simple");
        Wrapper w = Tomcat.addServlet(ctxt, "async", new AsyncFlushServlet(blockCount));
        w.setAsyncSupported(true);
        ctxt.addServletMappingDecoded("/async", "async");
        tomcat.start();

        openClientConnection();
        doHttpUpgrade();
        sendClientPreface();
        validateHttp2InitialResponse();

        // Reset connection window size after initial response
        sendWindowUpdate(0, SimpleServlet.CONTENT_LENGTH);

        // Send request
        byte[] frameHeader = new byte[9];
        ByteBuffer headersPayload = ByteBuffer.allocate(128);
        buildGetRequest(frameHeader, headersPayload, null, 3, "/async");
        writeFrame(frameHeader, headersPayload);

        // Headers
        parser.readFrame();
        // Body

        while (output.getBytesRead() < targetSize ) {
            if (output.getBytesRead() == totalWindow) {
                sendWindowUpdate(3, ConnectionSettingsBase.DEFAULT_INITIAL_WINDOW_SIZE);
                sendWindowUpdate(0, ConnectionSettingsBase.DEFAULT_INITIAL_WINDOW_SIZE);
                totalWindow += ConnectionSettingsBase.DEFAULT_INITIAL_WINDOW_SIZE;
            }
            parser.readFrame();
        }

        // Check that the right number of bytes were received
        Assert.assertEquals(targetSize, output.getBytesRead());
    }


    public static class AsyncFlushServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        private final int blockLimit;

        public AsyncFlushServlet(int blockLimit) {
            this.blockLimit = blockLimit;
        }

        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws IOException {

            final AsyncContext asyncContext = request.startAsync();

            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentType("application/binary");

            final ServletOutputStream output = response.getOutputStream();
            output.setWriteListener(new WriteListener() {

                int blockCount;
                byte[] bytes = new byte[BLOCK_SIZE];


                @Override
                public void onWritePossible() throws IOException {
                    while (output.isReady()) {
                        blockCount++;
                        output.write(bytes);
                        if (blockCount % 5 == 0) {
                            response.flushBuffer();
                        }
                        if (blockCount == blockLimit) {
                            asyncContext.complete();
                            return;
                        }
                    }
                }


                @Override
                public void onError(Throwable t) {
                    t.printStackTrace();
                }
            });
        }
    }
}
