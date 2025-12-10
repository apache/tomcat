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

package org.apache.tomcat.security;

import javax.net.ssl.SSLHandshakeException;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.net.ocsp.TestOcspIntegration;

public class TestSecurity2017 extends TomcatBaseTest {
    /*
     * https://www.cve.org/CVERecord?id=CVE-2017-15698
     *
     * Fixed in Tomcat Native
     * 1.2.16  https://github.com/apache/tomcat-native/commit/4582e6d9223da618b42db6e992bb2d55d9cd4c42
     *
     * Changes currently in Tomcat Native 2.0.x expand the OCSP checks to include the date the OCSP response was
     * generated. This causes this test to fail as the OCSP responder uses canned responses.
     *
     * A new version of this test has been written and will replace this test once a version of Tomcat Native is
     * released that includes all the OCSP changes.
     */
    @Test
    @Ignore
    public void testCVE_2017_15698() throws Exception {
        try {
            TestOcspIntegration.testLongUrlForOcspViaAIAWithTomcatNative(getTomcatInstance());
        } catch (SSLHandshakeException sslHandshakeException) {
            Assert.assertTrue(sslHandshakeException.toString().contains("certificate_revoked"));
        }
    }
}
