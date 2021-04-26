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

package org.apache.catalina.filters;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.descriptor.web.FilterDef;
import org.apache.tomcat.util.descriptor.web.FilterMap;

public class TestAddCharSetFilter extends TomcatBaseTest {

    @Test
    public void testNoneSpecifiedMode1() throws Exception {
        doTest(null, "ISO-8859-1");
    }

    @Test
    public void testNoneSpecifiedMode2() throws Exception {
        doTest(null, "ISO-8859-2", 2, true);
    }

    @Test
    public void testNoneSpecifiedMode3() throws Exception {
        doTest(null, "ISO-8859-3", 3, true);
    }

    @Test
    public void testDefault() throws Exception {
        doTest("default", "ISO-8859-1");
    }

    @Test
    public void testDefaultMixedCase() throws Exception {
        doTest("dEfAuLt", "ISO-8859-1");
    }

    @Test
    public void testSystem() throws Exception {
        doTest("system", Charset.defaultCharset().name());
    }

    @Test
    public void testSystemMixedCase() throws Exception {
        doTest("SyStEm", Charset.defaultCharset().name());
    }

    @Test
    public void testUTF8() throws Exception {
        doTest("utf-8", "utf-8");
    }


    private void doTest(String encoding, String expected) throws Exception {
        doTest(encoding, expected, 1, true);
        tearDown();
        setUp();
        doTest(encoding, expected, 1, false);
    }

    private void doTest(String encoding, String expected, int mode, boolean useSetContentType)
            throws Exception {
        // Setup Tomcat instance
        Tomcat tomcat = getTomcatInstance();

        // No file system docBase required
        Context ctx = tomcat.addContext("", null);

        // Add the Servlet
        CharsetServlet servlet = new CharsetServlet(mode, useSetContentType);
        Tomcat.addServlet(ctx, "servlet", servlet);
        ctx.addServletMappingDecoded("/", "servlet");

        // Add the Filter
        FilterDef filterDef = new FilterDef();
        filterDef.setFilterClass(AddDefaultCharsetFilter.class.getName());
        filterDef.setFilterName("filter");
        if (encoding != null) {
            filterDef.addInitParameter("encoding", encoding);
        }
        ctx.addFilterDef(filterDef);
        FilterMap filterMap = new FilterMap();
        filterMap.setFilterName("filter");
        filterMap.addServletName("servlet");
        ctx.addFilterMap(filterMap);

        tomcat.start();

        Map<String, List<String>> headers = new HashMap<>();
        getUrl("http://localhost:" + getPort() + "/", new ByteChunk(), headers);

        String ct = getSingleHeader("Content-Type", headers).toLowerCase(Locale.ENGLISH);
        Assert.assertEquals("text/plain;charset=" + expected.toLowerCase(Locale.ENGLISH), ct);
    }

    private static class CharsetServlet extends HttpServlet {
        private static final long serialVersionUID = 1L;
        private static final String OUTPUT = "OK";

        private final int mode;
        private final boolean useSetContentType;

        public CharsetServlet(int mode, boolean useSetContentType) {
            this.mode = mode;
            this.useSetContentType = useSetContentType;
        }

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {

            String value;
            switch (mode) {
                case 1:
                    value = "text/plain";
                    if (useSetContentType) {
                        resp.setContentType(value);
                    } else {
                        resp.setHeader("Content-Type", value);
                    }
                    break;
                case 2:
                    value = "text/plain;charset=ISO-8859-2";
                    if (useSetContentType) {
                        resp.setContentType(value);
                    } else {
                        resp.setHeader("Content-Type", value);
                    }
                    break;
                case 3:
                    if (useSetContentType) {
                        resp.setContentType("text/plain");
                        resp.setCharacterEncoding("ISO-8859-3");
                    } else {
                        resp.setHeader("Content-Type", "text/plain;charset=ISO-8859-3");
                    }
                    break;
                default:
                    value = "text/plain;charset=ISO-8859-4";
                    if (useSetContentType) {
                        resp.setContentType(value);
                    } else {
                        resp.setHeader("Content-Type", value);
                    }
                    break;
            }

            resp.getWriter().print(OUTPUT);
        }
    }
}
