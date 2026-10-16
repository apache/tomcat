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
package org.apache.tomcat.util.net.openssl.panama;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.file.ConfigFileLoader;
import org.apache.tomcat.util.net.Constants;
import org.apache.tomcat.util.net.SSLHostConfig;
import org.apache.tomcat.util.net.SSLHostConfigCertificate;
import org.apache.tomcat.util.net.openssl.OpenSSLPreSharedKeySelector;
import org.apache.tomcat.util.openssl.SSL_psk_find_session_cb_func;
import org.apache.tomcat.util.openssl.openssl_h;
import org.apache.tomcat.util.openssl.pem_password_cb;
import org.apache.tomcat.util.res.StringManager;

/**
 * Loads certificates and pre-shared keys from the connector's host
 * configuration into native OpenSSL objects.
 * <p>
 * Individual {@link SSLHostConfigCertificate}s are loaded through
 * {@link #loadCertEntry(SSLHostConfigCertificate, Arena)} and their Java
 * X.509 chains are read through
 * {@link #getCertificateChain(SSLHostConfigCertificate)}. The native objects
 * of an entry are released with {@link #freeCertEntry(CertificateEntry)} when
 * the configuration that owns them is discarded.
 * <p>
 * Also hosts the TLS 1.3 server-side pre-shared key support: the find-session
 * callback installed by
 * {@link #installFindSessionCallback(MemorySegment, Function, Arena)} sits on
 * a context's SSL_CTX and resolves the per-host selector per connection,
 * since a single listener serves all hosts. OpenSSL copies the context's
 * callback to every connection SSL it creates, so a context-level callback is
 * sufficient. The session id context constant
 * {@link #DEFAULT_SESSION_ID_CONTEXT} is shared with {@link OpenSSLContext}.
 */
public final class CertificateLoader {

    private static final Log log = LogFactory.getLog(CertificateLoader.class);
    private static final StringManager sm = StringManager.getManager(CertificateLoader.class);

    /**
     * The session id context used for TLS 1.3 pre-shared key sessions. The
     * socket {@link OpenSSLContext} sets the same context on its server
     * session context: a pre-shared key session created for a connection is
     * only accepted when it carries the context of the connection's context.
     */
    public static final byte[] DEFAULT_SESSION_ID_CONTEXT =
            new byte[] { 'd', 'e', 'f', 'a', 'u', 'l', 't' };

    private CertificateLoader() {
        // Utility class, not instantiable
    }


    /**
     * A single native certificate + private key pair (for one key type),
     * loaded from one {@link SSLHostConfigCertificate}. The native objects
     * are allocated in the arena of the configuration snapshot that owns
     * them and are
     * referenced (not copied) when applied to a connection by the SNI
     * callback.
     */
    public static final class CertificateEntry {

        /**
         * The leaf certificate ({@code X509 *}).
         */
        public final MemorySegment leaf;

        /**
         * The private key ({@code EVP_PKEY *}).
         */
        public final MemorySegment key;

        /**
         * Intermediate chain certificates ({@code X509 *}), sent after the
         * leaf. May be empty.
         */
        public final MemorySegment[] chain;


        public CertificateEntry(MemorySegment leaf, MemorySegment key, MemorySegment[] chain) {
            this.leaf = leaf;
            this.key = key;
            this.chain = chain;
        }
    }


    // ------------------------------------- Certificate loading

    /**
     * Loads one {@link SSLHostConfigCertificate} (PEM, PKCS#12 or Java
     * key store) into native objects allocated in the given arena.
     *
     * @param certificate the certificate configuration
     * @param arena       arena for the native allocations
     *
     * @return the loaded entry, or {@code null} if it could not be loaded
     *         (the failure is logged)
     */
    public static CertificateEntry loadCertEntry(SSLHostConfigCertificate certificate, Arena arena) {
        return loadCertEntry(certificate, arena, null);
    }


    /**
     * Loads one {@link SSLHostConfigCertificate} (PEM, PKCS#12 or Java
     * key store) into native objects allocated in the given arena, with an
     * OpenSSL ENGINE fallback for PEM private keys that cannot be parsed.
     *
     * @param certificate     the certificate configuration
     * @param arena           arena for the native allocations
     * @param engineKeyLoader fallback invoked with the adjusted key file path
     *                        when PEM parsing fails, returning a private key
     *                        loaded through an OpenSSL ENGINE or
     *                        {@code MemorySegment.NULL} when none is
     *                        available; may be {@code null}
     *
     * @return the loaded entry, or {@code null} if it could not be loaded
     *         (the failure is logged)
     */
    public static CertificateEntry loadCertEntry(SSLHostConfigCertificate certificate, Arena arena,
            Function<String,MemorySegment> engineKeyLoader) {
        String hostName = certificate.getSSLHostConfig().getHostName();
        try {
            // Pick the right key password
            String keyPassToUse;
            try {
                keyPassToUse = resolveKeyPassword(certificate);
            } catch (IOException ioe) {
                String keyPassFile = certificate.getCertificateKeyPasswordFile();
                if (keyPassFile == null) {
                    keyPassFile = certificate.getCertificateKeystorePasswordFile();
                }
                log.warn(sm.getString("certificateLoader.certificateLoadFailed", hostName, keyPassFile), ioe);
                return null;
            }

            String certFile = certificate.getCertificateFile();
            if (certFile != null) {
                certFile = SSLHostConfig.adjustRelativePath(certFile);
                if (certFile.toLowerCase(Locale.ENGLISH).endsWith(".pkcs12")) {
                    return loadPkcs12(certificate, hostName, certFile, keyPassToUse, arena);
                }
                return loadPem(certificate, hostName, certFile, keyPassToUse, arena,
                        engineKeyLoader);
            }
            return loadKeyStore(certificate, hostName, keyPassToUse, arena);
        } catch (Throwable t) {
            log.warn(sm.getString("certificateLoader.certificateLoadFailed", hostName,
                    t.getClass().getName() + (t.getMessage() != null ? ": " + t.getMessage() : "")), t);
            return null;
        }
    }


