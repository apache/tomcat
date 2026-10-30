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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.jdbc.JdbcPoolBaseTest;
import org.apache.tomcat.jdbc.pool.ConnectionPool;
import org.apache.tomcat.jdbc.pool.DataSource;

/**
 * The query timeout is applied by the {@code createStatement} hook of
 * {@link QueryTimeoutInterceptor}, which only runs for creation calls that
 * pass through that interceptor. A {@link StatementCache} hit is served
 * without invoking the rest of the chain, and a statement recycled into
 * the cache carries query timeout 0. The ordering in
 * {@code jdbcInterceptors} therefore decides whether reused statements
 * keep the configured timeout - a requirement now documented on the
 * interceptor and in the jdbc-pool docs, warned about by the pool at
 * start when the cache is listed first, and pinned here in both orders.
 */
class TestQueryTimeoutWithStatementCache extends JdbcPoolBaseTest {

    private static final String SQL = "SELECT 42";

    private DataSource poolWith(String... interceptors) throws SQLException {
        StringBuilder chain = new StringBuilder();
        for (String interceptor : interceptors) {
            if (chain.length() > 0) {
                chain.append(';');
            }
            chain.append(interceptor);
        }
        DataSource ds = newDataSource();
        ds.getPoolProperties().setJdbcInterceptors(chain.toString());
        ds.createPool();
        return ds;
    }

    @Test
    void reusedStatementKeepsTheTimeoutWhenTheInterceptorIsListedFirst()
            throws Exception {
        DataSource ds = poolWith(
                QueryTimeoutInterceptor.class.getName() + "(queryTimeout=5)",
                StatementCache.class.getName() + "(prepared=true,max=5)");
        try (Connection pooled = ds.getConnection()) {
            PreparedStatement first = pooled.prepareStatement(SQL);
            Assertions.assertEquals(5, first.getQueryTimeout());
            Statement physical = driverStatement(first);
            first.close();
            Assertions.assertEquals(0, physical.getQueryTimeout(),
                    "the cache must have recycled the statement with the timeout reset");

            // the cache serves the second creation, and because this
            // interceptor sits outside the cache its hook still runs - the
            // timeout is re-applied to the recycled statement
            PreparedStatement reused = pooled.prepareStatement(SQL);
            Assertions.assertSame(physical, driverStatement(reused),
                    "the same driver statement must come back from the cache");
            Assertions.assertEquals(5, reused.getQueryTimeout(),
                    "a cache hit served through an outer QueryTimeoutInterceptor "
                            + "must carry the configured timeout");
            reused.close();
        }
    }

    @Test
    void reusedStatementLosesTheTimeoutWhenTheCacheIsListedFirst()
            throws Exception {
        DataSource ds = poolWith(
                StatementCache.class.getName() + "(prepared=true,max=5)",
                QueryTimeoutInterceptor.class.getName() + "(queryTimeout=5)");
        try (Connection pooled = ds.getConnection()) {
            PreparedStatement first = pooled.prepareStatement(SQL);
            Assertions.assertEquals(5, first.getQueryTimeout(),
                    "the creating call reaches the interceptor even in this order");
            Statement physical = driverStatement(first);
            first.close();
            Assertions.assertEquals(0, physical.getQueryTimeout(),
                    "the cache must have recycled the statement with the timeout reset");

            // documented limitation: the cache answers the second creation
            // itself, the deeper interceptor never runs, and the recycled
            // statement carries the 0 the cache reset it to - pinning this
            // so a future change to either interceptor is noticed here
            PreparedStatement reused = pooled.prepareStatement(SQL);
            Assertions.assertSame(physical, driverStatement(reused),
                    "the same driver statement must come back from the cache");
            Assertions.assertEquals(0, reused.getQueryTimeout(),
                    "a cache hit served around an inner QueryTimeoutInterceptor "
                            + "carries no timeout - which is why the interceptor "
                            + "must be listed before the cache; if this ever reads "
                            + "the configured value instead, the order dependency "
                            + "has been removed and the notes on "
                            + "QueryTimeoutInterceptor and StatementCache must be "
                            + "updated along with this test");
            reused.close();
        }
    }

