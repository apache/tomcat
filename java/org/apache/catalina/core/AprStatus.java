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
package org.apache.catalina.core;

/**
 * Holds APR status.
 */
public class AprStatus {
    private static volatile boolean aprInitialized = false;
    private static volatile boolean aprAvailable = false;
    private static volatile boolean useAprConnector = false;
    private static volatile boolean useOpenSSL = true;
    private static volatile boolean instanceCreated = false;


    public static boolean isAprInitialized() {
        return aprInitialized;
    }

    public static boolean isAprAvailable() {
        return aprAvailable;
    }

    public static boolean getUseAprConnector() {
        return useAprConnector;
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

    public static void setUseAprConnector(boolean useAprConnector) {
        AprStatus.useAprConnector = useAprConnector;
    }

    public static void setUseOpenSSL(boolean useOpenSSL) {
        AprStatus.useOpenSSL = useOpenSSL;
    }

    public static void setInstanceCreated(boolean instanceCreated) {
        AprStatus.instanceCreated = instanceCreated;
    }
}
