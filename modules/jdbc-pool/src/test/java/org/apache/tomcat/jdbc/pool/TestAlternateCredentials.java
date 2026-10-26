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
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;

/**
 * Expected functionality of borrowing with credentials other than the
 * pool's default: the session a borrower gets must be authenticated as
 * exactly the user it asked for, and a session must never be handed to a
 * borrower with a different identity. The identity oracle is the
 * database's own {@code CURRENT_USER}, so a pool that merely remembered
 * the requested name without re-authenticating cannot pass.
 *
 * <p>Scenario harvested from the retired legacy
 * {@code AlternateUsernameTest}, which could only ask a fake driver which
 * credentials it had been handed.</p>
 */
class TestAlternateCredentials extends JdbcPoolBaseTest {

    private static final String ALT_USER = "POOL_ALT_USER";
    private static final String ALT_PASSWORD = "alt-password";

    private static final String DEFAULT_USER = "SA";

    @BeforeAll
    static void createAlternateUser() throws SQLException {
        try (Connection admin = DriverManager.getConnection(getUrl(), "sa", "");
                Statement st = admin.createStatement()) {
            st.execute("CREATE USER IF NOT EXISTS " + ALT_USER
                    + " PASSWORD '" + ALT_PASSWORD + "'");
        }
    }

    private DataSource alternatePool(boolean allowed) {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setAlternateUsernameAllowed(allowed);
        ds.getPoolProperties().setMaxActive(4);
        return ds;
    }

    /** The user the database authenticated this connection as. */
    private static String sessionUser(Connection c) throws SQLException {
        try (Statement st = c.createStatement();
                ResultSet rs = st.executeQuery("SELECT CURRENT_USER")) {
            rs.next();
            return rs.getString(1);
        }
    }

    @ParameterizedTest(name = "alternateUsernameAllowed={0}")
    @CsvSource({ "true", "false" })
    void theSessionServesTheIdentityTheBorrowerAskedFor(boolean allowed) throws Exception {
        DataSource ds = alternatePool(allowed);
        ds.createPool();

        try (Connection c = ds.getConnection()) {
            Assertions.assertEquals(DEFAULT_USER, sessionUser(c),
                    "a plain borrow uses the pool's own credentials");
        }

        try (Connection c = ds.getConnection(ALT_USER, ALT_PASSWORD)) {
            String expected = allowed ? ALT_USER : DEFAULT_USER;
            Assertions.assertEquals(expected, sessionUser(c), allowed
                    ? "a borrow with alternate credentials must be authenticated as that user"
                    : "with alternates disallowed the configured user must be kept");
        }

        try (Connection c = ds.getConnection()) {
            Assertions.assertEquals(DEFAULT_USER, sessionUser(c),
                    "a default borrow must never be served by the alternate user's session");
        }
    }

    @Test
    void aSessionIsNeverServedToABorrowerOfAnotherIdentity() throws Exception {
        DataSource ds = alternatePool(true);
        ds.createPool();

        Connection first = ds.getConnection(ALT_USER, ALT_PASSWORD);
        setSessionMarker(first, 12);
        first.close();

        // the same identity gets the pooled session back, untouched
        Connection again = ds.getConnection(ALT_USER, ALT_PASSWORD);
        try {
            Assertions.assertEquals(Integer.valueOf(12), sessionMarker(again),
                    "the alternate user's session must be pooled and reused for the same user");
            Assertions.assertEquals(ALT_USER, sessionUser(again));
        } finally {
            again.close();
        }

        // a different identity must not be served by that session: the pool
        // re-establishes the session instead, so the switch costs a reconnect
        try (Connection other = ds.getConnection()) {
            Assertions.assertEquals(DEFAULT_USER, sessionUser(other),
                    "the alternate user's session must not be handed to the default user");
        }
        Assertions.assertEquals(1, ds.getSize(),
                "switching identity replaces the session in place, it does not add one");
        awaitSessions(1);
    }

    @Test
    void aWrongAlternatePasswordIsRefusedAndThePoolSurvives() throws Exception {
        DataSource ds = alternatePool(true);
        ds.getPoolProperties().setMaxWait(5000);
        ds.createPool();

        Assertions.assertThrows(SQLException.class,
                () -> ds.getConnection(ALT_USER, "wrong-password"),
                "the database must refuse a borrow with a wrong password");

        try (Connection c = ds.getConnection()) {
            Assertions.assertEquals(DEFAULT_USER, sessionUser(c),
                    "a refused borrow must leave the pool serving its default user");
        }
        Assertions.assertEquals(0, ds.getActive(),
                "a refused borrow must not leak a checked out connection");
    }

    @Test
    void anUnknownUserIsRefusedByTheDatabase() throws Exception {
        DataSource ds = alternatePool(true);
        ds.getPoolProperties().setMaxWait(5000);
        ds.createPool();

        Assertions.assertThrows(SQLException.class,
                () -> ds.getConnection("NO_SUCH_POOL_USER", "x"),
                "the database must refuse a borrow for a user that does not exist");
        Assertions.assertEquals(0, ds.getActive());
    }

    @Test
    void mixedIdentitiesUnderConcurrencyAlwaysGetTheirOwnUser() throws Exception {
        final int threads = 8;
        final int iterations = 15;
        DataSource ds = alternatePool(true);
        ds.getPoolProperties().setMaxActive(threads);
        ds.createPool();

        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        Queue<String> problems = new ConcurrentLinkedQueue<>();
        AtomicInteger served = new AtomicInteger();
        for (int i = 0; i < threads; i++) {
            final boolean useAlternate = i % 2 == 0;
            Thread worker = new Thread(() -> {
                try {
                    start.await(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                    for (int n = 0; n < iterations; n++) {
                        try (Connection c = useAlternate
                                ? ds.getConnection(ALT_USER, ALT_PASSWORD)
                                : ds.getConnection()) {
                            String user = sessionUser(c);
                            String expected = useAlternate ? ALT_USER : DEFAULT_USER;
                            if (!expected.equals(user)) {
                                problems.add("expected " + expected + " but got " + user);
                            }
                            served.incrementAndGet();
                        }
                    }
                } catch (Exception e) {
                    problems.add(e.toString());
                } finally {
                    done.countDown();
                }
            }, "credential-churn-" + i);
            worker.setDaemon(true);
            worker.start();
        }
        start.countDown();
        Assertions.assertTrue(done.await(2 * AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS),
                "the churn threads must finish, problems: " + problems);

        Assertions.assertTrue(problems.isEmpty(),
                "every borrower must get its own identity: " + problems);
        Assertions.assertEquals(threads * iterations, served.get());
        Assertions.assertEquals(0, ds.getActive(), "every borrowed connection must be back");
        Assertions.assertTrue(ds.getSize() <= threads,
                "the pool must not exceed maxActive, size was " + ds.getSize());
        Assertions.assertTrue(poolSessions() <= threads,
                "the database must never see more sessions than maxActive, was "
                        + poolSessions());
    }
}
