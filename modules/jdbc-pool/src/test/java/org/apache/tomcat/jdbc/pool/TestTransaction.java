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
import java.sql.Statement;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;

/**
 * Expected transaction functionality of pooled connections: commit,
 * rollback, and the state the pool restores between borrowers. Every
 * commit/rollback assertion is checked from an independent admin
 * connection, i.e. against what the database actually persisted.
 */
class TestTransaction extends JdbcPoolBaseTest {

    private String createTable(DataSource ds) throws Exception {
        String t = uniqueTable();
        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            st.execute("CREATE TABLE " + t + " (v INT)");
        }
        return t;
    }

    private void insert(Connection c, String table, int value) throws Exception {
        try (Statement st = c.createStatement()) {
            st.executeUpdate("INSERT INTO " + table + " VALUES (" + value + ")");
        }
    }

    @Test
    void autoCommitIsDefaultAndPersistsImmediately() throws Exception {
        DataSource ds = newDataSource();
        String t = createTable(ds);
        try (Connection c = ds.getConnection()) {
            Assertions.assertTrue(c.getAutoCommit(), "auto-commit is the pool default");
            insert(c, t, 1);
            // visible to others without close/commit
            Assertions.assertEquals(1, adminRowCount(t));
        }
    }

    @Test
    void explicitRollbackDiscardsWork() throws Exception {
        DataSource ds = newDataSource();
        String t = createTable(ds);
        try (Connection c = ds.getConnection()) {
            c.setAutoCommit(false);
            insert(c, t, 1);
            Assertions.assertEquals(0, adminRowCount(t), "uncommitted work is invisible");
            c.rollback();
            Assertions.assertEquals(0, adminRowCount(t));
        }
        Assertions.assertEquals(0, adminRowCount(t));
    }

    @Test
    void explicitCommitPersistsWork() throws Exception {
        DataSource ds = newDataSource();
        String t = createTable(ds);
        try (Connection c = ds.getConnection()) {
            c.setAutoCommit(false);
            insert(c, t, 1);
            c.commit();
            Assertions.assertEquals(1, adminRowCount(t));
            insert(c, t, 2);
            c.commit();
            Assertions.assertEquals(2, adminRowCount(t));
        }
    }

    @Test
    void rollbackOnReturnDiscardsUncommittedWork() throws Exception {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setDefaultAutoCommit(Boolean.FALSE);
        ds.getPoolProperties().setRollbackOnReturn(true);
        String t = createTable(ds);

        Connection c1 = ds.getConnection();
        insert(c1, t, 1);
        c1.close(); // returned without commit -> must be rolled back

        Assertions.assertEquals(0, adminRowCount(t), "uncommitted work must not survive");
        try (Connection c2 = ds.getConnection()) {
            try (Statement st = c2.createStatement()) {
                try (java.sql.ResultSet rs = st.executeQuery(
                        "SELECT COUNT(*) FROM " + t)) {
                    Assertions.assertTrue(rs.next());
                    Assertions.assertEquals(0, rs.getInt(1),
                            "the next borrower must not observe the previous work");
                }
            }
        }
    }

    @Test
    void configuredDefaultAutoCommitIsRestoredForNextBorrower() throws Exception {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setDefaultAutoCommit(Boolean.TRUE);
        ds.getPoolProperties().setJdbcInterceptors(
                "org.apache.tomcat.jdbc.pool.interceptor.ConnectionState");
        try (Connection c1 = ds.getConnection()) {
            Assertions.assertTrue(c1.getAutoCommit(), "configured default");
            c1.setAutoCommit(false);
            Assertions.assertFalse(c1.getAutoCommit());
        }
        try (Connection c2 = ds.getConnection()) {
            Assertions.assertTrue(c2.getAutoCommit(),
                    "configured default must be restored before the next borrow");
        }
    }

    @Test
    void configuredDefaultIsolationIsRestoredForNextBorrower() throws Exception {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setDefaultTransactionIsolation(
                Connection.TRANSACTION_READ_COMMITTED);
        ds.getPoolProperties().setJdbcInterceptors(
                "org.apache.tomcat.jdbc.pool.interceptor.ConnectionState");
        try (Connection c1 = ds.getConnection()) {
            Assertions.assertEquals(Connection.TRANSACTION_READ_COMMITTED,
                    c1.getTransactionIsolation(), "configured default");
            c1.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
            Assertions.assertEquals(Connection.TRANSACTION_SERIALIZABLE,
                    c1.getTransactionIsolation());
        }
        try (Connection c2 = ds.getConnection()) {
            Assertions.assertEquals(Connection.TRANSACTION_READ_COMMITTED,
                    c2.getTransactionIsolation(),
                    "configured default must not leak to the next borrower");
        }
    }

    @Test
    void configuredDefaultReadOnlyIsRestoredAndNextBorrowerCanWrite() throws Exception {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setDefaultReadOnly(Boolean.FALSE);
        ds.getPoolProperties().setJdbcInterceptors(
                "org.apache.tomcat.jdbc.pool.interceptor.ConnectionState");
        String t = createTable(ds);
        try (Connection c1 = ds.getConnection()) {
            c1.setReadOnly(true);
            Assertions.assertTrue(c1.isReadOnly());
        }
        try (Connection c2 = ds.getConnection()) {
            Assertions.assertFalse(c2.isReadOnly(),
                    "configured default must be restored before the next borrow");
            insert(c2, t, 1);
        }
        Assertions.assertEquals(1, adminRowCount(t));
    }

    @Test
    void transactionsAcrossBorrowBoundariesAreIndependent() throws Exception {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setMaxActive(2);
        String t = createTable(ds);
        try (Connection a = ds.getConnection(); Connection b = ds.getConnection()) {
            a.setAutoCommit(false);
            b.setAutoCommit(false);
            insert(a, t, 1);
            insert(b, t, 2);
            b.rollback();
            a.commit();
            Assertions.assertEquals(1, adminRowCount(t),
                    "one connection's rollback must not undo the other's commit");
        }
    }
}
