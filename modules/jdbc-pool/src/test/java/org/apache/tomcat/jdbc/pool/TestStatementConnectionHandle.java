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
import org.apache.tomcat.jdbc.pool.interceptor.StatementDecoratorInterceptor;
import org.apache.tomcat.jdbc.pool.interceptor.StatementFinalizer;

/**
 * What a pooled statement reports as its connection.
 *
 * <p>{@link java.sql.Statement#getConnection()} is specified to return the
 * {@link Connection} that produced the statement, which for a pooled
 * statement is the handle the application borrowed - not the driver's own
 * connection. Handing out the driver object instead lets an application
 * reach a physical session the pool no longer owns: it can close it, or
 * keep creating statements on it after the handle was returned, and those
 * statements then escape the interceptors that were configured for them
 * ({@link StatementFinalizer}'s close-on-return, a statement cache).</p>
 *
 * <p>{@link StatementDecoratorInterceptor} has always answered this call
 * with the pooled handle; {@link StatementFacade}, which is on by default,
 * did not.</p>
 */
class TestStatementConnectionHandle extends JdbcPoolBaseTest {

    @Test
    void aStatementReportsThePooledHandleThatCreatedIt() throws Exception {
        DataSource ds = newDataSource();
        ds.createPool();

        try (Connection pooled = ds.getConnection()) {
            try (Statement plain = pooled.createStatement()) {
                Assertions.assertSame(pooled, plain.getConnection(),
                        "createStatement() must yield a statement that reports the borrowed handle");
            }
            try (Statement prepared = pooled.prepareStatement("SELECT 1")) {
                Assertions.assertSame(pooled, prepared.getConnection(),
                        "prepareStatement() must yield a statement that reports the borrowed handle");
            }
            try (Statement callable = pooled.prepareCall("CALL 1")) {
                Assertions.assertSame(pooled, callable.getConnection(),
                        "prepareCall() must yield a statement that reports the borrowed handle");
            }
            // The route BZ 48392 was about: a handle reached through a result
            // set rather than held directly.
            try (Statement st = pooled.createStatement();
                    ResultSet rs = st.executeQuery("SELECT 1")) {
                Assertions.assertSame(pooled, rs.getStatement().getConnection(),
                        "a connection reached through a result set's statement must be the "
                                + "borrowed handle as well");
            }
        }
    }

    @Test
    void aReportedConnectionIsThePoolHandleAndNotTheDriverConnection() throws Exception {
        DataSource ds = newDataSource();
        ds.createPool();

        try (Connection pooled = ds.getConnection();
                Statement st = pooled.createStatement()) {
            Connection reported = st.getConnection();
            Assertions.assertFalse(reported.getClass().getName().startsWith("org.h2."),
                    "the physical driver connection must not reach the application: "
                            + reported.getClass().getName());
            Assertions.assertSame(pooled, reported,
                    "the reported connection must be the very handle that was borrowed");
        }
    }

    /**
     * The consequence that makes the leak an availability problem rather
     * than a cosmetic one: statements created through the handle a statement
     * reports are the ones {@code StatementFinalizer} is documented to close
     * when the connection goes back.
     */
    @Test
    void statementsCreatedThroughAReportedConnectionAreClosedOnReturn() throws Exception {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setJdbcInterceptors(StatementFinalizer.class.getName());
        ds.createPool();

        Statement escaped;
        Connection pooled = ds.getConnection();
        try (Statement st = pooled.createStatement()) {
            escaped = st.getConnection().createStatement();
        }
        pooled.close();

        Assertions.assertTrue(escaped.isClosed(),
                "a statement created through the connection a pooled statement reports "
                        + "must be closed when that connection is returned");
    }

    /**
     * What the change is for: the handle a statement reports belongs to the
     * borrow it came from, so it stops working when that borrow ends. The
     * driver connection the facade used to hand out kept serving whoever
     * held the physical session next.
     */
    @Test
    void aReportedConnectionStopsWorkingWhenItsBorrowEnds() throws Exception {
        DataSource ds = newDataSource();
        // force the next borrow onto the same physical session
        ds.getPoolProperties().setMaxActive(1);
        ds.getPoolProperties().setMaxIdle(1);
        ds.createPool();

        Connection first = ds.getConnection();
        setSessionMarker(first, 4242);
        Statement survivor = first.createStatement();
        Connection reported = survivor.getConnection();
        Assertions.assertSame(first, reported);
        first.close();

        Connection second = ds.getConnection();
        Assertions.assertEquals(Integer.valueOf(4242), sessionMarker(second),
                "the next borrower holds the very session the reported connection came from");

        Assertions.assertTrue(reported.isClosed(),
                "the reported handle must report itself closed once its borrow ended");
        Assertions.assertThrows(SQLException.class, reported::getAutoCommit,
                "the reported connection must not serve the next borrower");
        survivor.close();
        second.close();
    }

    /**
     * The facade answers {@code getConnection()} from the handle it was
     * given, so the answer survives the statement being closed only as far
     * as the JDBC specification allows: a closed statement still throws.
     */
    @Test
    void aClosedStatementStillReportsNoConnection() throws Exception {
        DataSource ds = newDataSource();
        ds.createPool();

        try (Connection pooled = ds.getConnection()) {
            Statement st = pooled.createStatement();
            st.close();
            Assertions.assertThrows(SQLException.class, st::getConnection,
                    "getConnection() on a closed statement must throw, as java.sql.Statement specifies");
        }
    }

    /**
     * {@code unwrap()} is the documented way to reach the driver's object
     * and is deliberately left delegating to the driver.
     */
    @Test
    void unwrapStillReachesTheDriverStatement() throws Exception {
        DataSource ds = newDataSource();
        ds.createPool();

        try (Connection pooled = ds.getConnection();
                Statement st = pooled.createStatement()) {
            Assertions.assertTrue(st.unwrap(Statement.class).getClass().getName().startsWith("org.h2."),
                    "unwrap() must still reach the driver statement: "
                            + st.unwrap(Statement.class).getClass().getName());
        }
    }

    /**
     * The same contract with either statement wrapper in the chain, so the
     * answer does not depend on an unrelated interceptor being configured.
     */
    @Test
    void theDecoratorAndTheFacadeAnswerTheSameWay() throws Exception {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setJdbcInterceptors(
                StatementDecoratorInterceptor.class.getSimpleName());
        ds.createPool();

        try (Connection pooled = ds.getConnection();
                Statement st = pooled.createStatement()) {
            Assertions.assertSame(pooled, st.getConnection(),
                    "with the decorator configured the reported connection must stay the pooled handle");
        }

        DataSource unfacaded = newDataSource();
        unfacaded.getPoolProperties().setUseStatementFacade(false);
        unfacaded.getPoolProperties().setJdbcInterceptors(
                StatementDecoratorInterceptor.class.getSimpleName());
        unfacaded.createPool();
        try (Connection pooled = unfacaded.getConnection();
                Statement st = pooled.createStatement()) {
            Assertions.assertSame(pooled, st.getConnection(),
                    "the decorator alone must keep answering with the pooled handle");
        }
    }
}
