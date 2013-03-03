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
import org.junit.Before;
import org.junit.Test;

public class TestUtf8Extended {

    private List<Utf8TestCase> testCases = new ArrayList<>();

    @Before
    public void setup() {
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
    }

    @Test
    public void testHarmonyDecoder() {
        doTest(new Utf8Decoder());
    }


    @Test
    public void testJvmDecoder() {
        doTest(Charset.forName("UTF-8").newDecoder());
    }


    private void doTest(CharsetDecoder decoder) {
        for (Utf8TestCase testCase : testCases) {
            // Configure decoder to fail on an error
            decoder.reset();
            decoder.onMalformedInput(CodingErrorAction.REPORT);
            decoder.onUnmappableCharacter(CodingErrorAction.REPORT);

            // Add each byte one at a time. The decoder should fail as soon as
            // an invalid sequence has been provided
            int len = testCase.input.length;
            ByteBuffer bb = ByteBuffer.allocate(len);
            CharBuffer cb = CharBuffer.allocate(len);
            for (int i = 0; i < len; i++) {
                bb.put((byte) testCase.input[i]);
                bb.flip();
                CoderResult cr = decoder.decode(bb, cb, false);
                if (cr.isError()) {
                    Assert.assertEquals(testCase.description,
                            testCase.invalidIndex, i);
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
            Assert.assertEquals(testCase.description, testCase.outputReplaced,
                    cb.toString());
        }
    }


    /**
     * Encapsulates a single UTF-8 test case
     */
    private static class Utf8TestCase {
        private final String description;
        private final int[] input;
        private final int invalidIndex;
        private final String outputReplaced;

        public Utf8TestCase(String description, int[] input, int invalidIndex,
                String outputReplaced) {
            this.description = description;
            this.input = input;
            this.invalidIndex = invalidIndex;
            this.outputReplaced = outputReplaced;
        }
    }
}
