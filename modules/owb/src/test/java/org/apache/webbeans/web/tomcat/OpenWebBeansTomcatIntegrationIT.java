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

import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import org.apache.webbeans.servlet.WebBeansConfigurationListener;
import org.apache.webbeans.web.tomcat.OwbTestServer.ListenerMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OpenWebBeansTomcatIntegrationIT {

    @TempDir
    Path tempDir;

    @Test
    void serverListener() throws Exception {
        Path shadedJar = OwbTestSupport.shadedJar();
        Path webapp = new WebAppBuilder(tempDir.resolve("server"), "cdi41").build(shadedJar);
        try (OwbTestServer server = OwbTestServer.start(webapp, ListenerMode.SERVER, false, true)) {
            assertHello(server);
            assertSecurityValve(server, true);
            assertWebBeansListener(server, true);
        }
    }

    @Test
    void contextListener() throws Exception {
        Path shadedJar = OwbTestSupport.shadedJar();
        Path webapp = new WebAppBuilder(tempDir.resolve("context"), "cdi41").build(shadedJar);
        try (OwbTestServer server = OwbTestServer.start(webapp, ListenerMode.CONTEXT, false, true)) {
            assertHello(server);
            assertSecurityValve(server, true);
            assertWebBeansListener(server, true);
        }
    }

    @Test
    void startWithoutBeansXml() throws Exception {
        Path shadedJar = OwbTestSupport.shadedJar();
        Path webapp = new WebAppBuilder(tempDir.resolve("no-beans"), "cdi41")
                .includeBeansXml(false)
                .build(shadedJar);
        try (OwbTestServer server = OwbTestServer.start(webapp, ListenerMode.SERVER, true, true)) {
            assertHello(server);
            assertEquals("injected", status(server));
            assertWebBeansListener(server, true);
        }
    }

    @Test
    void doNotStartWithoutBeansXml() throws Exception {
        Path shadedJar = OwbTestSupport.shadedJar();
        Path webapp = new WebAppBuilder(tempDir.resolve("no-beans-disabled"), "cdi41")
                .includeBeansXml(false)
                .build(shadedJar);
        try (OwbTestServer server = OwbTestServer.start(webapp, ListenerMode.SERVER, false, true)) {
            assertEquals("not-injected", status(server));
            assertWebBeansListener(server, false);
        }
    }

    @Test
    void beansXmlInClasses() throws Exception {
        Path shadedJar = OwbTestSupport.shadedJar();
        Path webapp = new WebAppBuilder(tempDir.resolve("beans-in-classes"), "cdi41")
                .includeBeansXml(false)
                .beansXmlInClasses()
                .build(shadedJar);
        try (OwbTestServer server = OwbTestServer.start(webapp, ListenerMode.SERVER, false, true)) {
            assertHello(server);
        }
    }

    @Test
    void securityValveDisabled() throws Exception {
        Path shadedJar = OwbTestSupport.shadedJar();
        Path webapp = new WebAppBuilder(tempDir.resolve("no-security-valve"), "cdi41").build(shadedJar);
        try (OwbTestServer server = OwbTestServer.start(webapp, ListenerMode.SERVER, false, false)) {
            assertHello(server);
            assertSecurityValve(server, false);
        }
    }

    @Test
    void contextSecurityValveDisabled() throws Exception {
        Path shadedJar = OwbTestSupport.shadedJar();
        Path webapp = new WebAppBuilder(tempDir.resolve("context-no-security-valve"), "cdi41").build(shadedJar);
        try (OwbTestServer server = OwbTestServer.start(webapp, ListenerMode.CONTEXT, false, false)) {
            assertHello(server);
            assertSecurityValve(server, false);
        }
    }

    @Test
    void dynamicContextDeployment() throws Exception {
        Path shadedJar = OwbTestSupport.shadedJar();
        Path initial = new WebAppBuilder(tempDir.resolve("dynamic-initial"), "cdi41").build(shadedJar);
        Path dynamic = new WebAppBuilder(tempDir.resolve("dynamic-app"), "multi/one").build(shadedJar);
        try (OwbTestServer server = OwbTestServer.start(initial, ListenerMode.SERVER, false, true)) {
            assertHello(server);

            server.deploy("/owb-dynamic", dynamic);

            HttpClient client = OwbTestSupport.newClient();
            assertEquals("one", OwbTestSupport.get(client, server.baseUri("/owb-dynamic").resolve("value")));
            assertHello(server);
        }
    }

    @Test
    void filterListenerAndServletInjection() throws Exception {
        Path markerDirectory = tempDir.resolve("markers");
        Files.createDirectories(markerDirectory);
        String previousMarkerDirectory = System.getProperty("owb.test.marker.dir");
        System.setProperty("owb.test.marker.dir", markerDirectory.toString());
        try {
            Path shadedJar = OwbTestSupport.shadedJar();
            Path webapp = new WebAppBuilder(tempDir.resolve("lifecycle"), "lifecycle").build(shadedJar);
            try (OwbTestServer server = OwbTestServer.start(webapp, ListenerMode.SERVER, false, true)) {
                HttpClient client = OwbTestSupport.newClient();
                HttpResponse<String> response = OwbTestSupport.getResponse(client, server.baseUri().resolve("lifecycle"));
                assertEquals(200, response.statusCode(), response.body());
                assertEquals("lifecycle-owb-4.1.1|lifecycle-owb-4.1.1|lifecycle-owb-4.1.1",
                        response.body().strip());
                assertEquals("lifecycle-owb-4.1.1", response.headers().firstValue("X-Owb-Greeting").orElse(null));
                OwbTestSupport.assertMarker(markerDirectory, "postConstruct.marker");
            }
            OwbTestSupport.assertMarker(markerDirectory, "preDestroy.marker");
            OwbTestSupport.assertMarker(markerDirectory, "filterPreDestroy.marker");
            OwbTestSupport.assertMarker(markerDirectory, "listenerPreDestroy.marker");
        } finally {
            if (previousMarkerDirectory == null) {
                System.clearProperty("owb.test.marker.dir");
            } else {
                System.setProperty("owb.test.marker.dir", previousMarkerDirectory);
            }
        }
    }

    private static void assertHello(OwbTestServer server) throws Exception {
        HttpClient client = OwbTestSupport.newClient();
        assertEquals("hello-owb-4.1.1", OwbTestSupport.get(client, server.baseUri().resolve("hello")));
    }

    private static String status(OwbTestServer server) throws Exception {
        HttpClient client = OwbTestSupport.newClient();
        return OwbTestSupport.get(client, server.baseUri().resolve("injection-status"));
    }

    private static void assertSecurityValve(OwbTestServer server, boolean expected) {
        boolean found = Arrays.stream(server.context().getPipeline().getValves())
                .anyMatch(valve -> valve instanceof OpenWebBeansSecurityValve);
        assertEquals(expected, found, "Unexpected OpenWebBeansSecurityValve presence");
    }

    private static void assertWebBeansListener(OwbTestServer server, boolean expected) {
        boolean classListener = Arrays.asList(server.context().findApplicationListeners())
                .contains(WebBeansConfigurationListener.class.getName());
        boolean instanceListener = Arrays.stream(server.context().getApplicationLifecycleListeners())
                .anyMatch(WebBeansConfigurationListener.class::isInstance);
        assertEquals(expected, classListener || instanceListener,
                "Unexpected WebBeansConfigurationListener presence");
    }
}
