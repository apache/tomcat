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

import java.io.CharConversionException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.Assert;
import org.junit.Test;

public class TestUDecoder {

    @Test(expected = IllegalArgumentException.class)
    public void testURLDecodeStringInvalid01() {
        // %n rather than %nn should throw an IAE according to the Javadoc
        UDecoder.URLDecode("%5xxxxx", StandardCharsets.UTF_8);
    }


    @Test(expected = IllegalArgumentException.class)
    public void testURLDecodeStringInvalid02() {
        // Edge case trying to trigger ArrayIndexOutOfBoundsException
        UDecoder.URLDecode("%5", StandardCharsets.UTF_8);
    }


    @Test
    public void testURLDecodeStringValidIso88591Start() {
        String result = UDecoder.URLDecode("%41xxxx", StandardCharsets.ISO_8859_1);
        Assert.assertEquals("Axxxx", result);
    }


    @Test
    public void testURLDecodeStringValidIso88591Middle() {
        String result = UDecoder.URLDecode("xx%41xx", StandardCharsets.ISO_8859_1);
        Assert.assertEquals("xxAxx", result);
    }


    @Test
    public void testURLDecodeStringValidIso88591End() {
        String result = UDecoder.URLDecode("xxxx%41", StandardCharsets.ISO_8859_1);
        Assert.assertEquals("xxxxA", result);
    }


    @Test
    public void testURLDecodeStringValidUtf8Start() {
        String result = UDecoder.URLDecode("%c3%aaxxxx", StandardCharsets.UTF_8);
        Assert.assertEquals("\u00eaxxxx", result);
    }


    @Test
    public void testURLDecodeStringValidUtf8Middle() {
        String result = UDecoder.URLDecode("xx%c3%aaxx", StandardCharsets.UTF_8);
        Assert.assertEquals("xx\u00eaxx", result);
    }


    @Test
    public void testURLDecodeStringValidUtf8End() {
        String result = UDecoder.URLDecode("xxxx%c3%aa", StandardCharsets.UTF_8);
        Assert.assertEquals("xxxx\u00ea", result);
    }


    @Test
    public void testURLDecodeStringNonAsciiValidNone() {
        String result = UDecoder.URLDecode("\u00eaxxxx", StandardCharsets.UTF_8);
        Assert.assertEquals("\u00eaxxxx", result);
    }


    @Test
    public void testURLDecodeStringNonAsciiValidUtf8() {
        String result = UDecoder.URLDecode("\u00ea%c3%aa", StandardCharsets.UTF_8);
        Assert.assertEquals("\u00ea\u00ea", result);
    }


    @Test
    public void testURLDecodeStringSolidus01() throws IOException {
        doTestSolidus("xxxxxx", "xxxxxx");
    }


    @Test
    public void testURLDecodeStringSolidus02() throws IOException {
        doTestSolidus("%20xxxx", " xxxx");
    }


    @Test
    public void testURLDecodeStringSolidus03() throws IOException {
        doTestSolidus("xx%20xx", "xx xx");
    }


    @Test
    public void testURLDecodeStringSolidus04() throws IOException {
        doTestSolidus("xxxx%20", "xxxx ");
    }


    @Test(expected = CharConversionException.class)
    public void testURLDecodeStringSolidus05a() throws IOException {
        doTestSolidus("%2fxxxx", EncodedSolidusHandling.REJECT);
    }


    @Test
    public void testURLDecodeStringSolidus05b() throws IOException {
        String result = doTestSolidus("%2fxxxx", EncodedSolidusHandling.PASS_THROUGH);
        Assert.assertEquals("%2fxxxx", result);
    }


    @Test
    public void testURLDecodeStringSolidus05c() throws IOException {
        String result = doTestSolidus("%2fxxxx", EncodedSolidusHandling.DECODE);
        Assert.assertEquals("/xxxx", result);
    }


