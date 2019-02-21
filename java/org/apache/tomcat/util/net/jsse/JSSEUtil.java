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

import java.io.IOException;
import java.security.Key;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.X509KeyManager;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.compat.JreVendor;
import org.apache.tomcat.util.compat.TLS;
import org.apache.tomcat.util.net.Constants;
import org.apache.tomcat.util.net.SSLContext;
import org.apache.tomcat.util.net.SSLHostConfig;
import org.apache.tomcat.util.net.SSLHostConfigCertificate;
import org.apache.tomcat.util.net.SSLUtilBase;
import org.apache.tomcat.util.res.StringManager;

/**
 * SSLUtil implementation for JSSE.
 *
 * @author Harish Prabandham
 * @author Costin Manolache
 * @author Stefan Freyr Stefansson
 * @author EKR
 * @author Jan Luehe
 */
public class JSSEUtil extends SSLUtilBase {

    private static final Log log = LogFactory.getLog(JSSEUtil.class);
    private static final StringManager sm = StringManager.getManager(JSSEUtil.class);

    private static final Set<String> implementedProtocols;
    private static final Set<String> implementedCiphers;

    static {
        SSLContext context;
        try {
            context = new JSSESSLContext(Constants.SSL_PROTO_TLS);
            context.init(null,  null,  null);
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            // This is fatal for the connector so throw an exception to prevent
            // it from starting
            throw new IllegalArgumentException(e);
        }

        String[] implementedProtocolsArray = context.getSupportedSSLParameters().getProtocols();
        implementedProtocols = new HashSet<>(implementedProtocolsArray.length);

        // Filter out SSLv2 from the list of implemented protocols (just in case
        // we are running on a JVM that supports it) since it is no longer
        // considered secure but allow SSLv2Hello.
        // Note SSLv3 is allowed despite known insecurities because some users
        // still have a requirement for it.
        for (String protocol : implementedProtocolsArray) {
            String protocolUpper = protocol.toUpperCase(Locale.ENGLISH);
            if (!"SSLV2HELLO".equals(protocolUpper) && !"SSLV3".equals(protocolUpper)) {
                if (protocolUpper.contains("SSL")) {
                    log.debug(sm.getString("jsse.excludeProtocol", protocol));
                    continue;
                }
            }
            implementedProtocols.add(protocol);
        }

        if (implementedProtocols.size() == 0) {
            log.warn(sm.getString("jsse.noDefaultProtocols"));
        }

        String[] implementedCipherSuiteArray = context.getSupportedSSLParameters().getCipherSuites();
        // The IBM JRE will accept cipher suites names SSL_xxx or TLS_xxx but
        // only returns the SSL_xxx form for supported cipher suites. Therefore
        // need to filter the requested cipher suites using both forms with an
        // IBM JRE.
        if (JreVendor.IS_IBM_JVM) {
            implementedCiphers = new HashSet<>(implementedCipherSuiteArray.length * 2);
            for (String name : implementedCipherSuiteArray) {
                implementedCiphers.add(name);
                if (name.startsWith("SSL")) {
                    implementedCiphers.add("TLS" + name.substring(3));
                }
            }
        } else {
            implementedCiphers = new HashSet<>(implementedCipherSuiteArray.length);
            implementedCiphers.addAll(Arrays.asList(implementedCipherSuiteArray));
        }
    }


    public JSSEUtil (SSLHostConfigCertificate certificate) {
        this(certificate, true);
    }


    public JSSEUtil (SSLHostConfigCertificate certificate, boolean warnOnSkip) {
        super(certificate, warnOnSkip);
    }


    @Override
    protected Log getLog() {
        return log;
    }


    @Override
    protected Set<String> getImplementedProtocols() {
        return implementedProtocols;
    }


    @Override
    protected Set<String> getImplementedCiphers() {
        return implementedCiphers;
    }


    @Override
    protected boolean isTls13Available() {
        return TLS.isTlsv13Available();
    }


    @Override
    protected boolean isTls13RenegAuthAvailable() {
        // TLS 1.3 does not support authentication after the initial handshake
        return false;
    }


    @Override
    public SSLContext createSSLContextInternal(List<String> negotiableProtocols)
            throws NoSuchAlgorithmException {
        return new JSSESSLContext(sslHostConfig.getSslProtocol());
    }


