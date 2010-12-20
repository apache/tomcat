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

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.Context;
import org.apache.catalina.startup.SimpleHttpClient;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;

public class TestMaxConnections extends TomcatBaseTest{

    Tomcat tomcat = null;
    static int soTimeout = 3000;
    static int connectTimeout = 1000;
    
    public void test1() throws Exception {
        init();
        start();
        ConnectThread[] t = new ConnectThread[10];
        int passcount = 0;
        int connectfail = 0;
        for (int i=0; i<t.length; i++) {
            t[i] = new ConnectThread();
            t[i].setName("ConnectThread["+i+"+]");
            t[i].start();
        }
        for (int i=0; i<t.length; i++) {
            t[i].join();
            if (t[i].passed) passcount++;
            if (t[i].connectfailed) connectfail++;
        }
        assertEquals("The number of successful requests should have been 5.",5, passcount);
        assertEquals("The number of failed connects should have been 5.",5, connectfail);
        stop();
    }
    

    private class ConnectThread extends Thread {
        public boolean passed = true;
        public boolean connectfailed = false;
        public void run() {
            try {
                TestKeepAliveClient client = new TestKeepAliveClient();
                client.doHttp10Request();
            }catch (Exception x) {
                passed = false;
                System.err.println(Thread.currentThread().getName()+" Error:"+x.getMessage());
                connectfailed = "connect timed out".equals(x.getMessage());
            }
        }
    }

    private boolean init;
    
    private synchronized void init() {
        if (init) return;
        
        Tomcat tomcat = getTomcatInstance();
        Context root = tomcat.addContext("", SimpleHttpClient.TEMP_DIR);
        Tomcat.addServlet(root, "Simple", new SimpleServlet());
        root.addServletMapping("/test", "Simple");
        tomcat.getConnector().setProperty("maxKeepAliveRequests", "5");
        tomcat.getConnector().setProperty("soTimeout", "20000");
        tomcat.getConnector().setProperty("keepAliveTimeout", "50000");
        tomcat.getConnector().setProperty("port", "8080");
        tomcat.getConnector().setProperty("maxConnections", "4");
        tomcat.getConnector().setProperty("acceptCount", "1");
        init = true;
    }

    private synchronized void start() throws Exception {
        tomcat = getTomcatInstance();
        init();
        tomcat.start();
    }
    
    private synchronized void stop() throws Exception {
        tomcat.stop();
    }
    
    private class TestKeepAliveClient extends SimpleHttpClient {

        private void doHttp10Request() throws Exception {
            
            long start = System.currentTimeMillis();
            // Open connection
            connect(connectTimeout,soTimeout);
            
            // Send request in two parts
            String[] request = new String[1];
            request[0] =
                "GET /test HTTP/1.0" + CRLF + CRLF;
            setRequest(request);
            boolean passed = false;
            processRequest(false); // blocks until response has been read
            long stop = System.currentTimeMillis();
            System.out.println(Thread.currentThread().getName()+" Request complete:"+(stop-start)+" ms.");
            passed = (this.readLine()==null);
            // Close the connection
            disconnect();
            reset();
            assertTrue(passed);
        }
        
        private void doHttp11Request() throws Exception {
            Tomcat tomcat = getTomcatInstance();
            init();
            tomcat.start();
            // Open connection
            connect();
            
            // Send request in two parts
            String[] request = new String[1];
            request[0] =
                "GET /test HTTP/1.1" + CRLF + 
                "Host: localhost" + CRLF +
                "Connection: Keep-Alive" + CRLF+
                "Keep-Alive: 300"+ CRLF+ CRLF;
            
            setRequest(request);
            
            for (int i=0; i<5; i++) {
                processRequest(false); // blocks until response has been read
                assertTrue(getResponseLine()!=null && getResponseLine().trim().startsWith("HTTP/1.1 200"));
            }
            boolean passed = (this.readLine()==null);
            // Close the connection
            disconnect();
            reset();
            tomcat.stop();
            assertTrue(passed);
        }
        
        @Override
        public boolean isResponseBodyOK() {
            return true;
        }
        
    }
    
    
    private static class SimpleServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        @Override
        protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            try {
                Thread.sleep(TestMaxConnections.soTimeout/2);
            }catch (InterruptedException x) {
                
            }
            resp.setContentLength(0);
            resp.flushBuffer();
        }
        
    }
    
}
