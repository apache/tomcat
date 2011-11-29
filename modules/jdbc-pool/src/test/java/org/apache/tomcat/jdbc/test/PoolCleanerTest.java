package org.apache.tomcat.jdbc.test;

import org.apache.tomcat.jdbc.pool.ConnectionPool;
import org.apache.tomcat.jdbc.pool.DataSource;

public class PoolCleanerTest extends DefaultTestCase {

    public PoolCleanerTest(String name) {
        super(name);
    }
    
    public void testPoolCleaner() throws Exception {
        datasource.getPoolProperties().setTimeBetweenEvictionRunsMillis(2000);
        datasource.getPoolProperties().setTestWhileIdle(true);
        assertEquals("Pool cleaner should not be started yet.",0,ConnectionPool.getPoolCleaners().size() );
        assertNull("Pool timer should be null", ConnectionPool.getPoolTimer());
        
        datasource.getConnection().close();
        assertEquals("Pool cleaner should have 1 cleaner.",1,ConnectionPool.getPoolCleaners().size() );
        assertNotNull("Pool timer should not be null", ConnectionPool.getPoolTimer());
        
        datasource.close();
        assertEquals("Pool shutdown, no cleaners should be present.",0,ConnectionPool.getPoolCleaners().size() );
        assertNull("Pool timer should be null after shutdown", ConnectionPool.getPoolTimer());
    }

    public void test2PoolCleaners() throws Exception {
        datasource.getPoolProperties().setTimeBetweenEvictionRunsMillis(2000);
        datasource.getPoolProperties().setTestWhileIdle(true);
        
        DataSource ds2 = new DataSource(datasource.getPoolProperties());
        
        assertEquals("Pool cleaner should not be started yet.",0,ConnectionPool.getPoolCleaners().size() );
        assertNull("Pool timer should be null", ConnectionPool.getPoolTimer());
        
        datasource.getConnection().close();
        ds2.getConnection().close();
        assertEquals("Pool cleaner should have 2 cleaner.",2,ConnectionPool.getPoolCleaners().size() );
        assertNotNull("Pool timer should not be null", ConnectionPool.getPoolTimer());
        
        datasource.close();
        assertEquals("Pool cleaner should have 1 cleaner.",1,ConnectionPool.getPoolCleaners().size() );
        assertNotNull("Pool timer should not be null", ConnectionPool.getPoolTimer());

        ds2.close();
        assertEquals("Pool shutdown, no cleaners should be present.",0,ConnectionPool.getPoolCleaners().size() );
        assertNull("Pool timer should be null after shutdown", ConnectionPool.getPoolTimer());
    }
    
    public void testIdleTimeout() throws Exception {
        datasource.getPoolProperties().setTimeBetweenEvictionRunsMillis(2000);
        datasource.getPoolProperties().setTestWhileIdle(true);
        datasource.getPoolProperties().setMaxIdle(0);
        datasource.getPoolProperties().setInitialSize(0);
        datasource.getPoolProperties().setMinIdle(0);
        datasource.getPoolProperties().setMinEvictableIdleTimeMillis(1000);
        datasource.getConnection().close();
        assertEquals("Pool should have 1 idle.", 1, datasource.getIdle());
        Thread.sleep(3000);
        assertEquals("Pool should have 0 idle.", 0, datasource.getIdle());
        
        
    }
    
    
}
