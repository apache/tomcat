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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import org.apache.tomcat.jni.SSL;

/**
 * Implementation of SSLSupport for APR.
 * <p>
 * TODO: Add a mechanism (or figure out how to use what we already have) to
 *       invalidate the session.
 */
public class AprSSLSupport implements SSLSupport {

    private final AprEndpoint.AprSocketWrapper socketWrapper;
    private final String clientCertProvider;


    public AprSSLSupport(AprEndpoint.AprSocketWrapper socketWrapper, String clientCertProvider) {
        this.socketWrapper = socketWrapper;
        this.clientCertProvider = clientCertProvider;
    }


    @Override
    public String getCipherSuite() throws IOException {
        try {
            return socketWrapper.getSSLInfoS(SSL.SSL_INFO_CIPHER);
        } catch (Exception e) {
            throw new IOException(e);
        }
    }


    @Override
    public X509Certificate[] getPeerCertificateChain() throws IOException {
        try {
            // certLength == -1 indicates an error unless TLS session tickets
            // are in use in which case OpenSSL won't store the chain in the
            // ticket.
            int certLength = socketWrapper.getSSLInfoI(SSL.SSL_INFO_CLIENT_CERT_CHAIN);
            byte[] clientCert = socketWrapper.getSSLInfoB(SSL.SSL_INFO_CLIENT_CERT);
            X509Certificate[] certs = null;

            if (clientCert != null) {
                if (certLength < 0) {
                    certLength = 0;
                }
                certs = new X509Certificate[certLength + 1];
                CertificateFactory cf;
                if (clientCertProvider == null) {
                    cf = CertificateFactory.getInstance("X.509");
                } else {
                    cf = CertificateFactory.getInstance("X.509", clientCertProvider);
                }
                certs[0] = (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(clientCert));
                for (int i = 0; i < certLength; i++) {
                    byte[] data = socketWrapper.getSSLInfoB(SSL.SSL_INFO_CLIENT_CERT_CHAIN + i);
                    certs[i+1] = (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(data));
                }
            }
            return certs;
        } catch (Exception e) {
            throw new IOException(e);
        }
    }


    @Override
    public Integer getKeySize() throws IOException {
        try {
            return Integer.valueOf(socketWrapper.getSSLInfoI(SSL.SSL_INFO_CIPHER_USEKEYSIZE));
        } catch (Exception e) {
            throw new IOException(e);
        }
    }


    @Override
    public String getSessionId() throws IOException {
        try {
            return socketWrapper.getSSLInfoS(SSL.SSL_INFO_SESSION_ID);
        } catch (Exception e) {
            throw new IOException(e);
        }
    }


    @Override
    public String getProtocol() throws IOException {
        try {
            return socketWrapper.getSSLInfoS(SSL.SSL_INFO_PROTOCOL);
        } catch (Exception e) {
            throw new IOException(e);
        }
   }


    @Override
    public String getRequestedProtocols() throws IOException {
        return null;
    }


    @Override
    public String getRequestedCiphers() throws IOException {
        return null;
    }
}
