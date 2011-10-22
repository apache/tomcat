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
package org.apache.catalina.startup;

import java.io.File;
import java.io.IOException;
import java.util.Set;

import javax.servlet.Servlet;
import javax.servlet.ServletContainerInitializer;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRegistration;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import org.apache.catalina.core.StandardContext;
import org.apache.tomcat.util.buf.ByteChunk;

public class TestContextConfig extends TomcatBaseTest {

    @Test
    public void testOverrideWithSCIDefaultName() throws Exception {
        doTestOverrideDefaultServletWithSCI("default");
    }

    @Test
    public void testOverrideWithSCIDefaultMapping() throws Exception {
        doTestOverrideDefaultServletWithSCI("anything");
    }

    private void doTestOverrideDefaultServletWithSCI(String servletName)
            throws Exception{

        Tomcat tomcat = getTomcatInstance();

        File appDir = new File("test/webapp-3.0");
        StandardContext ctxt = (StandardContext) tomcat.addContext(null,
                "/test", appDir.getAbsolutePath());
        ctxt.setDefaultWebXml(new File("conf/web.xml").getAbsolutePath());
        ctxt.addLifecycleListener(new ContextConfig());

        ctxt.addServletContainerInitializer(
                new CustomDefaultServletSCI(servletName), null);

        tomcat.start();

        ByteChunk res = new ByteChunk();

        int rc =getUrl("http://localhost:" + getPort() + "/test", res, null);

        // Check return code
        assertEquals(HttpServletResponse.SC_OK, rc);

        // Check context
        assertEquals("OK - Custom default Servlet", res.toString());
    }

    @Test
    public void testBug51396() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        File appDir =  new File("test/webapp-3.0-fragments");
        // app dir is relative to server home
        tomcat.addWebapp(null, "/test", appDir.getAbsolutePath());

        tomcat.start();

        ByteChunk bc = new ByteChunk();
        int rc = getUrl("http://localhost:" + getPort() +
                "/test/bug51396.jsp", bc, null);

        assertEquals(HttpServletResponse.SC_OK, rc);
        assertTrue(bc.toString().contains("<p>OK</p>"));
    }

    private static class CustomDefaultServletSCI
            implements ServletContainerInitializer {

        private String servletName;

        public CustomDefaultServletSCI(String servletName) {
            this.servletName = servletName;
        }

        @Override
        public void onStartup(Set<Class<?>> c, ServletContext ctx)
                throws ServletException {
            Servlet s = new CustomDefaultServlet();
            ServletRegistration.Dynamic r = ctx.addServlet(servletName, s);
            r.addMapping("/");
        }

    }

    private static class CustomDefaultServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {
            resp.setContentType("text/plain");
            resp.getWriter().print("OK - Custom default Servlet");
        }
    }
}
