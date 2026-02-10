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

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;

import org.apache.catalina.connector.Connector;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;

@RunWith(Parameterized.class)
public class TestSSLHostConfigProtocol extends TomcatBaseTest {

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
    }


    @Test
    public void testSSLv2() throws Exception {
        doTestIgnoreProtocol("SSLv2");
    }


    @Test
    public void testUnknown() throws Exception {
        doTestIgnoreProtocol("Unknown");
    }


    private void doTestIgnoreProtocol(String protocol) throws Exception {
        SSLHostConfig sslHostConfig = getSSLHostConfig();

        sslHostConfig.setProtocols("+" + protocol + "+TLSv1.2");

        Tomcat tomcat = getTomcatInstance();
        tomcat.start();

        // Expect only TLSv1.2 as unrecognised protocol should always be disabled
        String[] enabledProtocols = sslHostConfig.getEnabledProtocols();

        Assert.assertNotNull(enabledProtocols);
        Assert.assertEquals(1, enabledProtocols.length);
        Assert.assertEquals("TLSv1.2", enabledProtocols[0]);
    }


    private SSLHostConfig getSSLHostConfig() {
        Tomcat tomcat = getTomcatInstance();
        Connector connector = tomcat.getConnector();
        return connector.findSslHostConfigs()[0];
    }

}
