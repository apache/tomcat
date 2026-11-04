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
import java.sql.SQLException;
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
 * The reporting interceptors take their configuration as text in the
 * {@code jdbcInterceptors} list, and text is where typos live. Their property
 * handling read that text with the raw parsers:
 * <ul>
 * <li>{@code Boolean.parseBoolean} answered every value that was not
 * {@code true} - {@code logSlow=ture}, {@code logSlow=yes}, {@code logSlow=1},
 * an empty value - with {@code false}, so a misspelled value switched a
 * reporting channel off with nothing said about it;</li>
 * <li>{@code Long.parseLong} and {@code Integer.parseInt} let a value that is
 * not a number - {@code maxQueries=1OO0} with a letter O, an empty
 * {@code threshold=} - leave the method as a {@link NumberFormatException},
 * which the pool reports as a failed start carrying no message at all about
 * the value it choked on; and</li>
 * <li>a name the interceptor does not know - {@code maxQueryes} - was dropped
 * in silence, so the setting the operator meant to change kept a default.</li>
 * </ul>
 * <p>
 * The tests pin the convention the module's other interceptors follow, and
 * which this campaign established for {@link QueryTimeoutInterceptor}: an
 * unrecognized name and an unparseable value are each reported, naming the
 * property and the value, and the setting keeps the value it carries. A
 * well-formed configuration stays silent and applies as it always did, and the
 * names the subclass adds - {@code notifyPool} and {@code objectName} - are
 * known to it, so a correct JMX configuration is never reported as a typo.
 * </p>
 */
class TestSlowQueryReportProperties extends JdbcPoolBaseTest {

    /*
     * Stable fragment of the report of an unparseable value. Pinning it is
     * what makes the silence assertions below assert on the absence of a real
     * report rather than on any incidental log line.
     */
    private static final String BAD_VALUE_REPORT = "and is not applied";

    /* Stable fragment of the report of an unrecognized property name. */
    private static final String UNKNOWN_NAME_REPORT = "is not recognized and is not applied";

    private DataSource poolWith(String interceptorEntry) {
        DataSource ds = newDataSource();
        ds.setName("SqrProperties-" + System.nanoTime());
        ds.setJdbcInterceptors(interceptorEntry);
        ds.getPoolProperties().setInitialSize(0);
        return ds;
    }

    /*
     * Borrows from a pool whose interceptor properties are being tested. A
     * property the interceptor cannot read must not decide the configuration
     * by failing the start, so the borrow itself is part of what is asserted:
     * a failure out of it is reported as the defect rather than as a test
     * error, naming the exception the pool handed the application.
     */
    private Connection borrow(DataSource ds, String why) {
        try {
            return ds.getConnection();
        } catch (SQLException x) {
            throw new AssertionError(why + ", SQLException: " + x.getClass().getName()
                    + ": " + x.getMessage(), x);
        }
    }

    @Test
    void aMisspelledLogSlowKeepsSlowQueryLoggingAndSaysSo() throws Exception {
        try (PropertyLogCapture logs = new PropertyLogCapture()) {
            // "ture" is a typo, not the value false: the channel the operator
            // meant to keep switched off in silence, and a pool that reports
            // nothing is the one kind of monitoring that goes unnoticed
            DataSource ds = poolWith(SlowQueryReport.class.getName() + "(threshold=0,logSlow=ture)");
            try (Connection con = borrow(ds,
                    "a boolean typo must not fail the pool start")) {
                SlowQueryReport report = interceptorOf(con, SlowQueryReport.class);
                Assertions.assertTrue(report.isLogSlow(),
                        "an unrecognizable boolean must not switch slow query logging off - the"
                                + " setting keeps the value it carries, which is what makes the"
                                + " misconfiguration something to say out loud");
                logs.assertBadValueReports(1, "the rejected value is reported", "logSlow", "[ture]");
            }
        }
    }

    @Test
    void anEmptyLogSlowIsRejectedLikeAnyOtherTypo() throws Exception {
        try (PropertyLogCapture logs = new PropertyLogCapture()) {
            DataSource ds = poolWith(SlowQueryReport.class.getName() + "(threshold=0,logSlow=)");
            try (Connection con = borrow(ds,
                    "an empty boolean value must not fail the pool start")) {
                SlowQueryReport report = interceptorOf(con, SlowQueryReport.class);
                Assertions.assertTrue(report.isLogSlow(),
                        "an empty value leaves the setting at the value it carries");
                logs.assertBadValueReports(1, "an empty value is a typo like any other",
                        "logSlow", "logSlow []");
            }
        }
    }

