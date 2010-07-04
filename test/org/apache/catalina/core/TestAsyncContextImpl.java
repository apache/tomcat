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

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.Context;
import org.apache.catalina.Wrapper;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;

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
        getUrl("http://localhost:" + getPort() + "/");

        assertEquals("", servlet.getErrors());
    }
    
    private static class Bug49528Servlet extends HttpServlet {

        private static final long serialVersionUID = 1L;
        
        private StringBuilder errors = new StringBuilder();
        
        public String getErrors() {
            return errors.toString();
        }

        @Override
        protected void doGet(final HttpServletRequest req,
                final HttpServletResponse resp)
                throws ServletException, IOException {
            
            confirmFalse("1", req);
            req.startAsync();
            confirmTrue("2", req);
            
            req.getAsyncContext().start(new Runnable() {
                @Override
                public void run() {
                    try {
                        confirmTrue("3", req);
                        Thread.sleep(1000);
                        confirmTrue("4", req);
                        req.getAsyncContext().complete();
                        confirmFalse("5", req);
                    } catch (InterruptedException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                }
            });
            // Pointless method call so there is somewhere to put a break point
            // when debugging
            req.getMethod();
        }
        
        private void confirmFalse(String stage, HttpServletRequest req) {
            if (req.isAsyncStarted()) {
                errors.append("Stage " + stage +
                        ": Async started when not expected\n");
            }
        }

        private void confirmTrue(String stage, HttpServletRequest req) {
            if (!req.isAsyncStarted()) {
                errors.append("Stage " + stage +
                        ": Async not started when expected\n");
            }
        }

    }
}
