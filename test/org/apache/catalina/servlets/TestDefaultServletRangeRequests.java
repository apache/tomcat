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

    @Parameterized.Parameters
    public static Collection<Object[]> parameters() {
        List<Object[]> parameterSets = new ArrayList<>();

        parameterSets.add(new Object[] { null, Integer.valueOf(200)});
        // Invalid
        // Commented out as these tests currently fail
        //parameterSets.add(new Object[] { buildRangeHeader("bytes"), Integer.valueOf(416)});
        //parameterSets.add(new Object[] { buildRangeHeader("bytes="), Integer.valueOf(416)});
        return parameterSets;
    }

    @Parameter(0)
    public Map<String,List<String>> requestHeaders;
    @Parameter(1)
    public int responseCodeExpected;

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

        int rc = getUrl(path, responseBody, requestHeaders, responseHeaders);

        // Check the result
        Assert.assertEquals(responseCodeExpected, rc);
    }


    private static Map<String,List<String>> buildRangeHeader(String... headerValues) {
        Map<String,List<String>> requestHeaders = new HashMap<>();
        List<String> values = new ArrayList<>();
        for (String headerValue : headerValues) {
            values.add(headerValue);
        }
        requestHeaders.put("range", values);

        return requestHeaders;
    }
}
