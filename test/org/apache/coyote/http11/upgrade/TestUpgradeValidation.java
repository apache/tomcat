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
package org.apache.coyote.http11.upgrade;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.lang.reflect.Field;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.net.SocketFactory;

import jakarta.annotation.PreDestroy;
import jakarta.servlet.AsyncContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpUpgradeHandler;
import jakarta.servlet.http.WebConnection;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import static org.apache.catalina.startup.SimpleHttpClient.CRLF;
import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.coyote.http11.AbstractHttp11Protocol;

/**
 * Tests for the validation of the response Upgrade header when an application calls
 * {@link HttpServletRequest#upgrade(Class)}.
 */
public class TestUpgradeValidation extends TomcatBaseTest {

    private static final String MESSAGE = "This is a test.";

    private static final AtomicBoolean handlerInitCalled = new AtomicBoolean();
    private static final AtomicBoolean handlerDestroyed = new AtomicBoolean();
    private static final AtomicBoolean handlerInstantiated = new AtomicBoolean();
    private static final AtomicBoolean iseThrown = new AtomicBoolean();
    private static volatile CountDownLatch upgradeCalled = new CountDownLatch(1);


    @Before
    public void resetFlags() {
        handlerInitCalled.set(false);
        handlerDestroyed.set(false);
        handlerInstantiated.set(false);
        iseThrown.set(false);
        upgradeCalled = new CountDownLatch(1);
    }


    /*
     * The application does not set the Upgrade header on the response. The upgrade must be abandoned, the handler must
     * be cleaned up and the connection closed.
     */
    @Test
    public void testNoResponseHeader() throws Exception {
        Response response = doRequest(Mode.NO_RESPONSE_HEADER, "user-token");

        assertConnectionClosed(response);
        assertCleanedUp();
        Assert.assertFalse(getUpgradeGroupInfos().containsKey("user-token"));
        Assert.assertTrue(getUpgradeGroupInfos().isEmpty());
    }


    /*
     * As above but the client should be told the upgrade failed rather than being sent a 101 response that has no
     * Upgrade header. This requires the validation to take place before the response is committed.
     */
    @Test
    public void testNoResponseHeaderStatus() throws Exception {
        Response response = doRequest(Mode.NO_RESPONSE_HEADER, "user-token");

        Assert.assertEquals("500", response.status);
        assertConnectionClosed(response);
    }


    /*
     * The response header differs from the request header. The response header (the application's choice) must be used
     * and the request header must be ignored.
     */
    @Test
    public void testResponseHeaderDiffersFromRequest() throws Exception {
        Response response = doRequest(Mode.DIFFERENT_RESPONSE_HEADER, "client-token");

        Assert.assertEquals("101", response.status);
        Assert.assertEquals("app-token", response.upgradeHeader);

        // Make sure the upgraded connection is actually working (this also means the upgrade processor and its
        // per-protocol state have been created)
        response.writer.write(MESSAGE + CRLF);
        response.writer.flush();
        Assert.assertEquals(MESSAGE, response.reader.readLine());

        Map<String,?> infos = getUpgradeGroupInfos();
        Assert.assertTrue(infos.toString(), infos.containsKey("app-token"));
        Assert.assertFalse(infos.toString(), infos.containsKey("client-token"));

        response.socket.close();
    }


    /*
     * The application sets a response header that matches the request header. The upgrade must succeed.
     */
    @Test
    public void testResponseHeaderMatchesRequest() throws Exception {
        Response response = doRequest(Mode.ECHO_RESPONSE_HEADER, "test");

        Assert.assertEquals("101", response.status);
        Assert.assertEquals("test", response.upgradeHeader);

        response.writer.write(MESSAGE + CRLF);
        response.writer.flush();
        Assert.assertEquals(MESSAGE, response.reader.readLine());
        Assert.assertTrue(getUpgradeGroupInfos().containsKey("test"));

        response.socket.close();
    }


    /*
     * Upgrade and async can't be mixed. Calling startAsync() after upgrade() must fail but must not affect the upgrade.
     */
    @Test
    public void testStartAsyncAfterUpgrade() throws Exception {
        Response response = doRequest(Mode.UPGRADE_THEN_ASYNC, "test");

        Assert.assertTrue("startAsync() must throw IllegalStateException", iseThrown.get());
        Assert.assertEquals("101", response.status);

        response.writer.write(MESSAGE + CRLF);
        response.writer.flush();
        Assert.assertEquals(MESSAGE, response.reader.readLine());

        response.socket.close();
    }


