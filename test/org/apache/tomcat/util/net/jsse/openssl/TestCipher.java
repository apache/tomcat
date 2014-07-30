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

import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import org.apache.catalina.util.IOTools;
import org.apache.tomcat.util.http.fileupload.ByteArrayOutputStream;

public class TestCipher {

    /**
     * Checks that every cipher suite returned by OpenSSL is mapped to at least
     * one cipher suite that is recognised by JSSE or is a cipher suite known
     * not to be supported by JSSE.
     */
    @Test
    @Ignore //FIXME: enable the test
    public void testAllOpenSSlCiphersMapped() throws Exception {
        Set<String> openSSLCipherSuites = getOpenSSLCiphersAsSet("ALL");

        for (String openSSLCipherSuite : openSSLCipherSuites) {
            List<String> jsseCipherSuites =
                    OpenSSLCipherConfigurationParser.parseExpression(openSSLCipherSuite);

            for (JsseImpl jsseImpl : JSSE_IMPLS) {
                boolean found = false;
                for (String jsseCipherSuite : jsseCipherSuites) {
                    if (jsseImpl.getStandardNames().contains(jsseCipherSuite)) {
                        found = true;
                        Assert.assertFalse("Mapping found in " + jsseImpl.getVendor() +
                                "'s JSSE implementation for " + openSSLCipherSuite +
                                " when none was expected",
                                jsseImpl.getOpenSslUnmapped().contains(openSSLCipherSuite));
                        break;
                    }
                }
                if (!found) {
                    Assert.assertTrue("No mapping found in " + jsseImpl.getVendor() +
                            "'s JSSE implementation for " + openSSLCipherSuite +
                            " when one was expected",
                            jsseImpl.getOpenSslUnmapped().contains(openSSLCipherSuite));
                }
            }
        }
    }


    private static Set<String> getOpenSSLCiphersAsSet(String specification) throws Exception {
        String[] ciphers = getOpenSSLCiphersAsExpression(specification).trim().split(":");
        Set<String> result = new HashSet<>(ciphers.length);
        for (String cipher : ciphers) {
            result.add(cipher);
        }
        return result;

    }


    private static String getOpenSSLCiphersAsExpression(String specification) throws Exception {
        // TODO The path to OpenSSL needs to be made configurable
        StringBuilder cmd = new StringBuilder("/opt/local/bin/openssl ciphers");
        if (specification != null) {
            cmd.append(' ');
            cmd.append(specification);
        }
        Process process = Runtime.getRuntime().exec(cmd.toString());
        InputStream stderr = process.getErrorStream();
        InputStream stdout = process.getInputStream();

        ByteArrayOutputStream stderrBytes = new ByteArrayOutputStream();
        IOTools.flow(stderr, stderrBytes);
        //String errorText = stderrBytes.toString();
        //Assert.assertTrue(errorText, errorText.length() == 0);

        ByteArrayOutputStream stdoutBytes = new ByteArrayOutputStream();
        IOTools.flow(stdout, stdoutBytes);
        return stdoutBytes.toString();
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
                    "DES-CBC-MD5",
                    "DES-CBC3-MD5",
                    "IDEA-CBC-MD5",
                    "RC2-CBC-MD5")));


    /**
     * These are all the IBM standard Java names for cipher suites taken from
     * http://www-01.ibm.com/support/knowledgecenter/SSYKE2_7.0.0/com.ibm.java.security.component.71.doc/security-component/jsse2Docs/ciphersuites.html?lang=en
     * on 29th July 2014.
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
                    "ADH-CAMELLIA128-SHA",
                    "ADH-CAMELLIA256-SHA",
                    "ADH-SEED-SHA",
                    "CAMELLIA128-SHA",
                    "CAMELLIA256-SHA",
                    "DES-CBC-MD5",
                    "DES-CBC3-MD5",
                    "DHE-DSS-CAMELLIA128-SHA",
                    "DHE-DSS-CAMELLIA256-SHA",
                    "DHE-DSS-SEED-SHA",
                    "DHE-RSA-CAMELLIA128-SHA",
                    "DHE-RSA-CAMELLIA256-SHA",
                    "DHE-RSA-SEED-SHA",
                    "IDEA-CBC-MD5",
                    "IDEA-CBC-SHA",
                    "PSK-3DES-EDE-CBC-SHA",
                    "PSK-AES128-CBC-SHA",
                    "PSK-AES256-CBC-SHA",
                    "PSK-RC4-SHA",
                    "RC2-CBC-MD5",
                    "SEED-SHA",
                    "SRP-AES-128-CBC-SHA",
                    "SRP-AES-256-CBC-SHA",
                    "SRP-3DES-EDE-CBC-SHA",
                    "SRP-DSS-3DES-EDE-CBC-SHA",
                    "SRP-DSS-AES-128-CBC-SHA",
                    "SRP-DSS-AES-256-CBC-SHA",
                    "SRP-RSA-3DES-EDE-CBC-SHA",
                    "SRP-RSA-AES-128-CBC-SHA",
                    "SRP-RSA-AES-256-CBC-SHA")));


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
}
