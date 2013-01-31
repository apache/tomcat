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
package org.apache.tomcat.websocket;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.util.concurrent.TimeUnit;

public class WsRemoteEndpointClient extends WsRemoteEndpointBase {

    private final AsynchronousSocketChannel channel;

    public WsRemoteEndpointClient(AsynchronousSocketChannel channel) {
        this.channel = channel;
    }


    @Override
    protected byte getMasked() {
        return (byte) 0x80;
    }


    @Override
    protected void sendMessage(WsCompletionHandler handler) {
        long timeout = getAsyncSendTimeout();
        if (timeout < 1) {
            timeout = Long.MAX_VALUE;

        }
        channel.write(new ByteBuffer[] {outputBuffer, payload}, 0, 2,
                getAsyncSendTimeout(), TimeUnit.MILLISECONDS, null, handler);
    }

    @Override
    protected void close() {
        try {
            channel.close();
        } catch (IOException ignore) {
            // Ignore
        }
    }
}