    /*
     * Calling upgrade() after startAsync() must fail and must not affect the async processing.
     */
    @Test
    public void testUpgradeAfterStartAsync() throws Exception {
        Response response = doRequest(Mode.ASYNC_THEN_UPGRADE, "test");

        Assert.assertTrue("upgrade() must throw IllegalStateException", iseThrown.get());
        Assert.assertEquals("200", response.status);
        Assert.assertFalse("HttpUpgradeHandler must not be created", handlerInstantiated.get());
        Assert.assertTrue(getUpgradeGroupInfos().isEmpty());

        response.socket.close();
    }


    /*
     * The application fails after calling upgrade(). Even though it set the Upgrade header, the upgrade must not
     * proceed and the handler must be cleaned up.
     */
    @Test
    public void testExceptionAfterUpgrade() throws Exception {
        Response response = doRequest(Mode.EXCEPTION_AFTER_UPGRADE, "test");

        Assert.assertNotEquals("101", response.status);
        assertConnectionClosed(response);
        assertCleanedUp();
        Assert.assertTrue(getUpgradeGroupInfos().isEmpty());
    }


    /*
     * The application calls upgrade() but then deliberately sets a different status. The upgrade must be abandoned and
     * the handler cleaned up but the status chosen by the application must not be replaced with a 500.
     */
    @Test
    public void testStatusChangedAfterUpgrade() throws Exception {
        Response response = doRequest(Mode.STATUS_CHANGED_AFTER_UPGRADE, "test");

        Assert.assertEquals("400", response.status);
        assertConnectionClosed(response);
        assertCleanedUp();
        Assert.assertTrue(getUpgradeGroupInfos().isEmpty());
    }


    /*
     * The response is committed before upgrade() is called so the Upgrade header validation that takes place when the
     * response is committed never sees the upgrade. Either upgrade() must be rejected or the (client controlled)
     * request header must not be used as the protocol name.
     */
    @Test
    public void testCommitThenUpgrade() throws Exception {
        Response response = doRequest(Mode.COMMIT_THEN_UPGRADE, "user-token");

        Assert.assertEquals("200", response.status);

        // The response headers are sent before upgrade() is called so wait for the server to get that far
        Assert.assertTrue(upgradeCalled.await(5, TimeUnit.SECONDS));

        if (!iseThrown.get()) {
            // The upgrade went ahead. Make sure the upgrade processor (and any per-protocol state) has been created.
            response.writer.write(MESSAGE + CRLF);
            response.writer.flush();
            // The flushed response was chunked so skip any chunk framing that precedes the echoed message
            String line = response.reader.readLine();
            while (line != null && (line.isEmpty() || "0".equals(line))) {
                line = response.reader.readLine();
            }
            Assert.assertEquals(MESSAGE, line);
        }

        Map<String,?> infos = getUpgradeGroupInfos();
        Assert.assertFalse(infos.toString(), infos.containsKey("user-token"));

        response.socket.close();
    }


    private void assertCleanedUp() {
        Assert.assertFalse("HttpUpgradeHandler.init() must not be called", handlerInitCalled.get());
        Assert.assertTrue("The HttpUpgradeHandler instance must be destroyed", handlerDestroyed.get());
    }


    private void assertConnectionClosed(Response response) throws IOException {
        try {
            // Consume any error page body then expect EOF
            while (response.reader.read() != -1) {
                // Keep reading
            }
        } catch (SocketTimeoutException ste) {
            Assert.fail("The connection was left open");
        } catch (IOException ioe) {
            // Connection reset is also a closed connection
        } finally {
            response.socket.close();
        }
    }


    @SuppressWarnings("unchecked")
    private Map<String,?> getUpgradeGroupInfos() throws Exception {
        AbstractHttp11Protocol<?> protocol =
                (AbstractHttp11Protocol<?>) getTomcatInstance().getConnector().getProtocolHandler();
        Field field = AbstractHttp11Protocol.class.getDeclaredField("upgradeProtocolGroupInfos");
        field.setAccessible(true);
        return (Map<String,?>) field.get(protocol);
    }


