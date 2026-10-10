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

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Session;
import org.apache.catalina.startup.ExpandWar;
import org.apache.tomcat.unittest.TesterContext;
import org.apache.tomcat.unittest.TesterHost;
import org.apache.tomcat.unittest.TesterServletContext;

public class TestPersistentManagerBase {

    private static final String SESSION_ID = "testerSessionId";

    /*
     * Removing a stale copy of a session must not evict a different session
     * instance with the same ID from the manager.
     */
    @Test
    public void testRemoveStaleCopyDoesNotEvictLiveSession() throws Exception {
        File dir = new File("SESS_TEMP_STALE_REMOVE");
        ExpandWar.delete(dir);
        try {
            PersistentManager manager = new PersistentManager();

            FileStore store = new FileStore();
            store.setDirectory(dir.getAbsolutePath());
            manager.setStore(store);

            TesterContext context = new TesterContext();
            context.setServletContext(new TesterServletContext());
            context.setParent(new TesterHost());
            manager.setContext(context);

            manager.start();

            Session live = manager.createSession(SESSION_ID);

            // A different instance carrying the same session ID, not
            // registered with the manager, as produced by loading a copy from
            // the store before it is swapped in
            StandardSession stale = new StandardSession(manager);
            stale.id = SESSION_ID;

            manager.remove(stale, false);

            // Before the fix, the removal was based on the session ID alone
            // and removed the live session from the manager
            Session current = manager.findSession(SESSION_ID);
            Assert.assertSame(live, current);
        } finally {
            ExpandWar.delete(dir);
        }
    }

    /*
     * Control for the test above: removing the instance that is in the map
     * does remove the session.
     */
    @Test
    public void testRemoveLiveInstanceRemovesSession() throws Exception {
        File dir = new File("SESS_TEMP_LIVE_REMOVE");
        ExpandWar.delete(dir);
        try {
            PersistentManager manager = new PersistentManager();

            FileStore store = new FileStore();
            store.setDirectory(dir.getAbsolutePath());
            manager.setStore(store);

            TesterContext context = new TesterContext();
            context.setServletContext(new TesterServletContext());
            context.setParent(new TesterHost());
            manager.setContext(context);

            manager.start();

            Session session = manager.createSession(SESSION_ID);
            manager.remove(session, false);

            Assert.assertNull(manager.findSession(SESSION_ID));
        } finally {
            ExpandWar.delete(dir);
        }
    }
}
