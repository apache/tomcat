/*
 *  Copyright 1999-2004 The Apache Software Foundation
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
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
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.Security;
import java.security.Provider;

import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;

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
 * @author Bill Barker
 */
public class JSSE13SocketFactory extends JSSESocketFactory
{
    /**
     * Flag for client authentication
     */
    protected boolean clientAuth = false;

    public JSSE13SocketFactory () {
        super();
    }

    /**
     * Reads the keystore and initializes the SSL socket factory.
     *
     * NOTE: This method is identical in functionality to the method of the
     * same name in JSSE14SocketFactory, except that this method is used with
     * JSSE 1.0.x (which is an extension to the 1.3 JVM), whereas the other is
     * used with JSSE 1.1.x (which ships with the 1.4 JVM). Therefore, this
     * method uses classes in com.sun.net.ssl, which have since moved to
     * javax.net.ssl, and explicitly registers the required security providers,
     * which come standard in a 1.4 JVM.
     */
     void init() throws IOException {
        try {
            try {
                Class ssps = Class.forName("sun.security.provider.Sun");
                Security.addProvider ((Provider)ssps.newInstance());
            }catch(Exception cnfe) {
                //Ignore, since this is a non-Sun JVM
            }
            Security.addProvider (new com.sun.net.ssl.internal.ssl.Provider());

            String clientAuthStr = (String)attributes.get("clientauth");
            if("true".equalsIgnoreCase(clientAuthStr) || 
               "yes".equalsIgnoreCase(clientAuthStr)  ||
               "want".equalsIgnoreCase(clientAuthStr)) {
                clientAuth = true;
            }
            
            // SSL protocol variant (e.g., TLS, SSL v3, etc.)
            String protocol = (String)attributes.get("protocol");
            if (protocol == null) protocol = defaultProtocol;
            
            // Certificate encoding algorithm (e.g., SunX509)
            String algorithm = (String)attributes.get("algorithm");
            if (algorithm == null) algorithm = defaultAlgorithm;

            // Set up KeyManager, which will extract server key
            com.sun.net.ssl.KeyManagerFactory kmf = 
                com.sun.net.ssl.KeyManagerFactory.getInstance(algorithm);
            String keystoreType = (String)attributes.get("keystoreType");
            if (keystoreType == null) {
                keystoreType = defaultKeystoreType;
            }
            String keystorePass = getKeystorePassword();
            kmf.init(getKeystore(keystoreType, keystorePass),
                     keystorePass.toCharArray());

            // Set up TrustManager
            com.sun.net.ssl.TrustManager[] tm = null;
            String truststoreType = (String)attributes.get("truststoreType");
            if(truststoreType == null) {
                truststoreType = keystoreType;
            }
            KeyStore trustStore = getTrustStore(truststoreType);
            if (trustStore != null) {
                com.sun.net.ssl.TrustManagerFactory tmf =
                    com.sun.net.ssl.TrustManagerFactory.getInstance("SunX509");
                tmf.init(trustStore);
                tm = tmf.getTrustManagers();
            }

            // Create and init SSLContext
            com.sun.net.ssl.SSLContext context = 
                com.sun.net.ssl.SSLContext.getInstance(protocol); 
            context.init(kmf.getKeyManagers(), tm, new SecureRandom());

            // Create proxy
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
    protected String[] getEnabledProtocols(SSLServerSocket socket,
                                           String requestedProtocols){
        return null;
    }
    protected void setEnabledProtocols(SSLServerSocket socket, 
                                             String [] protocols){
    }

    protected void configureClientAuth(SSLServerSocket socket){
        socket.setNeedClientAuth(clientAuth);
    }

    protected void configureClientAuth(SSLSocket socket){
        // In JSSE 1.0.2 docs it does not explicitly
        // state whether SSLSockets returned from 
        // SSLServerSocket.accept() inherit this setting.
        socket.setNeedClientAuth(clientAuth);
    }

}
