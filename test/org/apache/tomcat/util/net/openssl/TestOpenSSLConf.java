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

import static org.junit.Assert.assertEquals;

import org.junit.Assume;
import org.junit.Test;

import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.jni.SSLContext;
import org.apache.tomcat.util.net.SSLHostConfig;
import org.apache.tomcat.util.net.TesterSupport;

public class TestOpenSSLConf extends TomcatBaseTest {

    private static final String CIPHER = "AES256-SHA256";
    private static final String PROTOCOL = "-SSLv3,-TLSv1,TLSv1.1,-TLSv1.2";
    private static final String EXPECTED_PROTOCOLS = "SSLv2Hello,TLSv1.1";

    public SSLHostConfig initOpenSSLConfCmdCipher(String name, String value) throws Exception {
        Tomcat tomcat = getTomcatInstance();

        TesterSupport.initSsl(tomcat);

        String protocol = tomcat.getConnector().getProtocolHandlerClassName();
        // The tests are only supported for APR and OpenSSL
        if (!protocol.contains("Apr")) {
            String sslImplementation =
                tomcat.getConnector().getProperty("sslImplementationName").toString();
            Assume.assumeTrue("This test is only for OpenSSL based SSL connectors",
                sslImplementation.contains("openssl"));
        }

        OpenSSLConfCmd cmd = new OpenSSLConfCmd();
        cmd.setName(name);
        cmd.setValue(value);
        OpenSSLConf conf = new OpenSSLConf();
        conf.addCmd(cmd);
        SSLHostConfig[] sslHostConfigs = tomcat.getConnector().getProtocolHandler().findSslHostConfigs();
        assertEquals("Checking SSLHostConfigCount", 1, sslHostConfigs.length);
        sslHostConfigs[0].setOpenSslConf(conf);

        tomcat.start();

        sslHostConfigs = tomcat.getConnector().getProtocolHandler().findSslHostConfigs();
        assertEquals("Checking SSLHostConfigCount", 1, sslHostConfigs.length);
        return sslHostConfigs[0];
    }

    @Test
    public void testOpenSSLConfCmdCipher() throws Exception {
        SSLHostConfig sslHostConfig = initOpenSSLConfCmdCipher("CipherString", CIPHER);
        String[] ciphers = sslHostConfig.getEnabledCiphers();
        assertEquals("Checking enabled cipher count", 1, ciphers.length);
        assertEquals("Checking enabled cipher", CIPHER, ciphers[0]);
        ciphers = SSLContext.getCiphers(sslHostConfig.getOpenSslContext().longValue());
        assertEquals("Checking context cipher count", 1, ciphers.length);
        assertEquals("Checking context cipher", CIPHER, ciphers[0]);
    }

    @Test
    public void testOpenSSLConfCmdProtocol() throws Exception {
        SSLHostConfig sslHostConfig = initOpenSSLConfCmdCipher("Protocol", PROTOCOL);
        String[] protocols = sslHostConfig.getEnabledProtocols();
        assertEquals("Checking enabled protocol count", 2, protocols.length);
        assertEquals("Checking enabled protocol", EXPECTED_PROTOCOLS,
                     protocols[0] + "," + protocols[1]);
    }
}
