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

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import org.apache.tomcat.util.net.openssl.OpenSSLStatus.Name;

public class TestOpenSSLStatus {

    private Name originalName;

    @Before
    public void saveName() {
        originalName = OpenSSLStatus.getName();
    }

    @After
    public void restoreName() {
        OpenSSLStatus.setName(originalName);
    }


    /*
     * OpenSSL 3.0 or later means 3.0, 4.0 and any later release. A library that is identified as OpenSSL 4 is also
     * OpenSSL 3.0 or later.
     */
    @Test
    public void testOpenSSL3() {
        OpenSSLStatus.setName(Name.OPENSSL3);

        Assert.assertTrue(OpenSSLStatus.isOpenSSL3());
        Assert.assertFalse(OpenSSLStatus.isOpenSSL4());
    }


    @Test
    public void testOpenSSL4() {
        OpenSSLStatus.setName(Name.OPENSSL4);

        Assert.assertTrue(OpenSSLStatus.isOpenSSL3());
        Assert.assertTrue(OpenSSLStatus.isOpenSSL4());
    }


    @Test
    public void testOpenSSLBefore3() {
        OpenSSLStatus.setName(Name.OPENSSL);

        Assert.assertFalse(OpenSSLStatus.isOpenSSL3());
        Assert.assertFalse(OpenSSLStatus.isOpenSSL4());
    }


    @Test
    public void testOtherLibraries() {
        for (Name other : new Name[] { Name.LIBRESSL, Name.BORINGSSL, Name.UNKNOWN }) {
            OpenSSLStatus.setName(other);

            Assert.assertFalse(other.name(), OpenSSLStatus.isOpenSSL3());
            Assert.assertFalse(other.name(), OpenSSLStatus.isOpenSSL4());
        }
    }
}
