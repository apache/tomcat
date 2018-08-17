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

import javax.servlet.AsyncContext;
import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.Wrapper;
import org.apache.catalina.startup.Tomcat;

public class TestAsync extends Http2TestBase {

    private static final int BLOCK_SIZE = 0x8000;

    // https://bz.apache.org/bugzilla/show_bug.cgi?id=62614
    @Test
    public void testEmptyBothWindowsUpdateConnectionFirst() throws Exception {
        doEmptyWindowTest(true, false, false);
    }


    @Test
    public void testEmptyBothWindowsUpdateStreamFirst() throws Exception {
        doEmptyWindowTest(false, false, false);
    }


    @Test
    public void testEmptyConnectionWindowUpdateConnectionFirst() throws Exception {
        doEmptyWindowTest(true, false, true);
    }


    @Test
    public void testEmptyConnectionWindowUpdateStreamFirst() throws Exception {
        doEmptyWindowTest(false, false, true);
    }


    @Test
    public void testEmptyStreamWindowUpdateConnectionFirst() throws Exception {
        doEmptyWindowTest(true, true, false);
    }


    @Test
    public void testEmptyStreamWindowUpdateStreamFirst() throws Exception {
        doEmptyWindowTest(false, true, false);
    }


    // No point testing when both Stream and Connection are unlimited


    private void doEmptyWindowTest(boolean expandConnectionFirst, boolean connectionUnlimited,
            boolean streamUnlimited) throws Exception {
        int blockCount = 4;

        enableHttp2();

        Tomcat tomcat = getTomcatInstance();

        Context ctxt = tomcat.addContext("", null);
        Tomcat.addServlet(ctxt, "simple", new SimpleServlet());
        ctxt.addServletMappingDecoded("/simple", "simple");
        Wrapper w = Tomcat.addServlet(ctxt, "async", new AsyncServlet(blockCount));
        w.setAsyncSupported(true);
        ctxt.addServletMappingDecoded("/async", "async");
        tomcat.start();

        openClientConnection();
        doHttpUpgrade();
        sendClientPreface();
        validateHttp2InitialResponse();

        byte[] frameHeader = new byte[9];
        ByteBuffer headersPayload = ByteBuffer.allocate(128);
        buildGetRequest(frameHeader, headersPayload, null, 3, "/async");
        writeFrame(frameHeader, headersPayload);

        // Reset connection window size after intial response
        sendWindowUpdate(0, SimpleServlet.CONTENT_LENGTH);

        if (connectionUnlimited) {
            // Effectively unlimited for this test
            sendWindowUpdate(0, blockCount * BLOCK_SIZE * 2);
        }
        if (streamUnlimited) {
            // Effectively unlimited for this test
            sendWindowUpdate(3, blockCount * BLOCK_SIZE * 2);
        }

        // Headers
        parser.readFrame(true);
        // Body
        int startingWindowSize = ConnectionSettingsBase.DEFAULT_INITIAL_WINDOW_SIZE;

        while (output.getBytesRead() < startingWindowSize) {
            parser.readFrame(true);
        }

        // Check that the right number of bytes were received
        Assert.assertEquals(startingWindowSize, output.getBytesRead());

        // Increase the Window size (50% of total body)
        int windowSizeIncrease = blockCount * BLOCK_SIZE / 2;
        if (expandConnectionFirst) {
            sendWindowUpdate(0, windowSizeIncrease);
            sendWindowUpdate(3, windowSizeIncrease);
        } else {
            sendWindowUpdate(3, windowSizeIncrease);
            sendWindowUpdate(0, windowSizeIncrease);
        }

        while (output.getBytesRead() < startingWindowSize + windowSizeIncrease) {
            parser.readFrame(true);
        }

        // Check that the right number of bytes were received
        Assert.assertEquals(startingWindowSize + windowSizeIncrease, output.getBytesRead());

        // Increase the Window size
        if (expandConnectionFirst) {
            sendWindowUpdate(0, windowSizeIncrease);
            sendWindowUpdate(3, windowSizeIncrease);
        } else {
            sendWindowUpdate(3, windowSizeIncrease);
            sendWindowUpdate(0, windowSizeIncrease);
        }

        while (!output.getTrace().endsWith("3-EndOfStream\n")) {
            parser.readFrame(true);
        }

        // Check that the right number of bytes were received
        Assert.assertEquals(blockCount * BLOCK_SIZE, output.getBytesRead());
    }


    public static class AsyncServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        private final int blockLimit;

        public AsyncServlet(int blockLimit) {
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
                        if (blockCount > blockLimit) {
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
