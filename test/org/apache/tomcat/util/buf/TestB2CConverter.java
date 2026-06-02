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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.connector.InputBuffer;

public class TestB2CConverter {

    private static final byte[] UTF16_MESSAGE =
            new byte[] {-2, -1, 0, 65, 0, 66, 0, 67};

    private static final byte[] UTF8_INVALID = new byte[] {-8, -69, -73, -77};

    private static final byte[] UTF8_PARTIAL = new byte[] {-50};

    @Test
    public void testSingleMessage() throws Exception {
        testMessages(1);
    }

    @Test
    public void testTwoMessage() throws Exception {
        testMessages(2);
    }

    @Test
    public void testManyMessage() throws Exception {
        testMessages(10);
    }

    private void testMessages(int msgCount) throws Exception {
        B2CConverter conv = new B2CConverter(StandardCharsets.UTF_16);

        ByteChunk bc = new ByteChunk();
        CharChunk cc = new CharChunk(32);


        for (int i = 0; i < msgCount; i++) {
            bc.append(UTF16_MESSAGE, 0, UTF16_MESSAGE.length);
            conv.convert(bc, cc, true);
            Assert.assertEquals("ABC", cc.toString());
            bc.recycle();
            cc.recycle();
            conv.recycle();
        }

        System.out.println(cc);
    }

    @Test
    public void testLeftoverSize() {
        float maxLeftover = 0;
        String charsetName = "UNSET";
        for (Charset charset : Charset.availableCharsets().values()) {
            float leftover;
            if (charset.name().toLowerCase(Locale.ENGLISH).startsWith("x-")) {
                // Non-standard charset that browsers won't be using
                // Likely something used internally by the JRE
                continue;
            }
            try {
                leftover = charset.newEncoder().maxBytesPerChar();
            } catch (UnsupportedOperationException uoe) {
                // Skip it
                continue;
            }
            if (leftover > maxLeftover) {
                maxLeftover = leftover;
                charsetName = charset.name();
            }
        }
        Assert.assertTrue("Limit needs to be at least " + maxLeftover +
                " (used in charset '" + charsetName + "')",
                maxLeftover <= B2CConverter.LEFTOVER_SIZE);
    }

    @Test(expected=MalformedInputException.class)
    public void testBug54602a() throws Exception {
        // Check invalid input is rejected straight away
        B2CConverter conv = new B2CConverter(StandardCharsets.UTF_8);
        ByteChunk bc = new ByteChunk();
        CharChunk cc = new CharChunk();

        bc.append(UTF8_INVALID, 0, UTF8_INVALID.length);
        cc.allocate(bc.getLength(), -1);

        conv.convert(bc, cc, false);
    }

    @Test(expected=MalformedInputException.class)
    public void testBug54602b() throws Exception {
        // Check partial input is rejected
        B2CConverter conv = new B2CConverter(StandardCharsets.UTF_8);
        ByteChunk bc = new ByteChunk();
        CharChunk cc = new CharChunk();

        bc.append(UTF8_PARTIAL, 0, UTF8_PARTIAL.length);
        cc.allocate(bc.getLength(), -1);

        conv.convert(bc, cc, true);
    }

    @Test
    public void testBug54602c() throws Exception {
        // Check partial input is rejected once it is known to be all available
        B2CConverter conv = new B2CConverter(StandardCharsets.UTF_8);
        ByteChunk bc = new ByteChunk();
        CharChunk cc = new CharChunk();

        bc.append(UTF8_PARTIAL, 0, UTF8_PARTIAL.length);
        cc.allocate(bc.getLength(), -1);

        conv.convert(bc, cc, false);

        Exception e = null;
        try {
            conv.convert(bc, cc, true);
        } catch (MalformedInputException mie) {
            e = mie;
        }
        Assert.assertNotNull(e);
    }


