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

/*
 * Invalid percent-escape sequences in the query string are not rejected by the HTTP parser (they are only checked
 * when the query string is decoded). The upgrade processing must reject such requests with a 400 response rather than
 * failing with an uncaught IllegalArgumentException.
 */
public class TestHandshakeRequest extends WebSocketBaseTest {

    @Test
    public void testInvalidEscapePair() throws Exception {
        doTest("?x=%ZZ", HttpServletResponse.SC_BAD_REQUEST);
    }


    @Test
    public void testIncompleteEscape() throws Exception {
        doTest("?x=%2", HttpServletResponse.SC_BAD_REQUEST);
    }


    @Test
    public void testTrailingPercent() throws Exception {
        doTest("?x=%", HttpServletResponse.SC_BAD_REQUEST);
    }


    @Test
    public void testLowerCaseInvalidEscapePair() throws Exception {
        doTest("?x=%zz", HttpServletResponse.SC_BAD_REQUEST);
    }


    @Test
    public void testInvalidEscapeInOneOfMultipleParameters() throws Exception {
        doTest("?x=1&y=%GG", HttpServletResponse.SC_BAD_REQUEST);
    }


    /*
     * URI syntax is valid here so the request reaches query string decoding. That mechanism already rejects the
     * invalid UTF-8 sequence with a 400 response. Pinned here since it is a different code path.
     */
    @Test
    public void testInvalidUtf8Escape() throws Exception {
        doTest("?x=%C0%80", HttpServletResponse.SC_BAD_REQUEST);
    }


    @Test
    public void testEmptyQueryString() throws Exception {
        doTest("?", HttpServletResponse.SC_SWITCHING_PROTOCOLS);
    }


    @Test
    public void testValidEscape() throws Exception {
        doTest("?x=%20", HttpServletResponse.SC_SWITCHING_PROTOCOLS);
    }


    private void doTest(String queryString, int expectedStatusCode) throws Exception {
        startServer(TesterEchoServer.Config.class);

        TesterWsClient client = new TesterWsClient("localhost", getPort());
        String req = client.createUpgradeRequest(TesterEchoServer.Config.PATH_BASIC + queryString);
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
