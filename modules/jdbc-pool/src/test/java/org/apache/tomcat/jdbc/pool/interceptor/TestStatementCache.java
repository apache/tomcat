/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tomcat.jdbc.pool.interceptor;

import java.sql.Connection;
import java.sql.DriverPropertyInfo;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.SQLWarning;
import java.util.Arrays;
import java.util.Properties;
import java.util.logging.Logger;

import org.junit.Assert;
import org.junit.Test;

import org.apache.tomcat.jdbc.pool.DataSource;

/**
 * Reproduces a batch crossing the {@link StatementCache} facade close boundary, using a real connection pool and a
 * minimal mock JDBC driver, with no Tomcat, servlet or JNDI involvement. Scenario: a caller adds an item to a
 * {@link PreparedStatement} batch and then closes the statement without ever calling {@code executeBatch()} (e.g.
 * because it abandoned the unit of work after a failed check). A later caller prepares a statement for the same
 * SQL/cache key on the same pooled connection and executes its own batch. Without a complete reset when a statement is
 * recycled into the cache, the later caller's {@code executeBatch()} also executes the earlier caller's abandoned batch
 * item. The same reuse boundary can also retain other mutable statement properties, such as {@code queryTimeout}, so a
 * later caller can end up running under a timeout it never requested.
 */
public class TestStatementCache {

    private static final String DELETE_SQL = "delete from orders where id = ?";
    private static final Integer ZERO = Integer.valueOf(0);


    @Test
    public void testAbandonedWithStatementCacheEnabled() throws Exception {
        runBatchScenario(true, "[1]");
    }

    @Test
    public void testAbandonedBatchWithStatementCacheDisabled() throws Exception {
        runBatchScenario(false, "[1]");
    }

    @Test
    public void testQueryTimeoutWithStatementCacheEnabled() throws Exception {
        runPropertyScenario(true, s -> s.setQueryTimeout(37), PreparedStatement::getQueryTimeout, ZERO);
    }

    @Test
    public void testQueryTimeoutWithStatementCacheDisabled() throws Exception {
        runPropertyScenario(false, s -> s.setQueryTimeout(37), PreparedStatement::getQueryTimeout, ZERO);
    }

    @Test
    public void testMaxRowsWithStatementCacheEnabled() throws Exception {
        runPropertyScenario(true, s -> s.setMaxRows(999), PreparedStatement::getMaxRows, ZERO);
    }

    @Test
    public void testMaxRowsWithStatementCacheDisabled() throws Exception {
        runPropertyScenario(false, s -> s.setMaxRows(999), PreparedStatement::getMaxRows, ZERO);
    }

    @Test
    public void testFetchSizeWithStatementCacheEnabled() throws Exception {
        runPropertyScenario(true, s -> s.setFetchSize(777), PreparedStatement::getFetchSize, ZERO);
    }

    @Test
    public void testFetchSizeWithStatementCacheDisabled() throws Exception {
        runPropertyScenario(false, s -> s.setFetchSize(777), PreparedStatement::getFetchSize, ZERO);
    }

    @Test
    public void testMaxFieldSizeWithStatementCacheEnabled() throws Exception {
        runPropertyScenario(true, s -> s.setMaxFieldSize(555), PreparedStatement::getMaxFieldSize, ZERO);
    }

    @Test
    public void testMaxFieldSizeWithStatementCacheDisabled() throws Exception {
        runPropertyScenario(false, s -> s.setMaxFieldSize(555), PreparedStatement::getMaxFieldSize, ZERO);
    }

    @Test
    public void testPoolableWithStatementCacheEnabled() throws Exception {
        // PreparedStatement/CallableStatement are poolable by default (JDBC spec), so the leak to look for here is
        // the opposite of the other properties: a caller that opts *out* of pooling.
        runPropertyScenario(true, s -> s.setPoolable(false), PreparedStatement::isPoolable, Boolean.TRUE);
    }

    @Test
    public void testPoolableWithStatementCacheDisabled() throws Exception {
        runPropertyScenario(false, s -> s.setPoolable(false), PreparedStatement::isPoolable, Boolean.TRUE);
    }

    @Test
    public void testFetchDirectionWithStatementCacheEnabled() throws Exception {
        runPropertyScenario(true, s -> s.setFetchDirection(ResultSet.FETCH_REVERSE),
                PreparedStatement::getFetchDirection, Integer.valueOf(ResultSet.FETCH_FORWARD));
    }

