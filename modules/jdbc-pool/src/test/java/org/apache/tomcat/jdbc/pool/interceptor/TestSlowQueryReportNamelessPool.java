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

import java.sql.SQLException;
import java.util.ArrayList;
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
 * Everything the reporting interceptors publish is keyed by the name of the
 * pool - the statistics map, the live-pool bookkeeping that decides when a
 * name's statistics are released, and the JMX registration of the subclass -
 * while the pool itself treats a name it does not have as a state it supports:
 * {@code ConnectionPool.getRegistrationName()} answers {@code null} for a pool
 * whose name property was cleared, and {@code releaseStatistics} steps around
 * that case. The interceptor's own reads did not: the statistics registry is a
 * {@link java.util.concurrent.ConcurrentHashMap}, which takes no {@code null}
 * key, so a nameless pool hit a raw {@link NullPointerException} on start -
 * reported to the application as a start failure with no message at all - and
 * {@code getPoolStats(null)} threw rather than answering the same
 * {@code null} it answers for any other name that holds nothing.
 * <p>
 * The tests pin the two halves of the corrected contract: a lookup of a name
 * that can never be keyed answers {@code null} like any other absent name -
 * which is also the answer the borrow-time reference in {@code reset} resolves,
 * since it comes through this lookup - and a pool that starts without a name is
 * refused by a failure that says which setting is missing.
 * </p>
 */
class TestSlowQueryReportNamelessPool extends JdbcPoolBaseTest {

    /*
     * Stable fragment of the report of a nameless pool. Pinning it is what
     * makes the assertions below assert on a real explanation rather than on
     * any incidental failure line.
     */
    private static final String NAME_REQUIRED_REPORT = "must have a name";

    private DataSource namelessPoolWith(String interceptorEntry) {
        DataSource ds = newDataSource();
        // The generated default is what a pool that never sets the name
        // property carries; clearing it is what this test is about
        ds.getPoolProperties().setName(null);
        ds.setJdbcInterceptors(interceptorEntry);
        ds.getPoolProperties().setInitialSize(0);
        return ds;
    }

    @Test
    void theStatisticsLookupAnswersForANameThatCanNeverBeKeyed() {
        Assertions.assertNull(SlowQueryReport.getPoolStats("NamelessLookup-" + System.nanoTime()),
                "a name nothing is registered under has no statistics, and the lookup says so");
        Assertions.assertNull(SlowQueryReport.getPoolStats(null),
                "a name that cannot be a key of the registry at all must give the same answer as"
                        + " any other absent name, rather than throwing from the map");
    }

    @Test
    void aNamelessPoolIsRefusedWithAReportThatNamesTheMissingSetting() throws Exception {
        try (StartLogCapture logs = new StartLogCapture()) {
            DataSource ds = namelessPoolWith(SlowQueryReport.class.getName() + "(threshold=0)");
            SQLException failure = Assertions.assertThrows(SQLException.class, ds::createPool,
                    "a pool that starts without the name its reporting interceptor keys everything"
                            + " on must not start as if it had one");
            Assertions.assertTrue(logs.reportsExplainTheMissingName(),
                    "the start failure must be explained by a report naming the setting that is"
                            + " missing, rather than by a bare NullPointerException out of the"
                            + " statistics registry, cause chain of the application-facing failure: "
                            + causeChain(failure));
        }
    }

    @Test
    void theJmxReportingDefinitionRefusesANamelessPoolTheSameWay() throws Exception {
        try (StartLogCapture logs = new StartLogCapture()) {
            DataSource ds = namelessPoolWith(SlowQueryReportJmx.class.getName()
                    + "(notifyPool=false,threshold=0)");
            SQLException failure = Assertions.assertThrows(SQLException.class, ds::createPool,
                    "the subclass publishes its MBean under the same name and must refuse a pool"
                            + " that carries none");
            Assertions.assertTrue(logs.reportsExplainTheMissingName(),
                    "the subclass must not fail differently from the interceptor it extends,"
                            + " cause chain: " + causeChain(failure));
        }
    }

    @Test
    void aPoolThatNeverSetsTheNamePropertyIsNotANamelessPool() throws Exception {
        try (StartLogCapture logs = new StartLogCapture()) {
            DataSource ds = newDataSource();
            ds.setJdbcInterceptors(SlowQueryReport.class.getName() + "(threshold=0)");
            ds.getPoolProperties().setInitialSize(0);
            ds.createPool();
            String name = ds.getPoolProperties().getName();
            Assertions.assertNotNull(name,
                    "a pool that never sets the name property carries the generated default");
            Assertions.assertNotNull(SlowQueryReport.getPoolStats(name),
                    "the generated default is a name like any other, and the interceptor registers"
                            + " its statistics under it");
            Assertions.assertFalse(logs.reportsExplainTheMissingName(),
                    "a pool that was never named is not a misconfiguration to report");
        }
    }

    private static String causeChain(Throwable x) {
        StringBuilder text = new StringBuilder();
        for (Throwable t = x; t != null; t = t.getCause()) {
            text.append(t.getClass().getName()).append(": ").append(t.getMessage()).append("; ");
        }
        return text.toString();
    }

    /**
     * Captures what the pool reports about a start that failed, following the
     * handler idiom of the pool's other diagnostic tests: the interceptor's
     * explanation reaches the log through the failure the pool is told about,
     * so both the pool's logger and the interceptor's are observed, and the
     * canary record - written through the very JULI logger the pool logs
     * through, not through JUL directly - proves the wiring observes those
     * records before the pool under test gets its chance to fail.
     */
    private static final class StartLogCapture implements AutoCloseable {
        private static final String CANARY = "nameless-pool-handler-wiring-canary";

        private final Class<?>[] sources = { ConnectionPool.class, SlowQueryReport.class };
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

        StartLogCapture() {
            for (Class<?> source : sources) {
                Logger logger = Logger.getLogger(source.getName());
                loggers.add(logger);
                originalLevels.add(logger.getLevel());
                logger.addHandler(handler);
                logger.setLevel(Level.ALL);
            }
            LogFactory.getLog(ConnectionPool.class).error(CANARY);
            synchronized (records) {
                if (records.stream().noneMatch(r -> CANARY.equals(r.getMessage()))) {
                    throw new AssertionError("the test handler must observe what these classes log");
                }
            }
        }

        /*
         * Whether anything logged so far explains the missing name - in its
         * own message or in the failure it carries, since the pool reports the
         * interceptor's failure as its own with the interceptor's message one
         * link down.
         */
        boolean reportsExplainTheMissingName() {
            synchronized (records) {
                return records.stream().anyMatch(StartLogCapture::explainsTheMissingName);
            }
        }

        private static boolean explainsTheMissingName(LogRecord record) {
            if (record.getMessage() != null && record.getMessage().contains(NAME_REQUIRED_REPORT)) {
                return true;
            }
            for (Throwable t = record.getThrown(); t != null; t = t.getCause()) {
                if (t.getMessage() != null && t.getMessage().contains(NAME_REQUIRED_REPORT)) {
                    return true;
                }
            }
            return false;
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
