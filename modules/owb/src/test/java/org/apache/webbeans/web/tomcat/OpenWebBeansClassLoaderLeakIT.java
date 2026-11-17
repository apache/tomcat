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

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.http.HttpClient;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.apache.catalina.core.StandardContext;
import org.apache.webbeans.web.tomcat.OwbTestServer.Deployment;
import org.apache.webbeans.web.tomcat.OwbTestServer.ListenerMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OpenWebBeansClassLoaderLeakIT {

    private static final String CLASSLOADER_LOGGER = "org.apache.catalina.loader.WebappClassLoaderBase";

    @TempDir
    Path tempDir;

    @Test
    void contextStopDoesNotReportClassLoaderLeaks() throws Exception {
        Logger logger = Logger.getLogger(CLASSLOADER_LOGGER);
        List<String> messages = new CopyOnWriteArrayList<>();
        Level previousLevel = logger.getLevel();
        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                messages.add(record.getMessage());
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        logger.addHandler(handler);
        logger.setLevel(Level.ALL);
        try {
            Path webapp = new WebAppBuilder(tempDir.resolve("leak"), "lifecycle")
                    .build(OwbTestSupport.shadedJar());
            try (OwbTestServer server = OwbTestServer.start(
                    List.of(new Deployment("/owb-leak", webapp)), ListenerMode.SERVER, false, true, false, true,
                    null)) {
                StandardContext context = (StandardContext) server.context("/owb-leak");
                assertTrue(context.getClearReferencesThreadLocals());
                assertTrue(context.getClearReferencesRmiTargets());

                HttpClient client = OwbTestSupport.newClient();
                OwbTestSupport.get(client, server.baseUri("/owb-leak").resolve("lifecycle"));

                server.stopContext("/owb-leak");
                assertTrue(messages.stream().noneMatch(OpenWebBeansClassLoaderLeakIT::isLeakMessage),
                        messages.toString());

                server.startContext("/owb-leak");
                OwbTestSupport.get(client, server.baseUri("/owb-leak").resolve("lifecycle"));
            }
        } finally {
            logger.removeHandler(handler);
            logger.setLevel(previousLevel);
        }
    }

    private static boolean isLeakMessage(String message) {
        String lower = message.toLowerCase(Locale.ROOT);
        return lower.contains("threadlocal") || lower.contains("rmi target") || lower.contains("memory leak");
    }
}
