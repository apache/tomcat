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
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.startup.SimpleHttpClient;

/**
 * The read side of the manager2 configuration API: the component tree, the node details and their descriptions,
 * the root context and the attribute round trip with its guards.
 */
public class TestManager2ConfigTree extends Manager2ConfigTestBase {

    @Test
    public void testTreeShape() throws Exception {
        setup();

        SimpleHttpClient client = new TestClient();
        client.setPort(getPort());
        client.connect();
        login(client, "manager1");

        request(client, "GET", MANAGER2 + "/api/config/tree", null, null, 200);
        Map<String, Object> root = parseObject(client.getResponseBody());
        Map<String, Object> tree = getMap(root, "tree");

        Assert.assertEquals("server", tree.get("type"));
        Assert.assertEquals("server", tree.get("id"));

        // The default service / engine / host are present. The service and
        // engine names are not fixed, so resolve them by type.
        Map<String, Object> service = firstChildOfType(tree, "service");
        Assert.assertNotNull("Expected a service", service);
        Map<String, Object> engine = firstChildOfType(service, "engine");
        Assert.assertNotNull("Expected an engine", engine);
        Map<String, Object> host = findChild(engine, "host", "localhost");
        Assert.assertNotNull("Expected the localhost host", host);

        // The manager2 context is a child of the host, addressed by its
        // encoded path, and is flagged as the self component.
        Map<String, Object> self = firstChildOfType(host, "context");
        Assert.assertNotNull("Expected the manager2 context", self);
        Assert.assertEquals(Boolean.TRUE, self.get("self"));
        Assert.assertTrue(((String) self.get("id")).endsWith("/context/+manager2"));

        client.disconnect();
    }


    @Test
    public void testNodeDetails() throws Exception {
        setup();

        SimpleHttpClient client = new TestClient();
        client.setPort(getPort());
        client.connect();
        login(client, "manager1");

        request(client, "GET", MANAGER2 + "/api/config/node/server", null, null, 200);
        Map<String, Object> node = parseObject(client.getResponseBody());
        Assert.assertEquals("server", node.get("id"));
        Assert.assertEquals("server", node.get("type"));
        Assert.assertNotNull(node.get("className"));
        Assert.assertTrue(getList(node, "properties").size() > 0);
        // The descriptions come from the message bundle: the standard,
        // descriptor-backed attributes of a seeded type are overridden with
        // the (translatable) bundle text.
        Assert.assertEquals("TCP port (excluding any offset) for shutdown messages",
                propertyDescription(node, "port"));

        // The self context reports the self flag and its id.
        String ctxId = selfContextId(fetchTree(client));
        request(client, "GET", MANAGER2 + "/api/config/node/" + ctxId, null, null, 200);
        node = parseObject(client.getResponseBody());
        Assert.assertEquals("context", node.get("type"));
        Assert.assertEquals(Boolean.TRUE, node.get("self"));
        Assert.assertTrue(((String) node.get("id")).endsWith("/context/+manager2"));
        Assert.assertEquals("The display name of this web application",
                propertyDescription(node, "displayName"));

        // A component without a modeler descriptor: the description of an
        // explicitly defined attribute is looked up from the bundle by scope
        // and name (manager2.attr.cookieProcessor.sameSiteCookies).
        request(client, "GET", MANAGER2 + "/api/config/node/" + ctxId + "/cookieProcessor/0", null, null, 200);
        node = parseObject(client.getResponseBody());
        Assert.assertEquals("cookieProcessor", node.get("type"));
        Assert.assertEquals(
                "The SameSite attribute added to the cookies of this web application (Unset, None, Lax or Strict).",
                propertyDescription(node, "sameSiteCookies"));

        // An unknown node is reported.
        request(client, "GET", MANAGER2 + "/api/config/node/nowhere", null, null, 404);
        Assert.assertTrue(client.getResponseBody().contains("NOT_FOUND"));

        client.disconnect();
    }


