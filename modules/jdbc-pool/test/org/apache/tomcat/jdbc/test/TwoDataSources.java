package org.apache.tomcat.jdbc.test;

import java.sql.Connection;

import org.apache.tomcat.jdbc.pool.DataSourceProxy;

public class TwoDataSources extends DefaultTestCase {

    public TwoDataSources(String name) {
        super(name);
    }
    
    public void testTwoDataSources() throws Exception {
        DataSourceProxy d1 = this.createDefaultDataSource();
        DataSourceProxy d2 = this.createDefaultDataSource();
        d1.setRemoveAbandoned(true);
        d1.setRemoveAbandonedTimeout(10);
        d1.setTimeBetweenEvictionRunsMillis(1000);
        d2.setRemoveAbandoned(false);
        Connection c1 = d1.getConnection();
        Connection c2 = d2.getConnection();
        Thread.sleep(15000);
        try {
            c1.createStatement();
            this.assertTrue("Connection should have been abandoned.",false);
        }catch (Exception x) {
            this.assertTrue("This is correct, c1 is abandoned",true);
        }

        try {
            c2.createStatement();
            this.assertTrue("Connection should not have been abandoned.",true);
        }catch (Exception x) {
            this.assertTrue("Connection c2 should be working",false);
        }
        try {
            this.assertTrue("Connection should have been closed.",c1.isClosed());
        }catch (Exception x) {
            this.assertTrue("This is correct, c1 is closed",true);
        }
        try {
            this.assertFalse("Connection c2 should not have been closed.",c2.isClosed());
        }catch (Exception x) {
            this.assertTrue("Connection c2 should be working",false);
        }
        

        
    }

}
