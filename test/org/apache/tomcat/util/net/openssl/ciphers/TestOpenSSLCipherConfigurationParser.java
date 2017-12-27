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
package org.apache.tomcat.util.net.openssl.ciphers;

import java.util.List;
import java.util.TreeSet;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

public class TestOpenSSLCipherConfigurationParser {

    @Test
    public void testDEFAULT() throws Exception {
        if (TesterOpenSSL.VERSION < 10100) {
            // Account for classes of ciphers removed from DEFAULT in 1.1.0
            testSpecification("DEFAULT:!RC4:!DSS:!SEED:!IDEA:!CAMELLIA:!AESCCM:!3DES");
        } else {
            testSpecification("DEFAULT");
        }
    }


    @Test
    public void testCOMPLEMENTOFDEFAULT() throws Exception {
        if (TesterOpenSSL.VERSION < 10100) {
            // Account for classes of ciphers removed from DEFAULT in 1.1.0
            testSpecification("COMPLEMENTOFDEFAULT:RC4:DSS:SEED:IDEA:CAMELLIA:AESCCM:aNULL:3DES");
        } else {
            testSpecification("COMPLEMENTOFDEFAULT");
        }
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
        // This alias was introduced in 1.0.2
        if (TesterOpenSSL.VERSION >= 10002) {
            testSpecification("kDHE");
        }
    }


    @Test
    public void testEDH() throws Exception {
        testSpecification("EDH");
    }


    @Test
    public void testDHE() throws Exception {
        // This alias was introduced in 1.0.2
        if (TesterOpenSSL.VERSION >= 10002) {
            testSpecification("DHE");
        }
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
    public void testkECDHE() throws Exception {
        testSpecification("kECDHE");
    }


    @Test
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
    public void testDSS() throws Exception {
        testSpecification("DSS");
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
        // In OpenSSL 1.1.0-dev, TLSv1 refers to those ciphers that require
        // TLSv1 rather than being an alias for SSLv3
        if (TesterOpenSSL.VERSION >= 10100) {
            testSpecification("TLSv1");
        }
    }


    @Test
    public void testSSLv2() throws Exception {
        testSpecification("SSLv2");
    }


    @Test
    public void testSSLv3() throws Exception {
        // In OpenSSL 1.1.0-dev, TLSv1 refers to those ciphers that require
        // TLSv1 rather than being an alias for SSLv3
        if (TesterOpenSSL.VERSION < 10100) {
            testSpecification("SSLv3:TLSv1");
        }
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
    public void testAESCCM() throws Exception {
        testSpecification("AESCCM");
    }


    @Test
    public void testAESCCM8() throws Exception {
        testSpecification("AESCCM8");
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
    public void testCHACHA20() throws Exception {
        testSpecification("CHACHA20");
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
    public void testaPSK() throws Exception {
        testSpecification("aPSK");
    }


    @Test
    public void testkPSK() throws Exception {
        testSpecification("kPSK");
    }


    @Test
    public void testkRSAPSK() throws Exception {
        testSpecification("kRSAPSK");
    }


    @Test
    public void testkECDHEPSK() throws Exception {
        testSpecification("kECDHEPSK");
    }


    @Test
    public void testkDHEPSK() throws Exception {
        testSpecification("kDHEPSK");
    }


    @Test
    public void testPSK() throws Exception {
        testSpecification("PSK");
    }


    @Test
    public void testARIA() throws Exception {
        testSpecification("ARIA");
    }


    @Test
    public void testARIA128() throws Exception {
        testSpecification("ARIA128");
    }


    @Test
    public void testARIA256() throws Exception {
        testSpecification("ARIA256");
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
        testSpecification("!aNULL:!eNULL:!EXPORT:!DSS:!DES:!SSLv2:kEECDH:ECDH:EDH:AES256-GCM-SHA384:AES128-GCM-SHA256:+RC4:HIGH:aRSA:kECDHr:MEDIUM");
    }


    @Test
    public void testSpecification03() throws Exception {
        // Reported as failing during 8.0.11 release vote by Ognjen Blagojevic
        // EDH was introduced in 1.0.0
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

        // First check the lists have the same entries
        // Order is NOT important at this point. It is checked below.
        Assert.assertEquals(
                "Expected " + jsseCipherListFromParser.size() + " ciphers but got "
                        + jsseCipherListFromOpenSSL.size() + " for the specification '"
                        + specification + "'",
                new TreeSet<>(jsseCipherListFromParser), new TreeSet<>(jsseCipherListFromOpenSSL));

        // OpenSSL treats many ciphers as having equal preference. The order
        // returned depends on the order they are requested. The following code
        // checks that the Parser produces a cipher list that is consistent with
        // OpenSSL's preference order by confirming that running through OpenSSL
        // does not change the order.
        String parserOrderedExpression = listToString(jsseCipherListFromParser, ',');
        Assert.assertEquals(
                listToString(OpenSSLCipherConfigurationParser.parseExpression(
                        parserOrderedExpression), ','),
                parserOrderedExpression);
    }


    private String listToString(List<String> list, char separator) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (String entry : list) {
            if (first) {
                first = false;
            } else {
                sb.append(separator);
            }
            sb.append(entry);
        }
        return sb.toString();
    }
}
