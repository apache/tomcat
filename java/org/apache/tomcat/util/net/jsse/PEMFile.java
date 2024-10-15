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
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

import org.apache.tomcat.util.buf.Asn1Parser;
import org.apache.tomcat.util.buf.Asn1Writer;
import org.apache.tomcat.util.buf.HexUtils;
import org.apache.tomcat.util.file.ConfigFileLoader;
import org.apache.tomcat.util.res.StringManager;
import org.ietf.jgss.GSSException;
import org.ietf.jgss.Oid;

/**
 * RFC 1421 PEM file containing X509 certificates or private keys.
 */
public class PEMFile {

    private static final StringManager sm = StringManager.getManager(PEMFile.class);

    private static final byte[] OID_EC_PUBLIC_KEY =
            new byte[] { 0x06, 0x07, 0x2A, (byte) 0x86, 0x48, (byte) 0xCE, 0x3D, 0x02, 0x01 };
    // 1.2.840.113549.1.5.13
    private static final byte[] OID_PBES2 =
            new byte[] { 0x2A, (byte) 0x86, 0x48, (byte) 0x86, (byte) 0xF7, 0x0D, 0x01, 0x05, 0x0D };
    // 1.2.840.113549.1.5.12
    private static final byte[] OID_PBKDF2 =
            new byte[] { 0x2A, (byte) 0x86, 0x48, (byte) 0x86, (byte) 0xF7, 0x0D, 0x01, 0x05, 0x0C };

    // Default defined in RFC 8018
    private static final String DEFAULT_PRF = "HmacSHA1";

    private static final Map<String,String> OID_TO_PRF = new HashMap<>();
    static {
        // 1.2.840.113549.2.7
        OID_TO_PRF.put("2a864886f70d0207", DEFAULT_PRF);
        // 1.2.840.113549.2.8
        OID_TO_PRF.put("2a864886f70d0208", "HmacSHA224");
        // 1.2.840.113549.2.9
        OID_TO_PRF.put("2a864886f70d0209", "HmacSHA256");
        // 1.2.840.113549.2.10
        OID_TO_PRF.put("2a864886f70d020a", "HmacSHA384");
        // 1.2.840.113549.2.11
        OID_TO_PRF.put("2a864886f70d020b", "HmacSHA512");
        // 1.2.840.113549.2.12
        OID_TO_PRF.put("2a864886f70d020c", "HmacSHA512/224");
        // 1.2.840.113549.2.13
        OID_TO_PRF.put("2a864886f70d020d", "HmacSHA512/256");
    }

    private static final Map<String,Algorithm> OID_TO_ALGORITHM = new HashMap<>();
    static {
        // 1.2.840.113549.3.7
        OID_TO_ALGORITHM.put("2a864886f70d0307", Algorithm.DES_EDE3_CBC);
        // 2.16.840.1.101.3.4.1.2
        OID_TO_ALGORITHM.put("608648016503040102", Algorithm.AES128_CBC_PAD);
        // 2.16.840.1.101.3.4.1.42
        OID_TO_ALGORITHM.put("60864801650304012a", Algorithm.AES256_CBC_PAD);
    }

    public static String toPEM(X509Certificate certificate) throws CertificateEncodingException {
        StringBuilder result = new StringBuilder();
        result.append(Part.BEGIN_BOUNDARY + Part.CERTIFICATE + Part.FINISH_BOUNDARY);
        result.append(System.lineSeparator());
        result.append(Base64.getMimeEncoder().encodeToString(certificate.getEncoded()));
        result.append(Part.END_BOUNDARY + Part.CERTIFICATE + Part.FINISH_BOUNDARY);
        return result.toString();
    }

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

    public PEMFile(String filename, String password, String keyAlgorithm) throws IOException, GeneralSecurityException {
        this(filename, ConfigFileLoader.getSource().getResource(filename).getInputStream(), password, keyAlgorithm);
    }

