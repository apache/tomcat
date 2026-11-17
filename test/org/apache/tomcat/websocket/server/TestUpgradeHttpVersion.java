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
 * RFC 9110, section 7.8 requires a server to ignore an Upgrade header field received in an HTTP/1.0 request and RFC
 * 6455, section 4.2.1 requires the WebSocket opening handshake to use HTTP/1.1 or later.
 */
public class TestUpgradeHttpVersion extends WebSocketBaseTest {

    @Test
    public void testHttp11() throws Exception {
        Assert.assertEquals(HttpServletResponse.SC_SWITCHING_PROTOCOLS, doTest("HTTP/1.1"));
    }


    @Test
    public void testHttp10() throws Exception {
        Assert.assertNotEquals(HttpServletResponse.SC_SWITCHING_PROTOCOLS, doTest("HTTP/1.0"));
    }


    private int doTest(String httpVersion) throws Exception {
        startServer(TesterEchoServer.Config.class);

        TesterWsClient client = new TesterWsClient("localhost", getPort());
        String req = client.createUpgradeRequest(TesterEchoServer.Config.PATH_BASIC)
                .replaceFirst(" HTTP/1\\.1", " " + httpVersion);
        client.write(req.getBytes(StandardCharsets.UTF_8));
        int rc = client.readUpgradeResponse();

        if (rc == HttpServletResponse.SC_SWITCHING_PROTOCOLS) {
            client.sendCloseFrame(CloseCodes.NORMAL_CLOSURE);
            // Read (and ignore) the response
            byte[] buf = new byte[256];
            while (client.read(buf) > 0) {
                // Ignore
            }
        }
        client.closeSocket();
        return rc;
    }
}
