/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.catalina.realm;

import java.security.Principal;
import java.util.HashSet;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.Realm;
import org.apache.catalina.startup.TesterMapRealm;
import org.apache.tomcat.unittest.TesterContext;

public class TestCombinedRealm {

    private static final String USER_NAME = "user";
    private static final String PASSWORD = "password";


    /**
     * A nested Realm that fails to start. It also tracks whether it is used for authentication so tests can confirm
     * that a Realm that failed to start is not used.
     */
    private static final class FailToStartRealm extends RealmBase {

        private volatile boolean authenticateCalled = false;

        @Override
        protected void startInternal() throws LifecycleException {
            throw new LifecycleException("Deliberate failure to start");
        }

        @Override
        public Principal authenticate(String username, String credentials) {
            authenticateCalled = true;
            return null;
        }

        private boolean isAuthenticateCalled() {
            return authenticateCalled;
        }

        @Override
        protected String getPassword(String username) {
            return null;
        }

        @Override
        protected Principal getPrincipal(String username) {
            return null;
        }
    }


    /**
     * A nested Realm that authenticates a single user and counts the calls made to stop() and destroy() so tests can
     * confirm a Realm is cleaned up exactly once.
     */
    private static final class CountingRealm extends RealmBase {

        private final String username;
        private final String password;
        private int stopCount = 0;
        private int destroyCount = 0;

        private CountingRealm(String username, String password) {
            this.username = username;
            this.password = password;
            setCredentialHandler(new MessageDigestCredentialHandler());
        }

        @Override
        protected void stopInternal() throws LifecycleException {
            stopCount++;
            super.stopInternal();
        }

        @Override
        protected void destroyInternal() throws LifecycleException {
            destroyCount++;
            super.destroyInternal();
        }

        private int getStopCount() {
            return stopCount;
        }

        private int getDestroyCount() {
            return destroyCount;
        }

        @Override
        protected String getPassword(String username) {
            if (this.username.equals(username)) {
                return password;
            }
            return null;
        }

        @Override
        protected Principal getPrincipal(String username) {
            if (this.username.equals(username)) {
                return new GenericPrincipal(username, null, null);
            }
            return null;
        }
    }


    private static TesterMapRealm createNestedRealm() {
        TesterMapRealm realm = new TesterMapRealm();
        realm.setCredentialHandler(new MessageDigestCredentialHandler());
        realm.addUser(USER_NAME, PASSWORD);
        return realm;
    }


    private static CombinedRealm createCombinedRealm(Realm... nestedRealms) {
        Context context = new TesterContext();
        CombinedRealm combinedRealm = new CombinedRealm();
        combinedRealm.setContainer(context);
        for (Realm nestedRealm : nestedRealms) {
            combinedRealm.addRealm(nestedRealm);
        }
        return combinedRealm;
    }


    /*
     * A nested Realm that fails to start must be removed from the combined Realm since it cannot be used to
     * authenticate users. The combined Realm must still start.
     */
    @Test
    public void testNestedRealmFailsToStart() throws Exception {
        TesterMapRealm good = createNestedRealm();
        FailToStartRealm bad = new FailToStartRealm();

        CombinedRealm combinedRealm = createCombinedRealm(good, bad);
        Assert.assertEquals(2, combinedRealm.getNestedRealms().length);

        combinedRealm.start();

        // The combined Realm must not fail because one nested Realm failed
        Assert.assertEquals(LifecycleState.STARTED, combinedRealm.getState());

        // The Realm that failed to start must have been removed
        Realm[] nestedRealms = combinedRealm.getNestedRealms();
        Assert.assertEquals(1, nestedRealms.length);
        Assert.assertSame(good, nestedRealms[0]);

        // The Realm that failed to start must have been cleaned up
        Assert.assertEquals(LifecycleState.DESTROYED, bad.getState());
    }


    /*
     * A nested Realm that fails to start must not be used to authenticate users. The Realm that fails to start is added
     * first so that it would be the first Realm used for authentication if it had not been removed.
     */
    @Test
    public void testNestedRealmThatFailsToStartIsNotUsed() throws Exception {
        FailToStartRealm bad = new FailToStartRealm();
        TesterMapRealm good = createNestedRealm();

        CombinedRealm combinedRealm = createCombinedRealm(bad, good);
        combinedRealm.start();

        // Authentication must still work via the Realm that started
        Assert.assertNotNull(combinedRealm.authenticate(USER_NAME, PASSWORD));
        Assert.assertNull(combinedRealm.authenticate(USER_NAME, "wrong"));

        Assert.assertFalse(bad.isAuthenticateCalled());
    }


