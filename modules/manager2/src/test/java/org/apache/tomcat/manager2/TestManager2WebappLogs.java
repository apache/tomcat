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
import org.apache.catalina.valves.AccessLogValve;

/**
 * The log browsing API of the manager2 webapp: the catalina/host logs and the access logs, with their parsing,
 * filtering and download endpoints.
 */
public class TestManager2WebappLogs extends Manager2WebappTestBase {

    @Test
    public void testLogFilesListAndSeverityFilter() throws Exception {
        setup(false);

        File logsDir = new File(getTemporaryDirectory(), "logs");
        Assert.assertTrue(logsDir.isDirectory() || logsDir.mkdirs());
        writeLogFile(new File(logsDir, "catalina.2026-01-01.log"),
                "01-Jan-2026 10:00:00.001 INFO [main] org.apache.test.A.start Server starting\n" +
                        "01-Jan-2026 10:00:01.002 WARNING [http] org.apache.test.B.warn Something fishy\n" +
                        "01-Jan-2026 10:00:02.003 SEVERE [http] org.apache.test.C.fail It broke\n" +
                        "\tat org.apache.test.C.fail(C.java:10)\n" +
                        "01-Jan-2026 10:00:03.004 INFO [main] org.apache.test.D.done Server done\n");
        writeLogFile(new File(logsDir, "localhost.2026-01-02.log"),
                "{\"time\": \"2026-01-02T10:00:00.001Z\", \"level\": \"INFO\", \"thread\": \"main\"," +
                        " \"class\": \"org.apache.test.A\", \"method\": \"start\", \"message\": \"boot\"}\n" +
                        "{\"time\": \"2026-01-02T10:00:01.002Z\", \"level\": \"SEVERE\", \"thread\": \"http\"," +
                        " \"class\": \"org.apache.test.C\", \"method\": \"fail\", \"message\": \"boom\"," +
                        " \"throwable\": [\"java.lang.IllegalStateException: boom\"," +
                        " \" at org.apache.test.C.fail(C.java:10)\"]}\n");

        SimpleHttpClient client = new TestClient();
        client.setPort(getPort());
        client.connect();
        login(client, "manager1");

        // The list contains both files with their detected formats and not
        // the access log files.
        request(client, "GET", MANAGER2 + "/api/logs", null, null, 200);
        String list = client.getResponseBody();
        Assert.assertTrue(list.contains("\"name\":\"catalina.2026-01-01.log\""));
        Assert.assertTrue(list.contains("\"name\":\"localhost.2026-01-02.log\""));
        Assert.assertFalse(list.contains("access_log"));

        // The plain text log: all four records, the level counts and the
        // stack trace attached to the SEVERE record.
        request(client, "GET", MANAGER2 + "/api/logs/file?name=catalina.2026-01-01.log", null, null, 200);
        String body = client.getResponseBody();
        Assert.assertTrue(body.contains("\"format\":\"text\""));
        Assert.assertTrue(body.contains("\"total\":4"));
        Assert.assertTrue(body.contains("\"SEVERE\":1"));
        Assert.assertTrue(body.contains("\"WARNING\":1"));
        Assert.assertTrue(body.contains("\"INFO\":2"));
        Assert.assertTrue(body.contains("at org.apache.test.C.fail"));
        // The records are ordered from most recent to least recent.
        Assert.assertTrue(body.indexOf("Server done") < body.indexOf("Server starting"));

        // The severity filter keeps only the SEVERE record.
        request(client, "GET", MANAGER2 + "/api/logs/file?name=catalina.2026-01-01.log&level=SEVERE", null, null, 200);
        body = client.getResponseBody();
        Assert.assertTrue(body.contains("\"matched\":1"));
        Assert.assertTrue(body.contains("It broke"));
        Assert.assertFalse(body.contains("Server done"));

        // The JSON log is parsed as well and can be filtered by level.
        request(client, "GET", MANAGER2 + "/api/logs/file?name=localhost.2026-01-02.log&level=SEVERE", null, null, 200);
        body = client.getResponseBody();
        Assert.assertTrue(body.contains("\"format\":\"json\""));
        Assert.assertTrue(body.contains("\"matched\":1"));
        Assert.assertTrue(body.contains("boom"));

        // A free text search works across the fields.
        request(client, "GET", MANAGER2 + "/api/logs/file?name=catalina.2026-01-01.log&search=fishy", null, null, 200);
        body = client.getResponseBody();
        Assert.assertTrue(body.contains("\"matched\":1"));
        Assert.assertTrue(body.contains("Something fishy"));

        // Invalid file names are rejected.
        request(client, "GET", MANAGER2 + "/api/logs/file?name=..%2Fweb.xml", null, null, 400);

        // The full raw file can be downloaded, unfiltered and without a line
        // limit.
        request(client, "GET", MANAGER2 + "/api/logs/download?name=catalina.2026-01-01.log", null, null, 200);
        body = client.getResponseBody();
        Assert.assertTrue(body.contains("Server starting"));
        Assert.assertTrue(body.contains("Server done"));
        // Invalid file names are rejected for downloads as well.
        request(client, "GET", MANAGER2 + "/api/logs/download?name=..%2Fweb.xml", null, null, 400);

        client.disconnect();
    }


