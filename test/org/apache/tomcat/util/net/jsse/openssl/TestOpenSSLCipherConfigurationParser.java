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
import org.junit.Test;

public class TestOpenSSLCipherConfigurationParser {

    @Test
    public void testENull() throws Exception {
        testSpecification("eNULL");
    }


    @Test
    public void testANull() throws Exception {
        testSpecification("aNULL");
    }


    @Test
    public void testHigh() throws Exception {
        testSpecification("HIGH");
    }


    @Test
    public void testMedium() throws Exception {
        testSpecification("MEDIUM");
    }


    @Test
    public void testLow() throws Exception {
        testSpecification("LOW");
    }


    @Test
    public void testExport40() throws Exception {
        testSpecification("EXPORT40");
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
