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
package org.apache.catalina.webresources;

import java.util.BitSet;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * This class represents the contents of a jar by determining whether a given
 * resource <b>might</b> be in the cache, based on a bloom filter. This is not a
 * general-purpose bloom filter because it contains logic to strip out
 * characters from the beginning of the key.
 *
 * The hash methods are simple but good enough for this purpose.
 */
public final class JarContents {
    private final BitSet bits1;
    private final BitSet bits2;
    /**
     * Constant used by a typical hashing method.
     */
    private static final int HASH_PRIME_1 = 31;

    /**
     * Constant used by a typical hashing method.
     */
    private static final int HASH_PRIME_2 = 17;

    /**
     * Size of the fixed-length bit table. Larger reduces false positives,
     * smaller saves memory.
     */
    private static final int TABLE_SIZE = 2048;

    /**
     * Parses the passed-in jar and populates the bit array.
     *
     * @param jar the JAR file
     */
    public JarContents(JarFile jar) {
        Enumeration<JarEntry> entries = jar.entries();
        bits1 = new BitSet(TABLE_SIZE);
        bits2 = new BitSet(TABLE_SIZE);

        // Enumerations. When will they update this API?!
        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            String name = entry.getName();
            int startPos = 0;

            // If the path starts with a slash, that's not useful information.
            // Skipping it increases the significance of our key by
            // removing an insignificant character.
            boolean precedingSlash = name.charAt(0) == '/';
            if (precedingSlash) {
                startPos = 1;
            }

            // Find the correct table slot
            int pathHash1 = hashcode(name, startPos, HASH_PRIME_1);
            int pathHash2 = hashcode(name, startPos, HASH_PRIME_2);

            bits1.set(pathHash1 % TABLE_SIZE);
            bits2.set(pathHash2 % TABLE_SIZE);
        }
    }

    /**
     * Simple hashcode of a portion of the string. Typically we would use
     * substring, but memory and runtime speed are critical.
     *
     * @param content
     *            Wrapping String.
     * @param startPos
     *            First character in the range.
     * @return hashcode of the range.
     */
    private int hashcode(String content, int startPos, int hashPrime) {
        int h = hashPrime/2;
        int contentLength = content.length();
        for (int i = startPos; i < contentLength; i++) {
            h = hashPrime * h + content.charAt(i);
        }

        if (h < 0) {
            h = h * -1;
        }
        return h;
    }


    /**
     * Method that identifies whether a given path <b>MIGHT</b> be in this jar.
     * Uses the Bloom filter mechanism.
     *
     * @param path
     *            Requested path. Sometimes starts with "/WEB-INF/classes".
     * @param webappRoot
     *            The value of the webapp location, which can be stripped from
     *            the path. Typically is "/WEB-INF/classes".
     * @return Whether the prefix of the path is known to be in this jar.
     */
    public final boolean mightContainResource(String path, String webappRoot) {
        int startPos = 0;
        if (path.startsWith(webappRoot)) {
            startPos = webappRoot.length();
        }

        if (path.charAt(startPos) == '/') {
            // ignore leading slash
            startPos++;
        }

        // calculate the hash lazyly and return a boolean value for this path
        return (bits1.get(hashcode(path, startPos, HASH_PRIME_1) % TABLE_SIZE) &&
                bits2.get(hashcode(path, startPos, HASH_PRIME_2) % TABLE_SIZE));
    }

}