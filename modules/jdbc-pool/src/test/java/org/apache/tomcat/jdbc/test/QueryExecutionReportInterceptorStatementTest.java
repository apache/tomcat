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
package org.apache.tomcat.jdbc.test;

import org.junit.Test;

import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.Assert.*;

/**
 * Test with {@link java.sql.Statement}.
 *
 * @author Tadaya Tsuyukubo
 */
public class QueryExecutionReportInterceptorStatementTest extends QueryExecutionReportInterceptorTestBase {

    @Test
    public void testStatementSuccess() throws Exception {
        Statement statement = datasource.getConnection().createStatement();
        statement.execute("select 1");

        assertTrue(queryExecutionInfo.isSuccess());
        assertFalse(queryExecutionInfo.isBatch());
        assertEquals(1, queryExecutionInfo.getQueries().size());
        assertEquals("select 1", queryExecutionInfo.getQueries().get(0).getQuery());

    }

    @Test
    public void testStatementFail() throws Exception {
        Statement statement = datasource.getConnection().createStatement();
        try {
            statement.execute("wrong query");
            fail("invalid query should fail");
        } catch (SQLException e) {
        }

        verifyExecution(false, false, "wrong query");
    }

    @Test
    public void testStatementExecuteQuery() throws Exception {
        Statement statement = datasource.getConnection().createStatement();
        statement.executeQuery("select 1");

        verifyExecution(true, false, "select 1");
    }

    @Test
    public void testStatementExecuteUpdate() throws Exception {
        Statement statement = datasource.getConnection().createStatement();
        statement.executeUpdate("insert into users values (100, 'foo') ");

        verifyExecution(true, false, "insert into users values (100, 'foo') ");
    }

    @Test
    public void testStatementExecuteBatch() throws Exception {
        Statement statement = datasource.getConnection().createStatement();
        statement.addBatch("insert into users values (100, 'foo')");
        statement.addBatch("insert into users values (101, 'bar')");
        statement.executeBatch();

        verifyExecution(true, true,
                "insert into users values (100, 'foo')",
                "insert into users values (101, 'bar')");
    }

    @Test
    public void testStatementExecuteBatchWithWrongQuery() throws Exception {
        Statement statement = datasource.getConnection().createStatement();
        statement.addBatch("insert into users values (100, 'foo')");
        statement.addBatch("wrong query");
        statement.addBatch("insert into users values (101, 'bar')");
        try {
            statement.executeBatch();
            fail("invalid query should fail");
        } catch (SQLException e) {
        }

        verifyExecution(false, true,
                "insert into users values (100, 'foo')",
                "wrong query",
                "insert into users values (101, 'bar')");
    }

    @Test
    public void testStatementExecuteBatchWithClearBatch() throws Exception {
        Statement statement = datasource.getConnection().createStatement();
        statement.addBatch("insert into users values (100, 'foo')");
        statement.addBatch("insert into users values (101, 'bar')");
        statement.clearBatch();
        statement.addBatch("insert into users values (200, 'FOO')");
        statement.addBatch("insert into users values (201, 'BAR')");
        statement.executeBatch();

        verifyExecution(true, true,
                "insert into users values (200, 'FOO')",
                "insert into users values (201, 'BAR')");
    }


    private void verifyExecution(boolean success, boolean batch, String... queries) {
        assertEquals("Was query successful", success, queryExecutionInfo.isSuccess());
        assertEquals("Was batch query?", batch, queryExecutionInfo.isBatch());

        assertEquals(queries.length, queryExecutionInfo.getQueries().size());
        for (int i = 0; i < queries.length; i++) {
            assertEquals(queries[i], queryExecutionInfo.getQueries().get(i).getQuery());
        }

    }

}
