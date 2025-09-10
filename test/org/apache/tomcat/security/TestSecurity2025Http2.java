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

import java.io.IOException;

import org.junit.Assert;
import org.junit.Test;

import org.apache.coyote.http2.Http2TestBase;

public class TestSecurity2025Http2 extends Http2TestBase {

    /*
     * http://cve.mitre.org/cgi-bin/cvename.cgi?name=CVE-2025-53506
     *
     * Fixed in
     * 11.0.9  https://github.com/apache/tomcat/commit/be8f330f83ceddaf3baeed57522e571572b6b99b
     * 10.1.43 https://github.com/apache/tomcat/commit/2aa6261276ebe50b99276953591e3a2be7898bdb
     * 9.0.107 https://github.com/apache/tomcat/commit/434772930f362145516dd60681134e7f0cf8115b
     */
    @Test
    public void testCVE_2025_53506() throws Exception {
        enableHttp2(100);
        configureAndStartWebApplication();
        openClientConnection(false, false);
        doHttpUpgrade();
        sendClientPreface();
        validateHttp2InitialResponse(100);

        int streamId = 3;
        Throwable t = null;
        try {
            /*
             * Note: The client will create streams and send requests faster than Tomcat can process them so the
             * concurrent stream count will be well above 100 by the time the client sees the exception. However,
             * Tomcat will only have processed the first 100.
             */
            while (true) {
                sendSimpleGetRequest(streamId);
                streamId += 2;
            }
        } catch (IOException ioe) {
            t = ioe;
        }
        Assert.assertNotNull(t);
    }
}
