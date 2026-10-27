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
package org.apache.tomcat.jdbc;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;

import org.apache.tomcat.jdbc.pool.ConnectionPool;
import org.apache.tomcat.jdbc.pool.DataSource;
import org.apache.tomcat.jdbc.pool.JdbcInterceptor;
import org.apache.tomcat.jdbc.pool.PooledConnection;
import org.h2.tools.Server;

/**
 * Base class for the functional test suite. Every test class gets its
 * own H2 server (TCP mode, ephemeral port, in-memory database) so that
 * pool connections are genuine client/server JDBC connections that can
 * be observed - and asserted against - from the database side:
 *
 * <ul>
 * <li>{@link #poolSessions()} counts the <em>physical</em> sessions the
 * database currently holds for this test, so reuse, release, eviction
 * and leaks are verified with server-side truth rather than
 * pool-reported statistics;</li>
 * <li>{@link #setSessionMarker(Connection, int)} /
 * {@link #sessionMarker(Connection)} use an H2 session-scoped variable
 * to identify which physical session a borrowed connection maps to.</li>
 * </ul>
 *
 * Test classes must not run in parallel (each uses static fixture
 * state); Surefire's default sequential execution applies.
 */
public abstract class JdbcPoolBaseTest {

    /** Upper bound for any await-style condition; generous on slow CI. */
    protected static final long AWAIT_TIMEOUT_MS = 15000;

    private static Server server;
    private static String url;

    private final List<DataSource> createdDataSources = new ArrayList<>();

    @BeforeAll
    public static void startDatabase() throws Exception {
        // Port 0 -> ephemeral port, getPort() returns the bound port.
        // -ifNotExists so the first client connection may create the
        // in-memory database over TCP.
        server = Server.createTcpServer("-tcpPort", "0", "-ifNotExists").start();
        url = "jdbc:h2:tcp://localhost:" + server.getPort()
                + "/mem:pooltest_" + UUID.randomUUID();
        // Create the database and pin its lifetime to the server. Without
        // DB_CLOSE_DELAY an in-memory database is destroyed as soon as the
        // pool closes its last physical connection - which eviction,
        // purge() and an alternate-credential reconnect all do - and the
        // next connection would silently get an empty database, letting
        // assertions about committed data pass against a database that is
        // not the one the test wrote to. The setting is applied here rather
        // than in the URL because it needs admin rights, and every client
        // connection would have to reapply it.
        try (Connection admin = DriverManager.getConnection(url, "sa", "");
                Statement st = admin.createStatement()) {
            st.execute("SET DB_CLOSE_DELAY -1");
        }
    }

    @AfterAll
    public static void stopDatabase() {
        if (server != null) {
            // Stopping the server also kills any connection a test
            // leaked, keeping test classes isolated from each other.
            server.stop();
            server = null;
        }
    }

    protected static String getUrl() {
        return url;
    }

    /*
     * Creates a DataSource pointing at this test class' database. JMX is
     * disabled by default to keep MBean registration out of functional
     * tests (it has its own test area). Initial/min idle sizes are pinned
     * to the minimum so each test observes exactly the connections it
     * asks for; tests override these explicitly when needed.
     */
    protected DataSource newDataSource() {
        DataSource ds = new DataSource();
        ds.setUrl(url);
        ds.setDriverClassName("org.h2.Driver");
        ds.setUsername("sa");
        ds.setPassword("");
        ds.getPoolProperties().setJmxEnabled(false);
        ds.getPoolProperties().setInitialSize(1);
        ds.getPoolProperties().setMinIdle(0);
        return manage(ds);
    }

    /* Registers an externally constructed DataSource for teardown closing. */
    protected <D extends DataSource> D manage(D ds) {
        createdDataSources.add(ds);
        return ds;
    }

    @AfterEach
    public void closeDataSources() {
        for (DataSource ds : createdDataSources) {
            try {
                ds.close();
            } catch (Exception ignore) {
                // best effort; the @AfterAll server stop is the hard reset
            }
        }
        createdDataSources.clear();
    }

    /* A raw admin connection that bypasses the pool entirely. */
    protected Connection adminConnection() throws SQLException {
        return DriverManager.getConnection(url, "sa", "");
    }

