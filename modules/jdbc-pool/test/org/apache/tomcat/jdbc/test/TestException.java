package org.apache.tomcat.jdbc.test;

import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.SQLException;

import org.apache.tomcat.jdbc.pool.ConnectionPool;
import org.apache.tomcat.jdbc.pool.JdbcInterceptor;
import org.apache.tomcat.jdbc.pool.PooledConnection;
import org.apache.tomcat.jdbc.pool.interceptor.AbstractCreateStatementInterceptor;
import org.apache.tomcat.jdbc.pool.interceptor.AbstractQueryReport;

public class TestException extends DefaultTestCase {

    public TestException(String name) {
        super(name);
    }
    
    public void testException() throws Exception {
        init();
        datasource.getPoolProperties().setJdbcInterceptors(TestInterceptor.class.getName());
        Connection con = datasource.getConnection();
        try {
            con.createStatement();
        }catch (Exception x) {
            
        }
    }
    
    
    public static class TestInterceptor extends JdbcInterceptor {

        @Override
        public void reset(ConnectionPool parent, PooledConnection con) {
            // TODO Auto-generated method stub
            
        }

    
    }

}
