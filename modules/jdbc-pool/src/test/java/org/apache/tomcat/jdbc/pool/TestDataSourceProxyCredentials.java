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
package org.apache.tomcat.jdbc.pool;

import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Configuration only, no database and no pool: writing a credential through the
 * data source's setters ends up in the database properties that
 * {@link PooledConnection} clones when it hands them to the driver, and passing
 * {@code null} must clear the credential from both stores instead of throwing
 * halfway. The store that holds both - {@link PoolProperties}, which the data
 * source and the pool's MBean each write through - is what keeps them together,
 * so these assertions hold for either surface. The connection path only
 * overrides the clone with the stored password when that is non-null, so a
 * mirror left behind keeps authenticating freshly established connections with
 * the cleared credential.
 */
class TestDataSourceProxyCredentials {

    @Test
    void aNullPasswordClearsTheMirroredDatabaseProperty() {
        DataSourceProxy proxy = new DataSourceProxy();
        proxy.setUsername("alice");
        proxy.setPassword("s3cret");
        Properties dbProperties = proxy.getPoolProperties().getDbProperties();
        Assertions.assertEquals("alice", dbProperties.getProperty(PoolUtilities.PROP_USER));
        Assertions.assertEquals("s3cret", dbProperties.getProperty(PoolUtilities.PROP_PASSWORD));

        // Before the fix this threw after clearing the field, leaving the
        // old password in the properties the driver receives.
        Assertions.assertDoesNotThrow(() -> proxy.setPassword(null));

        Assertions.assertNull(proxy.getPoolProperties().getPassword());
        Assertions.assertFalse(dbProperties.containsKey(PoolUtilities.PROP_PASSWORD),
                "the cleared password must not stay in the properties the driver is given");
        Assertions.assertEquals("alice", dbProperties.getProperty(PoolUtilities.PROP_USER),
                "clearing the password must leave the user name alone");
    }

    @Test
    void aNullUserNameClearsTheMirroredDatabaseProperty() {
        DataSourceProxy proxy = new DataSourceProxy();
        proxy.setUsername("alice");
        proxy.setPassword("s3cret");
        Properties dbProperties = proxy.getPoolProperties().getDbProperties();

        Assertions.assertDoesNotThrow(() -> proxy.setUsername(null));

        Assertions.assertNull(proxy.getPoolProperties().getUsername());
        Assertions.assertFalse(dbProperties.containsKey(PoolUtilities.PROP_USER),
                "the cleared user name must not stay in the properties the driver is given");
        Assertions.assertEquals("s3cret", dbProperties.getProperty(PoolUtilities.PROP_PASSWORD),
                "clearing the user name must leave the password alone");
    }

    @Test
    void credentialsCanBeSetAgainAfterTheyWereCleared() {
        DataSourceProxy proxy = new DataSourceProxy();
        proxy.setPassword("first");
        proxy.setUsername("alice");
        proxy.setPassword(null);
        proxy.setUsername(null);
        proxy.setPassword("second");
        proxy.setUsername("bob");
        Properties dbProperties = proxy.getPoolProperties().getDbProperties();

        Assertions.assertEquals("second", dbProperties.getProperty(PoolUtilities.PROP_PASSWORD));
        Assertions.assertEquals("bob", dbProperties.getProperty(PoolUtilities.PROP_USER));
    }

    @Test
    void settingCredentialsToleratesNullDatabaseProperties() {
        // PoolProperties.setDbProperties accepts null, and a credential
        // setter must not turn that into a NullPointerException that leaves
        // the value half-applied; the field is the whole of the state.
        DataSourceProxy proxy = new DataSourceProxy();
        proxy.getPoolProperties().setDbProperties(null);

        Assertions.assertDoesNotThrow(() -> proxy.setPassword("hunter2"));
        Assertions.assertDoesNotThrow(() -> proxy.setUsername(null));

        Assertions.assertEquals("hunter2", proxy.getPoolProperties().getPassword());
        Assertions.assertNull(proxy.getPoolProperties().getUsername());
    }

    @Test
    void readingDbPropertiesToleratesNullDatabaseProperties() {
        // The same null state read back: the password-masking getter cloned
        // the properties unconditionally, which turned the supported null
        // into a NullPointerException on every read of the bean/JMX view.
        // Before the masking rewrite the getter was a pass-through, so the
        // null passed through; the masking must keep it passing through.
        DataSourceProxy proxy = new DataSourceProxy();
        proxy.getPoolProperties().setDbProperties(null);

        Assertions.assertDoesNotThrow(proxy::getDbProperties);
        Assertions.assertNull(proxy.getDbProperties(),
                "a configuration with no database properties has nothing to mask");

        // The masking getter must still hand out a copy for a live store.
        Properties live = new Properties();
        live.setProperty(PoolUtilities.PROP_PASSWORD, "hunter2");
        proxy.getPoolProperties().setDbProperties(live);
        Properties view = proxy.getDbProperties();
        Assertions.assertNotNull(view);
        Assertions.assertFalse(view.containsKey(PoolUtilities.PROP_PASSWORD),
                "the copy must not carry the password");
        Assertions.assertNotSame(live, view, "the copy must not be the live store");
    }

