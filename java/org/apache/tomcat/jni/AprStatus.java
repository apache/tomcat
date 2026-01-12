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
package org.apache.tomcat.jni;

import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Holds APR status without the need to load other classes.
 */
public class AprStatus {
    private static volatile boolean aprInitialized = false;
    private static volatile boolean aprAvailable = false;
    private static volatile boolean useOpenSSL = true;
    private static volatile boolean instanceCreated = false;
    private static volatile int openSSLVersion = 0;
    private static ReentrantReadWriteLock statusLock = new ReentrantReadWriteLock();

    public static boolean isAprInitialized() {
        return aprInitialized;
    }

    public static boolean isAprAvailable() {
        return aprAvailable;
    }

    public static boolean getUseOpenSSL() {
        return useOpenSSL;
    }

    public static boolean isInstanceCreated() {
        return instanceCreated;
    }

    public static void setAprInitialized(boolean aprInitialized) {
        AprStatus.aprInitialized = aprInitialized;
    }

    public static void setAprAvailable(boolean aprAvailable) {
        AprStatus.aprAvailable = aprAvailable;
    }

    public static void setUseOpenSSL(boolean useOpenSSL) {
        AprStatus.useOpenSSL = useOpenSSL;
    }

    public static void setInstanceCreated(boolean instanceCreated) {
        AprStatus.instanceCreated = instanceCreated;
    }

    /**
     * @return the openSSLVersion
     */
    public static int getOpenSSLVersion() {
        return openSSLVersion;
    }

    /**
     * @param openSSLVersion the openSSLVersion to set
     */
    public static void setOpenSSLVersion(int openSSLVersion) {
        AprStatus.openSSLVersion = openSSLVersion;
    }

    /**
     * Code that changes the status of the APR library MUST hold the write lock while making any changes.
     * <p>
     * Code that needs the status to be consistent for an operation must hold the read lock for the duration of that
     * operation.
     *
     * @return The read/write lock for APR library status
     */
    public static ReentrantReadWriteLock getStatusLock() {
        return statusLock;
    }
}
