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
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.catalina.util.IOTools;
import org.apache.tomcat.util.http.fileupload.ByteArrayOutputStream;

public class TesterOpenSSL {

    public static final int VERSION;

    public static final Set<Cipher> OPENSSL_UNIMPLEMENTED_CIPHERS;

    static {
        // Note: The tests are configured for the OpenSSL 1.1.0 development
        //       branch. Running with a different version is likely to trigger
        //       failures.
        String versionString = null;
        try {
            versionString = executeOpenSSLCommand("version");
        } catch (IOException e) {
            versionString = "";
        }
        if (versionString.startsWith("OpenSSL 1.1.0")) {
            VERSION = 10100;
        } else if (versionString.startsWith("OpenSSL 1.0.2")) {
            VERSION = 10002;
        } else if (versionString.startsWith("OpenSSL 1.0.1")) {
            VERSION = 10001;
        } else if (versionString.startsWith("OpenSSL 1.0.0")) {
            VERSION = 10000;
        } else if (versionString.startsWith("OpenSSL 0.9.8")) {
            VERSION =   908;
        } else {
            // Unknown OpenSSL version
            throw new IllegalStateException("Unknown OpenSSL version " + versionString);
        }

        HashSet<Cipher> unimplemented = new HashSet<>();

        // Note: The following lists are intended to be aligned with the most
        //       recent release of each OpenSSL release branch

        // TODO Validate this for all current OpenSSL versions
        //      0.9.8 - TODO
        //      1.0.0 - TODO
        //      1.0.1 - Done
        //      1.0.2 - Done
        //      1.1.0 - Done

        // These were removed in 0.9.8 (or earlier) so won't be available in any
        // supported version.
        unimplemented.add(Cipher.TLS_DHE_DSS_WITH_RC4_128_SHA);
        unimplemented.add(Cipher.TLS_DHE_DSS_EXPORT1024_WITH_DES_CBC_SHA);
        unimplemented.add(Cipher.TLS_RSA_EXPORT1024_WITH_DES_CBC_SHA);
        unimplemented.add(Cipher.TLS_RSA_EXPORT1024_WITH_RC2_CBC_56_MD5);
        unimplemented.add(Cipher.TLS_DHE_DSS_EXPORT1024_WITH_RC4_56_SHA);
        unimplemented.add(Cipher.TLS_RSA_EXPORT1024_WITH_RC4_56_SHA);
        unimplemented.add(Cipher.TLS_RSA_EXPORT1024_WITH_RC4_56_MD5);

        if (VERSION < 10000) {
            // These were implemented in 1.0.0 so won't be available in any
            // earlier version
        } else {
            // These were removed in 1.0.0 so won't be available from that
            // version onwards.
        }


        if (VERSION < 10001) {
            // These were added in 1.0.1 so won't be available in any earlier
            // version
            unimplemented.add(Cipher.TLS_RSA_WITH_CAMELLIA_128_CBC_SHA256);
            unimplemented.add(Cipher.TLS_DH_DSS_WITH_CAMELLIA_128_CBC_SHA256);
            unimplemented.add(Cipher.TLS_DH_RSA_WITH_CAMELLIA_128_CBC_SHA256);
            unimplemented.add(Cipher.TLS_DHE_DSS_WITH_CAMELLIA_128_CBC_SHA256);
            unimplemented.add(Cipher.TLS_DHE_RSA_WITH_CAMELLIA_128_CBC_SHA256);
            unimplemented.add(Cipher.TLS_DH_anon_WITH_CAMELLIA_128_CBC_SHA256);
            unimplemented.add(Cipher.TLS_RSA_WITH_CAMELLIA_256_CBC_SHA256);
            unimplemented.add(Cipher.TLS_DHE_DSS_WITH_CAMELLIA_256_CBC_SHA256);
            unimplemented.add(Cipher.TLS_DHE_RSA_WITH_CAMELLIA_256_CBC_SHA256);
            unimplemented.add(Cipher.TLS_DH_anon_WITH_CAMELLIA_256_CBC_SHA256);
            unimplemented.add(Cipher.TLS_ECDHE_ECDSA_WITH_CAMELLIA_128_CBC_SHA256);
            unimplemented.add(Cipher.TLS_ECDHE_ECDSA_WITH_CAMELLIA_256_CBC_SHA384);
            unimplemented.add(Cipher.TLS_ECDH_ECDSA_WITH_CAMELLIA_128_CBC_SHA256);
            unimplemented.add(Cipher.TLS_ECDH_ECDSA_WITH_CAMELLIA_256_CBC_SHA384);
            unimplemented.add(Cipher.TLS_ECDHE_RSA_WITH_CAMELLIA_128_CBC_SHA256);
            unimplemented.add(Cipher.TLS_ECDHE_RSA_WITH_CAMELLIA_256_CBC_SHA384);
            unimplemented.add(Cipher.TLS_ECDH_RSA_WITH_CAMELLIA_128_CBC_SHA256);
            unimplemented.add(Cipher.TLS_ECDH_RSA_WITH_CAMELLIA_256_CBC_SHA384);
            unimplemented.add(Cipher.TLS_PSK_WITH_AES_128_GCM_SHA256);
            unimplemented.add(Cipher.TLS_PSK_WITH_AES_256_GCM_SHA384);
        } else {
            // These were removed in 1.0.1 so won't be available from that
            // version onwards.
            unimplemented.add(Cipher.TLS_RSA_EXPORT1024_WITH_RC4_56_MD5);
            unimplemented.add(Cipher.TLS_RSA_EXPORT1024_WITH_RC2_CBC_56_MD5);
            unimplemented.add(Cipher.TLS_RSA_EXPORT1024_WITH_DES_CBC_SHA);
            unimplemented.add(Cipher.TLS_DHE_DSS_EXPORT1024_WITH_DES_CBC_SHA);
            unimplemented.add(Cipher.TLS_RSA_EXPORT1024_WITH_RC4_56_SHA);
            unimplemented.add(Cipher.TLS_DHE_DSS_EXPORT1024_WITH_RC4_56_SHA);
            unimplemented.add(Cipher.TLS_DHE_DSS_WITH_RC4_128_SHA);
        }

        if (VERSION < 10002) {
            // These were implemented in 1.0.2 so won't be available in any
            // earlier version
            unimplemented.add(Cipher.TLS_DH_DSS_EXPORT_WITH_DES40_CBC_SHA);
            unimplemented.add(Cipher.TLS_DH_DSS_WITH_AES_128_CBC_SHA);
            unimplemented.add(Cipher.TLS_DH_DSS_WITH_AES_256_CBC_SHA);
            unimplemented.add(Cipher.TLS_DH_DSS_WITH_AES_128_CBC_SHA256);
            unimplemented.add(Cipher.TLS_DH_DSS_WITH_AES_256_CBC_SHA256);
            unimplemented.add(Cipher.TLS_DH_DSS_WITH_AES_128_GCM_SHA256);
            unimplemented.add(Cipher.TLS_DH_DSS_WITH_AES_256_GCM_SHA384);
            unimplemented.add(Cipher.TLS_DH_DSS_WITH_CAMELLIA_128_CBC_SHA);
            unimplemented.add(Cipher.TLS_DH_DSS_WITH_CAMELLIA_256_CBC_SHA);
            unimplemented.add(Cipher.TLS_DH_DSS_WITH_DES_CBC_SHA);
            unimplemented.add(Cipher.TLS_DH_DSS_WITH_3DES_EDE_CBC_SHA);
            unimplemented.add(Cipher.TLS_DH_DSS_WITH_SEED_CBC_SHA);
            unimplemented.add(Cipher.TLS_DH_RSA_EXPORT_WITH_DES40_CBC_SHA);
            unimplemented.add(Cipher.TLS_DH_RSA_WITH_AES_128_CBC_SHA);
            unimplemented.add(Cipher.TLS_DH_RSA_WITH_AES_256_CBC_SHA);
            unimplemented.add(Cipher.TLS_DH_RSA_WITH_AES_128_CBC_SHA256);
            unimplemented.add(Cipher.TLS_DH_RSA_WITH_AES_256_CBC_SHA256);
            unimplemented.add(Cipher.TLS_DH_RSA_WITH_AES_128_GCM_SHA256);
            unimplemented.add(Cipher.TLS_DH_RSA_WITH_AES_256_GCM_SHA384);
            unimplemented.add(Cipher.TLS_DH_RSA_WITH_CAMELLIA_128_CBC_SHA);
            unimplemented.add(Cipher.TLS_DH_RSA_WITH_CAMELLIA_256_CBC_SHA);
            unimplemented.add(Cipher.TLS_DH_RSA_WITH_DES_CBC_SHA);
            unimplemented.add(Cipher.TLS_DH_RSA_WITH_3DES_EDE_CBC_SHA);
            unimplemented.add(Cipher.TLS_DH_RSA_WITH_SEED_CBC_SHA);

        } else {
            // These were removed in 1.0.2 so won't be available from that
            // version onwards.
            unimplemented.add(Cipher.TLS_DH_RSA_EXPORT_WITH_DES40_CBC_SHA);
            unimplemented.add(Cipher.TLS_DH_DSS_EXPORT_WITH_DES40_CBC_SHA);
        }

        if (VERSION < 10100) {
            // These were implemented in 1.1.0 so won't be available in any
            // earlier version
            unimplemented.add(Cipher.TLS_PSK_WITH_NULL_SHA);
            unimplemented.add(Cipher.TLS_DHE_PSK_WITH_NULL_SHA);
            unimplemented.add(Cipher.TLS_RSA_PSK_WITH_NULL_SHA);
            unimplemented.add(Cipher.TLS_DHE_PSK_WITH_AES_128_GCM_SHA256);
            unimplemented.add(Cipher.TLS_DHE_PSK_WITH_AES_256_GCM_SHA384);
            unimplemented.add(Cipher.TLS_RSA_PSK_WITH_AES_128_GCM_SHA256);
            unimplemented.add(Cipher.TLS_RSA_PSK_WITH_AES_256_GCM_SHA384);
            unimplemented.add(Cipher.TLS_PSK_WITH_AES_128_CBC_SHA256);
            unimplemented.add(Cipher.TLS_PSK_WITH_AES_256_CBC_SHA384);
            unimplemented.add(Cipher.TLS_PSK_WITH_NULL_SHA256);
            unimplemented.add(Cipher.TLS_PSK_WITH_NULL_SHA384);
            unimplemented.add(Cipher.TLS_DHE_PSK_WITH_AES_128_CBC_SHA256);
            unimplemented.add(Cipher.TLS_DHE_PSK_WITH_AES_256_CBC_SHA384);
            unimplemented.add(Cipher.TLS_DHE_PSK_WITH_NULL_SHA256);
            unimplemented.add(Cipher.TLS_DHE_PSK_WITH_NULL_SHA384);
            unimplemented.add(Cipher.TLS_RSA_PSK_WITH_AES_128_CBC_SHA256);
            unimplemented.add(Cipher.TLS_RSA_PSK_WITH_AES_256_CBC_SHA384);
            unimplemented.add(Cipher.TLS_RSA_PSK_WITH_NULL_SHA256);
            unimplemented.add(Cipher.TLS_RSA_PSK_WITH_NULL_SHA384);
            unimplemented.add(Cipher.TLS_DHE_PSK_WITH_RC4_128_SHA);
            unimplemented.add(Cipher.TLS_DHE_PSK_WITH_3DES_EDE_CBC_SHA);
            unimplemented.add(Cipher.TLS_DHE_PSK_WITH_AES_128_CBC_SHA);
            unimplemented.add(Cipher.TLS_DHE_PSK_WITH_AES_256_CBC_SHA);
            unimplemented.add(Cipher.TLS_RSA_PSK_WITH_RC4_128_SHA);
            unimplemented.add(Cipher.TLS_RSA_PSK_WITH_3DES_EDE_CBC_SHA);
            unimplemented.add(Cipher.TLS_RSA_PSK_WITH_AES_128_CBC_SHA);
            unimplemented.add(Cipher.TLS_RSA_PSK_WITH_AES_256_CBC_SHA);
            unimplemented.add(Cipher.TLS_ECDHE_PSK_WITH_RC4_128_SHA);
            unimplemented.add(Cipher.TLS_ECDHE_PSK_WITH_3DES_EDE_CBC_SHA);
            unimplemented.add(Cipher.TLS_ECDHE_PSK_WITH_AES_128_CBC_SHA);
            unimplemented.add(Cipher.TLS_ECDHE_PSK_WITH_AES_256_CBC_SHA);
            unimplemented.add(Cipher.TLS_ECDHE_PSK_WITH_AES_128_CBC_SHA256);
            unimplemented.add(Cipher.TLS_ECDHE_PSK_WITH_AES_256_CBC_SHA384);
            unimplemented.add(Cipher.TLS_ECDHE_PSK_WITH_NULL_SHA);
            unimplemented.add(Cipher.TLS_ECDHE_PSK_WITH_NULL_SHA256);
            unimplemented.add(Cipher.TLS_ECDHE_PSK_WITH_NULL_SHA384);
            unimplemented.add(Cipher.TLS_PSK_WITH_CAMELLIA_128_CBC_SHA256);
            unimplemented.add(Cipher.TLS_PSK_WITH_CAMELLIA_256_CBC_SHA384);
            unimplemented.add(Cipher.TLS_DHE_PSK_WITH_CAMELLIA_128_CBC_SHA256);
            unimplemented.add(Cipher.TLS_DHE_PSK_WITH_CAMELLIA_256_CBC_SHA384);
            unimplemented.add(Cipher.TLS_RSA_PSK_WITH_CAMELLIA_128_CBC_SHA256);
            unimplemented.add(Cipher.TLS_RSA_PSK_WITH_CAMELLIA_256_CBC_SHA384);
            unimplemented.add(Cipher.TLS_ECDHE_PSK_WITH_CAMELLIA_128_CBC_SHA256);
            unimplemented.add(Cipher.TLS_ECDHE_PSK_WITH_CAMELLIA_256_CBC_SHA384);
            unimplemented.add(Cipher.TLS_DH_DSS_WITH_CAMELLIA_256_CBC_SHA256);
            unimplemented.add(Cipher.TLS_DH_RSA_WITH_CAMELLIA_256_CBC_SHA256);
            unimplemented.add(Cipher.TLS_DHE_DSS_WITH_CAMELLIA_256_CBC_SHA256);
            unimplemented.add(Cipher.TLS_DHE_RSA_WITH_CAMELLIA_256_CBC_SHA256);
            unimplemented.add(Cipher.TLS_DH_DSS_WITH_CAMELLIA_128_CBC_SHA256);
            unimplemented.add(Cipher.TLS_DH_RSA_WITH_CAMELLIA_128_CBC_SHA256);
            unimplemented.add(Cipher.TLS_DHE_DSS_WITH_CAMELLIA_128_CBC_SHA256);
            unimplemented.add(Cipher.TLS_DHE_RSA_WITH_CAMELLIA_128_CBC_SHA256);
            unimplemented.add(Cipher.TLS_ECDH_ECDSA_WITH_CAMELLIA_256_CBC_SHA384);
            unimplemented.add(Cipher.TLS_ECDH_RSA_WITH_CAMELLIA_256_CBC_SHA384);
            unimplemented.add(Cipher.TLS_ECDHE_ECDSA_WITH_CAMELLIA_128_CBC_SHA256);
            unimplemented.add(Cipher.TLS_ECDHE_RSA_WITH_CAMELLIA_128_CBC_SHA256);
            unimplemented.add(Cipher.TLS_ECDHE_ECDSA_WITH_CAMELLIA_256_CBC_SHA384);
            unimplemented.add(Cipher.TLS_ECDH_ECDSA_WITH_CAMELLIA_128_CBC_SHA256);
            unimplemented.add(Cipher.TLS_ECDHE_RSA_WITH_CAMELLIA_256_CBC_SHA384);
            unimplemented.add(Cipher.TLS_ECDH_RSA_WITH_CAMELLIA_128_CBC_SHA256);
            unimplemented.add(Cipher.TLS_RSA_WITH_CAMELLIA_128_CBC_SHA256);
            unimplemented.add(Cipher.TLS_DH_DSS_WITH_CAMELLIA_128_CBC_SHA256);
            unimplemented.add(Cipher.TLS_DH_RSA_WITH_CAMELLIA_128_CBC_SHA256);
            unimplemented.add(Cipher.TLS_DHE_DSS_WITH_CAMELLIA_128_CBC_SHA256);
            unimplemented.add(Cipher.TLS_DHE_RSA_WITH_CAMELLIA_128_CBC_SHA256);
            unimplemented.add(Cipher.TLS_DH_anon_WITH_CAMELLIA_128_CBC_SHA256);
            unimplemented.add(Cipher.TLS_RSA_WITH_CAMELLIA_256_CBC_SHA256);
            unimplemented.add(Cipher.TLS_PSK_WITH_AES_128_GCM_SHA256);
            unimplemented.add(Cipher.TLS_PSK_WITH_AES_256_GCM_SHA384);
            unimplemented.add(Cipher.TLS_DH_anon_WITH_CAMELLIA_256_CBC_SHA256);
        } else {
            // These were removed in 1.1.0 so won't be available from that
            // version onwards.
            unimplemented.add(Cipher.SSL_CK_RC2_128_CBC_EXPORT40_WITH_MD5);
            unimplemented.add(Cipher.SSL_CK_RC4_128_WITH_MD5);
            unimplemented.add(Cipher.SSL2_DES_192_EDE3_CBC_WITH_MD5);
            unimplemented.add(Cipher.SSL2_DES_64_CBC_WITH_MD5);
            unimplemented.add(Cipher.SSL2_IDEA_128_CBC_WITH_MD5);
            unimplemented.add(Cipher.SSL2_RC4_128_EXPORT40_WITH_MD5);
            unimplemented.add(Cipher.SSL_CK_RC2_128_CBC_WITH_MD5);
        }
        OPENSSL_UNIMPLEMENTED_CIPHERS = Collections.unmodifiableSet(unimplemented);
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
        String stdout;
        if (specification == null) {
            stdout = executeOpenSSLCommand("ciphers", "-v");
        } else {
            stdout = executeOpenSSLCommand("ciphers", "-v", specification);
        }

        if (stdout.length() == 0) {
            return stdout;
        }

        StringBuilder output = new StringBuilder();
        boolean first = true;

        // OpenSSL should have returned one cipher per line
        String ciphers[] = stdout.split("\n");
        for (String cipher : ciphers) {
            // Handle rename for 1.1.0 onwards
            cipher = cipher.replaceAll("EDH", "DHE");
            if (first) {
                first = false;
            } else {
                output.append(':');
            }
            // Name is first part
            int i = cipher.indexOf(' ');
            output.append(cipher.substring(0, i));

            // Advance i past the space
            while (Character.isWhitespace(cipher.charAt(i))) {
                i++;
            }

            // Protocol is the second
            int j = cipher.indexOf(' ', i);
            output.append('+');
            output.append(cipher.substring(i, j));
        }
        return output.toString();
    }


    /*
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

        ProcessBuilder pb = new ProcessBuilder(cmd.toArray(new String[cmd.size()]));
        Process p = pb.start();

        InputStreamToText stdout = new InputStreamToText(p.getInputStream());
        InputStreamToText stderr = new InputStreamToText(p.getErrorStream());

        Thread t1 = new Thread(stdout);
        t1.setName("OpenSSL stdout reader");
        t1.start();

        Thread t2 = new Thread(stderr);
        t2.setName("OpenSSL stderr reader");
        t2.start();

        try {
            t1.join();
            t2.join();
        } catch (InterruptedException e) {
            throw new IOException(e);
        }

        String errorText = stderr.getText();
        if (errorText.length() > 0) {
            System.err.println(errorText);
        }

        return stdout.getText().trim();
    }

    private static class InputStreamToText implements Runnable {

        private final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        private final InputStream is;

        InputStreamToText(InputStream is) {
            this.is = is;
        }

        @Override
        public void run() {
            try {
                IOTools.flow(is, baos);
            } catch (IOException e) {
                // Ignore
            }
        }

        public String getText() {
            return baos.toString();
        }
    }
}
