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

import java.io.IOException;
import java.net.SocketTimeoutException;

import javax.servlet.ServletOutputStream;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.res.StringManager;
import org.apache.tomcat.websocket.WsRemoteEndpointBase;

/**
 * This is the server side {@link javax.websocket.RemoteEndpoint} implementation
 * - i.e. what the server uses to send data to the client. Communication is over
 * a {@link ServletOutputStream}.
 */
public class WsRemoteEndpointServer extends WsRemoteEndpointBase {

    private static StringManager sm =
            StringManager.getManager(Constants.PACKAGE_NAME);
    private static final Log log =
            LogFactory.getLog(WsProtocolHandler.class);

    private final ServletOutputStream sos;
    private final WsTimeout wsTimeout;
    private volatile WsCompletionHandler handler = null;
    private volatile long timeoutExpiry = -1;
    private volatile boolean close;
    private volatile Long size = null;
    private volatile boolean headerWritten = false;
    private volatile boolean payloadWritten = false;


    public WsRemoteEndpointServer(ServletOutputStream sos,
            ServerContainerImpl serverContainer) {
        this.sos = sos;
        this.wsTimeout = serverContainer.getTimeout();
    }


    @Override
    protected byte getMasked() {
        // Messages from the server are not masked
        return 0;
    }


    @Override
    protected void sendMessage(WsCompletionHandler handler) {
        this.handler = handler;
        onWritePossible();
    }


    public void onWritePossible() {
        try {
            // If this is false there will be a call back when it is true
            while (sos.canWrite()) {
                if (!headerWritten) {
                    headerWritten = true;
                    size = Long.valueOf(
                            header.remaining() + payload.remaining());
                    sos.write(header.array(), header.arrayOffset(),
                            header.limit());
                } else if (!payloadWritten) {
                    payloadWritten = true;
                    sos.write(payload.array(), payload.arrayOffset(),
                            payload.limit());
                } else {
                    wsTimeout.unregister(this);
                    if (close) {
                        close();
                    }
                    handler.completed(size, null);
                    nextWrite();
                    break;
                }
            }
        } catch (IOException ioe) {
            wsTimeout.unregister(this);
            close();
            handler.failed(ioe, null);
            nextWrite();
        }
        if (handler != null) {
            // Async write is in progress

            long timeout = getAsyncSendTimeout();
            if (timeout > 0) {
                // Register with timeout thread
                timeoutExpiry = timeout + System.currentTimeMillis();
                wsTimeout.register(this);
            }
        }
    }


    @Override
    protected void close() {
        try {
            sos.close();
        } catch (IOException e) {
            if (log.isInfoEnabled()) {
                log.info(sm.getString("wsRemoteEndpointServer.closeFailed"), e);
            }
        }
        wsTimeout.unregister(this);
    }


    protected long getTimeoutExpiry() {
        return timeoutExpiry;
    }


    protected void onTimeout() {
        close();
        handler.failed(new SocketTimeoutException(), null);
        nextWrite();
    }


    private void nextWrite() {
        handler = null;
        size = null;
        headerWritten = false;
        payloadWritten = false;
    }
}
