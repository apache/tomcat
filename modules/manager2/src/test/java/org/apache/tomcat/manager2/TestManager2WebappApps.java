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

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.startup.SimpleHttpClient;

/**
 * The application lifecycle API of the manager2 webapp: listing, start/stop/undeploy, deployment from a server-side
 * WAR, the session endpoints and the host and resource listings.
 */
public class TestManager2WebappApps extends Manager2WebappTestBase {

    @Test
    public void testAppLifecycleAndList() throws Exception {
        setup(false);

        SimpleHttpClient client = new TestClient();
        client.setPort(getPort());
        client.connect();
        String token = loginAndGetToken(client, "manager1");

        // The list of applications includes the test app.
        request(client, "GET", MANAGER2 + "/api/apps", null, null, 200);
        Assert.assertTrue(client.getResponseBody().contains("\"path\":\"" + TESTAPP + "\""));

        // The test app is deployed and serving.
        requestRaw(client, "GET", TESTAPP + "/", 200);

        // Stop the test app.
        request(client, "POST", MANAGER2 + "/api/apps/testapp/stop?path=%2Ftestapp", token, "{}", 200);
        Assert.assertTrue(client.getResponseBody().contains("\"ok\":true"));
        requestRaw(client, "GET", TESTAPP + "/", 404);

        // Start it again.
        request(client, "POST", MANAGER2 + "/api/apps/testapp/start?path=%2Ftestapp", token, "{}", 200);
        Assert.assertTrue(client.getResponseBody().contains("\"ok\":true"));
        requestRaw(client, "GET", TESTAPP + "/", 200);

        // Undeploy it.
        request(client, "DELETE", MANAGER2 + "/api/apps/testapp?path=%2Ftestapp", token, null, 200);
        Assert.assertTrue(client.getResponseBody().contains("\"ok\":true"));
        requestRaw(client, "GET", TESTAPP + "/", 404);

        client.disconnect();
    }


    @Test
    public void testHostsEndpoint() throws Exception {
        setup(false);

        SimpleHttpClient client = new TestClient();
        client.setPort(getPort());
        client.connect();
        login(client, "manager1");

        request(client, "GET", MANAGER2 + "/api/hosts", null, null, 200);
        Assert.assertTrue(client.getResponseBody().contains("\"name\":\"localhost\""));
        // The default host is up, so it must be reported as started
        // (state STARTED is not the same thing as the raw state name the
        // UI used to match on).
        Assert.assertTrue(client.getResponseBody().contains("\"state\":\"STARTED\""));
        Assert.assertTrue(client.getResponseBody().contains("\"started\":true"));
        Assert.assertTrue(client.getResponseBody().contains("\"self\":true"));

        client.disconnect();
    }


    @Test
    public void testResourcesEndpointDropsStatusLine() throws Exception {
        setup(false, true);

        File xmlFile = new File(getTemporaryDirectory(), "tomcat-users-resources.xml");
        writeUserDatabaseXml(xmlFile, "");
        addUserDatabase("UserDatabase", xmlFile, false);

        SimpleHttpClient client = new TestClient();
        client.setPort(getPort());
        client.connect();
        login(client, "manager1");

        request(client, "GET", MANAGER2 + "/api/resources", null, null, 200);
        String body = client.getResponseBody();
        // The registered resource is listed with its class name (the
        // resolved implementation class of the factory).
        Assert.assertTrue(body.contains("UserDatabase:org.apache.catalina.users.MemoryUserDatabase"));
        // The human readable status line the classic manager renders first
        // ("OK - Listed global resources of all types") is not part of the
        // resource list.
        Assert.assertFalse(body.contains("Listed global resources"));

        client.disconnect();
    }


    @Test
    public void testDeployFromServerWar() throws Exception {
        setup(false);

        File warFile = createTestWar();

        SimpleHttpClient client = new TestClient();
        client.setPort(getPort());
        client.connect();
        String token = loginAndGetToken(client, "manager1");

        // Deploy from a server-side WAR location.
        String body = "{\"path\":\"/deployed\",\"war\":\"file://" + warFile.getAbsolutePath() + "\"}";
        request(client, "POST", MANAGER2 + "/api/apps/deploy", token, body, 200);
        Assert.assertTrue(client.getResponseBody().contains("\"ok\":true"));
        requestRaw(client, "GET", "/deployed/", 200);

        // Undeploy again.
        request(client, "DELETE", MANAGER2 + "/api/apps/deployed?path=%2Fdeployed", token, null, 200);
        Assert.assertTrue(client.getResponseBody().contains("\"ok\":true"));

        client.disconnect();
    }


    @Test
    public void testSessionsFlow() throws Exception {
        setup(false);

        SimpleHttpClient client = new TestClient();
        client.setPort(getPort());
        client.connect();

        // Create a session in the test app.
        requestRaw(client, "GET", TESTAPP + "/session.jsp", 200);

        String token = loginAndGetToken(client, "manager1");

        // List sessions.
        request(client, "GET", MANAGER2 + "/api/apps/testapp/sessions?path=%2Ftestapp", null, null, 200);
        String body = client.getResponseBody();
        Assert.assertTrue(body.contains("\"sessions\":["));
        int idStart = body.indexOf("\"id\":\"");
        Assert.assertTrue("Expected at least one session", idStart >= 0);
        String sessionId = body.substring(idStart + 6, body.indexOf('"', idStart + 6));

        // Session detail includes attributes.
        request(client, "GET", MANAGER2 + "/api/apps/testapp/sessions/" + sessionId + "?path=%2Ftestapp", null, null,
                200);
        Assert.assertTrue(client.getResponseBody().contains("\"name\":\"testAttr\""));

        // Invalidate the session.
        request(client, "POST", MANAGER2 + "/api/apps/testapp/sessions/invalidate?path=%2Ftestapp", token,
                "{\"ids\":[\"" + sessionId + "\"]}", 200);
        Assert.assertTrue(client.getResponseBody().contains("\"count\":1"));

        client.disconnect();
    }
}
