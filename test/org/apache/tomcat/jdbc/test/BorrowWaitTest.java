package org.apache.tomcat.jdbc.test;

import java.sql.Connection;
import java.sql.SQLException;

public class BorrowWaitTest extends DefaultTestCase {

    public BorrowWaitTest(String name) {
        super(name);
    }
    
    public void testWaitTime() throws Exception {
        int wait = 10000;
        this.init();
        this.datasource.setMaxActive(1);
        this.datasource.setMaxWait(wait);
        Connection con = datasource.getConnection();
        long start = System.currentTimeMillis();
        try {
            Connection con2 = datasource.getConnection();
            assertFalse("This should not happen, connection should be unavailable.",true);
        }catch (SQLException x) {
            long delta = System.currentTimeMillis();
            boolean inrange = Math.abs(wait-delta) < 1000;
            assertTrue("Connection should have been acquired within +/- 1 second.",true);
        }
        con.close();
    }
    
    public void testWaitTimeInfinite() throws Exception {
        if(true){
            System.err.println("testWaitTimeInfinite() test is disabled.");
            return;//this would lock up the test suite
        }
        int wait = -1;
        this.init();
        this.datasource.setMaxActive(1);
        this.datasource.setMaxWait(wait);
        Connection con = datasource.getConnection();
        long start = System.currentTimeMillis();
        try {
            Connection con2 = datasource.getConnection();
            assertFalse("This should not happen, connection should be unavailable.",true);
        }catch (SQLException x) {
            long delta = System.currentTimeMillis();
            boolean inrange = Math.abs(wait-delta) < 1000;
            assertTrue("Connection should have been acquired within +/- 1 second.",true);
        }
        con.close();
    }
    
    
    
    
}
