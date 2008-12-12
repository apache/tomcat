/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tomcat.jdbc.test;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Map;

import org.apache.tomcat.jdbc.pool.ConnectionPool;
import org.apache.tomcat.jdbc.pool.interceptor.SlowQueryReportJmx;

public class TestSlowQueryReport extends DefaultTestCase {

    public TestSlowQueryReport(String name) {
        super(name);
    }
    
    public void testSlowSql() throws Exception {
        int count = 3;
        this.init();
        this.datasource.setMaxActive(1);
        this.datasource.setJdbcInterceptors(SlowQueryReportJmx.class.getName());
        Connection con = this.datasource.getConnection();
        String slowSql = "select count(1) from test where val1 like 'ewqeq' and val2 = 'ewrre' and val3 = 'sdada' and val4 = 'dadada'";
        for (int i=0; i<count; i++) {
            Statement st = con.createStatement();
            ResultSet rs = st.executeQuery(slowSql);
            rs.close();
            st.close();
        }
        Map<String,SlowQueryReportJmx.QueryStats> map = SlowQueryReportJmx.getPoolStats(datasource.getPool().getName());
        assertNotNull(map);
        assertEquals(1,map.size());
        String key = map.keySet().iterator().next();
        SlowQueryReportJmx.QueryStats stats = map.get(key);
        System.out.println("Stats:"+stats);
        
        for (int i=0; i<count; i++) {
            PreparedStatement st = con.prepareStatement(slowSql);
            ResultSet rs = st.executeQuery();
            rs.close();
            st.close();
        }
        System.out.println("Stats:"+stats);
        
        for (int i=0; i<count; i++) {
            CallableStatement st = con.prepareCall(slowSql);
            ResultSet rs = st.executeQuery();
            rs.close();
            st.close();
        }
        System.out.println("Stats:"+stats);
        ConnectionPool pool = datasource.getPool();
        con.close();
        tearDown();
        //make sure we actually did clean up when the pool closed
        assertNull(SlowQueryReportJmx.getPoolStats(pool.getName()));
    }

    public void testFastSql() throws Exception {
        int count = 3;
        this.init();
        this.datasource.setMaxActive(1);
        this.datasource.setJdbcInterceptors(SlowQueryReportJmx.class.getName());
        Connection con = this.datasource.getConnection();
        String slowSql = "select 1";
        for (int i=0; i<count; i++) {
            Statement st = con.createStatement();
            ResultSet rs = st.executeQuery(slowSql);
            rs.close();
            st.close();
        }
        Map<String,SlowQueryReportJmx.QueryStats> map = SlowQueryReportJmx.getPoolStats(datasource.getPool().getName());
        assertNotNull(map);
        assertEquals(0,map.size());
        ConnectionPool pool = datasource.getPool();
        con.close();
        tearDown();
        assertNull(SlowQueryReportJmx.getPoolStats(pool.getName()));
    }    
    
    public void testFailedSql() throws Exception {
        int count = 3;
        this.init();
        this.datasource.setMaxActive(1);
        this.datasource.setJdbcInterceptors(SlowQueryReportJmx.class.getName());
        Connection con = this.datasource.getConnection();
        String slowSql = "select 1 from non_existent";
        int exceptionCount = 0;
        for (int i=0; i<count; i++) {
            Statement st = con.createStatement();
            try {
                ResultSet rs = st.executeQuery(slowSql);
                rs.close();
            }catch (Exception x) {
                exceptionCount++;
            }
            st.close();
            
        }
        Map<String,SlowQueryReportJmx.QueryStats> map = SlowQueryReportJmx.getPoolStats(datasource.getPool().getName());
        assertNotNull(map);
        assertEquals(1,map.size());
        ConnectionPool pool = datasource.getPool();
        String key = map.keySet().iterator().next();
        SlowQueryReportJmx.QueryStats stats = map.get(key);
        System.out.println("Stats:"+stats);
        con.close();
        tearDown();
        assertNull(SlowQueryReportJmx.getPoolStats(pool.getName()));
    }    


}
