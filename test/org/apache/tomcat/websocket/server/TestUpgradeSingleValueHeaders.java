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

import java.nio.charset.StandardCharsets;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.websocket.CloseReason.CloseCodes;

import org.junit.Assert;
import org.junit.Test;

import org.apache.tomcat.websocket.TesterEchoServer;
import org.apache.tomcat.websocket.WebSocketBaseTest;

/**
 * RFC 6455, sections 4.2.1, 11.3.1 and 11.3.5: the Sec-WebSocket-Key and Sec-WebSocket-Version header fields must
 * appear once in the opening handshake request and the version must be 13.
 */
public class TestUpgradeSingleValueHeaders extends WebSocketBaseTest {

    private static final String KEY = "Sec-WebSocket-Key: MDEyMzQ1Njc4OTAxMjM0NQ==\r\n";
    private static final String VERSION = "Sec-WebSocket-Version: 13\r\n";


    @Test
    public void testValid() throws Exception {
        doTest(KEY + VERSION, HttpServletResponse.SC_SWITCHING_PROTOCOLS);
    }


    @Test
    public void testDuplicateKey() throws Exception {
        doTest(KEY + KEY + VERSION, HttpServletResponse.SC_BAD_REQUEST);
    }


    @Test
    public void testDuplicateVersion() throws Exception {
        doTest(KEY + VERSION + VERSION, HttpServletResponse.SC_BAD_REQUEST);
    }


    @Test
    public void testVersionList() throws Exception {
        doTest(KEY + "Sec-WebSocket-Version: 8, 13\r\n", HttpServletResponse.SC_UPGRADE_REQUIRED);
    }


    @Test
    public void testUnsupportedVersion() throws Exception {
        doTest(KEY + "Sec-WebSocket-Version: 8\r\n", HttpServletResponse.SC_UPGRADE_REQUIRED);
    }


    private void doTest(String keyAndVersionHeaders, int expectedStatusCode) throws Exception {
        startServer(TesterEchoServer.Config.class);

        TesterWsClient client = new TesterWsClient("localhost", getPort());
        // Replace the key and version header lines of the default request
        String req = client.createUpgradeRequest(TesterEchoServer.Config.PATH_BASIC)
                .replaceAll("Sec-WebSocket-(Key|Version): [^\r]*\r\n", "")
                .replace("Upgrade: websocket\r\n", "Upgrade: websocket\r\n" + keyAndVersionHeaders);
        client.write(req.getBytes(StandardCharsets.UTF_8));
        int rc = client.readUpgradeResponse();

        Assert.assertEquals(expectedStatusCode, rc);

        if (expectedStatusCode == HttpServletResponse.SC_SWITCHING_PROTOCOLS) {
            client.sendCloseFrame(CloseCodes.NORMAL_CLOSURE);
            // Read (and ignore) the response
            byte[] buf = new byte[256];
            while (client.read(buf) > 0) {
                // Ignore
            }
        }
        client.closeSocket();
    }
}
