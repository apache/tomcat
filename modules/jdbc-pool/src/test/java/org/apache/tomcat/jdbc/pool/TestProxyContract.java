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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
 * {@code MultipleCloseTest} and {@code EqualsHashCodeTest}. Note that
 * the pool's own {@code equals}/{@code hashCode} handling <em>below</em>
 * the facade (with {@code useDisposableConnectionFacade=false}) is not
 * covered here on purpose - see TESTING-PLAN.md.</p>
 */
class TestProxyContract extends JdbcPoolBaseTest {

    @Test
    void aClosedFacadeStaysClosedAfterTheSessionIsReborrowed() throws Exception {
        DataSource ds = newDataSource(); // the disposable facade is on by default
        ds.createPool();

        Connection first = ds.getConnection();
        setSessionMarker(first, 31);
        assertFalse(first.isClosed());

        first.close();
        assertTrue(first.isClosed(), "a returned facade must report itself closed");

        try (Connection second = ds.getConnection()) {
            assertEquals(Integer.valueOf(31), sessionMarker(second),
                    "the next borrower must get the very same physical session back");
            assertFalse(second.isClosed(), "the new facade must be usable");

            assertTrue(first.isClosed(),
                    "a closed facade must stay closed while its session works for somebody else");
            assertFalse(first.isValid(1),
                    "a closed facade must report itself invalid, not its session's state");
            assertThrows(SQLException.class, first::createStatement,
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
        assertTrue(pooled.isClosed());

        // exactly one return took place, so the session is idle once
        assertEquals(1, ds.getIdle());
        assertEquals(1, poolSessions());
    }

    @Test
    void everyBorrowGetsItsOwnFacadeIdentity() throws Exception {
        DataSource ds = newDataSource();
        ds.createPool();

        Connection first = ds.getConnection();
        assertTrue(first.equals(first), "a facade must be equal to itself");
        int firstHash = first.hashCode();
        assertEquals(firstHash, first.hashCode(), "hashCode must be stable");

        first.close();
        assertTrue(first.equals(first),
                "closing must not change the identity of a facade");
        assertEquals(firstHash, first.hashCode(),
                "closing must not change the hash code of a facade");

        try (Connection second = ds.getConnection()) {
            assertFalse(first.equals(second),
                    "two borrows are two facades even over one physical session");
            assertFalse(second.equals(first));
            assertNotEquals(firstHash, second.hashCode(),
                    "two facades must not share an identity hash");
            assertEquals(second.hashCode(), second.hashCode());
        }
    }

    @Test
    void thePhysicalSessionStaysReachableThroughThePooledConnectionInterface()
            throws Exception {
        DataSource ds = newDataSource();
        ds.createPool();

        try (Connection pooled = ds.getConnection()) {
            assertInstanceOf(PooledConnection.class, pooled,
                    "the facade must also expose the pooled connection interface");
            Connection physical = ((PooledConnection) pooled).getConnection();
            assertNotNull(physical, "the underlying session must be reachable");
            assertTrue(physical.getClass().getName().startsWith("org.h2."),
                    "the underlying session must be the driver's own object, was "
                            + physical.getClass().getName());
            assertEquals(getUrl(), physical.getMetaData().getURL(),
                    "the reachable session must be the one the pool opened");

            assertTrue(pooled.isWrapperFor(Connection.class));
            assertFalse(pooled.isWrapperFor(javax.sql.XAConnection.class),
                    "a plain pool must not claim to be an XA wrapper");
        }
    }
}
