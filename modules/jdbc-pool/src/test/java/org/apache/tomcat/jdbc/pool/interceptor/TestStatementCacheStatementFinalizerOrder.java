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
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.jdbc.JdbcPoolBaseTest;
import org.apache.tomcat.jdbc.pool.ConnectionPool;
import org.apache.tomcat.jdbc.pool.DataSource;

/**
 * The interaction between the statement cache and the statement finalizer,
 * which the pool builds into one chain in configuration order. The finalizer
 * tracks the statement object its hook receives - the one the interceptor
 * below the cache produces - and closes every tracked statement that is still
 * open when the connection is returned. A parked statement is deliberately
 * open, so with the cache listed first (outermost) the return-time close
 * reaches the statement out from under a live cache entry, and the next
 * borrower's cache hit hands out a statement that fails on any use. The cache
 * therefore must refuse to park whenever a tracker sits beneath it, closing
 * the statement through the normal path instead, while the safe order (the
 * finalizer first, tracking the cache's own handle, which retires itself when
 * the statement parks) must keep park-and-reusing. The pool warns once at
 * start when it can see the misordering in the configured classes.
 */
class TestStatementCacheStatementFinalizerOrder extends JdbcPoolBaseTest {

    private static final String CACHE = StatementCache.class.getName() + "(prepared=true,max=5)";
    private static final String FINALIZER = StatementFinalizer.class.getName();

    /** Distinguishes the new warning from the analogous QueryTimeout one. */
    private static final String FINALIZER_ORDER_WARNING = "before [StatementFinalizer]";

    private DataSource poolWith(String interceptors) throws SQLException {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setJdbcInterceptors(interceptors);
        ds.getPoolProperties().setMaxActive(1);
        ds.createPool();
        return ds;
    }

    /**
     * The defect: the cache listed first parks on close, the finalizer beneath
     * it still tracks the parked statement, and returning the connection
     * closes it out from under the cache. The next borrower of that session
     * then received a cache hit whose statement throws on any use (pre-fix
     * this failed at {@code setInt} with "The object is already closed").
     * With the tracker beneath it the cache must not park at all: the close
     * has to reach the statement, and the next borrower must get a fresh
     * statement that works.
     */
    @Test
    void aClosedStatementIsNotParkedBeneathATrackerAndTheNextBorrowerGetsAWorkingStatement()
            throws Exception {
        DataSource ds = poolWith(CACHE + ";" + FINALIZER);
        String table = uniqueTable();
        try (Connection admin = adminConnection(); Statement st = admin.createStatement()) {
            st.execute("CREATE TABLE " + table + " (v INT)");
        }
        String insert = "INSERT INTO " + table + " VALUES (?)";

        Connection first = ds.getConnection();
        StatementCache cache = interceptorOf(first, StatementCache.class);
        Assertions.assertTrue(cache.getNext() instanceof StatementFinalizer,
                "the configuration order must really put the finalizer beneath the cache");
        setSessionMarker(first, 51);
        PreparedStatement prepared = first.prepareStatement(insert);
        Statement physical = driverStatement(prepared);
        prepared.setInt(1, 1);
        Assertions.assertEquals(1, prepared.executeUpdate());
        prepared.close();
        Assertions.assertTrue(physical.isClosed(),
                "with a tracker beneath the cache the close must reach the statement "
                        + "instead of parking it open above the tracker");
        Assertions.assertEquals(0, cache.getCacheSizePerConnection(),
                "nothing may be parked while a tracker beneath the cache would "
                        + "close the parked statement at connection return");
        first.close();

        Connection second = ds.getConnection();
        try {
            Assertions.assertEquals(Integer.valueOf(51), sessionMarker(second),
                    "the same physical session must have been reused");
            PreparedStatement reused = second.prepareStatement(insert);
            Assertions.assertNotSame(physical, driverStatement(reused),
                    "the next borrower must get a fresh prepare, not the closed "
                            + "statement of the previous cache entry");
            reused.setInt(1, 2);
            Assertions.assertEquals(1, reused.executeUpdate(),
                    "the statement the next borrower receives must work");
            reused.close();
        } finally {
            second.close();
        }
        Assertions.assertEquals(2, adminRowCount(table),
                "both executes must have reached the database");
    }

