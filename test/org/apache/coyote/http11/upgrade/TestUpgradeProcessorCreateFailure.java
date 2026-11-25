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
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import javax.net.SocketFactory;

import jakarta.annotation.PreDestroy;
import jakarta.servlet.ServletException;
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
import org.apache.coyote.AbstractProtocol;
import org.apache.coyote.Processor;
import org.apache.coyote.UpgradeToken;
import org.apache.coyote.http11.Http11NioProtocol;
import org.apache.tomcat.util.net.SocketWrapperBase;

/**
 * Tests the clean-up when the HTTP upgrade fails after the processor that handled the HTTP request has been released
 * but before the upgrade processor has taken ownership of the {@link HttpUpgradeHandler}.
 */
public class TestUpgradeProcessorCreateFailure extends TomcatBaseTest {

    private static final AtomicBoolean handlerCreated = new AtomicBoolean();
    private static final AtomicBoolean handlerInitialised = new AtomicBoolean();
    private static final AtomicBoolean handlerDestroyed = new AtomicBoolean();
    private static final AtomicBoolean handlerDestroyMethodCalled = new AtomicBoolean();
    private static final AtomicInteger createUpgradeProcessorCalls = new AtomicInteger();


    @Before
    public void resetFlags() {
        handlerCreated.set(false);
        handlerInitialised.set(false);
        handlerDestroyed.set(false);
        handlerDestroyMethodCalled.set(false);
        createUpgradeProcessorCalls.set(0);
    }


    @Override
    protected String getProtocol() {
        return FailingUpgradeProtocol.class.getName();
    }


    @Test
    public void testCreateUpgradeProcessorFails() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        // No file system docBase required
        Context ctx = getProgrammaticRootContext();

        Tomcat.addServlet(ctx, "servlet", new UpgradeServlet());
        ctx.addServletMapping("/", "servlet");

        tomcat.start();

        Socket socket = SocketFactory.getDefault().createSocket("localhost", getPort());
        socket.setSoTimeout(5000);
        try {
            Writer writer = new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8);
            BufferedReader reader =
                    new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            writer.write("GET / HTTP/1.1" + CRLF);
            writer.write("Host: whatever" + CRLF);
            writer.write("Connection: Upgrade" + CRLF);
            writer.write("Upgrade: test" + CRLF);
            writer.write(CRLF);
            writer.flush();

            // The 101 response is sent before the upgrade processor is created. Then the connection is closed.
            String line = reader.readLine();
            while (line != null) {
                line = reader.readLine();
            }
        } catch (IOException ioe) {
            // Connection reset is also a closed connection
        } finally {
            socket.close();
        }

        Assert.assertEquals(1, createUpgradeProcessorCalls.get());
        Assert.assertTrue("The HttpUpgradeHandler must have been created", handlerCreated.get());

        int count = 0;
        while (!handlerDestroyed.get() && count < 50) {
            Thread.sleep(100);
            count++;
        }
        Assert.assertTrue("The HttpUpgradeHandler must be destroyed", handlerDestroyed.get());
        Assert.assertFalse("The HttpUpgradeHandler must not be initialised", handlerInitialised.get());
        Assert.assertFalse("destroy() must not be called if init() was not called", handlerDestroyMethodCalled.get());

        // The processor that handled the HTTP request must only have been recycled once
        Assert.assertEquals("The processor was released more than once", 1, getRecycledProcessorCount());
    }


    private int getRecycledProcessorCount() throws Exception {
        Object protocol = getTomcatInstance().getConnector().getProtocolHandler();
        Field handlerField = AbstractProtocol.class.getDeclaredField("handler");
        handlerField.setAccessible(true);
        Object handler = handlerField.get(protocol);
        Field recycledField = null;
        for (Class<?> clazz = handler.getClass(); clazz != null && recycledField == null; clazz = clazz.getSuperclass()) {
            try {
                recycledField = clazz.getDeclaredField("recycledProcessors");
            } catch (NoSuchFieldException e) {
                // Try the super class
            }
        }
        Assert.assertNotNull(recycledField);
        recycledField.setAccessible(true);
        Object recycled = recycledField.get(handler);
        Field sizeField = recycled.getClass().getDeclaredField("size");
        sizeField.setAccessible(true);
        return ((java.util.concurrent.atomic.AtomicInteger) sizeField.get(recycled)).get();
    }


    public static class FailingUpgradeProtocol extends Http11NioProtocol {

        @Override
        protected Processor createUpgradeProcessor(SocketWrapperBase<?> socket, UpgradeToken upgradeToken) {
            createUpgradeProcessorCalls.incrementAndGet();
            throw new IllegalStateException("Deliberate failure creating the upgrade processor");
        }
    }


    private static class UpgradeServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            resp.setHeader("Upgrade", "test");
            resp.setHeader("Connection", "upgrade");
            req.upgrade(TestHandler.class);
        }
    }


    public static class TestHandler implements HttpUpgradeHandler {

        public TestHandler() {
            handlerCreated.set(true);
        }

        @Override
        public void init(WebConnection connection) {
            handlerInitialised.set(true);
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
}
