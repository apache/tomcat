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
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;
import org.apache.tomcat.jdbc.pool.interceptor.StatementCache;

/**
 * How long the statement and result set handles the statement facade hands
 * out stay good.
 *
 * <p>The pool creates one facade per borrow, so the facade knows exactly when
 * its borrow ended. A handle that outlives it must not go on working: the
 * physical session it runs on has gone back to the pool and is somebody
 * else's by then, so continuing to execute on it means two components sharing
 * one session, one transaction and any server-side cursor the first of them
 * left open.</p>
 */
class TestStatementHandleLifetime extends JdbcPoolBaseTest {

    /** Borrows one connection, uses a statement and returns it. */
    private void useConnection(DataSource ds, int marker) throws SQLException {
        try (Connection con = ds.getConnection();
                Statement st = con.createStatement();
                ResultSet rs = st.executeQuery("SELECT 1")) {
            setSessionMarker(con, marker);
            Assertions.assertTrue(rs.next());
        }
    }

    @Test
    void aStatementDoesNotOutliveTheBorrowThatCreatedIt() throws Exception {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setMaxActive(1);
        ds.getPoolProperties().setMaxIdle(1);
        ds.createPool();

        Connection first = ds.getConnection();
        Statement survivor = first.createStatement();
        Assertions.assertFalse(survivor.isClosed());
        first.close();

        // The session is in use again by someone else.
        Connection second = ds.getConnection();
        setSessionMarker(second, 5150);

        Assertions.assertTrue(survivor.isClosed(),
                "a statement whose borrow ended must report itself closed");
        SQLException thrown = Assertions.assertThrows(SQLException.class,
                () -> survivor.execute("INSERT INTO NONEXISTENT_TABLE_YET VALUES (1)"),
                "a statement whose borrow ended must not run against the next borrower's session");
        Assertions.assertTrue(thrown.getMessage().contains("returned to the pool"),
                "the failure must say what happened, but said: " + thrown.getMessage());
        // The driver's own statement stays out of reach even through
        // unwrap(): the physical statement is still open, so only this
        // guard stands between a retired handle and the next borrower's
        // session.
        Assertions.assertThrows(SQLException.class,
                () -> survivor.unwrap(Statement.class),
                "a retired handle must not reach the driver through unwrap()");

        // Closing such a statement twice over stays the harmless no-op that a
        // closed statement's close() is.
        survivor.close();
        survivor.close();
        Assertions.assertEquals(Integer.valueOf(5150), sessionMarker(second),
                "the second borrower's session must be untouched");
        second.close();
    }

    @Test
    void aResultSetDoesNotOutliveTheBorrowItWasReadFrom() throws Exception {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setMaxActive(1);
        ds.getPoolProperties().setMaxIdle(1);
        ds.createPool();

        Connection first = ds.getConnection();
        Statement st = first.createStatement();
        ResultSet survivor = st.executeQuery("SELECT 1");
        Assertions.assertTrue(survivor.next(), "the cursor is live during the borrow");
        first.close();

        Assertions.assertTrue(survivor.isClosed(),
                "a result set whose borrow ended must report itself closed");
        SQLException thrown = Assertions.assertThrows(SQLException.class, survivor::next,
                "a result set whose borrow ended must not read the next borrower's session");
        Assertions.assertTrue(thrown.getMessage().contains("returned to the pool"),
                "the failure must say what happened, but said: " + thrown.getMessage());
        survivor.close();

        useConnection(ds, 5150);
    }

    /**
     * Invalidation belongs to the borrow, not to the physical connection: the
     * next borrower must get working handles over the very same session.
     */
    @Test
    void theNextBorrowerGetsWorkingHandlesOverTheSameSession() throws Exception {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setMaxActive(1);
        ds.getPoolProperties().setMaxIdle(1);
        ds.createPool();

        Connection first = ds.getConnection();
        Statement retired = first.createStatement();
        setSessionMarker(first, 4242);
        first.close();

        try (Connection second = ds.getConnection()) {
            Assertions.assertEquals(Integer.valueOf(4242), sessionMarker(second),
                    "the next borrower holds the same physical session");
            try (Statement st = second.createStatement();
                    ResultSet rs = st.executeQuery("SELECT 1")) {
                Assertions.assertTrue(rs.next(), "the new borrower's statement must work");
            }
            Assertions.assertTrue(retired.isClosed(),
                    "while the previous borrow's handle stays retired");
        }
    }

    /**
     * The statement cache is the one case where a statement is deliberately
     * handed to the next borrower, and the facade must not disturb it: the
     * cache hands its own handle to each borrow and the facade wraps it
     * afresh, so a cached statement arrives working.
     */
    @Test
    void aCachedStatementArrivesWorkingForTheNextBorrower() throws Exception {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setMaxActive(1);
        ds.getPoolProperties().setMaxIdle(1);
        ds.getPoolProperties().setJdbcInterceptors(
                StatementCache.class.getName() + "(prepared=true,callable=false,max=10)");
        ds.createPool();

        PreparedStatement retired;
        Connection first = ds.getConnection();
        retired = first.prepareStatement("SELECT ?");
        // captured while the handle is still live, since a retired handle no
        // longer reaches the driver at all
        Statement cached = driverStatement(retired);
        retired.setInt(1, 1);
        try (ResultSet rs = retired.executeQuery()) {
            Assertions.assertTrue(rs.next());
        }
        retired.close(); // goes into the cache
        first.close();

        try (Connection second = ds.getConnection()) {
            PreparedStatement recycled = second.prepareStatement("SELECT ?");
            Assertions.assertSame(cached, driverStatement(recycled),
                    "the cached statement must be the one the second borrower got");
            recycled.setInt(1, 2);
            try (ResultSet rs = recycled.executeQuery()) {
                Assertions.assertTrue(rs.next());
                Assertions.assertEquals(2, rs.getInt(1),
                        "the recycled statement must answer with this borrower's parameters");
            }
            Assertions.assertTrue(retired.isClosed(),
                    "and the handle the first borrower closed must stay closed");
            recycled.close();
        }
    }
}
