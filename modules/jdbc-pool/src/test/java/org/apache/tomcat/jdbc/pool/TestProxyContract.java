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
 * for the callers that need it.
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
