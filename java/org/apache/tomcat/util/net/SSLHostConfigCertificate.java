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
import java.io.IOException;
import java.io.Serializable;
import java.security.KeyStore;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import javax.management.ObjectName;
import javax.net.ssl.X509KeyManager;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.net.openssl.ciphers.Authentication;
import org.apache.tomcat.util.net.openssl.ciphers.SignatureScheme;
import org.apache.tomcat.util.res.StringManager;

/**
 * Represents the SSL certificate configuration for a virtual host.
 * Holds the certificate details for either JSSE or OpenSSL implementations.
 */
public class SSLHostConfigCertificate implements Serializable {

    private static final long serialVersionUID = 1L;

    private static final Log log = LogFactory.getLog(SSLHostConfigCertificate.class);
    private static final StringManager sm = StringManager.getManager(SSLHostConfigCertificate.class);

    /**
     * The default certificate type when none is explicitly specified.
     */
    public static final Type DEFAULT_TYPE = Type.UNDEFINED;

    static final String DEFAULT_KEYSTORE_PROVIDER = System.getProperty("javax.net.ssl.keyStoreProvider");
    static final String DEFAULT_KEYSTORE_TYPE = System.getProperty("javax.net.ssl.keyStoreType", "JKS");
    private static final String DEFAULT_KEYSTORE_FILE = System.getProperty("user.home") + File.separator + ".keystore";
    private static final String DEFAULT_KEYSTORE_PASSWORD = "changeit";

    // Internal
    private ObjectName oname;

    /*
     * OpenSSL can handle multiple certs in a single config so the reference to the context is at the virtual host
     * level. JSSE can't so the reference is held here on the certificate. Typically, the SSLContext is generated from
     * the configuration but, particularly in embedded scenarios, it can be provided directly.
     */
    private transient volatile SSLContext sslContextProvided;
    private transient volatile SSLContext sslContextGenerated;


    // Common
    private final SSLHostConfig sslHostConfig;
    private final Type type;
    private String certificateKeyPassword = null;
    private String certificateKeyPasswordFile = null;

    // JSSE
    private String certificateKeyAlias;
    private String certificateKeystorePassword = DEFAULT_KEYSTORE_PASSWORD;
    private String certificateKeystorePasswordFile = null;
    private String certificateKeystoreFile = DEFAULT_KEYSTORE_FILE;
    private String certificateKeystoreProvider = DEFAULT_KEYSTORE_PROVIDER;
    private String certificateKeystoreType = DEFAULT_KEYSTORE_TYPE;
    private transient KeyStore certificateKeystore = null;
    private transient X509KeyManager certificateKeyManager = null;

    // OpenSSL
    private String certificateChainFile;
    private String certificateFile;
    private String certificateKeyFile;

    // Certificate store type
    private StoreType storeType = null;

    /**
     * Creates a new certificate configuration with default settings.
     */
    public SSLHostConfigCertificate() {
        this(null, DEFAULT_TYPE);
    }

    /**
     * Creates a new certificate configuration for the given host and type.
     *
     * @param sslHostConfig the parent SSL host configuration
     * @param type the type of this certificate
     */
    public SSLHostConfigCertificate(SSLHostConfig sslHostConfig, Type type) {
        this.sslHostConfig = sslHostConfig;
        this.type = type;
    }


    /**
     * Returns the SSLContext for this certificate. Returns the provided
     * context if set, otherwise returns the generated context.
     *
     * @return the SSLContext instance
     */
    public SSLContext getSslContext() {
        if (sslContextProvided != null) {
            return sslContextProvided;
        }
        return sslContextGenerated;
    }


    /**
     * Sets the provided SSLContext for this certificate configuration.
     *
     * @param sslContext the SSLContext to use
     */
    public void setSslContext(SSLContext sslContext) {
        this.sslContextProvided = sslContext;
    }


    /**
     * Returns the SSLContext generated from the certificate configuration.
     *
     * @return the generated SSLContext, or null if not yet generated
     */
    public SSLContext getSslContextGenerated() {
        return sslContextGenerated;
    }


    void setSslContextGenerated(SSLContext sslContext) {
        this.sslContextGenerated = sslContext;
    }


    /**
     * Returns the SSLHostConfig that owns this certificate configuration.
     *
     * @return the parent SSLHostConfig
     */
    public SSLHostConfig getSSLHostConfig() {
        return sslHostConfig;
    }


    // Internal

    /**
     * Returns the JMX ObjectName for this certificate configuration.
     *
     * @return the JMX ObjectName
     */
    public ObjectName getObjectName() {
        return oname;
    }


    /**
     * Sets the JMX ObjectName for this certificate configuration.
     *
     * @param oname the JMX ObjectName
     */
    public void setObjectName(ObjectName oname) {
        this.oname = oname;
    }


    // Common

    /**
     * Returns the type of this certificate configuration.
     *
     * @return the certificate type
     */
    public Type getType() {
        return type;
    }


    /**
     * Returns the password for the certificate's private key.
     *
     * @return the certificate key password
     */
    public String getCertificateKeyPassword() {
        return certificateKeyPassword;
    }


