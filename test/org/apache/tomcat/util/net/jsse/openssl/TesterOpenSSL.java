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

import org.apache.catalina.util.IOTools;
import org.apache.tomcat.util.http.fileupload.ByteArrayOutputStream;

public class TesterOpenSSL {

    public static final boolean IS_EXPECTED_VERSION;

    public static final Set<Cipher> OPENSSL_UNIMPLEMENTED_CIPHERS =
            Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
                    // The following ciphers are not implemented in an OpenSSL
                    // version
                    Cipher.TLS_DHE_DSS_WITH_RC4_128_SHA,
                    Cipher.SSL_CK_RC2_128_CBC_WITH_MD5,
                    Cipher.SSL_FORTEZZA_DMS_WITH_NULL_SHA,
                    Cipher.SSL_FORTEZZA_DMS_WITH_FORTEZZA_CBC_SHA,
                    Cipher.SSL_FORTEZZA_DMS_WITH_RC4_128_SHA,
                    Cipher.TLS_DHE_DSS_EXPORT1024_WITH_DES_CBC_SHA,
                    Cipher.TLS_RSA_EXPORT1024_WITH_DES_CBC_SHA,
                    Cipher.TLS_RSA_EXPORT1024_WITH_RC2_CBC_56_MD5,
                    Cipher.TLS_DHE_DSS_EXPORT1024_WITH_RC4_56_SHA,
                    Cipher.TLS_RSA_EXPORT1024_WITH_RC4_56_SHA,
                    Cipher.TLS_RSA_EXPORT1024_WITH_RC4_56_MD5,
                    // The following are not implemented in 1.1.x onwards. They
                    // are implemented in 1.0.x and earlier
                    Cipher.SSL_CK_RC2_128_CBC_EXPORT40_WITH_MD5,
                    Cipher.SSL_CK_RC4_128_WITH_MD5,
                    Cipher.SSL2_DES_64_CBC_WITH_MD5,
                    Cipher.SSL2_DES_192_EDE3_CBC_WITH_MD5,
                    Cipher.SSL2_IDEA_128_CBC_WITH_MD5,
                    Cipher.SSL2_RC2_CBC_128_CBC_WITH_MD5,
                    Cipher.SSL2_RC4_128_EXPORT40_WITH_MD5)));

    static {
        // Note: The tests are configured for OpenSSL 1.1.0. Running with a
        //       different version is likely to trigger failures
        String expected_version = System.getProperty("tomcat.test.openssl.version", "");
        String versionString = null;
        try {
            versionString = executeOpenSSLCommand("version");
        } catch (IOException e) {
            versionString = "";
        }
        IS_EXPECTED_VERSION = versionString.startsWith("OpenSSL " + expected_version);
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
