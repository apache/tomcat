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
import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

/**
 * These tests have been written with reference to
 * <a href="http://www.unicode.org/versions/Unicode6.2.0/ch03.pdf">unicode 6.2,
 * chapter 3, section 3.9</a>.
 */
public class TestUtf8Extended {

    // Indicates that at invalid sequence is detected one character later than
    // the earliest possible moment
    private static final int ERROR_POS_PLUS1 = 1;
    // Indicates that at invalid sequence is detected two characters later than
    // the earliest possible moment
    private static final int ERROR_POS_PLUS2 = 2;
    // Indicates that at invalid sequence is detected three characters later
    // than the earliest possible moment
    private static final int ERROR_POS_PLUS3 = 4;
    // Indicates that the trailing valid byte is included in replacement of the
    // previous error
    private static final int REPLACE_SWALLOWS_TRAILER = 8;

    public static final List<Utf8TestCase> TEST_CASES = new ArrayList<>();

    static {
        TEST_CASES.add(new Utf8TestCase(
                "Zero length input",
                new int[] {},
                -1,
                ""));
        TEST_CASES.add(new Utf8TestCase(
                "Valid one byte sequence",
                new int[] {0x41},
                -1,
                "A"));
        TEST_CASES.add(new Utf8TestCase(
                "Valid two byte sequence",
                new int[] {0xC2, 0xA9},
                -1,
                "\u00A9"));
        TEST_CASES.add(new Utf8TestCase(
                "Valid three byte sequence",
                new int[] {0xE0, 0xA4, 0x87},
                -1,
                "\u0907"));
        TEST_CASES.add(new Utf8TestCase(
                "Valid four byte sequence",
                new int[] {0xF0, 0x90, 0x90, 0x80},
                -1,
                "\uD801\uDC00"));
        // JVM decoder does not report error until all 4 bytes are available
        TEST_CASES.add(new Utf8TestCase(
                "Invalid code point - out of range",
                new int[] {0x41, 0xF4, 0x90, 0x80, 0x80, 0x41},
                2,
                "A\uFFFD\uFFFD\uFFFD\uFFFDA").addForJvm(ERROR_POS_PLUS2));
        // JVM decoder does not report error until all 2 bytes are available
        TEST_CASES.add(new Utf8TestCase(
                "Valid sequence padded from one byte to two",
                new int[] {0x41, 0xC0, 0xC1, 0x41},
                1,
                "A\uFFFD\uFFFDA").addForJvm(ERROR_POS_PLUS1));
        // JVM decoder does not report error until all 3 bytes are available
        TEST_CASES.add(new Utf8TestCase(
                "Valid sequence padded from one byte to three",
                new int[] {0x41, 0xE0, 0x80, 0xC1, 0x41},
                2,
                "A\uFFFD\uFFFD\uFFFDA").addForJvm(ERROR_POS_PLUS1));
        // JVM decoder does not report error until all 4 bytes are available
        TEST_CASES.add(new Utf8TestCase(
                "Valid sequence padded from one byte to four",
                new int[] {0x41, 0xF0, 0x80, 0x80, 0xC1, 0x41},
                2,
                "A\uFFFD\uFFFD\uFFFD\uFFFDA").addForJvm(ERROR_POS_PLUS2));
        TEST_CASES.add(new Utf8TestCase(
                "Invalid one byte 1111 1111",
                new int[] {0x41, 0xFF, 0x41},
                1,
                "A\uFFFDA"));
        TEST_CASES.add(new Utf8TestCase(
                "Invalid one byte 1111 0000",
                new int[] {0x41, 0xF0, 0x41},
                2,
                "A\uFFFDA").addForJvm(REPLACE_SWALLOWS_TRAILER));
        TEST_CASES.add(new Utf8TestCase(
                "Invalid one byte 1110 0000",
                new int[] {0x41, 0xE0, 0x41},
                2,
                "A\uFFFDA").addForJvm(REPLACE_SWALLOWS_TRAILER));
        TEST_CASES.add(new Utf8TestCase(
                "Invalid one byte 1100 0000",
                new int[] {0x41, 0xC0, 0x41},
                1,
                "A\uFFFDA").addForJvm(ERROR_POS_PLUS1));
        TEST_CASES.add(new Utf8TestCase(
                "Invalid one byte 1000 000",
                new int[] {0x41, 0x80, 0x41},
                1,
                "A\uFFFDA"));
        TEST_CASES.add(new Utf8TestCase(
                "Invalid sequence from unicode 6.2 spec, table 3-8",
                new int[] {0x61, 0xF1, 0x80, 0x80, 0xE1, 0x80, 0xC2, 0x62, 0x80,
                        0x63, 0x80, 0xBF, 0x64},
                4,
                "a\uFFFD\uFFFD\uFFFDb\uFFFDc\uFFFD\uFFFDd"));
        TEST_CASES.add(new Utf8TestCase(
                "Valid 4-byte sequence truncated to 3 bytes",
                new int[] {0x61, 0xF0, 0x90, 0x90},
                3,
                "a\uFFFD"));
        TEST_CASES.add(new Utf8TestCase(
                "Valid 4-byte sequence truncated to 2 bytes",
                new int[] {0x61, 0xF0, 0x90},
                2,
                "a\uFFFD"));
        TEST_CASES.add(new Utf8TestCase(
                "Valid 4-byte sequence truncated to 1 byte",
                new int[] {0x61, 0xF0},
                1,
                "a\uFFFD"));
        TEST_CASES.add(new Utf8TestCase(
                "Valid 4-byte sequence truncated to 3 bytes with trailer",
                new int[] {0x61, 0xF0, 0x90, 0x90, 0x61},
                4,
                "a\uFFFDa"));
        TEST_CASES.add(new Utf8TestCase(
                "Valid 4-byte sequence truncated to 2 bytes with trailer",
                new int[] {0x61, 0xF0, 0x90, 0x61},
                3,
                "a\uFFFDa").addForJvm(REPLACE_SWALLOWS_TRAILER));
        TEST_CASES.add(new Utf8TestCase(
                "Valid 4-byte sequence truncated to 1 byte with trailer",
                new int[] {0x61, 0xF0, 0x61},
                2,
                "a\uFFFDa").addForJvm(REPLACE_SWALLOWS_TRAILER));
        TEST_CASES.add(new Utf8TestCase(
                "U+0000 zero-padded to two bytes",
                new int[] {0x61, 0xC0, 0x80, 0x61},
                1,
                "a\uFFFD\uFFFDa").addForJvm(ERROR_POS_PLUS1));
        TEST_CASES.add(new Utf8TestCase(
                "U+007F zero-padded to two bytes",
                new int[] {0x61, 0xC1, 0xBF, 0x61},
                2,
                "a\uFFFD\uFFFDa"));
        TEST_CASES.add(new Utf8TestCase(
                "Two bytes, all 1's",
                new int[] {0x61, 0xFF, 0xFF, 0x61},
                1,
                "a\uFFFD\uFFFDa"));
        TEST_CASES.add(new Utf8TestCase(
                "Two bytes, 1110 first byte first nibble",
                new int[] {0x61, 0xE0, 0x80, 0x61},
                2,
                "a\uFFFD\uFFFDa").addForJvm(ERROR_POS_PLUS1));
        TEST_CASES.add(new Utf8TestCase(
                "Two bytes, 101x first byte first nibble",
                new int[] {0x61, 0xA0, 0x80, 0x61},
                1,
                "a\uFFFD\uFFFDa"));
        TEST_CASES.add(new Utf8TestCase(
                "Two bytes, invalid second byte",
                new int[] {0x61, 0xC2, 0x00, 0x61},
                2,
                "a\uFFFD\u0000a"));
        TEST_CASES.add(new Utf8TestCase(
                "Two bytes, invalid second byte",
                new int[] {0x61, 0xC2, 0xC0, 0x61},
                2,
                "a\uFFFD\uFFFDa"));
    }

