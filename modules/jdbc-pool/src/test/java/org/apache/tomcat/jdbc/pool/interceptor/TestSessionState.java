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
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;
import org.apache.tomcat.jdbc.pool.DataSource;

/**
 * Expected functionality of session state across borrowers: a borrower
 * may change the session it was given, but the next one must find the
 * session in the state the pool was configured for - a session must never
 * carry one borrower's auto commit, isolation or read-only setting into
 * another borrower's work.
 *
 * <p>Scenario harvested from the retired legacy
 * {@code TestConnectionState}, with the database itself deciding whether
 * the state really was restored: a write that stays invisible to an admin
 * connection proves the physical session is in manual commit mode.</p>
 */
class TestSessionState extends JdbcPoolBaseTest {

    private DataSource configuredPool() {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setJdbcInterceptors(ConnectionState.class.getSimpleName());
        ds.getPoolProperties().setDefaultAutoCommit(Boolean.FALSE);
        ds.getPoolProperties()
                .setDefaultTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
        ds.getPoolProperties().setDefaultReadOnly(Boolean.FALSE);
        return ds;
    }

    @Test
    void aBorrowerSeesTheConfiguredDefaults() throws Exception {
        DataSource ds = configuredPool();
        ds.createPool();

        try (Connection c = ds.getConnection()) {
            assertFalse(c.getAutoCommit(), "the configured auto commit must apply");
            assertEquals(Connection.TRANSACTION_SERIALIZABLE, c.getTransactionIsolation(),
                    "the configured isolation level must apply");
            assertFalse(c.isReadOnly(), "the configured read-only state must apply");
        }
    }

    @Test
    void theDefaultsAreRestoredForTheNextBorrower() throws Exception {
        DataSource ds = configuredPool();
        ds.createPool();

        try (Connection first = ds.getConnection()) {
            setSessionMarker(first, 21);
            first.setAutoCommit(true);
            first.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            first.setReadOnly(true);
            assertTrue(first.getAutoCommit());
            assertTrue(first.isReadOnly());
        }

        try (Connection second = ds.getConnection()) {
            assertEquals(Integer.valueOf(21), sessionMarker(second),
                    "the same physical session must have been reused");
            assertFalse(second.getAutoCommit(),
                    "auto commit must be back at the pool default for the next borrower");
            assertEquals(Connection.TRANSACTION_SERIALIZABLE,
                    second.getTransactionIsolation(),
                    "the isolation level must be back at the pool default");
            assertFalse(second.isReadOnly(),
                    "the read-only flag must be back at the pool default");

            // database-side proof that the physical session really is not in
            // auto commit any more: the row stays invisible until committed
            String table = uniqueTable();
            try (Connection admin = adminConnection();
                    Statement st = admin.createStatement()) {
                st.execute("CREATE TABLE " + table + " (v INT)");
            }
            try (Statement st = second.createStatement()) {
                st.executeUpdate("INSERT INTO " + table + " VALUES (1)");
            }
            assertEquals(0, adminRowCount(table),
                    "an uncommitted write must not be visible on a session in manual commit mode");
            second.rollback();
        }
        assertEquals(1, poolSessions(), "the session must have survived the state changes");
    }

    @Test
    void theReadOnlyFlagDoesNotOutliveTheBorrower() throws Exception {
        DataSource ds = configuredPool();
        String table = uniqueTable();
        ds.createPool();
        try (Connection admin = adminConnection(); Statement st = admin.createStatement()) {
            st.execute("CREATE TABLE " + table + " (v INT)");
        }

        try (Connection reader = ds.getConnection()) {
            reader.setReadOnly(true);
            assertTrue(reader.isReadOnly(),
                    "the borrower's own change must be visible to it");
        }

        try (Connection writer = ds.getConnection()) {
            assertFalse(writer.isReadOnly(),
                    "the read-only flag must not leak to the next borrower");
            assertEquals(1, writer.createStatement()
                    .executeUpdate("INSERT INTO " + table + " VALUES (2)"),
                    "the next borrower must be able to write again");
            writer.commit();
        }
        assertEquals(1, adminRowCount(table));
    }
}
