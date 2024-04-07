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
package org.apache.catalina.session;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpSession.Accessor;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.Session;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;

public class TestStandardSessionAccessor extends TomcatBaseTest {

    @Test
    public void testLastAccess() throws Exception {
        // Setup Tomcat instance
        Tomcat tomcat = getTomcatInstance();

        // No file system docBase required
        Context ctx = getProgrammaticRootContext();

        CountDownLatch latch = new CountDownLatch(1);

        Tomcat.addServlet(ctx, "accessor", new AccessorServlet(latch));
        ctx.addServletMappingDecoded("/accessor", "accessor");

        tomcat.start();

        int rc = getUrl("http://localhost:" + getPort() + "/accessor", null, null);
        Assert.assertEquals(HttpServletResponse.SC_OK, rc);

        // There should be a single session in the Manager at this point
        Session[] sessions = ctx.getManager().findSessions();
        Assert.assertNotNull(sessions);
        Assert.assertEquals(1, sessions.length);

        Session session = sessions[0];

        // Check the current last accessed time
        long lastAccessedA = session.getLastAccessedTime();

        // Wait 1 second
        Thread.sleep(1000);

        // Release the latch
        latch.countDown();

        // The last accessed time should have increased - allow up to 10 seconds for that to happen
        int count = 0;
        long lastAccessedB = session.getLastAccessedTime();
        while (lastAccessedB == lastAccessedA && count < 200) {
            Thread.sleep(50);
            lastAccessedB = session.getLastAccessedTime();
            count++;
        }

        Assert.assertTrue("Session last access time not updated", lastAccessedB > lastAccessedA);
    }


    public static class AccessorServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        private final CountDownLatch latch;

        public AccessorServlet(CountDownLatch latch) {
            this.latch = latch;
        }

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            HttpSession httpSession = req.getSession();
            Accessor acessor = httpSession.getAccessor();

            Thread t = new Thread(new AccessorRunnable(acessor, latch));
            t.start();
        }
    }


    public static class AccessorRunnable implements Runnable {

        private final Accessor accessor;
        private final CountDownLatch latch;

        public AccessorRunnable(Accessor accessor, CountDownLatch latch) {
            this.accessor = accessor;
            this.latch = latch;
        }


        @Override
        public void run() {
            try {
                latch.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            accessor.access(new SessionUpdater());
        }
    }


    public static class SessionUpdater implements Consumer<HttpSession> {

        @Override
        public void accept(HttpSession httpSession) {
            // NO-OP - process of accessing the session will update the last accessed time.
        }
    }
}