    @Test
    public void testFetchDirectionWithStatementCacheDisabled() throws Exception {
        runPropertyScenario(false, s -> s.setFetchDirection(ResultSet.FETCH_REVERSE),
                PreparedStatement::getFetchDirection, Integer.valueOf(ResultSet.FETCH_FORWARD));
    }

    @Test
    public void testCloseOnCompletionWithStatementCacheEnabled() throws Exception {
        // There is no JDBC method to undo closeOnCompletion(), so the only correct outcome here is that the next
        // borrow gets a statement that was never told to close on completion in the first place.
        runPropertyScenario(true, PreparedStatement::closeOnCompletion, PreparedStatement::isCloseOnCompletion,
                Boolean.FALSE);
    }

    @Test
    public void testCloseOnCompletionWithStatementCacheDisabled() throws Exception {
        runPropertyScenario(false, PreparedStatement::closeOnCompletion, PreparedStatement::isCloseOnCompletion,
                Boolean.FALSE);
    }

    @Test
    public void testWarningsWithStatementCacheEnabled() throws Exception {
        // executeUpdate() is the "mutation" here - it is what causes the mock statement to generate a warning,
        // the same way a real driver would as a side effect of execution rather than via an explicit setter.
        runPropertyScenario(true, PreparedStatement::executeUpdate, PreparedStatement::getWarnings, null);
    }

    @Test
    public void testWarningsWithStatementCacheDisabled() throws Exception {
        runPropertyScenario(false, PreparedStatement::executeUpdate, PreparedStatement::getWarnings, null);
    }

    private void runBatchScenario(boolean statementCacheEnabled, String expectedCounts) throws Exception {
        DataSource dataSource = createDataSource(statementCacheEnabled);
        try {
            // First caller adds a batch item then abandons the unit of work (e.g. a failed check) without ever
            // calling executeBatch(). The statement is closed - and, if caching is enabled, recycled - regardless.
            Connection abandoning = dataSource.getConnection();
            try (PreparedStatement statement = abandoning.prepareStatement(DELETE_SQL)) {
                statement.setInt(1, 101);
                statement.addBatch();
            } finally {
                abandoning.close();
            }

            // Second caller prepares the same SQL against the same pooled connection (maxActive is 1) and executes
            // its own batch.
            int[] counts;
            Connection executing = dataSource.getConnection();
            try (PreparedStatement statement = executing.prepareStatement(DELETE_SQL)) {
                statement.setInt(1, 202);
                statement.addBatch();
                counts = statement.executeBatch();
            } finally {
                executing.close();
            }

            Assert.assertEquals("Unexpected executeBatch() result", expectedCounts, Arrays.toString(counts));
        } finally {
            dataSource.close(true);
        }
    }

    /**
     * Common shape shared by the properties checked below: a first caller sets a non-default value and abandons the
     * statement without resetting it, then a second caller borrows a statement for the same SQL/cache key on the same
     * pooled connection and reads the property back without ever having set it itself.
     */
    private <T> void runPropertyScenario(boolean statementCacheEnabled, StatementMutator mutate,
            StatementReader<T> read, T expectedDefault) throws Exception {
        DataSource dataSource = createDataSource(statementCacheEnabled);
        try {
            Connection mutating = dataSource.getConnection();
            try (PreparedStatement statement = mutating.prepareStatement(DELETE_SQL)) {
                mutate.apply(statement);
            } finally {
                mutating.close();
            }

            T actual;
            Connection reading = dataSource.getConnection();
            try (PreparedStatement statement = reading.prepareStatement(DELETE_SQL)) {
                actual = read.apply(statement);
            } finally {
                reading.close();
            }

            Assert.assertEquals("Unexpected value after statement reborrow", expectedDefault, actual);
        } finally {
            dataSource.close(true);
        }
    }

    private interface StatementMutator {
        void apply(PreparedStatement statement) throws SQLException;
    }

    private interface StatementReader<T> {
        T apply(PreparedStatement statement) throws SQLException;
    }

