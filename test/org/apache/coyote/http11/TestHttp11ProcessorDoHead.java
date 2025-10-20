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
package org.apache.coyote.http11;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;

import org.apache.catalina.Context;
import org.apache.catalina.Wrapper;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.buf.ByteChunk;

@RunWith(Parameterized.class)
public class TestHttp11ProcessorDoHead extends TomcatBaseTest {

    private static final int DEFAULT_BUFFER_SIZE = 8192;

    @Parameterized.Parameters(name = "{index}: explicit-cl[{0}], cl[{1}], useContainerHead[{2}], expected-cl[{3}]")
    public static Collection<Object[]> parameters() {

        List<Object[]> parameterSets = new ArrayList<>();

        int[] contentLengths = new int[] { DEFAULT_BUFFER_SIZE / 2, DEFAULT_BUFFER_SIZE -1 , DEFAULT_BUFFER_SIZE, DEFAULT_BUFFER_SIZE + 1, DEFAULT_BUFFER_SIZE * 2};

        for (Boolean explicitContentLength : booleans) {
            for (int contentLength : contentLengths) {
                for (HeadSource headSource : HeadSource.values()) {
                    Integer expectedContentLength = null;
                    /*
                     * Tomcat should send a content-length for a head request when the same value would be sent for a
                     * equivalent GET request. Those circumstances are:
                     * - The Servlet sets an explicit content-length
                     * - When the container is providing the HEAD response and the content-length is less than the
                     *   buffer size (so the container would normally set the content-length on a GET)
                     */
                    if (explicitContentLength.booleanValue() ||
                            !HeadSource.SERVLET.equals(headSource) && contentLength <= DEFAULT_BUFFER_SIZE) {
                        expectedContentLength = Integer.valueOf(contentLength);
                    }
                    parameterSets.add(new Object[] { explicitContentLength, Integer.valueOf(contentLength),
                            headSource, expectedContentLength });
                }
            }
        }
        return parameterSets;
    }

    @Parameter(0)
    public boolean explicitContentLength;
    @Parameter(1)
    public int contentLength;
    @Parameter(2)
    public HeadSource headSource;
    @Parameter(3)
    public Integer expectedContentLength;

    @SuppressWarnings("removal")
    @Test
    public void testHeadWithtDoHeadMethod() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        // No file system docBase required
        Context ctx = getProgrammaticRootContext();

        // Add servlet
        Wrapper w = Tomcat.addServlet(ctx, "DoHeadServlet",
                new DoHeadServlet(explicitContentLength, contentLength, headSource));
        if (HeadSource.CONTAINER_LEGACY.equals(headSource)) {
            w.addInitParameter(HttpServlet.LEGACY_DO_HEAD, "true");
        }

        ctx.addServletMappingDecoded("/head", "DoHeadServlet");

        tomcat.start();

        ByteChunk responseBody = new ByteChunk();
        Map<String, List<String>> responseHeaders = new HashMap<>();
        int rc = headUrl("http://localhost:" + getPort() + "/head", responseBody, responseHeaders);

        Assert.assertEquals(HttpServletResponse.SC_OK, rc);

        List<String> values = responseHeaders.get("Content-Length");
        if (expectedContentLength == null) {
            Assert.assertNull(values);
        } else {
            Assert.assertNotNull(values);
            Assert.assertEquals(1, values.size());
            Assert.assertEquals(expectedContentLength.toString(), values.get(0));
        }
    }


    private static class DoHeadServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        private boolean explicitContentLength;

        private int contentLength;

        private HeadSource headSource;

        private DoHeadServlet(boolean explicitContentLength, int contentLength, HeadSource headSource) {
            this.explicitContentLength = explicitContentLength;
            this.contentLength = contentLength;
            this.headSource = headSource;
        }


        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            writeResponseHeaders(resp);
            PrintWriter pw = resp.getWriter();
            // Efficiency is not a concern here
            for (int i = 0; i < contentLength; i++) {
                pw.print('X');
            }
        }


        @Override
        protected void doHead(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            switch (headSource) {
                case SERVLET:
                    writeResponseHeaders(resp);
                    break;
                case CONTAINER_NEW:
                case CONTAINER_LEGACY:
                    super.doHead(req, resp);
                    break;
            }
        }


        private void writeResponseHeaders(HttpServletResponse resp) {
            resp.setContentType("text/plain");
            resp.setCharacterEncoding(StandardCharsets.UTF_8.name());
            if (explicitContentLength) {
                resp.setContentLength(contentLength);
            }
        }
    }


    private enum HeadSource {
        SERVLET,
        CONTAINER_NEW,
        CONTAINER_LEGACY
    }
}
