/*
 */
package org.apache.tomcat.lite.io;

import junit.framework.TestCase;


public class UEncoderTest extends TestCase {
    IOWriter enc=new IOWriter(null);
    UrlEncoding dec = new UrlEncoding();
    CBuffer cc = CBuffer.newInstance();

    /*
     *
     * Test method for 'org.apache.tomcat.util.buf.UEncoder.encodeURL(String)'
     * TODO: find the relevant rfc and apache tests and add more
     */
    public void testEncodeURL() {

        String eurl1=encodeURL("test");
        assertEquals("test", eurl1);

        eurl1=encodeURL("/test");
        assertEquals("/test", eurl1);

        // safe ranges
        eurl1=encodeURL("test$-_.");
        assertEquals("test$-_.", eurl1);

        eurl1=encodeURL("test$-_.!*'(),");
        assertEquals("test$-_.!*'(),", eurl1);

        eurl1=encodeURL("//test");
        assertEquals("//test", eurl1);
    }

    public String encodeURL(String uri) {
        cc.recycle();
        dec.urlEncode(uri, cc, enc);
        return cc.toString();
    }


}