    @Test(expected = CharConversionException.class)
    public void testURLDecodeStringSolidus06a() throws IOException {
        doTestSolidus("%2fxx%20xx", EncodedSolidusHandling.REJECT);
    }


    @Test
    public void testURLDecodeStringSolidus06b() throws IOException {
        String result = doTestSolidus("%2fxx%20xx", EncodedSolidusHandling.PASS_THROUGH);
        Assert.assertEquals("%2fxx xx", result);
    }


    @Test
    public void testURLDecodeStringSolidus06c() throws IOException {
        String result = doTestSolidus("%2fxx%20xx", EncodedSolidusHandling.DECODE);
        Assert.assertEquals("/xx xx", result);
    }


    @Test(expected = CharConversionException.class)
    public void testURLDecodeStringSolidus07a() throws IOException {
        doTestSolidus("xx%2f%20xx", EncodedSolidusHandling.REJECT);
    }


    @Test
    public void testURLDecodeStringSolidus07b() throws IOException {
        String result = doTestSolidus("xx%2f%20xx", EncodedSolidusHandling.PASS_THROUGH);
        Assert.assertEquals("xx%2f xx", result);
    }


    @Test
    public void testURLDecodeStringSolidus07c() throws IOException {
        String result = doTestSolidus("xx%2f%20xx", EncodedSolidusHandling.DECODE);
        Assert.assertEquals("xx/ xx", result);
    }


    @Test(expected = CharConversionException.class)
    public void testURLDecodeStringSolidus08a() throws IOException {
        doTestSolidus("xx%20%2fxx", EncodedSolidusHandling.REJECT);
    }


    @Test
    public void testURLDecodeStringSolidus08b() throws IOException {
        String result = doTestSolidus("xx%20%2fxx", EncodedSolidusHandling.PASS_THROUGH);
        Assert.assertEquals("xx %2fxx", result);
    }


    @Test
    public void testURLDecodeStringSolidus08c() throws IOException {
        String result = doTestSolidus("xx%20%2fxx", EncodedSolidusHandling.DECODE);
        Assert.assertEquals("xx /xx", result);
    }


    @Test(expected = CharConversionException.class)
    public void testURLDecodeStringSolidus09a() throws IOException {
        doTestSolidus("xx%20xx%2f", EncodedSolidusHandling.REJECT);
    }


    @Test
    public void testURLDecodeStringSolidus09b() throws IOException {
        String result = doTestSolidus("xx%20xx%2f", EncodedSolidusHandling.PASS_THROUGH);
        Assert.assertEquals("xx xx%2f", result);
    }


    @Test
    public void testURLDecodeStringSolidus09c() throws IOException {
        String result = doTestSolidus("xx%20xx%2f", EncodedSolidusHandling.DECODE);
        Assert.assertEquals("xx xx/", result);
    }


    @Test
    public void testURLDecodeStringSolidus10a() throws IOException {
        String result = doTestSolidus("xx%25xx", EncodedSolidusHandling.REJECT);
        Assert.assertEquals("xx%xx", result);
    }


    @Test
    public void testURLDecodeStringSolidus10b() throws IOException {
        String result = doTestSolidus("xx%25xx", EncodedSolidusHandling.PASS_THROUGH);
        Assert.assertEquals("xx%25xx", result);
    }


    @Test
    public void testURLDecodeStringSolidus10c() throws IOException {
        String result = doTestSolidus("xx%25xx", EncodedSolidusHandling.DECODE);
        Assert.assertEquals("xx%xx", result);
    }


    @Test(expected = CharConversionException.class)
    public void testURLDecodeStringSolidus11a() throws IOException {
        String result = doTestSolidus("xx%2f%25xx", EncodedSolidusHandling.REJECT);
        Assert.assertEquals("xx%xx", result);
    }


    @Test
    public void testURLDecodeStringSolidus11b() throws IOException {
        String result = doTestSolidus("xx%2f%25xx", EncodedSolidusHandling.PASS_THROUGH);
        Assert.assertEquals("xx%2f%25xx", result);
    }


