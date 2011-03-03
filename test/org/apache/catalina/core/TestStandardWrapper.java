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

import javax.servlet.ServletException;
import javax.servlet.annotation.HttpConstraint;
import javax.servlet.annotation.ServletSecurity;
import javax.servlet.annotation.ServletSecurity.EmptyRoleSemantic;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.Context;
import org.apache.catalina.Wrapper;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.buf.ByteChunk;

public class TestStandardWrapper extends TomcatBaseTest {

    public void testSecurityAnnotations1() throws Exception {
        // Setup Tomcat instance
        Tomcat tomcat = getTomcatInstance();
        
        // Must have a real docBase - just use temp
        Context ctx =
            tomcat.addContext("", System.getProperty("java.io.tmpdir"));
        
        Wrapper wrapper = Tomcat.addServlet(ctx, "servlet",
                "org.apache.catalina.core.TestStandardWrapper$DenyServlet");
        wrapper.setAsyncSupported(true);
        ctx.addServletMapping("/", "servlet");
        
        tomcat.start();
        
        // Call the servlet once
        ByteChunk bc = new ByteChunk();
        int rc = getUrl("http://localhost:" + getPort() + "/", bc, null);
        
        assertNull(bc.toString());
        assertEquals(403, rc);
    }

    @ServletSecurity(@HttpConstraint(EmptyRoleSemantic.DENY))
    public static class DenyServlet extends HttpServlet {
        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {
            
            resp.setContentType("text/plain");
            resp.getWriter().print("FAIL");
        }
    }
}