    /**
     * Sets the password for the certificate's private key.
     *
     * @param certificateKeyPassword the certificate key password
     */
    public void setCertificateKeyPassword(String certificateKeyPassword) {
        this.certificateKeyPassword = certificateKeyPassword;
    }


    /**
     * Returns the path to the file containing the certificate key password.
     *
     * @return the key password file path
     */
    public String getCertificateKeyPasswordFile() {
        return certificateKeyPasswordFile;
    }


    /**
     * Sets the path to the file containing the certificate key password.
     *
     * @param certificateKeyPasswordFile the key password file path
     */
    public void setCertificateKeyPasswordFile(String certificateKeyPasswordFile) {
        this.certificateKeyPasswordFile = certificateKeyPasswordFile;
    }


    // JSSE

    /**
     * Sets the alias of the key entry in the keystore.
     *
     * @param certificateKeyAlias the key alias
     */
    public void setCertificateKeyAlias(String certificateKeyAlias) {
        sslHostConfig.setProperty("Certificate.certificateKeyAlias", SSLHostConfig.Type.JSSE);
        this.certificateKeyAlias = certificateKeyAlias;
    }


    /**
     * Returns the alias of the key entry in the keystore.
     *
     * @return the key alias
     */
    public String getCertificateKeyAlias() {
        return certificateKeyAlias;
    }


    /**
     * Sets the path to the keystore file.
     *
     * @param certificateKeystoreFile the keystore file path
     */
    public void setCertificateKeystoreFile(String certificateKeystoreFile) {
        sslHostConfig.setProperty("Certificate.certificateKeystoreFile", SSLHostConfig.Type.JSSE);
        setStoreType("Certificate.certificateKeystoreFile", StoreType.KEYSTORE);
        this.certificateKeystoreFile = certificateKeystoreFile;
    }


    /**
     * Returns the path to the keystore file.
     *
     * @return the keystore file path
     */
    public String getCertificateKeystoreFile() {
        return certificateKeystoreFile;
    }


    /**
     * Sets the password used to access the keystore.
     *
     * @param certificateKeystorePassword the keystore password
     */
    public void setCertificateKeystorePassword(String certificateKeystorePassword) {
        sslHostConfig.setProperty("Certificate.certificateKeystorePassword", SSLHostConfig.Type.JSSE);
        setStoreType("Certificate.certificateKeystorePassword", StoreType.KEYSTORE);
        this.certificateKeystorePassword = certificateKeystorePassword;
    }


    /**
     * Returns the password used to access the keystore.
     *
     * @return the keystore password
     */
    public String getCertificateKeystorePassword() {
        return certificateKeystorePassword;
    }


    /**
     * Sets the path to the file containing the keystore password.
     *
     * @param certificateKeystorePasswordFile the keystore password file path
     */
    public void setCertificateKeystorePasswordFile(String certificateKeystorePasswordFile) {
        sslHostConfig.setProperty("Certificate.certificateKeystorePasswordFile", SSLHostConfig.Type.JSSE);
        setStoreType("Certificate.certificateKeystorePasswordFile", StoreType.KEYSTORE);
        this.certificateKeystorePasswordFile = certificateKeystorePasswordFile;
    }


    /**
     * Returns the path to the file containing the keystore password.
     *
     * @return the keystore password file path
     */
    public String getCertificateKeystorePasswordFile() {
        return certificateKeystorePasswordFile;
    }


    /**
     * Sets the provider of the keystore.
     *
     * @param certificateKeystoreProvider the keystore provider name
     */
    public void setCertificateKeystoreProvider(String certificateKeystoreProvider) {
        sslHostConfig.setProperty("Certificate.certificateKeystoreProvider", SSLHostConfig.Type.JSSE);
        setStoreType("Certificate.certificateKeystoreProvider", StoreType.KEYSTORE);
        this.certificateKeystoreProvider = certificateKeystoreProvider;
    }


    /**
     * Returns the provider of the keystore.
     *
     * @return the keystore provider name
     */
    public String getCertificateKeystoreProvider() {
        return certificateKeystoreProvider;
    }


    /**
     * Sets the type of the keystore (e.g., JKS, PKCS12).
     *
     * @param certificateKeystoreType the keystore type
     */
    public void setCertificateKeystoreType(String certificateKeystoreType) {
        sslHostConfig.setProperty("Certificate.certificateKeystoreType", SSLHostConfig.Type.JSSE);
        setStoreType("Certificate.certificateKeystoreType", StoreType.KEYSTORE);
        this.certificateKeystoreType = certificateKeystoreType;
    }


    /**
     * Returns the type of the keystore (e.g., JKS, PKCS12).
     *
     * @return the keystore type
     */
    public String getCertificateKeystoreType() {
        return certificateKeystoreType;
    }


    /**
     * Sets the Java KeyStore for this certificate.
     *
     * @param certificateKeystore the KeyStore instance
     */
    public void setCertificateKeystore(KeyStore certificateKeystore) {
        this.certificateKeystore = certificateKeystore;
        if (certificateKeystore != null) {
            setCertificateKeystoreType(certificateKeystore.getType());
        }
    }


