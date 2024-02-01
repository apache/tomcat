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
package org.apache.catalina.core;

import java.lang.reflect.InvocationTargetException;

import javax.naming.NamingException;

import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.servlets.DefaultServlet;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.InstanceManager;

/*
 * This is an absolute performance test. There is no benefit it running it as part of a standard test run so it is
 * excluded due to the name starting Tester...
 */
public class TesterDefaultInstanceManagerPerformance extends TomcatBaseTest {

    @Test
    public void testConcurrency() throws Exception {
        // Create a populated InstanceManager
        Tomcat tomcat = getTomcatInstance();
        Context ctx = tomcat.addContext(null, "", null);

        tomcat.start();

        InstanceManager im = ctx.getInstanceManager();

        for (int i = 1; i < 9; i++) {
            doTestConcurrency(im, i);
        }
    }


    private void doTestConcurrency(InstanceManager im, int threadCount) throws Exception {
        long start = System.nanoTime();

        Thread[] threads = new Thread[threadCount];

        for (int i = 0; i < threadCount; i++) {
            threads[i] = new Thread(new InstanceManagerRunnable(im));
        }

        for (int i = 0; i < threadCount; i++) {
            threads[i].start();
        }

        for (int i = 0; i < threadCount; i++) {
            threads[i].join();
        }

        long duration = System.nanoTime() - start;

        System.out.println(threadCount + " threads completed in " + duration + "ns");
    }


    private static class InstanceManagerRunnable implements Runnable {

        private final InstanceManager im;

        private InstanceManagerRunnable(InstanceManager im) {
            this.im = im;
        }

        @Override
        public void run() {
            try {
                Object test = new DefaultServlet();
                for (int i = 0; i < 200000; i++) {
                    im.newInstance(test);
                    im.destroyInstance(test);
                }
            } catch (NamingException | IllegalAccessException | InvocationTargetException ne) {
                ne.printStackTrace();
            }
        }
    }
}
