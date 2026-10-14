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

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Random;

import org.junit.Assert;
import org.junit.Test;

/**
 * Test cases for {@link Asn1Writer}.
 */
public class TestAsn1Writer {

    @Test
    public void testWriteInteger0() {
        // 02 01 00
        assertIntegerEncoding(0, new byte[] { 0x02, 0x01, 0x00 });
    }


    @Test
    public void testWriteIntegerSingleByte() {
        // 127 -> 02 01 7F
        assertIntegerEncoding(127, new byte[] { 0x02, 0x01, 0x7F });
    }


    @Test
    public void testWriteIntegerHighBitSetNeedsPadding() {
        // 128 -> 02 02 00 80 (a leading zero byte is needed so the value is
        // not decoded as -128)
        assertIntegerEncoding(128, new byte[] { 0x02, 0x02, 0x00, (byte) 0x80 });
    }


    @Test
    public void testWriteIntegerTwoBytes() {
        // 0x1234 -> 02 02 12 34
        assertIntegerEncoding(0x1234, new byte[] { 0x02, 0x02, 0x12, 0x34 });
    }


    @Test
    public void testWriteInteger256() {
        // 256 -> 02 02 01 00
        assertIntegerEncoding(256, new byte[] { 0x02, 0x02, 0x01, 0x00 });
    }


    @Test
    public void testWriteIntegerThreeBytes() {
        // 0x123456 -> 02 03 12 34 56
        assertIntegerEncoding(0x123456, new byte[] { 0x02, 0x03, 0x12, 0x34, 0x56 });
    }


    @Test
    public void testWriteIntegerFourBytes() {
        // Integer.MAX_VALUE -> 02 04 7F FF FF FF
        assertIntegerEncoding(Integer.MAX_VALUE, new byte[] { 0x02, 0x04, 0x7F, (byte) 0xFF, (byte) 0xFF,
                (byte) 0xFF });
    }


    @Test
    public void testWriteIntegerRoundTrip() {
        // Every value that encodes in one or two bytes
        for (int i = 0; i <= 0xFFFF; i++) {
            assertIntegerRoundTrip(i);
        }
        // Boundary values around the three and four byte transitions
        assertIntegerRoundTrip(0x10000);
        assertIntegerRoundTrip(0xFFFFFF);
        assertIntegerRoundTrip(0x1000000);
        assertIntegerRoundTrip(0x7FFFFFFF);
        // Random values (fixed seed to keep the test reproducible)
        Random random = new Random(20261014);
        for (int i = 0; i < 1000; i++) {
            assertIntegerRoundTrip(random.nextInt());
        }
    }


    @Test
    public void testWriteIntegerNegative() {
        try {
            Asn1Writer.writeInteger(-1);
            Assert.fail("Expected IllegalArgumentException for a negative value");
        } catch (IllegalArgumentException e) {
            // Expected
        }
        try {
            Asn1Writer.writeInteger(Integer.MIN_VALUE);
            Assert.fail("Expected IllegalArgumentException for a negative value");
        } catch (IllegalArgumentException e) {
            // Expected
        }
    }


    // Regression test for an infinite loop in the length size calculation:
    // the shift distance is evaluated modulo 32, so a data length of 16 MiB
    // or more made the loop run forever.
    @Test
    public void testWriteOctetStringSixteenMegabytes() {
        byte[] data = new byte[0x1000000];
        byte[] encoded = Asn1Writer.writeOctetString(data);
        // 04 84 01 00 00 00 - 4 length bytes for 0x1000000
        Assert.assertEquals(0x04, encoded[0] & 0xFF);
        Assert.assertEquals(0x84, encoded[1] & 0xFF);
        Assert.assertArrayEquals(new byte[] { 0x01, 0x00, 0x00, 0x00 },
                Arrays.copyOfRange(encoded, 2, 6));
        Assert.assertEquals(6 + data.length, encoded.length);
    }


    private static void assertIntegerEncoding(int value, byte[] expected) {
        Assert.assertArrayEquals("Unexpected encoding for " + value, expected, Asn1Writer.writeInteger(value));
    }


    private static void assertIntegerRoundTrip(int value) {
        // Random.nextInt() can produce negative values which must be rejected
        if (value < 0) {
            try {
                Asn1Writer.writeInteger(value);
                Assert.fail("Expected IllegalArgumentException for " + value);
            } catch (IllegalArgumentException e) {
                return;
            }
        }
        byte[] encoded = Asn1Writer.writeInteger(value);
        // The header is always two bytes for these short content lengths.
        // BigInteger.toByteArray() produces the canonical minimal DER content
        // octets for a non-negative value, so compare against that to also
        // verify the encoding is minimal.
        Assert.assertArrayEquals("Non-minimal encoding for " + value,
                BigInteger.valueOf(value).toByteArray(), Arrays.copyOfRange(encoded, 2, encoded.length));
        BigInteger decoded = new Asn1Parser(encoded).parseInt();
        Assert.assertEquals("Round-trip failed for " + value, BigInteger.valueOf(value), decoded);
    }
}
