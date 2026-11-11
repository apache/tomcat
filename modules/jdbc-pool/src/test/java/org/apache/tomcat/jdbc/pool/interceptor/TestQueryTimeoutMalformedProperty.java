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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.jdbc.JdbcPoolBaseTest;
import org.apache.tomcat.jdbc.pool.DataSource;

/**
 * The timeout is configured as an integer number of seconds given as text,
 * and text is where typos live - {@code queryTimeout=5s} reads like a
 * duration to a human but is not a number. The value not being a number must
 * not decide the timeout by silence: the one-second default that the bad
 * value leaves in effect cancels queries the application meant to run for
 * five seconds, so the misconfiguration has to be said - as the bad value is
 * configured, on every instance the pool configures with it - while the
 * statement-level behaviour (the default in effect) stays pinned as it is
 * today. Omitting the property is the documented way to take the default, and
 * must stay silent.
 */
class TestQueryTimeoutMalformedProperty extends JdbcPoolBaseTest {

    /*
     * Stable fragment of the interceptor's report. Pinning it is what makes
     * the silence assertions below assert on the absence of a real report
     * rather than on any incidental log line.
     */
    private static final String MALFORMED_REPORT = "is not an integer number of seconds";

    /*
     * Stable fragment of the report of an unrecognized property name. The
     * name-typo case is the asymmetric one: a bad value warns while a
     * mis-cased name used to leave the aggressive one-second default armed in
     * silence, degrading protection in the destructive direction.
     */
    private static final String UNKNOWN_NAME_REPORT = "is not recognized and is not applied";

    private DataSource poolWith(String interceptorEntry) {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setJdbcInterceptors(interceptorEntry);
        return ds;
    }

    private void assertDriverTimeout(Connection pooled, int expected, String why) throws Exception {
        try (Statement st = pooled.createStatement()) {
            Assertions.assertEquals(expected, st.getQueryTimeout(), why);
        }
    }

    @Test
    void malformedValueKeepsTheOneSecondDefaultAndIsReportedPerConfiguration() throws Exception {
        try (PoolLogCapture logs = new PoolLogCapture()) {
            DataSource ds = poolWith(QueryTimeoutInterceptor.class.getName() + "(queryTimeout=5s)");
            ds.createPool();

            Connection first = ds.getConnection();
            Connection second = ds.getConnection();
            Assertions.assertEquals(2, poolSessions(),
                    "both borrowed connections must sit on their own physical sessions");
            // the interceptor's chain is built once per physical session, so
            // two sessions are two setProperties calls with the same bad value
            assertDriverTimeout(first, 1,
                    "the unparseable value must not replace the aggressive one-second default - the pool"
                            + " keeps cancelling statements at one second, which is exactly why the"
                            + " misconfiguration must not pass in silence");
            assertDriverTimeout(second, 1, "the second session carries the same kept default");
            first.close();
            second.close();

            List<LogRecord> reports = logs.assertMalformedValueReports(3,
                    "one report per configuration of the bad value: the instance the pool configures at"
                            + " start plus the chain of each of the two physical sessions; the instance"
                            + " configured at pool stop is configured after this capture is closed");
            for (LogRecord report : reports) {
                Assertions.assertTrue(report.getMessage().contains("queryTimeout [5s]"),
                        "the report must name the property and the offending value: " + report.getMessage());
                Assertions.assertTrue(report.getMessage().contains("default 1-second query timeout"),
                        "the report must name the effective value the bad value left in effect: "
                                + report.getMessage());
            }
        }
    }

    @Test
    void emptyValueIsReportedLikeAnyOtherTypoAndKeepsTheDefault() throws Exception {
        try (PoolLogCapture logs = new PoolLogCapture()) {
            DataSource ds = poolWith(QueryTimeoutInterceptor.class.getName() + "(queryTimeout=)");
            ds.createPool();
            try (Connection pooled = ds.getConnection()) {
                assertDriverTimeout(pooled, 1,
                        "an empty value leaves the default in effect, like any unparseable one");
            }
            List<LogRecord> reports = logs.assertMalformedValueReports(2,
                    "the instance the pool configures at start plus the chain the first borrow builds"
                            + " when it sets its session up; the pool-stop instance is configured after"
                            + " this capture is closed");
            for (LogRecord report : reports) {
                Assertions.assertTrue(report.getMessage().contains("queryTimeout []"),
                        "the report must show the empty value it rejected: " + report.getMessage());
            }
        }
    }

    @Test
    void omittedPropertyIsSilentAndKeepsTheOneSecondDefault() throws Exception {
        try (PoolLogCapture logs = new PoolLogCapture()) {
            DataSource ds = poolWith(QueryTimeoutInterceptor.class.getName());
            ds.createPool();
            try (Connection pooled = ds.getConnection()) {
                assertDriverTimeout(pooled, 1,
                        "omitting the property is documented to apply the one-second default");
            }
            logs.assertMalformedValueReports(0,
                    "an omitted property is the documented way to take the default,"
                            + " not a misconfiguration to warn about");
        }
    }

