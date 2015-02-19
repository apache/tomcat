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
import org.apache.tomcat.jni.SSLSocket;

/**
 * Implementation of SSLSupport for APR.
 * <p>
 * TODO: Add a mechanism (or figure out how to use what we already have) to
 *       invalidate the session.
 */
public class AprSSLSupport implements SSLSupport {

    private final SocketWrapperBase<Long> socketWrapper;
    private final String clientCertProvider;


    public AprSSLSupport(SocketWrapperBase<Long> socketWrapper, String clientCertProvider) {
        this.socketWrapper = socketWrapper;
        this.clientCertProvider = clientCertProvider;
    }


    @Override
    public String getCipherSuite() throws IOException {
        long socketRef = socketWrapper.getSocket().longValue();
        if (socketRef == 0) {
            return null;
        }
        try {
            return SSLSocket.getInfoS(socketRef, SSL.SSL_INFO_CIPHER);
        } catch (Exception e) {
            throw new IOException(e);
        }
    }


    @Override
    public X509Certificate[] getPeerCertificateChain() throws IOException {
        long socketRef = socketWrapper.getSocket().longValue();
        if (socketRef == 0) {
            return null;
        }

        try {
            // certLength == -1 indicates an error
            int certLength = SSLSocket.getInfoI(socketRef, SSL.SSL_INFO_CLIENT_CERT_CHAIN);
            byte[] clientCert = SSLSocket.getInfoB(socketRef, SSL.SSL_INFO_CLIENT_CERT);
            X509Certificate[] certs = null;
            if (clientCert != null  && certLength > -1) {
                certs = new X509Certificate[certLength + 1];
                CertificateFactory cf;
                if (clientCertProvider == null) {
                    cf = CertificateFactory.getInstance("X.509");
                } else {
                    cf = CertificateFactory.getInstance("X.509", clientCertProvider);
                }
                certs[0] = (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(clientCert));
                for (int i = 0; i < certLength; i++) {
                    byte[] data = SSLSocket.getInfoB(socketRef, SSL.SSL_INFO_CLIENT_CERT_CHAIN + i);
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
        long socketRef = socketWrapper.getSocket().longValue();
        if (socketRef == 0) {
            return null;
        }

        try {
            return Integer.valueOf(SSLSocket.getInfoI(socketRef, SSL.SSL_INFO_CIPHER_USEKEYSIZE));
        } catch (Exception e) {
            throw new IOException(e);
        }
    }


    @Override
    public String getSessionId() throws IOException {
        long socketRef = socketWrapper.getSocket().longValue();
        if (socketRef == 0) {
            return null;
        }

        try {
            return SSLSocket.getInfoS(socketRef, SSL.SSL_INFO_SESSION_ID);
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    @Override
    public String getProtocol() throws IOException {
        long socketRef = socketWrapper.getSocket().longValue();
        if (socketRef == 0) {
            return null;
        }

        try {
            return SSLSocket.getInfoS(socketRef, SSL.SSL_INFO_PROTOCOL);
        } catch (Exception e) {
            throw new IOException(e);
        }
   }
}
