/*
 */
package org.apache.tomcat.lite.http;

import java.util.Random;

import junit.framework.TestCase;

import org.apache.tomcat.lite.io.IOBuffer;

public class CompressFilterTest extends TestCase {

    CompressFilter cf = new CompressFilter();

    private void check(String clear, String xtra) throws Exception {
        IOBuffer in = new IOBuffer();
        IOBuffer out = new IOBuffer();

        in.append(clear);
        in.close();

        cf.compress(in, out);

//        BBuffer bb = out.copyAll(null);
//        String hd = Hex.getHexDump(bb.array(), bb.position(),
//                bb.remaining(), true);
//        System.err.println(hd);

        if (xtra != null) {
            out.append(xtra);
        }
        in.recycle();
        out.close();
        cf.decompress(out, in);

        assertEquals(in.copyAll(null).toString(), clear);
        assertTrue(in.isAppendClosed());

        if (xtra != null) {
            assertEquals(out.copyAll(null).toString(), xtra);
        }
    }

    public void test1() throws Exception {
        check("X1Y2Z3", null);
    }

    public void testXtra() throws Exception {
        check("X1Y2Z3", "GET /");
    }

    public void testLarge() throws Exception {
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < 2 * 1024; i++) {
            sb.append("0123456789012345");
        }
        check(sb.toString(), null);
    }

    public void testLarge10() throws Exception {
        for (int i = 0; i < 10; i++) {
            testLargeIn();
            cf.recycle();
        }
    }

    public void testLargeIn() throws Exception {
        StringBuffer sb = new StringBuffer();
        Random r = new Random();
        for (int i = 0; i < 16 * 2 * 1024; i++) {
            sb.append(' ' + r.nextInt(32));
        }
        check(sb.toString(), null);
    }


    public void testSpdy() throws Exception {
        cf.setDictionary(SpdyConnection.SPDY_DICT, SpdyConnection.DICT_ID);
        check("connection: close\n", null);
    }

}
