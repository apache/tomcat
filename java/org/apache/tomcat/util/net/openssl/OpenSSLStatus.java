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
     * OpenSSL library variant that has been identified
     */
    public enum Name {
        OPENSSL, OPENSSL3, LIBRESSL, BORINGSSL, UNKNOWN
    }

    private static volatile boolean libraryInitialized = false;
    private static volatile boolean initialized = false;
    private static volatile boolean available = false;
    private static volatile boolean useOpenSSL = true;
    private static volatile boolean instanceCreated = false;
    private static volatile long version = 0;
    private static volatile Name name = Name.UNKNOWN;


    public static boolean isLibraryInitialized() {
        return libraryInitialized;
    }

    public static boolean isInitialized() {
        return initialized;
    }

    public static boolean isAvailable() {
        return available;
    }

    public static boolean getUseOpenSSL() {
        return useOpenSSL;
    }

    public static boolean isInstanceCreated() {
        return instanceCreated;
    }

    public static void setLibraryInitialized(boolean libraryInitialized) {
        OpenSSLStatus.libraryInitialized = libraryInitialized;
    }

    public static void setInitialized(boolean initialized) {
        OpenSSLStatus.initialized = initialized;
    }

    public static void setAvailable(boolean available) {
        OpenSSLStatus.available = available;
    }

    public static void setUseOpenSSL(boolean useOpenSSL) {
        OpenSSLStatus.useOpenSSL = useOpenSSL;
    }

    public static void setInstanceCreated(boolean instanceCreated) {
        OpenSSLStatus.instanceCreated = instanceCreated;
    }

    /**
     * @return the version
     */
    public static long getVersion() {
        return version;
    }

    /**
     * @param version the version to set
     */
    public static void setVersion(long version) {
        OpenSSLStatus.version = version;
    }

    /**
     * @return the library name
     */
    public static Name getName() {
        return name;
    }

    /**
     * @param name the name to set
     */
    public static void setName(Name name) {
        OpenSSLStatus.name = name;
    }

    /**
     * @return true if running with OpenSSL 3.0+
     */
    public static boolean isOpenSSL3() {
        return Name.OPENSSL3.equals(name);
    }

}
