/*
 */
package org.apache.tomcat.lite.servlet;

import java.io.IOException;
import java.util.Properties;

import junit.framework.TestCase;

import org.apache.tomcat.integration.simple.SimpleObjectManager;


public class PropertiesSpiTest extends TestCase {

    SimpleObjectManager spi;
    
    public void setUp() {
        spi = new SimpleObjectManager();
        
        spi.getProperties().put("obj1.name", "foo");
        spi.getProperties().put("obj1.(class)", BoundObj.class.getName());
        
    }
    
    public void testArgs() throws IOException { 
        spi = new SimpleObjectManager(new String[] {
            "-a=1", "-b", "2"});
        Properties res = spi.getProperties();
        
        assertEquals("1", res.get("a"));
        assertEquals("2", res.get("b"));
        
        
    }
    
    public static class BoundObj {
        String name;
        
        public void setName(String n) {
            this.name = n;
        }
    }
    
    public void testBind() throws Exception {
        BoundObj bo = new BoundObj();
        spi.bind("obj1", bo);
        assertEquals(bo.name, "foo");        
    }
    
    public void testCreate() throws Exception {
        BoundObj bo = (BoundObj) spi.get("obj1");
        assertEquals(bo.name, "foo");
    }
}
