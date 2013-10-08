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

import java.io.EOFException;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;

import javax.websocket.SendHandler;
import javax.websocket.SendResult;

import org.apache.coyote.http11.upgrade.AbstractServletOutputStream;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.res.StringManager;
import org.apache.tomcat.websocket.WsRemoteEndpointImplBase;

/**
 * This is the server side {@link javax.websocket.RemoteEndpoint} implementation
 * - i.e. what the server uses to send data to the client. Communication is over
 * a {@link javax.servlet.ServletOutputStream}.
 */
public class WsRemoteEndpointImplServer extends WsRemoteEndpointImplBase {

    private static final StringManager sm =
            StringManager.getManager(Constants.PACKAGE_NAME);
    private static final Log log =
            LogFactory.getLog(WsHttpUpgradeHandler.class);

    private final AbstractServletOutputStream sos;
    private final WsWriteTimeout wsWriteTimeout;
    private volatile SendHandler handler = null;
    private volatile ByteBuffer[] buffers = null;

    private volatile long timeoutExpiry = -1;
    private volatile boolean close;


    public WsRemoteEndpointImplServer(AbstractServletOutputStream sos,
            WsServerContainer serverContainer) {
        this.sos = sos;
        this.wsWriteTimeout = serverContainer.getTimeout();
    }


    @Override
    protected final boolean isMasked() {
        return false;
    }


    @Override
    protected void doWrite(SendHandler handler, ByteBuffer... buffers) {
        this.handler = handler;
        this.buffers = buffers;
        onWritePossible();
    }


    public void onWritePossible() {
        boolean complete = true;
        try {
            // If this is false there will be a call back when it is true
            while (sos.isReady()) {
                complete = true;
                for (ByteBuffer buffer : buffers) {
                    if (buffer.hasRemaining()) {
                        complete = false;
                        sos.write(buffer.array(), buffer.arrayOffset(),
                                buffer.limit());
                        buffer.position(buffer.limit());
                        break;
                    }
                }
                if (complete) {
                    wsWriteTimeout.unregister(this);
                    clearHandler(null);
                    if (close) {
                        close();
                    }
                    break;
                }
            }

        } catch (IOException ioe) {
            wsWriteTimeout.unregister(this);
            clearHandler(ioe);
            close();
        }
        if (!complete) {
            // Async write is in progress

            long timeout = getSendTimeout();
            if (timeout > 0) {
                // Register with timeout thread
                timeoutExpiry = timeout + System.currentTimeMillis();
                wsWriteTimeout.register(this);
            }
        }
    }


    @Override
    protected void doClose() {
        if (handler != null) {
            clearHandler(new EOFException());
        }
        try {
            sos.close();
        } catch (IOException e) {
            if (log.isInfoEnabled()) {
                log.info(sm.getString("wsRemoteEndpointServer.closeFailed"), e);
            }
        }
        wsWriteTimeout.unregister(this);
    }


    protected long getTimeoutExpiry() {
        return timeoutExpiry;
    }


    protected void onTimeout() {
        if (handler != null) {
            clearHandler(new SocketTimeoutException());
        }
        close();
    }


    private void clearHandler(Throwable t) {
        // Setting the result marks this (partial) message as
        // complete which means the next one may be sent which
        // could update the value of the handler. Therefore, keep a
        // local copy before signalling the end of the (partial)
        // message.
        SendHandler sh = handler;
        handler = null;
        if (sh != null) {
            if (t == null) {
                sh.onResult(new SendResult());
            } else {
                sh.onResult(new SendResult(t));
            }
        }
    }
}
