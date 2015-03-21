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
package org.apache.coyote.http2;

import java.nio.ByteBuffer;

import org.junit.Assert;
import org.junit.Test;

import org.apache.tomcat.util.http.MimeHeaders;

public class TestHpack {

    @Test
    public void testEncode() throws Exception {
        MimeHeaders headers = new MimeHeaders();
        headers.setValue("header1").setString("value1");
        headers.setValue(":status").setString("200");
        headers.setValue("header2").setString("value2");
        ByteBuffer output = ByteBuffer.allocate(512);
        HpackEncoder encoder = new HpackEncoder(1024);
        encoder.encode(headers, output);
        output.flip();
        // Size is supposed to be 33 without huffman, or 27 with it
        // TODO: use the HpackHeaderFunction to enable huffman predictably
        Assert.assertEquals(27, output.remaining());
        output.clear();
        encoder.encode(headers, output);
        output.flip();
        // Size is now 3 after using the table
        Assert.assertEquals(3, output.remaining());
    }

    @Test
    public void testDecode() throws Exception {
        MimeHeaders headers = new MimeHeaders();
        headers.setValue("header1").setString("value1");
        headers.setValue(":status").setString("200");
        headers.setValue("header2").setString("value2");
        ByteBuffer output = ByteBuffer.allocate(512);
        HpackEncoder encoder = new HpackEncoder(1024);
        encoder.encode(headers, output);
        output.flip();
        MimeHeaders headers2 = new MimeHeaders();
        HpackDecoder decoder = new HpackDecoder();
        decoder.setHeaderEmitter(new HeadersListener(headers2));
        decoder.decode(output);
        // Redo (table is supposed to be updated)
        output.clear();
        encoder.encode(headers, output);
        output.flip();
        headers2.recycle();
        Assert.assertEquals(3, output.remaining());
        // Check that the decoder is using the table right
        decoder.decode(output);
        Assert.assertEquals("value2", headers2.getHeader("header2"));
    }

    private static class HeadersListener implements HpackDecoder.HeaderEmitter {
        private final MimeHeaders headers;
        public HeadersListener(MimeHeaders headers) {
            this.headers = headers;
        }
        @Override
        public void emitHeader(String name, String value, boolean neverIndex) {
            headers.setValue(name).setString(value);
        }
    }

    // TODO: Write more complete tests

}
