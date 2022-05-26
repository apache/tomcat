/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.tomcat.util.net.jsse;

import java.io.File;
import java.io.IOException;
import java.security.PrivateKey;

import org.junit.Assert;
import org.junit.Test;

public class TestPEMFile {

    private static final String KEY_PASSWORD = "changeit";

    private static final String KEY_PKCS1 = "key-pkcs1.pem";
    private static final String KEY_ENCRYPTED_PKCS1_DES_CBC = "key-encrypted-pkcs1-des-cbc.pem";
    private static final String KEY_ENCRYPTED_PKCS1_DES_EDE3_CBC = "key-encrypted-pkcs1-des-ede3-cbc.pem";
    private static final String KEY_ENCRYPTED_PKCS1_AES256 = "key-encrypted-pkcs1-aes256.pem";
    private static final String KEY_ENCRYPTED_PKCS8 = "key-encrypted-pkcs8.pem";


    @Test
    public void testKeyPkcs1() throws Exception {
        testKey(KEY_PKCS1, null);
    }


    @Test
    public void testKeyPkcs1WithUnnecessaryPassword() throws Exception {
        testKey(KEY_PKCS1, "ignore-me");
    }


    @Test
    public void testKeyEncryptedPkcs1DesEde3Cbc() throws Exception {
        testKeyEncrypted(KEY_ENCRYPTED_PKCS1_DES_EDE3_CBC);
    }


    @Test
    public void testKeyEncryptedPkcs1DesCbc() throws Exception {
        testKeyEncrypted(KEY_ENCRYPTED_PKCS1_DES_CBC);
    }


    @Test
    public void testKeyEncryptedPkcs1Aes256() throws Exception {
        testKeyEncrypted(KEY_ENCRYPTED_PKCS1_AES256);
    }


    @Test
    public void testKeyEncryptedPkcs8() throws Exception {
        testKeyEncrypted(KEY_ENCRYPTED_PKCS8);
    }


    private void testKeyEncrypted(String file) throws Exception {
        testKey(file, KEY_PASSWORD);
    }


    private void testKey(String file, String password) throws Exception {
        PEMFile pemFile = new PEMFile(getPath(file), password);
        PrivateKey pk = pemFile.getPrivateKey();
        Assert.assertNotNull(pk);
    }


    private String getPath(String file) throws IOException {
        String packageName = this.getClass().getPackageName();
        String path = packageName.replace(".", File.separator);
        File f = new File("test" + File.separator + path + File.separator + file);

        return f.getCanonicalPath();
    }
}
