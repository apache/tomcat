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

import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.Manager;
import org.apache.catalina.Session;
import org.apache.catalina.Store;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;

public class TestPersistentManager extends TomcatBaseTest {

    private final String ACTIVITY_CHECK = "org.apache.catalina.session.StandardSession.ACTIVITY_CHECK";

    private String oldActivityCheck;

    @Before
    public void setActivityCheck() {
        oldActivityCheck = System.setProperty(ACTIVITY_CHECK, "true");
    }

    @After
    public void resetActivityCheck() {
        if (oldActivityCheck != null) {
            System.setProperty(ACTIVITY_CHECK, oldActivityCheck);
        } else {
            System.clearProperty(ACTIVITY_CHECK);
        }
    }

    @Test
    public void backsUpOnce() throws IOException, LifecycleException {

        // Setup Tomcat instance
        Tomcat tomcat = getTomcatInstance();
        // Must have a real docBase - just use temp
        Context ctx = tomcat.addContext("",
                System.getProperty("java.io.tmpdir"));

        Tomcat.addServlet(ctx, "DummyServlet", new DummyServlet());
        ctx.addServletMapping("/dummy", "DummyServlet");

        PersistentManager manager = new PersistentManager();
        DummyStore store = new DummyStore();

        manager.setStore(store);
        manager.setMaxIdleBackup(0);
        manager.setDistributable(true);
        ctx.setManager(manager);
        tomcat.start();
        String sessionId = getUrl("http://localhost:" + getPort() + "/dummy")
                .toString();
        sleepABit();

        manager.processPersistenceChecks();
        Assert.assertEquals(Arrays.asList(sessionId), store.getSavedIds());

        // session was not accessed, so no save will be performed
        manager.processPersistenceChecks();
        Assert.assertEquals(Arrays.asList(sessionId), store.getSavedIds());

        // access session
        manager.findSession(sessionId).access();
        manager.findSession(sessionId).endAccess();
        sleepABit();

        // session was accessed, so it will be saved once again
        manager.processPersistenceChecks();
        Assert.assertEquals(Arrays.asList(sessionId, sessionId),
                store.getSavedIds());

        // session was not accessed, so once again no save will happen
        manager.processPersistenceChecks();
        Assert.assertEquals(Arrays.asList(sessionId, sessionId),
                store.getSavedIds());
    }

    private void sleepABit() {
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            // ignore Interrupt
        }
    }

    private static class DummyServlet extends HttpServlet {

        private static final long serialVersionUID = -3696433049266123995L;

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {
            resp.getWriter().print(req.getSession().getId());
        }

    }

    private static class DummyStore implements Store {

        private Manager manager;
        private List<String> savedIds = new ArrayList<>();

        List<String> getSavedIds() {
            return savedIds;
        }

        @Override
        public Manager getManager() {
            return this.manager;
        }

        @Override
        public void setManager(Manager manager) {
            this.manager = manager;
        }

        @Override
        public int getSize() throws IOException {
            return 0;
        }

        @Override
        public void addPropertyChangeListener(PropertyChangeListener listener) {
        }

        @Override
        public String[] keys() throws IOException {
            return null;
        }

        @Override
        public Session load(String id) throws ClassNotFoundException,
                IOException {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public void remove(String id) throws IOException {
        }

        @Override
        public void clear() throws IOException {
        }

        @Override
        public void removePropertyChangeListener(PropertyChangeListener listener) {
        }

        @Override
        public void save(Session session) throws IOException {
            savedIds.add(session.getId());
        }

    }
}