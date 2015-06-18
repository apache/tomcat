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
import org.apache.tomcat.util.net.SSLUtil;

public class OpenSSLUtil implements SSLUtil {

    private final SSLHostConfig sslHostConfig;

    private String[] enabledProtocols = null;
    private String[] enabledCiphers = null;

    public OpenSSLUtil(SSLHostConfig sslHostConfig) {
        this.sslHostConfig = sslHostConfig;
    }

    @Override
    public SSLContext createSSLContext() throws Exception {
        return new OpenSSLContext(sslHostConfig);
    }

    @Override
    public KeyManager[] getKeyManagers() throws Exception {
        KeyManager[] managers = {
                new OpenSSLKeyManager(SSLHostConfig.adjustRelativePath(sslHostConfig.getCertificateFile()),
                        SSLHostConfig.adjustRelativePath(sslHostConfig.getCertificateKeyFile()))
                };
        return managers;
    }

    @Override
    public TrustManager[] getTrustManagers() throws Exception {
        return null;
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