    @Test
    public void testLeftoverChunk() throws Exception {
        // E2 8C A8 is the "keyboard" character
        B2CConverter conv = new B2CConverter(StandardCharsets.UTF_8);
        ByteChunk bc = new ByteChunk();
        CharChunk cc = new CharChunk();
        cc.allocate(1, -1);

        bc.append((byte) 0x41);

        bc.append((byte) 0xE2);
        conv.convert(bc, cc, false);
        Assert.assertEquals(1, cc.getLength());

        bc.append((byte) 0x8C);
        conv.convert(bc, cc, false);
        Assert.assertEquals(1, cc.getLength());

        bc.append((byte) 0xA8);
        conv.convert(bc, cc, true);
        // Expect overflow so while all 3 bytes are present, they aren't converted
        Assert.assertEquals(1, cc.getLength());

        cc.setLimit(2);
        cc.makeSpace(2);
        conv.convert(bc, cc, true);
        Assert.assertEquals("A\u2328", cc.toString());
    }


    @Test
    public void testLeftoverBuffer() throws Exception {
        // E2 8C A8 is the "keyboard" character
        B2CConverter conv = new B2CConverter(StandardCharsets.UTF_8);
        ByteBuffer bb = ByteBuffer.allocate(8);
        CharBuffer cb = newCharBuffer(1);
        TesterInputBuffer ib = new TesterInputBuffer(bb);

        bb.put((byte) 0x41);
        bb.flip();

        conv.convert(bb, cb, ib, false);
        assertCharBufferEquals("A", cb);

        // Will trigger overflow if there is a complete character
        bb.clear();
        bb.put((byte) 0xE2);
        bb.flip();
        conv.convert(bb, cb, ib, false);
        assertCharBufferEquals("A", cb);

        // NO-OP (underflow)
        conv.convert(bb, cb, ib, false);
        assertCharBufferEquals("A", cb);

        // Adds second byte of an incomplete 3 byte character to leftovers
        ib.setNextRealBytes(new byte[] { (byte) 0x8C });
        conv.convert(bb, cb, ib, false);
        assertCharBufferEquals("A", cb);

        // Adds final byte of 3 byte character to leftovers. Not converted as output will overflow
        ib.setNextRealBytes(new byte[] { (byte) 0xA8 });
        conv.convert(bb, cb, ib, false);
        assertCharBufferEquals("A", cb);

        cb = newCharBuffer(2);
        cb.limit(cb.capacity());
        cb.put('A');
        cb.limit(1);
        conv.convert(bb, cb, ib, false);
        assertCharBufferEquals("A\u2328", cb);
    }


    @Test
    public void testLeftoverChunkWithTrailingBytes() throws Exception {
        B2CConverter conv = new B2CConverter(StandardCharsets.UTF_8);
        ByteChunk bc = new ByteChunk();
        CharChunk cc = new CharChunk();
        cc.allocate(4, -1);

        bc.append((byte) 0x41);
        bc.append((byte) 0xE2);
        conv.convert(bc, cc, false);

        bc.append((byte) 0x8C);
        conv.convert(bc, cc, false);

        bc.append((byte) 0xA8);
        bc.append((byte) 0x42);
        conv.convert(bc, cc, true);

        Assert.assertEquals("A\u2328B", cc.toString());
    }


    @Test
    public void testLeftoverChunkMalformed() throws Exception {
        B2CConverter conv = new B2CConverter(StandardCharsets.UTF_8, true);
        ByteChunk bc = new ByteChunk();
        CharChunk cc = new CharChunk();
        cc.allocate(4, -1);

        bc.append((byte) 0x41);
        bc.append((byte) 0xE2);
        conv.convert(bc, cc, false);

        bc.append((byte) 0x42);
        conv.convert(bc, cc, true);

        Assert.assertEquals("A\ufffdB", cc.toString());
    }


    @Test
    public void testLeftoverChunkMalformedAtEnd() throws Exception {
        B2CConverter conv = new B2CConverter(StandardCharsets.UTF_8, true);
        ByteChunk bc = new ByteChunk();
        CharChunk cc = new CharChunk();
        cc.allocate(4, -1);

        bc.append((byte) 0x41);
        bc.append((byte) 0xE2);
        conv.convert(bc, cc, true);

        Assert.assertEquals("A\ufffd", cc.toString());
    }


    @Test
    public void testBug54602d() throws Exception {
        B2CConverter conv = new B2CConverter(StandardCharsets.UTF_8);
        ByteBuffer bb = ByteBuffer.allocate(4);
        CharBuffer cb = newCharBuffer(4);
        TesterInputBuffer ib = new TesterInputBuffer(bb);

        bb.put(UTF8_PARTIAL);
        bb.flip();
        conv.convert(bb, cb, ib, false);
        assertCharBufferEquals("", cb);

        conv.convert(bb, cb, ib, false);
        assertCharBufferEquals("", cb);

        Exception e = null;
        try {
            conv.convert(bb, cb, ib, true);
        } catch (MalformedInputException mie) {
            e = mie;
        }
        Assert.assertNotNull(e);
    }


