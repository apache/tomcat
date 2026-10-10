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
 * Access control of the manager2 configuration API: FORM authentication, the read-only status role and the CSRF
 * filter.
 */
public class TestManager2ConfigAccess extends Manager2ConfigTestBase {

    @Test
    public void testUnauthenticatedTreeShowsLoginPage() throws Exception {
        setup();

        SimpleHttpClient client = new TestClient();
        client.setPort(getPort());
        client.connect();

        // The tree is a JSON API endpoint: unauthenticated requests are
        // forwarded to the login page (FORM authentication).
        requestRaw(client, "GET", MANAGER2 + "/api/config/tree", 200);
        Assert.assertTrue(client.getResponseBody().contains("j_security_check"));

        client.disconnect();
    }


    @Test
    public void testReadOnlyRoleDenied() throws Exception {
        setup();

        SimpleHttpClient client = new TestClient();
        client.setPort(getPort());
        client.connect();
        login(client, "status1");

        // The config API is not part of the read-only status endpoints; it
        // requires the manager-gui role.
        request(client, "GET", MANAGER2 + "/api/config/tree", null, null, 403);
        String token = getCsrfToken(client);
        request(client, "POST", MANAGER2 + "/api/config/child", token, "{\"parent\":\"server\",\"type\":\"alias\"}",
                403);

        client.disconnect();
    }


    @Test
    public void testMutateWithoutCsrfTokenIsRejected() throws Exception {
        setup();

        SimpleHttpClient client = new TestClient();
        client.setPort(getPort());
        client.connect();
        login(client, "manager1");

        // A mutation without a CSRF token is rejected by the CSRF filter.
        request(client, "POST", MANAGER2 + "/api/config/attribute", null,
                "{\"id\":\"server\",\"name\":\"port\",\"value\":8005}", 403);

        client.disconnect();
    }
}
