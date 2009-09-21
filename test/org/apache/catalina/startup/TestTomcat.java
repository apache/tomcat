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

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Map;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.core.StandardContext;
import org.apache.catalina.deploy.ContextEnvironment;
import org.apache.tomcat.util.buf.ByteChunk;

public class TestTomcat extends TestTomcatBase {

    /**
     * Simple servlet to test in-line registration 
     */
    public static class HelloWorld extends HttpServlet {

        private static final long serialVersionUID = 1L;

        public void doGet(HttpServletRequest req, HttpServletResponse res) 
                throws IOException {
            res.getWriter().write("Hello world");
        }
    }

    /**
     * Simple servlet to test JNDI 
     */
    public static class HelloWorldJndi extends HttpServlet {

        private static final long serialVersionUID = 1L;

        private static final String JNDI_ENV_NAME = "test";
        
        public void doGet(HttpServletRequest req, HttpServletResponse res) 
                throws IOException {
            
            String name = null;
            
            try {
                Context initCtx = new InitialContext();
                Context envCtx = (Context) initCtx.lookup("java:comp/env");
                name = (String) envCtx.lookup(JNDI_ENV_NAME);
            } catch (NamingException e) {
                throw new IOException(e);
            }
            
            res.getWriter().write("Hello, " + name);
        }
    }

    /**
     * Servlet that tries to obtain a URL for WEB-INF/web.xml
     */
    public static class GetResource extends HttpServlet {
        
        private static final long serialVersionUID = 1L;
        
        public void doGet(HttpServletRequest req, HttpServletResponse res) 
        throws IOException {
            URL url = req.getServletContext().getResource("/WEB-INF/web.xml");
         
            res.getWriter().write("The URL obtained for /WEB-INF/web.xml was ");
            if (url == null) {
                res.getWriter().write("null");
            } else {
                res.getWriter().write(url.toString());
            }
        }
    }
    
    /** 
     * Start tomcat with a single context and one 
     * servlet - all programmatic, no server.xml or 
     * web.xml used.
     * 
     * @throws Exception 
     */
    public void testProgrammatic() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        
        // Must have a real docBase - just use temp
        StandardContext ctx = 
            tomcat.addContext("/", System.getProperty("java.io.tmpdir"));
        // You can customize the context by calling 
        // its API
        
        Tomcat.addServlet(ctx, "myServlet", new HelloWorld());
        ctx.addServletMapping("/", "myServlet");
        
        tomcat.start();
        
        ByteChunk res = getUrl("http://localhost:" + getPort() + "/");
        assertEquals("Hello world", res.toString());
    }

    public void testSingleWebapp() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        File appDir = 
            new File("output/build/webapps/examples");
        // app dir is relative to server home
        tomcat.addWebapp(null, "/examples", appDir.getAbsolutePath());
        
        tomcat.start();

        ByteChunk res = getUrl("http://localhost:" + getPort() +
                "/examples/servlets/servlet/HelloWorldExample");
        assertTrue(res.toString().indexOf("<h1>Hello World!</h1>") > 0);
    }
    
    public void testLaunchTime() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        long t0 = System.currentTimeMillis();
        tomcat.addContext(null, "/", ".");
        tomcat.start();
        System.err.println("Test time: " + 
                (System.currentTimeMillis() - t0));
     }

    
    /** 
     * Test for enabling JNDI.
     */
    public void testEnableNaming() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        
        // Must have a real docBase - just use temp
        StandardContext ctx = 
            tomcat.addContext("/", System.getProperty("java.io.tmpdir"));
        
        // You can customise the context by calling its API
        
        // Enable JNDI - it is disabled by default
        tomcat.enableNaming();

        ContextEnvironment environment = new ContextEnvironment();
        environment.setType("java.lang.String");
        environment.setName(HelloWorldJndi.JNDI_ENV_NAME);
        environment.setValue("Tomcat User");
        ctx.getNamingResources().addEnvironment(environment);
        
        Tomcat.addServlet(ctx, "jndiServlet", new HelloWorldJndi());
        ctx.addServletMapping("/", "jndiServlet");
        
        tomcat.start();
        
        ByteChunk res = getUrl("http://localhost:" + getPort() + "/");
        assertEquals("Hello, Tomcat User", res.toString());
    }


    /**
     * Test for https://issues.apache.org/bugzilla/show_bug.cgi?id=47866
     */
    public void testGetResource() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        
        // Must have a real docBase - just use temp
        StandardContext ctx = 
            tomcat.addContext("/", System.getProperty("java.io.tmpdir"));
        // You can customize the context by calling 
        // its API
        
        Tomcat.addServlet(ctx, "myServlet", new GetResource());
        ctx.addServletMapping("/", "myServlet");
        
        tomcat.start();
        
        int rc =getUrl("http://localhost:" + getPort() + "/", new ByteChunk(),
                null);
        assertEquals(HttpServletResponse.SC_OK, rc);
    }

    /**
     *  Wrapper for getting the response.
     */
    public static ByteChunk getUrl(String path) throws IOException {
        ByteChunk out = new ByteChunk();
        getUrl(path, out, null);
        return out;
    }

    public static int getUrl(String path, 
                             ByteChunk out, 
                             Map<String, List<String>> resHead) throws IOException {
        URL url = new URL(path);
        HttpURLConnection connection = 
            (HttpURLConnection) url.openConnection();
        connection.setReadTimeout(100000);
        connection.connect();
        int rc = connection.getResponseCode();
        if (resHead != null) {
            Map<String, List<String>> head = connection.getHeaderFields();
            resHead.putAll(head);
        }
        InputStream is = connection.getInputStream();
        BufferedInputStream bis = new BufferedInputStream(is);
        byte[] buf = new byte[2048];
        int rd = 0;
        while((rd = bis.read(buf)) > 0) {
            out.append(buf, 0, rd);
        }
        return rc;
    }
}
