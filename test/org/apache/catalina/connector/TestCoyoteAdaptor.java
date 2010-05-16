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
package org.apache.catalina.connector;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.buf.ByteChunk;

public class TestCoyoteAdaptor extends TomcatBaseTest {

    public void testPathParams() throws Exception {
        // Setup Tomcat instance
        Tomcat tomcat = getTomcatInstance();
        
        // Must have a real docBase - just use temp
        Context ctx = 
            tomcat.addContext("/", System.getProperty("java.io.tmpdir"));

        Tomcat.addServlet(ctx, "servlet", new PathParamServlet());
        ctx.addServletMapping("/", "servlet");
        
        tomcat.start();
        
        testPath("/", "none");
        testPath("/;jsessionid=1234", "1234");
        testPath("/foo;jsessionid=1234", "1234");
        testPath("/foo;jsessionid=1234;dummy", "1234");
        testPath("/foo;jsessionid=1234;dummy=5678", "1234");
        testPath("/foo;jsessionid=1234;=5678", "1234");
        testPath("/foo;jsessionid=1234/bar", "1234");
    }

    private void testPath(String path, String expected) throws Exception {
        ByteChunk res = getUrl("http://localhost:" + getPort() + path);
        assertEquals(expected, res.toString());
    }

    private static class PathParamServlet extends HttpServlet {
        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) 
                throws ServletException, IOException {
            resp.setContentType("text/plain");
            PrintWriter pw = resp.getWriter();
            String sessionId = req.getRequestedSessionId();
            if (sessionId == null) {
                sessionId = "none";
            }
            pw.write(sessionId);
        }
    }
}
