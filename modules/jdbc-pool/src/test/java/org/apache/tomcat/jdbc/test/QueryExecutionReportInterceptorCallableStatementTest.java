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

import java.sql.CallableStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Test with {@link java.sql.CallableStatement}.
 *
 * @author Tadaya Tsuyukubo
 */
public class QueryExecutionReportInterceptorCallableStatementTest extends QueryExecutionReportInterceptorTestBase {

    @Test
    public void testExecuteWithNoParam() throws Exception {
        String query = "CALL sp_with_no_param()";
        CallableStatement cs = datasource.getConnection().prepareCall(query);
        cs.execute();

        verifyNonBatchExecution(query);
        verifyNoParameter();
    }

    @Test
    public void testExecuteWithParamByPosition() throws Exception {
        String query = "CALL sp_with_param(?, ?)";
        CallableStatement cs = datasource.getConnection().prepareCall(query);
        cs.setInt(1, 100);  // position based param
        cs.setString(2, "FOO");
        cs.execute();

        verifyNonBatchExecution(query);
        verifyQueryParameter(0, "100", "FOO");
    }

    @Test
    public void testExecuteWithParamByName() throws Exception {
        String query = "CALL sp_with_named_param(?, ?)";
        CallableStatement cs = datasource.getConnection().prepareCall(query);
        cs.setInt("ID", 100);  // named parameter
        cs.setString("NAME", "FOO");
        cs.execute();

        verifyNonBatchExecution(query);
        verifyNamedParameter();
    }

    @Test
    public void testExecuteWithNoParamSpecified() throws Exception {
        String query = "CALL sp_with_param(?, ?)";
        CallableStatement cs = datasource.getConnection().prepareCall(query);
        try {
            cs.execute();
            fail("not setting parameter should fail");
        } catch (SQLException e) {
        }
        assertFalse("is success", queryExecutionInfo.isSuccess());
    }

    @Test
    public void testExecuteUpdateWithNoParam() throws Exception {
        String query = "CALL sp_with_no_param()";
        CallableStatement cs = datasource.getConnection().prepareCall(query);
        cs.executeUpdate();

        verifyNonBatchExecution(query);
        verifyNoParameter();
    }


    @Test
    public void testExecuteUpdateWithParamByPosition() throws Exception {
        String query = "CALL sp_with_param(?, ?)";
        CallableStatement cs = datasource.getConnection().prepareCall(query);
        cs.setInt(1, 100);  // position based param
        cs.setString(2, "FOO");
        cs.executeUpdate();

        verifyNonBatchExecution(query);
        verifyQueryParameter(0, "100", "FOO");
    }

    @Test
    public void testExecuteUpdateWithParamByName() throws Exception {
        String query = "CALL sp_with_named_param(?, ?)";
        CallableStatement cs = datasource.getConnection().prepareCall(query);
        cs.setInt("ID", 100);  // named parameter
        cs.setString("NAME", "FOO");
        cs.executeUpdate();

        verifyNonBatchExecution(query);
        verifyNamedParameter();
    }

    @Test
    public void testExecuteUpdateWithNoParamSpecified() throws Exception {
        String query = "CALL sp_with_param(?, ?)";
        CallableStatement cs = datasource.getConnection().prepareCall(query);
        try {
            cs.executeUpdate();
            fail("not setting parameter should fail");
        } catch (SQLException e) {
        }
        assertFalse("is success", queryExecutionInfo.isSuccess());
    }

    @Test
    public void testExecuteQueryWithNoParam() throws Exception {
        String query = "CALL sp_with_no_param()";
        CallableStatement cs = datasource.getConnection().prepareCall(query);
        cs.executeQuery();

        verifyNonBatchExecution(query);
        verifyNoParameter();
    }


    @Test
    public void testExecuteQueryWithParamByPosition() throws Exception {
        String query = "CALL sp_with_param(?, ?)";
        CallableStatement cs = datasource.getConnection().prepareCall(query);
        cs.setInt(1, 100);  // position based param
        cs.setString(2, "FOO");
        cs.executeQuery();

        verifyNonBatchExecution(query);
        verifyQueryParameter(0, "100", "FOO");
    }

    @Test
    public void testExecuteQueryWithParamByName() throws Exception {
        String query = "CALL sp_with_named_param(?, ?)";
        CallableStatement cs = datasource.getConnection().prepareCall(query);
        cs.setInt("ID", 100);  // named parameter
        cs.setString("NAME", "FOO");
        cs.executeQuery();

        verifyNonBatchExecution(query);
        verifyNamedParameter();
    }

    @Test
    public void testExecuteQueryWithNoParamSpecified() throws Exception {
        String query = "CALL sp_with_named_param(?, ?)";
        CallableStatement cs = datasource.getConnection().prepareCall(query);
        try {
            cs.executeQuery();
            fail("not setting parameter should fail");
        } catch (SQLException e) {
        }
        assertFalse("is success", queryExecutionInfo.isSuccess());
    }

    @Test
    public void testClearParameters() throws Exception {
        String query = "CALL sp_with_param(?, ?)";
        CallableStatement cs = datasource.getConnection().prepareCall(query);
        cs.setInt(1, 100);
        cs.setString(2, "FOO");

        cs.clearParameters();

        cs.setInt(1, 200);
        cs.setString(2, "BAR");
        cs.executeQuery();

        verifyNonBatchExecution(query);
        verifyQueryParameter(0, "200", "BAR");
    }

