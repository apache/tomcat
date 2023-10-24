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
package org.apache.tomcat.util.net.openssl.panama;

/**
 * Holds OpenSSL status without the need to load other classes.
 */
public class OpenSSLStatus {
    private static volatile boolean libraryInitialized = false;
    private static volatile boolean initialized = false;
    private static volatile boolean available = false;
    private static volatile boolean instanceCreated = false;


    public static boolean isLibraryInitialized() {
        return libraryInitialized;
    }

    public static boolean isInitialized() {
        return initialized;
    }

    public static boolean isAvailable() {
        return available;
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

    public static void setInstanceCreated(boolean instanceCreated) {
        OpenSSLStatus.instanceCreated = instanceCreated;
    }
}
