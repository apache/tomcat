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
package org.apache.tomcat.util.net.ocsp;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.net.ssl.SSLHandshakeException;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class TestOcspEnabled extends OcspBaseTest {

    private static TesterOcspResponder ocspResponder;

    @BeforeClass
    public static void startOcspResponder() throws IOException {
        ocspResponder = new TesterOcspResponder();
        ocspResponder.start();
    }


    @AfterClass
    public static void stopOcspResponder() {
        ocspResponder.stop();
        ocspResponder = null;
    }


    @Parameters(name = "{0} with OpenSSL trust {2}: clientOk {4}, serverOk {5}, verifyClient {6}, verifyServer {7}")
    public static Collection<Object[]> parameters() {
        List<Object[]> parameterSets = new ArrayList<>();
        Collection<Object[]> baseData = OcspBaseTest.parameters();

        for (Object[] base : baseData) {
            for (Boolean clientCertValid : booleans) {
                for (Boolean serverCertValid : booleans) {
                    for (ClientCertificateVerification verifyClientCert : ClientCertificateVerification.values()) {
                        boolean useOpenSSLTrust = ((Boolean) base[2]).booleanValue();
                        if (verifyClientCert == ClientCertificateVerification.OPTIONAL_NO_CA && !useOpenSSLTrust ||
                                verifyClientCert == ClientCertificateVerification.ENABLED && !clientCertValid.booleanValue()) {
                            continue;
                        }
                        for (Boolean verifyServerCert : booleans) {
                            Boolean handshakeFailureExpected;
                            if (!serverCertValid.booleanValue() && verifyServerCert.booleanValue()) {
                                handshakeFailureExpected = Boolean.TRUE;
                            } else {
                                handshakeFailureExpected = Boolean.FALSE;
                            }
                            parameterSets.add(new Object[] { base[0], base[1], base[2], base[3], clientCertValid,
                                    serverCertValid, verifyClientCert, verifyServerCert, handshakeFailureExpected});
                        }
                    }
                }
            }
        }
        return parameterSets;
    }

    @Parameter(4)
    public boolean clientCertValid;

    @Parameter(5)
    public boolean serverCertValid;

    @Parameter(6)
    public ClientCertificateVerification verifyClientCert;

    @Parameter(7)
    public boolean verifyServerCert;

    @Parameter(8)
    public boolean handshakeFailureExpected;

    @Test
    public void test() throws Exception {
        try {
            doTest(clientCertValid, serverCertValid, verifyClientCert, verifyServerCert);
            if (handshakeFailureExpected) {
                Assert.fail("Handshake did not fail when expected to do so.");
            }
        } catch (SSLHandshakeException e) {
            if (!handshakeFailureExpected) {
                Assert.fail("Handshake failed when not expected to do so.");
            }
        }
    }
}