    @Override
    public KeyManager[] getKeyManagers() throws Exception {
        String keyAlias = certificate.getCertificateKeyAlias();
        String algorithm = sslHostConfig.getKeyManagerAlgorithm();
        String keyPass = certificate.getCertificateKeyPassword();
        // This has to be here as it can't be moved to SSLHostConfig since the
        // defaults vary between JSSE and OpenSSL.
        if (keyPass == null) {
            keyPass = certificate.getCertificateKeystorePassword();
        }

        KeyStore ks = certificate.getCertificateKeystore();
        KeyStore ksUsed = ks;

        /*
         * Use an in memory key store where possible.
         * For PEM format keys and certificates, it allows them to be imported
         * into the expected format.
         * For Java key stores with PKCS8 encoded keys (e.g. JKS files), it
         * enables Tomcat to handle the case where multiple keys exist in the
         * key store, each with a different password. The KeyManagerFactory
         * can't handle that so using an in memory key store with just the
         * required key works around that.
         * Other keys stores (hardware, MS, etc.) will be used as is.
         */

        char[] keyPassArray = keyPass.toCharArray();

        if (ks == null) {
            if (certificate.getCertificateFile() == null) {
                throw new IOException(sm.getString("jsse.noCertFile"));
            }

            PEMFile privateKeyFile = new PEMFile(SSLHostConfig.adjustRelativePath
                    (certificate.getCertificateKeyFile() != null ? certificate.getCertificateKeyFile() : certificate.getCertificateFile()),
                    keyPass);
            PEMFile certificateFile = new PEMFile(SSLHostConfig.adjustRelativePath(certificate.getCertificateFile()));

            Collection<Certificate> chain = new ArrayList<>();
            chain.addAll(certificateFile.getCertificates());
            if (certificate.getCertificateChainFile() != null) {
                PEMFile certificateChainFile = new PEMFile(SSLHostConfig.adjustRelativePath(certificate.getCertificateChainFile()));
                chain.addAll(certificateChainFile.getCertificates());
            }

            if (keyAlias == null) {
                keyAlias = "tomcat";
            }

            // Switch to in-memory key store
            ksUsed = KeyStore.getInstance("JKS");
            ksUsed.load(null,  null);
            ksUsed.setKeyEntry(keyAlias, privateKeyFile.getPrivateKey(), keyPass.toCharArray(),
                    chain.toArray(new Certificate[chain.size()]));
        } else {
            if (keyAlias != null && !ks.isKeyEntry(keyAlias)) {
                throw new IOException(sm.getString("jsse.alias_no_key_entry", keyAlias));
            } else if (keyAlias == null) {
                Enumeration<String> aliases = ks.aliases();
                if (!aliases.hasMoreElements()) {
                    throw new IOException(sm.getString("jsse.noKeys"));
                }
                while (aliases.hasMoreElements() && keyAlias == null) {
                    keyAlias = aliases.nextElement();
                    if (!ks.isKeyEntry(keyAlias)) {
                        keyAlias = null;
                    }
                }
                if (keyAlias == null) {
                    throw new IOException(sm.getString("jsse.alias_no_key_entry", (Object) null));
                }
            }

            Key k = ks.getKey(keyAlias, keyPassArray);
            if (k != null && !"DKS".equalsIgnoreCase(certificate.getCertificateKeystoreType()) &&
                    "PKCS#8".equalsIgnoreCase(k.getFormat())) {
                // Switch to in-memory key store
                String provider = certificate.getCertificateKeystoreProvider();
                if (provider == null) {
                    ksUsed = KeyStore.getInstance(certificate.getCertificateKeystoreType());
                } else {
                    ksUsed = KeyStore.getInstance(certificate.getCertificateKeystoreType(),
                            provider);
                }
                ksUsed.load(null,  null);
                ksUsed.setKeyEntry(keyAlias, k, keyPassArray, ks.getCertificateChain(keyAlias));
            }
            // Non-PKCS#8 key stores will use the original key store
        }


        KeyManagerFactory kmf = KeyManagerFactory.getInstance(algorithm);
        kmf.init(ksUsed, keyPassArray);

        KeyManager[] kms = kmf.getKeyManagers();

        // Only need to filter keys by alias if there are key managers to filter
        // and the original key store was used. The in memory key stores only
        // have a single key so don't need filtering
        if (kms != null && ksUsed == ks) {
            String alias = keyAlias;
            // JKS keystores always convert the alias name to lower case
            if ("JKS".equals(certificate.getCertificateKeystoreType())) {
                alias = alias.toLowerCase(Locale.ENGLISH);
            }
            for(int i = 0; i < kms.length; i++) {
                kms[i] = new JSSEKeyManager((X509KeyManager)kms[i], alias);
            }
        }

        return kms;
    }
}
