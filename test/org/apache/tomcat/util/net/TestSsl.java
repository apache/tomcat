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
package org.apache.tomcat.util.net;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;

import javax.net.ssl.HandshakeCompletedEvent;
import javax.net.ssl.HandshakeCompletedListener;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import static org.junit.Assert.assertTrue;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.Wrapper;
import org.apache.catalina.startup.TesterServlet;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.websocket.server.WsContextListener;

/**
 * The keys and certificates used in this file are all available in svn and were
 * generated using a test CA the files for which are in the Tomcat PMC private
 * repository since not all of them are AL2 licensed.
 */
public class TestSsl extends TomcatBaseTest {

    @Test
    public void testSimpleSsl() throws Exception {
        TesterSupport.configureClientSsl();

        Tomcat tomcat = getTomcatInstance();

        File appDir = new File(getBuildDirectory(), "webapps/examples");
        org.apache.catalina.Context ctxt  = tomcat.addWebapp(
                null, "/examples", appDir.getAbsolutePath());
        ctxt.addApplicationListener(WsContextListener.class.getName());

        TesterSupport.initSsl(tomcat);

        tomcat.start();
        ByteChunk res = getUrl("https://localhost:" + getPort() +
            "/examples/servlets/servlet/HelloWorldExample");
        assertTrue(res.toString().indexOf("<h1>Hello World!</h1>") > 0);
    }

    @Test
    public void testKeyPass() throws Exception {
        TesterSupport.configureClientSsl();

        Tomcat tomcat = getTomcatInstance();

        File appDir = new File(getBuildDirectory(), "webapps/examples");
        org.apache.catalina.Context ctxt  = tomcat.addWebapp(
                null, "/examples", appDir.getAbsolutePath());
        ctxt.addApplicationListener(WsContextListener.class.getName());

        TesterSupport.initSsl(tomcat, "localhost-copy1.jks", "changeit",
                "tomcatpass");

        tomcat.start();
        ByteChunk res = getUrl("https://localhost:" + getPort() +
            "/examples/servlets/servlet/HelloWorldExample");
        assertTrue(res.toString().indexOf("<h1>Hello World!</h1>") > 0);
    }


    @Test
    public void testRenegotiateWorks() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        Assume.assumeTrue("SSL renegotiation has to be supported for this test",
                TesterSupport.isRenegotiationSupported(getTomcatInstance()));

        Context root = tomcat.addContext("", TEMP_DIR);
        Wrapper w =
            Tomcat.addServlet(root, "tester", new TesterServlet());
        w.setAsyncSupported(true);
        root.addServletMapping("/", "tester");

        TesterSupport.initSsl(tomcat);

        tomcat.start();

        SSLContext sslCtx = SSLContext.getInstance("TLS");
        sslCtx.init(null, TesterSupport.getTrustManagers(), null);
        SSLSocketFactory socketFactory = sslCtx.getSocketFactory();
        SSLSocket socket = (SSLSocket) socketFactory.createSocket("localhost",
                getPort());

        OutputStream os = socket.getOutputStream();
        InputStream is = socket.getInputStream();
        Reader r = new InputStreamReader(is);

        doRequest(os, r);

        TesterHandshakeListener listener = new TesterHandshakeListener();
        socket.addHandshakeCompletedListener(listener);

        socket.startHandshake();

        // One request should be sufficient
        int requestCount = 0;
        int listenerComplete = 0;
        try {
            while (requestCount < 10) {
                requestCount++;
                doRequest(os, r);
                if (listener.isComplete() && listenerComplete == 0) {
                    listenerComplete = requestCount;
                }
            }
        } catch (AssertionError | IOException e) {
            String message = "Failed on request number " + requestCount
                    + " after startHandshake(). " + e.getMessage();
            log.error(message, e);
            Assert.fail(message);
        }

        Assert.assertTrue(listener.isComplete());
        System.out.println("Renegotiation completed after " + listenerComplete + " requests");
    }

    private void doRequest(OutputStream os, Reader r) throws IOException {
        char[] expectedResponseLine = "HTTP/1.1 200 \r\n".toCharArray();

        os.write("GET /tester HTTP/1.1\r\n".getBytes());
        os.write("Host: localhost\r\n".getBytes());
        os.write("Connection: Keep-Alive\r\n\r\n".getBytes());
        os.flush();

        // First check we get the expected response line
        for (char c : expectedResponseLine) {
            int read = r.read();
            Assert.assertEquals(c, read);
        }

        // Skip to the end of the headers
        char[] endOfHeaders ="\r\n\r\n".toCharArray();
        int found = 0;
        while (found != endOfHeaders.length) {
            if (r.read() == endOfHeaders[found]) {
                found++;
            } else {
                found = 0;
            }
        }

        // Read the body
        char[] expectedBody = "OK".toCharArray();
        for (char c : expectedBody) {
            int read = r.read();
            Assert.assertEquals(c, read);
        }
    }

    private static class TesterHandshakeListener implements HandshakeCompletedListener {

        private volatile boolean complete = false;

        @Override
        public void handshakeCompleted(HandshakeCompletedEvent event) {
            complete = true;
        }

        public boolean isComplete() {
            return complete;
        }
    }
}
