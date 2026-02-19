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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.nio.channels.FileLock;
import java.util.List;

import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.BeforeClass;

import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.Valve;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.compat.JrePlatform;
import org.apache.tomcat.util.net.TesterSupport;

/**
 * Base class for httpd integration tests.
 * Manages httpd and Tomcat process lifecycle.
 */
public abstract class HttpdIntegrationBaseTest extends TomcatBaseTest {

    private static final String HTTPD_CONFIG =
               """
                      Listen %{HTTPD_PORT}
                      PidFile %{CONF_DIR}/httpd.pid
                      LoadModule authz_core_module modules/mod_authz_core.so
                """
                + (JrePlatform.IS_WINDOWS ?
                """
                      LoadModule mpm_winnt_module modules/mod_mpm_winnt.so
                      ErrorLog "|C:/Windows/System32/more.com"
                """
                :
                """
                      LoadModule unixd_module modules/mod_unixd.so
                      LoadModule mpm_event_module modules/mod_mpm_event.so
                      ErrorLog /dev/stderr
                """
                ) +
                """
                      LogLevel warn
                      ServerName localhost:%{HTTPD_PORT}
                """;

    private static final String SERVLET_NAME = "snoop";

    private static final File lockFile = new File("test/org/apache/tomcat/integration/httpd/httpd-binary.lock");
    private static FileLock lock = null;

    private TesterHttpd httpd;
    private int httpdPort;
    private int httpdSslPort;
    protected File httpdConfDir;

    private int tomcatPort;

    protected abstract String getHttpdConfig();
    protected abstract List<Valve> getValveConfig();

    @BeforeClass
    public static void obtainHttpdBinaryLock() throws IOException {
        @SuppressWarnings("resource")
        FileOutputStream fos = new FileOutputStream(lockFile);
        lock = fos.getChannel().lock();
    }

    @AfterClass
    public static void releaseHttpdBinaryLock() throws IOException {
        // Should not be null be in case obtaining the lock fails, avoid a second error.
        if (lock != null) {
            lock.release();
        }
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();
        setUpTomcat();
        setUpHttpd();
    }

    @Override
    public void tearDown() throws Exception {
        if (httpd != null) {
            httpd.stop();
            httpd = null;
        }
        super.tearDown();
    }

    private void setUpTomcat() throws LifecycleException {
        Tomcat tomcat = getTomcatInstance();
        Context ctx = getProgrammaticRootContext();
        for (Valve valve : getValveConfig()) {
            ctx.getPipeline().addValve(valve);
        }
        Tomcat.addServlet(ctx, SERVLET_NAME, new SnoopServlet());
        ctx.addServletMappingDecoded("/" + SERVLET_NAME, SERVLET_NAME);
        tomcat.start();
        tomcatPort = getPort();
    }

    private void setUpHttpd() throws IOException {
        httpdPort = findFreePort();
        httpdSslPort = findFreePort();
        httpdConfDir = getTemporaryDirectory();
        generateHttpdConfig(getHttpdConfig());

        httpd = new TesterHttpd(httpdConfDir, httpdPort);
        try {
            httpd.start();
        } catch (IOException | InterruptedException ioe) {
            httpd = null;
        } catch (IllegalStateException ise) {
            httpd = null;
            Assume.assumeFalse("Required httpd module not available", ise.getMessage() != null && ise.getMessage().contains("Cannot load modules"));
            throw ise;
        }
    }

    private static int findFreePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    public void generateHttpdConfig(String httpdConf) throws IOException {

        httpdConf = HTTPD_CONFIG + httpdConf;

        httpdConf = httpdConf.replace("%{HTTPD_PORT}", Integer.toString(httpdPort))
                             .replace("%{TOMCAT_PORT}", Integer.toString(tomcatPort))
                             .replace("%{SERVLET_NAME}", SERVLET_NAME)
                             .replace("%{CONF_DIR}", httpdConfDir.getAbsolutePath())
                             .replace("%{HTTPD_SSL_PORT}", Integer.toString(httpdSslPort))
                             .replace("%{SSL_CERT_FILE}", new File(TesterSupport.LOCALHOST_RSA_CERT_PEM).getAbsolutePath())
                             .replace("%{SSL_KEY_FILE}", new File(TesterSupport.LOCALHOST_RSA_KEY_PEM).getAbsolutePath())
                             .replace("%{SSL_CA_CERT_FILE}", new File(TesterSupport.CA_CERT_PEM).getAbsolutePath());

        try (PrintWriter writer = new PrintWriter(new File(httpdConfDir, "httpd.conf"))) {
            writer.write(httpdConf);
        }

    }

    public int getHttpdPort() {
        return httpdPort;
    }

    public int getHttpdSslPort() {
        return httpdSslPort;
    }
}
