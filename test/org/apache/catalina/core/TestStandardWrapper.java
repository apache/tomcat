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
import java.security.Principal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.servlet.Servlet;
import jakarta.servlet.ServletContainerInitializer;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRegistration;
import jakarta.servlet.annotation.HttpConstraint;
import jakarta.servlet.annotation.HttpMethodConstraint;
import jakarta.servlet.annotation.ServletSecurity;
import jakarta.servlet.annotation.ServletSecurity.EmptyRoleSemantic;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.Wrapper;
import org.apache.catalina.authenticator.BasicAuthenticator;
import org.apache.catalina.realm.MessageDigestCredentialHandler;
import org.apache.catalina.startup.TesterMapRealm;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.descriptor.web.LoginConfig;

public class TestStandardWrapper extends TomcatBaseTest {

    @Test
    public void testSecurityAnnotationsSimple() throws Exception {
        doTest(DenyAllServlet.class.getName(), false, false, false, false);
    }

    @Test
    public void testSecurityAnnotationsSubclass1() throws Exception {
        doTest(SubclassDenyAllServlet.class.getName(),
                false, false, false,false);
    }

    @Test
    public void testSecurityAnnotationsSubclass2() throws Exception {
        doTest(SubclassAllowAllServlet.class.getName(),
                false, false, true, false);
    }

    @Test
    public void testSecurityAnnotationsMethods1() throws Exception {
        doTest(MethodConstraintServlet.class.getName(),
                false, false, false, false);
    }

    @Test
    public void testSecurityAnnotationsMethods2() throws Exception {
        doTest(MethodConstraintServlet.class.getName(),
                true, false, true, false);
    }

    @Test
    public void testSecurityAnnotationsRole1() throws Exception {
        doTest(RoleAllowServlet.class.getName(), false, true, true, false);
    }

    @Test
    public void testSecurityAnnotationsRole2() throws Exception {
        doTest(RoleDenyServlet.class.getName(), false, true, false, false);
    }

    @Test
    public void testSecurityAnnotationsUncoveredGet01() throws Exception {
        // Use a POST with role - should be allowed
        doTest(UncoveredGetServlet.class.getName(), true, true, true, false);
    }

    @Test
    public void testSecurityAnnotationsUncoveredGet02() throws Exception {
        // Use a POST with role - should be allowed
        doTest(UncoveredGetServlet.class.getName(), true, true, true, true);
    }

    @Test
    public void testSecurityAnnotationsUncoveredGet03() throws Exception {
        // Use a POST no role - should be blocked
        doTest(UncoveredGetServlet.class.getName(), true, false, false, false);
    }

    @Test
    public void testSecurityAnnotationsUncoveredGet04() throws Exception {
        // Use a POST no role - should be blocked
        doTest(UncoveredGetServlet.class.getName(), true, false, false, true);
    }

    @Test
    public void testSecurityAnnotationsUncoveredGet05() throws Exception {
        // Use a GET with role - should be allowed as denyUncovered is false
        doTest(UncoveredGetServlet.class.getName(), false, true, true, false);
    }

    @Test
    public void testSecurityAnnotationsUncoveredGet06() throws Exception {
        // Use a GET with role - should be blocked as denyUncovered is true
        doTest(UncoveredGetServlet.class.getName(), false, true, false, true);
    }

    @Test
    public void testSecurityAnnotationsUncoveredGet07() throws Exception {
        // Use a GET no role - should be allowed as denyUncovered is false
        doTest(UncoveredGetServlet.class.getName(), false, false, true, false);
    }

    @Test
    public void testSecurityAnnotationsUncoveredGet08() throws Exception {
        // Use a GET no role - should be blocked as denyUncovered is true
        doTest(UncoveredGetServlet.class.getName(), true, false, false, true);
    }

    @Test
    public void testSecurityAnnotationsWebXmlPriority() throws Exception {

        // Setup Tomcat instance
        Tomcat tomcat = getTomcatInstance();

        File appDir = new File("test/webapp-fragments");
        Context ctx = tomcat.addWebapp(null, "", appDir.getAbsolutePath());
        skipTldsForResourceJars(ctx);

        tomcat.start();

        ByteChunk bc = new ByteChunk();
        int rc;
        rc = getUrl("http://localhost:" + getPort() +
                "/testStandardWrapper/securityAnnotationsWebXmlPriority",
                bc, null, null);

        Assert.assertTrue(bc.getLength() > 0);
        Assert.assertEquals(403, rc);
    }

