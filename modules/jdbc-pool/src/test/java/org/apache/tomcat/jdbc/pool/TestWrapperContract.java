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
 *
 * <p>The pool's own {@link org.apache.tomcat.jdbc.pool.PooledConnection} is
 * answered the same way through both entry points of the chain - the handle's
 * dispatch and the bottom interceptor's own public methods - and there
 * {@code isWrapperFor} must agree with what {@code unwrap} hands out. Once the
 * interceptor holds no pooled connection at all, the pair for that class is
 * the refusal: {@code isWrapperFor} says false and {@code unwrap} answers the
 * not-a-wrapper failure it predicts, rather than handing back a silent null.
 * The one answer the two entry points legitimately differ on is
 * {@link javax.sql.PooledConnection}, which the handle implements and the
 * interceptor does not: the contract asks each object about its own wrappers,
 * so both answers are pinned as the correct per-object pair.</p>
 */
class TestWrapperContract extends JdbcPoolBaseTest {

    private XADataSource newXaDataSource() {
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
        return manage(ds);
    }

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
        XADataSource ds = newXaDataSource();
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

    @Test
    void theInternalPooledConnectionIsAnsweredIdenticallyByBothEntryPoints()
            throws Exception {
        DataSource ds = newDataSource();
        ds.createPool();

        try (Connection pooled = ds.getConnection()) {
            ProxyConnection interceptor = interceptorOf(pooled, ProxyConnection.class);
            // What the handle's dispatch answers for the pool's own pooled
            // connection, the bottom interceptor's public method answers
            // too - one object on both entry points, pinning the
            // consistency the two entry points of the chain must keep.
            Object viaHandle = pooled.unwrap(
                    org.apache.tomcat.jdbc.pool.PooledConnection.class);
            Object viaInterceptor = interceptor.unwrap(
                    org.apache.tomcat.jdbc.pool.PooledConnection.class);
            Assertions.assertSame(viaInterceptor, viaHandle,
                    "the handle and the interceptor must answer the same object");
            Assertions.assertSame(interceptor.getConnection(), viaHandle,
                    "the answer both entry points give is the pool's own pooled connection");
            // The Wrapper contract: isWrapperFor must tell the truth about
            // what unwrap hands out. Both objects sit on - are indirectly a
            // wrapper of - the pooled connection their unwrap returns, so
            // both must answer true, not the false that contradicted unwrap.
            Assertions.assertTrue(
                    pooled.isWrapperFor(org.apache.tomcat.jdbc.pool.PooledConnection.class),
                    "isWrapperFor must agree with the unwrap the handle answers");
            Assertions.assertTrue(
                    interceptor.isWrapperFor(org.apache.tomcat.jdbc.pool.PooledConnection.class),
                    "isWrapperFor must agree with the unwrap the interceptor answers");
        }
    }

    @Test
    void theUnheldPooledConnectionIsRefusedAsNotAWrapperByTheInterceptor()
            throws Exception {
        DataSource ds = newDataSource();
        ds.createPool();

        Connection pooled = ds.getConnection();
        // Taken while the chain is intact: after the close the disposable
        // facade seals and no longer forwards to the interceptor.
        ProxyConnection interceptor = interceptorOf(pooled, ProxyConnection.class);
        pooled.close();
        // The released interceptor holds no pooled connection, so
        // isWrapperFor for that class says false - and the same query pair
        // must hold in this corner as while it is held: unwrap answers the
        // not-a-wrapper failure isWrapperFor predicts, the way the
        // XAConnection case has always done, not the silent null that
        // contradicts the refusal and surfaces as an unrelated
        // NullPointerException at the caller's first use.
        Assertions.assertFalse(
                interceptor.isWrapperFor(org.apache.tomcat.jdbc.pool.PooledConnection.class),
                "an interceptor holding nothing is not a wrapper of it");
        SQLException thrown = Assertions.assertThrows(SQLException.class,
                () -> interceptor.unwrap(org.apache.tomcat.jdbc.pool.PooledConnection.class),
                "unwrap must answer the not-a-wrapper failure isWrapperFor "
                + "predicts, not hand back a silent null");
        Assertions.assertTrue(thrown.getMessage().contains("Not a wrapper"),
                "the failure must say it is a not-a-wrapper case: "
                + thrown.getMessage());
    }

    @Test
    void theJavaxPooledConnectionAnswerFollowsEachObjectsOwnInterfaces()
            throws Exception {
        DataSource ds = newDataSource();
        ds.createPool();

        try (Connection pooled = ds.getConnection()) {
            ProxyConnection interceptor = interceptorOf(pooled, ProxyConnection.class);
            // The handle implements javax.sql.PooledConnection, so per the
            // contract it is a wrapper of it and unwraps to itself.
            Assertions.assertTrue(pooled.isWrapperFor(javax.sql.PooledConnection.class));
            Assertions.assertSame(pooled, pooled.unwrap(javax.sql.PooledConnection.class));
            // The interceptor is no such interface, and nothing it can reach
            // implements one - neither the pool's own pooled connection nor
            // the physical driver connection does - so its honest answer is
            // the not-a-wrapper pair. The entry points differ here because
            // they are different objects and the contract asks each object
            // about its own wrappers; pin that no realignment turns either
            // answer into a lie.
            Assertions.assertFalse(
                    interceptor.isWrapperFor(javax.sql.PooledConnection.class),
                    "the interceptor implements no javax.sql.PooledConnection");
            Assertions.assertThrows(SQLException.class,
                    () -> interceptor.unwrap(javax.sql.PooledConnection.class),
                    "unwrapping to an interface nothing behind the interceptor "
                    + "provides must fail, not hand back a foreign object");
        }
    }

    @Test
    void theXaConnectionIsAnsweredIdenticallyByBothEntryPoints() throws Exception {
        XADataSource ds = newXaDataSource();
        ds.createPool();

        try (Connection pooled = ds.getConnection()) {
            ProxyConnection interceptor = interceptorOf(pooled, ProxyConnection.class);
            Assertions.assertTrue(pooled.isWrapperFor(XAConnection.class));
            Assertions.assertTrue(interceptor.isWrapperFor(XAConnection.class));
            Object viaHandle = pooled.unwrap(XAConnection.class);
            Object viaInterceptor = interceptor.unwrap(XAConnection.class);
            Assertions.assertSame(viaInterceptor, viaHandle,
                    "both entry points must answer with the same XA connection");
            Assertions.assertNotSame(pooled, viaHandle,
                    "the XA answer is the driver's XA connection object, not the handle");
        }
    }

    @Test
    void thePlainPoolRefusalOfXaIsIdenticalThroughBothEntryPoints() throws Exception {
        DataSource ds = newDataSource();
        ds.createPool();

        try (Connection pooled = ds.getConnection()) {
            ProxyConnection interceptor = interceptorOf(pooled, ProxyConnection.class);
            // A plain pool refuses this class the same way through both
            // entry points, with isWrapperFor agreeing - pinning that the
            // pooled-connection answer above did not blur the XA boundary.
            Assertions.assertFalse(pooled.isWrapperFor(XAConnection.class));
            Assertions.assertFalse(interceptor.isWrapperFor(XAConnection.class));
            Assertions.assertThrows(SQLException.class,
                    () -> pooled.unwrap(XAConnection.class));
            Assertions.assertThrows(SQLException.class,
                    () -> interceptor.unwrap(XAConnection.class));
        }
    }
}
