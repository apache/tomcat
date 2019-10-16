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
package javax.servlet.http;

import org.apache.catalina.Context;
import org.apache.catalina.Wrapper;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.buf.ByteChunk;
import org.junit.Assert;
import org.junit.Test;

import javax.servlet.AsyncContext;
import javax.servlet.ServletException;
import java.io.IOException;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class TestHttpServletAsync extends TomcatBaseTest {

    @Test
    public void testSendError() throws Exception {
        final Tomcat tomcat = getTomcatInstance();

        final Context ctx = tomcat.addContext("", null);
        final Wrapper w = Tomcat.addServlet(ctx, "target", new TestAsyncServlet());
        w.setAsyncSupported(true);
        ctx.addServletMappingDecoded("/target", "target");

        tomcat.start();

        final AtomicInteger errorCount = new AtomicInteger(0);
        final Random random = new Random();

        final Runnable clientRequest = new Runnable() {
            @Override
            public void run() {
                try {
                    // pick 2 random ints
                    final int x = random.nextInt(100);
                    final int y = random.nextInt(100);


                    // pick whether the operation should be synchronous or async
                    final boolean async = random.nextBoolean();

                    // pick whether the operation should timeout (if async)
                    final boolean timeout = random.nextBoolean();

                    final int expectedValue = x + y;

                    final ByteChunk bc = new ByteChunk();
                    int rc;

                    final String url;

                    if (async && timeout) {
                        url = "http://localhost:" + getPort() + "/target?x=" + x + "&y=" + y + "&async=true&timeout=500&delay=2000";
                    } else if (async && !timeout) {
                        url = "http://localhost:" + getPort() + "/target?x=" + x + "&y=" + y + "&async=true&timeout=5000&delay=500";
                    } else {
                        url = "http://localhost:" + getPort() + "/target?x=" + x + "&y=" + y + "&async=false";
                    }


                    rc = getUrl(url, bc, null, null);

                    final String body = bc.toString();

                    if (async && timeout) {
                        Assert.assertEquals(500, rc);
                    } else {
                        Assert.assertEquals(200, rc);
                        Assert.assertEquals(Integer.toString(expectedValue), body);
                    }

                } catch (Throwable t) {
                    System.out.println(t.getMessage());
                    errorCount.incrementAndGet();
                }
            }
        };

        final CountDownLatch start = new CountDownLatch(1);
        final CountDownLatch ready = new CountDownLatch(10);
        final CountDownLatch complete = new CountDownLatch(10);

        for (int i = 0; i < 10; i++) {
            new Thread() {
                @Override
                public void run() {
                    try {
                        ready.countDown();
                        start.await();

                        for (int i = 0; i < 100; i++) {
                            clientRequest.run();
                        }

                        complete.countDown();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }.start();
        }

        ready.await(1, TimeUnit.MINUTES);
        start.countDown();
        complete.await(2, TimeUnit.MINUTES);

        Assert.assertEquals(0, errorCount.get());
    }


    public static class TestAsyncServlet extends HttpServlet {
        public static final String RESULT_ATTRIBUTE = "RESULT";

        @Override
        protected void doGet(final HttpServletRequest req, final HttpServletResponse resp) throws IOException, ServletException {
            process(req, resp);
        }

        @Override
        protected void doPost(final HttpServletRequest req, final HttpServletResponse resp) throws IOException, ServletException {
            process(req, resp);
        }

        private void process(final HttpServletRequest req, final HttpServletResponse resp) throws IOException, ServletException {

            if (req.getAttribute(RESULT_ATTRIBUTE) == null) {

                final ResultHolder result = new ResultHolder();
                req.setAttribute("RESULT", result);

                final String asyncParam = req.getParameter("async");
                final String delayParam = req.getParameter("delay");
                final String timeoutParam = req.getParameter("timeout");
                final String xParam = req.getParameter("x");
                final String yParam = req.getParameter("y");

                final int x;
                try {
                    x = Integer.parseInt(xParam);
                } catch (final Exception e) {
                    throw new ServletException(e);
                }

                final int y;
                try {
                    y = Integer.parseInt(yParam);
                } catch (final Exception e) {
                    throw new ServletException(e);
                }

                int delay = 0;
                try {
                    delay = Integer.parseInt(delayParam);
                } catch (final Exception e) {
                    // ignore
                }

                int timeout = -1;
                try {
                    timeout = Integer.parseInt(timeoutParam);
                } catch (final Exception e) {
                    // ignore
                }

                boolean async = false;
                try {
                    async = Boolean.parseBoolean(asyncParam);
                } catch (final Exception e) {
                    // ignore
                }

                if (!async) {
                    process(x, y, result);
                    resp.getWriter().print(result.getResult());
                    return;
                }

                final int threadDelay = delay;
                final AsyncContext asyncContext = req.startAsync();
                asyncContext.setTimeout(timeout);
                asyncContext.start(() -> {

                    try {
                        Thread.sleep(threadDelay);
                    } catch (final InterruptedException e) {
                        // ignore
                    }

                    try {
                        process(x, y, result);
                    } catch (final Exception e) {

                    } finally {
                        asyncContext.dispatch();
                    }

                });
            } else {
                final ResultHolder result = (ResultHolder) req.getAttribute("RESULT");
                resp.getWriter().print(result.getResult());
            }
        }

        private void process(final int x, final int y, final ResultHolder result) {
            result.setResult(x + y);
        }

        public static class ResultHolder {
            private int result = 0;

            public int getResult() {
                return result;
            }

            public void setResult(final int result) {
                this.result = result;
            }
        }
    }






}
