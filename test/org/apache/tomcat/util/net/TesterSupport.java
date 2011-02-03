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
package org.apache.tomcat.util.net;

import java.io.File;
import java.security.cert.X509Certificate;

import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.apache.catalina.startup.Tomcat;

public final class TesterSupport {
    protected static final TrustManager[] TRUST_ALL_CERTS = new TrustManager[] { 
        new X509TrustManager() { 
            @Override
            public X509Certificate[] getAcceptedIssuers() { 
                return null;
            }
            @Override
            public void checkClientTrusted(X509Certificate[] certs,
                    String authType) {
                // NOOP - Trust everything
            }
            @Override
            public void checkServerTrusted(X509Certificate[] certs,
                    String authType) {
                // NOOP - Trust everything
            }
        }
    };

    protected static void initSsl(Tomcat tomcat) {
        String protocol = tomcat.getConnector().getProtocolHandlerClassName();
        if (protocol.indexOf("Apr") == -1) {
            tomcat.getConnector().setProperty("sslProtocol", "tls");
            File keystoreFile = new File(
                    "test/org/apache/catalina/startup/test.keystore");
            tomcat.getConnector().setAttribute("keystoreFile",
                    keystoreFile.getAbsolutePath());
        } else {
            File keystoreFile = new File(
                    "test/org/apache/catalina/startup/test-cert.pem");
            tomcat.getConnector().setAttribute("SSLCertificateFile",
                    keystoreFile.getAbsolutePath());
            keystoreFile = new File(
                    "test/org/apache/catalina/startup/test-key.pem");
            tomcat.getConnector().setAttribute("SSLCertificateKeyFile",
                    keystoreFile.getAbsolutePath());
        }
        tomcat.getConnector().setSecure(true);            
        tomcat.getConnector().setProperty("SSLEnabled", "true");
    }
}
