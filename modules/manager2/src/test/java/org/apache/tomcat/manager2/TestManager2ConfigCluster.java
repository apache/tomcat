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
 * The cluster and the connector upgrade protocols (HTTP/2) through the manager2 configuration API.
 */
public class TestManager2ConfigCluster extends Manager2ConfigTestBase {

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
}
