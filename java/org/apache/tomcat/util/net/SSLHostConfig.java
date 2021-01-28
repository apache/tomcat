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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Serializable;
import java.security.KeyStore;
import java.security.UnrecoverableKeyException;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javax.management.ObjectName;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.net.openssl.OpenSSLConf;
import org.apache.tomcat.util.net.openssl.ciphers.Cipher;
import org.apache.tomcat.util.net.openssl.ciphers.OpenSSLCipherConfigurationParser;
import org.apache.tomcat.util.res.StringManager;

/**
 * Represents the TLS configuration for a virtual host.
 */
public class SSLHostConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    private static final Log log = LogFactory.getLog(SSLHostConfig.class);
    private static final StringManager sm = StringManager.getManager(SSLHostConfig.class);

    // Must be lower case. SSL host names are always stored using lower case as
    // they are case insensitive but are used by case sensitive code such as
    // keys in Maps.
    protected static final String DEFAULT_SSL_HOST_NAME = "_default_";
    protected static final Set<String> SSL_PROTO_ALL_SET = new HashSet<>();

    static {
        /* Default used if protocols are not configured, also used if
         * protocols="All"
         */
        SSL_PROTO_ALL_SET.add(Constants.SSL_PROTO_SSLv2Hello);
        SSL_PROTO_ALL_SET.add(Constants.SSL_PROTO_TLSv1);
        SSL_PROTO_ALL_SET.add(Constants.SSL_PROTO_TLSv1_1);
        SSL_PROTO_ALL_SET.add(Constants.SSL_PROTO_TLSv1_2);
        SSL_PROTO_ALL_SET.add(Constants.SSL_PROTO_TLSv1_3);
    }

    private Type configType = null;

    private String hostName = DEFAULT_SSL_HOST_NAME;

    private transient Long openSslConfContext = Long.valueOf(0);
    // OpenSSL can handle multiple certs in a single config so the reference to
    // the context is here at the virtual host level. JSSE can't so the
    // reference is held on the certificate.
    private transient Long openSslContext = Long.valueOf(0);

    // Configuration properties

    // Internal
    private String[] enabledCiphers;
    private String[] enabledProtocols;
    private ObjectName oname;
    // Need to know if TLS 1.3 has been explicitly requested as a warning needs
    // to generated if it is explicitly requested for a JVM that does not
    // support it. Uses a set so it is extensible for TLS 1.4 etc.
    private Set<String> explicitlyRequestedProtocols = new HashSet<>();
    // Nested
    private SSLHostConfigCertificate defaultCertificate = null;
    private Set<SSLHostConfigCertificate> certificates = new LinkedHashSet<>(4);
    // Common
    private String certificateRevocationListFile;
    private CertificateVerification certificateVerification = CertificateVerification.NONE;
    private int certificateVerificationDepth = 10;
    // Used to track if certificateVerificationDepth has been explicitly set
    private boolean certificateVerificationDepthConfigured = false;
    private String ciphers = "HIGH:!aNULL:!eNULL:!EXPORT:!DES:!RC4:!MD5:!kRSA";
    private LinkedHashSet<Cipher> cipherList = null;
    private List<String> jsseCipherNames = null;
    private boolean honorCipherOrder = false;
    private Set<String> protocols = new HashSet<>();
    // Values <0 mean use the implementation default
    private int sessionCacheSize = -1;
    private int sessionTimeout = 86400;
    // JSSE
    private String keyManagerAlgorithm = KeyManagerFactory.getDefaultAlgorithm();
    private boolean revocationEnabled = false;
    private String sslProtocol = Constants.SSL_PROTO_TLS;
    private String trustManagerClassName;
    private String truststoreAlgorithm = TrustManagerFactory.getDefaultAlgorithm();
    private String truststoreFile = System.getProperty("javax.net.ssl.trustStore");
    private String truststorePassword = System.getProperty("javax.net.ssl.trustStorePassword");
    private String truststoreProvider = System.getProperty("javax.net.ssl.trustStoreProvider");
    private String truststoreType = System.getProperty("javax.net.ssl.trustStoreType");
    private transient KeyStore truststore = null;
    // OpenSSL
    private String certificateRevocationListPath;
    private String caCertificateFile;
    private String caCertificatePath;
    private boolean disableCompression = true;
    private boolean disableSessionTickets = false;
    private boolean insecureRenegotiation = false;
    private OpenSSLConf openSslConf = null;

    public SSLHostConfig() {
        // Set defaults that can't be (easily) set when defining the fields.
        setProtocols(Constants.SSL_PROTO_ALL);
    }


    public Long getOpenSslConfContext() {
        return openSslConfContext;
    }


    public void setOpenSslConfContext(Long openSslConfContext) {
        this.openSslConfContext = openSslConfContext;
    }


    public Long getOpenSslContext() {
        return openSslContext;
    }


    public void setOpenSslContext(Long openSslContext) {
        this.openSslContext = openSslContext;
    }


    // Expose in String form for JMX
    public String getConfigType() {
        return configType.name();
    }


    /**
     * Set property which belongs to the specified configuration type.
     * @param name the property name
     * @param configType the configuration type
     * @return true if the property belongs to the current configuration,
     *   and false otherwise
     */
    boolean setProperty(String name, Type configType) {
        if (this.configType == null) {
            this.configType = configType;
        } else {
            if (configType != this.configType) {
                log.warn(sm.getString("sslHostConfig.mismatch",
                        name, getHostName(), configType, this.configType));
                return false;
            }
        }
        return true;
    }


    // ----------------------------------------------------- Internal properties

    /**
     * @see SSLUtil#getEnabledProtocols()
     *
     * @return The protocols enabled for this TLS virtual host
     */
    public String[] getEnabledProtocols() {
        return enabledProtocols;
    }


    public void setEnabledProtocols(String[] enabledProtocols) {
        this.enabledProtocols = enabledProtocols;
    }


    /**
     * @see SSLUtil#getEnabledCiphers()
     *
     * @return The ciphers enabled for this TLS virtual host
     */
    public String[] getEnabledCiphers() {
        return enabledCiphers;
    }


    public void setEnabledCiphers(String[] enabledCiphers) {
        this.enabledCiphers = enabledCiphers;
    }


    public ObjectName getObjectName() {
        return oname;
    }


    public void setObjectName(ObjectName oname) {
        this.oname = oname;
    }


    // ------------------------------------------- Nested configuration elements

    private void registerDefaultCertificate() {
        if (defaultCertificate == null) {
            SSLHostConfigCertificate defaultCertificate = new SSLHostConfigCertificate(
                    this, SSLHostConfigCertificate.Type.UNDEFINED);
            addCertificate(defaultCertificate);
            this.defaultCertificate = defaultCertificate;
        }
    }


    public void addCertificate(SSLHostConfigCertificate certificate) {
        // Need to make sure that if there is more than one certificate, none of
        // them have a type of undefined.
        if (certificates.size() == 0) {
            certificates.add(certificate);
            return;
        }

        if (certificates.size() == 1 &&
                certificates.iterator().next().getType() == SSLHostConfigCertificate.Type.UNDEFINED ||
                        certificate.getType() == SSLHostConfigCertificate.Type.UNDEFINED) {
            // Invalid config
            throw new IllegalArgumentException(sm.getString("sslHostConfig.certificate.notype"));
        }

        certificates.add(certificate);
    }


    public OpenSSLConf getOpenSslConf() {
        return openSslConf;
    }


    public void setOpenSslConf(OpenSSLConf conf) {
        if (conf == null) {
            throw new IllegalArgumentException(sm.getString("sslHostConfig.opensslconf.null"));
        } else if (openSslConf != null) {
            throw new IllegalArgumentException(sm.getString("sslHostConfig.opensslconf.alreadySet"));
        }
        setProperty("<OpenSSLConf>", Type.OPENSSL);
        openSslConf = conf;
    }


    public Set<SSLHostConfigCertificate> getCertificates() {
        return getCertificates(false);
    }


    public Set<SSLHostConfigCertificate> getCertificates(boolean createDefaultIfEmpty) {
        if (certificates.size() == 0 && createDefaultIfEmpty) {
            registerDefaultCertificate();
        }
        return certificates;
    }


    // ----------------------------------------- Common configuration properties

    // TODO: This certificate setter can be removed once it is no longer
    // necessary to support the old configuration attributes (Tomcat 10?).

    public String getCertificateKeyPassword() {
        if (defaultCertificate == null) {
            return null;
        } else {
            return defaultCertificate.getCertificateKeyPassword();
        }
    }
    public void setCertificateKeyPassword(String certificateKeyPassword) {
        registerDefaultCertificate();
        defaultCertificate.setCertificateKeyPassword(certificateKeyPassword);
    }


    public void setCertificateRevocationListFile(String certificateRevocationListFile) {
        this.certificateRevocationListFile = certificateRevocationListFile;
    }


    public String getCertificateRevocationListFile() {
        return certificateRevocationListFile;
    }


    public void setCertificateVerification(String certificateVerification) {
        try {
            this.certificateVerification =
                    CertificateVerification.fromString(certificateVerification);
        } catch (IllegalArgumentException iae) {
            // If the specified value is not recognised, default to the
            // strictest possible option.
            this.certificateVerification = CertificateVerification.REQUIRED;
            throw iae;
        }
    }


    public CertificateVerification getCertificateVerification() {
        return certificateVerification;
    }


    public void setCertificateVerificationAsString(String certificateVerification) {
        setCertificateVerification(certificateVerification);
    }


    public String getCertificateVerificationAsString() {
        return certificateVerification.toString();
    }


    public void setCertificateVerificationDepth(int certificateVerificationDepth) {
        this.certificateVerificationDepth = certificateVerificationDepth;
        certificateVerificationDepthConfigured = true;
    }


    public int getCertificateVerificationDepth() {
        return certificateVerificationDepth;
    }


    public boolean isCertificateVerificationDepthConfigured() {
        return certificateVerificationDepthConfigured;
    }


    /**
     * Set the new cipher configuration. Note: Regardless of the format used to
     * set the configuration, it is always stored in OpenSSL format.
     *
     * @param ciphersList The new cipher configuration in OpenSSL or JSSE format
     */
    public void setCiphers(String ciphersList) {
        // Ciphers is stored in OpenSSL format. Convert the provided value if
        // necessary.
        if (ciphersList != null && !ciphersList.contains(":")) {
            StringBuilder sb = new StringBuilder();
            // Not obviously in OpenSSL format. May be a single OpenSSL or JSSE
            // cipher name. May be a comma separated list of cipher names
            String ciphers[] = ciphersList.split(",");
            for (String cipher : ciphers) {
                String trimmed = cipher.trim();
                if (trimmed.length() > 0) {
                    String openSSLName = OpenSSLCipherConfigurationParser.jsseToOpenSSL(trimmed);
                    if (openSSLName == null) {
                        // Not a JSSE name. Maybe an OpenSSL name or alias
                        openSSLName = trimmed;
                    }
                    if (sb.length() > 0) {
                        sb.append(':');
                    }
                    sb.append(openSSLName);
                }
            }
            this.ciphers = sb.toString();
        } else {
            this.ciphers = ciphersList;
        }
        this.cipherList = null;
        this.jsseCipherNames = null;
    }


    /**
     * @return An OpenSSL cipher string for the current configuration.
     */
    public String getCiphers() {
        return ciphers;
    }


    public LinkedHashSet<Cipher> getCipherList() {
        if (cipherList == null) {
            cipherList = OpenSSLCipherConfigurationParser.parse(getCiphers());
        }
        return cipherList;
    }


    /**
     * Obtain the list of JSSE cipher names for the current configuration.
     * Ciphers included in the configuration but not supported by JSSE will be
     * excluded from this list.
     *
     * @return A list of the JSSE cipher names
     */
    public List<String> getJsseCipherNames() {
        if (jsseCipherNames == null) {
            jsseCipherNames = OpenSSLCipherConfigurationParser.convertForJSSE(getCipherList());
        }
        return jsseCipherNames;
    }


    public void setHonorCipherOrder(boolean honorCipherOrder) {
        this.honorCipherOrder = honorCipherOrder;
    }


    public boolean getHonorCipherOrder() {
        return honorCipherOrder;
    }


    public void setHostName(String hostName) {
        this.hostName = hostName.toLowerCase(Locale.ENGLISH);
    }


    /**
     * @return The host name associated with this SSL configuration - always in
     *         lower case.
     */
    public String getHostName() {
        return hostName;
    }


    public void setProtocols(String input) {
        protocols.clear();
        explicitlyRequestedProtocols.clear();

        // List of protocol names, separated by ",", "+" or "-".
        // Semantics is adding ("+") or removing ("-") from left
        // to right, starting with an empty protocol set.
        // Tokens are individual protocol names or "all" for a
        // default set of supported protocols.
        // Separator "," is only kept for compatibility and has the
        // same semantics as "+", except that it warns about a potentially
        // missing "+" or "-".

        // Split using a positive lookahead to keep the separator in
        // the capture so we can check which case it is.
        for (String value: input.split("(?=[-+,])")) {
            String trimmed = value.trim();
            // Ignore token which only consists of prefix character
            if (trimmed.length() > 1) {
                if (trimmed.charAt(0) == '+') {
                    trimmed = trimmed.substring(1).trim();
                    if (trimmed.equalsIgnoreCase(Constants.SSL_PROTO_ALL)) {
                        protocols.addAll(SSL_PROTO_ALL_SET);
                    } else {
                        protocols.add(trimmed);
                        explicitlyRequestedProtocols.add(trimmed);
                    }
                } else if (trimmed.charAt(0) == '-') {
                    trimmed = trimmed.substring(1).trim();
                    if (trimmed.equalsIgnoreCase(Constants.SSL_PROTO_ALL)) {
                        protocols.removeAll(SSL_PROTO_ALL_SET);
                    } else {
                        protocols.remove(trimmed);
                        explicitlyRequestedProtocols.remove(trimmed);
                    }
                } else {
                    if (trimmed.charAt(0) == ',') {
                        trimmed = trimmed.substring(1).trim();
                    }
                    if (!protocols.isEmpty()) {
                        log.warn(sm.getString("sslHostConfig.prefix_missing",
                                 trimmed, getHostName()));
                    }
                    if (trimmed.equalsIgnoreCase(Constants.SSL_PROTO_ALL)) {
                        protocols.addAll(SSL_PROTO_ALL_SET);
                    } else {
                        protocols.add(trimmed);
                        explicitlyRequestedProtocols.add(trimmed);
                    }
                }
            }
        }
    }


    public Set<String> getProtocols() {
        return protocols;
    }


    boolean isExplicitlyRequestedProtocol(String protocol) {
        return explicitlyRequestedProtocols.contains(protocol);
    }


    public void setSessionCacheSize(int sessionCacheSize) {
        this.sessionCacheSize = sessionCacheSize;
    }


    public int getSessionCacheSize() {
        return sessionCacheSize;
    }


    public void setSessionTimeout(int sessionTimeout) {
        this.sessionTimeout = sessionTimeout;
    }


    public int getSessionTimeout() {
        return sessionTimeout;
    }


    // ---------------------------------- JSSE specific configuration properties

    // TODO: These certificate setters can be removed once it is no longer
    // necessary to support the old configuration attributes (Tomcat 10?).

    public String getCertificateKeyAlias() {
        if (defaultCertificate == null) {
            return null;
        } else {
            return defaultCertificate.getCertificateKeyAlias();
        }
    }
    public void setCertificateKeyAlias(String certificateKeyAlias) {
        registerDefaultCertificate();
        defaultCertificate.setCertificateKeyAlias(certificateKeyAlias);
    }


    public String getCertificateKeystoreFile() {
        if (defaultCertificate == null) {
            return null;
        } else {
            return defaultCertificate.getCertificateKeystoreFile();
        }
    }
    public void setCertificateKeystoreFile(String certificateKeystoreFile) {
        registerDefaultCertificate();
        defaultCertificate.setCertificateKeystoreFile(certificateKeystoreFile);
    }


    public String getCertificateKeystorePassword() {
        if (defaultCertificate == null) {
            return null;
        } else {
            return defaultCertificate.getCertificateKeystorePassword();
        }
    }
    public void setCertificateKeystorePassword(String certificateKeystorePassword) {
        registerDefaultCertificate();
        defaultCertificate.setCertificateKeystorePassword(certificateKeystorePassword);
    }


    public String getCertificateKeystoreProvider() {
        if (defaultCertificate == null) {
            return null;
        } else {
            return defaultCertificate.getCertificateKeystoreProvider();
        }
    }
    public void setCertificateKeystoreProvider(String certificateKeystoreProvider) {
        registerDefaultCertificate();
        defaultCertificate.setCertificateKeystoreProvider(certificateKeystoreProvider);
    }


    public String getCertificateKeystoreType() {
        if (defaultCertificate == null) {
            return null;
        } else {
            return defaultCertificate.getCertificateKeystoreType();
        }
    }
    public void setCertificateKeystoreType(String certificateKeystoreType) {
        registerDefaultCertificate();
        defaultCertificate.setCertificateKeystoreType(certificateKeystoreType);
    }


    public void setKeyManagerAlgorithm(String keyManagerAlgorithm) {
        setProperty("keyManagerAlgorithm", Type.JSSE);
        this.keyManagerAlgorithm = keyManagerAlgorithm;
    }


    public String getKeyManagerAlgorithm() {
        return keyManagerAlgorithm;
    }


    public void setRevocationEnabled(boolean revocationEnabled) {
        setProperty("revocationEnabled", Type.JSSE);
        this.revocationEnabled = revocationEnabled;
    }


    public boolean getRevocationEnabled() {
        return revocationEnabled;
    }


    public void setSslProtocol(String sslProtocol) {
        setProperty("sslProtocol", Type.JSSE);
        this.sslProtocol = sslProtocol;
    }


    public String getSslProtocol() {
        return sslProtocol;
    }


    public void setTrustManagerClassName(String trustManagerClassName) {
        setProperty("trustManagerClassName", Type.JSSE);
        this.trustManagerClassName = trustManagerClassName;
    }


    public String getTrustManagerClassName() {
        return trustManagerClassName;
    }


    public void setTruststoreAlgorithm(String truststoreAlgorithm) {
        setProperty("truststoreAlgorithm", Type.JSSE);
        this.truststoreAlgorithm = truststoreAlgorithm;
    }


    public String getTruststoreAlgorithm() {
        return truststoreAlgorithm;
    }


    public void setTruststoreFile(String truststoreFile) {
        setProperty("truststoreFile", Type.JSSE);
        this.truststoreFile = truststoreFile;
    }


    public String getTruststoreFile() {
        return truststoreFile;
    }


    public void setTruststorePassword(String truststorePassword) {
        setProperty("truststorePassword", Type.JSSE);
        this.truststorePassword = truststorePassword;
    }


    public String getTruststorePassword() {
        return truststorePassword;
    }


    public void setTruststoreProvider(String truststoreProvider) {
        setProperty("truststoreProvider", Type.JSSE);
        this.truststoreProvider = truststoreProvider;
    }


    public String getTruststoreProvider() {
        if (truststoreProvider == null) {
            Set<SSLHostConfigCertificate> certificates = getCertificates();
            if (certificates.size() == 1) {
                return certificates.iterator().next().getCertificateKeystoreProvider();
            }
            return SSLHostConfigCertificate.DEFAULT_KEYSTORE_PROVIDER;
        } else {
            return truststoreProvider;
        }
    }


    public void setTruststoreType(String truststoreType) {
        setProperty("truststoreType", Type.JSSE);
        this.truststoreType = truststoreType;
    }


    public String getTruststoreType() {
        if (truststoreType == null) {
            Set<SSLHostConfigCertificate> certificates = getCertificates();
            if (certificates.size() == 1) {
                String keystoreType = certificates.iterator().next().getCertificateKeystoreType();
                // Don't use keystore type as the default if we know it is not
                // going to be used as a trust store type
                if (!"PKCS12".equalsIgnoreCase(keystoreType)) {
                    return keystoreType;
                }
            }
            return SSLHostConfigCertificate.DEFAULT_KEYSTORE_TYPE;
        } else {
            return truststoreType;
        }
    }


    public void setTrustStore(KeyStore truststore) {
        this.truststore = truststore;
    }


    public KeyStore getTruststore() throws IOException {
        KeyStore result = truststore;
        if (result == null) {
            if (truststoreFile != null){
                try {
                    result = SSLUtilBase.getStore(getTruststoreType(), getTruststoreProvider(),
                            getTruststoreFile(), getTruststorePassword());
                } catch (IOException ioe) {
                    Throwable cause = ioe.getCause();
                    if (cause instanceof UnrecoverableKeyException) {
                        // Log a warning we had a password issue
                        log.warn(sm.getString("sslHostConfig.invalid_truststore_password"),
                                cause);
                        // Re-try
                        result = SSLUtilBase.getStore(getTruststoreType(), getTruststoreProvider(),
                                getTruststoreFile(), null);
                    } else {
                        // Something else went wrong - re-throw
                        throw ioe;
                    }
                }
            }
        }
        return result;
    }


    // ------------------------------- OpenSSL specific configuration properties

    // TODO: These certificate setters can be removed once it is no longer
    // necessary to support the old configuration attributes (Tomcat 10?).

    public String getCertificateChainFile() {
        if (defaultCertificate == null) {
            return null;
        } else {
            return defaultCertificate.getCertificateChainFile();
        }
    }
    public void setCertificateChainFile(String certificateChainFile) {
        registerDefaultCertificate();
        defaultCertificate.setCertificateChainFile(certificateChainFile);
    }


    public String getCertificateFile() {
        if (defaultCertificate == null) {
            return null;
        } else {
            return defaultCertificate.getCertificateFile();
        }
    }
    public void setCertificateFile(String certificateFile) {
        registerDefaultCertificate();
        defaultCertificate.setCertificateFile(certificateFile);
    }


    public String getCertificateKeyFile() {
        if (defaultCertificate == null) {
            return null;
        } else {
            return defaultCertificate.getCertificateKeyFile();
        }
    }
    public void setCertificateKeyFile(String certificateKeyFile) {
        registerDefaultCertificate();
        defaultCertificate.setCertificateKeyFile(certificateKeyFile);
    }


    public void setCertificateRevocationListPath(String certificateRevocationListPath) {
        setProperty("certificateRevocationListPath", Type.OPENSSL);
        this.certificateRevocationListPath = certificateRevocationListPath;
    }


    public String getCertificateRevocationListPath() {
        return certificateRevocationListPath;
    }


    public void setCaCertificateFile(String caCertificateFile) {
        if (setProperty("caCertificateFile", Type.OPENSSL)) {
            // Reset default JSSE trust store if not a JSSE configuration
            if (truststoreFile != null) {
                truststoreFile = null;
            }
        }
        this.caCertificateFile = caCertificateFile;
    }


    public String getCaCertificateFile() {
        return caCertificateFile;
    }


    public void setCaCertificatePath(String caCertificatePath) {
        if (setProperty("caCertificatePath", Type.OPENSSL)) {
            // Reset default JSSE trust store if not a JSSE configuration
            if (truststoreFile != null) {
                truststoreFile = null;
            }
        }
        this.caCertificatePath = caCertificatePath;
    }


    public String getCaCertificatePath() {
        return caCertificatePath;
    }


    public void setDisableCompression(boolean disableCompression) {
        setProperty("disableCompression", Type.OPENSSL);
        this.disableCompression = disableCompression;
    }


    public boolean getDisableCompression() {
        return disableCompression;
    }


    public void setDisableSessionTickets(boolean disableSessionTickets) {
        setProperty("disableSessionTickets", Type.OPENSSL);
        this.disableSessionTickets = disableSessionTickets;
    }


    public boolean getDisableSessionTickets() {
        return disableSessionTickets;
    }


    public void setInsecureRenegotiation(boolean insecureRenegotiation) {
        setProperty("insecureRenegotiation", Type.OPENSSL);
        this.insecureRenegotiation = insecureRenegotiation;
    }


    public boolean getInsecureRenegotiation() {
        return insecureRenegotiation;
    }


    // --------------------------------------------------------- Support methods

    public static String adjustRelativePath(String path) throws FileNotFoundException {
        // Empty or null path can't point to anything useful. The assumption is
        // that the value is deliberately empty / null so leave it that way.
        if (path == null || path.length() == 0) {
            return path;
        }
        String newPath = path;
        File f = new File(newPath);
        if ( !f.isAbsolute()) {
            newPath = System.getProperty(Constants.CATALINA_BASE_PROP) + File.separator + newPath;
            f = new File(newPath);
        }
        if (!f.exists()) {
            throw new FileNotFoundException(sm.getString("sslHostConfig.fileNotFound", newPath));
        }
        return newPath;
    }


    // ----------------------------------------------------------- Inner classes

    public enum Type {
        JSSE,
        OPENSSL
    }


    public enum CertificateVerification {
        NONE,
        OPTIONAL_NO_CA,
        OPTIONAL,
        REQUIRED;

        public static CertificateVerification fromString(String value) {
            if ("true".equalsIgnoreCase(value) ||
                    "yes".equalsIgnoreCase(value) ||
                    "require".equalsIgnoreCase(value) ||
                    "required".equalsIgnoreCase(value)) {
                return REQUIRED;
            } else if ("optional".equalsIgnoreCase(value) ||
                    "want".equalsIgnoreCase(value)) {
                return OPTIONAL;
            } else if ("optionalNoCA".equalsIgnoreCase(value) ||
                    "optional_no_ca".equalsIgnoreCase(value)) {
                return OPTIONAL_NO_CA;
            } else if ("false".equalsIgnoreCase(value) ||
                    "no".equalsIgnoreCase(value) ||
                    "none".equalsIgnoreCase(value)) {
                return NONE;
            } else {
                // Could be a typo. Don't default to NONE since that is not
                // secure. Force user to fix config. Could default to REQUIRED
                // instead.
                throw new IllegalArgumentException(
                        sm.getString("sslHostConfig.certificateVerificationInvalid", value));
            }
        }
    }
}