    /**
     * Resolves the password used to open the private key / key store (or
     * PKCS#12 bundle) configured for the given certificate entry: the key
     * password - or the password read from the key password file, one line -
     * if configured, otherwise the keystore password (or its file). Shared
     * by the native load path and the Java certificate-chain view exposed
     * via the certificate's SSLContext (JMX, the Manager application).
     *
     * @param certificate The certificate entry to resolve the password for
     *
     * @return The password, or {@code null} if none is configured
     *
     * @throws IOException If a configured password file could not be read
     */
    public static String resolveKeyPassword(SSLHostConfigCertificate certificate) throws IOException {
        String keyPass = certificate.getCertificateKeyPassword();
        if (keyPass == null) {
            keyPass = certificate.getCertificateKeystorePassword();
        }
        String keyPassFile = certificate.getCertificateKeyPasswordFile();
        if (keyPassFile == null) {
            keyPassFile = certificate.getCertificateKeystorePasswordFile();
        }
        if (keyPassFile != null) {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(
                            ConfigFileLoader.getSource().getResource(keyPassFile).getInputStream(),
                            StandardCharsets.UTF_8))) {
                return reader.readLine();
            }
        }
        return keyPass;
    }


    /**
     * Selects the key entry alias to use in the given key store: the
     * configured alias if one is set, otherwise the first key entry in the
     * store. A configured alias is returned unvalidated - callers check it
     * with {@link KeyStore#isKeyEntry(String)} so an explicit but wrong
     * alias fails identically in every view of the store. Shared by the
     * native load path and the Java certificate-chain view.
     *
     * @param keyStore        The key store to select from
     * @param configuredAlias The configured key alias, or {@code null} to
     *                        use the first key entry
     *
     * @return The selected alias, or {@code null} if the store holds no
     *         key entry
     *
     * @throws KeyStoreException If the key store cannot be examined
     */
    public static String resolveKeyAlias(KeyStore keyStore, String configuredAlias)
            throws KeyStoreException {
        if (configuredAlias != null) {
            return configuredAlias;
        }
        Enumeration<?> aliases = keyStore.aliases();
        while (aliases.hasMoreElements()) {
            String candidate = aliases.nextElement().toString();
            if (keyStore.isKeyEntry(candidate)) {
                return candidate;
            }
        }
        return null;
    }


    /**
     * Loads a PEM certificate (and private key) with an optional separate
     * private key file and an optional chain file.
     *
     * @param certificate  The certificate configuration
     * @param hostName     The host name the certificate belongs to, used for
     *                     logging
     * @param certFile     The (adjusted) path to the certificate file
     * @param keyPassToUse The key passphrase, or {@code null} if none
     * @param arena        Arena for the native allocations
     *
     * @return The loaded entry, or {@code null} if it could not be loaded
     *         (the failure is logged)
     *
     * @throws IOException If a configured file could not be read
     */
    public static CertificateEntry loadPem(SSLHostConfigCertificate certificate, String hostName,
            String certFile, String keyPassToUse, Arena arena) throws IOException {
        return loadPem(certificate, hostName, certFile, keyPassToUse, arena, null);
    }


    /**
     * Loads a PEM certificate (and private key) with an optional separate
     * private key file, an optional chain file and an OpenSSL ENGINE
     * fallback for keys that cannot be parsed.
     *
     * @param certificate     The certificate configuration
     * @param hostName        The host name the certificate belongs to, used
     *                        for logging
     * @param certFile        The (adjusted) path to the certificate file
     * @param keyPassToUse    The key passphrase, or {@code null} if none
     * @param arena           Arena for the native allocations
     * @param engineKeyLoader fallback invoked with the adjusted key file
     *                        path when PEM parsing fails, returning a
     *                        private key loaded through an OpenSSL ENGINE or
     *                        {@code MemorySegment.NULL} when none is
     *                        available; may be {@code null}
     *
     * @return The loaded entry, or {@code null} if it could not be loaded
     *         (the failure is logged)
     *
     * @throws IOException If a configured file could not be read
     */
    public static CertificateEntry loadPem(SSLHostConfigCertificate certificate, String hostName,
            String certFile, String keyPassToUse, Arena arena,
            Function<String,MemorySegment> engineKeyLoader) throws IOException {
        String keyFile = certificate.getCertificateKeyFile();
        if (keyFile == null) {
            keyFile = certFile;
        } else {
            keyFile = SSLHostConfig.adjustRelativePath(keyFile);
        }
        String chainFile = certificate.getCertificateChainFile();
        if (chainFile != null) {
            chainFile = SSLHostConfig.adjustRelativePath(chainFile);
        }

        byte[] certBytes = readConfigFileBytes(certFile);
        if (certBytes == null) {
            log.warn(sm.getString("certificateLoader.certificateLoadFailed", hostName, certFile));
            return null;
        }
        byte[] keyBytes = readConfigFileBytes(keyFile);
        if (keyBytes == null) {
            log.warn(sm.getString("certificateLoader.certificateLoadFailed", hostName, keyFile));
            return null;
        }

        // The certificate file may hold the intermediates after the leaf (the
        // common combined "fullchain" style); read all of them, not just the
        // first. A file that holds no PEM certificate at all is retried as a
        // single DER encoded certificate.
        MemorySegment[] certFileCerts = readX509ChainFromBytes(certBytes);
        MemorySegment leaf;
        MemorySegment[] chain;
        if (certFileCerts.length > 0) {
            leaf = certFileCerts[0];
            chain = Arrays.copyOfRange(certFileCerts, 1, certFileCerts.length);
        } else {
            leaf = readX509FromBytes(certBytes);
            chain = new MemorySegment[0];
        }
        if (leaf.equals(MemorySegment.NULL)) {
            for (MemorySegment extra : chain) {
                openssl_h.X509_free(extra);
            }
            log.warn(sm.getString("certificateLoader.certificateLoadFailed", hostName,
                    certFile + " (" + OpenSSLLibrary.getLastError() + ")"));
            return null;
        }
        MemorySegment key = readPrivateKeyFromBytes(keyBytes, keyPassToUse, arena);
        if (key.equals(MemorySegment.NULL) && engineKeyLoader != null) {
            // Not a parseable PEM key: try the configured OpenSSL ENGINE.
            // The loader returns NULL when no ENGINE is available.
            key = engineKeyLoader.apply(keyFile);
        }
        if (key.equals(MemorySegment.NULL)) {
            for (MemorySegment extra : chain) {
                openssl_h.X509_free(extra);
            }
            openssl_h.X509_free(leaf);
            log.warn(sm.getString("certificateLoader.certificateLoadFailed", hostName,
                    keyFile + " (" + OpenSSLLibrary.getLastError() + ")"));
            return null;
        }

        if (chainFile != null) {
            byte[] chainBytes = readConfigFileBytes(chainFile);
            if (chainBytes == null) {
                log.warn(sm.getString("certificateLoader.certificateLoadFailed", hostName, chainFile));
            } else {
                // Append the separately configured chain to the intermediates
                // (if any) read from the certificate file.
                MemorySegment[] extraChain = readX509ChainFromBytes(chainBytes);
                chain = Arrays.copyOf(chain, chain.length + extraChain.length);
                System.arraycopy(extraChain, 0, chain, chain.length - extraChain.length,
                        extraChain.length);
            }
        }

        return new CertificateEntry(leaf, key, chain);
    }


    /**
     * Loads a PKCS#12 certificate bundle.
     *
     * @param certificate  The certificate configuration
     * @param hostName     The host name the certificate belongs to, used for
     *                     logging
     * @param certFile     The (adjusted) path to the PKCS#12 file
     * @param keyPassToUse The bundle password, or {@code null} if none
     * @param arena        Arena for the native allocations
     *
     * @return The loaded entry, or {@code null} if it could not be loaded
     *         (the failure is logged)
     */
    public static CertificateEntry loadPkcs12(SSLHostConfigCertificate certificate, String hostName,
            String certFile, String keyPassToUse, Arena arena) {
        byte[] fileBytes = readConfigFileBytes(certFile);
        if (fileBytes == null) {
            log.warn(sm.getString("certificateLoader.certificateLoadFailed", hostName, certFile));
            return null;
        }

        try {
            // Parse with the JDK rather than OpenSSL: the MAC check doubles as
            // the password check, the key entry selection matches the Java
            // certificate-chain view used by JMX, and the bundle's
            // intermediate certificates come out as part of the key entry
            // chain without any native stack handling.
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            keyStore.load(new ByteArrayInputStream(fileBytes),
                    (keyPassToUse != null) ? keyPassToUse.toCharArray() : null);
            return loadJavaKeyStoreKey(keyStore, certificate.getCertificateKeyAlias(), keyPassToUse,
                    certFile, hostName, arena);
        } catch (IOException e) {
            // Covers a wrong password: the JDK fails the MAC check
            log.warn(sm.getString("certificateLoader.certificateLoadFailed", hostName,
                    certFile + " (" + sm.getString("certificateLoader.badPassword") + ")"));
            return null;
        } catch (Throwable t) {
            log.warn(sm.getString("certificateLoader.certificateLoadFailed", hostName,
                    certFile + " (" + t.getClass().getName() +
                    (t.getMessage() != null ? ": " + t.getMessage() : "") + ")"), t);
            return null;
        }
    }


    /**
     * Loads a certificate (and private key) from a Java key store, using the
     * configured alias or the first key entry if no alias is configured.
     *
     * @param certificate  The certificate configuration
     * @param hostName     The host name the certificate belongs to, used for
     *                     logging
     * @param keyPassToUse The key passphrase, or {@code null} if none
     * @param arena        Arena for the native allocations
     *
     * @return The loaded entry, or {@code null} if it could not be loaded
     *         (the failure is logged)
     */
    public static CertificateEntry loadKeyStore(SSLHostConfigCertificate certificate, String hostName,
            String keyPassToUse, Arena arena) {
        KeyStore keyStore;
        try {
            keyStore = certificate.getCertificateKeystore();
        } catch (Throwable e) {
            log.warn(sm.getString("certificateLoader.certificateLoadFailed", hostName,
                    certificate.getCertificateKeystoreFile()), e);
            return null;
        }
        if (keyStore == null) {
            log.warn(sm.getString("certificateLoader.certificateLoadFailed", hostName,
                    certificate.getCertificateKeystoreFile()));
            return null;
        }
        return loadJavaKeyStoreKey(keyStore, certificate.getCertificateKeyAlias(), keyPassToUse,
                certificate.getCertificateKeystoreFile(), hostName, arena);
    }


    /**
     * Extracts the key entry of the given Java key store (the configured
     * alias, or the first key entry if no alias is configured) and converts
     * it to native objects. Shared by the Java key store view and the
     * PKCS#12 file view.
     *
     * @param keyStore        The key store to load the entry from
     * @param configuredAlias The configured key alias, or {@code null} to
     *                        use the first key entry
     * @param keyPassToUse    The key passphrase, or {@code null} if none
     * @param source          Key store source, used for logging
     * @param hostName        The host name the entry belongs to, used for
     *                        logging
     * @param arena           Arena for the native allocations
     *
     * @return The loaded entry, or {@code null} if it could not be loaded
     *         (the failure is logged)
     */
    private static CertificateEntry loadJavaKeyStoreKey(KeyStore keyStore, String configuredAlias,
            String keyPassToUse, String source, String hostName, Arena arena) {
        try {
            String alias = resolveKeyAlias(keyStore, configuredAlias);
            if (alias == null || !keyStore.isKeyEntry(alias)) {
                log.warn(sm.getString("certificateLoader.certificateLoadFailed", hostName,
                        source + " (" + sm.getString("certificateLoader.noKeyEntry") + ")"));
                return null;
            }

            char[] keyPass = (keyPassToUse != null) ? keyPassToUse.toCharArray() : new char[0];
            java.security.Key keyObj = keyStore.getKey(alias, keyPass);
            if (!(keyObj instanceof PrivateKey)) {
                log.warn(sm.getString("certificateLoader.certificateLoadFailed", hostName,
                        source + " (" + sm.getString("certificateLoader.noKeyEntry") + ")"));
                return null;
            }
            PrivateKey javaKey = (PrivateKey) keyObj;
            Certificate[] chain = keyStore.getCertificateChain(alias);
            if (chain == null || chain.length == 0) {
                log.warn(sm.getString("certificateLoader.certificateLoadFailed", hostName,
                        source + " (" + sm.getString("certificateLoader.noKeyEntry") + ")"));
                return null;
            }
            X509Certificate[] x509Chain = new X509Certificate[chain.length];
            for (int i = 0; i < chain.length; i++) {
                if (!(chain[i] instanceof X509Certificate)) {
                    log.warn(sm.getString("certificateLoader.certificateLoadFailed", hostName,
                            source + " (" + sm.getString("certificateLoader.noKeyEntry") + ")"));
                    return null;
                }
                x509Chain[i] = (X509Certificate) chain[i];
            }

            CertificateEntry entry = toCertEntry(javaKey, x509Chain, arena);
            if (entry == null) {
                log.warn(sm.getString("certificateLoader.certificateLoadFailed", hostName,
                        source + " (" + OpenSSLLibrary.getLastError() + ")"));
                return null;
            }
            return entry;
        } catch (Throwable e) {
            log.warn(sm.getString("certificateLoader.certificateLoadFailed", hostName, source), e);
            return null;
        }
    }


    /**
     * Converts Java key material (as exposed by a {@link KeyStore}) into the
     * native objects of a {@link CertificateEntry}. Used by the Java key store
     * load path ({@link #loadJavaKeyStoreKey}).
     *
     * @param javaKey The private key (PKCS#8 encodable)
     * @param chain   The certificate chain, leaf first
     * @param arena   Arena for the native allocations
     *
     * @return The new entry owning freshly created native objects, or
     *         {@code null} if any object could not be created (already
     *         created objects are released; the failure is not logged)
     */
    public static CertificateEntry toCertEntry(PrivateKey javaKey, X509Certificate[] chain,
            Arena arena) {
        if (javaKey == null || chain == null || chain.length == 0) {
            return null;
        }
        MemorySegment leaf = MemorySegment.NULL;
        MemorySegment key = MemorySegment.NULL;
        MemorySegment[] chainCerts = new MemorySegment[chain.length - 1];
        int chainLoaded = 0;
        CertificateEntry entry = null;
        try {
            // Private key (PKCS#8 DER via d2i_AutoPrivateKey)
            byte[] keyBytes = javaKey.getEncoded();
            if (keyBytes == null) {
                // Unsupported key encoding
                return null;
            }
            MemorySegment keyNative = arena.allocateFrom(ValueLayout.JAVA_BYTE, keyBytes);
            MemorySegment keyPtr = arena.allocate(ValueLayout.ADDRESS);
            keyPtr.set(ValueLayout.ADDRESS, 0, keyNative);
            key = openssl_h.d2i_AutoPrivateKey(
                    MemorySegment.NULL, keyPtr, (long) keyBytes.length);
            if (key.equals(MemorySegment.NULL)) {
                return null;
            }

            // Leaf certificate
            leaf = derToX509(chain[0].getEncoded(), arena);
            if (leaf.equals(MemorySegment.NULL)) {
                return null;
            }

            // Intermediate chain certificates (skip the leaf)
            for (int i = 1; i < chain.length; i++) {
                chainCerts[i - 1] = derToX509(chain[i].getEncoded(), arena);
                if (chainCerts[i - 1].equals(MemorySegment.NULL)) {
                    return null;
                }
                chainLoaded++;
            }

            entry = new CertificateEntry(leaf, key, chainCerts);
            return entry;
        } catch (Throwable t) {
            entry = null;
            return null;
        } finally {
            if (entry == null) {
                // Release the partially built native objects (freeCertEntry
                // ignores a NULL leaf/key and the failed NULL element is
                // outside the copied range).
                freeCertEntry(new CertificateEntry(leaf, key,
                        Arrays.copyOfRange(chainCerts, 0, chainLoaded)));
            }
        }
    }


    // ------------------------------------- File and native reading helpers

    /**
     * Reads a configuration file (relative paths are resolved against the
     * Catalina base directory) into a byte array.
     *
     * @param path The path of the file to read
     *
     * @return the file content, or {@code null} if the file cannot be read
     */
    public static byte[] readConfigFileBytes(String path) {
        try (InputStream is = ConfigFileLoader.getSource().getResource(path).getInputStream();
                ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = is.read(buffer)) != -1) {
                bos.write(buffer, 0, read);
            }
            return bos.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }


    /**
     * Creates a memory BIO containing the given bytes.
     */
    private static MemorySegment createMemoryBio(byte[] bytes) {
        MemorySegment bio = openssl_h.BIO_new(openssl_h.BIO_s_mem());
        if (bio.equals(MemorySegment.NULL)) {
            return MemorySegment.NULL;
        }
        // BIO_write copies the data, so a short-lived arena is sufficient
        try (Arena local = Arena.ofConfined()) {
            MemorySegment data = local.allocateFrom(ValueLayout.JAVA_BYTE, bytes);
            if (openssl_h.BIO_write(bio, data, bytes.length) <= 0) {
                openssl_h.BIO_free(bio);
                return MemorySegment.NULL;
            }
        }
        return bio;
    }


    /**
     * Reads the first X.509 certificate from a byte array (PEM with DER
     * fallback, matching the behaviour of the OpenSSL SSL endpoint).
     */
    private static MemorySegment readX509FromBytes(byte[] bytes) {
        MemorySegment bio = createMemoryBio(bytes);
        if (bio.equals(MemorySegment.NULL)) {
            return MemorySegment.NULL;
        }
        try {
            MemorySegment cert = openssl_h.PEM_read_bio_X509_AUX(bio, MemorySegment.NULL,
                    MemorySegment.NULL, MemorySegment.NULL);
            if (cert.equals(MemorySegment.NULL) &&
                    // EOF is accepted, then try again
                    ((openssl_h.ERR_peek_last_error() & openssl_h.ERR_REASON_MASK())
                            == openssl_h.PEM_R_NO_START_LINE())) {
                openssl_h.ERR_clear_error();
                MemorySegment bio2 = createMemoryBio(bytes);
                if (!bio2.equals(MemorySegment.NULL)) {
                    try {
                        cert = openssl_h.d2i_X509_bio(bio2, MemorySegment.NULL);
                    } finally {
                        openssl_h.BIO_free(bio2);
                    }
                }
            }
            return cert;
        } finally {
            openssl_h.BIO_free(bio);
        }
    }


    /**
     * Reads all X.509 certificates from a PEM byte array.
     */
    private static MemorySegment[] readX509ChainFromBytes(byte[] bytes) {
        MemorySegment bio = createMemoryBio(bytes);
        if (bio.equals(MemorySegment.NULL)) {
            return new MemorySegment[0];
        }
        List<MemorySegment> certs = new ArrayList<>();
        try {
            MemorySegment cert = openssl_h.PEM_read_bio_X509_AUX(bio, MemorySegment.NULL,
                    MemorySegment.NULL, MemorySegment.NULL);
            while (!cert.equals(MemorySegment.NULL)) {
                certs.add(cert);
                cert = openssl_h.PEM_read_bio_X509_AUX(bio, MemorySegment.NULL,
                        MemorySegment.NULL, MemorySegment.NULL);
            }
            // EOF is accepted, otherwise log an error
            if ((openssl_h.ERR_peek_last_error() & openssl_h.ERR_REASON_MASK())
                    != openssl_h.PEM_R_NO_START_LINE()) {
                log.warn(sm.getString("certificateLoader.chainReadError",
                        OpenSSLLibrary.getLastError()));
            } else {
                openssl_h.ERR_clear_error();
            }
        } finally {
            openssl_h.BIO_free(bio);
        }
        return certs.toArray(new MemorySegment[0]);
    }


    /**
     * Reads a PEM encoded private key. The configured key passphrase is
     * supplied to OpenSSL through the callback, so the read is deterministic
     * and a single attempt is made.
     *
     * @return the parsed key, or {@code MemorySegment.NULL} if the key could
     *         not be read (for example, because the passphrase is wrong)
     */
    private static MemorySegment readPrivateKeyFromBytes(byte[] bytes, String keyPassToUse,
            Arena arena) {
        MemorySegment bio = createMemoryBio(bytes);
        if (bio.equals(MemorySegment.NULL)) {
            return MemorySegment.NULL;
        }
        try {
            return openssl_h.PEM_read_bio_PrivateKey(bio, MemorySegment.NULL,
                    pem_password_cb.allocate(
                            (buf, size, rwflag, user) -> {
                                if (keyPassToUse == null || keyPassToUse.isEmpty()) {
                                    return 0;
                                }
                                try (Arena localArena = Arena.ofConfined()) {
                                    MemorySegment passwordNative =
                                            localArena.allocateFrom(keyPassToUse);
                                    if (passwordNative.byteSize() > size) {
                                        // The password is too long
                                        return 0;
                                    }
                                    MemorySegment bufSegment = buf.reinterpret(size,
                                            localArena, null);
                                    bufSegment.copyFrom(passwordNative);
                                    // OpenSSL's contract (PEM_def_callback) is to
                                    // return the password length excluding the NUL
                                    // terminator: PEM_do_header() feeds the returned
                                    // length straight into EVP_BytesToKey, so
                                    // reporting the terminator as a password byte
                                    // derives a different (wrong) key. The NUL
                                    // itself is copied and can stay in buf. Note:
                                    // the upstream-derived PasswordCallback in
                                    // OpenSSLContext still reports the terminator
                                    // as a password byte (byteSize(), including the
                                    // NUL) - a known upstream inconsistency with
                                    // this, correct, contract; not replicated here.
                                    return (int) passwordNative.byteSize() - 1;
                                }
                            }, arena),
                    MemorySegment.NULL);
        } finally {
            openssl_h.BIO_free(bio);
        }
    }


    /**
     * Decodes a DER encoded X.509 certificate.
     */
    private static MemorySegment derToX509(byte[] der, Arena arena) {
        MemorySegment derNative = arena.allocateFrom(ValueLayout.JAVA_BYTE, der);
        MemorySegment inPtr = arena.allocate(ValueLayout.ADDRESS);
        inPtr.set(ValueLayout.ADDRESS, 0, derNative);
        return openssl_h.d2i_X509(MemorySegment.NULL, inPtr, der.length);
    }


    // ------------------------------------- Release

    /**
     * Releases the native {@code X509} and {@code EVP_PKEY} objects owned by
     * an entry. They live on the OpenSSL heap, so closing the arena that held
     * the entry's allocations does not free them; each entry is released
     * exactly once when the configuration snapshot that owns it is discarded.
     *
     * @param entry The entry whose native objects to release (may be null)
     */
    public static void freeCertEntry(CertificateEntry entry) {
        if (entry == null) {
            return;
        }
        for (MemorySegment chainCert : entry.chain) {
            if (chainCert != null && !chainCert.equals(MemorySegment.NULL)) {
                openssl_h.X509_free(chainCert);
            }
        }
        if (entry.leaf != null && !entry.leaf.equals(MemorySegment.NULL)) {
            openssl_h.X509_free(entry.leaf);
        }
        if (entry.key != null && !entry.key.equals(MemorySegment.NULL)) {
            openssl_h.EVP_PKEY_free(entry.key);
        }
    }


    /**
     * Closes the arena, ignoring any failure (the native objects may already
     * be freed or the arena may already be closed).
     *
     * @param arena The arena to close (may be null)
     */
    public static void closeQuietly(Arena arena) {
        if (arena != null) {
            try {
                arena.close();
            } catch (Throwable t) {
                // Ignore
            }
        }
    }


    // ------------------------------------- X.509 chain reading (JMX / expiry)

    /**
     * Reads the X.509 certificate chain for the given certificate entry from
     * the same source used by
     * {@link #loadCertEntry(SSLHostConfigCertificate, Arena)}, but as Java
     * {@link X509Certificate}s rather than native objects. Used to populate
     * the certificate data exposed via JMX and read by the certificate expiry
     * checks, without a native OpenSSL context.
     *
     * @param certificate The certificate entry to read the chain from
     *
     * @return The certificate chain, or {@code null} if it could not be read
     */
    public static X509Certificate[] getCertificateChain(
            SSLHostConfigCertificate certificate) {
        try {
            String certFile = certificate.getCertificateFile();
            if (certFile != null) {
                certFile = SSLHostConfig.adjustRelativePath(certFile);
                if (certFile.toLowerCase(Locale.ENGLISH).endsWith(".pkcs12")) {
                    return getCertificateChainPkcs12(certificate, certFile);
                }
                return getCertificateChainPem(certificate, certFile);
            }
            return getCertificateChainKeyStore(certificate);
        } catch (Exception e) {
            return null;
        }
    }


    /**
     * Returns the certificate chain from a PEM certificate file. The
     * certificate file may hold the intermediate certificates after the
     * leaf; a separately configured chain file is appended to the chain.
     *
     * @param certificate The certificate entry the file belongs to
     * @param certFile    The (adjusted) path to the certificate file
     *
     * @return The certificate chain, or {@code null} if it could not be read
     */
    private static X509Certificate[] getCertificateChainPem(
            SSLHostConfigCertificate certificate, String certFile)
            throws Exception {
        byte[] fileBytes = readConfigFileBytes(certFile);
        if (fileBytes == null) {
            return null;
        }

        List<X509Certificate> chain = new ArrayList<>();
        CertificateFactory factory = CertificateFactory.getInstance("X.509");
        addX509Certificates(chain, factory, fileBytes);

        String chainFile = certificate.getCertificateChainFile();
        if (chainFile != null) {
            chainFile = SSLHostConfig.adjustRelativePath(chainFile);
            byte[] chainBytes = readConfigFileBytes(chainFile);
            if (chainBytes != null) {
                addX509Certificates(chain, factory, chainBytes);
            }
        }

        return chain.isEmpty() ? null : chain.toArray(new X509Certificate[0]);
    }


    /**
     * Appends every X.509 certificate found in the given (PEM or DER
     * encoded) file to the given chain.
     */
    private static void addX509Certificates(List<X509Certificate> chain,
            CertificateFactory factory, byte[] fileBytes) throws Exception {
        for (Certificate cert : factory.generateCertificates(
                new ByteArrayInputStream(fileBytes))) {
            if (cert instanceof X509Certificate) {
                chain.add((X509Certificate) cert);
            }
        }
    }


    /**
     * Returns the certificate chain from a PKCS#12 file, using the key entry
     * matching the configured alias or, if no alias is configured, the first
     * key entry in the bundle.
     *
     * @param certificate The certificate entry the file belongs to
     * @param certFile    The (adjusted) path to the PKCS#12 file
     *
     * @return The certificate chain, or {@code null} if it could not be read
     */
    private static X509Certificate[] getCertificateChainPkcs12(
            SSLHostConfigCertificate certificate, String certFile)
            throws Exception {
        byte[] fileBytes = readConfigFileBytes(certFile);
        if (fileBytes == null) {
            return null;
        }
        String password = resolveKeyPassword(certificate);
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(new ByteArrayInputStream(fileBytes),
                (password != null) ? password.toCharArray() : null);
        return getCertificateChainKeyStore(keyStore,
                certificate.getCertificateKeyAlias());
    }


    /**
     * Returns the certificate chain from the Java key store configured for
     * the given certificate entry, using the configured alias or the first
     * key entry if no alias is configured.
     *
     * @param certificate The certificate entry to read the chain from
     *
     * @return The certificate chain, or {@code null} if it could not be read
     */
    private static X509Certificate[] getCertificateChainKeyStore(
            SSLHostConfigCertificate certificate) throws Exception {
        KeyStore keyStore = certificate.getCertificateKeystore();
        if (keyStore == null) {
            return null;
        }
        return getCertificateChainKeyStore(keyStore,
                certificate.getCertificateKeyAlias());
    }


    /**
     * Extracts the X.509 certificate chain for the given alias from the
     * given key store. If no alias is configured, the first key entry in the
     * key store is used.
     *
     * @param keyStore        The key store to read the chain from
     * @param configuredAlias The configured key alias, or {@code null} to
     *                        use the first key entry
     *
     * @return The certificate chain, or {@code null} if it could not be read
     */
    private static X509Certificate[] getCertificateChainKeyStore(
            KeyStore keyStore, String configuredAlias) throws Exception {
        String alias = resolveKeyAlias(keyStore, configuredAlias);
        if (alias == null || !keyStore.isKeyEntry(alias)) {
            return null;
        }
        Certificate[] chain = keyStore.getCertificateChain(alias);
        if (chain == null || chain.length == 0) {
            return null;
        }
        X509Certificate[] result = new X509Certificate[chain.length];
        for (int i = 0; i < chain.length; i++) {
            if (!(chain[i] instanceof X509Certificate)) {
                return null;
            }
            result[i] = (X509Certificate) chain[i];
        }
        return result;
    }

    // ------------------------------------- Pre-shared key find-session callback

    /**
     * Installs the TLS 1.3 server-side pre-shared key callback on the given
     * context.
     *
     * @param sslCtx    The native SSL_CTX to configure
     * @param resolver  Maps the connection's SSL object to the selector for
     *                  the pre-shared keys to use for that connection. A
     *                  {@code null} result means no pre-shared keys are
     *                  configured for the host the connection is served by,
     *                  and the handshake continues without pre-shared key
     *                  authentication
     * @param arena     The arena that owns the callback stub
     */
    public static void installFindSessionCallback(MemorySegment sslCtx,
            Function<MemorySegment, OpenSSLPreSharedKeySelector> resolver, Arena arena) {
        openssl_h.SSL_CTX_set_psk_find_session_callback(sslCtx,
                SSL_psk_find_session_cb_func.allocate(new FindSessionCallback(resolver), arena));
    }


    /**
     * The {@code psk_find_session} callback. Resolves the selector for the
     * connection, looks the offered identity up and, when it matches, builds
     * a TLS 1.3 {@code SSL_SESSION} holding the pre-shared key as the session
     * master key. Returning {@code 1} with no session tells OpenSSL to ignore
     * the offered identity and continue with a full handshake, mirroring the
     * native connector's behaviour.
     * <p>
     * The whole callback body is guarded against any throwable: an upcall
     * that lets one escape terminates the JVM, so a failure is logged and
     * reported to OpenSSL as a rejected identity instead.
     */
    private static final class FindSessionCallback
            implements SSL_psk_find_session_cb_func.Function {

        private final Function<MemorySegment, OpenSSLPreSharedKeySelector> resolver;

        FindSessionCallback(
                Function<MemorySegment, OpenSSLPreSharedKeySelector> resolver) {
            this.resolver = resolver;
        }

        @Override
        public int apply(MemorySegment ssl, MemorySegment identity, long identityLength,
                MemorySegment sessionPointer) {
            // A throwable escaping an FFM upcall ends the process ("an
            // unchecked exception thrown by an upcall is unrecoverable"),
            // so the callback body is fully guarded: nothing - not even an
            // Error - may cross the native boundary. A failure rejects the
            // offered identity (return 0, the same outcome as the malformed
            // key/cipher failure paths below) instead of taking down the
            // JVM from inside one handshake.
            try {
                return findSession(ssl, identity, identityLength, sessionPointer);
            } catch (Throwable t) {
                log.error(sm.getString("certificateLoader.pskFindSessionError"), t);
                return 0;
            }
        }


        private int findSession(MemorySegment ssl, MemorySegment identity,
                long identityLength, MemorySegment sessionPointer) {
            try (var localArena = Arena.ofConfined()) {
                MemorySegment sessionPointerSegment =
                        sessionPointer.reinterpret(ValueLayout.ADDRESS.byteSize(), localArena, null);
                sessionPointerSegment.set(ValueLayout.ADDRESS, 0, MemorySegment.NULL);
                if (MemorySegment.NULL.equals(identity) || identityLength < 0 || identityLength > Integer.MAX_VALUE) {
                    return 0;
                }

                OpenSSLPreSharedKeySelector selector = resolver.apply(ssl);
                if (selector == null) {
                    // No pre-shared keys configured for the host this
                    // connection is served by; continue without PSK.
                    return 1;
                }

                byte[] identityBytes =
                        identity.reinterpret(identityLength, localArena, null).toArray(ValueLayout.JAVA_BYTE);
                int[] cipherSuite = new int[1];
                byte[] key = selector.select(ssl.address(), identityBytes, cipherSuite);
                if (key == null) {
                    return 1;
                }
                if (key.length == 0 || cipherSuite[0] <= 0 || cipherSuite[0] > 0xFFFF) {
                    return 0;
                }

                byte[] cipherId = new byte[] { (byte) (cipherSuite[0] >> 8), (byte) cipherSuite[0] };
                MemorySegment cipher = openssl_h.SSL_CIPHER_find(ssl,
                        localArena.allocateFrom(ValueLayout.JAVA_BYTE, cipherId));
                if (MemorySegment.NULL.equals(cipher)
                        || !Constants.SSL_PROTO_TLSv1_3.equals(
                                openssl_h.SSL_CIPHER_get_version(cipher).getString(0))) {
                    return 0;
                }

                MemorySegment session = openssl_h.SSL_SESSION_new();
                if (MemorySegment.NULL.equals(session)) {
                    return 0;
                }
                boolean success = false;
                try {
                    MemorySegment keySegment = localArena.allocateFrom(ValueLayout.JAVA_BYTE, key);
                    try {
                        // The session id context must be the one the server
                        // context uses; it is shared with OpenSSLContext where
                        // the socket contexts set it.
                        MemorySegment sidCtxSegment = localArena.allocateFrom(ValueLayout.JAVA_BYTE,
                                DEFAULT_SESSION_ID_CONTEXT);
                        if (openssl_h.SSL_SESSION_set1_master_key(session, keySegment, key.length) == 0 ||
                                openssl_h.SSL_SESSION_set_cipher(session, cipher) == 0 ||
                                openssl_h.SSL_SESSION_set_protocol_version(session,
                                        openssl_h.TLS1_3_VERSION()) == 0 ||
                                openssl_h.SSL_SESSION_set1_id_context(session, sidCtxSegment,
                                        DEFAULT_SESSION_ID_CONTEXT.length) == 0) {
                            return 0;
                        }
                    } finally {
                        keySegment.fill((byte) 0);
                    }
                    sessionPointerSegment.set(ValueLayout.ADDRESS, 0, session);
                    success = true;
                    return 1;
                } finally {
                    if (!success) {
                        openssl_h.SSL_SESSION_free(session);
                    }
                }
            }
        }
    }
}
