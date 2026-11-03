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

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * The properties the connect path hands the driver are a copy of the live
 * {@code dbProperties} collection, and {@code setConnectionProperties}
 * reconfigures that collection in place while the pool runs. The copy reads
 * the collection more than once and is not atomic against a publication, so
 * taken outside the configuration's monitor it can mix two published
 * generations: a driver then starts with a key a concurrent reconfigure has
 * just removed next to the keys that replaced it, or - when the credential
 * travels in the connection-properties string, as here, where the credential
 * fields are unset and nothing is overlaid after the copy - with a
 * user/password pair no generation ever published together. The connect path
 * therefore takes the copy under the same monitor the reconfiguring setters
 * hold; this test forces a reconfiguration into the middle of a copy and
 * pins that the driver is handed one generation whole.
 */
class TestReconfigureVsConnectProperties {

    private static final String GENERATION_1 = "user=bob;password=P1;sslmode=require";
    private static final String GENERATION_2 = "user=alice;password=P2;newsetting=1";

    /**
     * Records the properties each connect was offered and then declines to
     * open a session: the test is about what the copy contained, not about
     * any real database voting on it.
     */
    public static class RecordingNullDriver implements Driver {

        static final AtomicReference<Properties> lastConnectInfo = new AtomicReference<>();

        @Override
        public Connection connect(String url, Properties info) throws SQLException {
            Properties handed = new Properties();
            handed.putAll(info);
            lastConnectInfo.set(handed);
            return null;
        }

        @Override
        public boolean acceptsURL(String url) throws SQLException {
            return true;
        }

        @Override
        public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) throws SQLException {
            return new DriverPropertyInfo[0];
        }

        @Override
        public int getMajorVersion() {
            return 1;
        }

        @Override
        public int getMinorVersion() {
            return 0;
        }

