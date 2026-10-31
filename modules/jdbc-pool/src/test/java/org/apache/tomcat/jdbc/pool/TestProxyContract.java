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

import javax.sql.PooledConnection;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;

/**
 * Expected contract of the {@link java.sql.Connection} object the pool
 * hands out: it is a disposable facade over a pooled connection, so
 * closing it is permanent even though the physical session goes on
 * serving other borrowers, and the underlying session stays reachable
 * for the callers that need it - while the borrow is live; a handle that
 * reports itself closed refuses that accessor, as the two discarded-
 * handle scenarios pinned here show.
 *
 * <p>Scenario harvested from the retired legacy classes
 * {@code MultipleCloseTest} and {@code EqualsHashCodeTest}. The same
 * contract now also holds for configurations that still ask for the
 * (deprecated, no-effect) {@code useDisposableConnectionFacade=false};
 * that is pinned in {@link TestFacadeAlwaysUsed}.</p>
 */
class TestProxyContract extends JdbcPoolBaseTest {

    @Test
    void aClosedFacadeStaysClosedAfterTheSessionIsReborrowed() throws Exception {
        DataSource ds = newDataSource(); // the disposable facade is on by default
        ds.createPool();

        Connection first = ds.getConnection();
        setSessionMarker(first, 31);
        Assertions.assertFalse(first.isClosed());

        first.close();
        Assertions.assertTrue(first.isClosed(), "a returned facade must report itself closed");

        try (Connection second = ds.getConnection()) {
            Assertions.assertEquals(Integer.valueOf(31), sessionMarker(second),
                    "the next borrower must get the very same physical session back");
            Assertions.assertFalse(second.isClosed(), "the new facade must be usable");

            Assertions.assertTrue(first.isClosed(),
                    "a closed facade must stay closed while its session works for somebody else");
            Assertions.assertFalse(first.isValid(1),
                    "a closed facade must report itself invalid, not its session's state");
            Assertions.assertThrows(SQLException.class, first::createStatement,
                    "a disposed facade must refuse to hand out work for the session");
        }
    }

    @Test
    void closingTheSameFacadeTwiceIsHarmless() throws Exception {
        DataSource ds = newDataSource();
        ds.createPool();

        Connection pooled = ds.getConnection();
        pooled.close();
        pooled.close();
        Assertions.assertTrue(pooled.isClosed());

        // exactly one return took place, so the session is idle once
        Assertions.assertEquals(1, ds.getIdle());
        Assertions.assertEquals(1, poolSessions());
    }

    @Test
    void everyBorrowGetsItsOwnFacadeIdentity() throws Exception {
        DataSource ds = newDataSource();
        ds.createPool();

        Connection first = ds.getConnection();
        Assertions.assertTrue(first.equals(first), "a facade must be equal to itself");
        int firstHash = first.hashCode();
        Assertions.assertEquals(firstHash, first.hashCode(), "hashCode must be stable");

        first.close();
        Assertions.assertTrue(first.equals(first),
                "closing must not change the identity of a facade");
        Assertions.assertEquals(firstHash, first.hashCode(),
                "closing must not change the hash code of a facade");

        try (Connection second = ds.getConnection()) {
            Assertions.assertFalse(first.equals(second),
                    "two borrows are two facades even over one physical session");
            Assertions.assertFalse(second.equals(first));
            Assertions.assertNotEquals(firstHash, second.hashCode(),
                    "two facades must not share an identity hash");
            Assertions.assertEquals(second.hashCode(), second.hashCode());
        }
    }

