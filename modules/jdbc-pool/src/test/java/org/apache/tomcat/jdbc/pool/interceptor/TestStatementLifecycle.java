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
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;
import org.apache.tomcat.jdbc.pool.DataSource;

/**
 * Expected functionality of the statement decorators: statements an
 * application forgot about are closed when its connection goes back, and
 * a statement that the pool recycles (cached) arrives at the next
 * borrower with nothing left over from the previous one - no queued work,
 * no settings it never asked for.
 *
 * <p>Scenarios harvested from the retired legacy classes
 * {@code StatementFinalizerTest} and {@code TestStatementCache}. Where the
 * legacy versions had to ask a fake driver what it remembered, the batch
 * case here is decided by the database: the rows a recycled statement
 * actually inserted.</p>
 */
class TestStatementLifecycle extends JdbcPoolBaseTest {

    private static final String INSERT_SQL = "INSERT INTO %s VALUES (?)";

    private DataSource pooledWithInterceptor(String interceptorWithProperties) {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setMaxActive(1);
        ds.getPoolProperties().setMaxIdle(1);
        ds.getPoolProperties().setJdbcInterceptors(interceptorWithProperties);
        return ds;
    }

    private DataSource cachingPool() {
        return pooledWithInterceptor(StatementCache.class.getName()
                + "(prepared=true,callable=false,max=10)");
    }

    @Test
    void closingTheConnectionClosesStatementsTheApplicationLeftOpen() throws Exception {
        DataSource ds = pooledWithInterceptor(StatementFinalizer.class.getName());
        ds.createPool();

        Connection pooled = ds.getConnection();
        Statement single = pooled.createStatement();
        Assertions.assertFalse(single.isClosed());

        Statement[] many = new Statement[50];
        for (int i = 0; i < many.length; i++) {
            many[i] = pooled.createStatement();
            Assertions.assertFalse(many[i].isClosed(), "statement " + i + " must be open");
        }

        pooled.close();

        Assertions.assertTrue(single.isClosed(),
                "a statement the application never closed must be closed with its connection");
        for (Statement st : many) {
            Assertions.assertTrue(st.isClosed(), "every dangling statement must be finalised");
        }
        // finalising the statements must not have cost the pool its session
        Assertions.assertEquals(1, ds.getSize());
        Assertions.assertEquals(1, poolSessions());
    }

    @Test
    void anAbandonedBatchItemIsNotExecutedByTheNextBorrower() throws Exception {
        DataSource ds = cachingPool();
        String table = uniqueTable();
        ds.createPool();
        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            st.execute("CREATE TABLE " + table + " (v INT)");
        }

        // first caller queues a row and abandons the unit of work without
        // ever executing it; the statement is closed and recycled
        try (Connection abandoning = ds.getConnection()) {
            try (PreparedStatement ps =
                    abandoning.prepareStatement(String.format(INSERT_SQL, table))) {
                ps.setInt(1, 101);
                ps.addBatch();
            }
        }

        int[] counts;
        try (Connection next = ds.getConnection()) {
            try (PreparedStatement ps =
                    next.prepareStatement(String.format(INSERT_SQL, table))) {
                ps.setInt(1, 202);
                ps.addBatch();
                counts = ps.executeBatch();
            }
        }

        Assertions.assertArrayEquals(new int[] { 1 }, counts,
                "the recycled statement must carry exactly the batch it was given");
        Assertions.assertEquals(1, adminRowCount(table),
                "only the second caller's row may reach the database");
    }

    @Test
    void aStatementToldToCloseOnCompletionIsNotRecycled() throws Exception {
        DataSource ds = cachingPool();
        ds.createPool();

        try (Connection first = ds.getConnection()) {
            try (PreparedStatement ps = first.prepareStatement("SELECT 1")) {
                // there is no JDBC call that undoes closeOnCompletion(), so
                // the only correct outcome is that the cache never takes it
                ps.closeOnCompletion();
            }
        }

        try (Connection second = ds.getConnection()) {
            try (PreparedStatement ps = second.prepareStatement("SELECT 1")) {
                Assertions.assertFalse(ps.isCloseOnCompletion(),
                        "the next borrower must not inherit closeOnCompletion");
                try (ResultSet rs = ps.executeQuery()) {
                    Assertions.assertTrue(rs.next());
                }
            }
        }
    }

    /**
     * A property the first caller set and never reset must not survive the
     * recycle into the statement cache. Only the properties a real driver
     * reports back are checked; H2 silently accepts and forgets
     * {@code maxFieldSize} and always reports {@code FETCH_FORWARD}, so a
     * leak of those cannot be observed here.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("leakableProperties")
    void statementPropertiesDoNotLeakAcrossTheCacheRecycle(String label,
            Consumer<PreparedStatement> mutate, Reader reader, int leaked,
            int afterReset) throws Exception {
        DataSource ds = cachingPool();
        ds.createPool();

        try (Connection first = ds.getConnection()) {
            try (PreparedStatement ps = first.prepareStatement("SELECT 1")) {
                mutate.accept(ps);
            }
        }

        try (Connection second = ds.getConnection()) {
            try (PreparedStatement ps = second.prepareStatement("SELECT 1")) {
                int observed = reader.read(ps);
                Assertions.assertNotEquals(leaked, observed,
                        label + " must not be inherited by the next borrower");
                Assertions.assertEquals(afterReset, observed,
                        label + " must be back at the driver default");
            }
        }
    }

    static Stream<Arguments> leakableProperties() {
        // H2 maps a fetch size of 0 onto its own default of 100, which is
        // what the reset leaves behind; the other two have a real zero.
        return Stream.of(
                Arguments.of("queryTimeout",
                        (Consumer<PreparedStatement>) ps -> setQuietly(ps, 37),
                        (Reader) Statement::getQueryTimeout, Integer.valueOf(37), Integer.valueOf(0)),
                Arguments.of("maxRows",
                        (Consumer<PreparedStatement>) ps -> setMaxRowsQuietly(ps, 999),
                        (Reader) Statement::getMaxRows, Integer.valueOf(999), Integer.valueOf(0)),
                Arguments.of("fetchSize",
                        (Consumer<PreparedStatement>) ps -> setFetchSizeQuietly(ps, 777),
                        (Reader) Statement::getFetchSize, Integer.valueOf(777), Integer.valueOf(100)));
    }

    /** Reads one of the statement's mutable properties. */
    interface Reader {
        int read(PreparedStatement ps) throws SQLException;
    }

    private static void setQuietly(PreparedStatement ps, int timeout) {
        try {
            ps.setQueryTimeout(timeout);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void setMaxRowsQuietly(PreparedStatement ps, int rows) {
        try {
            ps.setMaxRows(rows);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void setFetchSizeQuietly(PreparedStatement ps, int size) {
        try {
            ps.setFetchSize(size);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void resultSetsAndStatementsPointBackAtThePoolProxies() throws Exception {
        DataSource ds = pooledWithInterceptor(
                StatementDecoratorInterceptor.class.getSimpleName());
        ds.createPool();

        try (Connection pooled = ds.getConnection();
                Statement st = pooled.createStatement();
                ResultSet rs = st.executeQuery("SELECT 1")) {
            Assertions.assertTrue(rs.next());
            Assertions.assertSame(st, rs.getStatement(),
                    "a result set must report the pool's statement, not the driver's");
            Assertions.assertFalse(rs.getStatement().getClass().getName().startsWith("org.h2."),
                    "the reported statement must not be the raw driver statement");
            Assertions.assertSame(pooled, rs.getStatement().getConnection(),
                    "a statement must report the connection it was created from");
        }
    }
}
