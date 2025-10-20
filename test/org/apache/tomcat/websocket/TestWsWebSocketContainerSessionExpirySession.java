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
public class TestWsWebSocketContainerSessionExpirySession extends WsWebSocketContainerBaseTest {

    @Test
    public void testSessionExpirySession() throws Exception {

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
        Session s1a = connectToEchoServer(wsContainer, endpointA, TesterEchoServer.Config.PATH_BASIC);
        s1a.setMaxIdleTimeout(3000);
        Session s2a = connectToEchoServer(wsContainer, endpointA, TesterEchoServer.Config.PATH_BASIC);
        s2a.setMaxIdleTimeout(6000);
        Session s3a = connectToEchoServer(wsContainer, endpointA, TesterEchoServer.Config.PATH_BASIC);
        s3a.setMaxIdleTimeout(9000);

        // Check all three sessions are open
        Set<Session> setA = s3a.getOpenSessions();

        int expected = 3;
        while (expected > 0) {
            Assert.assertEquals(expected, getOpenCount(setA));

            int count = 0;
            while (getOpenCount(setA) == expected && count < 50) {
                count++;
                Thread.sleep(100);
            }

            expected--;
        }

        Assert.assertEquals(0, getOpenCount(setA));
    }


    private int getOpenCount(Set<Session> sessions) {
        int result = 0;
        for (Session session : sessions) {
            if (session.isOpen()) {
                result++;
            }
        }
        return result;
    }
}