    /**
     * A pool whose chain lists the cache before the timeout interceptor
     * answers every repeated prepare of the same SQL from the cache, around
     * the deeper interceptor, with the timeout the cache reset to 0 - so
     * ConnectionPool warns about the ordering once when the pool starts.
     */
    @Test
    void cacheListedFirstIsWarnedAboutAtPoolStart() throws Exception {
        try (PoolLogCapture logs = new PoolLogCapture()) {
            poolWith(
                    StatementCache.class.getName() + "(prepared=true,max=5)",
                    QueryTimeoutInterceptor.class.getName() + "(queryTimeout=5)");
            logs.assertOrderWarningLogged();
        }
    }

    /**
     * The reverse order is the documented configuration: the timeout
     * interceptor sits outside the cache and re-applies the timeout on every
     * hit, so there must be no warning.
     */
    @Test
    void timeoutListedFirstIsNotWarnedAbout() throws Exception {
        try (PoolLogCapture logs = new PoolLogCapture()) {
            poolWith(
                    QueryTimeoutInterceptor.class.getName() + "(queryTimeout=5)",
                    StatementCache.class.getName() + "(prepared=true,max=5)");
            logs.assertNoOrderWarningLogged();
        }
    }

    /**
     * The check runs against the parsed, resolved interceptor definitions, so
     * a short name that only resolves against the interceptor package, and a
     * parameterized entry whose parenthesized properties the parser strips,
     * must normalize to the same comparison as the fully qualified spellings.
     */
    @Test
    void shortNamesNormalizeToTheSameOrderCheck() throws Exception {
        try (PoolLogCapture logs = new PoolLogCapture()) {
            poolWith("StatementCache", "QueryTimeoutInterceptor");
            logs.assertOrderWarningLogged();
        }
        try (PoolLogCapture logs = new PoolLogCapture()) {
            poolWith("QueryTimeoutInterceptor(queryTimeout=5)", "StatementCache(prepared=true)");
            logs.assertNoOrderWarningLogged();
        }
    }

    /**
     * Captures what {@link ConnectionPool} logs, following the handler idiom
     * of the pool's other diagnostic tests. The canary record - written
     * through the very JULI logger the pool logs through, not through JUL
     * directly - proves the wiring observes those records before the pool
     * under test gets its chance to log.
     */
    private static final class PoolLogCapture implements AutoCloseable {
        private static final String CANARY = "interceptor-order-handler-wiring-canary";

        private final Logger logger = Logger.getLogger(ConnectionPool.class.getName());
        private final Level originalLevel = logger.getLevel();
        private final List<LogRecord> records = Collections.synchronizedList(new ArrayList<>());
        private final Handler handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                records.add(record);
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
            if (records.stream().noneMatch(r -> CANARY.equals(r.getMessage()))) {
                close();
                throw new AssertionError("the test handler must observe what this class logs");
            }
        }

        private static boolean isOrderWarning(LogRecord record) {
            return record.getLevel().intValue() == Level.WARNING.intValue()
                    && record.getMessage() != null
                    && record.getMessage().contains(
                            "[StatementCache] interceptor is configured before");
        }

        void assertOrderWarningLogged() {
            Assertions.assertTrue(Arrays.stream(snapshot()).anyMatch(PoolLogCapture::isOrderWarning),
                    "a chain that lists the statement cache before the timeout interceptor"
                            + " must be warned about at pool start, records: "
                            + Arrays.toString(snapshot()));
        }

        void assertNoOrderWarningLogged() {
            for (LogRecord record : snapshot()) {
                Assertions.assertFalse(isOrderWarning(record),
                        "a correctly ordered chain must not be warned about: " + record.getMessage());
            }
        }

        private LogRecord[] snapshot() {
            synchronized (records) {
                return records.toArray(new LogRecord[0]);
            }
        }

        @Override
        public void close() {
            logger.removeHandler(handler);
            logger.setLevel(originalLevel);
        }
    }
}
