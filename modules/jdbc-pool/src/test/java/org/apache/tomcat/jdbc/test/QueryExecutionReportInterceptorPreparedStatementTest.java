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

import org.apache.tomcat.jdbc.pool.interceptor.QueryExecutionReportInterceptor;
import org.junit.Test;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Test with {@link java.sql.PreparedStatement}.
 *
 * @author Tadaya Tsuyukubo
 */
public class QueryExecutionReportInterceptorPreparedStatementTest extends QueryExecutionReportInterceptorTestBase {

    @Test
    public void testExecute() throws Exception {
        String query = "select 1 from users where id = ? and name = ?";

        PreparedStatement ps = datasource.getConnection().prepareStatement(query);
        ps.setInt(1, 99);
        ps.setString(2, "foo");
        ps.execute();

        verifyNonBatchExecution(query);
        verifyQueryParameter(0, "99", "foo");
    }

    @Test
    public void testExecuteQuery() throws Exception {
        String query = "select 1 from users where id = ? and name = ?";

        PreparedStatement ps = datasource.getConnection().prepareStatement(query);
        ps.setInt(1, 99);
        ps.setString(2, "foo");
        ps.executeQuery();

        verifyNonBatchExecution(query);
        verifyQueryParameter(0, "99", "foo");
    }

    @Test
    public void testClearParameters() throws Exception {
        String query = "select 1 from users where id = ? and name = ?";

        PreparedStatement ps = datasource.getConnection().prepareStatement(query);
        ps.setInt(1, 99);
        ps.setString(2, "foo");

        ps.clearParameters();

        ps.setInt(1, 100);
        ps.setString(2, "bar");
        ps.executeQuery();

        verifyNonBatchExecution(query);
        verifyQueryParameter(0, "100", "bar");
    }

    @Test
    public void testBatch() throws Exception {
        String query = "insert into users (id, name) values (?, ?)";

        PreparedStatement ps = datasource.getConnection().prepareStatement(query);
        ps.setInt(1, 99);
        ps.setString(2, "foo");
        ps.addBatch();
        ps.setInt(1, 100);
        ps.setString(2, "bar");
        ps.addBatch();
        ps.executeBatch();

        verifyBatchExecution(query, 2);
        verifyQueryParameter(0, "99", "foo");
        verifyQueryParameter(1, "100", "bar");
    }

    @Test
    public void testBatchWithClearBatch() throws Exception {
        String query = "insert into users (id, name) values (?, ?)";

        PreparedStatement ps = datasource.getConnection().prepareStatement(query);
        ps.setInt(1, 99);
        ps.setString(2, "foo");
        ps.addBatch();
        ps.clearBatch();
        ps.setInt(1, 100);
        ps.setString(2, "bar");
        ps.addBatch();
        ps.executeBatch();

        verifyBatchExecution(query, 1);
        verifyQueryParameter(0, "100", "bar");
    }

    @Test
    public void testBatchWithClearBatchOnly() throws Exception {
        String query = "insert into users (id, name) values (?, ?)";

        PreparedStatement ps = datasource.getConnection().prepareStatement(query);
        ps.setInt(1, 99);
        ps.setString(2, "foo");
        ps.addBatch();
        ps.clearBatch();
        ps.executeBatch();

        verifyBatchExecution(query, 0);
    }

    @Test
    public void testBatchWithClearParameters() throws Exception {
        String query = "insert into users (id, name) values (?, ?)";

        PreparedStatement ps = datasource.getConnection().prepareStatement(query);
        ps.setInt(1, 99);
        ps.setString(2, "foo");
        ps.clearParameters();
        ps.setInt(1, 100);
        ps.setString(2, "bar");
        ps.addBatch();
        ps.executeBatch();

        verifyBatchExecution(query, 1);
        verifyQueryParameter(0, "100", "bar");
    }

    @Test
    public void testBatchWithAddBatchThenClearParameters() throws Exception {
        String query = "insert into users (id, name) values (?, ?)";

        PreparedStatement ps = datasource.getConnection().prepareStatement(query);
        ps.setInt(1, 99);
        ps.setString(2, "foo");
        ps.addBatch();
        ps.clearParameters();
        ps.executeBatch();

        verifyBatchExecution(query, 1);
        verifyQueryParameter(0, "99", "foo");
    }

    @Test
    public void testFailedQuery() throws Exception {
        String query = "insert into users (id, name) values (?, ?)";

        PreparedStatement ps = datasource.getConnection().prepareStatement(query);
        try {
            ps.execute();
            fail("not setting parameter should fail");
        } catch (SQLException e) {
        }
        assertFalse("is success", queryExecutionInfo.isSuccess());
    }

    @Test
    public void testSetNull() throws Exception {
        String query = "select 1 from users where id = ? and name = ?";

        PreparedStatement ps = datasource.getConnection().prepareStatement(query);
        ps.setNull(1, Types.INTEGER);
        ps.setNull(2, Types.VARCHAR);
        ps.execute();

        verifyNonBatchExecution(query);
        verifyQueryParameter(0, null, null);
    }

    private void verifyNonBatchExecution(String query) {
        assertFalse("isStatement", queryExecutionInfo.isStatement());
        assertTrue("isPreparedStatement", queryExecutionInfo.isPreparedStatement());
        assertFalse("isCallableStatement", queryExecutionInfo.isCallableStatement());
        assertEquals(QueryExecutionReportInterceptor.StatementType.PREPARED, queryExecutionInfo.getStatementType());

        assertTrue("is success", queryExecutionInfo.isSuccess());
        assertFalse("is batch", queryExecutionInfo.isBatch());

        assertEquals(1, queryExecutionInfo.getQueries().size());
        assertEquals(query, queryExecutionInfo.getQueries().get(0).getQuery());
        List<Map<String, String>> params = queryExecutionInfo.getQueries().get(0).getParams();
        assertNotNull(params);
        assertEquals("num of batch params for non batch", 1, params.size());
        Map<String, String> queryParams = params.get(0);
        assertNotNull(queryParams);
    }

    private void verifyBatchExecution(String query, int batchSize) {
        assertFalse("isStatement", queryExecutionInfo.isStatement());
        assertTrue("isPreparedStatement", queryExecutionInfo.isPreparedStatement());
        assertFalse("isCallableStatement", queryExecutionInfo.isCallableStatement());
        assertEquals(QueryExecutionReportInterceptor.StatementType.PREPARED, queryExecutionInfo.getStatementType());

        assertTrue("is succsss", queryExecutionInfo.isSuccess());
        assertTrue("is batch", queryExecutionInfo.isBatch());

        assertEquals(1, queryExecutionInfo.getQueries().size());
        assertEquals(query, queryExecutionInfo.getQueries().get(0).getQuery());
        List<Map<String, String>> params = queryExecutionInfo.getQueries().get(0).getParams();
        assertNotNull(params);
        assertEquals("num of batch params for non batch", batchSize, params.size());
        for (Map<String, String> queryParams : params) {
            assertNotNull(queryParams);
        }
    }

    private void verifyQueryParameter(int paramsIndex, String id, String name) {
        List<Map<String, String>> params = queryExecutionInfo.getQueries().get(0).getParams();
        Map<String, String> queryParams = params.get(paramsIndex);
        assertEquals("parameter size", 2, queryParams.size());
        assertTrue(queryParams.containsKey("1"));
        assertEquals(id, queryParams.get("1"));
        assertTrue(queryParams.containsKey("2"));
        assertEquals(name, queryParams.get("2"));
    }


}
