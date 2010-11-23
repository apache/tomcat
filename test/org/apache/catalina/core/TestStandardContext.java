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
import java.util.Set;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.HttpConstraintElement;
import javax.servlet.Servlet;
import javax.servlet.ServletContainerInitializer;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRegistration;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.ServletSecurityElement;
import javax.servlet.annotation.ServletSecurity.TransportGuarantee;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.Context;
import org.apache.catalina.authenticator.BasicAuthenticator;
import org.apache.catalina.deploy.FilterDef;
import org.apache.catalina.deploy.FilterMap;
import org.apache.catalina.deploy.LoginConfig;
import org.apache.catalina.startup.SimpleHttpClient;
import org.apache.catalina.startup.TestTomcat.MapRealm;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.buf.ByteChunk;

public class TestStandardContext extends TomcatBaseTest {

    private static final String REQUEST =
        "GET / HTTP/1.1\r\n" +
        "Host: anything\r\n" +
        "Connection: close\r\n" +
        "\r\n";

    public void testBug46243() throws Exception {
        
        // Set up a container
        Tomcat tomcat = getTomcatInstance();
        
        File docBase = new File(tomcat.getHost().getAppBase(), "ROOT");
        if (!docBase.exists() && !docBase.mkdirs()) {
            fail("Unable to create docBase");
        }
        
        Context root = tomcat.addContext("", "ROOT");
       
        // Add test a filter that fails
        FilterDef filterDef = new FilterDef();
        filterDef.setFilterClass(Bug46243Filter.class.getName());
        filterDef.setFilterName("Bug46243");
        root.addFilterDef(filterDef);
        FilterMap filterMap = new FilterMap();
        filterMap.setFilterName("Bug46243");
        filterMap.addURLPattern("*");
        root.addFilterMap(filterMap);

        // Add a test servlet so there is something to generate a response if
        // it works (although it shouldn't)
        Tomcat.addServlet(root, "Bug46243", new HelloWorldServlet());
        root.addServletMapping("/", "Bug46243");
        
        tomcat.start();
        
        // Configure the client
        Bug46243Client client = new Bug46243Client();
        client.setPort(getPort());
        client.setRequest(new String[] { REQUEST });

        client.connect();
        client.processRequest();
        assertTrue(client.isResponse404());
    }
    
    private static final class Bug46243Client extends SimpleHttpClient {
        @Override
        public boolean isResponseBodyOK() {
            // Don't care about the body in this test
            return true;
        }
    }
    
    public static final class Bug46243Filter implements Filter {
        
        @Override
        public void destroy() {
            // NOOP
        }

        @Override
        public void doFilter(ServletRequest request, ServletResponse response,
                FilterChain chain) throws IOException, ServletException {
            // If it works, do nothing
            chain.doFilter(request, response);
        }

        @Override
        public void init(FilterConfig filterConfig) throws ServletException {
            throw new ServletException("Init fail", new ClassNotFoundException());
        }
        
    }


    public void testBug49922() throws Exception {
        
        // Set up a container
        Tomcat tomcat = getTomcatInstance();
        
        File root = new File("test/webapp-3.0");
        tomcat.addWebapp("", root.getAbsolutePath());
        
        tomcat.start();
        ByteChunk result;

        // Check filter and servlet aren't called
        result = getUrl("http://localhost:" + getPort() +
                "/bug49922/foo");
        assertNull(result.toString());

        // Check extension mapping works
        result = getUrl("http://localhost:" + getPort() + "/foo.do");
        assertEquals("FilterServlet", result.toString());

        // Check path mapping works
        result = getUrl("http://localhost:" + getPort() + "/bug49922/servlet");
        assertEquals("FilterServlet", result.toString());

        // Check servlet name mapping works
        result = getUrl("http://localhost:" + getPort() + "/foo.od");
        assertEquals("FilterServlet", result.toString());

        // Check filter is only called once
        result = getUrl("http://localhost:" + getPort() +
                "/bug49922/servlet/foo.do");
        assertEquals("FilterServlet", result.toString());
        result = getUrl("http://localhost:" + getPort() +
                "/bug49922/servlet/foo.od");
        assertEquals("FilterServlet", result.toString());

        // Check dispatcher mapping
        result = getUrl("http://localhost:" + getPort() +
                "/bug49922/target");
        assertEquals("Target", result.toString());
        result = getUrl("http://localhost:" + getPort() +
                "/bug49922/forward");
        assertEquals("FilterTarget", result.toString());
        result = getUrl("http://localhost:" + getPort() +
                "/bug49922/include");
        assertEquals("IncludeFilterTarget", result.toString());
    }

    
    public static final class Bug49922Filter implements Filter {
        