    @Test
    public void testRootContext() throws Exception {
        setup();

        // Add a root context (the empty path) to the default host, like a
        // ROOT webapp deployed from the app base.
        File rootDocBase = new File(getTemporaryDirectory(), "rootapp");
        Assert.assertTrue(rootDocBase.isDirectory() || rootDocBase.mkdirs());
        getTomcatInstance().addWebapp(null, "", rootDocBase.getAbsolutePath());

        SimpleHttpClient client = new TestClient();
        client.setPort(getPort());
        client.connect();
        String token = loginAndGetToken(client, "manager1");

        // The root context is named "/" and addressed with a bare "+"
        // segment in its node id.
        Map<String, Object> tree = fetchTree(client);
        Map<String, Object> service = firstChildOfType(tree, "service");
        Map<String, Object> engine = firstChildOfType(service, "engine");
        Map<String, Object> hostNode = findChild(engine, "host", "localhost");
        Map<String, Object> rootNode = findChild(hostNode, "context", "/");
        Assert.assertNotNull("Expected the root context", rootNode);
        String rootId = (String) rootNode.get("id");
        Assert.assertTrue("Unexpected root context id: " + rootId, rootId.endsWith("/context/+"));

        // The node detail of the root context resolves...
        request(client, "GET", MANAGER2 + "/api/config/node/" + rootId, null, null, 200);
        Map<String, Object> node = parseObject(client.getResponseBody());
        Assert.assertEquals("context", node.get("type"));
        Assert.assertEquals("/", node.get("name"));

        // ...and so do child components of the root context.
        request(client, "POST", MANAGER2 + "/api/config/child", token,
                "{\"parent\":\"" + rootId + "\",\"type\":\"wrapper\"," + "\"name\":\"e2e-root-default\"," +
                        "\"servletClass\":\"org.apache.catalina.servlets.DefaultServlet\"}",
                200);
        request(client, "GET", MANAGER2 + "/api/config/node/" + rootId, null, null, 200);
        node = parseObject(client.getResponseBody());
        Map<String, Object> wrapper = findChild(node, "wrapper", "e2e-root-default");
        Assert.assertNotNull("Expected the wrapper in the root context", wrapper);
        request(client, "GET", MANAGER2 + "/api/config/node/" + wrapper.get("id"), null, null, 200);
        node = parseObject(client.getResponseBody());
        Assert.assertEquals("wrapper", node.get("type"));

        client.disconnect();
    }


    @Test
    public void testAttributeRoundTripAndGuards() throws Exception {
        setup();

        SimpleHttpClient client = new TestClient();
        client.setPort(getPort());
        client.connect();
        String token = loginAndGetToken(client, "manager1");

        String ctxId = selfContextId(fetchTree(client));

        // Read the current (writable) sessionTimeout of the self context.
        request(client, "GET", MANAGER2 + "/api/config/node/" + ctxId, null, null, 200);
        Map<String, Object> node = parseObject(client.getResponseBody());
        Number original = getInt(findProperty(node, "sessionTimeout").get("value"));
        Assert.assertNotNull("Expected a sessionTimeout property", original);

        // Update it and read it back.
        int updated = original.intValue() + 1;
        request(client, "POST", MANAGER2 + "/api/config/attribute", token,
                "{\"id\":\"" + ctxId + "\",\"name\":\"sessionTimeout\",\"value\":" + updated + "}", 200);
        Assert.assertTrue(client.getResponseBody().contains("\"ok\":true"));
        request(client, "GET", MANAGER2 + "/api/config/node/" + ctxId, null, null, 200);
        node = parseObject(client.getResponseBody());
        Assert.assertEquals(updated, getInt(findProperty(node, "sessionTimeout").get("value")).intValue());

        // Restore the original value.
        request(client, "POST", MANAGER2 + "/api/config/attribute", token,
                "{\"id\":\"" + ctxId + "\",\"name\":\"sessionTimeout\",\"value\":" + original + "}", 200);

        // Guards ----------------------------------------------------------------

        // A read-only attribute is rejected.
        request(client, "POST", MANAGER2 + "/api/config/attribute", token,
                "{\"id\":\"server\",\"name\":\"portWithOffset\",\"value\":8005}", 400);
        Assert.assertTrue(client.getResponseBody().contains("READ_ONLY"));

        // An unknown attribute is reported.
        request(client, "POST", MANAGER2 + "/api/config/attribute", token,
                "{\"id\":\"server\",\"name\":\"noSuchAttribute\",\"value\":1}", 404);
        Assert.assertTrue(client.getResponseBody().contains("ATTRIBUTE_NOT_FOUND"));

        // A value that does not convert to the attribute type is rejected.
        request(client, "POST", MANAGER2 + "/api/config/attribute", token,
                "{\"id\":\"" + ctxId + "\",\"name\":\"sessionTimeout\",\"value\":\"not-a-number\"}", 400);
        Assert.assertTrue(client.getResponseBody().contains("INVALID_VALUE"));

        // The name/path of the self context cannot be changed. The
        // self-component guard takes precedence over the risky-attribute
        // confirmation, with or without a confirm.
        request(client, "POST", MANAGER2 + "/api/config/attribute", token,
                "{\"id\":\"" + ctxId + "\",\"name\":\"path\",\"value\":\"/renamed\"}", 403);
        Assert.assertTrue(client.getResponseBody().contains("SELF_COMPONENT"));
        request(client, "POST", MANAGER2 + "/api/config/attribute", token,
                "{\"id\":\"" + ctxId + "\",\"name\":\"path\",\"value\":\"/renamed\",\"confirm\":\"/manager2\"}", 403);
        Assert.assertTrue(client.getResponseBody().contains("SELF_COMPONENT"));

        client.disconnect();
    }
}
