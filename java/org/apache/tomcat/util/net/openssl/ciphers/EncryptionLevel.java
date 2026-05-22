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

/**
 * Enumerates the encryption strength levels used in OpenSSL cipher suite configuration.
 */
public enum EncryptionLevel {
    /** Strong ciphers with no export restrictions. */
    STRONG_NONE,
    /** Export-grade 40-bit encryption. */
    EXP40,
    /** Export-grade 56-bit encryption. */
    EXP56,
    /** Low strength encryption. */
    LOW,
    /** Medium strength encryption. */
    MEDIUM,
    /** High strength encryption. */
    HIGH,
    /** FIPS-approved encryption only. */
    FIPS
}