    @SuppressWarnings("unlikely-arg-type") // Use of unlikely arg-type is deliberate
    @Test
    void equalsAgainstNullOrForeignObjectsIsFalseNotAnException()
            throws Exception {
        DataSource ds = newDataSource();
        ds.createPool();

        Connection pooled = ds.getConnection();
        Assertions.assertFalse(pooled.equals(null),
                "Object.equals(null) must answer false, not throw");
        Assertions.assertFalse(pooled.equals("a foreign object"),
                "equals against a non-proxy must answer false, not throw");
        // a proxy that is not the pool's own passes the proxy guard but
        // must still compare unequal - its handler is not this facade
        Object foreignProxy = java.lang.reflect.Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[] { Runnable.class },
                (p, m, a) -> null);
        Assertions.assertFalse(pooled.equals(foreignProxy),
                "equals against a foreign dynamic proxy must answer false");
        pooled.close();
        // the equals branch runs ahead of the disposal guard, so the same
        // contract must hold on a disposed facade too
        Assertions.assertFalse(pooled.equals(null),
                "a disposed facade must keep the equals(null) contract");
        Assertions.assertFalse(pooled.equals("a foreign object"),
                "a disposed facade must keep the equals(non-proxy) contract");
    }

    /*
     * The javax.sql.PooledConnection accessor on the handle answers the
     * borrow, not the pooled connection: a handle that reports itself
     * closed must refuse it rather than reveal the session below. The
     * state pinned here is the reachable one - an interceptor marked the
     * session discarded while this borrower still holds the handle, so
     * the physical session stays open until the late return retires it -
     * and the handle's own closed answer is pinned by the same probe the
     * validity tests use.
     */
    @Test
    void aDiscardedHandleRefusesToHandOutItsSession() throws Exception {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setJdbcInterceptors(
                DiscardMarkingInterceptor.class.getName());
        ds.createPool();

        Connection held = ds.getConnection();
        // While the borrow is live the accessor answers, as it must: the
        // caller that needs the session behind a live handle still gets it.
        Connection physical = ((PooledConnection) held).getConnection();
        Assertions.assertNotNull(physical,
                "precondition: a live handle must hand out the physical session");

        // The marking probe leaves the borrower holding a handle whose
        // session the pool has committed to retiring.
        Assertions.assertTrue(held.isValid(1),
                "precondition: the first probe finds the session live, at the driver");
        Assertions.assertTrue(held.isClosed(),
                "precondition: the interceptor's marking must have reached the handle");
        Assertions.assertEquals(1, poolSessions(),
                "precondition: the physical session is still open until the handle is returned");

        Assertions.assertThrows(SQLException.class,
                () -> ((PooledConnection) held).getConnection(),
                "a handle that reports itself closed must not hand out the very session"
                        + " the pool has committed to retiring");
        Assertions.assertEquals(1, poolSessions(),
                "the refusal is the handle's closed state, not a session the pool had"
                        + " already taken out of service");

        held.close();
        awaitSessions(0); // the late return completes the discard
    }

    /*
     * The other closed shape of the same accessor: the session was
     * disconnected behind the still-held handle - what an abandon or a
     * forced pool close does to a busy connection, which the handle
     * keeps referencing until the release resets the interceptor chain.
     * That window is pinned permanently here by making the reconnect the
     * disconnect is followed by fail: the pooled connection then holds no
     * physical session at all while the handle still stands on it, and
     * the accessor must fail the caller, not answer with that null -
     * which would surface as an unrelated NullPointerException at the
     * caller's first use of the "physical" session.
     */
    @Test
    void aSessionDisconnectedBehindAHeldHandleRefusesToBeHandedOut() throws Exception {
        DataSource ds = newDataSource();
        ds.createPool();

        Connection held = ds.getConnection();
        org.apache.tomcat.jdbc.pool.PooledConnection pooled =
                interceptorOf(held, ProxyConnection.class).getDelegateConnection();

        // A driver call to an unreachable port fails the reconnect, so the
        // disconnected-behind-the-handle state the abandon window holds
        // becomes the settled state this test observes.
        ds.getPoolProperties().setUrl("jdbc:h2:tcp://localhost:1/mem:reconnect_must_fail");
        Assertions.assertThrows(SQLException.class, () -> pooled.reconnect(),
                "precondition: the reconnect must fail and leave the pooled connection"
                        + " disconnected");
        ds.getPoolProperties().setUrl(getUrl());

        Assertions.assertNull(pooled.getConnection(),
                "precondition: the pooled connection no longer holds a physical session");
        Assertions.assertTrue(pooled.isDiscarded(),
                "precondition: the session is marked out of service");
        Assertions.assertTrue(held.isClosed(),
                "precondition: the handle holding that session reports itself closed");

        Assertions.assertThrows(SQLException.class,
                () -> ((PooledConnection) held).getConnection(),
                "a session the pool disconnected behind the handle must fail the accessor,"
                        + " not hand out the null its field holds by then");

        held.close(); // the late return releases the disconnected session
    }

    @Test
    void thePhysicalSessionStaysReachableThroughThePooledConnectionInterface()
            throws Exception {
        DataSource ds = newDataSource();
        ds.createPool();

        try (Connection pooled = ds.getConnection()) {
            Assertions.assertInstanceOf(PooledConnection.class, pooled,
                    "the facade must also expose the pooled connection interface");
            Connection physical = ((PooledConnection) pooled).getConnection();
            Assertions.assertNotNull(physical, "the underlying session must be reachable");
            Assertions.assertTrue(physical.getClass().getName().startsWith("org.h2."),
                    "the underlying session must be the driver's own object, was "
                            + physical.getClass().getName());
            Assertions.assertEquals(getUrl(), physical.getMetaData().getURL(),
                    "the reachable session must be the one the pool opened");

            Assertions.assertTrue(pooled.isWrapperFor(Connection.class));
            Assertions.assertFalse(pooled.isWrapperFor(javax.sql.XAConnection.class),
                    "a plain pool must not claim to be an XA wrapper");
        }
    }
}
