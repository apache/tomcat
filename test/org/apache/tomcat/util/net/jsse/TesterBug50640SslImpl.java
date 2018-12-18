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
package org.apache.tomcat.util.net.jsse;

import org.apache.tomcat.util.compat.JreCompat;
import org.apache.tomcat.util.net.Constants;
import org.apache.tomcat.util.net.SSLHostConfig;
import org.apache.tomcat.util.net.SSLHostConfigCertificate;
import org.apache.tomcat.util.net.SSLUtil;

public class TesterBug50640SslImpl extends JSSEImplementation {

    public static final String PROPERTY_NAME = "sslEnabledProtocols";
    public static final String PROPERTY_VALUE = "magic";


    @Override
    public SSLUtil getSSLUtil(SSLHostConfigCertificate certificate) {
        SSLHostConfig sslHostConfig = certificate.getSSLHostConfig();
        if (sslHostConfig.getProtocols().size() == 1 &&
                sslHostConfig.getProtocols().contains(PROPERTY_VALUE)) {
            if (JreCompat.isJre8Available()) {
                sslHostConfig.setProtocols(Constants.SSL_PROTO_TLSv1_2);
            } else {
                sslHostConfig.setProtocols(Constants.SSL_PROTO_TLSv1);
            }
            return super.getSSLUtil(certificate);
        } else {
            return null;
        }
    }
}
