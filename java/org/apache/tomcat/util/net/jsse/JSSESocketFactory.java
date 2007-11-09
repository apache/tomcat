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
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.security.KeyStore;
import java.security.SecureRandom;
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
import java.util.Collection;
import java.util.Vector;

import javax.net.ssl.CertPathTrustManagerParameters;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.ManagerFactoryParameters;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509KeyManager;

import org.apache.tomcat.util.res.StringManager;

/*
  1. Make the JSSE's jars available, either as an installed
     extension (copy them into jre/lib/ext) or by adding
     them to the Tomcat classpath.
  2. keytool -genkey -alias tomcat -keyalg RSA
     Use "changeit" as password ( this is the default we use )
 */

/**
 * SSL server socket factory. It _requires_ a valid RSA key and
 * JSSE. 
 *
 * @author Harish Prabandham
 * @author Costin Manolache
 * @author Stefan Freyr Stefansson
 * @author EKR -- renamed to JSSESocketFactory
 * @author Jan Luehe
 * @author Bill Barker
 */
public class JSSESocketFactory
    extends org.apache.tomcat.util.net.ServerSocketFactory {

    private static StringManager sm =
        StringManager.getManager("org.apache.tomcat.util.net.jsse.res");

    // defaults
    static String defaultProtocol = "TLS";
    static boolean defaultClientAuth = false;
    static String defaultKeystoreType = "JKS";
    private static final String defaultKeystoreFile
        = System.getProperty("user.home") + "/.keystore";
    private static final String defaultKeyPass = "changeit";
    static org.apache.juli.logging.Log log =
        org.apache.juli.logging.LogFactory.getLog(JSSESocketFactory.class);

    protected boolean initialized;
    protected String clientAuth = "false";
    protected SSLServerSocketFactory sslProxy = null;
    protected String[] enabledCiphers;

    /**
     * Flag to state that we require client authentication.
     */
    protected boolean requireClientAuth = false;

    /**
     * Flag to state that we would like client authentication.
     */
    protected boolean wantClientAuth    = false;


    public JSSESocketFactory () {
    }

    public ServerSocket createSocket (int port)
        throws IOException
    {
        if (!initialized) init();
        ServerSocket socket = sslProxy.createServerSocket(port);
        initServerSocket(socket);
        return socket;
    }
    
    public ServerSocket createSocket (int port, int backlog)
        throws IOException
    {
        if (!initialized) init();
        ServerSocket socket = sslProxy.createServerSocket(port, backlog);
        initServerSocket(socket);
        return socket;
    }
    
    public ServerSocket createSocket (int port, int backlog,
                                      InetAddress ifAddress)
        throws IOException
    {   
        if (!initialized) init();
        ServerSocket socket = sslProxy.createServerSocket(port, backlog,
                                                          ifAddress);
        initServerSocket(socket);
        return socket;
    }
    
    public Socket acceptSocket(ServerSocket socket)
        throws IOException
    {
        SSLSocket asock = null;
        try {
             asock = (SSLSocket)socket.accept();
             configureClientAuth(asock);
        } catch (SSLException e){
          throw new SocketException("SSL handshake error" + e.toString());
        }
        return asock;
    }

    public void handshake(Socket sock) throws IOException {
        ((SSLSocket)sock).startHandshake();
    }

    /*
     * Determines the SSL cipher suites to be enabled.
     *
     * @param requestedCiphers Comma-separated list of requested ciphers
     * @param supportedCiphers Array of supported ciphers
     *
     * @return Array of SSL cipher suites to be enabled, or null if none of the
     * requested ciphers are supported
     */
    protected String[] getEnabledCiphers(String requestedCiphers,
                                         String[] supportedCiphers) {

        String[] enabledCiphers = null;

        if (requestedCiphers != null) {
            Vector vec = null;
            String cipher = requestedCiphers;
            int index = requestedCiphers.indexOf(',');
            if (index != -1) {
                int fromIndex = 0;
                while (index != -1) {
                    cipher = requestedCiphers.substring(fromIndex, index).trim();
                    if (cipher.length() > 0) {
                        /*
                         * Check to see if the requested cipher is among the
                         * supported ciphers, i.e., may be enabled
                         */
                        for (int i=0; supportedCiphers != null
                                     && i<supportedCiphers.length; i++) {
                            if (supportedCiphers[i].equals(cipher)) {
                                if (vec == null) {
                                    vec = new Vector();
                                }
                                vec.addElement(cipher);
                                break;
                            }
                        }
                    }
                    fromIndex = index+1;
                    index = requestedCiphers.indexOf(',', fromIndex);
                } // while
                cipher = requestedCiphers.substring(fromIndex);
            }

            if (cipher != null) {
                cipher = cipher.trim();
                if (cipher.length() > 0) {
                    /*
                     * Check to see if the requested cipher is among the
                     * supported ciphers, i.e., may be enabled
                     */
                    for (int i=0; supportedCiphers != null
                                 && i<supportedCiphers.length; i++) {
                        if (supportedCiphers[i].equals(cipher)) {
                            if (vec == null) {
                                vec = new Vector();
                            }
                            vec.addElement(cipher);
                            break;
                        }
                    }
                }
            }           

            if (vec != null) {
                enabledCiphers = new String[vec.size()];
                vec.copyInto(enabledCiphers);
            }
        } else {
            enabledCiphers = sslProxy.getDefaultCipherSuites();
        }

        return enabledCiphers;
    }
     
    /*
     * Gets the SSL server's keystore password.
     */
    protected String getKeystorePassword() {
        String keyPass = (String)attributes.get("keypass");
        if (keyPass == null) {
            keyPass = defaultKeyPass;
        }
        String keystorePass = (String)attributes.get("keystorePass");
        if (keystorePass == null) {
            keystorePass = keyPass;
        }
        return keystorePass;
    }

    /*
     * Gets the SSL server's keystore.
     */
    protected KeyStore getKeystore(String type, String pass)
            throws IOException {

        String keystoreFile = (String)attributes.get("keystore");
        if (keystoreFile == null)
            keystoreFile = defaultKeystoreFile;

        return getStore(type, keystoreFile, pass);
    }

    /*
     * Gets the SSL server's truststore.
     */
    protected KeyStore getTrustStore(String keystoreType) throws IOException {
        KeyStore trustStore = null;

        String trustStoreFile = (String)attributes.get("truststoreFile");
        if(trustStoreFile == null) {
            trustStoreFile = System.getProperty("javax.net.ssl.trustStore");
        }
        if(log.isDebugEnabled()) {
            log.debug("Truststore = " + trustStoreFile);
        }
        String trustStorePassword = (String)attributes.get("truststorePass");
        if( trustStorePassword == null) {
            trustStorePassword = System.getProperty("javax.net.ssl.trustStorePassword");
        }
        if( trustStorePassword == null ) {
            trustStorePassword = getKeystorePassword();
        }
        if(log.isDebugEnabled()) {
            log.debug("TrustPass = " + trustStorePassword);
        }
        String truststoreType = (String)attributes.get("truststoreType");
        if(truststoreType == null) {
            truststoreType = keystoreType;
        }
        if(log.isDebugEnabled()) {
            log.debug("trustType = " + truststoreType);
        }
        if (trustStoreFile != null && trustStorePassword != null){
            trustStore = getStore(truststoreType, trustStoreFile,
                                  trustStorePassword);
        }

        return trustStore;
    }

    /*
     * Gets the key- or truststore with the specified type, path, and password.
     */
    private KeyStore getStore(String type, String path, String pass)
            throws IOException {

        KeyStore ks = null;
        InputStream istream = null;
        try {
            ks = KeyStore.getInstance(type);
            if(! "PKCS11".equalsIgnoreCase(type) ) {
                File keyStoreFile = new File(path);
                if (!keyStoreFile.isAbsolute()) {
                    keyStoreFile = new File(System.getProperty("catalina.base"),
                                            path);
                }
                istream = new FileInputStream(keyStoreFile);
            }

            ks.load(istream, pass.toCharArray());
        } catch (FileNotFoundException fnfe) {
            throw fnfe;
        } catch (IOException ioe) {
            throw ioe;      
        } catch(Exception ex) {
            log.error("Exception trying to load keystore " +path,ex);
            throw new IOException("Exception trying to load keystore " +
                                  path + ": " + ex.getMessage() );
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

    /**
     * Reads the keystore and initializes the SSL socket factory.
     */
    void init() throws IOException {
        try {

            String clientAuthStr = (String) attributes.get("clientauth");
            if("true".equalsIgnoreCase(clientAuthStr) ||
               "yes".equalsIgnoreCase(clientAuthStr)) {
                requireClientAuth = true;
            } else if("want".equalsIgnoreCase(clientAuthStr)) {
                wantClientAuth = true;
            }

            // SSL protocol variant (e.g., TLS, SSL v3, etc.)
            String protocol = (String) attributes.get("protocol");
            if (protocol == null) {
                protocol = defaultProtocol;
            }

            // Certificate encoding algorithm (e.g., SunX509)
            String algorithm = (String) attributes.get("algorithm");
            if (algorithm == null) {
                algorithm = KeyManagerFactory.getDefaultAlgorithm();;
            }

            String keystoreType = (String) attributes.get("keystoreType");
            if (keystoreType == null) {
                keystoreType = defaultKeystoreType;
            }

        String trustAlgorithm = (String)attributes.get("truststoreAlgorithm");
        if( trustAlgorithm == null ) {
            trustAlgorithm = TrustManagerFactory.getDefaultAlgorithm();
        }
            // Create and init SSLContext
            SSLContext context = SSLContext.getInstance(protocol); 
            context.init(getKeyManagers(keystoreType, algorithm,
                                        (String) attributes.get("keyAlias")),
                         getTrustManagers(keystoreType, trustAlgorithm),
                         new SecureRandom());

            // create proxy
            sslProxy = context.getServerSocketFactory();

            // Determine which cipher suites to enable
            String requestedCiphers = (String)attributes.get("ciphers");
            enabledCiphers = getEnabledCiphers(requestedCiphers,
                                               sslProxy.getSupportedCipherSuites());

        } catch(Exception e) {
            if( e instanceof IOException )
                throw (IOException)e;
            throw new IOException(e.getMessage());
        }
    }

    /**
     * Gets the initialized key managers.
     */
    protected KeyManager[] getKeyManagers(String keystoreType,
                                          String algorithm,
                                          String keyAlias)
                throws Exception {

        KeyManager[] kms = null;

        String keystorePass = getKeystorePassword();

        KeyStore ks = getKeystore(keystoreType, keystorePass);
        if (keyAlias != null && !ks.isKeyEntry(keyAlias)) {
            throw new IOException(sm.getString("jsse.alias_no_key_entry", keyAlias));
        }

        KeyManagerFactory kmf = KeyManagerFactory.getInstance(algorithm);
        kmf.init(ks, keystorePass.toCharArray());

        kms = kmf.getKeyManagers();
        if (keyAlias != null) {
            if (JSSESocketFactory.defaultKeystoreType.equals(keystoreType)) {
                keyAlias = keyAlias.toLowerCase();
            }
            for(int i=0; i<kms.length; i++) {
                kms[i] = new JSSEKeyManager((X509KeyManager)kms[i], keyAlias);
            }
        }

        return kms;
    }

    /**
     * Gets the intialized trust managers.
     */
    protected TrustManager[] getTrustManagers(String keystoreType, String algorithm)
        throws Exception {
        String crlf = (String) attributes.get("crlFile");
        
        TrustManager[] tms = null;
        
        String truststoreType = (String) attributes.get("truststoreType");
        if (truststoreType == null) {
            truststoreType = keystoreType;
        }
        KeyStore trustStore = getTrustStore(truststoreType);
        if (trustStore != null) {
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
    
    /**
     * Return the initialization parameters for the TrustManager.
     * Currently, only the default <code>PKIX</code> is supported.
     * 
     * @param algorithm The algorithm to get parameters for.
     * @param crlf The path to the CRL file.
     * @param trustStore The configured TrustStore.
     * @return The parameters including the CRLs and TrustStore.
     */
    protected CertPathParameters getParameters(String algorithm, 
                                                String crlf, 
                                                KeyStore trustStore)
        throws Exception {
        CertPathParameters params = null;
        if("PKIX".equalsIgnoreCase(algorithm)) {
            PKIXBuilderParameters xparams = new PKIXBuilderParameters(trustStore, 
                                                                     new X509CertSelector());
            Collection crls = getCRLs(crlf);
            CertStoreParameters csp = new CollectionCertStoreParameters(crls);
            CertStore store = CertStore.getInstance("Collection", csp);
            xparams.addCertStore(store);
            xparams.setRevocationEnabled(true);
            String trustLength = (String)attributes.get("trustMaxCertLength");
            if(trustLength != null) {
                try {
                    xparams.setMaxPathLength(Integer.parseInt(trustLength));
                } catch(Exception ex) {
                    log.warn("Bad maxCertLength: "+trustLength);
                }
            }

            params = xparams;
        } else {
            throw new CRLException("CRLs not supported for type: "+algorithm);
        }
        return params;
    }


    /**
     * Load the collection of CRLs.
     * 
     */
    protected Collection<? extends CRL> getCRLs(String crlf) 
        throws IOException, CRLException, CertificateException {

        File crlFile = new File(crlf);
        if( !crlFile.isAbsolute() ) {
            crlFile = new File(System.getProperty("catalina.base"), crlf);
        }
        Collection<? extends CRL> crls = null;
        InputStream is = null;
        try {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            is = new FileInputStream(crlFile);
            crls = cf.generateCRLs(is);
        } catch(IOException iex) {
            throw iex;
        } catch(CRLException crle) {
            throw crle;
        } catch(CertificateException ce) {
            throw ce;
        } finally { 
            if(is != null) {
                try{
                    is.close();
                } catch(Exception ex) {
                }
            }
        }
        return crls;
    }

    /**
     * Set the SSL protocol variants to be enabled.
     * @param socket the SSLServerSocket.
     * @param protocols the protocols to use.
     */
    protected void setEnabledProtocols(SSLServerSocket socket, String []protocols){
        if (protocols != null) {
            socket.setEnabledProtocols(protocols);
        }
    }

    /**
     * Determines the SSL protocol variants to be enabled.
     *
     * @param socket The socket to get supported list from.
     * @param requestedProtocols Comma-separated list of requested SSL
     * protocol variants
     *
     * @return Array of SSL protocol variants to be enabled, or null if none of
     * the requested protocol variants are supported
     */
    protected String[] getEnabledProtocols(SSLServerSocket socket,
                                           String requestedProtocols){
        String[] supportedProtocols = socket.getSupportedProtocols();

        String[] enabledProtocols = null;

        if (requestedProtocols != null) {
            Vector vec = null;
            String protocol = requestedProtocols;
            int index = requestedProtocols.indexOf(',');
            if (index != -1) {
                int fromIndex = 0;
                while (index != -1) {
                    protocol = requestedProtocols.substring(fromIndex, index).trim();
                    if (protocol.length() > 0) {
                        /*
                         * Check to see if the requested protocol is among the
                         * supported protocols, i.e., may be enabled
                         */
                        for (int i=0; supportedProtocols != null
                                     && i<supportedProtocols.length; i++) {
                            if (supportedProtocols[i].equals(protocol)) {
                                if (vec == null) {
                                    vec = new Vector();
                                }
                                vec.addElement(protocol);
                                break;
                            }
                        }
                    }
                    fromIndex = index+1;
                    index = requestedProtocols.indexOf(',', fromIndex);
                } // while
                protocol = requestedProtocols.substring(fromIndex);
            }

            if (protocol != null) {
                protocol = protocol.trim();
                if (protocol.length() > 0) {
                    /*
                     * Check to see if the requested protocol is among the
                     * supported protocols, i.e., may be enabled
                     */
                    for (int i=0; supportedProtocols != null
                                 && i<supportedProtocols.length; i++) {
                        if (supportedProtocols[i].equals(protocol)) {
                            if (vec == null) {
                                vec = new Vector();
                            }
                            vec.addElement(protocol);
                            break;
                        }
                    }
                }
            }           

            if (vec != null) {
                enabledProtocols = new String[vec.size()];
                vec.copyInto(enabledProtocols);
            }
        }

        return enabledProtocols;
    }

    /**
     * Configure Client authentication for this version of JSSE.  The
     * JSSE included in Java 1.4 supports the 'want' value.  Prior
     * versions of JSSE will treat 'want' as 'false'.
     * @param socket the SSLServerSocket
     */
    protected void configureClientAuth(SSLServerSocket socket){
        if (wantClientAuth){
            socket.setWantClientAuth(wantClientAuth);
        } else {
            socket.setNeedClientAuth(requireClientAuth);
        }
    }

    /**
     * Configure Client authentication for this version of JSSE.  The
     * JSSE included in Java 1.4 supports the 'want' value.  Prior
     * versions of JSSE will treat 'want' as 'false'.
     * @param socket the SSLSocket
     */
    protected void configureClientAuth(SSLSocket socket){
        // Per JavaDocs: SSLSockets returned from 
        // SSLServerSocket.accept() inherit this setting.
    }
    
    /**
     * Configures the given SSL server socket with the requested cipher suites,
     * protocol versions, and need for client authentication
     */
    private void initServerSocket(ServerSocket ssocket) {

        SSLServerSocket socket = (SSLServerSocket) ssocket;

        if (enabledCiphers != null) {
            socket.setEnabledCipherSuites(enabledCiphers);
        }

        String requestedProtocols = (String) attributes.get("protocols");
        setEnabledProtocols(socket, getEnabledProtocols(socket, 
                                                         requestedProtocols));

        // we don't know if client auth is needed -
        // after parsing the request we may re-handshake
        configureClientAuth(socket);
    }

}
