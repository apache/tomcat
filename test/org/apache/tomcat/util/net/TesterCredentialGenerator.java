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
import java.util.Base64;
import java.util.Date;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

public final class TesterCredentialGenerator {

    private TesterCredentialGenerator() {}

    @FunctionalInterface
    public interface CertificateExtensionsCustomizer {
        void customize(KeyPair keyPair, X509v3CertificateBuilder certBuilder)
            throws Exception;
    }

    /**
     * Generate a temporary credential using a self-signed RSA certificate.
     *
     * @param cn       the Common Name for the certificate subject
     * @param alias    the keystore alias for the key entry
     * @param sanNames DNS Subject Alternative Names to include, or {@code null} for none
     * @param customizer callback to add extensions to the certificate, or {@code null} for none.
     *
     *  @return a temporary set of credential files with password {@link TesterSupport#JKS_PASS}
     *
     *  @throws Exception if credential creation fails
     */
    public static TesterCredential generateCredential(String cn, String alias, String[] sanNames,
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

        // Create the keystore from the key and certificate
        KeyStore ks = KeyStore.getInstance("JKS");
        ks.load(null, null);
        ks.setKeyEntry(alias, keyPair.getPrivate(), TesterSupport.JKS_PASS.toCharArray(), new X509Certificate[] { certificate });

        File keystoreFile = File.createTempFile("test-cert-", ".jks");
        keystoreFile.deleteOnExit();
        try (FileOutputStream fos = new FileOutputStream(keystoreFile)) {
            ks.store(fos, TesterSupport.JKS_PASS.toCharArray());
        }

        // Create the key file
        File keyFile = File.createTempFile("test-key-", ".key");
        keyFile.deleteOnExit();
        try (FileWriter fw = new FileWriter(keyFile)) {
            fw.write("-----BEGIN PRIVATE KEY-----\n");
            fw.write(Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded()));
            fw.write("\n-----END PRIVATE KEY-----");
        }

        // Create the certificate file
        File certificateFile = File.createTempFile("test-certificate-", ".cert");
        certificateFile.deleteOnExit();
        try (FileWriter fw = new FileWriter(certificateFile)) {
            fw.write("-----BEGIN CERTIFICATE-----\n");
            fw.write(Base64.getEncoder().encodeToString(certificate.getEncoded()));
            fw.write("\n-----END CERTIFICATE-----");
        }

        return new TesterCredential(keystoreFile, keyFile, certificateFile);
    }


    public static class TesterCredential {

        private final File keystore;
        private final File key;
        private final File certificate;

        public TesterCredential(File keystore, File key, File certificate) {
            this.keystore = keystore;
            this.key = key;
            this.certificate = certificate;
        }

        public File getKeystore() {
            return keystore;
        }

        public File getKey() {
            return key;
        }

        public File getCertificate() {
            return certificate;
        }
    }
}