    private static DataSource createDataSource(boolean statementCacheEnabled) {
        DataSource dataSource = new DataSource();
        dataSource.setDriverClassName(MockDriver.class.getName());
        dataSource.setUrl("jdbc:tomcat:test:statementcache");
        dataSource.setInitialSize(1);
        dataSource.setMinIdle(1);
        dataSource.setMaxIdle(1);
        dataSource.setMaxActive(1);
        dataSource.setMaxWait(5000);
        dataSource.setJmxEnabled(false);
        dataSource.setTestOnBorrow(false);
        dataSource.setTestOnReturn(false);
        dataSource.setTestWhileIdle(false);
        dataSource.setTimeBetweenEvictionRunsMillis(-1);
        if (statementCacheEnabled) {
            dataSource.setJdbcInterceptors(StatementCache.class.getName() + "(prepared=true,callable=false,max=10)");
        }
        return dataSource;
    }

    /**
     * Minimal mock driver. The connection pool instantiates this directly (via the configured driver class name) and
     * calls {@link #connect(String, Properties)}, so there's no need to register it with
     * {@link java.sql.DriverManager}.
     */
    public static final class MockDriver implements java.sql.Driver {

        @Override
        public Connection connect(String url, Properties info) throws SQLException {
            return new MockConnection(info);
        }

        @Override
        public boolean acceptsURL(String url) throws SQLException {
            return true;
        }

        @Override
        public int getMajorVersion() {
            return 0;
        }

        @Override
        public int getMinorVersion() {
            return 0;
        }

        @Override
        public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) throws SQLException {
            return null;
        }

        @Override
        public boolean jdbcCompliant() {
            return false;
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            return null;
        }
    }

    public static final class MockConnection extends org.apache.tomcat.jdbc.test.driver.Connection {

        public MockConnection(Properties info) {
            super(info);
        }

        @Override
        public PreparedStatement prepareStatement(String sql) throws SQLException {
            return new MockPreparedStatement();
        }
    }

    /**
     * Adds just enough tracking on top of the shared mock statement (which otherwise stubs these methods as no-ops) to
     * observe whether state set by one caller survives a statement being recycled by the {@link StatementCache} and
     * handed out to a later caller.
     */
    public static final class MockPreparedStatement extends org.apache.tomcat.jdbc.test.driver.Statement {

        private int batchSize;
        private int maxRows;
        private int fetchSize;
        private int maxFieldSize;
        // PreparedStatement/CallableStatement are poolable by default per the JDBC spec.
        private boolean poolable = true;
        private int fetchDirection = ResultSet.FETCH_FORWARD;
        private boolean closeOnCompletionRequested;
        private SQLWarning warning;

        @Override
        public void addBatch() throws SQLException {
            batchSize++;
        }

        @Override
        public void clearBatch() throws SQLException {
            batchSize = 0;
        }

        @Override
        public int[] executeBatch() throws SQLException {
            int[] counts = new int[batchSize];
            Arrays.fill(counts, 1);
            batchSize = 0;
            return counts;
        }

        @Override
        public void setMaxRows(int max) throws SQLException {
            maxRows = max;
        }

        @Override
        public int getMaxRows() throws SQLException {
            return maxRows;
        }

        @Override
        public void setFetchSize(int rows) throws SQLException {
            fetchSize = rows;
        }

        @Override
        public int getFetchSize() throws SQLException {
            return fetchSize;
        }

        @Override
        public void setMaxFieldSize(int max) throws SQLException {
            maxFieldSize = max;
        }

        @Override
        public int getMaxFieldSize() throws SQLException {
            return maxFieldSize;
        }

        @Override
        public void setPoolable(boolean poolable) throws SQLException {
            this.poolable = poolable;
        }

        @Override
        public boolean isPoolable() throws SQLException {
            return poolable;
        }

        @Override
        public void setFetchDirection(int direction) throws SQLException {
            fetchDirection = direction;
        }

        @Override
        public int getFetchDirection() throws SQLException {
            return fetchDirection;
        }

        @Override
        public void closeOnCompletion() throws SQLException {
            closeOnCompletionRequested = true;
        }

        @Override
        public boolean isCloseOnCompletion() throws SQLException {
            return closeOnCompletionRequested;
        }

        @Override
        public int executeUpdate() throws SQLException {
            warning = new SQLWarning("mock warning");
            return 0;
        }

        @Override
        public SQLWarning getWarnings() throws SQLException {
            return warning;
        }

        @Override
        public void clearWarnings() throws SQLException {
            warning = null;
        }
    }
}
