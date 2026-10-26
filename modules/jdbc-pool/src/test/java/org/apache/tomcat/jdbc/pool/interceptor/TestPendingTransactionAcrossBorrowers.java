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
package org.apache.tomcat.jdbc.pool.interceptor;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;
import org.apache.tomcat.jdbc.pool.DataSource;

/**
 * A borrower that returns a connection while still in a transaction hands
 * that transaction to the pool. Without {@code rollbackOnReturn}/
 * {@code commitOnReturn} the pool leaves it open, and the next borrow's
 * state restore then runs against it: on many drivers the isolation and
 * read-only restores are deferred until the next transaction (or refused),
 * and restoring auto-commit to true commits whatever the previous borrower
 * left pending. The {@link ConnectionState} interceptor must therefore
 * terminate the pending transaction before restoring the configured
 * defaults - but only when there are defaults to restore, since a pool that
 * uses the interceptor purely to cache the state must leave the pool's own
 * documented transaction handling untouched.
 */
class TestPendingTransactionAcrossBorrowers extends JdbcPoolBaseTest {

    private DataSource enforcingPool() {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setJdbcInterceptors(ConnectionState.class.getSimpleName());
        ds.getPoolProperties().setDefaultAutoCommit(Boolean.TRUE);
        return ds;
    }

    private String createTable(DataSource ds) throws Exception {
        String t = uniqueTable();
        try (Connection c = ds.getConnection();
                Statement st = c.createStatement()) {
            st.execute("CREATE TABLE " + t + " (v INT)");
        }
        return t;
    }

    private void insertInTransaction(Connection c, String table) throws Exception {
        c.setAutoCommit(false);
        try (Statement st = c.createStatement()) {
            st.executeUpdate("INSERT INTO " + table + " VALUES (1)");
        }
    }

    private int rowCount(Connection c, String table) throws Exception {
        try (Statement st = c.createStatement();
                ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + table)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    @Test
    void restoringAutoCommitDoesNotCommitThePreviousBorrowersWork() throws Exception {
        DataSource ds = enforcingPool();
        String t = createTable(ds);

        Connection first = ds.getConnection();
        setSessionMarker(first, 33);
        insertInTransaction(first, t);
        first.close(); // returned mid-transaction, pool must not touch it

        try (Connection second = ds.getConnection()) {
            Assertions.assertEquals(Integer.valueOf(33), sessionMarker(second),
                    "the same physical session must have been reused");
            // the borrow alone ran the restore; nothing else was done here
        }
        // Same session, so the only way the row can be visible from outside
        // is that the restore committed it.
        Assertions.assertEquals(0, adminRowCount(t),
                "restoring the configured auto-commit must not commit the "
                        + "work the previous borrower abandoned");
    }

    @Test
    void theAbandonedTransactionIsNotInheritedByTheNextBorrower() throws Exception {
        DataSource ds = enforcingPool();
        String t = createTable(ds);

        Connection first = ds.getConnection();
        setSessionMarker(first, 31);
        insertInTransaction(first, t);
        first.close();

        try (Connection second = ds.getConnection()) {
            Assertions.assertEquals(Integer.valueOf(31), sessionMarker(second),
                    "the same physical session must have been reused");
            Assertions.assertEquals(0, rowCount(second, t),
                    "the next borrower must not find itself inside the "
                            + "previous borrower's transaction");
        }
    }

    @Test
    void withoutConfiguredDefaultsTheInterceptorLeavesTheTransactionAlone()
            throws Exception {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setJdbcInterceptors(ConnectionState.class.getSimpleName());
        // no default auto-commit/isolation/read-only/catalog: the
        // interceptor only caches the state, nothing is restored on borrow
        String t = createTable(ds);

        Connection first = ds.getConnection();
        setSessionMarker(first, 32);
        insertInTransaction(first, t);
        first.close();

        try (Connection second = ds.getConnection()) {
            Assertions.assertEquals(Integer.valueOf(32), sessionMarker(second),
                    "the same physical session must have been reused");
            Assertions.assertEquals(1, rowCount(second, t),
                    "a caching-only configuration must keep the pool's "
                            + "documented transaction handling untouched");
        }
    }

    @Test
    void aRestoreNeededOnlyForTheIsolationLevelStillTerminatesTheTransaction()
            throws Exception {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setJdbcInterceptors(ConnectionState.class.getSimpleName());
        ds.getPoolProperties().setDefaultAutoCommit(Boolean.FALSE);
        ds.getPoolProperties()
                .setDefaultTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
        String t = createTable(ds);

        Connection first = ds.getConnection();
        setSessionMarker(first, 34);
        // auto-commit stays at the configured default, so only the
        // isolation level must be restored for the next borrow - and that
        // restore is only honest if the abandoned transaction ends first
        first.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
        try (Statement st = first.createStatement()) {
            st.executeUpdate("INSERT INTO " + t + " VALUES (1)");
        }
        first.close(); // still in a transaction (auto-commit default false)

        try (Connection second = ds.getConnection()) {
            Assertions.assertEquals(Integer.valueOf(34), sessionMarker(second),
                    "the same physical session must have been reused");
            Assertions.assertEquals(Connection.TRANSACTION_READ_COMMITTED,
                    second.getTransactionIsolation(),
                    "the isolation level must be back at the pool default");
            Assertions.assertEquals(0, rowCount(second, t),
                    "the isolation-only restore must still terminate the "
                            + "transaction the previous borrower left open");
        }
        Assertions.assertEquals(0, adminRowCount(t),
                "the abandoned work must not have survived");
    }
}
