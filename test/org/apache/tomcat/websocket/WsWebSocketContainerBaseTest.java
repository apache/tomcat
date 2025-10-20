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
package org.apache.tomcat.websocket;

import java.net.URI;

import jakarta.websocket.ClientEndpointConfig;
import jakarta.websocket.Endpoint;
import jakarta.websocket.Session;
import jakarta.websocket.WebSocketContainer;

public class WsWebSocketContainerBaseTest extends WebSocketBaseTest {

    protected static final byte[] MESSAGE_BINARY_4K = new byte[4096];

    protected static final long TIMEOUT_MS = 5 * 1000;
    protected static final long MARGIN = 500;


    /*
     * Make this possible to override so sub-class can more easily test proxy
     */
    protected String getHostName() {
        return "localhost";
    }


    protected Session connectToEchoServer(WebSocketContainer wsContainer, Endpoint endpoint, String path)
            throws Exception {
        return wsContainer.connectToServer(endpoint, ClientEndpointConfig.Builder.create().build(),
                new URI("ws://" + getHostName() + ":" + getPort() + path));
    }
}
