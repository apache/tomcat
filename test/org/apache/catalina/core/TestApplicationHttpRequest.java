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
package org.apache.catalina.core;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Map;
import java.util.Map.Entry;

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

public class TestApplicationHttpRequest extends TomcatBaseTest {

    /*
     * https://bz.apache.org/bugzilla/show_bug.cgi?id=58836
     */
    @Test
    public void testForwardQueryString01() throws Exception {
        doQueryStringTest(null, "a=b", "a:(b)");
    }


    @Test
    public void testForwardQueryString02() throws Exception {
        doQueryStringTest(null, "a=b&a=c", "a:(b),(c)");
    }


    @Test
    public void testForwardQueryString03() throws Exception {
        doQueryStringTest(null, "a=b&c=d", "a:(b);c:(d)");
    }


    @Test
    public void testForwardQueryString04() throws Exception {
        doQueryStringTest(null, "a=b&c=d&a=e", "a:(b),(e);c:(d)");
    }


    @Test
    public void testForwardQueryString05() throws Exception {
        // Parameters with no value are assigned a vale of the empty string
        doQueryStringTest(null, "a=b&c&a=e", "a:(b),(e);c:()");
    }


    @Test
    public void testOriginalQueryString01() throws Exception {
        doQueryStringTest("a=b", null, "a:(b)");
    }


    @Test
    public void testOriginalQueryString02() throws Exception {
        doQueryStringTest("a=b&a=c", null, "a:(b),(c)");
    }


    @Test
    public void testOriginalQueryString03() throws Exception {
        doQueryStringTest("a=b&c=d", null, "a:(b);c:(d)");
    }


    @Test
    public void testOriginalQueryString04() throws Exception {
        doQueryStringTest("a=b&c=d&a=e", null, "a:(b),(e);c:(d)");
    }


    @Test
    public void testOriginalQueryString05() throws Exception {
        // Parameters with no value are assigned a vale of the empty string
        doQueryStringTest("a=b&c&a=e", null, "a:(b),(e);c:()");
    }


    @Test
    public void testMergeQueryString01() throws Exception {
        doQueryStringTest("a=b", "a=z", "a:(z),(b)");
    }


    @Test
    public void testMergeQueryString02() throws Exception {
        // Parameters with no value are assigned a vale of the empty string
        doQueryStringTest("a=b&c&a=e", "a=z", "a:(z),(b),(e);c:()");
    }


    @Test
    public void testMergeQueryString03() throws Exception {
        // Parameters with no value are assigned a vale of the empty string
        doQueryStringTest("a=b&c&a=e", "c=z", "a:(b),(e);c:(z),()");
    }


    @Test
    public void testMergeQueryString04() throws Exception {
        // Parameters with no value are assigned a vale of the empty string
        doQueryStringTest("a=b&c&a=e", "a", "a:(),(b),(e);c:()");
    }


    private void doQueryStringTest(String originalQueryString, String forwardQueryString,
            String expected) throws Exception {
        Tomcat tomcat = getTomcatInstance();

        // No file system docBase required
        Context ctx = tomcat.addContext("", null);

        if (forwardQueryString == null) {
            Tomcat.addServlet(ctx, "forward", new ForwardServlet("/display"));
        } else {
            Tomcat.addServlet(ctx, "forward", new ForwardServlet("/display?" + forwardQueryString));
        }
        ctx.addServletMapping("/forward", "forward");

        Tomcat.addServlet(ctx, "display", new DisplayParameterServlet());
        ctx.addServletMapping("/display", "display");

        tomcat.start();

        ByteChunk response = new ByteChunk();
        StringBuilder target = new StringBuilder("http://localhost:");
        target.append(getPort());
        target.append("/forward");
        if (originalQueryString != null) {
            target.append('?');
            target.append(originalQueryString);
        }
        int rc = getUrl(target.toString(), response, null);

        Assert.assertEquals(200, rc);
        Assert.assertEquals(expected, response.toString());
    }


    private static class ForwardServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        private final String target;

        public ForwardServlet(String target) {
            this.target = target;
        }

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {
            req.getRequestDispatcher(target).forward(req, resp);
        }
    }


    private static class DisplayParameterServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {
            resp.setContentType("text/plain");
            resp.setCharacterEncoding("UTF-8");
            PrintWriter w = resp.getWriter();
            Map<String,String[]> params = req.getParameterMap();
            boolean firstParam = true;
            for (Entry<String,String[]> param : params.entrySet()) {
                if (firstParam) {
                    firstParam = false;
                } else {
                    w.print(';');
                }
                w.print(param.getKey());
                w.print(':');
                boolean firstValue = true;
                for (String value : param.getValue()) {
                    if (firstValue) {
                        firstValue = false;
                    } else {
                        w.print(',');
                    }
                    w.print('(');
                    w.print(value);
                    w.print(')');
                }
            }
        }
    }
}
