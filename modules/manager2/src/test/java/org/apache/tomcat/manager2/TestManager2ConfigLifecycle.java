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

import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.startup.SimpleHttpClient;

/**
 * Lifecycle operations (start / stop / restart) and realm configuration through the manager2 configuration API.
 */
public class TestManager2ConfigLifecycle extends Manager2ConfigTestBase {

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


    @SuppressWarnings("unchecked")
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

            // The context was not deployed from server.xml and has no
            // configuration file, so the store gives it a new context file
            // instead of inlining it in server.xml. The combined realm and
            // its sub realms are part of that file, not of the server.xml.
            request(client, "GET", MANAGER2 + "/api/config/store/preview", null, null, 200);
            Map<String, Object> preview = parseObject(client.getResponseBody());
            String xml = (String) preview.get("xml");
            Assert.assertFalse(xml.contains("org.apache.catalina.realm.LockOutRealm"));
            List<Object> previewFiles = (List<Object>) preview.get("files");
            Assert.assertTrue("Expected a new context file for /realmapp: " + previewFiles,
                    previewFiles.stream().anyMatch(f -> String.valueOf(f).endsWith("realmapp.xml")));

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
}
