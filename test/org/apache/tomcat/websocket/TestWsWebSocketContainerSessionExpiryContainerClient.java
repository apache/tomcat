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

import java.util.Set;

import jakarta.websocket.ContainerProvider;
import jakarta.websocket.Session;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.servlets.DefaultServlet;
import org.apache.catalina.startup.Tomcat;
import org.apache.tomcat.websocket.TestWsWebSocketContainer.EndpointA;

/*
 * Moved to separate test class to improve test concurrency. These tests are
 * some of the last tests to start and having them all in a single class
 * significantly extends the length of a test run when using multiple test
 * threads.
 */
public class TestWsWebSocketContainerSessionExpiryContainerClient extends WsWebSocketContainerBaseTest {

    @Test
    public void testSessionExpiryContainer() throws Exception {

        Tomcat tomcat = getTomcatInstance();
        // No file system docBase required
        Context ctx = getProgrammaticRootContext();
        ctx.addApplicationListener(TesterEchoServer.Config.class.getName());
        Tomcat.addServlet(ctx, "default", new DefaultServlet());
        ctx.addServletMappingDecoded("/", "default");

        tomcat.start();

        // Need access to implementation methods for configuring unit tests
        WsWebSocketContainer wsContainer = (WsWebSocketContainer) ContainerProvider.getWebSocketContainer();

        // 5 second timeout
        wsContainer.setDefaultMaxSessionIdleTimeout(5000);
        wsContainer.setProcessPeriod(1);

        EndpointA endpointA = new EndpointA();
        connectToEchoServer(wsContainer, endpointA, TesterEchoServer.Config.PATH_BASIC);
        connectToEchoServer(wsContainer, endpointA, TesterEchoServer.Config.PATH_BASIC);
        Session s3a = connectToEchoServer(wsContainer, endpointA, TesterEchoServer.Config.PATH_BASIC);

        // Check all three sessions are open
        Set<Session> setA = s3a.getOpenSessions();
        Assert.assertEquals(3, setA.size());

        int count = 0;
        boolean isOpen = true;
        while (isOpen && count < 100) {
            count++;
            Thread.sleep(100);
            isOpen = false;
            for (Session session : setA) {
                if (session.isOpen()) {
                    isOpen = true;
                    break;
                }
            }
        }

        if (isOpen) {
            for (Session session : setA) {
                if (session.isOpen()) {
                    System.err.println("Session with ID [" + session.getId() + "] is open");
                }
            }
            Assert.fail("There were open sessions");
        }
    }
}
