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

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.junit.Assert;
import org.junit.Test;

public class TestMessageBytes {

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
     * Checks the the optimized code is at least twice as fast as the
     * non-optimized code.
     */
    @Test
    public void testConversionPerformance() {
        long optimized = -1;
        long nonOptimized = -1;

        /*
         * One loop is likely to be enough as the optimised code is
         * significantly (3x to 4x on markt's desktop) faster than the
         * non-optimised code. Loop three times allows once to warn up the JVM
         * once to run the test and once more in case of unexpected CI /GC
         * slowness. The test will exit early if possible.
         */
        for (int i = 0; i < 3; i++) {
            optimized = doTestConversionPerformance(StandardCharsets.ISO_8859_1);
            // US_ASCII chosen as the conversion is the same and it is another
            // Charset available on all platforms.
            nonOptimized = doTestConversionPerformance(StandardCharsets.US_ASCII);

            System.out.println(optimized + " " + nonOptimized);
            if (optimized * 2 < nonOptimized) {
                break;
            }
        }

        Assert.assertTrue("Non-optimised code was faster (" + nonOptimized + "ns) compared to optimized (" + optimized + "ns)", optimized < nonOptimized);
    }


    private long doTestConversionPerformance(Charset charset) {
        MessageBytes mb = MessageBytes.newInstance();

        int loops = 1000000;

        long start = System.nanoTime();
        for (int i = 0; i < loops; i++) {
            mb.recycle();
            mb.setCharset(charset);
            mb.setString("0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF" +
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
                    "0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF");
            mb.toBytes();
        }
        return System.nanoTime() - start;
    }
}
