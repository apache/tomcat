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

import org.junit.Assert;
import org.junit.Test;

import org.apache.tomcat.util.res.StringManager;

/**
 * Test cases for {@link Asn1Parser}.
 */
public class TestAsn1Parser {

    private static final StringManager sm = StringManager.getManager(Asn1Parser.class);

    private static final int MAX_LENGTH_OCTETS = 126;


    /*
     * Note: parseLength() reads the length field directly, so the source arrays below start with the length identifier
     * octet rather than with a tag. The values used where a message is checked are kept below 1000 because the
     * MessageFormat used by StringManager applies locale specific grouping separators to numbers.
     */

    @Test
    public void testShortFormLength() {
        assertLength(new byte[] { 0x05, 1, 2, 3, 4, 5 }, 5);
    }


    @Test
    public void testShortFormMaxLength() {
        // The short form covers lengths up to 127
        byte[] source = new byte[1 + 127];
        source[0] = 0x7F;
        assertLength(source, 127);
    }


    @Test
    public void testLongFormLength() {
        assertLength(new byte[] { (byte) 0x81, 0x05, 1, 2, 3, 4, 5 }, 5);
    }


    @Test
    public void testLongFormZeroLength() {
        // A non-minimal zero length is accepted: BER permits more octets than needed
        assertLength(new byte[] { (byte) 0x82, 0x00, 0x00 }, 0);
    }


    @Test
    public void testNonMinimalLengthMoreThanFourOctets() {
        // 0x85 with a five octet length for the value 10. The limit of four length
        // octets that this parser previously enforced rejected this encoding, but it
        // is valid BER.
        assertLength(new byte[] { (byte) 0x85, 0x00, 0x00, 0x00, 0x00, 0x0A, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 }, 10);
    }


    @Test
    public void testMaxPermittedLengthOctets() {
        // 0xFE requests 126 subsequent octets, the maximum X.690 8.1.3.5 c) permits
        byte[] source = new byte[1 + MAX_LENGTH_OCTETS];
        source[0] = (byte) 0xFE;
        assertLength(source, 0);
    }


    @Test
    public void testReservedLengthOctetCount() {
        // X.690 8.1.3.5 c) prohibits the identifier value 0xFF, which would request
        // 127 subsequent octets
        byte[] source = new byte[1 + 127];
        source[0] = (byte) 0xFF;
        assertLengthRejected(source, "asn1Parser.lengthOctetsInvalid", Integer.valueOf(127),
                Integer.valueOf(MAX_LENGTH_OCTETS));
    }


    @Test
    public void testIndefiniteLengthRejected() {
        // X.690 8.1.3.6.1 defines 0x80 as the indefinite length form, which DER does
        // not permit. Treating it as a length of zero, as this parser previously did,
        // silently consumes the identifier and desynchronises the parse.
        assertLengthRejected(new byte[] { (byte) 0x80 }, "asn1Parser.indefiniteLength");
    }


    @Test
    public void testShortFormExceedsRemainingData() {
        // The decoded length, not a placeholder, must be reported
        assertLengthRejected(new byte[] { 0x0A, 1, 2, 3 }, "asn1Parser.lengthInvalid", Integer.valueOf(10),
                Integer.valueOf(3));
    }


    @Test
    public void testLongFormExceedsRemainingData() {
        assertLengthRejected(new byte[] { (byte) 0x82, 0x00, (byte) 0xC8, 1, 2, 3 }, "asn1Parser.lengthInvalid",
                Integer.valueOf(200), Integer.valueOf(3));
    }


    @Test
    public void testLengthExactlyMaximumValue() {
        // Integer.MAX_VALUE is representable, so this fails on the remaining data
        // rather than as an unsupported length
        byte[] source = new byte[] { (byte) 0x84, 0x7F, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF };
        assertLengthRejected(source, "asn1Parser.lengthInvalid", Integer.valueOf(Integer.MAX_VALUE),
                Integer.valueOf(0));
    }


    @Test
    public void testLengthExceedingMaximumValue() {
        byte[] source = new byte[] { (byte) 0x84, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF };
        assertLengthRejected(source, "asn1Parser.lengthUnsupported", new BigInteger("4294967295"),
                Integer.valueOf(Integer.MAX_VALUE));
    }


    @Test
    public void testLengthOctetsTruncated() {
        // The length identifier requests four octets but the input provides one. The
        // input is truncated rather than the length invalid.
        assertLengthRejected(new byte[] { (byte) 0x84, 0x00 }, "asn1Parser.truncatedData", Integer.valueOf(1),
                Integer.valueOf(0));
    }


    @Test
    public void testFullLengthReportsActualLength() {
        byte[] source = new byte[] { 0x0A, 1, 2, 3 };
        try {
            new Asn1Parser(source).parseFullLength();
            Assert.fail("Expected an IllegalArgumentException for " + Arrays.toString(source));
        } catch (IllegalArgumentException e) {
            Assert.assertEquals(sm.getString("asn1Parser.lengthInvalid", Integer.valueOf(10), Integer.valueOf(3)),
                    e.getMessage());
        }
    }


    private static void assertLength(byte[] source, int expected) {
        Assert.assertEquals("Unexpected length for " + Arrays.toString(source), expected,
                new Asn1Parser(source).parseLength());
    }


    private static void assertLengthRejected(byte[] source, String key, Object... args) {
        try {
            new Asn1Parser(source).parseLength();
            Assert.fail("Expected an IllegalArgumentException for " + Arrays.toString(source));
        } catch (IllegalArgumentException e) {
            Assert.assertEquals("Unexpected message for " + Arrays.toString(source), sm.getString(key, args),
                    e.getMessage());
        }
    }
}
