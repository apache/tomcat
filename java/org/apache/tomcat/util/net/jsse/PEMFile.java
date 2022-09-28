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
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.cert.CertificateEncodingException;
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
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

import org.apache.tomcat.util.buf.Asn1Parser;
import org.apache.tomcat.util.buf.Asn1Writer;
import org.apache.tomcat.util.codec.binary.Base64;
import org.apache.tomcat.util.file.ConfigFileLoader;
import org.apache.tomcat.util.res.StringManager;

/**
 * RFC 1421 PEM file containing X509 certificates or private keys.
 */
public class PEMFile {

    private static final StringManager sm = StringManager.getManager(PEMFile.class);

    private static final byte[] OID_EC_PUBLIC_KEY =
            new byte[] { 0x06, 0x07, 0x2A, (byte) 0x86, 0x48, (byte) 0xCE, 0x3D, 0x02, 0x01 };

    private static final String OID_PKCS5_PBES2 = "1.2.840.113549.1.5.13";
    private static final String PBES2 = "PBES2";

    public static String toPEM(X509Certificate certificate) throws CertificateEncodingException {
        StringBuilder result = new StringBuilder();
        result.append(Part.BEGIN_BOUNDARY + Part.CERTIFICATE + Part.FINISH_BOUNDARY);
        result.append(System.lineSeparator());
        Base64 b64 = new Base64(64);
        result.append(b64.encodeAsString(certificate.getEncoded()));
        result.append(Part.END_BOUNDARY + Part.CERTIFICATE + Part.FINISH_BOUNDARY);
        return result.toString();
    }

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
        try (InputStream inputStream = ConfigFileLoader.getSource().getResource(filename).getInputStream()) {
            BufferedReader reader =
                    new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.US_ASCII));
            Part part = null;
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith(Part.BEGIN_BOUNDARY)) {
                    part = new Part();
                    part.type = line.substring(Part.BEGIN_BOUNDARY.length(),
                            line.length() - Part.FINISH_BOUNDARY.length()).trim();
                } else if (line.startsWith(Part.END_BOUNDARY)) {
                    parts.add(part);
                    part = null;
                } else if (part != null && !line.contains(":") && !line.startsWith(" ")) {
                    part.content += line;
                } else if (part != null && line.contains(":") && !line.startsWith(" ")) {
                    /* Something like DEK-Info: DES-EDE3-CBC,B5A53CB8B7E50064 */
                    if (line.startsWith("DEK-Info: ")) {
                        String[] pieces = line.split(" ");
                        pieces = pieces[1].split(",");
                        if (pieces.length == 2) {
                            part.algorithm = pieces[0];
                            part.ivHex = pieces[1];
                        }
                    }                }
            }
        }

        for (Part part : parts) {
            switch (part.type) {
                case Part.PRIVATE_KEY:
                    privateKey = part.toPrivateKey(null, keyAlgorithm, Format.PKCS8);
                    break;
                case Part.EC_PRIVATE_KEY:
                    privateKey = part.toPrivateKey(null, "EC", Format.RFC5915);
                    break;
                case Part.ENCRYPTED_PRIVATE_KEY:
                    privateKey = part.toPrivateKey(password, keyAlgorithm, Format.PKCS8);
                    break;
                case Part.RSA_PRIVATE_KEY:
                    if (part.algorithm == null) {
                        // If no encryption algorithm was detected, ignore any
                        // (probably default) key password provided.
                        privateKey = part.toPrivateKey(null, keyAlgorithm, Format.PKCS1);
                    } else {
                        privateKey = part.toPrivateKey(password, keyAlgorithm, Format.PKCS1);
                    }
                    break;
                case Part.CERTIFICATE:
                case Part.X509_CERTIFICATE:
                    certificates.add(part.toCertificate());
                    break;
            }
        }
    }

    private class Part {
        public static final String BEGIN_BOUNDARY = "-----BEGIN ";
        public static final String END_BOUNDARY   = "-----END ";
        public static final String FINISH_BOUNDARY = "-----";

        public static final String PRIVATE_KEY = "PRIVATE KEY";
        public static final String EC_PRIVATE_KEY = "EC PRIVATE KEY";
        public static final String ENCRYPTED_PRIVATE_KEY = "ENCRYPTED PRIVATE KEY";
        public static final String RSA_PRIVATE_KEY = "RSA PRIVATE KEY";
        public static final String CERTIFICATE = "CERTIFICATE";
        public static final String X509_CERTIFICATE = "X509 CERTIFICATE";

        public String type;
        public String content = "";
        public String algorithm = null;
        public String ivHex = null;

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
                    case RFC5915: {
                        keySpec = new PKCS8EncodedKeySpec(rfc5915ToPkcs8(decode()));
                        break;
                    }
                }
            } else {
                if (algorithm == null) {
                    // PKCS 8
                    EncryptedPrivateKeyInfo privateKeyInfo = new EncryptedPrivateKeyInfo(decode());
                    String pbeAlgorithm = getPBEAlgorithm(privateKeyInfo);
                    SecretKeyFactory secretKeyFactory = SecretKeyFactory.getInstance(pbeAlgorithm);
                    SecretKey secretKey = secretKeyFactory.generateSecret(new PBEKeySpec(password.toCharArray()));

                    Cipher cipher = Cipher.getInstance(pbeAlgorithm);
                    cipher.init(Cipher.DECRYPT_MODE, secretKey, privateKeyInfo.getAlgParameters());

                    keySpec = privateKeyInfo.getKeySpec(cipher);
                } else {
                    // PKCS 1
                    String secretKeyAlgorithm;
                    String cipherTransformation;
                    int keyLength;

                    // Is there a generic way to derive these three values from
                    // just the algorithm name?
                    switch (algorithm) {
                        case "DES-CBC": {
                            secretKeyAlgorithm = "DES";
                            cipherTransformation = "DES/CBC/PKCS5Padding";
                            keyLength = 8;
                            break;
                        }
                        case "DES-EDE3-CBC": {
                            secretKeyAlgorithm = "DESede";
                            cipherTransformation = "DESede/CBC/PKCS5Padding";
                            keyLength = 24;
                            break;
                        }
                        case "AES-256-CBC": {
                            secretKeyAlgorithm = "AES";
                            cipherTransformation = "AES/CBC/PKCS5Padding";
                            keyLength = 32;
                            break;
                        }
                        default:
                            // This will almost certainly trigger errors
                            secretKeyAlgorithm = algorithm;
                            cipherTransformation = algorithm;
                            keyLength = 8;
                            break;
                    }

                    byte[] iv = fromHex(ivHex);
                    byte[] key = deriveKey(keyLength, password, iv);
                    SecretKey secretKey = new SecretKeySpec(key, secretKeyAlgorithm);
                    Cipher cipher = Cipher.getInstance(cipherTransformation);
                    cipher.init(Cipher.DECRYPT_MODE, secretKey, new IvParameterSpec(iv));
                    byte[] pkcs1 = cipher.doFinal(decode());
                    keySpec = parsePKCS1(pkcs1);
                }
            }

            InvalidKeyException exception = new InvalidKeyException(sm.getString("pemFile.parseError", filename));
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


        private String getPBEAlgorithm(EncryptedPrivateKeyInfo privateKeyInfo) {
            AlgorithmParameters parameters = privateKeyInfo.getAlgParameters();
            String algName = privateKeyInfo.getAlgName();
            // Java 11 returns OID_PKCS5_PBES2
            // Java 17 returns PBES2
            if (parameters != null && (OID_PKCS5_PBES2.equals(algName) || PBES2.equals(algName))) {
                /*
                 * This should be "PBEWith<prf>And<encryption>".
                 * Relying on the toString() implementation is potentially
                 * fragile but acceptable in this case since the JRE depends on
                 * the toString() implementation as well.
                 * In the future, if necessary, we can parse the value of
                 * parameters.getEncoded() but the associated complexity and
                 * unlikeliness of the JRE implementation changing means that
                 * Tomcat will use to toString() approach for now.
                 */
                return parameters.toString();
            }
            return privateKeyInfo.getAlgName();
        }


        private byte[] deriveKey(int keyLength, String password, byte[] iv) throws NoSuchAlgorithmException {
            // PBKDF1-MD5 as specified by PKCS#5
            byte[] key = new byte[keyLength];

            int insertPosition = 0;

            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] pw = password.getBytes(StandardCharsets.UTF_8);

            while (insertPosition < keyLength) {
                digest.update(pw);
                digest.update(iv, 0, 8);
                byte[] round = digest.digest();
                digest.update(round);

                System.arraycopy(round, 0, key, insertPosition, Math.min(keyLength - insertPosition, round.length));
                insertPosition += round.length;
            }

            return key;
        }


        /*
         * RFC5915: SEQ
         *           INT               value = 1
         *           OCTET STRING      len = 32 bytes
         *           [0]
         *             OID             named EC
         *           [1]
         *             BIT STRING      len = 520 bits
         *
         * PKCS8:   SEQ
         *           INT               value = 0
         *           SEQ
         *             OID             1.2.840.10045.2.1 (EC public key)
         *             OID             named EC
         *           OCTET STRING
         *             SEQ
         *               INT           value = 1
         *               OCTET STRING  len = 32 bytes
         *               [1]
         *                 BIT STRING  len = 520 bits
         *
         */
        private byte[] rfc5915ToPkcs8(byte[] source) {
            // Parse RFC 5915 format EC private key
            Asn1Parser p = new Asn1Parser(source);

            // Type (sequence)
            p.parseTag(0x30);
            // Length
            p.parseFullLength();

            // Version
            BigInteger version = p.parseInt();
            if (version.intValue() != 1) {
                throw new IllegalArgumentException(sm.getString("pemFile.notValidRFC5915"));
            }

            // Private key
            p.parseTag(0x04);
            int privateKeyLen = p.parseLength();
            byte[] privateKey = new byte[privateKeyLen];
            p.parseBytes(privateKey);

            // [0] OID
            p.parseTag(0xA0);
            int oidLen = p.parseLength();
            byte[] oid = new byte[oidLen];
            p.parseBytes(oid);
            if (oid[0] != 0x06) {
                throw new IllegalArgumentException(sm.getString("pemFile.notValidRFC5915"));
            }

            // [1] Public key
            p.parseTag(0xA1);
            int publicKeyLen = p.parseLength();
            byte[] publicKey = new byte[publicKeyLen];
            p.parseBytes(publicKey);
            if (publicKey[0] != 0x03) {
                throw new IllegalArgumentException(sm.getString("pemFile.notValidRFC5915"));
            }


            // Write out PKCS#8 format
            return Asn1Writer.writeSequence(
                    Asn1Writer.writeInteger(0),
                    Asn1Writer.writeSequence(
                            OID_EC_PUBLIC_KEY,
                            oid),
                    Asn1Writer.writeOctetString(
                            Asn1Writer.writeSequence(
                                    Asn1Writer.writeInteger(1),
                                    Asn1Writer.writeOctetString(privateKey),
                                    Asn1Writer.writeTag((byte) 0xA1, publicKey))
                            )
                    );
        }


        private RSAPrivateCrtKeySpec parsePKCS1(byte[] source) {
            Asn1Parser p = new Asn1Parser(source);

            // https://en.wikipedia.org/wiki/X.690#BER_encoding
            // https://tools.ietf.org/html/rfc8017#page-55

            // Type (sequence)
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



        private byte[] fromHex(String hexString) {
            byte[] bytes = new byte[hexString.length() / 2];
            for (int i = 0; i < hexString.length(); i += 2)
            {
                bytes[i / 2] = (byte) ((Character.digit(hexString.charAt(i), 16) << 4)
                    + Character.digit(hexString.charAt(i + 1), 16));
            }
            return bytes;
        }
    }


    private enum Format {
        PKCS1,
        PKCS8,
        RFC5915
    }
}
