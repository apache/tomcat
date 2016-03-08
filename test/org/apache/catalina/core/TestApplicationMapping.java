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

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Mapping;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.buf.ByteChunk;

public class TestApplicationMapping extends TomcatBaseTest {

    @Test
    public void testContextNonRootMappingContextRoot() throws Exception {
        doTestMapping("/dummy", "", "", "", "CONTEXT_ROOT");
    }

    @Test
    public void testContextNonRootMappingDefault() throws Exception {
        doTestMapping("/dummy", "/", "/foo", "/", "DEFAULT");
    }

    @Test
    public void testContextNonRootMappingExtension() throws Exception {
        doTestMapping("/dummy", "*.test", "/foo/bar.test", "/foo/bar", "EXTENSION");
    }

    @Test
    public void testContextNonRootMappingExact() throws Exception {
        doTestMapping("/dummy", "/foo/bar", "/foo/bar", "/foo/bar", "EXACT");
    }

    @Test
    public void testContextNonRootMappingPath() throws Exception {
        doTestMapping("/dummy", "/foo/bar/*", "/foo/bar/foo2", "/foo2", "PATH");
    }

    @Test
    public void testContextRootMappingContextRoot() throws Exception {
        doTestMapping("", "", "", "", "CONTEXT_ROOT");
    }

    @Test
    public void testContextRootMappingDefault() throws Exception {
        doTestMapping("", "/", "/foo", "/", "DEFAULT");
    }

    @Test
    public void testContextRootMappingExtension() throws Exception {
        doTestMapping("", "*.test", "/foo/bar.test", "/foo/bar", "EXTENSION");
    }

    @Test
    public void testContextRootMappingExact() throws Exception {
        doTestMapping("", "/foo/bar", "/foo/bar", "/foo/bar", "EXACT");
    }

    @Test
    public void testContextRootMappingPath() throws Exception {
        doTestMapping("", "/foo/bar/*", "/foo/bar/foo2", "/foo2", "PATH");
    }

    private void doTestMapping(String contextPath, String mapping, String requestPath,
            String matchValue, String matchType) throws Exception {
        Tomcat tomcat = getTomcatInstance();

        // No file system docBase required
        Context ctx = tomcat.addContext(contextPath, null);

        Tomcat.addServlet(ctx, "Mapping", new MappingServlet());
        ctx.addServletMapping(mapping, "Mapping");

        tomcat.start();

        ByteChunk bc = getUrl("http://localhost:" + getPort() + contextPath + requestPath);
        String body = bc.toString();

        Assert.assertTrue(body, body.contains("MatchValue=[" + matchValue + "]"));
        Assert.assertTrue(body, body.contains("Pattern=[" + mapping + "]"));
        Assert.assertTrue(body, body.contains("MatchType=[" + matchType + "]"));
    }


    private static class MappingServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {
            resp.setContentType("text/plain;charset=UTF-8");
            PrintWriter pw = resp.getWriter();
            Mapping mapping = req.getMapping();
            pw.println("MatchValue=[" + mapping.getMatchValue() + "]");
            pw.println("Pattern=[" + mapping.getPattern() + "]");
            pw.println("MatchType=[" + mapping.getMatchType() + "]");
        }
    }
}
