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
package org.apache.tomcat.util.net.openssl.ciphers;

import java.util.Set;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@Ignore // Currently 76 out of 486 tests fail
@RunWith(Parameterized.class)
public class TestCipherSuiteConverter {

    @Parameters(name = "{0}")
    public static Cipher[] getCiphers() {
        return Cipher.values();
    }

    private Cipher cipher;

    public TestCipherSuiteConverter(Cipher cipher) {
        this.cipher = cipher;
    }


    @Test
    public void testToJsse() {
        Set<String> jsseNames = cipher.getJsseNames();

        // Test the primary OpenSSL alias
        doToJsseTest(cipher.getOpenSSLAlias(), jsseNames);

        // Test the OpenSSL alternative names
        Set<String> openSSLAltNames = cipher.getOpenSSLAltNames();
        for (String openSSLAltName : openSSLAltNames) {
            doToJsseTest(openSSLAltName, jsseNames);
        }
    }


    private void doToJsseTest(String openSSLName, Set<String> jsseNames) {
        String convertedJsseName = CipherSuiteConverter.toJava(openSSLName, "TLS");
        Assert.assertTrue(
                "[" + openSSLName + "] -> [" + convertedJsseName + "] not in [" + jsseNames + "]",
                jsseNames.contains(convertedJsseName));
    }


    @Test
    public void testToOpenSSL() {
        Set<String> jsseNames = cipher.getJsseNames();

        for (String jsseName : jsseNames) {
            String convertedOpenSSLName = CipherSuiteConverter.toOpenSsl(jsseName);
            Assert.assertEquals(jsseName, cipher.getOpenSSLAlias(), convertedOpenSSLName);
        }
    }
}