    @Test
    public void testAccessLogTextPatternAndFilters() throws Exception {
        setup(false);

        File logsDir = new File(getTemporaryDirectory(), "logs");
        Assert.assertTrue(logsDir.isDirectory() || logsDir.mkdirs());
        writeLogFile(new File(logsDir, "localhost_access_log.2026-01-01.txt"),
                "127.0.0.1 - - [01/Jan/2026:10:00:00 +0000] \"GET /ok HTTP/1.1\" 200 100 ABCDEF\n" +
                        "127.0.0.1 - - [01/Jan/2026:10:00:01 +0000] \"GET /missing HTTP/1.1\" 404 55 -\n" +
                        "127.0.0.1 - manager1 [01/Jan/2026:10:00:02 +0000] \"POST /submit HTTP/1.1\" 500 10 GHIJKL\n");

        // Configure an access log valve with a pattern that also logs the
        // session ID: the available fields (and filters) must follow the
        // configured pattern.
        AccessLogValve valve = new AccessLogValve();
        valve.setDirectory(new File(getTemporaryDirectory(), "valve-logs").getAbsolutePath());
        valve.setPrefix("valve");
        valve.setPattern("%h %l %u %t \"%r\" %s %b %S");
        getTomcatInstance().getHost().getPipeline().addValve(valve);

        SimpleHttpClient client = new TestClient();
        client.setPort(getPort());
        client.connect();
        login(client, "manager1");

        // The list reports the file, the pattern and the derived fields.
        request(client, "GET", MANAGER2 + "/api/access-log", null, null, 200);
        String list = client.getResponseBody();
        Assert.assertTrue(list.contains("\"name\":\"localhost_access_log.2026-01-01.txt\""));
        Assert.assertTrue(list.contains("%S"));
        Assert.assertTrue(list.contains("sessionId"));

        // All three records are parsed; the method, path and protocol are
        // derived from the request line and the status is a number.
        request(client, "GET", MANAGER2 + "/api/access-log/file?name=localhost_access_log.2026-01-01.txt", null, null,
                200);
        String body = client.getResponseBody();
        Assert.assertTrue(body.contains("\"format\":\"text\""));
        Assert.assertTrue(body.contains("\"total\":3"));
        Assert.assertTrue(body.contains("\"2xx\":1"));
        Assert.assertTrue(body.contains("\"4xx\":1"));
        Assert.assertTrue(body.contains("\"5xx\":1"));
        Assert.assertTrue(body.contains("\"method\":\"GET\""));
        Assert.assertTrue(body.contains("\"path\":\"/missing\""));
        Assert.assertTrue(body.contains("\"protocol\":\"HTTP/1.1\""));
        Assert.assertTrue(body.contains("\"statusCode\":404"));
        Assert.assertTrue(body.contains("\"sessionId\":\"ABCDEF\""));
        Assert.assertTrue(body.contains("\"user\":\"manager1\""));
        // The records are ordered from most recent to least recent.
        Assert.assertTrue(body.indexOf("/submit") < body.indexOf("/ok"));

        // Filter by status class.
        request(client, "GET", MANAGER2 + "/api/access-log/file?name=localhost_access_log.2026-01-01.txt&status=4xx",
                null, null, 200);
        body = client.getResponseBody();
        Assert.assertTrue(body.contains("\"matched\":1"));
        Assert.assertTrue(body.contains("/missing"));

        // Filter by method.
        request(client, "GET", MANAGER2 + "/api/access-log/file?name=localhost_access_log.2026-01-01.txt&method=POST",
                null, null, 200);
        body = client.getResponseBody();
        Assert.assertTrue(body.contains("\"matched\":1"));
        Assert.assertTrue(body.contains("manager1"));

        // Filter by user.
        request(client, "GET", MANAGER2 + "/api/access-log/file?name=localhost_access_log.2026-01-01.txt&user=manager1",
                null, null, 200);
        body = client.getResponseBody();
        Assert.assertTrue(body.contains("\"matched\":1"));

        // Filter by session ID.
        request(client, "GET",
                MANAGER2 + "/api/access-log/file?name=localhost_access_log.2026-01-01.txt&session=GHIJKL", null, null,
                200);
        body = client.getResponseBody();
        Assert.assertTrue(body.contains("\"matched\":1"));
        Assert.assertTrue(body.contains("/submit"));

        // The full raw file can be downloaded.
        request(client, "GET", MANAGER2 + "/api/access-log/download?name=localhost_access_log.2026-01-01.txt",
                null, null, 200);
        body = client.getResponseBody();
        Assert.assertTrue(body.contains("GET /ok HTTP/1.1"));
        Assert.assertTrue(body.contains("POST /submit HTTP/1.1"));

        client.disconnect();
    }


