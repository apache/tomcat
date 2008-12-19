package org.apache.tomcat.jdbc.pool.interceptor;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.tomcat.jdbc.pool.ConnectionPool;
import org.apache.tomcat.jdbc.pool.JdbcInterceptor;
import org.apache.tomcat.jdbc.pool.PooledConnection;
import org.apache.tomcat.jdbc.pool.PoolProperties.InterceptorProperty;

public class TestInterceptor extends JdbcInterceptor {
    public static boolean poolstarted = false;
    public static boolean poolclosed = false;
    public static AtomicInteger instancecount = new AtomicInteger(0);

    @Override
    public void poolClosed(ConnectionPool pool) {
        // TODO Auto-generated method stub
        super.poolClosed(pool);
        poolclosed = true;
    }

    @Override
    public void poolStarted(ConnectionPool pool) {
        super.poolStarted(pool);
        poolstarted = true;
    }

    @Override
    public void reset(ConnectionPool parent, PooledConnection con) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void setProperties(Map<String, InterceptorProperty> properties) {
        instancecount.incrementAndGet();
        super.setProperties(properties);
    }
    
    
    
}
