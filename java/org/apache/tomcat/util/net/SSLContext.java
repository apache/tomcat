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

import java.security.KeyManagementException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSessionContext;
import javax.net.ssl.TrustManager;

/**
 * This interface is needed to override the default SSLContext class
 * to allow SSL implementation pluggability without having to use JCE. With
 * regular JSSE it will do nothing but delegate to the SSLContext.
 */
public interface SSLContext {

    void init(KeyManager[] kms, TrustManager[] tms,
            SecureRandom sr) throws KeyManagementException;

    void destroy();

    SSLSessionContext getServerSessionContext();

    SSLEngine createSSLEngine();

    SSLServerSocketFactory getServerSocketFactory();

    SSLParameters getSupportedSSLParameters();

    X509Certificate[] getCertificateChain(String alias);

    X509Certificate[] getAcceptedIssuers();
}
