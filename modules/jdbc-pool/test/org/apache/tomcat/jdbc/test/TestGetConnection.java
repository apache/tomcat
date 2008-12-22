package org.apache.tomcat.jdbc.test;

import java.sql.Connection;

import javax.sql.PooledConnection;

public class TestGetConnection extends DefaultTestCase {

    public TestGetConnection(String name) {
        super(name);
    }
    
    public void testGetConnection() throws Exception {
        this.init();
        Connection con = this.datasource.getConnection();
        assertTrue("Connection should implement javax.sql.PooledConnection",con instanceof PooledConnection);
        Connection actual = ((PooledConnection)con).getConnection();
        assertNotNull("Connection delegate should not be null.",actual);
        System.out.println("Actual connection:"+actual.getClass().getName());
        
    }

}
