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

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.net.AbstractEndpoint.Handler.SocketState;
import org.apache.tomcat.util.net.SocketEvent;
import org.apache.tomcat.util.net.SocketWrapperBase;
import org.apache.tomcat.util.res.StringManager;
import org.apache.tomcat.websocket.Transformation;
import org.apache.tomcat.websocket.WsFrameBase;
import org.apache.tomcat.websocket.WsIOException;
import org.apache.tomcat.websocket.WsSession;

public class WsFrameServer extends WsFrameBase {

    private final Log log = LogFactory.getLog(WsFrameServer.class); // must not be static
    private static final StringManager sm = StringManager.getManager(WsFrameServer.class);

    private final SocketWrapperBase<?> socketWrapper;
    private final ClassLoader applicationClassLoader;


    public WsFrameServer(SocketWrapperBase<?> socketWrapper, WsSession wsSession,
            Transformation transformation, ClassLoader applicationClassLoader) {
        super(wsSession, transformation);
        this.socketWrapper = socketWrapper;
        this.applicationClassLoader = applicationClassLoader;
    }


    /**
     * Called when there is data in the ServletInputStream to process.
     *
     * @throws IOException if an I/O error occurs while processing the available
     *                     data
     */
    private void onDataAvailable() throws IOException {
        if (log.isDebugEnabled()) {
            log.debug("wsFrameServer.onDataAvailable");
        }
        if (isOpen() && inputBuffer.hasRemaining() && !isSuspended()) {
            // There might be a data that was left in the buffer when
            // the read has been suspended.
            // Consume this data before reading from the socket.
            processInputBuffer();
        }

        while (isOpen() && !isSuspended()) {
            // Fill up the input buffer with as much data as we can
            inputBuffer.mark();
            inputBuffer.position(inputBuffer.limit()).limit(inputBuffer.capacity());
            int read = socketWrapper.read(false, inputBuffer);
            inputBuffer.limit(inputBuffer.position()).reset();
            if (read < 0) {
                throw new EOFException();
            } else if (read == 0) {
                return;
            }
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("wsFrameServer.bytesRead", Integer.toString(read)));
            }
            processInputBuffer();
        }
    }


    @Override
    protected boolean isMasked() {
        // Data is from the client so it should be masked
        return true;
    }


    @Override
    protected Transformation getTransformation() {
        // Overridden to make it visible to other classes in this package
        return super.getTransformation();
    }


    @Override
    protected boolean isOpen() {
        // Overridden to make it visible to other classes in this package
        return super.isOpen();
    }


    @Override
    protected Log getLog() {
        return log;
    }


    @Override
    protected void sendMessageText(boolean last) throws WsIOException {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(applicationClassLoader);
            super.sendMessageText(last);
        } finally {
            Thread.currentThread().setContextClassLoader(cl);
        }
    }


    @Override
    protected void sendMessageBinary(ByteBuffer msg, boolean last) throws WsIOException {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(applicationClassLoader);
            super.sendMessageBinary(msg, last);
        } finally {
            Thread.currentThread().setContextClassLoader(cl);
        }
    }


    @Override
    protected void resumeProcessing() {
        socketWrapper.processSocket(SocketEvent.OPEN_READ, true);
    }

    SocketState notifyDataAvailable() throws IOException {
        while (isOpen()) {
            switch (getReadState()) {
            case WAITING:
                if (!changeReadState(ReadState.WAITING, ReadState.PROCESSING)) {
                    continue;
                }
                try {
                    return doOnDataAvailable();
                } catch (IOException e) {
                    changeReadState(ReadState.CLOSING);
                    throw e;
                }
            case SUSPENDING_WAIT:
                if (!changeReadState(ReadState.SUSPENDING_WAIT, ReadState.SUSPENDED)) {
                    continue;
                }
                return SocketState.SUSPENDED;
            default:
                throw new IllegalStateException(
                        sm.getString("wsFrameServer.illegalReadState", getReadState()));
            }
        }

        return SocketState.CLOSED;
    }

    private SocketState doOnDataAvailable() throws IOException {
        onDataAvailable();
        while (isOpen()) {
            switch (getReadState()) {
            case PROCESSING:
                if (!changeReadState(ReadState.PROCESSING, ReadState.WAITING)) {
                    continue;
                }
                return SocketState.UPGRADED;
            case SUSPENDING_PROCESS:
                if (!changeReadState(ReadState.SUSPENDING_PROCESS, ReadState.SUSPENDED)) {
                    continue;
                }
                return SocketState.SUSPENDED;
            default:
                throw new IllegalStateException(
                        sm.getString("wsFrameServer.illegalReadState", getReadState()));
            }
        }

        return SocketState.CLOSED;
    }
}
