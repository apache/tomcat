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
import java.net.SocketTimeoutException;

import javax.net.ssl.SSLHandshakeException;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/*
 * The timeout for reading an OCSP response is 15s by default for both JSSE and OpenSSL.
 */
@RunWith(Parameterized.class)
public class TestOcspTimeout extends OcspBaseTest {

    private static TesterOcspResponderNoResponse ocspResponder;

    @BeforeClass
    public static void startOcspResponder() {
        /*
         * Use shorter timeout to speed up test.
         *
         * Note: OpenSSL timeout set later as it requires access to SSLHostConfig.
         */
        System.setProperty("com.sun.security.ocsp.readtimeout", "1000ms");
        ocspResponder = new TesterOcspResponderNoResponse();
        ocspResponder.start();
    }


    @AfterClass
    public static void stopOcspResponder() {
        ocspResponder.stop();
        ocspResponder = null;
    }


    @Test
    public void testTimeoutWithSoftFail() throws Exception {
        doTest(false, false, ClientCertificateVerification.ENABLED, false, Boolean.TRUE);
    }


    @Test(expected = SSLHandshakeException.class)
    public void testTimeoutWithoutSoftFail() throws Exception {
        try {
            doTest(false, false, ClientCertificateVerification.ENABLED, false, Boolean.FALSE);
        } catch (SocketTimeoutException | SocketException e) {
            // May throw a SocketTimeoutException or SocketException rather than a SSLHandshakeException
            throw new SSLHandshakeException(e.getMessage());
        }
    }
}
