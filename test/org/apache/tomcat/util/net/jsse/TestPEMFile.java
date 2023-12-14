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
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;

@RunWith(Parameterized.class)
public class TestPEMFile {

    private static final String KEY_PASSWORD = "changeit";
    private static final String KEY_PASSWORD_FILE = "key-password";

    private static final String KEY_PKCS1 = "key-pkcs1.pem";
    private static final String KEY_ENCRYPTED_PKCS1_DES_CBC = "key-encrypted-pkcs1-des-cbc.pem";
    private static final String KEY_ENCRYPTED_PKCS1_DES_EDE3_CBC = "key-encrypted-pkcs1-des-ede3-cbc.pem";
    private static final String KEY_ENCRYPTED_PKCS1_AES256 = "key-encrypted-pkcs1-aes256.pem";
    private static final String KEY_ENCRYPTED_PKCS8_HMACSHA1DEFAULT_DES_EDE3_CBC = "key-encrypted-pkcs8-hmacsha1default-des-ede3-cbc.pem";
    private static final String KEY_ENCRYPTED_PKCS8_HMACSHA256_AES_128_CBC = "key-encrypted-pkcs8-hmacsha256-aes-128-cbc.pem";
    private static final String KEY_ENCRYPTED_PKCS8_HMACSHA256_AES_256_CBC = "key-encrypted-pkcs8-hmacsha256-aes-256-cbc.pem";
    private static final String KEY_ENCRYPTED_PKCS8_HMACSHA256_DES_EDE3_CBC = "key-encrypted-pkcs8-hmacsha256-des-ede3-cbc.pem";

    @Parameterized.Parameters
    public static Collection<Object[]> parameters() {
        List<Object[]> parameterSets = new ArrayList<>();

        parameterSets.add(new Object[] { KEY_PASSWORD, null });
        parameterSets.add(new Object[] { null, KEY_PASSWORD_FILE });
        parameterSets.add(new Object[] { KEY_PASSWORD, KEY_PASSWORD_FILE });

        return parameterSets;
    }


    @Parameter(0)
    public String password;

    @Parameter(1)
    public String passwordFile;


    @Test
    public void testKeyPkcs1() throws Exception {
        testKey(KEY_PKCS1, null, null);
    }


    @Test
    public void testKeyPkcs1WithUnnecessaryPassword() throws Exception {
        testKey(KEY_PKCS1, "ignore-me", null);
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
    public void testKeyEncryptedPkcs8HmacSha1DefaultDesEde3Cbc() throws Exception {
        testKeyEncrypted(KEY_ENCRYPTED_PKCS8_HMACSHA1DEFAULT_DES_EDE3_CBC);
    }


    @Test
    public void testKeyEncryptedPkcs8HmacSha256Aes128() throws Exception {
        testKeyEncrypted(KEY_ENCRYPTED_PKCS8_HMACSHA256_AES_128_CBC);
    }


    @Test
    public void testKeyEncryptedPkcs8HmacSha256Aes256() throws Exception {
        testKeyEncrypted(KEY_ENCRYPTED_PKCS8_HMACSHA256_AES_256_CBC);
    }


    @Test
    public void testKeyEncryptedPkcs8HmacSha256DesEde3Cbc() throws Exception {
        testKeyEncrypted(KEY_ENCRYPTED_PKCS8_HMACSHA256_DES_EDE3_CBC);
    }


    private void testKeyEncrypted(String file) throws Exception {
        testKey(file, password, passwordFile);
    }


    private void testKey(String file, String password, String passwordFile) throws Exception {
        PEMFile pemFile = new PEMFile(getPath(file), password, getPath(passwordFile), null);
        PrivateKey pk = pemFile.getPrivateKey();
        Assert.assertNotNull(pk);
    }


    private String getPath(String file) throws IOException {
        if (file == null) {
            return null;
        }
        String packageName = this.getClass().getPackageName();
        String path = packageName.replace(".", File.separator);
        File f = new File("test" + File.separator + path + File.separator + file);

        return f.getCanonicalPath();
    }
}
