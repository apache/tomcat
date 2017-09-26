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

import java.util.regex.Pattern;

import org.junit.Test;

public class TestAbstractFileResourceSetPerformance {

    private static final Pattern UNSAFE_WINDOWS_FILENAME_PATTERN = Pattern.compile(" $|[\"<>]");

    private static final int LOOPS = 10_000_000;

    /*
     * Checking individual characters is about 10 times faster on markt's dev
     * PC for typical length file names. The file names need to get to ~65
     * characters before the Pattern matching is faster.
     */
    @Test
    public void testFileNameFiltering() {

        long start = System.nanoTime();
        for (int i = 0; i < LOOPS; i++) {
            UNSAFE_WINDOWS_FILENAME_PATTERN.matcher("testfile.jsp ").find();
        }
        long end = System.nanoTime();
        System.out.println("Regular expression took " + (end - start) + "ns or " +
                (end-start)/LOOPS + "ns per iteration");

        start = System.nanoTime();
        for (int i = 0; i < LOOPS; i++) {
            checkForBadCharsArray("testfile.jsp ");
        }
        end = System.nanoTime();
        System.out.println("char[] check took " + (end - start) + "ns or " +
                (end-start)/LOOPS + "ns per iteration");

        start = System.nanoTime();
        for (int i = 0; i < LOOPS; i++) {
            checkForBadCharsAt("testfile.jsp ");
        }
        end = System.nanoTime();
        System.out.println("charAt() check took " + (end - start) + "ns or " +
                (end-start)/LOOPS + "ns per iteration");

    }

    private boolean checkForBadCharsArray(String filename) {
        char[] chars = filename.toCharArray();
        for (char c : chars) {
            if (c == '\"' || c == '<' || c == '>') {
                return false;
            }
        }
        if (chars[chars.length -1] == ' ') {
            return false;
        }
        return true;
    }


    private boolean checkForBadCharsAt(String filename) {
        final int len = filename.length();
        for (int i = 0; i < len; i++) {
            char c = filename.charAt(i);
            if (c == '\"' || c == '<' || c == '>') {
                return false;
            }
        }
        if (filename.charAt(len - 1) == ' ') {
            return false;
        }
        return true;
    }
}
