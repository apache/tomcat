/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tomcat.util.buf;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class TestUtf8 {

    // Invalid UTF-8
    private static final byte[] SRC_BYTES_1 =
            new byte[] {-50, -70, -31,  -67, -71, -49, -125, -50, -68, -50,
                        -75, -19, -96, -128, 101, 100,  105, 116, 101, 100};

    // Invalid code point (out of range)
    private static final byte[] SRC_BYTES_2 =
            new byte[] {-12, -112, -128, -128};

    // Various invalid UTF-8 sequences
    private static final byte[][] MALFORMED = {
            // One-byte sequences:
            {(byte)0xFF },
            {(byte)0xC0 },
            {(byte)0x80 },

            // Two-byte sequences:
            {(byte)0xC0, (byte)0x80}, // U+0000 zero-padded
            {(byte)0xC1, (byte)0xBF}, // U+007F zero-padded
            {(byte)0xFF, (byte)0xFF}, // all ones
            {(byte)0xE0, (byte)0x80}, // 111x first byte first nibble
            {(byte)0xA0, (byte)0x80}, // 101x first byte first nibble
            {(byte)0xC2, (byte)0x00}, // invalid second byte
            {(byte)0xC2, (byte)0xC0}, // invalid second byte

            // Three-byte sequences
            {(byte)0xE0, (byte)0x80, (byte)0x80 }, // U+0000 zero-padded
            {(byte)0xE0, (byte)0x81, (byte)0xBF }, // U+007F zero-padded
            {(byte)0xE0, (byte)0x9F, (byte)0xBF }, // U+07FF zero-padded
            {(byte)0xFF, (byte)0xFF, (byte)0xFF }, // all ones
            {(byte)0xF0, (byte)0x80, (byte)0x80 }, // invalid first byte
            {(byte)0xE0, (byte)0xC0, (byte)0x80 }, // invalid second byte
            {(byte)0xE0, (byte)0x80, (byte)0xC0 }, // invalid third byte

            // Four-byte sequences
            {(byte)0xF0, (byte)0x80, (byte)0x80, (byte)0x80 }, // U+0000 zero-padded
            {(byte)0xF0, (byte)0x80, (byte)0x81, (byte)0xBF }, // U+007F zero-padded
            {(byte)0xF0, (byte)0x80, (byte)0x9F, (byte)0xBF }, // U+007F zero-padded
            {(byte)0xF0, (byte)0x8F, (byte)0xBF, (byte)0xBF }, // U+07FF zero-padded

            {(byte)0xFF, (byte)0xFF, (byte)0xFF, (byte)0xFF }, // all ones
            {(byte)0xF8, (byte)0x80, (byte)0x80, (byte)0x80 }, // invalid first byte
            {(byte)0xF0, (byte)0xC0, (byte)0x80, (byte)0x80 }, // invalid second byte
            {(byte)0xF0, (byte)0x80, (byte)0xC0, (byte)0x80 }, // invalid third byte
            {(byte)0xF0, (byte)0x80, (byte)0x80, (byte)0xC0 }, // invalid fourth byte

            // Five-byte sequences
            {(byte)0xF8, (byte)0x80, (byte)0x80, (byte)0x80, (byte)0x80 }, // U+0000 zero-padded
            {(byte)0xF8, (byte)0x80, (byte)0x80, (byte)0x81, (byte)0xBF }, // U+007F zero-padded
            {(byte)0xF8, (byte)0x80, (byte)0x80, (byte)0x9F, (byte)0xBF }, // U+07FF zero-padded
            {(byte)0xF8, (byte)0x80, (byte)0x8F, (byte)0xBF, (byte)0xBF }, // U+FFFF zero-padded

            // Six-byte sequences
            {(byte)0xFC, (byte)0x80, (byte)0x80, (byte)0x80, (byte)0x80, (byte)0x80 }, // U+0000 zero-padded
            {(byte)0xFC, (byte)0x80, (byte)0x80, (byte)0x80, (byte)0x81, (byte)0xBF }, // U+007F zero-padded
            {(byte)0xFC, (byte)0x80, (byte)0x80, (byte)0x80, (byte)0x9F, (byte)0xBF }, // U+07FF zero-padded
            {(byte)0xFC, (byte)0x80, (byte)0x80, (byte)0x8F, (byte)0xBF, (byte)0xBF }, // U+FFFF zero-padded
        };

    @Test
    public void testJvmDecoder1() {
        // This should trigger an error but currently passes. Once the JVM is
        // fixed, s/false/true/ and s/20/13/
        doJvmDecoder(SRC_BYTES_1, false, false, 20);
    }


    @Test
    public void testJvmDecoder2() {
        // Ideally should fail after 2 bytes (i==1)
        doJvmDecoder(SRC_BYTES_2, false, true, 3);
    }


    private void doJvmDecoder(byte[] src, boolean endOfinput,
            boolean errorExpected, int failPosExpected) {
        CharsetDecoder decoder = B2CConverter.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);


        ByteBuffer bb = ByteBuffer.allocate(src.length);
        CharBuffer cb = CharBuffer.allocate(bb.limit());

        boolean error = false;
        int i = 0;
        for (; i < src.length; i++) {
            bb.put(src[i]);
            bb.flip();
            CoderResult cr = decoder.decode(bb, cb, endOfinput);
            if (cr.isError()) {
                error = true;
                break;
            }
            bb.compact();
        }

        StringBuilder ashex = new StringBuilder(src.length * 4);
        for (int j = 0; j < src.length; j++) {
            if (i > 0) ashex.append(' ');
            ashex.append(Integer.toBinaryString(src[j] & 0xff));
        }

        assertEquals(ashex.toString(),
                Boolean.valueOf(errorExpected), Boolean.valueOf(error));
        if (failPosExpected != -1) {
            assertEquals(failPosExpected, i);
        }
    }


    @Test
    public void testHarmonyDecoder1() {
        doHarmonyDecoder(SRC_BYTES_1, false, true, 13);
    }


    @Test
    public void testHarmonyDecoder2() {
        doHarmonyDecoder(SRC_BYTES_2, false, true, 1);
    }


    private void doHarmonyDecoder(byte[] src, boolean endOfinput,
            boolean errorExpected, int failPosExpected) {
        CharsetDecoder decoder = new Utf8Decoder();

        ByteBuffer bb = ByteBuffer.allocate(src.length);
        CharBuffer cb = CharBuffer.allocate(bb.limit());

        boolean error = false;
        int i = 0;
        for (; i < src.length; i++) {
            bb.put(src[i]);
            bb.flip();
            CoderResult cr = decoder.decode(bb, cb, endOfinput);
            if (cr.isError()) {
                error = true;
                break;
            }
            bb.compact();
        }

        StringBuilder ashex = new StringBuilder(src.length * 4);
        for (int j = 0; j < src.length; j++) {
            if (i > 0) ashex.append(' ');
            ashex.append(Integer.toBinaryString(src[j] & 0xff));
        }

        assertEquals(ashex.toString(),
                Boolean.valueOf(errorExpected), Boolean.valueOf(error));
        if (failPosExpected != -1) {
            assertEquals(failPosExpected, i);
        }
    }


    @Test
    public void testUtf8MalformedJvm() {
        for (int i = 0 ; i < MALFORMED.length; i++) {
            // Known failures
            // JVM UTF-8 decoder spots invalid sequences but not if they occur
            // at the end of the input and endOfInput is not true
            if (i == 1 || i == 6 || i == 14 | i == 22) {
                doJvmDecoder(MALFORMED[i], false, false, -1);
            } else {
                doJvmDecoder(MALFORMED[i], false, true, -1);
            }
        }
    }


    @Test
    public void testUtf8MalformedJvm2() {
        // JVM UTF-8 decoder spots invalid sequences but not if they occur at
        // the end of the input and endOfInput is not true
        for (int i = 0 ; i < MALFORMED.length; i++) {
            doJvmDecoder(MALFORMED[i], true, true, -1);
        }
    }


    @Test
    public void testUtf8MalformedHarmony() {
        for (byte[] input : MALFORMED) {
            doHarmonyDecoder(input, false, true, -1);
        }
    }
}
