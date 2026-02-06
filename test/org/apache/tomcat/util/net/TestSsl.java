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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import javax.net.SocketFactory;
import javax.net.ssl.HandshakeCompletedEvent;
import javax.net.ssl.HandshakeCompletedListener;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SNIServerName;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import jakarta.servlet.ServletException;
import jakarta.servlet.SessionTrackingMode;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;

import static org.apache.catalina.startup.SimpleHttpClient.CRLF;
import org.apache.catalina.Context;
import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleEvent;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.Wrapper;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.startup.SimpleHttpClient;
import org.apache.catalina.startup.TesterServlet;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.catalina.valves.ValveBase;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.net.SSLHostConfigCertificate.Type;
import org.apache.tomcat.util.net.TesterSupport.ClientSSLSocketFactory;
import org.apache.tomcat.util.net.openssl.OpenSSLStatus;
import org.apache.tomcat.websocket.server.WsContextListener;

/**
 * The keys and certificates used in this file are all available in svn and were
 * generated using a test CA the files for which are in the Tomcat PMC private
 * repository since not all of them are AL2 licensed.
 */
@RunWith(Parameterized.class)
public class TestSsl extends TomcatBaseTest {

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> parameters() {
        List<Object[]> parameterSets = new ArrayList<>();
        parameterSets.add(new Object[] {
                "JSSE", Boolean.FALSE, "org.apache.tomcat.util.net.jsse.JSSEImplementation"});
        parameterSets.add(new Object[] {
                "OpenSSL", Boolean.TRUE, "org.apache.tomcat.util.net.openssl.OpenSSLImplementation"});
        parameterSets.add(new Object[] {
                "OpenSSL-FFM", Boolean.TRUE, "org.apache.tomcat.util.net.openssl.panama.OpenSSLImplementation"});

        return parameterSets;
    }

    @Parameter(0)
    public String connectorName;

    @Parameter(1)
    public boolean useOpenSSL;

    @Parameter(2)
    public String sslImplementationName;


    @Test
    public void testSimpleSsl() throws Exception {
        TesterSupport.configureClientSsl();

        Tomcat tomcat = getTomcatInstance();

        File appDir = new File(getBuildDirectory(), "webapps/examples");
        Context ctxt  = tomcat.addWebapp(null, "/examples", appDir.getAbsolutePath());
        ctxt.addApplicationListener(WsContextListener.class.getName());

        TesterSupport.initSsl(tomcat);
        TesterSupport.configureSSLImplementation(tomcat, sslImplementationName, useOpenSSL);

        tomcat.start();
        ByteChunk res = getUrl("https://localhost:" + getPort() +
                "/examples/servlets/servlet/HelloWorldExample");
        Assert.assertTrue(res.toString().indexOf("<a href=\"../helloworld.html\">") > 0);
        Assert.assertTrue("Checking no client issuer has been requested",
                TesterSupport.getLastClientAuthRequestedIssuerCount() == 0);
    }

    private static final int POST_DATA_SIZE = 16 * 1024 * 1024;
    private static final byte[] POST_DATA;
    static {
        POST_DATA = new byte[POST_DATA_SIZE]; // 16 MiB
        Arrays.fill(POST_DATA, (byte) 1);

    }

    private String sessionId = null;

    @Test
    public void testSSLSessionTracking() throws Exception {
        TesterSupport.configureClientSsl();

        Tomcat tomcat = getTomcatInstance();
        tomcat.getEngine().getPipeline().addValve(new VerificationValve());

        File appDir = new File(getBuildDirectory(), "webapps/examples");
        Context ctxt  = tomcat.addWebapp(null, "/examples", appDir.getAbsolutePath());
        ctxt.addLifecycleListener(new SSLSessionTrackingLifecycleListener());
        ctxt.addApplicationListener(WsContextListener.class.getName());

        ctxt = getProgrammaticRootContext();
        Tomcat.addServlet(ctxt, "post", new SimplePostServlet());
        ctxt.addServletMappingDecoded("/post", "post");

        TesterSupport.initSsl(tomcat);
        TesterSupport.configureSSLImplementation(tomcat, sslImplementationName, useOpenSSL);

        tomcat.start();

        Assume.assumeFalse("BoringSSL and LibreSSL return no session id",
                TesterSupport.isOpenSSLVariant(sslImplementationName, OpenSSLStatus.Name.BORINGSSL)
                    || TesterSupport.isOpenSSLVariant(sslImplementationName, OpenSSLStatus.Name.LIBRESSL));

        getUrl("https://localhost:" + getPort() + "/examples/servlets/servlet/HelloWorldExample");
        // SSL is the only source for the requested session ID, and SessionTrackingMode.SSL is set on examples
        Assert.assertNotNull(sessionId);
        Assert.assertTrue(sessionId.length() > 1);
        // No SessionTrackingMode.SSL on the root webapp
        getUrl("https://localhost:" + getPort() + "/post");
        Assert.assertNull(sessionId);
    }

