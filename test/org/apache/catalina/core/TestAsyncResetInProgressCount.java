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
package org.apache.catalina.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.servlet.AsyncContext;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.tomcat.util.descriptor.web.FilterDef;
import org.apache.tomcat.util.descriptor.web.FilterMap;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.Wrapper;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;

/*
 * Reconstruction of the scenario in the (unconfirmed) 11.0.27 draft report: a client RST during the initial async
 * service call, with the application worker requesting a dispatch from inside onError(). The claim under test is that
 * the async request is recycled without onComplete and without balancing StandardContext.inProgressAsyncCount.
 *
 * This ports the latch-coordinated "plain" mode of the standalone reproducer into a Spring-free TomcatBaseTest so the
 * interleaving (dispatch requested while the container thread is inside onError) is forced rather than left to chance.
 *
 * Statics are used deliberately: TomcatBaseTest runs one test per JVM and never concurrently.
 */
public class TestAsyncResetInProgressCount extends TomcatBaseTest {

    private static final int BODY_SIZE = 16 * 1024 * 1024;
    private static final int ROUNDS = Integer.getInteger("reset.rounds", 20).intValue();
    // Widen the initial service() window so the client RST and the application dispatch land while the async state is
    // still STARTING (the specific "initial service" schedule in the report), not the already-robust STARTED path.
    // Set reset.holdMillis=0 to exercise the natural (uncoordinated) race instead.
    private static final long HOLD_MILLIS = Long.getLong("reset.holdMillis", 200).longValue();

    private static final ExecutorService WORKERS = Executors.newFixedThreadPool(8);

    private static volatile Cycle current;

    private static final class Cycle {
        final CountDownLatch completed = new CountDownLatch(1);
        final CountDownLatch writerDone = new CountDownLatch(1);
        final CountDownLatch errorEntered = new CountDownLatch(1);
        final CountDownLatch dispatchAttempted = new CountDownLatch(1);
        final AtomicInteger starts = new AtomicInteger();
        final AtomicInteger completions = new AtomicInteger();
        final AtomicInteger errors = new AtomicInteger();
        volatile String writeError = "none";
        volatile String dispatchError = "none";
        volatile long bytes;
    }

    @Test
    public void testResetDuringInitialServiceBalancesAsyncCount() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        Context ctx = getProgrammaticRootContext();
        StandardContext standardContext = (StandardContext) ctx;

        Wrapper w = Tomcat.addServlet(ctx, "stream", new StreamServlet());
        w.setAsyncSupported(true);
        ctx.addServletMapping("/stream", "stream");

        FilterDef def = new FilterDef();
        def.setFilterName("hold");
        def.setFilter(new HoldFilter());
        def.setAsyncSupported("true");
        ctx.addFilterDef(def);
        FilterMap map = new FilterMap();
        map.setFilterName("hold");
        map.addURLPattern("/*");
        map.setDispatcher(DispatcherType.REQUEST.name());
        ctx.addFilterMap(map);

        tomcat.start();

        int port = getPort();
        InetAddress loopback = InetAddress.getLoopbackAddress();

        int failures = 0;
        for (int i = 1; i <= ROUNDS; i++) {
            Cycle cycle = new Cycle();
            current = cycle;
            long before = standardContext.getInProgressAsyncCount();

            try (Socket socket = new Socket(loopback, port)) {
                socket.setSoTimeout(5000);
                socket.getOutputStream().write(
                        ("GET /stream HTTP/1.1\r\nHost: localhost\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                InputStream in = socket.getInputStream();
                byte[] buf = new byte[64 * 1024];
                int read = 0;
                while (read < buf.length) {
                    int n = in.read(buf, read, buf.length - read);
                    if (n < 0) {
                        break;
                    }
                    read += n;
                }
                // Send a TCP RST on close.
                socket.setSoLinger(true, 0);
            }

            boolean completed = cycle.completed.await(2, TimeUnit.SECONDS);
            cycle.writerDone.await(2, TimeUnit.SECONDS);
            cycle.dispatchAttempted.await(2, TimeUnit.SECONDS);

            // The count is decremented just after onComplete; allow that callback to return.
            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(500);
            while (standardContext.getInProgressAsyncCount() > before && System.nanoTime() < deadline) {
                Thread.sleep(1);
            }
            long after = standardContext.getInProgressAsyncCount();

            boolean pass = completed && cycle.completions.get() == 1 && after == before;
            if (!pass) {
                failures++;
            }
            System.out.printf(
                    "RESET round=%d result=%s onComplete=%d onError=%d activeBefore=%d activeAfter=%d bytes=%d writeError=%s dispatchError=%s%n",
                    i, pass ? "PASS" : "FAIL", cycle.completions.get(), cycle.errors.get(), before, after,
                    cycle.bytes, cycle.writeError, cycle.dispatchError);
        }

        long leaked = standardContext.getInProgressAsyncCount();
        System.out.printf("SUMMARY rounds=%d failures=%d residualInProgressAsyncCount=%d%n", ROUNDS, failures, leaked);

        Assert.assertEquals("inProgressAsyncCount must return to zero after all rounds", 0, leaked);
        Assert.assertEquals("every round must notify onComplete and balance the async count", 0, failures);
    }

    private static final class StreamServlet extends HttpServlet {
        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) {
            if (request.getDispatcherType() == DispatcherType.ASYNC) {
                return;
            }
            final Cycle cycle = current;
            response.setContentLengthLong(BODY_SIZE);

            final AsyncContext async = request.startAsync();
            async.setTimeout(5000);
            cycle.starts.incrementAndGet();
            async.addListener(new AsyncListener() {
                @Override
                public void onComplete(AsyncEvent event) {
                    cycle.completions.incrementAndGet();
                    cycle.completed.countDown();
                }

                @Override
                public void onError(AsyncEvent event) {
                    cycle.errors.incrementAndGet();
                    cycle.errorEntered.countDown();
                    try {
                        cycle.dispatchAttempted.await(2, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }

                @Override
                public void onTimeout(AsyncEvent event) {
                    System.err.println("ASYNC TIMEOUT");
                }

                @Override
                public void onStartAsync(AsyncEvent event) {
                    event.getAsyncContext().addListener(this);
                }
            });

            WORKERS.execute(() -> {
                try {
                    writeBody(response.getOutputStream(), cycle);
                } catch (IOException expectedOnReset) {
                    // Like an application callable returning to the container after a client disconnect.
                } finally {
                    if (!cycle.writeError.equals("none")) {
                        try {
                            cycle.errorEntered.await(2, TimeUnit.SECONDS);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                    try {
                        async.dispatch();
                    } catch (RuntimeException e) {
                        cycle.dispatchError = e.toString();
                    } finally {
                        cycle.dispatchAttempted.countDown();
                    }
                }
            });
        }
    }

    /*
     * Keeps the container thread inside the initial service() call for a short, bounded window after the servlet
     * returns, so the client RST and the application dispatch can land while the async state is still STARTING.
     */
    private static final class HoldFilter implements Filter {
        @Override
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                throws IOException, ServletException {
            chain.doFilter(request, response);
            if (HOLD_MILLIS > 0) {
                try {
                    Thread.sleep(HOLD_MILLIS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    private static void writeBody(OutputStream output, Cycle cycle) throws IOException {
        byte[] block = new byte[8192];
        try {
            while (cycle.bytes < BODY_SIZE) {
                output.write(block);
                cycle.bytes += block.length;
            }
            output.flush();
        } catch (IOException e) {
            cycle.writeError = e.getClass().getSimpleName();
            throw e;
        } finally {
            cycle.writerDone.countDown();
        }
    }
}
