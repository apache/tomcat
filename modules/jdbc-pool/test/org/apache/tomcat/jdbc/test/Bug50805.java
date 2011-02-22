package org.apache.tomcat.jdbc.test;

import java.sql.Connection;
import java.util.concurrent.Future;

public class Bug50805 extends DefaultTestCase {
    public Bug50805(String name) {
        super(name);
    }
    
    public void test50805() throws Exception {
        init();
        this.datasource.setInitialSize(0);
        this.datasource.setMaxActive(10);
        this.datasource.setMinIdle(1);
        
        assertEquals("Current size should be 0.", 0, this.datasource.getSize());
        
        this.datasource.getConnection().close();
        
        assertEquals("Current size should be 1.", 1, this.datasource.getSize());
        assertEquals("Idle size should be 1.", 1, this.datasource.getIdle());
        assertEquals("Busy size should be 0.", 0, this.datasource.getActive());
        
        Future<Connection> fc = this.datasource.getConnectionAsync();
        
        Connection con = fc.get();
        
        assertEquals("Current size should be 1.", 1, this.datasource.getSize());
        assertEquals("Idle size should be 0.", 0, this.datasource.getIdle());
        assertEquals("Busy size should be 1.", 1, this.datasource.getActive());
        
        con.close();
        assertEquals("Current size should be 1.", 1, this.datasource.getSize());
        assertEquals("Idle size should be 1.", 1, this.datasource.getIdle());
        assertEquals("Busy size should be 0.", 0, this.datasource.getActive());
    }
}
