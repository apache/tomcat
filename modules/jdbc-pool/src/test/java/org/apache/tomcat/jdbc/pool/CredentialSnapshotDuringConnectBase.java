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

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;

/**
 * The credential fields and the {@code dbProperties} collection are published
 * together under the configuration's monitor, so that monitor is the snapshot
 * boundary for the credential/database-properties pair. A connect path that
 * reads the credential field before taking the monitor can lose the race: it
 * then takes a newer {@code dbProperties} collection and overlays the older
 * credential it read earlier, handing the driver a pair no configuration ever
 * published. A connect through a {@code DataSource} has the same boundary for
 * the configured credentials it passes to the provider.
 */
abstract class CredentialSnapshotDuringConnectBase extends JdbcPoolBaseTest {

    /**
     * Records the properties each connect was offered and then declines to
     * open a session: the tests observe the snapshot, not a real database
     * voting on it.
     */
    public static class SnapshotRecordingDriver implements Driver {

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
     * Reads the old password, then lets a credential rotation land while the
     * connect is parked, and records the user/password pair of the database
     * properties the connect copies.
     */
    private static final class RotationProbe extends PoolProperties {

        private static final long serialVersionUID = 1L;

        final CountDownLatch passwordRead = new CountDownLatch(1);
        final CountDownLatch passwordRelease = new CountDownLatch(1);
        private final AtomicBoolean parked = new AtomicBoolean(true);
        volatile String copiedUser;
        volatile String copiedPassword;

        @Override
        public Properties getDbProperties() {
            Properties props = super.getDbProperties();
            if (props != null) {
                copiedUser = props.getProperty("user");
                copiedPassword = props.getProperty("password");
            }
            return props;
        }

        @Override
        public String getPassword() {
            String value = super.getPassword();
            if (parked.compareAndSet(true, false)) {
                passwordRead.countDown();
                try {
                    if (!passwordRelease.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("timed out waiting to resume the connect");
                    }
                } catch (InterruptedException x) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(x);
                }
            }
            return value;
        }
    }

    /**
     * Records whether the credential getters run while the configuration's
     * monitor is held by the calling thread.
     */
    private static final class MonitorProbe extends PoolProperties {

        private static final long serialVersionUID = 1L;

        final AtomicBoolean userReadUnderMonitor = new AtomicBoolean();
        final AtomicBoolean passwordReadUnderMonitor = new AtomicBoolean();

        @Override
        public String getUsername() {
            if (Thread.holdsLock(this)) {
                userReadUnderMonitor.set(true);
            }
            return super.getUsername();
        }

        @Override
        public String getPassword() {
            if (Thread.holdsLock(this)) {
                passwordReadUnderMonitor.set(true);
            }
            return super.getPassword();
        }
    }

    @Test
    void aCredentialRotationRacingTheConnectCopyCannotBeOverlaidOnTheNewProperties() throws Exception {
        RotationProbe props = new RotationProbe();
        props.setJmxEnabled(false);
        props.setInitialSize(0);
        props.setMinIdle(0);
        props.setUrl("jdbc:tomcat-jdbc-test:credential-snapshot");
        props.setDriverClassName(SnapshotRecordingDriver.class.getName());
        props.setUsername("olduser");
        props.setPassword("oldpass");
        SnapshotRecordingDriver.lastConnectInfo.set(null);

        ConnectionPool pool = new ConnectionPool(props);
        PooledConnection con = new PooledConnection(props, pool);
        try {
            AtomicReference<Throwable> failure = new AtomicReference<>();
            Thread connectThread = new Thread(() -> {
                try {
                    con.connect();
                } catch (Throwable x) {
                    // The recording driver declines every connect
                    failure.set(x);
                }
            }, "connect-under-credential-rotation");

            connectThread.start();
            Assertions.assertTrue(props.passwordRead.await(10, TimeUnit.SECONDS),
                    "the connect must reach the configured password while taking its snapshot");

            Thread rotateThread = new Thread(() -> {
                props.setUsername("newuser");
                props.setPassword("newpass");
            }, "rotate-credentials");
            rotateThread.start();

            // The connect holds the configuration's monitor while it takes the
            // credential/database-properties snapshot, so the rotation is held
            // off; without it the rotation lands inside the window and the
            // connect goes on to take the rotated collection.
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (System.nanoTime() < deadline) {
                if (rotateThread.getState() == Thread.State.BLOCKED || !rotateThread.isAlive()) {
                    break;
                }
                Thread.sleep(1);
            }
            props.passwordRelease.countDown();

            connectThread.join(TimeUnit.SECONDS.toMillis(10));
            rotateThread.join(TimeUnit.SECONDS.toMillis(10));
            Assertions.assertFalse(connectThread.isAlive(), "the connect must have ended");
            Assertions.assertFalse(rotateThread.isAlive(), "the rotation must have ended");

            Properties handed = SnapshotRecordingDriver.lastConnectInfo.get();
            Assertions.assertNotNull(handed, "the driver must have been offered the properties copy");
            Assertions.assertTrue(failure.get() instanceof SQLException,
                    "the driver declines every connect, after recording the copy");

            Assertions.assertEquals(props.copiedUser, handed.getProperty("user"),
                    "the driver must be handed the user name that belongs to the database-properties snapshot");
            Assertions.assertEquals(props.copiedPassword, handed.getProperty("password"),
                    "the driver must be handed the password that belongs to the database-properties snapshot");

            // The rotation the snapshot held off lands whole, after it
            Assertions.assertEquals("newuser", props.getUsername());
            Assertions.assertEquals("newpass", props.getPassword());
        } finally {
            con.release();
            pool.close(true);
        }
    }

    @Test
    void aDataSourceConnectReadsTheConfiguredCredentialsUnderTheConfigurationMonitor() throws Exception {
        AtomicReference<String> handedUser = new AtomicReference<>();
        AtomicReference<String> handedPassword = new AtomicReference<>();

        InvocationHandler handler = new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                if ("getConnection".equals(method.getName()) && args != null && args.length == 2) {
                    handedUser.set((String) args[0]);
                    handedPassword.set((String) args[1]);
                    throw new SQLException("recorded credential overload");
                }
                if ("getLogWriter".equals(method.getName())) {
                    return null;
                }
                if ("getLoginTimeout".equals(method.getName())) {
                    return Integer.valueOf(0);
                }
                if ("getParentLogger".equals(method.getName())) {
                    throw new SQLFeatureNotSupportedException();
                }
                return null;
            }
        };
        Object dataSource = Proxy.newProxyInstance(CredentialSnapshotDuringConnectBase.class.getClassLoader(),
                new Class<?>[] { javax.sql.DataSource.class }, handler);

        MonitorProbe props = new MonitorProbe();
        props.setJmxEnabled(false);
        props.setInitialSize(0);
        props.setMinIdle(0);
        props.setDataSource(dataSource);
        props.setUsername("alice");
        props.setPassword("secret");

        ConnectionPool pool = new ConnectionPool(props);
        PooledConnection con = new PooledConnection(props, pool);
        try {
            Assertions.assertThrows(SQLException.class, con::connect);
            Assertions.assertTrue(props.userReadUnderMonitor.get(),
                    "the configured user name must be read under the configuration's monitor");
            Assertions.assertTrue(props.passwordReadUnderMonitor.get(),
                    "the configured password must be read under the configuration's monitor");
            Assertions.assertEquals("alice", handedUser.get());
            Assertions.assertEquals("secret", handedPassword.get());
        } finally {
            con.release();
            pool.close(true);
        }
    }
}
