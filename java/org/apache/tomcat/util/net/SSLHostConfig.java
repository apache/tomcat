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
import java.io.Serial;
import java.io.Serializable;
import java.security.KeyStore;
import java.security.UnrecoverableKeyException;
import java.security.cert.X509Certificate;
import java.util.Date;
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
import org.apache.tomcat.util.net.openssl.ciphers.Group;
import org.apache.tomcat.util.net.openssl.ciphers.OpenSSLCipherConfigurationParser;
import org.apache.tomcat.util.res.StringManager;

/**
 * Represents the TLS configuration for a virtual host.
 */
public class SSLHostConfig implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final Log log = LogFactory.getLog(SSLHostConfig.class);
    private static final StringManager sm = StringManager.getManager(SSLHostConfig.class);

    // Must be lowercase. SSL host names are always stored using lower case as
    // they are case-insensitive but are used by case-sensitive code such as
    // keys in Maps.
    /**
     * Default SSL host name.
     */
    protected static final String DEFAULT_SSL_HOST_NAME = "_default_";
    /**
     * Set of all SSL protocols.
     */
    protected static final Set<String> SSL_PROTO_ALL_SET = new HashSet<>();
    /**
     * Default cipher list for TLS 1.2 and below.
     */
    public static final String DEFAULT_TLS_CIPHERS_12 = "HIGH:!aNULL:!eNULL:!DES:!RC4:!MD5:!kRSA";
    /**
     * Default cipher suite list for TLS 1.3.
     */
    public static final String DEFAULT_TLS_CIPHERS_13 = "TLS_AES_256_GCM_SHA384:TLS_CHACHA20_POLY1305_SHA256:TLS_AES_128_GCM_SHA256";
    /**
     * Default cipher list for TLS 1.2 and below.
     * @deprecated Replaced by {@link #DEFAULT_TLS_CIPHERS_12}
     */
    @Deprecated
    public static final String DEFAULT_TLS_CIPHERS = DEFAULT_TLS_CIPHERS_12;

    static {
        /*
         * Default used if protocols are not configured, also used if protocols="All"
         */
        SSL_PROTO_ALL_SET.add(Constants.SSL_PROTO_SSLv2Hello);
        SSL_PROTO_ALL_SET.add(Constants.SSL_PROTO_TLSv1);
        SSL_PROTO_ALL_SET.add(Constants.SSL_PROTO_TLSv1_1);
        SSL_PROTO_ALL_SET.add(Constants.SSL_PROTO_TLSv1_2);
        SSL_PROTO_ALL_SET.add(Constants.SSL_PROTO_TLSv1_3);
    }

    private Type configType = null;
    private Type trustConfigType = null;

    private String hostName = DEFAULT_SSL_HOST_NAME;

    private transient volatile Long openSslConfContext = Long.valueOf(0);
    // OpenSSL can handle multiple certs in a single config so the reference to
    // the context is here at the virtual host level. JSSE can't so the
    // reference is held on the certificate.
    private transient volatile Long openSslContext = Long.valueOf(0);

    private boolean tls13RenegotiationAvailable = false;

    // Configuration properties

    // Internal
    private String[] enabledCiphers;
    private String[] enabledProtocols;
    private ObjectName oname;
    // Need to know if TLS 1.3 has been explicitly requested as a warning needs
    // to generated if it is explicitly requested for a JVM that does not
    // support it. Uses a set so it is extensible for TLS 1.4 etc.
    private final Set<String> explicitlyRequestedProtocols = new HashSet<>();
    // Nested
    private SSLHostConfigCertificate defaultCertificate = null;
    private final Set<SSLHostConfigCertificate> certificates = new LinkedHashSet<>(4);
    // Common
    private String certificateRevocationListFile;
    private CertificateVerification certificateVerification = CertificateVerification.NONE;
    private int certificateVerificationDepth = 10;
    // Used to track if certificateVerificationDepth has been explicitly set
    private boolean certificateVerificationDepthConfigured = false;
    private String ciphers = DEFAULT_TLS_CIPHERS_12;
    private String cipherSuites = DEFAULT_TLS_CIPHERS_13;
    private String cipherSuitesFromCiphers = null;
    private LinkedHashSet<Cipher> cipherList = null;
    private LinkedHashSet<Cipher> cipherSuiteList = null;
    private List<String> jsseCipherNames = null;
    private boolean honorCipherOrder = false;
    private boolean ocspEnabled = false;
    private boolean ocspSoftFail = true;
    private int ocspTimeout = 15000;
    private int ocspVerifyFlags = 0;
    private final Set<String> protocols = new HashSet<>();
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
    private String groups = System.getProperty("jdk.tls.namedGroups");
    private LinkedHashSet<Group> groupList = null;
    // OpenSSL
    private String certificateRevocationListPath;
    private String caCertificateFile;
    private String caCertificatePath;
    private boolean disableCompression = true;
    private boolean disableSessionTickets = false;
    private boolean insecureRenegotiation = false;
    private OpenSSLConf openSslConf = null;

    /**
     * Default constructor.
     */
    public SSLHostConfig() {
        // Set defaults that can't be (easily) set when defining the fields.
        setProtocols(Constants.SSL_PROTO_ALL);
    }


    /**
     * Returns whether TLS 1.3 renegotiation is available.
     *
     * @return {@code true} if TLS 1.3 renegotiation is available
     */
    public boolean isTls13RenegotiationAvailable() {
        return tls13RenegotiationAvailable;
    }


    /**
     * Sets whether TLS 1.3 renegotiation is available.
     *
     * @param tls13RenegotiationAvailable {@code true} if TLS 1.3 renegotiation is available
     */
    public void setTls13RenegotiationAvailable(boolean tls13RenegotiationAvailable) {
        this.tls13RenegotiationAvailable = tls13RenegotiationAvailable;
    }


    /**
     * Returns the OpenSSL configuration context pointer.
     *
     * @return the OpenSSL configuration context pointer
     */
    public Long getOpenSslConfContext() {
        return openSslConfContext;
    }


    /**
     * Sets the OpenSSL configuration context pointer.
     *
     * @param openSslConfContext the OpenSSL configuration context pointer
     */
    public void setOpenSslConfContext(Long openSslConfContext) {
        this.openSslConfContext = openSslConfContext;
    }


    /**
     * Returns the OpenSSL context pointer.
     *
     * @return the OpenSSL context pointer
     */
    public Long getOpenSslContext() {
        return openSslContext;
    }


    /**
     * Sets the OpenSSL context pointer.
     *
     * @param openSslContext the OpenSSL context pointer
     */
    public void setOpenSslContext(Long openSslContext) {
        this.openSslContext = openSslContext;
    }


    /**
     * Expose in String form for JMX.
     *
     * @return the configuration type as a string
     */
    public String getConfigType() {
        return configType.name();
    }


    /**
     * Set property which belongs to the specified configuration type.
     *
     * @param name       the property name
     * @param configType the configuration type
     *
     * @return true if the property belongs to the current configuration type, and false otherwise
     */
    boolean setProperty(String name, Type configType) {
        if (this.configType == null) {
            this.configType = configType;
        } else {
            if (configType != this.configType) {
                log.warn(sm.getString("sslHostConfig.mismatch", name, getHostName(), configType, this.configType));
                return false;
            }
        }
        return true;
    }


    /**
     * Set property which belongs to the specified trust configuration type.
     *
     * @param name            the property name
     * @param trustConfigType the trust configuration type
     *
     * @return true if the property belongs to the current trust configuration type, and false otherwise
     */
    boolean setTrustProperty(String name, Type trustConfigType) {
        if (this.trustConfigType == null) {
            this.trustConfigType = trustConfigType;
        } else {
            if (trustConfigType != this.trustConfigType) {
                log.warn(sm.getString("sslHostConfig.mismatch.trust", name, getHostName(), trustConfigType,
                        this.trustConfigType));
                return false;
            }
        }
        return true;
    }


    // ----------------------------------------------------- Internal properties

    /**
     * Returns the protocols enabled for this TLS virtual host.
     *
     * @see SSLUtil#getEnabledProtocols()
     *
     * @return The protocols enabled for this TLS virtual host
     */
    public String[] getEnabledProtocols() {
        return enabledProtocols;
    }


    /**
     * Sets the protocols enabled for this TLS virtual host.
     *
     * @param enabledProtocols the protocols to enable
     */
    public void setEnabledProtocols(String[] enabledProtocols) {
        this.enabledProtocols = enabledProtocols;
    }


    /**
     * Returns the ciphers enabled for this TLS virtual host.
     *
     * @see SSLUtil#getEnabledCiphers()
     *
     * @return The ciphers enabled for this TLS virtual host
     */
    public String[] getEnabledCiphers() {
        return enabledCiphers;
    }


    /**
     * Sets the ciphers enabled for this TLS virtual host.
     *
     * @param enabledCiphers the ciphers to enable
     */
    public void setEnabledCiphers(String[] enabledCiphers) {
        this.enabledCiphers = enabledCiphers;
    }


    /**
     * Returns the JMX object name.
     *
     * @return the object name
     */
    public ObjectName getObjectName() {
        return oname;
    }


    /**
     * Sets the JMX object name.
     *
     * @param oname the object name
     */
    public void setObjectName(ObjectName oname) {
        this.oname = oname;
    }


    // ------------------------------------------- Nested configuration elements

    private void registerDefaultCertificate() {
        if (defaultCertificate == null) {
            SSLHostConfigCertificate defaultCertificate =
                    new SSLHostConfigCertificate(this, SSLHostConfigCertificate.Type.UNDEFINED);
            addCertificate(defaultCertificate);
            this.defaultCertificate = defaultCertificate;
        }
    }


    /**
     * Adds a certificate to this SSL host configuration.
     *
     * @param certificate the certificate to add
     */
    public void addCertificate(SSLHostConfigCertificate certificate) {
        // Need to make sure that if there is more than one certificate, none of
        // them have a type of undefined.
        if (certificates.isEmpty()) {
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


    /**
     * Returns the OpenSSL configuration.
     *
     * @return the OpenSSL configuration
     */
    public OpenSSLConf getOpenSslConf() {
        return openSslConf;
    }


    /**
     * Sets the OpenSSL configuration.
     *
     * @param conf the OpenSSL configuration
     */
    public void setOpenSslConf(OpenSSLConf conf) {
        if (conf == null) {
            throw new IllegalArgumentException(sm.getString("sslHostConfig.opensslconf.null"));
        } else if (openSslConf != null) {
            throw new IllegalArgumentException(sm.getString("sslHostConfig.opensslconf.alreadySet"));
        }
        openSslConf = conf;
    }


    /**
     * Returns the set of certificates.
     *
     * @return the certificates
     */
    public Set<SSLHostConfigCertificate> getCertificates() {
        return getCertificates(false);
    }


    /**
     * Returns the set of certificates, optionally creating a default if empty.
     *
     * @param createDefaultIfEmpty {@code true} to create a default certificate if the set is empty
     * @return the certificates
     */
    public Set<SSLHostConfigCertificate> getCertificates(boolean createDefaultIfEmpty) {
        if (certificates.isEmpty() && createDefaultIfEmpty) {
            registerDefaultCertificate();
        }
        return certificates;
    }


    // ----------------------------------------- Common configuration properties

    /**
     * Sets the certificate revocation list file.
     *
     * @param certificateRevocationListFile the certificate revocation list file
     */
    public void setCertificateRevocationListFile(String certificateRevocationListFile) {
        this.certificateRevocationListFile = certificateRevocationListFile;
    }


    /**
     * Returns the certificate revocation list file.
     *
     * @return the certificate revocation list file
     */
    public String getCertificateRevocationListFile() {
        return certificateRevocationListFile;
    }


    /**
     * Sets the certificate verification mode.
     *
     * @param certificateVerification the certificate verification mode
     */
    public void setCertificateVerification(String certificateVerification) {
        try {
            this.certificateVerification = CertificateVerification.fromString(certificateVerification);
        } catch (IllegalArgumentException iae) {
            // If the specified value is not recognised, default to the
            // strictest possible option.
            this.certificateVerification = CertificateVerification.REQUIRED;
            throw iae;
        }
    }


    /**
     * Returns the certificate verification mode.
     *
     * @return the certificate verification mode
     */
    public CertificateVerification getCertificateVerification() {
        return certificateVerification;
    }


    /**
     * Sets the certificate verification mode as a string.
     *
     * @param certificateVerification the certificate verification mode
     */
    public void setCertificateVerificationAsString(String certificateVerification) {
        setCertificateVerification(certificateVerification);
    }


    /**
     * Returns the certificate verification mode as a string.
     *
     * @return the certificate verification mode as a string
     */
    public String getCertificateVerificationAsString() {
        return certificateVerification.toString();
    }


    /**
     * Sets the certificate verification depth.
     *
     * @param certificateVerificationDepth the certificate verification depth
     */
    public void setCertificateVerificationDepth(int certificateVerificationDepth) {
        this.certificateVerificationDepth = certificateVerificationDepth;
        certificateVerificationDepthConfigured = true;
    }


    /**
     * Returns the certificate verification depth.
     *
     * @return the certificate verification depth
     */
    public int getCertificateVerificationDepth() {
        return certificateVerificationDepth;
    }


    /**
     * Returns whether the certificate verification depth has been configured.
     *
     * @return {@code true} if the certificate verification depth has been configured
     */
    public boolean isCertificateVerificationDepthConfigured() {
        return certificateVerificationDepthConfigured;
    }


    /**
     * Set the new cipher (TLSv1.2 and below) configuration. Note: Regardless of the format used to set the
     * configuration, it is always stored in OpenSSL format.
     *
     * @param ciphersList The new cipher configuration in OpenSSL or JSSE format
     */
    public void setCiphers(String ciphersList) {
        // Ciphers is stored in OpenSSL format. Convert the provided value if
        // necessary.
        if (ciphersList != null) {
            if (ciphersList.contains(":")) {
                // OpenSSL format
                StringBuilder sbCiphers = new StringBuilder();
                StringBuilder sbCipherSuitesFromCiphers = new StringBuilder();
                String[] components = ciphersList.split(":");
                // Remove any TLS 1.3 cipher suites
                for (String component : components) {
                    String trimmed = component.trim();
                    if (OpenSSLCipherConfigurationParser.isTls13Cipher(trimmed)) {
                        log.warn(sm.getString("sslHostConfig.handleTls13CiphersuiteInCiphers", trimmed));
                        if (!sbCipherSuitesFromCiphers.isEmpty()) {
                            sbCipherSuitesFromCiphers.append(':');
                        }
                        sbCipherSuitesFromCiphers.append(trimmed);
                    } else {
                        if (!sbCiphers.isEmpty()) {
                            sbCiphers.append(':');
                        }
                        sbCiphers.append(trimmed);
                    }
                }
                this.ciphers = sbCiphers.toString();
                this.cipherSuitesFromCiphers = sbCipherSuitesFromCiphers.toString();
            } else {
                // Not obviously in OpenSSL format. Might be a single OpenSSL or JSSE
                // cipher name. Might be a comma separated list of cipher names
                StringBuilder sbCiphers = new StringBuilder();
                StringBuilder sbCipherSuitesFromCiphers = new StringBuilder();
                String[] ciphers = ciphersList.split(",");
                for (String cipher : ciphers) {
                    String trimmed = cipher.trim();
                    if (!trimmed.isEmpty()) {
                        if (OpenSSLCipherConfigurationParser.isTls13Cipher(trimmed)) {
                            log.warn(sm.getString("sslHostConfig.handleTls13CiphersuiteInCiphers", trimmed));
                            if (!sbCipherSuitesFromCiphers.isEmpty()) {
                                sbCipherSuitesFromCiphers.append(':');
                            }
                            sbCipherSuitesFromCiphers.append(trimmed);
                        } else {
                            String openSSLName = OpenSSLCipherConfigurationParser.jsseToOpenSSL(trimmed);
                            if (openSSLName == null) {
                                // Not a JSSE name. Maybe an OpenSSL name or alias
                                openSSLName = trimmed;
                            }
                            if (!sbCiphers.isEmpty()) {
                                sbCiphers.append(':');
                            }
                            sbCiphers.append(openSSLName);
                        }
                    }
                }
                this.ciphers = sbCiphers.toString();
                this.cipherSuitesFromCiphers = sbCipherSuitesFromCiphers.toString();
            }
        } else {
            this.ciphers = null;
            this.cipherSuitesFromCiphers = null;
        }
        this.cipherList = null;
        this.jsseCipherNames = null;
        this.cipherSuiteList = null;
    }


    /**
     * Returns the cipher (TLSv1.2 and below) configuration.
     *
     * @return An OpenSSL cipher string for the current configuration.
     */
    public String getCiphers() {
        return ciphers;
    }


    /**
     * Returns the list of configured ciphers.
     *
     * @return the cipher list
     */
    public LinkedHashSet<Cipher> getCipherList() {
        if (cipherList == null) {
            cipherList = OpenSSLCipherConfigurationParser.parse(getCiphers());
        }
        return cipherList;
    }


    /**
     * Obtain the list of JSSE cipher names for the current configuration. Ciphers included in the configuration but not
     * supported by JSSE will be excluded from this list. TLS 1.3 ciphers will be first in the list.
     *
     * @return A list of the JSSE cipher names
     */
    public List<String> getJsseCipherNames() {
        if (jsseCipherNames == null) {
            Set<Cipher> jsseCiphers = new LinkedHashSet<>();
            jsseCiphers.addAll(getCipherSuiteList());
            jsseCiphers.addAll(getCipherList());
            jsseCipherNames = OpenSSLCipherConfigurationParser.convertForJSSE(jsseCiphers);
        }
        return jsseCipherNames;
    }


    /**
     * Set the cipher suite (TLSv1.3) configuration.
     *
     * @param cipherSuites The cipher suites to use in a colon-separated, preference order list
     */
    public void setCipherSuites(String cipherSuites) {
        StringBuilder sb = new StringBuilder();
        String[] values;
        if (cipherSuites.contains(":")) {
            // OpenSSL format
            values = cipherSuites.split(":");
        } else {
            // JSSE format or possible a single cipher suite name
            values = cipherSuites.split(",");
        }
        for (String value : values) {
            String trimmed = value.trim();
            if (!trimmed.isEmpty()) {
                if (!OpenSSLCipherConfigurationParser.isTls13Cipher(trimmed)) {
                    log.warn(sm.getString("sslHostConfig.ignoreNonTls13Ciphersuite", trimmed));
                    continue;
                }
                /*
                 * OpenSSL and JSSE names for TLSv1.3 cipher suites are currently (January 2026) the same but handle the
                 * possible future case where they are not.
                 */
                String openSSLName = OpenSSLCipherConfigurationParser.jsseToOpenSSL(trimmed);
                if (openSSLName == null) {
                    // Not a JSSE name. Maybe an OpenSSL name or alias
                    openSSLName = trimmed;
                }
                if (!sb.isEmpty()) {
                    sb.append(':');
                }
                sb.append(trimmed);
            }
        }
        this.cipherSuites = sb.toString();
        this.cipherSuiteList = null;
        this.jsseCipherNames = null;
    }


    /**
     * Obtain the current cipher suite (TLSv1.3) configuration.
     *
     * @return An OpenSSL cipher suite string for the current configuration.
     */
    public String getCipherSuites() {
        StringBuilder sb = new StringBuilder(cipherSuites);
        if (cipherSuitesFromCiphers != null && !cipherSuitesFromCiphers.isEmpty()) {
            if (!sb.isEmpty()) {
                sb.append(':');
            }
            sb.append(cipherSuitesFromCiphers);
        }
        return sb.toString();
    }


    private LinkedHashSet<Cipher> getCipherSuiteList() {
        if (cipherSuiteList == null) {
            cipherSuiteList = OpenSSLCipherConfigurationParser.parse(getCipherSuites());
        }
        return cipherSuiteList;
    }


    /**
     * Sets whether to honor the cipher order.
     *
     * @param honorCipherOrder {@code true} to honor the cipher order
     */
    public void setHonorCipherOrder(boolean honorCipherOrder) {
        this.honorCipherOrder = honorCipherOrder;
    }


    /**
     * Returns whether to honor the cipher order.
     *
     * @return {@code true} to honor the cipher order
     */
    public boolean getHonorCipherOrder() {
        return honorCipherOrder;
    }


    /**
     * Sets the host name.
     *
     * @param hostName the host name
     */
    public void setHostName(String hostName) {
        this.hostName = hostName.toLowerCase(Locale.ENGLISH);
    }


    /**
     * Returns the host name associated with this SSL configuration.
     *
     * @return The host name associated with this SSL configuration - always in lower case.
     */
    public String getHostName() {
        return hostName;
    }


    /**
     * Returns whether OCSP is enabled.
     *
     * @return {@code true} if OCSP is enabled
     */
    public boolean getOcspEnabled() {
        return ocspEnabled;
    }


    /**
     * Sets whether OCSP is enabled.
     *
     * @param ocspEnabled {@code true} if OCSP is enabled
     */
    public void setOcspEnabled(boolean ocspEnabled) {
        this.ocspEnabled = ocspEnabled;
    }


    /**
     * Returns whether OCSP soft fail is enabled.
     *
     * @return {@code true} if OCSP soft fail is enabled
     */
    public boolean getOcspSoftFail() {
        return ocspSoftFail;
    }


    /**
     * Sets whether OCSP soft fail is enabled.
     *
     * @param ocspSoftFail {@code true} if OCSP soft fail is enabled
     */
    public void setOcspSoftFail(boolean ocspSoftFail) {
        this.ocspSoftFail = ocspSoftFail;
    }


    /**
     * Returns the OCSP timeout.
     *
     * @return the OCSP timeout
     */
    public int getOcspTimeout() {
        return ocspTimeout;
    }


    /**
     * Sets the OCSP timeout.
     *
     * @param ocspTimeout the OCSP timeout
     */
    public void setOcspTimeout(int ocspTimeout) {
        this.ocspTimeout = ocspTimeout;
    }


    /**
     * Returns the OCSP verify flags.
     *
     * @return the OCSP verify flags
     */
    public int getOcspVerifyFlags() {
        return ocspVerifyFlags;
    }


    /**
     * Sets the OCSP verify flags.
     *
     * @param ocspVerifyFlags the OCSP verify flags
     */
    public void setOcspVerifyFlags(int ocspVerifyFlags) {
        this.ocspVerifyFlags = ocspVerifyFlags;
    }


    /**
     * Sets the protocols to be used.
     *
     * @param input the protocol string
     */
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
        for (String value : input.split("(?=[-+,])")) {
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
                        log.warn(sm.getString("sslHostConfig.prefix_missing", trimmed, getHostName()));
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


    /**
     * Returns the configured protocols.
     *
     * @return the protocols
     */
    public Set<String> getProtocols() {
        return protocols;
    }


    boolean isExplicitlyRequestedProtocol(String protocol) {
        return explicitlyRequestedProtocols.contains(protocol);
    }


    /**
     * Sets the session cache size.
     *
     * @param sessionCacheSize the session cache size
     */
    public void setSessionCacheSize(int sessionCacheSize) {
        this.sessionCacheSize = sessionCacheSize;
    }


    /**
     * Returns the session cache size.
     *
     * @return the session cache size
     */
    public int getSessionCacheSize() {
        return sessionCacheSize;
    }


    /**
     * Sets the session timeout.
     *
     * @param sessionTimeout the session timeout
     */
    public void setSessionTimeout(int sessionTimeout) {
        this.sessionTimeout = sessionTimeout;
    }


    /**
     * Returns the session timeout.
     *
     * @return the session timeout
     */
    public int getSessionTimeout() {
        return sessionTimeout;
    }


    /**
     * Returns the configured named groups.
     *
     * @return the configured named groups
     */
    public String getGroups() {
        return groups;
    }


    /**
     * Set the enabled named groups.
     *
     * @param groups the case sensitive comma separated list of groups
     */
    public void setGroups(String groups) {
        this.groups = groups;
        this.groupList = null;
    }


    /**
     * Returns the parsed group list.
     *
     * @return the groupList
     */
    public LinkedHashSet<Group> getGroupList() {
        if (groupList == null) {
            String groups = this.groups;
            if (groups != null) {
                LinkedHashSet<Group> groupList = new LinkedHashSet<>();
                String[] groupNames = groups.split(",");
                for (String groupName : groupNames) {
                    try {
                        Group group = Group.valueOf(groupName.trim());
                        groupList.add(group);
                    } catch (IllegalArgumentException e) {
                        log.warn(sm.getString("sslHostConfig.unknownGroup", groupName));
                    }
                }
                this.groupList = groupList;
            }
        }
        return this.groupList;
    }


    // ---------------------------------- JSSE specific configuration properties


    /**
     * Sets the key manager algorithm.
     *
     * @param keyManagerAlgorithm the key manager algorithm
     */
    public void setKeyManagerAlgorithm(String keyManagerAlgorithm) {
        setProperty("keyManagerAlgorithm", Type.JSSE);
        this.keyManagerAlgorithm = keyManagerAlgorithm;
    }


    /**
     * Returns the key manager algorithm.
     *
     * @return the key manager algorithm
     */
    public String getKeyManagerAlgorithm() {
        return keyManagerAlgorithm;
    }


    /**
     * Sets whether revocation checking is enabled.
     *
     * @param revocationEnabled {@code true} if revocation checking is enabled
     */
    public void setRevocationEnabled(boolean revocationEnabled) {
        setProperty("revocationEnabled", Type.JSSE);
        this.revocationEnabled = revocationEnabled;
    }


    /**
     * Returns whether revocation checking is enabled.
     *
     * @return {@code true} if revocation checking is enabled
     */
    public boolean getRevocationEnabled() {
        return revocationEnabled;
    }


    /**
     * Sets the SSL protocol.
     *
     * @param sslProtocol the SSL protocol
     */
    public void setSslProtocol(String sslProtocol) {
        setProperty("sslProtocol", Type.JSSE);
        this.sslProtocol = sslProtocol;
    }


    /**
     * Returns the SSL protocol.
     *
     * @return the SSL protocol
     */
    public String getSslProtocol() {
        return sslProtocol;
    }


    /**
     * Sets the trust manager class name.
     *
     * @param trustManagerClassName the trust manager class name
     */
    public void setTrustManagerClassName(String trustManagerClassName) {
        setTrustProperty("trustManagerClassName", Type.JSSE);
        this.trustManagerClassName = trustManagerClassName;
    }


    /**
     * Returns the trust manager class name.
     *
     * @return the trust manager class name
     */
    public String getTrustManagerClassName() {
        return trustManagerClassName;
    }


    /**
     * Sets the truststore algorithm.
     *
     * @param truststoreAlgorithm the truststore algorithm
     */
    public void setTruststoreAlgorithm(String truststoreAlgorithm) {
        setTrustProperty("truststoreAlgorithm", Type.JSSE);
        this.truststoreAlgorithm = truststoreAlgorithm;
    }


    /**
     * Returns the truststore algorithm.
     *
     * @return the truststore algorithm
     */
    public String getTruststoreAlgorithm() {
        return truststoreAlgorithm;
    }


    /**
     * Sets the truststore file.
     *
     * @param truststoreFile the truststore file
     */
    public void setTruststoreFile(String truststoreFile) {
        setTrustProperty("truststoreFile", Type.JSSE);
        this.truststoreFile = truststoreFile;
    }


    /**
     * Returns the truststore file.
     *
     * @return the truststore file
     */
    public String getTruststoreFile() {
        return truststoreFile;
    }


    /**
     * Sets the truststore password.
     *
     * @param truststorePassword the truststore password
     */
    public void setTruststorePassword(String truststorePassword) {
        setTrustProperty("truststorePassword", Type.JSSE);
        this.truststorePassword = truststorePassword;
    }


    /**
     * Returns the truststore password.
     *
     * @return the truststore password
     */
    public String getTruststorePassword() {
        return truststorePassword;
    }


    /**
     * Sets the truststore provider.
     *
     * @param truststoreProvider the truststore provider
     */
    public void setTruststoreProvider(String truststoreProvider) {
        setTrustProperty("truststoreProvider", Type.JSSE);
        this.truststoreProvider = truststoreProvider;
    }


    /**
     * Returns the truststore provider.
     *
     * @return the truststore provider
     */
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


 /**
     * Sets the truststore type.
     *
     * @param truststoreType the truststore type
     */
    public void setTruststoreType(String truststoreType) {
        setTrustProperty("truststoreType", Type.JSSE);
        this.truststoreType = truststoreType;
    }


    /**
     * Returns the truststore type.
     *
     * @return the truststore type
     */
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


    /**
     * Sets the truststore.
     *
     * @param truststore the truststore
     */
    public void setTrustStore(KeyStore truststore) {
        setTrustProperty("trustStore", Type.JSSE);
        this.truststore = truststore;
    }


    /**
     * Returns the truststore.
     *
     * @return the truststore
     *
     * @throws IOException if an I/O error occurs
     */
    public KeyStore getTruststore() throws IOException {
        KeyStore result = truststore;
        if (result == null) {
            if (truststoreFile != null) {
                try {
                    result = SSLUtilBase.getStore(getTruststoreType(), getTruststoreProvider(), getTruststoreFile(),
                            getTruststorePassword(), null);
                } catch (IOException ioe) {
                    Throwable cause = ioe.getCause();
                    if (cause instanceof UnrecoverableKeyException) {
                        // Log a warning we had a password issue
                        log.warn(sm.getString("sslHostConfig.invalid_truststore_password"), cause);
                        // Re-try
                        result = SSLUtilBase.getStore(getTruststoreType(), getTruststoreProvider(), getTruststoreFile(),
                                null, null);
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

    /**
     * Sets the certificate revocation list path.
     *
     * @param certificateRevocationListPath the certificate revocation list path
     */
    public void setCertificateRevocationListPath(String certificateRevocationListPath) {
        setProperty("certificateRevocationListPath", Type.OPENSSL);
        this.certificateRevocationListPath = certificateRevocationListPath;
    }


    /**
     * Returns the certificate revocation list path.
     *
     * @return the certificate revocation list path
     */
    public String getCertificateRevocationListPath() {
        return certificateRevocationListPath;
    }


    /**
     * Sets the CA certificate file.
     *
     * @param caCertificateFile the CA certificate file
     */
    public void setCaCertificateFile(String caCertificateFile) {
        if (setTrustProperty("caCertificateFile", Type.OPENSSL)) {
            // Reset default JSSE trust store if not a JSSE configuration
            if (truststoreFile != null) {
                truststoreFile = null;
            }
        }
        this.caCertificateFile = caCertificateFile;
    }


    /**
     * Returns the CA certificate file.
     *
     * @return the CA certificate file
     */
    public String getCaCertificateFile() {
        return caCertificateFile;
    }


    /**
     * Sets the CA certificate path.
     *
     * @param caCertificatePath the CA certificate path
     */
    public void setCaCertificatePath(String caCertificatePath) {
        if (setTrustProperty("caCertificatePath", Type.OPENSSL)) {
            // Reset default JSSE trust store if not a JSSE configuration
            if (truststoreFile != null) {
                truststoreFile = null;
            }
        }
        this.caCertificatePath = caCertificatePath;
    }


    /**
     * Returns the CA certificate path.
     *
     * @return the CA certificate path
     */
    public String getCaCertificatePath() {
        return caCertificatePath;
    }


    /**
     * Sets whether compression is disabled.
     *
     * @param disableCompression {@code true} if compression is disabled
     */
    public void setDisableCompression(boolean disableCompression) {
        setProperty("disableCompression", Type.OPENSSL);
        this.disableCompression = disableCompression;
    }


    /**
     * Returns whether compression is disabled.
     *
     * @return {@code true} if compression is disabled
     */
    public boolean getDisableCompression() {
        return disableCompression;
    }


    /**
     * Sets whether session tickets are disabled.
     *
     * @param disableSessionTickets {@code true} if session tickets are disabled
     */
    public void setDisableSessionTickets(boolean disableSessionTickets) {
        setProperty("disableSessionTickets", Type.OPENSSL);
        this.disableSessionTickets = disableSessionTickets;
    }


    /**
     * Returns whether session tickets are disabled.
     *
     * @return {@code true} if session tickets are disabled
     */
    public boolean getDisableSessionTickets() {
        return disableSessionTickets;
    }


    /**
     * Sets whether insecure renegotiation is allowed.
     *
     * @param insecureRenegotiation {@code true} if insecure renegotiation is allowed
     */
    public void setInsecureRenegotiation(boolean insecureRenegotiation) {
        setProperty("insecureRenegotiation", Type.OPENSSL);
        this.insecureRenegotiation = insecureRenegotiation;
    }


    /**
     * Returns whether insecure renegotiation is allowed.
     *
     * @return {@code true} if insecure renegotiation is allowed
     */
    public boolean getInsecureRenegotiation() {
        return insecureRenegotiation;
    }


    // --------------------------------------------------------- Support methods

    /**
     * Returns the set of certificates that expire before the given date.
     *
     * @param date the date to check against
     *
     * @return the set of certificates expiring before the given date
     */
    public Set<X509Certificate> certificatesExpiringBefore(Date date) {
        Set<X509Certificate> result = new HashSet<>();
        Set<SSLHostConfigCertificate> sslHostConfigCertificates = getCertificates();
        for (SSLHostConfigCertificate sslHostConfigCertificate : sslHostConfigCertificates) {
            SSLContext sslContext = sslHostConfigCertificate.getSslContext();
            if (sslContext != null) {
                String alias = sslHostConfigCertificate.getCertificateKeyAlias();
                if (alias == null) {
                    alias = SSLUtilBase.DEFAULT_KEY_ALIAS;
                }
                X509Certificate[] certificates = sslContext.getCertificateChain(alias);
                if (certificates != null && certificates.length > 0) {
                    X509Certificate certificate = certificates[0];
                    Date expirationDate = certificate.getNotAfter();
                    if (date.after(expirationDate)) {
                        result.add(certificate);
                    }
                }
            }
        }
        return result;
    }


    /**
     * Adjusts a relative path to an absolute path based on the CATALINA_BASE property.
     *
     * @param path the path to adjust
     *
     * @return the adjusted path
     *
     * @throws FileNotFoundException if the file does not exist
     */
    public static String adjustRelativePath(String path) throws FileNotFoundException {
        // Empty or null path can't point to anything useful. The assumption is
        // that the value is deliberately empty / null so leave it that way.
        if (path == null || path.isEmpty()) {
            return path;
        }
        String newPath = path;
        File f = new File(newPath);
        if (!f.isAbsolute()) {
            newPath = System.getProperty(Constants.CATALINA_BASE_PROP) + File.separator + newPath;
            f = new File(newPath);
        }
        if (!f.exists()) {
            throw new FileNotFoundException(sm.getString("sslHostConfig.fileNotFound", newPath));
        }
        return newPath;
    }


    // ----------------------------------------------------------- Inner classes

    /**
     * SSL configuration type.
     */
    public enum Type {
        /**
         * JSSE configuration.
         */
        JSSE,
        /**
         * OpenSSL configuration.
         */
        OPENSSL
    }


    /**
     * Certificate verification levels.
     */
    public enum CertificateVerification {
        /**
         * No certificate verification.
         */
        NONE(false),
        /**
         * Optional verification without CA check.
         */
        OPTIONAL_NO_CA(true),
        /**
         * Optional verification.
         */
        OPTIONAL(true),
        /**
         * Required verification.
         */
        REQUIRED(false);

        /**
         * Whether the verification is optional.
         */
        private final boolean optional;

        /**
         * Constructor.
         *
         * @param optional whether the verification is optional
         */
        CertificateVerification(boolean optional) {
            this.optional = optional;
        }

        /**
         * Returns whether this verification level is optional.
         *
         * @return {@code true} if optional
         */
        public boolean isOptional() {
            return optional;
        }

        /**
         * Creates a CertificateVerification from a string value.
         *
         * @param value the string value
         *
         * @return the corresponding CertificateVerification
         */
        public static CertificateVerification fromString(String value) {
            if ("true".equalsIgnoreCase(value) || "yes".equalsIgnoreCase(value) || "require".equalsIgnoreCase(value) ||
                    "required".equalsIgnoreCase(value)) {
                return REQUIRED;
            } else if ("optional".equalsIgnoreCase(value) || "want".equalsIgnoreCase(value)) {
                return OPTIONAL;
            } else if ("optionalNoCA".equalsIgnoreCase(value) || "optional_no_ca".equalsIgnoreCase(value)) {
                return OPTIONAL_NO_CA;
            } else if ("false".equalsIgnoreCase(value) || "no".equalsIgnoreCase(value) ||
                    "none".equalsIgnoreCase(value)) {
                return NONE;
            } else {
                // Could be a typo. Don't default to NONE since that is not
                // secure. Force user to fix config. Could default to REQUIRED
                // instead.
                throw new IllegalArgumentException(sm.getString("sslHostConfig.certificateVerificationInvalid", value));
            }
        }
    }
}
