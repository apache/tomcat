/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tomcat.util.net.openssl;

import java.security.cert.X509Certificate;

import javax.net.ssl.X509KeyManager;
import javax.net.ssl.X509TrustManager;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.jni.CertificateVerifier;
import org.apache.tomcat.util.res.StringManager;

public class OpenSSLCertificateVerifier implements CertificateVerifier {

    private static final Log log = LogFactory.getLog(OpenSSLCertificateVerifier.class);
    private static final StringManager sm = StringManager.getManager(OpenSSLContext.class);

    private X509TrustManager x509TrustManager;

    public OpenSSLCertificateVerifier(X509TrustManager x509TrustManager) {
        this.x509TrustManager = x509TrustManager;
    }

    @Override
    public boolean verify(long ssl, byte[][] chain, String auth) {
        X509Certificate[] peerCerts = certificates(chain);
        try {
            x509TrustManager.checkClientTrusted(peerCerts, auth);
            return true;
        } catch (Exception e) {
            log.debug(sm.getString("openssl.certificateVerificationFailed"), e);
        }
        return false;
    }

    private static X509Certificate[] certificates(byte[][] chain) {
        X509Certificate[] peerCerts = new X509Certificate[chain.length];
        for (int i = 0; i < peerCerts.length; i++) {
            peerCerts[i] = new OpenSSLX509Certificate(chain[i]);
        }
        return peerCerts;
    }
}
