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

import javax.net.ssl.SSLHandshakeException;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

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


    @Test
    public void testRevokedClientRevokedServerVerifyNone() throws Exception {
        doTest(false, false, ClientCertificateVerification.DISABLED, false);
    }

    @Test
    public void testRevokedClientRevokedServerVerifyClientDefault() throws Exception {
        doTest(false, false, ClientCertificateVerification.DEFAULT, false);
    }

    @Test(expected = SSLHandshakeException.class)
    public void testRevokedClientRevokedServerVerifyServer() throws Exception {
        doTest(false, false, ClientCertificateVerification.DISABLED, true);
    }

    @Test
    public void testRevokedClientRevokedServerVerifyClientOptional() throws Exception {
        doTest(false, false, ClientCertificateVerification.OPTIONAL_NO_CA, false);
    }

    @Test(expected = SSLHandshakeException.class)
    public void testRevokedClientRevokedServerVerifyClientOpionalVerifyServer() throws Exception {
        // Same as false, false, false, true since server certificate is verified before client certificate
        doTest(false, false, ClientCertificateVerification.OPTIONAL_NO_CA, true);
    }

    @Test(expected = SSLHandshakeException.class)
    public void testRevokedClientRevokedServerVerifyClient() throws Exception {
        doTest(false, false, ClientCertificateVerification.ENABLED, false);
    }

    @Test(expected = SSLHandshakeException.class)
    public void testRevokedClientRevokedServerVerifyBoth() throws Exception {
        // Same as false, false, false, true since server certificate is verified before client certificate
        doTest(false, false, ClientCertificateVerification.ENABLED, true);
    }

    @Test
    public void testRevokedClientValidServerVerifyNone() throws Exception {
        doTest(false, true, ClientCertificateVerification.DISABLED, false);
    }

    @Test
    public void testRevokedClientValidServerVerifyServer() throws Exception {
        doTest(false, true, ClientCertificateVerification.DISABLED, true);
    }

    @Test
    public void testRevokedClientValidServerVerifyClientOptional() throws Exception {
        doTest(false, true, ClientCertificateVerification.OPTIONAL_NO_CA, false);
    }

    @Test
    public void testRevokedClientValidServerVerifyClientOptionalVerifyServer() throws Exception {
        doTest(false, true, ClientCertificateVerification.OPTIONAL_NO_CA, true);
    }

    @Test(expected = SSLHandshakeException.class)
    public void testRevokedClientValidServerVerifyClient() throws Exception {
        doTest(false, true, ClientCertificateVerification.ENABLED, false);
    }

    @Test(expected = SSLHandshakeException.class)
    public void testRevokedClientValidServerVerifyBoth() throws Exception {
        doTest(false, true, ClientCertificateVerification.ENABLED, true);
    }

    @Test
    public void testValidClientRevokedServerVerifyNone() throws Exception {
        doTest(true, false, ClientCertificateVerification.DISABLED, false);
    }

    @Test(expected = SSLHandshakeException.class)
    public void testValidClientRevokedServerVerifyServer() throws Exception {
        doTest(true, false, ClientCertificateVerification.DISABLED, true);
    }

    @Test
    public void testValidClientRevokedServerVerifyClientOptional() throws Exception {
        doTest(true, false, ClientCertificateVerification.OPTIONAL_NO_CA, false);
    }

    @Test(expected = SSLHandshakeException.class)
    public void testValidClientRevokedServerVerifyClientOptionalVerifyServer() throws Exception {
        doTest(true, false, ClientCertificateVerification.OPTIONAL_NO_CA, true);
    }

    @Test
    public void testValidClientRevokedServerVerifyClient() throws Exception {
        doTest(true, false, ClientCertificateVerification.ENABLED, false);
    }

    @Test(expected = SSLHandshakeException.class)
    public void testValidClientRevokedServerVerifyBoth() throws Exception {
        doTest(true, false, ClientCertificateVerification.ENABLED, true);
    }

    @Test
    public void testValidClientValidServerVerifyNone() throws Exception {
        doTest(true, true, ClientCertificateVerification.DISABLED, false);
    }

    @Test
    public void testValidClientValidServerVerifyServer() throws Exception {
        doTest(true, true, ClientCertificateVerification.DISABLED, true);
    }

    @Test
    public void testValidClientValidServerVerifyClientOptional() throws Exception {
        doTest(true, true, ClientCertificateVerification.OPTIONAL_NO_CA, false);
    }

    @Test
    public void testValidClientValidServerVerifyClientOptionalVerifyServer() throws Exception {
        doTest(true, true, ClientCertificateVerification.OPTIONAL_NO_CA, true);
    }

    @Test
    public void testValidClientValidServerVerifyClient() throws Exception {
        doTest(true, true, ClientCertificateVerification.ENABLED, false);
    }

    @Test
    public void testValidClientValidServerVerifyBoth() throws Exception {
        doTest(true, true, ClientCertificateVerification.ENABLED, true);
    }
}
