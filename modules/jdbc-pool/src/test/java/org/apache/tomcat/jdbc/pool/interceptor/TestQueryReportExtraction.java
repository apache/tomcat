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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * The SQL extraction the query report hooks perform, checked in
 * isolation. The SQL a statement was prepared with takes precedence over
 * the argument of the invoked method, an argument that is not a string is
 * not a query - it must not abort the report - and a batch executed on a
 * plain statement is reported under the name {@code batch}.
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
                "setFetchSize", 0, null));
        // but the SQL of the prepared statement still is
        Assertions.assertEquals("SELECT 1", REPORT.reportFailedQuery("SELECT 1",
                new Object[] { Integer.valueOf(5) }, "setFetchSize", 0, null));
    }

    @Test
    void aBatchOnAPlainStatementIsNamedBatch() {
        // a batch execution carries no SQL and no arguments at all
        Assertions.assertEquals("batch",
                REPORT.reportFailedQuery(null, null, "executeBatch", 0, null));
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
}
