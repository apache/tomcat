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
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

import static org.apache.catalina.startup.SimpleHttpClient.CRLF;
import org.apache.catalina.Context;
import org.apache.catalina.UserDatabase;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.core.StandardEngine;
import org.apache.catalina.realm.MemoryRealm;
import org.apache.catalina.servlets.DefaultServlet;
import org.apache.catalina.startup.SimpleHttpClient;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.json.JSONParser;


/**
 * Integration tests for the manager2 configuration API ({@code /api/config/*}). The tests deploy the
 * {@code manager2.war} built by this module (via the {@code deploy} target) into a throw-away Tomcat instance and drive
 * it over HTTP with {@link SimpleHttpClient}, exercising the component tree, attribute updates, structural add/remove
 * of child components, lifecycle operations (start / stop / restart), and persistence to {@code server.xml} through
 * storeconfig.
 */
public class TestManager2Config extends TomcatBaseTest {

    private static final String MANAGER2 = "/manager2";

    private String storeBaseProp = null;


    @After
    public void clearStoreBase() {
        if (storeBaseProp != null) {
            System.clearProperty("manager2.store.base");
            storeBaseProp = null;
        }
    }


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

        // The self context reports the self flag and its id.
        String ctxId = selfContextId(fetchTree(client));
        request(client, "GET", MANAGER2 + "/api/config/node/" + ctxId, null, null, 200);
        node = parseObject(client.getResponseBody());
        Assert.assertEquals("context", node.get("type"));
        Assert.assertEquals(Boolean.TRUE, node.get("self"));
        Assert.assertTrue(((String) node.get("id")).endsWith("/context/+manager2"));

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
    public void testLifecycle() throws Exception {
        setup(true);

        SimpleHttpClient client = new TestClient();
        client.setPort(getPort());
        client.connect();
        String token = loginAndGetToken(client, "manager1");

        String serviceId = null;
        String hostId = null;
        String contextId = null;

        try {
            // A throw-away service that never hosts this web application.
            request(client, "POST", MANAGER2 + "/api/config/child", token,
                    "{\"parent\":\"server\",\"type\":\"service\",\"name\":\"LifecycleSvc\"}", 200);
            Assert.assertTrue(client.getResponseBody().contains("\"ok\":true"));
            serviceId = "server/service/LifecycleSvc";
            request(client, "POST", MANAGER2 + "/api/config/child", token,
                    "{\"parent\":\"" + serviceId + "/engine/LifecycleSvc\"," +
                            "\"type\":\"host\",\"name\":\"lifecycle-host\"}", 200);
            hostId = serviceId + "/engine/LifecycleSvc/host/lifecycle-host";
            request(client, "POST", MANAGER2 + "/api/config/child", token,
                    "{\"parent\":\"" + hostId + "\",\"type\":\"context\",\"path\":\"/lifecycle\"}", 200);
            contextId = hostId + "/context/+lifecycle";

            // The node detail flags -------------------------------------
            //
            // The server is a lifecycle, and a start or stop of it always
            // affects this web application.
            Map<String, Object> serverNode = fetchNode(client, "server");
            Assert.assertEquals(Boolean.TRUE, serverNode.get("lifecycle"));
            Assert.assertEquals(Boolean.TRUE, serverNode.get("affectsSelf"));

            // The components that route this web application are reported
            // as affecting it as well. Their ids are derived from the id
            // of the self context (server/service/{s}/engine/{e}/host/{h}/
            // context/{p}).
            String selfCtxId = selfContextId(fetchTree(client));
            int ctxIx = selfCtxId.lastIndexOf("/context/");
            String selfHostId = selfCtxId.substring(0, ctxIx);
            int hostIx = selfHostId.lastIndexOf("/host/");
            String selfEngineId = selfHostId.substring(0, hostIx);
            int engIx = selfEngineId.lastIndexOf("/engine/");
            String selfServiceId = selfEngineId.substring(0, engIx);
            String selfConnectorId = selfServiceId + "/connector/0";
            for (String id : new String[] { selfServiceId, selfEngineId, selfHostId, selfCtxId,
                    selfConnectorId }) {
                Map<String, Object> node = fetchNode(client, id);
                Assert.assertEquals(Boolean.TRUE, node.get("lifecycle"));
                Assert.assertEquals("Expected " + id + " to affect this web application",
                        Boolean.TRUE, node.get("affectsSelf"));
            }
            // A wrapper of the context that runs this web application
            // affects it as well.
            Map<String, Object> selfWrapper = firstChildOfType(fetchNode(client, selfCtxId), "wrapper");
            Assert.assertNotNull(selfWrapper);
            Map<String, Object> wrapperNode = fetchNode(client, (String) selfWrapper.get("id"));
            Assert.assertEquals(Boolean.TRUE, wrapperNode.get("lifecycle"));
            Assert.assertEquals(Boolean.TRUE, wrapperNode.get("affectsSelf"));

            // The throw-away components are lifecycles as well, but they
            // do not affect this web application.
            for (String id : new String[] { serviceId, hostId, contextId }) {
                Map<String, Object> node = fetchNode(client, id);
                Assert.assertEquals(Boolean.TRUE, node.get("lifecycle"));
                Assert.assertNull("Expected " + id + " not to affect this web application",
                        node.get("affectsSelf"));
            }
            // A valve is a lifecycle as well (ValveBase extends
            // LifecycleBase).
            Map<String, Object> valveNode = fetchNode(client, selfCtxId + "/valve/0");
            Assert.assertEquals(Boolean.TRUE, valveNode.get("lifecycle"));
            Assert.assertNull(valveNode.get("affectsSelf"));

            // A component without a lifecycle (a JNDI entry) does not
            // report the flag at all.
            String envId = selfCtxId + "/namingResources/0/environment/lcenv";
            request(client, "POST", MANAGER2 + "/api/config/child", token,
                    "{\"parent\":\"" + selfCtxId + "/namingResources/0\",\"type\":\"environment\"," +
                            "\"name\":\"lcenv\",\"jndiType\":\"java.lang.String\",\"value\":\"x\"}",
                    200);
            Map<String, Object> envNode = fetchNode(client, envId);
            Assert.assertNull(envNode.get("lifecycle"));

            // Guards: a start or stop of a component that affects this
            // web application is refused (a restart is not - but it is
            // not exercised here, as it would restart the very server
            // that serves this test).
            for (String id : new String[] { "server", selfServiceId, selfEngineId, selfHostId,
                    selfCtxId, selfConnectorId, (String) selfWrapper.get("id") }) {
                request(client, "POST", MANAGER2 + "/api/config/lifecycle", token,
                        "{\"id\":\"" + id + "\",\"op\":\"stop\"}", 403);
                Assert.assertTrue(client.getResponseBody().contains("SELF_COMPONENT"));
                request(client, "POST", MANAGER2 + "/api/config/lifecycle", token,
                        "{\"id\":\"" + id + "\",\"op\":\"start\"}", 403);
                Assert.assertTrue(client.getResponseBody().contains("SELF_COMPONENT"));
            }

            // Stop / start / restart of a component that does not affect
            // this web application.
            //
            // Stop.
            request(client, "POST", MANAGER2 + "/api/config/lifecycle", token,
                    "{\"id\":\"" + contextId + "\",\"op\":\"stop\"}", 200);
            Assert.assertTrue(client.getResponseBody().contains("\"ok\":true"));
            Map<String, Object> ctx = fetchNode(client, contextId);
            Assert.assertEquals("STOPPED", ctx.get("state"));
            // A second stop is a (reported) no-op.
            request(client, "POST", MANAGER2 + "/api/config/lifecycle", token,
                    "{\"id\":\"" + contextId + "\",\"op\":\"stop\"}", 200);
            Assert.assertTrue(client.getResponseBody().contains("already stopped"));
            // Start.
            request(client, "POST", MANAGER2 + "/api/config/lifecycle", token,
                    "{\"id\":\"" + contextId + "\",\"op\":\"start\"}", 200);
            Assert.assertTrue(client.getResponseBody().contains("\"ok\":true"));
            ctx = fetchNode(client, contextId);
            Assert.assertEquals("STARTED", ctx.get("state"));
            // A second start is a (reported) no-op.
            request(client, "POST", MANAGER2 + "/api/config/lifecycle", token,
                    "{\"id\":\"" + contextId + "\",\"op\":\"start\"}", 200);
            Assert.assertTrue(client.getResponseBody().contains("already running"));
            // Restart (stop followed by start).
            request(client, "POST", MANAGER2 + "/api/config/lifecycle", token,
                    "{\"id\":\"" + contextId + "\",\"op\":\"restart\"}", 200);
            Assert.assertTrue(client.getResponseBody().contains("\"ok\":true"));
            ctx = fetchNode(client, contextId);
            Assert.assertEquals("STARTED", ctx.get("state"));

            // Guards: an unknown operation and a component without a
            // lifecycle are rejected.
            request(client, "POST", MANAGER2 + "/api/config/lifecycle", token,
                    "{\"id\":\"" + contextId + "\",\"op\":\"bogus\"}", 400);
            Assert.assertTrue(client.getResponseBody().contains("INVALID_OP"));
            request(client, "POST", MANAGER2 + "/api/config/lifecycle", token,
                    "{\"id\":\"" + envId + "\",\"op\":\"stop\"}", 400);
            Assert.assertTrue(client.getResponseBody().contains("NOT_A_LIFECYCLE"));

            // Remove the throw-away components (leaves first).
            request(client, "DELETE", MANAGER2 + "/api/config/child", token,
                    "{\"id\":\"" + envId + "\",\"confirm\":\"lcenv\"}", 200);
            request(client, "DELETE", MANAGER2 + "/api/config/child", token,
                    "{\"id\":\"" + contextId + "\",\"confirm\":\"/lifecycle\"}", 200);
            request(client, "DELETE", MANAGER2 + "/api/config/child", token,
                    "{\"id\":\"" + hostId + "\",\"confirm\":\"lifecycle-host\"}", 200);
            request(client, "DELETE", MANAGER2 + "/api/config/child", token,
                    "{\"id\":\"" + serviceId + "\",\"confirm\":\"LifecycleSvc\"}", 200);
        } finally {
            cleanup(client, token, serviceId, hostId, contextId, null, null, null, null, null);
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
    public void testCluster() throws Exception {
        setup();

        SimpleHttpClient client = new TestClient();
        client.setPort(getPort());
        client.connect();
        String token = loginAndGetToken(client, "manager1");

        Map<String, Object> tree = fetchTree(client);
        Map<String, Object> engine = firstChildOfType(firstChildOfType(tree, "service"), "engine");
        String engineId = (String) engine.get("id");

        // No cluster by default.
        Assert.assertNull(firstChildOfType(engine, "cluster"));

        // Add a cluster to the engine: this starts the channel (applying the
        // cluster defaults) and attaches the cluster to the engine.
        request(client, "POST", MANAGER2 + "/api/config/child", token, "{\"parent\":\"" + engineId +
                "\",\"type\":\"cluster\"," + "\"className\":\"org.apache.catalina.ha.tcp.SimpleTcpCluster\"}", 200);
        Assert.assertTrue(client.getResponseBody().contains("\"ok\":true"));

        // The cluster shows up with its default sub components.
        tree = fetchTree(client);
        engine = firstChildOfType(firstChildOfType(tree, "service"), "engine");
        Map<String, Object> cluster = firstChildOfType(engine, "cluster");
        Assert.assertNotNull("Expected the cluster in the tree", cluster);
        Assert.assertEquals("SimpleTcpCluster", cluster.get("name"));
        String clusterId = (String) cluster.get("id");

        Map<String, Object> channel = firstChildOfType(cluster, "channel");
        Assert.assertNotNull("Expected the channel in the cluster", channel);
        Assert.assertNotNull("Expected a cluster valve", firstChildOfType(cluster, "clusterValve"));
        Assert.assertNotNull("Expected the cluster manager", firstChildOfType(cluster, "clusterManager"));
        Assert.assertNotNull("Expected a cluster listener", firstChildOfType(cluster, "clusterListener"));
        String channelId = (String) channel.get("id");

        // The channel holds the membership, sender, receiver and the default
        // interceptor stack (two interceptors).
        Map<String, Object> channelDetail = fetchNode(client, channelId);
        Assert.assertEquals("channel", channelDetail.get("type"));
        Assert.assertNotNull(firstChildOfType(channelDetail, "membership"));
        Assert.assertNotNull(firstChildOfType(channelDetail, "sender"));
        Assert.assertNotNull(firstChildOfType(channelDetail, "receiver"));
        Assert.assertEquals(2, countChildrenOfType(channelDetail, "interceptor"));

        // Configure explicit attributes: the multicast membership (a
        // descriptor-less class with an explicit attribute list) and the
        // channel.
        String membershipId = channelId + "/membership/0";
        request(client, "POST", MANAGER2 + "/api/config/attribute", token,
                "{\"id\":\"" + membershipId + "\",\"name\":\"port\",\"value\":45599}", 200);
        Assert.assertTrue(client.getResponseBody().contains("\"ok\":true"));
        request(client, "POST", MANAGER2 + "/api/config/attribute", token,
                "{\"id\":\"" + channelId + "\",\"name\":\"name\",\"value\":\"e2e-cluster\"}", 200);
        Assert.assertTrue(client.getResponseBody().contains("\"ok\":true"));

        // The membership node detail reports the updated port.
        Map<String, Object> membershipDetail = fetchNode(client, membershipId);
        Assert.assertEquals("membership", membershipDetail.get("type"));
        Map<String, Object> port = findProperty(membershipDetail, "port");
        Assert.assertEquals(45599, ((Number) port.get("value")).intValue());

        // The configuration round-trips to server.xml (the cluster is stored
        // with the configured values). The response is a JSON object holding
        // the generated XML, so the xml field must be extracted first.
        request(client, "GET", MANAGER2 + "/api/config/store/preview", null, null, 200);
        String xml = (String) parseObject(client.getResponseBody()).get("xml");
        Assert.assertTrue(xml.contains("<Cluster"));
        Assert.assertTrue(xml.contains("<Channel"));
        Assert.assertTrue(xml.contains("name=\"e2e-cluster\""));
        Assert.assertTrue(xml.contains("port=\"45599\""));
        Assert.assertTrue(xml.contains("McastService"));
        Assert.assertTrue(xml.contains("DeltaManager"));

        // Add an extra interceptor (by class name).
        request(client, "POST", MANAGER2 + "/api/config/child", token,
                "{\"parent\":\"" + channelId + "\",\"type\":\"interceptor\"," +
                        "\"className\":\"org.apache.catalina.tribes.group" + ".interceptors.GzipInterceptor\"}",
                200);
        Assert.assertTrue(client.getResponseBody().contains("\"ok\":true"));
        Assert.assertEquals(3, countChildrenOfType(fetchNode(client, channelId), "interceptor"));

        // Guards: a valve that is not a cluster valve is rejected...
        request(client, "POST", MANAGER2 + "/api/config/child", token, "{\"parent\":\"" + clusterId +
                "\",\"type\":\"clusterValve\"," + "\"className\":\"org.apache.catalina.valves.AccessLogValve\"}", 400);
        Assert.assertTrue(client.getResponseBody().contains("INVALID_CLASS"));
        // ...and a cluster valve cannot be removed from a running cluster.
        Map<String, Object> clusterValve = firstChildOfType(cluster, "clusterValve");
        request(client, "DELETE", MANAGER2 + "/api/config/child", token,
                "{\"id\":\"" + clusterValve.get("id") + "\",\"confirm\":\"" + clusterValve.get("name") + "\"}", 400);
        Assert.assertTrue(client.getResponseBody().contains("REMOVE_NOT_SUPPORTED"));

        // Remove the cluster (requires confirmation).
        request(client, "DELETE", MANAGER2 + "/api/config/child", token,
                "{\"id\":\"" + clusterId + "\",\"confirm\":\"SimpleTcpCluster\"}", 200);
        Assert.assertTrue(client.getResponseBody().contains("\"ok\":true"));
        tree = fetchTree(client);
        engine = firstChildOfType(firstChildOfType(tree, "service"), "engine");
        Assert.assertNull(firstChildOfType(engine, "cluster"));

        client.disconnect();
    }


    /**
     * Fetch the node detail of the component with the given id.
     */
    private Map<String, Object> fetchNode(SimpleHttpClient client, String id) throws Exception {
        request(client, "GET", MANAGER2 + "/api/config/node/" + id, null, null, 200);
        return parseObject(client.getResponseBody());
    }


    /**
     * Count the direct children of a node with the given type.
     */
    private static long countChildrenOfType(Map<String, Object> node, String type) {
        List<Object> children = getList(node, "children");
        if (children == null) {
            return 0;
        }
        long count = 0;
        for (Object child : children) {
            @SuppressWarnings("unchecked")
            Map<String, Object> cm = (Map<String, Object>) child;
            if (type.equals(cm.get("type"))) {
                count++;
            }
        }
        return count;
    }


    @Test
    public void testSslHostConfig() throws Exception {
        setup();

        SimpleHttpClient client = new TestClient();
        client.setPort(getPort());
        client.connect();
        String token = loginAndGetToken(client, "manager1");

        // A PKCS12 keystore with an RSA key for the test certificate.
        File keystore = createKeystore(new File(getTemporaryDirectory(), "ssl-test"));

        // A dedicated service so that the TLS connector never is the
        // connector that hosts this web application itself.
        String serviceId = "server/service/CatalinaTLS";
        String connectorId = null;
        int port = 0;

        try {
            request(client, "POST", MANAGER2 + "/api/config/child", token,
                    "{\"parent\":\"server\",\"type\":\"service\",\"name\":\"CatalinaTLS\"}", 200);
            Assert.assertTrue(client.getResponseBody().contains("\"ok\":true"));

            port = freePort();
            request(client, "POST", MANAGER2 + "/api/config/child", token, "{\"parent\":\"" + serviceId +
                    "\",\"type\":\"connector\",\"protocol\":\"HTTP/1.1\"," + "\"port\":" + port + "}", 200);
            Assert.assertTrue(client.getResponseBody().contains("\"ok\":true"));
            connectorId = serviceId + "/connector/0";

            // The node detail of a connector reports whether TLS is
            // enabled (it is not, yet).
            request(client, "GET", MANAGER2 + "/api/config/node/" + connectorId, null, null, 200);
            Map<String, Object> connectorNode = parseObject(client.getResponseBody());
            Assert.assertEquals(Boolean.FALSE, connectorNode.get("sslEnabled"));
            Assert.assertEquals(Boolean.FALSE, findProperty(connectorNode, "sslEnabled").get("value"));

            // Guards ---------------------------------------------------

            // An SSL host configuration can only be added to a connector.
            request(client, "POST", MANAGER2 + "/api/config/child", token,
                    "{\"parent\":\"" + serviceId + "/engine/CatalinaTLS\"," + "\"type\":\"sslHostConfig\"}", 400);
            Assert.assertTrue(client.getResponseBody().contains("BAD_PARENT"));

            // The running connector cannot be switched to TLS without a
            // certificate.
            request(client, "POST", MANAGER2 + "/api/config/child", token,
                    "{\"parent\":\"" + connectorId + "\",\"type\":\"sslHostConfig\"}", 400);
            Assert.assertTrue(client.getResponseBody().contains("INVALID_VALUE"));
            // Neither attempt leaves a trace in the tree.
            Map<String, Object> tree = fetchTree(client);
            Map<String, Object> connectorNodeTree = findChildById(tree, connectorId);
            Assert.assertNull("Expected no SSL host configuration after the rollback",
                    findChild(connectorNodeTree, "sslHostConfig", "_default_"));

            // A certificate whose keystore cannot be loaded is rejected
            // (the TLS configuration is validated before it is
            // applied) and rolled back; the connector keeps serving
            // plain HTTP without being interrupted.
            request(client, "POST", MANAGER2 + "/api/config/child", token,
                    "{\"parent\":\"" + connectorId + "\",\"type\":\"sslHostConfig\"," +
                            "\"certificate\":{\"type\":\"RSA\"," +
                            "\"certificateKeystoreFile\":\"/does/not/exist.p12\"," +
                            "\"certificateKeystorePassword\":\"changeit\"}}",
                    400);
            Assert.assertTrue(client.getResponseBody().contains("ADD_FAILED"));
            Assert.assertNull("Expected no SSL host configuration after the rollback",
                    findChild(findChildById(fetchTree(client), connectorId), "sslHostConfig", "_default_"));

            // An unknown certificate type is rejected.
            request(client, "POST", MANAGER2 + "/api/config/child", token, "{\"parent\":\"" + connectorId +
                    "\",\"type\":\"sslHostConfig\"," + "\"certificate\":{\"type\":\"BOGUS\"}}", 400);
            Assert.assertTrue(client.getResponseBody().contains("INVALID_NAME"));

            // A certificate is only a child of an SSL host configuration.
            request(client, "POST", MANAGER2 + "/api/config/child", token,
                    "{\"parent\":\"" + connectorId + "\",\"type\":\"certificate\"}", 400);
            Assert.assertTrue(client.getResponseBody().contains("BAD_PARENT"));

            // Add ------------------------------------------------------

            // Add the SSL host configuration together with its
            // certificate. The running connector picks up TLS without
            // being interrupted and serves TLS from now on.
            request(client, "POST", MANAGER2 + "/api/config/child", token,
                    "{\"parent\":\"" + connectorId + "\",\"type\":\"sslHostConfig\"," +
                            "\"certificate\":{\"type\":\"RSA\",\"certificateKeystoreFile\":\"" + keystore.getPath() +
                            "\",\"certificateKeystorePassword\":\"changeit\"," +
                            "\"certificateKeyAlias\":\"tomcat\",\"certificateKeystoreType\":\"PKCS12\"}}",
                    200);
            Assert.assertTrue(client.getResponseBody().contains("\"ok\":true"));

            String sslHostConfigId = connectorId + "/sslHostConfig/_default_";
            String certificateId = sslHostConfigId + "/certificate/0";

            // The TLS branch is visible in the tree.
            tree = fetchTree(client);
            connectorNodeTree = findChildById(tree, connectorId);
            Assert.assertNotNull("Expected the connector in the tree", connectorNodeTree);
            Map<String, Object> sslNode = findChild(connectorNodeTree, "sslHostConfig", "_default_");
            Assert.assertNotNull("Expected the SSL host configuration in the tree", sslNode);
            Assert.assertEquals(sslHostConfigId, sslNode.get("id"));
            Map<String, Object> certNode = findChild(sslNode, "certificate", "RSA");
            Assert.assertNotNull("Expected the certificate in the tree", certNode);
            Assert.assertEquals(certificateId, certNode.get("id"));

            // The node details of the TLS components resolve...
            request(client, "GET", MANAGER2 + "/api/config/node/" + sslHostConfigId, null, null, 200);
            Map<String, Object> sslDetail = parseObject(client.getResponseBody());
            Assert.assertEquals("sslHostConfig", sslDetail.get("type"));
            Assert.assertEquals("_default_", sslDetail.get("name"));
            Assert.assertEquals(Boolean.TRUE, sslDetail.get("isDefault"));
            Map<String, Object> protocolsProp = findProperty(sslDetail, "protocols");
            Assert.assertNotNull("Expected a protocols property", protocolsProp);
            Assert.assertEquals(Boolean.TRUE, protocolsProp.get("writable"));
            request(client, "GET", MANAGER2 + "/api/config/node/" + certificateId, null, null, 200);
            Map<String, Object> certDetail = parseObject(client.getResponseBody());
            Assert.assertEquals("certificate", certDetail.get("type"));
            Assert.assertEquals("RSA", certDetail.get("name"));
            Assert.assertEquals("RSA", findProperty(certDetail, "type").get("value"));
            Assert.assertEquals(keystore.getPath(), findProperty(certDetail, "certificateKeystoreFile").get("value"));

            // ...as does the connector, which now reports TLS enabled.
            request(client, "GET", MANAGER2 + "/api/config/node/" + connectorId, null, null, 200);
            connectorNode = parseObject(client.getResponseBody());
            Assert.assertEquals(Boolean.TRUE, connectorNode.get("sslEnabled"));

            // The connector really serves TLS.
            int status = httpsGet(port, "/");
            Assert.assertTrue("Expected an HTTP response over TLS, got " + status, status >= 200 && status < 600);

            // An attribute of the SSL host configuration can be updated.
            request(client, "POST", MANAGER2 + "/api/config/attribute", token,
                    "{\"id\":\"" + sslHostConfigId + "\",\"name\":\"protocols\"," + "\"value\":\"TLSv1.2+TLSv1.3\"}",
                    200);
            request(client, "GET", MANAGER2 + "/api/config/node/" + sslHostConfigId, null, null, 200);
            sslDetail = parseObject(client.getResponseBody());
            // The protocol set is order independent.
            String protocols = (String) findProperty(sslDetail, "protocols").get("value");
            Assert.assertEquals(new java.util.HashSet<>(List.of("TLSv1.2", "TLSv1.3")),
                    new java.util.HashSet<>(List.of(protocols.split("\\+"))));

            // Read-only TLS attributes are protected.
            request(client, "POST", MANAGER2 + "/api/config/attribute", token,
                    "{\"id\":\"" + certificateId + "\",\"name\":\"type\",\"value\":\"DSA\"}", 400);
            Assert.assertTrue(client.getResponseBody().contains("READ_ONLY"));
            request(client, "POST", MANAGER2 + "/api/config/attribute", token,
                    "{\"id\":\"" + sslHostConfigId + "\",\"name\":\"hostName\",\"value\":\"x\"}", 400);
            Assert.assertTrue(client.getResponseBody().contains("READ_ONLY"));

            // A second certificate can be added (and is applied at once
            // on the running connector) ... For a certificate the
            // "type" field carries the certificate type, not a child
            // component type.
            request(client, "POST", MANAGER2 + "/api/config/child", token,
                    "{\"parent\":\"" + sslHostConfigId + "\",\"type\":\"RSA\"," + "\"certificateKeystoreFile\":\"" +
                            keystore.getPath() + "\",\"certificateKeystorePassword\":\"changeit\"," +
                            "\"certificateKeyAlias\":\"tomcat\",\"certificateKeystoreType\":\"PKCS12\"}",
                    200);
            Assert.assertTrue(client.getResponseBody().contains("\"ok\":true"));

            // ...and removed again.
            request(client, "DELETE", MANAGER2 + "/api/config/child", token,
                    "{\"id\":\"" + sslHostConfigId + "/certificate/1\"}", 200);
            Assert.assertTrue(client.getResponseBody().contains("\"ok\":true"));

            // The last certificate of a running, TLS enabled connector
            // cannot be removed (the connector would not be able to
            // start anymore).
            request(client, "DELETE", MANAGER2 + "/api/config/child", token, "{\"id\":\"" + certificateId + "\"}", 400);
            Assert.assertTrue(client.getResponseBody().contains("SSL_LAST_CERTIFICATE"));

            // The TLS configuration is part of the stored server.xml.
            request(client, "GET", MANAGER2 + "/api/config/store/preview", null, null, 200);
            String xml = (String) parseObject(client.getResponseBody()).get("xml");
            Assert.assertTrue(xml.contains("<SSLHostConfig"));
            Assert.assertTrue(xml.contains("certificateKeystoreFile=\""));
            Assert.assertTrue(xml.contains(keystore.getPath()));

            // A duplicate host name is rejected.
            request(client, "POST", MANAGER2 + "/api/config/child", token,
                    "{\"parent\":\"" + connectorId + "\",\"type\":\"sslHostConfig\"," +
                            "\"hostName\":\"_default_\",\"certificate\":{\"type\":\"RSA\"," +
                            "\"certificateKeystoreFile\":\"" + keystore.getPath() +
                            "\",\"certificateKeystorePassword\":\"changeit\"}}",
                    409);
            Assert.assertTrue(client.getResponseBody().contains("DUPLICATE"));

            // Remove ---------------------------------------------------

            // Remove the SSL host configuration: the connector is
            // restarted and serves plain HTTP again.
            request(client, "DELETE", MANAGER2 + "/api/config/child", token,
                    "{\"id\":\"" + sslHostConfigId + "\",\"confirm\":\"_default_\"}", 200);
            Assert.assertTrue(client.getResponseBody().contains("\"ok\":true"));
            request(client, "GET", MANAGER2 + "/api/config/node/" + connectorId, null, null, 200);
            connectorNode = parseObject(client.getResponseBody());
            Assert.assertEquals(Boolean.FALSE, connectorNode.get("sslEnabled"));
            // The connector serves plain HTTP again (no TLS handshake).
            int plainStatus = httpGetPlain(port, "/");
            Assert.assertTrue("Expected plain HTTP, got " + plainStatus, plainStatus >= 200 && plainStatus < 600);
        } finally {
            // Best effort cleanup (ignore failures, including assertion
            // errors: the failure of the test itself is reported).
            try {
                if (connectorId != null) {
                    request(client, "DELETE", MANAGER2 + "/api/config/child", token,
                            "{\"id\":\"" + connectorId + "\"," + "\"confirm\":\"HTTP/1.1 (port " + port + ")\"}", 200);
                }
            } catch (Throwable e) {
                // Best effort.
            }
            try {
                request(client, "DELETE", MANAGER2 + "/api/config/child", token,
                        "{\"id\":\"" + serviceId + "\",\"confirm\":\"CatalinaTLS\"}", 200);
            } catch (Throwable e) {
                // Best effort.
            }
        }

        client.disconnect();
    }


    @Test
    public void testUpgradeProtocol() throws Exception {
        setup();

        SimpleHttpClient client = new TestClient();
        client.setPort(getPort());
        client.connect();
        String token = loginAndGetToken(client, "manager1");

        // A dedicated service so that the test never touches the
        // connector that hosts this web application itself.
        String serviceId = "server/service/CatalinaH2";
        String connectorId = null;
        int port = 0;

        try {
            request(client, "POST", MANAGER2 + "/api/config/child", token,
                    "{\"parent\":\"server\",\"type\":\"service\",\"name\":\"CatalinaH2\"}", 200);
            Assert.assertTrue(client.getResponseBody().contains("\"ok\":true"));

            port = freePort();
            request(client, "POST", MANAGER2 + "/api/config/child", token, "{\"parent\":\"" + serviceId +
                    "\",\"type\":\"connector\",\"protocol\":\"HTTP/1.1\",\"port\":" + port + "}", 200);
            Assert.assertTrue(client.getResponseBody().contains("\"ok\":true"));
            connectorId = serviceId + "/connector/0";

            // Guards ---------------------------------------------------

            // An upgrade protocol can only be added to a connector.
            request(client, "POST", MANAGER2 + "/api/config/child", token,
                    "{\"parent\":\"" + serviceId + "/engine/CatalinaH2\"," + "\"type\":\"upgradeProtocol\"}", 400);
            Assert.assertTrue(client.getResponseBody().contains("BAD_PARENT"));

            // A class that is not an UpgradeProtocol is rejected.
            request(client, "POST", MANAGER2 + "/api/config/child", token,
                    "{\"parent\":\"" + connectorId + "\",\"type\":\"upgradeProtocol\"," +
                            "\"className\":\"java.lang.String\"}", 400);
            Assert.assertTrue(client.getResponseBody().contains("INVALID_CLASS"));

            // Add ------------------------------------------------------

            // Add with the default class (no className in the body): the
            // only UpgradeProtocol shipped with Tomcat is the HTTP/2 one.
            request(client, "POST", MANAGER2 + "/api/config/child", token,
                    "{\"parent\":\"" + connectorId + "\",\"type\":\"upgradeProtocol\"}", 200);
            Assert.assertTrue(client.getResponseBody().contains("\"ok\":true"));
            Assert.assertTrue(client.getResponseBody().contains("h2"));

            String upgradeProtocolId = connectorId + "/upgradeProtocol/0";

            // The protocol is visible in the tree ...
            Map<String, Object> tree = fetchTree(client);
            Map<String, Object> connectorNode = findChildById(tree, connectorId);
            Assert.assertNotNull("Expected the connector in the tree", connectorNode);
            Map<String, Object> upNode = findChild(connectorNode, "upgradeProtocol", "h2");
            Assert.assertNotNull("Expected the upgrade protocol in the tree", upNode);
            Assert.assertEquals(upgradeProtocolId, upNode.get("id"));
            Assert.assertEquals("org.apache.coyote.http2.Http2Protocol", upNode.get("className"));
            // ...and the node detail resolves (no lifecycle: no state).
            Map<String, Object> detail = fetchNode(client, upgradeProtocolId);
            Assert.assertEquals("upgradeProtocol", detail.get("type"));
            Assert.assertEquals("h2", detail.get("name"));
            Assert.assertEquals("org.apache.coyote.http2.Http2Protocol", detail.get("className"));
            Assert.assertNull(detail.get("state"));
            Assert.assertNull(detail.get("lifecycle"));

            // The node detail lists the HTTP/2 settings of the protocol
            // (the class has no modeler descriptor; the list is
            // explicit).
            Assert.assertEquals(22, getList(detail, "properties").size());
            Assert.assertEquals(5000L,
                    ((Number) findProperty(detail, "readTimeout").get("value")).longValue());
            Assert.assertEquals(100L,
                    ((Number) findProperty(detail, "maxConcurrentStreams").get("value")).longValue());
            Assert.assertEquals(Boolean.TRUE, findProperty(detail, "useSendfile").get("writable"));
            Assert.assertEquals(Boolean.TRUE, findProperty(detail, "maxConcurrentStreams").get("writable"));
            // The header/trailer sizes come from the HTTP/1.1 protocol
            // handler: they are read-only.
            Assert.assertEquals(Boolean.FALSE, findProperty(detail, "maxHeaderSize").get("writable"));
            Assert.assertEquals(Boolean.FALSE, findProperty(detail, "maxTrailerSize").get("writable"));

            // A setting can be updated; like the protocol itself it
            // takes effect when the connector is restarted.
            request(client, "POST", MANAGER2 + "/api/config/attribute", token,
                    "{\"id\":\"" + upgradeProtocolId + "\",\"name\":\"maxConcurrentStreams\",\"value\":500}", 200);
            Assert.assertTrue(client.getResponseBody().contains("\"ok\":true"));
            detail = fetchNode(client, upgradeProtocolId);
            Assert.assertEquals(500L,
                    ((Number) findProperty(detail, "maxConcurrentStreams").get("value")).longValue());

            // Guards: a read-only and an unknown attribute are refused,
            // as is a value that does not convert to the attribute type.
            request(client, "POST", MANAGER2 + "/api/config/attribute", token,
                    "{\"id\":\"" + upgradeProtocolId + "\",\"name\":\"maxHeaderSize\",\"value\":100}", 400);
            Assert.assertTrue(client.getResponseBody().contains("READ_ONLY"));
            request(client, "POST", MANAGER2 + "/api/config/attribute", token,
                    "{\"id\":\"" + upgradeProtocolId + "\",\"name\":\"bogus\",\"value\":1}", 404);
            Assert.assertTrue(client.getResponseBody().contains("ATTRIBUTE_NOT_FOUND"));
            request(client, "POST", MANAGER2 + "/api/config/attribute", token,
                    "{\"id\":\"" + upgradeProtocolId + "\",\"name\":\"maxConcurrentStreams\",\"value\":\"abc\"}", 400);
            Assert.assertTrue(client.getResponseBody().contains("INVALID_VALUE"));

            // The running connector is unchanged: the protocol (and its
            // settings) only take effect when the connector is
            // restarted, so it keeps serving plain HTTP.
            int status = httpGetPlain(port, "/");
            Assert.assertTrue("Expected plain HTTP, got " + status, status >= 200 && status < 600);

            // A second protocol with the same name is rejected.
            request(client, "POST", MANAGER2 + "/api/config/child", token,
                    "{\"parent\":\"" + connectorId + "\",\"type\":\"upgradeProtocol\"}", 409);
            Assert.assertTrue(client.getResponseBody().contains("DUPLICATE"));

            // The upgrade protocol is part of the stored server.xml,
            // including the changed setting (attributes that still have
            // their default value are not written).
            request(client, "GET", MANAGER2 + "/api/config/store/preview", null, null, 200);
            String xml = (String) parseObject(client.getResponseBody()).get("xml");
            Assert.assertTrue(xml.contains("<UpgradeProtocol"));
            Assert.assertTrue(xml.contains("org.apache.coyote.http2.Http2Protocol"));
            Assert.assertTrue(xml.contains("maxConcurrentStreams=\"500\""));

            // Remove ---------------------------------------------------

            request(client, "DELETE", MANAGER2 + "/api/config/child", token,
                    "{\"id\":\"" + upgradeProtocolId + "\",\"confirm\":\"h2\"}", 200);
            Assert.assertTrue(client.getResponseBody().contains("\"ok\":true"));
            connectorNode = findChildById(fetchTree(client), connectorId);
            Assert.assertNull("Expected the upgrade protocol to be gone",
                    findChild(connectorNode, "upgradeProtocol", "h2"));
        } finally {
            // Best effort cleanup (ignore failures, including assertion
            // errors: the failure of the test itself is reported).
            try {
                if (connectorId != null) {
                    request(client, "DELETE", MANAGER2 + "/api/config/child", token,
                            "{\"id\":\"" + connectorId + "\"," + "\"confirm\":\"HTTP/1.1 (port " + port + ")\"}",
                            200);
                }
            } catch (Throwable e) {
                // Best effort.
            }
            try {
                request(client, "DELETE", MANAGER2 + "/api/config/child", token,
                        "{\"id\":\"" + serviceId + "\",\"confirm\":\"CatalinaH2\"}", 200);
            } catch (Throwable e) {
                // Best effort.
            }
        }

        client.disconnect();
    }


    @Test
    public void testRealm() throws Exception {
        setup();

        SimpleHttpClient client = new TestClient();
        client.setPort(getPort());
        client.connect();
        String token = loginAndGetToken(client, "manager1");

        String hostId = null;
        String ctxId = null;
        try {
            // Display --------------------------------------------------

            Map<String, Object> tree = fetchTree(client);
            Map<String, Object> service = firstChildOfType(tree, "service");
            Map<String, Object> engine = firstChildOfType(service, "engine");
            String engineId = (String) engine.get("id");
            Map<String, Object> hostNode = findChild(engine, "host", "localhost");
            hostId = (String) hostNode.get("id");

            // The engine has its own realm (see setup()) and it is shown
            // as a child of the engine.
            Map<String, Object> engineRealm = findChild(engine, "realm", "MemoryRealm");
            Assert.assertNotNull("Expected the engine realm", engineRealm);
            String engineRealmId = (String) engineRealm.get("id");
            Assert.assertEquals(engineId + "/realm/0", engineRealmId);

            // A container without its own realm does not show one (it
            // inherits the parent realm, which is not its child).
            Assert.assertNull("Expected no realm on the host", findChild(hostNode, "realm", "MemoryRealm"));

            // The node detail of a realm exposes its modeler properties.
            request(client, "GET", MANAGER2 + "/api/config/node/" + engineRealmId, null, null, 200);
            Map<String, Object> realmDetail = parseObject(client.getResponseBody());
            Assert.assertEquals("realm", realmDetail.get("type"));
            Assert.assertEquals("MemoryRealm", realmDetail.get("name"));
            Assert.assertEquals(Boolean.FALSE, realmDetail.get("acceptsSubRealm"));
            Assert.assertNotNull(findProperty(realmDetail, "allRolesMode"));

            // Add ------------------------------------------------------

            // A (sub) realm can only be added to a container or to a
            // combined realm. A plain realm does not accept sub realms.
            request(client, "POST", MANAGER2 + "/api/config/child", token, "{\"parent\":\"" + engineRealmId +
                    "\",\"type\":\"realm\"," + "\"className\":\"org.apache.catalina.realm.MemoryRealm\"}", 400);
            Assert.assertTrue(client.getResponseBody().contains("BAD_PARENT"));
            // An unknown realm class is rejected.
            request(client, "POST", MANAGER2 + "/api/config/child", token,
                    "{\"parent\":\"" + hostId + "\",\"type\":\"realm\"," + "\"className\":\"org.example.NoSuchRealm\"}",
                    400);
            Assert.assertTrue(client.getResponseBody().contains("INVALID_CLASS"));

            // A realm can be added to a container ...
            request(client, "POST", MANAGER2 + "/api/config/child", token, "{\"parent\":\"" + hostId +
                    "\",\"type\":\"realm\"," + "\"className\":\"org.apache.catalina.realm.MemoryRealm\"}", 200);
            Assert.assertTrue(client.getResponseBody().contains("\"ok\":true"));
            // ...but a container holds at most one realm of its own.
            request(client, "POST", MANAGER2 + "/api/config/child", token, "{\"parent\":\"" + hostId +
                    "\",\"type\":\"realm\"," + "\"className\":\"org.apache.catalina.realm.MemoryRealm\"}", 409);
            Assert.assertTrue(client.getResponseBody().contains("DUPLICATE"));

            tree = fetchTree(client);
            hostNode = findChild(firstChildOfType(firstChildOfType(tree, "service"), "engine"), "host", "localhost");
            Map<String, Object> hostRealm = findChild(hostNode, "realm", "MemoryRealm");
            Assert.assertNotNull("Expected the host realm", hostRealm);
            String hostRealmId = (String) hostRealm.get("id");

            // Configure ------------------------------------------------

            // An attribute of the realm can be updated ...
            request(client, "POST", MANAGER2 + "/api/config/attribute", token,
                    "{\"id\":\"" + hostRealmId + "\",\"name\":\"allRolesMode\"," + "\"value\":\"authOnly\"}", 200);
            Assert.assertTrue(client.getResponseBody().contains("\"ok\":true"));
            request(client, "GET", MANAGER2 + "/api/config/node/" + hostRealmId, null, null, 200);
            realmDetail = parseObject(client.getResponseBody());
            Assert.assertEquals("authOnly", findProperty(realmDetail, "allRolesMode").get("value"));
            // ...and an invalid value is rejected by the realm itself.
            request(client, "POST", MANAGER2 + "/api/config/attribute", token,
                    "{\"id\":\"" + hostRealmId + "\",\"name\":\"allRolesMode\"," + "\"value\":\"bogus\"}", 400);
            Assert.assertTrue(client.getResponseBody().contains("SET_FAILED"));

            // Sub realms ------------------------------------------------

            // A context gets a combined realm (a LockOutRealm, which is a
            // CombinedRealm) with a sub realm.
            request(client, "POST", MANAGER2 + "/api/config/child", token,
                    "{\"parent\":\"" + hostId + "\",\"type\":\"context\"," + "\"path\":\"/realmapp\"}", 200);
            Assert.assertTrue(client.getResponseBody().contains("\"ok\":true"));
            tree = fetchTree(client);
            hostNode = findChild(firstChildOfType(firstChildOfType(tree, "service"), "engine"), "host", "localhost");
            ctxId = (String) findChild(hostNode, "context", "/realmapp").get("id");

            request(client, "POST", MANAGER2 + "/api/config/child", token, "{\"parent\":\"" + ctxId +
                    "\",\"type\":\"realm\"," + "\"className\":\"org.apache.catalina.realm.LockOutRealm\"}", 200);
            Assert.assertTrue(client.getResponseBody().contains("\"ok\":true"));
            String ctxRealmId = ctxId + "/realm/0";
            String subRealmId = ctxRealmId + "/realm/0";

            // The combined realm is shown with its sub realms.
            tree = fetchTree(client);
            Map<String, Object> ctxNode = findChildById(tree, ctxId);
            Assert.assertNotNull("Expected the context in the tree", ctxNode);
            Map<String, Object> ctxRealm = findChild(ctxNode, "realm", "LockOutRealm");
            Assert.assertNotNull("Expected the context realm", ctxRealm);
            Map<String, Object> subRealm = findChild(ctxRealm, "realm", "MemoryRealm");
            Assert.assertNull("Expected no sub realm, yet", subRealm);

            // The node detail of a combined realm reports that it accepts
            // sub realms.
            request(client, "GET", MANAGER2 + "/api/config/node/" + ctxRealmId, null, null, 200);
            realmDetail = parseObject(client.getResponseBody());
            Assert.assertEquals(Boolean.TRUE, realmDetail.get("acceptsSubRealm"));

            // A sub realm can be added ...
            request(client, "POST", MANAGER2 + "/api/config/child", token, "{\"parent\":\"" + ctxRealmId +
                    "\",\"type\":\"realm\"," + "\"className\":\"org.apache.catalina.realm.MemoryRealm\"}", 200);
            Assert.assertTrue(client.getResponseBody().contains("\"ok\":true"));
            // ...a second one, and both are shown.
            request(client, "POST", MANAGER2 + "/api/config/child", token, "{\"parent\":\"" + ctxRealmId +
                    "\",\"type\":\"realm\"," + "\"className\":\"org.apache.catalina.realm.MemoryRealm\"}", 200);
            Assert.assertTrue(client.getResponseBody().contains("\"ok\":true"));
            tree = fetchTree(client);
            ctxNode = findChildById(tree, ctxId);
            ctxRealm = findChild(ctxNode, "realm", "LockOutRealm");
            Assert.assertEquals("Expected two sub realms", 2, ((List<Object>) ctxRealm.get("children")).size());
            Assert.assertNotNull(findChild(ctxRealm, "realm", "MemoryRealm"));

            // The sub realm's node detail resolves.
            request(client, "GET", MANAGER2 + "/api/config/node/" + subRealmId, null, null, 200);
            realmDetail = parseObject(client.getResponseBody());
            Assert.assertEquals("realm", realmDetail.get("type"));
            Assert.assertEquals("MemoryRealm", realmDetail.get("name"));
            Assert.assertEquals(Boolean.FALSE, realmDetail.get("acceptsSubRealm"));

            // The combined realm and its sub realms are part of the
            // stored server.xml.
            request(client, "GET", MANAGER2 + "/api/config/store/preview", null, null, 200);
            String xml = (String) parseObject(client.getResponseBody()).get("xml");
            Assert.assertTrue(xml.contains("org.apache.catalina.realm.LockOutRealm"));
            Assert.assertTrue(xml.contains("org.apache.catalina.realm.MemoryRealm"));

            // Remove ---------------------------------------------------

            // A sub realm can be removed (and, with it, its effect).
            request(client, "DELETE", MANAGER2 + "/api/config/child", token,
                    "{\"id\":\"" + subRealmId + "\",\"confirm\":\"MemoryRealm\"}", 200);
            Assert.assertTrue(client.getResponseBody().contains("\"ok\":true"));
            tree = fetchTree(client);
            ctxNode = findChildById(tree, ctxId);
            ctxRealm = findChild(ctxNode, "realm", "LockOutRealm");
            Assert.assertEquals("Expected one sub realm left", 1, ((List<Object>) ctxRealm.get("children")).size());

            // A directly attached realm can be removed when the container
            // falls back to a parent realm ...
            request(client, "DELETE", MANAGER2 + "/api/config/child", token,
                    "{\"id\":\"" + ctxRealmId + "\",\"confirm\":\"LockOutRealm\"}", 200);
            Assert.assertTrue(client.getResponseBody().contains("\"ok\":true"));
            tree = fetchTree(client);
            Assert.assertNull("Expected no realm on the context",
                    findChild(findChildById(tree, ctxId), "realm", "LockOutRealm"));

            // ...but not when the container would be left without a realm.
            request(client, "DELETE", MANAGER2 + "/api/config/child", token,
                    "{\"id\":\"" + engineRealmId + "\",\"confirm\":\"MemoryRealm\"}", 400);
            Assert.assertTrue(client.getResponseBody().contains("LAST_REALM"));

            // The host realm (the host falls back to the engine realm)
            // can be removed.
            request(client, "DELETE", MANAGER2 + "/api/config/child", token,
                    "{\"id\":\"" + hostRealmId + "\",\"confirm\":\"MemoryRealm\"}", 200);
            Assert.assertTrue(client.getResponseBody().contains("\"ok\":true"));
        } finally {
            // Best effort cleanup (ignore failures, including assertion
            // errors: the failure of the test itself is reported).
            try {
                if (ctxId != null) {
                    request(client, "DELETE", MANAGER2 + "/api/config/child", token,
                            "{\"id\":\"" + ctxId + "\",\"confirm\":\"/realmapp\"}", 200);
                }
            } catch (Throwable e) {
                // Best effort.
            }
            try {
                if (hostId != null) {
                    request(client, "DELETE", MANAGER2 + "/api/config/child", token,
                            "{\"id\":\"" + hostId + "/realm/0\",\"confirm\":\"MemoryRealm\"}", 200);
                }
            } catch (Throwable e) {
                // Best effort.
            }
        }

        client.disconnect();
    }


    @Test
    public void testContextComponents() throws Exception {
        setup();

        SimpleHttpClient client = new TestClient();
        client.setPort(getPort());
        client.connect();
        String token = loginAndGetToken(client, "manager1");

        String hostId = null;
        String ctxId = null;
        try {
            // A context to operate on (the self context is guarded).
            Map<String, Object> tree = fetchTree(client);
            Map<String, Object> engine = firstChildOfType(firstChildOfType(tree, "service"), "engine");
            String engineId = (String) engine.get("id");
            Map<String, Object> hostNode = findChild(engine, "host", "localhost");
            hostId = (String) hostNode.get("id");

            request(client, "POST", MANAGER2 + "/api/config/child", token,
                    "{\"parent\":\"" + hostId + "\",\"type\":\"context\"," + "\"path\":\"/comptest\"}", 200);
            Assert.assertTrue(client.getResponseBody().contains("\"ok\":true"));

            // Display --------------------------------------------------

            // A running context always has a manager, a resource root,
            // a loader and a cookie processor (created at context start)
            // and they are all shown as children of the context.
            tree = fetchTree(client);
            Map<String, Object> engine2 = firstChildOfType(firstChildOfType(tree, "service"), "engine");
            Map<String, Object> hostNode2 = findChild(engine2, "host", "localhost");
            Map<String, Object> ctxEntry = findChild(hostNode2, "context", "/comptest");
            Assert.assertNotNull("Expected the new context in the tree", ctxEntry);
            ctxId = (String) ctxEntry.get("id");
            Map<String, Object> ctxNode = findChildById(tree, ctxId);
            Map<String, Object> manager = findChild(ctxNode, "manager", "StandardManager");
            Assert.assertNotNull("Expected the manager", manager);
            String managerId = (String) manager.get("id");
            Assert.assertEquals(ctxId + "/manager/0", managerId);
            Assert.assertEquals("STARTED", manager.get("state"));
            Map<String, Object> resources = findChild(ctxNode, "resources", "StandardRoot");
            Assert.assertNotNull("Expected the resources", resources);
            String resourcesId = (String) resources.get("id");
            Map<String, Object> loader = findChild(ctxNode, "loader", "WebappLoader");
            Assert.assertNotNull("Expected the loader", loader);
            String loaderId = (String) loader.get("id");
            Map<String, Object> cookie = findChild(ctxNode, "cookieProcessor", "Rfc6265CookieProcessor");
            Assert.assertNotNull("Expected the cookie processor", cookie);
            String cookieId = (String) cookie.get("id");

            // The manager shows its session id generator as a child.
            Map<String, Object> generator = findChild(manager, "sessionIdGenerator", "StandardSessionIdGenerator");
            Assert.assertNotNull("Expected the session id generator", generator);
            String generatorId = (String) generator.get("id");
            Assert.assertEquals(managerId + "/sessionIdGenerator/0", generatorId);

            // The node detail of each component exposes its properties:
            // the manager and the resources through their modeler
            // descriptor, the others through the explicit attribute list.
            request(client, "GET", MANAGER2 + "/api/config/node/" + managerId, null, null, 200);
            Map<String, Object> detail = parseObject(client.getResponseBody());
            Assert.assertEquals("manager", detail.get("type"));
            Assert.assertEquals(Boolean.TRUE, findProperty(detail, "maxActive").get("writable"));
            request(client, "GET", MANAGER2 + "/api/config/node/" + resourcesId, null, null, 200);
            detail = parseObject(client.getResponseBody());
            Assert.assertEquals(Boolean.TRUE, findProperty(detail, "allowLinking").get("writable"));
            request(client, "GET", MANAGER2 + "/api/config/node/" + loaderId, null, null, 200);
            detail = parseObject(client.getResponseBody());
            Assert.assertEquals(Boolean.TRUE, findProperty(detail, "delegate").get("writable"));
            request(client, "GET", MANAGER2 + "/api/config/node/" + cookieId, null, null, 200);
            detail = parseObject(client.getResponseBody());
            Assert.assertEquals(Boolean.TRUE, findProperty(detail, "sameSiteCookies").get("writable"));
            request(client, "GET", MANAGER2 + "/api/config/node/" + generatorId, null, null, 200);
            detail = parseObject(client.getResponseBody());
            Assert.assertEquals(Boolean.TRUE, findProperty(detail, "sessionIdLength").get("writable"));

            // Configure ------------------------------------------------

            // The attributes of each component can be updated and the
            // new value is reported by the node detail.
            request(client, "POST", MANAGER2 + "/api/config/attribute", token,
                    "{\"id\":\"" + managerId + "\",\"name\":\"maxActive\",\"value\":42}", 200);
            request(client, "GET", MANAGER2 + "/api/config/node/" + managerId, null, null, 200);
            detail = parseObject(client.getResponseBody());
            Assert.assertEquals(42, ((Number) findProperty(detail, "maxActive").get("value")).intValue());
            request(client, "POST", MANAGER2 + "/api/config/attribute", token,
                    "{\"id\":\"" + loaderId + "\",\"name\":\"delegate\",\"value\":true}", 200);
            request(client, "GET", MANAGER2 + "/api/config/node/" + loaderId, null, null, 200);
            detail = parseObject(client.getResponseBody());
            Assert.assertEquals(Boolean.TRUE, findProperty(detail, "delegate").get("value"));
            request(client, "POST", MANAGER2 + "/api/config/attribute", token,
                    "{\"id\":\"" + cookieId + "\",\"name\":\"sameSiteCookies\"," + "\"value\":\"LAX\"}", 200);
            request(client, "GET", MANAGER2 + "/api/config/node/" + cookieId, null, null, 200);
            detail = parseObject(client.getResponseBody());
            Assert.assertEquals("LAX", findProperty(detail, "sameSiteCookies").get("value"));
            request(client, "POST", MANAGER2 + "/api/config/attribute", token,
                    "{\"id\":\"" + generatorId + "\",\"name\":\"sessionIdLength\",\"value\":40}", 200);
            request(client, "GET", MANAGER2 + "/api/config/node/" + generatorId, null, null, 200);
            detail = parseObject(client.getResponseBody());
            Assert.assertEquals(40, ((Number) findProperty(detail, "sessionIdLength").get("value")).intValue());

            // An invalid value is rejected by the component itself.
            request(client, "POST", MANAGER2 + "/api/config/attribute", token,
                    "{\"id\":\"" + cookieId + "\",\"name\":\"sameSiteCookies\"," + "\"value\":\"bogus\"}", 400);
            Assert.assertTrue(client.getResponseBody().contains("SET_FAILED"));

            // Add (replace) ---------------------------------------------

            // A context holds exactly one of each of these components, so
            // adding one replaces the current instance: the attribute
            // value set on the old instance is gone afterwards.
            request(client, "POST", MANAGER2 + "/api/config/child", token, "{\"parent\":\"" + ctxId +
                    "\",\"type\":\"manager\"," + "\"className\":\"org.apache.catalina.session.StandardManager\"}", 200);
            request(client, "GET", MANAGER2 + "/api/config/node/" + managerId, null, null, 200);
            detail = parseObject(client.getResponseBody());
            Assert.assertEquals("Expected the default maxActive of the new manager", 0,
                    ((Number) findProperty(detail, "maxActive").get("value")).intValue());
            Assert.assertEquals("STARTED", detail.get("state"));
            // The new manager has its own (default) session id generator.
            tree = fetchTree(client);
            Map<String, Object> newManager = findChild(findChildById(tree, ctxId), "manager", "StandardManager");
            Assert.assertNotNull(findChild(newManager, "sessionIdGenerator", "StandardSessionIdGenerator"));

            request(client, "POST", MANAGER2 + "/api/config/child", token,
                    "{\"parent\":\"" + ctxId + "\",\"type\":\"cookieProcessor\"," +
                            "\"className\":\"org.apache.tomcat.util.http.Rfc6265CookieProcessor\"}",
                    200);
            request(client, "GET", MANAGER2 + "/api/config/node/" + cookieId, null, null, 200);
            detail = parseObject(client.getResponseBody());
            Assert.assertEquals("Expected the default sameSiteCookies of the new processor", "UNSET",
                    findProperty(detail, "sameSiteCookies").get("value"));

            request(client, "POST", MANAGER2 + "/api/config/child", token,
                    "{\"parent\":\"" + managerId + "\",\"type\":\"sessionIdGenerator\"," +
                            "\"className\":\"org.apache.catalina.util.StandardSessionIdGenerator\"}",
                    200);
            request(client, "GET", MANAGER2 + "/api/config/node/" + generatorId, null, null, 200);
            detail = parseObject(client.getResponseBody());
            Assert.assertEquals("Expected the default sessionIdLength of the new generator", 16,
                    ((Number) findProperty(detail, "sessionIdLength").get("value")).intValue());

            // A class that does not implement the expected interface (or
            // that does not exist) is rejected.
            request(client, "POST", MANAGER2 + "/api/config/child", token, "{\"parent\":\"" + ctxId +
                    "\",\"type\":\"manager\"," + "\"className\":\"org.apache.catalina.realm.MemoryRealm\"}", 400);
            Assert.assertTrue(client.getResponseBody().contains("INVALID_CLASS"));
            request(client, "POST", MANAGER2 + "/api/config/child", token, "{\"parent\":\"" + ctxId +
                    "\",\"type\":\"loader\"," + "\"className\":\"org.example.NoSuchLoader\"}", 400);
            Assert.assertTrue(client.getResponseBody().contains("INVALID_CLASS"));
            request(client, "POST", MANAGER2 + "/api/config/child", token, "{\"parent\":\"" + managerId +
                    "\",\"type\":\"sessionIdGenerator\"," + "\"className\":\"org.example.NoSuchGenerator\"}", 400);
            Assert.assertTrue(client.getResponseBody().contains("INVALID_CLASS"));

            // The parent must be a context (a manager for the session id
            // generator).
            request(client, "POST", MANAGER2 + "/api/config/child", token, "{\"parent\":\"" + engineId +
                    "\",\"type\":\"manager\"," + "\"className\":\"org.apache.catalina.session.StandardManager\"}", 400);
            Assert.assertTrue(client.getResponseBody().contains("BAD_PARENT"));
            request(client, "POST", MANAGER2 + "/api/config/child", token,
                    "{\"parent\":\"" + ctxId + "\",\"type\":\"sessionIdGenerator\"," +
                            "\"className\":\"org.apache.catalina.util.StandardSessionIdGenerator\"}",
                    400);
            Assert.assertTrue(client.getResponseBody().contains("BAD_PARENT"));

            // Replacing the manager (or the loader) of this web
            // application's own context is refused: it would destroy the
            // admin session (or the classes of the running application).
            // (The self context id is derived directly: the context path
            // "/" encodes as "+", so "/manager2" becomes "+manager2".)
            String selfCtxId = hostId + "/context/" + MANAGER2.replace("/", "+");
            request(client, "POST", MANAGER2 + "/api/config/child", token, "{\"parent\":\"" + selfCtxId +
                    "\",\"type\":\"manager\"," + "\"className\":\"org.apache.catalina.session.StandardManager\"}", 403);
            Assert.assertTrue(client.getResponseBody().contains("SELF_COMPONENT"));
            request(client, "POST", MANAGER2 + "/api/config/child", token, "{\"parent\":\"" + selfCtxId +
                    "\",\"type\":\"loader\"," + "\"className\":\"org.apache.catalina.loader.WebappLoader\"}", 403);
            Assert.assertTrue(client.getResponseBody().contains("SELF_COMPONENT"));

            // The resources of a running context cannot be replaced...
            request(client, "POST", MANAGER2 + "/api/config/child", token, "{\"parent\":\"" + ctxId +
                    "\",\"type\":\"resources\"," + "\"className\":\"org.apache.catalina.webresources.StandardRoot\"}",
                    400);
            Assert.assertTrue(client.getResponseBody().contains("CONTEXT_RUNNING"));

            // ...but they can on a stopped context.
            Context context = (Context) getTomcatInstance().getHost().findChild("/comptest");
            context.stop();
            request(client, "POST", MANAGER2 + "/api/config/child", token, "{\"parent\":\"" + ctxId +
                    "\",\"type\":\"resources\"," + "\"className\":\"org.apache.catalina.webresources.StandardRoot\"}",
                    200);
            context.start();
            request(client, "GET", MANAGER2 + "/api/config/node/" + resourcesId, null, null, 200);
            detail = parseObject(client.getResponseBody());
            Assert.assertEquals("StandardRoot", detail.get("name"));
            Assert.assertEquals("STARTED", detail.get("state"));

            // Remove ---------------------------------------------------

            // These components are required by the context (or the
            // manager) and cannot be removed - only replaced.
            for (String id : List.of(managerId, resourcesId, loaderId, cookieId, generatorId)) {
                request(client, "DELETE", MANAGER2 + "/api/config/child", token,
                        "{\"id\":\"" + id + "\",\"confirm\":\"x\"}", 400);
                Assert.assertTrue(client.getResponseBody().contains("REQUIRED_COMPONENT"));
            }
        } finally {
            // Best effort cleanup (ignore failures, including assertion
            // errors: the failure of the test itself is reported).
            try {
                if (ctxId != null) {
                    request(client, "DELETE", MANAGER2 + "/api/config/child", token,
                            "{\"id\":\"" + ctxId + "\",\"confirm\":\"/comptest\"}", 200);
                }
            } catch (Throwable e) {
                // Best effort.
            }
        }

        client.disconnect();
    }


    @Test
    public void testNamingResources() throws Exception {
        setup(true);

        SimpleHttpClient client = new TestClient();
        client.setPort(getPort());
        client.connect();
        String token = loginAndGetToken(client, "manager1");

        String serverNrId = "server/namingResources/0";
        File usersXml = new File(getTemporaryDirectory(), "conf/tomcat-users.xml");
        Assert.assertTrue("Expected the test user file", usersXml.isFile());

        String ctxId = null;
        try {
            // --------------------------- Server level ----------------------

            // The server exposes a single, global naming resources node.
            Map<String, Object> tree = fetchTree(client);
            Map<String, Object> serverNr = findChild(tree, "namingResources", "NamingResourcesImpl");
            Assert.assertNotNull("Expected the global naming resources", serverNr);
            Assert.assertEquals(serverNrId, serverNr.get("id"));
            request(client, "GET", MANAGER2 + "/api/config/node/" + serverNrId, null, null, 200);
            Map<String, Object> nrDetail = parseObject(client.getResponseBody());
            Assert.assertEquals("namingResources", nrDetail.get("type"));
            Assert.assertEquals(Boolean.TRUE, nrDetail.get("global"));

            // Add a global UserDatabase with a first party factory.
            request(client, "POST", MANAGER2 + "/api/config/child", token,
                    "{\"parent\":\"" + serverNrId + "\",\"type\":\"resource\"," +
                            "\"name\":\"UserDatabaseTest\",\"jndiType\":\"org.apache.catalina.UserDatabase\"," +
                            "\"factory\":\"org.apache.catalina.users.MemoryUserDatabaseFactory\"," +
                            "\"params\":{\"pathname\":\"" + usersXml.getAbsolutePath() + "\",\"readonly\":\"true\"}}",
                    200);
            Assert.assertTrue(client.getResponseBody().contains("\"ok\":true"));

            // The resource is bound in the live global naming context and
            // loaded the configured users.
            Object lookedUp = getTomcatInstance().getServer().getGlobalNamingContext().lookup("UserDatabaseTest");
            Assert.assertTrue("Expected a UserDatabase to be bound", lookedUp instanceof UserDatabase);
            Assert.assertNotNull(((UserDatabase) lookedUp).findUser("manager1"));

            // The tree shows the resource and its node detail exposes the
            // closed factory options (with the set values) as parameters.
            tree = fetchTree(client);
            Map<String, Object> dbRes = findChild(findChild(tree, "namingResources", "NamingResourcesImpl"), "resource",
                    "UserDatabaseTest");
            Assert.assertNotNull("Expected the resource in the tree", dbRes);
            Assert.assertEquals(serverNrId + "/resource/UserDatabaseTest", dbRes.get("id"));
            String dbId = (String) dbRes.get("id");
            request(client, "GET", MANAGER2 + "/api/config/node/" + dbId, null, null, 200);
            Map<String, Object> dbDetail = parseObject(client.getResponseBody());
            Assert.assertEquals("resource", dbDetail.get("type"));
            Assert.assertEquals(Boolean.TRUE, findProperty(dbDetail, "name").get("writable"));
            Assert.assertEquals(Boolean.TRUE, findProperty(dbDetail, "pathname").get("param"));
            Assert.assertEquals(usersXml.getAbsolutePath(), findProperty(dbDetail, "pathname").get("value"));
            Assert.assertEquals("true", findProperty(dbDetail, "readonly").get("value"));
            // A closed factory option that is not set is still listed.
            Assert.assertNotNull(findProperty(dbDetail, "watchSource"));

            // Add the other global entry types.
            request(client, "POST", MANAGER2 + "/api/config/child", token,
                    "{\"parent\":\"" + serverNrId + "\",\"type\":\"environment\"," +
                            "\"name\":\"genv\",\"jndiType\":\"java.lang.Integer\",\"value\":\"42\"}",
                    200);
            request(client, "POST", MANAGER2 + "/api/config/child", token,
                    "{\"parent\":\"" + serverNrId + "\",\"type\":\"ejb\"," +
                            "\"name\":\"gejb\",\"jndiType\":\"org.example.Home\"," + "\"home\":\"org.example.Home\"}",
                    200);
            request(client, "POST", MANAGER2 + "/api/config/child", token,
                    "{\"parent\":\"" + serverNrId + "\",\"type\":\"localEjb\"," +
                            "\"name\":\"glejb\",\"jndiType\":\"org.example.Local\"," +
                            "\"local\":\"org.example.Local\"}",
                    200);
            request(client, "POST", MANAGER2 + "/api/config/child", token,
                    "{\"parent\":\"" + serverNrId + "\",\"type\":\"serviceRef\"," +
                            "\"name\":\"gservice\",\"jndiType\":\"org.example.Svc\"," +
                            "\"interface\":\"org.example.Svc\",\"displayname\":\"test\"}",
                    200);
            tree = fetchTree(client);
            Map<String, Object> serverNr2 = findChild(tree, "namingResources", "NamingResourcesImpl");
            Assert.assertNotNull(findChild(serverNr2, "environment", "genv"));
            Assert.assertNotNull(findChild(serverNr2, "ejb", "gejb"));
            Assert.assertNotNull(findChild(serverNr2, "localEjb", "glejb"));
            Assert.assertNotNull(findChild(serverNr2, "serviceRef", "gservice"));

            // The generic string parameters of every JNDI entry type (the
            // ResourceBase property map) are shown in the entry detail and
            // can be added, edited and removed there.
            String genvId = serverNrId + "/environment/genv";
            request(client, "POST", MANAGER2 + "/api/config/attribute", token,
                    "{\"id\":\"" + genvId + "\",\"name\":\"envKey\",\"value\":\"envValue\"}", 200);
            request(client, "GET", MANAGER2 + "/api/config/node/" + genvId, null, null, 200);
            Map<String, Object> genvDetail = parseObject(client.getResponseBody());
            Assert.assertEquals("envValue", findProperty(genvDetail, "envKey").get("value"));
            Assert.assertEquals(Boolean.TRUE, findProperty(genvDetail, "envKey").get("param"));
            // An ejb and a web service reference expose their parameters too.
            String gejbId = serverNrId + "/ejb/gejb";
            request(client, "POST", MANAGER2 + "/api/config/attribute", token,
                    "{\"id\":\"" + gejbId + "\",\"name\":\"ejbKey\",\"value\":\"ejbValue\"}", 200);
            request(client, "GET", MANAGER2 + "/api/config/node/" + gejbId, null, null, 200);
            Assert.assertEquals("ejbValue", findProperty(parseObject(client.getResponseBody()), "ejbKey").get("value"));
            String gsvcId = serverNrId + "/serviceRef/gservice";
            request(client, "POST", MANAGER2 + "/api/config/attribute", token,
                    "{\"id\":\"" + gsvcId + "\",\"name\":\"svcKey\",\"value\":\"svcValue\"}", 200);
            request(client, "GET", MANAGER2 + "/api/config/node/" + gsvcId, null, null, 200);
            Assert.assertEquals("svcValue", findProperty(parseObject(client.getResponseBody()), "svcKey").get("value"));
            // A parameter can be edited and then removed (by clearing it).
            request(client, "POST", MANAGER2 + "/api/config/attribute", token,
                    "{\"id\":\"" + genvId + "\",\"name\":\"envKey\",\"value\":\"envValue2\"}", 200);
            request(client, "GET", MANAGER2 + "/api/config/node/" + genvId, null, null, 200);
            Assert.assertEquals("envValue2",
                    findProperty(parseObject(client.getResponseBody()), "envKey").get("value"));
            request(client, "POST", MANAGER2 + "/api/config/attribute", token,
                    "{\"id\":\"" + genvId + "\",\"name\":\"envKey\",\"value\":\"\"}", 200);
            request(client, "GET", MANAGER2 + "/api/config/node/" + genvId, null, null, 200);
            Assert.assertNull(findProperty(parseObject(client.getResponseBody()), "envKey"));

            // Guards (server level).
            request(client, "POST", MANAGER2 + "/api/config/child", token, "{\"parent\":\"" + serverNrId +
                    "\",\"type\":\"resource\"," + "\"name\":\"UserDatabaseTest\",\"jndiType\":\"java.lang.String\"}",
                    409);
            Assert.assertTrue(client.getResponseBody().contains("DUPLICATE"));
            request(client, "POST", MANAGER2 + "/api/config/child", token,
                    "{\"parent\":\"" + serverNrId + "\",\"type\":\"resource\",\"name\":\"noType\"}", 400);
            Assert.assertTrue(client.getResponseBody().contains("MISSING_FIELD"));
            // Resource links are not part of the global naming environment.
            request(client, "POST", MANAGER2 + "/api/config/child", token,
                    "{\"parent\":\"" + serverNrId + "\",\"type\":\"resourceLink\"," +
                            "\"name\":\"glink\",\"jndiType\":\"java.lang.String\",\"global\":\"x\"}",
                    400);
            Assert.assertTrue(client.getResponseBody().contains("BAD_PARENT"));
            request(client, "POST", MANAGER2 + "/api/config/child", token,
                    "{\"parent\":\"" + serverNrId + "\",\"type\":\"resource\"," +
                            "\"name\":\"badfactory\",\"jndiType\":\"javax.sql.DataSource\"," +
                            "\"factory\":\"org.example.NoSuchFactory\"}",
                    400);
            Assert.assertTrue(client.getResponseBody().contains("INVALID_CLASS"));

            // A parameter update re-registers the entry, so the live JNDI
            // environment reflects the change (and stays bound).
            request(client, "POST", MANAGER2 + "/api/config/attribute", token,
                    "{\"id\":\"" + dbId + "\",\"name\":\"readonly\",\"value\":\"false\"}", 200);
            request(client, "GET", MANAGER2 + "/api/config/node/" + dbId, null, null, 200);
            Assert.assertEquals("false", findProperty(parseObject(client.getResponseBody()), "readonly").get("value"));
            Assert.assertNotNull(getTomcatInstance().getServer().getGlobalNamingContext().lookup("UserDatabaseTest"));

            // Renaming requires a type-to-confirm and rebinds the resource.
            request(client, "POST", MANAGER2 + "/api/config/attribute", token,
                    "{\"id\":\"" + dbId + "\",\"name\":\"name\"," + "\"value\":\"UserDatabaseRenamed\"}", 400);
            Assert.assertTrue(client.getResponseBody().contains("CONFIRM_REQUIRED"));
            request(client, "POST", MANAGER2 + "/api/config/attribute", token, "{\"id\":\"" + dbId +
                    "\",\"name\":\"name\"," + "\"value\":\"UserDatabaseRenamed\",\"confirm\":\"UserDatabaseTest\"}",
                    200);
            Assert.assertNotNull(
                    getTomcatInstance().getServer().getGlobalNamingContext().lookup("UserDatabaseRenamed"));
            try {
                getTomcatInstance().getServer().getGlobalNamingContext().lookup("UserDatabaseTest");
                Assert.fail("Expected the old name to be unbound");
            } catch (Exception e) {
                Assert.assertTrue(e instanceof javax.naming.NameNotFoundException);
            }
            String dbId2 = serverNrId + "/resource/UserDatabaseRenamed";

            // A free form parameter can be added and cleared.
            request(client, "POST", MANAGER2 + "/api/config/attribute", token,
                    "{\"id\":\"" + dbId2 + "\",\"name\":\"extraKey\",\"value\":\"hello\"}", 200);
            request(client, "GET", MANAGER2 + "/api/config/node/" + dbId2, null, null, 200);
            Assert.assertEquals("hello", findProperty(parseObject(client.getResponseBody()), "extraKey").get("value"));
            request(client, "POST", MANAGER2 + "/api/config/attribute", token,
                    "{\"id\":\"" + dbId2 + "\",\"name\":\"extraKey\",\"value\":\"\"}", 200);
            request(client, "GET", MANAGER2 + "/api/config/node/" + dbId2, null, null, 200);
            Assert.assertNull(findProperty(parseObject(client.getResponseBody()), "extraKey"));

            // The global naming resources node is required (cannot be removed).
            request(client, "DELETE", MANAGER2 + "/api/config/child", token,
                    "{\"id\":\"" + serverNrId + "\",\"confirm\":\"x\"}", 400);
            Assert.assertTrue(client.getResponseBody().contains("REQUIRED_COMPONENT"));

            // The global naming resources round trip to server.xml,
            // including the web service reference (ServiceRef).
            File storeBase = new File(getTemporaryDirectory(), "store-naming-base");
            File storeConf = new File(storeBase, "conf");
            Assert.assertTrue(storeConf.mkdirs());
            addDeleteOnTearDown(storeBase);
            setStoreBase(storeBase);
            request(client, "GET", MANAGER2 + "/api/config/store/preview", null, null, 200);
            String xml = (String) parseObject(client.getResponseBody()).get("xml");
            Assert.assertTrue(xml.contains("<GlobalNamingResources"));
            Assert.assertTrue(xml.contains("UserDatabaseRenamed"));
            Assert.assertTrue(xml.contains("<ServiceRef"));
            Assert.assertTrue(xml.contains("<EJB"));
            Assert.assertTrue(xml.contains("<Environment"));
            // The generic parameters of the entries round trip too.
            Assert.assertTrue(xml.contains("ejbKey=\"ejbValue\""));
            Assert.assertTrue(xml.contains("svcKey=\"svcValue\""));

            // --------------------------- Context level ---------------------

            Map<String, Object> engine = firstChildOfType(firstChildOfType(tree, "service"), "engine");
            String hostId = (String) findChild(engine, "host", "localhost").get("id");
            request(client, "POST", MANAGER2 + "/api/config/child", token,
                    "{\"parent\":\"" + hostId + "\",\"type\":\"context\",\"path\":\"/comptest\"}", 200);
            tree = fetchTree(client);
            Map<String, Object> engine2 = firstChildOfType(firstChildOfType(tree, "service"), "engine");
            Map<String, Object> ctxEntry = findChild(findChild(engine2, "host", "localhost"), "context", "/comptest");
            Assert.assertNotNull("Expected the new context", ctxEntry);
            ctxId = (String) ctxEntry.get("id");
            String ctxNrId = ctxId + "/namingResources/0";

            // A context naming resources node is not global.
            request(client, "GET", MANAGER2 + "/api/config/node/" + ctxNrId, null, null, 200);
            Assert.assertEquals(Boolean.FALSE, parseObject(client.getResponseBody()).get("global"));

            // A context may hold resource links (unlike the server).
            request(client, "POST", MANAGER2 + "/api/config/child", token,
                    "{\"parent\":\"" + ctxNrId + "\",\"type\":\"resourceLink\"," +
                            "\"name\":\"link/db\",\"jndiType\":\"org.apache.catalina.UserDatabase\"," +
                            "\"global\":\"UserDatabaseRenamed\"}",
                    200);
            // A context resource that resolves to the default (first party)
            // data source factory from its type.
            request(client, "POST", MANAGER2 + "/api/config/child", token,
                    "{\"parent\":\"" + ctxNrId + "\",\"type\":\"resource\"," +
                            "\"name\":\"jdbc/ctxds\",\"jndiType\":\"javax.sql.DataSource\"," +
                            "\"params\":{\"url\":\"jdbc:h2:mem:ctx\",\"username\":\"sa\"," + "\"maxTotal\":\"10\"}}",
                    200);

            StandardContext context = (StandardContext) getTomcatInstance().getHost().findChild("/comptest");
            javax.naming.Context envCtx = context.getNamingContextListener().getEnvContext();
            // The link is configured to point to the global resource.
            request(client, "GET", MANAGER2 + "/api/config/node/" + ctxNrId + "/resourceLink/link+db", null, null, 200);
            Assert.assertEquals("UserDatabaseRenamed",
                    findProperty(parseObject(client.getResponseBody()), "global").get("value"));
            // The resource is bound in the live context environment.
            Object ds = envCtx.lookup("jdbc/ctxds");
            Assert.assertTrue("Expected a DataSource to be bound", ds instanceof javax.sql.DataSource);

            tree = fetchTree(client);
            Map<String, Object> ctxNr = findChild(findChildById(tree, ctxId), "namingResources", "NamingResourcesImpl");
            Assert.assertNotNull(findChild(ctxNr, "resourceLink", "link/db"));
            Assert.assertNotNull(findChild(ctxNr, "resource", "jdbc/ctxds"));
            String dsId = ctxNrId + "/resource/jdbc+ctxds";
            request(client, "GET", MANAGER2 + "/api/config/node/" + dsId, null, null, 200);
            Map<String, Object> dsDetail = parseObject(client.getResponseBody());
            Assert.assertEquals("jdbc:h2:mem:ctx", findProperty(dsDetail, "url").get("value"));
            Assert.assertEquals("10", findProperty(dsDetail, "maxTotal").get("value"));

            // A parameter update re-registers the entry (the live re-lookup
            // still resolves).
            request(client, "POST", MANAGER2 + "/api/config/attribute", token,
                    "{\"id\":\"" + dsId + "\",\"name\":\"maxTotal\",\"value\":\"5\"}", 200);
            request(client, "GET", MANAGER2 + "/api/config/node/" + dsId, null, null, 200);
            Assert.assertEquals("5", findProperty(parseObject(client.getResponseBody()), "maxTotal").get("value"));
            Assert.assertTrue(envCtx.lookup("jdbc/ctxds") instanceof javax.sql.DataSource);

            // Context guard: duplicate name.
            request(client, "POST", MANAGER2 + "/api/config/child", token, "{\"parent\":\"" + ctxNrId +
                    "\",\"type\":\"resource\"," + "\"name\":\"jdbc/ctxds\",\"jndiType\":\"java.lang.String\"}", 409);
            Assert.assertTrue(client.getResponseBody().contains("DUPLICATE"));

            // Removing an entry (type-to-confirm) unbinds it.
            request(client, "DELETE", MANAGER2 + "/api/config/child", token,
                    "{\"id\":\"" + dsId + "\",\"confirm\":\"jdbc/ctxds\"}", 200);
            try {
                envCtx.lookup("jdbc/ctxds");
                Assert.fail("Expected the resource to be unbound");
            } catch (Exception e) {
                Assert.assertTrue(e instanceof javax.naming.NameNotFoundException);
            }
        } finally {
            // Best effort cleanup (ignore failures: the test failure itself
            // is reported).
            try {
                if (ctxId != null) {
                    request(client, "DELETE", MANAGER2 + "/api/config/child", token,
                            "{\"id\":\"" + ctxId + "\",\"confirm\":\"/comptest\"}", 200);
                }
            } catch (Throwable e) {
                // Best effort.
            }
            String[][] globals = { { "resource", "UserDatabaseRenamed" }, { "environment", "genv" }, { "ejb", "gejb" },
                    { "localEjb", "glejb" }, { "serviceRef", "gservice" } };
            for (String[] g : globals) {
                try {
                    request(client, "DELETE", MANAGER2 + "/api/config/child", token,
                            "{\"id\":\"" + serverNrId + "/" + g[0] + "/" + g[1] + "\",\"confirm\":\"" + g[1] + "\"}",
                            200);
                } catch (Throwable e) {
                    // Best effort.
                }
            }
        }

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


    @Test
    public void testStorePreviewDoesNotWrite() throws Exception {
        setup();

        File storeBase = new File(getTemporaryDirectory(), "store-preview-base");
        File conf = new File(storeBase, "conf");
        Assert.assertTrue(conf.mkdirs());
        addDeleteOnTearDown(storeBase);
        setStoreBase(storeBase);

        int before = conf.list().length;

        SimpleHttpClient client = new TestClient();
        client.setPort(getPort());
        client.connect();
        login(client, "manager1");

        request(client, "GET", MANAGER2 + "/api/config/store/preview", null, null, 200);
        Map<String, Object> res = parseObject(client.getResponseBody());
        String xml = (String) res.get("xml");
        Assert.assertNotNull(xml);
        Assert.assertTrue(xml.contains("<Server"));
        // The live state (the default host) is part of the preview.
        Assert.assertTrue(xml.contains("localhost"));

        // The preview reports the external context files that would be
        // rewritten (the manager runs from its own context.xml) and that the
        // save restarts the manager.
        @SuppressWarnings("unchecked")
        List<Object> files = (List<Object>) res.get("files");
        Assert.assertNotNull(files);
        Assert.assertTrue(files.stream().anyMatch(f -> String.valueOf(f).endsWith("context.xml")));
        Assert.assertEquals(Boolean.TRUE, res.get("restartsManager"));

        // The preview must not have written anything to the conf directory.
        Assert.assertEquals(before, conf.list().length);

        client.disconnect();
    }


    @Test
    public void testStoreWritesFileAndBackup() throws Exception {
        setup();

        File storeBase = new File(getTemporaryDirectory(), "store-base");
        File conf = new File(storeBase, "conf");
        Assert.assertTrue(conf.mkdirs());
        addDeleteOnTearDown(storeBase);
        setStoreBase(storeBase);
        // A pre-existing server.xml so that a backup can be created.
        try (PrintWriter pw = new PrintWriter(new File(conf, "server.xml"), StandardCharsets.UTF_8)) {
            pw.println("<Server port=\"8005\" shutdown=\"SHUTDOWN\"></Server>");
        }

        SimpleHttpClient client = new TestClient();
        client.setPort(getPort());
        client.connect();
        String token = loginAndGetToken(client, "manager1");

        String ctxId = selfContextId(fetchTree(client));
        String hostId = hostIdOf(ctxId);

        // Add a recognizable component that must end up in the stored file.
        request(client, "POST", MANAGER2 + "/api/config/child", token,
                "{\"parent\":\"" + hostId + "\",\"type\":\"alias\",\"alias\":\"store-test-alias\"}", 200);

        try {
            request(client, "POST", MANAGER2 + "/api/config/store", token, "{}", 200);
            Map<String, Object> res = parseObject(client.getResponseBody());
            Assert.assertEquals(Boolean.TRUE, res.get("ok"));
            Assert.assertEquals("conf/server.xml", res.get("file"));
            String backup = (String) res.get("backup");
            Assert.assertNotNull("Expected a backup file name", backup);

            // The live state (including the added alias) was written.
            String written = readFile(new File(conf, "server.xml"));
            Assert.assertTrue(written.contains("store-test-alias"));
            Assert.assertTrue(written.contains("<Server"));

            // A timestamped backup of the previous file was kept.
            Assert.assertTrue(new File(conf, backup).isFile());

            // Contexts are kept in their current location: the manager runs
            // from its own context.xml, so the store rewrote that (external)
            // file as well - creating a timestamped backup next to it -
            // which is why the save resets the admin session.
            File selfCtxDir = new File(getBuildDirectory(), "webapps/manager2/META-INF");
            String[] selfCtxFiles = selfCtxDir.list();
            boolean selfCtxBackup = false;
            if (selfCtxFiles != null) {
                for (String name : selfCtxFiles) {
                    if (name.startsWith("context.xml.")) {
                        selfCtxBackup = true;
                        break;
                    }
                }
            }
            Assert.assertTrue(
                    "Expected the manager context file to be rewritten " + "(a timestamped backup was created)",
                    selfCtxBackup);
        } finally {
            // The store rewrote the manager's own (watched) context file,
            // which restarts the context and resets the session, so clean up
            // with a fresh login.
            try {
                String token2 = loginAndGetToken(client, "manager1");
                request(client, "DELETE", MANAGER2 + "/api/config/child", token2,
                        "{\"id\":\"" + hostId + "/alias/store-test-alias\"}", 200);
            } catch (Throwable e) {
                // Best effort: the context may still be restarting.
            }
        }

        client.disconnect();
    }


    // -----------------------------------------------------------------------


    private void setStoreBase(File storeBase) {
        System.setProperty("manager2.store.base", storeBase.getAbsolutePath());
        storeBaseProp = storeBase.getAbsolutePath();
    }


    /**
     * Best effort cleanup for the add/remove test if a removal failed midway: remove any components that are still
     * present so the shared instance and subsequent tests are not affected.
     */
    private void cleanup(SimpleHttpClient client, String token, String serviceId, String hostId, String contextId,
            String wrapperId, String valveId, String connectorId, String executorId, String aliasId) throws Exception {
        for (String id : new String[] { connectorId, executorId, wrapperId, valveId, aliasId, contextId, hostId,
                serviceId }) {
            if (id == null) {
                continue;
            }
            try {
                request(client, "DELETE", MANAGER2 + "/api/config/child", token,
                        "{\"id\":\"" + id + "\",\"confirm\":\"confirm\"}", 200);
            } catch (AssertionError e) {
                // Already removed (or never created): ignore.
            }
        }
    }


    private static int freePort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }


    /**
     * Issue an HTTPS GET against the given port with a trust-all trust manager (the test certificate is self signed).
     * Returns the HTTP status code; any status proves that the TLS handshake succeeded and the connector served the
     * request.
     */
    private static int httpsGet(int port, String path) throws Exception {
        TrustManager[] trustAll = new TrustManager[] { new X509TrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType) {
            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType) {
            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }
        } };
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, trustAll, new SecureRandom());
        HttpsURLConnection connection = (HttpsURLConnection) new URL("https://localhost:" + port + path)
                .openConnection();
        connection.setSSLSocketFactory(sslContext.getSocketFactory());
        connection.setHostnameVerifier((hostname, session) -> true);
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(10000);
        try {
            return connection.getResponseCode();
        } finally {
            connection.disconnect();
        }
    }


    /**
     * Issue a plain (non-TLS) HTTP GET against the given port and return the HTTP status code (or a negative value when
     * no HTTP response was received at all).
     */
    private static int httpGetPlain(int port, String path) {
        try {
            java.net.HttpURLConnection connection = (java.net.HttpURLConnection) new URL(
                    "http://localhost:" + port + path).openConnection();
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            try {
                return connection.getResponseCode();
            } finally {
                connection.disconnect();
            }
        } catch (Exception e) {
            return -1;
        }
    }


    /**
     * Create a PKCS12 keystore with a self signed RSA certificate (key alias "tomcat", passwords "changeit") using the
     * keytool of the JDK the tests run on.
     */
    private File createKeystore(File dir) throws Exception {
        Assert.assertTrue(dir.isDirectory() || dir.mkdirs());
        File keytool = new File(new File(System.getProperty("java.home"), "bin"), "keytool");
        File keystore = new File(dir, "e2e-keystore.p12");
        ProcessBuilder builder = new ProcessBuilder(keytool.getAbsolutePath(), "-genkeypair", "-alias", "tomcat",
                "-keyalg", "RSA", "-keysize", "2048", "-storetype", "PKCS12", "-keystore", keystore.getAbsolutePath(),
                "-storepass", "changeit", "-keypass", "changeit", "-dname", "CN=localhost", "-validity", "30");
        builder.redirectErrorStream(true);
        Process process = builder.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exit = process.waitFor();
        Assert.assertEquals("keytool failed: " + output, 0, exit);
        Assert.assertTrue(keystore.isFile());
        addDeleteOnTearDown(keystore);
        return keystore;
    }


    private static String readFile(File file) throws Exception {
        return new String(java.nio.file.Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }


    // ------------------------------------------------------- JSON navigation


    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseObject(String json) throws Exception {
        JSONParser parser = new JSONParser(json);
        parser.setNativeNumbers(true);
        return (Map<String, Object>) parser.parseObject();
    }


    @SuppressWarnings("unchecked")
    private static Map<String, Object> getMap(Map<String, Object> map, String key) {
        return (Map<String, Object>) map.get(key);
    }


    @SuppressWarnings("unchecked")
    private static List<Object> getList(Map<String, Object> map, String key) {
        return (List<Object>) map.get(key);
    }


    private static Number getInt(Object value) {
        return (Number) value;
    }


    /**
     * Find a direct child node (from a node's {@code children} list) by type and name.
     */
    private static Map<String, Object> findChild(Map<String, Object> node, String type, String name) {
        List<Object> children = getList(node, "children");
        if (children == null) {
            return null;
        }
        for (Object child : children) {
            @SuppressWarnings("unchecked")
            Map<String, Object> cm = (Map<String, Object>) child;
            if (type.equals(cm.get("type")) && name.equals(cm.get("name"))) {
                return cm;
            }
        }
        return null;
    }


    /**
     * Find a node with the given id anywhere in the tree.
     */
    private static Map<String, Object> findChildById(Map<String, Object> node, String id) {
        if (id.equals(node.get("id"))) {
            return node;
        }
        List<Object> children = getList(node, "children");
        if (children != null) {
            for (Object child : children) {
                @SuppressWarnings("unchecked")
                Map<String, Object> cm = (Map<String, Object>) child;
                Map<String, Object> found = findChildById(cm, id);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }


    /**
     * Find the first direct child node (from a node's {@code children} list) of the given type.
     */
    private static Map<String, Object> firstChildOfType(Map<String, Object> node, String type) {
        List<Object> children = getList(node, "children");
        if (children == null) {
            return null;
        }
        for (Object child : children) {
            @SuppressWarnings("unchecked")
            Map<String, Object> cm = (Map<String, Object>) child;
            if (type.equals(cm.get("type"))) {
                return cm;
            }
        }
        return null;
    }


    /**
     * Fetch the component tree.
     */
    private Map<String, Object> fetchTree(SimpleHttpClient client) throws Exception {
        request(client, "GET", MANAGER2 + "/api/config/tree", null, null, 200);
        return getMap(parseObject(client.getResponseBody()), "tree");
    }


    /**
     * Resolve the id of the manager2 (self) context from the tree. The service and engine names are not fixed (they
     * depend on how the Tomcat instance was created), so the path must not be hard-coded.
     */
    private static String selfContextId(Map<String, Object> tree) {
        Map<String, Object> service = firstChildOfType(tree, "service");
        Assert.assertNotNull("Expected a service", service);
        Map<String, Object> engine = firstChildOfType(service, "engine");
        Assert.assertNotNull("Expected an engine", engine);
        Map<String, Object> host = firstChildOfType(engine, "host");
        Assert.assertNotNull("Expected a host", host);
        Map<String, Object> context = firstChildOfType(host, "context");
        Assert.assertNotNull("Expected the manager2 context", context);
        Assert.assertEquals(Boolean.TRUE, context.get("self"));
        return (String) context.get("id");
    }


    /**
     * The id of the host that owns the context with the given id.
     */
    private static String hostIdOf(String contextId) {
        int ix = contextId.lastIndexOf('/');
        return contextId.substring(0, contextId.lastIndexOf('/', ix - 1));
    }


    /**
     * Find a property entry (from a node's {@code properties} list) by name.
     */
    private static Map<String, Object> findProperty(Map<String, Object> node, String name) {
        List<Object> properties = getList(node, "properties");
        if (properties == null) {
            return null;
        }
        for (Object property : properties) {
            @SuppressWarnings("unchecked")
            Map<String, Object> p = (Map<String, Object>) property;
            if (name.equals(p.get("name"))) {
                return p;
            }
        }
        return null;
    }


    // -----------------------------------------------------------------------


    private void setup() throws Exception {
        setup(false);
    }


    private void setup(boolean withNaming) throws Exception {
        Tomcat tomcat = getTomcatInstance();
        tomcat.setAddDefaultWebXmlToWebapp(false);
        if (withNaming) {
            // Enable JNDI naming so that JNDI resources can be registered
            // in (and looked up from) the global naming context of the
            // server, like the file based one of the default server.xml.
            tomcat.enableNaming();
        }

        // A conf/tomcat-users.xml with the test users so that MemoryRealm
        // instances can be started, mirroring a real CATALINA_BASE layout.
        File conf = new File(getTemporaryDirectory(), "conf");
        Assert.assertTrue(conf.isDirectory() || conf.mkdirs());
        try (PrintWriter pw = new PrintWriter(new File(conf, "tomcat-users.xml"), StandardCharsets.UTF_8)) {
            pw.println("<tomcat-users>");
            pw.println("  <role rolename=\"manager-gui\"/>");
            pw.println("  <role rolename=\"manager-status\"/>");
            pw.println("  <user username=\"manager1\" password=\"secret\" roles=\"manager-gui\"/>");
            pw.println("  <user username=\"status1\" password=\"secret\" roles=\"manager-status\"/>");
            pw.println("</tomcat-users>");
        }

        // The programmatic Tomcat API installs a private internal realm that
        // storeconfig cannot serialise. Use the same realm type as the
        // production server.xml; it loads the users from the file above.
        ((StandardEngine) tomcat.getEngine()).setRealm(new MemoryRealm());

        File webapps = new File(getBuildDirectory(), "webapps");
        File appDir = new File(webapps, "manager2");
        File appWar = new File(webapps, "manager2.war");
        File source = appDir.exists() ? appDir : appWar;
        Assert.assertTrue("manager2 webapp missing - run 'ant deploy' first", source.exists());
        Context manager2 = tomcat.addWebapp(null, MANAGER2, source.getAbsolutePath());
        addDefaultServlet(manager2);
        manager2.addMimeMapping("css", "text/css");
        manager2.addMimeMapping("js", "text/javascript");
        manager2.addMimeMapping("html", "text/html");

        tomcat.start();
    }


    private void addDefaultServlet(Context context) {
        Tomcat.addServlet(context, "default", new DefaultServlet());
        context.addServletMapping("/", "default");
    }


    /**
     * Establish a login session for the given user via the FORM login.
     */
    private void login(SimpleHttpClient client, String user) throws Exception {
        client.setRequest(new String[] { "GET " + MANAGER2 + "/api/info HTTP/1.1" + CRLF,
                "Host: localhost:" + getPort() + CRLF, "Connection: Close" + CRLF, CRLF });
        client.connect();
        client.processRequest(true);

        Assert.assertNotNull("Expected a session to be established", client.getSessionId());

        String body = "j_username=" + user + "&j_password=secret";
        client.setRequest(new String[] { "POST " + MANAGER2 + "/j_security_check HTTP/1.1" + CRLF,
                "Host: localhost:" + getPort() + CRLF, "Cookie: JSESSIONID=" + client.getSessionId() + CRLF,
                "Content-Type: application/x-www-form-urlencoded" + CRLF,
                "Content-Length: " + body.getBytes(StandardCharsets.UTF_8).length + CRLF, "Connection: Close" + CRLF,
                CRLF, body });
        client.connect();
        client.processRequest(true);

        Assert.assertEquals(303, client.getStatusCode());
    }


    private String loginAndGetToken(SimpleHttpClient client, String user) throws Exception {
        login(client, user);
        request(client, "GET", MANAGER2 + "/api/info", null, null, 200);
        String token = getCsrfToken(client);
        Assert.assertNotNull("Expected an X-CSRF-Token header", token);
        return token;
    }


    private void request(SimpleHttpClient client, String method, String path, String token, String body,
            int expectedStatus) throws Exception {
        StringBuilder request = new StringBuilder();
        request.append(method).append(' ').append(path).append(" HTTP/1.1").append(CRLF);
        request.append("Host: localhost:").append(getPort()).append(CRLF);
        if (client.getSessionId() != null) {
            request.append("Cookie: JSESSIONID=").append(client.getSessionId()).append(CRLF);
        }
        if (token != null) {
            request.append("X-CSRF-Token: ").append(token).append(CRLF);
        }
        if (body != null) {
            request.append("Content-Type: application/json").append(CRLF);
            request.append("Content-Length: ").append(body.getBytes(StandardCharsets.UTF_8).length).append(CRLF);
        }
        request.append("Connection: Close").append(CRLF);
        request.append(CRLF);
        if (body != null) {
            request.append(body);
        }
        client.setRequest(new String[] { request.toString() });
        client.connect();
        client.processRequest(true);
        if (client.getStatusCode() != expectedStatus) {
            System.out.println("DBG unexpected: " + method + " " + path + " status=" + client.getStatusCode() +
                    " body=" + client.getResponseBody().substring(0, Math.min(300, client.getResponseBody().length())));
        }
        Assert.assertEquals(expectedStatus, client.getStatusCode());
    }


    private void requestRaw(SimpleHttpClient client, String method, String path, int expectedStatus) throws Exception {
        StringBuilder request = new StringBuilder();
        request.append(method).append(' ').append(path).append(" HTTP/1.1").append(CRLF);
        request.append("Host: localhost:").append(getPort()).append(CRLF);
        if (client.getSessionId() != null) {
            request.append("Cookie: JSESSIONID=").append(client.getSessionId()).append(CRLF);
        }
        request.append("Connection: Close").append(CRLF);
        request.append(CRLF);
        client.setRequest(new String[] { request.toString() });
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(expectedStatus, client.getStatusCode());
    }


    private String getCsrfToken(SimpleHttpClient client) {
        for (String header : client.getResponseHeaders()) {
            if (header.toLowerCase().startsWith("x-csrf-token: ")) {
                return header.substring("X-CSRF-Token: ".length());
            }
        }
        return null;
    }


    private static class TestClient extends SimpleHttpClient {

        @Override
        public boolean isResponseBodyOK() {
            return true;
        }
    }
}
