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
import java.io.CharArrayWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.lang.reflect.Field;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import javax.net.SocketFactory;

import jakarta.annotation.PreDestroy;
import jakarta.servlet.AsyncContext;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
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
import org.apache.catalina.connector.Connector;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.catalina.valves.AbstractAccessLogValve;
import org.apache.coyote.http11.AbstractHttp11Protocol;

/**
 * Tests for the validation of the response Upgrade header when an application calls
 * {@link HttpServletRequest#upgrade(Class)}.
 */
public class TestUpgradeValidation extends TomcatBaseTest {

    private static final String MESSAGE = "This is a test.";
    private static final String GREETING = "Greeting from the handler.";

    private static final AtomicBoolean handlerInitCalled = new AtomicBoolean();
    private static final AtomicBoolean handlerDestroyed = new AtomicBoolean();
    private static final AtomicBoolean handlerDestroyMethodCalled = new AtomicBoolean();
    private static final AtomicBoolean handlerInstantiated = new AtomicBoolean();
    private static final AtomicBoolean iseThrown = new AtomicBoolean();
    private static final AtomicBoolean asyncCompleteCalled = new AtomicBoolean();
    private static final AtomicInteger greetingHandlersCreated = new AtomicInteger();
    private static final AtomicReference<String> bodyRead = new AtomicReference<>();
    private static volatile Connector connectorToPause;
    private CapturingAccessLogValve accessLog;
    private static final String UNCOMMITTED_DATA = "This data must not be sent.";
    private static final int PARTIAL_READ_SIZE = 5;
    private static volatile CountDownLatch upgradeCalled = new CountDownLatch(1);
    private static volatile CountDownLatch clientDisconnected = new CountDownLatch(1);


