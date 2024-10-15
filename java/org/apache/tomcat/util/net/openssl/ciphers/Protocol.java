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
package org.apache.tomcat.util.net.openssl.ciphers;

import org.apache.tomcat.util.net.Constants;

public enum Protocol {

    SSLv3(Constants.SSL_PROTO_SSLv3),
    SSLv2(Constants.SSL_PROTO_SSLv2),
    TLSv1(Constants.SSL_PROTO_TLSv1),
    TLSv1_2(Constants.SSL_PROTO_TLSv1_2),
    TLSv1_3(Constants.SSL_PROTO_TLSv1_3);

    private final String openSSLName;

    Protocol(String openSSLName) {
        this.openSSLName = openSSLName;
    }

    /**
     * The name returned by OpenSSL in the protocol column when using
     * <code>openssl ciphers -v</code>. This is currently only used by the unit
     * tests hence it is package private.
     */
    String getOpenSSLName() {
        return openSSLName;
    }
}
