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

package org.apache.tomcat.security;

import java.io.File;
import java.net.URI;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;

import jakarta.websocket.ClientEndpointConfig;
import jakarta.websocket.ContainerProvider;
import jakarta.websocket.DeploymentException;
import jakarta.websocket.WebSocketContainer;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.servlets.DefaultServlet;
import org.apache.catalina.startup.Tomcat;
import org.apache.tomcat.util.net.TesterKeystoreGenerator;
import org.apache.tomcat.util.net.TesterSupport;
import org.apache.tomcat.websocket.TesterEchoServer;
import org.apache.tomcat.websocket.TesterMessageCountClient;
import org.apache.tomcat.websocket.WebSocketBaseTest;

public class TestSecurity2018 extends WebSocketBaseTest {

    // https://www.cve.org/CVERecord?id=CVE-2018-8034
    @Test(expected = DeploymentException.class)
    public void testCVE_2018_8034() throws Exception {
        File keystoreFile = TesterKeystoreGenerator.generateKeystore(
                "localhost", "tomcat",
                new String[]{"localhost"}, null);

        Tomcat tomcat = getTomcatInstance();

        TesterSupport.initSsl(tomcat, keystoreFile.getAbsolutePath(), false);

        Context ctx = getProgrammaticRootContext();
        ctx.addApplicationListener(TesterEchoServer.Config.class.getName());
        Tomcat.addServlet(ctx, "default", new DefaultServlet());
        ctx.addServletMappingDecoded("/", "default");

        tomcat.start();

        WebSocketContainer wsContainer = ContainerProvider.getWebSocketContainer();

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, new TrustManager[]{new TesterSupport.TrustAllCerts()}, null);

        ClientEndpointConfig clientEndpointConfig = ClientEndpointConfig.Builder.create().sslContext(sslContext)
                .build();

        wsContainer.connectToServer(
                TesterMessageCountClient.TesterProgrammaticEndpoint.class,
                clientEndpointConfig,
                new URI("wss://127.0.0.1:" + getPort() +
                    TesterEchoServer.Config.PATH_ASYNC));
        Assert.fail(
                "Hostname verification should have failed for 127.0.0.1 with a certificate issued for localhost only.");
    }
}
