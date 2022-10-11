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
package org.apache.tomcat.websocket;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URI;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import jakarta.websocket.ClientEndpointConfig;
import jakarta.websocket.ContainerProvider;
import jakarta.websocket.MessageHandler;
import jakarta.websocket.Session;
import jakarta.websocket.WebSocketContainer;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;

import org.apache.catalina.Context;
import org.apache.catalina.core.AprLifecycleListener;
import org.apache.catalina.core.StandardServer;
import org.apache.catalina.servlets.DefaultServlet;
import org.apache.catalina.startup.Tomcat;
import org.apache.tomcat.util.net.TesterSupport;
import org.apache.tomcat.util.security.KeyStoreUtil;
import org.apache.tomcat.websocket.TesterMessageCountClient.BasicText;
import org.apache.tomcat.websocket.TesterMessageCountClient.SleepingText;
import org.apache.tomcat.websocket.TesterMessageCountClient.TesterProgrammaticEndpoint;

@RunWith(Parameterized.class)
public class TestWebSocketFrameClientSSL extends WebSocketBaseTest {

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> parameters() {
        List<Object[]> parameterSets = new ArrayList<>();
        parameterSets.add(new Object[] {
                "JSSE", Boolean.FALSE, "org.apache.tomcat.util.net.jsse.JSSEImplementation"});
        parameterSets.add(new Object[] {
                "OpenSSL", Boolean.TRUE, "org.apache.tomcat.util.net.openssl.OpenSSLImplementation"});
        parameterSets.add(new Object[] {
                "OpenSSL-Panama", Boolean.FALSE, "org.apache.tomcat.util.net.openssl.panama.OpenSSLImplementation"});

        return parameterSets;
    }

    @Parameter(0)
    public String connectorName;

    @Parameter(1)
    public boolean needApr;

    @Parameter(2)
    public String sslImplementationName;


    @Test
    public void testConnectToServerEndpoint() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        // No file system docBase required
        Context ctx = tomcat.addContext("", null);
        ctx.addApplicationListener(TesterFirehoseServer.ConfigInline.class.getName());
        Tomcat.addServlet(ctx, "default", new DefaultServlet());
        ctx.addServletMappingDecoded("/", "default");

        tomcat.start();

        WebSocketContainer wsContainer = ContainerProvider.getWebSocketContainer();

        // Build the SSLContext
        SSLContext sslContext = SSLContext.getInstance("TLS");
        File trustStoreFile = new File(TesterSupport.CA_JKS);
        KeyStore ks = KeyStore.getInstance("JKS");
        try (InputStream is = new FileInputStream(trustStoreFile)) {
            KeyStoreUtil.load(ks, is, TesterSupport.JKS_PASS.toCharArray());
        }
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(ks);
        sslContext.init(null,  tmf.getTrustManagers(), null);

        ClientEndpointConfig clientEndpointConfig =
                ClientEndpointConfig.Builder.create().sslContext(sslContext).build();

        Session wsSession = wsContainer.connectToServer(
                TesterProgrammaticEndpoint.class,
                clientEndpointConfig,
                new URI("wss://localhost:" + getPort() +
                        TesterFirehoseServer.PATH));
        CountDownLatch latch =
                new CountDownLatch(TesterFirehoseServer.MESSAGE_COUNT);
        BasicText handler = new BasicText(latch);
        wsSession.addMessageHandler(handler);
        wsSession.getBasicRemote().sendText("Hello");

        System.out.println("Sent Hello message, waiting for data");

        // Ignore the latch result as the message count test below will tell us
        // if the right number of messages arrived
        handler.getLatch().await(TesterFirehoseServer.WAIT_TIME_MILLIS,
                TimeUnit.MILLISECONDS);

        Queue<String> messages = handler.getMessages();
        Assert.assertEquals(
                TesterFirehoseServer.MESSAGE_COUNT, messages.size());
        for (String message : messages) {
            Assert.assertEquals(TesterFirehoseServer.MESSAGE, message);
        }
    }


    @Test
    public void testBug56032() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        // No file system docBase required
        Context ctx = tomcat.addContext("", null);
        ctx.addApplicationListener(TesterFirehoseServer.ConfigInline.class.getName());
        Tomcat.addServlet(ctx, "default", new DefaultServlet());
        ctx.addServletMappingDecoded("/", "default");

        tomcat.start();

        WebSocketContainer wsContainer = ContainerProvider.getWebSocketContainer();

        // Build the SSLContext
        SSLContext sslContext = SSLContext.getInstance("TLS");
        File trustStoreFile = new File(TesterSupport.CA_JKS);
        KeyStore ks = KeyStore.getInstance("JKS");
        try (InputStream is = new FileInputStream(trustStoreFile)) {
            KeyStoreUtil.load(ks, is, TesterSupport.JKS_PASS.toCharArray());
        }
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(ks);
        sslContext.init(null,  tmf.getTrustManagers(), null);

        ClientEndpointConfig clientEndpointConfig =
                ClientEndpointConfig.Builder.create().sslContext(sslContext).build();

        Session wsSession = wsContainer.connectToServer(
                TesterProgrammaticEndpoint.class,
                clientEndpointConfig,
                new URI("wss://localhost:" + getPort() +
                        TesterFirehoseServer.PATH));

        // Process incoming messages very slowly
        MessageHandler handler = new SleepingText(5000);
        wsSession.addMessageHandler(handler);
        wsSession.getBasicRemote().sendText("Hello");

        // Wait long enough for the buffers to fill and the send to timeout
        int count = 0;
        int limit = TesterFirehoseServer.WAIT_TIME_MILLIS / 100;

        System.out.println("Waiting for server to report an error");
        while (TesterFirehoseServer.Endpoint.getErrorCount() == 0 && count < limit) {
            Thread.sleep(100);
            count ++;
        }

        if (TesterFirehoseServer.Endpoint.getErrorCount() == 0) {
            Assert.fail("No error reported by Endpoint when timeout was expected");
        }

        // Wait again for the connection to be closed -
        // should be a lot faster.
        System.out.println("Waiting for connection to be closed");
        count = 0;
        limit = TesterFirehoseServer.WAIT_TIME_MILLIS / 100;
        while (TesterFirehoseServer.Endpoint.getOpenConnectionCount() != 0 && count < limit) {
            Thread.sleep(100);
            count ++;
        }

        int openConnectionCount = TesterFirehoseServer.Endpoint.getOpenConnectionCount();
        if (openConnectionCount != 0) {
            Assert.fail("There are [" + openConnectionCount + "] connections still open");
        }

        // Close the client session.
        wsSession.close();
    }


    @Override
    public void setUp() throws Exception {
        super.setUp();

        Tomcat tomcat = getTomcatInstance();

        TesterSupport.initSsl(tomcat);

        TesterSupport.configureSSLImplementation(tomcat, sslImplementationName);

        if (needApr) {
            AprLifecycleListener listener = new AprLifecycleListener();
            Assume.assumeTrue(AprLifecycleListener.isAprAvailable());
            StandardServer server = (StandardServer) tomcat.getServer();
            server.addLifecycleListener(listener);
        }
    }
}
