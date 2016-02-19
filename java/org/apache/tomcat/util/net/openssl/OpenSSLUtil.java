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
import java.util.Set;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLSessionContext;
import javax.net.ssl.TrustManager;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.net.SSLContext;
import org.apache.tomcat.util.net.SSLHostConfig;
import org.apache.tomcat.util.net.SSLHostConfigCertificate;
import org.apache.tomcat.util.net.SSLUtilBase;
import org.apache.tomcat.util.net.jsse.JSSEUtil;

public class OpenSSLUtil extends SSLUtilBase {

    private static final Log log = LogFactory.getLog(OpenSSLUtil.class);

    private final JSSEUtil jsseUtil;

    public OpenSSLUtil(SSLHostConfigCertificate certificate) {
        super(certificate);

        if (certificate.getCertificateFile() == null) {
            // Using JSSE configuration for keystore and truststore
            jsseUtil = new JSSEUtil(certificate);
        } else {
            // Use OpenSSL configuration for certificates
            jsseUtil = null;
        }
    }


    @Override
    protected Log getLog() {
        return log;
    }


    @Override
    protected Set<String> getImplementedProtocols() {
        return OpenSSLEngine.IMPLEMENTED_PROTOCOLS_SET;
    }


    @Override
    protected Set<String> getImplementedCiphers() {
        return OpenSSLEngine.AVAILABLE_CIPHER_SUITES;
    }


    @Override
    public SSLContext createSSLContext(List<String> negotiableProtocols) throws Exception {
        return new OpenSSLContext(certificate, negotiableProtocols);
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
        if (jsseUtil != null) {
            jsseUtil.configureSessionContext(sslSessionContext);
        }
    }
}
