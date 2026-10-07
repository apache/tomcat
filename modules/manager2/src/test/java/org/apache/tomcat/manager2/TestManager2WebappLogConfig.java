/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.tomcat.manager2;

import java.io.File;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.startup.SimpleHttpClient;

/**
 * The log configuration API of the manager2 webapp: saving (with backup) and applying {@code conf/logging.properties}
 * and setting logger levels. These tests mutate JVM-global state (the {@code manager2.store.base} system property, the
 * JULI loggers), so they live in their own class: the JUnit task runs them sequentially within a single thread while
 * the other test classes of the suite run in parallel.
 */
public class TestManager2WebappLogConfig extends Manager2WebappTestBase {

    @Test
    public void testLogConfigSaveBackupAndApply() throws Exception {
        setup(false);

        // Redirect conf/logging.properties to a throw-away base (the same
        // override the configuration store tests use). The base is kept
        // under the per-test-class temporary directory so that test classes
        // running in parallel never share (and delete) the same path.
        File base = new File(getTemporaryDirectory(), "manager2-logconfig");
        deleteRecursive(base);
        File conf = new File(base, "conf");
        Assert.assertTrue(conf.mkdirs());
        addDeleteOnTearDown(base);
        File file = new File(conf, "logging.properties");
        writeLogFile(file, "# original\nhandlers = java.util.logging.ConsoleHandler\n");
        System.setProperty("manager2.store.base", base.getAbsolutePath());
        try {
            SimpleHttpClient client = new TestClient();
            client.setPort(getPort());
            client.connect();
            String token = loginAndGetToken(client, "manager1");

            // The saved text is served back. The handler property is kept so
            // that a (hypothetical) apply on a JULI that supports it does not
            // strip the JVM's logging.
            request(client, "POST", MANAGER2 + "/api/logs/config/file", token,
                    "{\"text\":\"# edited\\nhandlers = java.util.logging.ConsoleHandler\\n\"}", 200);
            String body = client.getResponseBody();
            Assert.assertTrue(body.contains("\"backup\":\"logging.properties."));
            Assert.assertEquals("# edited\nhandlers = java.util.logging.ConsoleHandler\n", readFile(file));

            // A backup of the previous file was kept next to it.
            File[] backups = conf.listFiles((dir, name) -> name.startsWith("logging.properties."));
            Assert.assertNotNull(backups);
            Assert.assertEquals(1, backups.length);
            Assert.assertTrue(readFile(backups[0]).contains("# original"));

            // Apply: supported only with a JULI that provides the reconfigure
            // methods; the test JVM normally runs the default LogManager, in
            // which case the API must report it cleanly instead of failing.
            request(client, "GET", MANAGER2 + "/api/logs/config", null, null, 200);
            body = client.getResponseBody();
            Assert.assertTrue(body.contains("# edited"));
            Assert.assertTrue(body.contains("\"exists\":true"));
            if (body.contains("\"liveApply\":true")) {
                request(client, "POST", MANAGER2 + "/api/logs/config/apply", token, "{}", 200);
            } else {
                request(client, "POST", MANAGER2 + "/api/logs/config/apply", token, "{}", 501);
                Assert.assertTrue(client.getResponseBody().contains("LIVE_UNSUPPORTED"));
            }

            // A missing body field is rejected.
            request(client, "POST", MANAGER2 + "/api/logs/config/file", token, "{}", 400);
            request(client, "POST", MANAGER2 + "/api/logs/config/apply", null, "{}", 403);

            client.disconnect();
        } finally {
            System.clearProperty("manager2.store.base");
        }
    }


    @Test
    public void testLogConfigLevelRoundTrip() throws Exception {
        setup(false);

        // A logger that certainly exists once the server has started. The
        // test JVM uses the default LogManager, for which the class loader
        // contexts coincide, so the system context addresses it.
        String name = "org.apache.catalina.core.StandardContext";
        Logger logger = Logger.getLogger(name);

        SimpleHttpClient client = new TestClient();
        client.setPort(getPort());
        client.connect();
        String token = loginAndGetToken(client, "manager1");

        try {
            request(client, "POST", MANAGER2 + "/api/logs/config/level", token,
                    "{\"context\":\"system\",\"name\":\"" + name + "\",\"level\":\"FINE\"}", 200);
            String body = client.getResponseBody();
            Assert.assertTrue(body.contains("\"level\":\"FINE\""));
            Assert.assertEquals(Level.FINE, logger.getLevel());

            // INHERIT clears the level again.
            request(client, "POST", MANAGER2 + "/api/logs/config/level", token,
                    "{\"context\":\"system\",\"name\":\"" + name + "\",\"level\":\"INHERIT\"}", 200);
            Assert.assertNull(logger.getLevel());
        } finally {
            logger.setLevel(null);
        }

        client.disconnect();
    }
}
