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
        Assert.assertTrue(responseHeaders.containsKey("Content-Type"));
        List<String> contentType = responseHeaders.get("Content-Type");
        Assert.assertEquals(1, contentType.size());
        Assert.assertEquals("text/plain;charset=uTf-8", contentType.get(0));
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
}
