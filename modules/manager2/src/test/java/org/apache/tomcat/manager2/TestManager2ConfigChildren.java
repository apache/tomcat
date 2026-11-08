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

import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.startup.SimpleHttpClient;

/**
 * Structural changes through the manager2 configuration API: adding and removing child components of every
 * supported type, with the add validation and the remove guards.
 */
public class TestManager2ConfigChildren extends Manager2ConfigTestBase {

    @Test
    public void testAddRemoveChildTypes() throws Exception {
        setup();

        SimpleHttpClient client = new TestClient();
        client.setPort(getPort());
        client.connect();
        String token = loginAndGetToken(client, "manager1");

        String serviceId = null;
        String engineId = null;
        String hostId = null;
        String contextId = null;
        String wrapperId = null;
        String valveId = null;
        String connectorId = null;
        String executorId = "server/service/Catalina2/executor/e2e-exec";
        String aliasId = "server/service/Catalina2/engine/Catalina2/host/e2e-host/alias/e2e-alias";

        try {
            // service (creates service + engine) -------------------------
            request(client, "POST", MANAGER2 + "/api/config/child", token,
                    "{\"parent\":\"server\",\"type\":\"service\",\"name\":\"Catalina2\"}", 200);
            Assert.assertTrue(client.getResponseBody().contains("\"ok\":true"));
            serviceId = "server/service/Catalina2";
            engineId = serviceId + "/engine/Catalina2";

            // connector ---------------------------------------------------
            int port = freePort();
            request(client, "POST", MANAGER2 + "/api/config/child", token, "{\"parent\":\"" + serviceId +
                    "\",\"type\":\"connector\",\"protocol\":\"HTTP/1.1\",\"port\":" + port + "}", 200);
            connectorId = serviceId + "/connector/0";

            // executor ----------------------------------------------------
            request(client, "POST", MANAGER2 + "/api/config/child", token, "{\"parent\":\"" + serviceId +
                    "\",\"type\":\"executor\",\"name\":\"e2e-exec\"," + "\"maxThreads\":10,\"minSpareThreads\":2}",
                    200);

            // host --------------------------------------------------------
            request(client, "POST", MANAGER2 + "/api/config/child", token,
                    "{\"parent\":\"" + engineId + "\",\"type\":\"host\",\"name\":\"e2e-host\"}", 200);
            hostId = engineId + "/host/e2e-host";

            // alias -------------------------------------------------------
            request(client, "POST", MANAGER2 + "/api/config/child", token,
                    "{\"parent\":\"" + hostId + "\",\"type\":\"alias\",\"alias\":\"e2e-alias\"}", 200);

            // context -----------------------------------------------------
            request(client, "POST", MANAGER2 + "/api/config/child", token,
                    "{\"parent\":\"" + hostId + "\",\"type\":\"context\",\"path\":\"/e2eapp\"}", 200);
            contextId = hostId + "/context/+e2eapp";

            // wrapper -----------------------------------------------------
            request(client, "POST", MANAGER2 + "/api/config/child", token,
                    "{\"parent\":\"" + contextId + "\",\"type\":\"wrapper\",\"name\":\"e2ewrapper\"," +
                            "\"servletClass\":\"org.apache.catalina.servlets.DefaultServlet\"}",
                    200);
            wrapperId = contextId + "/wrapper/e2ewrapper";

            // valve -------------------------------------------------------
            request(client, "POST", MANAGER2 + "/api/config/child", token, "{\"parent\":\"" + contextId +
                    "\",\"type\":\"valve\"," + "\"className\":\"org.apache.catalina.valves.RemoteIpValve\"}", 200);

            // Verify the whole branch is visible in the tree --------------
            request(client, "GET", MANAGER2 + "/api/config/tree", null, null, 200);
            String tree = client.getResponseBody();
            Assert.assertTrue(tree.contains(serviceId));
            Assert.assertTrue(tree.contains(hostId));
            Assert.assertTrue(tree.contains(contextId));
            Assert.assertTrue(tree.contains(wrapperId));
            Assert.assertTrue(tree.contains(aliasId));
            Assert.assertTrue(tree.contains(executorId));
            Assert.assertTrue(tree.contains(connectorId));

            // The valve id is positional; resolve it from the node detail.
            request(client, "GET", MANAGER2 + "/api/config/node/" + contextId, null, null, 200);
            Map<String, Object> ctx = parseObject(client.getResponseBody());
            valveId = findChild(ctx, "valve", "RemoteIpValve").get("id").toString();

            // Remove each child (leaves first) ---------------------------
            request(client, "DELETE", MANAGER2 + "/api/config/child", token,
                    "{\"id\":\"" + connectorId + "\",\"confirm\":\"HTTP/1.1 (port " + port + ")\"}", 200);
            request(client, "DELETE", MANAGER2 + "/api/config/child", token,
                    "{\"id\":\"" + executorId + "\",\"confirm\":\"e2e-exec\"}", 200);
            request(client, "DELETE", MANAGER2 + "/api/config/child", token,
                    "{\"id\":\"" + wrapperId + "\",\"confirm\":\"e2ewrapper\"}", 200);
            request(client, "DELETE", MANAGER2 + "/api/config/child", token,
                    "{\"id\":\"" + valveId + "\",\"confirm\":\"RemoteIpValve\"}", 200);
            request(client, "DELETE", MANAGER2 + "/api/config/child", token, "{\"id\":\"" + aliasId + "\"}", 200);
            request(client, "DELETE", MANAGER2 + "/api/config/child", token,
                    "{\"id\":\"" + contextId + "\",\"confirm\":\"/e2eapp\"}", 200);
            request(client, "DELETE", MANAGER2 + "/api/config/child", token,
                    "{\"id\":\"" + hostId + "\",\"confirm\":\"e2e-host\"}", 200);
            request(client, "DELETE", MANAGER2 + "/api/config/child", token,
                    "{\"id\":\"" + serviceId + "\",\"confirm\":\"Catalina2\"}", 200);

            // The branch is gone.
            request(client, "GET", MANAGER2 + "/api/config/tree", null, null, 200);
            Assert.assertFalse(client.getResponseBody().contains(serviceId));
        } finally {
            cleanup(client, token, serviceId, hostId, contextId, wrapperId, valveId, connectorId, executorId, aliasId);
        }

        client.disconnect();
    }