    @Test
    void getLoginTimeoutNeverReturnsANegativeValue() {
        DataSourceProxy proxy = new DataSourceProxy();
        proxy.getPoolProperties().setMaxWait(-5000);

        Assertions.assertEquals(0, proxy.getLoginTimeout(),
                "a negative maxWait means wait indefinitely, and a negative login timeout is"
                        + " not a legal JDBC value");
        proxy.setLoginTimeout(5);
        Assertions.assertEquals(5, proxy.getLoginTimeout(), "a normal timeout must still round-trip");
    }

    @Test
    void parsedInterceptorDefinitionsAreMaskedAndAreNotTheLiveDefinitions() {
        DataSourceProxy proxy = new DataSourceProxy();
        proxy.setJdbcInterceptors("ResetAbandonedTimer(password=s3cret);StatementCache(pass=s3cret2)");

        PoolProperties.InterceptorDefinition[] view = proxy.getJdbcInterceptorsAsArray();
        PoolProperties.InterceptorDefinition timer = null;
        PoolProperties.InterceptorDefinition cache = null;
        for (PoolProperties.InterceptorDefinition definition : view) {
            if (definition.getClassName().endsWith("ResetAbandonedTimer")) {
                timer = definition;
            } else if (definition.getClassName().endsWith("StatementCache")) {
                cache = definition;
            }
        }
        Assertions.assertNotNull(timer, "the parsed view must carry the configured timer");
        Assertions.assertNotNull(cache, "the parsed view must carry the configured cache");
        Assertions.assertEquals("********", timer.getProperties().get("password").getValue(),
                "the parsed bean/JMX view must mask a credential exactly like the string getter");
        Assertions.assertEquals("********", cache.getProperties().get("pass").getValue(),
                "the parsed bean/JMX view must mask every credential-shaped key");

        // The raw definitions stay reachable through getPoolProperties() for
        // trusted in-process code, and mutating the display copy cannot change
        // the chain the pool builds.
        PoolProperties.InterceptorDefinition liveTimer = null;
        for (PoolProperties.InterceptorDefinition definition : proxy.getPoolProperties()
                .getJdbcInterceptorsAsArray()) {
            if (definition.getClassName().endsWith("ResetAbandonedTimer")) {
                liveTimer = definition;
            }
        }
        Assertions.assertNotNull(liveTimer);
        Assertions.assertEquals("s3cret", liveTimer.getProperties().get("password").getValue());
        timer.getProperties().put("password", new PoolProperties.InterceptorProperty("password", "changed"));
        Assertions.assertEquals("s3cret", liveTimer.getProperties().get("password").getValue(),
                "the display copy must share no property map with the live definitions");
    }

    /**
     * Database properties whose {@code password} write, once armed, lets a
     * concurrent credential change run inside it and then completes the write -
     * exactly what a rotation writer sees when it is preempted between storing
     * the field and mirroring it.
     */
    private static final class HookedDatabaseProperties extends Properties {

        private static final long serialVersionUID = 1L;

        private final AtomicBoolean armed = new AtomicBoolean();
        volatile Runnable onMirrorWrite;

        @Override
        public Object setProperty(String key, String value) {
            if (PoolUtilities.PROP_PASSWORD.equals(key) && armed.compareAndSet(true, false)) {
                onMirrorWrite.run();
            }
            return super.setProperty(key, value);
        }
    }

    @Test
    void aConcurrentClearCannotLeaveTheClearedPasswordMirrored() throws Exception {
        // The field write and the mirror write of setPassword had no common
        // lock, so a rotation that read the field could still write its
        // value into the database properties after a concurrent clear had
        // removed it: the driver then authenticates freshly established
        // connections with a credential that was cleared. The deterministic
        // interleaving below is what the setters must exclude.
        HookedDatabaseProperties dbProperties = new HookedDatabaseProperties();
        PoolProperties props = new PoolProperties();
        props.setDbProperties(dbProperties);
        DataSourceProxy proxy = new DataSourceProxy();
        proxy.setPoolProperties(props);
        proxy.setPassword("old");
        Assertions.assertEquals("old", dbProperties.getProperty(PoolUtilities.PROP_PASSWORD));

        CountDownLatch cleared = new CountDownLatch(1);
        AtomicReference<Throwable> clearerFailure = new AtomicReference<>();
        Thread clearer = new Thread(() -> {
            try {
                proxy.setPassword(null);
            } catch (Throwable t) {
                clearerFailure.set(t);
            } finally {
                cleared.countDown();
            }
        }, "concurrent-credential-clear");

        dbProperties.onMirrorWrite = () -> {
            // The rotation pauses right here - field written, mirror not
            // yet. Let the clear try to take this window.
            clearer.start();
            try {
                cleared.await(500, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError(e);
            }
        };
        dbProperties.armed.set(true);

        proxy.setPassword("rotated");

        // If the clear could not run inside the window - the store holds one
        // monitor across field and mirror - it runs as soon as the rotation
        // returns, and its verdict is the final one.
        clearer.join(5000);
        Assertions.assertFalse(clearer.isAlive(), "the concurrent clear must eventually run");
        Assertions.assertNull(clearerFailure.get(), "the clear must not fail");

        Assertions.assertNull(proxy.getPoolProperties().getPassword(),
                "the clear ran last, so the field must read cleared");
        Assertions.assertFalse(dbProperties.containsKey(PoolUtilities.PROP_PASSWORD),
                "a cleared password must not survive in the properties the driver is given");
    }
}
