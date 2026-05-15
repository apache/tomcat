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
package org.apache.tomcat.util.net.openssl;

/**
 * Holds OpenSSL status without the need to load other classes.
 */
public class OpenSSLStatus {

    /**
     * Constructs an OpenSSLStatus.
     */
    public OpenSSLStatus() {
    }

    /**
     * OpenSSL library variant that has been identified.
     */
    public enum Name {
        /** OpenSSL. */
        OPENSSL,
        /** OpenSSL 3.x. */
        OPENSSL3,
        /** LibreSSL. */
        LIBRESSL,
        /** BoringSSL. */
        BORINGSSL,
        /** Unknown variant. */
        UNKNOWN
    }

    /**
     * OpenSSL status fields: libraryInitialized, initialized, available, useOpenSSL, instanceCreated,
     * version, majorVersion, minorVersion, name.
     */
    private static volatile boolean libraryInitialized = false;
    private static volatile boolean initialized = false;
    private static volatile boolean available = false;
    private static volatile boolean useOpenSSL = true;
    private static volatile boolean instanceCreated = false;
    private static volatile long version = 0;
    private static volatile int majorVersion = 0;
    private static volatile int minorVersion = 0;
    private static volatile Name name = Name.UNKNOWN;

    /**
     * Checks if the OpenSSL library has been initialized.
     *
     * @return true if the library is initialized
     */
    public static boolean isLibraryInitialized() {
        return libraryInitialized;
    }

    /**
     * Checks if OpenSSL has been initialized.
     *
     * @return true if OpenSSL is initialized
     */
    public static boolean isInitialized() {
        return initialized;
    }

    /**
     * Checks if OpenSSL is available.
     *
     * @return true if OpenSSL is available
     */
    public static boolean isAvailable() {
        return available;
    }

    /**
     * Checks if OpenSSL should be used.
     *
     * @return true if OpenSSL should be used
     */
    public static boolean getUseOpenSSL() {
        return useOpenSSL;
    }

    /**
     * Checks if an OpenSSL instance has been created.
     *
     * @return true if an instance has been created
     */
    public static boolean isInstanceCreated() {
        return instanceCreated;
    }

    /**
     * Sets the library initialization state.
     *
     * @param libraryInitialized true if the library is initialized
     */
    public static void setLibraryInitialized(boolean libraryInitialized) {
        OpenSSLStatus.libraryInitialized = libraryInitialized;
    }

    /**
     * Sets the initialization state.
     *
     * @param initialized true if OpenSSL is initialized
     */
    public static void setInitialized(boolean initialized) {
        OpenSSLStatus.initialized = initialized;
    }

    /**
     * Sets the availability state.
     *
     * @param available true if OpenSSL is available
     */
    public static void setAvailable(boolean available) {
        OpenSSLStatus.available = available;
    }

    /**
     * Sets whether OpenSSL should be used.
     *
     * @param useOpenSSL true if OpenSSL should be used
     */
    public static void setUseOpenSSL(boolean useOpenSSL) {
        OpenSSLStatus.useOpenSSL = useOpenSSL;
    }

    /**
     * Sets the instance created state.
     *
     * @param instanceCreated true if an instance has been created
     */
    public static void setInstanceCreated(boolean instanceCreated) {
        OpenSSLStatus.instanceCreated = instanceCreated;
    }

    /**
     * Returns the OpenSSL version.
     *
     * @return The version number
     */
    public static long getVersion() {
        return version;
    }

    /**
     * Sets the OpenSSL version.
     *
     * @param version The version number
     */
    public static void setVersion(long version) {
        OpenSSLStatus.version = version;
    }

    /**
     * Returns the OpenSSL major version.
     *
     * @return The major version number
     */
    public static int getMajorVersion() {
        return majorVersion;
    }

    /**
     * Sets the OpenSSL major version.
     *
     * @param majorVersion The major version number
     */
    public static void setMajorVersion(int majorVersion) {
        OpenSSLStatus.majorVersion = majorVersion;
    }

    /**
     * Returns the OpenSSL minor version.
     *
     * @return The minor version number
     */
    public static int getMinorVersion() {
        return minorVersion;
    }

    /**
     * Sets the OpenSSL minor version.
     *
     * @param minorVersion The minor version number
     */
    public static void setMinorVersion(int minorVersion) {
        OpenSSLStatus.minorVersion = minorVersion;
    }

    /**
     * Returns the OpenSSL library name.
     *
     * @return The library name
     */
    public static Name getName() {
        return name;
    }

    /**
     * Sets the OpenSSL library name.
     *
     * @param name The library name
     */
    public static void setName(Name name) {
        OpenSSLStatus.name = name;
    }

    /**
     * Checks if running with OpenSSL 3.0 or later.
     *
     * @return true if running with OpenSSL 3.0+
     */
    public static boolean isOpenSSL3() {
        return Name.OPENSSL3.equals(name);
    }

    /**
     * Checks if running with BoringSSL.
     *
     * @return true if running with BoringSSL
     */
    public static boolean isBoringSSL() {
        return Name.BORINGSSL.equals(name);
    }

    /**
     * Checks if running with LibreSSL earlier than 3.5.
     *
     * @return true if running with LibreSSL &lt; 3.5
     */
    public static boolean isLibreSSLPre35() {
        return Name.LIBRESSL.equals(name) && ((majorVersion == 3 && minorVersion < 5) || majorVersion < 3);
    }

}
