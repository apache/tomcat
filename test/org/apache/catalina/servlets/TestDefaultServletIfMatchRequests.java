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
public class TestDefaultServletIfMatchRequests extends TomcatBaseTest {

    @Parameterized.Parameters(name = "{index} ifMatchHeader [{0}]")
    public static Collection<Object[]> parameters() {

        // Get the length of the file used for this test
        // It varies by platform due to line-endings
        File index = new File("test/webapp/index.html");
        String strongETag =  "\"" + index.length() + "-" + index.lastModified() + "\"";
        String weakETag = "W/" + strongETag;

        List<Object[]> parameterSets = new ArrayList<>();

        parameterSets.add(new Object[] { null, Integer.valueOf(200) });
        parameterSets.add(new Object[] { "*", Integer.valueOf(200) });
        parameterSets.add(new Object[] { weakETag, Integer.valueOf(200) });
        parameterSets.add(new Object[] { strongETag, Integer.valueOf(200) });
        parameterSets.add(new Object[] { weakETag + ",123456789", Integer.valueOf(200) });
        parameterSets.add(new Object[] { strongETag + ",123456789", Integer.valueOf(200) });
        parameterSets.add(new Object[] { weakETag + " ,123456789", Integer.valueOf(200) });
        parameterSets.add(new Object[] { strongETag + " ,123456789", Integer.valueOf(200) });
        parameterSets.add(new Object[] { "123456789," + weakETag, Integer.valueOf(200) });
        parameterSets.add(new Object[] { "123456789," + strongETag, Integer.valueOf(200) });
        parameterSets.add(new Object[] { "123456789, " + weakETag, Integer.valueOf(200) });
        parameterSets.add(new Object[] { "123456789, " + strongETag, Integer.valueOf(200) });
        parameterSets.add(new Object[] { "123456789", Integer.valueOf(412) });
        parameterSets.add(new Object[] { "W/123456789", Integer.valueOf(412) });
        parameterSets.add(new Object[] { "W/", Integer.valueOf(412) });

        return parameterSets;
    }

    @Parameter(0)
    public String ifMatchHeader;

    @Parameter(1)
    public int responseCodeExpected;


    @Test
    public void testIfMatch() throws Exception {

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

        Map<String,List<String>> requestHeaders = null;
        if (ifMatchHeader != null) {
            requestHeaders = new HashMap<>();
            List<String> values = new ArrayList<>(1);
            values.add(ifMatchHeader);
            requestHeaders.put("If-Match", values);
        }

        int rc = getUrl(path, responseBody, requestHeaders, responseHeaders);

        // Check the result
        Assert.assertEquals(responseCodeExpected, rc);
    }
}
