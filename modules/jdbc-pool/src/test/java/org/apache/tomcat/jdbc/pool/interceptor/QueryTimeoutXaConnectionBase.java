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
import java.util.logging.LogRecord;
import java.util.stream.Collectors;

import javax.sql.XAConnection;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;
import org.apache.tomcat.jdbc.pool.DataSource;
import org.apache.tomcat.jdbc.pool.XADataSource;
import org.apache.tomcat.jdbc.test.support.LogCapture;

/**
 * On a pool backed by a {@link javax.sql.XADataSource}, an application's
 * connection normally comes from {@link javax.sql.XAConnection#getConnection()}.
 * The pool answers that call with the pooled handle itself, so statements
 * created through it pass the interceptor chain and carry the configured
 * query timeout. There is no bypass to warn about, and the interceptor must
 * stay silent about one that does not exist.
 */
abstract class QueryTimeoutXaConnectionBase extends JdbcPoolBaseTest {

    /** Substring of the warning the old raw-session dispatch used to log. */
    private static final String OLD_BYPASS_WARNING = "will not reach the statements applications create";

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
     * The XA handle's {@code getConnection()} must answer the pooled handle
     * itself, so a statement created through it passes the chain and carries
     * the configured timeout. The interceptor must not warn about the bypass
     * the raw-session dispatch used to create.
     */
    @Test
    void anXaPoolAppliesTheTimeoutToTheLogicalConnectionItHandsOut() throws Exception {
        XADataSource ds = xaPool("(queryTimeout=5)");
        try (XaLogCapture logs = new XaLogCapture()) {
            ds.createPool();
            logs.assertNoBypassWarning("an XA pool hands out the pooled handle, so there is no bypass to warn about");

            XAConnection xc = ds.getXAConnection();
            try {
                Connection logical = xc.getConnection();
                Assertions.assertSame(xc, logical,
                        "getConnection() must answer the pooled handle itself, not the raw driver session");
                try (Statement st = logical.createStatement()) {
                    Assertions.assertEquals(5, st.getQueryTimeout(),
                            "a statement created through XAConnection.getConnection() must carry the configured timeout");
                }
            } finally {
                xc.close();
            }

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
    void aPlainPoolReportsNoBypassWarning() throws Exception {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setJdbcInterceptors(
                QueryTimeoutInterceptor.class.getName() + "(queryTimeout=5)");
        try (XaLogCapture logs = new XaLogCapture()) {
            ds.createPool();
            try (Connection pooled = ds.getConnection();
                    Statement st = pooled.createStatement()) {
                Assertions.assertEquals(5, st.getQueryTimeout(),
                        "the chained path must still apply the timeout the silence of this test is about");
            }
            logs.assertNoBypassWarning("a pool that serves its statements through the chain has no bypass to report");
        }
    }

    /**
     * A timeout of zero switches the interceptor off, so on such a pool nothing
     * is withheld and the bypass warning would report a missing protection
     * nobody configured. The silence here is the honest one.
     */
    @Test
    void anXaPoolWithoutAConfiguredTimeoutReportsNoBypassWarning() throws Exception {
        XADataSource ds = xaPool("(queryTimeout=0)");
        try (XaLogCapture logs = new XaLogCapture()) {
            ds.createPool();
            logs.assertNoBypassWarning(
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
    private static final class XaLogCapture extends LogCapture {
        XaLogCapture() {
            super("query-timeout-xa-handler-wiring-canary", QueryTimeoutInterceptor.class, QueryTimeoutInterceptor.class);
        }




        /*
         * No record may carry the old bypass warning: the raw-session dispatch
         * that made it true is gone, so reporting it would be a false alarm.
         */
        void assertNoBypassWarning(String why) {
            ArrayList<LogRecord> seen;
            synchronized (records) {
                seen = new ArrayList<>(records);
            }
            Assertions.assertTrue(
                    seen.stream().noneMatch(r -> r.getMessage() != null
                            && r.getMessage().contains(OLD_BYPASS_WARNING)),
                    why + ", messages seen: " + seen.stream().map(LogRecord::getMessage)
                            .collect(Collectors.toList()));
        }


    }
}
