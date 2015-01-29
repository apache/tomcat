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
package org.apache.coyote.http11;

import java.io.ByteArrayInputStream;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import org.apache.coyote.ActionCode;
import org.apache.coyote.http11.filters.BufferedInputFilter;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.jni.SSL;
import org.apache.tomcat.jni.SSLSocket;
import org.apache.tomcat.util.net.AbstractEndpoint;
import org.apache.tomcat.util.net.AprEndpoint;
import org.apache.tomcat.util.net.SSLSupport;


/**
 * Processes HTTP requests.
 *
 * @author Remy Maucherat
 */
public class Http11AprProcessor extends AbstractHttp11Processor<Long> {


    private static final Log log = LogFactory.getLog(Http11AprProcessor.class);
    @Override
    protected Log getLog() {
        return log;
    }


    // ----------------------------------------------------------- Constructors

    public Http11AprProcessor(int maxHttpHeaderSize, AbstractEndpoint<Long> endpoint,
            int maxTrailerSize, int maxExtensionSize, int maxSwallowSize) {

        super(maxHttpHeaderSize, endpoint, maxTrailerSize, maxExtensionSize, maxSwallowSize);
    }


    // ----------------------------------------------------- ActionHook Methods

    /**
     * Send an action to the connector.
     *
     * @param actionCode Type of the action
     * @param param Action parameter
     */
    @Override
    @SuppressWarnings("incomplete-switch") // Other cases are handled by action()
    public void actionInternal(ActionCode actionCode, Object param) {

        long socketRef = socketWrapper.getSocket().longValue();

        switch (actionCode) {
        case REQ_SSL_CERTIFICATE: {
            if (endpoint.isSSLEnabled() && (socketRef != 0)) {
                // Consume and buffer the request body, so that it does not
                // interfere with the client's handshake messages
                InputFilter[] inputFilters = getInputBuffer().getFilters();
                ((BufferedInputFilter) inputFilters[Constants.BUFFERED_FILTER]).setLimit(maxSavePostSize);
                getInputBuffer().addActiveFilter(inputFilters[Constants.BUFFERED_FILTER]);
                try {
                    // Configure connection to require a certificate
                    SSLSocket.setVerify(socketRef, SSL.SSL_CVERIFY_REQUIRE,
                            ((AprEndpoint)endpoint).getSSLVerifyDepth());
                    // Renegotiate certificates
                    if (SSLSocket.renegotiate(socketRef) == 0) {
                        // Don't look for certs unless we know renegotiation worked.
                        // Get client certificate and the certificate chain if present
                        // certLength == -1 indicates an error
                        int certLength = SSLSocket.getInfoI(socketRef,SSL.SSL_INFO_CLIENT_CERT_CHAIN);
                        byte[] clientCert = SSLSocket.getInfoB(socketRef, SSL.SSL_INFO_CLIENT_CERT);
                        X509Certificate[] certs = null;
                        if (clientCert != null && certLength > -1) {
                            certs = new X509Certificate[certLength + 1];
                            CertificateFactory cf = CertificateFactory.getInstance("X.509");
                            certs[0] = (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(clientCert));
                            for (int i = 0; i < certLength; i++) {
                                byte[] data = SSLSocket.getInfoB(socketRef, SSL.SSL_INFO_CLIENT_CERT_CHAIN + i);
                                certs[i+1] = (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(data));
                            }
                        }
                        if (certs != null) {
                            request.setAttribute(SSLSupport.CERTIFICATE_KEY, certs);
                        }
                    }
                } catch (Exception e) {
                    log.warn(sm.getString("http11processor.socket.ssl"), e);
                }
            }
            break;
        }
        }
    }
}