    @Before
    public void resetFlags() {
        handlerInitCalled.set(false);
        handlerDestroyed.set(false);
        handlerDestroyMethodCalled.set(false);
        handlerInstantiated.set(false);
        iseThrown.set(false);
        asyncCompleteCalled.set(false);
        greetingHandlersCreated.set(0);
        bodyRead.set(null);
        upgradeCalled = new CountDownLatch(1);
        clientDisconnected = new CountDownLatch(1);
        connectorToPause = null;
        accessLog = null;
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
     * upgrade() may be called after startAsync() on the container thread provided it is called before complete(). The
     * upgrade completes the asynchronous processing.
     */
    @Test
    public void testUpgradeAfterStartAsync() throws Exception {
        Response response = doRequest(Mode.ASYNC_THEN_UPGRADE, "test");

        Assert.assertFalse("upgrade() must not throw IllegalStateException", iseThrown.get());
        assertEchoUpgrade(response);
        Assert.assertTrue("AsyncListener.onComplete() must be called", asyncCompleteCalled.get());
    }


    /*
     * As above but upgrade() and complete() are called from an application thread.
     */
    @Test
    public void testUpgradeAfterStartAsyncAppThread() throws Exception {
        Response response = doRequest(Mode.ASYNC_THEN_UPGRADE_APP_THREAD, "test");

        Assert.assertTrue(upgradeCalled.await(5, TimeUnit.SECONDS));
        Assert.assertFalse("upgrade() must not throw IllegalStateException", iseThrown.get());
        assertEchoUpgrade(response);
        Assert.assertTrue("AsyncListener.onComplete() must be called", asyncCompleteCalled.get());
    }


    /*
     * upgrade() must be called before complete(). Calling it afterwards must fail and must not affect the completion
     * of the async processing.
     */
    @Test
    public void testUpgradeAfterAsyncComplete() throws Exception {
        Response response = doRequest(Mode.ASYNC_COMPLETE_THEN_UPGRADE, "test");

        Assert.assertTrue("upgrade() must throw IllegalStateException", iseThrown.get());
        Assert.assertEquals("200", response.status);
        Assert.assertFalse("HttpUpgradeHandler must not be created", handlerInstantiated.get());
        Assert.assertTrue(getUpgradeGroupInfos().isEmpty());

        response.socket.close();
    }


    /*
     * As above but complete() and upgrade() are called from an application thread. By the time upgrade() is called the
     * request has been recycled.
     */
    @Test
    public void testUpgradeAfterAsyncCompleteAppThread() throws Exception {
        Response response = doRequest(Mode.ASYNC_COMPLETE_THEN_UPGRADE_APP_THREAD, "test");

        Assert.assertEquals("200", response.status);
        Assert.assertTrue(upgradeCalled.await(5, TimeUnit.SECONDS));
        Assert.assertTrue("upgrade() must throw IllegalStateException", iseThrown.get());
        Assert.assertFalse("HttpUpgradeHandler must not be created", handlerInstantiated.get());
        Assert.assertTrue(getUpgradeGroupInfos().isEmpty());

        response.socket.close();
    }


    /*
     * The application calls upgrade() after startAsync() but never calls complete(). The async processing times out.
     * The upgrade must be abandoned and the handler cleaned up.
     */
    @Test
    public void testUpgradeAfterStartAsyncTimeout() throws Exception {
        Response response = doRequest(Mode.ASYNC_THEN_UPGRADE_TIMEOUT, "test");

        Assert.assertNotEquals("101", response.status);
        assertConnectionClosed(response);
        assertCleanedUp();
        Assert.assertTrue(getUpgradeGroupInfos().isEmpty());
    }


    /*
     * As testNoResponseHeader() but the upgrade is requested after startAsync(). The upgrade must be abandoned, the
     * handler cleaned up and no per-protocol state may be created for the (client controlled) request header.
     */
    @Test
    public void testAsyncNoResponseHeader() throws Exception {
        Response response = doRequest(Mode.ASYNC_NO_RESPONSE_HEADER, "user-token");

        Assert.assertEquals("500", response.status);
        assertConnectionClosed(response);
        assertCleanedUp();
        Assert.assertTrue(getUpgradeGroupInfos().isEmpty());
    }


    /*
     * The complete request body is read using non-blocking I/O before the upgrade.
     */
    @Test
    public void testAsyncUpgradeAfterCompleteNonBlockingRead() throws Exception {
        String body = "0123456789";
        Response response = doRequest(Mode.ASYNC_READ_COMPLETE_THEN_UPGRADE, "test", body);

        Assert.assertFalse("upgrade() must not throw IllegalStateException", iseThrown.get());
        Assert.assertEquals(body, bodyRead.get());
        assertEchoUpgrade(response);
    }


    /*
     * Only part of the request body is read using non-blocking I/O before the upgrade. The remainder of the request
     * body must not be passed to the upgraded protocol as if it had been sent by the client using that protocol.
     */
    @Test
    public void testAsyncUpgradeAfterPartialNonBlockingRead() throws Exception {
        String body = "0123456789ABCDEFGHIJ";
        Response response = doRequest(Mode.ASYNC_READ_PARTIAL_THEN_UPGRADE, "test", body);

        Assert.assertFalse("upgrade() must not throw IllegalStateException", iseThrown.get());
        Assert.assertEquals(body.substring(0, PARTIAL_READ_SIZE), bodyRead.get());
        // The echo would return the unread body first if it had been passed to the handler
        assertEchoUpgrade(response);
    }


    /*
     * As above but the request body is not read at all.
     */
    @Test
    public void testAsyncUpgradeWithUnreadRequestBody() throws Exception {
        Response response = doRequest(Mode.ASYNC_UNREAD_BODY_THEN_UPGRADE, "test", "0123456789ABCDEFGHIJ");

        Assert.assertFalse("upgrade() must not throw IllegalStateException", iseThrown.get());
        assertEchoUpgrade(response);
    }


    /*
     * Response data has been written, using non-blocking I/O, but not committed. It must be discarded as part of the
     * upgrade.
     */
    @Test
    public void testAsyncUpgradeAfterUncommittedNonBlockingWrite() throws Exception {
        Response response = doRequest(Mode.ASYNC_WRITE_UNCOMMITTED_THEN_UPGRADE, "test");

        Assert.assertFalse("upgrade() must not throw IllegalStateException", iseThrown.get());
        // The first data received on the upgraded connection must be from the handler. If the data written before the
        // upgrade was sent, it would be received first.
        assertEchoUpgrade(response);
    }


    /*
     * Response data has been written, using non-blocking I/O, and the response committed. The upgrade must fail and
     * must not affect the response.
     */
    @Test
    public void testAsyncUpgradeAfterCommittedNonBlockingWrite() throws Exception {
        Response response = doRequest(Mode.ASYNC_WRITE_COMMITTED_THEN_UPGRADE, "test");

        Assert.assertEquals("200", response.status);
        Assert.assertTrue(upgradeCalled.await(5, TimeUnit.SECONDS));
        Assert.assertTrue("upgrade() must throw IllegalStateException", iseThrown.get());
        Assert.assertEquals("HttpUpgradeHandler must not be created", 0, greetingHandlersCreated.get());
        Assert.assertTrue(getUpgradeGroupInfos().isEmpty());

        response.socket.close();
    }


    /*
     * upgrade() must be called before dispatch(). The dispatch must not be affected.
     */
    @Test
    public void testUpgradeAfterAsyncDispatch() throws Exception {
        Response response = doRequest(Mode.ASYNC_DISPATCH_THEN_UPGRADE, "test");

        Assert.assertEquals("200", response.status);
        Assert.assertTrue("upgrade() must throw IllegalStateException", iseThrown.get());
        Assert.assertEquals("HttpUpgradeHandler must not be created", 0, greetingHandlersCreated.get());
        Assert.assertTrue(getUpgradeGroupInfos().isEmpty());

        response.socket.close();
    }


    /*
     * upgrade() can only be called once. The second call must fail without creating another handler (which would
     * never be destroyed) and without affecting the upgrade.
     */
    @Test
    public void testAsyncDoubleUpgrade() throws Exception {
        Response response = doRequest(Mode.ASYNC_DOUBLE_UPGRADE, "test");

        Assert.assertTrue("The second upgrade() must throw IllegalStateException", iseThrown.get());
        Assert.assertEquals("Only one HttpUpgradeHandler must be created", 1, greetingHandlersCreated.get());
        assertEchoUpgrade(response);
    }


    @Test
    public void testDoubleUpgrade() throws Exception {
        Response response = doRequest(Mode.DOUBLE_UPGRADE, "test");

        Assert.assertTrue("The second upgrade() must throw IllegalStateException", iseThrown.get());
        Assert.assertEquals("Only one HttpUpgradeHandler must be created", 1, greetingHandlersCreated.get());
        assertEchoUpgrade(response);
    }


    /*
     * The client disconnects after the application has called upgrade() but before the asynchronous processing is
     * completed. The response (and so the upgrade) can't be completed. The HttpUpgradeHandler instance that was
     * created must still be destroyed rather than being leaked.
     */
    @Test
    public void testAsyncUpgradeClientDisconnect() throws Exception {
        startServer(Mode.ASYNC_UPGRADE_CLIENT_DISCONNECT);

        Socket socket = SocketFactory.getDefault().createSocket("localhost", getPort());
        Writer writer = new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8);
        writer.write("GET / HTTP/1.1" + CRLF);
        writer.write("Host: whatever" + CRLF);
        writer.write("Connection: Upgrade" + CRLF);
        writer.write("Upgrade: test" + CRLF);
        writer.write(CRLF);
        writer.flush();

        Assert.assertTrue(upgradeCalled.await(5, TimeUnit.SECONDS));
        Assert.assertTrue("The HttpUpgradeHandler instance must have been created", handlerInstantiated.get());

        // Abort the connection (RST) so any attempt to write the response fails
        socket.setSoLinger(true, 0);
        socket.close();
        // Give the RST time to arrive then let the application complete the asynchronous processing
        Thread.sleep(500);
        clientDisconnected.countDown();

        int count = 0;
        while (!handlerDestroyed.get() && count < 50) {
            Thread.sleep(100);
            count++;
        }
        assertCleanedUp();
        Assert.assertTrue(getUpgradeGroupInfos().isEmpty());
    }


