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
package org.apache.catalina.servlets;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;

import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.buf.ByteChunk;

@RunWith(Parameterized.class)
public class TestDefaultServletRangeRequests extends TomcatBaseTest {

    @Parameterized.Parameters(name = "{index} rangeHeader [{0}]")
    public static Collection<Object[]> parameters() {

        // Get the length of the file used for this test
        // It varies by platform due to line-endings
        File index = new File("test/webapp/index.html");
        long len = index.length();
        String strLen = Long.toString(len);

        List<Object[]> parameterSets = new ArrayList<>();

        parameterSets.add(new Object[] { "", Integer.valueOf(200), strLen, "" });
        // Invalid
        parameterSets.add(new Object[] { "bytes", Integer.valueOf(416), "", "*/" + len });
        parameterSets.add(new Object[] { "bytes=", Integer.valueOf(416), "", "*/" + len });
        // Invalid with unknown type
        parameterSets.add(new Object[] { "unknown", Integer.valueOf(416), "", "*/" + len });
        parameterSets.add(new Object[] { "unknown=", Integer.valueOf(416), "", "*/" + len });
        // Invalid ranges
        parameterSets.add(new Object[] { "bytes=-", Integer.valueOf(416), "", "*/" + len });
        parameterSets.add(new Object[] { "bytes=10-b", Integer.valueOf(416), "", "*/" + len });
        parameterSets.add(new Object[] { "bytes=b-10", Integer.valueOf(416), "", "*/" + len });
        // Invalid no equals
        parameterSets.add(new Object[] { "bytes 1-10", Integer.valueOf(416), "", "*/" + len });
        parameterSets.add(new Object[] { "bytes1-10", Integer.valueOf(416), "", "*/" + len });
        parameterSets.add(new Object[] { "bytes10-", Integer.valueOf(416), "", "*/" + len });
        parameterSets.add(new Object[] { "bytes-10", Integer.valueOf(416), "", "*/" + len });
        // Unknown types
        parameterSets.add(new Object[] { "unknown=1-2", Integer.valueOf(200), strLen, "" });
        parameterSets.add(new Object[] { "bytesX=1-2", Integer.valueOf(200), strLen, "" });
        parameterSets.add(new Object[] { "Xbytes=1-2", Integer.valueOf(200), strLen, "" });
        // Valid range
        parameterSets.add(new Object[] { "bytes=0-9", Integer.valueOf(206), "10", "0-9/" + len });
        parameterSets.add(new Object[] { "bytes=-100", Integer.valueOf(206), "100", (len - 100) + "-" + (len - 1) + "/" + len });
        parameterSets.add(new Object[] { "bytes=100-", Integer.valueOf(206), "" + (len - 100), "100-" + (len - 1) + "/" + len });
        // Valid range (too much)
        parameterSets.add(new Object[] { "bytes=0-1000", Integer.valueOf(206), strLen, "0-" +  (len - 1) + "/" + len });
        parameterSets.add(new Object[] { "bytes=-1000", Integer.valueOf(206), strLen, "0-" + (len - 1) + "/" + len });
        return parameterSets;
    }

    @Parameter(0)
    public String rangeHeader;
    @Parameter(1)
    public int responseCodeExpected;
    @Parameter(2)
    public String contentLengthExpected;
    @Parameter(3)
    public String responseRangeExpected;

    @Test
    public void testRange() throws Exception {

        Tomcat tomcat = getTomcatInstance();

        File appDir = new File("test/webapp");
        Context ctxt = tomcat.addContext("", appDir.getAbsolutePath());

        Tomcat.addServlet(ctxt, "default", DefaultServlet.class.getName());
        ctxt.addServletMappingDecoded("/", "default");

        tomcat.start();

        // Set up parameters
        String path = "http://localhost:" + getPort() + "/index.html";
        ByteChunk responseBody = new ByteChunk();
        Map<String,List<String>> responseHeaders = new HashMap<>();

        int rc = getUrl(path, responseBody, buildRangeHeader(rangeHeader), responseHeaders);

        // Check the result
        Assert.assertEquals(responseCodeExpected, rc);

        if (contentLengthExpected.length() > 0) {
            String contentLength = responseHeaders.get("Content-Length").get(0);
            Assert.assertEquals(contentLengthExpected, contentLength);
        }

        if (responseRangeExpected.length() > 0) {
            String responseRange = null;
            List<String> headerValues = responseHeaders.get("Content-Range");
            if (headerValues != null && headerValues.size() == 1) {
                responseRange = headerValues.get(0);
            }
            Assert.assertEquals("bytes " + responseRangeExpected, responseRange);
        }
    }


    private static Map<String,List<String>> buildRangeHeader(String... headerValues) {
        Map<String,List<String>> requestHeaders = new HashMap<>();
        List<String> values = new ArrayList<>();
        for (String headerValue : headerValues) {
            if (headerValue.length() > 0) {
                values.add(headerValue);
            }
        }

        if (values.size() == 0) {
            return null;
        }

        requestHeaders.put("range", values);

        return requestHeaders;
    }
}