    @Test
    public void testExecuteBatch() throws Exception {
        String query = "CALL sp_with_param(?, ?)";
        CallableStatement cs = datasource.getConnection().prepareCall(query);
        cs.setInt(1, 100);
        cs.setString(2, "FOO");
        cs.addBatch();
        cs.setInt(1, 200);
        cs.setString(2, "BAR");
        cs.addBatch();
        cs.executeBatch();

        verifyBatchExecution(query, 2);
        verifyQueryParameter(0, "100", "FOO");
        verifyQueryParameter(1, "200", "BAR");
    }

    @Test
    public void testBatchWithClearBatch() throws Exception {
        String query = "CALL sp_with_param(?, ?)";
        CallableStatement cs = datasource.getConnection().prepareCall(query);
        cs.setInt(1, 100);
        cs.setString(2, "FOO");
        cs.addBatch();

        cs.clearBatch();

        cs.setInt(1, 200);
        cs.setString(2, "BAR");
        cs.addBatch();
        cs.executeBatch();

        verifyBatchExecution(query, 1);
        verifyQueryParameter(0, "200", "BAR");
    }

    @Test
    public void testBatchWithClearBatchOnly() throws Exception {
        String query = "CALL sp_with_param(?, ?)";
        CallableStatement cs = datasource.getConnection().prepareCall(query);
        cs.setInt(1, 100);
        cs.setString(2, "FOO");
        cs.addBatch();

        cs.clearBatch();
        cs.executeBatch();

        verifyBatchExecution(query, 0);
    }


    @Test
    public void testBatchWithClearParameters() throws Exception {
        String query = "CALL sp_with_param(?, ?)";
        CallableStatement cs = datasource.getConnection().prepareCall(query);
        cs.setInt(1, 100);
        cs.setString(2, "FOO");

        cs.clearParameters();

        cs.setInt(1, 200);
        cs.setString(2, "BAR");
        cs.addBatch();
        cs.executeBatch();

        verifyBatchExecution(query, 1);
        verifyQueryParameter(0, "200", "BAR");
    }

    @Test
    public void testBatchWithAddBatchThenClearParameters() throws Exception {
        String query = "CALL sp_with_param(?, ?)";
        CallableStatement cs = datasource.getConnection().prepareCall(query);
        cs.setInt(1, 100);
        cs.setString(2, "FOO");
        cs.addBatch();
        cs.clearParameters();
        cs.executeBatch();

        verifyBatchExecution(query, 1);
        verifyQueryParameter(0, "100", "FOO");
    }

    @Test
    public void testSetNull() throws Exception {
        String query = "CALL sp_with_param(?, ?)";
        CallableStatement cs = datasource.getConnection().prepareCall(query);
        cs.setNull(1, Types.INTEGER);
        cs.setNull(2, Types.VARCHAR);
        cs.execute();

        verifyNonBatchExecution(query);
        verifyQueryParameter(0, null, null);
    }

    @Test(expected = SQLException.class)
    public void testParameterNameIsNull() throws Exception {
        String query = "CALL sp_with_param(?, ?)";
        CallableStatement cs = datasource.getConnection().prepareCall(query);
        cs.setInt(null, 100);
    }

    private void verifyNonBatchExecution(String query) {
        assertFalse("isStatement", queryExecutionInfo.isStatement());
        assertFalse("isPreparedStatement", queryExecutionInfo.isPreparedStatement());
        assertTrue("isCallableStatement", queryExecutionInfo.isCallableStatement());
        assertEquals(QueryExecutionReportInterceptor.StatementType.CALLABLE, queryExecutionInfo.getStatementType());

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

    private void verifyQueryParameter(int paramsIndex, String id, String name) {
        List<Map<String, String>> params = queryExecutionInfo.getQueries().get(0).getParams();
        Map<String, String> queryParams = params.get(paramsIndex);
        assertEquals("parameter size", 2, queryParams.size());
        assertTrue(queryParams.containsKey("1"));
        assertEquals(id, queryParams.get("1"));
        assertTrue(queryParams.containsKey("2"));
        assertEquals(name, queryParams.get("2"));
    }

    private void verifyNoParameter() {
        List<Map<String, String>> params = queryExecutionInfo.getQueries().get(0).getParams();
        Map<String, String> queryParams = params.get(0);
        assertEquals("parameter size", 0, queryParams.size());
    }

    private void verifyNamedParameter() {
        List<Map<String, String>> params = queryExecutionInfo.getQueries().get(0).getParams();
        Map<String, String> queryParams = params.get(0);
        assertEquals("parameter size", 2, queryParams.size());
        assertTrue(queryParams.containsKey("ID"));
        assertEquals("100", queryParams.get("ID"));
        assertTrue(queryParams.containsKey("NAME"));
        assertEquals("FOO", queryParams.get("NAME"));
    }

    private void verifyBatchExecution(String query, int numOfBatch) {
        assertFalse("isStatement", queryExecutionInfo.isStatement());
        assertFalse("isPreparedStatement", queryExecutionInfo.isPreparedStatement());
        assertTrue("isCallableStatement", queryExecutionInfo.isCallableStatement());
        assertEquals(QueryExecutionReportInterceptor.StatementType.CALLABLE, queryExecutionInfo.getStatementType());

        assertTrue("is success", queryExecutionInfo.isSuccess());
        assertTrue("is batch", queryExecutionInfo.isBatch());

        assertEquals(1, queryExecutionInfo.getQueries().size());
        assertEquals(query, queryExecutionInfo.getQueries().get(0).getQuery());
        List<Map<String, String>> params = queryExecutionInfo.getQueries().get(0).getParams();
        assertNotNull(params);
        assertEquals("num of batch params for non batch", numOfBatch, params.size());
    }

}
