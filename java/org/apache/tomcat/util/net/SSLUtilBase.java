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
package org.apache.tomcat.util.net;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.DomainLoadStoreParameter;
import java.security.Key;
import java.security.KeyStore;
import java.security.cert.CRL;
import java.security.cert.CRLException;
import java.security.cert.CertPathParameters;
import java.security.cert.CertStore;
import java.security.cert.CertStoreParameters;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateFactory;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.CollectionCertStoreParameters;
import java.security.cert.PKIXBuilderParameters;
import java.security.cert.X509CertSelector;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javax.net.ssl.CertPathTrustManagerParameters;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.ManagerFactoryParameters;
import javax.net.ssl.SSLSessionContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509KeyManager;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.compat.JreCompat;
import org.apache.tomcat.util.file.ConfigFileLoader;
import org.apache.tomcat.util.net.jsse.JSSEKeyManager;
import org.apache.tomcat.util.net.jsse.PEMFile;
import org.apache.tomcat.util.res.StringManager;

/**
 * Common base class for {@link SSLUtil} implementations.
 */
public abstract class SSLUtilBase implements SSLUtil {

    private static final Log log = LogFactory.getLog(SSLUtilBase.class);
    private static final StringManager sm = StringManager.getManager(SSLUtilBase.class);

    public static final String DEFAULT_KEY_ALIAS = "tomcat";

    protected final SSLHostConfig sslHostConfig;
    protected final SSLHostConfigCertificate certificate;

    private final String[] enabledProtocols;
    private final String[] enabledCiphers;


    protected SSLUtilBase(SSLHostConfigCertificate certificate) {
        this(certificate, true);
    }


    protected SSLUtilBase(SSLHostConfigCertificate certificate, boolean warnTls13) {
        this.certificate = certificate;
        this.sslHostConfig = certificate.getSSLHostConfig();

        // Calculate the enabled protocols
        Set<String> configuredProtocols = sslHostConfig.getProtocols();
        Set<String> implementedProtocols = getImplementedProtocols();
        // If TLSv1.3 is not implemented and not explicitly requested we can
        // ignore it. It is included in the defaults so it may be configured.
        if (!implementedProtocols.contains(Constants.SSL_PROTO_TLSv1_3) &&
                !sslHostConfig.isExplicitlyRequestedProtocol(Constants.SSL_PROTO_TLSv1_3)) {
            configuredProtocols.remove(Constants.SSL_PROTO_TLSv1_3);
        }
        // Newer JREs are dropping support for SSLv2Hello. If it is not
        // implemented and not explicitly requested we can ignore it. It is
        // included in the defaults so it may be configured.
        if (!implementedProtocols.contains(Constants.SSL_PROTO_SSLv2Hello) &&
                !sslHostConfig.isExplicitlyRequestedProtocol(Constants.SSL_PROTO_SSLv2Hello)) {
            configuredProtocols.remove(Constants.SSL_PROTO_SSLv2Hello);
        }

        List<String> enabledProtocols =
                getEnabled("protocols", getLog(), warnTls13, configuredProtocols, implementedProtocols);
        if (enabledProtocols.contains("SSLv3")) {
            log.warn(sm.getString("sslUtilBase.ssl3"));
        }
        this.enabledProtocols = enabledProtocols.toArray(new String[0]);

        if (enabledProtocols.contains(Constants.SSL_PROTO_TLSv1_3) &&
                sslHostConfig.getCertificateVerification().isOptional() &&
                !isTls13RenegAuthAvailable() && warnTls13) {
            log.warn(sm.getString("sslUtilBase.tls13.auth"));
        }

        // Make TLS 1.3 renegotiation status visible further up the stack
        sslHostConfig.setTls13RenegotiationAvailable(isTls13RenegAuthAvailable());

        // Calculate the enabled ciphers
        if (!JreCompat.isJre22Available() && sslHostConfig.getCiphers().startsWith("PROFILE=")) {
            // OpenSSL profiles cannot be resolved without Java 22
            this.enabledCiphers = new String[0];
        } else {
            boolean warnOnSkip = !sslHostConfig.getCiphers().equals(SSLHostConfig.DEFAULT_TLS_CIPHERS);
            List<String> configuredCiphers = sslHostConfig.getJsseCipherNames();
            Set<String> implementedCiphers = getImplementedCiphers();
            List<String> enabledCiphers =
                    getEnabled("ciphers", getLog(), warnOnSkip, configuredCiphers, implementedCiphers);
            this.enabledCiphers = enabledCiphers.toArray(new String[0]);
        }
    }