    @Test
    void anUnparseableMaxQueriesKeepsTheDefaultInsteadOfFailingTheStart() throws Exception {
        try (PropertyLogCapture logs = new PropertyLogCapture()) {
            // The letter O for a zero: the parse throws, and before the fix
            // the exception left setProperties to abort the start of the pool
            // with a failure that named neither the property nor the value
            DataSource ds = poolWith(SlowQueryReport.class.getName() + "(maxQueries=1OO0)");
            try (Connection con = borrow(ds,
                    "an unparseable maxQueries must not abort the start of the pool")) {
                SlowQueryReport report = interceptorOf(con, SlowQueryReport.class);
                Assertions.assertEquals(1000, report.maxQueries,
                        "the rejected value must leave the capacity at its default");
                logs.assertBadValueReports(1, "the rejected capacity is reported",
                        "maxQueries", "[1OO0]", "1000");
            }
        }
    }

    @Test
    void anEmptyThresholdKeepsTheDefaultInsteadOfFailingTheStart() throws Exception {
        try (PropertyLogCapture logs = new PropertyLogCapture()) {
            DataSource ds = poolWith(SlowQueryReport.class.getName() + "(threshold=)");
            try (Connection con = borrow(ds,
                    "an empty threshold must not abort the start of the pool")) {
                SlowQueryReport report = interceptorOf(con, SlowQueryReport.class);
                Assertions.assertEquals(1000, report.getThreshold(),
                        "the rejected value must leave the threshold at its default");
                logs.assertBadValueReports(1, "the empty threshold is reported like any other typo",
                        "threshold", "threshold []");
            }
        }
    }

    @Test
    void anUnknownPropertyNameIsReportedAndIgnored() throws Exception {
        try (PropertyLogCapture logs = new PropertyLogCapture()) {
            // The dropped "s" means the intended capacity of ten was never
            // configured; the default went on holding a thousand entries
            DataSource ds = poolWith(SlowQueryReport.class.getName() + "(maxQueryes=10)");
            try (Connection con = borrow(ds,
                    "an unrecognized property name must not fail the pool start")) {
                SlowQueryReport report = interceptorOf(con, SlowQueryReport.class);
                Assertions.assertEquals(1000, report.maxQueries,
                        "an unrecognized name must not be read as the property it resembles");
                logs.assertUnknownNameReports(1, "the unrecognized name is reported", "[maxQueryes]");
            }
        }
    }

    @Test
    void wellFormedValuesAreAppliedAndSilent() throws Exception {
        try (PropertyLogCapture logs = new PropertyLogCapture()) {
            DataSource ds = poolWith(SlowQueryReport.class.getName()
                    + "(threshold=250,maxQueries=5,logSlow=FALSE,logFailed=true,useEquals=false)");
            try (Connection con = borrow(ds, "a well-formed configuration must start the pool")) {
                SlowQueryReport report = interceptorOf(con, SlowQueryReport.class);
                Assertions.assertEquals(250, report.getThreshold(), "the threshold applies");
                Assertions.assertEquals(5, report.maxQueries, "the capacity applies");
                Assertions.assertFalse(report.isLogSlow(), "the log flag applies");
                Assertions.assertTrue(report.isLogFailed(), "the other log flag applies");
                logs.assertBadValueReports(0, "a well-formed value has nothing to report");
                logs.assertUnknownNameReports(0,
                        "useEquals is a name the JdbcInterceptor base reads; recognizing it keeps"
                                + " the report about typos");
            }
        }
    }

    @Test
    void theJmxInterceptorsOwnNamesAreKnownToIt() throws Exception {
        try (PropertyLogCapture logs = new PropertyLogCapture()) {
            // notifyPool and objectName belong to the subclass: a correct JMX
            // configuration must not be reported as a typo by the property
            // check it inherits
            DataSource ds = poolWith(SlowQueryReportJmx.class.getName()
                    + "(notifyPool=false,threshold=0,maxQueries=5,logSlow=false,logFailed=true,"
                    + "useEquals=false)");
            try (Connection con = borrow(ds,
                    "a well-formed JMX configuration must start the pool")) {
                SlowQueryReportJmx report = interceptorOf(con, SlowQueryReportJmx.class);
                Assertions.assertFalse(report.isNotifyPool(), "the subclass name applies");
                Assertions.assertEquals(0, report.getThreshold(), "the inherited name applies");
                logs.assertBadValueReports(0, "a well-formed JMX configuration has nothing to report");
                logs.assertUnknownNameReports(0,
                        "the names a subclass adds are known to it, so its own configuration is not"
                                + " a typo");
            }
        }
    }

