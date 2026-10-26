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
import java.sql.ResultSet;
import java.sql.Statement;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;

/**
 * rollbackOnReturn/commitOnReturn must terminate the transaction of a
 * connection that was left in a transaction by its borrower, regardless of
 * the pool-level defaultAutoCommit property - the documented semantics key
 * off the connection's actual state, and the property must not act as a
 * silent kill switch for the feature.
 */
class TestTerminateTransactionOnReturn extends JdbcPoolBaseTest {

    private String createTable(DataSource ds) throws Exception {
        String t = uniqueTable();
        try (Connection c = ds.getConnection();
                Statement st = c.createStatement()) {
            st.execute("CREATE TABLE " + t + " (v INT)");
        }
        return t;
    }

    private int rowCount(Connection c, String table) throws Exception {
        try (Statement st = c.createStatement();
                ResultSet rs = st.executeQuery(
                        "SELECT COUNT(*) FROM " + table)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    @Test
    void rollbackOnReturnIsNotGatedOnDefaultAutoCommitFalse() throws Exception {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setRollbackOnReturn(true);
        // defaultAutoCommit is deliberately left at its default (null)
        String t = createTable(ds);

        Connection c1 = ds.getConnection();
        c1.setAutoCommit(false);
        try (Statement st = c1.createStatement()) {
            st.executeUpdate("INSERT INTO " + t + " VALUES (1)");
        }
        c1.close(); // returned mid-transaction -> must be rolled back

        Assertions.assertEquals(0, adminRowCount(t),
                "uncommitted work must not survive the return");
        // The pool reuses the same idle session; a still-open transaction
        // would make the previous borrower's work visible right here.
        try (Connection c2 = ds.getConnection()) {
            Assertions.assertEquals(0, rowCount(c2, t),
                    "the next borrower must not observe the previous "
                            + "borrower's uncommitted work");
        }
    }

    @Test
    void commitOnReturnIsNotGatedOnDefaultAutoCommitFalse() throws Exception {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setCommitOnReturn(true);
        // defaultAutoCommit is deliberately left at its default (null)
        String t = createTable(ds);

        Connection c1 = ds.getConnection();
        c1.setAutoCommit(false);
        try (Statement st = c1.createStatement()) {
            st.executeUpdate("INSERT INTO " + t + " VALUES (1)");
        }
        c1.close(); // returned mid-transaction -> must be committed

        Assertions.assertEquals(1, adminRowCount(t),
                "commitOnReturn must persist the work on return, even "
                        + "without defaultAutoCommit=false");
    }

    @Test
    void withoutEitherFlagTheTransactionSurvivesTheReturn() throws Exception {
        DataSource ds = newDataSource();
        // neither rollbackOnReturn nor commitOnReturn is set
        String t = createTable(ds);

        Connection c1 = ds.getConnection();
        c1.setAutoCommit(false);
        try (Statement st = c1.createStatement()) {
            st.executeUpdate("INSERT INTO " + t + " VALUES (1)");
        }
        c1.close(); // returned mid-transaction -> pool must not touch it

        try (Connection c2 = ds.getConnection()) {
            Assertions.assertEquals(1, rowCount(c2, t),
                    "without either flag the pool must leave the "
                            + "transaction untouched");
        }
    }

    @Test
    void rollbackWinsWhenBothFlagsAreSet() throws Exception {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setRollbackOnReturn(true);
        ds.getPoolProperties().setCommitOnReturn(true);
        String t = createTable(ds);

        Connection c1 = ds.getConnection();
        c1.setAutoCommit(false);
        try (Statement st = c1.createStatement()) {
            st.executeUpdate("INSERT INTO " + t + " VALUES (1)");
        }
        c1.close(); // rollback takes precedence over commit

        Assertions.assertEquals(0, adminRowCount(t),
                "when both flags are set, rollbackOnReturn takes "
                        + "precedence");
    }
}
