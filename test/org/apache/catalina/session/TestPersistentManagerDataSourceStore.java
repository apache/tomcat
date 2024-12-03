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
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.Session;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;

public class TestPersistentManagerDataSourceStore extends TomcatBaseTest {

    public static final String SIMPLE_SCHEMA =
            "create table tomcatsessions (\n"
            + "  id                varchar(32) not null primary key,\n"
            + "  app               varchar(32) not null,\n"
            + "  data              blob(2048) not null,\n"
            + "  valid             varchar(1) not null,\n"
            + "  maxinactive       int not null,\n"
            + "  lastaccess        double not null\n"
            + ")";

    /**
     * Wait enough for the system clock to update its value. On some systems
     * (e.g. old Windows) the clock granularity is tens of milliseconds.
     */
    private void waitForClockUpdate() throws InterruptedException {
        long startTime = System.currentTimeMillis();
        int waitTime = 1;
        do {
            Thread.sleep(waitTime);
            waitTime *= 10;
        } while (System.currentTimeMillis() == startTime);
    }

    /**
     * Wait while session access counter has a positive value.
     */
    private void waitWhileSessionIsActive(StandardSession session)
            throws InterruptedException {
        long maxWaitTime = System.currentTimeMillis() + 60000;
        AtomicInteger accessCount = session.accessCount;
        while (accessCount.get() > 0) {
            // Wait until o.a.c.connector.Request.recycle() completes,
            // as it updates lastAccessedTime.
            Assert.assertTrue(System.currentTimeMillis() < maxWaitTime);
            Thread.sleep(200);
        }
    }

    @Test
    public void testDSStore() throws Exception {
        // Setup Tomcat instance
        Tomcat tomcat = getTomcatInstance();

        // No file system docBase required
        Context ctx = getProgrammaticRootContext();
        ctx.setDistributable(true);

        Tomcat.addServlet(ctx, "DummyServlet", new DummyServlet());
        ctx.addServletMappingDecoded("/dummy", "DummyServlet");

        PersistentManager manager = new PersistentManager();
        DerbyDataSourceStore store = new DerbyDataSourceStore("basictest");
        store.setSessionTable("tomcatsessions");

        manager.setStore(store);
        manager.setMaxIdleBackup(0);
        manager.setSessionActivityCheck(true);
        ctx.setManager(manager);
        tomcat.start();
        String sessionId = getUrl("http://localhost:" + getPort() + "/dummy")
                .toString();

        // Note: PersistenceManager.findSession() silently updates
        // session.lastAccessedTime, so call it only once before other work.
        Session session = manager.findSession(sessionId);

        // Wait until request processing ends, as Request.recycle() updates
        // session.lastAccessedTime via session.endAccess().
        waitWhileSessionIsActive((StandardSession) session);

        long lastAccessedTime = session.getLastAccessedTimeInternal();

        // Session should be idle at least for 0 second (maxIdleBackup)
        // to be eligible for persistence, thus no need to wait.

        // Waiting a bit, to catch changes in last accessed time of a session
        waitForClockUpdate();

        manager.processPersistenceChecks();
        Assert.assertTrue(store.getSize() == 1);
        Assert.assertEquals(sessionId, store.keys()[0]);
        Assert.assertEquals(lastAccessedTime, session.getLastAccessedTimeInternal());

        // session was not accessed, so no save will be performed
        waitForClockUpdate();
        manager.processPersistenceChecks();
        Assert.assertEquals(sessionId, store.keys()[0]);
        Assert.assertEquals(lastAccessedTime, session.getLastAccessedTimeInternal());

        // access session
        session.access();
        session.endAccess();

        // session was accessed, so it will be saved once again
        manager.processPersistenceChecks();
        Assert.assertEquals(sessionId, store.keys()[0]);

        Session session2 = store.load(sessionId);
        Assert.assertEquals(sessionId, session2.getId());

        session.expire();
        Assert.assertTrue(store.getSize() == 0);
        store.clear();
    }

    private static class DummyServlet extends HttpServlet {

        private static final long serialVersionUID = -3696433049266123995L;

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {
            boolean createSession = !Boolean.parseBoolean(req
                            .getParameter("no_create_session"));
            HttpSession session = req.getSession(createSession);
            if (session == null) {
                resp.getWriter().print("NO_SESSION");
            } else {
                String id = session.getId();
                resp.getWriter().print(id);
            }
        }

    }

    protected class DerbyDataSourceStore extends DataSourceStore {
        protected final String name;
        protected Connection connection = null;
        public DerbyDataSourceStore(String name) {
            this.name = "/store-" + name;
        }
        @Override
        protected Connection open() {
            // Replace DataSource use and JNDI access with direct Derby
            // connection
            if (connection == null) {
                try {
                    Class.forName("org.apache.derby.jdbc.EmbeddedDriver");
                    connection = DriverManager.getConnection("jdbc:derby:" + getTemporaryDirectory().getAbsolutePath()
                            + name + ";create=true");
                    try (Statement statement = connection.createStatement()) {
                        statement.execute(SIMPLE_SCHEMA);
                    }
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }
            return connection;
        }
        @Override
        protected void close(Connection dbConnection) {
            // Only one connection so don't close it here
        }
        @Override
        public void stopInternal() throws LifecycleException {
            super.stopInternal();
            if (connection != null) {
                super.close(connection);
            }
        }
    }
}
