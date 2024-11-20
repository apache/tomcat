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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;

import org.apache.catalina.Context;
import org.apache.catalina.Wrapper;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.catalina.util.IOTools;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.buf.HexUtils;

@RunWith(Parameterized.class)
public class TestDefaultServletIfMatchRequests extends TomcatBaseTest {

    private static final Integer RC_200 = Integer.valueOf(200);
    private static final Integer RC_304 = Integer.valueOf(304);
    private static final Integer RC_400 = Integer.valueOf(400);
    private static final Integer RC_412 = Integer.valueOf(412);

    private static final String[] CONCAT = new String[] { ",", " ,", ", ", " , " };
    private static String resourceETagStrong;
    private static String resourceETagWeak;

    @Parameterized.Parameters(name = "{index} resource-strong [{0}], matchHeader [{1}]")
    public static Collection<Object[]> parameters() {

        // Get the length of the file used for this test
        // It varies by platform due to line-endings
        File index = new File("test/webapp/index.html");
        try (FileInputStream is = new FileInputStream(index)) {
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            IOTools.flow(is, os);
            resourceETagStrong = "\"" + HexUtils.toHexString(MessageDigest.getInstance("SHA-1").digest(os.toByteArray())) + "\"";
        } catch (Exception e) {
        }
        resourceETagWeak = "W/" + "\"" + index.length() + "-" + index.lastModified() + "\"";

        String otherETagStrong = "\"123456789\"";
        String otherETagWeak = "W/\"123456789\"";

        List<Object[]> parameterSets = new ArrayList<>();

        for (Boolean resourceWithStrongETag : booleans) {
            // No match header
            parameterSets.add(new Object[] { resourceWithStrongETag, null, RC_200, RC_200 });

            // match header is invalid
            parameterSets.add(new Object[] { resourceWithStrongETag, "", RC_400, RC_400 });
            parameterSets.add(new Object[] { resourceWithStrongETag, "W", RC_400, RC_400 });
            parameterSets.add(new Object[] { resourceWithStrongETag, "W/", RC_400, RC_400 });
            parameterSets.add(new Object[] { resourceWithStrongETag, "w/" + resourceETagStrong, RC_400, RC_400 });
            parameterSets.add(new Object[] { resourceWithStrongETag, resourceETagStrong + " x", RC_400, RC_400 });
            parameterSets.add(new Object[] { resourceWithStrongETag, resourceETagStrong + "x", RC_400, RC_400 });
            for (String concat : CONCAT) {
                parameterSets.add(new Object[] { resourceWithStrongETag, concat + resourceETagStrong, RC_400, RC_400 });
                parameterSets.add(new Object[] { resourceWithStrongETag, concat + resourceETagWeak, RC_400, RC_400 });
                parameterSets.add(new Object[] { resourceWithStrongETag, resourceETagStrong + concat, RC_400, RC_400 });
                parameterSets.add(new Object[] { resourceWithStrongETag, resourceETagWeak + concat, RC_400, RC_400 });
            }

            // match header always matches resource (leading and trailing space should be ignored)
            parameterSets.add(new Object[] { resourceWithStrongETag, "*", RC_200, RC_304 });
            parameterSets.add(new Object[] { resourceWithStrongETag, " *", RC_200, RC_304 });
            parameterSets.add(new Object[] { resourceWithStrongETag, "* ", RC_200, RC_304 });

            // match header never matches resource
            parameterSets.add(new Object[] { resourceWithStrongETag, otherETagStrong, RC_412, RC_200 });
            parameterSets.add(new Object[] { resourceWithStrongETag, otherETagWeak, RC_412, RC_200 });

            // match header includes weak tag
            String etag;
            if (resourceWithStrongETag.booleanValue()) {
                etag = "W/" + resourceETagStrong;
            } else {
                etag = resourceETagWeak;
            }
            parameterSets.add(new Object[] { resourceWithStrongETag, etag, RC_412, RC_304 });
            for (String concat : CONCAT) {
                parameterSets.add(new Object[] { resourceWithStrongETag, etag + concat + otherETagWeak,
                        RC_412, RC_304 });
                parameterSets.add(new Object[] { resourceWithStrongETag, etag + concat + otherETagStrong,
                        RC_412, RC_304 });
                parameterSets.add(new Object[] { resourceWithStrongETag, otherETagWeak + concat + etag,
                        RC_412, RC_304 });
                parameterSets.add(new Object[] { resourceWithStrongETag, otherETagStrong + concat + etag,
                        RC_412, RC_304 });
            }

            // match header includes strong entity tag
            // If-Match result depends on whether the resource has a strong entity tag
            Integer rcIfMatch;
            if (resourceWithStrongETag.booleanValue()) {
                rcIfMatch = RC_200;
                etag = resourceETagStrong;
            } else {
                rcIfMatch = RC_412;
                etag = resourceETagWeak.substring(2);
            }
            parameterSets.add(new Object[] { resourceWithStrongETag, etag, rcIfMatch, RC_304 });
            for (String concat : CONCAT) {
                parameterSets.add(new Object[] { resourceWithStrongETag, etag + concat + otherETagWeak,
                        rcIfMatch, RC_304 });
                parameterSets.add(new Object[] { resourceWithStrongETag, etag + concat + otherETagStrong,
                        rcIfMatch, RC_304 });
                parameterSets.add(new Object[] { resourceWithStrongETag, otherETagWeak + concat + etag,
                        rcIfMatch, RC_304 });
                parameterSets.add(new Object[] { resourceWithStrongETag, otherETagStrong + concat + etag,
                        rcIfMatch, RC_304 });
            }
        }

        return parameterSets;
    }