    /**
     * The safe order, which worked before and must keep working: the finalizer
     * above the cache tracks the cache's own handle, whose retirement at the
     * park makes the return-time reclaim a no-op, so the statement stays open
     * in the cache and the next borrower of the session gets the very same
     * driver statement back. A fix aimed at the other order must not quietly
     * switch the caching off here.
     */
    @Test
    void theSafeOrderStillParksAndHandsTheSameStatementToTheNextBorrower() throws Exception {
        DataSource ds = poolWith(FINALIZER + ";" + CACHE);
        String table = uniqueTable();
        try (Connection admin = adminConnection(); Statement st = admin.createStatement()) {
            st.execute("CREATE TABLE " + table + " (v INT)");
        }
        String insert = "INSERT INTO " + table + " VALUES (?)";

        Connection first = ds.getConnection();
        StatementCache cache = interceptorOf(first, StatementCache.class);
        Assertions.assertFalse(cache.getNext() instanceof StatementFinalizer,
                "an interceptor listed before the cache must not sit beneath it");
        setSessionMarker(first, 52);
        PreparedStatement prepared = first.prepareStatement(insert);
        Statement physical = driverStatement(prepared);
        prepared.setInt(1, 1);
        Assertions.assertEquals(1, prepared.executeUpdate());
        prepared.close();
        Assertions.assertFalse(physical.isClosed(),
                "the tracker above the cache must let the statement park open");
        Assertions.assertEquals(1, cache.getCacheSizePerConnection(),
                "the safe order must still park");
        first.close();

        Connection second = ds.getConnection();
        try {
            Assertions.assertEquals(Integer.valueOf(52), sessionMarker(second),
                    "the same physical session must have been reused");
            Assertions.assertEquals(1, cacheOf(second).getCacheSizePerConnection(),
                    "the cache must survive the return to the pool - the tracker "
                            + "above must not close the parked statement");
            PreparedStatement reused = second.prepareStatement(insert);
            Assertions.assertSame(physical, driverStatement(reused),
                    "the next borrower must get the same physical statement");
            reused.setInt(1, 2);
            Assertions.assertEquals(1, reused.executeUpdate());
            reused.close();
            Assertions.assertEquals(1, cacheOf(second).getCacheSizePerConnection(),
                    "the statement must go back into the cache when closed again");
        } finally {
            second.close();
        }
        Assertions.assertEquals(2, adminRowCount(table),
                "both executes must have reached the database");
    }

    /* The cache as the given borrower sees it. */
    private static StatementCache cacheOf(Connection pooled) {
        return interceptorOf(pooled, StatementCache.class);
    }

    /**
     * The pool-side announcement: {@code checkInterceptorOrder} compares the
     * configured classes and must warn once at start when the cache is listed
     * before the finalizer - the same way it already warns for the analogous
     * query-timeout misordering - and must stay silent for the safe order. A
     * finalizer listed on both sides of the cache still has one beneath it,
     * which defeats the cache just the same, so that order must be reported
     * too (pre-fix only the first occurrence was looked at and it was not).
     */
    @Test
    void thePoolWarnsAtStartOnlyForTheOrderThatDefeatsTheCache() throws Exception {
        try (PoolLogCapture logs = new PoolLogCapture()) {
            DataSource wrong = newDataSource();
            wrong.getPoolProperties().setJdbcInterceptors(CACHE + ";" + FINALIZER);
            wrong.createPool();
            logs.assertFinalizerOrderWarnings(1,
                    "a cache configured above the finalizer defeats the cache and must be reported");
        }
        try (PoolLogCapture logs = new PoolLogCapture()) {
            DataSource wrongToo = newDataSource();
            wrongToo.getPoolProperties().setJdbcInterceptors(
                    FINALIZER + ";" + CACHE + ";" + FINALIZER);
            wrongToo.createPool();
            logs.assertFinalizerOrderWarnings(1,
                    "a finalizer on both sides still leaves one beneath the cache "
                            + "and must be reported (pre-fix it was silent)");
        }
        try (PoolLogCapture logs = new PoolLogCapture()) {
            DataSource right = newDataSource();
            right.getPoolProperties().setJdbcInterceptors(FINALIZER + ";" + CACHE);
            right.createPool();
            logs.assertFinalizerOrderWarnings(0,
                    "the safe order must not be reported");
        }
    }

    /**
     * Captures what {@link ConnectionPool} logs at pool start, following the
     * handler idiom of the pool's other diagnostic tests. The canary - written
     * through the JULI logger the pool logs through - proves the wiring
     * observes those records before the pool under test gets its chance to log.
     */
    private static final class PoolLogCapture implements AutoCloseable {
        private static final String CANARY = "finalizer-order-handler-wiring-canary";

        private final Logger logger = Logger.getLogger(ConnectionPool.class.getName());
        private final Level originalLevel = logger.getLevel();
        private final List<LogRecord> records = new ArrayList<>();
        private final Handler handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                synchronized (records) {
                    records.add(record);
                }
            }

            @Override
            public void flush() {
                // no buffering
            }

            @Override
            public void close() {
                // nothing to release
            }
        };

        PoolLogCapture() {
            logger.addHandler(handler);
            logger.setLevel(Level.ALL);
            LogFactory.getLog(ConnectionPool.class).error(CANARY);
            synchronized (records) {
                if (records.stream().noneMatch(r -> CANARY.equals(r.getMessage()))) {
                    close();
                    throw new AssertionError("the test handler must observe what this class logs");
                }
            }
        }

        /* Records carrying the finalizer-order warning, counted. */
        void assertFinalizerOrderWarnings(int expected, String why) {
            List<LogRecord> warnings;
            synchronized (records) {
                warnings = records.stream()
                        .filter(r -> r.getMessage() != null && r.getMessage().contains(FINALIZER_ORDER_WARNING))
                        .collect(Collectors.toList());
            }
            Assertions.assertEquals(expected, warnings.size(),
                    why + ", messages seen: " + records.stream().map(LogRecord::getMessage)
                            .collect(Collectors.toList()));
        }

        @Override
        public void close() {
            logger.setLevel(originalLevel);
            logger.removeHandler(handler);
        }
    }
}
