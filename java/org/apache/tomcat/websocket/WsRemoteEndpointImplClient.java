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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.ReentrantLock;

import jakarta.websocket.SendHandler;
import jakarta.websocket.SendResult;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

public class WsRemoteEndpointImplClient extends WsRemoteEndpointImplBase {

    private final Log log = LogFactory.getLog(WsRemoteEndpointImplClient.class); // must not be static

    private final AsyncChannelWrapper channel;
    private final ReentrantLock lock = new ReentrantLock();

    public WsRemoteEndpointImplClient(AsyncChannelWrapper channel) {
        this.channel = channel;
    }


    @Override
    protected boolean isMasked() {
        return true;
    }


    @Override
    protected void doWrite(SendHandler handler, long blockingWriteTimeoutExpiry, ByteBuffer... data) {
        long timeout;
        for (ByteBuffer byteBuffer : data) {
            if (blockingWriteTimeoutExpiry == -1) {
                timeout = getSendTimeout();
                if (timeout < 1) {
                    timeout = Long.MAX_VALUE;
                }
            } else {
                timeout = blockingWriteTimeoutExpiry - System.currentTimeMillis();
                if (timeout < 0) {
                    SendResult sr = new SendResult(getSession(),
                            new IOException(sm.getString("wsRemoteEndpoint.writeTimeout")));
                    handler.onResult(sr);
                    return;
                }
            }

            try {
                channel.write(byteBuffer).get(timeout, TimeUnit.MILLISECONDS);
            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                log.warn(sm.getString("wsRemoteEndpointClient.writeFailed", Long.valueOf(blockingWriteTimeoutExpiry),
                        Long.valueOf(timeout)), e);
                handler.onResult(new SendResult(getSession(), e));
                return;
            }
        }
        handler.onResult(new SendResult(getSession()));
    }


    @Override
    protected void doClose() {
        channel.close();
    }


    @Override
    protected ReentrantLock getLock() {
        return lock;
    }
}