    @Test
    public void testAddRemoveListener() throws Exception {
        setup();

        SimpleHttpClient client = new TestClient();
        client.setPort(getPort());
        client.connect();
        String token = loginAndGetToken(client, "manager1");

        // Resolve the default host from the tree.
        Map<String, Object> tree = fetchTree(client);
        Map<String, Object> service = firstChildOfType(tree, "service");
        Map<String, Object> engine = firstChildOfType(service, "engine");
        Map<String, Object> hostNode = findChild(engine, "host", "localhost");
        String hostId = (String) hostNode.get("id");

        // The node detail reports whether the component accepts a
        // lifecycle listener.
        request(client, "GET", MANAGER2 + "/api/config/node/" + hostId, null, null, 200);
        Assert.assertEquals(Boolean.TRUE, parseObject(client.getResponseBody()).get("acceptsListener"));

        // Add a listener...
        request(client, "POST", MANAGER2 + "/api/config/child", token,
                "{\"parent\":\"" + hostId + "\",\"type\":\"listener\"," +
                        "\"className\":\"org.apache.catalina.mbeans.GlobalResourcesLifecycleListener\"}",
                200);
        Assert.assertTrue(client.getResponseBody().contains("\"ok\":true"));

        // ...it shows up in the tree and its node detail resolves...
        tree = fetchTree(client);
        hostNode = findChild(firstChildOfType(firstChildOfType(tree, "service"), "engine"), "host", "localhost");
        Map<String, Object> listener = findChild(hostNode, "listener", "GlobalResourcesLifecycleListener");
        Assert.assertNotNull("Expected the listener in the tree", listener);
        String listenerId = (String) listener.get("id");
        request(client, "GET", MANAGER2 + "/api/config/node/" + listenerId, null, null, 200);
        Map<String, Object> listenerDetail = parseObject(client.getResponseBody());
        Assert.assertEquals("listener", listenerDetail.get("type"));
        Assert.assertEquals("GlobalResourcesLifecycleListener", listenerDetail.get("name"));

        // ...and it can be removed (no confirmation is required for
        // listeners).
        request(client, "DELETE", MANAGER2 + "/api/config/child", token, "{\"id\":\"" + listenerId + "\"}", 200);
        Assert.assertTrue(client.getResponseBody().contains("\"ok\":true"));
        tree = fetchTree(client);
        hostNode = findChild(firstChildOfType(firstChildOfType(tree, "service"), "engine"), "host", "localhost");
        Assert.assertNull(findChild(hostNode, "listener", "GlobalResourcesLifecycleListener"));

        // Guards: a class that is not a lifecycle listener (or does not
        // exist) is rejected...
        request(client, "POST", MANAGER2 + "/api/config/child", token,
                "{\"parent\":\"" + hostId + "\",\"type\":\"listener\"," + "\"className\":\"java.lang.String\"}", 400);
        Assert.assertTrue(client.getResponseBody().contains("INVALID_CLASS"));
        request(client, "POST", MANAGER2 + "/api/config/child", token, "{\"parent\":\"" + hostId +
                "\",\"type\":\"listener\"," + "\"className\":\"org.example.NoSuchListener\"}", 400);
        Assert.assertTrue(client.getResponseBody().contains("INVALID_CLASS"));

        // ...and an alias is not a valid parent.
        request(client, "POST", MANAGER2 + "/api/config/child", token,
                "{\"parent\":\"" + hostId + "\",\"type\":\"alias\",\"alias\":\"e2e-lst-alias\"}", 200);
        String aliasId = hostId + "/alias/e2e-lst-alias";
        request(client, "POST", MANAGER2 + "/api/config/child", token,
                "{\"parent\":\"" + aliasId + "\",\"type\":\"listener\"," +
                        "\"className\":\"org.apache.catalina.mbeans.GlobalResourcesLifecycleListener\"}",
                400);
        Assert.assertTrue(client.getResponseBody().contains("BAD_PARENT"));
        request(client, "DELETE", MANAGER2 + "/api/config/child", token, "{\"id\":\"" + aliasId + "\"}", 200);

        client.disconnect();
    }