        @Override
        public void destroy() {
            // NOOP
        }

        @Override
        public void doFilter(ServletRequest request, ServletResponse response,
                FilterChain chain) throws IOException, ServletException {
            response.setContentType("text/plain");
            response.getWriter().print("Filter");
            chain.doFilter(request, response);
        }

        @Override
        public void init(FilterConfig filterConfig) throws ServletException {
            // NOOP
        }
    }
    
    public static final class Bug49922ForwardServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {
            req.getRequestDispatcher("/bug49922/target").forward(req, resp);
        }
        
    }

    public static final class Bug49922IncludeServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {
            resp.setContentType("text/plain");
            resp.getWriter().print("Include");
            req.getRequestDispatcher("/bug49922/target").include(req, resp);
        }
        
    }

    public static final class Bug49922TargetServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {
            resp.setContentType("text/plain");
            resp.getWriter().print("Target");
        }
        
    }

    public static final class Bug49922Servlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {
            resp.setContentType("text/plain");
            resp.getWriter().print("Servlet");
        }
        
    }
    
    public void testBug50015() throws Exception {
        // Set up a container
        Tomcat tomcat = getTomcatInstance();

        // Must have a real docBase - just use temp
        File docBase = new File(System.getProperty("java.io.tmpdir"));
        Context ctx = tomcat.addContext("", docBase.getAbsolutePath());

        // Setup realm
        MapRealm realm = new MapRealm();
        realm.addUser("tomcat", "tomcat");
        realm.addUserRole("tomcat", "tomcat");
        ctx.setRealm(realm);

        // Configure app for BASIC auth
        LoginConfig lc = new LoginConfig();
        lc.setAuthMethod("BASIC");
        ctx.setLoginConfig(lc);
        ctx.getPipeline().addValve(new BasicAuthenticator());

        // Add ServletContainerInitializer
        ServletContainerInitializer sci = new Bug50015SCI();
        ctx.addServletContainerInitializer(sci, null);
        
        // Start the context
        tomcat.start();
        
        // Request the first servlet
        ByteChunk bc = new ByteChunk();
        int rc = getUrl("http://localhost:" + getPort() + "/bug50015",
                bc, null);
        
        // Check for a 401
        assertNotSame("OK", bc.toString());
        assertEquals(401, rc);
    }
    
    public static final class Bug50015SCI
            implements ServletContainerInitializer {

        @Override
        public void onStartup(Set<Class<?>> c, ServletContext ctx)
                throws ServletException {
            // Register and map servlet
            Servlet s = new Bug50015Servlet();
            ServletRegistration.Dynamic sr = ctx.addServlet("bug50015", s);
            sr.addMapping("/bug50015");
            
            // Limit access to users in the Tomcat role
            HttpConstraintElement hce = new HttpConstraintElement(
                    TransportGuarantee.NONE, "tomcat");
            ServletSecurityElement sse = new ServletSecurityElement(hce);
            sr.setServletSecurity(sse);
        }
    }
    
    public static final class Bug50015Servlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {
            resp.setContentType("text/plain");
            resp.getWriter().write("OK");
        }
        
    }
}