    static <T> List<T> getEnabled(String name, Log log, boolean warnOnSkip, Collection<T> configured,
            Collection<T> implemented) {

        List<T> enabled = new ArrayList<>();

        if (implemented.size() == 0) {
            // Unable to determine the list of available protocols. This will
            // have been logged previously.
            // Use the configuredProtocols and hope they work. If not, an error
            // will be generated when the list is used. Not ideal but no more
            // can be done at this point.
            enabled.addAll(configured);
        } else {
            enabled.addAll(configured);
            enabled.retainAll(implemented);

            if (enabled.isEmpty()) {
                // Don't use the defaults in this case. They may be less secure
                // than the configuration the user intended.
                // Force the failure of the connector
                throw new IllegalArgumentException(
                        sm.getString("sslUtilBase.noneSupported", name, configured));
            }
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("sslUtilBase.active", name, enabled));
            }
            if (log.isDebugEnabled() || warnOnSkip) {
                if (enabled.size() != configured.size()) {
                    List<T> skipped = new ArrayList<>(configured);
                    skipped.removeAll(enabled);
                    String msg = sm.getString("sslUtilBase.skipped", name, skipped);
                    if (warnOnSkip) {
                        log.warn(msg);
                    } else {
                        log.debug(msg);
                    }
                }
            }
        }

        return enabled;
    }


    /*
     * Gets the key- or truststore with the specified type, path, password and password file.
     */
    static KeyStore getStore(String type, String provider, String path,
            String pass, String passFile) throws IOException {

        KeyStore ks = null;
        InputStream istream = null;
        try {
            if (provider == null) {
                ks = KeyStore.getInstance(type);
            } else {
                ks = KeyStore.getInstance(type, provider);
            }
            if ("DKS".equalsIgnoreCase(type)) {
                URI uri = ConfigFileLoader.getSource().getURI(path);
                ks.load(new DomainLoadStoreParameter(uri, Collections.emptyMap()));
            } else {
                // Some key store types (e.g. hardware) expect the InputStream
                // to be null
                if(!("PKCS11".equalsIgnoreCase(type) ||
                        path.isEmpty() ||
                        "NONE".equalsIgnoreCase(path))) {
                    istream = ConfigFileLoader.getSource().getResource(path).getInputStream();
                }

                // The digester cannot differentiate between null and "".
                // Unfortunately, some key stores behave differently with null
                // and "".
                // JKS key stores treat null and "" interchangeably.
                // PKCS12 key stores don't return the cert if null is used.
                // Key stores that do not use passwords expect null
                // Therefore:
                // - generally use null if pass is null or ""
                // - for JKS or PKCS12 only use null if pass is null
                //   (because JKS will auto-switch to PKCS12)
                char[] storePass = null;
                String passToUse = null;
                if (passFile != null) {
                    try (BufferedReader reader =
                            new BufferedReader(new InputStreamReader(
                            ConfigFileLoader.getSource().getResource(passFile).getInputStream(),
                                StandardCharsets.UTF_8))) {
                        passToUse = reader.readLine();
                    }
                } else {
                    passToUse = pass;
                }

                if (passToUse != null && (!"".equals(passToUse) ||
                        "JKS".equalsIgnoreCase(type) || "PKCS12".equalsIgnoreCase(type))) {
                    storePass = passToUse.toCharArray();
                }
                ks.load(istream, storePass);
            }
        } catch (IOException ioe) {
            // May be expected when working with a trust store
            // Re-throw. Caller will catch and log as required
            throw ioe;
        } catch(Exception ex) {
            String msg = sm.getString("sslUtilBase.keystore_load_failed", type, path,
                    ex.getMessage());
            log.error(msg, ex);
            throw new IOException(msg);
        } finally {
            if (istream != null) {
                try {
                    istream.close();
                } catch (IOException ioe) {
                    // Do nothing
                }
            }
        }

        return ks;
    }


    @Override
    public final SSLContext createSSLContext(List<String> negotiableProtocols) throws Exception {
        SSLContext sslContext = createSSLContextInternal(negotiableProtocols);
        sslContext.init(getKeyManagers(), getTrustManagers(), null);

        SSLSessionContext sessionContext = sslContext.getServerSessionContext();
        if (sessionContext != null) {
            configureSessionContext(sessionContext);
        }

        return sslContext;
    }


    @Override
    public void configureSessionContext(SSLSessionContext sslSessionContext) {
        // <0 - don't set anything - use the implementation default
        if (sslHostConfig.getSessionCacheSize() >= 0) {
            sslSessionContext.setSessionCacheSize(sslHostConfig.getSessionCacheSize());
        }

        // <0 - don't set anything - use the implementation default
        if (sslHostConfig.getSessionTimeout() >= 0) {
            sslSessionContext.setSessionTimeout(sslHostConfig.getSessionTimeout());
        }
    }


    @Override
    public KeyManager[] getKeyManagers() throws Exception {
        String keyAlias = certificate.getCertificateKeyAlias();
        String algorithm = sslHostConfig.getKeyManagerAlgorithm();
        String keyPassFile = certificate.getCertificateKeyPasswordFile();
        String keyPass = certificate.getCertificateKeyPassword();
        // This has to be here as it can't be moved to SSLHostConfig since the
        // defaults vary between JSSE and OpenSSL.
        if (keyPassFile == null) {
            keyPassFile = certificate.getCertificateKeystorePasswordFile();
        }
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
        char[] keyPassArray = new char[0];
        String keyPassToUse = null;
        if (keyPassFile != null) {
            try (BufferedReader reader =
                    new BufferedReader(new InputStreamReader(
                    ConfigFileLoader.getSource().getResource(keyPassFile).getInputStream(),
                        StandardCharsets.UTF_8))) {
                keyPassToUse = reader.readLine();
            }
        } else {
            keyPassToUse = keyPass;
        }

        if (keyPassToUse != null) {
            keyPassArray = keyPassToUse.toCharArray();
        }

        KeyManagerFactory kmf = KeyManagerFactory.getInstance(algorithm);
        if (kmf.getProvider().getInfo().contains("FIPS")) {
            // FIPS doesn't like ANY wrapping nor key manipulation.
            if (keyAlias != null) {
                log.warn(sm.getString("sslUtilBase.aliasIgnored", keyAlias));
            }
            kmf.init(ksUsed, keyPassArray);
            return kmf.getKeyManagers();
        }

        if (ks == null) {
            if (certificate.getCertificateFile() == null) {
                throw new IOException(sm.getString("sslUtilBase.noCertFile"));
            }

            PEMFile privateKeyFile = new PEMFile(
                    certificate.getCertificateKeyFile() != null ? certificate.getCertificateKeyFile() : certificate.getCertificateFile(),
                    keyPass, keyPassFile, null);
            PEMFile certificateFile = new PEMFile(certificate.getCertificateFile());

            Collection<Certificate> chain = new ArrayList<>(certificateFile.getCertificates());
            if (certificate.getCertificateChainFile() != null) {
                PEMFile certificateChainFile = new PEMFile(certificate.getCertificateChainFile());
                chain.addAll(certificateChainFile.getCertificates());
            }

            if (keyAlias == null) {
                keyAlias = DEFAULT_KEY_ALIAS;
            }

            // Switch to in-memory key store
            ksUsed = KeyStore.getInstance("JKS");
            ksUsed.load(null,  null);
            ksUsed.setKeyEntry(keyAlias, privateKeyFile.getPrivateKey(), keyPassArray,
                    chain.toArray(new Certificate[0]));
        } else {
            if (keyAlias != null && !ks.isKeyEntry(keyAlias)) {
                throw new IOException(sm.getString("sslUtilBase.alias_no_key_entry", keyAlias));
            } else if (keyAlias == null) {
                Enumeration<String> aliases = ks.aliases();
                if (!aliases.hasMoreElements()) {
                    throw new IOException(sm.getString("sslUtilBase.noKeys"));
                }
                while (aliases.hasMoreElements() && keyAlias == null) {
                    keyAlias = aliases.nextElement();
                    if (!ks.isKeyEntry(keyAlias)) {
                        keyAlias = null;
                    }
                }
                if (keyAlias == null) {
                    throw new IOException(sm.getString("sslUtilBase.alias_no_key_entry", (Object) null));
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


    @Override
    public String[] getEnabledProtocols() {
        return enabledProtocols;
    }


    @Override
    public String[] getEnabledCiphers() {
        return enabledCiphers;
    }


    @Override
    public TrustManager[] getTrustManagers() throws Exception {

        String className = sslHostConfig.getTrustManagerClassName();
        if(className != null && className.length() > 0) {
             ClassLoader classLoader = getClass().getClassLoader();
             Class<?> clazz = classLoader.loadClass(className);
             if(!(TrustManager.class.isAssignableFrom(clazz))){
                throw new InstantiationException(sm.getString(
                        "sslUtilBase.invalidTrustManagerClassName", className));
             }
             Object trustManagerObject = clazz.getConstructor().newInstance();
             TrustManager trustManager = (TrustManager) trustManagerObject;
             return new TrustManager[]{ trustManager };
        }

        TrustManager[] tms = null;

        KeyStore trustStore = sslHostConfig.getTruststore();
        if (trustStore != null) {
            checkTrustStoreEntries(trustStore);
            String algorithm = sslHostConfig.getTruststoreAlgorithm();
            String crlf = sslHostConfig.getCertificateRevocationListFile();
            boolean revocationEnabled = sslHostConfig.getRevocationEnabled();

            if ("PKIX".equalsIgnoreCase(algorithm)) {
                TrustManagerFactory tmf = TrustManagerFactory.getInstance(algorithm);
                CertPathParameters params = getParameters(crlf, trustStore, revocationEnabled);
                ManagerFactoryParameters mfp = new CertPathTrustManagerParameters(params);
                tmf.init(mfp);
                tms = tmf.getTrustManagers();
            } else {
                TrustManagerFactory tmf = TrustManagerFactory.getInstance(algorithm);
                tmf.init(trustStore);
                tms = tmf.getTrustManagers();
                if (crlf != null && crlf.length() > 0) {
                    throw new CRLException(sm.getString("sslUtilBase.noCrlSupport", algorithm));
                }
                // Only warn if the attribute has been explicitly configured
                if (sslHostConfig.isCertificateVerificationDepthConfigured()) {
                    log.warn(sm.getString("sslUtilBase.noVerificationDepth", algorithm));
                }
            }
        }

        return tms;
    }


    private void checkTrustStoreEntries(KeyStore trustStore) throws Exception {
        Enumeration<String> aliases = trustStore.aliases();
        if (aliases != null) {
            Date now = new Date();
            while (aliases.hasMoreElements()) {
                String alias = aliases.nextElement();
                if (trustStore.isCertificateEntry(alias)) {
                    Certificate cert = trustStore.getCertificate(alias);
                    if (cert instanceof X509Certificate) {
                        try {
                            ((X509Certificate) cert).checkValidity(now);
                        } catch (CertificateExpiredException | CertificateNotYetValidException e) {
                            String msg = sm.getString("sslUtilBase.trustedCertNotValid", alias,
                                    ((X509Certificate) cert).getSubjectX500Principal(), e.getMessage());
                            if (log.isDebugEnabled()) {
                                log.warn(msg, e);
                            } else {
                                log.warn(msg);
                            }
                        }
                    } else {
                        if (log.isDebugEnabled()) {
                            log.debug(sm.getString("sslUtilBase.trustedCertNotChecked", alias));
                        }
                    }
                }
            }
        }
    }


    /**
     * Return the initialization parameters for the TrustManager.
     * Currently, only the default <code>PKIX</code> is supported.
     *
     * @param crlf The path to the CRL file.
     * @param trustStore The configured TrustStore.
     * @param revocationEnabled Should the JSSE provider perform revocation
     *                          checks? Ignored if {@code crlf} is non-null.
     *                          Configuration of revocation checks are expected
     *                          to be via proprietary JSSE provider methods.
     * @return The parameters including the CRLs and TrustStore.
     * @throws Exception An error occurred
     */
    protected CertPathParameters getParameters(String crlf, KeyStore trustStore,
            boolean revocationEnabled) throws Exception {

        PKIXBuilderParameters xparams =
                new PKIXBuilderParameters(trustStore, new X509CertSelector());
        if (crlf != null && crlf.length() > 0) {
            Collection<? extends CRL> crls = getCRLs(crlf);
            CertStoreParameters csp = new CollectionCertStoreParameters(crls);
            CertStore store = CertStore.getInstance("Collection", csp);
            xparams.addCertStore(store);
            xparams.setRevocationEnabled(true);
        } else {
            xparams.setRevocationEnabled(revocationEnabled);
        }
        xparams.setMaxPathLength(sslHostConfig.getCertificateVerificationDepth());
        return xparams;
    }


    /**
     * Load the collection of CRLs.
     * @param crlf The path to the CRL file.
     * @return the CRLs collection
     * @throws IOException Error reading CRL file
     * @throws CRLException CRL error
     * @throws CertificateException Error processing certificate
     */
    protected Collection<? extends CRL> getCRLs(String crlf)
        throws IOException, CRLException, CertificateException {

        Collection<? extends CRL> crls = null;
        try {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            try (InputStream is = ConfigFileLoader.getSource().getResource(crlf).getInputStream()) {
                crls = cf.generateCRLs(is);
            }
        } catch(IOException | CRLException | CertificateException e) {
            throw e;
        }
        return crls;
    }


    protected abstract Set<String> getImplementedProtocols();
    protected abstract Set<String> getImplementedCiphers();
    protected abstract Log getLog();
    protected abstract boolean isTls13RenegAuthAvailable();
    protected abstract SSLContext createSSLContextInternal(List<String> negotiableProtocols) throws Exception;
}