    /*
     * If every nested Realm fails to start, the combined Realm must still start but must not authenticate any user.
     */
    @Test
    public void testAllNestedRealmsFailToStart() throws Exception {
        FailToStartRealm bad = new FailToStartRealm();

        CombinedRealm combinedRealm = createCombinedRealm(bad);
        combinedRealm.start();

        Assert.assertEquals(LifecycleState.STARTED, combinedRealm.getState());
        Assert.assertEquals(0, combinedRealm.getNestedRealms().length);
        Assert.assertNull(combinedRealm.authenticate(USER_NAME, PASSWORD));
        Assert.assertFalse(bad.isAuthenticateCalled());
    }


    /*
     * A Realm removed while the combined Realm is running stops being used for authentication immediately but must not
     * be destroyed until the combined Realm is destroyed since it may be in use by a concurrent authenticate() call.
     */
    @Test
    public void testRemoveRealmWhileRunningDefersCleanUp() throws Exception {
        CountingRealm retained = new CountingRealm("retained", PASSWORD);
        CountingRealm removed = new CountingRealm("removed", PASSWORD);

        CombinedRealm combinedRealm = createCombinedRealm(retained, removed);
        combinedRealm.start();
        Assert.assertNotNull(combinedRealm.authenticate("removed", PASSWORD));

        Assert.assertTrue(combinedRealm.removeRealm(removed));

        // No longer used for authentication
        Assert.assertEquals(1, combinedRealm.getNestedRealms().length);
        Assert.assertSame(retained, combinedRealm.getNestedRealms()[0]);
        Assert.assertNull(combinedRealm.authenticate("removed", PASSWORD));
        Assert.assertNotNull(combinedRealm.authenticate("retained", PASSWORD));

        // Clean-up must have been deferred
        Assert.assertEquals(LifecycleState.STARTED, removed.getState());
        Assert.assertEquals(0, removed.getStopCount());
        Assert.assertEquals(0, removed.getDestroyCount());

        // Stopping the combined Realm stops the Realms it still contains and those that were removed
        combinedRealm.stop();
        Assert.assertEquals(LifecycleState.STOPPED, retained.getState());
        Assert.assertEquals(LifecycleState.STOPPED, removed.getState());

        // Destroying the combined Realm must clean up both, each exactly once
        combinedRealm.destroy();
        Assert.assertEquals(LifecycleState.DESTROYED, retained.getState());
        Assert.assertEquals(LifecycleState.DESTROYED, removed.getState());
        Assert.assertEquals(1, retained.getStopCount());
        Assert.assertEquals(1, retained.getDestroyCount());
        Assert.assertEquals(1, removed.getStopCount());
        Assert.assertEquals(1, removed.getDestroyCount());
    }


    /*
     * A Realm removed when the combined Realm is not running can be cleaned up immediately since it cannot be in use.
     */
    @Test
    public void testRemoveRealmWhileStoppedCleansUpImmediately() throws Exception {
        CountingRealm removed = new CountingRealm("removed", PASSWORD);

        CombinedRealm combinedRealm = createCombinedRealm(removed);
        combinedRealm.start();
        combinedRealm.stop();
        Assert.assertEquals(LifecycleState.STOPPED, removed.getState());

        Assert.assertTrue(combinedRealm.removeRealm(removed));

        // Clean-up must not have been deferred
        Assert.assertEquals(LifecycleState.DESTROYED, removed.getState());
        Assert.assertEquals(1, removed.getStopCount());
        Assert.assertEquals(1, removed.getDestroyCount());

        combinedRealm.destroy();

        // Still exactly once
        Assert.assertEquals(1, removed.getStopCount());
        Assert.assertEquals(1, removed.getDestroyCount());
    }


