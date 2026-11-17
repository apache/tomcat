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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Random;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.servlet.AsyncContext;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
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
 * Stress test for BZ 70288. Sends many small POST requests over HTTP/2 to a servlet that reads the body with a
 * ReadListener. HEADERS and DATA(END_STREAM) are sent with a random gap so that the data arrives while the servlet is
 * calling setReadListener(). Every request must see exactly one body byte and complete. Without the fix some requests
 * get onAllDataRead() with no data or never receive a read event. Place the file in test/org/apache/coyote/http2 and
 * run: ant test -Dtest.name="**&#47;TestStreamReadCompletionRace.java"
 * <p>
 * This test is disabled for normal runs as it takes around 60s to complete.
 */
public class TesterStreamReadCompletionRace extends Http2TestBase {

    private final CountingServlet servlet = new CountingServlet();

    @Test
    public void testSmallBodyWithReadListener() throws Exception {
        http2Connect();

        // Drain the responses so that the server is never blocked writing to this client
        Thread drain = new Thread(() -> {
            byte[] sink = new byte[65536];
            try {
                while (s.getInputStream().read(sink) >= 0) {
                    // Discard
                }
            } catch (IOException e) {
                // Connection closed at the end of the test
            }
        });
        drain.setDaemon(true);
        drain.start();

        int batches = Integer.getInteger("race.batches", 2000).intValue();
        int batchSize = 50;
        Random random = new Random(42);
        int stalls = 0;
        int streamId = 3;
        for (int bt = 0; bt < batches && stalls < 5; bt++) {
            for (int j = 0; j < batchSize; j++, streamId += 2) {
                int i = streamId;
                byte[] headersFrameHeader = new byte[9];
                ByteBuffer headersPayload = ByteBuffer.allocate(128);
                byte[] dataFrameHeader = new byte[9];
                ByteBuffer dataPayload = ByteBuffer.allocate(1);
                buildPostRequest(headersFrameHeader, headersPayload, false, null, 1, "/async?i=" + i, dataFrameHeader,
                        dataPayload, null, false, streamId);
                dataFrameHeader[0] = 0;
                dataFrameHeader[1] = 0;
                dataFrameHeader[2] = 1;
                dataFrameHeader[3] = 0;
                dataFrameHeader[4] = 0x01;
                dataFrameHeader[5] = (byte) (streamId >>> 24);
                dataFrameHeader[6] = (byte) (streamId >>> 16);
                dataFrameHeader[7] = (byte) (streamId >>> 8);
                dataFrameHeader[8] = (byte) streamId;

                os.write(headersFrameHeader);
                os.write(headersPayload.array(), 0, headersPayload.limit());
                os.flush();
                long end = System.nanoTime() + random.nextInt(300_000);
                while (System.nanoTime() < end) {
                    Thread.onSpinWait();
                }
                os.write(dataFrameHeader);
                os.write('x');
                os.flush();
            }
            if (!servlet.done.tryAcquire(batchSize, 5, TimeUnit.SECONDS)) {
                stalls++;
                System.out.println("STALL batch=" + bt + " completedSoFar=" + servlet.completed.get() + " expected=" +
                        (bt + 1) * batchSize);
                for (java.util.Map.Entry<Integer,String> e : servlet.events.entrySet()) {
                    if (!e.getValue().contains("onAllDataRead")) {
                        System.out.println("  EVENTS stream=" + e.getKey() + " " + e.getValue());
                    }
                }
                break;
            }
        }
        System.out.println("RESULT completed=" + servlet.completed.get() + " wrongCount=" + servlet.wrong.get() +
                " stalls=" + stalls + " onErrors=" + servlet.errors.get());
        for (java.util.Map.Entry<Integer,String> e : servlet.events.entrySet()) {
            if (e.getValue().contains("onAllDataRead(") && !e.getValue().contains("onAllDataRead(1)")) {
                System.out.println("WRONG stream=" + e.getKey() + " " + e.getValue());
            }
        }
        Assert.assertEquals(0, stalls);
        Assert.assertEquals(0, servlet.wrong.get());
    }

    @Override
    protected void configureAndStartWebApplication() throws LifecycleException {
        Tomcat tomcat = getTomcatInstance();
        Context ctxt = getProgrammaticRootContext();
        Tomcat.addServlet(ctxt, "simple", new SimpleServlet());
        ctxt.addServletMapping("/simple", "simple");
        Wrapper w = Tomcat.addServlet(ctxt, "async", servlet);
        w.setAsyncSupported(true);
        ctxt.addServletMapping("/async", "async");
        tomcat.start();
    }

    public static class CountingServlet extends HttpServlet {
        private static final long serialVersionUID = 1L;
        final Semaphore done = new Semaphore(0);
        final AtomicInteger completed = new AtomicInteger();
        final AtomicInteger wrong = new AtomicInteger();
        final AtomicInteger errors = new AtomicInteger();
        final java.util.concurrent.ConcurrentHashMap<Integer,String> events =
                new java.util.concurrent.ConcurrentHashMap<>();

        void ev(HttpServletRequest req, String e) {
            Integer k = Integer.valueOf(req.getParameter("i"));
            events.merge(k, e, (a, b) -> a + "," + b);
        }

        @Override
        protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            ev(req, "doPost");
            final AsyncContext ac = req.startAsync();
            final ServletInputStream in = req.getInputStream();
            final CountingServlet self = this;
            final HttpServletRequest rq = req;
            in.setReadListener(new ReadListener() {
                private int count;

                @Override
                public void onDataAvailable() throws IOException {
                    self.ev(rq, "onDataAvailable");
                    byte[] buf = new byte[64];
                    while (in.isReady() && !in.isFinished()) {
                        int n = in.read(buf);
                        if (n < 0) {
                            break;
                        }
                        count += n;
                    }
                }

                @Override
                public void onAllDataRead() throws IOException {
                    self.ev(rq, "onAllDataRead(" + count + ")");
                    if (count != 1) {
                        wrong.incrementAndGet();
                    }
                    completed.incrementAndGet();
                    ac.complete();
                    done.release();
                }

                @Override
                public void onError(Throwable t) {
                    self.ev(rq, "onError(" + t + ")");
                    errors.incrementAndGet();
                    ac.complete();
                    done.release();
                }
            });
            ev(req, "setReadListenerReturned");
        }
    }
}