    /**
     * Returns the Java KeyStore for this certificate. If not previously set,
     * loads it from the configured keystore file.
     *
     * @return the KeyStore instance
     * @throws IOException if the keystore cannot be loaded
     */
    public KeyStore getCertificateKeystore() throws IOException {
        KeyStore result = certificateKeystore;

        if (result == null && storeType == StoreType.KEYSTORE) {
            result = SSLUtilBase.getStore(getCertificateKeystoreType(), getCertificateKeystoreProvider(),
                    getCertificateKeystoreFile(), getCertificateKeystorePassword(),
                    getCertificateKeystorePasswordFile());
        }

        return result;
    }


    KeyStore getCertificateKeystoreInternal() {
        return certificateKeystore;
    }


    /**
     * Sets the X509KeyManager for this certificate configuration.
     *
     * @param certificateKeyManager the X509KeyManager instance
     */
    public void setCertificateKeyManager(X509KeyManager certificateKeyManager) {
        this.certificateKeyManager = certificateKeyManager;
    }


    /**
     * Returns the X509KeyManager for this certificate configuration.
     *
     * @return the X509KeyManager instance
     */
    public X509KeyManager getCertificateKeyManager() {
        return certificateKeyManager;
    }


    // OpenSSL

    /**
     * Sets the path to the certificate chain file used by OpenSSL.
     *
     * @param certificateChainFile the path to the certificate chain file
     */
    public void setCertificateChainFile(String certificateChainFile) {
        setStoreType("Certificate.certificateChainFile", StoreType.PEM);
        this.certificateChainFile = certificateChainFile;
    }


    /**
     * Returns the path to the certificate chain file used by OpenSSL.
     *
     * @return the certificate chain file path
     */
    public String getCertificateChainFile() {
        return certificateChainFile;
    }


    /**
     * Sets the path to the certificate file used by OpenSSL.
     *
     * @param certificateFile the path to the certificate file
     */
    public void setCertificateFile(String certificateFile) {
        setStoreType("Certificate.certificateFile", StoreType.PEM);
        this.certificateFile = certificateFile;
    }


    /**
     * Returns the path to the certificate file used by OpenSSL.
     *
     * @return the certificate file path
     */
    public String getCertificateFile() {
        return certificateFile;
    }


    /**
     * Sets the path to the private key file used by OpenSSL.
     *
     * @param certificateKeyFile the path to the private key file
     */
    public void setCertificateKeyFile(String certificateKeyFile) {
        setStoreType("Certificate.certificateKeyFile", StoreType.PEM);
        this.certificateKeyFile = certificateKeyFile;
    }


    /**
     * Returns the path to the private key file used by OpenSSL.
     *
     * @return the private key file path
     */
    public String getCertificateKeyFile() {
        return certificateKeyFile;
    }


    private void setStoreType(String name, StoreType type) {
        if (storeType == null) {
            storeType = type;
        } else if (storeType != type) {
            log.warn(sm.getString("sslHostConfigCertificate.mismatch", name, sslHostConfig.getHostName(), type,
                    this.storeType));
        }
    }

    StoreType getStoreType() {
        return storeType;
    }


    /**
     * Defines the types of SSL certificates supported.
     */
    public enum Type {

        /**
         * Unspecified certificate type.
         */
        UNDEFINED,

        /**
         * RSA certificate type.
         */
        RSA(Authentication.RSA),

        /**
         * DSA certificate type.
         */
        DSA(Authentication.DSS, Authentication.EdDSA),

        /**
         * Elliptic Curve certificate type.
         */
        EC(Authentication.ECDH, Authentication.ECDSA),

        /**
         * ML-DSA certificate type.
         */
        MLDSA("ML-DSA", Authentication.MLDSA);

        private final String keyType;
        private final Set<Authentication> compatibleAuthentications;

        Type(Authentication... authentications) {
            this(null, authentications);
        }

        Type(String keyType, Authentication... authentications) {
            this.keyType = keyType;
            compatibleAuthentications = new HashSet<>();
            if (authentications != null) {
                compatibleAuthentications.addAll(Arrays.asList(authentications));
            }
        }

        /**
         * Checks if this certificate type is compatible with the given authentication type.
         *
         * @param au the authentication type to check
         * @return true if compatible
         */
        public boolean isCompatibleWith(Authentication au) {
            return compatibleAuthentications.contains(au);
        }

        /**
         * Checks if this certificate type is compatible with the given signature scheme.
         *
         * @param scheme the signature scheme to check
         * @return true if compatible
         */
        public boolean isCompatibleWith(SignatureScheme scheme) {
            return compatibleAuthentications.contains(scheme.getAuth());
        }

        /**
         * Returns the key type for this certificate type.
         *
         * @return the key type string
         */
        public String getKeyType() {
            if (keyType != null) {
                return keyType;
            }
            return super.toString();
        }

    }

    enum StoreType {
        KEYSTORE,
        PEM
    }
}
