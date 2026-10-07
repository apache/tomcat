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

import java.io.File;
import java.io.FileOutputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.security.Principal;
import java.util.concurrent.atomic.AtomicInteger;

import javax.servlet.http.HttpSessionActivationListener;
import javax.servlet.http.HttpSessionEvent;
import javax.servlet.http.HttpSessionListener;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Session;
import org.apache.catalina.startup.ExpandWar;
import org.apache.tomcat.unittest.TesterContext;
import org.apache.tomcat.unittest.TesterHost;
import org.apache.tomcat.unittest.TesterServletContext;

public class TestStandardManager {

    private static final String VALID_ID = "validSessionId";
    private static final String INVALID_ID = "invalidSessionId";

    public static class TesterActivationAttribute implements HttpSessionActivationListener, Serializable {

        private static final long serialVersionUID = 1L;

        private boolean activated;

        @Override
        public void sessionDidActivate(HttpSessionEvent se) {
            activated = true;
        }

        @Override
        public void sessionWillPassivate(HttpSessionEvent se) {
            // NO-OP
        }

        public boolean wasActivated() {
            return activated;
        }
    }

    public static class TesterNonSerializableGraphPrincipal implements Principal, Serializable {

        private static final long serialVersionUID = 1L;

        private final String name;

        @SuppressWarnings("unused")
        private final Object notSerializable = new Object();

        public TesterNonSerializableGraphPrincipal(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }
    }


    /*
     * A session that was persisted as invalid (which can happen when a
     * session expires concurrently with unload()) must be discarded on load
     * without being activated and expired again, since that would emit a
     * sessionDestroyed event for a session that never emitted sessionCreated
     * in this JVM.
     */
    @Test
    public void testLoadDiscardsInvalidPersistedSession() throws Exception {
        File dir = new File("SESS_TEMP_STANDARD_LOAD");
        ExpandWar.delete(dir);
        Assert.assertTrue(dir.mkdirs());
        File file = new File(dir, "TEST_SESSIONS.ser");
        try {
            StandardManager manager = new StandardManager();

            AtomicInteger created = new AtomicInteger();
            AtomicInteger destroyed = new AtomicInteger();
            HttpSessionListener listener = new HttpSessionListener() {
                @Override
                public void sessionCreated(HttpSessionEvent se) {
                    created.incrementAndGet();
                }

                @Override
                public void sessionDestroyed(HttpSessionEvent se) {
                    destroyed.incrementAndGet();
                }
            };

            TesterContext context = new TesterContext() {
                @Override
                public Object[] getApplicationLifecycleListeners() {
                    return new Object[] { listener };
                }
            };
            context.setServletContext(new TesterServletContext());
            context.setParent(new TesterHost());
            manager.setContext(context);

            manager.setPathname(file.getAbsolutePath());
            manager.start();

            StandardSession valid = (StandardSession) manager.createSession(VALID_ID);
            TesterActivationAttribute activation = new TesterActivationAttribute();
            valid.setAttribute("activation", activation);

            // A session that was already invalid when it was persisted
            StandardSession invalid = manager.getNewSession();
            invalid.setManager(manager);
            invalid.id = INVALID_ID;

            try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(file))) {
                oos.writeObject(Integer.valueOf(2));
                valid.writeObjectData(oos);
                invalid.writeObjectData(oos);
            }

            // Session creation already reported the session created during
            // setup above. Reset the counters so only events emitted by load()
            // are observed.
            created.set(0);
            destroyed.set(0);

            manager.load();

            // Before the fix, the invalid session was activated and then
            // expired, emitting an unbalanced sessionDestroyed event
            Assert.assertEquals(0, destroyed.get());
            Assert.assertEquals(0, created.get());
            Assert.assertNull(manager.findSession(INVALID_ID));

            // Control: the valid session is loaded and activated as usual
            Assert.assertNotNull(manager.findSession(VALID_ID));
            TesterActivationAttribute loadedActivation = (TesterActivationAttribute) ((StandardSession) manager
                    .findSession(VALID_ID)).getAttribute("activation");
            Assert.assertTrue(loadedActivation.wasActivated());
        } finally {
            ExpandWar.delete(dir);
        }
    }

    /*
     * A principal whose own class is Serializable but whose object graph
     * contains a non-serializable element must not corrupt the persisted
     * session record. The principal is dropped instead, keeping the rest of
     * the session loadable.
     */
    @Test
    public void testUnloadDropsPrincipalWithNonSerializableGraph() throws Exception {
        File dir = new File("SESS_TEMP_STANDARD_PRINCIPAL");
        ExpandWar.delete(dir);
        Assert.assertTrue(dir.mkdirs());
        File file = new File(dir, "TEST_SESSIONS.ser");
        try {
            StandardManager manager = new StandardManager();

            TesterContext context = new TesterContext();
            context.setServletContext(new TesterServletContext());
            context.setParent(new TesterHost());
            manager.setContext(context);

            manager.setPathname(file.getAbsolutePath());
            manager.setPersistAuthentication(true);
            manager.start();

            Session session = manager.createSession(VALID_ID);
            session.setPrincipal(new TesterNonSerializableGraphPrincipal("testUser"));

            manager.unload();

            try {
                manager.load();
            } catch (Throwable t) {
                // Before the fix, the NotSerializableException was swallowed
                // mid-write, corrupting the stream, so loading the file failed
                Assert.fail("Loading the persisted session failed: " + t);
            }

            Session loaded = manager.findSession(VALID_ID);
            Assert.assertNotNull(loaded);
            Assert.assertNull(loaded.getPrincipal());
        } finally {
            ExpandWar.delete(dir);
        }
    }
}
