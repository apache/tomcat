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
import java.util.Map;

import javax.net.ssl.SSLSession;

import org.apache.tomcat.jni.AprStatus;
import org.apache.tomcat.util.net.SSLHostConfigCertificate;
import org.apache.tomcat.util.net.SSLImplementation;
import org.apache.tomcat.util.net.SSLSupport;
import org.apache.tomcat.util.net.SSLUtil;
import org.apache.tomcat.util.net.jsse.JSSESupport;
import org.apache.tomcat.util.res.StringManager;

/**
 * OpenSSL implementation of SSLImplementation.
 */
public class OpenSSLImplementation extends SSLImplementation {

    private static final StringManager sm = StringManager.getManager(OpenSSLImplementation.class);


    /**
     * Constructs an OpenSSLImplementation.
     */
    public OpenSSLImplementation() {
    }

    @Override
    public SSLSupport getSSLSupport(SSLSession session, Map<String,List<String>> additionalAttributes) {
        ensureAvailable();
        return new JSSESupport(session, additionalAttributes);
    }

    @Override
    public SSLUtil getSSLUtil(SSLHostConfigCertificate certificate) {
        ensureAvailable();
        return new OpenSSLUtil(certificate);
    }


    private void ensureAvailable() {
        // Avoid a core dump if an older than minimum version is installed
        if (!AprStatus.isAprAvailable()) {
            throw new IllegalStateException(sm.getString("opensslImplementation.notAvailable"));
        }
    }
}
