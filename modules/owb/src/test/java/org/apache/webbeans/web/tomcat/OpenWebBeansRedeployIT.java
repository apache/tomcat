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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import org.apache.webbeans.servlet.WebBeansConfigurationListener;
import org.apache.webbeans.web.tomcat.OwbTestServer.Deployment;
import org.apache.webbeans.web.tomcat.OwbTestServer.ListenerMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OpenWebBeansRedeployIT {

    @TempDir
    Path tempDir;

    @Test
    void multipleWebappsAreIsolated() throws Exception {
        Path shadedJar = OwbTestSupport.shadedJar();
        Path one = new WebAppBuilder(tempDir.resolve("one"), "multi/one").build(shadedJar);
        Path two = new WebAppBuilder(tempDir.resolve("two"), "multi/two").build(shadedJar);

        try (OwbTestServer server = OwbTestServer.start(
                List.of(new Deployment("/owb-one", one), new Deployment("/owb-two", two)),
                ListenerMode.SERVER, false, true, false, null)) {
            assertEquals("one", get(server, "/owb-one", "value"));
            assertEquals("two", get(server, "/owb-two", "value"));

            ClassLoader oneLoader = server.context("/owb-one").getLoader().getClassLoader();
            ClassLoader twoLoader = server.context("/owb-two").getLoader().getClassLoader();
            assertNotSame(oneLoader, twoLoader);

            server.stopContext("/owb-one");
            assertEquals("two", get(server, "/owb-two", "value"));
        }
    }

    @Test
    void contextRestartStartsFreshContainer() throws Exception {
        Path markerDirectory = tempDir.resolve("markers");
        Files.createDirectories(markerDirectory);
        String previousMarkerDirectory = System.getProperty("owb.test.marker.dir");
        System.setProperty("owb.test.marker.dir", markerDirectory.toString());
        try {
            Path shadedJar = OwbTestSupport.shadedJar();
            Path webapp = new WebAppBuilder(tempDir.resolve("redeploy"), "redeploy").build(shadedJar);
            try (OwbTestServer server = OwbTestServer.start(webapp, ListenerMode.SERVER, false, true,
                    "/owb-redeploy", null)) {
                assertEquals("redeploy-owb-4.1.1", get(server, "/owb-redeploy", "redeploy"));
                String firstPostConstruct = Files.readString(markerDirectory.resolve("postConstruct.marker"));
                assertTrue(Files.notExists(markerDirectory.resolve("preDestroy.marker")));

                server.stopContext("/owb-redeploy");
                assertTrue(Files.exists(markerDirectory.resolve("preDestroy.marker")));

                server.startContext("/owb-redeploy");
                assertEquals("redeploy-owb-4.1.1", get(server, "/owb-redeploy", "redeploy"));
                String secondPostConstruct = Files.readString(markerDirectory.resolve("postConstruct.marker"));
                assertNotEquals(firstPostConstruct, secondPostConstruct);
                assertEquals(1, listenerCount(server));
            }
        } finally {
            if (previousMarkerDirectory == null) {
                System.clearProperty("owb.test.marker.dir");
            } else {
                System.setProperty("owb.test.marker.dir", previousMarkerDirectory);
            }
        }
    }

    private static String get(OwbTestServer server, String contextPath, String path) throws Exception {
        HttpClient client = OwbTestSupport.newClient();
        return OwbTestSupport.get(client, server.baseUri(contextPath).resolve(path));
    }

    private static int listenerCount(OwbTestServer server) {
        return (int) Arrays.stream(server.context("/owb-redeploy").findApplicationListeners())
                .filter(WebBeansConfigurationListener.class.getName()::equals)
                .count();
    }
}
