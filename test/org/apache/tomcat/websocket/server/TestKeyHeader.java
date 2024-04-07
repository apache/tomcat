/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
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
package org.apache.tomcat.websocket.server;

import java.nio.charset.StandardCharsets;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.websocket.CloseReason.CloseCodes;

import org.junit.Assert;
import org.junit.Test;

import org.apache.tomcat.websocket.TesterEchoServer;
import org.apache.tomcat.websocket.WebSocketBaseTest;

public class TestKeyHeader extends WebSocketBaseTest {

    @Test
    public void testEmptyString() throws Exception {
        doTest("", HttpServletResponse.SC_BAD_REQUEST);
    }


    @Test
    public void testValid() throws Exception {
        // "0123456789012345" encoded with base64
        doTest("MDEyMzQ1Njc4OTAxMjM0NQ==", HttpServletResponse.SC_SWITCHING_PROTOCOLS);
    }


    @Test
    public void testInvalidCharacter() throws Exception {
        // "0123456789012345" encoded with base64
        doTest("MDEy(zQ1Njc4OTAxMjM0NQ==", HttpServletResponse.SC_BAD_REQUEST);
    }


    @Test
    public void testTooShort() throws Exception {
        // "012345678901234" encoded with base64
        doTest("MDEyMzQ1Njc4OTAxMjM0", HttpServletResponse.SC_BAD_REQUEST);
    }


    @Test
    public void testTooLong01() throws Exception {
        // "01234567890123456" encoded with base64
        doTest("MDEyMzQ1Njc4OTAxMjM0NTY=", HttpServletResponse.SC_BAD_REQUEST);
    }


    @Test
    public void testTooLong02() throws Exception {
        // "012345678901234678" encoded with base64
        doTest("MDEyMzQ1Njc4OTAxMjM0NTY3OA==", HttpServletResponse.SC_BAD_REQUEST);
    }

    private void doTest(String keyHeaderValue, int expectedStatusCode) throws Exception {
        startServer(TesterEchoServer.Config.class);

        TesterWsClient client = new TesterWsClient("localhost", getPort(), keyHeaderValue);
        String req = client.createUpgradeRequest(TesterEchoServer.Config.PATH_BASIC);
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
