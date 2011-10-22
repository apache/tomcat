/*
 */
package org.apache.tomcat.lite.io;

import org.apache.tomcat.lite.io.BBuffer;

import junit.framework.TestCase;

public class BBufferTest extends TestCase {
    BBuffer res = BBuffer.wrapper("");

    BBuffer l1 = BBuffer.wrapper("");
    BBuffer l1a = BBuffer.wrapper("a");

    BBuffer l2 = BBuffer.wrapper("\r");
    BBuffer l3 = BBuffer.wrapper("\n");
    BBuffer l4 = BBuffer.wrapper("\r\n");
    BBuffer l5 = BBuffer.wrapper("\r\na");
    BBuffer l5_a = BBuffer.wrapper("\ra");
    BBuffer l5_b = BBuffer.wrapper("\na");
    BBuffer l6 = BBuffer.wrapper("a\n");
    BBuffer l7 = BBuffer.wrapper("GET \n");
    BBuffer l8 = BBuffer.wrapper("GET /\n");
    BBuffer l9 = BBuffer.wrapper("GET /a?b\n");
    BBuffer l10 = BBuffer.wrapper("GET /a?b HTTP/1.0\n");
    BBuffer l11 = BBuffer.wrapper("GET /a?b HTTP/1.0\na:b");
    BBuffer l12 = BBuffer.wrapper("GET /a?b HTTP/1.0\na:b\n");

    BBuffer f1 = BBuffer.wrapper("GET /a?b HTTP/1.0\na:b\n\n");
    BBuffer f2 = BBuffer.wrapper("GET /a?b HTTP/1.0\na:b\r\n\r\n");
    BBuffer f3 = BBuffer.wrapper("GET /a?b HTTP/1.0\na:b\r\r");
    BBuffer f4 = BBuffer.wrapper("GET /a?b HTTP/1.0\na:b\r\n\r");

    BBuffer s1 = BBuffer.wrapper(" \n");
    BBuffer s2 = BBuffer.wrapper(" a");
    BBuffer s3 = BBuffer.wrapper("  ");
    BBuffer s4 = BBuffer.wrapper("   a");

    BBuffer h1 = BBuffer.wrapper("a");
    BBuffer h2 = BBuffer.wrapper("a?b");
    BBuffer h3 = BBuffer.wrapper("a b");

    public void hashTest(String s) {
        assertEquals(s.hashCode(), BBuffer.wrapper(s).hashCode());
    }

    public void testHash() {
        hashTest("");
        hashTest("a");
        hashTest("123abc");
        hashTest("123abc\0");
        // Fails for UTF chars - only ascii hashTest("123abc\u12345;");
    }

    public void testReadToSpace() {
        assertEquals(3, l8.readToSpace(res));
        assertEquals("GET", res.toString());
        assertEquals(" /\n", l8.toString());

        assertEquals(0, l1.readToSpace(res));
        assertEquals("", res.toString());
        assertEquals("", l1.toString());
    }

    public void testReadToDelim() {
        assertEquals(1, h1.readToDelimOrSpace((byte)'?', res));
        assertEquals("a", res.toString());
        assertEquals("", h1.toString());

        assertEquals(1, h2.readToDelimOrSpace((byte)'?', res));
        assertEquals("a", res.toString());
        assertEquals("?b", h2.toString());

        assertEquals(1, h3.readToDelimOrSpace((byte)'?', res));
        assertEquals("a", res.toString());
        assertEquals(" b", h3.toString());
    }

    public void testGet() {
        assertEquals(0x20, s1.get(0));
        assertEquals(0x0a, s1.get(1));
        try {
            s1.get(2);
        } catch(ArrayIndexOutOfBoundsException ex) {
            return;
        }
        fail("Exception");
    }

    public void testSkipSpace() {
        assertEquals(1, s1.skipSpace());
        assertEquals("\n", s1.toString());

        assertEquals(1, s2.skipSpace());
        assertEquals("a", s2.toString());

        assertEquals(2, s3.skipSpace());
        assertEquals("", s3.toString());

        assertEquals(3, s4.skipSpace());
        assertEquals("a", s4.toString());

        assertEquals(0, l1.skipSpace());
        assertEquals("", l1.toString());
    }

    public void testLFLF() {
        assertTrue(f1.hasLFLF());
        assertTrue(f2.hasLFLF());
        assertTrue(f3.hasLFLF());

        assertFalse(f4.hasLFLF());
        assertFalse(l1.hasLFLF());
        assertFalse(l2.hasLFLF());
        assertFalse(l3.hasLFLF());

        assertFalse(l10.hasLFLF());
        assertFalse(l11.hasLFLF());
        assertFalse(l12.hasLFLF());
    }

    public void testReadLine() {
        assertEquals(-1, l1.readLine(res));
        assertEquals("", res.toString());
        assertEquals("", l1.toString());

        assertEquals(-1, l1a.readLine(res));
        assertEquals("", res.toString());
        assertEquals("a", l1a.toString());

        assertEquals(0, l2.readLine(res));
        assertEquals("", l2.toString());
        assertEquals("", res.toString());
        assertEquals(0, l3.readLine(res));
        assertEquals("", l3.toString());
        assertEquals("", res.toString());
        assertEquals(0, l4.readLine(res));
        assertEquals("", res.toString());

        assertEquals(0, l5.readLine(res));
        assertEquals("", res.toString());
        assertEquals("a", l5.toString());
        assertEquals(0, l5_b.readLine(res));
        assertEquals("", res.toString());
        assertEquals("a", l5_b.toString());
        assertEquals(0, l5_a.readLine(res));
        assertEquals("", res.toString());
        assertEquals("a", l5_a.toString());

        assertEquals(1, l6.readLine(res));
        assertEquals("a", res.toString());

        assertEquals(4, l7.readLine(res));
        assertEquals("GET ", res.toString());
        assertEquals(5, l8.readLine(res));
        assertEquals("GET /", res.toString());

    }
}
