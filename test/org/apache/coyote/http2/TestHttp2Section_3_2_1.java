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
package org.apache.coyote.http2;

import org.junit.Test;

public class TestHttp2Section_3_2_1 extends Http2TestBase {

    @Test
    public void testZeroHttp2Settings() throws Exception {
        enableHttp2();
        configureAndStartWebApplication();
        openClientConnection();
        doHttpUpgrade(Http2TestBase.DEFAULT_CONNECTION_HEADER_VALUE, "h2c", "", false);
        parseHttp11Response();
    }


    @Test
    public void testMultipleHttp2Settings() throws Exception {
        enableHttp2();
        configureAndStartWebApplication();
        openClientConnection();
        doHttpUpgrade(Http2TestBase.DEFAULT_CONNECTION_HEADER_VALUE, "h2c",
                Http2TestBase.EMPTY_HTTP2_SETTINGS_HEADER +
                Http2TestBase.EMPTY_HTTP2_SETTINGS_HEADER, false);
        parseHttp11Response();
    }


    @Test
    public void testMissingConnectionValue() throws Exception {
        enableHttp2();
        configureAndStartWebApplication();
        openClientConnection();
        doHttpUpgrade("Upgrade", "h2c", Http2TestBase.EMPTY_HTTP2_SETTINGS_HEADER, false);
        parseHttp11Response();
    }


    @Test
    public void testSplitConnectionValue01() throws Exception {
        enableHttp2();
        configureAndStartWebApplication();
        openClientConnection();
        doHttpUpgrade("Upgrade\r\nConnection: HTTP2-Settings", "h2c",
                Http2TestBase.EMPTY_HTTP2_SETTINGS_HEADER, true);
        sendClientPreface();
        validateHttp2InitialResponse();
    }


    @Test
    public void testSplitConnectionValue02() throws Exception {
        enableHttp2();
        configureAndStartWebApplication();
        openClientConnection();
        doHttpUpgrade("HTTP2-Settings\r\nConnection: Upgrade", "h2c",
                Http2TestBase.EMPTY_HTTP2_SETTINGS_HEADER, true);
        sendClientPreface();
        validateHttp2InitialResponse();
    }

    // No need to test how trailing '=' are handled here. HTTP2Settings payloads
    // are always a multiple of 6 long which means valid payloads never end in
    // '='. Invalid payloads will be rejected anyway.
}
