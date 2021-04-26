/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tomcat.websocket;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.websocket.ClientEndpointConfig.Builder;
import jakarta.websocket.ContainerProvider;
import jakarta.websocket.DeploymentException;
import jakarta.websocket.WebSocketContainer;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.servlets.DefaultServlet;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.websocket.TesterMessageCountClient.TesterProgrammaticEndpoint;

@Ignore // Not for use in normal unit test runs
public class TestConnectionLimit extends TomcatBaseTest {

    /*
     * Simple test to see how many outgoing connections can be created on a
     * single machine.
     */
    @Test
    public void testSingleMachine() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        // No file system docBase required
        Context ctx = tomcat.addContext("", null);
        ctx.addApplicationListener(TesterEchoServer.Config.class.getName());
        Tomcat.addServlet(ctx, "default", new DefaultServlet());
        ctx.addServletMappingDecoded("/", "default");

        Assert.assertTrue(tomcat.getConnector().setProperty("maxConnections", "-1"));

        tomcat.start();

        URI uri = new URI("ws://localhost:" + getPort() +
                TesterEchoServer.Config.PATH_ASYNC);
        AtomicInteger counter = new AtomicInteger(0);

        int threadCount = 50;

        Thread[] threads = new ConnectionThread[threadCount];

        for (int i = 0; i < threadCount; i++) {
            threads[i] = new ConnectionThread(counter, uri);
            threads[i].start();
        }

        // Wait for the threads to die
        for (Thread thread : threads) {
            thread.join();
        }

        System.out.println("Maximum connection count was " + counter.get());
    }

    private static class ConnectionThread extends Thread {

        private final AtomicInteger counter;
        private final URI uri;

        private ConnectionThread(AtomicInteger counter, URI uri) {
            this.counter = counter;
            this.uri = uri;
        }

        @Override
        public void run() {
            WebSocketContainer wsContainer =
                    ContainerProvider.getWebSocketContainer();

            int count = 0;

            try {
                while (true) {
                    wsContainer.connectToServer(TesterProgrammaticEndpoint.class,
                            Builder.create().build(), uri);
                    count = counter.incrementAndGet();
                    if (count % 100 == 0) {
                        System.out.println(count + " and counting...");
                    }
                }
            } catch (IOException | DeploymentException ioe) {
                // Let thread die
            }
        }
    }
}
