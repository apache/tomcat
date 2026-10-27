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
 * The {@code useDisposableConnectionFacade} property is deprecated and has
 * no effect: the pool always wraps the connection handle in a
 * {@link DisposableConnectionFacade}. Switching the facade off used to
 * expose the cached, re-armed interceptor chain directly, where a handle
 * kept from an earlier borrow could return or drive the connection of the
 * current borrower. These tests pin the facade against a configuration
 * that still asks for it to be switched off.
 */
class TestFacadeAlwaysUsed extends JdbcPoolBaseTest {

    @SuppressWarnings("deprecation")
    private DataSource facadelessConfiguration() {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setUseDisposableConnectionFacade(false);
        return ds;
    }

    @Test
    void aHandleFromDeprecatedFacadelessConfigurationStaysDisposable()
            throws Exception {
        DataSource ds = facadelessConfiguration();

        Connection first = ds.getConnection();
        setSessionMarker(first, 7);
        first.close(); // returned to the pool

        Connection second = ds.getConnection();
        Assertions.assertEquals(7, sessionMarker(second).intValue(),
                "precondition: the pool re-armed the same physical session");

        first.close(); // stale double-close from the previous borrower

        // Pre-deprecation, with the facade switched off this stale close ran
        // pool.returnConnection() on the connection the second borrower is
        // still using.
        Assertions.assertEquals(0, ds.getPool().getIdle(),
                "the stale close must not hand the current borrower's "
                        + "connection back to the pool");
        Assertions.assertEquals(1, ds.getPool().getActive());

        // The current borrower keeps full use of its connection.
        setSessionMarker(second, 9);
        Assertions.assertEquals(9, sessionMarker(second).intValue());

        // A stale call is refused instead of driving the live handle.
        Assertions.assertThrows(SQLException.class, first::getAutoCommit);

        second.close();
        Assertions.assertEquals(1, ds.getPool().getIdle());
    }

    @Test
    void aReleasedHandleFromDeprecatedFacadelessConfigurationKeepsTheContract()
            throws Exception {
        DataSource ds = facadelessConfiguration();

        Connection pooled = ds.getConnection();
        Assertions.assertTrue(pooled.equals(pooled));
        int hash = pooled.hashCode();
        pooled.close();

        // The disposed facade keeps answering: inert close, closed and
        // invalid answers, stable identity.
        Assertions.assertTrue(pooled.isClosed(),
                "a released handle reports itself closed");
        Assertions.assertFalse(pooled.isValid(1),
                "a released handle reports itself invalid");
        Assertions.assertNotNull(pooled.toString(),
                "a released handle stays printable for logging");
        Assertions.assertEquals(hash, pooled.hashCode(),
                "closing must not change the hash code of a handle");
        Assertions.assertTrue(pooled.equals(pooled),
                "closing must not change the identity of a handle");
        pooled.close(); // a further close stays a no-op
    }

    @Test
    void twoBorrowsOverOneSessionAreDistinctHandlesEvenInDeprecatedConfiguration()
            throws Exception {        DataSource ds = facadelessConfiguration();

        Connection first = ds.getConnection();
        setSessionMarker(first, 42);
        first.close();

        try (Connection second = ds.getConnection()) {
            Assertions.assertEquals(Integer.valueOf(42), sessionMarker(second),
                    "the test must actually be looking at one reused physical session");
            Assertions.assertFalse(first.equals(second),
                    "two borrows are two handles even over one physical session");
            Assertions.assertNotEquals(first.hashCode(), second.hashCode(),
                    "two handles must not share an identity hash");
        }
    }

    @SuppressWarnings("deprecation")
    @Test
    void anXaHandleFromDeprecatedFacadelessConfigurationStaysDisposable()
            throws Exception {
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
        ds.getPoolProperties().setUseDisposableConnectionFacade(false);
        manage(ds);
        ds.createPool();

        Connection first = ds.getConnection();
        setSessionMarker(first, 5);
        first.close();

        Connection second = ds.getConnection();
        Assertions.assertEquals(5, sessionMarker(second).intValue(),
                "precondition: the pool re-armed the same physical session");

        // The forced facade covers the XA proxy class too: a close through
        // the stale XA handle must not return the session the current
        // borrower is using.
        first.close();
        Assertions.assertEquals(0, ds.getPool().getIdle(),
                "the stale close must not hand the current borrower's "
                        + "connection back to the pool");
        Assertions.assertEquals(1, ds.getPool().getActive());
        Assertions.assertTrue(first.isClosed());
        Assertions.assertThrows(SQLException.class, first::getAutoCommit,
                "a stale XA handle must not drive the current borrower");

        second.close();
        Assertions.assertEquals(1, ds.getPool().getIdle());
    }
}
