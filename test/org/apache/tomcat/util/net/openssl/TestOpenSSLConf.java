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
package org.apache.tomcat.util.net.openssl;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import org.apache.catalina.connector.Connector;
import org.apache.catalina.core.AprLifecycleListener;
import org.apache.catalina.core.StandardServer;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.jni.SSLContext;
import org.apache.tomcat.util.net.SSLHostConfig;
import org.apache.tomcat.util.net.TesterSupport;

public class TestOpenSSLConf extends TomcatBaseTest {

    private static final String ENABLED_CIPHER = "AES256-SHA256";
    private static final String[] EXPECTED_CIPHERS = {ENABLED_CIPHER};
    private static final String[] ENABLED_PROTOCOLS = {"TLSv1.1"};
    private static final String[] DISABLED_PROTOCOLS = {"SSLv3", "TLSv1", "TLSv1.2"};
    private static final String[] DISABLED_PROTOCOLS_TLS13 = {"TLSv1.3"};
    // Test behavior needs to adjust for OpenSSL 1.1.1-pre3 and above
    private static final int OPENSSL_TLS13_SUPPORT_MIN_VERSION = 0x10101003;

    private static int OPENSSL_VERSION = TesterSupport.getOpensslVersion();

    private static boolean hasTLS13() {
        return OPENSSL_VERSION >= OPENSSL_TLS13_SUPPORT_MIN_VERSION;
    }

    private SSLHostConfig initOpenSSLConfCmd(String... commands) throws Exception {
        Assert.assertNotNull(commands);
        Assert.assertTrue("Invalid length", commands.length % 2 == 0);

        Tomcat tomcat = getTomcatInstance();
        Connector connector = tomcat.getConnector();

        TesterSupport.initSsl(tomcat);

        Assert.assertTrue(connector.setProperty("sslImplementationName", OpenSSLImplementation.class.getName()));

        OpenSSLConf conf = new OpenSSLConf();
        for (int i = 0; i < commands.length;) {
            OpenSSLConfCmd cmd = new OpenSSLConfCmd();
            cmd.setName(commands[i++]);
            cmd.setValue(commands[i++]);
            conf.addCmd(cmd);
        }

        SSLHostConfig[] sslHostConfigs = connector.getProtocolHandler().findSslHostConfigs();
        Assert.assertEquals("Wrong SSLHostConfigCount", 1, sslHostConfigs.length);
        sslHostConfigs[0].setOpenSslConf(conf);

        tomcat.start();

        sslHostConfigs = connector.getProtocolHandler().findSslHostConfigs();
        Assert.assertEquals("Wrong SSLHostConfigCount", 1, sslHostConfigs.length);
        return sslHostConfigs[0];
    }

    @Test
    public void testOpenSSLConfCmdCipher() throws Exception {
        SSLHostConfig sslHostConfig;
        if (hasTLS13()) {
            // Ensure TLSv1.3 ciphers aren't returned
            sslHostConfig = initOpenSSLConfCmd("CipherString", ENABLED_CIPHER,
                                               "CipherSuites", "");
        } else {
            sslHostConfig = initOpenSSLConfCmd("CipherString", ENABLED_CIPHER);
        }
        String[] ciphers = sslHostConfig.getEnabledCiphers();
        MatcherAssert.assertThat("Wrong HostConfig ciphers", ciphers,
                CoreMatchers.is(EXPECTED_CIPHERS));
        ciphers = SSLContext.getCiphers(sslHostConfig.getOpenSslContext().longValue());
        MatcherAssert.assertThat("Wrong native SSL context ciphers", ciphers,
                CoreMatchers.is(EXPECTED_CIPHERS));
    }

    @Test
    public void testOpenSSLConfCmdProtocol() throws Exception {
        Set<String> disabledProtocols = new HashSet<>(Arrays.asList(DISABLED_PROTOCOLS));
        StringBuilder sb = new StringBuilder();
        for (String protocol : DISABLED_PROTOCOLS) {
            sb.append(",").append("-").append(protocol);
        }
        if (hasTLS13()) {
            // Also disable TLSv1.3
            for (String protocol : DISABLED_PROTOCOLS_TLS13) {
                sb.append(",").append("-").append(protocol);
                disabledProtocols.add(protocol);
            }
        }
        for (String protocol : ENABLED_PROTOCOLS) {
            sb.append(",").append(protocol);
        }
        SSLHostConfig sslHostConfig = initOpenSSLConfCmd("Protocol", sb.substring(1));
        String[] protocols = sslHostConfig.getEnabledProtocols();
        for (String protocol : protocols) {
            Assert.assertFalse("Protocol " + protocol + " is not allowed",
                    disabledProtocols.contains(protocol));
        }
        Set<String> enabledProtocols = new HashSet<>(Arrays.asList(protocols));
        for (String protocol : ENABLED_PROTOCOLS) {
            Assert.assertTrue("Protocol " + protocol + " is not enabled",
                    enabledProtocols.contains(protocol));
        }
    }


    @Override
    public void setUp() throws Exception {
        super.setUp();

        // Tests are only intended for OpenSSL
        Assume.assumeTrue(TesterSupport.isOpensslAvailable());

        Tomcat tomcat = getTomcatInstance();

        AprLifecycleListener listener = new AprLifecycleListener();
        Assume.assumeTrue(AprLifecycleListener.isAprAvailable());
        StandardServer server = (StandardServer) tomcat.getServer();
        server.addLifecycleListener(listener);
    }
}
