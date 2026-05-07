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
package org.apache.catalina.tribes.util;

import java.security.SecureRandom;
import java.util.Random;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

/**
 * Smple generation of a UUID.
 */
public class UUIDGenerator {
    /**
     * Constructs a new UUIDGenerator.
     */
    public UUIDGenerator() {
    }

    private static final Log log = LogFactory.getLog(UUIDGenerator.class);
    /**
     * StringManager for internationalized strings.
     */
    protected static final StringManager sm = StringManager.getManager("org.apache.catalina.tribes.util");

    /**
     * Length of a UUID in bytes.
     */
    public static final int UUID_LENGTH = 16;
    /**
     * UUID version.
     */
    public static final int UUID_VERSION = 4;
    /**
     * Number of bytes per integer.
     */
    public static final int BYTES_PER_INT = 4;
    /**
     * Number of bits per byte.
     */
    public static final int BITS_PER_BYTE = 8;

    /**
     * Secure random number generator.
     */
    protected static final SecureRandom secrand;
    /**
     * Standard random number generator.
     */
    protected static final Random rand = new Random();

    static {
        long start = System.currentTimeMillis();
        secrand = new SecureRandom();
        // seed the generator
        secrand.nextInt();
        long time = System.currentTimeMillis() - start;
        if (time > 100) {
            log.info(sm.getString("uuidGenerator.createRandom", secrand.getAlgorithm(), Long.valueOf(time)));
        }
    }

    /**
     * Generates a random UUID.
     *
     * @param secure Whether to use a secure random generator
     *
     * @return The generated UUID as a byte array
     */
    public static byte[] randomUUID(boolean secure) {
        byte[] result = new byte[UUID_LENGTH];
        return randomUUID(secure, result, 0);
    }

    /**
     * Generates a random UUID and stores it in the provided byte array.
     *
     * @param secure Whether to use a secure random generator
     * @param into   The byte array to store the UUID
     * @param offset The offset in the byte array
     *
     * @return The byte array containing the UUID
     *
     * @throws ArrayIndexOutOfBoundsException If the byte array is too small
     */
    public static byte[] randomUUID(boolean secure, byte[] into, int offset) {
        if ((offset + UUID_LENGTH) > into.length) {
            throw new ArrayIndexOutOfBoundsException(
                    sm.getString("uuidGenerator.unable.fit", Integer.toString(UUID_LENGTH),
                            Integer.toString(into.length), Integer.toString(offset + UUID_LENGTH)));
        }
        Random r = (secure && (secrand != null)) ? secrand : rand;
        nextBytes(into, offset, UUID_LENGTH, r);
        into[6 + offset] &= 0x0F;
        into[6 + offset] |= (UUID_VERSION << 4);
        into[8 + offset] &= 0x3F; // 0011 1111
        into[8 + offset] |= (byte) 0x80; // 1000 0000
        return into;
    }

    /**
     * Same as java.util.Random.nextBytes except this one we don't have to allocate a new byte array
     *
     * @param into   byte[]
     * @param offset int
     * @param length int
     * @param r      Random
     */
    public static void nextBytes(byte[] into, int offset, int length, Random r) {
        int numGot = 0, rnd = 0;
        while (true) {
            for (int i = 0; i < BYTES_PER_INT; i++) {
                if (numGot == length) {
                    return;
                }
                rnd = (i == 0 ? r.nextInt() : rnd >> BITS_PER_BYTE);
                into[offset + numGot] = (byte) rnd;
                numGot++;
            }
        }
    }

}
