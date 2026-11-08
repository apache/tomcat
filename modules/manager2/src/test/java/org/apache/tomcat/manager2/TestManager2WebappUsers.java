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
 * The user, group and role management API of the manager2 webapp, backed by a file based {@code UserDatabase} JNDI
 * resource.
 */
public class TestManager2WebappUsers extends Manager2WebappTestBase {

    @Test
    public void testUsersApiWithoutUserDatabase() throws Exception {
        setup(false);

        // The programmatic test instance has no UserDatabase JNDI resource
        // configured, so the API reports that no user database is available.
        SimpleHttpClient client = new TestClient();
        client.setPort(getPort());
        client.connect();
        String token = loginAndGetToken(client, "manager1");

        request(client, "GET", MANAGER2 + "/api/users", null, null, 404);
        String body = client.getResponseBody();
        Assert.assertTrue(body.contains("\"error\":\"USER_DATABASE_MISSING\""));

        // Mutations are rejected for the same reason.
        request(client, "POST", MANAGER2 + "/api/users", token, "{\"username\":\"x\",\"password\":\"y\"}", 404);

        client.disconnect();
    }


    @Test
    public void testUserDatabaseReadonlyRejectsMutations() throws Exception {
        setup(false, true);

        File xmlFile = new File(getTemporaryDirectory(), "tomcat-users-readonly.xml");
        writeUserDatabaseXml(xmlFile, "  <role rolename=\"manager-gui\"/>\n" +
                "  <user username=\"manager1\" password=\"secret\" roles=\"manager-gui\"/>\n");
        addUserDatabase("Users", xmlFile, true);

        SimpleHttpClient client = new TestClient();
        client.setPort(getPort());
        client.connect();
        String token = loginAndGetToken(client, "manager1");

        // The list reports the database as read-only.
        request(client, "GET", MANAGER2 + "/api/users", null, null, 200);
        String body = client.getResponseBody();
        Assert.assertTrue(body.contains("\"name\":\"Users\""));
        Assert.assertTrue(body.contains("\"readonly\":true"));
        Assert.assertTrue(body.contains("\"username\":\"manager1\""));
        Assert.assertTrue(body.contains("\"rolename\":\"manager-gui\""));

        // Mutations are rejected while the database is read-only.
        request(client, "POST", MANAGER2 + "/api/users", token, "{\"username\":\"bob\",\"password\":\"secret\"}", 400);
        Assert.assertTrue(client.getResponseBody().contains("USER_DATABASE_READONLY"));

        client.disconnect();
    }


