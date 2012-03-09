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
package org.apache.catalina.loader;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.buf.ByteChunk;
import org.junit.Test;

public class TestWebappClassLoaderThreadLocalMemoryLeak extends TomcatBaseTest {

    @Test
    public void testThreadLocalLeak() throws Exception {

        Tomcat tomcat = getTomcatInstance();

        // Must have a real docBase - just use temp
        Context ctx = tomcat.addContext("",
                System.getProperty("java.io.tmpdir"));

        Tomcat.addServlet(ctx, "leakServlet", new LeakingServlet());
        ctx.addServletMapping("/leak1", "leakServlet");

        Tomcat.addServlet(ctx, "leakServlet2", new LeakingServlet2());
        ctx.addServletMapping("/leak2", "leakServlet2");

        tomcat.start();

        // This will trigger the timer & thread creation
        ByteChunk chunk = getUrl("http://localhost:" + getPort() + "/leak1");
        System.out.print("First Threadlocal test response " + chunk.toString());

        chunk = getUrl("http://localhost:" + getPort() + "/leak2");
        System.out
                .print("Second Threadlocal test response " + chunk.toString());

        // Stop the context
        ctx.stop();

        // If the thread still exists, we have a thread/memory leak
        try {
            Thread.sleep(1000);
        } catch (InterruptedException ie) {
            // ignore
        }
    }

    class LeakingServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        private ThreadLocal<MyCounter> myThreadLocal = new ThreadLocal<MyCounter>();

        @Override
        protected void doGet(HttpServletRequest request,
                HttpServletResponse response) throws ServletException,
                IOException {

            MyCounter counter = myThreadLocal.get();
            if (counter == null) {
                counter = new MyCounter();
                myThreadLocal.set(counter);
            }

            response.getWriter().println(
                    "The current thread served this servlet "
                            + counter.getCount() + " times");
            counter.increment();
        }

        @Override
        public void destroy() {
            super.destroy();
            // normally not needed, just to make my point
            myThreadLocal = null;
        }
    }

    class LeakingServlet2 extends HttpServlet {

        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(HttpServletRequest request,
                HttpServletResponse response) throws ServletException,
                IOException {

            List<MyCounter> counterList = ThreadScopedHolder.getFromHolder();
            MyCounter counter;
            if (counterList == null) {
                counter = new MyCounter();
                ThreadScopedHolder.saveInHolder(Arrays.asList(counter));
            } else {
                counter = counterList.get(0);
            }

            response.getWriter().println(
                    "The current thread served this servlet "
                            + counter.getCount() + " times");
            counter.increment();
        }
    }

    static class ThreadScopedHolder {
        private final static ThreadLocal<List<MyCounter>> threadLocal =
                new ThreadLocal<List<MyCounter>>();

        public static void saveInHolder(List<MyCounter> o) {
            threadLocal.set(o);
        }

        public static List<MyCounter> getFromHolder() {
            return threadLocal.get();
        }
    }

    class MyCounter {
        private int count = 0;

        public void increment() {
            count++;
        }

        public int getCount() {
            return count;
        }
    }
}