    public PEMFile(String filename, String password, String passwordFilename, String keyAlgorithm)
            throws IOException, GeneralSecurityException {
        this(filename, ConfigFileLoader.getSource().getResource(filename).getInputStream(), password, passwordFilename,
                passwordFilename != null ? ConfigFileLoader.getSource().getResource(passwordFilename).getInputStream() :
                        null,
                keyAlgorithm);
    }

    public PEMFile(String filename, InputStream fileStream, String password, String keyAlgorithm)
            throws IOException, GeneralSecurityException {
        this(filename, fileStream, password, null, null, keyAlgorithm);
    }

    /**
     * @param filename           the filename to mention in error messages, not used for anything else.
     * @param fileStream         the stream containing the pem(s).
     * @param password           password to load the pem objects.
     * @param passwordFilename   the password filename to mention in error messages, not used for anything else.
     * @param passwordFileStream stream containing the password to load the pem objects.
     * @param keyAlgorithm       the algorithm to help to know how to load the objects (guessed if null).
     *
     * @throws IOException              if input can't be read.
     * @throws GeneralSecurityException if input can't be parsed/loaded.
     */
    public PEMFile(String filename, InputStream fileStream, String password, String passwordFilename,
            InputStream passwordFileStream, String keyAlgorithm) throws IOException, GeneralSecurityException {
        List<Part> parts = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(fileStream, StandardCharsets.US_ASCII))) {
            Part part = null;
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith(Part.BEGIN_BOUNDARY)) {
                    part = new Part();
                    part.type =
                            line.substring(Part.BEGIN_BOUNDARY.length(), line.length() - Part.FINISH_BOUNDARY.length())
                                    .trim();
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
                    }
                }
            }
        }

        String passwordToUse = null;
        if (passwordFileStream != null) {
            try (BufferedReader reader =
                    new BufferedReader(new InputStreamReader(passwordFileStream, StandardCharsets.UTF_8))) {
                passwordToUse = reader.readLine();
            }
        } else {
            passwordToUse = password;
        }

        for (Part part : parts) {
            switch (part.type) {
                case Part.PRIVATE_KEY:
                    privateKey = part.toPrivateKey(keyAlgorithm, Format.PKCS8, filename);
                    break;
                case Part.EC_PRIVATE_KEY:
                    privateKey = part.toPrivateKey("EC", Format.RFC5915, filename);
                    break;
                case Part.ENCRYPTED_PRIVATE_KEY:
                    privateKey = part.toPrivateKey(passwordToUse, keyAlgorithm, Format.PKCS8, filename);
                    break;
                case Part.RSA_PRIVATE_KEY:
                    if (part.algorithm == null) {
                        // If no encryption algorithm was detected, ignore any
                        // (probably default) key password provided.
                        privateKey = part.toPrivateKey(keyAlgorithm, Format.PKCS1, filename);
                    } else {
                        privateKey = part.toPrivateKey(passwordToUse, keyAlgorithm, Format.PKCS1, filename);
                    }
                    break;
                case Part.CERTIFICATE:
                case Part.X509_CERTIFICATE:
                    certificates.add(part.toCertificate());
                    break;
            }
        }
    }

    private static class Part {
        public static final String BEGIN_BOUNDARY = "-----BEGIN ";
        public static final String END_BOUNDARY = "-----END ";
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
            return Base64.getMimeDecoder().decode(content);
        }

        public X509Certificate toCertificate() throws CertificateException {
            CertificateFactory factory = CertificateFactory.getInstance("X.509");
            return (X509Certificate) factory.generateCertificate(new ByteArrayInputStream(decode()));
        }


        /**
         * Extracts the private key from an unencrypted PEMFile.
         *
         * @param keyAlgorithm Key algorithm if known or null if it needs to be obtained from the PEM file
         * @param format       The format used to encode the private key
         * @param filename     The name of the PEM file
         *
         * @return The clear text private key extracted from the PEM file
         *
         * @throws GeneralSecurityException If there is a cryptographic error processing the PEM file
         */
        public PrivateKey toPrivateKey(String keyAlgorithm, Format format, String filename)
                throws GeneralSecurityException {
            return toPrivateKey(keyAlgorithm, format, filename, decode());
        }


        /**
         * Extracts the private key from an encrypted PEMFile.
         *
         * @param password     Password to decrypt the private key
         * @param keyAlgorithm Key algorithm if known or null if it needs to be obtained from the PEM file
         * @param format       The format used to encode the private key
         * @param filename     The name of the PEM file
         *
         * @return The clear text private key extracted from the PEM file
         *
         * @throws GeneralSecurityException If there is a cryptographic error processing the PEM file
         * @throws IOException              If there is an I/O error reading the PEM file
         */
        public PrivateKey toPrivateKey(String password, String keyAlgorithm, Format format, String filename)
                throws GeneralSecurityException, IOException {

            String secretKeyAlgorithm;
            String cipherTransformation;
            int keyLength;

            switch (format) {
                case PKCS1: {

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
                    // The IV is also used as salt for the password generation
                    byte[] key = deriveKeyPBKDF1(keyLength, password, iv);
                    SecretKey secretKey = new SecretKeySpec(key, secretKeyAlgorithm);
                    Cipher cipher = Cipher.getInstance(cipherTransformation);
                    cipher.init(Cipher.DECRYPT_MODE, secretKey, new IvParameterSpec(iv));
                    byte[] pkcs1 = cipher.doFinal(decode());
                    return toPrivateKey(keyAlgorithm, format, filename, pkcs1);
                }
                case PKCS8: {
                    // Encrypted PEM file is PKCS8
                    Asn1Parser p = new Asn1Parser(decode());

                    //@formatter:off
                    /*
                     * RFC 5208 - PKCS #8
                     * RFC 8018 - PKCS #5
                     *
                     *                  Nesting
                     * SEQ                    1 - PKCS #8
                     *   SEQ                  2 - PKCS #8 encryptionAlgorithm
                     *     OID                  - PKCS #5 PBES2 OID
                     *     SEQ                3 - PKCS #5 PBES2-params
                     *       SEQ              4 - PKCS #5 PBES2 key derivation function
                     *         OID              - PKCS #5 PBES2 KDF OID - must be PBKDF2
                     *         SEQ            5 - PKCS #5 PBKDF2-params
                     *           OCTET STRING   - PKCS #5 PBKDF2 salt
                     *           INT            - PKCS #5 PBKDF2 interationCount
                     *           INT            - PKCS #5 PBKDF2 key length OPTIONAL
                     *           SEQ          6 - PKCS #5 PBKDF2 PRF defaults to HmacSHA1 if not present
                     *             OID          - PKCS #5 PBKDF2 PRF OID
                     *             NULL         - PKCS #5 PBKDF2 PRF parameters
                     *       SEQ              4 - PKCS #5 PBES2 encryption scheme
                     *         OID              - PKCS #5 PBES2 algorithm OID
                     *         OCTET STRING     - PKCS #5 PBES2 algorithm iv
                     *   OCTET STRING           - PKCS #8 encryptedData
                     */
                    //@formatter:on

                    // Parse the PKCS #8 outer sequence and validate the length
                    p.parseTagSequence();
                    p.parseFullLength();

                    // Parse the PKCS #8 encryption algorithm
                    p.parseTagSequence();
                    p.parseLength();

                    // PBES2 OID
                    byte[] oidEncryptionAlgorithm = p.parseOIDAsBytes();
                    /*
                     * Implementation note. If other algorithms are ever supported, the KDF check below is likely to
                     * need to be adjusted.
                     */
                    if (!Arrays.equals(oidEncryptionAlgorithm, OID_PBES2)) {
                        throw new NoSuchAlgorithmException(sm.getString("pemFile.unknownPkcs8Algorithm",
                                toDottedOidString(oidEncryptionAlgorithm)));
                    }

                    // PBES2-params
                    p.parseTagSequence();
                    p.parseLength();

                    // PBES2 KDF
                    p.parseTagSequence();
                    p.parseLength();
                    byte[] oidKDF = p.parseOIDAsBytes();
                    if (!Arrays.equals(oidKDF, OID_PBKDF2)) {
                        throw new NoSuchAlgorithmException(
                                sm.getString("pemFile.notPbkdf2", toDottedOidString(oidKDF)));
                    }

                    // PBES2 KDF-params
                    p.parseTagSequence();
                    p.parseLength();
                    byte[] salt = p.parseOctetString();
                    int iterationCount = p.parseInt().intValue();
                    if (p.peekTag() == Asn1Parser.TAG_INTEGER) {
                        keyLength = p.parseInt().intValue();
                    }

                    // PBKDF2 PRF
                    p.parseTagSequence();
                    p.parseLength();
                    String prf = null;
                    // This tag is optional. If present the nested sequence level will be 6 else if will be 4.
                    if (p.getNestedSequenceLevel() == 6) {
                        byte[] oidPRF = p.parseOIDAsBytes();
                        prf = OID_TO_PRF.get(HexUtils.toHexString(oidPRF));
                        if (prf == null) {
                            throw new NoSuchAlgorithmException(sm.getString("pemFile.unknownPrfAlgorithm", toDottedOidString(oidPRF)));
                        }
                        p.parseNull();

                        // Read the sequence tag for the PBES2 encryption scheme
                        p.parseTagSequence();
                        p.parseLength();
                    } else {
                        // Use the default
                        prf = DEFAULT_PRF;
                    }

                    // PBES2 encryption scheme
                    byte[] oidCipher = p.parseOIDAsBytes();
                    Algorithm algorithm = OID_TO_ALGORITHM.get(HexUtils.toHexString(oidCipher));
                    if (algorithm == null) {
                        throw new NoSuchAlgorithmException(
                                sm.getString("pemFile.unknownEncryptionAlgorithm", toDottedOidString(oidCipher)));
                    }

                    byte[] iv = p.parseOctetString();

                    // Encrypted data
                    byte[] encryptedData = p.parseOctetString();

                    // ASN.1 parsing complete

                    // Build secret key to decrypt encrypted data
                    byte[] key = deriveKeyPBKDF2("PBKDF2With" + prf, password, salt, iterationCount,
                            algorithm.getKeyLength());
                    SecretKey secretKey = new SecretKeySpec(key, algorithm.getSecretKeyAlgorithm());

                    // Configure algorithm to decrypt encrypted data
                    Cipher cipher = Cipher.getInstance(algorithm.getTransformation());
                    cipher.init(Cipher.DECRYPT_MODE, secretKey, new IvParameterSpec(iv));

                    // Decrypt the encrypted key and call this method again to process the key
                    byte[] decryptedData = cipher.doFinal(encryptedData);
                    return toPrivateKey(keyAlgorithm, format, filename, decryptedData);
                }
                default: {
                    // Expected to be unencrypted
                    throw new NoSuchAlgorithmException(sm.getString("pemFile.unknownEncryptedFormat", format));
                }
            }
        }


        private PrivateKey toPrivateKey(String keyAlgorithm, Format format, String filename, byte[] source)
                throws GeneralSecurityException {

            KeySpec keySpec = null;

            switch (format) {
                case PKCS1: {
                    keySpec = parsePKCS1(source);
                    break;
                }
                case PKCS8: {
                    keySpec = new PKCS8EncodedKeySpec(source);
                    break;
                }
                case RFC5915: {
                    keySpec = new PKCS8EncodedKeySpec(rfc5915ToPkcs8(source));
                    break;
                }
            }

            InvalidKeyException exception = new InvalidKeyException(sm.getString("pemFile.parseError", filename));
            if (keyAlgorithm == null) {
                for (String algorithm : new String[] { "RSA", "DSA", "EC" }) {
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


        private byte[] deriveKeyPBKDF1(int keyLength, String password, byte[] salt) throws NoSuchAlgorithmException {
            if (password == null) {
                throw new IllegalArgumentException(sm.getString("pemFile.noPassword"));
            }
            // PBKDF1-MD5 as specified by PKCS#5
            byte[] key = new byte[keyLength];

            int insertPosition = 0;

            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] pw = password.getBytes(StandardCharsets.UTF_8);

            while (insertPosition < keyLength) {
                digest.update(pw);
                digest.update(salt, 0, 8);
                byte[] round = digest.digest();
                digest.update(round);

                System.arraycopy(round, 0, key, insertPosition, Math.min(keyLength - insertPosition, round.length));
                insertPosition += round.length;
            }

            return key;
        }


        private byte[] deriveKeyPBKDF2(String algorithm, String password, byte[] salt, int iterations, int keyLength)
                throws GeneralSecurityException {
            if (password == null) {
                throw new IllegalArgumentException(sm.getString("pemFile.noPassword"));
            }
            SecretKeyFactory secretKeyFactory = SecretKeyFactory.getInstance(algorithm);
            KeySpec keySpec;
            keySpec = new PBEKeySpec(password.toCharArray(), salt, iterations, keyLength);
            SecretKey secretKey = secretKeyFactory.generateSecret(keySpec);
            return secretKey.getEncoded();
        }


        //@formatter:off
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
        //@formatter:on
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
            return Asn1Writer.writeSequence(Asn1Writer.writeInteger(0),
                    Asn1Writer.writeSequence(OID_EC_PUBLIC_KEY, oid),
                    Asn1Writer.writeOctetString(Asn1Writer.writeSequence(Asn1Writer.writeInteger(1),
                            Asn1Writer.writeOctetString(privateKey), Asn1Writer.writeTag((byte) 0xA1, publicKey))));
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
            return new RSAPrivateCrtKeySpec(p.parseInt(), p.parseInt(), p.parseInt(), p.parseInt(), p.parseInt(),
                    p.parseInt(), p.parseInt(), p.parseInt());
        }


        private byte[] fromHex(String hexString) {
            byte[] bytes = new byte[hexString.length() / 2];
            for (int i = 0; i < hexString.length(); i += 2) {
                bytes[i / 2] = (byte) ((Character.digit(hexString.charAt(i), 16) << 4) +
                        Character.digit(hexString.charAt(i + 1), 16));
            }
            return bytes;
        }


        private String toDottedOidString(byte[] oidBytes) {
            try {
                Oid oid = new Oid(oidBytes);
                return oid.toString();
            } catch (GSSException e) {
                return HexUtils.toHexString(oidBytes);
            }
        }
    }


    private enum Format {
        PKCS1,
        PKCS8,
        RFC5915
    }


    private enum Algorithm {
        AES128_CBC_PAD("AES/CBC/PKCS5PADDING", "AES", 128),
        AES256_CBC_PAD("AES/CBC/PKCS5PADDING", "AES", 256),
        DES_EDE3_CBC("DESede/CBC/PKCS5Padding", "DESede", 192);

        private final String transformation;
        private final String secretKeyAlgorithm;
        private final int keyLength;

        Algorithm(String transformation, String secretKeyAlgorithm, int keyLength) {
            this.transformation = transformation;
            this.secretKeyAlgorithm = secretKeyAlgorithm;
            this.keyLength = keyLength;
        }

        public String getTransformation() {
            return transformation;
        }

        public String getSecretKeyAlgorithm() {
            return secretKeyAlgorithm;
        }

        public int getKeyLength() {
            return keyLength;
        }
    }
}