    @Test
    public void testURLDecodeStringSolidus11c() throws IOException {
        String result = doTestSolidus("xx%2f%25xx", EncodedSolidusHandling.DECODE);
        Assert.assertEquals("xx/%xx", result);
    }


    private void doTestSolidus(String input, String expected) throws IOException {
        for (EncodedSolidusHandling solidusHandling : EncodedSolidusHandling.values()) {
            String result = doTestSolidus(input, solidusHandling);
            Assert.assertEquals(expected, result);
        }
    }


    private String doTestSolidus(String input, EncodedSolidusHandling solidusHandling) throws IOException {
        byte[] b = input.getBytes(StandardCharsets.UTF_8);
        ByteChunk bc = new ByteChunk(16);
        bc.setBytes(b, 0,  b.length);
        bc.setCharset(StandardCharsets.UTF_8);

        UDecoder udecoder = new UDecoder();
        udecoder.convert(bc, solidusHandling, EncodedSolidusHandling.DECODE);

        return bc.toString();
    }


    @Test
    public void testURLDecodeStringReverseSolidus01() throws IOException {
        doTestReverseSolidus("xxxxxx", "xxxxxx");
    }


    @Test
    public void testURLDecodeStringReverseSolidus02() throws IOException {
        doTestReverseSolidus("%20xxxx", " xxxx");
    }


    @Test
    public void testURLDecodeStringReverseSolidus03() throws IOException {
        doTestReverseSolidus("xx%20xx", "xx xx");
    }


    @Test
    public void testURLDecodeStringReverseSolidus04() throws IOException {
        doTestReverseSolidus("xxxx%20", "xxxx ");
    }


    @Test(expected = CharConversionException.class)
    public void testURLDecodeStringReverseSolidus05a() throws IOException {
        doTestReverseSolidus("%5cxxxx", EncodedSolidusHandling.REJECT);
    }


    @Test
    public void testURLDecodeStringReverseSolidus05b() throws IOException {
        String result = doTestReverseSolidus("%5cxxxx", EncodedSolidusHandling.PASS_THROUGH);
        Assert.assertEquals("%5cxxxx", result);
    }


    @Test
    public void testURLDecodeStringReverseSolidus05c() throws IOException {
        String result = doTestReverseSolidus("%5cxxxx", EncodedSolidusHandling.DECODE);
        Assert.assertEquals("\\xxxx", result);
    }


    @Test(expected = CharConversionException.class)
    public void testURLDecodeStringReverseSolidus06a() throws IOException {
        doTestReverseSolidus("%5cxx%20xx", EncodedSolidusHandling.REJECT);
    }


    @Test
    public void testURLDecodeStringReverseSolidus06b() throws IOException {
        String result = doTestReverseSolidus("%5cxx%20xx", EncodedSolidusHandling.PASS_THROUGH);
        Assert.assertEquals("%5cxx xx", result);
    }


    @Test
    public void testURLDecodeStringReverseSolidus06c() throws IOException {
        String result = doTestReverseSolidus("%5cxx%20xx", EncodedSolidusHandling.DECODE);
        Assert.assertEquals("\\xx xx", result);
    }


    @Test(expected = CharConversionException.class)
    public void testURLDecodeStringReverseSolidus07a() throws IOException {
        doTestReverseSolidus("xx%5c%20xx", EncodedSolidusHandling.REJECT);
    }


    @Test
    public void testURLDecodeStringReverseSolidus07b() throws IOException {
        String result = doTestReverseSolidus("xx%5c%20xx", EncodedSolidusHandling.PASS_THROUGH);
        Assert.assertEquals("xx%5c xx", result);
    }


    @Test
    public void testURLDecodeStringReverseSolidus07c() throws IOException {
        String result = doTestReverseSolidus("xx%5c%20xx", EncodedSolidusHandling.DECODE);
        Assert.assertEquals("xx\\ xx", result);
    }


