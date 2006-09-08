package org.apache.catalina.tribes.test.io;

import org.apache.catalina.tribes.io.XByteBuffer;
import junit.framework.TestCase;

public class TestSerialization extends TestCase {
    protected void setUp() throws Exception {
        super.setUp();
    }
    
    public void testEmptyArray() throws Exception {
        
    }

    protected void tearDown() throws Exception {
        super.tearDown();
    }
    
    public static void main(String[] args) throws Exception {
        //XByteBuffer.deserialize(new byte[0]);
        XByteBuffer.deserialize(new byte[] {-84, -19, 0, 5, 115, 114, 0, 17, 106});
    }

}
