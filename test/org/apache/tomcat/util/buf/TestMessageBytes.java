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
package org.apache.tomcat.util.buf;

import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import org.apache.tomcat.util.compat.JreCompat;

public class TestMessageBytes {

    private static final String CONVERSION_STRING =
            "0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF" +
            "0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF" +
            "0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF" +
            "0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF" +
            "0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF" +
            "0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF" +
            "0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF" +
            "0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF" +
            "0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF" +
            "0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF" +
            "0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF" +
            "0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF" +
            "0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF" +
            "0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF" +
            "0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF" +
            "0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF";

    private static final int CONVERSION_LOOPS = 1000000;

    @Test
    public void testToStringFromNull() {
        MessageBytes mb = MessageBytes.newInstance();
        mb.toString();
    }


    @Test
    public void testToBytesFromNull() {
        MessageBytes mb = MessageBytes.newInstance();
        mb.toBytes();
    }


    @Test
    public void testToCharsFromNull() {
        MessageBytes mb = MessageBytes.newInstance();
        mb.toChars();
    }


    @Test
    public void testToStringAfterRecycle() {
        MessageBytes mb = MessageBytes.newInstance();
        mb.setString("foo");
        mb.recycle();
        mb.toString();
    }


    @Test
    public void testToBytesAfterRecycle() {
        MessageBytes mb = MessageBytes.newInstance();
        mb.setString("foo");
        mb.recycle();
        mb.toBytes();
    }


    @Test
    public void testToCharsAfterRecycle() {
        MessageBytes mb = MessageBytes.newInstance();
        mb.setString("foo");
        mb.recycle();
        mb.toChars();
    }


    /*
     * Checks the the optimized code is faster than the non-optimized code.
     */
    @Test
    public void testConversionPerformance() {

        // ISO_8859_1 conversion appears to be optimised in Java 16 onwards
        Assume.assumeFalse(JreCompat.isJre16Available());

        long optimized = -1;
        long nonOptimized = -1;

        /*
         * One loop is likely to be enough as the optimised code is
         * significantly (3x to 4x on markt's desktop) faster than the
         * non-optimised code. Loop three times allows once to warn up the JVM
         * once to run the test and once more in case of unexpected CI /GC
         * slowness. The test will exit early if possible.
         *
         * MessageBytes only optimises conversion for ISO_8859_1
         */
        for (int i = 0; i < 3; i++) {
            optimized = doTestOptimisedConversionPerformance();
            nonOptimized = doTestConversionPerformance();

            System.out.println(optimized + " " + nonOptimized);
            if (optimized * 2 < nonOptimized) {
                break;
            }
        }

        Assert.assertTrue("Non-optimised code was faster (" + nonOptimized + "ns) compared to optimized (" +
                optimized + "ns)", optimized < nonOptimized);
    }


    private long doTestOptimisedConversionPerformance() {
        MessageBytes mb = MessageBytes.newInstance();

        long start = System.nanoTime();
        for (int i = 0; i < CONVERSION_LOOPS; i++) {
            mb.recycle();
            mb.setCharset(StandardCharsets.ISO_8859_1);
            mb.setString(CONVERSION_STRING);
            mb.toBytes();
        }
        return System.nanoTime() - start;
    }


    private long doTestConversionPerformance() {
        long start = System.nanoTime();
        for (int i = 0; i < CONVERSION_LOOPS; i++) {
            CharsetEncoder encoder = StandardCharsets.ISO_8859_1.newEncoder().onMalformedInput(
                    CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT);
            try {
                encoder.encode(CharBuffer.wrap(CONVERSION_STRING));
            } catch (CharacterCodingException cce) {
                Assert.fail();
            }
        }
        return System.nanoTime() - start;
    }
}
