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
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * The pool's immutable snapshot is what a connect reads. A write through the live view publishes a new snapshot; a
 * write to the mutable builder directly changes what the management getters report but does not reach a connection
 * that is already using the snapshot. The mutable values the view hands out are copies, so changing one cannot
 * bypass the apply path.
 */
abstract class LiveConfigurationSnapshotBase {

    /** Records the properties each connect was offered and then declines to open a session. */
    public static class SnapshotRecordingDriver implements Driver {

        static final AtomicReference<Properties> LAST_CONNECT_INFO = new AtomicReference<>();

        @Override
        public Connection connect(String url, Properties info) throws SQLException {
            Properties handed = new Properties();
            handed.putAll(info);
            LAST_CONNECT_INFO.set(handed);
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

    private static PoolProperties properties() {
        PoolProperties props = new PoolProperties();
        props.setJmxEnabled(false);
        props.setInitialSize(0);
        props.setMinIdle(0);
        props.setUrl("jdbc:tomcat-jdbc-test:snapshot");
        props.setDriverClassName(SnapshotRecordingDriver.class.getName());
        props.setUsername("olduser");
        props.setPassword("oldpass");
        return props;
    }

    @Test
    void aConnectUsesTheSnapshotThePoolTookAtCreation() throws Exception {
        PoolProperties props = properties();
        ConnectionPool pool = new ConnectionPool(props);
        try {
            // The builder moves on. The pool's snapshot does not.
            props.setUsername("newuser");
            props.setPassword("newpass");
            SnapshotRecordingDriver.LAST_CONNECT_INFO.set(null);

            PooledConnection con = new PooledConnection(pool.getPoolProperties(), pool);
            Assertions.assertThrows(SQLException.class, con::connect,
                    "the recording driver declines the connect after recording the properties");

            Properties handed = SnapshotRecordingDriver.LAST_CONNECT_INFO.get();
            Assertions.assertNotNull(handed, "the driver must have been offered the snapshot");
            Assertions.assertEquals("olduser", handed.getProperty("user"),
                    "a direct builder write must not reach a connect that reads the pool snapshot");
            Assertions.assertEquals("oldpass", handed.getProperty("password"));
        } finally {
            pool.close(true);
        }
    }

    @Test
    void aWriteThroughTheLiveViewPublishesANewSnapshot() throws Exception {
        PoolProperties props = properties();
        ConnectionPool pool = new ConnectionPool(props);
        try {
            pool.getPoolProperties().setUsername("newuser");
            pool.getPoolProperties().setPassword("newpass");
            SnapshotRecordingDriver.LAST_CONNECT_INFO.set(null);

            PooledConnection con = new PooledConnection(pool.getPoolProperties(), pool);
            Assertions.assertThrows(SQLException.class, con::connect,
                    "the recording driver declines the connect after recording the properties");

            Properties handed = SnapshotRecordingDriver.LAST_CONNECT_INFO.get();
            Assertions.assertNotNull(handed, "the driver must have been offered the new snapshot");
            Assertions.assertEquals("newuser", handed.getProperty("user"));
            Assertions.assertEquals("newpass", handed.getProperty("password"));
        } finally {
            pool.close(true);
        }
    }

    @Test
    void theMutableValuesHandedOutByTheViewAreCopies() throws Exception {
        PoolProperties props = properties();
        ConnectionPool pool = new ConnectionPool(props);
        try {
            Properties copy = pool.getPoolProperties().getDbProperties();
            copy.setProperty("injected", "yes");

            Assertions.assertNull(pool.getPoolProperties().getDbProperties().getProperty("injected"),
                    "a caller changing the collection a getter returned must not change the pool configuration");
        } finally {
            pool.close(true);
        }
    }

    @Test
    void theInterceptorDefinitionsHandedOutByTheViewAreCopies() throws Exception {
        PoolProperties props = properties();
        props.setJdbcInterceptors("StatementFinalizer(trace=true)");
        ConnectionPool pool = new ConnectionPool(props);
        try {
            PoolConfiguration view = pool.getPoolProperties();
            PoolProperties.InterceptorDefinition[] definitions = view.getJdbcInterceptorsAsArray();
            PoolProperties.InterceptorDefinition target = null;
            for (PoolProperties.InterceptorDefinition definition : definitions) {
                if (definition.getClassName().contains("StatementFinalizer")) {
                    target = definition;
                    break;
                }
            }
            Assertions.assertNotNull(target, "the configured interceptor must be in the returned array");
            target.addProperty("injected", "yes");

            PoolProperties.InterceptorDefinition[] again = view.getJdbcInterceptorsAsArray();
            boolean injected = false;
            for (PoolProperties.InterceptorDefinition definition : again) {
                if (definition.getClassName().contains("StatementFinalizer")
                        && definition.getProperties().containsKey("injected")) {
                    injected = true;
                    break;
                }
            }
            Assertions.assertFalse(injected,
                    "a caller changing a definition returned by the view must not change the pool configuration");
        } finally {
            pool.close(true);
        }
    }

    @Test
    void aSnapshotCopyWaitsForTheSourceConfigurationMonitor() throws Exception {
        PoolProperties props = properties();
        Properties oldGeneration = new Properties();
        oldGeneration.setProperty("generation", "old");
        props.setDbProperties(oldGeneration);

        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch started = new CountDownLatch(1);
        AtomicReference<PoolProperties> copied = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Thread writer = new Thread(() -> {
            synchronized (props) {
                locked.countDown();
                try {
                    release.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException x) {
                    Thread.currentThread().interrupt();
                    return;
                }
                Properties newGeneration = new Properties();
                newGeneration.setProperty("generation", "new");
                props.setDbProperties(newGeneration);
            }
        }, "snapshot-writer");
        writer.setDaemon(true);
        writer.start();

        Thread copier = new Thread(() -> {
            started.countDown();
            try {
                copied.set(PoolProperties.copyOf(props));
            } catch (Throwable x) {
                failure.set(x);
            }
        }, "snapshot-copier");
        copier.setDaemon(true);

        try {
            Assertions.assertTrue(locked.await(10, TimeUnit.SECONDS),
                    "the writer must hold the source configuration monitor");
            copier.start();
            Assertions.assertTrue(started.await(10, TimeUnit.SECONDS),
                    "the copier must have started its snapshot");
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (System.nanoTime() < deadline && copier.isAlive()
                    && copier.getState() != Thread.State.BLOCKED) {
                Thread.sleep(1);
            }
        } finally {
            release.countDown();
        }

        writer.join(TimeUnit.SECONDS.toMillis(10));
        copier.join(TimeUnit.SECONDS.toMillis(10));
        Assertions.assertFalse(writer.isAlive(), "the writer must finish");
        Assertions.assertFalse(copier.isAlive(), "the copier must finish");
        Assertions.assertNull(failure.get(), () -> "the snapshot copy failed: " + failure.get());
        Assertions.assertEquals("new", copied.get().getDbProperties().getProperty("generation"),
                "the snapshot copy must wait for the source monitor and capture the completed generation");
    }
}
