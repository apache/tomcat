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

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.servlet.AsyncContext;
import javax.servlet.AsyncEvent;
import javax.servlet.AsyncListener;
import javax.servlet.DispatcherType;
import javax.servlet.GenericServlet;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletRequestEvent;
import javax.servlet.ServletRequestListener;
import javax.servlet.ServletRequestWrapper;
import javax.servlet.ServletResponse;
import javax.servlet.ServletResponseWrapper;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.Wrapper;
import org.apache.catalina.connector.Request;
import org.apache.catalina.deploy.ApplicationListener;
import org.apache.catalina.deploy.ErrorPage;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.catalina.valves.TesterAccessLogValve;
import org.apache.tomcat.util.buf.ByteChunk;

public class TestAsyncContextImpl extends TomcatBaseTest {

    // Time for a request to process (need to allow for threads to start etc.)
    private static final long REQUEST_TIME = 1500;
    // Timeout thread (where used) checks for timeout every second
    private static final long TIMEOUT_MARGIN = 1000;
    // Default timeout for these tests
    private static final long TIMEOUT = 3000;

    @Test
    public void testBug49528() throws Exception {
        // Setup Tomcat instance
        Tomcat tomcat = getTomcatInstance();

        // Must have a real docBase - just use temp
        Context ctx =
            tomcat.addContext("", System.getProperty("java.io.tmpdir"));

        Bug49528Servlet servlet = new Bug49528Servlet();

        Wrapper wrapper = Tomcat.addServlet(ctx, "servlet", servlet);
        wrapper.setAsyncSupported(true);
        ctx.addServletMapping("/", "servlet");

        TesterAccessLogValve alv = new TesterAccessLogValve();
        ctx.getPipeline().addValve(alv);

        tomcat.start();

        // Call the servlet once
        ByteChunk bc = getUrl("http://localhost:" + getPort() + "/");
        assertEquals("OK", bc.toString());

        // Give the async thread a chance to finish (but not too long)
        int counter = 0;
        while (!servlet.isDone() && counter < 10) {
            Thread.sleep(1000);
            counter++;
        }

        assertEquals("1false2true3true4true5false", servlet.getResult());

        // Check the access log
        alv.validateAccessLog(1, 200, Bug49528Servlet.THREAD_SLEEP_TIME,
                Bug49528Servlet.THREAD_SLEEP_TIME + REQUEST_TIME);
    }

    @Test
    public void testBug49567() throws Exception {
        // Setup Tomcat instance
        Tomcat tomcat = getTomcatInstance();

        // Must have a real docBase - just use temp
        Context ctx =
            tomcat.addContext("", System.getProperty("java.io.tmpdir"));

        Bug49567Servlet servlet = new Bug49567Servlet();

        Wrapper wrapper = Tomcat.addServlet(ctx, "servlet", servlet);
        wrapper.setAsyncSupported(true);
        ctx.addServletMapping("/", "servlet");

        TesterAccessLogValve alv = new TesterAccessLogValve();
        ctx.getPipeline().addValve(alv);

        tomcat.start();

        // Call the servlet once
        ByteChunk bc = getUrl("http://localhost:" + getPort() + "/");
        assertEquals("OK", bc.toString());

        // Give the async thread a chance to finish (but not too long)
        int counter = 0;
        while (!servlet.isDone() && counter < 10) {
            Thread.sleep(1000);
            counter++;
        }

        assertEquals("1false2true3true4true5false", servlet.getResult());

        // Check the access log
        alv.validateAccessLog(1, 200, Bug49567Servlet.THREAD_SLEEP_TIME,
                Bug49567Servlet.THREAD_SLEEP_TIME + REQUEST_TIME);
    }

    @Test
    public void testAsyncStartNoComplete() throws Exception {
        // Setup Tomcat instance
        Tomcat tomcat = getTomcatInstance();

        // Minimise pauses during test
        tomcat.getConnector().setAttribute(
                "connectionTimeout", Integer.valueOf(3000));

        // Must have a real docBase - just use temp
        Context ctx =
            tomcat.addContext("", System.getProperty("java.io.tmpdir"));

        AsyncStartNoCompleteServlet servlet =
            new AsyncStartNoCompleteServlet();

        Wrapper wrapper = Tomcat.addServlet(ctx, "servlet", servlet);
        wrapper.setAsyncSupported(true);
        ctx.addServletMapping("/", "servlet");

        TesterAccessLogValve alv = new TesterAccessLogValve();
        ctx.getPipeline().addValve(alv);

        tomcat.start();

        // Call the servlet the first time
        ByteChunk bc1 = getUrl("http://localhost:" + getPort() +
                "/?echo=run1");
        assertEquals("OK-run1", bc1.toString());

        // Call the servlet the second time with a request parameter
        ByteChunk bc2 = getUrl("http://localhost:" + getPort() +
                "/?echo=run2");
        assertEquals("OK-run2", bc2.toString());

        // Check the access log
        alv.validateAccessLog(2, 500,
                AsyncStartNoCompleteServlet.ASYNC_TIMEOUT,
                AsyncStartNoCompleteServlet.ASYNC_TIMEOUT + TIMEOUT_MARGIN +
                        REQUEST_TIME);
    }

    @Test
    public void testAsyncStartWithComplete() throws Exception {
        // Setup Tomcat instance
        Tomcat tomcat = getTomcatInstance();

        // Must have a real docBase - just use temp
        Context ctx =
            tomcat.addContext("", System.getProperty("java.io.tmpdir"));

        AsyncStartWithCompleteServlet servlet =
            new AsyncStartWithCompleteServlet();

        Wrapper wrapper = Tomcat.addServlet(ctx, "servlet", servlet);
        wrapper.setAsyncSupported(true);
        ctx.addServletMapping("/", "servlet");

        TesterAccessLogValve alv = new TesterAccessLogValve();
        ctx.getPipeline().addValve(alv);

        tomcat.start();

        // Call the servlet once
        ByteChunk bc = getUrl("http://localhost:" + getPort() + "/");
        assertEquals("OK", bc.toString());

        // Check the access log
        alv.validateAccessLog(1, 200, 0, REQUEST_TIME);
    }

    /*
     * NOTE: This servlet is only intended to be used in single-threaded tests.
     */
    private static class Bug49528Servlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        private volatile boolean done = false;

        private StringBuilder result;

        public static final long THREAD_SLEEP_TIME = 1000;

        public String getResult() {
            return result.toString();
        }

        public boolean isDone() {
            return done;
        }

