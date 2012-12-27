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
package org.apache.tomcat.websocket;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

import org.apache.tomcat.util.buf.B2CConverter;

public class TestUtf8 {

    // Invalid UTF-8
    private static final byte[] SRC_BYTES_1 =
            new byte[] {-50, -70, -31,  -67, -71, -49, -125, -50, -68, -50,
                        -75, -19, -96, -128, 101, 100,  105, 116, 101, 100};

    // Invalid code point (out of range)
    private static final byte[] SRC_BYTES_2 =
            new byte[] {-12, -112, -128, -128};

    @Test
    public void testJvmDecoder1() {
        // This should trigger an error but currently passes. Once the JVM is
        // fixed, s/false/true/ and s/20/13/
        doJvmDecoder(SRC_BYTES_1, false, 20);
    }


    @Test
    public void testJvmDecoder2() {
        // Ideally should fail after 2 bytes (i==1)
        doJvmDecoder(SRC_BYTES_2, true, 3);
    }


    private void doJvmDecoder(byte[] src, boolean errorExpected,
            int failPosExpected) {
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
            CoderResult cr = decoder.decode(bb, cb, false);
            if (cr.isError()) {
                error = true;
                break;
            }
            bb.compact();
        }

        assertEquals(Boolean.valueOf(errorExpected), Boolean.valueOf(error));
        assertEquals(failPosExpected, i);
    }


    @Test
    public void testHarmonyDecoder1() {
        doHarmonyDecoder(SRC_BYTES_1, true, 13);
    }


    @Test
    public void testHarmonyDecoder2() {
        doHarmonyDecoder(SRC_BYTES_2, true, 1);
    }


    public void doHarmonyDecoder(byte[] src, boolean errorExpected,
            int failPosExpected) {
        CharsetDecoder decoder = new Utf8Decoder();

        ByteBuffer bb = ByteBuffer.allocate(src.length);
        CharBuffer cb = CharBuffer.allocate(bb.limit());

        boolean error = false;
        int i = 0;
        for (; i < src.length; i++) {
            bb.put(src[i]);
            bb.flip();
            CoderResult cr = decoder.decode(bb, cb, false);
            if (cr.isError()) {
                error = true;
                break;
            }
            bb.compact();
        }

        assertEquals(Boolean.valueOf(errorExpected), Boolean.valueOf(error));
        assertEquals(failPosExpected, i);
    }
}