    /*
     * The application calls upgrade() from AsyncListener.onTimeout() and then completes the asynchronous processing.
     * The upgrade must succeed.
     */
    @Test
    public void testAsyncUpgradeInOnTimeout() throws Exception {
        Response response = doRequest(Mode.ASYNC_UPGRADE_IN_TIMEOUT, "test");

        Assert.assertTrue(upgradeCalled.await(5, TimeUnit.SECONDS));
        Assert.assertFalse("upgrade() must not throw IllegalStateException", iseThrown.get());
        assertEchoUpgrade(response);
        Assert.assertTrue("AsyncListener.onComplete() must be called", asyncCompleteCalled.get());
    }


    /*
     * The application calls upgrade() from AsyncListener.onTimeout() but does not complete the asynchronous
     * processing. The timeout is then handled as an error so the upgrade must be abandoned and the handler cleaned up.
     */
    @Test
    public void testAsyncUpgradeInOnTimeoutWithoutComplete() throws Exception {
        Response response = doRequest(Mode.ASYNC_UPGRADE_IN_TIMEOUT_NO_COMPLETE, "test");

        Assert.assertTrue(upgradeCalled.await(5, TimeUnit.SECONDS));
        Assert.assertFalse("upgrade() must not throw IllegalStateException", iseThrown.get());
        Assert.assertEquals("500", response.status);
        assertConnectionClosed(response);
        assertCleanedUp();
        Assert.assertTrue(getUpgradeGroupInfos().isEmpty());
    }


    /*
     * The protocol is paused after the application has called upgrade() but before the asynchronous processing
     * completes. As when upgrade is requested without async, the connection is closed rather than upgraded and the
     * handler must be cleaned up.
     */
    @Test
    public void testAsyncUpgradeWhileProtocolPaused() throws Exception {
        connectorToPause = getTomcatInstance().getConnector();

        Response response = doRequest(Mode.ASYNC_UPGRADE_THEN_PAUSE, "test");

        Assert.assertTrue(upgradeCalled.await(5, TimeUnit.SECONDS));
        // The 101 headers are sent before the connection is closed
        assertConnectionClosed(response);
        awaitHandlerDestroyed();
        assertCleanedUp();
        Assert.assertTrue(getUpgradeGroupInfos().isEmpty());
    }


    /*
     * The servlet throws an exception after calling startAsync() and upgrade(). The upgrade must be abandoned.
     */
    @Test
    public void testAsyncUpgradeThenException() throws Exception {
        Response response = doRequest(Mode.ASYNC_UPGRADE_THEN_EXCEPTION, "test");

        Assert.assertNotEquals("101", response.status);
        assertConnectionClosed(response);
        awaitHandlerDestroyed();
        assertCleanedUp();
        Assert.assertTrue(getUpgradeGroupInfos().isEmpty());
    }


    /*
     * As above but an AsyncListener completes the asynchronous processing from onError().
     */
    @Test
    public void testAsyncUpgradeThenExceptionListenerCompletes() throws Exception {
        Response response = doRequest(Mode.ASYNC_UPGRADE_ERROR_LISTENER_COMPLETE, "test");

        Assert.assertTrue(upgradeCalled.await(5, TimeUnit.SECONDS));
        Assert.assertNotEquals("101", response.status);
        assertConnectionClosed(response);
        awaitHandlerDestroyed();
        assertCleanedUp();
        Assert.assertTrue(getUpgradeGroupInfos().isEmpty());
    }


    /*
     * The servlet throws an exception and an AsyncListener attempts to handle the error, from onError(), by upgrading
     * the connection. Once an error has occurred an upgrade is not permitted. The attempt must fail and no handler may
     * be created.
     */
    @Test
    public void testAsyncUpgradeInOnError() throws Exception {
        Response response = doRequest(Mode.ASYNC_UPGRADE_IN_ERROR_LISTENER, "test");

        Assert.assertTrue(upgradeCalled.await(5, TimeUnit.SECONDS));
        Assert.assertTrue("upgrade() must throw IllegalStateException", iseThrown.get());
        Assert.assertNotEquals("101", response.status);
        Assert.assertFalse("HttpUpgradeHandler must not be created", handlerInstantiated.get());
        Assert.assertTrue(getUpgradeGroupInfos().isEmpty());

        response.socket.close();
    }


    /*
     * A successful upgrade (without async) must be written to the access log once with the 101 status and no response
     * body.
     */
    @Test
    public void testAccessLogUpgrade() throws Exception {
        accessLog = new CapturingAccessLogValve();

        Response response = doRequest(Mode.ECHO_RESPONSE_HEADER, "test");
        Assert.assertEquals("101", response.status);

        awaitAccessLogEntries(1);
        Assert.assertEquals(Collections.singletonList("101 0"), accessLog.entries);

        response.socket.close();
    }


    /*
     * As above but the upgrade is requested after startAsync(). The request must be logged once, when the asynchronous
     * processing completes, not once for the initial processing and again for the completion.
     */
    @Test
    public void testAccessLogAsyncUpgrade() throws Exception {
        accessLog = new CapturingAccessLogValve();

        Response response = doRequest(Mode.ASYNC_THEN_UPGRADE, "test");
        assertEchoUpgrade(response);

        awaitAccessLogEntries(1);
        // Allow time for any (unexpected) duplicate entry
        Thread.sleep(500);
        Assert.assertEquals(Collections.singletonList("101 0"), accessLog.entries);
    }


