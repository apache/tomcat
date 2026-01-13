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
package org.apache.tomcat.util.net.ocsp;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.Assert;

import org.apache.tomcat.util.net.TesterSupport;

/*
 * The OpenSSL ocsp tool is great. But is does generate a lot of output. That needs to be swallowed else the
 * process will freeze with the output buffers (stdout and stderr) are full.
 *
 * There is a command line option to redirect stdout (which could be redirected to /dev/null) but there is no option to
 * redirect stderr. Therefore, this class uses a couple of dedicated threads to read stdout and stderr. By default, the
 * output is ignored but it can be dumped to Java's stdout/stderr if required for debugging purposes.
 */
public class TesterOcspResponder {

    private static List<String> ocspArgs = Arrays.asList("ocsp", "-port", "8888", "-text", "-index",
            TesterSupport.DB_INDEX, "-CA", TesterSupport.CA_CERT_PEM, "-rkey", TesterSupport.OCSP_RESPONDER_RSA_KEY,
            "-rsigner", TesterSupport.OCSP_RESPONDER_RSA_CERT, "-nmin", "60");

    private Process p;

    public void start() throws IOException {
        if (p != null) {
            throw new IllegalStateException("Already started");
        }

        String openSSLPath = System.getProperty("tomcat.test.openssl.path");
        String openSSLLibPath = null;
        if (openSSLPath == null || openSSLPath.length() == 0) {
            openSSLPath = "openssl";
        } else {
            // Explicit OpenSSL path may also need explicit lib path
            // (e.g. Gump needs this)
            openSSLLibPath = openSSLPath.substring(0, openSSLPath.lastIndexOf('/'));
            openSSLLibPath = openSSLLibPath + "/../:" + openSSLLibPath + "/../lib:" + openSSLLibPath + "/../lib64";
        }
        List<String> cmd = new ArrayList<>();
        cmd.add(openSSLPath);
        cmd.addAll(ocspArgs);

        ProcessBuilder pb = new ProcessBuilder(cmd.toArray(new String[0]));

        if (openSSLLibPath != null) {
            Map<String,String> env = pb.environment();
            String libraryPath = env.get("LD_LIBRARY_PATH");
            if (libraryPath == null) {
                libraryPath = openSSLLibPath;
            } else {
                libraryPath = libraryPath + ":" + openSSLLibPath;
            }
            env.put("LD_LIBRARY_PATH", libraryPath);
        }

        p = pb.start();

        redirect(new BufferedReader(new InputStreamReader(p.getInputStream())) , System.out, true);
        redirect(new BufferedReader(new InputStreamReader(p.getErrorStream())), System.err, true);

        Assert.assertTrue(p.isAlive());
    }

    public void stop() {
        if (p == null) {
            throw new IllegalStateException("Not started");
        }
        p.destroy();

        try {
            if (!p.waitFor(30, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Failed to stop");
            }
        } catch (InterruptedException e) {
            throw new IllegalStateException("Interrupted while waiting to stop", e);
        }
    }


    private void redirect(final Reader r, final PrintStream os, final boolean swallow) {
        /*
         * InputStream will close when process ends. Thread will exit once stream closes.
         */
        new Thread( () -> {
            char[] cbuf = new char[1024];
            try {
                int read;
                while ((read = r.read(cbuf)) > 0) {
                    if (!swallow) {
                        os.print(new String(cbuf, 0, read));
                    }
                }
            } catch (IOException ignore) {
                // Ignore
            }

        }).start();
    }
}
