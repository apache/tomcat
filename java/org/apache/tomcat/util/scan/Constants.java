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
 * String constants for the scan package.
 */
public final class Constants {

    /**
     * Package name for scan utilities.
     */
    public static final String Package = "org.apache.tomcat.util.scan";

    /* System properties */
    /**
     * System property for JARs to skip during scanning.
     */
    public static final String SKIP_JARS_PROPERTY = "tomcat.util.scan.StandardJarScanFilter.jarsToSkip";
    /**
     * System property for JARs to scan explicitly.
     */
    public static final String SCAN_JARS_PROPERTY = "tomcat.util.scan.StandardJarScanFilter.jarsToScan";

    /* Commons strings */
    /**
     * JAR file extension.
     */
    public static final String JAR_EXT = ".jar";
    /**
     * Standard web application library directory path.
     */
    public static final String WEB_INF_LIB = "/WEB-INF/lib/";
    /**
     * Standard web application compiled classes directory path.
     */
    public static final String WEB_INF_CLASSES = "/WEB-INF/classes";

    /**
     * Private constructor to prevent instantiation.
     */
    private Constants() {
        // Hide default constructor
    }
}