    @Test
    void aMisspelledNotifyPoolKeepsItsDefaultAndSaysSo() throws Exception {
        try (PropertyLogCapture logs = new PropertyLogCapture()) {
            // The subclass read its own flag the same raw way, so the same
            // typo shape switched the notification route in silence
            DataSource ds = poolWith(SlowQueryReportJmx.class.getName() + "(notifyPool=ture)");
            try (Connection con = borrow(ds,
                    "a boolean typo on the subclass flag must not fail the pool start")) {
                SlowQueryReportJmx report = interceptorOf(con, SlowQueryReportJmx.class);
                Assertions.assertTrue(report.isNotifyPool(),
                        "an unrecognizable boolean must not move notifications off the route the"
                                + " default selects");
                logs.assertBadValueReports(1, "the rejected subclass value is reported",
                        "notifyPool", "[ture]");
            }
        }
    }

    /**
     * Captures what the reporting interceptors log, following the handler
     * idiom of the pool's other diagnostic tests. Both loggers of the family
     * are observed, since the base class and the subclass log under their own
     * names, and the canary record - written through the very JULI loggers the
     * interceptors log through, not through JUL directly - proves the wiring
     * observes those records before the configuration under test gets its
     * chance to log.
     */
    private static final class PropertyLogCapture implements AutoCloseable {
        private static final String CANARY = "slow-query-properties-handler-wiring-canary";

        private final Class<?>[] sources = { SlowQueryReport.class, SlowQueryReportJmx.class };
        private final List<LogRecord> records = new ArrayList<>();
        private final List<Logger> loggers = new ArrayList<>();
        private final List<Level> originalLevels = new ArrayList<>();
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

        PropertyLogCapture() {
            for (Class<?> source : sources) {
                Logger logger = Logger.getLogger(source.getName());
                loggers.add(logger);
                originalLevels.add(logger.getLevel());
                logger.addHandler(handler);
                logger.setLevel(Level.ALL);
            }
            LogFactory.getLog(SlowQueryReport.class).error(CANARY);
            synchronized (records) {
                if (records.stream().noneMatch(r -> CANARY.equals(r.getMessage()))) {
                    throw new AssertionError("the test handler must observe what these classes log");
                }
            }
        }

        private List<LogRecord> matching(String fragment, boolean excludingUnknown) {
            List<LogRecord> seen;
            synchronized (records) {
                seen = new ArrayList<>(records);
            }
            return seen.stream()
                    .filter(r -> r.getMessage() != null && r.getMessage().contains(fragment))
                    .filter(r -> !excludingUnknown || !r.getMessage().contains(UNKNOWN_NAME_REPORT))
                    .collect(Collectors.toList());
        }

        private static void assertIsWarning(LogRecord record) {
            Assertions.assertEquals(Level.WARNING.intValue(), record.getLevel().intValue(),
                    "the report of a bad configuration is a warning, not something quieter: "
                            + record.getMessage());
        }

        /*
         * At least {@code expected} reports of an unparseable value, each a
         * warning naming the property and carrying every one of the given
         * fragments. The pool configures the properties once on its definition
         * instance and again on the chain of each physical session, so the
         * floor is what is pinned rather than an exact count; an empty list
         * fails as loudly as a silent one does, since the assertions are not
         * met by the interceptor having said nothing.
         */
        void assertBadValueReports(int expected, String why, String... fragments) {
            List<LogRecord> reports = matching(BAD_VALUE_REPORT, true);
            assertAtLeast(expected, why, reports);
            for (LogRecord report : reports) {
                assertIsWarning(report);
                for (String fragment : fragments) {
                    Assertions.assertTrue(report.getMessage().contains(fragment),
                            "the report must carry [" + fragment + "]: " + report.getMessage());
                }
            }
        }

        void assertUnknownNameReports(int expected, String why, String... fragments) {
            List<LogRecord> reports = matching(UNKNOWN_NAME_REPORT, false);
            assertAtLeast(expected, why, reports);
            for (LogRecord report : reports) {
                Assertions.assertEquals(Level.WARNING.intValue(), report.getLevel().intValue(),
                        "the report of a bad name is a warning, not something quieter: "
                                + report.getMessage());
                for (String fragment : fragments) {
                    Assertions.assertTrue(report.getMessage().contains(fragment),
                            "the report must name the unrecognized property: " + report.getMessage());
                }
            }
        }

        private static void assertAtLeast(int expected, String why, List<LogRecord> reports) {
            if (expected == 0) {
                Assertions.assertEquals(0, reports.size(),
                        why + ", messages seen: " + reports.stream().map(LogRecord::getMessage)
                                .collect(Collectors.toList()));
                return;
            }
            Assertions.assertTrue(reports.size() >= expected,
                    why + ", expected at least " + expected + " but saw " + reports.size()
                            + ", messages: " + reports.stream().map(LogRecord::getMessage)
                                    .collect(Collectors.toList()));
        }

        @Override
        public void close() {
            for (Logger logger : loggers) {
                logger.removeHandler(handler);
            }
            for (int i = 0; i < loggers.size(); i++) {
                loggers.get(i).setLevel(originalLevels.get(i));
            }
        }
    }
}
