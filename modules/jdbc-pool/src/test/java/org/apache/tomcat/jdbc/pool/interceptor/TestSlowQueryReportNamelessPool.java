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

import java.io.Serializable;
import java.lang.management.ManagementFactory;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import javax.management.MBeanServer;
import javax.management.Notification;
import javax.management.NotificationListener;
import javax.management.ObjectName;

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
 * The tests pin the halves of the corrected contract: a lookup of a name that
 * can never be keyed answers {@code null} like any other absent name - which is
 * also the answer the borrow-time reference in {@code reset} resolves, since it
 * comes through this lookup - and a pool that starts without a name is refused
 * by a failure that says which setting is missing.
 * </p>
 * <p>
 * The refusal only covers a definition the pool was started with, because a
 * reporting definition is refused at its start. The interceptor string is
 * mutable while the pool runs, so a definition the pool gains after its start
 * never meets that check, and the nameless pool and its unformable name arrive
 * at the borrow-time registration side by side. A borrow is not the place to
 * learn that nothing can be registered under no name: what the missing key can
 * cost there is the monitoring, never the query - so the last tests pin that a
 * late definition costs a nameless pool neither its borrows nor its statements.
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
    void aJmxReportingDefinitionAddedToANamelessPoolAfterTheStartStillServesItsBorrows()
            throws Exception {
        // A definition the pool gains after its start never runs poolStarted,
        // so the refusal above cannot reach it: this pairing exists, and its
        // borrow-time registration is where the missing key turns up. Nothing
        // can be registered under no name, and a borrow is not the place where
        // that is decided - the cost of the missing key is the monitoring.
        DataSource ds = namelessPoolWith("");
        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        try {
            ds.createPool();
            ds.setJdbcInterceptors(SlowQueryReportJmx.class.getName()
                    + "(notifyPool=false,threshold=0)");
            Assertions.assertDoesNotThrow(() -> {
                try (@SuppressWarnings("unused") Connection con = ds.getConnection()) {
                    // the borrow itself is the measured step
                }
            }, "a reporting definition the pool took on after its start must not cost a"
                    + " nameless pool its borrows: the NPE out of the name-keyed registry"
                    + " escapes the interceptor reset and fails every connection the pool"
                    + " hands out, discarding the physical connection each attempt opened");

            try (@SuppressWarnings("unused") Connection con = ds.getConnection()) {
                // borrowed - now make sure the quietly declined registration
                // declined rather than published under a made-up name
                Assertions.assertTrue(server.queryNames(new ObjectName("tomcat.jdbc:type="
                        + SlowQueryReportJmx.class.getName() + ",*"), null).stream()
                        .noneMatch(name -> "null".equals(name.getKeyProperty("name"))),
                        "a pool with no name has no name to publish under: declining the"
                                + " registration must not become a MBean under the literal"
                                + " text of a null reference");
            }
        } finally {
            ds.close();
        }
    }

    @Test
    void aSlowQueryOfANamelessPoolWithALateReportingDefinitionIsServedAndStillNotified()
            throws Exception {
        // Same pairing, met one step later: the slow-query notification names
        // its source by the registration the pool's name holds, and a name
        // that does not exist is not a key the registry can be asked about.
        // The proxy keeps a report from disturbing the application, so the
        // statement does come back - what the missing key silently takes is
        // the notification itself, and a monitoring channel that is configured
        // and quietly sends nothing is the outcome the class refuses everywhere
        // else for a name it cannot form.
        DataSource ds = namelessPoolWith("");
        try {
            ds.createPool();
            ds.setJdbcInterceptors(SlowQueryReportJmx.class.getName()
                    + "(notifyPool=false,threshold=0)");
            try (Connection admin = adminConnection();
                    Statement setup = admin.createStatement()) {
                setup.execute("CREATE ALIAS IF NOT EXISTS SLOW_SLEEP FOR "
                        + "\"java.lang.Thread.sleep(long)\"");
            }
            String slow = "SELECT 1 FROM SYSTEM_RANGE(1,1) WHERE SLOW_SLEEP(25) IS NULL";
            try (Connection con = ds.getConnection()) {
                List<Notification> received = new CopyOnWriteArrayList<>();
                NotificationListener listener = (notification, handback) -> received.add(notification);
                interceptorOf(con, SlowQueryReportJmx.class).addNotificationListener(listener, null, null);
                Assertions.assertDoesNotThrow(() -> {
                    try (Statement st = con.createStatement()) {
                        st.execute(slow);
                    }
                }, "a slow query of a pool that carries no name must still come back as a"
                        + " executed statement");
                Assertions.assertEquals(1, received.size(),
                        "the slow query must still be reported to the listeners the"
                                + " interceptor has: the source of a notification may lose the"
                                + " registered name it would rather carry, but a source it"
                                + " cannot look up must not cost the notification - the"
                                + " failure rides through as a swallowed warning, so the"
                                + " monitoring of a nameless pool is simply gone");
                Assertions.assertInstanceOf(Serializable.class, received.get(0).getSource(),
                        "the source of a notification is marshalled to listeners outside this"
                                + " JVM, so even the fallback for a pool that carries no name"
                                + " must be something that survives that delivery");
            }
        } finally {
            ds.close();
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