    @Test(timeout = 1000)
    public void testLeftoverBufferWithBufferedContinuationBytes() throws Exception {
        B2CConverter conv = new B2CConverter(StandardCharsets.UTF_8);
        ByteBuffer bb = ByteBuffer.wrap(new byte[] { (byte) 0xE2 });
        CharBuffer cb = newCharBuffer(4);
        TesterInputBuffer ib = new TesterInputBuffer(bb);

        conv.convert(bb, cb, ib, false);

        bb = ByteBuffer.wrap(new byte[] { (byte) 0x8C, (byte) 0xA8, (byte) 0x42 });
        ib.setByteBuffer(bb);
        conv.convert(bb, cb, ib, true);

        assertCharBufferEquals("\u2328B", cb);
    }


    @Test
    public void testLeftoverBufferRefillWithTrailingBytes() throws Exception {
        B2CConverter conv = new B2CConverter(StandardCharsets.UTF_8);
        ByteBuffer bb = ByteBuffer.allocate(8);
        CharBuffer cb = newCharBuffer(4);
        TesterInputBuffer ib = new TesterInputBuffer(bb);

        bb.put((byte) 0xE2);
        bb.flip();
        conv.convert(bb, cb, ib, false);

        ib.setNextRealBytes(new byte[] { (byte) 0x8C, (byte) 0xA8, (byte) 0x42 });
        conv.convert(bb, cb, ib, true);

        assertCharBufferEquals("\u2328B", cb);
    }


    @Test
    public void testLeftoverBufferRefillWithReplacementBuffer() throws Exception {
        B2CConverter conv = new B2CConverter(StandardCharsets.UTF_8);
        ByteBuffer bb = ByteBuffer.allocate(8);
        CharBuffer cb = newCharBuffer(4);
        TesterInputBuffer ib = new TesterInputBuffer(bb);

        bb.put((byte) 0xE2);
        bb.flip();
        conv.convert(bb, cb, ib, false);

        ib.replaceNextByteBuffer(new byte[] { (byte) 0x8C, (byte) 0xA8, (byte) 0x42 });
        conv.convert(bb, cb, ib, true);

        assertCharBufferEquals("\u2328B", cb);
    }


    private static class TesterInputBuffer extends InputBuffer {

        private byte[] nextRealBytes = null;
        private boolean replaceByteBuffer = false;

        TesterInputBuffer(ByteBuffer byteBuffer) {
            super();
            setByteBuffer(byteBuffer);
        }

        @Override
        public int realReadBytes() throws IOException {
            if (nextRealBytes == null) {
                return -1;
            } else {
                ByteBuffer byteBuffer;
                if (replaceByteBuffer) {
                    byteBuffer = ByteBuffer.allocate(Math.max(getByteBuffer().capacity(), nextRealBytes.length));
                    setByteBuffer(byteBuffer);
                } else {
                    byteBuffer = getByteBuffer();
                }
                byteBuffer.clear();
                byteBuffer.put(nextRealBytes);
                byteBuffer.flip();
                int result = nextRealBytes.length;
                nextRealBytes = null;
                replaceByteBuffer = false;
                return result;
            }
        }

        public void setNextRealBytes(byte[] nextRealBytes) {
            this.nextRealBytes = nextRealBytes;
        }

        public void replaceNextByteBuffer(byte[] nextRealBytes) {
            this.nextRealBytes = nextRealBytes;
            replaceByteBuffer = true;
        }
    }


    private static CharBuffer newCharBuffer(int capacity) {
        CharBuffer charBuffer = CharBuffer.allocate(capacity);
        charBuffer.limit(0);
        return charBuffer;
    }


    private static void assertCharBufferEquals(String expected, CharBuffer actual) {
        CharBuffer actualCopy = actual.asReadOnlyBuffer();
        actualCopy.position(0);
        Assert.assertEquals(expected, actualCopy.toString());
    }
}
