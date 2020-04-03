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
package org.apache.coyote;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.buf.ByteChunk;

public class TestResponse extends TomcatBaseTest {

    /*
     * https://bz.apache.org/bugzilla/show_bug.cgi?id=61197
     */
    @Test
    public void testUserCharsetIsRetained() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        // No file system docBase required
        Context ctx = tomcat.addContext("", null);

        // Add  servlet
        Tomcat.addServlet(ctx, "CharsetServlet", new CharsetServlet());
        ctx.addServletMappingDecoded("/*", "CharsetServlet");

        tomcat.start();

        ByteChunk responseBody = new ByteChunk();
        Map<String,List<String>> responseHeaders = new HashMap<>();
        int rc = getUrl("http://localhost:" + getPort() + "/test?charset=uTf-8", responseBody,
                responseHeaders);

        Assert.assertEquals(HttpServletResponse.SC_OK, rc);

        String contentType = getSingleHeader("Content-Type", responseHeaders);
        Assert.assertEquals("text/plain;charset=uTf-8", contentType);
    }


    private static class CharsetServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {
            resp.setContentType("text/plain");
            resp.setCharacterEncoding(req.getParameter("charset"));

            resp.getWriter().print("OK");
        }
    }


    @Test
    public void testContentTypeWithSpace() throws Exception {
        doTestContentTypeSpacing(true);
    }


    @Test
    public void testContentTypeWithoutSpace() throws Exception {
        doTestContentTypeSpacing(false);
    }


    private void doTestContentTypeSpacing(boolean withSpace) throws Exception {
        Tomcat tomcat = getTomcatInstance();

        // No file system docBase required
        Context ctx = tomcat.addContext("", null);

        // Add  servlet
        Tomcat.addServlet(ctx, "ContentTypeServlet", new ContentTypeServlet());
        ctx.addServletMappingDecoded("/*", "ContentTypeServlet");

        tomcat.start();

        ByteChunk responseBody = new ByteChunk();
        Map<String,List<String>> responseHeaders = new HashMap<>();
        StringBuilder uri = new StringBuilder("http://localhost:");
        uri.append(getPort());
        uri.append("/test");
        if (withSpace) {
            uri.append("?withSpace=true");
        }
        int rc = getUrl(uri.toString(), responseBody, responseHeaders);

        Assert.assertEquals(HttpServletResponse.SC_OK, rc);

        String contentType = getSingleHeader("Content-Type", responseHeaders);
        StringBuilder expected = new StringBuilder("text/plain;");
        if (withSpace) {
            expected.append(" ");
        }
        expected.append("v=1;charset=UTF-8");
        Assert.assertEquals(expected.toString() , contentType);
    }


    private static class ContentTypeServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {

            if (req.getParameter("withSpace") == null) {
                resp.setContentType("text/plain;v=1");
            } else {
                resp.setContentType("text/plain; v=1");
            }
            resp.setCharacterEncoding("UTF-8");

            resp.getWriter().print("OK");
        }
    }
}
