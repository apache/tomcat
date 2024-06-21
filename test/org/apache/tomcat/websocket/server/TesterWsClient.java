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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import jakarta.websocket.CloseReason.CloseCode;

/**
 * A client for testing Websocket behavior that differs from standard client behavior.
 */
public class TesterWsClient {

    private static final byte[] maskingKey = new byte[] { 0x12, 0x34, 0x56, 0x78 };
    private static final String DEFAULT_KEY_HEADER_VALUE = "OEvAoAKn5jsuqv2/YJ1Wfg==";

    private final Socket socket;
    private final String keyHeaderValue;

    public TesterWsClient(String host, int port) throws Exception {
        this(host, port, DEFAULT_KEY_HEADER_VALUE);
    }

    public TesterWsClient(String host, int port, String keyHeaderValue) throws Exception {
        this.socket = new Socket(host, port);
        // Set read timeout in case of failure so test doesn't hang
        socket.setSoTimeout(2000);
        // Disable Nagle's algorithm to ensure packets sent immediately
        // TODO: Hoping this causes writes to wait for a TCP ACK for TCP RST
        // test cases but I'm not sure?
        socket.setTcpNoDelay(true);
        this.keyHeaderValue = keyHeaderValue;
    }

    public void httpUpgrade(String path) throws IOException {
        String req = createUpgradeRequest(path);
        write(req.getBytes(StandardCharsets.UTF_8));
        readUpgradeResponse();
    }

    public void sendTextMessage(String text) throws IOException {
        sendTextMessage(text.getBytes(StandardCharsets.UTF_8));
    }

    public void sendTextMessage(byte[] utf8Bytes) throws IOException {
        write(createFrame(true, 1, utf8Bytes));
    }

    public void sendCloseFrame(CloseCode closeCode) throws IOException {
        int code = closeCode.getCode();
        byte[] codeBytes = new byte[2];
        codeBytes[0] = (byte) (code >> 8);
        codeBytes[1] = (byte) code;
        write(createFrame(true, 8, codeBytes));
    }

    public int readUpgradeResponse() throws IOException {
        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        int result = -1;
        String line = in.readLine();
        while (line != null && !line.isEmpty()) {
            if (result == -1) {
                if (line.length() > 11) {
                    // First line expected to be "HTTP/1.1 nnn "
                    result = Integer.parseInt(line.substring(9, 12));
                } else {
                    // No response code - treat as server error for this test
                    result = 500;
                }
            }
            line = in.readLine();
        }
        return result;
    }

    public void closeSocket() throws IOException {
        // Enable SO_LINGER to ensure close() only returns when TCP closing
        // handshake completes
        socket.setSoLinger(true, 65535);
        socket.close();
    }

    /*
     * Send a TCP RST instead of a TCP closing handshake
     */
    public void forceCloseSocket() throws IOException {
        // SO_LINGER sends a TCP RST when timeout expires
        socket.setSoLinger(true, 0);
        socket.close();
    }

    public int read(byte[] bytes) throws IOException {
        return socket.getInputStream().read(bytes);
    }

    public void write(byte[] bytes) throws IOException {
        socket.getOutputStream().write(bytes);
        socket.getOutputStream().flush();
    }

    public String createUpgradeRequest(String path) {
        String[] upgradeRequestLines = { "GET " + path + " HTTP/1.1", "Connection: Upgrade", "Host: localhost:8080",
                "Origin: localhost:8080", "Sec-WebSocket-Key: " + keyHeaderValue, "Sec-WebSocket-Version: 13",
                "Upgrade: websocket" };
        StringBuffer sb = new StringBuffer();
        for (String line : upgradeRequestLines) {
            sb.append(line);
            sb.append("\r\n");
        }
        sb.append("\r\n");
        return sb.toString();
    }

    private static byte[] createFrame(boolean fin, int opCode, byte[] payload) {
        byte[] frame = new byte[6 + payload.length];
        frame[0] = (byte) (opCode | (fin ? 1 << 7 : 0));
        frame[1] = (byte) (0x80 | payload.length);

        frame[2] = maskingKey[0];
        frame[3] = maskingKey[1];
        frame[4] = maskingKey[2];
        frame[5] = maskingKey[3];

        for (int i = 0; i < payload.length; i++) {
            frame[i + 6] = (byte) (payload[i] ^ maskingKey[i % 4]);
        }

        return frame;
    }
}