    @Test
    public void testPost() throws Exception {
        SocketFactory socketFactory = TesterSupport.configureClientSsl();

        Tomcat tomcat = getTomcatInstance();
        TesterSupport.initSsl(tomcat);
        Connector connector = tomcat.getConnector();
        // Increase timeout as default (3s) can be too low for some CI systems
        Assert.assertTrue(connector.setProperty("connectionTimeout", "20000"));
        TesterSupport.configureSSLImplementation(tomcat, sslImplementationName, useOpenSSL);

        Context ctxt = getProgrammaticRootContext();
        Tomcat.addServlet(ctxt, "post", new SimplePostServlet());
        ctxt.addServletMappingDecoded("/post", "post");
        tomcat.start();
        int iterations = 8;
        CountDownLatch latch = new CountDownLatch(iterations);
        AtomicInteger errorCount = new AtomicInteger(0);
        for (int i = 0; i < iterations; i++) {
            new Thread() {
                @Override
                public void run() {
                    try {
                        SSLSocket socket = (SSLSocket) socketFactory.createSocket("localhost",
                                getPort());

                        OutputStream os = socket.getOutputStream();

                        os.write("POST /post HTTP/1.1\r\n".getBytes());
                        os.write("Host: localhost\r\n".getBytes());
                        os.write(("Content-Length: " + Integer.valueOf(POST_DATA.length) + "\r\n\r\n").getBytes());
                        // Write in 128 KiB blocks
                        for (int i = 0; i < POST_DATA.length / (128 * 1024); i++) {
                            os.write(POST_DATA, 0, 1024 * 128);
                            sleep(10);
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

                        for (int i = 0; i < POST_DATA.length; i++) {
                            int read = is.read();
                            if (POST_DATA[i] != read) {
                                System.err.println("Byte in position [" + i + "] had value [" + read +
                                        "] rather than [" + Byte.toString(POST_DATA[i]) + "]");
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
        Context ctxt  = tomcat.addWebapp(null, "/examples", appDir.getAbsolutePath());
        ctxt.addApplicationListener(WsContextListener.class.getName());

        TesterSupport.initSsl(tomcat, TesterSupport.LOCALHOST_KEYPASS_JKS, false,
                TesterSupport.JKS_PASS, null, TesterSupport.JKS_KEY_PASS, null);

        TesterSupport.configureSSLImplementation(tomcat, sslImplementationName, useOpenSSL);

        tomcat.start();
        ByteChunk res = getUrl("https://localhost:" + getPort() +
                "/examples/servlets/servlet/HelloWorldExample");
        Assert.assertTrue(res.toString().indexOf("<a href=\"../helloworld.html\">") > 0);
        Assert.assertTrue("Checking no client issuer has been requested",
                TesterSupport.getLastClientAuthRequestedIssuerCount() == 0);
    }

    @Test
    public void testKeyPassFile() throws Exception {
        TesterSupport.configureClientSsl();

        Tomcat tomcat = getTomcatInstance();

        File appDir = new File(getBuildDirectory(), "webapps/examples");
        Context ctxt  = tomcat.addWebapp(null, "/examples", appDir.getAbsolutePath());
        ctxt.addApplicationListener(WsContextListener.class.getName());

        TesterSupport.initSsl(tomcat, TesterSupport.LOCALHOST_KEYPASS_JKS, false,
                      null, TesterSupport.JKS_PASS_FILE, null, TesterSupport.JKS_KEY_PASS_FILE);

        TesterSupport.configureSSLImplementation(tomcat, sslImplementationName, useOpenSSL);

        tomcat.start();
        ByteChunk res = getUrl("https://localhost:" + getPort() +
                "/examples/servlets/servlet/HelloWorldExample");
        Assert.assertTrue(res.toString().indexOf("<a href=\"../helloworld.html\">") > 0);
        Assert.assertTrue("Checking no client issuer has been requested",
                TesterSupport.getLastClientAuthRequestedIssuerCount() == 0);
    }

    @Test
    public void testSni() throws Exception {
        System.setProperty("jsse.enableSNIExtension", "true");
        ClientSSLSocketFactory clientSSLSocketFactory = TesterSupport.configureClientSsl();
        Tomcat tomcat = getTomcatInstance();
        tomcat.getConnector().setProperty("strictSni", "true");

        File appDir = new File(getBuildDirectory(), "webapps/examples");
        Context ctxt  = tomcat.addWebapp(null, "/examples", appDir.getAbsolutePath());
        ctxt.addApplicationListener(WsContextListener.class.getName());

        TesterSupport.initSsl(tomcat);

        // Add another config for localhost
        SSLHostConfig sslHostConfig = new SSLHostConfig();
        SSLHostConfigCertificate certificate = new SSLHostConfigCertificate(sslHostConfig, Type.UNDEFINED);
        sslHostConfig.addCertificate(certificate);
        certificate.setCertificateKeystoreFile(new File(TesterSupport.LOCALHOST_RSA_JKS).getAbsolutePath());
        certificate.setCertificateKeystorePassword(TesterSupport.JKS_PASS);
        sslHostConfig.setHostName("localhost");
        tomcat.getConnector().addSslHostConfig(sslHostConfig);

        // Add another config for foobar
        sslHostConfig = new SSLHostConfig();
        certificate = new SSLHostConfigCertificate(sslHostConfig, Type.UNDEFINED);
        sslHostConfig.addCertificate(certificate);
        certificate.setCertificateKeystoreFile(new File(TesterSupport.LOCALHOST_RSA_JKS).getAbsolutePath());
        certificate.setCertificateKeystorePassword(TesterSupport.JKS_PASS);
        sslHostConfig.setHostName("foobar");
        tomcat.getConnector().addSslHostConfig(sslHostConfig);

        TesterSupport.configureSSLImplementation(tomcat, sslImplementationName, useOpenSSL);

        tomcat.start();

        // Send SNI and it matches
        SSLSocket sslSocket = (SSLSocket) clientSSLSocketFactory.createSocket("localhost", getPort());
        SNIHostName serverName = new SNIHostName("localhost");
        List<SNIServerName> serverNames = new ArrayList<>(1);
        serverNames.add(serverName);
        SSLParameters params = sslSocket.getSSLParameters();
        params.setServerNames(serverNames);
        sslSocket.setSSLParameters(params);

        Client client = new Client();
        client.setPort(getPort());

        // @formatter:off
        client.setRequest(new String[] {
                "GET /examples/servlets/servlet/HelloWorldExample HTTP/1.1" + CRLF +
                    "Host: localhost" + CRLF +
                    "Connection: Close" + CRLF +
                    CRLF
                });
        // @formatter:on
        client.connect(sslSocket);
        client.processRequest(true);

        Assert.assertEquals(HttpServletResponse.SC_OK, client.getStatusCode());
        Assert.assertTrue(client.getResponseBody().contains("<a href=\"../helloworld.html\">"));
        client.disconnect();
        client.reset();

        // Send SNI and it does not match
        sslSocket = (SSLSocket) clientSSLSocketFactory.createSocket("localhost", getPort());
        params = sslSocket.getSSLParameters();
        params.setServerNames(serverNames);
        sslSocket.setSSLParameters(params);

        // @formatter:off
        client.setRequest(new String[] {
                "GET /examples/servlets/servlet/HelloWorldExample HTTP/1.1" + CRLF +
                    "Host: foobar" + CRLF +
                    "Connection: Close" + CRLF +
                    CRLF
                });
        // @formatter:on
        client.connect(sslSocket);
        client.processRequest(true);

        Assert.assertEquals(HttpServletResponse.SC_BAD_REQUEST, client.getStatusCode());
        client.disconnect();
        client.reset();

        // Send SNI and it does not match, but this goes to the default host which is the same one
        tomcat.getConnector().setProperty("defaultSSLHostConfigName", "localhost");
        sslSocket = (SSLSocket) clientSSLSocketFactory.createSocket("localhost", getPort());
        params = sslSocket.getSSLParameters();
        params.setServerNames(serverNames);
        sslSocket.setSSLParameters(params);

        // @formatter:off
        client.setRequest(new String[] {
                "GET /examples/servlets/servlet/HelloWorldExample HTTP/1.1" + CRLF +
                    "Host: something" + CRLF +
                    "Connection: Close" + CRLF +
                    CRLF
                });
        // @formatter:on
        client.connect(sslSocket);
        client.processRequest(true);

        Assert.assertEquals(HttpServletResponse.SC_OK, client.getStatusCode());
        client.disconnect();
        client.reset();
        tomcat.getConnector().setProperty("defaultSSLHostConfigName", "_default_");

        tomcat.getConnector().setProperty("strictSni", "false");

        // SNI is not verified
        sslSocket = (SSLSocket) clientSSLSocketFactory.createSocket("localhost", getPort());
        params = sslSocket.getSSLParameters();
        params.setServerNames(serverNames);
        sslSocket.setSSLParameters(params);

        // @formatter:off
        client.setRequest(new String[] {
                "GET /examples/servlets/servlet/HelloWorldExample HTTP/1.1" + CRLF +
                    "Host: foobar" + CRLF +
                    "Connection: Close" + CRLF +
                    CRLF
                });
        // @formatter:on
        client.connect(sslSocket);
        client.processRequest(true);

        Assert.assertEquals(HttpServletResponse.SC_OK, client.getStatusCode());
        client.disconnect();
        client.reset();

        tomcat.getConnector().setProperty("strictSni", "true");

        // No SNI but this is the default config
        tomcat.getConnector().setProperty("defaultSSLHostConfigName", "localhost");
        Assert.assertEquals(HttpServletResponse.SC_OK,
                getUrl("https://localhost:" + getPort() + "/examples/servlets/servlet/HelloWorldExample", new ByteChunk(), null));

        // No SNI and this is not the default config
        tomcat.getConnector().setProperty("defaultSSLHostConfigName", "_default_");
        Assert.assertEquals(HttpServletResponse.SC_BAD_REQUEST,
                getUrl("https://localhost:" + getPort() + "/examples/servlets/servlet/HelloWorldExample", new ByteChunk(), null));

    }

    private static final class Client extends SimpleHttpClient {

        @Override
        public boolean isResponseBodyOK() {
            return true;
        }

    }

    @Test
    public void testClientInitiatedRenegotiation() throws Exception {

        Tomcat tomcat = getTomcatInstance();
        TesterSupport.initSsl(tomcat);
        TesterSupport.configureSSLImplementation(tomcat, sslImplementationName, useOpenSSL);

        boolean renegotiationSupported = TesterSupport.isClientRenegotiationSupported(getTomcatInstance());

        Context root = tomcat.addContext("", TEMP_DIR);
        Wrapper w = Tomcat.addServlet(root, "tester", new TesterServlet());
        w.setAsyncSupported(true);
        root.addServletMappingDecoded("/", "tester");

        tomcat.start();

        SSLContext sslCtx;
        // Force TLS 1.2 if TLS 1.3 is available as JSSE's TLS 1.3
        // implementation doesn't support Post Handshake Authentication
        // which is required for this test to pass.
        sslCtx = SSLContext.getInstance(Constants.SSL_PROTO_TLSv1_2);

        sslCtx.init(null, TesterSupport.getTrustManagers(), null);
        SSLSocketFactory socketFactory = sslCtx.getSocketFactory();
        SSLSocket socket = (SSLSocket) socketFactory.createSocket("localhost", getPort());

        OutputStream os = socket.getOutputStream();
        InputStream is = socket.getInputStream();
        Reader r = new InputStreamReader(is);

        doRequest(os, r);
        Assert.assertTrue("Checking no client issuer has been requested",
                TesterSupport.getLastClientAuthRequestedIssuerCount() == 0);

        TesterHandshakeListener listener = new TesterHandshakeListener();
        socket.addHandshakeCompletedListener(listener);

        socket.startHandshake();

        try {
            doRequest(os, r);
            if (!renegotiationSupported) {
                Assert.fail("Renegotiation started when it should have failed");
            }
        } catch (IOException ioe) {
            if (renegotiationSupported) {
                Assert.fail("Renegotiation failed when it should be supported");
            }
            return;
        }
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

    public static class SSLSessionTrackingLifecycleListener implements LifecycleListener {
        @Override
        public void lifecycleEvent(LifecycleEvent event) {
            if (Lifecycle.BEFORE_START_EVENT.equals(event.getType())) {
                if (!(event.getLifecycle() instanceof Context)) {
                    throw new IllegalArgumentException("Not Context");
                }
                Context context = (Context) event.getLifecycle();
                context.getServletContext().setSessionTrackingModes(EnumSet.of(SessionTrackingMode.SSL));
            }
        }
    }

    public class VerificationValve extends ValveBase {
        @Override
        public void invoke(Request request, Response response) throws IOException, ServletException {
            sessionId = request.getRequestedSessionId();
        }
    }
}
