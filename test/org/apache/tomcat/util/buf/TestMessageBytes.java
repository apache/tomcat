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

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.junit.Assert;
import org.junit.Test;

public class TestMessageBytes {

    @Test
    public void testToStringFromNull() {
        MessageBytes mb = MessageBytes.newInstance();
        mb.toString();
    }


    @Test
    public void testToBytesFromNull() {
        MessageBytes mb = MessageBytes.newInstance();
        mb.toBytes();
    }


    @Test
    public void testToCharsFromNull() {
        MessageBytes mb = MessageBytes.newInstance();
        mb.toChars();
    }


    @Test
    public void testToStringAfterRecycle() {
        MessageBytes mb = MessageBytes.newInstance();
        mb.setString("foo");
        mb.recycle();
        mb.toString();
    }


    @Test
    public void testToBytesAfterRecycle() {
        MessageBytes mb = MessageBytes.newInstance();
        mb.setString("foo");
        mb.recycle();
        mb.toBytes();
    }


    @Test
    public void testToCharsAfterRecycle() {
        MessageBytes mb = MessageBytes.newInstance();
        mb.setString("foo");
        mb.recycle();
        mb.toChars();
    }


    @Test
    public void testEqualsNullNull() {
        MessageBytes mb1 = MessageBytes.newInstance();
        MessageBytes mb2 = MessageBytes.newInstance();

        Assert.assertTrue(mb1.equals(mb2));
    }


    @Test
    public void testSetLong() {
        doTestSetLong(0);
        doTestSetLong(-1);
        doTestSetLong(1);
        doTestSetLong(-12345);
        doTestSetLong(12345);
        doTestSetLong(Long.MIN_VALUE);
        doTestSetLong(Long.MAX_VALUE);
    }


    private void doTestSetLong(long l) {
        MessageBytes mb = MessageBytes.newInstance();
        mb.setLong(l);
        Assert.assertEquals(mb.toStringType(), Long.toString(l));
    }


    private static final Charset[] CHARSETS = { StandardCharsets.UTF_8, StandardCharsets.ISO_8859_1,
            StandardCharsets.US_ASCII, StandardCharsets.UTF_16BE, Charset.forName("windows-1252") };


    private static byte[] bytesOf(MessageBytes mb) {
        ByteChunk bc = mb.getByteChunk();
        byte[] result = new byte[bc.getLength()];
        System.arraycopy(bc.getBuffer(), bc.getStart(), result, 0, result.length);
        return result;
    }


    @Test
    public void testToBytesMatchesCharsetEncoding() {
        String[] values = { "", "a", "/context/path?name=value", "caf\u00E9", "\u20AC", "\u00E9\u20AC\u4E2D" };
        for (Charset charset : CHARSETS) {
            for (String value : values) {
                if (!charset.newEncoder().canEncode(value)) {
                    continue;
                }
                MessageBytes mb = MessageBytes.newInstance();
                mb.setCharset(charset);
                mb.setString(value);
                mb.toBytes();
                Assert.assertArrayEquals(charset + " string '" + value + "'", value.getBytes(charset), bytesOf(mb));

                char[] chars = ("xx" + value + "yy").toCharArray();
                mb = MessageBytes.newInstance();
                mb.setCharset(charset);
                mb.setChars(chars, 2, value.length());
                mb.toBytes();
                Assert.assertArrayEquals(charset + " chars '" + value + "'", value.getBytes(charset), bytesOf(mb));
            }
        }
    }


    @Test
    public void testToBytesUnmappableCharacter() {
        for (Charset charset : new Charset[] { StandardCharsets.US_ASCII, StandardCharsets.ISO_8859_1 }) {
            MessageBytes mb = MessageBytes.newInstance();
            mb.setCharset(charset);
            mb.setString("abc\u4E2D");
            try {
                mb.toBytes();
                Assert.fail(charset.toString());
            } catch (IllegalArgumentException expected) {
                // Expected
            }
        }
    }
}
