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
import java.sql.Statement;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;
import org.apache.tomcat.jdbc.pool.DataSource;
import org.apache.tomcat.jdbc.test.support.LogCapture;

/**
 * The slow and failed query WARN lines interpolate the statement text
 * into the pool log. That text is application-shaped, and wherever the
 * application concatenates, user-shaped data, and JULI writes message
 * text without newline escaping: carriage returns and line feeds inside
 * the SQL split the report into additional lines carried under the
 * pool's logger identity - a log forgery channel open to anyone who
 * shapes the SQL. The report lines now inline the line breaks as
 * spaces. What they must keep is the statement itself: the SQL stays
 * readable in the log line, and the statistics and their JMX
 * representation - the opt-in channel the operator reads - keep the
 * text exactly as executed, keyed by it.
 *
 * <p>Reproducer for the log-forgery hardening defect of
 * {@code defects-reports/defects-org.apache.tomcat.jdbc.pool.interceptor.SlowQueryReport.md}.</p>
 */
abstract class SlowQueryReportLogForgeryBase extends JdbcPoolBaseTest {

    private static final String FORGERY = "/*\r\n"
            + "Nov 04 00:00:00 localhost forged[0]: injected log entry\r\n"
            + "*/";

    private static String inlined(String sql) {
        return sql.replace('\r', ' ').replace('\n', ' ');
    }

    @Test
    void aSlowQueryReportLineCarriesNoLineBreaks() throws Exception {
        String poolName = "ForgedSlow-" + System.nanoTime();
        DataSource ds = newDataSource();
        ds.setName(poolName);
        ds.setJdbcInterceptors("org.apache.tomcat.jdbc.pool.interceptor.SlowQueryReport"
                + "(threshold=0,logSlow=true)");
        ds.getPoolProperties().setInitialSize(0);

        // A valid query with a line-broken comment block in its text: the
        // statement itself spans lines, so the report line it forges must
        // come out of it, not out of the report. The wait inside the
        // database (java.lang.Thread.sleep exposed by an alias) makes the
        // execution reliably over the threshold on any hardware.
        try (Connection admin = adminConnection();
                Statement st = admin.createStatement()) {
            st.execute("CREATE ALIAS IF NOT EXISTS SLOW_SLEEP FOR "
                    + "\"java.lang.Thread.sleep(long)\"");
        }
        String sql = "SELECT 1 " + FORGERY
                + " FROM SYSTEM_RANGE(1,1) WHERE SLOW_SLEEP(60) IS NULL";
        try (PoolLogCapture logs = new PoolLogCapture();
                Connection con = ds.getConnection();
                Statement st = con.createStatement()) {
            st.execute(sql);

            LogRecord report = logs.findQueryReportWarning("Slow Query Report SQL=");
            Assertions.assertFalse(report.getMessage().indexOf('\r') >= 0
                            || report.getMessage().indexOf('\n') >= 0,
                    "the report must fit the single line it owns, saw: "
                            + report.getMessage());
            Assertions.assertTrue(report.getMessage().contains(inlined(FORGERY)),
                    "inlining must keep the statement text readable, saw: "
                            + report.getMessage());
        }

        // The opt-in data channel keeps the text exactly as executed: the
        // sanitizing is for the log, not for what the operator reads
        Assertions.assertTrue(SlowQueryReport.getPoolStats(poolName).containsKey(sql),
                "the statistics must stay keyed by the exact SQL");
    }

    @Test
    void aFailedQueryReportLineCarriesNoLineBreaks() throws Exception {
        String poolName = "ForgedFailed-" + System.nanoTime();
        DataSource ds = newDataSource();
        ds.setName(poolName);
        ds.setJdbcInterceptors("org.apache.tomcat.jdbc.pool.interceptor.SlowQueryReport"
                + "(logFailed=true)");
        ds.getPoolProperties().setInitialSize(0);

        String sql = "SELECT * " + FORGERY + " FROM NO_SUCH_TABLE_" + System.nanoTime();
        try (PoolLogCapture logs = new PoolLogCapture();
                Connection con = ds.getConnection()) {
            try (Statement st = con.createStatement()) {
                Assertions.assertThrows(SQLException.class, () -> st.execute(sql),
                        "executing [" + sql + "] must fail");
            }

            LogRecord report = logs.findQueryReportWarning("Failed Query Report SQL=");
            Assertions.assertFalse(report.getMessage().indexOf('\r') >= 0
                            || report.getMessage().indexOf('\n') >= 0,
                    "the report must fit the single line it owns, saw: "
                            + report.getMessage());
            Assertions.assertTrue(report.getMessage().contains(inlined(FORGERY)),
                    "inlining must keep the statement text readable, saw: "
                            + report.getMessage());
        }
    }

    /**
     * Captures what {@link SlowQueryReport} logs, following the handler
     * idiom of the pool's other diagnostic tests; the canary record
     * proves the wiring observes the interceptor's own logger.
     */
    private static final class PoolLogCapture extends LogCapture {
        PoolLogCapture() {
            super("slow-query-log-forgery-handler-wiring-canary", SlowQueryReport.class, SlowQueryReport.class);
        }




        LogRecord findQueryReportWarning(String marker) {
            LogRecord[] hits;
            synchronized (records) {
                hits = records.stream()
                        .filter(r -> r.getLevel().intValue() == Level.WARNING.intValue()
                                && r.getMessage() != null && r.getMessage().contains(marker))
                        .toArray(LogRecord[]::new);
            }
            Assertions.assertEquals(1, hits.length,
                    "exactly one query report warning is expected, saw " + Arrays.toString(hits));
            return hits[0];
        }


    }
}
