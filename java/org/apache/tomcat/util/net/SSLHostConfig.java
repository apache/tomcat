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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.net.jsse.openssl.OpenSSLCipherConfigurationParser;
import org.apache.tomcat.util.res.StringManager;

/**
 * Represents the TLS configuration for a virtual host.
 */
public class SSLHostConfig {

    private static final Log log = LogFactory.getLog(SSLHostConfig.class);
    private static final StringManager sm = StringManager.getManager(SSLHostConfig.class);

    protected static final String DEFAULT_SSL_HOST_NAME = "_default_";
    protected static final Set<String> SSL_PROTO_ALL = new HashSet<>();

    static {
        /* Default used if protocols is not configured, also
           used if protocols="All" */
        /* If protocols is configured to be empty, the effective
           value comes from
           org.apache.tomcat.util.net.jsse.JSSESocketFactory.defaultServerProtocols
           (JSSE) resp. org.apache.tomcat.jni.SSL.SSL_PROTOCOL_ALL (OpenSSL)*/
        SSL_PROTO_ALL.add(Constants.SSL_PROTO_SSLv2Hello);
        SSL_PROTO_ALL.add(Constants.SSL_PROTO_TLSv1);
        SSL_PROTO_ALL.add(Constants.SSL_PROTO_TLSv1_1);
        SSL_PROTO_ALL.add(Constants.SSL_PROTO_TLSv1_2);
    }

    private Type configType = null;
    private Map<Type,Set<String>> configuredProperties = new HashMap<>();

    private String hostName = DEFAULT_SSL_HOST_NAME;

    private Object sslContext;

    // Configuration properties

    // Common
    private String certificateKeyPassword = null;
    private String certificateRevocationListFile;
    private CertificateVerification certificateVerification = CertificateVerification.NONE;
    private int certificateVerificationDepth = 10;
    private String ciphers = "HIGH:!aNULL:!eNULL:!EXPORT:!DES:!RC4:!MD5:!kRSA";
    private boolean honorCipherOrder = true;
    private Set<String> protocols = new HashSet<>();
    // JSSE
    private String certificateKeyAlias;
    private String certificateKeystorePassword = "changeit";
    private String certificateKeystoreFile = System.getProperty("user.home")+"/.keystore";
    private String certificateKeystoreProvider = System.getProperty("javax.net.ssl.keyStoreProvider");
    private String certificateKeystoreType = System.getProperty("javax.net.ssl.keyStoreType");
    private String keyManagerAlgorithm = KeyManagerFactory.getDefaultAlgorithm();
    private int sessionCacheSize = 0;
    private int sessionTimeout = 86400;
    private String sslProtocol = Constants.SSL_PROTO_TLS;
    private String trustManagerClassName;
    private String truststoreAlgorithm = TrustManagerFactory.getDefaultAlgorithm();
    private String truststoreFile = System.getProperty("javax.net.ssl.trustStore");
    private String truststorePassword = System.getProperty("javax.net.ssl.trustStorePassword");
    private String truststoreProvider = System.getProperty("javax.net.ssl.trustStoreProvider");
    private String truststoreType = System.getProperty("javax.net.ssl.trustStoreType");
    // OpenSSL
    private String certificateChainFile;
    private String certificateFile;
    private String certificateKeyFile;
    private String certificateRevocationListPath;
    private String caCertificateFile;
    private String caCertificatePath;
    private boolean disableCompression = true;
    private boolean disableSessionTickets = false;
    private boolean insecureRenegotiation = false;

    public SSLHostConfig() {
        // Set defaults that can't be (easily) set when defining the fields.
        setProtocols(Constants.SSL_PROTO_ALL);
        // Configure fall-back defaults if system property is not set.
        if (certificateKeystoreType == null) {
            certificateKeystoreType = "JKS";
        }
    }


    public Object getSslContext() {
        return sslContext;
    }


    public void setSslContext(Object sslContext) {
        this.sslContext = sslContext;
    }


