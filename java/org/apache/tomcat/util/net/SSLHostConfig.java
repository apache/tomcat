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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.net.ssl.KeyManagerFactory;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.net.jsse.openssl.OpenSSLCipherConfigurationParser;
import org.apache.tomcat.util.res.StringManager;

public class SSLHostConfig {

    private static final Log log = LogFactory.getLog(SSLHostConfig.class);
    private static final StringManager sm = StringManager.getManager(SSLHostConfig.class);

    public static final String DEFAULT_SSL_HOST_NAME = "_default_";

    private Type configType = null;
    private Map<Type,Set<String>> configuredProperties = new HashMap<>();

    private String hostName = DEFAULT_SSL_HOST_NAME;

    private Object sslContext;

    // Configuration properties

    // Common
    private String certificateKeyPassword = null;
    private CertificateVerification certificateVerification = CertificateVerification.NONE;
    private int certificateVerificationDepth = 10;
    private String ciphers = "HIGH:!aNULL:!eNULL:!EXPORT:!DES:!RC4:!MD5:!kRSA";
    private boolean honorCipherOrder = false;
    private Set<String> protocols = new HashSet<>();
    private String certificateRevocationListFile;
    // JSSE
    private String certificateKeystorePassword = "changeit";
    private String certificateKeystoreFile = System.getProperty("user.home")+"/.keystore";
    private String keyManagerAlgorithm = KeyManagerFactory.getDefaultAlgorithm();
    // OpenSSL
    private String certificateFile;
    private String certificateKeyFile;
    private String certificateRevocationListPath;

    public SSLHostConfig() {
        // Set defaults that can't be (easily) set when defining the fields.
        setProtocols("all");
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
        // OpenSSL and JSSE use the same names.
        String[] values = input.split(",|\\+");

        protocols.clear();

        for (String value: values) {
            String trimmed = value.trim();
            if (trimmed.length() > 0) {
                if (input.trim().equalsIgnoreCase("all")) {
                    protocols.add("TLSv1");
                    protocols.add("TLSv1.1");
                    protocols.add("TLSv1.2");
                } else {
                    protocols.add(trimmed);
                }
            }
        }
    }


    public Set<String> getProtocols() {
        return protocols;
    }


    // ---------------------------------- JSSE specific configuration properties

    public void setCertificateKeystorePassword(String certificateKeystorePassword) {
        this.certificateKeystorePassword = certificateKeystorePassword;
    }


    public String getCertificateKeystorePassword() {
        return certificateKeystorePassword;
    }


    public void setCertificateKeystoreFile(String certificateKeystoreFile) {
        setProperty("certificateKeystoreFile", Type.JSSE);
        this.certificateKeystoreFile = certificateKeystoreFile;
    }


    public String getCertificateKeystoreFile() {
        return certificateKeystoreFile;
    }


    public void setKeyManagerAlgorithm(String keyManagerAlgorithm) {
        setProperty("keyManagerAlgorithm", Type.JSSE);
        this.keyManagerAlgorithm = keyManagerAlgorithm;
    }


    public String getKeyManagerAlgorithm() {
        return keyManagerAlgorithm;
    }


    // ------------------------------- OpenSSL specific configuration properties

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
