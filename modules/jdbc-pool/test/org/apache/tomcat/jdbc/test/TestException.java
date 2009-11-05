package org.apache.tomcat.jdbc.test;

import java.sql.Connection;
import org.apache.tomcat.jdbc.pool.ConnectionPool;
import org.apache.tomcat.jdbc.pool.JdbcInterceptor;
import org.apache.tomcat.jdbc.pool.PooledConnection;

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
