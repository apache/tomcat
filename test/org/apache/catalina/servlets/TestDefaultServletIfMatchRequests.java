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
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
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

    @Parameterized.Parameters(name = "{index} matchHeader [{0}]")
    public static Collection<Object[]> parameters() {

        // Get the length of the file used for this test
        // It varies by platform due to line-endings
        File index = new File("test/webapp/index.html");
        String strongETag =  "\"" + index.length() + "-" + index.lastModified() + "\"";
        String weakETag = "W/" + strongETag;

        List<Object[]> parameterSets = Arrays.asList(
            new Object[] { "*", Boolean.TRUE, Boolean.TRUE },
            new Object[] { weakETag, Boolean.FALSE, Boolean.TRUE },
            new Object[] { strongETag, Boolean.TRUE, Boolean.TRUE },
            new Object[] { weakETag + ",\"anotherETag\"", Boolean.FALSE, Boolean.TRUE },
            new Object[] { strongETag + ",\"anotherETag\"", Boolean.TRUE, Boolean.TRUE },
            new Object[] { weakETag + " ,\"anotherETag\"", Boolean.FALSE, Boolean.TRUE },
            new Object[] { strongETag + " ,\"anotherETag\"", Boolean.TRUE, Boolean.TRUE },
            new Object[] { "\"anotherETag\"," + weakETag, Boolean.FALSE, Boolean.TRUE },
            new Object[] { "\"anotherETag\"," + strongETag, Boolean.TRUE, Boolean.TRUE },
            new Object[] { "\"anotherETag\", " + weakETag, Boolean.FALSE, Boolean.TRUE },
            new Object[] { "\"anotherETag\", " + strongETag, Boolean.TRUE, Boolean.TRUE },
            new Object[] { "\"anotherETag\"", Boolean.FALSE, Boolean.FALSE },
            new Object[] { "W/\"anotherETag\"", Boolean.FALSE, Boolean.FALSE },
            new Object[] { "W/", Boolean.FALSE, Boolean.FALSE }
        );

        return parameterSets;
    }

    @Parameter(0)
    public String matchHeader;

    @Parameter(1)
    public boolean ifMatchCondition;

    @Parameter(2)
    public boolean ifNoneMatchCondition;

    @Test
    public void testIfMatch() throws Exception {

        Tomcat tomcat = getTomcatInstance();

        File appDir = new File("test/webapp");
        Context ctxt = tomcat.addContext("", appDir.getAbsolutePath());

        Tomcat.addServlet(ctxt, "default", DefaultServlet.class.getName());
        ctxt.addServletMappingDecoded("/", "default");

        tomcat.start();

        int rc = performRequest("If-Match", matchHeader);

        // Check the result
        Assert.assertEquals("If-Match", ifMatchCondition ? 200 : 412, rc);

        rc = performRequest("If-None-Match", matchHeader);

        // Check the result
        Assert.assertEquals("If-None-Match", ifNoneMatchCondition ? 304 : 200, rc);
    }

    private int performRequest(String headerName, String matchHeader) throws IOException {
        // Set up parameters
        String path = "http://localhost:" + getPort() + "/index.html";
        ByteChunk responseBody = new ByteChunk();

        List<String> values = Collections.singletonList(matchHeader);
        Map<String,List<String>> requestHeaders = Collections.singletonMap(headerName, values);

        int rc = getUrl(path, responseBody, requestHeaders, null);
        return rc;
    }
}
