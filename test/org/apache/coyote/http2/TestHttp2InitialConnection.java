/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.coyote.http2;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.util.ServerInfo;
import org.apache.catalina.valves.ErrorReportValve;
import org.apache.tomcat.util.res.StringManager;

public class TestHttp2InitialConnection extends Http2TestBase {

    private TestData testData;


    @Test
    public void testValidHostHeader() throws Exception {
        List<String> hostHeaders = new ArrayList<>(1);
        hostHeaders.add("localhost:8080");

        testData = new TestData(hostHeaders, 200);

        http2Connect();
    }


    @Test
    public void testMultipleHostHeaders() throws Exception {
        List<String> hostHeaders = new ArrayList<>(1);
        hostHeaders.add("localhost:8080");
        hostHeaders.add("localhost:8081");

        testData = new TestData(hostHeaders, 400);

        http2Connect();
    }


    @Test
    public void testNoHostHeader() throws Exception {
        List<String> hostHeaders = new ArrayList<>(1);

        testData = new TestData(hostHeaders, 400);
        http2Connect();
    }


    @Override
    protected void doHttpUpgrade(String connection, String upgrade, String settings,
            boolean validate) throws IOException {
        StringBuilder request = new StringBuilder();
        request.append("GET /simple HTTP/1.1\r\n");
        for (String hostHeader : testData.getHostHeaders()) {
            request.append("Host: ");
            request.append(hostHeader);
            request.append("\r\n");
        }
        // Connection
        request.append("Connection: ");
        request.append(connection);
        request.append("\r\n");
        // Upgrade
        request.append("Upgrade: ");
        request.append(upgrade);
        request.append("\r\n");
        // Settings
        request.append(settings);
        // Locale - Force the en Locale else the i18n on the error page changes
        // the size of the response body and that triggers a failure as the test
        // checks the exact response length
        request.append("Accept-Language: en\r\n");
        // Request terminator
        request.append("\r\n");

        byte[] upgradeRequest = request.toString().getBytes(StandardCharsets.ISO_8859_1);
        os.write(upgradeRequest);
        os.flush();

        if (validate) {
            Assert.assertTrue("Failed to read HTTP Upgrade response",
                    readHttpUpgradeResponse());
        }
    }


    @Override
    protected String getResponseBodyFrameTrace(int streamId, String body) {
        if (testData.getExpectedStatus() == 200) {
            return super.getResponseBodyFrameTrace(streamId, body);
        } else if (testData.getExpectedStatus() == 400) {
            /*
             * Need to be careful here. The test wants the exact content length
             * in bytes.
             * This will vary depending on where the test is run due to:
             * - The length of the version string that appears once in the error
             *   page
             * - The status header uses a UTF-8 EN dash. When running in an IDE
             *   the UTF-8 properties files will be used directly rather than
             *   after native2ascii conversion.
             *
             * Note: The status header appears twice in the error page.
             */
            int serverInfoLength = ServerInfo.getServerInfo().getBytes().length;
            StringManager sm = StringManager.getManager(
                    ErrorReportValve.class.getPackage().getName(), Locale.ENGLISH);
            String reason = sm.getString("http." + testData.getExpectedStatus() + ".reason");
            int descriptionLength = sm.getString("http." + testData.getExpectedStatus() + ".desc")
                    .getBytes(StandardCharsets.UTF_8).length;
            int statusHeaderLength = sm
                    .getString("errorReportValve.statusHeader",
                            String.valueOf(testData.getExpectedStatus()), reason)
                    .getBytes(StandardCharsets.UTF_8).length;
            int typeLabelLength = sm.getString("errorReportValve.type")
                    .getBytes(StandardCharsets.UTF_8).length;
            int statusReportLabelLength = sm.getString("errorReportValve.statusReport")
                    .getBytes(StandardCharsets.UTF_8).length;
            int descriptionLabelLength = sm.getString("errorReportValve.description")
                    .getBytes(StandardCharsets.UTF_8).length;
            // 196 bytes is the static length of the pure HTML code from the ErrorReportValve
            int len = 196 + org.apache.catalina.util.TomcatCSS.TOMCAT_CSS
                    .getBytes(StandardCharsets.UTF_8).length +
                    typeLabelLength + statusReportLabelLength + descriptionLabelLength +
                    descriptionLength + serverInfoLength + statusHeaderLength * 2;
            String contentLength = String.valueOf(len);
            return getResponseBodyFrameTrace(streamId,
                    testData.getExpectedStatus(), "text/html;charset=utf-8",
                    "en", contentLength, contentLength);
        } else {
            Assert.fail();
            // To keep the IDE happy
            return null;
        }
    }


    private static class TestData {
        private final List<String> hostHeaders;
        private final int expectedStatus;

        public TestData(List<String> hostHeaders, int expectedStatus) {
            this.hostHeaders = hostHeaders;
            this.expectedStatus = expectedStatus;
        }

        public List<String> getHostHeaders() {
            return hostHeaders;
        }

        public int getExpectedStatus() {
            return expectedStatus;
        }
    }
}
