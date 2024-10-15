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

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Set;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.WebResourceRoot;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.catalina.webresources.StandardRoot;
import org.apache.tomcat.util.buf.ByteChunk;

public class TestStandardContextAliases extends TomcatBaseTest {

    @Test
    public void testDirContextAliases() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        // No file system docBase required
        Context ctx = getProgrammaticRootContext();

        File lib = new File("webapps/examples/WEB-INF/lib");
        ctx.setResources(new StandardRoot(ctx));
        ctx.getResources().createWebResourceSet(
                WebResourceRoot.ResourceSetType.POST, "/WEB-INF/lib",
                lib.getAbsolutePath(), null, "/");


        Tomcat.addServlet(ctx, "test", new TestServlet());
        ctx.addServletMappingDecoded("/", "test");

        tomcat.start();

        ByteChunk res = getUrl("http://localhost:" + getPort() + "/");

        String result = res.toString();
        if (result == null) {
            result = "";
        }

        Assert.assertTrue(result.contains("00-PASS"));
        Assert.assertTrue(result.contains("01-PASS"));
        Assert.assertTrue(result.contains("02-PASS"));
    }


    /**
     * Looks for the JSTL JARs in WEB-INF/lib.
     */
    public static class TestServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {

            resp.setContentType("text/plain");

            ServletContext context = getServletContext();

            // Check resources individually
            URL url = context.getResource("/WEB-INF/lib/taglibs-standard-spec-1.2.5-migrated-0.0.1.jar");
            if (url != null) {
                resp.getWriter().write("00-PASS\n");
            }

            url = context.getResource("/WEB-INF/lib/taglibs-standard-impl-1.2.5-migrated-0.0.1.jar");
            if (url != null) {
                resp.getWriter().write("01-PASS\n");
            }

            // Check a directory listing
            Set<String> libs = context.getResourcePaths("/WEB-INF/lib");
            if (libs == null) {
                return;
            }

            if (!libs.contains("/WEB-INF/lib/taglibs-standard-spec-1.2.5-migrated-0.0.1.jar")) {
                return;
            }
            if (!libs.contains("/WEB-INF/lib/taglibs-standard-impl-1.2.5-migrated-0.0.1.jar")) {
                return;
            }

            resp.getWriter().write("02-PASS\n");
        }

    }
}
