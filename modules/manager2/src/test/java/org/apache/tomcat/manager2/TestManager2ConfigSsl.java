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
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.startup.SimpleHttpClient;

/**
 * The TLS host configuration (with a generated keystore) and the context child components through the manager2
 * configuration API.
 */
public class TestManager2ConfigSsl extends Manager2ConfigTestBase {

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

            // Pre-shared keys ------------------------------------------

            // A pre-shared key can be added to the SSL host
            // configuration (it is applied at once on the running, TLS
            // enabled connector; inert for a JSSE based connector, which
            // is what the test connector is).
            request(client, "POST", MANAGER2 + "/api/config/child", token,
                    "{\"parent\":\"" + sslHostConfigId + "\",\"type\":\"preSharedKey\"," +
                            "\"identity\":\"client-a\",\"key\":\"000102030405060708090a0b0c0d0e0f\"}",
                    200);
            Assert.assertTrue(client.getResponseBody().contains("\"ok\":true"));
            String preSharedKeyId = sslHostConfigId + "/preSharedKey/0";

            // A missing identity or key, and a key that is not
            // hexadecimal, are rejected.
            request(client, "POST", MANAGER2 + "/api/config/child", token,
                    "{\"parent\":\"" + sslHostConfigId + "\",\"type\":\"preSharedKey\",\"key\":\"00ff\"}", 400);
            Assert.assertTrue(client.getResponseBody().contains("MISSING_FIELD"));
            request(client, "POST", MANAGER2 + "/api/config/child", token,
                    "{\"parent\":\"" + sslHostConfigId + "\",\"type\":\"preSharedKey\",\"identity\":\"x\"}", 400);
            Assert.assertTrue(client.getResponseBody().contains("MISSING_FIELD"));
            request(client, "POST", MANAGER2 + "/api/config/child", token,
                    "{\"parent\":\"" + sslHostConfigId + "\",\"type\":\"preSharedKey\"," +
                            "\"identity\":\"x\",\"key\":\"not-hex\"}",
                    400);
            Assert.assertTrue(client.getResponseBody().contains("INVALID_VALUE"));

            // A second key with the same identity is rejected (the keys
            // are selected by identity).
            request(client, "POST", MANAGER2 + "/api/config/child", token,
                    "{\"parent\":\"" + sslHostConfigId + "\",\"type\":\"preSharedKey\"," +
                            "\"identity\":\"client-a\",\"key\":\"00ff\"}",
                    409);
            Assert.assertTrue(client.getResponseBody().contains("DUPLICATE"));

            // A pre-shared key is only a child of an SSL host configuration.
            request(client, "POST", MANAGER2 + "/api/config/child", token,
                    "{\"parent\":\"" + connectorId + "\",\"type\":\"preSharedKey\"," +
                            "\"identity\":\"x\",\"key\":\"00ff\"}",
                    400);
            Assert.assertTrue(client.getResponseBody().contains("BAD_PARENT"));

            // The key is visible in the tree under its identity...
            tree = fetchTree(client);
            Map<String, Object> pskNode = findChild(
                    findChild(findChildById(tree, connectorId), "sslHostConfig", "_default_"),
                    "preSharedKey", "client-a");
            Assert.assertNotNull("Expected the pre-shared key in the tree", pskNode);
            Assert.assertEquals(preSharedKeyId, pskNode.get("id"));

            // ...and its node detail exposes the three attributes (the
            // key in its hexadecimal form, the digest with its default).
            request(client, "GET", MANAGER2 + "/api/config/node/" + preSharedKeyId, null, null, 200);
            Map<String, Object> pskDetail = parseObject(client.getResponseBody());
            Assert.assertEquals("preSharedKey", pskDetail.get("type"));
            Assert.assertEquals("client-a", pskDetail.get("name"));
            Assert.assertEquals("000102030405060708090a0b0c0d0e0f", findProperty(pskDetail, "key").get("value"));
            Assert.assertEquals("SHA256", findProperty(pskDetail, "digest").get("value"));

            // The digest can be changed and reads back...
            request(client, "POST", MANAGER2 + "/api/config/attribute", token,
                    "{\"id\":\"" + preSharedKeyId + "\",\"name\":\"digest\",\"value\":\"SHA384\"}", 200);
            request(client, "GET", MANAGER2 + "/api/config/node/" + preSharedKeyId, null, null, 200);
            pskDetail = parseObject(client.getResponseBody());
            Assert.assertEquals("SHA384", findProperty(pskDetail, "digest").get("value"));

            // ...while a key that is not hexadecimal is refused.
            request(client, "POST", MANAGER2 + "/api/config/attribute", token,
                    "{\"id\":\"" + preSharedKeyId + "\",\"name\":\"key\",\"value\":\"not-hex\"}", 400);
            Assert.assertTrue(client.getResponseBody().contains("SET_FAILED"));

            // The TLS configuration (including the pre-shared key) is
            // part of the stored server.xml.
            request(client, "GET", MANAGER2 + "/api/config/store/preview", null, null, 200);
            String xml = (String) parseObject(client.getResponseBody()).get("xml");
            Assert.assertTrue(xml.contains("<SSLHostConfig"));
            Assert.assertTrue(xml.contains("certificateKeystoreFile=\""));
            Assert.assertTrue(xml.contains(keystore.getPath()));
            Assert.assertTrue(xml.contains("<PreSharedKey"));
            Assert.assertTrue(xml.contains("identity=\"client-a\""));
            Assert.assertTrue(xml.contains("000102030405060708090a0b0c0d0e0f"));

            // A duplicate host name is rejected.
            request(client, "POST", MANAGER2 + "/api/config/child", token,
                    "{\"parent\":\"" + connectorId + "\",\"type\":\"sslHostConfig\"," +
                            "\"hostName\":\"_default_\",\"certificate\":{\"type\":\"RSA\"," +
                            "\"certificateKeystoreFile\":\"" + keystore.getPath() +
                            "\",\"certificateKeystorePassword\":\"changeit\"}}",
                    409);
            Assert.assertTrue(client.getResponseBody().contains("DUPLICATE"));

            // The last pre-shared key can be removed (unlike the last
            // certificate, a key is never required) and disappears from
            // the tree.
            request(client, "DELETE", MANAGER2 + "/api/config/child", token, "{\"id\":\"" + preSharedKeyId + "\"}",
                    200);
            Assert.assertTrue(client.getResponseBody().contains("\"ok\":true"));
            tree = fetchTree(client);
            Assert.assertNull("Expected the pre-shared key to be gone",
                    findChild(findChild(findChildById(tree, connectorId), "sslHostConfig", "_default_"),
                            "preSharedKey", "client-a"));

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
}
