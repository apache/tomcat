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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
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
    private static final String KEY_ENCRYPTED_PKCS1_AES128 = "key-encrypted-pkcs1-aes128.pem";
    private static final String KEY_ENCRYPTED_PKCS1_AES192 = "key-encrypted-pkcs1-aes192.pem";
    private static final String KEY_ENCRYPTED_PKCS1_AES256 = "key-encrypted-pkcs1-aes256.pem";
    private static final String KEY_ENCRYPTED_PKCS8_HMACSHA1DEFAULT_DES_EDE3_CBC =
            "key-encrypted-pkcs8-hmacsha1default-des-ede3-cbc.pem";
    private static final String KEY_ENCRYPTED_PKCS8_HMACSHA256_AES_128_CBC =
            "key-encrypted-pkcs8-hmacsha256-aes-128-cbc.pem";
    private static final String KEY_ENCRYPTED_PKCS8_HMACSHA256_AES_256_CBC =
            "key-encrypted-pkcs8-hmacsha256-aes-256-cbc.pem";
    private static final String KEY_ENCRYPTED_PKCS8_HMACSHA256_DES_EDE3_CBC =
            "key-encrypted-pkcs8-hmacsha256-des-ede3-cbc.pem";

    /*
     * A clean single certificate PEM. Generated with: keytool -exportcert -rfc -keystore test/localhost-rsa.jks
     * -storepass changeit -alias tomcat with the line endings converted from CRLF to LF. The certificate itself expires
     * on 2028-08-11.
     */
    private static final String CERTIFICATE = "certificate.pem";

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
    public void testKeyEncryptedPkcs1Aes128() throws Exception {
        testKeyEncrypted(KEY_ENCRYPTED_PKCS1_AES128);
    }


    @Test
    public void testKeyEncryptedPkcs1Aes192() throws Exception {
        testKeyEncrypted(KEY_ENCRYPTED_PKCS1_AES192);
    }


    @Test
    public void testKeyEncryptedPkcs1Aes256() throws Exception {
        testKeyEncrypted(KEY_ENCRYPTED_PKCS1_AES256);
    }


    @Test
    public void testKeyEncryptedPkcs1UnsupportedAlgorithm() throws Exception {
        // The name of the algorithm is not a JCE transformation name, but the
        // algorithm should still be rejected as unsupported rather than by
        // the failure of an attempt to use the name as a transformation. The
        // algorithm is rejected before the content is decrypted, so the rest
        // of the content does not matter.
        String pem = "-----BEGIN RSA PRIVATE KEY-----\n" + "Proc-Type: 4,ENCRYPTED\n" +
                "DEK-Info: AES-128-CTR,0123456789ABCDEF0123456789ABCDEF\n" + "QUJDRA==\n" +
                "-----END RSA PRIVATE KEY-----\n";
        try {
            @SuppressWarnings("unused")
            PEMFile pf = new PEMFile("unsupported", new ByteArrayInputStream(pem.getBytes(StandardCharsets.US_ASCII)),
                    KEY_PASSWORD, null);
            Assert.fail("Expected the unsupported encryption algorithm to be rejected");
        } catch (NoSuchAlgorithmException nsae) {
            // Expected
        }
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


    @Test
    public void testToPem() throws Exception {
        PEMFile pemFile = new PEMFile(getPath(CERTIFICATE));
        Assert.assertEquals(1, pemFile.getCertificates().size());
        X509Certificate certificate = pemFile.getCertificates().get(0);

        String pem = PEMFile.toPEM(certificate);

        // RFC 7468: each boundary is on a line of its own and the encoded text
        // ends with a line separator
        String[] lines = pem.split(System.lineSeparator());
        Assert.assertEquals("-----BEGIN CERTIFICATE-----", lines[0]);
        Assert.assertEquals("-----END CERTIFICATE-----", lines[lines.length - 1]);
        Assert.assertTrue("Missing line separator at end of: " + pem, pem.endsWith(System.lineSeparator()));

        // The same line separator is used throughout the encoded text and no
        // line is longer than the 64 characters RFC 7468 requires
        String separatorsRemoved = pem.replace(System.lineSeparator(), "");
        Assert.assertFalse("Unexpected line separator in: " + pem, separatorsRemoved.contains("\r"));
        Assert.assertFalse("Unexpected line separator in: " + pem, separatorsRemoved.contains("\n"));
        for (String line : lines) {
            Assert.assertTrue("Long line: " + line, line.length() <= 64 || line.startsWith("-----"));
        }
        // RFC 7468 requires all the lines of encoded text but the last one to
        // contain exactly 64 characters
        for (int i = 1; i < lines.length - 2; i++) {
            Assert.assertEquals("Short line: " + lines[i], 64, lines[i].length());
        }

        // The encoded text must be readable as PEM
        PEMFile roundTrip =
                new PEMFile("toPEM", new ByteArrayInputStream(pem.getBytes(StandardCharsets.US_ASCII)), null, null);
        Assert.assertEquals(1, roundTrip.getCertificates().size());
        Assert.assertEquals(certificate, roundTrip.getCertificates().get(0));
    }


    @Test
    public void testEndBoundaryWithoutStartBoundary() throws Exception {
        assertInvalidPem("-----END CERTIFICATE-----\n");
    }


    @Test
    public void testStartBoundaryWithoutEndBoundary() throws Exception {
        assertInvalidPem("-----BEGIN CERTIFICATE-----\n" + "QUJD\n");
    }


    @Test
    public void testStartBoundaryInsidePart() throws Exception {
        assertInvalidPem("-----BEGIN CERTIFICATE-----\n" + "QUJD\n" + "-----BEGIN CERTIFICATE-----\n" + "RUZH\n");
    }


    @Test
    public void testMismatchedBoundaryTypes() throws Exception {
        assertInvalidPem("-----BEGIN CERTIFICATE-----\n" + "QUJD\n" + "-----END RSA PRIVATE KEY-----\n");
    }


    @Test
    public void testBoundaryLineWithoutFinishBoundary() throws Exception {
        assertInvalidPem("-----BEGIN CERTIFICATE\n");
    }


    @Test
    public void testDekInfoWithoutAlgorithmAndIv() throws Exception {
        assertInvalidPem("-----BEGIN RSA PRIVATE KEY-----\n" + "DEK-Info: DES-EDE3-CBC\n" + "QUJD\n" +
                "-----END RSA PRIVATE KEY-----\n");
    }


    @Test
    public void testBoundaryLinesWithTrailingWhiteSpace() throws Exception {
        // RFC 7468 allows white space between the finish boundary and the end
        // of the line
        byte[] fileBytes = Files.readAllBytes(new File(getPath(CERTIFICATE)).toPath());
        String pem = new String(fileBytes, StandardCharsets.US_ASCII);
        String modified = pem.replace("-----BEGIN CERTIFICATE-----", "-----BEGIN CERTIFICATE-----\t")
                .replace("-----END CERTIFICATE-----", "-----END CERTIFICATE----- ");
        PEMFile pemFile = new PEMFile("trailing",
                new ByteArrayInputStream(modified.getBytes(StandardCharsets.US_ASCII)), null, null);
        Assert.assertEquals(1, pemFile.getCertificates().size());
    }


    /*
     * The structure of the file is checked while it is scanned, before any of the content is decoded, so these
     * malformed structures must be rejected even though the content is not valid PEM encoded data.
     */
    private void assertInvalidPem(String content) throws Exception {
        ByteArrayInputStream stream = new ByteArrayInputStream(content.getBytes(StandardCharsets.US_ASCII));
        try {
            @SuppressWarnings("unused")
            PEMFile pf = new PEMFile("invalid", stream, null, null);
            Assert.fail("Expected the malformed PEM to be rejected");
        } catch (IOException ioe) {
            // Expected
        }
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
