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
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Ignore;
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
public class TestDefaultServletRedirect extends TomcatBaseTest {

    @Parameterized.Parameters(name = "{index} redirectStatus [{0}]")
    public static Collection<Object[]> parameters() {
        List<Object[]> parameterSets = new ArrayList<>();

        parameterSets.add(new Object[] { Integer.valueOf(HttpServletResponse.SC_MOVED_PERMANENTLY) });
        parameterSets.add(new Object[] { Integer.valueOf(HttpServletResponse.SC_FOUND) });
        parameterSets.add(new Object[] { Integer.valueOf(HttpServletResponse.SC_TEMPORARY_REDIRECT) });
        parameterSets.add(new Object[] { Integer.valueOf(308) });

        return parameterSets;
    }

    @Parameter(0)
    public int redirectStatus;

    @Ignore // See PR #524
    @Test
    public void testRedirect() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        tomcat.setAddDefaultWebXmlToWebapp(false);

        File appDir = new File("test/webapp");
        Context ctx = tomcat.addWebapp(null, "/test", appDir.getAbsolutePath());
        Wrapper defaultServlet = Tomcat.addServlet(ctx, "default", new DefaultServlet());
        defaultServlet.addMapping("/");

        defaultServlet.addInitParameter("redirectStatusCode", Integer.toString(redirectStatus));

        tomcat.start();

        ByteChunk out = new ByteChunk();
        Map<String, List<String>> headers = new HashMap<>();
        // Should be redirected
        int rc = methodUrl("http://localhost:" + getPort() + "/test/jsp", out, DEFAULT_CLIENT_TIMEOUT_MS,
                null, headers, "GET", false);

        Assert.assertEquals("Unexpected status code", redirectStatus, rc);
    }


    @Test
    public void testIncludeThenRedirect() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        tomcat.setAddDefaultWebXmlToWebapp(false);

        File appDir = new File("test/webapp");
        Context ctx = tomcat.addWebapp(null, "/test", appDir.getAbsolutePath());
        Wrapper defaultServlet = Tomcat.addServlet(ctx, "default", new DefaultServlet());
        defaultServlet.addMapping("/");

        defaultServlet.addInitParameter("redirectStatusCode", Integer.toString(redirectStatus));

        Wrapper includeServlet = Tomcat.addServlet(ctx, "include", new IncludeServlet());
        includeServlet.addMapping("/include");

        tomcat.start();

        ByteChunk out = new ByteChunk();
        Map<String, List<String>> headers = new HashMap<>();
        // Should not be redirected
        int rc = getUrl("http://localhost:" + getPort() + "/test/include", out, headers);

        Assert.assertEquals("Unexpected status code", HttpServletResponse.SC_OK, rc);
    }


    private static class IncludeServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            resp.setContentType("text/plain");
            resp.setCharacterEncoding("UTF-8");

            RequestDispatcher rd = req.getRequestDispatcher("/jsp");
            rd.include(req, resp);

            resp.getWriter().print("OK");
        }
    }
}
