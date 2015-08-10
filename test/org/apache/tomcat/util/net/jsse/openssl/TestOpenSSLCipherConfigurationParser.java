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
import org.junit.Ignore;
import org.junit.Test;

public class TestOpenSSLCipherConfigurationParser {

    @Test
    public void testDEFAULT() throws Exception {
        testSpecification("DEFAULT");
    }


    @Test
    public void testCOMPLEMENTOFDEFAULT() throws Exception {
        testSpecification("COMPLEMENTOFDEFAULT");
    }


    @Test
    public void testALL() throws Exception {
        testSpecification("ALL");
    }


    @Test
    public void testCOMPLEMENTOFALL() throws Exception {
        testSpecification("COMPLEMENTOFALL");
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
    public void testkDHE() throws Exception {
        testSpecification("kDHE");
    }


    @Test
    public void testEDH() throws Exception {
        testSpecification("EDH");
    }


    @Test
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
    @Ignore("Contrary to the docs, OpenSSL does not recognise ECDHE")
    public void testECDHE() throws Exception {
        testSpecification("ECDHE");
    }


    @Test
    @Ignore("Contrary to the docs, OpenSSL does not recognise EECDHE")
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


    @Test
    public void testkFZA() throws Exception {
        testSpecification("kFZA");
    }


    @Test
    public void testaFZA() throws Exception {
        testSpecification("aFZA");
    }


    @Test
    public void testeFZA() throws Exception {
        testSpecification("eFZA");
    }


    @Test
    public void testFZA() throws Exception {
        testSpecification("FZA");
    }


    @Test
    public void testTLSv1_2() throws Exception {
        testSpecification("TLSv1.2");
    }


    @Test
    public void testTLSv1() throws Exception {
        testSpecification("TLSv1");
    }


    @Test
    public void testSSLv2() throws Exception {
        testSpecification("SSLv2");
    }


    @Test
    public void testSSLv3() throws Exception {
        testSpecification("SSLv3");
    }


    @Test
    public void testDH() throws Exception {
        testSpecification("DH");
    }


    @Test
    public void testADH() throws Exception {
        testSpecification("ADH");
    }


    @Test
    public void testAES128() throws Exception {
        testSpecification("AES128");
    }


    @Test
    public void testAES256() throws Exception {
        testSpecification("AES256");
    }


    @Test
    public void testAES() throws Exception {
        testSpecification("AES");
    }


    @Test
    public void testAESGCM() throws Exception {
        testSpecification("AESGCM");
    }


    @Test
    public void testCAMELLIA128() throws Exception {
        testSpecification("CAMELLIA128");
    }


    @Test
    public void testCAMELLIA256() throws Exception {
        testSpecification("CAMELLIA256");
    }


    @Test
    public void testCAMELLIA() throws Exception {
        testSpecification("CAMELLIA");
    }


    @Test
    public void test3DES() throws Exception {
        testSpecification("3DES");
    }


    @Test
    public void testDES() throws Exception {
        testSpecification("DES");
    }


    @Test
    public void testRC4() throws Exception {
        testSpecification("RC4");
    }


    @Test
    public void testRC2() throws Exception {
        testSpecification("RC2");
    }


    @Test
    public void testIDEA() throws Exception {
        testSpecification("IDEA");
    }


    @Test
    public void testSEED() throws Exception {
        testSpecification("SEED");
    }


    @Test
    public void testMD5() throws Exception {
        testSpecification("MD5");
    }


    @Test
    public void testSHA1() throws Exception {
        testSpecification("SHA1");
    }


    @Test
    public void testSHA() throws Exception {
        testSpecification("SHA");
    }


    @Test
    public void testSHA256() throws Exception {
        testSpecification("SHA256");
    }


    @Test
    public void testSHA384() throws Exception {
        testSpecification("SHA384");
    }


    @Test
    public void testKRB5() throws Exception {
        testSpecification("KRB5");
    }


    @Test
    public void testaGOST() throws Exception {
        testSpecification("aGOST");
    }


    @Test
    public void testaGOST01() throws Exception {
        testSpecification("aGOST01");
    }


    @Test
    public void testaGOST94() throws Exception {
        testSpecification("aGOST94");
    }


    @Test
    public void testkGOST() throws Exception {
        testSpecification("kGOST");
    }


    @Test
    public void testGOST94() throws Exception {
        testSpecification("GOST94");
    }


    @Test
    public void testGOST89MAC() throws Exception {
        testSpecification("GOST89MAC");
    }


    @Test
    public void testPSK() throws Exception {
        testSpecification("PSK");
    }


    // TODO: Add tests for the individual operators

    @Test
    public void testSpecification01() throws Exception {
        // Tomcat 8 default as of 2014-08-04
        // This gets an A- from https://www.ssllabs.com/ssltest with no FS for
        // a number of the reference browsers
        testSpecification("HIGH:!aNULL:!eNULL:!EXPORT:!DES:!RC4:!MD5");
    }


    @Test
    public void testSpecification02() throws Exception {
        // Suggestion from dev list (s/ECDHE/kEECDH/, s/DHE/EDH/
        testSpecification("!aNULL:!eNULL:!EXPORT:!DSS:!DES:!SSLv2:kEECDH:ECDH:EDH:AES256-GCM-SHA384:AES128-GCM-SHA256:+RC4:HIGH:MEDIUM");
    }


    @Test
    public void testSpecification03() throws Exception {
        // Reported as failing during 8.0.11 release vote by Ognjen Blagojevic
        testSpecification("EECDH+aRSA+SHA384:EECDH:EDH+aRSA:RC4:!aNULL:!eNULL:!LOW:!3DES:!MD5:!EXP:!PSK:!SRP:!DSS");
    }


    private void testSpecification(String specification) throws Exception {
        // Filter out cipher suites that OpenSSL does not implement
        String openSSLCipherList = TesterOpenSSL.getOpenSSLCiphersAsExpression(specification);
        List<String> jsseCipherListFromOpenSSL =
                OpenSSLCipherConfigurationParser.parseExpression(openSSLCipherList);
        List<String> jsseCipherListFromParser =
                OpenSSLCipherConfigurationParser.parseExpression(specification);

        TesterOpenSSL.removeUnimplementedCiphersJsse(jsseCipherListFromParser);

        Assert.assertEquals("Tested '" + specification + "': ",
                            listToString(jsseCipherListFromOpenSSL), listToString(jsseCipherListFromParser));
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