        @Override
        public boolean jdbcCompliant() {
            return false;
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            throw new SQLFeatureNotSupportedException();
        }
    }

    /**
     * The live driver-properties collection. Its first use as the source of a
     * {@code stringPropertyNames()} read - the copy's second observation of
     * the collection, after its {@code putAll} - lets the racer attempt the
     * second publication and then records which of the two outcomes the race
     * produced: the racer parked behind the monitor the copy is holding (the
     * copy is taken under the configuration's monitor), or the racer's
     * mutation landing while the copy was still reading it (it is not).
     */
    private static final class RacingStore extends Properties {

        private static final long serialVersionUID = 1L;

        final CountDownLatch copyObserving = new CountDownLatch(1);
        final CountDownLatch racerStarted = new CountDownLatch(1);
        final AtomicReference<Thread> racer = new AtomicReference<>();
        volatile String observation = "neither observed";

        private transient volatile boolean armed = true;

        @Override
        public Set<String> stringPropertyNames() {
            if (armed) {
                armed = false;
                copyObserving.countDown();
                awaitQuietly(racerStarted, "the reconfigure racer to start");
                Thread racerThread = racer.get();
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
                while (System.nanoTime() < deadline) {
                    if (racerThread.getState() == Thread.State.BLOCKED) {
                        // Parked entering the synchronized publication while
                        // this read still holds the very same monitor
                        observation = "racer held off by the copy's monitor";
                        break;
                    }
                    if (containsKey("newsetting")) {
                        // The second publication mutated this live collection
                        // while the copy was still reading it
                        observation = "racer mutated mid-copy";
                        break;
                    }
                    pause();
                }
            }
            return super.stringPropertyNames();
        }

        private static void awaitQuietly(CountDownLatch latch, String what) {
            try {
                if (!latch.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("timed out waiting for " + what);
                }
            } catch (InterruptedException x) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(x);
            }
        }

        private static void pause() {
            try {
                Thread.sleep(1);
            } catch (InterruptedException x) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Test
    void aReconfigureRacingTheConnectCopyCannotMixTwoGenerations() throws Exception {
        // Run the publication path once on a throwaway configuration, so the
        // only monitor the racer can park on during the race is the
        // configuration's own
        PoolProperties warmUp = new PoolProperties();
        warmUp.setConnectionProperties(GENERATION_1);
        warmUp.setConnectionProperties(GENERATION_2);

        PoolProperties props = new PoolProperties();
        props.setJmxEnabled(false);
        // No eager connections at pool init: the one-shot hook in the store
        // below belongs to the raced connect, not to the pool constructor
        props.setInitialSize(0);
        props.setUrl("jdbc:tomcat-jdbc-test:reconfiguration");
        props.setDriverClassName(RecordingNullDriver.class.getName());
        RacingStore store = new RacingStore();
        store.setProperty("user", "bob");
        store.setProperty("password", "P1");
        store.setProperty("sslmode", "require");
        props.setDbProperties(store);
        // The credential travels in the string, so the credential fields stay
        // null and nothing repairs the pair after the copy
        props.setConnectionProperties(GENERATION_1);
        RecordingNullDriver.lastConnectInfo.set(null);

        ConnectionPool pool = new ConnectionPool(props);
        try {
            PooledConnection con = new PooledConnection(props, pool);
            Thread racer = new Thread(() -> {
                store.racer.set(Thread.currentThread());
                store.racerStarted.countDown();
                props.setConnectionProperties(GENERATION_2);
            }, "reconfigure-racer");
            AtomicReference<Throwable> connectFailure = new AtomicReference<>();
            Thread connectThread = new Thread(() -> {
                try {
                    con.connectUsingDriver();
                } catch (Throwable x) {
                    // the recording driver declines every connect
                    connectFailure.set(x);
                }
            }, "connect-under-race");

            connectThread.start();
            Assertions.assertTrue(store.copyObserving.await(10, TimeUnit.SECONDS),
                    "the connect copy must reach the live collection while it is still taking it");
            racer.start();
            Assertions.assertTrue(store.racerStarted.await(10, TimeUnit.SECONDS),
                    "the reconfigure racer must attempt its publication");
            connectThread.join(TimeUnit.SECONDS.toMillis(10));
            racer.join(TimeUnit.SECONDS.toMillis(10));
            Assertions.assertFalse(connectThread.isAlive(), "the connect must have ended");
            Assertions.assertFalse(racer.isAlive(), "the reconfigure must have ended");

            Properties handed = RecordingNullDriver.lastConnectInfo.get();
            Assertions.assertNotNull(handed, "the driver must have been offered the copy");
            Assertions.assertTrue(connectFailure.get() instanceof SQLException,
                    "the driver declines every connect, after recording the copy");

            // The copy must be one published generation whole. Without the
            // copy under the monitor the racer's mutation lands mid-copy (the
            // observation records that) and the copy comes back the union of
            // the generations: the removed sslmode still aboard next to the
            // newsetting that did not belong to the generation the copy's
            // directly copied credentials came from.
            Assertions.assertEquals(new HashSet<>(Arrays.asList("user", "password", "sslmode")),
                    handed.stringPropertyNames(),
                    "the driver was handed no published generation of the "
                            + "driver properties (racer: " + store.observation + ")");
            Assertions.assertEquals("bob", handed.getProperty("user"),
                    "the pair the copy carries must belong together (racer: " + store.observation + ")");
            Assertions.assertEquals("P1", handed.getProperty("password"),
                    "the pair the copy carries must belong together (racer: " + store.observation + ")");
            Assertions.assertEquals("require", handed.getProperty("sslmode"));

            // And the publication the copy held off did land, whole, after it
            Assertions.assertEquals("alice", store.getProperty("user"));
            Assertions.assertEquals("P2", store.getProperty("password"));
            Assertions.assertEquals("1", store.getProperty("newsetting"));
            Assertions.assertNull(store.getProperty("sslmode"),
                    "the second generation dropped the key the first one carried");
        } finally {
            pool.close(true);
        }
    }
}
