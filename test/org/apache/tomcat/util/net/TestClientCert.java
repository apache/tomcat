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

import org.apache.catalina.Context;
import org.apache.catalina.core.AprLifecycleListener;
import org.apache.catalina.core.StandardServer;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.buf.ByteChunk;

/**
 * The keys and certificates used in this file are all available in svn and were
 * generated using a test CA the files for which are in the Tomcat PMC private
 * repository since not all of them are AL2 licensed.
 */
@RunWith(Parameterized.class)
public class TestClientCert extends TomcatBaseTest {

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> parameters() {
        List<Object[]> parameterSets = new ArrayList<>();
        parameterSets.add(new Object[] {
                "JSSE", Boolean.FALSE, "org.apache.tomcat.util.net.jsse.JSSEImplementation"});
        parameterSets.add(new Object[] {
                "OpenSSL", Boolean.TRUE, "org.apache.tomcat.util.net.openssl.OpenSSLImplementation"});
        parameterSets.add(new Object[] {
                "OpenSSL-Panama", Boolean.FALSE, "org.apache.tomcat.util.net.openssl.panama.OpenSSLImplementation"});

        return parameterSets;
    }

    @Parameter(0)
    public String connectorName;

    @Parameter(1)
    public boolean needApr;

    @Parameter(2)
    public String sslImplementationName;


    @Test
    public void testClientCertGetWithoutPreemptive() throws Exception {
        doTestClientCertGet(false);
    }

    @Test
    public void testClientCertGetWithPreemptive() throws Exception {
        doTestClientCertGet(true);
    }

    private void doTestClientCertGet(boolean preemptive) throws Exception {
        if (preemptive) {
            Tomcat tomcat = getTomcatInstance();
            // Only one context deployed
            Context c = (Context) tomcat.getHost().findChildren()[0];
            // Enable pre-emptive auth
            c.setPreemptiveAuthentication(true);
        }

        getTomcatInstance().start();

        // Unprotected resource
        ByteChunk res = getUrl("https://localhost:" + getPort() + "/unprotected");

        int count = TesterSupport.getLastClientAuthRequestedIssuerCount();
        if (log.isDebugEnabled()) {
            log.debug("Last client KeyManager usage: " + TesterSupport.getLastClientAuthKeyManagerUsage() +
                      ", " + count + " requested Issuers, first one: " +
                      (count > 0 ? TesterSupport.getLastClientAuthRequestedIssuer(0).getName() : "NONE"));
            log.debug("Expected requested Issuer: " +
                      (preemptive ? TesterSupport.getClientAuthExpectedIssuer() : "NONE"));
        }

        if (preemptive) {
            Assert.assertTrue("Checking requested client issuer against " +
                    TesterSupport.getClientAuthExpectedIssuer(),
                    TesterSupport.checkLastClientAuthRequestedIssuers());
            Assert.assertEquals("OK-" + TesterSupport.ROLE, res.toString());
        } else {
            Assert.assertEquals(0, count);
            Assert.assertEquals("OK", res.toString());
        }

        // Protected resource
        res = getUrl("https://localhost:" + getPort() + "/protected");

        if (log.isDebugEnabled()) {
            count = TesterSupport.getLastClientAuthRequestedIssuerCount();
            log.debug("Last client KeyManager usage: " + TesterSupport.getLastClientAuthKeyManagerUsage() +
                      ", " + count + " requested Issuers, first one: " +
                      (count > 0 ? TesterSupport.getLastClientAuthRequestedIssuer(0).getName() : "NONE"));
            log.debug("Expected requested Issuer: " + TesterSupport.getClientAuthExpectedIssuer());
        }
        Assert.assertTrue("Checking requested client issuer against " +
                TesterSupport.getClientAuthExpectedIssuer(),
                TesterSupport.checkLastClientAuthRequestedIssuers());

        Assert.assertEquals("OK-" + TesterSupport.ROLE, res.toString());
    }

    @Test
    public void testClientCertPostZero() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        tomcat.getConnector().setMaxSavePostSize(0);
        doTestClientCertPost(1024, false);
    }

    @Test
    public void testClientCertPostSmaller() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        int bodySize = tomcat.getConnector().getMaxSavePostSize() / 2;
        doTestClientCertPost(bodySize, false);
    }

    @Test
    public void testClientCertPostSame() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        int bodySize = tomcat.getConnector().getMaxSavePostSize();
        doTestClientCertPost(bodySize, false);
    }

    @Test
    public void testClientCertPostLarger() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        int bodySize = tomcat.getConnector().getMaxSavePostSize() * 2;
        doTestClientCertPost(bodySize, true);
    }

    private void doTestClientCertPost(int bodySize, boolean expectProtectedFail)
            throws Exception {
        Tomcat tomcat = getTomcatInstance();
        tomcat.start();

        byte[] body = new byte[bodySize];
        Arrays.fill(body, TesterSupport.DATA);

        // Unprotected resource
        ByteChunk res = postUrl(body, "https://localhost:" + getPort() + "/unprotected");

        int count = TesterSupport.getLastClientAuthRequestedIssuerCount();
        if (log.isDebugEnabled()) {
            log.debug("Last client KeyManager usage: " + TesterSupport.getLastClientAuthKeyManagerUsage() +
                      ", " + count + " requested Issuers, first one: " +
                      (count > 0 ? TesterSupport.getLastClientAuthRequestedIssuer(0).getName() : "NONE"));
            log.debug("Expected requested Issuer: NONE");
        }

        // Unprotected resource with no preemptive authentication
        Assert.assertEquals(0, count);
        // No authentication no need to buffer POST body during TLS handshake so
        // no possibility of hitting buffer limit
        Assert.assertEquals("OK-" + bodySize, res.toString());

        // Protected resource
        res.recycle();
        int rc = postUrl(body, "https://localhost:" + getPort() + "/protected", res, null);

        count = TesterSupport.getLastClientAuthRequestedIssuerCount();
        if (log.isDebugEnabled()) {
            log.debug("Last client KeyManager usage: " + TesterSupport.getLastClientAuthKeyManagerUsage() +
                      ", " + count + " requested Issuers, first one: " +
                      (count > 0 ? TesterSupport.getLastClientAuthRequestedIssuer(0).getName() : "NONE"));
            log.debug("Expected requested Issuer: " + TesterSupport.getClientAuthExpectedIssuer());
        }

        if (expectProtectedFail) {
            Assert.assertEquals(401, rc);
            // POST body buffer fails so TLS handshake never happens
            Assert.assertEquals(0, count);
        } else {
            int expectedBodySize;
            if (tomcat.getConnector().getMaxSavePostSize() == 0) {
                expectedBodySize = 0;
            } else {
                expectedBodySize = bodySize;
            }
            Assert.assertTrue("Checking requested client issuer against " +
                    TesterSupport.getClientAuthExpectedIssuer(),
                    TesterSupport.checkLastClientAuthRequestedIssuers());
            Assert.assertEquals("OK-" + expectedBodySize, res.toString());
        }
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();

        Tomcat tomcat = getTomcatInstance();

        TesterSupport.configureClientCertContext(tomcat);

        TesterSupport.configureClientSsl();

        TesterSupport.configureSSLImplementation(tomcat, sslImplementationName);

        if (needApr) {
            AprLifecycleListener listener = new AprLifecycleListener();
            Assume.assumeTrue(AprLifecycleListener.isAprAvailable());
            StandardServer server = (StandardServer) tomcat.getServer();
            server.addLifecycleListener(listener);
        }
    }
}
