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

package org.apache.tomcat.security;

import java.io.File;
import java.io.IOException;
import java.io.Serial;

import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.buf.ByteChunk;

public class TestSecurity2010 extends TomcatBaseTest {

    /*
     * https://www.cve.org/CVERecord?id=CVE-2010-3718
     *
     * Fixed in https://github.com/apache/tomcat/commit/a697f7b52c4e3aea0c6763b33d413b54a518e883
     */
    @Test
    public void testCVE_2010_3718() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        Context ctx = tomcat.addContext("", null);

        Tomcat.addServlet(ctx, "test", new TempdirCheckServlet());
        ctx.addServletMappingDecoded("/", "test");

        tomcat.start();

        int status = getUrl("http://localhost:" + getPort() + "/", new ByteChunk(), null);

        Assert.assertEquals(HttpServletResponse.SC_OK, status);
    }

    public static class TempdirCheckServlet extends HttpServlet {
        @Serial
        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            ServletContext ctx = getServletContext();

            File originalWorkDir = (File) ctx.getAttribute(ServletContext.TEMPDIR);
            String originalPath = originalWorkDir != null ? originalWorkDir.getAbsolutePath() : "null";

            ctx.removeAttribute(ServletContext.TEMPDIR);
            File maliciousDir = new File("catalina_base/", "conf");
            ctx.setAttribute(ServletContext.TEMPDIR, maliciousDir);

            File currentWorkDir = (File) ctx.getAttribute(ServletContext.TEMPDIR);
            String currentPath = currentWorkDir != null ? currentWorkDir.getAbsolutePath() : "null";

            if (currentPath.equals(originalPath)) {
                resp.setStatus(HttpServletResponse.SC_OK);
            } else {
                resp.setStatus(HttpServletResponse.SC_EXPECTATION_FAILED);
            }
        }
    }
}
