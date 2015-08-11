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
package org.apache.tomcat.util.net.jsse.openssl;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

public class TestCipher {

    @Before
    public void checkVersion() {
        Assume.assumeTrue(TesterOpenSSL.TESTS_ENABLED);
    }

    /*
     * Checks that every cipher suite returned by OpenSSL is mapped to at least
     * one cipher suite that is recognised by JSSE or is a cipher suite known
     * not to be supported by JSSE.
     */
    @Test
    public void testAllOpenSSLCiphersMapped() throws Exception {
        Set<String> openSSLCipherSuites = TesterOpenSSL.getOpenSSLCiphersAsSet("ALL:eNULL");

        StringBuilder errors = new StringBuilder();

        for (String openSSLCipherSuite : openSSLCipherSuites) {
            List<String> jsseCipherSuites =
                    OpenSSLCipherConfigurationParser.parseExpression(openSSLCipherSuite);

            for (JsseImpl jsseImpl : JSSE_IMPLS) {
                boolean found = false;
                for (String jsseCipherSuite : jsseCipherSuites) {
                    if (jsseImpl.getStandardNames().contains(jsseCipherSuite)) {
                        found = true;
                        if (jsseImpl.getOpenSslUnmapped().contains(openSSLCipherSuite)) {
                            errors.append("Mapping found in " + jsseImpl.getVendor() +
                                "'s JSSE implementation for " + openSSLCipherSuite +
                                " when none was expected\n");
                        }
                        break;
                    }
                }
                if (!found && !jsseImpl.getOpenSslUnmapped().contains(openSSLCipherSuite)) {
                    errors.append("No mapping found in " + jsseImpl.getVendor() +
                            "'s JSSE implementation for " + openSSLCipherSuite +
                            " when one was expected\n");
                }
            }
        }
        Assert.assertTrue(errors.toString(), errors.length() == 0);
    }


    /*
     * Checks that the unit tests are running with a version of OpenSSL that
     * includes all the expected ciphers and does not include any unexpected
     * ones.
     */
    @Test
    public void testOpenSSLCipherAvailability() throws Exception {
        // OpenSSL 0.9.8 does not include aNULL or eNULL in all.
        // OpenSSL does not include ECDH/ECDHE ciphers in all and there is no
        //         EC alias. Use aRSA.
        // OpenSSL 1.0.0 onwards does not include eNULL in all.
        Set<String> availableCipherSuites = TesterOpenSSL.getOpenSSLCiphersAsSet("ALL:eNULL:aNULL:aRSA");
        Set<String> expectedCipherSuites = new HashSet<>();
        for (Cipher cipher : Cipher.values()) {
            if (TesterOpenSSL.OPENSSL_UNIMPLEMENTED_CIPHERS.contains(cipher)) {
                continue;
            }
            expectedCipherSuites.add(cipher.getOpenSSLAlias() + "+" +
                    cipher.getProtocol().getOpenSSLName());
        }

        Set<String> unavailableCipherSuites = new HashSet<>();
        unavailableCipherSuites.addAll(expectedCipherSuites);
        unavailableCipherSuites.removeAll(availableCipherSuites);
        StringBuilder unavailableList = new StringBuilder();
        for (String cipher : unavailableCipherSuites) {
            unavailableList.append(cipher);
            unavailableList.append(' ');
        }
        Assert.assertEquals(unavailableList.toString(), 0,  unavailableCipherSuites.size());

        Set<String> unexpectedCipherSuites = new HashSet<>();
        unexpectedCipherSuites.addAll(availableCipherSuites);
        unexpectedCipherSuites.removeAll(expectedCipherSuites);
        StringBuilder unexpectedList = new StringBuilder();
        for (String cipher : unexpectedCipherSuites) {
            unexpectedList.append(cipher);
            unexpectedList.append(' ');
        }
        Assert.assertEquals(unexpectedList.toString(), 0,  unexpectedCipherSuites.size());
    }


    /**
     * Check that the elements of the Cipher enumeration are all using standard
     * names from the TLS registry or are known exceptions.
     */
    @Test
    public void testNames() {
        for (Cipher cipher : Cipher.values()) {
            String name = cipher.name();
            // These do not appear in TLS registry
            if (name.contains("FORTEZZA")) {
                continue;
            }
            if (name.contains("EXPORT1024") || name.equals("TLS_DHE_DSS_WITH_RC4_128_SHA")) {
                continue;
            }
            if (name.startsWith("SSL_CK") || name.startsWith("SSL2")) {
                continue;
            }
            Assert.assertTrue("Non-registered name used in Cipher enumeration: " + cipher,
                    REGISTERED_NAMES.contains(name));
        }
    }


