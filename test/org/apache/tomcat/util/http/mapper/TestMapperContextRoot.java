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
package org.apache.tomcat.util.http.mapper;

import java.io.IOException;

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

public class TestMapperContextRoot extends TomcatBaseTest{

    @Test
    public void testBug53339() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        tomcat.enableNaming();

        // Must have a real docBase - just use temp
        Context ctx =
            tomcat.addContext("", System.getProperty("java.io.tmpdir"));

        Tomcat.addServlet(ctx, "Bug53356", new Bug53356Servlet());
        ctx.addServletMapping("", "Bug53356");

        tomcat.start();

        ByteChunk body = getUrl("http://localhost:" + getPort());

        Assert.assertEquals("OK", body.toString());
    }

    private static class Bug53356Servlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {
            // Confirm behaviour as per Servler 12.2
            boolean pass = "/".equals(req.getPathInfo());
            if (pass) {
                pass = "".equals(req.getServletPath());
            }
            if (pass) {
                pass = "".equals(req.getContextPath());
            }

            resp.setContentType("text/plain");
            if (pass) {
                resp.getWriter().write("OK");
            } else {
                resp.getWriter().write("FAIL");
            }
        }
    }
}