    @Test
    public void testHarmonyDecoder() {
        CharsetDecoder decoder = new Utf8Decoder();
        for (Utf8TestCase testCase : TEST_CASES) {
            doTest(decoder, testCase, 0);
        }
    }


    @Test
    public void testJvmDecoder() {
        CharsetDecoder decoder = Charset.forName("UTF-8").newDecoder();
        for (Utf8TestCase testCase : TEST_CASES) {
            doTest(decoder, testCase, testCase.flagsJvm);
        }
    }


    private void doTest(CharsetDecoder decoder, Utf8TestCase testCase,
            int flags) {

        int len = testCase.input.length;
        ByteBuffer bb = ByteBuffer.allocate(len);
        CharBuffer cb = CharBuffer.allocate(len);

        // Configure decoder to fail on an error
        decoder.reset();
        decoder.onMalformedInput(CodingErrorAction.REPORT);
        decoder.onUnmappableCharacter(CodingErrorAction.REPORT);

        // Add each byte one at a time. The decoder should fail as soon as
        // an invalid sequence has been provided
        for (int i = 0; i < len; i++) {
            bb.put((byte) testCase.input[i]);
            bb.flip();
            CoderResult cr = decoder.decode(bb, cb, false);
            if (cr.isError()) {
                int expected =  testCase.invalidIndex;
                if ((flags & ERROR_POS_PLUS1) != 0) {
                    expected += 1;
                } else if ((flags & ERROR_POS_PLUS2) != 0) {
                    expected += 2;
                } else if ((flags & ERROR_POS_PLUS3) != 0) {
                    expected += 3;
                }
                Assert.assertEquals(testCase.description, expected, i);
                break;
            }
            bb.compact();
        }

        // Configure decoder to replace on an error
        decoder.reset();
        decoder.onMalformedInput(CodingErrorAction.REPLACE);
        decoder.onUnmappableCharacter(CodingErrorAction.REPLACE);

        // Add each byte one at a time.
        bb.clear();
        cb.clear();
        for (int i = 0; i < len; i++) {
            bb.put((byte) testCase.input[i]);
            bb.flip();
            CoderResult cr = decoder.decode(bb, cb, false);
            if (cr.isError()) {
                Assert.fail(testCase.description);
            }
            bb.compact();
        }
        // For incomplete sequences at the end of the input need to tell
        // the decoder the input has ended
        bb.flip();
        CoderResult cr = decoder.decode(bb, cb, true);
        if (cr.isError()) {
            Assert.fail(testCase.description);
        }
        cb.flip();
        if ((flags & REPLACE_SWALLOWS_TRAILER) == 0) {
            Assert.assertEquals(testCase.description, testCase.outputReplaced,
                    cb.toString());
        } else {
            Assert.assertEquals(testCase.description,
                    testCase.outputReplaced.subSequence(0,
                            testCase.outputReplaced.length() - 1),
                    cb.toString());
        }
    }


    /**
     * Encapsulates a single UTF-8 test case
     */
    public static class Utf8TestCase {
        public final String description;
        public final int[] input;
        public final int invalidIndex;
        public final String outputReplaced;
        public int flagsJvm = 0;

        public Utf8TestCase(String description, int[] input, int invalidIndex,
                String outputReplaced) {
            this.description = description;
            this.input = input;
            this.invalidIndex = invalidIndex;
            this.outputReplaced = outputReplaced;

        }

        public Utf8TestCase addForJvm(int flag) {
            this.flagsJvm = this.flagsJvm | flag;
            return this;
        }
    }
}
