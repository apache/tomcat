/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.webbeans.web.tomcat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.function.Consumer;

import org.apache.webbeans.web.tomcat.OwbTestServer.ListenerMode;
import org.apache.webbeans.web.tomcat.testapp.CustomPrincipal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OpenWebBeansSecurityIT {

    private static final String PRINCIPAL_PROXY_PROPERTY =
            "org.apache.webbeans.component.PrincipalBean.proxy";
    private static final String PRINCIPAL_PROXY_APIS_PROPERTY =
            "org.apache.webbeans.component.PrincipalBean.proxyApis";

    @TempDir
    Path tempDir;

    @Test
    void basicAuthentication() throws Exception {
        try (OwbTestServer server = startSecurityServer("security")) {
            HttpClient client = OwbTestSupport.newClient();
            URI uri = server.baseUri().resolve("whoami");

            HttpResponse<String> unauthenticated = OwbTestSupport.getResponse(client, uri);
            assertEquals(401, unauthenticated.statusCode(), unauthenticated.body());
            assertTrue(unauthenticated.headers().firstValue("WWW-Authenticate").orElse("").startsWith("Basic"));

            assertEquals("testuser|testuser", authenticatedBody(server, "whoami"));
        }
    }

    @Test
    void principalProxyEnabled() throws Exception {
        try (OwbTestServer server = startSecurityServer("security-proxy")) {
            String type = authenticatedBody(server, "security-service-principal-type");
            assertTrue(type.contains("TomcatSecurityServicePrincipal"), type);
        }
    }

    @Test
    void principalProxyDisabled() throws Exception {
        try (OwbTestServer server = startSecurityServer("security-no-proxy",
                builder -> builder.openWebBeansProperty(PRINCIPAL_PROXY_PROPERTY, "false"))) {
            String type = authenticatedBody(server, "security-service-principal-type");
            assertFalse(type.contains("TomcatSecurityServicePrincipal"), type);
        }
    }

    @Test
    void customPrincipalProxyApis() throws Exception {
        try (OwbTestServer server = startSecurityServer("security-custom-proxy",
                builder -> builder
                        .openWebBeansProperty(PRINCIPAL_PROXY_PROPERTY, "true")
                        .openWebBeansProperty(PRINCIPAL_PROXY_APIS_PROPERTY, CustomPrincipal.class.getName()))) {
            String type = authenticatedBody(server, "security-service-principal-custom");
            assertEquals("custom", type, type);
        }
    }

    private OwbTestServer startSecurityServer(String name) throws Exception {
        return startSecurityServer(name, builder -> {
        });
    }

    private OwbTestServer startSecurityServer(String name, Consumer<WebAppBuilder> customizer) throws Exception {
        WebAppBuilder builder = new WebAppBuilder(tempDir.resolve(name), "security");
        customizer.accept(builder);
        Path webapp = builder.build(OwbTestSupport.shadedJar());
        return OwbTestServer.start(webapp, ListenerMode.SERVER, false, true, "/owb-security",
                tomcat -> {
                    tomcat.addUser("testuser", "testpass");
                    tomcat.addRole("testuser", "test");
                });
    }

    private static String authenticatedBody(OwbTestServer server, String path) throws Exception {
        HttpClient client = OwbTestSupport.newClient();
        HttpResponse<String> response = OwbTestSupport.getResponse(client,
                server.baseUri().resolve(path), OwbTestSupport.basicAuth("testuser", "testpass"));
        assertEquals(200, response.statusCode(), response.body());
        return response.body().strip();
    }
}
