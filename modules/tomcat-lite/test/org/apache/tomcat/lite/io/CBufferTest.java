/*
 */
package org.apache.tomcat.lite.io;

import junit.framework.TestCase;

public class CBufferTest extends TestCase {

    CBuffer ext = CBuffer.newInstance();

    public void extTest(String path, String exp) {
        CBuffer.newInstance().append(path).getExtension(ext, '/', '.');
        assertEquals(exp, ext.toString());
    }

    public void testExt() {
        extTest("foo.jsp", "jsp");
        extTest("foo.j", "j");
        extTest("/foo.j", "j");
        extTest("//foo.j", "j");
        extTest(".j", "j");
        extTest(".", "");
        extTest("/abc", "");
        extTest("/abc.", "");
        extTest("/abc/", "");
        extTest("/abc/d", "");
    }

    public void testLastIndexOf() {

    }
}
