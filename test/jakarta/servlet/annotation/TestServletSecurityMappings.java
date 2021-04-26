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
package jakarta.servlet.annotation;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import jakarta.servlet.ServletContainerInitializer;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRegistration;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.buf.ByteChunk;

@RunWith(Parameterized.class)
public class TestServletSecurityMappings extends TomcatBaseTest {

    @Parameters(name="{0}, {1}, {2}, {3}")
    public static Collection<Object[]> inputs() {
        List<Object[]> result = new ArrayList<>();
        result.add(new Object[] { Boolean.FALSE, Boolean.FALSE, Boolean.FALSE, Boolean.FALSE });
        result.add(new Object[] { Boolean.FALSE, Boolean.FALSE, Boolean.FALSE, Boolean.TRUE });
        result.add(new Object[] { Boolean.FALSE, Boolean.FALSE, Boolean.TRUE , Boolean.FALSE });
        result.add(new Object[] { Boolean.FALSE, Boolean.FALSE, Boolean.TRUE,  Boolean.TRUE });
        result.add(new Object[] { Boolean.FALSE, Boolean.TRUE,  Boolean.FALSE, Boolean.FALSE });
        result.add(new Object[] { Boolean.FALSE, Boolean.TRUE,  Boolean.FALSE, Boolean.TRUE });
        result.add(new Object[] { Boolean.FALSE, Boolean.TRUE,  Boolean.TRUE , Boolean.FALSE });
        result.add(new Object[] { Boolean.FALSE, Boolean.TRUE,  Boolean.TRUE,  Boolean.TRUE });
        result.add(new Object[] { Boolean.TRUE,  Boolean.FALSE, Boolean.FALSE, Boolean.FALSE });
        result.add(new Object[] { Boolean.TRUE,  Boolean.FALSE, Boolean.FALSE, Boolean.TRUE });
        result.add(new Object[] { Boolean.TRUE,  Boolean.FALSE, Boolean.TRUE , Boolean.FALSE });
        result.add(new Object[] { Boolean.TRUE,  Boolean.FALSE, Boolean.TRUE,  Boolean.TRUE });
        result.add(new Object[] { Boolean.TRUE,  Boolean.TRUE,  Boolean.FALSE, Boolean.FALSE });
        result.add(new Object[] { Boolean.TRUE,  Boolean.TRUE,  Boolean.FALSE, Boolean.TRUE });
        result.add(new Object[] { Boolean.TRUE,  Boolean.TRUE,  Boolean.TRUE , Boolean.FALSE });
        result.add(new Object[] { Boolean.TRUE,  Boolean.TRUE,  Boolean.TRUE,  Boolean.TRUE });
        return result;
    }

    @Parameter(0)
    public boolean redirectContextRoot;

    @Parameter(1)
    public boolean secureRoot;

    @Parameter(2)
    public boolean secureDefault;

    @Parameter(3)
    public boolean secureFoo;


    @Test
    public void doTestSecurityAnnotationsAddServlet() throws Exception {

        // Setup Tomcat instance
        Tomcat tomcat = getTomcatInstance();

        // No file system docBase required
        Context ctx = tomcat.addContext("/test", null);
        ctx.setMapperContextRootRedirectEnabled(redirectContextRoot);

        ServletContainerInitializer sci = new SCI(secureRoot, secureDefault, secureFoo);
        ctx.addServletContainerInitializer(sci, null);

        tomcat.start();

        ByteChunk bc = new ByteChunk();
        int rc;

        // Foo
        rc = getUrl("http://localhost:" + getPort() + "/test/foo", bc, false);
        if (secureFoo || secureDefault) {
            Assert.assertEquals(403, rc);
        } else {
            Assert.assertEquals(200, rc);
        }
        bc.recycle();

        // Default
        rc = getUrl("http://localhost:" + getPort() + "/test/something", bc, false);
        if (secureDefault) {
            Assert.assertEquals(403, rc);
        } else {
            Assert.assertEquals(200, rc);
        }
        bc.recycle();

        // Root
        rc = getUrl("http://localhost:" + getPort() + "/test", bc, false);
        if (redirectContextRoot) {
           Assert.assertEquals(302, rc);
        } else {
            if (secureRoot || secureDefault) {
                Assert.assertEquals(403, rc);
            } else {
                Assert.assertEquals(200, rc);
            }
        }
    }


    public static class SCI implements ServletContainerInitializer {

        private final boolean secureRoot;
        private final boolean secureDefault;
        private final boolean secureFoo;

        public SCI(boolean secureRoot, boolean secureDefault, boolean secureFoo) {
            this.secureRoot = secureRoot;
            this.secureDefault = secureDefault;
            this.secureFoo = secureFoo;
        }

        @Override
        public void onStartup(Set<Class<?>> c, ServletContext ctx)
                throws ServletException {

            ServletRegistration.Dynamic sr;
            if (secureRoot) {
                sr = ctx.addServlet("Root", SecureRoot.class.getName());
            } else {
                sr =ctx.addServlet("Root", Root.class.getName());
            }
            sr.addMapping("");

            if (secureDefault) {
                sr = ctx.addServlet("Default", SecureDefault.class.getName());
            } else {
                sr = ctx.addServlet("Default", Default.class.getName());
            }
            sr.addMapping("/");

            if (secureFoo) {
                sr = ctx.addServlet("Foo", SecureFoo.class.getName());
            } else {
                sr = ctx.addServlet("Foo", Foo.class.getName());
            }
            sr.addMapping("/foo");
        }
    }


    @ServletSecurity(@HttpConstraint(ServletSecurity.EmptyRoleSemantic.DENY))
    public static class SecureRoot extends HttpServlet {

        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {
            resp.getWriter().print("OK");
        }
    }


    public static class Root extends HttpServlet {

        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {
            resp.getWriter().print("OK");
        }
    }


    @ServletSecurity(@HttpConstraint(ServletSecurity.EmptyRoleSemantic.DENY))
    public static class SecureDefault extends HttpServlet {

        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {
            resp.getWriter().print("OK");
        }
    }


    public static class Default extends HttpServlet {

        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {
            resp.getWriter().print("OK");
        }
    }


    @ServletSecurity(@HttpConstraint(ServletSecurity.EmptyRoleSemantic.DENY))
    public static class SecureFoo extends HttpServlet {

        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {
            resp.getWriter().print("OK");
        }
    }


    public static class Foo extends HttpServlet {

        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {
            resp.getWriter().print("OK");
        }
    }
}
