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
package javax.servlet.http;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletException;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.core.StandardContext;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.collections.CaseInsensitiveKeyMap;

public class TestHttpServlet extends TomcatBaseTest {

    @Test
    public void testBug53454() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        // No file system docBase required
        StandardContext ctx = (StandardContext) tomcat.addContext("", null);

        // Map the test Servlet
        LargeBodyServlet largeBodyServlet = new LargeBodyServlet();
        Tomcat.addServlet(ctx, "largeBodyServlet", largeBodyServlet);
        ctx.addServletMappingDecoded("/", "largeBodyServlet");

        tomcat.start();

        Map<String,List<String>> resHeaders= new HashMap<>();
        int rc = headUrl("http://localhost:" + getPort() + "/", new ByteChunk(),
               resHeaders);

        Assert.assertEquals(HttpServletResponse.SC_OK, rc);
        Assert.assertEquals(LargeBodyServlet.RESPONSE_LENGTH,
                resHeaders.get("Content-Length").get(0));
    }


    private static class LargeBodyServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;
        private static final String RESPONSE_LENGTH = "12345678901";

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {
            resp.setHeader("content-length", RESPONSE_LENGTH);
        }
    }


    /*
     * Verifies that the same Content-Length is returned for both GET and HEAD
     * operations when a Servlet includes content from another Servlet
     */
    @Test
    public void testBug57602() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        // No file system docBase required
        StandardContext ctx = (StandardContext) tomcat.addContext("", null);

        Bug57602ServletOuter outer = new Bug57602ServletOuter();
        Tomcat.addServlet(ctx, "Bug57602ServletOuter", outer);
        ctx.addServletMappingDecoded("/outer", "Bug57602ServletOuter");

        Bug57602ServletInner inner = new Bug57602ServletInner();
        Tomcat.addServlet(ctx, "Bug57602ServletInner", inner);
        ctx.addServletMappingDecoded("/inner", "Bug57602ServletInner");

        tomcat.start();

        Map<String,List<String>> resHeaders= new CaseInsensitiveKeyMap<>();
        String path = "http://localhost:" + getPort() + "/outer";
        ByteChunk out = new ByteChunk();

        int rc = getUrl(path, out, resHeaders);
        Assert.assertEquals(HttpServletResponse.SC_OK, rc);
        String length = getSingleHeader("Content-Length", resHeaders);
        Assert.assertEquals(Long.parseLong(length), out.getLength());
        out.recycle();

        rc = headUrl(path, out, resHeaders);
        Assert.assertEquals(HttpServletResponse.SC_OK, rc);
        Assert.assertEquals(0, out.getLength());
        Assert.assertEquals(length, resHeaders.get("Content-Length").get(0));

        tomcat.stop();
    }


    @Test
    public void testChunkingWithHead() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        // No file system docBase required
        StandardContext ctx = (StandardContext) tomcat.addContext("", null);

        ChunkingServlet s = new ChunkingServlet();
        Tomcat.addServlet(ctx, "ChunkingServlet", s);
        ctx.addServletMappingDecoded("/chunking", "ChunkingServlet");

        tomcat.start();

        Map<String,List<String>> getHeaders = new CaseInsensitiveKeyMap<>();
        String path = "http://localhost:" + getPort() + "/chunking";
        ByteChunk out = new ByteChunk();

        int rc = getUrl(path, out, getHeaders);
        Assert.assertEquals(HttpServletResponse.SC_OK, rc);
        out.recycle();

        Map<String,List<String>> headHeaders = new HashMap<>();
        rc = headUrl(path, out, headHeaders);
        Assert.assertEquals(HttpServletResponse.SC_OK, rc);

        // Headers should be the same (apart from Date)
        Assert.assertEquals(getHeaders.size(), headHeaders.size());
        for (Map.Entry<String, List<String>> getHeader : getHeaders.entrySet()) {
            String headerName = getHeader.getKey();
            if ("date".equalsIgnoreCase(headerName)) {
                continue;
            }
            Assert.assertTrue(headerName, headHeaders.containsKey(headerName));
            List<String> getValues = getHeader.getValue();
            List<String> headValues = headHeaders.get(headerName);
            Assert.assertEquals(getValues.size(), headValues.size());
            for (String value : getValues) {
                Assert.assertTrue(headValues.contains(value));
            }
        }

        tomcat.stop();
    }


    private static class Bug57602ServletOuter extends HttpServlet {

        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {
            resp.setContentType("text/plain");
            resp.setCharacterEncoding("UTF-8");
            PrintWriter pw = resp.getWriter();
            pw.println("Header");
            req.getRequestDispatcher("/inner").include(req, resp);
            pw.println("Footer");
        }
    }


    private static class Bug57602ServletInner extends HttpServlet {

        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {
            resp.setContentType("text/plain");
            resp.setCharacterEncoding("UTF-8");
            PrintWriter pw = resp.getWriter();
            pw.println("Included");
        }
    }


    private static class ChunkingServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {
            resp.setContentType("text/plain");
            resp.setCharacterEncoding("UTF-8");
            PrintWriter pw = resp.getWriter();
            // Trigger chunking
            pw.write(new char[8192 * 16]);
            pw.println("Data");
        }
    }
}