    public void setConfigType(Type configType) {
        this.configType = configType;
        configuredProperties.remove(configType);
        for (Map.Entry<Type,Set<String>> entry : configuredProperties.entrySet()) {
            for (String property : entry.getValue()) {
                log.warn(sm.getString("sslHostConfig.mismatch",
                        property, getHostName(), entry.getKey(), configType));
            }
        }
    }


    private void setProperty(String name, Type configType) {
        if (this.configType == null) {
            Set<String> properties = configuredProperties.get(configType);
            if (properties == null) {
                properties = new HashSet<>();
                configuredProperties.put(configType, properties);
            }
            properties.add(name);
        } else {
            if (configType != this.configType) {
                log.warn(sm.getString("sslHostConfig.mismatch",
                        name, getHostName(), configType, this.configType));
            }
        }
    }


    // ----------------------------------------- Common configuration properties

    public void setCertificateKeyPassword(String certificateKeyPassword) {
        this.certificateKeyPassword = certificateKeyPassword;
    }


    public String getCertificateKeyPassword() {
        return certificateKeyPassword;
    }


    public void setCertificateRevocationListFile(String certificateRevocationListFile) {
        this.certificateRevocationListFile = certificateRevocationListFile;
    }


    public String getCertificateRevocationListFile() {
        return certificateRevocationListFile;
    }


    public void setCertificateVerification(String certificateVerification) {
        this.certificateVerification = CertificateVerification.fromString(certificateVerification);
    }


    public CertificateVerification getCertificateVerification() {
        return certificateVerification;
    }


    public void setCertificateVerificationDepth(int certificateVerificationDepth) {
        this.certificateVerificationDepth = certificateVerificationDepth;
    }


