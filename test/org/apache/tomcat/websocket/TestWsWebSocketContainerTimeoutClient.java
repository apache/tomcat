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
package org.apache.tomcat.websocket;

import java.net.URI;
import java.nio.ByteBuffer;
import java.util.concurrent.Future;

import jakarta.websocket.ClientEndpointConfig;
import jakarta.websocket.ContainerProvider;
import jakarta.websocket.Session;
import jakarta.websocket.WebSocketContainer;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.servlets.DefaultServlet;
import org.apache.catalina.startup.Tomcat;
import org.apache.tomcat.websocket.TestWsWebSocketContainer.BlockingConfig;
import org.apache.tomcat.websocket.TestWsWebSocketContainer.BlockingPojo;
import org.apache.tomcat.websocket.TesterMessageCountClient.TesterProgrammaticEndpoint;

/*
 * Moved to separate test class to improve test concurrency. These tests are
 * some of the last tests to start and having them all in a single class
 * significantly extends the length of a test run when using multiple test
 * threads.
 */
public class TestWsWebSocketContainerTimeoutClient  extends WsWebSocketContainerBaseTest {

    @Test
    public void testWriteTimeoutClientContainer() throws Exception {
        doTestWriteTimeoutClient(true);
    }


    @Test
    public void testWriteTimeoutClientEndpoint() throws Exception {
        doTestWriteTimeoutClient(false);
    }


    private void doTestWriteTimeoutClient(boolean setTimeoutOnContainer)
            throws Exception {

        Tomcat tomcat = getTomcatInstance();
        // No file system docBase required
        Context ctx = tomcat.addContext("", null);
        ctx.addApplicationListener(BlockingConfig.class.getName());
        Tomcat.addServlet(ctx, "default", new DefaultServlet());
        ctx.addServletMappingDecoded("/", "default");

        WebSocketContainer wsContainer =
                ContainerProvider.getWebSocketContainer();

        // Set the async timeout
        if (setTimeoutOnContainer) {
            wsContainer.setAsyncSendTimeout(TIMEOUT_MS);
        }

        tomcat.start();

        Session wsSession = wsContainer.connectToServer(
                TesterProgrammaticEndpoint.class,
                ClientEndpointConfig.Builder.create().build(),
                new URI("ws://" + getHostName() + ":" + getPort() + BlockingConfig.PATH));

        if (!setTimeoutOnContainer) {
            wsSession.getAsyncRemote().setSendTimeout(TIMEOUT_MS);
        }

        long lastSend = 0;

        // Should send quickly until the network buffers fill up and then block
        // until the timeout kicks in
        Exception exception = null;
        try {
            while (true) {
                lastSend = System.currentTimeMillis();
                Future<Void> f = wsSession.getAsyncRemote().sendBinary(
                        ByteBuffer.wrap(MESSAGE_BINARY_4K));
                f.get();
            }
        } catch (Exception e) {
            exception = e;
        }

        long timeout = System.currentTimeMillis() - lastSend;

        // Clear the server side block and prevent further blocks to allow the
        // server to shutdown cleanly
        BlockingPojo.clearBlock();

        // Close the client session, primarily to allow the
        // BackgroundProcessManager to shut down.
        wsSession.close();

        String msg = "Time out was [" + timeout + "] ms";

        // Check correct time passed
        Assert.assertTrue(msg, timeout >= TIMEOUT_MS - MARGIN );

        // Check the timeout wasn't too long
        Assert.assertTrue(msg, timeout < TIMEOUT_MS * 2);

        Assert.assertNotNull(exception);
    }
}
