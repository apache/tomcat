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
package org.apache.el.parser;

import org.junit.Test;

/*
 * The purpose of this class is to generate the ranges used in the JavaCC grammar for EL parsing.
 *
 * The ranges for Tomcat 12 were generated with Java 21.
 *
 * The generated ranges are unchanged from Java 21 to Java 23.
 *
 * The generated ranges change in Java 24 (somewhere between EA 22 and EA 31).
 */
public class TesterGenerateIdentifierRanges {

    /*
     * Java Letter is all characters where Character.isJavaIdentifierStart() returns true.
     */
    @Test
    public void testGenerateJavaLetterRanges() {
        int start = 0;
        int end = 0;
        boolean inRange = false;

        for (int i = 0 ; i < 0xFFFF; i++) {
            if (Character.isJavaIdentifierStart(i)) {
                if (!inRange) {
                    inRange = true;
                    start = i;
                }
            } else {
                if (inRange) {
                    end = i - 1;
                    inRange = false;
                    System.out.print("        \"" + asUnicodeEscape(start) + "\"");
                    if (start == end) {
                        System.out.println(",");
                    } else {
                        System.out.println("-\"" + asUnicodeEscape(end) + "\",");
                    }
                }
            }
        }
    }


    /*
     * Java Digit is all characters where Character.isJavaIdentifierPart(0 returns true that aren't included in Java
     * Letter.
     */
    @Test
    public void testJavaDigitRanges() {
        int start = 0;
        int end = 0;
        boolean inRange = false;

        for (int i = 0 ; i < 0xFFFF; i++) {
            if (Character.isJavaIdentifierPart(i) && !Character.isJavaIdentifierStart(i)) {
                if (!inRange) {
                    inRange = true;
                    start = i;
                }
            } else {
                if (inRange) {
                    end = i - 1;
                    inRange = false;
                    System.out.print("        \"" + asUnicodeEscape(start) + "\"");
                    if (start == end) {
                        System.out.println(",");
                    } else {
                        System.out.println("-\"" + asUnicodeEscape(end) + "\",");
                    }
                }
            }
        }
    }



    private static String asUnicodeEscape(int input) {
        return String.format("\\u%04x", Integer.valueOf(input));
    }
}
