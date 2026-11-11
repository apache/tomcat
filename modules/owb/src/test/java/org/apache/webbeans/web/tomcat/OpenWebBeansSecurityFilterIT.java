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
import java.net.http.HttpResponse;
import java.nio.file.Path;

import org.apache.webbeans.web.tomcat.OwbTestServer.ListenerMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OpenWebBeansSecurityFilterIT {

    @TempDir
    Path tempDir;

    @Test
    void webappSecurityFilterPropagatesPrincipal() throws Exception {
        Path shadedJar = OwbTestSupport.shadedJar();
        Path webapp = new WebAppBuilder(tempDir.resolve("security-filter"), "security-filter").build(shadedJar);
        try (OwbTestServer server = OwbTestServer.start(webapp, ListenerMode.SERVER, false, false,
                "/owb-security-filter", tomcat -> {
                    tomcat.addUser("testuser", "testpass");
                    tomcat.addRole("testuser", "test");
                })) {
            HttpClient client = OwbTestSupport.newClient();
            URI uri = server.baseUri().resolve("whoami");

            HttpResponse<String> unauthenticated = OwbTestSupport.getResponse(client, uri);
            assertEquals(401, unauthenticated.statusCode(), unauthenticated.body());
            assertTrue(unauthenticated.headers().firstValue("WWW-Authenticate").orElse("").startsWith("Basic"));

            HttpResponse<String> authenticated = OwbTestSupport.getResponse(client, uri,
                    OwbTestSupport.basicAuth("testuser", "testpass"));
            assertEquals(200, authenticated.statusCode(), authenticated.body());
            assertEquals("testuser|testuser", authenticated.body().strip());
        }
    }
}
