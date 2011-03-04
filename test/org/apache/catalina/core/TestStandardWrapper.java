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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.annotation.HttpConstraint;
import javax.servlet.annotation.HttpMethodConstraint;
import javax.servlet.annotation.ServletSecurity;
import javax.servlet.annotation.ServletSecurity.EmptyRoleSemantic;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.Context;
import org.apache.catalina.Wrapper;
import org.apache.catalina.authenticator.BasicAuthenticator;
import org.apache.catalina.deploy.LoginConfig;
import org.apache.catalina.startup.TestTomcat.MapRealm;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.buf.ByteChunk;

public class TestStandardWrapper extends TomcatBaseTest {

    public void testSecurityAnnotationsSimple() throws Exception {
        doTest(DenyAllServlet.class.getName(), false, false, false);
    }

    public void testSecurityAnnotationsSubclass1() throws Exception {
        doTest(SubclassDenyAllServlet.class.getName(), false, false, false);
    }

    public void testSecurityAnnotationsSubclass2() throws Exception {
        doTest(SubclassAllowAllServlet.class.getName(), false, false, true);
    }

    public void testSecurityAnnotationsMethods1() throws Exception {
        doTest(MethodConstraintServlet.class.getName(), false, false, false);
    }

    public void testSecurityAnnotationsMethods2() throws Exception {
        doTest(MethodConstraintServlet.class.getName(), true, false, true);
    }

    public void testSecurityAnnotationsRole1() throws Exception {
        doTest(RoleAllowServlet.class.getName(), false, true, true);
    }

    public void testSecurityAnnotationsRole2() throws Exception {
        doTest(RoleDenyServlet.class.getName(), false, true, false);
    }

    public void testSecurityAnnotationsWebXmlPriority() throws Exception {

        // Setup Tomcat instance
        Tomcat tomcat = getTomcatInstance();
        
        File appDir = new File("test/webapp-3.0-fragments");
        tomcat.addWebapp(null, "", appDir.getAbsolutePath());
        
        tomcat.start();
        
        ByteChunk bc = new ByteChunk();
        int rc;
        rc = getUrl("http://localhost:" + getPort() +
                "/testStandardWrapper/securityAnnotationsWebXmlPriority",
                bc, null, null);
        
        assertNull(bc.toString());
        assertEquals(403, rc);
    }

    private void doTest(String servletClassName, boolean usePost,
            boolean useRole, boolean expect200) throws Exception {

        // Setup Tomcat instance
        Tomcat tomcat = getTomcatInstance();
        
        // Must have a real docBase - just use temp
        Context ctx =
            tomcat.addContext("", System.getProperty("java.io.tmpdir"));
        
        Wrapper wrapper = Tomcat.addServlet(ctx, "servlet", servletClassName);
        wrapper.setAsyncSupported(true);
        ctx.addServletMapping("/", "servlet");
        
        if (useRole) {
            MapRealm realm = new MapRealm();
            realm.addUser("testUser", "testPwd");
            realm.addUserRole("testUser", "testRole");
            ctx.setRealm(realm);
            
            ctx.setLoginConfig(new LoginConfig("BASIC", null, null, null));
            ctx.getPipeline().addValve(new BasicAuthenticator());
        }

        tomcat.start();
        
        ByteChunk bc = new ByteChunk();
        Map<String,List<String>> reqHeaders = null;
        if (useRole) {
            reqHeaders = new HashMap<String,List<String>>();
            List<String> authHeaders = new ArrayList<String>();
            // testUser, testPwd
            authHeaders.add("Basic dGVzdFVzZXI6dGVzdFB3ZA==");
            reqHeaders.put("Authorization", authHeaders);
        }

        int rc;
        if (usePost) {
            rc = postUrl(null, "http://localhost:" + getPort() + "/", bc,
                    reqHeaders, null);
        } else {
            rc = getUrl("http://localhost:" + getPort() + "/", bc, reqHeaders,
                    null);
        }
        
        if (expect200) {
            assertEquals("OK", bc.toString());
            assertEquals(200, rc);
        } else {
            assertNull(bc.toString());
            assertEquals(403, rc);
        }
    }

    public static class TestServlet extends HttpServlet {
        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {
            
            resp.setContentType("text/plain");
            resp.getWriter().print("OK");
        }
        
        @Override
        protected void doPost(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {
            doGet(req, resp);
        }
    }
    
    @ServletSecurity(@HttpConstraint(EmptyRoleSemantic.DENY))
    public static class DenyAllServlet extends TestServlet {
        private static final long serialVersionUID = 1L;
    }
    
    public static class SubclassDenyAllServlet extends DenyAllServlet {
        private static final long serialVersionUID = 1L;
    }
    
    @ServletSecurity(@HttpConstraint(EmptyRoleSemantic.PERMIT))
    public static class SubclassAllowAllServlet extends DenyAllServlet {
        private static final long serialVersionUID = 1L;
    }

    @ServletSecurity(value= @HttpConstraint(EmptyRoleSemantic.PERMIT),
        httpMethodConstraints = {
            @HttpMethodConstraint(value="GET",
                    emptyRoleSemantic = EmptyRoleSemantic.DENY)
        }
    )
    public static class MethodConstraintServlet extends TestServlet {
        private static final long serialVersionUID = 1L;
    }
    
    @ServletSecurity(@HttpConstraint(rolesAllowed = "testRole"))
    public static class RoleAllowServlet extends TestServlet {
        private static final long serialVersionUID = 1L;
    }

    @ServletSecurity(@HttpConstraint(rolesAllowed = "otherRole"))
    public static class RoleDenyServlet extends TestServlet {
        private static final long serialVersionUID = 1L;
    }
}