    /*
     * Finds an interceptor of the given type in the chain behind a
     * borrowed connection. The objects the pool hands out are JDK proxies
     * whose invocation handler is the first interceptor of the chain, so
     * this is how a test reaches the pool's own internals without needing
     * a test-only interceptor of its own.
     */
    protected static <T extends JdbcInterceptor> T interceptorOf(Connection pooled,
            Class<T> type) {
        JdbcInterceptor interceptor = (JdbcInterceptor) Proxy.getInvocationHandler(pooled);
        while (interceptor != null) {
            if (type.isInstance(interceptor)) {
                return type.cast(interceptor);
            }
            interceptor = interceptor.getNext();
        }
        return null;
    }

    /*
     * An interceptor that always throws from disconnected(), shared by the
     * tests that pin how the disconnect path isolates a misbehaving link of
     * the chain. It is configured through the jdbcInterceptors class-name
     * list, so it must stay a public static class with a no-arg constructor.
     */
    public static class BoomInterceptor extends JdbcInterceptor {
        @Override
        public void reset(ConnectionPool parent, PooledConnection con) {
            // empty
        }

        @Override
        public void disconnected(ConnectionPool parent, PooledConnection con,
                boolean finalizing) {
            throw new IllegalStateException("boom from disconnected()");
        }
    }

    /*
     * The driver's own object behind a pooled connection's statement. For
     * a prepared statement this is the honest oracle for "the session was
     * not asked to prepare this SQL again": two handles onto the same
     * instance mean one physical prepare, and {@code isClosed()} on it
     * says what really happened to the statement.
     */
    protected static Statement driverStatement(Statement pooled) throws SQLException {
        return pooled.unwrap(Statement.class);
    }

    /*
     * Number of physical sessions on the database other than the admin
     * connection this method itself opens - i.e. the connections the
     * pool (or a test) currently holds.
     */
    protected int poolSessions() throws SQLException {
        try (Connection admin = adminConnection();
                Statement st = admin.createStatement();
                ResultSet rs = st.executeQuery(
                        "SELECT COUNT(*) FROM INFORMATION_SCHEMA.SESSIONS")) {
            rs.next();
            return rs.getInt(1) - 1; // subtract this admin session
        }
    }

    /* Polls {@link #poolSessions()} until it reaches the expected count. */
    protected void awaitSessions(int expected) throws SQLException {
        long deadline = System.currentTimeMillis() + AWAIT_TIMEOUT_MS;
        int observed = poolSessions();
        while (observed != expected) {
            if (System.currentTimeMillis() > deadline) {
                throw new AssertionError("Timed out waiting for " + expected
                        + " physical sessions, last observed " + observed);
            }
            sleep(25);
            observed = poolSessions();
        }
    }

    /*
     * Sets an H2 session variable on the physical session behind this
     * connection. It is visible only to that same physical session,
     * which makes it a session identity probe.
     */
    protected void setSessionMarker(Connection c, int value) throws SQLException {
        try (Statement st = c.createStatement()) {
            st.execute("SET @POOL_TEST_MARKER = " + value);
        }
    }

    /* The marker visible on this connection's physical session, or null. */
    protected Integer sessionMarker(Connection c) throws SQLException {
        try (Statement st = c.createStatement();
                ResultSet rs = st.executeQuery("SELECT @POOL_TEST_MARKER")) {
            rs.next();
            Object value = rs.getObject(1);
            return value == null ? null : Integer.valueOf(((Number) value).intValue());
        }
    }

    /* Polls a condition until it holds or the await timeout expires. */
    protected static void awaitUntil(BooleanSupplier condition) {
        long deadline = System.currentTimeMillis() + AWAIT_TIMEOUT_MS;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) {
                throw new AssertionError(
                        "Condition not met within " + AWAIT_TIMEOUT_MS + " ms");
            }
            sleep(25);
        }
    }

    protected static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting", e);
        }
    }

    /* Unique table name for the duration of one test. */
    protected static String uniqueTable() {
        return "T_" + System.nanoTime();
    }

    /* Committed row count of a table, observed through an admin connection. */
    protected int adminRowCount(String table) throws SQLException {
        try (Connection admin = adminConnection();
                Statement st = admin.createStatement();
                ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + table)) {
            rs.next();
            return rs.getInt(1);
        }
    }
}
