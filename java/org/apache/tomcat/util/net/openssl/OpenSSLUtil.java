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

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.jni.SSL;
import org.apache.tomcat.util.net.SSLContext;
import org.apache.tomcat.util.net.SSLHostConfigCertificate;
import org.apache.tomcat.util.net.SSLUtilBase;

public class OpenSSLUtil extends SSLUtilBase {

    private static final Log log = LogFactory.getLog(OpenSSLUtil.class);

    public OpenSSLUtil(SSLHostConfigCertificate certificate) {
        super(certificate);
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
    protected boolean isTls13Available() {
        return SSL.version() >= 0x1010100f;
    }


    @Override
    protected boolean isTls13RenegAuthAvailable() {
        // OpenSSL does support authentication after the initial handshake
        return true;
    }


    @Override
    public SSLContext createSSLContextInternal(List<String> negotiableProtocols) throws Exception {
        return new OpenSSLContext(certificate, negotiableProtocols);
    }
}
