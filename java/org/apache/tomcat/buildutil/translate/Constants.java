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
package org.apache.tomcat.buildutil.translate;

/**
 * Constants for the translation build utility.
 */
public class Constants {

    /**
     * Prefix for localization string files.
     */
    public static final String L10N_PREFIX = "LocalStrings";
    /**
     * Suffix for localization properties files.
     */
    public static final String L10N_SUFFIX = ".properties";

    /**
     * Directory for storing translation settings.
     */
    public static final String STORAGE_DIR = ".settings/translations";

    /**
     * Marker to indicate the end of a package.
     */
    public static final String END_PACKAGE_MARKER = ".zzz.";

    /**
     * Private constructor to prevent instantiation.
     */
    private Constants() {
        // Hide default constructor
    }
}