    /*
     * An async upgrade that fails is logged once with the error status.
     */
    @Test
    public void testAccessLogAsyncUpgradeTimeout() throws Exception {
        accessLog = new CapturingAccessLogValve();

        Response response = doRequest(Mode.ASYNC_THEN_UPGRADE_TIMEOUT, "test");
        assertConnectionClosed(response);

        awaitAccessLogEntries(1);
        Thread.sleep(500);
        Assert.assertEquals(accessLog.entries.toString(), 1, accessLog.entries.size());
        Assert.assertTrue(accessLog.entries.toString(), accessLog.entries.get(0).startsWith("500 "));
    }


    private void awaitAccessLogEntries(int expected) throws InterruptedException {
        int count = 0;
        while (accessLog.entries.size() < expected && count < 50) {
            Thread.sleep(100);
            count++;
        }
    }


    private void awaitHandlerDestroyed() throws InterruptedException {
        int count = 0;
        while (!handlerDestroyed.get() && count < 50) {
            Thread.sleep(100);
            count++;
        }
    }


    /*
     * The application's HttpUpgradeHandler.destroy() throws an exception. The failure must not prevent the rest of the
     * clean-up: the instance must still be destroyed and, more importantly, the processor must still be released which
     * de-registers the connection from the per-protocol statistics. If it is not, the connection is leaked.
     */
    @Test
    public void testDestroyThrowsException() throws Exception {
        Response response = doRequest(Mode.DESTROY_THROWS, "test");

        Assert.assertEquals("101", response.status);
        response.writer.write(MESSAGE + CRLF);
        response.writer.flush();
        Assert.assertEquals(MESSAGE, response.reader.readLine());

        Object groupInfo = getUpgradeGroupInfos().get("test");
        Assert.assertNotNull(groupInfo);
        Assert.assertEquals("The active connection must be registered", 1, getActiveUpgradeInfoCount(groupInfo));

        response.socket.close();

        int count = 0;
        while (getActiveUpgradeInfoCount(groupInfo) != 0 && count < 50) {
            Thread.sleep(100);
            count++;
        }
        Assert.assertTrue("destroy() must be called after init()", handlerDestroyMethodCalled.get());
        Assert.assertTrue("The handler instance must be destroyed", handlerDestroyed.get());
        Assert.assertEquals("The processor must be released", 0, getActiveUpgradeInfoCount(groupInfo));
    }


    /*
     * The upgraded connection must be removed from the per-protocol statistics when it closes. If it is not, the
     * connection (and everything it references) is leaked.
     */
    @Test
    public void testAsyncUpgradeInfoRemovedWhenConnectionCloses() throws Exception {
        Response response = doRequest(Mode.ASYNC_THEN_UPGRADE, "test");

        Assert.assertEquals("101", response.status);
        Assert.assertEquals(GREETING, response.reader.readLine());
        response.writer.write(MESSAGE + CRLF);
        response.writer.flush();
        Assert.assertEquals(MESSAGE, response.reader.readLine());

        Map<String,?> infos = getUpgradeGroupInfos();
        Assert.assertEquals(infos.toString(), 1, infos.size());
        Object groupInfo = infos.get("test");
        Assert.assertNotNull(groupInfo);
        Assert.assertEquals("The active connection must be registered", 1, getActiveUpgradeInfoCount(groupInfo));

        response.socket.close();

        // Closing the connection is detected asynchronously
        int count = 0;
        while (getActiveUpgradeInfoCount(groupInfo) != 0 && count < 100) {
            Thread.sleep(100);
            count++;
        }
        Assert.assertEquals("The closed connection must be removed", 0, getActiveUpgradeInfoCount(groupInfo));
        // The group is reused rather than a new one being created for each connection
        Assert.assertEquals(1, getUpgradeGroupInfos().size());
    }


    /*
     * The per-protocol state is keyed by the protocol name from the response (chosen by the application) and not the
     * request (chosen by the client) so a client can't cause an unbounded number of entries to be created. Check that
     * this is also the case when the upgrade is requested after startAsync().
     */
    @Test
    public void testAsyncUpgradeGroupInfoNotKeyedByRequestHeader() throws Exception {
        startServer(Mode.ASYNC_THEN_UPGRADE_DIFFERENT_HEADER);

        for (String requestToken : new String[] { "client-token-1", "client-token-2", "client-token-3" }) {
            Response response = sendRequest(requestToken, null);
            Assert.assertEquals("101", response.status);
            Assert.assertEquals("app-token", response.upgradeHeader);
            Assert.assertEquals(GREETING, response.reader.readLine());
            response.socket.close();
        }

        Map<String,?> infos = getUpgradeGroupInfos();
        Assert.assertEquals(infos.toString(), 1, infos.size());
        Assert.assertTrue(infos.toString(), infos.containsKey("app-token"));
    }


    /*
     * The upgrade must not depend on keep-alive being enabled for the request. Here the connector only allows a single
     * request per connection so keep-alive is disabled before the application is called. The response must not tell
     * the client that the connection is going to be closed as the client is expecting the Connection header to
     * contain upgrade (as set by the application).
     */
    @Test
    public void testUpgradeWithKeepAliveDisabled() throws Exception {
        Assert.assertTrue(getTomcatInstance().getConnector().setProperty("maxKeepAliveRequests", "1"));

        Response response = doRequest(Mode.ECHO_RESPONSE_HEADER, "test");

        Assert.assertEquals("101", response.status);
        Assert.assertEquals(Collections.singletonList("upgrade"), response.connectionHeaders);
        response.writer.write(MESSAGE + CRLF);
        response.writer.flush();
        Assert.assertEquals(MESSAGE, response.reader.readLine());
        Assert.assertTrue(getUpgradeGroupInfos().containsKey("test"));

        response.socket.close();
    }


    /*
     * As above but the upgrade is requested after startAsync() so completion of the upgrade is triggered by the
     * completion of the asynchronous processing.
     */
    @Test
    public void testAsyncUpgradeWithKeepAliveDisabled() throws Exception {
        Assert.assertTrue(getTomcatInstance().getConnector().setProperty("maxKeepAliveRequests", "1"));

        Response response = doRequest(Mode.ASYNC_THEN_UPGRADE, "test");

        Assert.assertFalse("upgrade() must not throw IllegalStateException", iseThrown.get());
        Assert.assertEquals(Collections.singletonList("upgrade"), response.connectionHeaders);
        assertEchoUpgrade(response);
    }


