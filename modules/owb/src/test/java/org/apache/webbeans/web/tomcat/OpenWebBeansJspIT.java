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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.nio.file.Path;

import org.apache.webbeans.web.tomcat.OwbTestServer.ListenerMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OpenWebBeansJspIT {

    @TempDir
    Path tempDir;

    @Test
    void jspElResolvesCdiBean() throws Exception {
        Path shadedJar = OwbTestSupport.shadedJar();
        Path webapp = new WebAppBuilder(tempDir.resolve("jsp"), "jsp").build(shadedJar);
        try (OwbTestServer server = OwbTestServer.start(webapp, ListenerMode.SERVER, false, true, "/owb-jsp", true,
                null)) {
            HttpClient client = OwbTestSupport.newClient();
            HttpResponse<String> response = OwbTestSupport.getResponse(client, server.baseUri().resolve("index.jsp"));
            assertEquals(200, response.statusCode(), response.body());
            assertTrue(response.headers().firstValue("Content-Type").orElse("").startsWith("text/html"));
            String first = response.body();
            assertTrue(first.contains("property:hello-owb-4.1.1"), first);
            assertTrue(first.contains("method:hello-owb-4.1.1"), first);
            assertTrue(first.contains("included:hello-owb-4.1.1"), first);
            assertTrue(first.contains("tag:hello-owb-4.1.1"), first);
            assertTrue(first.contains("request:request:"), first);

            String second = OwbTestSupport.get(client, server.baseUri().resolve("index.jsp"));
            assertNotEquals(first, second, "Request scoped EL value was not recreated");

            assertEquals("hello-owb-4.1.1", OwbTestSupport.get(client, server.baseUri().resolve("el")));
        }
    }
}
