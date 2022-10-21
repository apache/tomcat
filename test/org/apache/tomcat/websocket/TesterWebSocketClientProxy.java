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
import java.util.Queue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import jakarta.websocket.ClientEndpointConfig;
import jakarta.websocket.ContainerProvider;
import jakarta.websocket.Session;
import jakarta.websocket.WebSocketContainer;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.authenticator.AuthenticatorBase;
import org.apache.catalina.servlets.DefaultServlet;
import org.apache.catalina.startup.Tomcat;
import org.apache.tomcat.util.descriptor.web.LoginConfig;
import org.apache.tomcat.util.descriptor.web.SecurityCollection;
import org.apache.tomcat.util.descriptor.web.SecurityConstraint;
import org.apache.tomcat.websocket.TesterMessageCountClient.BasicText;
import org.apache.tomcat.websocket.TesterMessageCountClient.TesterProgrammaticEndpoint;

/*
 * Tests WebSocket connections via a forward proxy.
 *
 * These tests have been successfully used with Apache Web Server (httpd)
 * configured with the following:
 *
 * Listen 8888
 * <VirtualHost *:8888>
 *     ProxyRequests On
 *     ProxyVia On
 *     AllowCONNECT 0-65535
 * </VirtualHost>
 *
 * Listen 8889
 * <VirtualHost *:8889>
 *     ProxyRequests On
 *     ProxyVia On
 *     AllowCONNECT 0-65535
 *     <Proxy *>
 *         Order deny,allow
 *         Allow from all
 *         AuthType Basic
 *         AuthName "Proxy Password Required"
 *         AuthUserFile password.file
 *         Require valid-user
 *     </Proxy>
 * </VirtualHost>
 *
 * and
 * # htpasswd -c password.file proxy
 * New Password: proxy-pass
 *
 */
public class TesterWebSocketClientProxy extends WebSocketBaseTest {

    private static final String MESSAGE_STRING = "proxy-test-message";

    private static final String PROXY_ADDRESS = "192.168.0.200";
    private static final String PROXY_PORT_NO_AUTH = "8888";
    private static final String PROXY_PORT_AUTH = "8889";
    // The IP address of the test instance that is reachable from the proxy
    private static final String TOMCAT_ADDRESS = "192.168.0.100";

    private static final String TOMCAT_USER = "tomcat";
    private static final String TOMCAT_PASSWORD = "tomcat-pass";
    private static final String TOMCAT_ROLE = "tomcat-role";

    private static final String PROXY_USER = "proxy";
    private static final String PROXY_PASSWORD = "proxy-pass";

    @Test
    public void testConnectToServerViaProxyWithNoAuthentication() throws Exception {
        doTestConnectToServerViaProxy(false, false);
    }


    @Test
    public void testConnectToServerViaProxyWithServerAuthentication() throws Exception {
        doTestConnectToServerViaProxy(true, false);
    }


    @Test
    public void testConnectToServerViaProxyWithProxyAuthentication() throws Exception {
        doTestConnectToServerViaProxy(false, true);
    }


    @Test
    public void testConnectToServerViaProxyWithServerAndProxyAuthentication() throws Exception {
        doTestConnectToServerViaProxy(true, true);
    }


    private void doTestConnectToServerViaProxy(boolean serverAuthentication, boolean proxyAuthentication)
            throws Exception {

        // Configure the proxy
        System.setProperty("http.proxyHost", PROXY_ADDRESS);
        if (proxyAuthentication) {
            System.setProperty("http.proxyPort", PROXY_PORT_AUTH);
        } else {
            System.setProperty("http.proxyPort", PROXY_PORT_NO_AUTH);
        }

        Tomcat tomcat = getTomcatInstance();

        // Need to listen on all addresses, not just loop-back
        tomcat.getConnector().setProperty("address", "0.0.0.0");

        // No file system docBase required
        Context ctx = tomcat.addContext("", null);
        ctx.addApplicationListener(TesterEchoServer.Config.class.getName());
        Tomcat.addServlet(ctx, "default", new DefaultServlet());
        ctx.addServletMappingDecoded("/", "default");

        if (serverAuthentication) {
            // Configure Realm
            tomcat.addUser(TOMCAT_USER, TOMCAT_PASSWORD);
            tomcat.addRole(TOMCAT_USER, TOMCAT_ROLE);

            // Configure security constraints
            SecurityCollection securityCollection = new SecurityCollection();
            securityCollection.addPatternDecoded("/*");
            SecurityConstraint securityConstraint = new SecurityConstraint();
            securityConstraint.addAuthRole(TOMCAT_ROLE);
            securityConstraint.addCollection(securityCollection);
            ctx.addConstraint(securityConstraint);

            // Configure authenticator
            LoginConfig loginConfig = new LoginConfig();
            loginConfig.setAuthMethod(BasicAuthenticator.schemeName);
            ctx.setLoginConfig(loginConfig);
            AuthenticatorBase basicAuthenticator = new org.apache.catalina.authenticator.BasicAuthenticator();
            ctx.getPipeline().addValve(basicAuthenticator);
        }

        tomcat.start();

        WebSocketContainer wsContainer = ContainerProvider.getWebSocketContainer();

        ClientEndpointConfig clientEndpointConfig = ClientEndpointConfig.Builder.create().build();
        // Configure the client
        if (serverAuthentication) {
            clientEndpointConfig.getUserProperties().put(Constants.WS_AUTHENTICATION_USER_NAME, TOMCAT_USER);
            clientEndpointConfig.getUserProperties().put(Constants.WS_AUTHENTICATION_PASSWORD, TOMCAT_PASSWORD);
        }
        if (proxyAuthentication) {
            clientEndpointConfig.getUserProperties().put(Constants.WS_AUTHENTICATION_PROXY_USER_NAME, PROXY_USER);
            clientEndpointConfig.getUserProperties().put(Constants.WS_AUTHENTICATION_PROXY_PASSWORD, PROXY_PASSWORD);
        }

        Session wsSession = wsContainer.connectToServer(
                TesterProgrammaticEndpoint.class,
                clientEndpointConfig,
                new URI("ws://" + TOMCAT_ADDRESS + ":" + getPort() +
                        TesterEchoServer.Config.PATH_ASYNC));
        CountDownLatch latch = new CountDownLatch(1);
        BasicText handler = new BasicText(latch);
        wsSession.addMessageHandler(handler);
        wsSession.getBasicRemote().sendText(MESSAGE_STRING);

        boolean latchResult = handler.getLatch().await(10, TimeUnit.SECONDS);

        Assert.assertTrue(latchResult);

        Queue<String> messages = handler.getMessages();
        Assert.assertEquals(1, messages.size());
        Assert.assertEquals(MESSAGE_STRING, messages.peek());
    }
}
