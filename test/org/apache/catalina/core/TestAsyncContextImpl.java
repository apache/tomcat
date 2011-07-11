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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.AsyncContext;
import javax.servlet.AsyncEvent;
import javax.servlet.AsyncListener;
import javax.servlet.ServletException;
import javax.servlet.ServletRequestEvent;
import javax.servlet.ServletRequestListener;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.Context;
import org.apache.catalina.Wrapper;
import org.apache.catalina.connector.Request;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.catalina.valves.TesterAccessLogValve;
import org.apache.catalina.valves.TesterAccessLogValve.Entry;
import org.apache.tomcat.util.buf.ByteChunk;

public class TestAsyncContextImpl extends TomcatBaseTest {

    // Time for a request to process (need to allow for threads to start etc.)
    private static final long REQUEST_TIME = 1000;
    // Timeout thread (where used) checks for timeout every second
    private static final long TIMEOUT_MARGIN = 1000;
    // Timing tests need a small error margin to prevent failures
    private static final long ERROR_MARGIN = 10;
    // Default timeout for these tests
    private static final long TIMEOUT = 3000;

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
        validateAccessLog(alv, 1, 200, Bug49528Servlet.THREAD_SLEEP_TIME,
                Bug49528Servlet.THREAD_SLEEP_TIME + REQUEST_TIME);
    }
    
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
        validateAccessLog(alv, 1, 200, Bug49567Servlet.THREAD_SLEEP_TIME,
                Bug49567Servlet.THREAD_SLEEP_TIME + REQUEST_TIME);
    }
    
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
        validateAccessLog(alv, 2, 200,
                AsyncStartNoCompleteServlet.ASYNC_TIMEOUT,
                AsyncStartNoCompleteServlet.ASYNC_TIMEOUT + TIMEOUT_MARGIN +
                        REQUEST_TIME);
    }
    
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
        validateAccessLog(alv, 1, 200, 0, REQUEST_TIME);
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

    public void testTimeoutListenerCompleteNoDispatch() throws Exception {
        // Should work
        doTestTimeout(true, null);
    }
    
    public void testTimeoutListenerNoCompleteNoDispatch() throws Exception {
        // Should trigger an error - must do one or other
        doTestTimeout(false, null);
    }

    public void testTimeoutListenerCompleteDispatch() throws Exception {
        // Should trigger an error - can't do both
        doTestTimeout(true, "/nonasync");
    }

    public void testTimeoutListenerNoCompleteDispatch() throws Exception {
        // Should work
        doTestTimeout(false, "/nonasync");
    }


    private void doTestTimeout(boolean completeOnTimeout, String dispatchUrl)
    throws Exception {
        // Setup Tomcat instance
        Tomcat tomcat = getTomcatInstance();
        
        // Must have a real docBase - just use temp
        File docBase = new File(System.getProperty("java.io.tmpdir"));
        
        // Create the folder that will trigger the redirect
        File foo = new File(docBase, "async");
        if (!foo.exists() && !foo.mkdirs()) {
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

        ctx.addApplicationListener(TrackingRequestListener.class.getName());

        TesterAccessLogValve alv = new TesterAccessLogValve();
        ctx.getPipeline().addValve(alv);
        
        tomcat.start();
        ByteChunk res = new ByteChunk();
        try {
            getUrl("http://localhost:" + getPort() + "/async", res, null);
        } catch (IOException ioe) {
            // Ignore - expected for some error conditions
        }
        StringBuilder expected = new StringBuilder("requestInitialized-");
        expected.append("TimeoutServletGet-onTimeout-");
        if (completeOnTimeout) {
            if (dispatchUrl == null) {
                expected.append("onComplete-");
                expected.append("requestDestroyed");
            } else {
                // Error - no further output
                // There is no onComplete- since the complete event would be
                // fired during post processing but since there is an error that
                // never happens.
            }
        } else {
            if (dispatchUrl == null) {
                expected.append("onError-");
            } else {
                expected.append("NonAsyncServletGet-");
            }
            expected.append("onComplete-");
            expected.append("requestDestroyed");
        }
        assertEquals(expected.toString(), res.toString());

        // Check the access log
        if (completeOnTimeout && dispatchUrl != null) {
            if (!isAccessLogEnabled()) {
                validateAccessLog(alv, 1, 500, 0, TimeoutServlet.ASYNC_TIMEOUT +
                        TIMEOUT_MARGIN + REQUEST_TIME);
            }
        } else {
            validateAccessLog(alv, 1, 200, TimeoutServlet.ASYNC_TIMEOUT,
                    TimeoutServlet.ASYNC_TIMEOUT + TIMEOUT_MARGIN +
                    REQUEST_TIME);
        }
    }
    
    private static class TimeoutServlet extends HttpServlet {
        private static final long serialVersionUID = 1L;

        private boolean completeOnTimeout;
        private String dispatchUrl;

        public static final long ASYNC_TIMEOUT = 3000;

        public TimeoutServlet(boolean completeOnTimeout, String dispatchUrl) {
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
                
                ac.addListener(new TrackingListener(
                        false, completeOnTimeout, dispatchUrl));
            } else
                resp.getWriter().print("FAIL: Async unsupported");
        }
    }

    public void testDispatchSingle() throws Exception {
        doTestDispatch(1, false);
    }
    
    public void testDispatchDouble() throws Exception {
        doTestDispatch(2, false);
    }
    
    public void testDispatchMultiple() throws Exception {
        doTestDispatch(5, false);
    }
    
    public void testDispatchWithThreadSingle() throws Exception {
        doTestDispatch(1, true);
    }
    
    public void testDispatchWithThreadDouble() throws Exception {
        doTestDispatch(2, true);
    }
    
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

        ctx.addApplicationListener(TrackingRequestListener.class.getName());

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
        validateAccessLog(alv, 1, 200, 0, REQUEST_TIME);
    }
    
    private static class DispatchingServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;
        private static final String ITER_PARAM = "iter";
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
                        ctxt.dispatch("/stage1?" + ITER_PARAM + "=" + iter);
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

        TimeoutServlet timeout = new TimeoutServlet(true, null);
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
        validateAccessLog(alv, 1, 200, TimeoutServlet.ASYNC_TIMEOUT,
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
        
        private boolean completeOnError;
        private boolean completeOnTimeout;
        private String dispatchUrl;
        
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

    public void testDispatchErrorSingle() throws Exception {
        doTestDispatchError(1, false, false);
    }
    
    public void testDispatchErrorDouble() throws Exception {
        doTestDispatchError(2, false, false);
    }
    
    public void testDispatchErrorMultiple() throws Exception {
        doTestDispatchError(5, false, false);
    }
    
    public void testDispatchErrorWithThreadSingle() throws Exception {
        doTestDispatchError(1, true, false);
    }
    
    public void testDispatchErrorWithThreadDouble() throws Exception {
        doTestDispatchError(2, true, false);
    }
    
    public void testDispatchErrorWithThreadMultiple() throws Exception {
        doTestDispatchError(5, true, false);
    }
    
    public void testDispatchErrorSingleThenComplete() throws Exception {
        doTestDispatchError(1, false, true);
    }
    
    public void testDispatchErrorDoubleThenComplete() throws Exception {
        doTestDispatchError(2, false, true);
    }
    
    public void testDispatchErrorMultipleThenComplete() throws Exception {
        doTestDispatchError(5, false, true);
    }
    
    public void testDispatchErrorWithThreadSingleThenComplete()
            throws Exception {
        doTestDispatchError(1, true, true);
    }
    
    public void testDispatchErrorWithThreadDoubleThenComplete()
            throws Exception {
        doTestDispatchError(2, true, true);
    }
    
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

        ctx.addApplicationListener(TrackingRequestListener.class.getName());

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
        validateAccessLog(alv, 1, 200, 0, REQUEST_TIME);
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
            throw new ServletException("Opps.");
        }
    }
    
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
        validateAccessLog(alv, 1, 200, AsyncStartRunnable.THREAD_SLEEP_TIME,
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
        validateAccessLog(alv, 1, 200, Bug50753Servlet.THREAD_SLEEP_TIME,
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
        validateAccessLog(alv, 1, 500, 0, REQUEST_TIME);
    }

    private void validateAccessLog(TesterAccessLogValve alv, int count,
            int status, long minTime, long maxTime) throws Exception {
        List<Entry> entries = alv.getEntries();
        
        // Wait (but not too long) until all expected entries appear (access log
        // entry will be made after response has been returned to user)
        for (int i = 0; i < 10 && entries.size() < count; i++) {
            Thread.sleep(100);
        }
        
        assertEquals(count, entries.size());
        for (int j = 0; j < count; j++) {
            Entry entry = entries.get(j);
            assertEquals(status, entry.getStatus());
            assertTrue(entry.toString(),
                    entry.getTime() >= minTime - ERROR_MARGIN);
            assertTrue(entry.toString(),
                    entry.getTime() < maxTime + ERROR_MARGIN);
        }
    }
    
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
        validateAccessLog(alv, 1, HttpServletResponse.SC_BAD_REQUEST, 0,
                REQUEST_TIME);

    }

    private static class AsyncStatusServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        private int status = 200;

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
    

    public void testBug51197() throws Exception {
        // Setup Tomcat instance
        Tomcat tomcat = getTomcatInstance();
        
        // Must have a real docBase - just use temp
        File docBase = new File(System.getProperty("java.io.tmpdir"));
        
        Context ctx = tomcat.addContext("", docBase.getAbsolutePath());

        AsyncErrorServlet asyncErrorServlet =
            new AsyncErrorServlet(HttpServletResponse.SC_BAD_REQUEST);
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
        
        int rc = getUrl(url.toString(), new ByteChunk(), null);
        
        assertEquals(HttpServletResponse.SC_BAD_REQUEST, rc);
        
        // Without this test may complete before access log has a chance to log
        // the request
        Thread.sleep(REQUEST_TIME);
        
        // Check the access log
        validateAccessLog(alv, 1, HttpServletResponse.SC_BAD_REQUEST, TIMEOUT,
                TIMEOUT + TIMEOUT_MARGIN + REQUEST_TIME);

    }

    private static class AsyncErrorServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        private int status = 200;

        public AsyncErrorServlet(int status) {
            this.status = status;
        }
        
        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {
            
            final AsyncContext actxt = req.startAsync();
            actxt.setTimeout(TIMEOUT);
            actxt.start(new Runnable() {
                @Override
                public void run() {
                    try {
                        ((HttpServletResponse) actxt.getResponse()).sendError(
                                status);
                    } catch (IOException e) {
                        // Ignore
                    }
                }
            });
        }
    }

}
