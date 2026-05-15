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

package org.apache.tomcat.util.net;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.Date;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

public final class TesterKeystoreGenerator {

    private TesterKeystoreGenerator() {}

    @FunctionalInterface
    public interface CertificateExtensionsCustomizer {
        void customize(KeyPair keyPair, X509v3CertificateBuilder certBuilder)
            throws Exception;
    }

    /**
     * Generate a temporary JKS keystore containing a self-signed RSA certificate.
     *
     * @param cn       the Common Name for the certificate subject
     * @param alias    the keystore alias for the key entry
     * @param sanNames DNS Subject Alternative Names to include, or {@code null} for none
     * @param customizer callback to add extensions to the certificate, or {@code null} for none.
     *
     *  @return a temporary keystore file with password {@link  TesterSupport#JKS_PASS}
     *
     *  @throws Exception if certificate generation or keystore creation fails
     */
    public static File generateKeystore(String cn, String alias, String[] sanNames,
                                        CertificateExtensionsCustomizer customizer) throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(4096);
        KeyPair keyPair = kpg.generateKeyPair();

        X500Name subject = new X500Name("CN=" + cn);
        BigInteger serial = BigInteger.valueOf(System.currentTimeMillis());
        long oneDay = 86400000L;
        Date notBefore = new Date(System.currentTimeMillis() - oneDay);
        Date notAfter = new Date(System.currentTimeMillis() + 365L * oneDay);

        X509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(subject, serial, notBefore, notAfter,
                subject, keyPair.getPublic());

        if (sanNames != null && sanNames.length > 0) {
            GeneralName[] generalNames = new GeneralName[sanNames.length];
            for (int i = 0; i < sanNames.length; i++) {
                generalNames[i] = new GeneralName(GeneralName.dNSName, sanNames[i]);
            }
            certBuilder.addExtension(Extension.subjectAlternativeName, false, new GeneralNames(generalNames));
        }

        if (customizer != null) {
            customizer.customize(keyPair, certBuilder);
        }

        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA").build(keyPair.getPrivate());
        X509Certificate certificate = new JcaX509CertificateConverter().getCertificate(certBuilder.build(signer));

        KeyStore ks = KeyStore.getInstance("JKS");
        ks.load(null, null);
        ks.setKeyEntry(alias, keyPair.getPrivate(), TesterSupport.JKS_PASS.toCharArray(), new X509Certificate[] { certificate });

        File keystoreFile = File.createTempFile("test-cert-", ".jks");
        keystoreFile.deleteOnExit();
        try (FileOutputStream fos = new FileOutputStream(keystoreFile)) {
            ks.store(fos, TesterSupport.JKS_PASS.toCharArray());
        }

        return keystoreFile;
    }

    /**
     * Generate temporary PEM files containing a self-signed PQC certificate and private key.
     *
     * @param cn        the Common Name for the certificate subject
     * @param algorithm the PQC algorithm name, e.g. {@code "ML-DSA-44"}, {@code "ML-DSA-65"},
     *                  or {@code "ML-DSA-87"}
     * @param sanNames  DNS Subject Alternative Names to include, or {@code null} for none
     * @param customizer callback to add extensions to the certificate, or {@code null} for none
     *
     * @return a two-element array: {@code [0]} is the certificate PEM file, {@code [1]} is the
     *         private key PEM file
     *
     * @throws Exception if certificate generation fails
     */
    public static File[] generatePQCCertificate(String cn, String algorithm, String[] sanNames,
                                                CertificateExtensionsCustomizer customizer) throws Exception {
        BouncyCastleProvider bouncyCastleProvider = new BouncyCastleProvider();

        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(algorithm, bouncyCastleProvider);
        KeyPair keyPair = keyPairGenerator.generateKeyPair();

        X500Name subject = new X500Name("CN=" + cn);
        BigInteger serial = BigInteger.valueOf(System.currentTimeMillis());
        long oneDay = 86400000L;
        Date notBefore = new Date(System.currentTimeMillis() - oneDay);
        Date notAfter = new Date(System.currentTimeMillis() + 365L * oneDay);

        X509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(subject, serial, notBefore,
                notAfter, subject, keyPair.getPublic());

        if (sanNames != null && sanNames.length > 0) {
            GeneralName[] generalNames = new GeneralName[sanNames.length];
            for (int i = 0; i < sanNames.length; i++) {
                generalNames[i] = new GeneralName(GeneralName.dNSName, sanNames[i]);
            }
            certBuilder.addExtension(Extension.subjectAlternativeName, false, new GeneralNames(generalNames));
        }

        if (customizer != null) {
            customizer.customize(keyPair, certBuilder);
        }

        ContentSigner signer = new JcaContentSignerBuilder(algorithm).setProvider(bouncyCastleProvider)
                .build(keyPair.getPrivate());
        X509Certificate certificate = new JcaX509CertificateConverter().setProvider(bouncyCastleProvider)
                .getCertificate(certBuilder.build(signer));

        File certFile = File.createTempFile("test-pqc-cert-", ".pem");
        certFile.deleteOnExit();
        try (JcaPEMWriter writer = new JcaPEMWriter(new FileWriter(certFile))) {
            writer.writeObject(certificate);
        }

        File keyFile = File.createTempFile("test-pqc-key-", ".pem");
        keyFile.deleteOnExit();
        try (JcaPEMWriter writer = new JcaPEMWriter(new FileWriter(keyFile))) {
            writer.writeObject(keyPair.getPrivate());
        }

        return new File[] { certFile, keyFile };
    }
}
