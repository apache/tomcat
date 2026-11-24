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

import java.io.PrintWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
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

    /** A connection object only the route test needs; nothing is expected to call it after the hand-over. */
    private static final Connection FAKE_CONNECTION = (Connection) Proxy.newProxyInstance(
            LiveConfigurationSnapshotBase.class.getClassLoader(), new Class<?>[] { Connection.class },
            new InvocationHandler() {
                @Override
                public Object invoke(Object proxy, java.lang.reflect.Method method, Object[] args) {
                    String name = method.getName();
                    if ("toString".equals(name)) {
                        return "fake connection";
                    }
                    if ("hashCode".equals(name)) {
                        return Integer.valueOf(System.identityHashCode(proxy));
                    }
                    if ("equals".equals(name)) {
                        return Boolean.valueOf(proxy == args[0]);
                    }
                    if ("close".equals(name)) {
                        return null;
                    }
                    if ("isClosed".equals(name)) {
                        return Boolean.FALSE;
                    }
                    throw new UnsupportedOperationException(name);
                }
            });

    /** A data source that hands out the fake connection and records no state of its own. */
    private static final class FakeDataSource implements javax.sql.DataSource {

        @Override
        public Connection getConnection() {
            return FAKE_CONNECTION;
        }

        @Override
        public Connection getConnection(String username, String password) {
            return FAKE_CONNECTION;
        }

        @Override
        public PrintWriter getLogWriter() {
            return null;
        }

        @Override
        public void setLogWriter(PrintWriter out) {
            // NOOP
        }

        @Override
        public void setLoginTimeout(int seconds) {
            // NOOP
        }

        @Override
        public int getLoginTimeout() {
            return 0;
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            throw new SQLFeatureNotSupportedException();
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return false;
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            throw new SQLException("the fake data source is not a wrapper");
        }
    }

    /**
     * Parks inside the data-source connect at either the no-argument entry point (the pre-fix call from
     * {@code connect()}) or the configuration-carrying one (the post-fix call), so the test can replace the live
     * configuration after the route decision but before the body reads its snapshot.
     */
    private static final class ParkingConnection extends PooledConnection {

        static volatile CountDownLatch entered = new CountDownLatch(1);
        static volatile CountDownLatch release = new CountDownLatch(1);

        ParkingConnection(PoolConfiguration properties, ConnectionPool parent) {
            super(properties, parent);
        }

        private static void park() {
            entered.countDown();
            try {
                release.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException x) {
                Thread.currentThread().interrupt();
            }
        }

        @Override
        protected void connectUsingDataSource() throws SQLException {
            park();
            super.connectUsingDataSource();
        }

        @Override
        protected void connectUsingDataSource(PoolConfiguration configuration) throws SQLException {
            park();
            super.connectUsingDataSource(configuration);
        }
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

    /**
     * A reconnect stages credentials for the connect it immediately performs,
     * so both must come from the same generation. A direct builder write ahead
     * of snapshot publication must not put a new password on a session
     * established with the older URL and driver properties.
     */
    @Test
    void aReconnectUsesTheCredentialGenerationItsConnectReads() throws Exception {
        PoolProperties props = properties();
        Properties generation = new Properties();
        generation.setProperty("generation", "old");
        props.setDbProperties(generation);
        ConnectionPool pool = new ConnectionPool(props);
        PooledConnection con = new PooledConnection(pool.getPoolProperties(), pool);
        try {
            // Simulate a builder write that has not reached the snapshot yet.
            pool.getMutableConfiguration().setPassword("newpass");
            SnapshotRecordingDriver.LAST_CONNECT_INFO.set(null);

            Assertions.assertThrows(SQLException.class, () -> con.reconnectWith(null, null),
                    "the recording driver declines the connect after recording the properties");

            Properties handed = SnapshotRecordingDriver.LAST_CONNECT_INFO.get();
            Assertions.assertNotNull(handed, "the driver must have been offered the connect generation");
            Assertions.assertEquals("oldpass", handed.getProperty("password"),
                    "the reconnect must use the password of the snapshot its connect reads");
            Assertions.assertEquals("olduser", handed.getProperty("user"));
            Assertions.assertEquals("old", handed.getProperty("generation"),
                    "URL and driver properties must come from the same generation as the credentials");
        } finally {
            con.release();
            pool.close(true);
        }
    }

    @Test
    void aConnectKeepsTheConfigurationGenerationItsRouteWasChosenFrom() throws Exception {
        ParkingConnection.entered = new CountDownLatch(1);
        ParkingConnection.release = new CountDownLatch(1);
        PoolProperties props = properties();
        props.setDataSource(new FakeDataSource());
        ConnectionPool pool = new ConnectionPool(props);
        PooledConnection con = new ParkingConnection(pool.getPoolProperties(), pool);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread connect = new Thread(() -> {
            try {
                con.connect();
            } catch (Throwable x) {
                failure.set(x);
            }
        }, "configuration-generation-connect");
        connect.setDaemon(true);
        try {
            connect.start();
            Assertions.assertTrue(ParkingConnection.entered.await(10, TimeUnit.SECONDS),
                    "the connect must reach the data source route");

            // Replace the live configuration after the route was chosen: the
            // published snapshot now carries no data source at all.
            pool.getPoolProperties().setDataSource(null);
            Assertions.assertNull(pool.getCurrentConfiguration().getDataSource(),
                    "precondition: the published snapshot no longer carries a data source");

            ParkingConnection.release.countDown();
            connect.join(TimeUnit.SECONDS.toMillis(10));
            Assertions.assertFalse(connect.isAlive(), "the connect must finish");
            Assertions.assertNull(failure.get(),
                    () -> "the connect must stay on the generation it started with: " + failure.get());
            Assertions.assertNotNull(con.getConnection(),
                    "a route chosen from the starting snapshot must still find its data source in the connect body");
        } finally {
            ParkingConnection.release.countDown();
            pool.close(true);
        }
    }
}