    @Test
    public void testAddValidationErrors() throws Exception {
        setup();

        SimpleHttpClient client = new TestClient();
        client.setPort(getPort());
        client.connect();
        String token = loginAndGetToken(client, "manager1");

        Map<String, Object> tree = fetchTree(client);
        Map<String, Object> service = firstChildOfType(tree, "service");
        Assert.assertNotNull("Expected a service", service);
        String serviceId = (String) service.get("id");
        String serviceName = (String) service.get("name");
        String ctxId = selfContextId(tree);

        // An unsupported child type.
        request(client, "POST", MANAGER2 + "/api/config/child", token, "{\"parent\":\"server\",\"type\":\"bogus\"}",
                400);
        Assert.assertTrue(client.getResponseBody().contains("UNSUPPORTED_TYPE"));

        // A wrong parent (a host under a context is invalid).
        request(client, "POST", MANAGER2 + "/api/config/child", token,
                "{\"parent\":\"" + ctxId + "\",\"type\":\"host\",\"name\":\"x\"}", 400);
        Assert.assertTrue(client.getResponseBody().contains("BAD_PARENT"));

        // A duplicate service name.
        request(client, "POST", MANAGER2 + "/api/config/child", token,
                "{\"parent\":\"server\",\"type\":\"service\",\"name\":\"" + serviceName + "\"}", 409);
        Assert.assertTrue(client.getResponseBody().contains("DUPLICATE"));

        // An invalid connector port.
        request(client, "POST", MANAGER2 + "/api/config/child", token,
                "{\"parent\":\"" + serviceId + "\",\"type\":\"connector\",\"port\":0}", 400);
        Assert.assertTrue(client.getResponseBody().contains("INVALID_VALUE"));

        // An executor whose minimum exceeds its maximum.
        request(client, "POST", MANAGER2 + "/api/config/child", token, "{\"parent\":\"" + serviceId +
                "\",\"type\":\"executor\",\"name\":\"e2e-bad\"," + "\"maxThreads\":10,\"minSpareThreads\":20}", 400);
        Assert.assertTrue(client.getResponseBody().contains("INVALID_VALUE"));

        client.disconnect();
    }


    @Test
    public void testRemoveGuards() throws Exception {
        setup();

        SimpleHttpClient client = new TestClient();
        client.setPort(getPort());
        client.connect();
        String token = loginAndGetToken(client, "manager1");

        Map<String, Object> tree = fetchTree(client);
        Map<String, Object> service = firstChildOfType(tree, "service");
        Assert.assertNotNull("Expected a service", service);
        String serviceId = (String) service.get("id");
        String serviceName = (String) service.get("name");
        String ctxId = selfContextId(tree);
        String hostId = hostIdOf(ctxId);

        // The last service cannot be removed.
        request(client, "DELETE", MANAGER2 + "/api/config/child", token,
                "{\"id\":\"" + serviceId + "\",\"confirm\":\"" + serviceName + "\"}", 400);
        Assert.assertTrue(client.getResponseBody().contains("LAST_SERVICE"));

        // The self context and self host cannot be removed.
        request(client, "DELETE", MANAGER2 + "/api/config/child", token,
                "{\"id\":\"" + ctxId + "\",\"confirm\":\"/manager2\"}", 403);
        Assert.assertTrue(client.getResponseBody().contains("SELF_COMPONENT"));
        request(client, "DELETE", MANAGER2 + "/api/config/child", token,
                "{\"id\":\"" + hostId + "\",\"confirm\":\"localhost\"}", 403);
        Assert.assertTrue(client.getResponseBody().contains("SELF_COMPONENT"));

        // The basic (first) valve of a pipeline cannot be removed.
        request(client, "DELETE", MANAGER2 + "/api/config/child", token,
                "{\"id\":\"" + ctxId + "/valve/0\",\"confirm\":\"x\"}", 400);
        Assert.assertTrue(client.getResponseBody().contains("BASIC_COMPONENT"));

        client.disconnect();
    }
}
