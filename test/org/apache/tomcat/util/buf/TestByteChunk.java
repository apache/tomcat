/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.tomcat.util.buf;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import org.apache.tomcat.util.buf.ByteChunk.ByteOutputChannel;

/**
 * Test cases for {@link ByteChunk}.
 */
public class TestByteChunk {

    @Test
    public void testConvertToBytes() throws UnsupportedEncodingException {
        String string = "HTTP/1.1 100 \r\n\r\n";
        byte[] bytes = ByteChunk.convertToBytes(string);
        byte[] expected = string.getBytes("ISO-8859-1");
        Assert.assertTrue(Arrays.equals(bytes, expected));
    }


    /*
     * Test for {@code findByte} vs. {@code indexOf} methods difference.
     *
     * <p>
     * As discussed in the "Re: r944918" thread on dev@, {@code
     * ByteChunk.indexOf()} works for 0-127 ASCII chars only, and cannot find
     * any chars outside of the range. {@code ByteChunk.findByte()} works for
     * any ISO-8859-1 chars.
     */
    @Test
    public void testFindByte() throws UnsupportedEncodingException {
        // 0xa0 = 160 = &nbsp; character
        byte[] bytes = "Hello\u00a0world".getBytes("ISO-8859-1");
        final int len = bytes.length;

        // indexOf() does not work outside of 0-127
        Assert.assertEquals(5, ByteChunk.findByte(bytes, 0, len, (byte) '\u00a0'));
        Assert.assertEquals(-1, ByteChunk.indexOf(bytes, 0, len, '\u00a0'));

        Assert.assertEquals(0, ByteChunk.findByte(bytes, 0, len, (byte) 'H'));
        Assert.assertEquals(0, ByteChunk.indexOf(bytes, 0, len, 'H'));

        Assert.assertEquals(len - 1, ByteChunk.findByte(bytes, 0, len, (byte) 'd'));
        Assert.assertEquals(len - 1, ByteChunk.indexOf(bytes, 0, len, 'd'));

        Assert.assertEquals(-1, ByteChunk.findByte(bytes, 0, len, (byte) 'x'));
        Assert.assertEquals(-1, ByteChunk.indexOf(bytes, 0, len, 'x'));

        Assert.assertEquals(7, ByteChunk.findByte(bytes, 5, len, (byte) 'o'));
        Assert.assertEquals(7, ByteChunk.indexOf(bytes, 5, len, 'o'));

        Assert.assertEquals(-1, ByteChunk.findByte(bytes, 2, 5, (byte) 'w'));
        Assert.assertEquals(-1, ByteChunk.indexOf(bytes, 5, 5, 'w'));
    }


    @Test
    public void testIndexOf_Char() throws UnsupportedEncodingException {
        byte[] bytes = "Hello\u00a0world".getBytes("ISO-8859-1");
        final int len = bytes.length;

        ByteChunk bc = new ByteChunk();
        bc.setBytes(bytes, 0, len);

        Assert.assertEquals(0, bc.indexOf('H', 0));
        Assert.assertEquals(6, bc.indexOf('w', 0));

        // Does not work outside of 0-127
        Assert.assertEquals(-1, bc.indexOf('\u00a0', 0));

        bc.setBytes(bytes, 6, 5);
        Assert.assertEquals(1, bc.indexOf('o', 0));

        bc.setBytes(bytes, 6, 2);
        Assert.assertEquals(0, bc.indexOf('w', 0));
        Assert.assertEquals(-1, bc.indexOf('d', 0));
    }


    @Test
    public void testIndexOf_String() throws UnsupportedEncodingException {
        byte[] bytes = "Hello\u00a0world".getBytes("ISO-8859-1");
        final int len = bytes.length;

        ByteChunk bc = new ByteChunk();
        bc.setBytes(bytes, 0, len);

        Assert.assertEquals(0, bc.indexOf("Hello", 0, "Hello".length(), 0));
        Assert.assertEquals(2, bc.indexOf("ll", 0, 2, 0));
        Assert.assertEquals(2, bc.indexOf("Hello", 2, 2, 0));

        Assert.assertEquals(7, bc.indexOf("o", 0, 1, 5));

        // Does not work outside of 0-127
        Assert.assertEquals(-1, bc.indexOf("\u00a0", 0, 1, 0));

        bc.setBytes(bytes, 6, 5);
        Assert.assertEquals(1, bc.indexOf("o", 0, 1, 0));

        bc.setBytes(bytes, 6, 2);
        Assert.assertEquals(0, bc.indexOf("wo", 0, 1, 0));
        Assert.assertEquals(-1, bc.indexOf("d", 0, 1, 0));
    }


    @Test
    public void testFindBytes() throws UnsupportedEncodingException {
        byte[] bytes = "Hello\u00a0world".getBytes("ISO-8859-1");
        final int len = bytes.length;

        Assert.assertEquals(0, ByteChunk.findBytes(bytes, 0, len, new byte[] { 'H' }));
        Assert.assertEquals(5, ByteChunk.findBytes(bytes, 0, len, new byte[] {
                (byte) '\u00a0', 'x' }));
        Assert.assertEquals(5, ByteChunk.findBytes(bytes, 0, len - 4, new byte[] {
                'x', (byte) '\u00a0' }));
        Assert.assertEquals(len - 1, ByteChunk.findBytes(bytes, 2, len, new byte[] {
                'x', 'd' }));
        Assert.assertEquals(1, ByteChunk.findBytes(bytes, 0, len, new byte[] { 'o',
                'e' }));
        Assert.assertEquals(-1, ByteChunk.findBytes(bytes, 2, 5, new byte[] { 'w' }));
    }


    @Test
    public void testSerialization() throws Exception {
        String data = "Hello world!";
        byte[] bytes = data.getBytes(StandardCharsets.UTF_8);

        ByteChunk bcIn = new ByteChunk();
        bcIn.setBytes(bytes, 0, bytes.length);
        bcIn.setCharset(StandardCharsets.UTF_8);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(bcIn);
        oos.close();

        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        ObjectInputStream ois = new ObjectInputStream(bais);
        ByteChunk bcOut = (ByteChunk) ois.readObject();

        Assert.assertArrayEquals(bytes, bcOut.getBytes());
        Assert.assertEquals(bcIn.getCharset(), bcOut.getCharset());
    }


    @Ignore // Requires a 6GB heap (on markt's desktop - YMMV)
    @Test
    public void testAppend() throws Exception {
        ByteChunk bc = new ByteChunk();
        bc.setByteOutputChannel(new Sink());
        // Defaults to no limit

        byte data[] = new byte[32 * 1024 * 1024];

        for (int i = 0; i < 100; i++) {
            bc.append(data, 0, data.length);
        }

        Assert.assertEquals(AbstractChunk.ARRAY_MAX_SIZE, bc.getBuffer().length);
    }


    public static class Sink implements ByteOutputChannel {

        @Override
        public void realWriteBytes(byte[] cbuf, int off, int len) throws IOException {
            // NO-OP
        }

        @Override
        public void realWriteBytes(ByteBuffer from) throws IOException {
            // NO-OP
        }
    }


    @Test
    public void testToString() {
        ByteChunk bc = new ByteChunk();
        Assert.assertNull(bc.toString());
        byte[] data = new byte[8];
        bc.setBytes(data, 0, data.length);
        Assert.assertNotNull(bc.toString());
        bc.recycle();
        // toString() should behave consistently for new ByteChunk and
        // immediately after a call to recycle().
        Assert.assertNull(bc.toString());
    }
}
