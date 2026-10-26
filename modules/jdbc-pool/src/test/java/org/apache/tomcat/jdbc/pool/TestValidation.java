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
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;

/**
 * Expected validation functionality of the pool: which checks run when,
 * what happens when a check fails, and what the check does to the
 * physical session. The oracles are the database's own session count and
 * session identity probe, plus the committed data, so a validation that
 * silently skipped its work cannot pass.
 *
 * <p>Scenarios harvested from the retired legacy classes
 * {@code TestValidation} and {@code TestValidationQueryTimeout}; the
 * {@code jdbc-pool} contract that a validation which fails on an idle
 * connection replaces the connection instead of reporting an error is
 * asserted explicitly, because it is easy to mistake for a bug.</p>
 */
class TestValidation extends JdbcPoolBaseTest {

    /** Heavy pure-SQL aggregate; H2 honours statement timeouts on it. */
    private static final String SLOW_SQL =
            "SELECT COUNT(*) FROM SYSTEM_RANGE(1,400000000) r1, SYSTEM_RANGE(1,3)";

    private static final String BROKEN_SQL = "SELECT * FROM NO_SUCH_VALIDATION_TABLE";

    /** Validates with a query and never skips a check. */
    private DataSource validatingDataSource(String validationQuery) {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setValidationQuery(validationQuery);
        ds.getPoolProperties().setValidationInterval(0);
        return ds;
    }

    /** The physical connection behind a pooled one, for fault injection. */
    private static Connection physical(Connection pooled) throws SQLException {
        return ((javax.sql.PooledConnection) pooled).getConnection();
    }

    @Test
    void aFailingValidationQueryFailsTheBorrowAndLeaksNothing() throws Exception {
        DataSource ds = validatingDataSource(BROKEN_SQL);
        ds.getPoolProperties().setTestOnBorrow(true);
        ds.getPoolProperties().setMaxActive(1);
        ds.getPoolProperties().setMaxWait(2000);
        ds.createPool();
        Assertions.assertEquals(1, poolSessions(),
                "the initial connection exists before the borrow");

        Assertions.assertThrows(SQLException.class, ds::getConnection,
                "an idle connection that fails validation must not be handed out");
        Assertions.assertEquals(0, poolSessions(),
                "the rejected connection must be closed on the database");
        Assertions.assertEquals(0, ds.getSize(), "the pool must not keep a connection it rejected");
    }

    @Test
    void aConnectionCreatedDuringTheBorrowIsNotValidatedOnBorrow() throws Exception {
        // Documented asymmetry: testOnBorrow guards idle connections. A
        // connection created to satisfy this very borrow is only subject to
        // the on-connect validation (testOnConnect / initSQL), which is off
        // here - so a broken validation query goes unnoticed on that path.
        DataSource ds = validatingDataSource(BROKEN_SQL);
        ds.getPoolProperties().setTestOnBorrow(true);
        ds.getPoolProperties().setInitialSize(0);
        ds.getPoolProperties().setMaxActive(2);
        ds.createPool();

        try (Connection c = ds.getConnection()) {
            c.createStatement().execute("SELECT 1");
            Assertions.assertEquals(1, poolSessions());
        }
        Assertions.assertEquals(1, ds.getIdle(),
                "the newly created connection is pooled without borrow validation");
    }

    @Test
    void aFailingValidationQueryAtConnectStopsThePoolFromStarting() throws Exception {
        DataSource ds = validatingDataSource(BROKEN_SQL);
        ds.getPoolProperties().setTestOnConnect(true);
        ds.getPoolProperties().setInitialSize(1);

        Assertions.assertThrows(SQLException.class, ds::createPool,
                "testOnConnect must fail fast when the initial connection does not validate");
        Assertions.assertEquals(0, poolSessions(), "no session may survive the failed startup");
    }

    @Test
    void aDeadIdleConnectionIsReplacedOnBorrowWithoutError() throws Exception {
        DataSource ds = validatingDataSource("SELECT 1");
        ds.getPoolProperties().setTestOnBorrow(true);
        ds.createPool();

        Connection first = ds.getConnection();
        setSessionMarker(first, 11);
        Connection physical = physical(first);
        first.close();
        physical.close(); // kill the session the pool believes it owns
        awaitSessions(0);

        try (Connection second = ds.getConnection()) {
            Assertions.assertNull(sessionMarker(second),
                    "the borrow must not get the dead session back, but a fresh one");
            try (java.sql.ResultSet rs = second.createStatement().executeQuery("SELECT 1")) {
                Assertions.assertTrue(rs.next(), "the replacement connection must work");
            }
        }
        Assertions.assertEquals(1, ds.getSize(), "the dead connection must be replaced, not lost");
        awaitSessions(1);
    }

