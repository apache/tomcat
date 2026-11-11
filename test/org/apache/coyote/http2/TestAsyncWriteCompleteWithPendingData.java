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

package org.apache.coyote.http2;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.SocketTimeoutException;
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
import org.apache.catalina.LifecycleException;
import org.apache.catalina.Wrapper;
import org.apache.catalina.startup.Tomcat;

/**
 * An asynchronous servlet that writes its response using a WriteListener calls complete() as soon as isReady() is
 * true after the last write. If the connection flow control window has been used by then, the response is still
 * waiting to be written when asynchronous processing completes. It must be written, and the response completed, once
 * the client increases the window.
 */
public class TestAsyncWriteCompleteWithPendingData extends Http2TestBase {

    /*
     * A multiple of the 8k output buffer size so that, once the window has been used, no write is in progress and the
     * application is told it is OK to write.
     */
    private static final int WINDOW = 8 * 8192;


    @Test
    public void testOneBufferPending() throws Exception {
        doTestCompleteWithWindowExhausted(8193);
    }


    @Test
    public void testOneAndAHalfBuffersPending() throws Exception {
        doTestCompleteWithWindowExhausted(12288);
    }


    @Test
    public void testTwoBuffersPending() throws Exception {
        doTestCompleteWithWindowExhausted(16384);
    }


    private void doTestCompleteWithWindowExhausted(final int pending) throws Exception {
        http2Connect();
        s.setSoTimeout(500);
        s.setTcpNoDelay(true);

        // Make the connection flow control window, which is shared by all the streams, the limit. Stream windows are
        // large enough not to be a factor.
        sendSettings(0, false, new SettingValue(Setting.INITIAL_WINDOW_SIZE.getId(), 1 << 20));
        sendWindowUpdate(0, SimpleServlet.CONTENT_LENGTH + WINDOW - ConnectionSettingsBase.DEFAULT_INITIAL_WINDOW_SIZE);

        final int streamId = 3;
        final int responseSize = WINDOW + pending;
        byte[] frameHeader = new byte[9];
        ByteBuffer headersPayload = ByteBuffer.allocate(128);
        buildGetRequest(frameHeader, headersPayload, null, streamId, "/write?size=" + responseSize);
        writeFrame(frameHeader, headersPayload);

        DataInputStream in = new DataInputStream(s.getInputStream());
        int received = 0;
        boolean windowIncreased = false;
        boolean endOfStream = false;
        int idleReads = 0;
        while (!endOfStream) {
            byte[] header = new byte[9];
            try {
                in.readFully(header);
            } catch (SocketTimeoutException e) {
                // Nothing more to read so the server is waiting for the client to increase the connection window
                if (!windowIncreased) {
                    windowIncreased = true;
                    sendWindowUpdate(0, WINDOW);
                } else if (++idleReads > 10) {
                    Assert.fail("Response not completed. Received [" + received + "] of [" + responseSize + "] bytes");
                }
                continue;
            }
            idleReads = 0;
            int length = ((header[0] & 0xFF) << 16) | ((header[1] & 0xFF) << 8) | (header[2] & 0xFF);
            int type = header[3] & 0xFF;
            int flags = header[4] & 0xFF;
            in.readFully(new byte[length]);
            if (type == FrameType.SETTINGS.getId() && (flags & 0x01) == 0) {
                // Acknowledge the server's settings
                sendSettings(0, true);
            } else if (type == FrameType.DATA.getId()) {
                received += length;
                endOfStream = (flags & 0x01) != 0;
            } else if (type == FrameType.HEADERS.getId()) {
                endOfStream = (flags & 0x01) != 0;
            } else if (type == FrameType.RST.getId() || type == FrameType.GOAWAY.getId()) {
                Assert.fail("Unexpected frame of type [" + type + "]");
            }
        }

        Assert.assertEquals(responseSize, received);
    }


    @Override
    protected void configureAndStartWebApplication() throws LifecycleException {
        Tomcat tomcat = getTomcatInstance();

        Context ctxt = getProgrammaticRootContext();
        Tomcat.addServlet(ctxt, "simple", new SimpleServlet());
        ctxt.addServletMapping("/simple", "simple");
        Wrapper w = Tomcat.addServlet(ctxt, "write", new WriteServlet());
        w.setAsyncSupported(true);
        ctxt.addServletMapping("/write", "write");
        tomcat.start();
    }


    public static class WriteServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            final int size = Integer.parseInt(req.getParameter("size"));
            final AsyncContext asyncContext = req.startAsync();
            final ServletOutputStream out = resp.getOutputStream();
            out.setWriteListener(new WriteListener() {

                private int written;

                @Override
                public void onWritePossible() throws IOException {
                    while (out.isReady()) {
                        if (written >= size) {
                            asyncContext.complete();
                            return;
                        }
                        int length = Math.min(1024, size - written);
                        out.write(new byte[length]);
                        written += length;
                    }
                }

                @Override
                public void onError(Throwable t) {
                    asyncContext.complete();
                }
            });
        }
    }
}
