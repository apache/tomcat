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
package org.apache.catalina.ha.context;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.Host;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.core.StandardHost;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.buf.ByteChunk;

public class TestReplicatedContext extends TomcatBaseTest {

    @Test
    public void testGetServletContextReturnsSameInstanceUnderConcurrency() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        Host host = tomcat.getHost();
        if (host instanceof StandardHost) {
            ((StandardHost) host).setContextClass(ReplicatedContext.class.getName());
        }

        File root = new File("test/webapp");
        ReplicatedContext replicatedContext =
                (ReplicatedContext) tomcat.addWebapp(host, "", root.getAbsolutePath());
        tomcat.start();

        // Null the context field to simulate the window during reload
        replicatedContext.context = null;

        int numThreads = 20;
        CyclicBarrier barrier = new CyclicBarrier(numThreads);
        List<Thread> threads = new ArrayList<>();
        ServletContext[] results = new ServletContext[numThreads];
        AtomicReference<Throwable> failure = new AtomicReference<>();

        for (int i = 0; i < numThreads; i++) {
            final int index = i;
            Thread thread = new Thread(() -> {
                try {
                    barrier.await();
                    results[index] = replicatedContext.getServletContext();
                } catch (Throwable ex) {
                    failure.set(ex);
                }
            });
            thread.start();
            threads.add(thread);
        }

        for (Thread thread : threads) {
            thread.join(5000);
        }

        if (failure.get() != null) {
            Assert.fail("Thread failed: " + failure.get());
        }

        ServletContext first = results[0];
        Assert.assertNotNull(first);
        for (int i = 1; i < numThreads; i++) {
            Assert.assertSame(first, results[i]);
        }
    }


    @Test
    public void testBug57425() throws LifecycleException, IOException {
        Tomcat tomcat = getTomcatInstance();
        Host host = tomcat.getHost();
        if (host instanceof StandardHost) {
            ((StandardHost) host).setContextClass(ReplicatedContext.class.getName());
        }

        File root = new File("test/webapp");
        Context context = tomcat.addWebapp(host, "", root.getAbsolutePath());

        Tomcat.addServlet(context, "test", new AccessContextServlet());
        context.addServletMappingDecoded("/access", "test");

        tomcat.start();

        ByteChunk result = getUrl("http://localhost:" + getPort() + "/access");

        Assert.assertEquals("OK", result.toString());

    }

    private static class AccessContextServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            getServletContext().setAttribute("NULL", null);
            resp.getWriter().print("OK");
        }
    }
}
