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

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.jni.SSLContext;
import org.apache.tomcat.util.net.SSLHostConfig;
import org.apache.tomcat.util.net.TesterSupport;

public class TestOpenSSLConf extends TomcatBaseTest {

    private static final String ENABLED_CIPHER = "AES256-SHA256";
    private static final String[] EXPECTED_CIPHERS = {"AES256-SHA256"};
    private static final String[] ENABLED_PROTOCOLS = {"TLSv1.1"};
    private static final String[] DISABLED_PROTOCOLS = {"SSLv3", "TLSv1", "TLSv1.2"};

    public SSLHostConfig initOpenSSLConfCmdCipher(String name, String value) throws Exception {
        Tomcat tomcat = getTomcatInstance();

        TesterSupport.initSsl(tomcat);

        String protocol = tomcat.getConnector().getProtocolHandlerClassName();
        // The tests are only supported for APR and OpenSSL
        if (!protocol.contains("Apr")) {
            String sslImplementation = String.valueOf(
                    tomcat.getConnector().getProperty("sslImplementationName"));
            Assume.assumeTrue("This test is only for OpenSSL based SSL connectors",
                    sslImplementation.contains("openssl"));
        }

        OpenSSLConfCmd cmd = new OpenSSLConfCmd();
        cmd.setName(name);
        cmd.setValue(value);
        OpenSSLConf conf = new OpenSSLConf();
        conf.addCmd(cmd);
        SSLHostConfig[] sslHostConfigs = tomcat.getConnector().
                                         getProtocolHandler().findSslHostConfigs();
        Assert.assertEquals("Wrong SSLHostConfigCount", 1, sslHostConfigs.length);
        sslHostConfigs[0].setOpenSslConf(conf);

        tomcat.start();

        sslHostConfigs = tomcat.getConnector().getProtocolHandler().findSslHostConfigs();
        Assert.assertEquals("Wrong SSLHostConfigCount", 1, sslHostConfigs.length);
        return sslHostConfigs[0];
    }

    @Test
    public void testOpenSSLConfCmdCipher() throws Exception {
        SSLHostConfig sslHostConfig = initOpenSSLConfCmdCipher("CipherString",
                                                               ENABLED_CIPHER);
        String[] ciphers = sslHostConfig.getEnabledCiphers();
        Assert.assertThat("Wrong HostConfig ciphers", ciphers,
                CoreMatchers.is(EXPECTED_CIPHERS));
        ciphers = SSLContext.getCiphers(sslHostConfig.getOpenSslContext().longValue());
        Assert.assertThat("Wrong native SSL context ciphers", ciphers,
                CoreMatchers.is(EXPECTED_CIPHERS));
    }

    @Test
    public void testOpenSSLConfCmdProtocol() throws Exception {
        Set<String> disabledProtocols = new HashSet<>(Arrays.asList(DISABLED_PROTOCOLS));
        StringBuilder sb = new StringBuilder();
        for (String protocol : DISABLED_PROTOCOLS) {
            sb.append(",").append("-").append(protocol);
        }
        for (String protocol : ENABLED_PROTOCOLS) {
            sb.append(",").append(protocol);
        }
        SSLHostConfig sslHostConfig = initOpenSSLConfCmdCipher("Protocol",
                                                               sb.substring(1));
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
}
