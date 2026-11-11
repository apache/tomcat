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
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;

/**
 * Between claiming a returned connection from the busy queue and handing
 * it to the idle queue (or releasing it), the return path calls into the
 * driver: a closed check, a transaction termination, a validation, a
 * warnings clear. Those calls fail with checked exceptions the return
 * path already handles, but nothing in the JDBC contract stops a driver
 * from failing them with an unchecked one instead. Such a throw must not
 * escape {@code close()} with the connection in neither queue: it would
 * be invisible to checkAbandoned(), to checkIdle() and to the close()
 * drain alike, never disconnected, and the pool would permanently lose
 * one of its maxActive slots per occurrence.
 */
class TestThrowingDriverOnReturn extends JdbcPoolBaseTest {

    /*
     * The method the pooled physical connections should fail from with a
     * RuntimeException, or null to let everything through. Static,
     * because the driver is instantiated by class name and the tests
     * alone decide when the failure window opens and closes.
     */
    private static final AtomicReference<String> THROW_FROM = new AtomicReference<>();

    /* One-slot pool on the throwing driver, with no competing sweeper. */
    private DataSource oneSlotPool(boolean rollbackOnReturn) throws SQLException {
        DataSource ds = newDataSource();
        ds.setDriverClassName(ThrowingDriver.class.getName());
        ds.getPoolProperties().setInitialSize(0);
        // exactly one slot: a stranded connection exhausts the pool at once
        ds.getPoolProperties().setMaxActive(1);
        // the tests drive the return themselves; a background sweep would
        // now be a working competitor for the idle assertions below
        ds.getPoolProperties().setTimeBetweenEvictionRunsMillis(0);
        ds.getPoolProperties().setRollbackOnReturn(rollbackOnReturn);
        ds.createPool();
        return ds;
    }

    /*
     * Borrow, make the driver throw from the named method during the
     * return, and require the return to cost the pool nothing: no throw
     * at the application, the connection disconnected, its slot back in
     * the size budget, and nothing stranded in either queue.
     */
    private void assertReturnSurvives(String method, boolean rollbackOnReturn) throws Exception {
        DataSource ds = oneSlotPool(rollbackOnReturn);
        Connection first = ds.getConnection();
        awaitSessions(1);

        THROW_FROM.set(method);
        try {
            // like a failed validation, an unchecked driver failure on the
            // return takes the connection out of service without letting
            // the driver's failure reach the application
            Assertions.assertDoesNotThrow(first::close);
        } finally {
            THROW_FROM.set(null);
        }

        // nothing stranded: neither queue holds it, the size budget got
        // its slot back, and the physical session really was closed
        awaitSessions(0);
        Assertions.assertEquals(0, ds.getPool().getIdle());
        Assertions.assertEquals(0, ds.getPool().getActive());
        Assertions.assertEquals(0, ds.getPool().getSize());

        // the single slot was not lost: the next borrow succeeds
        try (Connection again = ds.getConnection()) {
            Assertions.assertNotNull(again);
        }
        Assertions.assertEquals(0, ds.getPool().getActive());
        ds.close();
        awaitSessions(0);
    }

    @Test
    void uncheckedFailureOfTheClosedCheckDoesNotStrandTheReturn() throws Exception {
        // shouldClose() asks the driver whether the session is closed, and
        // only its SQLException is handled as a failed check
        assertReturnSurvives("isClosed", false);
    }

    @Test
    void uncheckedFailureOfTheTransactionTerminationDoesNotStrandTheReturn() throws Exception {
        // terminateTransaction() drives commit/rollback through the driver,
        // and only its SQLException is handled as a failed termination
        assertReturnSurvives("getAutoCommit", true);
    }

    @Test
    void uncheckedFailureOfTheWarningsClearDoesNotStrandTheReturn() throws Exception {
        // a connection that passed the checks can still fail on the way
        // into the idle queue, where the pool clears the driver's warnings
        assertReturnSurvives("clearWarnings", false);
    }

    /**
     * Driver handing out the real H2 connections wrapped in a proxy that
     * throws a RuntimeException from the currently armed method. Configured
     * through {@code driverClassName}, so it must stay a public static
     * class with a no-arg constructor.
     */
    public static class ThrowingDriver implements Driver {

        private final Driver delegate = new org.h2.Driver();

        @Override
        public Connection connect(String url, Properties info) throws SQLException {
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

    /* Fails the armed method by name; delegates everything else to H2. */
    private static class ThrowingHandler implements InvocationHandler {

        private final Connection vendor;

        ThrowingHandler(Connection vendor) {
            this.vendor = vendor;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (method.getName().equals(THROW_FROM.get())) {
                throw new IllegalStateException("boom from " + method.getName() + "()");
            }
            try {
                return method.invoke(vendor, args);
            } catch (InvocationTargetException x) {
                throw x.getCause();
            }
        }
    }
}