    @Test
    public void testSecurityAnnotationsMetaDataPriority() throws Exception {
        getTomcatInstanceTestWebapp(false, true);

        ByteChunk bc = new ByteChunk();
        int rc;
        rc = getUrl("http://localhost:" + getPort() +
                "/test/testStandardWrapper/securityAnnotationsMetaDataPriority",
                bc, null, null);

        Assert.assertEquals("OK", bc.toString());
        Assert.assertEquals(200, rc);
    }

    @Test
    public void testSecurityAnnotationsAddServlet1() throws Exception {
        doTestSecurityAnnotationsAddServlet(false);
    }

    @Test
    public void testSecurityAnnotationsAddServlet2() throws Exception {
        doTestSecurityAnnotationsAddServlet(true);
    }

    @Test
    public void testSecurityAnnotationsNoWebXmlConstraints() throws Exception {
        // Setup Tomcat instance
        Tomcat tomcat = getTomcatInstance();

        File appDir = new File("test/webapp-servletsecurity-a");
        tomcat.addWebapp(null, "", appDir.getAbsolutePath());

        tomcat.start();

        ByteChunk bc = new ByteChunk();
        int rc;
        rc = getUrl("http://localhost:" + getPort() + "/",
                bc, null, null);

        Assert.assertTrue(bc.getLength() > 0);
        Assert.assertEquals(403, rc);
    }

    @Test
    public void testSecurityAnnotationsNoWebXmlLoginConfig() throws Exception {
        // Setup Tomcat instance
        Tomcat tomcat = getTomcatInstance();

        File appDir = new File("test/webapp-servletsecurity-b");
        tomcat.addWebapp(null, "", appDir.getAbsolutePath());

        tomcat.start();

        ByteChunk bc = new ByteChunk();
        int rc;
        rc = getUrl("http://localhost:" + getPort() + "/protected.jsp",
                bc, null, null);

        Assert.assertTrue(bc.getLength() > 0);
        Assert.assertEquals(403, rc);

        bc.recycle();

        rc = getUrl("http://localhost:" + getPort() + "/unprotected.jsp",
                bc, null, null);

        Assert.assertEquals(200, rc);
        Assert.assertTrue(bc.toString().contains("00-OK"));
    }

    @Test
    public void testRoleMappingInEngine() throws Exception {
        doTestRoleMapping("engine");
    }

    @Test
    public void testRoleMappingInHost() throws Exception {
        doTestRoleMapping("host");
    }

    @Test
    public void testRoleMappingInContext() throws Exception {
        doTestRoleMapping("context");
    }