    @Parameter(0)
    public boolean resourceHasStrongETag;

    @Parameter(1)
    public String matchHeader;

    @Parameter(2)
    public int ifMatchResponseCode;

    @Parameter(3)
    public int ifNoneMatchResponseCode;

    @Test
    public void testIfMatch() throws Exception {
        doMatchTest("If-Match", ifMatchResponseCode, false);
    }


    @Test
    public void testIfNoneMatch() throws Exception {
        doMatchTest("If-None-Match", ifNoneMatchResponseCode, true);
    }


    private void doMatchTest(String headerName, int responseCodeExpected, boolean responseHasEtag) throws Exception {
        Tomcat tomcat = getTomcatInstance();

        File appDir = new File("test/webapp");
        Context ctxt = tomcat.addContext("", appDir.getAbsolutePath());

        Wrapper wrapper = Tomcat.addServlet(ctxt, "default", DefaultServlet.class.getName());
        if (resourceHasStrongETag) {
            wrapper.addInitParameter("useStrongETags", "true");
        }
        ctxt.addServletMappingDecoded("/", "default");

        tomcat.start();

        // Set up parameters
        String path = "http://localhost:" + getPort() + "/index.html";
        ByteChunk responseBody = new ByteChunk();
        Map<String,List<String>> responseHeaders = new HashMap<>();

        Map<String,List<String>> requestHeaders = null;
        if (matchHeader != null) {
            List<String> values = Collections.singletonList(matchHeader);
            requestHeaders = Collections.singletonMap(headerName, values);
        }

        int rc = getUrl(path, responseBody, requestHeaders, responseHeaders);

        // Check the result
        Assert.assertEquals(responseCodeExpected, rc);

        // If-None-Match should have a real resource ETag in successful response
        if (responseHasEtag && (rc == 200 || rc == 304)) {
            System.out.println(responseHeaders);
            String responseEtag = responseHeaders.get("ETag").get(0);
            Assert.assertEquals(resourceHasStrongETag ? resourceETagStrong : resourceETagWeak, responseEtag);
        }
    }

}
