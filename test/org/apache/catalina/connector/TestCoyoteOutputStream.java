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
package org.apache.catalina.connector;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import javax.servlet.AsyncContext;
import javax.servlet.ServletException;
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
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.buf.ByteChunk;

public class TestCoyoteOutputStream extends TomcatBaseTest {

    @Test
    public void testNonBlockingWriteNoneBlockingWriteNoneContainerThread() throws Exception {
        doNonBlockingTest(0, 0, true);
    }

    @Test
    public void testNonBlockingWriteOnceBlockingWriteNoneContainerThread() throws Exception {
        doNonBlockingTest(1, 0, true);
    }

    @Test
    public void testNonBlockingWriteTwiceBlockingWriteNoneContainerThread() throws Exception {
        doNonBlockingTest(2, 0, true);
    }

    @Test
    public void testNonBlockingWriteNoneBlockingWriteOnceContainerThread() throws Exception {
        doNonBlockingTest(0, 1, true);
    }

    @Test
    public void testNonBlockingWriteOnceBlockingWriteOnceContainerThread() throws Exception {
        doNonBlockingTest(1, 1, true);
    }

    @Test
    public void testNonBlockingWriteTwiceBlockingWriteOnceContainerThread() throws Exception {
        doNonBlockingTest(2, 1, true);
    }

    @Test
    public void testNonBlockingWriteNoneBlockingWriteNoneNonContainerThread() throws Exception {
        doNonBlockingTest(0, 0, false);
    }

    @Test
    public void testNonBlockingWriteOnceBlockingWriteNoneNonContainerThread() throws Exception {
        doNonBlockingTest(1, 0, false);
    }

    @Test
    public void testNonBlockingWriteTwiceBlockingWriteNoneNonContainerThread() throws Exception {
        doNonBlockingTest(2, 0, false);
    }

    @Test
    public void testNonBlockingWriteNoneBlockingWriteOnceNonContainerThread() throws Exception {
        doNonBlockingTest(0, 1, false);
    }

    @Test
    public void testNonBlockingWriteOnceBlockingWriteOnceNonContainerThread() throws Exception {
        doNonBlockingTest(1, 1, false);
    }

    @Test
    public void testNonBlockingWriteTwiceBlockingWriteOnceNonContainerThread() throws Exception {
        doNonBlockingTest(2, 1, false);
    }

    private void doNonBlockingTest(int asyncWriteTarget, int syncWriteTarget,
            boolean useContainerThreadToSetListener) throws Exception {

        Tomcat tomcat = getTomcatInstance();

        Context root = tomcat.addContext("", TEMP_DIR);
        Wrapper w = Tomcat.addServlet(root, "nbWrite",
                new NonBlockingWriteServlet(asyncWriteTarget, useContainerThreadToSetListener));
        w.setAsyncSupported(true);
        root.addServletMapping("/nbWrite", "nbWrite");
        Tomcat.addServlet(root, "write",
                new BlockingWriteServlet(asyncWriteTarget, syncWriteTarget));
        w.setAsyncSupported(true);
        root.addServletMapping("/write", "write");

        tomcat.start();

        ByteChunk bc = new ByteChunk();
        // Extend timeout to 5 mins for debugging
        int rc = getUrl("http://localhost:" + getPort() + "/nbWrite", bc,
                300000, null, null);

        int totalCount = asyncWriteTarget + syncWriteTarget;
        StringBuilder sb = new StringBuilder(totalCount * 16);
        for (int i = 0; i < totalCount; i++) {
            sb.append("OK - " + i + System.lineSeparator());
        }
        String expected = null;
        if (sb.length() > 0) {
            expected = sb.toString();
        }
        Assert.assertEquals(200, rc);
        Assert.assertEquals(expected, bc.toString());
    }

    private static final class NonBlockingWriteServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        private final int asyncWriteTarget;
        private final AtomicInteger asyncWriteCount = new AtomicInteger(0);
        private final boolean useContainerThreadToSetListener;

        public NonBlockingWriteServlet(int asyncWriteTarget,
                boolean useContainerThreadToSetListener) {
            this.asyncWriteTarget = asyncWriteTarget;
            this.useContainerThreadToSetListener = useContainerThreadToSetListener;
        }

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {

            resp.setContentType("text/plain");
            resp.setCharacterEncoding("UTF-8");
            ServletOutputStream sos = resp.getOutputStream();


            AsyncContext asyncCtxt = req.startAsync();
            asyncCtxt.setTimeout(5);
            Runnable task = new AsyncTask(asyncCtxt, sos);
            if (useContainerThreadToSetListener) {
                asyncCtxt.start(task);
            } else {
                Thread t = new Thread(task);
                t.start();
            }
        }

        private void doAsyncWrite(AsyncContext asyncCtxt,
                ServletOutputStream sos) throws IOException {
            while (sos.isReady()) {
                int next = asyncWriteCount.getAndIncrement();
                if (next < asyncWriteTarget) {
                    sos.write(
                            ("OK - " + next + System.lineSeparator()).getBytes(
                                    StandardCharsets.UTF_8));
                    sos.flush();
                } else {
                    asyncCtxt.dispatch("/write");
                    break;
                }
            }
        }

        private class AsyncTask implements Runnable {

            private final AsyncContext asyncCtxt;
            private final ServletOutputStream sos;

            public AsyncTask(AsyncContext asyncCtxt, ServletOutputStream sos) {
                this.asyncCtxt = asyncCtxt;
                this.sos = sos;
            }

            @Override
            public void run() {
                sos.setWriteListener(new MyWriteListener(asyncCtxt, sos));
            }
        }

        private final class MyWriteListener implements WriteListener {

            private final AsyncContext asyncCtxt;
            private final ServletOutputStream sos;

            public MyWriteListener(AsyncContext asyncCtxt,
                    ServletOutputStream sos) {
                this.asyncCtxt = asyncCtxt;
                this.sos = sos;
            }

            @Override
            public void onWritePossible() throws IOException {
                doAsyncWrite(asyncCtxt, sos);
            }

            @Override
            public void onError(Throwable throwable) {
                // Not expected.
                throwable.printStackTrace();
            }
        }
    }

    private static final class BlockingWriteServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        private final int start;
        private final int len;

        public BlockingWriteServlet(int start, int len) {
            this.start = start;
            this.len = len;
        }

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {

            resp.setContentType("text/plain");
            resp.setCharacterEncoding("UTF-8");
            ServletOutputStream sos = resp.getOutputStream();

            for (int i = start; i < start + len; i++) {
                sos.write(("OK - " + i + System.lineSeparator()).getBytes(
                        StandardCharsets.UTF_8));
            }
        }
    }
}
