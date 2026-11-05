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

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;

/**
 * Three paths take a connection out of a queue, call into the driver over
 * it, and then either hand it to the other queue or release it: the borrow
 * that reconnects before handing the session over, the return that checks
 * and terminates the transaction on the way back, and the idle sweep that
 * tests and reconnects outside either path. Each of them is guarded against
 * an unchecked exception, because a throw escaping the window between the
 * two queues leaves the connection in neither of them - invisible to the
 * abandoned check, to the eviction sweep and to the close drain alike, never
 * disconnected, and gone from the maxActive budget for the life of the pool.
 * <p>
 * An {@code Error} reaches those calls the same way it reaches the rest of
 * the pool: the driver, or the wrapper around it that a web application
 * class loader loaded, fails with a {@link LinkageError} - the
 * {@link NoClassDefFoundError} of a context that has since stopped is that
 * loader's ordinary failure, not an extraordinary one. A guard that catches
 * only exceptions lets exactly that arrival strand the connection, which is
 * the loss the guard exists to prevent. The three tests below put an Error
 * behind each of the three windows and require of each the outcome the
 * guard's own comment promises: the connection out of service, its slot
 * returned, and the work - the close, the borrow, the sweep - reporting the
 * failure rather than being broken by it.
 */
class TestThrowingDriverOnClaimedPaths extends JdbcPoolBaseTest {

    /*
     * The physical method the connection should fail from with an Error, or
     * null to let everything through, and the one-shot for the driver's own
     * connect(). Static, because the driver is instantiated by class name and
     * the tests alone decide when the window opens and shuts.
     */
    private static final AtomicReference<String> THROW_FROM = new AtomicReference<>();
    private static final AtomicBoolean FAIL_NEXT_CONNECT = new AtomicBoolean();
    private static final AtomicBoolean FAIL_NEXT_RESET = new AtomicBoolean();
    private static final AtomicInteger CONNECTS = new AtomicInteger();

    private static void resetDriver() {
        THROW_FROM.set(null);
        FAIL_NEXT_CONNECT.set(false);
        FAIL_NEXT_RESET.set(false);
        CONNECTS.set(0);
    }

    /*
     * One-slot pool on the throwing driver, with no competing sweeper: the
     * single slot makes a connection that went missing between the queues
     * visible at once, as the pool can no longer hand anything out.
     */
    private DataSource oneSlotPool() throws SQLException {
        DataSource ds = newDataSource();
        ds.setDriverClassName(ThrowingDriver.class.getName());
        ds.getPoolProperties().setInitialSize(0);
        ds.getPoolProperties().setMaxActive(1);
        // a real maxAge, so the reconnect is really attempted; the tests
        // expire the connection themselves, no clock waiting involved
        ds.getPoolProperties().setMaxAge(60000);
        // no timer: the sweep and the borrow are driven by the tests
        ds.getPoolProperties().setTimeBetweenEvictionRunsMillis(0);
        ds.getPoolProperties().setMaxWait(15000);
        ds.createPool();
        return ds;
    }

    /*
     * Expire a connection deterministically: rewind its last-connected clock
     * instead of waiting out a small maxAge.
     */
    private static void forceExpired(PooledConnection pc) throws Exception {
        Field lastConnected = PooledConnection.class.getDeclaredField("lastConnected");
        lastConnected.setAccessible(true);
        lastConnected.setLong(pc, System.currentTimeMillis() - 120000L);
    }

    /* The state a claimed window that failed correctly leaves behind. */
    private void assertNothingStranded(DataSource ds) throws Exception {
        Assertions.assertEquals(0, ds.getPool().getIdle());
        Assertions.assertEquals(0, ds.getPool().getActive());
        Assertions.assertEquals(0, ds.getPool().getSize());
        awaitSessions(0);
        // and the single slot was not lost: the next borrow connects afresh
        try (Connection again = ds.getConnection()) {
            Assertions.assertNotNull(again);
        }
        Assertions.assertEquals(0, ds.getPool().getActive());
        ds.close();
        awaitSessions(0);
    }

    @Test
    void anErrorFromTheDriverOnTheReturnDoesNotStrandTheConnection() throws Exception {
        resetDriver();
        DataSource ds = oneSlotPool();
        Connection first = ds.getConnection();
        awaitSessions(1);

        // shouldClose() asks the driver whether the session is closed, on the
        // way back from the busy queue
        THROW_FROM.set("isClosed");
        try {
            // shouldClose() asks the driver whether the session is closed
            // after the return claimed the connection out of the busy queue.
            // The driver's Error must not unwind the return with the
            // connection in neither queue.
            Assertions.assertDoesNotThrow(first::close);
        } finally {
            THROW_FROM.set(null);
        }

        assertNothingStranded(ds);
    }