        @Override
        protected void doGet(final HttpServletRequest req,
                final HttpServletResponse resp)
                throws ServletException, IOException {

            result  = new StringBuilder();
            result.append('1');
            result.append(req.isAsyncStarted());
            req.startAsync().setTimeout(10000);
            result.append('2');
            result.append(req.isAsyncStarted());

            req.getAsyncContext().start(new Runnable() {
                @Override
                public void run() {
                    try {
                        result.append('3');
                        result.append(req.isAsyncStarted());
                        Thread.sleep(THREAD_SLEEP_TIME);
                        result.append('4');
                        result.append(req.isAsyncStarted());
                        resp.setContentType("text/plain");
                        resp.getWriter().print("OK");
                        req.getAsyncContext().complete();
                        result.append('5');
                        result.append(req.isAsyncStarted());
                        done = true;
                    } catch (InterruptedException e) {
                        result.append(e);
                    } catch (IOException e) {
                        result.append(e);
                    }
                }
            });
            // Pointless method call so there is somewhere to put a break point
            // when debugging
            req.getMethod();
        }
    }

    /*
     * NOTE: This servlet is only intended to be used in single-threaded tests.
     */
    private static class Bug49567Servlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        private volatile boolean done = false;

        private StringBuilder result;

        public static final long THREAD_SLEEP_TIME = 1000;

        public String getResult() {
            return result.toString();
        }

        public boolean isDone() {
            return done;
        }

        @Override
        protected void doGet(final HttpServletRequest req,
                final HttpServletResponse resp)
                throws ServletException, IOException {

            result = new StringBuilder();
            result.append('1');
            result.append(req.isAsyncStarted());
            req.startAsync();
            result.append('2');
            result.append(req.isAsyncStarted());

            req.getAsyncContext().start(new Runnable() {
                @Override
                public void run() {
                    Thread t = new Thread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                result.append('3');
                                result.append(req.isAsyncStarted());
                                Thread.sleep(THREAD_SLEEP_TIME);
                                result.append('4');
                                result.append(req.isAsyncStarted());
                                resp.setContentType("text/plain");
                                resp.getWriter().print("OK");
                                req.getAsyncContext().complete();
                                result.append('5');
                                result.append(req.isAsyncStarted());
                                done = true;
                            } catch (InterruptedException e) {
                                result.append(e);
                            } catch (IOException e) {
                                result.append(e);
                            }
                        }
                    });
                    t.start();
                }
            });
            // Pointless method call so there is somewhere to put a break point
            // when debugging
            req.getMethod();
        }
    }

    private static class AsyncStartNoCompleteServlet extends HttpServlet {

        public static final long ASYNC_TIMEOUT = 1000;

        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(final HttpServletRequest req,
                final HttpServletResponse resp)
                throws ServletException, IOException {

            String echo = req.getParameter("echo");
            AsyncContext actxt = req.startAsync();
            resp.setContentType("text/plain");
            resp.getWriter().print("OK");
            if (echo != null) {
                resp.getWriter().print("-" + echo);
            }
            // Speed up the test by reducing the timeout
            actxt.setTimeout(ASYNC_TIMEOUT);
        }
    }

    private static class AsyncStartWithCompleteServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(final HttpServletRequest req,
                final HttpServletResponse resp)
                throws ServletException, IOException {

            AsyncContext actxt = req.startAsync();
            actxt.setTimeout(3000);
            resp.setContentType("text/plain");
            resp.getWriter().print("OK");
            actxt.complete();
        }
    }

    @Test
    public void testTimeoutListenerCompleteNoDispatch() throws Exception {
        // Should work
        doTestTimeout(Boolean.TRUE, null);
    }

    @Test
    public void testTimeoutListenerNoCompleteNoDispatch() throws Exception {
        // Should trigger an error - must do one or other
        doTestTimeout(Boolean.FALSE, null);
    }

    @Test
    public void testTimeoutListenerCompleteDispatch() throws Exception {
        // Should trigger an error - can't do both
        doTestTimeout(Boolean.TRUE, "/nonasync");
    }

    @Test
    public void testTimeoutListenerNoCompleteDispatch() throws Exception {
        // Should work
        doTestTimeout(Boolean.FALSE, "/nonasync");
    }

    @Test
    public void testTimeoutNoListener() throws Exception {
        // Should work
        doTestTimeout(null, null);
    }

    private void doTestTimeout(Boolean completeOnTimeout, String dispatchUrl)
    throws Exception {
        // Setup Tomcat instance
        Tomcat tomcat = getTomcatInstance();

        // Must have a real docBase - just use temp
        File docBase = new File(System.getProperty("java.io.tmpdir"));

        // Create the folder that will trigger the redirect
        File foo = new File(docBase, "async");
        addDeleteOnTearDown(foo);
        if (!foo.mkdirs() && !foo.isDirectory()) {
            fail("Unable to create async directory in docBase");
        }

        Context ctx = tomcat.addContext("", docBase.getAbsolutePath());

        TimeoutServlet timeout =
                new TimeoutServlet(completeOnTimeout, dispatchUrl);

        Wrapper wrapper = Tomcat.addServlet(ctx, "time", timeout);
        wrapper.setAsyncSupported(true);
        ctx.addServletMapping("/async", "time");

        if (dispatchUrl != null) {
            NonAsyncServlet nonAsync = new NonAsyncServlet();
            Tomcat.addServlet(ctx, "nonasync", nonAsync);
            ctx.addServletMapping(dispatchUrl, "nonasync");
        }

        ctx.addApplicationListener(new ApplicationListener(
                TrackingRequestListener.class.getName(), false));

        TesterAccessLogValve alv = new TesterAccessLogValve();
        ctx.getPipeline().addValve(alv);
        TesterAccessLogValve alvGlobal = new TesterAccessLogValve();
        tomcat.getHost().getPipeline().addValve(alvGlobal);

        tomcat.start();
        ByteChunk res = new ByteChunk();
        try {
            getUrl("http://localhost:" + getPort() + "/async", res, null);
        } catch (IOException ioe) {
            // Ignore - expected for some error conditions
        }
        StringBuilder expected = new StringBuilder("requestInitialized-");
        expected.append("TimeoutServletGet-");
        if (completeOnTimeout == null) {
            expected.append("requestDestroyed");
        } else if (completeOnTimeout.booleanValue()) {
            expected.append("onTimeout-");
            expected.append("onComplete-");
            expected.append("requestDestroyed");
        } else {
            expected.append("onTimeout-");
            if (dispatchUrl != null) {
                expected.append("NonAsyncServletGet-");
            }
            expected.append("onComplete-");
            expected.append("requestDestroyed");
        }
        assertEquals(expected.toString(), res.toString());

        // Check the access log
        if (completeOnTimeout == null) {
            alvGlobal.validateAccessLog(1, 500, TimeoutServlet.ASYNC_TIMEOUT,
                    TimeoutServlet.ASYNC_TIMEOUT + TIMEOUT_MARGIN +
                    REQUEST_TIME);
            alv.validateAccessLog(1, 500, TimeoutServlet.ASYNC_TIMEOUT,
                    TimeoutServlet.ASYNC_TIMEOUT + TIMEOUT_MARGIN +
                    REQUEST_TIME);
        } else {
            alvGlobal.validateAccessLog(1, 200, TimeoutServlet.ASYNC_TIMEOUT,
                    TimeoutServlet.ASYNC_TIMEOUT + TIMEOUT_MARGIN +
                    REQUEST_TIME);
            alv.validateAccessLog(1, 200, TimeoutServlet.ASYNC_TIMEOUT,
                    TimeoutServlet.ASYNC_TIMEOUT + TIMEOUT_MARGIN +
                    REQUEST_TIME);
        }
    }

    private static class TimeoutServlet extends HttpServlet {
        private static final long serialVersionUID = 1L;

        private final Boolean completeOnTimeout;
        private final String dispatchUrl;

        public static final long ASYNC_TIMEOUT = 3000;

        public TimeoutServlet(Boolean completeOnTimeout, String dispatchUrl) {
            this.completeOnTimeout = completeOnTimeout;
            this.dispatchUrl = dispatchUrl;
        }

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {
            if (req.isAsyncSupported()) {
                resp.getWriter().print("TimeoutServletGet-");
                final AsyncContext ac = req.startAsync();
                ac.setTimeout(ASYNC_TIMEOUT);

                if (completeOnTimeout != null) {
                    ac.addListener(new TrackingListener(false,
                            completeOnTimeout.booleanValue(), dispatchUrl));
                }
            } else {
                resp.getWriter().print("FAIL: Async unsupported");
            }
        }
    }

    @Test
    public void testDispatchSingle() throws Exception {
        doTestDispatch(1, false);
    }

    @Test
    public void testDispatchDouble() throws Exception {
        doTestDispatch(2, false);
    }

    @Test
    public void testDispatchMultiple() throws Exception {
        doTestDispatch(5, false);
    }

    @Test
    public void testDispatchWithThreadSingle() throws Exception {
        doTestDispatch(1, true);
    }

    @Test
    public void testDispatchWithThreadDouble() throws Exception {
        doTestDispatch(2, true);
    }

    @Test
    public void testDispatchWithThreadMultiple() throws Exception {
        doTestDispatch(5, true);
    }

    private void doTestDispatch(int iter, boolean useThread) throws Exception {
        // Setup Tomcat instance
        Tomcat tomcat = getTomcatInstance();

        // Must have a real docBase - just use temp
        File docBase = new File(System.getProperty("java.io.tmpdir"));

        Context ctx = tomcat.addContext("", docBase.getAbsolutePath());

        DispatchingServlet dispatch = new DispatchingServlet(false, false);
        Wrapper wrapper = Tomcat.addServlet(ctx, "dispatch", dispatch);
        wrapper.setAsyncSupported(true);
        ctx.addServletMapping("/stage1", "dispatch");

        NonAsyncServlet nonasync = new NonAsyncServlet();
        Wrapper wrapper2 = Tomcat.addServlet(ctx, "nonasync", nonasync);
        wrapper2.setAsyncSupported(true);
        ctx.addServletMapping("/stage2", "nonasync");

        ctx.addApplicationListener(new ApplicationListener(
                TrackingRequestListener.class.getName(), false));

        TesterAccessLogValve alv = new TesterAccessLogValve();
        ctx.getPipeline().addValve(alv);

        tomcat.start();

        StringBuilder url = new StringBuilder(48);
        url.append("http://localhost:");
        url.append(getPort());
        url.append("/stage1?iter=");
        url.append(iter);
        if (useThread) {
            url.append("&useThread=y");
        }
        ByteChunk res = getUrl(url.toString());

        StringBuilder expected = new StringBuilder("requestInitialized-");
        int loop = iter;
        while (loop > 0) {
            expected.append("DispatchingServletGet-");
            loop--;
        }
        expected.append("NonAsyncServletGet-");
        expected.append("requestDestroyed");
        assertEquals(expected.toString(), res.toString());

        // Check the access log
        alv.validateAccessLog(1, 200, 0, REQUEST_TIME);
    }

    private static class DispatchingServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;
        private static final String ITER_PARAM = "iter";
        private static final String DISPATCH_CHECK = "check";
        private boolean addTrackingListener = false;
        private boolean completeOnError = false;

        public DispatchingServlet(boolean addTrackingListener,
                boolean completeOnError) {
            this.addTrackingListener = addTrackingListener;
            this.completeOnError = completeOnError;
        }

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {

            if ("y".equals(req.getParameter(DISPATCH_CHECK))) {
                if (req.getDispatcherType() != DispatcherType.ASYNC) {
                    resp.getWriter().write("WrongDispatcherType-");
                }
            }
            resp.getWriter().write("DispatchingServletGet-");
            resp.flushBuffer();
            final int iter = Integer.parseInt(req.getParameter(ITER_PARAM)) - 1;
            final AsyncContext ctxt = req.startAsync();
            if (addTrackingListener) {
                TrackingListener listener =
                    new TrackingListener(completeOnError, true, null);
                ctxt.addListener(listener);
            }
            Runnable run = new Runnable() {
                @Override
                public void run() {
                    if (iter > 0) {
                        ctxt.dispatch("/stage1?" + ITER_PARAM + "=" + iter +
                                "&" + DISPATCH_CHECK + "=y");
                    } else {
                        ctxt.dispatch("/stage2");
                    }
                }
            };
            if ("y".equals(req.getParameter("useThread"))) {
                new Thread(run).start();
            } else {
                run.run();
            }
        }
    }

    private static class NonAsyncServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {
            resp.getWriter().write("NonAsyncServletGet-");
            resp.flushBuffer();
        }
    }

    @Test
    public void testListeners() throws Exception {
        // Setup Tomcat instance
        Tomcat tomcat = getTomcatInstance();

        // Must have a real docBase - just use temp
        File docBase = new File(System.getProperty("java.io.tmpdir"));

        Context ctx = tomcat.addContext("", docBase.getAbsolutePath());

        TrackingServlet tracking = new TrackingServlet();
        Wrapper wrapper = Tomcat.addServlet(ctx, "tracking", tracking);
        wrapper.setAsyncSupported(true);
        ctx.addServletMapping("/stage1", "tracking");

        TimeoutServlet timeout = new TimeoutServlet(Boolean.TRUE, null);
        Wrapper wrapper2 = Tomcat.addServlet(ctx, "timeout", timeout);
        wrapper2.setAsyncSupported(true);
        ctx.addServletMapping("/stage2", "timeout");

        TesterAccessLogValve alv = new TesterAccessLogValve();
        ctx.getPipeline().addValve(alv);

        tomcat.start();

        StringBuilder url = new StringBuilder(48);
        url.append("http://localhost:");
        url.append(getPort());
        url.append("/stage1");

        ByteChunk res = getUrl(url.toString());

        assertEquals(
                "DispatchingServletGet-DispatchingServletGet-onStartAsync-" +
                "TimeoutServletGet-onStartAsync-onTimeout-onComplete-",
                res.toString());

        // Check the access log
        alv.validateAccessLog(1, 200, TimeoutServlet.ASYNC_TIMEOUT,
                TimeoutServlet.ASYNC_TIMEOUT + TIMEOUT_MARGIN + REQUEST_TIME);
    }

    private static class TrackingServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        private static volatile boolean first = true;

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {
            resp.getWriter().write("DispatchingServletGet-");
            resp.flushBuffer();

            final boolean first = TrackingServlet.first;
            TrackingServlet.first = false;

            final AsyncContext ctxt = req.startAsync();
            TrackingListener listener = new TrackingListener(false, true, null);
            ctxt.addListener(listener);
            ctxt.setTimeout(3000);

            Runnable run = new Runnable() {
                @Override
                public void run() {
                    if (first) {
                        ctxt.dispatch("/stage1");
                    } else {
                        ctxt.dispatch("/stage2");
                    }
                }
            };
            if ("y".equals(req.getParameter("useThread"))) {
                new Thread(run).start();
            } else {
                run.run();
            }
        }
    }

    private static class TrackingListener implements AsyncListener {

        private final boolean completeOnError;
        private final boolean completeOnTimeout;
        private final String dispatchUrl;

        public TrackingListener(boolean completeOnError,
                boolean completeOnTimeout, String dispatchUrl) {
            this.completeOnError = completeOnError;
            this.completeOnTimeout = completeOnTimeout;
            this.dispatchUrl = dispatchUrl;
        }

        @Override
        public void onComplete(AsyncEvent event) throws IOException {
            ServletResponse resp = event.getAsyncContext().getResponse();
            resp.getWriter().write("onComplete-");
            resp.flushBuffer();
        }

        @Override
        public void onTimeout(AsyncEvent event) throws IOException {
            ServletResponse resp = event.getAsyncContext().getResponse();
            resp.getWriter().write("onTimeout-");
            resp.flushBuffer();
            if (completeOnTimeout){
                event.getAsyncContext().complete();
            }
            if (dispatchUrl != null) {
                event.getAsyncContext().dispatch(dispatchUrl);
            }
        }

        @Override
        public void onError(AsyncEvent event) throws IOException {
            ServletResponse resp = event.getAsyncContext().getResponse();
            resp.getWriter().write("onError-");
            resp.flushBuffer();
            if (completeOnError) {
                event.getAsyncContext().complete();
            }
        }

        @Override
        public void onStartAsync(AsyncEvent event) throws IOException {
            ServletResponse resp = event.getAsyncContext().getResponse();
            resp.getWriter().write("onStartAsync-");
            resp.flushBuffer();
        }
    }

    public static class TrackingRequestListener
            implements ServletRequestListener {

        @Override
        public void requestDestroyed(ServletRequestEvent sre) {
            // Need the response and it isn't available via the Servlet API
            Request r = (Request) sre.getServletRequest();
            try {
                r.getResponse().getWriter().print("requestDestroyed");
            } catch (IOException e) {
                // Test will fail if this happens
                e.printStackTrace();
            }
        }

        @Override
        public void requestInitialized(ServletRequestEvent sre) {
            // Need the response and it isn't available via the Servlet API
            Request r = (Request) sre.getServletRequest();
            try {
                r.getResponse().getWriter().print("requestInitialized-");
            } catch (IOException e) {
                // Test will fail if this happens
                e.printStackTrace();
            }
        }
    }

    @Test
    public void testDispatchErrorSingle() throws Exception {
        doTestDispatchError(1, false, false);
    }

    @Test
    public void testDispatchErrorDouble() throws Exception {
        doTestDispatchError(2, false, false);
    }

    @Test
    public void testDispatchErrorMultiple() throws Exception {
        doTestDispatchError(5, false, false);
    }

    @Test
    public void testDispatchErrorWithThreadSingle() throws Exception {
        doTestDispatchError(1, true, false);
    }

    @Test
    public void testDispatchErrorWithThreadDouble() throws Exception {
        doTestDispatchError(2, true, false);
    }

    @Test
    public void testDispatchErrorWithThreadMultiple() throws Exception {
        doTestDispatchError(5, true, false);
    }

    @Test
    public void testDispatchErrorSingleThenComplete() throws Exception {
        doTestDispatchError(1, false, true);
    }

    @Test
    public void testDispatchErrorDoubleThenComplete() throws Exception {
        doTestDispatchError(2, false, true);
    }

    @Test
    public void testDispatchErrorMultipleThenComplete() throws Exception {
        doTestDispatchError(5, false, true);
    }

    @Test
    public void testDispatchErrorWithThreadSingleThenComplete()
            throws Exception {
        doTestDispatchError(1, true, true);
    }

    @Test
    public void testDispatchErrorWithThreadDoubleThenComplete()
            throws Exception {
        doTestDispatchError(2, true, true);
    }

    @Test
    public void testDispatchErrorWithThreadMultipleThenComplete()
            throws Exception {
        doTestDispatchError(5, true, true);
    }

    private void doTestDispatchError(int iter, boolean useThread,
            boolean completeOnError)
            throws Exception {
        // Setup Tomcat instance
        Tomcat tomcat = getTomcatInstance();

        // Must have a real docBase - just use temp
        File docBase = new File(System.getProperty("java.io.tmpdir"));

        Context ctx = tomcat.addContext("", docBase.getAbsolutePath());

        DispatchingServlet dispatch =
            new DispatchingServlet(true, completeOnError);
        Wrapper wrapper = Tomcat.addServlet(ctx, "dispatch", dispatch);
        wrapper.setAsyncSupported(true);
        ctx.addServletMapping("/stage1", "dispatch");

        ErrorServlet error = new ErrorServlet(true);
        Tomcat.addServlet(ctx, "error", error);
        ctx.addServletMapping("/stage2", "error");

        ctx.addApplicationListener(new ApplicationListener(
                TrackingRequestListener.class.getName(), false));

        TesterAccessLogValve alv = new TesterAccessLogValve();
        ctx.getPipeline().addValve(alv);

        tomcat.start();

        StringBuilder url = new StringBuilder(48);
        url.append("http://localhost:");
        url.append(getPort());
        url.append("/stage1?iter=");
        url.append(iter);
        if (useThread) {
            url.append("&useThread=y");
        }
        ByteChunk res = getUrl(url.toString());

        StringBuilder expected = new StringBuilder("requestInitialized-");
        int loop = iter;
        while (loop > 0) {
            expected.append("DispatchingServletGet-");
            if (loop != iter) {
                expected.append("onStartAsync-");
            }
            loop--;
        }
        expected.append("ErrorServletGet-onError-onComplete-requestDestroyed");
        assertEquals(expected.toString(), res.toString());

        // Check the access log
        alv.validateAccessLog(1, 200, 0, REQUEST_TIME);
    }

    private static class ErrorServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        private boolean flush = false;

        public ErrorServlet(boolean flush) {
            this.flush = flush;
        }

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {
            resp.getWriter().write("ErrorServletGet-");
            if (flush) {
                resp.flushBuffer();
            }
            try {
                // Give the original thread a chance to exit the
                // ErrorReportValve before we throw this exception
                Thread.sleep(500);
            } catch (InterruptedException e) {
                throw new ServletException(e);
            }
            throw new ServletException("Opps.");
        }
    }

    @Test
    public void testBug50352() throws Exception {
        // Setup Tomcat instance
        Tomcat tomcat = getTomcatInstance();

        // Must have a real docBase - just use temp
        File docBase = new File(System.getProperty("java.io.tmpdir"));

        Context ctx = tomcat.addContext("", docBase.getAbsolutePath());

        AsyncStartRunnable servlet = new AsyncStartRunnable();
        Wrapper wrapper = Tomcat.addServlet(ctx, "servlet", servlet);
        wrapper.setAsyncSupported(true);
        ctx.addServletMapping("/", "servlet");

        TesterAccessLogValve alv = new TesterAccessLogValve();
        ctx.getPipeline().addValve(alv);

        tomcat.start();

        ByteChunk res = getUrl("http://localhost:" + getPort() + "/");

        assertEquals("Runnable-onComplete-", res.toString());

        // Check the access log
        alv.validateAccessLog(1, 200, AsyncStartRunnable.THREAD_SLEEP_TIME,
                AsyncStartRunnable.THREAD_SLEEP_TIME + REQUEST_TIME);
    }

    private static final class AsyncStartRunnable extends HttpServlet {

        private static final long serialVersionUID = 1L;

        public static final long THREAD_SLEEP_TIME = 3000;

        @Override
        protected void doGet(HttpServletRequest request,
                HttpServletResponse response)
                throws ServletException, IOException {

            final AsyncContext asyncContext =
                request.startAsync(request, response);

            asyncContext.addListener(new TrackingListener(false, false, null));

            asyncContext.start(new Runnable() {

                @Override
                public void run() {
                    try {
                        Thread.sleep(THREAD_SLEEP_TIME);
                        asyncContext.getResponse().getWriter().write(
                                "Runnable-");
                        asyncContext.complete();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });
        }
    }

    @Test
    public void testBug50753() throws Exception {
        // Setup Tomcat instance
        Tomcat tomcat = getTomcatInstance();

        // Must have a real docBase - just use temp
        Context ctx =
            tomcat.addContext("", System.getProperty("java.io.tmpdir"));

        Bug50753Servlet servlet = new Bug50753Servlet();

        Wrapper wrapper = Tomcat.addServlet(ctx, "servlet", servlet);
        wrapper.setAsyncSupported(true);
        ctx.addServletMapping("/", "servlet");

        TesterAccessLogValve alv = new TesterAccessLogValve();
        ctx.getPipeline().addValve(alv);

        tomcat.start();

        // Call the servlet once
        Map<String,List<String>> headers =
            new LinkedHashMap<String,List<String>>();
        ByteChunk bc = new ByteChunk();
        int rc = getUrl("http://localhost:" + getPort() + "/", bc, headers);
        assertEquals(200, rc);
        assertEquals("OK", bc.toString());
        List<String> testHeader = headers.get("A");
        assertNotNull(testHeader);
        assertEquals(1, testHeader.size());
        assertEquals("xyz",testHeader.get(0));

        // Check the access log
        alv.validateAccessLog(1, 200, Bug50753Servlet.THREAD_SLEEP_TIME,
                Bug50753Servlet.THREAD_SLEEP_TIME + REQUEST_TIME);
    }

    private static class Bug50753Servlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        public static final long THREAD_SLEEP_TIME = 5000;

        @Override
        protected void doGet(HttpServletRequest req,
                final HttpServletResponse resp)
                throws ServletException, IOException {
            final AsyncContext ctx = req.startAsync();
            ctx.start(new Runnable() {
                @Override
                public void run() {
                    try {
                        Thread.sleep(THREAD_SLEEP_TIME);
                        resp.setHeader("A", "xyz");
                        resp.setContentType("text/plain");
                        resp.setContentLength("OK".getBytes().length);
                        resp.getWriter().print("OK");
                        ctx.complete();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });
        }
    }

    @Test
    public void testErrorHandling() throws Exception {
        // Setup Tomcat instance
        Tomcat tomcat = getTomcatInstance();

        // Must have a real docBase - just use temp
        File docBase = new File(System.getProperty("java.io.tmpdir"));

        Context ctx = tomcat.addContext("", docBase.getAbsolutePath());

        ErrorServlet error = new ErrorServlet(false);
        Tomcat.addServlet(ctx, "error", error);
        ctx.addServletMapping("/error", "error");

        TesterAccessLogValve alv = new TesterAccessLogValve();
        ctx.getPipeline().addValve(alv);

        tomcat.start();

        StringBuilder url = new StringBuilder(48);
        url.append("http://localhost:");
        url.append(getPort());
        url.append("/error");

        int rc = getUrl(url.toString(), new ByteChunk(), null);

        assertEquals(500, rc);

        // Without this test may complete before access log has a chance to log
        // the request
        Thread.sleep(REQUEST_TIME);

        // Check the access log
        alv.validateAccessLog(1, 500, 0, REQUEST_TIME);
    }

    @Test
    public void testCommitOnComplete() throws Exception {
        // Setup Tomcat instance
        Tomcat tomcat = getTomcatInstance();

        // Must have a real docBase - just use temp
        File docBase = new File(System.getProperty("java.io.tmpdir"));

        Context ctx = tomcat.addContext("", docBase.getAbsolutePath());

        AsyncStatusServlet asyncStatusServlet =
            new AsyncStatusServlet(HttpServletResponse.SC_BAD_REQUEST);
        Wrapper wrapper =
            Tomcat.addServlet(ctx, "asyncStatusServlet", asyncStatusServlet);
        wrapper.setAsyncSupported(true);
        ctx.addServletMapping("/asyncStatusServlet", "asyncStatusServlet");

        TesterAccessLogValve alv = new TesterAccessLogValve();
        ctx.getPipeline().addValve(alv);

        tomcat.start();

        StringBuilder url = new StringBuilder(48);
        url.append("http://localhost:");
        url.append(getPort());
        url.append("/asyncStatusServlet");

        int rc = getUrl(url.toString(), new ByteChunk(), null);

        assertEquals(HttpServletResponse.SC_BAD_REQUEST, rc);

        // Without this test may complete before access log has a chance to log
        // the request
        Thread.sleep(REQUEST_TIME);

        // Check the access log
        alv.validateAccessLog(1, HttpServletResponse.SC_BAD_REQUEST, 0,
                REQUEST_TIME);

    }

    private static class AsyncStatusServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        private int status;

        public AsyncStatusServlet(int status) {
            this.status = status;
        }

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {

            AsyncContext actxt = req.startAsync();
            resp.setStatus(status);
            actxt.complete();
        }
    }

    @Test
    public void testBug51197a() throws Exception {
        doTestBug51197(false);
    }

    @Test
    public void testBug51197b() throws Exception {
        doTestBug51197(true);
    }

    private void doTestBug51197(boolean threaded) throws Exception {
        // Setup Tomcat instance
        Tomcat tomcat = getTomcatInstance();

        // Must have a real docBase - just use temp
        File docBase = new File(System.getProperty("java.io.tmpdir"));

        Context ctx = tomcat.addContext("", docBase.getAbsolutePath());

        AsyncErrorServlet asyncErrorServlet =
            new AsyncErrorServlet(HttpServletResponse.SC_BAD_REQUEST, threaded);
        Wrapper wrapper =
            Tomcat.addServlet(ctx, "asyncErrorServlet", asyncErrorServlet);
        wrapper.setAsyncSupported(true);
        ctx.addServletMapping("/asyncErrorServlet", "asyncErrorServlet");

        TesterAccessLogValve alv = new TesterAccessLogValve();
        ctx.getPipeline().addValve(alv);

        tomcat.start();

        StringBuilder url = new StringBuilder(48);
        url.append("http://localhost:");
        url.append(getPort());
        url.append("/asyncErrorServlet");

        ByteChunk res = new ByteChunk();
        int rc = getUrl(url.toString(), res, null);

        assertEquals(HttpServletResponse.SC_BAD_REQUEST, rc);

        // SRV 10.9.2 - Writing the response is entirely the application's
        // responsibility when an error occurs on an application thread.
        // The test servlet writes no content in this case.
        if (threaded) {
            assertEquals(0, res.getLength());
        } else {
            assertTrue(res.getLength() > 0);
        }

        // Without this test may complete before access log has a chance to log
        // the request
        Thread.sleep(REQUEST_TIME);

        // Check the access log
        alv.validateAccessLog(1, HttpServletResponse.SC_BAD_REQUEST, 0,
                REQUEST_TIME);
    }

    private static class AsyncErrorServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        public static final String ERROR_MESSAGE = "It broke.";

        private int status;
        private boolean threaded;

        public AsyncErrorServlet(int status, boolean threaded) {
            this.status = status;
            this.threaded = threaded;
        }

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {

            final AsyncContext actxt = req.startAsync();
            actxt.setTimeout(TIMEOUT);
            if (threaded) {
                actxt.start(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            HttpServletResponse resp =
                                    (HttpServletResponse) actxt.getResponse();
                            resp.sendError(status, ERROR_MESSAGE);
                            // Complete so there is no delay waiting for the
                            // timeout
                            actxt.complete();
                        } catch (IOException e) {
                            // Ignore
                        }
                    }
                });
            } else {
                resp.sendError(status);
            }
        }
    }

    @Test
    public void testBug53337() throws Exception {
        // Setup Tomcat instance
        Tomcat tomcat = getTomcatInstance();

        // Must have a real docBase - just use temp
        File docBase = new File(System.getProperty("java.io.tmpdir"));

        Context ctx = tomcat.addContext("", docBase.getAbsolutePath());
        Wrapper a = Tomcat.addServlet(ctx, "ServletA", new Bug53337ServletA());
        a.setAsyncSupported(true);
        Wrapper b = Tomcat.addServlet(ctx, "ServletB", new Bug53337ServletB());
        b.setAsyncSupported(true);
        Tomcat.addServlet(ctx, "ServletC", new Bug53337ServletC());
        ctx.addServletMapping("/ServletA", "ServletA");
        ctx.addServletMapping("/ServletB", "ServletB");
        ctx.addServletMapping("/ServletC", "ServletC");

        tomcat.start();

        StringBuilder url = new StringBuilder(48);
        url.append("http://localhost:");
        url.append(getPort());
        url.append("/ServletA");

        ByteChunk body = new ByteChunk();
        int rc = getUrl(url.toString(), body, null);

        assertEquals(HttpServletResponse.SC_OK, rc);
        assertEquals("OK", body.toString());
    }

    private static class Bug53337ServletA extends HttpServlet {

        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {
            RequestDispatcher rd = req.getRequestDispatcher("/ServletB");
            rd.forward(req, resp);
        }
    }

    private static class Bug53337ServletB extends HttpServlet {

        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(final HttpServletRequest req,
                final HttpServletResponse resp)
                throws ServletException, IOException {

            final AsyncContext async = req.startAsync();
            // Just for debugging
            async.setTimeout(100000);

            ExecutorService executor = Executors.newSingleThreadExecutor();
            executor.submit(new Runnable() {

                @Override
                public void run() {
                    async.dispatch("/ServletC");
                }
            });
            executor.shutdown();
        }
    }

    private static class Bug53337ServletC extends HttpServlet {

        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {
            resp.setContentType("text/plain");
            resp.getWriter().print("OK");
        }
    }

    @Test
    public void testBug53843() throws Exception {
        // Setup Tomcat instance
        Tomcat tomcat = getTomcatInstance();

        // Must have a real docBase - just use temp
        File docBase = new File(System.getProperty("java.io.tmpdir"));

        Context ctx = tomcat.addContext("", docBase.getAbsolutePath());
        Bug53843ServletA servletA = new Bug53843ServletA();
        Wrapper a = Tomcat.addServlet(ctx, "ServletA", servletA);
        a.setAsyncSupported(true);
        Tomcat.addServlet(ctx, "ServletB", new Bug53843ServletB());

        ctx.addServletMapping("/ServletA", "ServletA");
        ctx.addServletMapping("/ServletB", "ServletB");

        tomcat.start();

        StringBuilder url = new StringBuilder(48);
        url.append("http://localhost:");
        url.append(getPort());
        url.append("/ServletA");

        ByteChunk body = new ByteChunk();
        int rc = getUrl(url.toString(), body, null);

        assertEquals(HttpServletResponse.SC_OK, rc);
        assertEquals("OK", body.toString());
        assertTrue(servletA.isAsyncWhenExpected());
    }

    private static class Bug53843ServletA extends HttpServlet {

        private static final long serialVersionUID = 1L;

        private boolean isAsyncWhenExpected = true;

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {

            // Should not be async at this point
            isAsyncWhenExpected = isAsyncWhenExpected && !req.isAsyncStarted();

            final AsyncContext async = req.startAsync();

            // Should be async at this point
            isAsyncWhenExpected = isAsyncWhenExpected && req.isAsyncStarted();

            async.start(new Runnable() {

                @Override
                public void run() {
                    // This should be delayed until the original container
                    // thread exists
                    async.dispatch("/ServletB");
                }
            });

            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                throw new ServletException(e);
            }

            // Should be async at this point
            isAsyncWhenExpected = isAsyncWhenExpected && req.isAsyncStarted();
        }

        public boolean isAsyncWhenExpected() {
            return isAsyncWhenExpected;
        }
    }

    private static class Bug53843ServletB extends HttpServlet {

        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {
            resp.setContentType("text/plain");
            resp.getWriter().print("OK");
        }
    }

    @Test
    public void testTimeoutErrorDispatchNone() throws Exception {
        doTestTimeoutErrorDispatch(null, null);
    }

    @Test
    public void testTimeoutErrorDispatchNonAsync() throws Exception {
        doTestTimeoutErrorDispatch(Boolean.FALSE, null);
    }

    @Test
    public void testTimeoutErrorDispatchAsyncStart() throws Exception {
        doTestTimeoutErrorDispatch(
                Boolean.TRUE, ErrorPageAsyncMode.NO_COMPLETE);
    }

    @Test
    public void testTimeoutErrorDispatchAsyncComplete() throws Exception {
        doTestTimeoutErrorDispatch(Boolean.TRUE, ErrorPageAsyncMode.COMPLETE);
    }

    @Test
    public void testTimeoutErrorDispatchAsyncDispatch() throws Exception {
        doTestTimeoutErrorDispatch(Boolean.TRUE, ErrorPageAsyncMode.DISPATCH);
    }

    private void doTestTimeoutErrorDispatch(Boolean asyncError,
            ErrorPageAsyncMode mode) throws Exception {
        // Setup Tomcat instance
        Tomcat tomcat = getTomcatInstance();

        // Must have a real docBase - just use temp
        File docBase = new File(System.getProperty("java.io.tmpdir"));

        Context ctx = tomcat.addContext("", docBase.getAbsolutePath());

        TimeoutServlet timeout = new TimeoutServlet(null, null);
        Wrapper w1 = Tomcat.addServlet(ctx, "time", timeout);
        w1.setAsyncSupported(true);
        ctx.addServletMapping("/async", "time");

        NonAsyncServlet nonAsync = new NonAsyncServlet();
        Wrapper w2 = Tomcat.addServlet(ctx, "nonAsync", nonAsync);
        w2.setAsyncSupported(true);
        ctx.addServletMapping("/error/nonasync", "nonAsync");

        AsyncErrorPage asyncErrorPage = new AsyncErrorPage(mode);
        Wrapper w3 = Tomcat.addServlet(ctx, "asyncErrorPage", asyncErrorPage);
        w3.setAsyncSupported(true);
        ctx.addServletMapping("/error/async", "asyncErrorPage");

        if (asyncError != null) {
            ErrorPage ep = new ErrorPage();
            ep.setErrorCode(500);
            if (asyncError.booleanValue()) {
                ep.setLocation("/error/async");
            } else {
                ep.setLocation("/error/nonasync");
            }

            ctx.addErrorPage(ep);
        }

        ctx.addApplicationListener(new ApplicationListener(
                TrackingRequestListener.class.getName(), false));

        TesterAccessLogValve alv = new TesterAccessLogValve();
        ctx.getPipeline().addValve(alv);
        TesterAccessLogValve alvGlobal = new TesterAccessLogValve();
        tomcat.getHost().getPipeline().addValve(alvGlobal);

        tomcat.start();
        ByteChunk res = new ByteChunk();
        try {
            getUrl("http://localhost:" + getPort() + "/async", res, null);
        } catch (IOException ioe) {
            // Ignore - expected for some error conditions
        }

        StringBuilder expected = new StringBuilder();
        if (asyncError == null) {
            // No error handler - just get the 500 response
            expected.append("requestInitialized-TimeoutServletGet-");
            // Note: With an error handler the response will be reset and these
            //       will be lost
        }
        if (asyncError != null) {
            if (asyncError.booleanValue()) {
                expected.append("AsyncErrorPageGet-");
                if (mode == ErrorPageAsyncMode.NO_COMPLETE){
                    expected.append("NoOp-");
                } else if (mode == ErrorPageAsyncMode.COMPLETE) {
                    expected.append("Complete-");
                } else if (mode == ErrorPageAsyncMode.DISPATCH) {
                    expected.append("Dispatch-NonAsyncServletGet-");
                }
            } else {
                expected.append("NonAsyncServletGet-");
            }
        }
        expected.append("requestDestroyed");

        Assert.assertEquals(expected.toString(), res.toString());

        // Check the access log
        alvGlobal.validateAccessLog(1, 500, TimeoutServlet.ASYNC_TIMEOUT,
                TimeoutServlet.ASYNC_TIMEOUT + TIMEOUT_MARGIN +
                REQUEST_TIME);
        alv.validateAccessLog(1, 500, TimeoutServlet.ASYNC_TIMEOUT,
                TimeoutServlet.ASYNC_TIMEOUT + TIMEOUT_MARGIN +
                REQUEST_TIME);
    }

    private static enum ErrorPageAsyncMode {
        NO_COMPLETE,
        COMPLETE,
        DISPATCH
    }

    private static class AsyncErrorPage extends HttpServlet {

        private static final long serialVersionUID = 1L;

        private final ErrorPageAsyncMode mode;

        public AsyncErrorPage(ErrorPageAsyncMode mode) {
            this.mode = mode;
        }

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {
            PrintWriter writer = resp.getWriter();
            writer.write("AsyncErrorPageGet-");
            resp.flushBuffer();

            final AsyncContext ctxt = req.getAsyncContext();

            switch(mode) {
                case COMPLETE:
                    writer.write("Complete-");
                    ctxt.complete();
                    break;
                case DISPATCH:
                    writer.write("Dispatch-");
                    ctxt.dispatch("/error/nonasync");
                    break;
                case NO_COMPLETE:
                    writer.write("NoOp-");
                    break;
                default:
                    // Impossible
                    break;
            }
        }
    }

    @Test
    public void testBug54178() throws Exception {
        // Setup Tomcat instance
        Tomcat tomcat = getTomcatInstance();

        // Must have a real docBase - just use temp
        File docBase = new File(System.getProperty("java.io.tmpdir"));

        Context ctx = tomcat.addContext("", docBase.getAbsolutePath());

        Bug54178ServletA bug54178ServletA = new Bug54178ServletA();
        Wrapper wrapper =
            Tomcat.addServlet(ctx, "bug54178ServletA", bug54178ServletA);
        wrapper.setAsyncSupported(true);
        ctx.addServletMapping("/bug54178ServletA", "bug54178ServletA");

        Bug54178ServletB bug54178ServletB = new Bug54178ServletB();
        Tomcat.addServlet(ctx, "bug54178ServletB", bug54178ServletB);
        ctx.addServletMapping("/bug54178ServletB", "bug54178ServletB");

        tomcat.start();

        ByteChunk body = new ByteChunk();
        int rc = -1;

        try {
            rc = getUrl("http://localhost:" + getPort() + "/bug54178ServletA?" +
                    Bug54178ServletA.PARAM_NAME + "=bar",
                    body, null);
        } catch (IOException ioe) {
            // This may happen if test fails. Output the exception in case it is
            // useful and let asserts handle the failure
            ioe.printStackTrace();
        }

        assertEquals(HttpServletResponse.SC_OK, rc);

        body.recycle();

        rc = getUrl("http://localhost:" + getPort() + "/bug54178ServletB",
                body, null);

        assertEquals(HttpServletResponse.SC_OK, rc);
        assertEquals("OK", body.toString());
    }

    private static class Bug54178ServletA extends HttpServlet {

        public static final String PARAM_NAME = "foo";
        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {

            req.getParameter(PARAM_NAME);
            AsyncContext actxt = req.startAsync();
            actxt.addListener(new Bug54178AsyncListener());
            actxt.complete();
        }
    }

    private static class Bug54178ServletB extends HttpServlet {

        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {

            resp.setContentType("text/plain");
            PrintWriter pw = resp.getWriter();
            String result = req.getParameter(Bug54178ServletA.PARAM_NAME);
            if (result == null) {
                pw.write("OK");
            } else {
                pw.write("FAIL");
            }
        }
    }

    private static class Bug54178AsyncListener implements AsyncListener {

        @Override
        public void onComplete(AsyncEvent event) throws IOException {
            throw new RuntimeException("Testing Bug54178");
        }

        @Override
        public void onTimeout(AsyncEvent event) throws IOException {
            // NO-OP
        }

        @Override
        public void onError(AsyncEvent event) throws IOException {
            // NO-OP
        }

        @Override
        public void onStartAsync(AsyncEvent event) throws IOException {
            // NO-OP
        }
    }

    @Test
    public void testForbiddenDispatching() throws Exception {
        // Setup Tomcat instance
        Tomcat tomcat = getTomcatInstance();

        // Must have a real docBase - just use temp
        File docBase = new File(System.getProperty("java.io.tmpdir"));

        Context ctx = tomcat.addContext("", docBase.getAbsolutePath());

        NonAsyncServlet nonAsyncServlet = new NonAsyncServlet();
        Wrapper wrapper = Tomcat.addServlet(ctx, "nonAsyncServlet",
                nonAsyncServlet);
        wrapper.setAsyncSupported(true);
        ctx.addServletMapping("/target", "nonAsyncServlet");

        DispatchingGenericServlet forbiddenDispatchingServlet = new DispatchingGenericServlet();
        Wrapper wrapper1 = Tomcat.addServlet(ctx,
                "forbiddenDispatchingServlet", forbiddenDispatchingServlet);
        wrapper1.setAsyncSupported(true);
        ctx.addServletMapping("/forbiddenDispatchingServlet",
                "forbiddenDispatchingServlet");

        tomcat.start();

        ByteChunk body = new ByteChunk();

        try {
            getUrl("http://localhost:" + getPort()
                    + "/forbiddenDispatchingServlet", body, null);
        } catch (IOException ioe) {
            // This may happen if test fails. Output the exception in case it is
            // useful and let asserts handle the failure
            ioe.printStackTrace();
        }

        assertTrue(body.toString().contains("OK"));
        assertTrue(body.toString().contains("NonAsyncServletGet"));
    }

    private static class DispatchingGenericServlet extends GenericServlet {

        private static final long serialVersionUID = 1L;
        private static final String CUSTOM_REQ_RESP = "crr";
        private static final String EMPTY_DISPATCH = "empty";

        @Override
        public void service(ServletRequest req, ServletResponse resp)
                throws ServletException, IOException {
            if (DispatcherType.ASYNC != req.getDispatcherType()) {
                AsyncContext asyncContext;
                if ("y".equals(req.getParameter(CUSTOM_REQ_RESP))) {
                    asyncContext = req.startAsync(
                            new ServletRequestWrapper(req),
                            new ServletResponseWrapper(resp));
                } else {
                    asyncContext = req.startAsync();
                }
                if ("y".equals(req.getParameter(EMPTY_DISPATCH))) {
                    asyncContext.dispatch();
                } else {
                    asyncContext.dispatch("/target");
                }
                try {
                    asyncContext.dispatch("/nonExistingServlet");
                    resp.getWriter().print("FAIL");
                } catch (IllegalStateException e) {
                    resp.getWriter().print("OK");
                }
            } else {
                resp.getWriter().print("DispatchingGenericServletGet-");
            }
        }

    }

    @Test
    public void testDispatchWithCustomRequestResponse() throws Exception {
        prepareApplicationWithGenericServlet("");

        StringBuilder expected = new StringBuilder();
        expected.append("OK");
        expected.append("CustomGenericServletGet-");
        requestApplicationWithGenericServlet("/dispatch?crr=y", expected);

        expected = new StringBuilder();
        expected.append("OK");
        expected.append("DispatchingGenericServletGet-");
        requestApplicationWithGenericServlet("/dispatch?crr=y&empty=y",
                expected);
    }

    private static class CustomGenericServlet extends GenericServlet {

        private static final long serialVersionUID = 1L;

        @Override
        public void service(ServletRequest req, ServletResponse res)
                throws ServletException, IOException {
            if (req instanceof ServletRequestWrapper
                    && res instanceof ServletResponseWrapper) {
                res.getWriter().print("CustomGenericServletGet-");
            }
        }

    }

    @Test
    public void testEmptyDispatch() throws Exception {
        prepareApplicationWithGenericServlet("/fo o");
        StringBuilder expected = new StringBuilder();
        expected.append("OK");
        expected.append("DispatchingGenericServletGet-");
        requestApplicationWithGenericServlet("/fo%20o/dispatch?empty=y",
                expected);
        requestApplicationWithGenericServlet("//fo%20o/dispatch?empty=y",
                expected);
        requestApplicationWithGenericServlet("/./fo%20o/dispatch?empty=y",
                expected);
        requestApplicationWithGenericServlet("/fo%20o//dispatch?empty=y",
                expected);
        requestApplicationWithGenericServlet("/fo%20o/./dispatch?empty=y",
                expected);
        requestApplicationWithGenericServlet("/fo%20o/c/../dispatch?empty=y",
                expected);
    }

    @Test
    public void testEmptyDispatchWithCustomRequestResponse() throws Exception {
        prepareApplicationWithGenericServlet("/fo o");
        StringBuilder expected = new StringBuilder();
        expected.append("OK");
        expected.append("DispatchingGenericServletGet-");
        requestApplicationWithGenericServlet("/fo%20o/dispatch?crr=y&empty=y",
                expected);
        requestApplicationWithGenericServlet("//fo%20o/dispatch?crr=y&empty=y",
                expected);
        requestApplicationWithGenericServlet(
                "/./fo%20o/dispatch?crr=y&empty=y", expected);
        requestApplicationWithGenericServlet("/fo%20o//dispatch?crr=y&empty=y",
                expected);
        requestApplicationWithGenericServlet(
                "/fo%20o/./dispatch?crr=y&empty=y", expected);
        requestApplicationWithGenericServlet(
                "/fo%20o/c/../dispatch?crr=y&empty=y", expected);
    }

    private void prepareApplicationWithGenericServlet(String contextPath)
            throws Exception {
        // Setup Tomcat instance
        Tomcat tomcat = getTomcatInstance();

        // Must have a real docBase - just use temp
        File docBase = new File(System.getProperty("java.io.tmpdir"));

        Context ctx = tomcat.addContext(contextPath, docBase.getAbsolutePath());

        DispatchingGenericServlet dispatch = new DispatchingGenericServlet();
        Wrapper wrapper = Tomcat.addServlet(ctx, "dispatch", dispatch);
        wrapper.setAsyncSupported(true);
        ctx.addServletMapping("/dispatch", "dispatch");

        CustomGenericServlet customGeneric = new CustomGenericServlet();
        Wrapper wrapper2 = Tomcat.addServlet(ctx, "customGeneric",
                customGeneric);
        wrapper2.setAsyncSupported(true);
        ctx.addServletMapping("/target", "customGeneric");

        tomcat.start();
    }

    private void requestApplicationWithGenericServlet(String path,
            StringBuilder expectedContent) throws Exception {
        ByteChunk res = getUrl("http://localhost:" + getPort() + path);

        assertEquals(expectedContent.toString(), res.toString());
    }
}
