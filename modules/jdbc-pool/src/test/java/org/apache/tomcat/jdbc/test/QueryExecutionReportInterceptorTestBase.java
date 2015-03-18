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
import org.h2.tools.SimpleResultSet;
import org.junit.Before;

import java.sql.*;

/**
 * Base class for testing {@link org.apache.tomcat.jdbc.pool.interceptor.QueryExecutionReportInterceptor}.
 *
 * @author Tadaya Tsuyukubo
 */
public class QueryExecutionReportInterceptorTestBase extends DefaultTestCase {

    protected static QueryExecutionReportInterceptor.QueryExecutionInfo queryExecutionInfo;

    public static class MyTestInterceptor extends QueryExecutionReportInterceptor {

        @Override
        protected void report(QueryExecutionInfo queryExecutionInfo) {
            super.report(queryExecutionInfo);

            // set to static variable for verification
            QueryExecutionReportInterceptorTestBase.queryExecutionInfo = queryExecutionInfo;
        }
    }

    @Before
    public void setUp() throws Exception {
        this.datasource.setUrl("jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=false");
        this.datasource.setJdbcInterceptors(MyTestInterceptor.class.getName());

        String classPath = QueryExecutionReportInterceptorTestBase.class.getName();

        String dataTable = "create table if not exists users (id int, name varchar)";

        // create stored procedure
        String spWithNoParam = "CREATE ALIAS IF NOT EXISTS sp_with_no_param FOR \"" + classPath + ".spWithNoParam\"";
        String spWithParam = "CREATE ALIAS IF NOT EXISTS sp_with_param FOR \"" + classPath + ".spWithParam\"";
        String spWithNamedParam = "CREATE ALIAS IF NOT EXISTS sp_with_named_param FOR \"" + classPath + ".spWithNamedParam\"";

        Statement statement = datasource.getConnection().createStatement();
        statement.addBatch(dataTable);
        statement.addBatch(spWithNoParam);
        statement.addBatch(spWithParam);
        statement.addBatch(spWithNamedParam);
        statement.executeBatch();
        statement.close();


        QueryExecutionReportInterceptorTestBase.queryExecutionInfo = null;
    }


    // h2 stored procedure
    public static void spWithNoParam() throws SQLException {
    }

    // h2 stored procedure
    public static int spWithParam(Connection conn, int id, String name) throws SQLException {
        return 99;
    }

    // h2 stored procedure
    public static ResultSet spWithNamedParam(Connection conn, int id, String name) throws SQLException {
        // H2 DB way to allow CallableStatement to use named parameters
        SimpleResultSet rs = new SimpleResultSet();
        rs.addColumn("ID", Types.INTEGER, 0, 0);
        rs.addColumn("NAME", Types.VARCHAR, 0, 0);
        return rs;
    }

}