    private Response doRequest(Mode mode, String requestUpgrade) throws Exception {
        Tomcat tomcat = getTomcatInstance();

        // No file system docBase required
        Context ctx = getProgrammaticRootContext();

        Tomcat.addServlet(ctx, "servlet", new UpgradeServlet(mode)).setAsyncSupported(true);
        ctx.addServletMapping("/", "servlet");

        tomcat.start();

        Socket socket = SocketFactory.getDefault().createSocket("localhost", getPort());
        socket.setSoTimeout(5000);

        Writer writer = new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8);
        BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));

        writer.write("GET / HTTP/1.1" + CRLF);
        writer.write("Host: whatever" + CRLF);
        writer.write("Connection: Upgrade" + CRLF);
        writer.write("Upgrade: " + requestUpgrade + CRLF);
        writer.write(CRLF);
        writer.flush();

        String statusLine = reader.readLine();
        if (statusLine == null) {
            // Connection closed without a response
            return new Response(socket, writer, reader, null, null);
        }

        String upgradeHeader = null;
        String line = reader.readLine();
        while (line != null && line.length() > 0) {
            if (line.regionMatches(true, 0, "Upgrade:", 0, 8)) {
                upgradeHeader = line.substring(8).trim();
            }
            line = reader.readLine();
        }

        return new Response(socket, writer, reader, getStatusCode(statusLine), upgradeHeader);
    }


    private static final class Response {
        private final Socket socket;
        private final Writer writer;
        private final BufferedReader reader;
        private final String status;
        private final String upgradeHeader;

        Response(Socket socket, Writer writer, BufferedReader reader, String status, String upgradeHeader) {
            this.socket = socket;
            this.writer = writer;
            this.reader = reader;
            this.status = status;
            this.upgradeHeader = upgradeHeader;
        }
    }


    private enum Mode {
        NO_RESPONSE_HEADER,
        ECHO_RESPONSE_HEADER,
        DIFFERENT_RESPONSE_HEADER,
        UPGRADE_THEN_ASYNC,
        ASYNC_THEN_UPGRADE,
        EXCEPTION_AFTER_UPGRADE,
        COMMIT_THEN_UPGRADE,
        STATUS_CHANGED_AFTER_UPGRADE
    }


    private static class UpgradeServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        private final Mode mode;

        UpgradeServlet(Mode mode) {
            this.mode = mode;
        }

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            switch (mode) {
                case NO_RESPONSE_HEADER:
                    req.upgrade(TestHandler.class);
                    break;
                case ECHO_RESPONSE_HEADER:
                    // Only a single protocol is requested so it is safe to echo it
                    resp.setHeader("Upgrade", req.getHeader("Upgrade"));
                    req.upgrade(EchoHandler.class);
                    break;
                case DIFFERENT_RESPONSE_HEADER:
                    resp.setHeader("Upgrade", "app-token");
                    req.upgrade(EchoHandler.class);
                    break;
                case UPGRADE_THEN_ASYNC:
                    resp.setHeader("Upgrade", req.getHeader("Upgrade"));
                    req.upgrade(EchoHandler.class);
                    try {
                        req.startAsync();
                    } catch (IllegalStateException ise) {
                        iseThrown.set(true);
                    }
                    break;
                case ASYNC_THEN_UPGRADE:
                    AsyncContext ac = req.startAsync();
                    try {
                        req.upgrade(TestHandler.class);
                    } catch (IllegalStateException ise) {
                        iseThrown.set(true);
                    }
                    ac.complete();
                    break;
                case COMMIT_THEN_UPGRADE:
                    // Once committed, the Upgrade header can no longer be set so the validation at commit time has
                    // already been missed
                    resp.flushBuffer();
                    try {
                        req.upgrade(EchoHandler.class);
                    } catch (IllegalStateException ise) {
                        iseThrown.set(true);
                    }
                    upgradeCalled.countDown();
                    break;
                case EXCEPTION_AFTER_UPGRADE:
                    resp.setHeader("Upgrade", req.getHeader("Upgrade"));
                    req.upgrade(TestHandler.class);
                    throw new ServletException("Deliberate failure after upgrade");
                case STATUS_CHANGED_AFTER_UPGRADE:
                    resp.setHeader("Upgrade", req.getHeader("Upgrade"));
                    req.upgrade(TestHandler.class);
                    // The application changes its mind
                    resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    break;
            }
        }
    }


    public static class TestHandler implements HttpUpgradeHandler {

        public TestHandler() {
            handlerInstantiated.set(true);
        }

        @Override
        public void init(WebConnection connection) {
            handlerInitCalled.set(true);
        }

        @Override
        public void destroy() {
            // NO-OP
        }

        @PreDestroy
        public void preDestroy() {
            handlerDestroyed.set(true);
        }
    }


    public static class EchoHandler implements HttpUpgradeHandler {

        @Override
        public void init(WebConnection connection) {
            try (ServletInputStream sis = connection.getInputStream();
                    ServletOutputStream sos = connection.getOutputStream()) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = sis.read(buffer)) >= 0) {
                    sos.write(buffer, 0, read);
                    sos.flush();
                }
            } catch (IOException ioe) {
                throw new IllegalStateException(ioe);
            }
        }

        @Override
        public void destroy() {
            // NO-OP
        }
    }
}
