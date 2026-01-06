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
package org.apache.tomcat.util.net;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.net.ssl.SSLHandshakeException;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;

import org.apache.catalina.Context;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.startup.TesterServlet;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.buf.ByteChunk;

@RunWith(Parameterized.class)
public class TestSSLHostConfigCipher extends TomcatBaseTest {

    private static final String CIPHER_12_AVAILABLE = "TLS_DHE_RSA_WITH_AES_128_GCM_SHA256";
    private static final String CIPHER_12_NOT_AVAILABLE = "TLS_DHE_RSA_WITH_AES_256_GCM_SHA384";
    private static final String CIPHER_13_AVAILABLE = "TLS_AES_128_GCM_SHA256";
    private static final String CIPHER_13_NOT_AVAILABLE = "TLS_AES_256_GCM_SHA384";

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


    @Override
    public void setUp() throws Exception {
        super.setUp();

        Tomcat tomcat = getTomcatInstance();

        // Server-side TLS configuration
        TesterSupport.initSsl(tomcat);
        TesterSupport.configureSSLImplementation(tomcat, sslImplementationName, useOpenSSL);

        // Test specific, server-side cipher & protocol configuration
        SSLHostConfig sslHostConfig = getSSLHostConfig();
        sslHostConfig.setProtocols("+TLSv1.2+TLSv1.3");
        sslHostConfig.setCiphers(CIPHER_12_AVAILABLE);
        sslHostConfig.setCipherSuites(CIPHER_13_AVAILABLE);

        // Simple webapp
        Context ctxt = getProgrammaticRootContext();
        Tomcat.addServlet(ctxt, "TesterServlet", new TesterServlet());
        ctxt.addServletMappingDecoded("/*", "TesterServlet");
    }


    @Test
    public void testTls12CipherAvailable() throws Exception {
        // Client-side TLS configuration
        TesterSupport.configureClientSsl(true, new String[] { CIPHER_12_AVAILABLE } );

        doTest();
    }


    @Test(expected=SSLHandshakeException.class)
    public void testTls12CipherNotAvailable() throws Exception {
        // Client-side TLS configuration
        TesterSupport.configureClientSsl(true, new String[] { CIPHER_12_NOT_AVAILABLE } );

        doTest();
    }


    @Test
    public void testTls13CipherAvailable() throws Exception {
        // Client-side TLS configuration
        TesterSupport.configureClientSsl(new String[] { CIPHER_13_AVAILABLE } );

        doTest();
    }


    @Test(expected=SSLHandshakeException.class)
    public void testTls13CipherNotAvailable() throws Exception {
        // Client-side TLS configuration
        TesterSupport.configureClientSsl(new String[] { CIPHER_13_NOT_AVAILABLE } );

        doTest();
    }


    private void doTest() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        tomcat.start();

        // Check a request can be made
        ByteChunk res = getUrl("https://localhost:" + getPort() + "/");
        Assert.assertEquals("OK", res.toString());
    }


    private SSLHostConfig getSSLHostConfig() {
        Tomcat tomcat = getTomcatInstance();
        Connector connector = tomcat.getConnector();
        return connector.findSslHostConfigs()[0];
    }
}
