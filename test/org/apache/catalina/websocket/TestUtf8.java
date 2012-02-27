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
package org.apache.catalina.websocket;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;

import static org.junit.Assert.fail;
import org.junit.Test;

import org.apache.tomcat.util.buf.B2CConverter;

public class TestUtf8 {

    // Invalid UTF-8
    private static final byte[] SRC_BYTES =
            new byte[] {-50, -70, -31,  -67, -71, -49, -125, -50, -68, -50,
                        -75, -19, -96, -128, 101, 100,  105, 116, 101, 100};


    @Test
    public void testDecoder() throws Exception {
        CharsetDecoder decoder = B2CConverter.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);


        ByteBuffer bb = ByteBuffer.wrap(SRC_BYTES);
        CharBuffer cb = CharBuffer.allocate(bb.limit());

        CoderResult cr = decoder.decode(bb, cb, true);
        // if (!cr.isError()) {
        if (cr.isError()) {
            // This should fail but currently passes. Once this test fails, the
            // JVM has been fixed and the commented out if statement above can
            // be used.
            fail();
        }
    }

    @Test
    public void testDecoder2() throws Exception {

        CharsetDecoder decoder = new Utf8Decoder();

        ByteBuffer bb = ByteBuffer.wrap(SRC_BYTES);
        CharBuffer cb = CharBuffer.allocate(bb.limit());

        CoderResult cr = decoder.decode(bb, cb, true);
        // Confirm the custom decoder correctly reports an error
        if (!cr.isError()) {
            fail();
        }
    }
}
