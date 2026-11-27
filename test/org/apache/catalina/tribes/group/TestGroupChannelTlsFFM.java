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
package org.apache.catalina.tribes.group;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.compat.JreCompat;
import org.apache.tomcat.util.net.openssl.OpenSSLStatus;

@RunWith(Parameterized.class)
public class TestGroupChannelTlsFFM extends GroupChannelTlsTestBase {

    @Before
    public void setup() throws Exception {
        Assume.assumeTrue("FFM support is compiled with Java 25+", JreCompat.isJre25Available());
        try {
            openSSLLibraryInit();
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            // Ignore
        }
        try {
            // Need to check both as only available is reset with init/destroy
            Assume.assumeTrue("TLS-PSK support requires OpenSSL", OpenSSLStatus.isAvailable());
            Assume.assumeTrue("TLS-PSK support requires OpenSSL 3", OpenSSLStatus.isOpenSSL3());
        } finally {
            if (!explicit || !OpenSSLStatus.isOpenSSL3()) {
                // If not explicit, leave OpenSSL-FFM uninitialized so Tribes performs initialization.
                // Clean-up if not OpenSSL 3
                openSSLLibraryDestroy();
            }
        }
    }


    @After
    public void teardown() throws Exception {
        if (explicit && OpenSSLStatus.isAvailable()) {
            // Models OpenSSLLifecycleListener. Stop FFM if it was started.
            openSSLLibraryDestroy();
        }
    }


    // Use reflection to avoid loading the FFM code (which has static initializers) before the Java 22 check.
    private static void openSSLLibraryInit() throws Exception {
        Class<?> openSSLLibraryClass =
                Class.forName("org.apache.tomcat.util.net.openssl.panama.OpenSSLLibrary");
        openSSLLibraryClass.getMethod("init").invoke(null);
    }


    private static void openSSLLibraryDestroy() throws Exception {
        Class<?> openSSLLibraryClass =
                Class.forName("org.apache.tomcat.util.net.openssl.panama.OpenSSLLibrary");
        openSSLLibraryClass.getMethod("destroy").invoke(null);
    }
}
