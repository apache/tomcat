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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Assert;
import org.junit.Test;

import static org.apache.catalina.startup.SimpleHttpClient.CRLF;
import org.apache.catalina.startup.SimpleHttpClient;

/**
 * The read-only status endpoints of the manager2 webapp: the server and per-application status, the cluster view, the
 * system snapshot and the status history.
 */
public class TestManager2WebappStatus extends Manager2WebappTestBase {

    @Test
    public void testStatusEndpoints() throws Exception {
        setup(false);

        SimpleHttpClient client = new TestClient();
        client.setPort(getPort());
        client.connect();
        login(client, "manager1");

        request(client, "GET", MANAGER2 + "/api/status", null, null, 200);
        Assert.assertTrue(client.getResponseBody().contains("\"jvm\""));
        Assert.assertTrue(client.getResponseBody().contains("\"memory\""));

        request(client, "GET", MANAGER2 + "/api/status/workers", null, null, 200);
        Assert.assertTrue(client.getResponseBody().startsWith("["));

        request(client, "GET", MANAGER2 + "/api/status/apps/testapp?path=%2Ftestapp", null, null, 200);
        Assert.assertTrue(client.getResponseBody().contains("\"wrappers\""));
        Assert.assertTrue(client.getResponseBody().contains("\"state\":\"STARTED\""));

        client.disconnect();
    }


    @Test
    public void testClusterEndpointUnclustered() throws Exception {
        setup(false);

        SimpleHttpClient client = new TestClient();
        client.setPort(getPort());
        client.connect();

        // Unauthenticated: the endpoint is protected like the other status
        // endpoints and forwards to the login page.
        client.setRequest(
                new String[] { "GET " + MANAGER2 + "/api/status/cluster HTTP/1.1" + CRLF,
                        "Host: localhost:" + getPort() + CRLF, "Connection: Close" + CRLF, CRLF });
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(200, client.getStatusCode());
        Assert.assertTrue(client.getResponseBody().contains("j_security_check"));

        // As manager-gui: the programmatic test server has no cluster, so the
        // endpoint reports the empty shape.
        login(client, "manager1");
        request(client, "GET", MANAGER2 + "/api/status/cluster", null, null, 200);
        Assert.assertEquals("{\"clustered\":false,\"clusters\":[]}", client.getResponseBody());

        // The authenticated deep link serves the SPA shell (HomeServlet).
        requestRaw(client, "GET", MANAGER2 + "/cluster", 200);
        Assert.assertTrue(client.getResponseBody().contains("shell-main"));

        client.disconnect();
    }


    @Test
    public void testStatusSystemEndpoint() throws Exception {
        setup(false);

        SimpleHttpClient client = new TestClient();
        client.setPort(getPort());
        client.connect();
        login(client, "manager1");

        // The instant CPU and memory snapshot.
        request(client, "GET", MANAGER2 + "/api/status/system", null, null, 200);
        String body = client.getResponseBody();
        Assert.assertTrue(body.contains("\"cpu\""));
        Assert.assertTrue(body.contains("\"memory\""));
        Assert.assertTrue(body.contains("\"availableProcessors\""));
        Assert.assertTrue(body.contains("\"systemLoad\""));
        Assert.assertTrue(body.contains("\"processLoad\""));
        Assert.assertTrue(body.contains("\"loadAverage\""));
        Assert.assertTrue(body.contains("\"threads\""));
        Assert.assertTrue(body.contains("\"heap\""));
        Assert.assertTrue(body.contains("\"nonHeap\""));
        Assert.assertTrue(body.contains("\"pools\""));
        // The JVM runs on at least one core, so the snapshot must not report
        // an unusable machine.
        Assert.assertFalse(body.contains("\"availableProcessors\":0"));
        // The heap is always present and in use by the running webapp.
        Assert.assertFalse(body.contains("\"heap\":{\"used\":0,"));

        // Physical memory: the total and free of the MXBean. The "available"
        // figure read from /proc/meminfo is platform dependent; where it is
        // present it must sit between zero and the total.
        Matcher physical = Pattern.compile("\"physical\":\\{\"total\":(\\d+)").matcher(body);
        Assert.assertTrue("physical memory block missing", physical.find());
        Matcher available = Pattern.compile("\"physical\":\\{[^}]*\"available\":(\\d+)").matcher(body);
        if (available.find()) {
            long value = Long.parseLong(available.group(1));
            Assert.assertTrue("available outside 0..total",
                    value >= 0 && value <= Long.parseLong(physical.group(1)));
        }

        client.disconnect();
    }


    @Test
    public void testStatusHistory() throws Exception {
        setup(false);

        SimpleHttpClient client = new TestClient();
        client.setPort(getPort());
        client.connect();
        login(client, "manager1");

        // The StatusApi servlet is load-on-startup, so the background
        // collection already runs at deployment time. The response reports
        // the configured defaults (10 minute window, 2 second tick).
        request(client, "GET", MANAGER2 + "/api/status/history", null, null, 200);
        String body = client.getResponseBody();
        Assert.assertTrue(body.contains("\"windowMs\":600000"));
        Assert.assertTrue(body.contains("\"tickMs\":2000"));
        Assert.assertTrue(body.contains("\"samples\""));

        // Wait for at least two more ticks: every sample after the first has
        // a baseline, so its rates must be non-null.
        Thread.sleep(4500);
        request(client, "GET", MANAGER2 + "/api/status/history", null, null, 200);
        body = client.getResponseBody();
        int sampleCount = body.split("\"ts\":", -1).length - 1;
        Assert.assertTrue("Expected at least 2 samples, found " + sampleCount, sampleCount >= 2);
        int nullRates = body.split("\"rps\":null", -1).length - 1;
        Assert.assertTrue("Expected at least one sample with a non-null rate", nullRates < sampleCount);

        client.disconnect();
    }
}
