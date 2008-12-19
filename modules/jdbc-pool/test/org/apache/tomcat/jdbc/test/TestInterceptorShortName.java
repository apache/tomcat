package org.apache.tomcat.jdbc.test;

import java.sql.Connection;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.tomcat.jdbc.pool.ConnectionPool;
import org.apache.tomcat.jdbc.pool.JdbcInterceptor;
import org.apache.tomcat.jdbc.pool.PooledConnection;
import org.apache.tomcat.jdbc.pool.interceptor.TestInterceptor;

public class TestInterceptorShortName extends DefaultTestCase {

    public TestInterceptorShortName(String name) {
        super(name);
    }
    
    public void testShortInterceptor() throws Exception {
        this.datasource = this.createDefaultDataSource();
        this.datasource.setJdbcInterceptors("TestInterceptor");
        this.datasource.setMaxActive(1);
        Connection con = this.datasource.getConnection();
        assertTrue("Pool should have been started.",TestInterceptor.poolstarted);
        assertEquals("Only one interceptor should have been called setProperties",1,TestInterceptor.instancecount.get());
        con.close();
        this.datasource.close();
        assertTrue("Pool should have been closed.",TestInterceptor.poolclosed);
    }
    
    

}