    @Test
    public void testAccessLogJsonAndSessionFilter() throws Exception {
        setup(false);

        File logsDir = new File(getTemporaryDirectory(), "logs");
        Assert.assertTrue(logsDir.isDirectory() || logsDir.mkdirs());
        writeLogFile(new File(logsDir, "localhost_access_log.2026-01-02.txt"),
                "{\"host\": \"127.0.0.1\", \"user\": \"manager1\"," +
                        " \"time\": \"[02/Jan/2026:10:00:00 +0000]\", \"method\": \"GET\"," +
                        " \"path\": \"/\", \"protocol\": \"HTTP/1.1\"," +
                        " \"statusCode\": \"200\", \"size\": \"10\", \"sessionId\": \"AAAA-1111\"}\n" +
                        "{\"host\": \"127.0.0.1\", \"user\": \"-\"," +
                        " \"time\": \"[02/Jan/2026:10:00:01 +0000]\", \"method\": \"GET\"," +
                        " \"path\": \"/forbidden\", \"protocol\": \"HTTP/1.1\"," +
                        " \"statusCode\": \"403\", \"size\": \"5\", \"sessionId\": \"CCCC-2222\"}\n");

        SimpleHttpClient client = new TestClient();
        client.setPort(getPort());
        client.connect();
        login(client, "manager1");

        // The JSON access log is detected and parsed; the status is
        // converted to a number and the "-" marker to null.
        request(client, "GET", MANAGER2 + "/api/access-log/file?name=localhost_access_log.2026-01-02.txt", null, null,
                200);
        String body = client.getResponseBody();
        Assert.assertTrue(body.contains("\"format\":\"json\""));
        Assert.assertTrue(body.contains("\"total\":2"));
        Assert.assertTrue(body.contains("\"sessionId\""));
        Assert.assertTrue(body.contains("\"statusCode\":403"));
        Assert.assertTrue(body.contains("\"user\":null"));

        // Filter by session ID.
        request(client, "GET", MANAGER2 + "/api/access-log/file?name=localhost_access_log.2026-01-02.txt&session=AAAA",
                null, null, 200);
        body = client.getResponseBody();
        Assert.assertTrue(body.contains("\"matched\":1"));
        Assert.assertTrue(body.contains("\"user\":\"manager1\""));

        // Filter by status class.
        request(client, "GET", MANAGER2 + "/api/access-log/file?name=localhost_access_log.2026-01-02.txt&status=4xx",
                null, null, 200);
        body = client.getResponseBody();
        Assert.assertTrue(body.contains("\"matched\":1"));
        Assert.assertTrue(body.contains("/forbidden"));

        // The full raw file can be downloaded.
        request(client, "GET", MANAGER2 + "/api/access-log/download?name=localhost_access_log.2026-01-02.txt",
                null, null, 200);
        body = client.getResponseBody();
        Assert.assertTrue(body.contains("AAAA-1111"));
        Assert.assertTrue(body.contains("/forbidden"));

        client.disconnect();
    }
}
