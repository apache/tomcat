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
import java.sql.SQLException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;

/**
 * The physical connection field of a {@link PooledConnection} is transiently
 * {@code null} while a reconnect is in flight - the reconnect triggered by
 * expired max age or alternate credentials runs {@code disconnect(false)}
 * before {@code connect()} - and a failed reconnect leaves it {@code null}
 * until the connection is released, while the per-connection MBean
 * ({@link PooledConnectionMBean}) can still be registered. The delegation
 * methods exposed through that MBean must report the state - a
 * {@link SQLException} or a meaningful answer - instead of escaping a raw
 * {@link NullPointerException} as a JMX {@code UnknownRuntimeException}.
 * JMX is disabled in these tests; the methods are exercised directly on a
 * pooled connection object that never had a physical session, which is the
 * deterministic stand-in for the state such a registered MBean is polled in.
 */
class TestMBeanDelegationWhenDisconnected extends JdbcPoolBaseTest {

    /*
     * A pooled connection object that never had a physical session: the
     * direct, deterministic stand-in for the state the registered MBean can
     * be polled in during the reconnect window.
     */
    private static PooledConnection disconnectedPooledConnection(ConnectionPool pool) {
        return new PooledConnection(pool.getPoolProperties(), pool);
    }

    @Test
    void stateDelegationReportsClosedWithoutANullPointer() throws Exception {
        DataSource ds = newDataSource();
        ds.createPool();
        ConnectionPool pool = ds.getPool();

        PooledConnection disconnected = disconnectedPooledConnection(pool);

        // Pre-fix: NullPointerException.
        Assertions.assertTrue(disconnected.isClosed(),
                "a pooled connection without a physical connection is closed");
        // Declares no exception, so it must not throw at all.
        Assertions.assertDoesNotThrow(disconnected::clearWarnings);

        // The pool's own return/release decision (ConnectionPool's
        // release(path) uses this same VALIDATE_RETURN call): pre-fix the
        // NPE escaped shouldClose()'s catch (SQLException) after the
        // connection had already been removed from the busy count,
        // without release() ever running.
        Assertions.assertTrue(
                pool.shouldClose(disconnected, PooledConnection.VALIDATE_RETURN),
                "a pooled connection without a physical connection must be "
                        + "closed rather than returned to the pool");
    }

    @Test
    void attributeDelegationFailsWithSQLExceptionNotANullPointer() throws Exception {
        DataSource ds = newDataSource();
        ds.createPool();
        ConnectionPool pool = ds.getPool();

        PooledConnection disconnected = disconnectedPooledConnection(pool);

        // Pre-fix: NullPointerException, which JMX surfaces as an
        // UnknownRuntimeException instead of the declared SQLException.
        Assertions.assertThrows(SQLException.class, disconnected::getAutoCommit);
        Assertions.assertThrows(SQLException.class, disconnected::getCatalog);
        Assertions.assertThrows(SQLException.class, disconnected::getHoldability);
        Assertions.assertThrows(SQLException.class, disconnected::isReadOnly);
        Assertions.assertThrows(SQLException.class, disconnected::getSchema);
        Assertions.assertThrows(SQLException.class,
                disconnected::getTransactionIsolation);
    }

    @Test
    void delegationStillReachesTheLivePhysicalConnection() throws Exception {
        DataSource ds = newDataSource();
        Connection handle = ds.getConnection();

        PooledConnection pooled = handle.unwrap(PooledConnection.class);
        Assertions.assertFalse(pooled.isClosed());
        Assertions.assertTrue(pooled.getAutoCommit());
        Assertions.assertNotNull(pooled.getCatalog());
        Assertions.assertNotNull(pooled.getSchema());
        Assertions.assertFalse(pooled.isReadOnly());
        Assertions.assertTrue(pooled.getTransactionIsolation()
                >= Connection.TRANSACTION_NONE);
        Assertions.assertTrue(pooled.getHoldability() > 0);
        pooled.clearWarnings(); // must not throw

        handle.close(); // back to the pool: still physically connected
        Assertions.assertFalse(pooled.isClosed(),
                "a returned connection keeps its physical session");
        Assertions.assertTrue(pooled.getAutoCommit(),
                "the delegation methods still reach the idle session");
    }
}
