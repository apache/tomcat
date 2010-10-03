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

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.Context;
import org.apache.catalina.deploy.FilterDef;
import org.apache.catalina.deploy.FilterMap;
import org.apache.catalina.startup.SimpleHttpClient;
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
        
        // Must have a real docBase - just use temp
        // Use the normal Tomcat ROOT context
        File root = new File("test/webapp-3.0");
        tomcat.addWebapp("", root.getAbsolutePath());
        
        tomcat.start();

        // Check path mapping works
        ByteChunk result = getUrl("http://localhost:" + getPort() +
        "/bug49922/foo");
        // Filter should only have been called once
        assertEquals("Filter", result.toString());

        // Check extension mapping works
        result = getUrl("http://localhost:" + getPort() +
        "/foo.do");
        // Filter should only have been called once
        assertEquals("Filter", result.toString());

        result = getUrl("http://localhost:" + getPort() +
                "/bug49922/index.do");
        // Filter should only have been called once
        assertEquals("Filter", result.toString());
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
    
    public static final class Bug49922Servlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {
            // NOOP
        }
        
    }
}
