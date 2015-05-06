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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CRL;
import java.security.cert.CRLException;
import java.security.cert.CertPathParameters;
import java.security.cert.CertStore;
import java.security.cert.CertStoreParameters;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.CollectionCertStoreParameters;
import java.security.cert.PKIXBuilderParameters;
import java.security.cert.X509CertSelector;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

import javax.net.ssl.CertPathTrustManagerParameters;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.ManagerFactoryParameters;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSessionContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509KeyManager;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.net.AbstractEndpoint;
import org.apache.tomcat.util.net.Constants;
import org.apache.tomcat.util.net.SSLContext;
import org.apache.tomcat.util.net.SSLHostConfig;
import org.apache.tomcat.util.net.SSLUtil;
import org.apache.tomcat.util.net.jsse.openssl.OpenSSLCipherConfigurationParser;
import org.apache.tomcat.util.res.StringManager;

/**
 * SSL server socket factory. It <b>requires</b> a valid RSA key and
 * JSSE.<br>
 * keytool -genkey -alias tomcat -keyalg RSA<br>
 * Use "changeit" as password (this is the default we use).
 *
 * @author Harish Prabandham
 * @author Costin Manolache
 * @author Stefan Freyr Stefansson
 * @author EKR -- renamed to JSSESocketFactory
 * @author Jan Luehe
 */
public class JSSESocketFactory implements SSLUtil {

    private static final Log log = LogFactory.getLog(JSSESocketFactory.class);
    private static final StringManager sm =
        StringManager.getManager("org.apache.tomcat.util.net.jsse.res");

    // Defaults - made public where re-used
    private static final String defaultProtocol = "TLS";
    private static final int defaultSessionCacheSize = 0;
    private static final int defaultSessionTimeout = 86400;

    private final AbstractEndpoint<?> endpoint;
    private final SSLHostConfig sslHostConfig;

    private final String[] defaultServerProtocols;


