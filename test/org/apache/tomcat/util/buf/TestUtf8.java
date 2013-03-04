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
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;

import static org.junit.Assert.assertEquals;

import org.junit.Assert;
import org.junit.Test;

/**
 * Tests the behaviour of the custom UTF-8 decoder and compares it to the JVM
 * implementation.
 */
public class TestUtf8 {

    // Invalid UTF-8
    private static final byte[] SRC_BYTES_1 =
            new byte[] {-50, -70, -31,  -67, -71, -49, -125, -50, -68, -50,
                        -75, -19, -96, -128, 101, 100,  105, 116, 101, 100};

    // Various invalid UTF-8 sequences
    private static final byte[][] MALFORMED = {
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

    // Expected result after UTF-8 decoding with replacement
    private static final String[] MALFORMED_REPLACE_UTF8 = {
        // three byte sequences
        "\uFFFD\uFFFD\uFFFD", "\uFFFD\uFFFD\uFFFD", "\uFFFD\uFFFD\uFFFD",
        "\uFFFD\uFFFD\uFFFD", "\uFFFD\uFFFD\uFFFD", "\uFFFD\uFFFD\uFFFD",
        "\uFFFD\uFFFD\uFFFD",

        // four byte sequences
        "\uFFFD\uFFFD\uFFFD\uFFFD", "\uFFFD\uFFFD\uFFFD\uFFFD",
        "\uFFFD\uFFFD\uFFFD\uFFFD", "\uFFFD\uFFFD\uFFFD\uFFFD",
        "\uFFFD\uFFFD\uFFFD\uFFFD", "\uFFFD\uFFFD\uFFFD\uFFFD",
        "\uFFFD\uFFFD\uFFFD\uFFFD", "\uFFFD\uFFFD\uFFFD\uFFFD",
        "\uFFFD\uFFFD\uFFFD\uFFFD",

        // five byte sequences
        "\uFFFD\uFFFD\uFFFD\uFFFD\uFFFD", "\uFFFD\uFFFD\uFFFD\uFFFD\uFFFD",
        "\uFFFD\uFFFD\uFFFD\uFFFD\uFFFD", "\uFFFD\uFFFD\uFFFD\uFFFD\uFFFD",

        // six byte sequences
        "\uFFFD\uFFFD\uFFFD\uFFFD\uFFFD\uFFFD",
        "\uFFFD\uFFFD\uFFFD\uFFFD\uFFFD\uFFFD",
        "\uFFFD\uFFFD\uFFFD\uFFFD\uFFFD\uFFFD",
        "\uFFFD\uFFFD\uFFFD\uFFFD\uFFFD\uFFFD" };


    @Test
    public void testJvmDecoder1() {
        // This should trigger an error but currently passes. Once the JVM is
        // fixed, s/false/true/ and s/20/12/
        doJvmDecoder(SRC_BYTES_1, false, false, 20);
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
        doHarmonyDecoder(SRC_BYTES_1, false, true, 12);
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
            if (i == 4 | i == 12) {
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
        // Harmony UTF-8 decoder fails as soon as an invalid sequence is
        // detected
        for (byte[] input : MALFORMED) {
            doHarmonyDecoder(input, false, true, -1);
        }
    }


    @Test
    public void testUtf8MalformedReplacementHarmony() throws Exception {
        CharsetDecoder decoder = new Utf8Decoder();
        decoder.onMalformedInput(CodingErrorAction.REPLACE);
        decoder.onUnmappableCharacter(CodingErrorAction.REPLACE);

        for (int i = 0; i < MALFORMED.length; i++) {
            doMalformed(decoder, i, MALFORMED[i], MALFORMED_REPLACE_UTF8[i]);
            decoder.reset();
        }
    }


    @Test
    public void testUtf8MalformedReplacementJvm() throws Exception {
        CharsetDecoder decoder = Charset.forName("UTF-8").newDecoder();
        decoder.onMalformedInput(CodingErrorAction.REPLACE);
        decoder.onUnmappableCharacter(CodingErrorAction.REPLACE);

        for (int i = 0; i < MALFORMED.length; i++) {
            // Handle JVM inconsistencies
            String expected;
            // In all other cases first invalid byte is replaced and processing
            // continues as if the next byte is the start of a new sequence
            // This does not happen for these tests
            if (i == 4 | i == 12 | i == 16 | i == 17 | i == 18 | i == 19 |
                    i == 20 | i == 21 | i == 22 | i == 23) {
                expected = "\uFFFD";
            } else {
                expected = MALFORMED_REPLACE_UTF8[i];
            }
            doMalformed(decoder, i, MALFORMED[i], expected);
            decoder.reset();
        }
    }


    private void doMalformed(CharsetDecoder decoder, int test, byte[] input,
            String expected) throws Exception {

        ByteBuffer bb = ByteBuffer.allocate(input.length);
        CharBuffer cb = CharBuffer.allocate(bb.limit());

        int i = 0;
        for (; i < input.length; i++) {
            bb.put(input[i]);
            bb.flip();
            CoderResult cr = decoder.decode(bb, cb, false);
            if (cr.isError()) {
                throw new Exception();
            }
            bb.compact();
        }
        bb.flip();
        CoderResult cr = decoder.decode(bb, cb, true);
        if (cr.isError()) {
            throw new Exception();
        }

        cb.flip();

        StringBuilder ashex = new StringBuilder(input.length * 4);
        for (int j = 0; j < input.length; j++) {
            if (i > 0) ashex.append(' ');
            ashex.append(Integer.toBinaryString(input[j] & 0xff));
        }
        String hex = ashex.toString();

        String result = cb.toString();
        Assert.assertEquals(test + ": " + hex, expected, result);
    }
}