    @Test
    void validValueIsSilentAndApplied() throws Exception {
        try (PoolLogCapture logs = new PoolLogCapture()) {
            DataSource ds = poolWith(QueryTimeoutInterceptor.class.getName() + "(queryTimeout=5)");
            ds.createPool();
            try (Connection pooled = ds.getConnection()) {
                assertDriverTimeout(pooled, 5, "a well-formed value is applied unchanged");
            }
            logs.assertMalformedValueReports(0,
                    "a well-formed value has nothing to report");
        }
    }

    @Test
    void unknownPropertyNameIsReportedAndKeepsTheDefault() throws Exception {
        try (PoolLogCapture logs = new PoolLogCapture()) {
            // the lowercase t makes this a different name, not a spelling of
            // the property: the intended ten seconds never arrive and the
            // one-second default goes on cancelling real queries
            DataSource ds = poolWith(QueryTimeoutInterceptor.class.getName() + "(querytimeout=10)");
            ds.createPool();
            try (Connection pooled = ds.getConnection()) {
                assertDriverTimeout(pooled, 1,
                        "an unrecognized name must not be read as the property - the default stays in"
                                + " effect, which is exactly why the typo must not pass in silence");
            }
            List<LogRecord> reports = logs.assertUnknownNameReports(2,
                    "the instance the pool configures at start plus the chain the first borrow builds;"
                            + " the pool-stop instance is configured after this capture is closed");
            for (LogRecord report : reports) {
                Assertions.assertTrue(report.getMessage().contains("[querytimeout]"),
                        "the report must name the unrecognized property: " + report.getMessage());
            }
        }
    }

    @Test
    void knownPropertyNamesAreSilent() throws Exception {
        try (PoolLogCapture logs = new PoolLogCapture()) {
            // useEquals is a name the JdbcInterceptor base reads; recognizing
            // it keeps the warning a report about typos and not about every
            // configuration that is more than a single property
            DataSource ds = poolWith(
                    QueryTimeoutInterceptor.class.getName() + "(queryTimeout=5,useEquals=false)");
            ds.createPool();
            try (Connection pooled = ds.getConnection()) {
                assertDriverTimeout(pooled, 5, "both known names apply as themselves");
            }
            logs.assertUnknownNameReports(0, "a configuration made only of known names is not a typo");
        }
    }

    /**
     * Captures what {@link QueryTimeoutInterceptor} logs, following the
     * handler idiom of the pool's other diagnostic tests. The canary record -
     * written through the very JULI logger the interceptor logs through, not
     * through JUL directly - proves the wiring observes those records before
     * the pool under test gets its chance to log.
     */
    private static final class PoolLogCapture implements AutoCloseable {
        private static final String CANARY = "query-timeout-malformed-property-handler-wiring-canary";

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

        PoolLogCapture() {
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
         * Every report of an unparsable queryTimeout, with the volume itself
         * pinned and each report checked to be a warning naming the property.
         * An empty list fails the positive tests as loudly as a chatty one
         * does: the assertions are not met by the interceptor having gone
         * quiet.
         */
        List<LogRecord> assertMalformedValueReports(int expected, String why) {
            List<LogRecord> seen;
            synchronized (records) {
                seen = new ArrayList<>(records);
            }
            List<LogRecord> reports = seen.stream()
                    .filter(r -> r.getMessage() != null && r.getMessage().contains(MALFORMED_REPORT))
                    .collect(Collectors.toList());
            Assertions.assertEquals(expected, reports.size(),
                    why + ", messages seen: " + seen.stream().map(LogRecord::getMessage)
                            .collect(Collectors.toList()));
            for (LogRecord record : reports) {
                Assertions.assertEquals(Level.WARNING.intValue(), record.getLevel().intValue(),
                        "the report of a bad value is a warning, not something quieter: " + record.getMessage());
                Assertions.assertTrue(record.getMessage().contains("queryTimeout"),
                        "the report must name the property it rejected: " + record.getMessage());
            }
            return reports;
        }

        /*
         * Every report of an unrecognized property name, with the volume
         * pinned and each report checked to be a warning naming the property,
         * the way the bad-value reports above are checked.
         */
        List<LogRecord> assertUnknownNameReports(int expected, String why) {
            List<LogRecord> seen;
            synchronized (records) {
                seen = new ArrayList<>(records);
            }
            List<LogRecord> reports = seen.stream()
                    .filter(r -> r.getMessage() != null && r.getMessage().contains(UNKNOWN_NAME_REPORT))
                    .collect(Collectors.toList());
            Assertions.assertEquals(expected, reports.size(),
                    why + ", messages seen: " + seen.stream().map(LogRecord::getMessage)
                            .collect(Collectors.toList()));
            for (LogRecord record : reports) {
                Assertions.assertEquals(Level.WARNING.intValue(), record.getLevel().intValue(),
                        "the report of a bad name is a warning, not something quieter: " + record.getMessage());
                Assertions.assertTrue(record.getMessage().contains("property"),
                        "the report must say it rejects a property: " + record.getMessage());
            }
            return reports;
        }

        @Override
        public void close() {
            logger.removeHandler(handler);
            logger.setLevel(originalLevel);
        }
    }
}
