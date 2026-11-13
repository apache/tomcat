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
package org.apache.tomcat.jdbc.pool;

import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;

/**
 * {@link PooledConnection#getLastValidated()} documents 0 for a session that
 * has not been validated. The timestamp belongs to one physical session, so a
 * disconnect must clear it: while a reconnect is in flight the per-connection
 * MBean stays registered, and the old session's timestamp must not be read as
 * if it vouched for the new session.
 */
class TestLastValidatedLifecycle extends JdbcPoolBaseTest {

    @Test
    void aDestroyedSessionClearsLastValidated() throws Exception {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setInitialSize(0);
        ds.getPoolProperties().setTimeBetweenEvictionRunsMillis(-1);
        ds.createPool();

        Connection held = ds.getConnection();
        PooledConnection pooled = interceptorOf(held, ProxyConnection.class).getConnection();
        pooled.setLastValidated(12345L);

        ds.close(true);

        Assertions.assertEquals(0L, pooled.getLastValidated(),
                "a disconnected session must report 0 rather than the validation of the session it replaced");
    }

    @Test
    void thePerConnectionMBeanReportsZeroAcrossAReconnect() throws Exception {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setJmxEnabled(true);
        ds.getPoolProperties().setInitialSize(0);
        ds.getPoolProperties().setTimeBetweenEvictionRunsMillis(-1);
        ds.createPool();
        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        ds.preRegister(server,
                new ObjectName("tomcat.jdbc:type=LastValidatedTest,uid=" + System.nanoTime()));

        Connection held = ds.getConnection();
        try {
            PooledConnection pooled = interceptorOf(held, ProxyConnection.class).getConnection();
            ObjectName name = pooled.getObjectName();
            Assertions.assertNotNull(name,
                    "precondition: the borrowed connection has published its MBean");

            pooled.setLastValidated(12345L);
            Assertions.assertEquals(12345L, attribute(server, name, "LastValidated"),
                    "precondition: the MBean reads the validation timestamp of the current session");

            pooled.reconnect();

            Assertions.assertEquals(0L, attribute(server, name, "LastValidated"),
                    "the replaced session's validation must not be reported for the new session");
            Assertions.assertEquals(0L, pooled.getLastValidated());
        } finally {
            held.close();
            ds.close(true);
        }
    }

    @Test
    void reconnectWithClearsLastValidatedWhileTheNewSessionIsBeingEstablished() throws Exception {
        BlockingDataSource backing = new BlockingDataSource(getUrl());
        DataSource ds = newDataSource();
        ds.getPoolProperties().setDataSource(backing);
        // Use the no-argument DataSource method for the initial connection, so the parking latch is reserved
        // for the credential-overloaded reconnect.
        ds.getPoolProperties().setUsername(null);
        ds.getPoolProperties().setPassword(null);
        ds.getPoolProperties().setInitialSize(0);
        ds.getPoolProperties().setTimeBetweenEvictionRunsMillis(-1);
        ds.createPool();

        Connection held = ds.getConnection();
        try {
            PooledConnection pooled = interceptorOf(held, ProxyConnection.class).getConnection();
            pooled.setLastValidated(12345L);

            Thread reconnect = new Thread(() -> {
                try {
                    pooled.reconnectWith("other", "secret");
                } catch (SQLException x) {
                    throw new IllegalStateException(x);
                }
            }, "reconnect-with-thread");
            reconnect.setDaemon(true);
            reconnect.start();

            Assertions.assertTrue(backing.entered.await(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS),
                    "the driver must be establishing the replacement session");
            Assertions.assertEquals(-1L, pooled.getLastConnected(),
                    "the pooled connection must report no current session while the replacement is in flight");
            Assertions.assertEquals(0L, pooled.getLastValidated(),
                    "the replaced session's validation timestamp must not be published during the reconnect window");
            Assertions.assertNull(pooled.getConnection(),
                    "the pooled connection must not expose the old session during the staged reconnect");

            backing.release.countDown();
            reconnect.join(AWAIT_TIMEOUT_MS);
            Assertions.assertFalse(reconnect.isAlive(), "the reconnect must complete");
        } finally {
            backing.release.countDown();
            held.close();
            ds.close(true);
        }
    }

    private static long attribute(MBeanServer server, ObjectName name, String attribute) throws Exception {
        return ((Number) server.getAttribute(name, attribute)).longValue();
    }

    /**
     * A backing data source whose credential overload parks the reconnect while the test inspects the pooled
     * connection's staged state.
     */
    private static final class BlockingDataSource implements javax.sql.DataSource {

        private final String url;
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);

        BlockingDataSource(String url) {
            this.url = url;
        }

        @Override
        public Connection getConnection() throws SQLException {
            return DriverManager.getConnection(url, "sa", "");
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            entered.countDown();
            try {
                release.await(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            } catch (InterruptedException x) {
                Thread.currentThread().interrupt();
                throw new SQLException(x);
            }
            return DriverManager.getConnection(url, "sa", "");
        }

        @Override
        public PrintWriter getLogWriter() {
            return null;
        }

        @Override
        public void setLogWriter(PrintWriter out) {
        }

        @Override
        public void setLoginTimeout(int seconds) {
        }

        @Override
        public int getLoginTimeout() {
            return 0;
        }

        @Override
        public Logger getParentLogger() {
            return null;
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            throw new SQLException();
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return false;
        }
    }
}