    /**
     * These are all the Oracle standard Java names for cipher suites taken from
     * http://docs.oracle.com/javase/8/docs/technotes/guides/security/StandardNames.html#ciphersuites
     * on 15th July 2014.
     */
    private static final Set<String> CIPHER_SUITE_STANDARD_NAMES_ORACLE =
            Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "SSL_DH_anon_EXPORT_WITH_DES40_CBC_SHA",
            "SSL_DH_anon_EXPORT_WITH_RC4_40_MD5",
            "SSL_DH_anon_WITH_3DES_EDE_CBC_SHA",
            "TLS_DH_anon_WITH_AES_128_CBC_SHA",
            "TLS_DH_anon_WITH_AES_128_CBC_SHA256",
            "TLS_DH_anon_WITH_AES_128_GCM_SHA256",
            "TLS_DH_anon_WITH_AES_256_CBC_SHA",
            "TLS_DH_anon_WITH_AES_256_CBC_SHA256",
            "TLS_DH_anon_WITH_AES_256_GCM_SHA384",
            "TLS_DH_anon_WITH_CAMELLIA_128_CBC_SHA",
            "TLS_DH_anon_WITH_CAMELLIA_128_CBC_SHA256",
            "TLS_DH_anon_WITH_CAMELLIA_256_CBC_SHA",
            "TLS_DH_anon_WITH_CAMELLIA_256_CBC_SHA256",
            "SSL_DH_anon_WITH_DES_CBC_SHA",
            "SSL_DH_anon_WITH_RC4_128_MD5",
            "TLS_DH_anon_WITH_SEED_CBC_SHA",
            "SSL_DH_DSS_EXPORT_WITH_DES40_CBC_SHA",
            "SSL_DH_DSS_WITH_3DES_EDE_CBC_SHA",
            "TLS_DH_DSS_WITH_AES_128_CBC_SHA",
            "TLS_DH_DSS_WITH_AES_128_CBC_SHA256",
            "TLS_DH_DSS_WITH_AES_128_GCM_SHA256",
            "TLS_DH_DSS_WITH_AES_256_CBC_SHA",
            "TLS_DH_DSS_WITH_AES_256_CBC_SHA256",
            "TLS_DH_DSS_WITH_AES_256_GCM_SHA384",
            "TLS_DH_DSS_WITH_CAMELLIA_128_CBC_SHA",
            "TLS_DH_DSS_WITH_CAMELLIA_128_CBC_SHA256",
            "TLS_DH_DSS_WITH_CAMELLIA_256_CBC_SHA",
            "TLS_DH_DSS_WITH_CAMELLIA_256_CBC_SHA256",
            "SSL_DH_DSS_WITH_DES_CBC_SHA",
            "TLS_DH_DSS_WITH_SEED_CBC_SHA",
            "SSL_DH_RSA_EXPORT_WITH_DES40_CBC_SHA",
            "SSL_DH_RSA_WITH_3DES_EDE_CBC_SHA",
            "TLS_DH_RSA_WITH_AES_128_CBC_SHA",
            "TLS_DH_RSA_WITH_AES_128_CBC_SHA256",
            "TLS_DH_RSA_WITH_AES_128_GCM_SHA256",
            "TLS_DH_RSA_WITH_AES_256_CBC_SHA",
            "TLS_DH_RSA_WITH_AES_256_CBC_SHA256",
            "TLS_DH_RSA_WITH_AES_256_GCM_SHA384",
            "TLS_DH_RSA_WITH_CAMELLIA_128_CBC_SHA",
            "TLS_DH_RSA_WITH_CAMELLIA_128_CBC_SHA256",
            "TLS_DH_RSA_WITH_CAMELLIA_256_CBC_SHA",
            "TLS_DH_RSA_WITH_CAMELLIA_256_CBC_SHA256",
            "SSL_DH_RSA_WITH_DES_CBC_SHA",
            "TLS_DH_RSA_WITH_SEED_CBC_SHA",
            "SSL_DHE_DSS_EXPORT_WITH_DES40_CBC_SHA",
            "SSL_DHE_DSS_EXPORT1024_WITH_DES_CBC_SHA",
            "SSL_DHE_DSS_EXPORT1024_WITH_RC4_56_SHA",
            "SSL_DHE_DSS_WITH_3DES_EDE_CBC_SHA",
            "TLS_DHE_DSS_WITH_AES_128_CBC_SHA",
            "TLS_DHE_DSS_WITH_AES_128_CBC_SHA256",
            "TLS_DHE_DSS_WITH_AES_128_GCM_SHA256",
            "TLS_DHE_DSS_WITH_AES_256_CBC_SHA",
            "TLS_DHE_DSS_WITH_AES_256_CBC_SHA256",
            "TLS_DHE_DSS_WITH_AES_256_GCM_SHA384",
            "TLS_DHE_DSS_WITH_CAMELLIA_128_CBC_SHA",
            "TLS_DHE_DSS_WITH_CAMELLIA_128_CBC_SHA256",
            "TLS_DHE_DSS_WITH_CAMELLIA_256_CBC_SHA",
            "TLS_DHE_DSS_WITH_CAMELLIA_256_CBC_SHA256",
            "SSL_DHE_DSS_WITH_DES_CBC_SHA",
            "SSL_DHE_DSS_WITH_RC4_128_SHA",
            "TLS_DHE_DSS_WITH_SEED_CBC_SHA",
            "TLS_DHE_PSK_WITH_3DES_EDE_CBC_SHA",
            "TLS_DHE_PSK_WITH_AES_128_CBC_SHA",
            "TLS_DHE_PSK_WITH_AES_128_CBC_SHA256",
            "TLS_DHE_PSK_WITH_AES_128_GCM_SHA256",
            "TLS_DHE_PSK_WITH_AES_256_CBC_SHA",
            "TLS_DHE_PSK_WITH_AES_256_CBC_SHA384",
            "TLS_DHE_PSK_WITH_AES_256_GCM_SHA384",
            "TLS_DHE_PSK_WITH_NULL_SHA",
            "TLS_DHE_PSK_WITH_NULL_SHA256",
            "TLS_DHE_PSK_WITH_NULL_SHA384",
            "TLS_DHE_PSK_WITH_RC4_128_SHA",
            "SSL_DHE_RSA_EXPORT_WITH_DES40_CBC_SHA",
            "SSL_DHE_RSA_WITH_3DES_EDE_CBC_SHA",
            "TLS_DHE_RSA_WITH_AES_128_CBC_SHA",
            "TLS_DHE_RSA_WITH_AES_128_CBC_SHA256",
            "TLS_DHE_RSA_WITH_AES_128_GCM_SHA256",
            "TLS_DHE_RSA_WITH_AES_256_CBC_SHA",
            "TLS_DHE_RSA_WITH_AES_256_CBC_SHA256",
            "TLS_DHE_RSA_WITH_AES_256_GCM_SHA384",
            "TLS_DHE_RSA_WITH_CAMELLIA_128_CBC_SHA",
            "TLS_DHE_RSA_WITH_CAMELLIA_128_CBC_SHA256",
            "TLS_DHE_RSA_WITH_CAMELLIA_256_CBC_SHA",
            "TLS_DHE_RSA_WITH_CAMELLIA_256_CBC_SHA256",
            "SSL_DHE_RSA_WITH_DES_CBC_SHA",
            "TLS_DHE_RSA_WITH_SEED_CBC_SHA",
            "TLS_ECDH_anon_WITH_3DES_EDE_CBC_SHA",
            "TLS_ECDH_anon_WITH_AES_128_CBC_SHA",
            "TLS_ECDH_anon_WITH_AES_256_CBC_SHA",
            "TLS_ECDH_anon_WITH_NULL_SHA",
            "TLS_ECDH_anon_WITH_RC4_128_SHA",
            "TLS_ECDH_ECDSA_WITH_3DES_EDE_CBC_SHA",
            "TLS_ECDH_ECDSA_WITH_AES_128_CBC_SHA",
            "TLS_ECDH_ECDSA_WITH_AES_128_CBC_SHA256",
            "TLS_ECDH_ECDSA_WITH_AES_128_GCM_SHA256",
            "TLS_ECDH_ECDSA_WITH_AES_256_CBC_SHA",
            "TLS_ECDH_ECDSA_WITH_AES_256_CBC_SHA384",
            "TLS_ECDH_ECDSA_WITH_AES_256_GCM_SHA384",
            "TLS_ECDH_ECDSA_WITH_NULL_SHA",
            "TLS_ECDH_ECDSA_WITH_RC4_128_SHA",
            "TLS_ECDH_RSA_WITH_3DES_EDE_CBC_SHA",
            "TLS_ECDH_RSA_WITH_AES_128_CBC_SHA",
            "TLS_ECDH_RSA_WITH_AES_128_CBC_SHA256",
            "TLS_ECDH_RSA_WITH_AES_128_GCM_SHA256",
            "TLS_ECDH_RSA_WITH_AES_256_CBC_SHA",
            "TLS_ECDH_RSA_WITH_AES_256_CBC_SHA384",
            "TLS_ECDH_RSA_WITH_AES_256_GCM_SHA384",
            "TLS_ECDH_RSA_WITH_NULL_SHA",
            "TLS_ECDH_RSA_WITH_RC4_128_SHA",
            "TLS_ECDHE_ECDSA_WITH_3DES_EDE_CBC_SHA",
            "TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA",
            "TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA256",
            "TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256",
            "TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA",
            "TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA384",
            "TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384",
            "TLS_ECDHE_ECDSA_WITH_NULL_SHA",
            "TLS_ECDHE_ECDSA_WITH_RC4_128_SHA",
            "TLS_ECDHE_PSK_WITH_3DES_EDE_CBC_SHA",
            "TLS_ECDHE_PSK_WITH_AES_128_CBC_SHA",
            "TLS_ECDHE_PSK_WITH_AES_128_CBC_SHA256",
            "TLS_ECDHE_PSK_WITH_AES_256_CBC_SHA",
            "TLS_ECDHE_PSK_WITH_AES_256_CBC_SHA384",
            "TLS_ECDHE_PSK_WITH_NULL_SHA",
            "TLS_ECDHE_PSK_WITH_NULL_SHA256",
            "TLS_ECDHE_PSK_WITH_NULL_SHA384",
            "TLS_ECDHE_PSK_WITH_RC4_128_SHA",
            "TLS_ECDHE_RSA_WITH_3DES_EDE_CBC_SHA",
            "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA",
            "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256",
            "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256",
            "TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA",
            "TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA384",
            "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384",
            "TLS_ECDHE_RSA_WITH_NULL_SHA",
            "TLS_ECDHE_RSA_WITH_RC4_128_SHA",
            "TLS_EMPTY_RENEGOTIATION_INFO_SCSV",
            "SSL_FORTEZZA_DMS_WITH_FORTEZZA_CBC_SHA",
            "SSL_FORTEZZA_DMS_WITH_NULL_SHA",
            "TLS_KRB5_EXPORT_WITH_DES_CBC_40_MD5",
            "TLS_KRB5_EXPORT_WITH_DES_CBC_40_SHA",
            "TLS_KRB5_EXPORT_WITH_RC2_CBC_40_MD5",
            "TLS_KRB5_EXPORT_WITH_RC2_CBC_40_SHA",
            "TLS_KRB5_EXPORT_WITH_RC4_40_MD5",
            "TLS_KRB5_EXPORT_WITH_RC4_40_SHA",
            "TLS_KRB5_WITH_3DES_EDE_CBC_MD5",
            "TLS_KRB5_WITH_3DES_EDE_CBC_SHA",
            "TLS_KRB5_WITH_DES_CBC_MD5",
            "TLS_KRB5_WITH_DES_CBC_SHA",
            "TLS_KRB5_WITH_IDEA_CBC_MD5",
            "TLS_KRB5_WITH_IDEA_CBC_SHA",
            "TLS_KRB5_WITH_RC4_128_MD5",
            "TLS_KRB5_WITH_RC4_128_SHA",
            "TLS_PSK_WITH_3DES_EDE_CBC_SHA",
            "TLS_PSK_WITH_AES_128_CBC_SHA",
            "TLS_PSK_WITH_AES_128_CBC_SHA256",
            "TLS_PSK_WITH_AES_128_GCM_SHA256",
            "TLS_PSK_WITH_AES_256_CBC_SHA",
            "TLS_PSK_WITH_AES_256_CBC_SHA384",
            "TLS_PSK_WITH_AES_256_GCM_SHA384",
            "TLS_PSK_WITH_NULL_SHA",
            "TLS_PSK_WITH_NULL_SHA256",
            "TLS_PSK_WITH_NULL_SHA384",
            "TLS_PSK_WITH_RC4_128_SHA",
            "SSL_RSA_EXPORT_WITH_DES40_CBC_SHA",
            "SSL_RSA_EXPORT_WITH_RC2_CBC_40_MD5",
            "SSL_RSA_EXPORT_WITH_RC4_40_MD5",
            "SSL_RSA_EXPORT1024_WITH_DES_CBC_SHA",
            "SSL_RSA_EXPORT1024_WITH_RC4_56_SHA",
            "SSL_RSA_FIPS_WITH_3DES_EDE_CBC_SHA",
            "SSL_RSA_FIPS_WITH_DES_CBC_SHA",
            "TLS_RSA_PSK_WITH_3DES_EDE_CBC_SHA",
            "TLS_RSA_PSK_WITH_AES_128_CBC_SHA",
            "TLS_RSA_PSK_WITH_AES_128_CBC_SHA256",
            "TLS_RSA_PSK_WITH_AES_128_GCM_SHA256",
            "TLS_RSA_PSK_WITH_AES_256_CBC_SHA",
            "TLS_RSA_PSK_WITH_AES_256_CBC_SHA384",
            "TLS_RSA_PSK_WITH_AES_256_GCM_SHA384",
            "TLS_RSA_PSK_WITH_NULL_SHA",
            "TLS_RSA_PSK_WITH_NULL_SHA256",
            "TLS_RSA_PSK_WITH_NULL_SHA384",
            "TLS_RSA_PSK_WITH_RC4_128_SHA",
            "SSL_RSA_WITH_3DES_EDE_CBC_SHA",
            "TLS_RSA_WITH_AES_128_CBC_SHA",
            "TLS_RSA_WITH_AES_128_CBC_SHA256",
            "TLS_RSA_WITH_AES_128_GCM_SHA256",
            "TLS_RSA_WITH_AES_256_CBC_SHA",
            "TLS_RSA_WITH_AES_256_CBC_SHA256",
            "TLS_RSA_WITH_AES_256_GCM_SHA384",
            "TLS_RSA_WITH_CAMELLIA_128_CBC_SHA",
            "TLS_RSA_WITH_CAMELLIA_128_CBC_SHA256",
            "TLS_RSA_WITH_CAMELLIA_256_CBC_SHA",
            "TLS_RSA_WITH_CAMELLIA_256_CBC_SHA256",
            "SSL_RSA_WITH_DES_CBC_SHA",
            "SSL_RSA_WITH_IDEA_CBC_SHA",
            "SSL_RSA_WITH_NULL_MD5",
            "SSL_RSA_WITH_NULL_SHA",
            "TLS_RSA_WITH_NULL_SHA256",
            "SSL_RSA_WITH_RC4_128_MD5",
            "SSL_RSA_WITH_RC4_128_SHA",
            "TLS_RSA_WITH_SEED_CBC_SHA",
            "TLS_SRP_SHA_DSS_WITH_3DES_EDE_CBC_SHA",
            "TLS_SRP_SHA_DSS_WITH_AES_128_CBC_SHA",
            "TLS_SRP_SHA_DSS_WITH_AES_256_CBC_SHA",
            "TLS_SRP_SHA_RSA_WITH_3DES_EDE_CBC_SHA",
            "TLS_SRP_SHA_RSA_WITH_AES_128_CBC_SHA",
            "TLS_SRP_SHA_RSA_WITH_AES_256_CBC_SHA",
            "TLS_SRP_SHA_WITH_3DES_EDE_CBC_SHA",
            "TLS_SRP_SHA_WITH_AES_128_CBC_SHA",
            "TLS_SRP_SHA_WITH_AES_256_CBC_SHA")));


    /**
     * These are the cipher suites implemented by OpenSSL that are not
     * implemented by Oracle's JSSE implementation.
     */
    private static Set<String> OPENSSL_UNMAPPED_ORACLE =
            Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
                    "DES-CBC-MD5+SSLv2",
                    "DES-CBC3-MD5+SSLv2",
                    "DHE-PSK-CAMELLIA128-SHA256+SSLv3",
                    "DHE-PSK-CAMELLIA256-SHA384+SSLv3",
                    "ECDH-ECDSA-CAMELLIA128-SHA256+TLSv1.2",
                    "ECDH-ECDSA-CAMELLIA256-SHA384+TLSv1.2",
                    "ECDH-RSA-CAMELLIA128-SHA256+TLSv1.2",
                    "ECDH-RSA-CAMELLIA256-SHA384+TLSv1.2",
                    "ECDHE-ECDSA-CAMELLIA128-SHA256+TLSv1.2",
                    "ECDHE-ECDSA-CAMELLIA256-SHA384+TLSv1.2",
                    "ECDHE-PSK-CAMELLIA128-SHA256+SSLv3",
                    "ECDHE-PSK-CAMELLIA256-SHA384+SSLv3",
                    "ECDHE-RSA-CAMELLIA128-SHA256+TLSv1.2",
                    "ECDHE-RSA-CAMELLIA256-SHA384+TLSv1.2",
                    "EXP-RC2-CBC-MD5+SSLv2",
                    "EXP-RC4-MD5+SSLv2",
                    "IDEA-CBC-MD5+SSLv2",
                    "PSK-CAMELLIA128-SHA256+SSLv3",
                    "PSK-CAMELLIA256-SHA384+SSLv3",
                    "RC2-CBC-MD5+SSLv2",
                    "RC4-MD5+SSLv2",
                    "RSA-PSK-CAMELLIA128-SHA256+SSLv3",
                    "RSA-PSK-CAMELLIA256-SHA384+SSLv3")));


    /**
     * These are all the IBM standard Java names for cipher suites taken from
     * http://www-01.ibm.com/support/knowledgecenter/SSYKE2_7.0.0/com.ibm.java.security.component.71.doc/security-component/jsse2Docs/ciphersuites.html?lang=en
     * on 29th July 2014.
     * <br>
     * As of 16 February 2015 the list for IBM Java 7 was identical to that for
     * IBM Java 8
     * http://www-01.ibm.com/support/knowledgecenter/SSYKE2_8.0.0/com.ibm.java.security.component.80.doc/security-component/jsse2Docs/ciphersuites.html?lang=en
     * <br>
     * Note that IBM cipher suites names can begin with TLS or SSL.
     */
    private static final Set<String> CIPHER_SUITE_STANDARD_NAMES_IBM;

    static {
        Set<String> sslNames = new HashSet<>(Arrays.asList(
            "SSL_ECDHE_ECDSA_WITH_AES_256_CBC_SHA384",
            "SSL_ECDHE_RSA_WITH_AES_256_CBC_SHA384",
            "SSL_RSA_WITH_AES_256_CBC_SHA256",
            "SSL_ECDH_ECDSA_WITH_AES_256_CBC_SHA384",
            "SSL_ECDH_RSA_WITH_AES_256_CBC_SHA384",
            "SSL_DHE_RSA_WITH_AES_256_CBC_SHA256",
            "SSL_DHE_DSS_WITH_AES_256_CBC_SHA256",
            "SSL_ECDHE_ECDSA_WITH_AES_256_CBC_SHA",
            "SSL_ECDHE_RSA_WITH_AES_256_CBC_SHA",
            "SSL_RSA_WITH_AES_256_CBC_SHA",
            "SSL_ECDH_ECDSA_WITH_AES_256_CBC_SHA",
            "SSL_ECDH_RSA_WITH_AES_256_CBC_SHA",
            "SSL_DHE_RSA_WITH_AES_256_CBC_SHA",
            "SSL_DHE_DSS_WITH_AES_256_CBC_SHA",
            "SSL_ECDHE_ECDSA_WITH_AES_128_CBC_SHA256",
            "SSL_ECDHE_RSA_WITH_AES_128_CBC_SHA256",
            "SSL_RSA_WITH_AES_128_CBC_SHA256",
            "SSL_ECDH_ECDSA_WITH_AES_128_CBC_SHA256",
            "SSL_ECDH_RSA_WITH_AES_128_CBC_SHA256",
            "SSL_DHE_RSA_WITH_AES_128_CBC_SHA256",
            "SSL_DHE_DSS_WITH_AES_128_CBC_SHA256",
            "SSL_ECDHE_ECDSA_WITH_AES_128_CBC_SHA",
            "SSL_ECDHE_RSA_WITH_AES_128_CBC_SHA",
            "SSL_RSA_WITH_AES_128_CBC_SHA",
            "SSL_ECDH_ECDSA_WITH_AES_128_CBC_SHA",
            "SSL_ECDH_RSA_WITH_AES_128_CBC_SHA",
            "SSL_DHE_RSA_WITH_AES_128_CBC_SHA",
            "SSL_DHE_DSS_WITH_AES_128_CBC_SHA",
            "SSL_ECDHE_ECDSA_WITH_RC4_128_SHA",
            "SSL_ECDHE_RSA_WITH_RC4_128_SHA",
            "SSL_RSA_WITH_RC4_128_SHA",
            "SSL_ECDH_ECDSA_WITH_RC4_128_SHA",
            "SSL_ECDH_RSA_WITH_RC4_128_SHA",
            "SSL_ECDHE_ECDSA_WITH_3DES_EDE_CBC_SHA",
            "SSL_ECDHE_RSA_WITH_3DES_EDE_CBC_SHA",
            "SSL_RSA_WITH_3DES_EDE_CBC_SHA",
            "SSL_ECDH_ECDSA_WITH_3DES_EDE_CBC_SHA",
            "SSL_ECDH_RSA_WITH_3DES_EDE_CBC_SHA",
            "SSL_DHE_RSA_WITH_3DES_EDE_CBC_SHA",
            "SSL_DHE_DSS_WITH_3DES_EDE_CBC_SHA",
            "SSL_RSA_WITH_RC4_128_MD5",
            "TLS_EMPTY_RENEGOTIATION_INFO_SCSV",
            "SSL_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384",
            "SSL_ECDHE_RSA_WITH_AES_256_GCM_SHA384",
            "SSL_RSA_WITH_AES_256_GCM_SHA384",
            "SSL_ECDH_ECDSA_WITH_AES_256_GCM_SHA384",
            "SSL_ECDH_RSA_WITH_AES_256_GCM_SHA384",
            "SSL_DHE_DSS_WITH_AES_256_GCM_SHA384",
            "SSL_DHE_RSA_WITH_AES_256_GCM_SHA384",
            "SSL_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256",
            "SSL_ECDHE_RSA_WITH_AES_128_GCM_SHA256",
            "SSL_RSA_WITH_AES_128_GCM_SHA256",
            "SSL_ECDH_ECDSA_WITH_AES_128_GCM_SHA256",
            "SSL_ECDH_RSA_WITH_AES_128_GCM_SHA256",
            "SSL_DHE_RSA_WITH_AES_128_GCM_SHA256",
            "SSL_DHE_DSS_WITH_AES_128_GCM_SHA256",
            "SSL_DH_anon_WITH_AES_256_CBC_SHA256",
            "SSL_ECDH_anon_WITH_AES_256_CBC_SHA",
            "SSL_DH_anon_WITH_AES_256_CBC_SHA",
            "SSL_DH_anon_WITH_AES_256_GCM_SHA384",
            "SSL_DH_anon_WITH_AES_128_GCM_SHA256",
            "SSL_DH_anon_WITH_AES_128_CBC_SHA256",
            "SSL_ECDH_anon_WITH_AES_128_CBC_SHA",
            "SSL_DH_anon_WITH_AES_128_CBC_SHA",
            "SSL_ECDH_anon_WITH_RC4_128_SHA",
            "SSL_DH_anon_WITH_RC4_128_MD5",
            "SSL_ECDH_anon_WITH_3DES_EDE_CBC_SHA",
            "SSL_DH_anon_WITH_3DES_EDE_CBC_SHA",
            "SSL_RSA_WITH_NULL_SHA256",
            "SSL_ECDHE_ECDSA_WITH_NULL_SHA",
            "SSL_ECDHE_RSA_WITH_NULL_SHA",
            "SSL_RSA_WITH_NULL_SHA",
            "SSL_ECDH_ECDSA_WITH_NULL_SHA",
            "SSL_ECDH_RSA_WITH_NULL_SHA",
            "SSL_ECDH_anon_WITH_NULL_SHA",
            "SSL_RSA_WITH_NULL_MD5",
            "SSL_RSA_WITH_DES_CBC_SHA",
            "SSL_DHE_RSA_WITH_DES_CBC_SHA",
            "SSL_DHE_DSS_WITH_DES_CBC_SHA",
            "SSL_DH_anon_WITH_DES_CBC_SHA",
            "SSL_RSA_FIPS_WITH_3DES_EDE_CBC_SHA",
            "SSL_RSA_FIPS_WITH_DES_EDE_CBC_SHA",
            "SSL_DHE_DSS_WITH_RC4_128_SHA",
            "SSL_RSA_EXPORT_WITH_RC4_40_MD5",
            "SSL_DH_anon_EXPORT_WITH_RC4_40_MD5",
            "SSL_RSA_EXPORT_WITH_DES40_CBC_SHA",
            "SSL_DHE_RSA_EXPORT_WITH_DES40_CBC_SHA",
            "SSL_DHE_DSS_EXPORT_WITH_DES40_CBC_SHA",
            "SSL_DH_anon_EXPORT_WITH_DES40_CBC_SHA",
            "SSL_KRB5_WITH_RC4_128_SHA",
            "SSL_KRB5_WITH_RC4_128_MD5",
            "SSL_KRB5_WITH_3DES_EDE_CBC_SHA",
            "SSL_KRB5_WITH_3DES_EDE_CBC_MD5",
            "SSL_KRB5_WITH_DES_CBC_SHA",
            "SSL_KRB5_WITH_DES_CBC_MD5",
            "SSL_KRB5_EXPORT_WITH_RC4_40_SHA",
            "SSL_KRB5_EXPORT_WITH_RC4_40_MD5",
            "SSL_KRB5_EXPORT_WITH_DES_CBC_40_SHA",
            "SSL_KRB5_EXPORT_WITH_DES_CBC_40_MD5",
            "SSL_RSA_EXPORT_WITH_RC2_CBC_40_MD5"));

        Set<String> allNames = new HashSet<>();

        allNames.addAll(sslNames);

        for (String sslName : sslNames) {
            allNames.add("TLS" + sslName.substring(3));
        }

        CIPHER_SUITE_STANDARD_NAMES_IBM = Collections.unmodifiableSet(allNames);
    }


    /**
     * These are the cipher suites implemented by OpenSSL that are not
     * implemented by IBM's JSSE implementation.
     */
    private static Set<String> OPENSSL_UNMAPPED_IBM =
            Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
                    "ADH-CAMELLIA128-SHA+SSLv3",
                    "ADH-CAMELLIA256-SHA+SSLv3",
                    "ADH-CAMELLIA128-SHA256+TLSv1.2",
                    "ADH-CAMELLIA256-SHA256+TLSv1.2",
                    "ADH-SEED-SHA+SSLv3",
                    "CAMELLIA128-SHA+SSLv3",
                    "CAMELLIA256-SHA+SSLv3",
                    "CAMELLIA128-SHA256+TLSv1.2",
                    "CAMELLIA256-SHA256+TLSv1.2",
                    "DES-CBC-MD5+SSLv2",
                    "DES-CBC3-MD5+SSLv2",
                    "DH-DSS-AES128-GCM-SHA256+TLSv1.2",
                    "DH-DSS-AES256-GCM-SHA384+TLSv1.2",
                    "DH-DSS-AES128-SHA+SSLv3",
                    "DH-DSS-AES128-SHA256+TLSv1.2",
                    "DH-DSS-AES256-SHA+SSLv3",
                    "DH-DSS-AES256-SHA256+TLSv1.2",
                    "DH-DSS-CAMELLIA128-SHA+SSLv3",
                    "DH-DSS-CAMELLIA128-SHA256+TLSv1.2",
                    "DH-DSS-CAMELLIA256-SHA+SSLv3",
                    "DH-DSS-CAMELLIA256-SHA256+TLSv1.2",
                    "DH-DSS-DES-CBC-SHA+SSLv3",
                    "DH-DSS-DES-CBC3-SHA+SSLv3",
                    "DH-DSS-SEED-SHA+SSLv3",
                    "DH-RSA-AES128-GCM-SHA256+TLSv1.2",
                    "DH-RSA-AES256-GCM-SHA384+TLSv1.2",
                    "DH-RSA-AES128-SHA+SSLv3",
                    "DH-RSA-AES128-SHA256+TLSv1.2",
                    "DH-RSA-AES256-SHA+SSLv3",
                    "DH-RSA-AES256-SHA256+TLSv1.2",
                    "DH-RSA-CAMELLIA128-SHA+SSLv3",
                    "DH-RSA-CAMELLIA128-SHA256+TLSv1.2",
                    "DH-RSA-CAMELLIA256-SHA+SSLv3",
                    "DH-RSA-CAMELLIA256-SHA256+TLSv1.2",
                    "DH-RSA-DES-CBC-SHA+SSLv3",
                    "DH-RSA-DES-CBC3-SHA+SSLv3",
                    "DH-RSA-SEED-SHA+SSLv3",
                    "DHE-DSS-CAMELLIA128-SHA+SSLv3",
                    "DHE-DSS-CAMELLIA128-SHA256+TLSv1.2",
                    "DHE-DSS-CAMELLIA256-SHA+SSLv3",
                    "DHE-DSS-CAMELLIA256-SHA256+TLSv1.2",
                    "DHE-DSS-SEED-SHA+SSLv3",
                    "DHE-PSK-3DES-EDE-CBC-SHA+SSLv3",
                    "DHE-PSK-AES128-CBC-SHA+SSLv3",
                    "DHE-PSK-AES128-CBC-SHA256+SSLv3",
                    "DHE-PSK-AES128-GCM-SHA256+TLSv1.2",
                    "DHE-PSK-AES256-CBC-SHA+SSLv3",
                    "DHE-PSK-AES256-CBC-SHA384+SSLv3",
                    "DHE-PSK-AES256-GCM-SHA384+TLSv1.2",
                    "DHE-PSK-CAMELLIA128-SHA256+SSLv3",
                    "DHE-PSK-CAMELLIA256-SHA384+SSLv3",
                    "DHE-PSK-NULL-SHA+SSLv3",
                    "DHE-PSK-NULL-SHA256+SSLv3",
                    "DHE-PSK-NULL-SHA384+SSLv3",
                    "DHE-PSK-RC4-SHA+SSLv3",
                    "DHE-RSA-CAMELLIA128-SHA+SSLv3",
                    "DHE-RSA-CAMELLIA128-SHA256+TLSv1.2",
                    "DHE-RSA-CAMELLIA256-SHA+SSLv3",
                    "DHE-RSA-CAMELLIA256-SHA256+TLSv1.2",
                    "DHE-RSA-SEED-SHA+SSLv3",
                    "ECDH-ECDSA-CAMELLIA128-SHA256+TLSv1.2",
                    "ECDH-ECDSA-CAMELLIA256-SHA384+TLSv1.2",
                    "ECDH-RSA-CAMELLIA128-SHA256+TLSv1.2",
                    "ECDH-RSA-CAMELLIA256-SHA384+TLSv1.2",
                    "ECDHE-ECDSA-CAMELLIA128-SHA256+TLSv1.2",
                    "ECDHE-ECDSA-CAMELLIA256-SHA384+TLSv1.2",
                    "ECDHE-PSK-3DES-EDE-CBC-SHA+SSLv3",
                    "ECDHE-PSK-AES128-CBC-SHA+SSLv3",
                    "ECDHE-PSK-AES128-CBC-SHA256+SSLv3",
                    "ECDHE-PSK-AES256-CBC-SHA+SSLv3",
                    "ECDHE-PSK-AES256-CBC-SHA384+SSLv3",
                    "ECDHE-PSK-CAMELLIA128-SHA256+SSLv3",
                    "ECDHE-PSK-CAMELLIA256-SHA384+SSLv3",
                    "ECDHE-PSK-NULL-SHA+SSLv3",
                    "ECDHE-PSK-NULL-SHA256+SSLv3",
                    "ECDHE-PSK-NULL-SHA384+SSLv3",
                    "ECDHE-PSK-RC4-SHA+SSLv3",
                    "ECDHE-RSA-CAMELLIA128-SHA256+TLSv1.2",
                    "ECDHE-RSA-CAMELLIA256-SHA384+TLSv1.2",
                    "EXP-DH-DSS-DES-CBC-SHA+SSLv3",
                    "EXP-DH-RSA-DES-CBC-SHA+SSLv3",
                    "EXP-RC2-CBC-MD5+SSLv2",
                    "EXP-RC4-MD5+SSLv2",
                    "IDEA-CBC-MD5+SSLv2",
                    "IDEA-CBC-SHA+SSLv3",
                    "PSK-3DES-EDE-CBC-SHA+SSLv3",
                    "PSK-AES128-CBC-SHA+SSLv3",
                    "PSK-AES128-CBC-SHA256+SSLv3",
                    "PSK-AES128-GCM-SHA256+TLSv1.2",
                    "PSK-AES256-CBC-SHA+SSLv3",
                    "PSK-AES256-CBC-SHA384+SSLv3",
                    "PSK-AES256-GCM-SHA384+TLSv1.2",
                    "PSK-CAMELLIA128-SHA256+SSLv3",
                    "PSK-CAMELLIA256-SHA384+SSLv3",
                    "PSK-NULL-SHA+SSLv3",
                    "PSK-NULL-SHA256+SSLv3",
                    "PSK-NULL-SHA384+SSLv3",
                    "PSK-RC4-SHA+SSLv3",
                    "RC2-CBC-MD5+SSLv2",
                    "RC4-MD5+SSLv2",
                    "RSA-PSK-3DES-EDE-CBC-SHA+SSLv3",
                    "RSA-PSK-AES128-CBC-SHA+SSLv3",
                    "RSA-PSK-AES128-CBC-SHA256+SSLv3",
                    "RSA-PSK-AES128-GCM-SHA256+TLSv1.2",
                    "RSA-PSK-AES256-CBC-SHA+SSLv3",
                    "RSA-PSK-AES256-CBC-SHA384+SSLv3",
                    "RSA-PSK-AES256-GCM-SHA384+TLSv1.2",
                    "RSA-PSK-CAMELLIA128-SHA256+SSLv3",
                    "RSA-PSK-CAMELLIA256-SHA384+SSLv3",
                    "RSA-PSK-NULL-SHA+SSLv3",
                    "RSA-PSK-NULL-SHA256+SSLv3",
                    "RSA-PSK-NULL-SHA384+SSLv3",
                    "RSA-PSK-RC4-SHA+SSLv3",
                    "SEED-SHA+SSLv3",
                    "SRP-AES-128-CBC-SHA+SSLv3",
                    "SRP-AES-256-CBC-SHA+SSLv3",
                    "SRP-3DES-EDE-CBC-SHA+SSLv3",
                    "SRP-DSS-3DES-EDE-CBC-SHA+SSLv3",
                    "SRP-DSS-AES-128-CBC-SHA+SSLv3",
                    "SRP-DSS-AES-256-CBC-SHA+SSLv3",
                    "SRP-RSA-3DES-EDE-CBC-SHA+SSLv3",
                    "SRP-RSA-AES-128-CBC-SHA+SSLv3",
                    "SRP-RSA-AES-256-CBC-SHA+SSLv3")));


    private static JsseImpl ORACLE_JSSE_CIPHER_IMPL = new JsseImpl("Oracle",
            CIPHER_SUITE_STANDARD_NAMES_ORACLE, OPENSSL_UNMAPPED_ORACLE);


    private static JsseImpl IBM_JSSE_CIPHER_IMPL = new JsseImpl("IBM",
            CIPHER_SUITE_STANDARD_NAMES_IBM, OPENSSL_UNMAPPED_IBM);


    private static Set<JsseImpl> JSSE_IMPLS = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(ORACLE_JSSE_CIPHER_IMPL, IBM_JSSE_CIPHER_IMPL)));


    private static class JsseImpl {
        private final String vendor;
        private final Set<String> standardNames;
        private final Set<String> openSslUnmapped;

        public JsseImpl(String vendor,  Set<String> standardNames,
                Set<String> openSslUnmapped) {
            this.vendor = vendor;
            this.standardNames = standardNames;
            this.openSslUnmapped = openSslUnmapped;
        }

        public String getVendor() {
            return vendor;
        }

        public Set<String> getStandardNames() {
            return standardNames;
        }

        public Set<String> getOpenSslUnmapped() {
            return openSslUnmapped;
        }
    }


    // Retrieved on 30 July 2014 from
    // http://www.iana.org/assignments/tls-parameters/tls-parameters.xhtml#tls-parameters-4
    private static Set<String> REGISTERED_NAMES = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(
            "TLS_NULL_WITH_NULL_NULL",
            "TLS_RSA_WITH_NULL_MD5",
            "TLS_RSA_WITH_NULL_SHA",
            "TLS_RSA_EXPORT_WITH_RC4_40_MD5",
            "TLS_RSA_WITH_RC4_128_MD5",
            "TLS_RSA_WITH_RC4_128_SHA",
            "TLS_RSA_EXPORT_WITH_RC2_CBC_40_MD5",
            "TLS_RSA_WITH_IDEA_CBC_SHA",
            "TLS_RSA_EXPORT_WITH_DES40_CBC_SHA",
            "TLS_RSA_WITH_DES_CBC_SHA",
            "TLS_RSA_WITH_3DES_EDE_CBC_SHA",
            "TLS_DH_DSS_EXPORT_WITH_DES40_CBC_SHA",
            "TLS_DH_DSS_WITH_DES_CBC_SHA",
            "TLS_DH_DSS_WITH_3DES_EDE_CBC_SHA",
            "TLS_DH_RSA_EXPORT_WITH_DES40_CBC_SHA",
            "TLS_DH_RSA_WITH_DES_CBC_SHA",
            "TLS_DH_RSA_WITH_3DES_EDE_CBC_SHA",
            "TLS_DHE_DSS_EXPORT_WITH_DES40_CBC_SHA",
            "TLS_DHE_DSS_WITH_DES_CBC_SHA",
            "TLS_DHE_DSS_WITH_3DES_EDE_CBC_SHA",
            "TLS_DHE_RSA_EXPORT_WITH_DES40_CBC_SHA",
            "TLS_DHE_RSA_WITH_DES_CBC_SHA",
            "TLS_DHE_RSA_WITH_3DES_EDE_CBC_SHA",
            "TLS_DH_anon_EXPORT_WITH_RC4_40_MD5",
            "TLS_DH_anon_WITH_RC4_128_MD5",
            "TLS_DH_anon_EXPORT_WITH_DES40_CBC_SHA",
            "TLS_DH_anon_WITH_DES_CBC_SHA",
            "TLS_DH_anon_WITH_3DES_EDE_CBC_SHA",
            "TLS_KRB5_WITH_DES_CBC_SHA",
            "TLS_KRB5_WITH_3DES_EDE_CBC_SHA",
            "TLS_KRB5_WITH_RC4_128_SHA",
            "TLS_KRB5_WITH_IDEA_CBC_SHA",
            "TLS_KRB5_WITH_DES_CBC_MD5",
            "TLS_KRB5_WITH_3DES_EDE_CBC_MD5",
            "TLS_KRB5_WITH_RC4_128_MD5",
            "TLS_KRB5_WITH_IDEA_CBC_MD5",
            "TLS_KRB5_EXPORT_WITH_DES_CBC_40_SHA",
            "TLS_KRB5_EXPORT_WITH_RC2_CBC_40_SHA",
            "TLS_KRB5_EXPORT_WITH_RC4_40_SHA",
            "TLS_KRB5_EXPORT_WITH_DES_CBC_40_MD5",
            "TLS_KRB5_EXPORT_WITH_RC2_CBC_40_MD5",
            "TLS_KRB5_EXPORT_WITH_RC4_40_MD5",
            "TLS_PSK_WITH_NULL_SHA",
            "TLS_DHE_PSK_WITH_NULL_SHA",
            "TLS_RSA_PSK_WITH_NULL_SHA",
            "TLS_RSA_WITH_AES_128_CBC_SHA",
            "TLS_DH_DSS_WITH_AES_128_CBC_SHA",
            "TLS_DH_RSA_WITH_AES_128_CBC_SHA",
            "TLS_DHE_DSS_WITH_AES_128_CBC_SHA",
            "TLS_DHE_RSA_WITH_AES_128_CBC_SHA",
            "TLS_DH_anon_WITH_AES_128_CBC_SHA",
            "TLS_RSA_WITH_AES_256_CBC_SHA",
            "TLS_DH_DSS_WITH_AES_256_CBC_SHA",
            "TLS_DH_RSA_WITH_AES_256_CBC_SHA",
            "TLS_DHE_DSS_WITH_AES_256_CBC_SHA",
            "TLS_DHE_RSA_WITH_AES_256_CBC_SHA",
            "TLS_DH_anon_WITH_AES_256_CBC_SHA",
            "TLS_RSA_WITH_NULL_SHA256",
            "TLS_RSA_WITH_AES_128_CBC_SHA256",
            "TLS_RSA_WITH_AES_256_CBC_SHA256",
            "TLS_DH_DSS_WITH_AES_128_CBC_SHA256",
            "TLS_DH_RSA_WITH_AES_128_CBC_SHA256",
            "TLS_DHE_DSS_WITH_AES_128_CBC_SHA256",
            "TLS_RSA_WITH_CAMELLIA_128_CBC_SHA",
            "TLS_DH_DSS_WITH_CAMELLIA_128_CBC_SHA",
            "TLS_DH_RSA_WITH_CAMELLIA_128_CBC_SHA",
            "TLS_DHE_DSS_WITH_CAMELLIA_128_CBC_SHA",
            "TLS_DHE_RSA_WITH_CAMELLIA_128_CBC_SHA",
            "TLS_DH_anon_WITH_CAMELLIA_128_CBC_SHA",
            "TLS_DHE_RSA_WITH_AES_128_CBC_SHA256",
            "TLS_DH_DSS_WITH_AES_256_CBC_SHA256",
            "TLS_DH_RSA_WITH_AES_256_CBC_SHA256",
            "TLS_DHE_DSS_WITH_AES_256_CBC_SHA256",
            "TLS_DHE_RSA_WITH_AES_256_CBC_SHA256",
            "TLS_DH_anon_WITH_AES_128_CBC_SHA256",
            "TLS_DH_anon_WITH_AES_256_CBC_SHA256",
            "TLS_RSA_WITH_CAMELLIA_256_CBC_SHA",
            "TLS_DH_DSS_WITH_CAMELLIA_256_CBC_SHA",
            "TLS_DH_RSA_WITH_CAMELLIA_256_CBC_SHA",
            "TLS_DHE_DSS_WITH_CAMELLIA_256_CBC_SHA",
            "TLS_DHE_RSA_WITH_CAMELLIA_256_CBC_SHA",
            "TLS_DH_anon_WITH_CAMELLIA_256_CBC_SHA",
            "TLS_PSK_WITH_RC4_128_SHA",
            "TLS_PSK_WITH_3DES_EDE_CBC_SHA",
            "TLS_PSK_WITH_AES_128_CBC_SHA",
            "TLS_PSK_WITH_AES_256_CBC_SHA",
            "TLS_DHE_PSK_WITH_RC4_128_SHA",
            "TLS_DHE_PSK_WITH_3DES_EDE_CBC_SHA",
            "TLS_DHE_PSK_WITH_AES_128_CBC_SHA",
            "TLS_DHE_PSK_WITH_AES_256_CBC_SHA",
            "TLS_RSA_PSK_WITH_RC4_128_SHA",
            "TLS_RSA_PSK_WITH_3DES_EDE_CBC_SHA",
            "TLS_RSA_PSK_WITH_AES_128_CBC_SHA",
            "TLS_RSA_PSK_WITH_AES_256_CBC_SHA",
            "TLS_RSA_WITH_SEED_CBC_SHA",
            "TLS_DH_DSS_WITH_SEED_CBC_SHA",
            "TLS_DH_RSA_WITH_SEED_CBC_SHA",
            "TLS_DHE_DSS_WITH_SEED_CBC_SHA",
            "TLS_DHE_RSA_WITH_SEED_CBC_SHA",
            "TLS_DH_anon_WITH_SEED_CBC_SHA",
            "TLS_RSA_WITH_AES_128_GCM_SHA256",
            "TLS_RSA_WITH_AES_256_GCM_SHA384",
            "TLS_DHE_RSA_WITH_AES_128_GCM_SHA256",
            "TLS_DHE_RSA_WITH_AES_256_GCM_SHA384",
            "TLS_DH_RSA_WITH_AES_128_GCM_SHA256",
            "TLS_DH_RSA_WITH_AES_256_GCM_SHA384",
            "TLS_DHE_DSS_WITH_AES_128_GCM_SHA256",
            "TLS_DHE_DSS_WITH_AES_256_GCM_SHA384",
            "TLS_DH_DSS_WITH_AES_128_GCM_SHA256",
            "TLS_DH_DSS_WITH_AES_256_GCM_SHA384",
            "TLS_DH_anon_WITH_AES_128_GCM_SHA256",
            "TLS_DH_anon_WITH_AES_256_GCM_SHA384",
            "TLS_PSK_WITH_AES_128_GCM_SHA256",
            "TLS_PSK_WITH_AES_256_GCM_SHA384",
            "TLS_DHE_PSK_WITH_AES_128_GCM_SHA256",
            "TLS_DHE_PSK_WITH_AES_256_GCM_SHA384",
            "TLS_RSA_PSK_WITH_AES_128_GCM_SHA256",
            "TLS_RSA_PSK_WITH_AES_256_GCM_SHA384",
            "TLS_PSK_WITH_AES_128_CBC_SHA256",
            "TLS_PSK_WITH_AES_256_CBC_SHA384",
            "TLS_PSK_WITH_NULL_SHA256",
            "TLS_PSK_WITH_NULL_SHA384",
            "TLS_DHE_PSK_WITH_AES_128_CBC_SHA256",
            "TLS_DHE_PSK_WITH_AES_256_CBC_SHA384",
            "TLS_DHE_PSK_WITH_NULL_SHA256",
            "TLS_DHE_PSK_WITH_NULL_SHA384",
            "TLS_RSA_PSK_WITH_AES_128_CBC_SHA256",
            "TLS_RSA_PSK_WITH_AES_256_CBC_SHA384",
            "TLS_RSA_PSK_WITH_NULL_SHA256",
            "TLS_RSA_PSK_WITH_NULL_SHA384",
            "TLS_RSA_WITH_CAMELLIA_128_CBC_SHA256",
            "TLS_DH_DSS_WITH_CAMELLIA_128_CBC_SHA256",
            "TLS_DH_RSA_WITH_CAMELLIA_128_CBC_SHA256",
            "TLS_DHE_DSS_WITH_CAMELLIA_128_CBC_SHA256",
            "TLS_DHE_RSA_WITH_CAMELLIA_128_CBC_SHA256",
            "TLS_DH_anon_WITH_CAMELLIA_128_CBC_SHA256",
            "TLS_RSA_WITH_CAMELLIA_256_CBC_SHA256",
            "TLS_DH_DSS_WITH_CAMELLIA_256_CBC_SHA256",
            "TLS_DH_RSA_WITH_CAMELLIA_256_CBC_SHA256",
            "TLS_DHE_DSS_WITH_CAMELLIA_256_CBC_SHA256",
            "TLS_DHE_RSA_WITH_CAMELLIA_256_CBC_SHA256",
            "TLS_DH_anon_WITH_CAMELLIA_256_CBC_SHA256",
            "TLS_EMPTY_RENEGOTIATION_INFO_SCSV",
            "TLS_ECDH_ECDSA_WITH_NULL_SHA",
            "TLS_ECDH_ECDSA_WITH_RC4_128_SHA",
            "TLS_ECDH_ECDSA_WITH_3DES_EDE_CBC_SHA",
            "TLS_ECDH_ECDSA_WITH_AES_128_CBC_SHA",
            "TLS_ECDH_ECDSA_WITH_AES_256_CBC_SHA",
            "TLS_ECDHE_ECDSA_WITH_NULL_SHA",
            "TLS_ECDHE_ECDSA_WITH_RC4_128_SHA",
            "TLS_ECDHE_ECDSA_WITH_3DES_EDE_CBC_SHA",
            "TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA",
            "TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA",
            "TLS_ECDH_RSA_WITH_NULL_SHA",
            "TLS_ECDH_RSA_WITH_RC4_128_SHA",
            "TLS_ECDH_RSA_WITH_3DES_EDE_CBC_SHA",
            "TLS_ECDH_RSA_WITH_AES_128_CBC_SHA",
            "TLS_ECDH_RSA_WITH_AES_256_CBC_SHA",
            "TLS_ECDHE_RSA_WITH_NULL_SHA",
            "TLS_ECDHE_RSA_WITH_RC4_128_SHA",
            "TLS_ECDHE_RSA_WITH_3DES_EDE_CBC_SHA",
            "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA",
            "TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA",
            "TLS_ECDH_anon_WITH_NULL_SHA",
            "TLS_ECDH_anon_WITH_RC4_128_SHA",
            "TLS_ECDH_anon_WITH_3DES_EDE_CBC_SHA",
            "TLS_ECDH_anon_WITH_AES_128_CBC_SHA",
            "TLS_ECDH_anon_WITH_AES_256_CBC_SHA",
            "TLS_SRP_SHA_WITH_3DES_EDE_CBC_SHA",
            "TLS_SRP_SHA_RSA_WITH_3DES_EDE_CBC_SHA",
            "TLS_SRP_SHA_DSS_WITH_3DES_EDE_CBC_SHA",
            "TLS_SRP_SHA_WITH_AES_128_CBC_SHA",
            "TLS_SRP_SHA_RSA_WITH_AES_128_CBC_SHA",
            "TLS_SRP_SHA_DSS_WITH_AES_128_CBC_SHA",
            "TLS_SRP_SHA_WITH_AES_256_CBC_SHA",
            "TLS_SRP_SHA_RSA_WITH_AES_256_CBC_SHA",
            "TLS_SRP_SHA_DSS_WITH_AES_256_CBC_SHA",
            "TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA256",
            "TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA384",
            "TLS_ECDH_ECDSA_WITH_AES_128_CBC_SHA256",
            "TLS_ECDH_ECDSA_WITH_AES_256_CBC_SHA384",
            "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256",
            "TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA384",
            "TLS_ECDH_RSA_WITH_AES_128_CBC_SHA256",
            "TLS_ECDH_RSA_WITH_AES_256_CBC_SHA384",
            "TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256",
            "TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384",
            "TLS_ECDH_ECDSA_WITH_AES_128_GCM_SHA256",
            "TLS_ECDH_ECDSA_WITH_AES_256_GCM_SHA384",
            "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256",
            "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384",
            "TLS_ECDH_RSA_WITH_AES_128_GCM_SHA256",
            "TLS_ECDH_RSA_WITH_AES_256_GCM_SHA384",
            "TLS_ECDHE_PSK_WITH_RC4_128_SHA",
            "TLS_ECDHE_PSK_WITH_3DES_EDE_CBC_SHA",
            "TLS_ECDHE_PSK_WITH_AES_128_CBC_SHA",
            "TLS_ECDHE_PSK_WITH_AES_256_CBC_SHA",
            "TLS_ECDHE_PSK_WITH_AES_128_CBC_SHA256",
            "TLS_ECDHE_PSK_WITH_AES_256_CBC_SHA384",
            "TLS_ECDHE_PSK_WITH_NULL_SHA",
            "TLS_ECDHE_PSK_WITH_NULL_SHA256",
            "TLS_ECDHE_PSK_WITH_NULL_SHA384",
            "TLS_RSA_WITH_ARIA_128_CBC_SHA256",
            "TLS_RSA_WITH_ARIA_256_CBC_SHA384",
            "TLS_DH_DSS_WITH_ARIA_128_CBC_SHA256",
            "TLS_DH_DSS_WITH_ARIA_256_CBC_SHA384",
            "TLS_DH_RSA_WITH_ARIA_128_CBC_SHA256",
            "TLS_DH_RSA_WITH_ARIA_256_CBC_SHA384",
            "TLS_DHE_DSS_WITH_ARIA_128_CBC_SHA256",
            "TLS_DHE_DSS_WITH_ARIA_256_CBC_SHA384",
            "TLS_DHE_RSA_WITH_ARIA_128_CBC_SHA256",
            "TLS_DHE_RSA_WITH_ARIA_256_CBC_SHA384",
            "TLS_DH_anon_WITH_ARIA_128_CBC_SHA256",
            "TLS_DH_anon_WITH_ARIA_256_CBC_SHA384",
            "TLS_ECDHE_ECDSA_WITH_ARIA_128_CBC_SHA256",
            "TLS_ECDHE_ECDSA_WITH_ARIA_256_CBC_SHA384",
            "TLS_ECDH_ECDSA_WITH_ARIA_128_CBC_SHA256",
            "TLS_ECDH_ECDSA_WITH_ARIA_256_CBC_SHA384",
            "TLS_ECDHE_RSA_WITH_ARIA_128_CBC_SHA256",
            "TLS_ECDHE_RSA_WITH_ARIA_256_CBC_SHA384",
            "TLS_ECDH_RSA_WITH_ARIA_128_CBC_SHA256",
            "TLS_ECDH_RSA_WITH_ARIA_256_CBC_SHA384",
            "TLS_RSA_WITH_ARIA_128_GCM_SHA256",
            "TLS_RSA_WITH_ARIA_256_GCM_SHA384",
            "TLS_DHE_RSA_WITH_ARIA_128_GCM_SHA256",
            "TLS_DHE_RSA_WITH_ARIA_256_GCM_SHA384",
            "TLS_DH_RSA_WITH_ARIA_128_GCM_SHA256",
            "TLS_DH_RSA_WITH_ARIA_256_GCM_SHA384",
            "TLS_DHE_DSS_WITH_ARIA_128_GCM_SHA256",
            "TLS_DHE_DSS_WITH_ARIA_256_GCM_SHA384",
            "TLS_DH_DSS_WITH_ARIA_128_GCM_SHA256",
            "TLS_DH_DSS_WITH_ARIA_256_GCM_SHA384",
            "TLS_DH_anon_WITH_ARIA_128_GCM_SHA256",
            "TLS_DH_anon_WITH_ARIA_256_GCM_SHA384",
            "TLS_ECDHE_ECDSA_WITH_ARIA_128_GCM_SHA256",
            "TLS_ECDHE_ECDSA_WITH_ARIA_256_GCM_SHA384",
            "TLS_ECDH_ECDSA_WITH_ARIA_128_GCM_SHA256",
            "TLS_ECDH_ECDSA_WITH_ARIA_256_GCM_SHA384",
            "TLS_ECDHE_RSA_WITH_ARIA_128_GCM_SHA256",
            "TLS_ECDHE_RSA_WITH_ARIA_256_GCM_SHA384",
            "TLS_ECDH_RSA_WITH_ARIA_128_GCM_SHA256",
            "TLS_ECDH_RSA_WITH_ARIA_256_GCM_SHA384",
            "TLS_PSK_WITH_ARIA_128_CBC_SHA256",
            "TLS_PSK_WITH_ARIA_256_CBC_SHA384",
            "TLS_DHE_PSK_WITH_ARIA_128_CBC_SHA256",
            "TLS_DHE_PSK_WITH_ARIA_256_CBC_SHA384",
            "TLS_RSA_PSK_WITH_ARIA_128_CBC_SHA256",
            "TLS_RSA_PSK_WITH_ARIA_256_CBC_SHA384",
            "TLS_PSK_WITH_ARIA_128_GCM_SHA256",
            "TLS_PSK_WITH_ARIA_256_GCM_SHA384",
            "TLS_DHE_PSK_WITH_ARIA_128_GCM_SHA256",
            "TLS_DHE_PSK_WITH_ARIA_256_GCM_SHA384",
            "TLS_RSA_PSK_WITH_ARIA_128_GCM_SHA256",
            "TLS_RSA_PSK_WITH_ARIA_256_GCM_SHA384",
            "TLS_ECDHE_PSK_WITH_ARIA_128_CBC_SHA256",
            "TLS_ECDHE_PSK_WITH_ARIA_256_CBC_SHA384",
            "TLS_ECDHE_ECDSA_WITH_CAMELLIA_128_CBC_SHA256",
            "TLS_ECDHE_ECDSA_WITH_CAMELLIA_256_CBC_SHA384",
            "TLS_ECDH_ECDSA_WITH_CAMELLIA_128_CBC_SHA256",
            "TLS_ECDH_ECDSA_WITH_CAMELLIA_256_CBC_SHA384",
            "TLS_ECDHE_RSA_WITH_CAMELLIA_128_CBC_SHA256",
            "TLS_ECDHE_RSA_WITH_CAMELLIA_256_CBC_SHA384",
            "TLS_ECDH_RSA_WITH_CAMELLIA_128_CBC_SHA256",
            "TLS_ECDH_RSA_WITH_CAMELLIA_256_CBC_SHA384",
            "TLS_RSA_WITH_CAMELLIA_128_GCM_SHA256",
            "TLS_RSA_WITH_CAMELLIA_256_GCM_SHA384",
            "TLS_DHE_RSA_WITH_CAMELLIA_128_GCM_SHA256",
            "TLS_DHE_RSA_WITH_CAMELLIA_256_GCM_SHA384",
            "TLS_DH_RSA_WITH_CAMELLIA_128_GCM_SHA256",
            "TLS_DH_RSA_WITH_CAMELLIA_256_GCM_SHA384",
            "TLS_DHE_DSS_WITH_CAMELLIA_128_GCM_SHA256",
            "TLS_DHE_DSS_WITH_CAMELLIA_256_GCM_SHA384",
            "TLS_DH_DSS_WITH_CAMELLIA_128_GCM_SHA256",
            "TLS_DH_DSS_WITH_CAMELLIA_256_GCM_SHA384",
            "TLS_DH_anon_WITH_CAMELLIA_128_GCM_SHA256",
            "TLS_DH_anon_WITH_CAMELLIA_256_GCM_SHA384",
            "TLS_ECDHE_ECDSA_WITH_CAMELLIA_128_GCM_SHA256",
            "TLS_ECDHE_ECDSA_WITH_CAMELLIA_256_GCM_SHA384",
            "TLS_ECDH_ECDSA_WITH_CAMELLIA_128_GCM_SHA256",
            "TLS_ECDH_ECDSA_WITH_CAMELLIA_256_GCM_SHA384",
            "TLS_ECDHE_RSA_WITH_CAMELLIA_128_GCM_SHA256",
            "TLS_ECDHE_RSA_WITH_CAMELLIA_256_GCM_SHA384",
            "TLS_ECDH_RSA_WITH_CAMELLIA_128_GCM_SHA256",
            "TLS_ECDH_RSA_WITH_CAMELLIA_256_GCM_SHA384",
            "TLS_PSK_WITH_CAMELLIA_128_GCM_SHA256",
            "TLS_PSK_WITH_CAMELLIA_256_GCM_SHA384",
            "TLS_DHE_PSK_WITH_CAMELLIA_128_GCM_SHA256",
            "TLS_DHE_PSK_WITH_CAMELLIA_256_GCM_SHA384",
            "TLS_RSA_PSK_WITH_CAMELLIA_128_GCM_SHA256",
            "TLS_RSA_PSK_WITH_CAMELLIA_256_GCM_SHA384",
            "TLS_PSK_WITH_CAMELLIA_128_CBC_SHA256",
            "TLS_PSK_WITH_CAMELLIA_256_CBC_SHA384",
            "TLS_DHE_PSK_WITH_CAMELLIA_128_CBC_SHA256",
            "TLS_DHE_PSK_WITH_CAMELLIA_256_CBC_SHA384",
            "TLS_RSA_PSK_WITH_CAMELLIA_128_CBC_SHA256",
            "TLS_RSA_PSK_WITH_CAMELLIA_256_CBC_SHA384",
            "TLS_ECDHE_PSK_WITH_CAMELLIA_128_CBC_SHA256",
            "TLS_ECDHE_PSK_WITH_CAMELLIA_256_CBC_SHA384",
            "TLS_RSA_WITH_AES_128_CCM",
            "TLS_RSA_WITH_AES_256_CCM",
            "TLS_DHE_RSA_WITH_AES_128_CCM",
            "TLS_DHE_RSA_WITH_AES_256_CCM",
            "TLS_RSA_WITH_AES_128_CCM_8",
            "TLS_RSA_WITH_AES_256_CCM_8",
            "TLS_DHE_RSA_WITH_AES_128_CCM_8",
            "TLS_DHE_RSA_WITH_AES_256_CCM_8",
            "TLS_PSK_WITH_AES_128_CCM",
            "TLS_PSK_WITH_AES_256_CCM",
            "TLS_DHE_PSK_WITH_AES_128_CCM",
            "TLS_DHE_PSK_WITH_AES_256_CCM",
            "TLS_PSK_WITH_AES_128_CCM_8",
            "TLS_PSK_WITH_AES_256_CCM_8",
            "TLS_PSK_DHE_WITH_AES_128_CCM_8",
            "TLS_PSK_DHE_WITH_AES_256_CCM_8",
            "TLS_ECDHE_ECDSA_WITH_AES_128_CCM",
            "TLS_ECDHE_ECDSA_WITH_AES_256_CCM",
            "TLS_ECDHE_ECDSA_WITH_AES_128_CCM_8",
            "TLS_ECDHE_ECDSA_WITH_AES_256_CCM_8")));

}
