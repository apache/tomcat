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

import javax.servlet.AsyncContext;
import javax.servlet.AsyncEvent;
import javax.servlet.AsyncListener;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.Context;
import org.apache.catalina.Wrapper;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.buf.ByteChunk;

public class TestAsyncContextImpl extends TomcatBaseTest {

    public void testBug49528() throws Exception {
        // Setup Tomcat instance
        Tomcat tomcat = getTomcatInstance();
        
        // Must have a real docBase - just use temp
        Context ctx = 
            tomcat.addContext("/", System.getProperty("java.io.tmpdir"));

        Bug49528Servlet servlet = new Bug49528Servlet();
        
        Wrapper wrapper = Tomcat.addServlet(ctx, "servlet", servlet);
        wrapper.setAsyncSupported(true);
        ctx.addServletMapping("/", "servlet");
        
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
    }
    
    public void testBug49567() throws Exception {
        // Setup Tomcat instance
        Tomcat tomcat = getTomcatInstance();
        
        // Must have a real docBase - just use temp
        Context ctx = 
            tomcat.addContext("/", System.getProperty("java.io.tmpdir"));

        Bug49567Servlet servlet = new Bug49567Servlet();
        
        Wrapper wrapper = Tomcat.addServlet(ctx, "servlet", servlet);
        wrapper.setAsyncSupported(true);
        ctx.addServletMapping("/", "servlet");
        
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
    }
    
    public void testAsyncStartNoComplete() throws Exception {
        // Setup Tomcat instance
        Tomcat tomcat = getTomcatInstance();
        
        // Minimise pauses during test
        tomcat.getConnector().setAttribute(
                "connectionTimeout", Integer.valueOf(3000));
        
        // Must have a real docBase - just use temp
        Context ctx = 
            tomcat.addContext("/", System.getProperty("java.io.tmpdir"));

        AsyncStartNoCompleteServlet servlet =
            new AsyncStartNoCompleteServlet();
        
        Wrapper wrapper = Tomcat.addServlet(ctx, "servlet", servlet);
        wrapper.setAsyncSupported(true);
        ctx.addServletMapping("/", "servlet");
        
        tomcat.start();
        
        // Call the servlet the first time
        ByteChunk bc1 = getUrl("http://localhost:" + getPort() +
                "/?echo=run1");
        assertEquals("OK-run1", bc1.toString());

        // Call the servlet the second time with a request parameter
        ByteChunk bc2 = getUrl("http://localhost:" + getPort() +
                "/?echo=run2");
        assertEquals("OK-run2", bc2.toString());
    }
    
    public void testAsyncStartWithComplete() throws Exception {
        // Setup Tomcat instance
        Tomcat tomcat = getTomcatInstance();
        
        // Must have a real docBase - just use temp
        Context ctx = 
            tomcat.addContext("/", System.getProperty("java.io.tmpdir"));

        AsyncStartWithCompleteServlet servlet =
            new AsyncStartWithCompleteServlet();
        
        Wrapper wrapper = Tomcat.addServlet(ctx, "servlet", servlet);
        wrapper.setAsyncSupported(true);
        ctx.addServletMapping("/", "servlet");
        
        tomcat.start();
        
        // Call the servlet once
        ByteChunk bc = getUrl("http://localhost:" + getPort() + "/");
        assertEquals("OK", bc.toString());
    }
    
    /*
     * NOTE: This servlet is only intended to be used in single-threaded tests.
     */
    private static class Bug49528Servlet extends HttpServlet {

        private static final long serialVersionUID = 1L;
        
        private volatile boolean done = false;
        
        private StringBuilder result;
        
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
            req.startAsync();
            result.append('2');
            result.append(req.isAsyncStarted());
            
            req.getAsyncContext().start(new Runnable() {
                @Override
                public void run() {
                    try {
                        result.append('3');
                        result.append(req.isAsyncStarted());
                        Thread.sleep(1000);
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
                                Thread.sleep(1000);
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
            actxt.setTimeout(1000);
        }
    }

    private static class AsyncStartWithCompleteServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;
        
        @Override
        protected void doGet(final HttpServletRequest req,
                final HttpServletResponse resp)
                throws ServletException, IOException {
            
            AsyncContext actxt = req.startAsync();
            resp.setContentType("text/plain");
            resp.getWriter().print("OK");
            actxt.complete();
        }
    }

    public void testTimeout() throws Exception {
        // Setup Tomcat instance
        Tomcat tomcat = getTomcatInstance();
        
        // Must have a real docBase - just use temp
        File docBase = new File(System.getProperty("java.io.tmpdir"));
        
        // Create the folder that will trigger the redirect
        File foo = new File(docBase, "async");
        if (!foo.exists() && !foo.mkdirs()) {
            fail("Unable to create async directory in docBase");
        }
        
        Context ctx = tomcat.addContext("/", docBase.getAbsolutePath());

        TimeoutServlet timeout = new TimeoutServlet();

        Wrapper wrapper = Tomcat.addServlet(ctx, "time", timeout);
        wrapper.setAsyncSupported(true);
        ctx.addServletMapping("/async", "time");

        tomcat.start();
        ByteChunk res = getUrl("http://localhost:" + getPort() + "/async");
        assertEquals("OK", res.toString());
    }
    
    private static class TimeoutServlet extends HttpServlet {
        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) 
                throws ServletException, IOException {
            if (req.isAsyncSupported()) {
                resp.getWriter().print("OK");
                final AsyncContext ac = req.startAsync();
                ac.setTimeout(2000);
                
                ac.addListener(new TimeoutListener());
            } else
                resp.getWriter().print("FAIL: Async unsupported");
        }
    }

    private static class TimeoutListener implements AsyncListener {

        @Override
        public void onComplete(AsyncEvent event) throws IOException {
            // NO-OP
        }

        @Override
        public void onTimeout(AsyncEvent event) throws IOException {
            event.getAsyncContext().complete();
        }

        @Override
        public void onError(AsyncEvent event) throws IOException {
            // NOOP
        }

        @Override
        public void onStartAsync(AsyncEvent event) throws IOException {
            // NOOP
        }
        
    }
}
