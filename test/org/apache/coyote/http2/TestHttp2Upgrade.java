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
package org.apache.coyote.http2;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.annotation.PreDestroy;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpUpgradeHandler;
import jakarta.servlet.http.WebConnection;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.startup.Tomcat;

public class TestHttp2Upgrade extends Http2TestBase {

    private final UpgradeAttemptServlet servlet = new UpgradeAttemptServlet();

    /*
     * HTTP upgrade (HttpServletRequest.upgrade()) is not supported over HTTP/2. The attempt to upgrade must fail and
     * the HttpUpgradeHandler instance, that has been created by the time the failure is detected, must be destroyed
     * rather than leaked.
     */
    @Test
    public void testUpgradeNotSupported() throws Exception {
        http2Connect();

        byte[] frameHeader = new byte[9];
        ByteBuffer headersPayload = ByteBuffer.allocate(128);
        buildGetRequest(frameHeader, headersPayload, null, 3, "/upgrade");
        writeFrame(frameHeader, headersPayload);

        // Response headers and body
        parser.readFrame();
        parser.readFrame();

        Assert.assertTrue(servlet.attempted.await(5, TimeUnit.SECONDS));
        Assert.assertNotNull("The upgrade attempt must fail", servlet.failure.get());
        Assert.assertTrue(servlet.failure.get().toString(),
                servlet.failure.get() instanceof UnsupportedOperationException);
        Assert.assertTrue("The HttpUpgradeHandler must have been created", UpgradeAttemptHandler.created.get());
        Assert.assertTrue("The HttpUpgradeHandler must be destroyed", UpgradeAttemptHandler.destroyed.get());
        Assert.assertFalse("The HttpUpgradeHandler must not be initialised", UpgradeAttemptHandler.initialised.get());
        Assert.assertFalse("destroy() must not be called if init() was not called",
                UpgradeAttemptHandler.destroyMethodCalled.get());
    }


    /*
     * The h2c upgrade response (101) must not contain a Connection: close header when keep-alive is disabled for the
     * request. The Connection header must be the Upgrade header required for the protocol switch.
     */
    @Test
    public void testH2cUpgradeWithKeepAliveDisabled() throws Exception {
        enableHttp2();
        Assert.assertTrue(getTomcatInstance().getConnector().setProperty("maxKeepAliveRequests", "1"));
        configureAndStartWebApplication();
        openClientConnection();
        doHttpUpgrade(DEFAULT_CONNECTION_HEADER_VALUE, "h2c", EMPTY_HTTP2_SETTINGS_HEADER, false);

        String[] headers = readHttpResponseHeaders();
        Assert.assertTrue(headers[0], headers[0].startsWith("HTTP/1.1 101 "));
        boolean upgradeFound = false;
        for (String header : headers) {
            Assert.assertFalse(header, header.equalsIgnoreCase("Connection: close"));
            if (header.equalsIgnoreCase("Connection: Upgrade")) {
                upgradeFound = true;
            }
        }
        Assert.assertTrue("Connection: Upgrade not found", upgradeFound);

        // The upgrade must have completed
        sendClientPreface();
        validateHttp2InitialResponse();
    }


    @Override
    protected void configureAndStartWebApplication() throws LifecycleException {
        Tomcat tomcat = getTomcatInstance();

        Context ctxt = getProgrammaticRootContext();
        Tomcat.addServlet(ctxt, "simple", new SimpleServlet());
        ctxt.addServletMapping("/simple", "simple");
        Tomcat.addServlet(ctxt, "upgrade", servlet);
        ctxt.addServletMapping("/upgrade", "upgrade");

        tomcat.start();
    }


    private static class UpgradeAttemptServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        private final transient CountDownLatch attempted = new CountDownLatch(1);
        private final transient AtomicReference<Throwable> failure = new AtomicReference<>();

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            try {
                req.upgrade(UpgradeAttemptHandler.class);
            } catch (Throwable t) {
                failure.set(t);
            }
            resp.setContentType("text/plain");
            resp.setContentLength(2);
            resp.getOutputStream().write("OK".getBytes(StandardCharsets.ISO_8859_1));
            attempted.countDown();
        }
    }


    public static class UpgradeAttemptHandler implements HttpUpgradeHandler {

        static final AtomicBoolean created = new AtomicBoolean();
        static final AtomicBoolean initialised = new AtomicBoolean();
        static final AtomicBoolean destroyed = new AtomicBoolean();
        static final AtomicBoolean destroyMethodCalled = new AtomicBoolean();

        public UpgradeAttemptHandler() {
            created.set(true);
        }

        @Override
        public void init(WebConnection connection) {
            initialised.set(true);
        }

        @Override
        public void destroy() {
            destroyMethodCalled.set(true);
        }

        @PreDestroy
        public void preDestroy() {
            destroyed.set(true);
        }
    }
}
