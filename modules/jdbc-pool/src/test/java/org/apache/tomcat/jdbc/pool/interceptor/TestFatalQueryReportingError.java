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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;
import org.apache.tomcat.jdbc.pool.DataSource;

/**
 * The query-reporting hooks must not disturb the application for an ordinary reporting failure, but they follow the
 * module's fatal-error policy too: a {@link java.lang.VirtualMachineError} from a reporting hook propagates instead of
 * being logged and swallowed, while the recoverable {@link StackOverflowError} is isolated like any other report
 * failure.
 */
class TestFatalQueryReportingError extends JdbcPoolBaseTest {

    /**
     * Throws the error the current test selected from a reporting hook. Configured through {@code jdbcInterceptors},
     * so it must stay a public static class with a no-arg constructor.
     */
    public static class FatalReport extends SlowQueryReport {
        static volatile Error failure;

        private static void throwIfSelected() {
            Error selected = failure;
            if (selected != null) {
                throw selected;
            }
        }

        @Override
        protected String reportQuery(String query, Object[] args, String name, long start, long delta) {
            throwIfSelected();
            return super.reportQuery(query, args, name, start, delta);
        }

        @Override
        protected String reportFailedQuery(String query, Object[] args, String name, long start, long delta,
                Throwable t) {
            throwIfSelected();
            return super.reportFailedQuery(query, args, name, start, delta, t);
        }
    }

    @Test
    void aVirtualMachineErrorFromAReportHookPropagates() throws Exception {
        FatalReport.failure = new InternalError("fatal report");
        DataSource ds = reportingDataSource();
        try (Connection con = ds.getConnection(); Statement st = con.createStatement()) {
            Assertions.assertThrows(InternalError.class, () -> st.execute("SELECT 1"),
                    "a virtual-machine error from a reporting hook must propagate");
        } finally {
            FatalReport.failure = null;
            ds.close();
        }
    }

    @Test
    void aRecoverableErrorFromAReportHookIsIsolated() throws Exception {
        FatalReport.failure = new StackOverflowError("recoverable report");
        DataSource ds = reportingDataSource();
        try (Connection con = ds.getConnection(); Statement st = con.createStatement()) {
            st.execute("SELECT 1");
        } finally {
            FatalReport.failure = null;
            ds.close();
        }
    }

    @Test
    void aVirtualMachineErrorFromAFailedQueryReportPropagates() throws Exception {
        FatalReport.failure = new InternalError("fatal failed-query report");
        DataSource ds = reportingDataSource();
        try (Connection con = ds.getConnection(); Statement st = con.createStatement()) {
            Assertions.assertThrows(InternalError.class, () -> st.execute("SELECT * FROM no_such_table"),
                    "a virtual-machine error from the failed-query report must propagate");
        } finally {
            FatalReport.failure = null;
            ds.close();
        }
    }

    private DataSource reportingDataSource() throws SQLException {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setInitialSize(0);
        ds.getPoolProperties().setMinIdle(0);
        ds.getPoolProperties().setJdbcInterceptors(FatalReport.class.getName() + "(threshold=-1)");
        ds.createPool();
        return ds;
    }
}
