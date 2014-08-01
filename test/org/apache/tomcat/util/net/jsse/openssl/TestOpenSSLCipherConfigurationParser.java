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
package org.apache.tomcat.util.net.jsse.openssl;

import java.util.List;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

public class TestOpenSSLCipherConfigurationParser {

    @Before
    public void checkVersion() {
        Assume.assumeTrue(TesterOpenSSL.IS_EXPECTED_VERSION);
    }

    @Test
    public void testALL() throws Exception {
        testSpecification("ALL");
    }


    @Test
    public void testaNULL() throws Exception {
        testSpecification("aNULL");
    }


    @Test
    public void testeNULL() throws Exception {
        testSpecification("eNULL");
    }


    @Test
    public void testHIGH() throws Exception {
        testSpecification("HIGH");
    }


    @Test
    public void testMEDIUM() throws Exception {
        testSpecification("MEDIUM");
    }


    @Test
    public void testLOW() throws Exception {
        testSpecification("LOW");
    }


    @Test
    public void testEXPORT40() throws Exception {
        testSpecification("EXPORT40");
    }


    @Test
    public void testEXPORT() throws Exception {
        testSpecification("EXPORT");
    }


    @Test
    public void testRSA() throws Exception {
        testSpecification("RSA");
    }


    @Test
    public void testaRSA() throws Exception {
        testSpecification("aRSA");
    }


    @Test
    public void testkRSA() throws Exception {
        testSpecification("kRSA");
    }


    @Test
    public void testkEDH() throws Exception {
        testSpecification("kEDH");
    }


    @Test
    @Ignore("Contrary to the docs, OpenSSL does not recognise kDHE")
    public void testkDHE() throws Exception {
        testSpecification("kDHE");
    }


    @Test
    public void testEDH() throws Exception {
        testSpecification("EDH");
    }


    @Test
    @Ignore("Contrary to the docs, OpenSSL does not recognise DHE")
    public void testDHE() throws Exception {
        testSpecification("DHE");
    }


    @Test
    public void testkDHr() throws Exception {
        testSpecification("kDHr");
    }


    @Test
    public void testkDHd() throws Exception {
        testSpecification("kDHd");
    }


    @Test
    public void testkDH() throws Exception {
        testSpecification("kDH");
    }


    @Test
    public void testkECDHr() throws Exception {
        testSpecification("kECDHr");
    }


    @Test
    public void testkECDHe() throws Exception {
        testSpecification("kECDHe");
    }


    @Test
    public void testkECDH() throws Exception {
        testSpecification("kECDH");
    }


    @Test
    public void testkEECDH() throws Exception {
        testSpecification("kEECDH");
    }


    @Test
    public void testECDH() throws Exception {
        testSpecification("ECDH");
    }


    @Test
    @Ignore("Contrary to the docs, OpenSSL does not recognise kECDHE")
    public void testkECDHE() throws Exception {
        testSpecification("kECDHE");
    }


    @Test
    @Ignore("Contrary to the docs, OpenSSL does not recognise kECDHE")
    public void testECDHE() throws Exception {
        testSpecification("ECDHE");
    }


    @Test
    @Ignore("Contrary to the docs, OpenSSL does not recognise kECDHE")
    public void testEECDHE() throws Exception {
        testSpecification("EECDHE");
    }


    @Test
    public void testAECDH() throws Exception {
        testSpecification("AECDH");
    }


    @Test
    public void testaDSS() throws Exception {
        testSpecification("aDSS");
    }


    @Test
    public void testaDH() throws Exception {
        testSpecification("aDH");
    }


    @Test
    public void testaECDH() throws Exception {
        testSpecification("aECDH");
    }


    @Test
    public void testaECDSA() throws Exception {
        testSpecification("aECDSA");
    }


    @Test
    public void testECDSA() throws Exception {
        testSpecification("ECDSA");
    }


    private void testSpecification(String specification) throws Exception {
        // Filter out cipher suites that OpenSSL does not implement
        String parserSpecification = "" + specification;
        String openSSLCipherList = TesterOpenSSL.getOpenSSLCiphersAsExpression(specification);
        List<String> jsseCipherListFromOpenSSL =
                OpenSSLCipherConfigurationParser.parseExpression(openSSLCipherList);
        List<String> jsseCipherListFromParser =
                OpenSSLCipherConfigurationParser.parseExpression(parserSpecification);

        TesterOpenSSL.removeUnimplementedCiphersJsse(jsseCipherListFromParser);

        Assert.assertEquals(listToString(jsseCipherListFromOpenSSL), listToString(jsseCipherListFromParser));
    }


    private String listToString(List<String> list) {
        StringBuilder sb = new StringBuilder();
        for (String entry : list) {
            sb.append(entry);
            sb.append(',');
        }
        return sb.toString();
    }
}
