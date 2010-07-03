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

import javax.servlet.ServletException;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.Context;
import org.apache.catalina.Wrapper;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;

/**
 * Simulate Bug 49528.
 * 
 * @author Peter Rossbach
 * @version $Revision$
 */
public class TestAsyncContextImpl  extends TomcatBaseTest {

    public void testIsAsyncStarted() throws Exception {
        // Setup Tomcat instance
        Tomcat tomcat = getTomcatInstance();        

        BUG49528Servlet servlet = createTestApp(tomcat);
        tomcat.start();
        getUrl("http://localhost:" + getPort() + "/async");
        // currently the bug show that we don't start a thread. All message log the same thread id!
        assertTrue(servlet.asyncStartBeforeDispatched);
        assertTrue(servlet.asyncStartRun);
        assertFalse(servlet.asyncStartAfterComplete);
    }
    
    private BUG49528Servlet createTestApp(Tomcat tomcat) {
        // Must have a real docBase - just use temp
        File docBase = new File(System.getProperty("java.io.tmpdir"));
        
        // Create the folder that will trigger the redirect
        File foo = new File(docBase, "async");
        if (!foo.exists() && !foo.mkdirs()) {
            fail("Unable to create async directory in docBase");
        }
        
        Context ctx = tomcat.addContext("/", docBase.getAbsolutePath());

        BUG49528Servlet servlet = new BUG49528Servlet();
        Wrapper wrapper = Tomcat.addServlet(ctx, "test", servlet);
        wrapper.setAsyncSupported(true);
        ctx.addServletMapping("/async", "test");
        return servlet;
    }
    
    // see BUG 49528 report TestCase servlet
    private static class BUG49528Servlet extends HttpServlet {

        private static final long serialVersionUID = 1L;
 
        protected volatile boolean asyncStartBeforeDispatched = false;
        
        protected volatile boolean asyncStartRun = false;
        
        protected volatile boolean asyncStartAfterComplete = true;

        @Override
        protected void doGet(final HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {

            long rtid = Thread.currentThread().getId();
            log(rtid + " Start async()");
            request.startAsync();
            log(rtid + " Dispatching start()");
            log(rtid + " request.isAsyncStarted()1" + request.isAsyncStarted());
            asyncStartBeforeDispatched = request.isAsyncStarted();
            request.getAsyncContext().start(new Runnable() {
                @Override
                public void run() {
                    try {
                        long tid = Thread.currentThread().getId();
                        asyncStartRun = request.isAsyncStarted();
                        log(tid + " request.isAsyncStarted()2" + request.isAsyncStarted());
                        log(tid + " Before sleep()");
                        Thread.sleep(500);
                        log(tid + " After sleep()");
                        log(tid + " request.isAsyncStarted()3" + request.isAsyncStarted());
                        request.getAsyncContext().complete();
                        asyncStartAfterComplete = request.isAsyncStarted();
                        log(tid + " Returning from run()");
                        log(tid + " request.isAsyncStarted()4" + request.isAsyncStarted());
                    } catch (InterruptedException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                }
            });
            log(rtid + " Returning from doGet()");
        }
        
    }

}
