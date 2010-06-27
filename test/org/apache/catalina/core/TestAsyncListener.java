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
import javax.servlet.AsyncListener;
import javax.servlet.AsyncEvent;
import javax.servlet.ServletException;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.Context;
import org.apache.catalina.Wrapper;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.buf.ByteChunk;

/**
 * Check Servlet 3.0 AsyncListener.
 * 
 * @author Peter Rossbach
 * @version $Revision$
 */
public class TestAsyncListener extends TomcatBaseTest {

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
        Thread.sleep(4000);
        assertEquals(1,timeout.getAsyncTimeout());
        //assertEquals(1,timeout.getAsyncStart());
        assertEquals(1,timeout.getAsyncComplete());
        //assertEquals("hello start: " + timeout.getStart() + "\n", res.toString());
        assertNull(res.toString());
    }
    
    private static class TimeoutServlet extends HttpServlet {
        private static final long serialVersionUID = 1L;

        private volatile int asyncStart = 0;
        private volatile int asyncComplete = 0;
        private volatile int asyncTimeout = 0;
        private volatile int asyncError = 0;

        private volatile long start;

        private int getAsyncTimeout() {
            return asyncTimeout;
        }
        
        
        private int getAsyncStart() {
            return asyncStart;
        }


        private int getAsyncComplete() {
            return asyncComplete;
        }


        private int getAsyncError() {
            return asyncError;
        }

        private long getStart() {
            return start;
        }

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) 
                throws ServletException, IOException {
            if (req.isAsyncSupported()) {
                
                final AsyncContext ac = req.startAsync();
                ac.setTimeout(2000);
                AsyncListener listener = new AsyncListener() {
                    
                    @Override
                    public void onTimeout(AsyncEvent event) throws IOException {
                        asyncTimeout++;
                        log("AsyncLog onTimeout:" + System.currentTimeMillis());
                    }
                    
                    @Override
                    public void onStartAsync(AsyncEvent event) throws IOException {
                        asyncStart++;
                        log("AsyncLog onStartAsync:" + System.currentTimeMillis());                       
                    }
                    
                    @Override
                    public void onError(AsyncEvent event) throws IOException {
                        asyncError++;
                        log("AsyncLog onError:" + System.currentTimeMillis());
                    }
                    
                    @Override
                    public void onComplete(AsyncEvent event) throws IOException {
                        asyncComplete++;
                        log("AsyncLog complete:" + System.currentTimeMillis());
                    }
                };
                
                ac.addListener(listener);
                start = System.currentTimeMillis();
                Runnable run = new Runnable() {

                    @Override
                    public void run() {

                        try {
                            Thread.sleep(5 * 1000);
                                                       
                            ServletResponse response = ac.getResponse();
                            if (response != null && !response.isCommitted()) {
                                response.getWriter().println(
                                    "hello start: " + start );
                                response.flushBuffer();
                                ac.complete();                            
                            } else {
                                log("AsyncContext is completed!") ;
                            }
                        } catch (InterruptedException e) {
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                        } catch (IOException io) {
                        }
                    }

                };
                // FIXME: Servlet 3.0 Spec say: Container must start a thread!
                //ac.start(run);
                // currently (21.06.2010) we don't do it! 
                Thread thread = new Thread(run);
                thread.start();
            } else
                resp.getWriter().println("async unsupported");
        }
    }
}
