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

import java.net.Socket;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.X509ExtendedKeyManager;
import javax.net.ssl.X509KeyManager;

/**
 * X509KeyManager which allows selection of a specific keypair and certificate
 * chain (identified by their keystore alias name) to be used by the server to
 * authenticate itself to SSL clients.
 *
 * @author Jan Luehe
 */
public final class JSSEKeyManager extends X509ExtendedKeyManager {

    private X509KeyManager delegate;
    private String serverKeyAlias;


    /**
     * Constructor.
     *
     * @param mgr The X509KeyManager used as a delegate
     * @param serverKeyAlias The alias name of the server's keypair and
     * supporting certificate chain
     */
    public JSSEKeyManager(X509KeyManager mgr, String serverKeyAlias) {
        super();
        this.delegate = mgr;
        this.serverKeyAlias = serverKeyAlias;
    }


    /**
     * Returns the server key alias that was provided in the constructor or the
     * result from {@link X509KeyManager#chooseServerAlias(String, Principal[],
     * Socket)} for the delegate if no alias is specified.
     */
    @Override
    public String chooseServerAlias(String keyType, Principal[] issuers, Socket socket) {
        if (serverKeyAlias != null) {
            return serverKeyAlias;
        }

        return delegate.chooseServerAlias(keyType, issuers, socket);
    }


    /**
     * Returns the server key alias that was provided in the constructor or the
     * result from {@link X509ExtendedKeyManager#chooseEngineServerAlias(String,
     * Principal[], SSLEngine)} for the delegate if no alias is specified.
     */
    @Override
    public String chooseEngineServerAlias(String keyType, Principal[] issuers,
            SSLEngine engine) {
        if (serverKeyAlias!=null) {
            return serverKeyAlias;
        }

        return super.chooseEngineServerAlias(keyType, issuers, engine);
    }


    @Override
    public String chooseClientAlias(String[] keyType, Principal[] issuers,
                                    Socket socket) {
        return delegate.chooseClientAlias(keyType, issuers, socket);
    }


    @Override
    public X509Certificate[] getCertificateChain(String alias) {
        return delegate.getCertificateChain(alias);
    }


    @Override
    public String[] getClientAliases(String keyType, Principal[] issuers) {
        return delegate.getClientAliases(keyType, issuers);
    }


    @Override
    public String[] getServerAliases(String keyType, Principal[] issuers) {
        return delegate.getServerAliases(keyType, issuers);
    }


    @Override
    public PrivateKey getPrivateKey(String alias) {
        return delegate.getPrivateKey(alias);
    }


    @Override
    public String chooseEngineClientAlias(String[] keyType, Principal[] issuers,
            SSLEngine engine) {
        return delegate.chooseClientAlias(keyType, issuers, null);
    }
}
