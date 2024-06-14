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
package org.apache.tomcat.util.net;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;

import org.apache.catalina.connector.Connector;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.jni.SSL;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.net.openssl.OpenSSLImplementation;

/**
 * The keys and certificates used in this file are all available in svn and were
 * generated using a test CA the files for which are in the Tomcat PMC private
 * repository since not all of them are AL2 licensed.
 *
 * The JSSE implementation of TLSv1.3 only supports authentication during the
 * initial handshake. This test requires TLSv1.3 on client and server so it is
 * skipped unless running on a Java version that supports TLSv1.3.
 */
@RunWith(Parameterized.class)
public class TestClientCertTls13 extends TomcatBaseTest {

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
    public void testClientCertGet() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        tomcat.start();
        ByteChunk res = getUrl("https://localhost:" + getPort() + "/protected");
        Assert.assertEquals("OK-" + TesterSupport.ROLE, res.toString());
    }

    @Test
    public void testClientCertPost() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        tomcat.start();

        int size = 32 * 1024;

        byte[] body = new byte[size];
        Arrays.fill(body, TesterSupport.DATA);

        // Protected resource
        ByteChunk res = new ByteChunk();
        int rc = postUrl(body, "https://localhost:" + getPort() + "/protected", res, null);

        Assert.assertEquals(200, rc);
        Assert.assertEquals("OK-" + size, res.toString());
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();

        Tomcat tomcat = getTomcatInstance();

        TesterSupport.configureClientCertContext(tomcat);

        TesterSupport.configureClientSsl();

        Connector connector = tomcat.getConnector();
        TesterSupport.configureSSLImplementation(tomcat, sslImplementationName, useOpenSSL);

        if (useOpenSSL) {
            if (OpenSSLImplementation.class.getName().equals(sslImplementationName)) {
                // Need at least OpenSSL 1.1.1 for TLSv1.3 support
                Assume.assumeTrue(SSL.version() >= 0x1010100f);
            }
        }

        // Tests default to TLSv1.2 when client cert auth is used
        // Need to override some of the previous settings
        SSLHostConfig[] sslHostConfigs = connector.findSslHostConfigs();
        Assert.assertNotNull(sslHostConfigs);
        Assert.assertEquals(1, sslHostConfigs.length);

        SSLHostConfig sslHostConfig = sslHostConfigs[0];

        // TLS 1.3 support
        sslHostConfig.setProtocols(Constants.SSL_PROTO_TLSv1_3);
        // And add force authentication to occur on the initial handshake
        sslHostConfig.setCertificateVerification("required");
    }
}
