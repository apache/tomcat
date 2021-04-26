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

public enum Authentication {
    RSA    /* RSA auth */,
    DSS    /* DSS auth */,
    aNULL  /* no auth (i.e. use ADH or AECDH) */,
    DH     /* Fixed DH auth (kDHd or kDHr) */,
    ECDH   /* Fixed ECDH auth (kECDHe or kECDHr) */,
    KRB5   /* KRB5 auth */,
    ECDSA  /* ECDSA auth*/,
    PSK    /* PSK auth */,
    GOST94 /* GOST R 34.10-94 signature auth */,
    GOST01 /* GOST R 34.10-2001 */,
    FZA    /* Fortezza */,
    SRP    /* Secure Remote Password */,
    ANY    /* TLS 1.3 */
}
