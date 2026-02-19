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

package org.apache.tomcat.integration.httpd;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Reader;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.Assert;


public class TesterHttpd {

    private final File httpdConfDir;
    private final int httpdPort;

    private static final String HTTPD_PATH = "tomcat.test.httpd.path";

    private Process p;

    public TesterHttpd(File httpdConfDir, int httpdPort) {
        this.httpdConfDir = httpdConfDir;
        this.httpdPort = httpdPort;
    }

    public void start() throws IOException, InterruptedException {
        start(false);
    }

    public void start(boolean swallowOutput) throws IOException, InterruptedException {
        if (p != null) {
            throw new IllegalStateException("Already started");
        }

        String httpdPath = System.getProperty(HTTPD_PATH);
        if (httpdPath == null || httpdPath.isEmpty()) {
            httpdPath = "httpd";
        }

        File httpdConfFile = new File(httpdConfDir, "httpd.conf");
        validateHttpdConfig(httpdPath, httpdConfFile.getAbsolutePath());

        List<String> cmd = new ArrayList<>(4);
        cmd.add(httpdPath);
        cmd.add("-f");
        cmd.add(httpdConfFile.getAbsolutePath());
        cmd.add("-X");

        ProcessBuilder pb = new ProcessBuilder(cmd.toArray(new String[0]));

        p = pb.start();

        redirect(p.inputReader(), System.out, swallowOutput);
        redirect(p.errorReader(), System.err, swallowOutput);

        Assert.assertTrue(p.isAlive() && isHttpdReady());
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

    private static void validateHttpdConfig(final String httpdPath, final String httpdConfPath) throws IOException, InterruptedException {
        List<String> cmd = new ArrayList<>(4);

        cmd.add(httpdPath);
        cmd.add("-t");
        cmd.add("-f");
        cmd.add(httpdConfPath);

        ProcessBuilder pb = new ProcessBuilder(cmd.toArray(new String[0]));
        pb.redirectErrorStream(true);

        Process p = pb.start();

        String output = new String(p.getInputStream().readAllBytes());
        int exitCode = p.waitFor();

        if (exitCode != 0) {
            throw new IllegalStateException("Httpd configuration invalid. Output: " + output);
        }
    }

    @SuppressWarnings("BusyWait")
    private boolean isHttpdReady() throws InterruptedException {
        long deadline = System.currentTimeMillis() + 1000;
        while (System.currentTimeMillis() < deadline) {
            try (Socket ignored = new Socket("localhost", this.httpdPort)) {
                return true;
            } catch (IOException e) {
                Thread.sleep(100);
            }
        }
        throw new IllegalStateException("Httpd has not been started.");
    }


}