    public int getCertificateVerificationDepth() {
        return certificateVerificationDepth;
    }


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
    }


    public String getCiphers() {
        return ciphers;
    }


    public void setHonorCipherOrder(boolean honorCipherOrder) {
        this.honorCipherOrder = honorCipherOrder;
    }


    public boolean getHonorCipherOrder() {
        return honorCipherOrder;
    }


    public void setHostName(String hostName) {
        this.hostName = hostName;
    }


    public String getHostName() {
        return hostName;
    }


    public void setProtocols(String input) {
        protocols.clear();

        // List of protocol names, separated by ",", "+" or "-".
        // Semantics is adding ("+") or removing ("-") from left
        // to right, starting with an empty protocol set.
        // Tokens are individual protocol names or "all" for a
        // default set of suppported protocols.
        // Separator "," is only kept for compatibility and has the
        // same semantics as "+", except that it warns about a potentially
        // missing "+" or "-".

        // Split using a positive lookahead to keep the separator in
        // the capture so we can check which case it is.
        for (String value: input.split("(?=[-+,])")) {
            String trimmed = value.trim();
            // Ignore token which only consists or prefix character
            if (trimmed.length() > 1) {
                if (trimmed.charAt(0) == '+') {
                    trimmed = trimmed.substring(1).trim();
                    if (trimmed.equalsIgnoreCase(Constants.SSL_PROTO_ALL)) {
                        protocols.addAll(SSL_PROTO_ALL);
                    } else {
                        protocols.add(trimmed);
                    }
                } else if (trimmed.charAt(0) == '-') {
                    trimmed = trimmed.substring(1).trim();
                    if (trimmed.equalsIgnoreCase(Constants.SSL_PROTO_ALL)) {
                        protocols.removeAll(SSL_PROTO_ALL);
                    } else {
                        protocols.remove(trimmed);
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
                        protocols.addAll(SSL_PROTO_ALL);
                    } else {
                        protocols.add(trimmed);
                    }
                }
            }
        }
    }


    public Set<String> getProtocols() {
        return protocols;
    }


    // ---------------------------------- JSSE specific configuration properties

    public void setCertificateKeyAlias(String certificateKeyAlias) {
        setProperty("certificateKeyAlias", Type.JSSE);
        this.certificateKeyAlias = certificateKeyAlias;
    }


    public String getCertificateKeyAlias() {
        return certificateKeyAlias;
    }


    public void setCertificateKeystoreFile(String certificateKeystoreFile) {
        setProperty("certificateKeystoreFile", Type.JSSE);
        this.certificateKeystoreFile = certificateKeystoreFile;
    }


    public String getCertificateKeystoreFile() {
        return certificateKeystoreFile;
    }


    public void setCertificateKeystorePassword(String certificateKeystorePassword) {
        setProperty("certificateKeystorePassword", Type.JSSE);
        this.certificateKeystorePassword = certificateKeystorePassword;
    }


    public String getCertificateKeystorePassword() {
        return certificateKeystorePassword;
    }


    public void setCertificateKeystoreProvider(String certificateKeystoreProvider) {
        setProperty("certificateKeystoreProvider", Type.JSSE);
        this.certificateKeystoreProvider = certificateKeystoreProvider;
    }


    public String getCertificateKeystoreProvider() {
        return certificateKeystoreProvider;
    }


    public void setCertificateKeystoreType(String certificateKeystoreType) {
        setProperty("certificateKeystoreType", Type.JSSE);
        this.certificateKeystoreType = certificateKeystoreType;
    }


    public String getCertificateKeystoreType() {
        return certificateKeystoreType;
    }


    public void setKeyManagerAlgorithm(String keyManagerAlgorithm) {
        setProperty("keyManagerAlgorithm", Type.JSSE);
        this.keyManagerAlgorithm = keyManagerAlgorithm;
    }


    public String getKeyManagerAlgorithm() {
        return keyManagerAlgorithm;
    }


    public void setSessionCacheSize(int sessionCacheSize) {
        setProperty("sessionCacheSize", Type.JSSE);
        this.sessionCacheSize = sessionCacheSize;
    }


    public int getSessionCacheSize() {
        return sessionCacheSize;
    }


    public void setSessionTimeout(int sessionTimeout) {
        setProperty("sessionTimeout", Type.JSSE);
        this.sessionTimeout = sessionTimeout;
    }


    public int getSessionTimeout() {
        return sessionTimeout;
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
            return getCertificateKeystoreProvider();
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
            return getCertificateKeystoreType();
        } else {
            return truststoreType;
        }
    }


    // ------------------------------- OpenSSL specific configuration properties

    public void setCertificateChainFile(String certificateChainFile) {
        setProperty("certificateChainFile", Type.OPENSSL);
        this.certificateChainFile = certificateChainFile;
    }


    public String getCertificateChainFile() {
        return certificateChainFile;
    }


    public void setCertificateFile(String certificateFile) {
        setProperty("certificateFile", Type.OPENSSL);
        this.certificateFile = certificateFile;
    }


    public String getCertificateFile() {
        return certificateFile;
    }


    public void setCertificateKeyFile(String certificateKeyFile) {
        setProperty("certificateKeyFile", Type.OPENSSL);
        this.certificateKeyFile = certificateKeyFile;
    }


    public String getCertificateKeyFile() {
        return certificateKeyFile;
    }


    public void setCertificateRevocationListPath(String certificateRevocationListPath) {
        setProperty("certificateRevocationListPath", Type.OPENSSL);
        this.certificateRevocationListPath = certificateRevocationListPath;
    }


    public String getCertificateRevocationListPath() {
        return certificateRevocationListPath;
    }


    public void setCaCertificateFile(String caCertificateFile) {
        setProperty("caCertificateFile", Type.OPENSSL);
        this.caCertificateFile = caCertificateFile;
    }


    public String getCaCertificateFile() {
        return caCertificateFile;
    }


    public void setCaCertificatePath(String caCertificatePath) {
        setProperty("caCertificatePath", Type.OPENSSL);
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

    public static String adjustRelativePath(String path) {
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
            // TODO i18n, sm
            log.warn("configured file:["+newPath+"] does not exist.");
        }
        return newPath;
    }


    // ----------------------------------------------------------- Inner classes

    public static enum Type {
        JSSE,
        OPENSSL
    }


    public static enum CertificateVerification {
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
