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

import org.apache.tomcat.util.buf.HexUtils;


public class TestHPackHuffman {

    /*
     * This specific String exposed an edge case that triggered an unexpected HPACK parsing failure.
     */
    @Test
    public void testValueLeftBrace() throws Exception {
        ByteBuffer buf = ByteBuffer.allocate(10);
        String data = "x-value{";
        HPackHuffman.encode(buf, data);

        buf.flip();
        // Remove the header byte (in Tomcat this is parsed before the bytes are passed to the HPACK decoder)
        buf.get();

        StringBuilder target = new StringBuilder();
        HPackHuffman.decode(buf, buf.remaining(), target);

        Assert.assertEquals("Value changed after encode/decode roundtrip", data, target.toString());
    }


    /*
     * Huffman encoded string literals from the examples in RFC 7541, appendix C.4 and C.6.
     */
    @Test
    public void testDecodeRfc7541Examples() throws Exception {
        doTestDecode("f1e3c2e5f23a6ba0ab90f4ff", "www.example.com");
        doTestDecode("a8eb10649cbf", "no-cache");
        doTestDecode("25a849e95ba97d7f", "custom-key");
        doTestDecode("25a849e95bb8e8b4bf", "custom-value");
        doTestDecode("6402", "302");
        doTestDecode("aec3771a4b", "private");
        doTestDecode("d07abe941054d444a8200595040b8166e082a62d1bff", "Mon, 21 Oct 2013 20:13:21 GMT");
        doTestDecode("9d29ad171863c78f0b97c8e9ae82ae43d3", "https://www.example.com");
        doTestDecode("9bd9ab", "gzip");
        doTestDecode("94e7821dd7f2e6c7b335dfdfcd5b3960d5af27087f3672c1ab270fb5291f9587316065c003ed4ee5b1063d5007",
                "foo=ASDJKHQKBZXOQWEOPIUAXQWEOIU; max-age=3600; version=1");
    }


    @Test(expected = HpackException.class)
    public void testDecodeEos() throws Exception {
        // RFC 7541, section 5.2: a string literal containing the EOS symbol MUST be treated as a decoding error
        doTestDecode("ffffffff", null);
    }


    @Test(expected = HpackException.class)
    public void testDecodePaddingNotEosPrefix() throws Exception {
        // RFC 7541, section 5.2: padding not corresponding to the most significant bits of EOS is a decoding error
        doTestDecode("f0", null);
    }


    @Test(expected = HpackException.class)
    public void testDecodeTooMuchPadding() throws Exception {
        // 'a' (00011) followed by eleven 1 bits of padding
        doTestDecode("1fff", null);
    }


    private void doTestDecode(String hex, String expected) throws HpackException {
        byte[] data = HexUtils.fromHexString(hex);
        StringBuilder target = new StringBuilder();
        HPackHuffman.decode(ByteBuffer.wrap(data), data.length, target);
        Assert.assertEquals(expected, target.toString());
    }
}
