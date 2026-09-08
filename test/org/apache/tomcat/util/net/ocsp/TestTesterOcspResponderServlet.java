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
import java.io.StringReader;
import java.math.BigInteger;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

import org.apache.tomcat.util.net.ocsp.TesterOcspResponderServlet.CertificateState;

public class TestTesterOcspResponderServlet {

    @Test
    public void testLoadCertificateStatuses() throws IOException {
        String input = "V\t280811131918Z\t\t1000\tunknown\t/CN=valid\n" +
                "R\t280811131918Z\t260812131922Z\t1001\tunknown\t/CN=revoked\n" +
                "E\t250811131918Z\t\t1002\tunknown\t/CN=expired\n";

        Map<BigInteger,CertificateState> statuses =
                TesterOcspResponderServlet.loadCertificateStatuses(new StringReader(input));

        Assert.assertEquals(2, statuses.size());
        Assert.assertSame(CertificateState.GOOD, statuses.get(new BigInteger("1000", 16)));
        Assert.assertSame(CertificateState.REVOKED, statuses.get(new BigInteger("1001", 16)));
        Assert.assertNull(statuses.get(new BigInteger("1002", 16)));
    }
}
