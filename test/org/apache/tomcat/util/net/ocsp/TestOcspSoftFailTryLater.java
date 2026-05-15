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

import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.net.ssl.SSLHandshakeException;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import org.apache.tomcat.util.net.ocsp.TesterOcspResponder.OcspResponse;

@RunWith(Parameterized.class)
public class TestOcspSoftFailTryLater extends OcspBaseTest {

    private static TesterOcspResponder ocspResponder;

    @BeforeClass
    public static void startOcspResponder() {
        ocspResponder = new TesterOcspResponder();
        ocspResponder.setFixedResponse(OcspResponse.TRY_LATER);
        try {
            ocspResponder.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    @AfterClass
    public static void stopOcspResponder() {
        if (ocspResponder != null) {
            ocspResponder.stop();
            ocspResponder = null;
        }
    }


    @Parameters(name = "{0} with OpenSSL trust {2}: softFail {4}, clientOk {5}")
    public static Collection<Object[]> parameters() {
        List<Object[]> parameterSets = new ArrayList<>();
        Collection<Object[]> baseData = OcspBaseTest.parameters();

        for (Object[] base : baseData) {
            for (Boolean softFail : booleans) {
                for (Boolean clientCertValid : booleans) {
                    Boolean handshakeFailureExpected;

                    if (softFail.booleanValue()) {
                        handshakeFailureExpected = Boolean.FALSE;
                    } else {
                        handshakeFailureExpected = Boolean.TRUE;
                    }

                    parameterSets.add(new Object[] { base[0], base[1], base[2], base[3], softFail, clientCertValid,
                            handshakeFailureExpected});
                }
            }
        }
        return parameterSets;
    }

    @Parameter(4)
    public Boolean softFail;

    @Parameter(5)
    public boolean clientCertValid;

    @Parameter(6)
    public boolean handshakeFailureExpected;

    @Test
    public void test() throws Exception {
        Assume.assumeNotNull(ocspResponder);
        try {
            doTest(clientCertValid, true, ClientCertificateVerification.ENABLED, false, softFail);
            if (handshakeFailureExpected) {
                Assert.fail("Handshake did not fail when expected to do so.");
            }
        } catch (SSLHandshakeException | SocketException e) {
            if (!handshakeFailureExpected) {
                Assert.fail("Handshake failed when not expected to do so.");
            }
        }
    }
}