    @Test
    public void testUsersAndGroupsCrudAndPersistence() throws Exception {
        setup(false, true);

        File xmlFile = new File(getTemporaryDirectory(), "tomcat-users-test.xml");
        writeUserDatabaseXml(xmlFile, "  <role rolename=\"manager-gui\"/>\n" +
                "  <user username=\"manager1\" password=\"secret\" roles=\"manager-gui\"/>\n");
        addUserDatabase("Users", xmlFile, false);

        SimpleHttpClient client = new TestClient();
        client.setPort(getPort());
        client.connect();
        String token = loginAndGetToken(client, "manager1");

        // Create a user with a new role; the change is persisted to the XML
        // file.
        request(client, "POST", MANAGER2 + "/api/users", token,
                "{\"username\":\"alice\",\"password\":\"wonderland\",\"fullName\":\"Alice\"," +
                        "\"roles\":[\"manager-gui\",\"ops\"]}",
                200);
        Assert.assertTrue(client.getResponseBody().contains("\"ok\":true"));
        Assert.assertTrue(readFile(xmlFile).contains("username=\"alice\""));
        Assert.assertTrue(readFile(xmlFile).contains("rolename=\"ops\""));

        // The list shows the user, its roles and the new role definition.
        request(client, "GET", MANAGER2 + "/api/users", null, null, 200);
        String body = client.getResponseBody();
        Assert.assertTrue(body.contains("\"username\":\"alice\""));
        Assert.assertTrue(body.contains("\"fullName\":\"Alice\""));
        Assert.assertTrue(body.contains("\"hasPassword\":true"));
        Assert.assertTrue(body.contains("\"rolename\":\"ops\""));

        // The password can be changed and is persisted.
        request(client, "POST", MANAGER2 + "/api/users/alice/password", token, "{\"password\":\"newsecret\"}", 200);
        Assert.assertTrue(readFile(xmlFile).contains("password=\"newsecret\""));

        // The roles of a user can be replaced.
        request(client, "POST", MANAGER2 + "/api/users/alice/roles", token, "{\"roles\":[\"ops\"]}", 200);
        request(client, "GET", MANAGER2 + "/api/users", null, null, 200);
        Assert.assertTrue(client.getResponseBody()
                .contains("\"username\":\"alice\",\"fullName\":\"Alice\",\"hasPassword\":true,\"roles\":[\"ops\"]"));

        // Groups can be created, their members and roles set, and removed.
        request(client, "POST", MANAGER2 + "/api/groups", token,
                "{\"groupname\":\"staff\",\"description\":\"The staff\",\"roles\":[\"ops\"]}", 200);
        request(client, "POST", MANAGER2 + "/api/groups/staff/members", token, "{\"members\":[\"alice\",\"manager1\"]}",
                200);
        request(client, "GET", MANAGER2 + "/api/users", null, null, 200);
        body = client.getResponseBody();
        Assert.assertTrue(body.contains("\"groupname\":\"staff\""));
        Assert.assertTrue(body.contains("\"description\":\"The staff\""));
        Assert.assertTrue(body.contains("\"members\":[\"alice\",\"manager1\"]"));
        // alice inherits the group role in addition to her direct roles.
        Assert.assertTrue(body.contains("\"effectiveRoles\":[\"manager-gui\",\"ops\"]"));
        // The group is persisted.
        Assert.assertTrue(readFile(xmlFile).contains("groupname=\"staff\""));

        // Unknown members are rejected.
        request(client, "POST", MANAGER2 + "/api/groups/staff/members", token, "{\"members\":[\"nobody\"]}", 400);
        Assert.assertTrue(client.getResponseBody().contains("UNKNOWN_GROUP_MEMBER"));

        // The group roles can be replaced.
        request(client, "POST", MANAGER2 + "/api/groups/staff/roles", token, "{\"roles\":[\"manager-status\"]}", 200);

        // Removing the group detaches it from its members.
        request(client, "DELETE", MANAGER2 + "/api/groups/staff", token, null, 200);
        request(client, "GET", MANAGER2 + "/api/users", null, null, 200);
        body = client.getResponseBody();
        Assert.assertFalse(body.contains("\"groupname\":\"staff\""));
        Assert.assertFalse(body.contains("\"members\":[\"alice\""));

        // Roles can be created explicitly (with a description) and are
        // persisted to the XML file.
        request(client, "POST", MANAGER2 + "/api/roles", token,
                "{\"rolename\":\"audit\",\"description\":\"Audit role\"}", 200);
        Assert.assertTrue(readFile(xmlFile).contains("rolename=\"audit\""));
        request(client, "GET", MANAGER2 + "/api/users", null, null, 200);
        body = client.getResponseBody();
        Assert.assertTrue(body.contains("\"rolename\":\"audit\",\"description\":\"Audit role\""));

        // An existing role name is rejected.
        request(client, "POST", MANAGER2 + "/api/roles", token, "{\"rolename\":\"audit\"}", 409);
        Assert.assertTrue(client.getResponseBody().contains("ROLE_EXISTS"));

        // Removing a role detaches it from the users that hold it.
        request(client, "POST", MANAGER2 + "/api/users/alice/roles", token, "{\"roles\":[\"ops\",\"audit\"]}", 200);
        request(client, "DELETE", MANAGER2 + "/api/roles/audit", token, null, 200);
        request(client, "GET", MANAGER2 + "/api/users", null, null, 200);
        body = client.getResponseBody();
        Assert.assertFalse(body.contains("\"rolename\":\"audit\""));
        Assert.assertTrue(body.contains("\"roles\":[\"ops\"]"));
        Assert.assertFalse(readFile(xmlFile).contains("rolename=\"audit\""));

        // An unknown role is reported.
        request(client, "DELETE", MANAGER2 + "/api/roles/ghost", token, null, 404);
        Assert.assertTrue(client.getResponseBody().contains("ROLE_NOT_FOUND"));

        // A role held by the signed-in account cannot be removed.
        request(client, "DELETE", MANAGER2 + "/api/roles/manager-gui", token, null, 400);
        Assert.assertTrue(client.getResponseBody().contains("SELF_ROLE_REMOVAL"));

        // Removing a user removes it from the database and the file.
        request(client, "DELETE", MANAGER2 + "/api/users/alice", token, null, 200);
        Assert.assertFalse(readFile(xmlFile).contains("username=\"alice\""));

        // Existing names are rejected, unknown ones reported.
        request(client, "POST", MANAGER2 + "/api/users", token, "{\"username\":\"manager1\",\"password\":\"x\"}", 409);
        Assert.assertTrue(client.getResponseBody().contains("USER_EXISTS"));
        request(client, "POST", MANAGER2 + "/api/groups", token, "{\"groupname\":\"staff\"}", 200);
        request(client, "POST", MANAGER2 + "/api/groups", token, "{\"groupname\":\"staff\"}", 409);
        Assert.assertTrue(client.getResponseBody().contains("GROUP_EXISTS"));
        request(client, "DELETE", MANAGER2 + "/api/users/ghost", token, null, 404);
        Assert.assertTrue(client.getResponseBody().contains("USER_NOT_FOUND"));
        request(client, "DELETE", MANAGER2 + "/api/groups/ghost", token, null, 404);
        Assert.assertTrue(client.getResponseBody().contains("GROUP_NOT_FOUND"));

        // The signed-in account cannot remove itself.
        request(client, "DELETE", MANAGER2 + "/api/users/manager1", token, null, 400);
        Assert.assertTrue(client.getResponseBody().contains("SELF_REMOVAL"));

        client.disconnect();
    }
}
