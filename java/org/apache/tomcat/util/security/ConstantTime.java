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
package org.apache.tomcat.util.security;

import java.security.MessageDigest;

import org.apache.tomcat.util.buf.ByteChunk;

/**
 * Utility class for methods that, for security reasons, need to run in - as far as practical - constant time.
 */
public class ConstantTime {

    private ConstantTime() {
        // Hide default constructor for this utility class
    }


    /**
     * Implements String equality which always compares all characters in the string, without stopping early if any
     * characters do not match.
     * <p>
     * <i>Note:</i> This implementation was adapted from {@link MessageDigest#isEqual} which we assume is as
     * optimizer-defeating as possible.
     *
     * @param s1         The first string to compare.
     * @param s2         The second string to compare.
     * @param ignoreCase <code>true</code> if the strings should be compared without regard to case. Note that "true"
     *                       here is only guaranteed to work with plain ASCII characters.
     *
     * @return <code>true</code> if the strings are equal to each other, <code>false</code> otherwise.
     */
    public static boolean equals(final String s1, final String s2, final boolean ignoreCase) {
        if (s1 == s2) {
            return true;
        }
        if (s1 == null || s2 == null) {
            return false;
        }

        final int len1 = s1.length();
        final int len2 = s2.length();

        if (len2 == 0) {
            return len1 == 0;
        }

        int result = 0;
        result |= len1 - len2;

        // time-constant comparison
        for (int i = 0; i < len1; i++) {
            // If i >= len2, index2 is 0; otherwise, i.
            final int index2 = ((i - len2) >>> 31) * i;
            char c1 = s1.charAt(i);
            char c2 = s2.charAt(index2);
            if (ignoreCase) {
                c1 = Character.toLowerCase(c1);
                c2 = Character.toLowerCase(c2);
            }
            result |= c1 ^ c2;
        }
        return result == 0;
    }


    /**
     * Implements ByteChunk / String equality which always compares all characters, without stopping early if any
     * characters do not match.
     * <p>
     * <i>Note:</i> This implementation was adapted from {@link MessageDigest#isEqual} which we assume is as
     * optimizer-defeating as possible.
     *
     * @param bc         The ByteChunk to compare.
     * @param s          The string to compare.
     *
     * @return <code>true</code> if the strings are equal to each other, <code>false</code> otherwise.
     */
    public static boolean equals(final ByteChunk bc, final String s) {
        if (bc == null && s == null) {
            return true;
        }
        if (bc == null || s == null) {
            return false;
        }

        final int len1 = bc.getLength();
        final int len2 = s.length();

        byte[] bytes = bc.getBytes();

        if (len2 == 0) {
            return len1 == 0;
        }

        int result = 0;
        result |= len1 - len2;

        // time-constant comparison
        for (int i = 0; i < len1; i++) {
            // If i >= len2, index2 is 0; otherwise, i.
            final int index2 = ((i - len2) >>> 31) * i;
            byte b = bytes[bc.getStart() + i];
            char c = s.charAt(index2);
            result |= (b & 0xFF) ^ c;
        }
        return result == 0;
    }


    /**
     * Implements byte-array equality which always compares all bytes in the array, without stopping early if any bytes
     * do not match.
     * <p>
     * <i>Note:</i> Implementation note: this method delegates to {@link MessageDigest#isEqual} under the assumption
     * that it provides a constant-time comparison of the bytes in the arrays. Java 7+ has such an implementation, but
     * neither the Javadoc nor any specification requires it. Therefore, Tomcat should continue to use <i>this</i>
     * method internally in case the JDK implementation changes so this method can be re-implemented properly.
     *
     * @param b1 The first array to compare.
     * @param b2 The second array to compare.
     *
     * @return <code>true</code> if the arrays are equal to each other, <code>false</code> otherwise.
     */
    public static boolean equals(final byte[] b1, final byte[] b2) {
        return MessageDigest.isEqual(b1, b2);
    }
}
