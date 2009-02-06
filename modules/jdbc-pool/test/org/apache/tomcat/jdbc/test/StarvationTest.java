package org.apache.tomcat.jdbc.test;

import java.util.concurrent.CountDownLatch;
import java.sql.Connection;
import java.sql.SQLException;

import org.apache.tomcat.jdbc.test.CheckOutThreadTest.TestThread;

/**
 * If a connection is abandoned and closed, 
 * then that should free up a spot in the pool, and other threads 
 * that are waiting should not time out and throw an error but be 
 * able to acquire a connection, since one was just released.
 * @author fhanik
 *
 */
public class StarvationTest extends DefaultTestCase {

    public StarvationTest(String name) {
        super(name);
    }
    
    private void config() {
        datasource.getPoolProperties().setMaxActive(1);
        datasource.getPoolProperties().setMaxIdle(1);
        datasource.getPoolProperties().setInitialSize(1);
        datasource.getPoolProperties().setRemoveAbandoned(true);
        datasource.getPoolProperties().setRemoveAbandonedTimeout(5);
        datasource.getPoolProperties().setTimeBetweenEvictionRunsMillis(500);
        datasource.getPoolProperties().setMaxWait(10000);
    }
    
    public void testDBCPConnectionStarvation() throws Exception {
        init();
        config();
        this.transferProperties();
        this.tDatasource.getConnection().close();
        javax.sql.DataSource datasource = this.tDatasource;
        Connection con1 = datasource.getConnection(); 
        Connection con2 = null;
        try {
            con2 = datasource.getConnection();
            try {
                con2.setCatalog("mysql");//make sure connection is valid
            }catch (SQLException x) {
                assertFalse("2nd Connection is not valid:"+x.getMessage(),true);
            }
            assertTrue("Connection 1 should be closed.",con1.isClosed()); //first connection should be closed
        }catch (Exception x) {
            assertFalse("Connection got starved:"+x.getMessage(),true);
        }finally {
            if (con2!=null) con2.close();
        }
    }
    
    public void testConnectionStarvation() throws Exception {
        init();
        config();
        Connection con1 = datasource.getConnection(); 
        Connection con2 = null;
        try {
            con2 = datasource.getConnection();
            try {
                con2.setCatalog("mysql");//make sure connection is valid
            }catch (SQLException x) {
                assertFalse("2nd Connection is not valid:"+x.getMessage(),true);
            }
            assertTrue("Connection 1 should be closed.",con1.isClosed()); //first connection should be closed
        }catch (Exception x) {
            assertFalse("Connection got starved:"+x.getMessage(),true);
        }finally {
            if (con2!=null) con2.close();
        }
    }

    public void testFairConnectionStarvation() throws Exception {
        init();
        config();
        datasource.getPoolProperties().setFairQueue(true);
        Connection con1 = datasource.getConnection(); 
        Connection con2 = null;
        try {
            con2 = datasource.getConnection();
            try {
                con2.setCatalog("mysql");//make sure connection is valid
            }catch (SQLException x) {
                assertFalse("2nd Connection is not valid:"+x.getMessage(),true);
            }
            assertTrue("Connection 1 should be closed.",con1.isClosed()); //first connection should be closed
        }catch (Exception x) {
            assertFalse("Connection got starved:"+x.getMessage(),true);
        }finally {
            if (con2!=null) con2.close();
        }
    }
}
