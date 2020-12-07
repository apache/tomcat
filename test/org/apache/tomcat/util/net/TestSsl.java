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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import javax.net.SocketFactory;
import javax.net.ssl.HandshakeCompletedEvent;
import javax.net.ssl.HandshakeCompletedListener;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

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
        Assert.assertTrue(res.toString().indexOf("<a href=\"../helloworld.html\">") > 0);
        Assert.assertTrue("Checking no client issuer has been requested",
                TesterSupport.getLastClientAuthRequestedIssuerCount() == 0);
    }

    private static final int POST_DATA_SIZE = 16 * 1024 * 1024;

    @Test
    public void testPost() throws Exception {
        final SocketFactory socketFactory = TesterSupport.configureClientSsl();

        Tomcat tomcat = getTomcatInstance();
        TesterSupport.initSsl(tomcat);
        // Increase timeout as default (3s) can be too low for some CI systems
        Assert.assertTrue(tomcat.getConnector().setProperty("connectionTimeout", "20000"));

        Context ctxt = tomcat.addContext("", null);
        Tomcat.addServlet(ctxt, "post", new SimplePostServlet());
        ctxt.addServletMappingDecoded("/post", "post");
        tomcat.start();
        int iterations = 8;
        final CountDownLatch latch = new CountDownLatch(iterations);
        final AtomicInteger errorCount = new AtomicInteger(0);
        for (int i = 0; i < iterations; i++) {
            new Thread() {
                @Override
                public void run() {
                    try {
                        SSLSocket socket = (SSLSocket) socketFactory.createSocket("localhost",
                                getPort());

                        OutputStream os = socket.getOutputStream();

                        byte[] bytes = new byte[POST_DATA_SIZE]; // 16MB
                        Arrays.fill(bytes, (byte) 1);

                        os.write("POST /post HTTP/1.1\r\n".getBytes());
                        os.write("Host: localhost\r\n".getBytes());
                        os.write(("Content-Length: " + Integer.valueOf(bytes.length) + "\r\n\r\n").getBytes());
                        // Write in 128KB blocks
                        for (int i = 0; i < bytes.length / (128 * 1024); i++) {
                            os.write(bytes, 0, 1024 * 128);
                            Thread.sleep(10);
                        }
                        os.flush();

                        InputStream is = socket.getInputStream();

                        // Skip to the end of the headers
                        byte[] endOfHeaders = "\r\n\r\n".getBytes();
                        int found = 0;
                        while (found != endOfHeaders.length) {
                            int c = is.read();
                            if (c == -1) {
                                // EOF
                                System.err.println("Unexpected EOF");
                                errorCount.incrementAndGet();
                                break;
                            } else if (c == endOfHeaders[found]) {
                                found++;
                            } else {
                                found = 0;
                            }
                        }

                        for (int i = 0; i < bytes.length; i++) {
                            int read = is.read();
                            if (bytes[i] != read) {
                                System.err.println("Byte in position [" + i + "] had value [" + read +
                                        "] rather than [" + Byte.toString(bytes[i]) + "]");
                                errorCount.incrementAndGet();
                                break;
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        errorCount.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                }
            }.start();
        }
        latch.await();
        Assert.assertEquals(0, errorCount.get());
    }

    @Test
    public void testKeyPass() throws Exception {
        TesterSupport.configureClientSsl();

        Tomcat tomcat = getTomcatInstance();

        File appDir = new File(getBuildDirectory(), "webapps/examples");
        org.apache.catalina.Context ctxt  = tomcat.addWebapp(
                null, "/examples", appDir.getAbsolutePath());
        ctxt.addApplicationListener(WsContextListener.class.getName());

        TesterSupport.initSsl(tomcat, TesterSupport.LOCALHOST_KEYPASS_JKS,
                TesterSupport.JKS_PASS, TesterSupport.JKS_KEY_PASS);

        tomcat.start();
        ByteChunk res = getUrl("https://localhost:" + getPort() +
            "/examples/servlets/servlet/HelloWorldExample");
        Assert.assertTrue(res.toString().indexOf("<a href=\"../helloworld.html\">") > 0);
        Assert.assertTrue("Checking no client issuer has been requested",
                TesterSupport.getLastClientAuthRequestedIssuerCount() == 0);
    }


    @Test
    public void testRenegotiateWorks() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        Assume.assumeTrue("SSL renegotiation has to be supported for this test",
                TesterSupport.isClientRenegotiationSupported(getTomcatInstance()));

        Context root = tomcat.addContext("", TEMP_DIR);
        Wrapper w =
            Tomcat.addServlet(root, "tester", new TesterServlet());
        w.setAsyncSupported(true);
        root.addServletMappingDecoded("/", "tester");

        TesterSupport.initSsl(tomcat);

        tomcat.start();

        SSLContext sslCtx;
        if (TesterSupport.isDefaultTLSProtocolForTesting13(tomcat.getConnector())) {
            // Force TLS 1.2 if TLS 1.3 is available as JSSE's TLS 1.3
            // implementation doesn't support Post Handshake Authentication
            // which is required for this test to pass.
            sslCtx = SSLContext.getInstance(Constants.SSL_PROTO_TLSv1_2);
        } else {
            sslCtx = SSLContext.getInstance(Constants.SSL_PROTO_TLS);
        }
        sslCtx.init(null, TesterSupport.getTrustManagers(), null);
        SSLSocketFactory socketFactory = sslCtx.getSocketFactory();
        SSLSocket socket = (SSLSocket) socketFactory.createSocket("localhost",
                getPort());

        OutputStream os = socket.getOutputStream();
        InputStream is = socket.getInputStream();
        Reader r = new InputStreamReader(is);

        doRequest(os, r);
        Assert.assertTrue("Checking no client issuer has been requested",
                TesterSupport.getLastClientAuthRequestedIssuerCount() == 0);

        TesterHandshakeListener listener = new TesterHandshakeListener();
        socket.addHandshakeCompletedListener(listener);

        socket.startHandshake();

        doRequest(os, r);
        // Handshake complete appears to be called asynchronously
        int wait = 0;
        while (wait < 5000 && !listener.isComplete()) {
            wait += 50;
            Thread.sleep(50);
        }
        Assert.assertTrue("Checking no client issuer has been requested",
                TesterSupport.getLastClientAuthRequestedIssuerCount() == 0);
        Assert.assertTrue(listener.isComplete());
        System.out.println("Renegotiation completed after " + wait + " ms");
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
            int c = r.read();
            if (c == -1) {
                // EOF
                Assert.fail("Unexpected EOF");
            } else if (c == endOfHeaders[found]) {
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

    public static class SimplePostServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        @Override
        protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            ByteArrayOutputStream baos = new ByteArrayOutputStream(POST_DATA_SIZE);
            byte[] in = new byte[1500];
            InputStream input = req.getInputStream();
            while (true) {
                int n = input.read(in);
                if (n > 0) {
                    baos.write(in, 0, n);
                } else {
                    break;
                }
            }
            byte[] out = baos.toByteArray();
            // Set the content-length to avoid having to parse chunked
            resp.setContentLength(out.length);
            resp.getOutputStream().write(out);
        }

    }
}