    /*
     * A Realm that is removed and then re-added must be used for authentication again and must not be cleaned up as a
     * result of the earlier removal.
     */
    @Test
    public void testRemoveAndReAddRealm() throws Exception {
        CountingRealm realm = new CountingRealm("removed", PASSWORD);

        CombinedRealm combinedRealm = createCombinedRealm(realm);
        combinedRealm.start();

        Assert.assertTrue(combinedRealm.removeRealm(realm));
        Assert.assertEquals(0, combinedRealm.getNestedRealms().length);

        combinedRealm.addRealm(realm);
        Assert.assertEquals(1, combinedRealm.getNestedRealms().length);
        Assert.assertNotNull(combinedRealm.authenticate("removed", PASSWORD));

        // The pending clean-up from the removal must have been cancelled so the
        // Realm is only cleaned up once, as a Realm the combined Realm contains
        combinedRealm.stop();
        combinedRealm.destroy();
        Assert.assertEquals(LifecycleState.DESTROYED, realm.getState());
        Assert.assertEquals(1, realm.getStopCount());
        Assert.assertEquals(1, realm.getDestroyCount());
    }


    /*
     * The realm path is used to construct the JMX object name so every nested Realm must be allocated a unique realm
     * path. If two nested Realms share a realm path, registering the second un-registers the first leaving a Realm
     * that is in use but not visible in JMX.
     */
    private static void assertDistinctRealmPaths(RealmBase... realms) {
        Set<String> realmPaths = new HashSet<>();
        for (RealmBase realm : realms) {
            Assert.assertTrue("Duplicate realm path [" + realm.getRealmPath() + "]",
                    realmPaths.add(realm.getRealmPath()));
        }
    }


    /*
     * A combined Realm may be nested inside another combined Realm. The nested Realms of the inner combined Realm must
     * still be allocated unique realm paths.
     */
    @Test
    public void testNestedCombinedRealmPaths() {
        // Mimics the digester: nested Realms are added to the Realm that contains
        // them and the container is set once the outermost Realm is complete.
        CombinedRealm outer = new CombinedRealm();
        CombinedRealm inner = new CombinedRealm();
        TesterMapRealm innerFirst = createNestedRealm();
        TesterMapRealm innerSecond = createNestedRealm();
        TesterMapRealm outerSecond = createNestedRealm();

        inner.addRealm(innerFirst);
        inner.addRealm(innerSecond);
        outer.addRealm(inner);
        outer.addRealm(outerSecond);
        outer.setContainer(new TesterContext());

        Assert.assertEquals("/realm0/realm0", inner.getRealmPath());
        Assert.assertEquals("/realm0/realm1", outerSecond.getRealmPath());
        Assert.assertEquals("/realm0/realm0/realm0", innerFirst.getRealmPath());
        Assert.assertEquals("/realm0/realm0/realm1", innerSecond.getRealmPath());

        assertDistinctRealmPaths(inner, outerSecond, innerFirst, innerSecond);
    }


    /*
     * Changing the realm path of a combined Realm must renumber the nested Realms so they remain unique and must leave
     * the next index such that a Realm added afterwards does not collide with an existing Realm.
     */
    @Test
    public void testSetRealmPathRenumbersNestedRealms() {
        TesterMapRealm first = createNestedRealm();
        TesterMapRealm second = createNestedRealm();
        CombinedRealm combinedRealm = createCombinedRealm(first, second);

        combinedRealm.setRealmPath("/changed");

        Assert.assertEquals("/changed/realm0", first.getRealmPath());
        Assert.assertEquals("/changed/realm1", second.getRealmPath());

        TesterMapRealm third = createNestedRealm();
        combinedRealm.addRealm(third);
        Assert.assertEquals("/changed/realm2", third.getRealmPath());

        assertDistinctRealmPaths(first, second, third);
    }


    /*
     * A realm path must not be re-used when a Realm is removed and another Realm added since the removed Realm may
     * still be registered with JMX.
     */
    @Test
    public void testRealmPathsAreNotReUsed() {
        TesterMapRealm removed = createNestedRealm();
        TesterMapRealm retained = createNestedRealm();
        CombinedRealm combinedRealm = createCombinedRealm(removed, retained);

        Assert.assertTrue(combinedRealm.removeRealm(removed));

        TesterMapRealm added = createNestedRealm();
        combinedRealm.addRealm(added);

        assertDistinctRealmPaths(retained, added);
    }
}
