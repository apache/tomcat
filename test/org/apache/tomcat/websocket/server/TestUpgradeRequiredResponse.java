/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tomcat.websocket.server;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import jakarta.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Test;

import org.apache.tomcat.websocket.TesterEchoServer;
import org.apache.tomcat.websocket.WebSocketBaseTest;

/**
 * RFC 9110, sections 7.8 and 15.5.22: a 426 response must include an Upgrade header field naming the required protocol
 * and the corresponding Connection option. RFC 6455, section 4.4: it also lists the supported WebSocket versions.
 */
public class TestUpgradeRequiredResponse extends WebSocketBaseTest {

    @Test
    public void testUnsupportedVersion() throws Exception {
        startServer(TesterEchoServer.Config.class);

        String request = "GET " + TesterEchoServer.Config.PATH_BASIC + " HTTP/1.1\r\n" +
                "Host: localhost:" + getPort() + "\r\n" +
                "Upgrade: websocket\r\n" +
                "Connection: upgrade\r\n" +
                "Sec-WebSocket-Version: 8\r\n" +
                "Sec-WebSocket-Key: MDEyMzQ1Njc4OTAxMjM0NQ==\r\n" +
                "\r\n";

        Map<String,String> headers = new HashMap<>();
        int status;
        try (Socket socket = new Socket("localhost", getPort())) {
            socket.getOutputStream().write(request.getBytes(StandardCharsets.ISO_8859_1));
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.ISO_8859_1));
            status = Integer.parseInt(in.readLine().substring(9, 12));
            String line = in.readLine();
            while (line != null && !line.isEmpty()) {
                int colon = line.indexOf(':');
                headers.put(line.substring(0, colon).toLowerCase(Locale.ENGLISH), line.substring(colon + 1).trim());
                line = in.readLine();
            }
        }

        Assert.assertEquals(HttpServletResponse.SC_UPGRADE_REQUIRED, status);
        Assert.assertEquals("websocket", headers.get("upgrade"));
        Assert.assertEquals("upgrade", headers.get("connection"));
        Assert.assertEquals("13", headers.get("sec-websocket-version"));
    }
}
