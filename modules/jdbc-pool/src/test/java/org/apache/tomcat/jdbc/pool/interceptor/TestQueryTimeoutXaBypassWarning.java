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
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import javax.sql.XAConnection;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.jdbc.JdbcPoolBaseTest;
import org.apache.tomcat.jdbc.pool.DataSource;
import org.apache.tomcat.jdbc.pool.XADataSource;

/**
 * On a pool backed by a {@link javax.sql.XADataSource} the applications'
 * statements are created on the session that
 * {@link javax.sql.XAConnection#getConnection()} hands out, which is the
 * physical session from behind the interceptor chain - so the query timeout
 * this interceptor configures never reaches them. That is a property of the
 * pool's dispatch, not something this class can change, but the interceptor
 * must not then go about its silence: an operator who configured the timeout
 * as a protection has to hear, at pool start, that the pool they configured is
 * one this interceptor cannot serve. And the warning must stay honest - it is
 * due on an XA pool with a configured timeout and on no other pool, and not
 * even on an XA pool whose timeout is switched off.
 */
class TestQueryTimeoutXaBypassWarning extends JdbcPoolBaseTest {

    /** Substring identifying the bypass warning among the captured records. */
    private static final String BYPASS_WARNING = "will not reach the statements applications create";

    /*
     * An XA-backed pool with this interceptor configured the way an operator
     * configures it, backed by H2's real XA data source so the dispatch under
     * test is the pool's genuine XA path.
     */
    private XADataSource xaPool(String interceptorProperties) {
        org.h2.jdbcx.JdbcDataSource inner = new org.h2.jdbcx.JdbcDataSource();
        inner.setUrl(getUrl());
        inner.setUser("sa");
        inner.setPassword("");
        XADataSource ds = new XADataSource();
        ds.setDriverClassName("org.h2.Driver");
        ds.getPoolProperties().setJmxEnabled(false);
        ds.getPoolProperties().setInitialSize(1);
        ds.getPoolProperties().setMinIdle(0);
        ds.getPoolProperties().setDataSource(inner);
        ds.getPoolProperties().setJdbcInterceptors(
                QueryTimeoutInterceptor.class.getName() + interceptorProperties);
        return manage(ds);
    }

    /**
     * The warning is about a hole that is really there: prove it end to end on
     * the same pool - a statement created through the XA handle's
     * {@code getConnection()} keeps the driver's own timeout while a statement
     * created on a chained borrow gets the configured one - before asserting
     * that the interceptor says so at start.
     */
    @Test
    void anXaPoolHearsOneBypassWarningAtStartNamingWhatItCovers() throws Exception {
        XADataSource ds = xaPool("(queryTimeout=5)");
        try (BypassLogCapture logs = new BypassLogCapture()) {
            ds.createPool();
            logs.assertBypassWarnings(1, "an XA pool must be told once at start");

            // The hole the warning names: statements from the XA handle's
            // logical session never pass through the interceptor.
            XAConnection xc = ds.getXAConnection();
            try {
                Connection logical = xc.getConnection();
                try (Statement st = logical.createStatement()) {
                    Assertions.assertEquals(0, st.getQueryTimeout(),
                            "a statement created through XAConnection.getConnection() must be untouched by the interceptor");
                }
            } finally {
                xc.close();
            }

            // And the warning must not be talk about a hole this pool does
            // not have: a statement created on the chained borrow is timed.
            try (Connection pooled = ds.getConnection();
                    Statement st = pooled.createStatement()) {
                Assertions.assertEquals(5, st.getQueryTimeout(),
                        "a statement created through the chain must carry the configured timeout");
            }
        }
    }

    /**
     * A plain pool - driver and URL, no XA data source - serves its statements
     * through the chain, so there is nothing to warn about and the interceptor
     * must stay quiet about a bypass that does not exist.
     */
    @Test
    void aPlainPoolHearsNoBypassWarning() throws Exception {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setJdbcInterceptors(
                QueryTimeoutInterceptor.class.getName() + "(queryTimeout=5)");
        try (BypassLogCapture logs = new BypassLogCapture()) {
            ds.createPool();
            try (Connection pooled = ds.getConnection();
                    Statement st = pooled.createStatement()) {
                Assertions.assertEquals(5, st.getQueryTimeout(),
                        "the chained path must still apply the timeout the silence of this test is about");
            }
            logs.assertBypassWarnings(0, "a pool that serves its statements through the chain has no bypass to report");
        }
    }

    /**
     * A timeout of zero switches the interceptor off, so on such a pool nothing
     * is withheld and the bypass warning would report a missing protection
     * nobody configured. The silence here is the honest one.
     */
    @Test
    void anXaPoolWithoutAConfiguredTimeoutHearsNoBypassWarning() throws Exception {
        XADataSource ds = xaPool("(queryTimeout=0)");
        try (BypassLogCapture logs = new BypassLogCapture()) {
            ds.createPool();
            logs.assertBypassWarnings(0,
                    "a switched-off interceptor has no protection to report as bypassed");
        }
    }

    /**
     * Captures what {@link QueryTimeoutInterceptor} logs, following the handler
     * idiom of the pool's other diagnostic tests. The canary record - written
     * through the very JULI logger the interceptor logs through, not through
     * JUL directly - proves the wiring observes those records before the pool
     * under test gets its chance to log.
     */
    private static final class BypassLogCapture implements AutoCloseable {
        private static final String CANARY = "query-timeout-xa-bypass-handler-wiring-canary";

        private final Logger logger = Logger.getLogger(QueryTimeoutInterceptor.class.getName());
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

        BypassLogCapture() {
            logger.addHandler(handler);
            logger.setLevel(Level.ALL);
            LogFactory.getLog(QueryTimeoutInterceptor.class).error(CANARY);
            synchronized (records) {
                if (records.stream().noneMatch(r -> CANARY.equals(r.getMessage()))) {
                    close();
                    throw new AssertionError("the test handler must observe what this class logs");
                }
            }
        }

        /*
         * The bypass warnings seen so far, with the volume pinned: an empty
         * list fails the no-warning tests as silently as a chatty one fails the
         * warning test, so both directions assert here.
         */
        void assertBypassWarnings(int expected, String why) {
            List<LogRecord> seen;
            synchronized (records) {
                seen = new ArrayList<>(records);
            }
            List<LogRecord> warnings = seen.stream()
                    .filter(r -> r.getMessage() != null && r.getMessage().contains(BYPASS_WARNING))
                    .collect(Collectors.toList());
            Assertions.assertEquals(expected, warnings.size(),
                    why + ", messages seen: " + seen.stream().map(LogRecord::getMessage)
                            .collect(Collectors.toList()));
            for (LogRecord record : warnings) {
                Assertions.assertEquals(Level.WARNING.intValue(), record.getLevel().intValue(),
                        "the bypass is a warning, not something quieter: " + record.getMessage());
                Assertions.assertTrue(record.getMessage().contains("javax.sql.XADataSource"),
                        "the report must name the backing that causes it: " + record.getMessage());
                Assertions.assertTrue(record.getMessage().contains("XAConnection.getConnection()"),
                        "the report must name the route the statements escape by: " + record.getMessage());
            }
        }

        @Override
        public void close() {
            logger.removeHandler(handler);
            logger.setLevel(originalLevel);
        }
    }
}