    @Test
    void aDeadConnectionIsReleasedOnReturn() throws Exception {
        DataSource ds = validatingDataSource("SELECT 1");
        ds.getPoolProperties().setTestOnReturn(true);
        ds.createPool();

        Connection pooled = ds.getConnection();
        physical(pooled).close(); // dies while the application holds it
        pooled.close(); // the return validation must notice

        Assertions.assertEquals(0, ds.getSize(),
                "a connection that failed the return validation must leave the pool");
        Assertions.assertEquals(0, ds.getIdle());
        awaitSessions(0);

        try (Connection fresh = ds.getConnection()) {
            fresh.createStatement().execute("SELECT 1");
        }
        Assertions.assertEquals(1, ds.getSize(), "the pool must recover with a new connection");
    }

    @Test
    void theValidationQueryTimeoutCancelsAnUnfinishedValidation() throws Exception {
        DataSource ds = validatingDataSource(SLOW_SQL);
        ds.getPoolProperties().setTestOnConnect(true);
        ds.getPoolProperties().setInitialSize(0);
        ds.getPoolProperties().setValidationQueryTimeout(1);
        ds.getPoolProperties().setMaxWait(5000);
        ds.createPool();

        long start = System.currentTimeMillis();
        Assertions.assertThrows(SQLException.class, ds::getConnection,
                "a validation query that outlives its timeout must fail the borrow");
        long elapsed = System.currentTimeMillis() - start;
        Assertions.assertTrue(elapsed >= 900 && elapsed < 10000,
                "the validation must be cancelled at the configured timeout, took "
                        + elapsed + " ms");
        awaitSessions(0);
    }

    @Test
    void aCustomValidatorReplacesTheQueryBasedValidation() throws Exception {
        CountingValidator validator = new CountingValidator();
        DataSource ds = validatingDataSource(BROKEN_SQL); // would fail if used
        ds.getPoolProperties().setValidator(validator);
        ds.getPoolProperties().setTestOnBorrow(true);
        ds.createPool();

        for (int i = 0; i < 2; i++) {
            try (Connection c = ds.getConnection()) {
                c.createStatement().execute("SELECT 1");
            }
        }
        Assertions.assertEquals(2, validator.actions.size(),
                "the validator must be consulted once per borrow");
        for (Integer action : validator.actions) {
            Assertions.assertEquals(Integer.valueOf(PooledConnection.VALIDATE_BORROW), action,
                    "a borrow validation must report the borrow action to the validator");
        }

        validator.accept = false;
        Assertions.assertThrows(SQLException.class, ds::getConnection,
                "a rejecting validator must keep the connection out of the application");
        awaitSessions(0);

        validator.accept = true;
        try (Connection c = ds.getConnection()) {
            c.createStatement().execute("SELECT 1");
        }
        Assertions.assertEquals(1, ds.getSize(),
                "the pool must serve again once validation passes");
    }

    @Test
    void theValidationIntervalSuppressesRepeatedChecks() throws Exception {
        CountingValidator validator = new CountingValidator();
        DataSource ds = validatingDataSource("SELECT 1");
        ds.getPoolProperties().setValidator(validator);
        ds.getPoolProperties().setTestOnBorrow(true);
        ds.getPoolProperties().setValidationInterval(600000);
        ds.createPool();

        for (int i = 0; i < 3; i++) {
            try (Connection c = ds.getConnection()) {
                c.createStatement().execute("SELECT 1");
            }
        }
        // The interval is measured from the last successful validation,
        // which for a fresh physical connection is its creation.
        Assertions.assertEquals(0, validator.actions.size(),
                "within the validation interval no borrow may validate");

        ds.getPoolProperties().setValidationInterval(0);
        try (Connection c = ds.getConnection()) {
            c.createStatement().execute("SELECT 1");
        }
        Assertions.assertEquals(1, validator.actions.size(),
                "with the interval switched off the borrow must validate");
    }

    @Test
    void validationCommitsTheTransactionOpenOnTheSession() throws Exception {
        // Documented behaviour: PooledConnection.validate() finishes with a
        // silent commit, so that a validated connection never carries an
        // open transaction into the next borrow. Legacy TestValidation
        // pinned the same contract ("no transaction must be running").
        DataSource ds = validatingDataSource("SELECT 1");
        ds.getPoolProperties().setDefaultAutoCommit(Boolean.FALSE);
        ds.getPoolProperties().setTestOnReturn(true);
        String table = uniqueTable();

        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            st.execute("CREATE TABLE " + table + " (v INT)");
            st.executeUpdate("INSERT INTO " + table + " VALUES (1)");
        } // returned without an explicit commit, autoCommit is off

        Assertions.assertEquals(1, adminRowCount(table),
                "the validation on return must leave no open transaction behind");
    }

    /** Records every validation the pool asks it to perform. */
    private static final class CountingValidator implements Validator {
        private final List<Integer> actions = new ArrayList<>();
        private volatile boolean accept = true;

        @Override
        public boolean validate(Connection connection, int validateAction) {
            actions.add(Integer.valueOf(validateAction));
            return accept;
        }
    }
}
