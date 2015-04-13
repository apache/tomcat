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

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSessionContext;
import javax.net.ssl.X509KeyManager;

import org.apache.tomcat.util.net.jsse.NioX509KeyManager;

public abstract class AbstractJsseEndpoint<S> extends AbstractEndpoint<S> {

    private SSLImplementation sslImplementation = null;
    public SSLImplementation getSslImplementation() {
        return sslImplementation;
    }

    private String[] enabledCiphers;
    @Override
    public String[] getCiphersUsed() {
        return enabledCiphers;
    }

    private String[] enabledProtocols;

    private SSLContext sslContext = null;
    public SSLContext getSSLContext() { return sslContext;}
    public void setSSLContext(SSLContext c) { sslContext = c;}


    protected void initialiseSsl() throws Exception {
        if (isSSLEnabled()) {
            sslImplementation = SSLImplementation.getInstance(getSslImplementationName());
            SSLUtil sslUtil = sslImplementation.getSSLUtil(this);

            sslContext = sslUtil.createSSLContext();
            sslContext.init(wrap(sslUtil.getKeyManagers()),
                    sslUtil.getTrustManagers(), null);

            SSLSessionContext sessionContext =
                sslContext.getServerSessionContext();
            if (sessionContext != null) {
                sslUtil.configureSessionContext(sessionContext);
            }
            // Determine which cipher suites and protocols to enable
            enabledCiphers = sslUtil.getEnableableCiphers(sslContext);
            enabledProtocols = sslUtil.getEnableableProtocols(sslContext);
        }
    }


    protected SSLEngine createSSLEngine(String sniHostName) {
        SSLEngine engine = sslContext.createSSLEngine();
        if ("false".equals(getClientAuth())) {
            engine.setNeedClientAuth(false);
            engine.setWantClientAuth(false);
        } else if ("true".equals(getClientAuth()) || "yes".equals(getClientAuth())){
            engine.setNeedClientAuth(true);
        } else if ("want".equals(getClientAuth())) {
            engine.setWantClientAuth(true);
        }
        engine.setUseClientMode(false);
        engine.setEnabledCipherSuites(enabledCiphers);
        engine.setEnabledProtocols(enabledProtocols);

        configureUseServerCipherSuitesOrder(engine);

        return engine;
    }



    @Override
    public void unbind() throws Exception {
        sslContext = null;
    }


    /**
     * Configures SSLEngine to honor cipher suites ordering based upon
     * endpoint configuration.
     */
    private void configureUseServerCipherSuitesOrder(SSLEngine engine) {
        String useServerCipherSuitesOrderStr = this
                .getUseServerCipherSuitesOrder().trim();

        SSLParameters sslParameters = engine.getSSLParameters();
        boolean useServerCipherSuitesOrder =
            ("true".equalsIgnoreCase(useServerCipherSuitesOrderStr)
                || "yes".equalsIgnoreCase(useServerCipherSuitesOrderStr));

        sslParameters.setUseCipherSuitesOrder(useServerCipherSuitesOrder);
        engine.setSSLParameters(sslParameters);
    }


    private KeyManager[] wrap(KeyManager[] managers) {
        if (managers==null) return null;
        KeyManager[] result = new KeyManager[managers.length];
        for (int i=0; i<result.length; i++) {
            if (managers[i] instanceof X509KeyManager && getKeyAlias()!=null) {
                result[i] = new NioX509KeyManager((X509KeyManager)managers[i],getKeyAlias());
            } else {
                result[i] = managers[i];
            }
        }
        return result;
    }
}
