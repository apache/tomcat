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

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.startup.SimpleHttpClient;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;

public class TestKeepAliveCount extends TomcatBaseTest {

    @Test
    public void testHttp10() throws Exception {
        TestKeepAliveClient client = new TestKeepAliveClient();
        client.doHttp10Request();
    }

    @Test
    public void testHttp11() throws Exception {
        TestKeepAliveClient client = new TestKeepAliveClient();
        client.doHttp11Request();
    }


    private class TestKeepAliveClient extends SimpleHttpClient {


        private boolean init;

        private synchronized void init() {
            if (init) {
                return;
            }

            Tomcat tomcat = getTomcatInstance();
            Context root = tomcat.addContext("", TEMP_DIR);
            Tomcat.addServlet(root, "Simple", new SimpleServlet());
            root.addServletMappingDecoded("/test", "Simple");
            Assert.assertTrue(tomcat.getConnector().setProperty("maxKeepAliveRequests", "5"));
            Assert.assertTrue(tomcat.getConnector().setProperty("connectionTimeout", "20000"));
            Assert.assertTrue(tomcat.getConnector().setProperty("keepAliveTimeout", "50000"));
            init = true;
        }

        private void doHttp10Request() throws Exception {
            Tomcat tomcat = getTomcatInstance();
            init();
            tomcat.start();
            setPort(tomcat.getConnector().getLocalPort());

            // Open connection
            connect();

            // Send request in two parts
            String[] request = new String[1];
            // @formatter:off
            request[0] =
                    "GET /test HTTP/1.0" + CRLF +
                    CRLF;
            // @formatter:on
            setRequest(request);
            processRequest(false); // blocks until response has been read
            boolean passed = (this.readLine() == null);
            // Close the connection
            disconnect();
            reset();
            tomcat.stop();
            Assert.assertTrue(passed);
        }

        private void doHttp11Request() throws Exception {
            Tomcat tomcat = getTomcatInstance();
            init();
            tomcat.start();
            setPort(tomcat.getConnector().getLocalPort());

            // Open connection
            connect();

            // Send request in two parts
            String[] request = new String[1];
            // @formatter:off
            request[0] =
                    "GET /test HTTP/1.1" + CRLF +
                    "Host: localhost" + CRLF +
                    "Connection: Keep-Alive" + CRLF +
                    "Keep-Alive: 300"+ CRLF +
                    CRLF;
            // @formatter:on

            setRequest(request);

            for (int i = 0; i < 5; i++) {
                processRequest(false); // blocks until response has been read
                Assert.assertTrue(getResponseLine() != null && getResponseLine().startsWith("HTTP/1.1 200 "));
            }
            boolean passed = (this.readLine() == null);
            // Close the connection
            disconnect();
            reset();
            tomcat.stop();
            Assert.assertTrue(passed);
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
            resp.setContentLength(0);
            resp.flushBuffer();
        }

    }

}
