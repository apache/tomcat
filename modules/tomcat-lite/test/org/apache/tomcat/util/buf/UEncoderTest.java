/*
 */
package org.apache.tomcat.util.buf;

import junit.framework.TestCase;

public class UEncoderTest extends TestCase {
    UEncoder enc=new UEncoder();
    
    /*
     * 
     * Test method for 'org.apache.tomcat.util.buf.UEncoder.encodeURL(String)'
     * TODO: find the relevant rfc and apache tests and add more 
     */
    public void testEncodeURL() {

        String eurl1=enc.encodeURL("test");
        assertEquals("test", eurl1);
        
        eurl1=enc.encodeURL("/test");
        assertEquals("%2ftest", eurl1);

        // safe ranges
        eurl1=enc.encodeURL("test$-_.");
        assertEquals("test$-_.", eurl1);

        eurl1=enc.encodeURL("test$-_.!*'(),");
        assertEquals("test$-_.!*'(),", eurl1);

        eurl1=enc.encodeURL("//test");
        assertEquals("%2f%2ftest", eurl1);

        
    }

}
