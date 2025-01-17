/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.el.util;

public class Validation {

    /*
     * Java keywords, boolean literals & the null literal in alphabetical order. As per the Java Language Specification,
     * none of these are permitted to be used as an identifier.
     */
    private static final String invalidIdentifiers[] = { "_", "abstract", "assert", "boolean", "break", "byte", "case",
            "catch", "char", "class", "const", "continue", "default", "do", "double", "else", "enum", "extends",
            "false", "final", "finally", "float", "for", "goto", "if", "implements", "import", "instanceof", "int",
            "interface", "long", "native", "new", "null", "package", "private", "protected", "public", "return",
            "short", "static", "strictfp", "super", "switch", "synchronized", "this", "throw", "throws", "transient",
            "true", "try", "void", "volatile", "while"};

    private static final boolean SKIP_IDENTIFIER_CHECK =
            Boolean.getBoolean("org.apache.el.parser.SKIP_IDENTIFIER_CHECK");


    private Validation() {
        // Utility class. Hide default constructor
    }

    /**
     * Test whether a string is a Java identifier. Note that the behaviour of this method depend on the system property
     * {@code org.apache.el.parser.SKIP_IDENTIFIER_CHECK}
     *
     * @param key The string to test
     *
     * @return {@code true} if the provided String should be treated as a Java identifier, otherwise false
     */
    public static boolean isIdentifier(String key) {

        if (SKIP_IDENTIFIER_CHECK) {
            return true;
        }

        // Should not be the case but check to be sure
        if (key == null || key.length() == 0) {
            return false;
        }

        // Check the list of known invalid values
        int i = 0;
        int j = invalidIdentifiers.length;
        while (i < j) {
            int k = (i + j) >>> 1; // Avoid overflow
            int result = invalidIdentifiers[k].compareTo(key);
            if (result == 0) {
                return false;
            }
            if (result < 0) {
                i = k + 1;
            } else {
                j = k;
            }
        }

        /*
         * The parser checks Character.isJavaIdentifierStart() and Character.isJavaIdentifierPart() so no need to check
         * them again here. However, we do need to check that '#' hasn't been used at the start of the identifier.
         */
        if (key.charAt(0) == '#') {
            return false;
        }

        return true;
    }
}
