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

import java.lang.management.ManagementFactory;
import java.sql.Connection;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;
import org.apache.tomcat.jdbc.pool.DataSource;

/**
 * The statistics are published under the pool name, registered when the
 * pool starts - but the name property itself is mutable at runtime: the
 * pool's own JMX exposes {@code setName}, which rewrites it on a running
 * pool. Resolving the registration through the <em>current</em> name
 * made a rename silently kill the statistics of a healthy pool: every
 * borrow resolved no statistics map, every execution booked nothing and
 * logged a false "no statistics store to book to" WARN,
 * and the close released the wrong registration (or, with the name nulled
 * out, threw into the close loop's catch), leaving the start-time entry -
 * and for the JMX definition the MBean, its broadcaster and the closed
 * pool's whole object graph - registered forever. The registration is now
 * recorded under the name the pool started with, and every lookup of it
 * - borrow, booking, release, JMX registration - goes through that
 * record, so renaming a live pool changes what the pool calls itself and
 * nothing else.
 *
 * <p>Reproducer for the mutable-name-keying and null-name-release defects
 * of {@code defects-reports/defects-org.apache.tomcat.jdbc.pool.interceptor.SlowQueryReport.md}.</p>
 */
class TestSlowQueryReportPoolRename extends JdbcPoolBaseTest {

    private static final String SQR =
            "org.apache.tomcat.jdbc.pool.interceptor.SlowQueryReport";
    private static final String SQRJ =
            "org.apache.tomcat.jdbc.pool.interceptor.SlowQueryReportJmx";

    private DataSource reportedPoolNamed(String name, String interceptor) {
        DataSource ds = newDataSource(); // JMX off: a renamed pool must not
        // clash with the pool's own MBean ObjectName, which really is keyed
        // by the live property
        ds.getPoolProperties().setName(name);
        ds.getPoolProperties().setJdbcInterceptors(interceptor);
        ds.getPoolProperties().setInitialSize(0);
        return ds;
    }

    private static void run(DataSource ds, String sql) throws Exception {
        try (Connection pooled = ds.getConnection();
                Statement st = pooled.createStatement()) {
            st.execute(sql);
        }
    }

    @Test
    void aRenamedLivePoolKeepsBookingAndReleasesWhatItRegistered() throws Exception {
        String startName = "rename-live-" + System.nanoTime();
        DataSource ds = reportedPoolNamed(startName, SQR + "(threshold=-1)");
        ds.createPool();
        run(ds, "SELECT 1");

        // exactly what the pool's JMX setName performs on a running pool
        String laterName = "rename-live-" + System.nanoTime();
        ds.getPoolProperties().setName(laterName);

        List<LogRecord> warnings = new ArrayList<>();
        Logger logger = Logger.getLogger(SlowQueryReport.class.getName());
        Level originalLevel = logger.getLevel();
        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                if (record.getLevel().intValue() >= Level.WARNING.intValue()) {
                    warnings.add(record);
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
        logger.addHandler(handler);
        logger.setLevel(Level.ALL);
        try {
            run(ds, "SELECT 1");
            run(ds, "SELECT 1");
        } finally {
            logger.removeHandler(handler);
            logger.setLevel(originalLevel);
        }

        Assertions.assertTrue(warnings.isEmpty(),
                "a healthy pool renamed at runtime must not turn its executions into"
                        + " closed-or-abandoned reports, saw: " + warnings);

        // what the rename must not do is move the registration: bookings
        // keep landing in the map the pool registered at start
        Assertions.assertEquals(3, SlowQueryReport.getPoolStats(startName).get("SELECT 1").getNrOfInvocations(),
                "executions after the rename must book into the registration made at start");
        Assertions.assertNull(SlowQueryReport.getPoolStats(laterName),
                "the new name holds no registration - the statistics were never moved");

        ds.close();
        Assertions.assertNull(SlowQueryReport.getPoolStats(startName),
                "the close must release the registration the pool actually made,"
                        + " not one keyed by the name it carries at close time");
    }

    @Test
    void aPoolClosedWhileNamelessStillReleasesItsRegistration() throws Exception {
        String startName = "rename-null-" + System.nanoTime();
        DataSource ds = reportedPoolNamed(startName, SQR + "(threshold=-1)");
        ds.createPool();
        run(ds, "SELECT 2");

        // a null name property must not strand the release: the name the NPE
        // used to throw from is taken, and the close loop only logs it at debug
        ds.getPoolProperties().setName(null);
        ds.close();

        Assertions.assertNull(SlowQueryReport.getPoolStats(startName),
                "the close must find the start-time registration even when the name"
                        + " property is null by close time");
    }

    @Test
    void aRenamedPoolReleasesItsJmxRegistrationOnClose() throws Exception {
        String startName = "rename-jmx-" + System.nanoTime();
        DataSource ds = reportedPoolNamed(startName, SQRJ + "(notifyPool=false)");
        ds.createPool();
        run(ds, "SELECT 3");

        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        ObjectName oname = new ObjectName("tomcat.jdbc:type=" + SlowQueryReportJmx.class.getName()
                + ",name=" + startName);
        Assertions.assertTrue(server.isRegistered(oname),
                "the interceptor MBean is registered under the name the pool runs under");

        ds.getPoolProperties().setName("rename-jmx-" + System.nanoTime());
        ds.close();

        Assertions.assertFalse(server.isRegistered(oname),
                "the close must take down the registration under the name it was"
                        + " published under, whatever the pool is called by then");
        Assertions.assertNull(SlowQueryReport.getPoolStats(startName),
                "and the statistics with it - a renamed close that missed the entry"
                        + " pinned the closed pool's whole graph through the statics");
    }
}