    @Test(expected = CharConversionException.class)
    public void testURLDecodeStringReverseSolidus08a() throws IOException {
        doTestReverseSolidus("xx%20%5cxx", EncodedSolidusHandling.REJECT);
    }


    @Test
    public void testURLDecodeStringReverseSolidus08b() throws IOException {
        String result = doTestReverseSolidus("xx%20%5cxx", EncodedSolidusHandling.PASS_THROUGH);
        Assert.assertEquals("xx %5cxx", result);
    }


    @Test
    public void testURLDecodeStringReverseSolidus08c() throws IOException {
        String result = doTestReverseSolidus("xx%20%5cxx", EncodedSolidusHandling.DECODE);
        Assert.assertEquals("xx \\xx", result);
    }


    @Test(expected = CharConversionException.class)
    public void testURLDecodeStringReverseSolidus09a() throws IOException {
        doTestReverseSolidus("xx%20xx%5c", EncodedSolidusHandling.REJECT);
    }


    @Test
    public void testURLDecodeStringReverseSolidus09b() throws IOException {
        String result = doTestReverseSolidus("xx%20xx%5c", EncodedSolidusHandling.PASS_THROUGH);
        Assert.assertEquals("xx xx%5c", result);
    }


    @Test
    public void testURLDecodeStringReverseSolidus09c() throws IOException {
        String result = doTestReverseSolidus("xx%20xx%5c", EncodedSolidusHandling.DECODE);
        Assert.assertEquals("xx xx\\", result);
    }


    @Test
    public void testURLDecodeStringReverseSolidus10a() throws IOException {
        String result = doTestReverseSolidus("xx%25xx", EncodedSolidusHandling.REJECT);
        Assert.assertEquals("xx%xx", result);
    }


    @Test
    public void testURLDecodeStringReverseSolidus10b() throws IOException {
        String result = doTestReverseSolidus("xx%25xx", EncodedSolidusHandling.PASS_THROUGH);
        Assert.assertEquals("xx%25xx", result);
    }


    @Test
    public void testURLDecodeStringReverseSolidus10c() throws IOException {
        String result = doTestReverseSolidus("xx%25xx", EncodedSolidusHandling.DECODE);
        Assert.assertEquals("xx%xx", result);
    }


    @Test(expected = CharConversionException.class)
    public void testURLDecodeStringReverseSolidus11a() throws IOException {
        String result = doTestReverseSolidus("xx%5c%25xx", EncodedSolidusHandling.REJECT);
        Assert.assertEquals("xx%xx", result);
    }


    @Test
    public void testURLDecodeStringReverseSolidus11b() throws IOException {
        String result = doTestReverseSolidus("xx%5c%25xx", EncodedSolidusHandling.PASS_THROUGH);
        Assert.assertEquals("xx%5c%25xx", result);
    }


    @Test
    public void testURLDecodeStringReverseSolidus11c() throws IOException {
        String result = doTestReverseSolidus("xx%5c%25xx", EncodedSolidusHandling.DECODE);
        Assert.assertEquals("xx\\%xx", result);
    }


    private void doTestReverseSolidus(String input, String expected) throws IOException {
        for (EncodedSolidusHandling solidusHandling : EncodedSolidusHandling.values()) {
            String result = doTestReverseSolidus(input, solidusHandling);
            Assert.assertEquals(expected, result);
        }
    }


    private String doTestReverseSolidus(String input, EncodedSolidusHandling reverseSolidusHandling) throws IOException {
        byte[] b = input.getBytes(StandardCharsets.UTF_8);
        ByteChunk bc = new ByteChunk(16);
        bc.setBytes(b, 0,  b.length);
        bc.setCharset(StandardCharsets.UTF_8);

        UDecoder udecoder = new UDecoder();
        udecoder.convert(bc, EncodedSolidusHandling.REJECT, reverseSolidusHandling);

        return bc.toString();
    }
}
