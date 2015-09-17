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

import java.io.File;

import javax.net.ssl.KeyManager;

import org.apache.tomcat.util.res.StringManager;

public class OpenSSLKeyManager implements KeyManager{

    private static final StringManager sm = StringManager.getManager(OpenSSLKeyManager.class);

    private File certificateChain;
    public File getCertificateChain() { return certificateChain; }
    public void setCertificateChain(File certificateChain) { this.certificateChain = certificateChain; }

    private File privateKey;
    public File getPrivateKey() { return privateKey; }
    public void setPrivateKey(File privateKey) { this.privateKey = privateKey; }

    OpenSSLKeyManager(String certChainFile, String keyFile) {
        if (certChainFile == null) {
            throw new IllegalArgumentException(sm.getString("keyManager.nullCertificateChain"));
        }
        if (keyFile == null) {
            throw new IllegalArgumentException(sm.getString("keyManager.nullPrivateKey"));
        }
        this.certificateChain = new File(certChainFile);
        this.privateKey = new File(keyFile);
    }

}