    /*
     * With keep-alive enabled the response must only have the Connection header set by the application.
     */
    @Test
    public void testUpgradeConnectionHeaderKeepAliveEnabled() throws Exception {
        Response response = doRequest(Mode.ECHO_RESPONSE_HEADER, "test");

        Assert.assertEquals("101", response.status);
        Assert.assertEquals(Collections.singletonList("upgrade"), response.connectionHeaders);

        response.socket.close();
    }


    /*
     * The upgrade fails (no Upgrade header) with keep-alive disabled. This is not a protocol switch so the response
     * must indicate that the connection will be closed.
     */
    @Test
    public void testFailedUpgradeWithKeepAliveDisabled() throws Exception {
        Assert.assertTrue(getTomcatInstance().getConnector().setProperty("maxKeepAliveRequests", "1"));

        Response response = doRequest(Mode.NO_RESPONSE_HEADER, "test");

        Assert.assertEquals("500", response.status);
        Assert.assertEquals(Collections.singletonList("close"), response.connectionHeaders);
        assertConnectionClosed(response);
        assertCleanedUp();
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


    private void assertEchoUpgrade(Response response) throws Exception {
        Assert.assertEquals("101", response.status);
        Assert.assertEquals("test", response.upgradeHeader);

        // The handler writes first so this also checks the upgrade was not delayed until the client sent data
        Assert.assertEquals(GREETING, response.reader.readLine());
        response.writer.write(MESSAGE + CRLF);
        response.writer.flush();
        Assert.assertEquals(MESSAGE, response.reader.readLine());
        Assert.assertTrue(getUpgradeGroupInfos().containsKey("test"));

        response.socket.close();
    }


    private void assertCleanedUp() {
        Assert.assertFalse("HttpUpgradeHandler.init() must not be called", handlerInitCalled.get());
        Assert.assertFalse("HttpUpgradeHandler.destroy() must not be called if init() was not called",
                handlerDestroyMethodCalled.get());
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


    private int getActiveUpgradeInfoCount(Object groupInfo) throws Exception {
        Field field = UpgradeGroupInfo.class.getDeclaredField("upgradeInfos");
        field.setAccessible(true);
        return ((Set<?>) field.get(groupInfo)).size();
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
        return doRequest(mode, requestUpgrade, null);
    }


    private Response doRequest(Mode mode, String requestUpgrade, String body) throws Exception {
        startServer(mode);
        return sendRequest(requestUpgrade, body);
    }


    private void startServer(Mode mode) throws Exception {
        Tomcat tomcat = getTomcatInstance();

        // No file system docBase required
        Context ctx = getProgrammaticRootContext();

        Tomcat.addServlet(ctx, "servlet", new UpgradeServlet(mode)).setAsyncSupported(true);
        ctx.addServletMapping("/", "servlet");

        if (accessLog != null) {
            ctx.getPipeline().addValve(accessLog);
        }

        tomcat.start();
    }


    private Response sendRequest(String requestUpgrade, String body) throws Exception {
        Socket socket = SocketFactory.getDefault().createSocket("localhost", getPort());
        socket.setSoTimeout(5000);

        Writer writer = new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8);
        BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));

        if (body == null) {
            writer.write("GET / HTTP/1.1" + CRLF);
        } else {
            writer.write("POST / HTTP/1.1" + CRLF);
        }
        writer.write("Host: whatever" + CRLF);
        writer.write("Connection: Upgrade" + CRLF);
        writer.write("Upgrade: " + requestUpgrade + CRLF);
        if (body != null) {
            writer.write("Content-Length: " + body.length() + CRLF);
        }
        writer.write(CRLF);
        if (body != null) {
            writer.write(body);
        }
        writer.flush();

        String statusLine = reader.readLine();
        if (statusLine == null) {
            // Connection closed without a response
            return new Response(socket, writer, reader, null, null, Collections.emptyList());
        }

        String upgradeHeader = null;
        List<String> connectionHeaders = new ArrayList<>();
        String line = reader.readLine();
        while (line != null && line.length() > 0) {
            if (line.regionMatches(true, 0, "Upgrade:", 0, 8)) {
                upgradeHeader = line.substring(8).trim();
            } else if (line.regionMatches(true, 0, "Connection:", 0, 11)) {
                connectionHeaders.add(line.substring(11).trim());
            }
            line = reader.readLine();
        }

