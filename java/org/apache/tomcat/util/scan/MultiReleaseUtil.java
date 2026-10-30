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
package org.apache.tomcat.util.scan;

/**
 * Utility class for working with multi-release JAR files.
 */
public class MultiReleaseUtil {

    private static final String VERSION_PATH = "META-INF/versions/";
    private static final int VERSION_PATH_LENGTH = VERSION_PATH.length();
    private static final int MINIMUM_VERSIONED_JAVA = 8;


    private MultiReleaseUtil() {
        // Hide default constructor
    }


    /**
     * Parses a JAR entry name and, if it represents a multi-release JAR entry, returns the target Java version and the
     * start point of the resource name in the JAR entry name. Version numbers with leading zeros, signs or values less
     * than 8 are ignored to match the JRE 21 onwards behaviour.
     *
     * @param name Name of the JAR entry
     *
     * @return The {@code MultiVersionInfo} for the entry if the entry represents a versioned entry, otherwise
     *             {@code null}
     */
    public static MultiVersionInfo parseVersion(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        int start = 0;
        if (name.charAt(0) == '/') {
            start = 1;
        }
        if (name.startsWith(VERSION_PATH, start)) {
            int end = name.indexOf('/', VERSION_PATH_LENGTH + start);
            if (end > 0) {
                for (int i = VERSION_PATH_LENGTH + start; i < end; i++) {
                    char c = name.charAt(i);
                    if (c < '0' || c > '9' || ((i == VERSION_PATH_LENGTH + start) && c == '0')) {
                        return new MultiVersionInfo(-1, end + 1);
                    }
                }
                try {
                    int version = Integer.parseInt(name, VERSION_PATH_LENGTH + start, end, 10);
                    if (version < MINIMUM_VERSIONED_JAVA) {
                        version = -1;
                    }
                    return new MultiVersionInfo(version, end + 1);
                } catch (NumberFormatException e) {
                    // Must be overflow
                    return new MultiVersionInfo(-1, end + 1);
                }
            }
            return new MultiVersionInfo(-1, -1);
        }

        return null;
    }


    /**
     * Provides the return value for calls to {@code MultiVersionUtil#parseVersion(String)}.
     */
    public static class MultiVersionInfo {

        public final int version;
        public final int namePos;

        /**
         * Create a new result.
         *
         * @param version The version or {@code -1} if the version was invalid
         * @param namePos The start position of the name. The first character after the '/' after the version.
         *                    {@code -1} if there was no '/' after the version or the version was not present
         */
        public MultiVersionInfo(int version, int namePos) {
            this.version = version;
            this.namePos = namePos;
        }

        /**
         * Has a valid multi-release entry been found?
         *
         * @return {@code true} if a valid multi-release entry was found, otherwise {@code false}
         */
        public boolean isValid() {
            return version >= 0;
        }
    }
}
