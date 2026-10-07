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

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.startup.SimpleHttpClient;

/**
 * Role based access control of the manager2 webapp: what the read-only manager-status role may and may not reach.
 */
public class TestManager2WebappRbac extends Manager2WebappTestBase {

    @Test
    public void testReadOnlyRoleCannotMutate() throws Exception {
        setup(true);

        SimpleHttpClient client = new TestClient();
        client.setPort(getPort());
        client.connect();
        login(client, "status1");

        // Read access to the status endpoints is allowed.
        request(client, "GET", MANAGER2 + "/api/status", null, null, 200);
        Assert.assertTrue(client.getResponseBody().contains("\"jvm\""));

        // The cluster view is read-only status data as well.
        request(client, "GET", MANAGER2 + "/api/status/cluster", null, null, 200);
        Assert.assertTrue(client.getResponseBody().contains("\"clustered\""));

        // Mutations are not (the CSRF filter and the security constraints
        // both reject the request).
        String token = getCsrfToken(client);
        request(client, "POST", MANAGER2 + "/api/apps/testapp/stop?path=%2Ftestapp", token, "{}", 403);

        client.disconnect();
    }


    @Test
    public void testLogApiReadOnlyRoleDenied() throws Exception {
        setup(true);

        SimpleHttpClient client = new TestClient();
        client.setPort(getPort());
        client.connect();
        login(client, "status1");

        // The log API is not part of the read-only status endpoints.
        request(client, "GET", MANAGER2 + "/api/logs", null, null, 403);
        request(client, "GET", MANAGER2 + "/api/access-log", null, null, 403);
        request(client, "GET", MANAGER2 + "/api/logs/download?name=catalina.log", null, null, 403);
        request(client, "GET", MANAGER2 + "/api/access-log/download?name=localhost_access_log.txt", null, null, 403);

        client.disconnect();
    }


    @Test
    public void testLogConfigAccessAndValidation() throws Exception {
        setup(true);

        // The read-only status role cannot even read the configuration.
        SimpleHttpClient statusClient = new TestClient();
        statusClient.setPort(getPort());
        statusClient.connect();
        login(statusClient, "status1");
        request(statusClient, "GET", MANAGER2 + "/api/logs/config", null, null, 403);
        request(statusClient, "POST", MANAGER2 + "/api/logs/config/file", null, "{\"text\":\"\"}", 403);
        statusClient.disconnect();

        SimpleHttpClient client = new TestClient();
        client.setPort(getPort());
        client.connect();
        login(client, "manager1");

        // Mutations need the CSRF token like the rest of the API.
        request(client, "POST", MANAGER2 + "/api/logs/config/file", null, "{\"text\":\"x\"}", 403);

        String token = loginAndGetToken(client, "manager1");

        // The GET payload: the file, the capability flag and at least the
        // system class loader context.
        request(client, "GET", MANAGER2 + "/api/logs/config", null, null, 200);
        String body = client.getResponseBody();
        Assert.assertTrue(body.contains("\"path\":\"conf/logging.properties\""));
        Assert.assertTrue(body.contains("\"liveApply\":"));
        Assert.assertTrue(body.contains("\"id\":\"system\""));

        // Validation of the level endpoint inputs. The logger endpoint never
        // creates loggers: an unknown name is a 404.
        request(client, "POST", MANAGER2 + "/api/logs/config/level", token,
                "{\"context\":\"nonsense\",\"name\":\"x\",\"level\":\"FINE\"}", 400);
        request(client, "POST", MANAGER2 + "/api/logs/config/level", token,
                "{\"context\":\"system\",\"name\":\"\",\"level\":\"FINE\"}", 400);
        request(client, "POST", MANAGER2 + "/api/logs/config/level", token,
                "{\"context\":\"system\",\"name\":\"no.such.logger.xyz\",\"level\":\"NOTALEVEL\"}", 400);
        request(client, "POST", MANAGER2 + "/api/logs/config/level", token,
                "{\"context\":\"system\",\"name\":\"no.such.logger.xyz\",\"level\":\"FINE\"}", 404);

        client.disconnect();
    }


    @Test
    public void testUsersApiReadOnlyRoleDenied() throws Exception {
        setup(true);

        SimpleHttpClient client = new TestClient();
        client.setPort(getPort());
        client.connect();
        login(client, "status1");

        // The users API is not part of the read-only status endpoints.
        request(client, "GET", MANAGER2 + "/api/users", null, null, 403);
        request(client, "GET", MANAGER2 + "/api/groups", null, null, 403);

        client.disconnect();
    }
}
