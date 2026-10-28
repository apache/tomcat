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
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;

/**
 * The {@code useStatementFacade} property is deprecated and has no effect:
 * the pool always wraps the statements it hands out - and the result sets
 * read from them - in a {@link StatementFacade}. Switching the facade off
 * used to leave a handle kept after the borrow ended fully usable: it
 * executed against the physical session the next borrower held, and
 * {@code getConnection()} reported the driver's own connection, which
 * survives the borrow and bypasses every configured interceptor. These
 * tests pin the facade against a configuration that still asks for it to be
 * switched off.
 */
class TestStatementFacadeAlwaysUsed extends JdbcPoolBaseTest {

    /* One borrow at a time over one idle session, so the next borrow
     * demonstrably gets the very session the retired handle was made on. */
    @SuppressWarnings("deprecation")
    private DataSource facadelessConfiguration() throws SQLException {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setUseStatementFacade(false);
        ds.getPoolProperties().setMaxActive(1);
        ds.getPoolProperties().setMaxIdle(1);
        ds.createPool();
        return ds;
    }

    @Test
    void aStatementFromDeprecatedFacadelessConfigurationReportsThePooledHandle()
            throws Exception {
        DataSource ds = facadelessConfiguration();

        try (Connection pooled = ds.getConnection()) {
            try (Statement plain = pooled.createStatement()) {
                Assertions.assertSame(pooled, plain.getConnection(),
                        "the facadeless request must not resurrect the driver's "
                                + "connection behind getConnection()");
            }
            try (Statement prepared = pooled.prepareStatement("SELECT 1")) {
                Assertions.assertSame(pooled, prepared.getConnection(),
                        "a prepared statement must report the pooled handle as well");
            }
            // The retired handle of the very first test below would otherwise
            // still be able to drive the session through this leak.
            try (Statement st = pooled.createStatement();
                    ResultSet rs = st.executeQuery("SELECT 1")) {
                Assertions.assertSame(pooled, rs.getStatement().getConnection(),
                        "the connection reached through a result set must be the pooled handle");
            }
        }
    }

    @Test
    void aStatementFromDeprecatedFacadelessConfigurationIsRetiredWithTheBorrow()
            throws Exception {
        DataSource ds = facadelessConfiguration();

        Connection first = ds.getConnection();
        setSessionMarker(first, 7);
        Statement survivor = first.createStatement();
        first.close(); // returned to the pool

        Connection second = ds.getConnection();
        Assertions.assertEquals(7, sessionMarker(second).intValue(),
                "precondition: the pool re-armed the same physical session");

        // Pre-deprecation, with the facade switched off this handle simply
        // executed: on the session the second borrower is now using. Writing
        // the session marker is what makes such an intrusion observable.
        SQLException thrown = Assertions.assertThrows(SQLException.class,
                () -> survivor.execute("SET @POOL_TEST_MARKER = 999"),
                "a statement whose borrow ended must not run against the "
                        + "next borrower's session");
        Assertions.assertTrue(thrown.getMessage().contains(
                "The pooled connection this statement was created from has "
                        + "been returned to the pool."),
                "the failure must be the facade's wording, but said: "
                        + thrown.getMessage());
        Assertions.assertEquals(7, sessionMarker(second).intValue(),
                "the next borrower's session must be untouched");

        Assertions.assertTrue(survivor.isClosed(),
                "a retired handle reports itself closed");
        Assertions.assertTrue(survivor.equals(survivor),
                "equals stays answerable on the retired handle");
        int hash = survivor.hashCode();
        Assertions.assertEquals(hash, survivor.hashCode(),
                "hashCode stays a stable value on the retired handle");

        // Closing such a handle twice over stays the harmless no-op a closed
        // statement's close() is.
        survivor.close();
        survivor.close();

        // The second borrower keeps full use of its session.
        try (Statement st = second.createStatement();
                ResultSet rs = st.executeQuery("SELECT 1")) {
            Assertions.assertTrue(rs.next(),
                    "the current borrower must get working handles");
        }
        second.close();
    }

    @Test
    void aResultSetFromDeprecatedFacadelessConfigurationIsSealedWithTheBorrow()
            throws Exception {
        DataSource ds = facadelessConfiguration();

        Connection first = ds.getConnection();
        setSessionMarker(first, 8);
        Statement st = first.createStatement();
        ResultSet survivor = st.executeQuery("SELECT X FROM SYSTEM_RANGE(1,10)");
        Assertions.assertTrue(survivor.next(), "the cursor is live during the borrow");
        first.close();

        Connection second = ds.getConnection();
        Assertions.assertEquals(8, sessionMarker(second).intValue(),
                "precondition: the pool re-armed the same physical session");

        Assertions.assertTrue(survivor.isClosed(),
                "a result set whose borrow ended must report itself closed");
        SQLException thrown = Assertions.assertThrows(SQLException.class, survivor::next,
                "a result set whose borrow ended must not read the next "
                        + "borrower's session");
        Assertions.assertTrue(thrown.getMessage().contains(
                "The pooled connection this result set was read from has "
                        + "been returned to the pool."),
                "the failure must be the facade's wording, but said: "
                        + thrown.getMessage());
        Assertions.assertEquals(8, sessionMarker(second).intValue(),
                "the next borrower's session must be untouched");
        survivor.close(); // the no-op a closed result set's close() is

        try (Statement probe = second.createStatement();
                ResultSet rs = probe.executeQuery("SELECT 1")) {
            Assertions.assertTrue(rs.next(),
                    "the current borrower must get working handles");
        }
        second.close();
    }

    @SuppressWarnings("deprecation")
    @Test
    void theDeprecatedValueIsStillStoredAndReported() throws Exception {
        DataSource ds = facadelessConfiguration();
        try (Connection pooled = ds.getConnection()) {
            Assertions.assertFalse(pooled.isClosed(),
                    "precondition: the pool serves connections from a "
                            + "configuration that asks for no facade");
        }

        // The property has no effect any more, but the configuration must
        // keep reading back as it was written - through the data source
        // proxy and through the pool properties behind it.
        Assertions.assertFalse(ds.getUseStatementFacade(),
                "the data source must keep reporting the configured value");
        Assertions.assertFalse(ds.getPoolProperties().getUseStatementFacade(),
                "the pool properties must keep storing the configured value");
    }
}
