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

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import junit.framework.TestCase;

import org.apache.catalina.core.StandardContext;
import org.apache.tomcat.util.buf.ByteChunk;

public class TestTomcat extends TestCase {
    Tomcat tomcat;
    // if you run in eclipse - or tomcat src dir 
    String base = "./"; 
    File tempDir;
    static int port = 8001;
    long t0;
    
    /**
     * Simple servlet to test in-line registration 
     */
    public static class HelloWorld extends HttpServlet {
        public void doGet(HttpServletRequest req, HttpServletResponse res) 
            throws IOException {
          res.getWriter().write("Hello world");
        }
    }

    public void setUp() throws Exception {
        t0 = System.currentTimeMillis();
        tempDir = new File("output/tmp");
        tempDir.mkdir();
        
        tomcat = new Tomcat();
        tomcat.getHost().setAppBase(tempDir.getAbsolutePath());
        tomcat.setBaseDir(tempDir.getAbsolutePath());
          
        // If each test is running on same port - they
        // may interfere with each other (on unix at least)
        port++;
        tomcat.setPort(port);
    }
    
    public void tearDown() throws Exception {
        tomcat.stop();
        System.err.println("Test time: " + 
                (System.currentTimeMillis() - t0));
    }
    
    /** 
     * Start tomcat with a single context and one 
     * servlet - all programmatic, no server.xml or 
     * web.xml used.
     * 
     * @throws Exception 
     */
    public void testProgrammatic() throws Exception {
        
        StandardContext ctx = 
            tomcat.addContext("/", 
                    tempDir.getAbsolutePath());
        // You can customize the context by calling 
        // its API
        
        tomcat.addServlet(ctx, "myServlet", 
                new HelloWorld());
        ctx.addServletMapping("/", "myServlet");
        
        tomcat.start();
        
        ByteChunk res = getUrl("http://localhost:" + port + "/");
        assertEquals(res.toString(), "Hello world");
    }

    public void testSingleWebapp() throws Exception {
        // Currently in sandbox/tomcat-lite
        File appDir = 
            new File(base + "output/build/webapps/examples");
        // app dir is relative to server home
        StandardContext ctx = 
            tomcat.addWebapp(null, "/examples", 
                    appDir.getAbsolutePath());
        
        ctx.start();
        tomcat.start();

        ByteChunk res = getUrl("http://localhost:" + port + "/examples/servlets/servlet/HelloWorldExample");
        assertTrue(res.toString().indexOf("<h1>Hello World!</h1>") > 0);
    }
    
    public void testLaunchTime() throws Exception {
        tomcat.addContext(null, "/", base);
        tomcat.start();
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
