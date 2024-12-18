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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.buf.ByteChunk;

public class TestDefaultServletRfc9110Section14 extends TomcatBaseTest {

    @Test
    public void testRangeHandlingDefinedMethods() throws Exception {
        // GET is the only method for which range handling is defined.

        Tomcat tomcat = getTomcatInstance();

        File appDir = new File("test/webapp");
        Context ctxt = tomcat.addContext("", appDir.getAbsolutePath());

        Tomcat.addServlet(ctxt, "default", DefaultServlet.class.getName());
        ctxt.addServletMappingDecoded("/", "default");

        tomcat.start();

        String path = "http://localhost:" + getPort() + "/index.html";
        ByteChunk responseBody = new ByteChunk();
        Map<String,List<String>> responseHeaders = new HashMap<>();
        Map<String,List<String>> requestHeaders = new HashMap<>();

        String rangeHeader = "bytes=0-10";
        // Get and Head

        requestHeaders.put("Range",List.of(rangeHeader));
        int rc = getUrl(path, responseBody, requestHeaders, responseHeaders);
        Assert.assertEquals("Range requests is turn on, SC_PARTIAL_CONTENT of GET is expected",
                HttpServletResponse.SC_PARTIAL_CONTENT, rc);
        Assert.assertTrue("Range requests is turn on, header `Accept-Ranges: bytes` is expected",
                responseHeaders.containsKey("Accept-Ranges") && responseHeaders.get("Accept-Ranges").contains("bytes"));

        rc = methodUrl(path, responseBody, DEFAULT_CLIENT_TIMEOUT_MS, requestHeaders, responseHeaders, "HEAD");
        Assert.assertEquals("Range requests is turn on, SC_OK of HEAD is expected", HttpServletResponse.SC_OK, rc);
        Assert.assertTrue("Range requests is turn on, header `Accept-Ranges: bytes` is expected",
                responseHeaders.containsKey("Accept-Ranges") && responseHeaders.get("Accept-Ranges").contains("bytes"));

        tomcat.stop();
    }

    @Test
    public void testUnsupportedRangeUnit() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        File appDir = new File("test/webapp");
        Context ctxt = tomcat.addContext("", appDir.getAbsolutePath());

        Tomcat.addServlet(ctxt, "default", DefaultServlet.class.getName());
        ctxt.addServletMappingDecoded("/", "default");

        tomcat.start();

        String path = "http://localhost:" + getPort() + "/index.html";
        ByteChunk responseBody = new ByteChunk();
        Map<String,List<String>> responseHeaders = new HashMap<>();
        Map<String,List<String>> requestHeaders = new HashMap<>();

        String rangeHeader = "Chars=0-10";
        // Get and Head

        requestHeaders.put("Range",List.of(rangeHeader));
        int rc = getUrl(path, responseBody, requestHeaders, responseHeaders);
        Assert.assertEquals(
                "RFC 9110 - 14.2: An origin server MUST ignore a Range header field that contains a range unit it does not understand. `Chars` is not a understandable RangeUnit, SC_OK is expected",
                HttpServletResponse.SC_OK, rc);

        tomcat.stop();
    }
}
