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

import java.util.List;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLSessionContext;
import javax.net.ssl.TrustManager;

import org.apache.tomcat.util.net.SSLContext;
import org.apache.tomcat.util.net.SSLHostConfig;
import org.apache.tomcat.util.net.SSLHostConfigCertificate;
import org.apache.tomcat.util.net.SSLUtil;
import org.apache.tomcat.util.net.jsse.JSSESocketFactory;

public class OpenSSLUtil implements SSLUtil {

    private final SSLHostConfig sslHostConfig;
    private final SSLHostConfigCertificate certificate;
    private final JSSESocketFactory jsseUtil;

    private String[] enabledProtocols = null;
    private String[] enabledCiphers = null;

    public OpenSSLUtil(SSLHostConfig sslHostConfig, SSLHostConfigCertificate certificate) {
        this.sslHostConfig = sslHostConfig;
        this.certificate = certificate;
        if (certificate.getCertificateFile() == null) {
            // Using JSSE configuration for keystore and truststore
            jsseUtil = new JSSESocketFactory(sslHostConfig, certificate);
        } else {
            // Use OpenSSL configuration for certificates
            jsseUtil = null;
        }
    }

    @Override
    public SSLContext createSSLContext(List<String> negotiableProtocols) throws Exception {
        return new OpenSSLContext(sslHostConfig, certificate, negotiableProtocols);
    }

    @Override
    public KeyManager[] getKeyManagers() throws Exception {
        if (jsseUtil != null) {
            return jsseUtil.getKeyManagers();
        } else {
            // Return something although it is not actually used
            KeyManager[] managers = {
                    new OpenSSLKeyManager(SSLHostConfig.adjustRelativePath(certificate.getCertificateFile()),
                            SSLHostConfig.adjustRelativePath(certificate.getCertificateKeyFile()))
            };
            return managers;
        }
    }

    @Override
    public TrustManager[] getTrustManagers() throws Exception {
        if (jsseUtil != null) {
            return jsseUtil.getTrustManagers();
        } else {
            return null;
        }
    }

    @Override
    public void configureSessionContext(SSLSessionContext sslSessionContext) {
        // do nothing. configuration is done in the init phase
    }

    @Override
    public String[] getEnableableCiphers(SSLContext context) {
        if (enabledCiphers == null) {
            List<String> enabledCiphersList = ((OpenSSLContext) context).getCiphers();
            enabledCiphers = enabledCiphersList.toArray(new String[enabledCiphersList.size()]);
        }
        return enabledCiphers;
    }

    @Override
    public String[] getEnableableProtocols(SSLContext context) {
        if (enabledProtocols == null) {
            enabledProtocols = new OpenSSLProtocols(((OpenSSLContext) context).getEnabledProtocol()).getProtocols();
        }
        return enabledProtocols;
    }

}