    @Test
    void anErrorFromTheDriverOnTheIdleSweepDoesNotStrandTheConnection() throws Exception {
        resetDriver();
        DataSource ds = oneSlotPool();
        Connection first = ds.getConnection();
        awaitSessions(1);
        PooledConnection pc = (PooledConnection) first.unwrap(PooledConnection.class);
        first.close();
        forceExpired(pc);

        FAIL_NEXT_CONNECT.set(true);
        try {
            // reconnectIfExpired() closes the expired session and asks the
            // driver for another one, inside the window in which the sweep has
            // claimed the connection out of the idle queue. The sweep has to
            // read the Error as the failed check it is and end its round.
            Assertions.assertDoesNotThrow(() -> ds.getPool().testAllIdle());
        } finally {
            FAIL_NEXT_CONNECT.set(false);
        }

        Assertions.assertTrue(CONNECTS.get() >= 2,
                "the sweep must have reached the driver's connect() the way the failure was armed");
        assertNothingStranded(ds);
    }

    @Test
    void anErrorFromTheDriverOnTheBorrowFailsTheBorrowAndKeepsTheSlot() throws Exception {
        resetDriver();
        DataSource ds = oneSlotPool();
        Connection first = ds.getConnection();
        awaitSessions(1);
        PooledConnection pc = (PooledConnection) first.unwrap(PooledConnection.class);
        first.close();
        forceExpired(pc);

        FAIL_NEXT_CONNECT.set(true);
        try {
            // the borrow that reconnects an expired connection calls the
            // driver over the claimed window too; the borrower is owed the
            // failure the pool owes any refused borrow, and owed it as the
            // checked exception a borrow reports in
            Assertions.assertThrows(SQLException.class, ds::getConnection);
        } finally {
            FAIL_NEXT_CONNECT.set(false);
        }

        assertNothingStranded(ds);
    }

    @Test
    void anErrorFromTheDriverOnThePhysicalCloseDoesNotLoseTheSlot() throws Exception {
        resetDriver();
        DataSource ds = oneSlotPool();
        // No idle retention: the return destroys the connection, so the
        // driver's close runs on the release path.
        ds.getPoolProperties().setMaxIdle(0);

        Connection first = ds.getConnection();
        awaitSessions(1);
        THROW_FROM.set("close");
        try {
            // The driver's Error from the physical close must not escape the
            // return and leave the destroyed connection counted in the pool.
            Assertions.assertDoesNotThrow(first::close);
        } finally {
            THROW_FROM.set(null);
        }

        assertNothingStranded(ds);
    }

    @Test
    void anErrorWhileReconnectingWithOtherCredentialsKeepsTheServedSession() throws Exception {
        resetDriver();
        DataSource ds = oneSlotPool();
        ds.getPoolProperties().setAlternateUsernameAllowed(true);

        // One idle session of the pool's own user for the alternate-credential
        // borrow below to claim.
        try (Connection first = ds.getConnection()) {
            Assertions.assertNotNull(first);
        }
        awaitSessions(1);

        FAIL_NEXT_CONNECT.set(true);
        try {
            Assertions.assertThrows(SQLException.class,
                    () -> ds.getConnection("other-user", "other-password"));
        } finally {
            FAIL_NEXT_CONNECT.set(false);
        }

        // The Error did not destroy the session: it was put back into the
        // pool, so the slot is still accounted for and the next borrow uses
        // the same physical session instead of leaking it.
        Assertions.assertEquals(1, ds.getSize(),
                "the served session must still hold its maxActive slot");
        Assertions.assertEquals(0, ds.getActive());
        Assertions.assertEquals(1, poolSessions(),
                "the served session must still be open after the failed reconnect");
        try (Connection again = ds.getConnection()) {
            Assertions.assertNotNull(again);
        }
        Assertions.assertEquals(0, ds.getActive());

        ds.close();
        awaitSessions(0);
    }

    @Test
    void anErrorFromTheDriverOnInitialCreationFailsTheBorrowAndKeepsTheSlot() throws Exception {
        resetDriver();
        DataSource ds = oneSlotPool();

        FAIL_NEXT_CONNECT.set(true);
        try {
            // The first borrow takes a maxActive slot before the driver is
            // called. The Error from connect() must fail the borrow as a
            // checked exception and give the slot back.
            Assertions.assertThrows(SQLException.class, ds::getConnection);
        } finally {
            FAIL_NEXT_CONNECT.set(false);
        }

        Assertions.assertEquals(1, CONNECTS.get(),
                "the borrow must have reached the driver's connect()");
        assertNothingStranded(ds);
    }