    public JSSESocketFactory (AbstractEndpoint<?> endpoint, SSLHostConfig sslHostConfig) {
        this.endpoint = endpoint;
        this.sslHostConfig = sslHostConfig;

        String sslProtocol = endpoint.getSslProtocol();
        if (sslProtocol == null) {
            sslProtocol = defaultProtocol;
        }

        javax.net.ssl.SSLContext context;
        try {
             context = javax.net.ssl.SSLContext.getInstance(sslProtocol);
             context.init(null,  null,  null);
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            // This is fatal for the connector so throw an exception to prevent
            // it from starting
            throw new IllegalArgumentException(e);
        }

        // There is no standard way to determine the default protocols and
        // cipher suites so create a server socket to see what the defaults are
        SSLServerSocketFactory ssf = context.getServerSocketFactory();
        SSLServerSocket socket;
        try {
            socket = (SSLServerSocket) ssf.createServerSocket();
        } catch (IOException e) {
            // This is very likely to be fatal but there is a slim chance that
            // the JSSE implementation just doesn't like creating unbound
            // sockets so allow the code to proceed.
            defaultServerProtocols = new String[0];
            log.warn(sm.getString("jsse.noDefaultProtocols", endpoint.getName()));
            return;
        }

        try {
            // Filter out all the SSL protocols (SSLv2 and SSLv3) from the
            // defaults
            // since they are no longer considered secure
            List<String> filteredProtocols = new ArrayList<>();
            for (String protocol : socket.getEnabledProtocols()) {
                if (protocol.toUpperCase(Locale.ENGLISH).contains("SSL")) {
                    log.debug(sm.getString("jsse.excludeDefaultProtocol",
                            protocol));
                    continue;
                }
                filteredProtocols.add(protocol);
            }
            defaultServerProtocols = filteredProtocols
                    .toArray(new String[filteredProtocols.size()]);
            if (defaultServerProtocols.length == 0) {
                log.warn(sm.getString("jsse.noDefaultProtocols",
                        endpoint.getName()));
            }
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                log.warn(sm.getString("jsse.exceptionOnClose"), e);
            }
        }
    }


    @Override
    public String[] getEnableableCiphers(SSLContext context) {
        String requestedCiphersStr = sslHostConfig.getCiphers();

        List<String> requestedCiphers =
                OpenSSLCipherConfigurationParser.parseExpression(requestedCiphersStr);

        List<String> ciphers = new ArrayList<>(requestedCiphers);
        ciphers.retainAll(Arrays.asList(context.getSupportedSSLParameters().getCipherSuites()));

        if (ciphers.isEmpty()) {
            log.warn(sm.getString("jsse.requested_ciphers_not_supported",
                    requestedCiphersStr));
        }
        if (log.isDebugEnabled()) {
            log.debug(sm.getString("jsse.enableable_ciphers", ciphers));
            if (ciphers.size() != requestedCiphers.size()) {
                List<String> skipped = new ArrayList<>(requestedCiphers);
                skipped.removeAll(ciphers);
                log.debug(sm.getString("jsse.unsupported_ciphers", skipped));
            }
        }

        return ciphers.toArray(new String[ciphers.size()]);
    }


    /*
     * Gets the SSL server's truststore.
     */
    protected KeyStore getTrustStore() throws IOException {
        KeyStore trustStore = null;

        String truststoreFile = sslHostConfig.getTruststoreFile();
        String truststoreType = sslHostConfig.getTruststoreType();
        String truststoreProvider = sslHostConfig.getTruststoreProvider();

        if (truststoreFile != null){
            try {
                trustStore = getStore(truststoreType, truststoreProvider,
                        truststoreFile, sslHostConfig.getTruststorePassword());
            } catch (IOException ioe) {
                Throwable cause = ioe.getCause();
                if (cause instanceof UnrecoverableKeyException) {
                    // Log a warning we had a password issue
                    log.warn(sm.getString("jsse.invalid_truststore_password"),
                            cause);
                    // Re-try
                    trustStore = getStore(truststoreType, truststoreProvider,
                            truststoreFile, null);
                } else {
                    // Something else went wrong - re-throw
                    throw ioe;
                }
            }
        }

        return trustStore;
    }


    /*
     * Gets the key- or truststore with the specified type, path, and password.
     */
    private KeyStore getStore(String type, String provider, String path,
            String pass) throws IOException {

        KeyStore ks = null;
        InputStream istream = null;
        try {
            if (provider == null) {
                ks = KeyStore.getInstance(type);
            } else {
                ks = KeyStore.getInstance(type, provider);
            }
            if(!("PKCS11".equalsIgnoreCase(type) ||
                    "".equalsIgnoreCase(path)) ||
                    "NONE".equalsIgnoreCase(path)) {
                File keyStoreFile = new File(path);
                istream = new FileInputStream(keyStoreFile);
            }

            char[] storePass = null;
            if (pass != null && !"".equals(pass)) {
                storePass = pass.toCharArray();
            }
            ks.load(istream, storePass);
        } catch (FileNotFoundException fnfe) {
            log.error(sm.getString("jsse.keystore_load_failed", type, path,
                    fnfe.getMessage()), fnfe);
            throw fnfe;
        } catch (IOException ioe) {
            // May be expected when working with a trust store
            // Re-throw. Caller will catch and log as required
            throw ioe;
        } catch(Exception ex) {
            String msg = sm.getString("jsse.keystore_load_failed", type, path,
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
    public SSLContext createSSLContext() throws Exception {

        // SSL protocol variant (e.g., TLS, SSL v3, etc.)
        String protocol = endpoint.getSslProtocol();
        if (protocol == null) {
            protocol = defaultProtocol;
        }

        return new JSSESSLContext(protocol);
    }


    @Override
    public KeyManager[] getKeyManagers() throws Exception {
        String keystoreType = sslHostConfig.getCertificateKeystoreType();
        String keystoreProvider = sslHostConfig.getCertificateKeystoreProvider();
        String keystoreFile = sslHostConfig.getCertificateKeystoreFile();
        String keystorePass = sslHostConfig.getCertificateKeystorePassword();
        String keyAlias = sslHostConfig.getCertificateKeyAlias();
        String algorithm = sslHostConfig.getKeyManagerAlgorithm();
        String keyPass = sslHostConfig.getCertificateKeyPassword();
        // This has to be here as it can't be moved to SSLHostConfig since the
        // defaults vary between JSSE and OpenSSL.
        if (keyPass == null) {
            keyPass = sslHostConfig.getCertificateKeystorePassword();
        }

        KeyManager[] kms = null;

        KeyStore ks = getStore(keystoreType, keystoreProvider, keystoreFile, keystorePass);
        if (keyAlias != null && !ks.isKeyEntry(keyAlias)) {
            throw new IOException(
                    sm.getString("jsse.alias_no_key_entry", keyAlias));
        }

        KeyManagerFactory kmf = KeyManagerFactory.getInstance(algorithm);
        kmf.init(ks, keyPass.toCharArray());

        kms = kmf.getKeyManagers();
        if (keyAlias != null) {
            String alias = keyAlias;
            if ("JKS".equals(keystoreType)) {
                alias = alias.toLowerCase(Locale.ENGLISH);
            }
            for(int i=0; i<kms.length; i++) {
                kms[i] = new JSSEKeyManager((X509KeyManager)kms[i], alias);
            }
        }

        return kms;
    }


    @Override
    public TrustManager[] getTrustManagers() throws Exception {
        String algorithm = sslHostConfig.getTruststoreAlgorithm();

        String crlf = sslHostConfig.getCertificateRevocationListFile();

        String className = sslHostConfig.getTrustManagerClassName();
        if(className != null && className.length() > 0) {
             ClassLoader classLoader = getClass().getClassLoader();
             Class<?> clazz = classLoader.loadClass(className);
             if(!(TrustManager.class.isAssignableFrom(clazz))){
                throw new InstantiationException(sm.getString(
                        "jsse.invalidTrustManagerClassName", className));
             }
             Object trustManagerObject = clazz.newInstance();
             TrustManager trustManager = (TrustManager) trustManagerObject;
             return new TrustManager[]{ trustManager };
        }

        TrustManager[] tms = null;

        KeyStore trustStore = getTrustStore();
        if (trustStore != null || className != null) {
            if (crlf == null) {
                TrustManagerFactory tmf = TrustManagerFactory.getInstance(algorithm);
                tmf.init(trustStore);
                tms = tmf.getTrustManagers();
            } else {
                TrustManagerFactory tmf = TrustManagerFactory.getInstance(algorithm);
                CertPathParameters params = getParameters(algorithm, crlf, trustStore);
                ManagerFactoryParameters mfp = new CertPathTrustManagerParameters(params);
                tmf.init(mfp);
                tms = tmf.getTrustManagers();
            }
        }

        return tms;
    }

    @Override
    public void configureSessionContext(SSLSessionContext sslSessionContext) {
        int sessionCacheSize;
        if (endpoint.getSessionCacheSize() != null) {
            sessionCacheSize = Integer.parseInt(
                    endpoint.getSessionCacheSize());
        } else {
            sessionCacheSize = defaultSessionCacheSize;
        }

        int sessionTimeout;
        if (endpoint.getSessionTimeout() != null) {
            sessionTimeout = Integer.parseInt(endpoint.getSessionTimeout());
        } else {
            sessionTimeout = defaultSessionTimeout;
        }

        sslSessionContext.setSessionCacheSize(sessionCacheSize);
        sslSessionContext.setSessionTimeout(sessionTimeout);
    }


    /**
     * Return the initialization parameters for the TrustManager.
     * Currently, only the default <code>PKIX</code> is supported.
     *
     * @param algorithm The algorithm to get parameters for.
     * @param crlf The path to the CRL file.
     * @param trustStore The configured TrustStore.
     * @return The parameters including the CRLs and TrustStore.
     */
    protected CertPathParameters getParameters(String algorithm, String crlf,
            KeyStore trustStore) throws Exception {

        if("PKIX".equalsIgnoreCase(algorithm)) {
            PKIXBuilderParameters xparams =
                    new PKIXBuilderParameters(trustStore, new X509CertSelector());
            Collection<? extends CRL> crls = getCRLs(crlf);
            CertStoreParameters csp = new CollectionCertStoreParameters(crls);
            CertStore store = CertStore.getInstance("Collection", csp);
            xparams.addCertStore(store);
            xparams.setRevocationEnabled(true);
            xparams.setMaxPathLength(sslHostConfig.getCertificateVerificationDepth());
            return xparams;
        } else {
            throw new CRLException("CRLs not supported for type: "+algorithm);
        }
    }


    /**
     * Load the collection of CRLs.
     *
     */
    protected Collection<? extends CRL> getCRLs(String crlf)
        throws IOException, CRLException, CertificateException {

        File crlFile = new File(crlf);
        if( !crlFile.isAbsolute() ) {
            crlFile = new File(
                    System.getProperty(Constants.CATALINA_BASE_PROP), crlf);
        }
        Collection<? extends CRL> crls = null;
        try {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            try (InputStream is = new FileInputStream(crlFile)) {
                crls = cf.generateCRLs(is);
            }
        } catch(IOException iex) {
            throw iex;
        } catch(CRLException crle) {
            throw crle;
        } catch(CertificateException ce) {
            throw ce;
        }
        return crls;
    }

    @Override
    public String[] getEnableableProtocols(SSLContext context) {
        if (sslHostConfig.getProtocols().size() == 0) {
            return defaultServerProtocols;
        }

        List<String> protocols = new ArrayList<>();
        protocols.addAll(sslHostConfig.getProtocols());
        protocols.retainAll(Arrays.asList(context.getSupportedSSLParameters()
                .getProtocols()));

        if (protocols.isEmpty()) {
            log.warn(sm.getString("jsse.requested_protocols_not_supported",
                    sslHostConfig.getProtocols()));
        }
        if (log.isDebugEnabled()) {
            log.debug(sm.getString("jsse.enableable_protocols", protocols));
            if (protocols.size() != sslHostConfig.getProtocols().size()) {
                List<String> skipped = new ArrayList<>();
                skipped.addAll(sslHostConfig.getProtocols());
                skipped.removeAll(protocols);
                log.debug(sm.getString("jsse.unsupported_protocols", skipped));
            }
        }
        return protocols.toArray(new String[protocols.size()]);
    }
}
