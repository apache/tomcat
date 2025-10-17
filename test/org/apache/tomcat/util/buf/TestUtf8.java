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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

/**
 * These tests have been written with reference to
 * <a href="http://www.unicode.org/versions/Unicode6.2.0/ch03.pdf">unicode 6.2,
 * chapter 3, section 3.9</a>.
 */
public class TestUtf8 {

    public static final List<Utf8TestCase> TEST_CASES;

    static {
        // All known issues have been fixed in Java 8
        // https://bugs.openjdk.java.net/browse/JDK-8039751

        ArrayList<Utf8TestCase> testCases = new ArrayList<>();

        testCases.add(new Utf8TestCase(
                "Zero length input",
                new int[] {},
                -1,
                ""));
        testCases.add(new Utf8TestCase(
                "Valid one byte sequence",
                new int[] {0x41},
                -1,
                "A"));
        testCases.add(new Utf8TestCase(
                "Valid two byte sequence",
                new int[] {0xC2, 0xA9},
                -1,
                "\u00A9"));
        testCases.add(new Utf8TestCase(
                "Valid three byte sequence",
                new int[] {0xE0, 0xA4, 0x87},
                -1,
                "\u0907"));
        testCases.add(new Utf8TestCase(
                "Valid four byte sequence",
                new int[] {0xF0, 0x90, 0x90, 0x80},
                -1,
                "\uD801\uDC00"));
        testCases.add(new Utf8TestCase(
                "Invalid code point - out of range",
                new int[] {0x41, 0xF4, 0x90, 0x80, 0x80, 0x41},
                2,
                "A\uFFFD\uFFFD\uFFFD\uFFFDA"));
        testCases.add(new Utf8TestCase(
                "Valid sequence padded from one byte to two",
                new int[] {0x41, 0xC0, 0xC1, 0x41},
                1,
                "A\uFFFD\uFFFDA"));
        testCases.add(new Utf8TestCase(
                "Valid sequence padded from one byte to three",
                new int[] {0x41, 0xE0, 0x80, 0xC1, 0x41},
                2,
                "A\uFFFD\uFFFD\uFFFDA"));
        testCases.add(new Utf8TestCase(
                "Valid sequence padded from one byte to four",
                new int[] {0x41, 0xF0, 0x80, 0x80, 0xC1, 0x41},
                2,
                "A\uFFFD\uFFFD\uFFFD\uFFFDA"));
        testCases.add(new Utf8TestCase(
                "Invalid one byte 1111 1111",
                new int[] {0x41, 0xFF, 0x41},
                1,
                "A\uFFFDA"));
        testCases.add(new Utf8TestCase(
                "Invalid one byte 1111 0000",
                new int[] {0x41, 0xF0, 0x41},
                2,
                "A\uFFFDA"));
        testCases.add(new Utf8TestCase(
                "Invalid one byte 1110 0000",
                new int[] {0x41, 0xE0, 0x41},
                2,
                "A\uFFFDA"));
        testCases.add(new Utf8TestCase(
                "Invalid one byte 1100 0000",
                new int[] {0x41, 0xC0, 0x41},
                1,
                "A\uFFFDA"));
        testCases.add(new Utf8TestCase(
                "Invalid one byte 1000 000",
                new int[] {0x41, 0x80, 0x41},
                1,
                "A\uFFFDA"));
        testCases.add(new Utf8TestCase(
                "Invalid sequence from unicode 6.2 spec, table 3-8",
                new int[] {0x61, 0xF1, 0x80, 0x80, 0xE1, 0x80, 0xC2, 0x62, 0x80,
                        0x63, 0x80, 0xBF, 0x64},
                4,
                "a\uFFFD\uFFFD\uFFFDb\uFFFDc\uFFFD\uFFFDd"));
        testCases.add(new Utf8TestCase(
                "Valid 4-byte sequence truncated to 3 bytes",
                new int[] {0x61, 0xF0, 0x90, 0x90},
                3,
                "a\uFFFD"));
        testCases.add(new Utf8TestCase(
                "Valid 4-byte sequence truncated to 2 bytes",
                new int[] {0x61, 0xF0, 0x90},
                2,
                "a\uFFFD"));
        testCases.add(new Utf8TestCase(
                "Valid 4-byte sequence truncated to 1 byte",
                new int[] {0x61, 0xF0},
                1,
                "a\uFFFD"));
        testCases.add(new Utf8TestCase(
                "Valid 4-byte sequence truncated to 3 bytes with trailer",
                new int[] {0x61, 0xF0, 0x90, 0x90, 0x61},
                4,
                "a\uFFFDa"));
        testCases.add(new Utf8TestCase(
                "Valid 4-byte sequence truncated to 2 bytes with trailer",
                new int[] {0x61, 0xF0, 0x90, 0x61},
                3,
                "a\uFFFDa"));
        testCases.add(new Utf8TestCase(
                "Valid 4-byte sequence truncated to 1 byte with trailer",
                new int[] {0x61, 0xF0, 0x61},
                2,
                "a\uFFFDa"));
        testCases.add(new Utf8TestCase(
                "U+0000 zero-padded to two bytes",
                new int[] {0x61, 0xC0, 0x80, 0x61},
                1,
                "a\uFFFD\uFFFDa"));
        testCases.add(new Utf8TestCase(
                "U+007F zero-padded to two bytes",
                new int[] {0x61, 0xC1, 0xBF, 0x61},
                1,
                "a\uFFFD\uFFFDa"));
        testCases.add(new Utf8TestCase(
                "Two bytes, all 1's",
                new int[] {0x61, 0xFF, 0xFF, 0x61},
                1,
                "a\uFFFD\uFFFDa"));
        testCases.add(new Utf8TestCase(
                "Two bytes, 1110 first byte first nibble",
                new int[] {0x61, 0xE0, 0x80, 0x61},
                2,
                "a\uFFFD\uFFFDa"));
        testCases.add(new Utf8TestCase(
                "Two bytes, 101x first byte first nibble",
                new int[] {0x61, 0xA0, 0x80, 0x61},
                1,
                "a\uFFFD\uFFFDa"));
        testCases.add(new Utf8TestCase(
                "Two bytes, invalid second byte",
                new int[] {0x61, 0xC2, 0x00, 0x61},
                2,
                "a\uFFFD\u0000a"));
        testCases.add(new Utf8TestCase(
                "Two bytes, invalid second byte",
                new int[] {0x61, 0xC2, 0xC0, 0x61},
                2,
                "a\uFFFD\uFFFDa"));
        testCases.add(new Utf8TestCase(
                "Three bytes, U+0000 zero-padded",
                new int[] {0x61, 0xE0, 0x80, 0x80, 0x61},
                2,
                "a\uFFFD\uFFFD\uFFFDa"));
        testCases.add(new Utf8TestCase(
                "Three bytes, U+007F zero-padded",
                new int[] {0x61, 0xE0, 0x81, 0xBF, 0x61},
                2,
                "a\uFFFD\uFFFD\uFFFDa"));
        testCases.add(new Utf8TestCase(
                "Three bytes, U+07FF zero-padded",
                new int[] {0x61, 0xE0, 0x9F, 0xBF, 0x61},
                2,
                "a\uFFFD\uFFFD\uFFFDa"));
        testCases.add(new Utf8TestCase(
                "Three bytes, all 1's",
                new int[] {0x61, 0xFF, 0xFF, 0xFF, 0x61},
                1,
                "a\uFFFD\uFFFD\uFFFDa"));
        testCases.add(new Utf8TestCase(
                "Three bytes, invalid first byte",
                new int[] {0x61, 0xF8, 0x80, 0x80, 0x61},
                1,
                "a\uFFFD\uFFFD\uFFFDa"));
        testCases.add(new Utf8TestCase(
                "Three bytes, invalid second byte",
                new int[] {0x61, 0xE0, 0xC0, 0x80, 0x61},
                2,
                "a\uFFFD\uFFFD\uFFFDa"));
        testCases.add(new Utf8TestCase(
                "Three bytes, invalid third byte",
                new int[] {0x61, 0xE1, 0x80, 0xC0, 0x61},
                3,
                "a\uFFFD\uFFFDa"));
        testCases.add(new Utf8TestCase(
                "Four bytes, U+0000 zero-padded",
                new int[] {0x61, 0xF0, 0x80, 0x80, 0x80, 0x61},
                2,
                "a\uFFFD\uFFFD\uFFFD\uFFFDa"));
        testCases.add(new Utf8TestCase(
                "Four bytes, U+007F zero-padded",
                new int[] {0x61, 0xF0, 0x80, 0x81, 0xBF, 0x61},
                2,
                "a\uFFFD\uFFFD\uFFFD\uFFFDa"));
        testCases.add(new Utf8TestCase(
                "Four bytes, U+07FF zero-padded",
                new int[] {0x61, 0xF0, 0x80, 0x9F, 0xBF, 0x61},
                2,
                "a\uFFFD\uFFFD\uFFFD\uFFFDa"));
        testCases.add(new Utf8TestCase(
                "Four bytes, U+FFFF zero-padded",
                new int[] {0x61, 0xF0, 0x8F, 0xBF, 0xBF, 0x61},
                2,
                "a\uFFFD\uFFFD\uFFFD\uFFFDa"));
        testCases.add(new Utf8TestCase(
                "Four bytes, all 1's",
                new int[] {0x61, 0xFF, 0xFF, 0xFF, 0xFF, 0x61},
                1,
                "a\uFFFD\uFFFD\uFFFD\uFFFDa"));
        testCases.add(new Utf8TestCase(
                "Four bytes, invalid first byte",
                new int[] {0x61, 0xF8, 0x80, 0x80, 0x80, 0x61},
                1,
                "a\uFFFD\uFFFD\uFFFD\uFFFDa"));
        testCases.add(new Utf8TestCase(
                "Four bytes, invalid second byte",
                new int[] {0x61, 0xF1, 0xC0, 0x80, 0x80, 0x61},
                2,
                "a\uFFFD\uFFFD\uFFFD\uFFFDa"));
        testCases.add(new Utf8TestCase(
                "Four bytes, invalid third byte",
                new int[] {0x61, 0xF1, 0x80, 0xC0, 0x80, 0x61},
                3,
                "a\uFFFD\uFFFD\uFFFDa"));
        testCases.add(new Utf8TestCase(
                "Four bytes, invalid fourth byte",
                new int[] {0x61, 0xF1, 0x80, 0x80, 0xC0, 0x61},
                4,
                "a\uFFFD\uFFFDa"));
        testCases.add(new Utf8TestCase(
                "Five bytes, U+0000 zero padded",
                new int[] {0x61, 0xF8, 0x80, 0x80, 0x80, 0x80, 0x61},
                1,
                "a\uFFFD\uFFFD\uFFFD\uFFFD\uFFFDa"));
        testCases.add(new Utf8TestCase(
                "Five bytes, U+007F zero padded",
                new int[] {0x61, 0xF8, 0x80, 0x80, 0x81, 0xBF, 0x61},
                1,
                "a\uFFFD\uFFFD\uFFFD\uFFFD\uFFFDa"));
        testCases.add(new Utf8TestCase(
                "Five bytes, U+07FF zero padded",
                new int[] {0x61, 0xF8, 0x80, 0x80, 0x9F, 0xBF, 0x61},
                1,
                "a\uFFFD\uFFFD\uFFFD\uFFFD\uFFFDa"));
        testCases.add(new Utf8TestCase(
                "Five bytes, U+FFFF zero padded",
                new int[] {0x61, 0xF8, 0x80, 0x8F, 0xBF, 0xBF, 0x61},
                1,
                "a\uFFFD\uFFFD\uFFFD\uFFFD\uFFFDa"));
        testCases.add(new Utf8TestCase(
                "Six bytes, U+0000 zero padded",
                new int[] {0x61, 0xFC, 0x80, 0x80, 0x80, 0x80, 0x80, 0x61},
                1,
                "a\uFFFD\uFFFD\uFFFD\uFFFD\uFFFD\uFFFDa"));
        testCases.add(new Utf8TestCase(
                "Six bytes, U+007F zero padded",
                new int[] {0x61, 0xFC, 0x80, 0x80, 0x80, 0x81, 0xBF, 0x61},
                1,
                "a\uFFFD\uFFFD\uFFFD\uFFFD\uFFFD\uFFFDa"));
        testCases.add(new Utf8TestCase(
                "Six bytes, U+07FF zero padded",
                new int[] {0x61, 0xFC, 0x80, 0x80, 0x80, 0x9F, 0xBF, 0x61},
                1,
                "a\uFFFD\uFFFD\uFFFD\uFFFD\uFFFD\uFFFDa"));
        testCases.add(new Utf8TestCase(
                "Six bytes, U+FFFF zero padded",
                new int[] {0x61, 0xFC, 0x80, 0x80, 0x8F, 0xBF, 0xBF, 0x61},
                1,
                "a\uFFFD\uFFFD\uFFFD\uFFFD\uFFFD\uFFFDa"));
        testCases.add(new Utf8TestCase(
                "Original test case - derived from Autobahn?",
                new int[] {0xCE, 0xBA, 0xE1, 0xDB, 0xB9, 0xCF, 0x83, 0xCE,
                           0xBC, 0xCE, 0xB5, 0xED, 0x80, 0x65, 0x64, 0x69,
                           0x74, 0x65, 0x64},
                3,
                "\u03BA\uFFFD\u06F9\u03C3\u03BC\u03B5\uFFFDedited"));

        TEST_CASES = Collections.unmodifiableList(testCases);
    }

    @Test
    public void testJvmDecoder() {
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder();
        int testCount = 0;
        try {
            for (Utf8TestCase testCase : TEST_CASES) {
                doTest(decoder, testCase);
                testCount++;
            }
        } finally {
            if (testCount < TEST_CASES.size()) {
                System.err.println("Executed " + testCount + " of " +
                        TEST_CASES.size() + " UTF-8 tests before " +
                        "encountering a failure");
            }
        }
    }


    private void doTest(CharsetDecoder decoder, Utf8TestCase testCase) {

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

        String expected = testCase.outputReplaced;

        Assert.assertEquals(testCase.description, expected, cb.toString());
    }


    /**
     * Encapsulates a single UTF-8 test case
     */
    public static class Utf8TestCase {
        public final String description;
        public final int[] input;
        public final int invalidIndex;
        public final String outputReplaced;

        public Utf8TestCase(String description, int[] input, int invalidIndex,
                String outputReplaced) {
            this.description = description;
            this.input = input;
            this.invalidIndex = invalidIndex;
            this.outputReplaced = outputReplaced;
        }
    }
}
