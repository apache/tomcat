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
package org.apache.catalina.valves.rewrite;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import jakarta.servlet.ServletException;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;

import org.apache.catalina.Container;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.core.AprLifecycleListener;
import org.apache.catalina.core.OpenSSLLifecycleListener;
import org.apache.catalina.core.StandardServer;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.catalina.valves.ValveBase;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.net.SSLHostConfig;
import org.apache.tomcat.util.net.TesterSupport;
import org.apache.tomcat.util.net.openssl.OpenSSLImplementation;

@RunWith(Parameterized.class)
public class TestResolverSSL extends TomcatBaseTest {

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> parameters() {
        List<Object[]> parameterSets = new ArrayList<>();
        parameterSets.add(new Object[] {
                "JSSE", Boolean.FALSE, "org.apache.tomcat.util.net.jsse.JSSEImplementation"});
        parameterSets.add(new Object[] {
                "OpenSSL", Boolean.TRUE, "org.apache.tomcat.util.net.openssl.OpenSSLImplementation"});
        parameterSets.add(new Object[] {
                "OpenSSL-FFM", Boolean.TRUE, "org.apache.tomcat.util.net.openssl.panama.OpenSSLImplementation"});

        return parameterSets;
    }

    @Parameter(0)
    public String connectorName;

    @Parameter(1)
    public boolean useOpenSSL;

    @Parameter(2)
    public String sslImplementationName;


    @Test
    public void testSslEnv() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        Container root = tomcat.getHost().findChild("");
        root.getPipeline().addValve(new ResolverTestValve());

        // Enable session caching so the SSL Session is available when using OpenSSL
        SSLHostConfig sslHostConfig = tomcat.getConnector().findSslHostConfigs()[0];
        sslHostConfig.setSessionCacheSize(20 * 1024);

        tomcat.start();
        ByteChunk res = getUrl("https://localhost:" + getPort() + "/protected");
        // Just look a bit at the result
        System.out.println(res.toString());
        Assert.assertTrue(res.toString().indexOf("OK") > 0);
    }

    //@formatter:off
    // List from https://httpd.apache.org/docs/2.4/mod/mod_ssl.html#envvars
    private static final String[] keys = {
            "HTTPS",
            "SSL_PROTOCOL",
            "SSL_SESSION_ID",
            "SSL_SESSION_RESUMED",
            "SSL_SECURE_RENEG",
            "SSL_CIPHER",
            "SSL_CIPHER_EXPORT",
            "SSL_CIPHER_USEKEYSIZE",
            "SSL_CIPHER_ALGKEYSIZE",
            "SSL_COMPRESS_METHOD",
            "SSL_VERSION_INTERFACE",
            "SSL_VERSION_LIBRARY",
            "SSL_CLIENT_M_VERSION",
            "SSL_CLIENT_M_SERIAL",
            "SSL_CLIENT_S_DN",
            "SSL_CLIENT_S_DN_CN", // CN component
            "SSL_CLIENT_S_DN_O", // O component
            "SSL_CLIENT_S_DN_C", // C component
            "SSL_CLIENT_SAN_Email_0",
            "SSL_CLIENT_SAN_DNS_0",
            "SSL_CLIENT_SAN_OTHER_msUPN_0",
            "SSL_CLIENT_I_DN",
            "SSL_CLIENT_I_DN_CN", // CN component
            "SSL_CLIENT_I_DN_O", // O component
            "SSL_CLIENT_I_DN_C", // C component
            "SSL_CLIENT_V_START",
            "SSL_CLIENT_V_END",
            "SSL_CLIENT_V_REMAIN",
            "SSL_CLIENT_A_SIG",
            "SSL_CLIENT_A_KEY",
            "SSL_CLIENT_CERT",
            "SSL_CLIENT_CERT_CHAIN_0",
            "SSL_CLIENT_CERT_RFC4523_CEA",
            "SSL_CLIENT_VERIFY",
            "SSL_SERVER_M_VERSION",
            "SSL_SERVER_M_SERIAL",
            "SSL_SERVER_S_DN",
            "SSL_SERVER_SAN_Email_0",
            "SSL_SERVER_SAN_DNS_0",
            "SSL_SERVER_SAN_OTHER_dnsSRV_0",
            "SSL_SERVER_S_DN_CN", // CN component
            "SSL_SERVER_S_DN_O", // O component
            "SSL_SERVER_S_DN_C", // C component
            "SSL_SERVER_I_DN",
            "SSL_SERVER_I_DN_CN", // CN component
            "SSL_SERVER_I_DN_O", // O component
            "SSL_SERVER_I_DN_C", // C component
            "SSL_SERVER_V_START",
            "SSL_SERVER_V_END",
            "SSL_SERVER_A_SIG",
            "SSL_SERVER_A_KEY",
            "SSL_SERVER_CERT",
            "SSL_SRP_USER",
            "SSL_SRP_USERINFO",
            "SSL_TLS_SNI" };
    //@formatter:on

    public static class ResolverTestValve extends ValveBase {

        @Override
        public void invoke(Request request, Response response) throws IOException, ServletException {
            PrintWriter writer = response.getWriter();
            Resolver resolver = new ResolverImpl(request);
            for (String key : keys) {
                resolve(key, resolver, writer);
            }
            writer.println("OK");
        }

        private void resolve(String key, Resolver resolver, PrintWriter writer) {
            writer.println("[" + key + "] " + resolver.resolveSsl(key));
        }
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();

        Tomcat tomcat = getTomcatInstance();

        TesterSupport.configureClientCertContext(tomcat);

        TesterSupport.configureClientSsl();

        Assert.assertTrue(tomcat.getConnector().setProperty("sslImplementationName", sslImplementationName));

        if (useOpenSSL) {
            if (OpenSSLImplementation.class.getName().equals(sslImplementationName)) {
                AprLifecycleListener listener = new AprLifecycleListener();
                Assume.assumeTrue(AprLifecycleListener.isAprAvailable());
                StandardServer server = (StandardServer) tomcat.getServer();
                server.addLifecycleListener(listener);
            } else if ("org.apache.tomcat.util.net.openssl.panama.OpenSSLImplementation".equals(sslImplementationName)) {
                OpenSSLLifecycleListener listener = new OpenSSLLifecycleListener();
                Assume.assumeTrue(OpenSSLLifecycleListener.isAvailable());
                StandardServer server = (StandardServer) tomcat.getServer();
                server.addLifecycleListener(listener);
            }
        }
    }
}