    private void doTestRoleMapping(String realmContainer)
            throws Exception {
        // Setup Tomcat instance
        Tomcat tomcat = getTomcatInstance();

        // No file system docBase required
        Context ctx = getProgrammaticRootContext();
        ctx.addRoleMapping("testRole", "very-complex-role-name");

        Wrapper wrapper = Tomcat.addServlet(ctx, "servlet", RoleAllowServlet.class.getName());
        ctx.addServletMappingDecoded("/", "servlet");

        ctx.setLoginConfig(new LoginConfig("BASIC", null, null, null));
        ctx.getPipeline().addValve(new BasicAuthenticator());

        TesterMapRealm realm = new TesterMapRealm();
        MessageDigestCredentialHandler ch = new MessageDigestCredentialHandler();
        ch.setAlgorithm("SHA");
        realm.setCredentialHandler(ch);

        /* Attach the realm to the appropriate container, but role mapping must
         * always succeed because it is evaluated at context level.
         */
        if (realmContainer.equals("engine")) {
            tomcat.getEngine().setRealm(realm);
        } else if (realmContainer.equals("host")) {
            tomcat.getHost().setRealm(realm);
        } else if (realmContainer.equals("context")) {
            ctx.setRealm(realm);
        } else {
            throw new IllegalArgumentException("realmContainer is invalid");
        }

        realm.addUser("testUser", ch.mutate("testPwd"));
        realm.addUserRole("testUser", "testRole1");
        realm.addUserRole("testUser", "very-complex-role-name");
        realm.addUserRole("testUser", "another-very-complex-role-name");

        tomcat.start();

        Principal p = realm.authenticate("testUser", "testPwd");

        Assert.assertNotNull(p);
        Assert.assertEquals("testUser", p.getName());
        // This one is mapped
        Assert.assertTrue(realm.hasRole(wrapper, p, "testRole"));
        Assert.assertTrue(realm.hasRole(wrapper, p, "testRole1"));
        Assert.assertFalse(realm.hasRole(wrapper, p, "testRole2"));
        Assert.assertTrue(realm.hasRole(wrapper, p, "very-complex-role-name"));
        Assert.assertTrue(realm.hasRole(wrapper, p, "another-very-complex-role-name"));

        // This now tests RealmBase#hasResourcePermission() because we need a wrapper
        // to be passed from an authenticator
        ByteChunk bc = new ByteChunk();
        Map<String, List<String>> reqHeaders = new HashMap<>();
        List<String> authHeaders = new ArrayList<>();
        // testUser, testPwd
        authHeaders.add("Basic dGVzdFVzZXI6dGVzdFB3ZA==");
        reqHeaders.put("Authorization", authHeaders);

        int rc = getUrl("http://localhost:" + getPort() + "/", bc, reqHeaders,
                null);

        Assert.assertEquals("OK", bc.toString());
        Assert.assertEquals(200, rc);
    }

    private void doTestSecurityAnnotationsAddServlet(boolean useCreateServlet)
            throws Exception {

        // Setup Tomcat instance
        Tomcat tomcat = getTomcatInstance();

        // No file system docBase required
        Context ctx = getProgrammaticRootContext();

        Servlet s = new DenyAllServlet();
        ServletContainerInitializer sci = new SCI(s, useCreateServlet);
        ctx.addServletContainerInitializer(sci, null);

        tomcat.start();

        ByteChunk bc = new ByteChunk();
        int rc;
        rc = getUrl("http://localhost:" + getPort() + "/", bc, null, null);

        if (useCreateServlet) {
            Assert.assertTrue(bc.getLength() > 0);
            Assert.assertEquals(403, rc);
        } else {
            Assert.assertEquals("OK", bc.toString());
            Assert.assertEquals(200, rc);
        }
    }

    private void doTest(String servletClassName, boolean usePost,
            boolean useRole, boolean expect200, boolean denyUncovered)
            throws Exception {

        // Setup Tomcat instance
        Tomcat tomcat = getTomcatInstance();

        // No file system docBase required
        Context ctx = getProgrammaticRootContext();

        ctx.setDenyUncoveredHttpMethods(denyUncovered);

        Wrapper wrapper = Tomcat.addServlet(ctx, "servlet", servletClassName);
        wrapper.setAsyncSupported(true);
        ctx.addServletMappingDecoded("/", "servlet");

        if (useRole) {
            TesterMapRealm realm = new TesterMapRealm();
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
            reqHeaders = new HashMap<>();
            List<String> authHeaders = new ArrayList<>();
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
            Assert.assertEquals("OK", bc.toString());
            Assert.assertEquals(200, rc);
        } else {
            Assert.assertTrue(bc.getLength() > 0);
            Assert.assertEquals(403, rc);
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

    @ServletSecurity(httpMethodConstraints = {
            @HttpMethodConstraint(value="POST",rolesAllowed = "testRole")
        }
    )
    public static class UncoveredGetServlet extends TestServlet {
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

    public static class SCI implements ServletContainerInitializer {

        private Servlet servlet;
        private boolean createServlet;

        public SCI(Servlet servlet, boolean createServlet) {
            this.servlet = servlet;
            this.createServlet = createServlet;
        }

        @Override
        public void onStartup(Set<Class<?>> c, ServletContext ctx)
                throws ServletException {
            Servlet s;

            if (createServlet) {
                s = ctx.createServlet(servlet.getClass());
            } else {
                s = servlet;
            }
            ServletRegistration.Dynamic r = ctx.addServlet("servlet", s);
            r.addMapping("/");
        }
    }
}
