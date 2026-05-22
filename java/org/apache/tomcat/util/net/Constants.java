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

/**
 * Constants for network utilities.
 */
public class Constants {

    /**
     * Name of the system property containing the tomcat instance installation path
     */
    public static final String CATALINA_BASE_PROP = "catalina.base";

    /**
     * JSSE and OpenSSL protocol names
     */
    public static final String SSL_PROTO_ALL = "all";
    /**
     * TLS protocol identifier.
     */
    public static final String SSL_PROTO_TLS = "TLS";
    /**
     * TLS 1.3 protocol identifier.
     */
    public static final String SSL_PROTO_TLSv1_3 = "TLSv1.3";
    /**
     * TLS 1.2 protocol identifier.
     */
    public static final String SSL_PROTO_TLSv1_2 = "TLSv1.2";
    /**
     * TLS 1.1 protocol identifier.
     */
    public static final String SSL_PROTO_TLSv1_1 = "TLSv1.1";
    // Two different forms for TLS 1.0
    /**
     * TLS 1.0 protocol identifier (with dot notation).
     */
    public static final String SSL_PROTO_TLSv1_0 = "TLSv1.0";
    /**
     * TLS 1.0 protocol identifier (without dot notation).
     */
    public static final String SSL_PROTO_TLSv1 = "TLSv1";
    /**
     * SSL 3.0 protocol identifier.
     */
    public static final String SSL_PROTO_SSLv3 = "SSLv3";
    /**
     * SSL 2.0 protocol identifier.
     */
    public static final String SSL_PROTO_SSLv2 = "SSLv2";
    /**
     * SSL 2.0 Hello protocol identifier.
     */
    public static final String SSL_PROTO_SSLv2Hello = "SSLv2Hello";

    /**
     * Private constructor to prevent instantiation.
     */
    private Constants() {
        // Hide default constructor
    }
}