        return new Response(socket, writer, reader, getStatusCode(statusLine), upgradeHeader, connectionHeaders);
    }


    private static final class Response {
        private final Socket socket;
        private final Writer writer;
        private final BufferedReader reader;
        private final String status;
        private final String upgradeHeader;
        private final List<String> connectionHeaders;

        Response(Socket socket, Writer writer, BufferedReader reader, String status, String upgradeHeader,
                List<String> connectionHeaders) {
            this.socket = socket;
            this.writer = writer;
            this.reader = reader;
            this.status = status;
            this.upgradeHeader = upgradeHeader;
            this.connectionHeaders = connectionHeaders;
        }
    }


    private enum Mode {
        NO_RESPONSE_HEADER,
        ECHO_RESPONSE_HEADER,
        DIFFERENT_RESPONSE_HEADER,
        UPGRADE_THEN_ASYNC,
        ASYNC_THEN_UPGRADE,
        ASYNC_THEN_UPGRADE_APP_THREAD,
        ASYNC_COMPLETE_THEN_UPGRADE,
        ASYNC_COMPLETE_THEN_UPGRADE_APP_THREAD,
        ASYNC_THEN_UPGRADE_TIMEOUT,
        ASYNC_THEN_UPGRADE_DIFFERENT_HEADER,
        ASYNC_NO_RESPONSE_HEADER,
        ASYNC_READ_COMPLETE_THEN_UPGRADE,
        ASYNC_READ_PARTIAL_THEN_UPGRADE,
        ASYNC_UNREAD_BODY_THEN_UPGRADE,
        ASYNC_WRITE_UNCOMMITTED_THEN_UPGRADE,
        ASYNC_WRITE_COMMITTED_THEN_UPGRADE,
        ASYNC_DISPATCH_THEN_UPGRADE,
        ASYNC_DOUBLE_UPGRADE,
        ASYNC_UPGRADE_CLIENT_DISCONNECT,
        ASYNC_UPGRADE_IN_TIMEOUT,
        ASYNC_UPGRADE_THEN_PAUSE,
        ASYNC_UPGRADE_THEN_EXCEPTION,
        ASYNC_UPGRADE_ERROR_LISTENER_COMPLETE,
        ASYNC_UPGRADE_IN_ERROR_LISTENER,
        ASYNC_UPGRADE_IN_TIMEOUT_NO_COMPLETE,
        DOUBLE_UPGRADE,
        DESTROY_THROWS,
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
        protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            doGet(req, resp);
        }

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            if (req.getDispatcherType() == DispatcherType.ASYNC) {
                // The target of the dispatch
                resp.setContentType("text/plain");
                resp.getWriter().write("dispatched");
                return;
            }
            switch (mode) {
                case NO_RESPONSE_HEADER:
                    req.upgrade(TestHandler.class);
                    break;
                case ECHO_RESPONSE_HEADER:
                    // Only a single protocol is requested so it is safe to echo it
                    resp.setHeader("Upgrade", req.getHeader("Upgrade"));
                    resp.setHeader("Connection", "upgrade");
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
                case ASYNC_THEN_UPGRADE: {
                    resp.setHeader("Upgrade", req.getHeader("Upgrade"));
                    resp.setHeader("Connection", "upgrade");
                    AsyncContext ac = req.startAsync();
                    ac.addListener(new CompleteListener());
                    try {
                        req.upgrade(GreetingEchoHandler.class);
                    } catch (IllegalStateException ise) {
                        iseThrown.set(true);
                    }
                    ac.complete();
                    break;
                }
                case ASYNC_THEN_UPGRADE_APP_THREAD: {
                    resp.setHeader("Upgrade", req.getHeader("Upgrade"));
                    AsyncContext ac = req.startAsync();
                    ac.addListener(new CompleteListener());
                    new Thread(() -> {
                        try {
                            req.upgrade(GreetingEchoHandler.class);
                        } catch (IllegalStateException ise) {
                            iseThrown.set(true);
                        } catch (IOException | ServletException e) {
                            throw new IllegalStateException(e);
                        } finally {
                            upgradeCalled.countDown();
                        }
                        ac.complete();
                    }).start();
                    break;
                }
                case ASYNC_COMPLETE_THEN_UPGRADE: {
                    resp.setHeader("Upgrade", req.getHeader("Upgrade"));
                    AsyncContext ac = req.startAsync();
                    ac.complete();
                    try {
                        req.upgrade(TestHandler.class);
                    } catch (IllegalStateException ise) {
                        iseThrown.set(true);
                    }
                    break;
                }
                case ASYNC_COMPLETE_THEN_UPGRADE_APP_THREAD: {
                    resp.setHeader("Upgrade", req.getHeader("Upgrade"));
                    AsyncContext ac = req.startAsync();
                    new Thread(() -> {
                        try {
                            ac.complete();
                            // Allow the container to complete the processing and recycle the request
                            Thread.sleep(500);
                            req.upgrade(TestHandler.class);
                        } catch (IllegalStateException ise) {
                            iseThrown.set(true);
                        } catch (InterruptedException | IOException | ServletException e) {
                            throw new IllegalStateException(e);
                        } finally {
                            upgradeCalled.countDown();
                        }
                    }).start();
                    break;
                }
                case ASYNC_THEN_UPGRADE_TIMEOUT: {
                    resp.setHeader("Upgrade", req.getHeader("Upgrade"));
                    AsyncContext ac = req.startAsync();
                    ac.setTimeout(500);
                    // Never completed
                    req.upgrade(TestHandler.class);
                    break;
                }
                case ASYNC_THEN_UPGRADE_DIFFERENT_HEADER: {
                    resp.setHeader("Upgrade", "app-token");
                    AsyncContext ac = req.startAsync();
                    req.upgrade(GreetingEchoHandler.class);
                    ac.complete();
                    break;
                }
                case ASYNC_NO_RESPONSE_HEADER: {
                    AsyncContext ac = req.startAsync();
                    req.upgrade(TestHandler.class);
                    ac.complete();
                    break;
                }
                case ASYNC_READ_COMPLETE_THEN_UPGRADE: {
                    resp.setHeader("Upgrade", req.getHeader("Upgrade"));
                    AsyncContext ac = req.startAsync();
                    ServletInputStream sis = req.getInputStream();
                    sis.setReadListener(new ReadListener() {
                        private final StringBuilder sb = new StringBuilder();

                        @Override
                        public void onDataAvailable() throws IOException {
                            byte[] buffer = new byte[1024];
                            while (sis.isReady() && !sis.isFinished()) {
                                int read = sis.read(buffer);
                                if (read > 0) {
                                    sb.append(new String(buffer, 0, read, StandardCharsets.UTF_8));
                                }
                            }
                        }

                        @Override
                        public void onAllDataRead() throws IOException {
                            bodyRead.set(sb.toString());
                            try {
                                req.upgrade(GreetingEchoHandler.class);
                            } catch (IllegalStateException | ServletException e) {
                                iseThrown.set(true);
                            }
                            ac.complete();
                        }

                        @Override
                        public void onError(Throwable t) {
                            ac.complete();
                        }
                    });
                    break;
                }
                case ASYNC_READ_PARTIAL_THEN_UPGRADE: {
                    resp.setHeader("Upgrade", req.getHeader("Upgrade"));
                    AsyncContext ac = req.startAsync();
                    ServletInputStream sis = req.getInputStream();
                    sis.setReadListener(new ReadListener() {
                        private boolean upgraded = false;

                        @Override
                        public void onDataAvailable() throws IOException {
                            if (upgraded) {
                                return;
                            }
                            if (sis.isReady()) {
                                // Read only part of the body
                                byte[] buffer = new byte[PARTIAL_READ_SIZE];
                                int read = sis.read(buffer);
                                bodyRead.set(new String(buffer, 0, read, StandardCharsets.UTF_8));
                            }
                            upgraded = true;
                            try {
                                req.upgrade(GreetingEchoHandler.class);
                            } catch (IllegalStateException | ServletException e) {
                                iseThrown.set(true);
                            }
                            ac.complete();
                        }

                        @Override
                        public void onAllDataRead() {
                            // NO-OP
                        }

                        @Override
                        public void onError(Throwable t) {
                            ac.complete();
                        }
                    });
                    break;
                }
                case ASYNC_UNREAD_BODY_THEN_UPGRADE: {
                    resp.setHeader("Upgrade", req.getHeader("Upgrade"));
                    AsyncContext ac = req.startAsync();
                    // The request body is not read at all
                    req.upgrade(GreetingEchoHandler.class);
                    ac.complete();
                    break;
                }
                case ASYNC_WRITE_UNCOMMITTED_THEN_UPGRADE: {
                    resp.setHeader("Upgrade", req.getHeader("Upgrade"));
                    AsyncContext ac = req.startAsync();
                    ServletOutputStream sos = resp.getOutputStream();
                    sos.setWriteListener(new WriteListener() {
                        private boolean written = false;

                        @Override
                        public void onWritePossible() throws IOException {
                            if (!written) {
                                written = true;
                                // Small enough to remain in the response buffer. Nothing is committed.
                                sos.write(UNCOMMITTED_DATA.getBytes(StandardCharsets.UTF_8));
                                Assert.assertFalse(resp.isCommitted());
                                try {
                                    req.upgrade(GreetingEchoHandler.class);
                                } catch (IllegalStateException | ServletException e) {
                                    iseThrown.set(true);
                                }
                                ac.complete();
                            }
                        }

                        @Override
                        public void onError(Throwable t) {
                            ac.complete();
                        }
                    });
                    break;
                }
                case ASYNC_WRITE_COMMITTED_THEN_UPGRADE: {
                    resp.setHeader("Upgrade", req.getHeader("Upgrade"));
                    AsyncContext ac = req.startAsync();
                    ServletOutputStream sos = resp.getOutputStream();
                    sos.setWriteListener(new WriteListener() {
                        private boolean written = false;

                        @Override
                        public void onWritePossible() throws IOException {
                            if (!written) {
                                written = true;
                                // Much larger than the response buffer so the response is committed
                                byte[] data = new byte[64 * 1024];
                                Arrays.fill(data, (byte) 'x');
                                sos.write(data);
                                Assert.assertTrue(resp.isCommitted());
                                try {
                                    req.upgrade(GreetingEchoHandler.class);
                                } catch (IllegalStateException | ServletException e) {
                                    iseThrown.set(true);
                                }
                                upgradeCalled.countDown();
                            }
                            if (sos.isReady()) {
                                ac.complete();
                            }
                        }

                        @Override
                        public void onError(Throwable t) {
                            ac.complete();
                        }
                    });
                    break;
                }
                case ASYNC_DISPATCH_THEN_UPGRADE: {
                    resp.setHeader("Upgrade", req.getHeader("Upgrade"));
                    AsyncContext ac = req.startAsync();
                    ac.dispatch("/dispatched");
                    try {
                        req.upgrade(GreetingEchoHandler.class);
                    } catch (IllegalStateException ise) {
                        iseThrown.set(true);
                    }
                    break;
                }
                case ASYNC_DOUBLE_UPGRADE: {
                    resp.setHeader("Upgrade", req.getHeader("Upgrade"));
                    AsyncContext ac = req.startAsync();
                    req.upgrade(GreetingEchoHandler.class);
                    try {
                        req.upgrade(GreetingEchoHandler.class);
                    } catch (IllegalStateException ise) {
                        iseThrown.set(true);
                    }
                    ac.complete();
                    break;
                }
                case ASYNC_UPGRADE_CLIENT_DISCONNECT: {
                    resp.setHeader("Upgrade", req.getHeader("Upgrade"));
                    resp.setHeader("Connection", "upgrade");
                    AsyncContext ac = req.startAsync();
                    req.upgrade(TestHandler.class);
                    upgradeCalled.countDown();
                    // Wait for the client to go away before completing so the response can't be written
                    new Thread(() -> {
                        try {
                            clientDisconnected.await(10, TimeUnit.SECONDS);
                        } catch (InterruptedException e) {
                            // Ignore. Complete anyway.
                        }
                        ac.complete();
                    }).start();
                    break;
                }
                case ASYNC_UPGRADE_IN_TIMEOUT:
                case ASYNC_UPGRADE_IN_TIMEOUT_NO_COMPLETE: {
                    boolean complete = (mode == Mode.ASYNC_UPGRADE_IN_TIMEOUT);
                    Class<? extends HttpUpgradeHandler> handlerClass =
                            complete ? GreetingEchoHandler.class : TestHandler.class;
                    AsyncContext ac = req.startAsync();
                    ac.setTimeout(500);
                    ac.addListener(new AsyncListener() {
                        @Override
                        public void onTimeout(AsyncEvent event) throws IOException {
                            HttpServletRequest asyncReq = (HttpServletRequest) event.getSuppliedRequest();
                            HttpServletResponse asyncResp = (HttpServletResponse) event.getSuppliedResponse();
                            asyncResp.setHeader("Upgrade", asyncReq.getHeader("Upgrade"));
                            asyncResp.setHeader("Connection", "upgrade");
                            try {
                                asyncReq.upgrade(handlerClass);
                            } catch (IllegalStateException | ServletException e) {
                                iseThrown.set(true);
                            }
                            upgradeCalled.countDown();
                            if (complete) {
                                event.getAsyncContext().complete();
                            }
                        }

                        @Override
                        public void onComplete(AsyncEvent event) {
                            asyncCompleteCalled.set(true);
                        }

                        @Override
                        public void onError(AsyncEvent event) {
                            // NO-OP
                        }

                        @Override
                        public void onStartAsync(AsyncEvent event) {
                            // NO-OP
                        }
                    });
                    break;
                }
                case ASYNC_UPGRADE_THEN_PAUSE: {
                    resp.setHeader("Upgrade", req.getHeader("Upgrade"));
                    resp.setHeader("Connection", "upgrade");
                    AsyncContext ac = req.startAsync();
                    req.upgrade(TestHandler.class);
                    upgradeCalled.countDown();
                    new Thread(() -> {
                        // The protocol is paused before the asynchronous processing completes
                        connectorToPause.pause();
                        ac.complete();
                    }).start();
                    break;
                }
                case ASYNC_UPGRADE_THEN_EXCEPTION: {
                    resp.setHeader("Upgrade", req.getHeader("Upgrade"));
                    resp.setHeader("Connection", "upgrade");
                    req.startAsync();
                    req.upgrade(TestHandler.class);
                    throw new ServletException("Deliberate failure after upgrade");
                }
                case ASYNC_UPGRADE_ERROR_LISTENER_COMPLETE: {
                    resp.setHeader("Upgrade", req.getHeader("Upgrade"));
                    resp.setHeader("Connection", "upgrade");
                    AsyncContext ac = req.startAsync();
                    ac.addListener(new ErrorListener(false));
                    req.upgrade(TestHandler.class);
                    throw new ServletException("Deliberate failure after upgrade");
                }
                case ASYNC_UPGRADE_IN_ERROR_LISTENER: {
                    AsyncContext ac = req.startAsync();
                    ac.addListener(new ErrorListener(true));
                    throw new ServletException("Deliberate failure before upgrade");
                }
                case DESTROY_THROWS:
                    resp.setHeader("Upgrade", req.getHeader("Upgrade"));
                    resp.setHeader("Connection", "upgrade");
                    req.upgrade(DestroyFailsHandler.class);
                    break;
                case DOUBLE_UPGRADE:
                    resp.setHeader("Upgrade", req.getHeader("Upgrade"));
                    req.upgrade(GreetingEchoHandler.class);
                    try {
                        req.upgrade(GreetingEchoHandler.class);
                    } catch (IllegalStateException ise) {
                        iseThrown.set(true);
                    }
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
            handlerDestroyMethodCalled.set(true);
        }

        @PreDestroy
        public void preDestroy() {
            handlerDestroyed.set(true);
        }
    }


    /*
     * Completes the asynchronous processing when an error occurs, optionally after calling upgrade().
     */
    private static class ErrorListener implements AsyncListener {

        private final boolean upgrade;

        ErrorListener(boolean upgrade) {
            this.upgrade = upgrade;
        }

        @Override
        public void onError(AsyncEvent event) throws IOException {
            if (upgrade) {
                HttpServletRequest asyncReq = (HttpServletRequest) event.getSuppliedRequest();
                HttpServletResponse asyncResp = (HttpServletResponse) event.getSuppliedResponse();
                asyncResp.setHeader("Upgrade", asyncReq.getHeader("Upgrade"));
                asyncResp.setHeader("Connection", "upgrade");
                try {
                    asyncReq.upgrade(TestHandler.class);
                } catch (IllegalStateException | ServletException e) {
                    iseThrown.set(true);
                }
            }
            upgradeCalled.countDown();
            event.getAsyncContext().complete();
        }

        @Override
        public void onComplete(AsyncEvent event) {
            asyncCompleteCalled.set(true);
        }

        @Override
        public void onTimeout(AsyncEvent event) {
            // NO-OP
        }

        @Override
        public void onStartAsync(AsyncEvent event) {
            // NO-OP
        }
    }


    private static class CapturingAccessLogValve extends AbstractAccessLogValve {

        private final List<String> entries = new CopyOnWriteArrayList<>();

        CapturingAccessLogValve() {
            // Status code and bytes sent
            setPattern("%s %B");
        }

        @Override
        protected void log(CharArrayWriter message) {
            entries.add(message.toString().trim());
        }
    }


    private static class CompleteListener implements AsyncListener {

        @Override
        public void onComplete(AsyncEvent event) {
            asyncCompleteCalled.set(true);
        }

        @Override
        public void onTimeout(AsyncEvent event) {
            // NO-OP
        }

        @Override
        public void onError(AsyncEvent event) {
            // NO-OP
        }

        @Override
        public void onStartAsync(AsyncEvent event) {
            // NO-OP
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


    /*
     * Echoes data until the client closes the connection. destroy() always fails.
     */
    public static class DestroyFailsHandler implements HttpUpgradeHandler {

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
            handlerDestroyMethodCalled.set(true);
            throw new IllegalStateException("Deliberate failure in destroy()");
        }

        @PreDestroy
        public void preDestroy() {
            handlerDestroyed.set(true);
        }
    }


    /*
     * Writes before it reads so a test can tell whether the upgrade was completed before the client sent any data.
     */
    public static class GreetingEchoHandler implements HttpUpgradeHandler {

        public GreetingEchoHandler() {
            greetingHandlersCreated.incrementAndGet();
        }

        @Override
        public void init(WebConnection connection) {
            try (ServletInputStream sis = connection.getInputStream();
                    ServletOutputStream sos = connection.getOutputStream()) {
                sos.write((GREETING + CRLF).getBytes(StandardCharsets.UTF_8));
                sos.flush();
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
