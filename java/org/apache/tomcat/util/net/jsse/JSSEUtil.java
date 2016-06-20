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
import java.io.InputStream;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CRL;
import java.security.cert.CRLException;
import java.security.cert.CertPathParameters;
import java.security.cert.CertStore;
import java.security.cert.CertStoreParameters;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.CollectionCertStoreParameters;
import java.security.cert.PKIXBuilderParameters;
import java.security.cert.X509CertSelector;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

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
import org.apache.tomcat.util.compat.JreVendor;
import org.apache.tomcat.util.file.ConfigFileLoader;
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

        // There is no standard way to determine the default protocols and
        // cipher suites so create a server socket to see what the defaults are
        SSLServerSocketFactory ssf = context.getServerSocketFactory();
        implementedProtocols = new HashSet<>();
        try (SSLServerSocket socket = (SSLServerSocket) ssf.createServerSocket()) {
            // Filter out all the SSL protocols (SSLv2 and SSLv3) from the
            // defaults since they are no longer considered secure but allow
            // SSLv2Hello
            for (String protocol : socket.getEnabledProtocols()) {
                String protocolUpper = protocol.toUpperCase(Locale.ENGLISH);
                if (!"SSLV2HELLO".equals(protocolUpper)) {
                    if (protocolUpper.contains("SSL")) {
                        log.debug(sm.getString("jsse.excludeDefaultProtocol", protocol));
                        continue;
                    }
                }
                implementedProtocols.add(protocol);
            }
        } catch (IOException e) {
            // This is very likely to be fatal but there is a slim chance that
            // the JSSE implementation just doesn't like creating unbound
            // sockets so allow the code to proceed.

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


    private final SSLHostConfig sslHostConfig;


    public JSSEUtil (SSLHostConfigCertificate certificate) {
        super(certificate);
        this.sslHostConfig = certificate.getSSLHostConfig();
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
    public SSLContext createSSLContext(List<String> negotiableProtocols) throws NoSuchAlgorithmException {
        return new JSSESSLContext(sslHostConfig.getSslProtocol());
    }


    @Override
    public KeyManager[] getKeyManagers() throws Exception {
        String keystoreType = certificate.getCertificateKeystoreType();
        String keyAlias = certificate.getCertificateKeyAlias();
        String algorithm = sslHostConfig.getKeyManagerAlgorithm();
        String keyPass = certificate.getCertificateKeyPassword();
        // This has to be here as it can't be moved to SSLHostConfig since the
        // defaults vary between JSSE and OpenSSL.
        if (keyPass == null) {
            keyPass = certificate.getCertificateKeystorePassword();
        }

        KeyManager[] kms = null;

        KeyStore ks = certificate.getCertificateKeystore();

        if (ks == null) {
            // create an in-memory keystore and import the private key
            // and the certificate chain from the PEM files
            ks = KeyStore.getInstance("JKS");
            ks.load(null, null);

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

            ks.setKeyEntry(keyAlias, privateKeyFile.getPrivateKey(), keyPass.toCharArray(), chain.toArray(new Certificate[chain.size()]));
        }

        if (keyAlias != null && !ks.isKeyEntry(keyAlias)) {
            throw new IOException(sm.getString("jsse.alias_no_key_entry", keyAlias));
        }

        KeyManagerFactory kmf = KeyManagerFactory.getInstance(algorithm);
        kmf.init(ks, keyPass.toCharArray());

        kms = kmf.getKeyManagers();
        if (kms == null) {
            return kms;
        }

        if (keyAlias != null) {
            String alias = keyAlias;
            // JKS keystores always convert the alias name to lower case
            if ("JKS".equals(keystoreType)) {
                alias = alias.toLowerCase(Locale.ENGLISH);
            }
            for(int i = 0; i < kms.length; i++) {
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

        KeyStore trustStore = sslHostConfig.getTruststore();
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
        sslSessionContext.setSessionCacheSize(sslHostConfig.getSessionCacheSize());
        sslSessionContext.setSessionTimeout(sslHostConfig.getSessionTimeout());
    }


    /**
     * Return the initialization parameters for the TrustManager.
     * Currently, only the default <code>PKIX</code> is supported.
     *
     * @param algorithm The algorithm to get parameters for.
     * @param crlf The path to the CRL file.
     * @param trustStore The configured TrustStore.
     * @return The parameters including the CRLs and TrustStore.
     * @throws Exception An error occurred
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
            try (InputStream is = ConfigFileLoader.getInputStream(crlf)) {
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
}
