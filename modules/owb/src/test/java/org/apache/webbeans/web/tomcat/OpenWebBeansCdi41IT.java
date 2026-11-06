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
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.net.URI;
import java.net.http.HttpClient;
import java.nio.file.Path;

import org.apache.webbeans.config.WebBeansContext;
import org.apache.webbeans.web.tomcat.OwbTestServer.ListenerMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OpenWebBeansCdi41IT {

    @TempDir
    Path tempDir;

    @Test
    void cdi41Webapp() throws Exception {
        Path shadedJar = OwbTestSupport.shadedJar();
        OwbTestSupport.assertShadedJar(shadedJar, OpenWebBeansListener.class, WebBeansContext.class);

        Path webapp = new WebAppBuilder(tempDir.resolve("webapp"), "cdi41").build(shadedJar);
        try (OwbTestServer server = OwbTestServer.start(webapp, ListenerMode.SERVER, false, true)) {
            HttpClient client = OwbTestSupport.newClient();
            URI baseUri = server.baseUri();

            assertEquals("hello-owb-4.1.1", OwbTestSupport.get(client, baseUri.resolve("hello")));

            String firstRequest = OwbTestSupport.get(client, baseUri.resolve("request"));
            String secondRequest = OwbTestSupport.get(client, baseUri.resolve("request"));
            assertNotEquals(firstRequest, secondRequest,
                    "The request scoped bean was not recreated for each request");
            assertFalse(firstRequest.isEmpty());
            assertFalse(secondRequest.isEmpty());

            assertEquals("true", OwbTestSupport.get(client, baseUri.resolve("matching")));
            assertEquals("true", OwbTestSupport.get(client, baseUri.resolve("matching-event")));
            assertEquals("true", OwbTestSupport.get(client, baseUri.resolve("contexts")));
            assertEquals("bound:ok", OwbTestSupport.get(client, baseUri.resolve("interceptor")));
            assertEquals("invoked:hello-owb-4.1.1", OwbTestSupport.get(client, baseUri.resolve("invoker")));
        }
    }
}
