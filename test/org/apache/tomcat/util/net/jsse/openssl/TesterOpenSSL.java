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

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Assert;

import org.apache.catalina.util.IOTools;
import org.apache.tomcat.util.http.fileupload.ByteArrayOutputStream;

public class TesterOpenSSL {

    public static final String EXPECTED_VERSION = "1.0.1h";
    public static final boolean IS_EXPECTED_VERSION;

    public static final Set<Cipher> OPENSSL_UNIMPLEMENTED_CIPHERS =
            Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
                    Cipher.TLS_DH_DSS_EXPORT_WITH_DES40_CBC_SHA,
                    Cipher.TLS_DH_RSA_EXPORT_WITH_DES40_CBC_SHA,
                    Cipher.TLS_DH_DSS_WITH_AES_256_GCM_SHA384,
                    Cipher.TLS_DH_RSA_WITH_AES_256_GCM_SHA384,
                    Cipher.TLS_DH_DSS_WITH_AES_256_CBC_SHA256,
                    Cipher.TLS_DH_RSA_WITH_AES_256_CBC_SHA256,
                    Cipher.TLS_DH_RSA_WITH_AES_256_CBC_SHA,
                    Cipher.TLS_DH_DSS_WITH_AES_256_CBC_SHA,
                    Cipher.TLS_DH_RSA_WITH_CAMELLIA_256_CBC_SHA,
                    Cipher.TLS_DH_DSS_WITH_CAMELLIA_256_CBC_SHA,
                    Cipher.TLS_DH_RSA_WITH_3DES_EDE_CBC_SHA,
                    Cipher.TLS_DH_DSS_WITH_3DES_EDE_CBC_SHA,
                    Cipher.TLS_DH_DSS_WITH_AES_128_GCM_SHA256,
                    Cipher.TLS_DH_RSA_WITH_AES_128_CBC_SHA256,
                    Cipher.TLS_DH_DSS_WITH_AES_128_CBC_SHA256,
                    Cipher.TLS_DH_RSA_WITH_CAMELLIA_128_CBC_SHA,
                    Cipher.TLS_DH_DSS_WITH_CAMELLIA_128_CBC_SHA,
                    Cipher.TLS_DH_RSA_WITH_AES_128_GCM_SHA256,
                    Cipher.TLS_DH_RSA_WITH_AES_128_CBC_SHA,
                    Cipher.TLS_DH_DSS_WITH_AES_128_CBC_SHA,
                    Cipher.TLS_DH_RSA_WITH_DES_CBC_SHA,
                    Cipher.TLS_DH_DSS_WITH_DES_CBC_SHA,
                    Cipher.TLS_DH_RSA_WITH_SEED_CBC_SHA,
                    Cipher.TLS_DH_DSS_WITH_SEED_CBC_SHA,
                    Cipher.TLS_DHE_DSS_WITH_RC4_128_SHA,
                    Cipher.SSL_CK_RC2_128_CBC_WITH_MD5,
                    Cipher.SSL_CK_RC2_128_CBC_EXPORT40_WITH_MD5,
                    Cipher.SSL_FORTEZZA_DMS_WITH_NULL_SHA,
                    Cipher.SSL_FORTEZZA_DMS_WITH_FORTEZZA_CBC_SHA,
                    Cipher.SSL_FORTEZZA_DMS_WITH_RC4_128_SHA,
                    Cipher.TLS_DHE_DSS_EXPORT1024_WITH_DES_CBC_SHA,
                    Cipher.TLS_RSA_EXPORT1024_WITH_DES_CBC_SHA,
                    Cipher.TLS_RSA_EXPORT1024_WITH_RC2_CBC_56_MD5,
                    Cipher.TLS_DHE_DSS_EXPORT1024_WITH_RC4_56_SHA,
                    Cipher.TLS_RSA_EXPORT1024_WITH_RC4_56_SHA,
                    Cipher.TLS_RSA_EXPORT1024_WITH_RC4_56_MD5)));

    static {
        String versionString = null;
        try {
            versionString = executeOpenSSLCommand("version");
        } catch (IOException e) {
            versionString = "";
        }
        IS_EXPECTED_VERSION = versionString.contains(EXPECTED_VERSION);
    }


    private TesterOpenSSL() {
        // Utility class. Hide default constructor.
    }


    public static Set<String> getOpenSSLCiphersAsSet(String specification) throws Exception {
        String[] ciphers = getOpenSSLCiphersAsExpression(specification).trim().split(":");
        Set<String> result = new HashSet<>(ciphers.length);
        for (String cipher : ciphers) {
            result.add(cipher);
        }
        return result;

    }


    public static String getOpenSSLCiphersAsExpression(String specification) throws Exception {
        if (specification == null) {
            return executeOpenSSLCommand("ciphers");
        } else {
            return executeOpenSSLCommand("ciphers", specification);
        }
    }


    /**
     * Use this method to filter parser results when comparing them to OpenSSL
     * results to take account of unimplemented cipher suites.
     */
    public static void removeUnimplementedCiphersJsse(List<String> list) {
        for (Cipher cipher : OPENSSL_UNIMPLEMENTED_CIPHERS) {
            for (String jsseName : cipher.getJsseNames()) {
                list.remove(jsseName);
            }
        }
    }


    private static String executeOpenSSLCommand(String... args) throws IOException {
        String openSSLPath = System.getProperty("tomcat.test.openssl.path");
        if (openSSLPath == null || openSSLPath.length() == 0) {
            openSSLPath = "openssl";
        }
        List<String> cmd = new ArrayList<>();
        cmd.add(openSSLPath);
        for (String arg : args) {
            cmd.add(arg);
        }

        Process process = Runtime.getRuntime().exec(cmd.toArray(new String[cmd.size()]));
        InputStream stderr = process.getErrorStream();
        InputStream stdout = process.getInputStream();

        ByteArrayOutputStream stderrBytes = new ByteArrayOutputStream();
        IOTools.flow(stderr, stderrBytes);
        String errorText = stderrBytes.toString();
        Assert.assertTrue(errorText, errorText.length() == 0);

        ByteArrayOutputStream stdoutBytes = new ByteArrayOutputStream();
        IOTools.flow(stdout, stdoutBytes);
        return stdoutBytes.toString().trim();
    }
}
