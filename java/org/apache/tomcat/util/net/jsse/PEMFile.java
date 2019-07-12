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

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPrivateCrtKeySpec;
import java.util.ArrayList;
import java.util.List;

import javax.crypto.Cipher;
import javax.crypto.EncryptedPrivateKeyInfo;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import org.apache.tomcat.util.buf.Asn1Parser;
import org.apache.tomcat.util.codec.binary.Base64;
import org.apache.tomcat.util.file.ConfigFileLoader;
import org.apache.tomcat.util.res.StringManager;

/**
 * RFC 1421 PEM file containing X509 certificates or private keys (PKCS#8 only,
 * i.e. with boundaries containing "BEGIN PRIVATE KEY" or "BEGIN ENCRYPTED PRIVATE KEY",
 * not "BEGIN RSA PRIVATE KEY" or other variations).
 */
public class PEMFile {

    private static final StringManager sm = StringManager.getManager(PEMFile.class);

    private String filename;
    private List<X509Certificate> certificates = new ArrayList<>();
    private PrivateKey privateKey;

    public List<X509Certificate> getCertificates() {
        return certificates;
    }

    public PrivateKey getPrivateKey() {
        return privateKey;
    }

    public PEMFile(String filename) throws IOException, GeneralSecurityException {
        this(filename, null);
    }

    public PEMFile(String filename, String password) throws IOException, GeneralSecurityException {
        this(filename, password, null);
    }

    public PEMFile(String filename, String password, String keyAlgorithm)
            throws IOException, GeneralSecurityException {
        this.filename = filename;

        List<Part> parts = new ArrayList<>();
        try (InputStream inputStream = ConfigFileLoader.getInputStream(filename)) {
            BufferedReader reader =
                    new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.US_ASCII));
            Part part = null;
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith(Part.BEGIN_BOUNDARY)) {
                    part = new Part();
                    part.type = line.substring(Part.BEGIN_BOUNDARY.length(), line.length() - 5).trim();
                } else if (line.startsWith(Part.END_BOUNDARY)) {
                    parts.add(part);
                    part = null;
                } else if (part != null && !line.contains(":") && !line.startsWith(" ")) {
                    part.content += line;
                }
            }
        }

        for (Part part : parts) {
            switch (part.type) {
                case "PRIVATE KEY":
                    privateKey = part.toPrivateKey(null, keyAlgorithm, Format.PKCS8);
                    break;
                case "ENCRYPTED PRIVATE KEY":
                    privateKey = part.toPrivateKey(password, keyAlgorithm, Format.PKCS8);
                    break;
                case "RSA PRIVATE KEY":
                    privateKey = part.toPrivateKey(null, keyAlgorithm, Format.PKCS1);
                    break;
                case "CERTIFICATE":
                case "X509 CERTIFICATE":
                    certificates.add(part.toCertificate());
                    break;
            }
        }
    }

    private class Part {
        public static final String BEGIN_BOUNDARY = "-----BEGIN ";
        public static final String END_BOUNDARY   = "-----END ";

        public String type;
        public String content = "";

        private byte[] decode() {
            return Base64.decodeBase64(content);
        }

        public X509Certificate toCertificate() throws CertificateException {
            CertificateFactory factory = CertificateFactory.getInstance("X.509");
            return (X509Certificate) factory.generateCertificate(new ByteArrayInputStream(decode()));
        }

        public PrivateKey toPrivateKey(String password, String keyAlgorithm, Format format)
                throws GeneralSecurityException, IOException {
            KeySpec keySpec = null;

            if (password == null) {
                switch (format) {
                    case PKCS1: {
                        keySpec = parsePKCS1(decode());
                        break;
                    }
                    case PKCS8: {
                        keySpec = new PKCS8EncodedKeySpec(decode());
                        break;
                    }
                }
            } else {
                EncryptedPrivateKeyInfo privateKeyInfo = new EncryptedPrivateKeyInfo(decode());
                SecretKeyFactory secretKeyFactory = SecretKeyFactory.getInstance(privateKeyInfo.getAlgName());
                SecretKey secretKey = secretKeyFactory.generateSecret(new PBEKeySpec(password.toCharArray()));

                Cipher cipher = Cipher.getInstance(privateKeyInfo.getAlgName());
                cipher.init(Cipher.DECRYPT_MODE, secretKey, privateKeyInfo.getAlgParameters());

                keySpec = privateKeyInfo.getKeySpec(cipher);
            }

            InvalidKeyException exception = new InvalidKeyException(sm.getString("jsse.pemParseError", filename));
            if (keyAlgorithm == null) {
                for (String algorithm : new String[] {"RSA", "DSA", "EC"}) {
                    try {
                        return KeyFactory.getInstance(algorithm).generatePrivate(keySpec);
                    } catch (InvalidKeySpecException e) {
                        exception.addSuppressed(e);
                    }
                }
            } else {
                try {
                    return KeyFactory.getInstance(keyAlgorithm).generatePrivate(keySpec);
                } catch (InvalidKeySpecException e) {
                    exception.addSuppressed(e);
                }
            }

            throw exception;
        }


        private RSAPrivateCrtKeySpec parsePKCS1(byte[] source) {
            Asn1Parser p = new Asn1Parser(source);

            // https://en.wikipedia.org/wiki/X.690#BER_encoding
            // https://tools.ietf.org/html/rfc8017#page-55

            // Type
            p.parseTag(0x30);
            // Length
            p.parseFullLength();

            BigInteger version = p.parseInt();
            if (version.intValue() == 1) {
                // JRE doesn't provide a suitable constructor for multi-prime
                // keys
                throw new IllegalArgumentException(sm.getString("pemFile.noMultiPrimes"));
            }
            return new RSAPrivateCrtKeySpec(p.parseInt(), p.parseInt(), p.parseInt(), p.parseInt(),
                    p.parseInt(), p.parseInt(), p.parseInt(), p.parseInt());
        }
    }


    private enum Format {
        PKCS1,
        PKCS8
    }
}
