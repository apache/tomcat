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
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.servlet.AsyncContext;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import org.apache.catalina.Context;
import org.apache.catalina.Wrapper;
import org.apache.catalina.startup.Tomcat;

/*
 * Based on
 * https://bz.apache.org/bugzilla/show_bug.cgi?id=62614
 * https://bz.apache.org/bugzilla/show_bug.cgi?id=62620
 * https://bz.apache.org/bugzilla/show_bug.cgi?id=62628
 */
@RunWith(Parameterized.class)
public class TestAsync extends Http2TestBase {

    private static final int BLOCK_SIZE = 0x8000;

    @Parameterized.Parameters(name = "{index}: expandConnectionFirst[{0}], " +
            "connectionUnlimited[{1}], streamUnlimited[{2}], useNonContainerThreadForWrite[{3}]," +
            "largeInitialWindow[{4}]")
    public static Collection<Object[]> parameters() {
        List<Object[]> parameterSets = new ArrayList<>();

        for (Boolean expandConnectionFirst : booleans) {
            for (Boolean connectionUnlimited : booleans) {
                for (Boolean streamUnlimited : booleans) {
                    for (Boolean useNonContainerThreadForWrite : booleans) {
                        for (Boolean largeInitialWindow : booleans) {
                            parameterSets.add(new Object[] {
                                    expandConnectionFirst, connectionUnlimited, streamUnlimited,
                                    useNonContainerThreadForWrite, largeInitialWindow
                            });
                        }
                    }
                }
            }
        }
        return parameterSets;
    }


    private final boolean expandConnectionFirst;
    private final boolean connectionUnlimited;
    private final boolean streamUnlimited;
    private final boolean useNonContainerThreadForWrite;
    private final boolean largeInitialWindow;


    public TestAsync(boolean expandConnectionFirst, boolean connectionUnlimited,
            boolean streamUnlimited, boolean useNonContainerThreadForWrite,
            boolean largeInitialWindow) {
        this.expandConnectionFirst = expandConnectionFirst;
        this.connectionUnlimited = connectionUnlimited;
        this.streamUnlimited = streamUnlimited;
        this.useNonContainerThreadForWrite = useNonContainerThreadForWrite;
        this.largeInitialWindow = largeInitialWindow;
    }


    @Test
    public void testEmptyWindow() throws Exception {
        int blockCount = 8;

        enableHttp2();

        Tomcat tomcat = getTomcatInstance();

        Context ctxt = tomcat.addContext("", null);
        Tomcat.addServlet(ctxt, "simple", new SimpleServlet());
        ctxt.addServletMappingDecoded("/simple", "simple");
        Wrapper w = Tomcat.addServlet(ctxt, "async",
                new AsyncServlet(blockCount, useNonContainerThreadForWrite));
        w.setAsyncSupported(true);
        ctxt.addServletMappingDecoded("/async", "async");
        tomcat.start();

        int startingWindowSize;

        openClientConnection();
        doHttpUpgrade();
        sendClientPreface();
        validateHttp2InitialResponse();

        // Reset connection window size after initial response
        sendWindowUpdate(0, SimpleServlet.CONTENT_LENGTH);

        if (largeInitialWindow) {
            startingWindowSize = ((1 << 17) - 1);
            SettingValue sv =
                    new SettingValue(Setting.INITIAL_WINDOW_SIZE.getId(), startingWindowSize);
            sendSettings(0, false, sv);
            // Test code assumes connection window and stream window size are the same at the start
            sendWindowUpdate(0, startingWindowSize - ConnectionSettingsBase.DEFAULT_INITIAL_WINDOW_SIZE);
        } else {
            startingWindowSize = ConnectionSettingsBase.DEFAULT_INITIAL_WINDOW_SIZE;
        }

        byte[] frameHeader = new byte[9];
        ByteBuffer headersPayload = ByteBuffer.allocate(128);
        buildGetRequest(frameHeader, headersPayload, null, 3, "/async");
        writeFrame(frameHeader, headersPayload);

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

        if (!connectionUnlimited || !streamUnlimited) {
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
        }

        while (!output.getTrace().endsWith("3-EndOfStream\n")) {
            try {
                parser.readFrame(true);
            } catch (IOException ioe) {
                // Attempt to debug intermittent test failures
                System.out.println(output.getTrace());
                System.out.println(output.getBytesRead());
                throw ioe;
            }
        }

        // Check that the right number of bytes were received
        Assert.assertEquals((long) blockCount * BLOCK_SIZE, output.getBytesRead());
    }


    public static class AsyncServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        private final int blockLimit;
        private final boolean useNonContainerThreadForWrite;
        private final transient ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        private transient volatile Future<?> future;

        public AsyncServlet(int blockLimit, boolean useNonContainerThreadForWrite) {
            this.blockLimit = blockLimit;
            this.useNonContainerThreadForWrite = useNonContainerThreadForWrite;
        }

        /*
         * Not thread-safe. OK for this test. NOt OK for use in the real world.
         */
        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws IOException {

            final AsyncContext asyncContext = request.startAsync();

            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentType("application/binary");

            final ServletOutputStream output = response.getOutputStream();
            output.setWriteListener(new WriteListener() {

                // Intermittent CI errors were observed where the response body
                // was exactly one block too small. Use an AtomicInteger to be
                // sure blockCount is thread-safe.
                final AtomicInteger blockCount = new AtomicInteger(0);
                byte[] bytes = new byte[BLOCK_SIZE];


                @Override
                public void onWritePossible() throws IOException {
                    if (useNonContainerThreadForWrite) {
                        future = scheduler.schedule(new Runnable() {

                            @Override
                            public void run() {
                                try {
                                    write();
                                } catch (IOException e) {
                                    throw new IllegalStateException(e);
                                }
                            }
                        }, 200, TimeUnit.MILLISECONDS);
                    } else {
                        write();
                    }
                }


                private void write() throws IOException {
                    while (output.isReady()) {
                        blockCount.incrementAndGet();
                        output.write(bytes);
                        if (blockCount.get()  == blockLimit) {
                            asyncContext.complete();
                            scheduler.shutdown();
                            return;
                        }
                    }
                }

                @Override
                public void onError(Throwable t) {
                    if (future != null) {
                        future.cancel(false);
                    }
                    t.printStackTrace();
                }
            });
        }
    }
}