    @Test
    void anErrorFromTheDriverDuringTheInitialPreloadFailsPoolCreation() throws Exception {
        resetDriver();
        DataSource ds = newDataSource();
        ds.setDriverClassName(ThrowingDriver.class.getName());
        ds.getPoolProperties().setInitialSize(1);
        ds.getPoolProperties().setMaxActive(1);
        ds.getPoolProperties().setTimeBetweenEvictionRunsMillis(0);
        ds.getPoolProperties().setMaxWait(15000);

        FAIL_NEXT_CONNECT.set(true);
        try {
            Assertions.assertThrows(SQLException.class, ds::createPool);
        } finally {
            FAIL_NEXT_CONNECT.set(false);
        }

        awaitSessions(0);
    }

    @Test
    void anErrorFromAnInterceptorDuringSetupDoesNotStrandTheConnection() throws Exception {
        resetDriver();
        DataSource ds = newDataSource();
        ds.setDriverClassName(ThrowingDriver.class.getName());
        ds.getPoolProperties().setInitialSize(0);
        ds.getPoolProperties().setMaxActive(1);
        ds.getPoolProperties().setTimeBetweenEvictionRunsMillis(0);
        ds.getPoolProperties().setMaxWait(15000);
        ds.getPoolProperties().setJdbcInterceptors(ErrorOnResetInterceptor.class.getName());
        ds.createPool();

        FAIL_NEXT_RESET.set(true);
        try {
            // setupConnection() has already claimed the connection into
            // busy. The Error from the interceptor reset must be reported
            // as a failed borrow and the connection taken out of service.
            Assertions.assertThrows(SQLException.class, ds::getConnection);
        } finally {
            FAIL_NEXT_RESET.set(false);
        }

        Assertions.assertEquals(0, ds.getPool().getIdle());
        Assertions.assertEquals(0, ds.getPool().getActive());
        Assertions.assertEquals(0, ds.getPool().getSize());
        awaitSessions(0);
        try (Connection again = ds.getConnection()) {
            Assertions.assertNotNull(again);
        }
        ds.close();
        awaitSessions(0);
    }

    /**
     * Interceptor failing the borrow-time reset once with an Error. Must be
     * public with a no-argument constructor because it is instantiated by
     * class name from {@code jdbcInterceptors}.
     */
    public static class ErrorOnResetInterceptor extends JdbcInterceptor {

        public ErrorOnResetInterceptor() {
        }

        @Override
        public void reset(ConnectionPool parent, PooledConnection con) {
            if (parent != null && FAIL_NEXT_RESET.compareAndSet(true, false)) {
                throw new NoClassDefFoundError(
                        "the context that loaded this interceptor has stopped");
            }
        }
    }

    /**
     * Driver handing out the real H2 connections wrapped in a proxy that
     * fails the currently armed method, or its own connect() when that is
     * armed once, with an Error. Configured through {@code driverClassName},
     * so it must stay a public static class with a no-arg constructor.
     */
    public static class ThrowingDriver implements Driver {

        private final Driver delegate = new org.h2.Driver();

        @Override
        public Connection connect(String url, Properties info) throws SQLException {
            // Counted before the window: an attempt that failed is still one
            // the test needs to see the sweep reached
            CONNECTS.incrementAndGet();
            if (FAIL_NEXT_CONNECT.compareAndSet(true, false)) {
                // The shape of a stopped context: the driver class itself is
                // still here, but a class it needs is not
                throw new NoClassDefFoundError(
                        "the context that loaded this driver wrapper has stopped");
            }
            Connection connection = delegate.connect(url, info);
            if (connection == null) {
                return null;
            }
            return (Connection) Proxy.newProxyInstance(
                    ThrowingDriver.class.getClassLoader(),
                    new Class<?>[] { Connection.class },
                    new ThrowingHandler(connection));
        }

        @Override
        public boolean acceptsURL(String url) throws SQLException {
            return delegate.acceptsURL(url);
        }

        @Override
        public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) throws SQLException {
            return delegate.getPropertyInfo(url, info);
        }

        @Override
        public int getMajorVersion() {
            return delegate.getMajorVersion();
        }

        @Override
        public int getMinorVersion() {
            return delegate.getMinorVersion();
        }

        @Override
        public boolean jdbcCompliant() {
            return delegate.jdbcCompliant();
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            return delegate.getParentLogger();
        }
    }

    /* Fails the armed method by name with an Error; delegates the rest. */
    private static class ThrowingHandler implements InvocationHandler {

        private final Connection vendor;

        ThrowingHandler(Connection vendor) {
            this.vendor = vendor;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (method.getName().equals(THROW_FROM.get())) {
                throw new NoClassDefFoundError("simulated stopped context from " + method.getName() + "()");
            }
            try {
                return method.invoke(vendor, args);
            } catch (InvocationTargetException x) {
                throw x.getCause();
            }
        }
    }
}
