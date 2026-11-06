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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.Objects;

/**
 * Shared helpers for the OWB integration tests.
 */
final class OwbTestSupport {

    private OwbTestSupport() {
    }

    static Path shadedJar() {
        return Path.of(Objects.requireNonNull(System.getProperty("owb.test.jar"),
                "owb.test.jar system property is not set"));
    }

    static void assertShadedJar(Path shadedJar, Class<?>... classes) throws Exception {
        Path expected = shadedJar.toRealPath();
        for (Class<?> clazz : classes) {
            Path actual = Path.of(clazz.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath();
            assertEquals(expected, actual, clazz.getName() + " must be loaded from the shaded module JAR");
        }
    }

    static HttpClient newClient() {
        return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    static HttpResponse<String> getResponse(HttpClient client, URI uri) throws Exception {
        return getResponse(client, uri, null);
    }

    static HttpResponse<String> getResponse(HttpClient client, URI uri, String authorization) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10)).GET();
        if (authorization != null) {
            builder.header("Authorization", authorization);
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    static String get(HttpClient client, URI uri) throws Exception {
        HttpResponse<String> response = getResponse(client, uri);
        assertEquals(200, response.statusCode(), "Unexpected response for " + uri + ": " + response.body());
        return response.body().strip();
    }

    static String basicAuth(String user, String password) {
        String credentials = user + ":" + password;
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }

    static void assertMarker(Path markerDirectory, String name) {
        assertTrue(Files.exists(markerDirectory.resolve(name)), "Missing lifecycle marker: " + name);
    }
}
