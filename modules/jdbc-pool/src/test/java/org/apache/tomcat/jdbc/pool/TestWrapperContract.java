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

import javax.sql.XAConnection;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;

/**
 * The {@link java.sql.Wrapper} contract of the connection handle the pool
 * hands out. Per the contract, {@code isWrapperFor} must answer true for an
 * interface the handle itself implements, and {@code unwrap} must fail as an
 * {@link SQLException} for an interface nothing behind the handle provides -
 * never hand back a silent {@code null}. The handle implements
 * {@link java.sql.Connection} and {@link javax.sql.PooledConnection} (plus
 * {@link javax.sql.XAConnection} on XA pools), while unwrapping interfaces
 * the physical driver connection provides keeps reaching the physical
 * connection.
 */
class TestWrapperContract extends JdbcPoolBaseTest {

    @Test
    void unwrapToXaConnectionOnAPlainPoolFailsAsNotAWrapperNotSilentlyReturnsNull()
            throws Exception {
        DataSource ds = newDataSource();
        ds.createPool();

        try (Connection pooled = ds.getConnection()) {
            SQLException thrown = Assertions.assertThrows(SQLException.class,
                    () -> pooled.unwrap(XAConnection.class),
                    "unwrap must throw for an interface nothing behind the "
                    + "handle provides, not hand back a silent null");
            Assertions.assertTrue(thrown.getMessage().contains("Not a wrapper"),
                    "the failure must say it is a not-a-wrapper case: "
                    + thrown.getMessage());
            // The two Wrapper queries must agree; they already did, pin that.
            Assertions.assertFalse(pooled.isWrapperFor(XAConnection.class));
        }
    }

    @Test
    void theHandleIsAWrapperOfTheInterfacesItImplementsItself()
            throws Exception {
        DataSource ds = newDataSource();
        ds.createPool();

        try (Connection pooled = ds.getConnection()) {
            // The handle IS a javax.sql.PooledConnection - a cast succeeds,
            // so isWrapperFor must answer true and unwrap must answer with
            // it, not report "Not a wrapper".
            Assertions.assertTrue(pooled.isWrapperFor(javax.sql.PooledConnection.class),
                    "isWrapperFor must honour an interface the handle itself implements");
            Object unwrapped = pooled.unwrap(javax.sql.PooledConnection.class);
            Assertions.assertSame(pooled, unwrapped,
                    "unwrapping an interface to the interface itself must return the handle");
        }
    }

    @Test
    void unwrappingThePrimaryInterfaceStillReachesThePhysicalConnection()
            throws Exception {
        DataSource ds = newDataSource();
        ds.createPool();

        try (Connection pooled = ds.getConnection()) {
            // The established idiom: the physical driver connection is a
            // java.sql.Connection too, and unwrapping interfaces the
            // physical connection provides must keep reaching it.
            Assertions.assertTrue(pooled.isWrapperFor(Connection.class));
            Connection physical = pooled.unwrap(Connection.class);
            Assertions.assertNotSame(pooled, physical,
                    "unwrap(Connection.class) has always returned the physical connection");
            Assertions.assertTrue(
                    physical.getClass().getName().startsWith("org.h2."),
                    "expected the driver's own connection object, got "
                    + physical.getClass().getName());
        }
    }

    @Test
    void theInternalPooledConnectionUnwrapIdiomIsUnchanged() throws Exception {
        DataSource ds = newDataSource();
        ds.createPool();

        try (Connection pooled = ds.getConnection()) {
            // The pool's own introspection idiom (used in tests and by
            // interceptors): unwrap to the internal pooled connection.
            org.apache.tomcat.jdbc.pool.PooledConnection internal =
                    pooled.unwrap(org.apache.tomcat.jdbc.pool.PooledConnection.class);
            Assertions.assertSame(
                    interceptorOf(pooled, ProxyConnection.class).getConnection(),
                    internal,
                    "unwrapping to the internal pooled connection must keep "
                    + "returning the pool's own object");
        }
    }

    @Test
    void anXaPoolStillUnwrapsToTheDriversXaConnection() throws Exception {
        org.h2.jdbcx.JdbcDataSource inner = new org.h2.jdbcx.JdbcDataSource();
        inner.setUrl(getUrl());
        inner.setUser("sa");
        inner.setPassword("");
        XADataSource ds = new XADataSource();
        ds.setDriverClassName("org.h2.Driver");
        ds.getPoolProperties().setJmxEnabled(false);
        ds.getPoolProperties().setInitialSize(1);
        ds.getPoolProperties().setMinIdle(0);
        ds.getPoolProperties().setDataSource(inner);
        manage(ds);
        ds.createPool();

        try (Connection pooled = ds.getConnection()) {
            Assertions.assertTrue(pooled.isWrapperFor(XAConnection.class));
            Object unwrapped = pooled.unwrap(XAConnection.class);
            // Established XA behavior: the driver's XA connection object,
            // not the handle. Pin it so the null-target fix above does not
            // silently re-point this at the handle.
            Assertions.assertNotNull(unwrapped);
            Assertions.assertInstanceOf(XAConnection.class, unwrapped);
            Assertions.assertNotSame(pooled, unwrapped);
        }
    }
}
