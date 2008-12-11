package org.apache.tomcat.jdbc.test;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Map;

import org.apache.tomcat.jdbc.pool.ConnectionPool;
import org.apache.tomcat.jdbc.pool.interceptor.SlowQueryReport;

public class TestSlowQueryReport extends DefaultTestCase {

    public TestSlowQueryReport(String name) {
        super(name);
    }

    
    public void testSlowSql() throws Exception {
        int count = 3;
        this.init();
        this.datasource.setMaxActive(1);
        this.datasource.setJdbcInterceptors(SlowQueryReport.class.getName());
        Connection con = this.datasource.getConnection();
        String slowSql = "select count(1) from test where val1 like 'ewqeq' and val2 = 'ewrre' and val3 = 'sdada' and val4 = 'dadada'";
        for (int i=0; i<count; i++) {
            Statement st = con.createStatement();
            ResultSet rs = st.executeQuery(slowSql);
            rs.close();
            st.close();
        }
        Map<String,SlowQueryReport.QueryStats> map = SlowQueryReport.getPoolStats(datasource.getPool().getName());
        assertNotNull(map);
        assertEquals(1,map.size());
        String key = map.keySet().iterator().next();
        SlowQueryReport.QueryStats stats = map.get(key);
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
        assertNull(SlowQueryReport.getPoolStats(pool.getName()));
    }

    public void testFastSql() throws Exception {
        int count = 3;
        this.init();
        this.datasource.setMaxActive(1);
        this.datasource.setJdbcInterceptors(SlowQueryReport.class.getName());
        Connection con = this.datasource.getConnection();
        String slowSql = "select 1";
        for (int i=0; i<count; i++) {
            Statement st = con.createStatement();
            ResultSet rs = st.executeQuery(slowSql);
            rs.close();
            st.close();
        }
        Map<String,SlowQueryReport.QueryStats> map = SlowQueryReport.getPoolStats(datasource.getPool().getName());
        assertNotNull(map);
        assertEquals(0,map.size());
        ConnectionPool pool = datasource.getPool();
        con.close();
        tearDown();
        assertNull(SlowQueryReport.getPoolStats(pool.getName()));
    }    
    
}
