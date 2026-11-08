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

import java.lang.reflect.Proxy;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * The SQL extraction the query report hooks perform, checked in
 * isolation. The SQL a statement was prepared with takes precedence over
 * the argument of the invoked method, an argument that is not a string is
 * not a query - it must not abort the report - and a batch executed on a
 * plain statement is reported under the name {@code batch}. A preparation
 * with no SQL at all is not a query either - it reaches no hook, yet the
 * proxy is still created.
 */
class TestQueryReportExtraction {

    private static final AbstractQueryReport REPORT = new AbstractQueryReport() {
        @Override
        protected void prepareStatement(String sql, long time) {
            // extraction only - nothing to record
        }

        @Override
        protected void prepareCall(String query, long time) {
            // extraction only - nothing to record
        }

        @Override
        public void closeInvoked() {
            // extraction only - nothing to record
        }
    };

    @Test
    void aNonStringArgumentIsNotAQuery() {
        // the argument of setFetchSize(int) and friends is not SQL
        Assertions.assertNull(REPORT.reportFailedQuery(null, new Object[] { Integer.valueOf(5) },
                "setFetchSize", 0, 1, null));
        // but the SQL of the prepared statement still is
        Assertions.assertEquals("SELECT 1", REPORT.reportFailedQuery("SELECT 1",
                new Object[] { Integer.valueOf(5) }, "setFetchSize", 0, 1, null));
    }

    @Test
    void aBatchOnAPlainStatementIsNamedBatch() {
        // a batch execution carries no SQL and no arguments at all
        Assertions.assertEquals("batch",
                REPORT.reportFailedQuery(null, null, "executeBatch", 0, 1, null));
        Assertions.assertEquals("batch",
                REPORT.reportQuery(null, new Object[0], "executeBatch", 0, 1));
        Assertions.assertEquals("batch",
                REPORT.reportSlowQuery(null, null, "executeBatch", 0, 1));
    }

    @Test
    void theSQLOfTheStatementWinsOverTheMethodArgument() {
        Assertions.assertEquals("CALL p()", REPORT.reportQuery("CALL p()",
                new Object[] { "SELECT 1" }, "execute", 0, 1));
        Assertions.assertEquals("SELECT 1", REPORT.reportSlowQuery(null,
                new Object[] { "SELECT 1" }, "executeQuery", 0, 1));
    }

    /**
     * Report that records whether its preparation hooks are invoked and
     * with what, and that books the statistics the real
     * {@code SlowQueryReport} books.
     */
    private static class RecordingReport extends SlowQueryReport {
        private boolean statementHookCalled;
        private boolean callHookCalled;
        private String calledSQL;

        @Override
        public void prepareStatement(String sql, long time) {
            statementHookCalled = true;
            super.prepareStatement(sql, time);
        }

        @Override
        public void prepareCall(String sql, long time) {
            callHookCalled = true;
            calledSQL = sql;
            super.prepareCall(sql, time);
        }
    }

    private static Statement delegateStatement() {
        return (Statement) Proxy.newProxyInstance(TestQueryReportExtraction.class.getClassLoader(),
                new Class<?>[] { Statement.class }, (proxy, method, args) -> null);
    }

    @Test
    void aPreparationWithoutSQLReachesNoHookAndBooksNoStats() throws Exception {
        // a driver that tolerates prepareCall(null) - the pool itself never
        // passes a null SQL, only a permissive driver plus an application
        // error gets this far - must report nothing for SQL that does not
        // exist: neither through the hook nor through a statistics entry
        // keyed by the empty query, which would occupy one of the
        // maxQueries slots of the per-pool map for good
        RecordingReport report = new RecordingReport();
        report.queries = new ConcurrentHashMap<>();

        Object callable = report.createStatement(null,
                Connection.class.getMethod("prepareCall", String.class),
                new Object[] { null }, delegateStatement(), 0);
        Assertions.assertFalse(report.callHookCalled || report.statementHookCalled,
                "a null SQL must not reach the preparation hooks");
        Assertions.assertTrue(report.queries.isEmpty(),
                "a null SQL must not book a statistics entry, keys were " + report.queries.keySet());
        // skipping the hook must not disturb the proxy creation either
        Assertions.assertTrue(Proxy.isProxyClass(callable.getClass()) && callable instanceof CallableStatement,
                "the callable statement proxy must still be created for a null SQL");

        Object prepared = report.createStatement(null,
                Connection.class.getMethod("prepareStatement", String.class),
                new Object[] { null }, delegateStatement(), 0);
        Assertions.assertFalse(report.statementHookCalled || report.callHookCalled,
                "a null SQL must not reach the preparation hooks");
        Assertions.assertTrue(report.queries.isEmpty(),
                "a null SQL must not book a statistics entry, keys were " + report.queries.keySet());
        Assertions.assertTrue(Proxy.isProxyClass(prepared.getClass()) && prepared instanceof PreparedStatement,
                "the prepared statement proxy must still be created for a null SQL");

        // a real SQL on any of the overloads is still reported as before
        report.createStatement(null,
                Connection.class.getMethod("prepareCall", String.class, int.class, int.class),
                new Object[] { "CALL p()", Integer.valueOf(1), Integer.valueOf(2) }, delegateStatement(), 0);
        Assertions.assertEquals("CALL p()", report.calledSQL,
                "the SQL of the prepareCall overload must still reach the hook");
        Set<String> keys = new HashSet<>();
        keys.add("CALL p()");
        Assertions.assertEquals(keys, report.queries.keySet(),
                "the SQL of the prepareCall overload must still be booked");
    }
}
