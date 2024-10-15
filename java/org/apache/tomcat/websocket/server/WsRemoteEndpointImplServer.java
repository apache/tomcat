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
import java.nio.channels.CompletionHandler;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import jakarta.servlet.http.WebConnection;
import jakarta.websocket.SendHandler;
import jakarta.websocket.SendResult;

import org.apache.coyote.http11.upgrade.UpgradeInfo;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.net.SocketWrapperBase;
import org.apache.tomcat.util.net.SocketWrapperBase.BlockingMode;
import org.apache.tomcat.util.res.StringManager;
import org.apache.tomcat.websocket.Constants;
import org.apache.tomcat.websocket.Transformation;
import org.apache.tomcat.websocket.WsRemoteEndpointImplBase;
import org.apache.tomcat.websocket.WsSession;

/**
 * This is the server side {@link jakarta.websocket.RemoteEndpoint} implementation - i.e. what the server uses to send
 * data to the client.
 */
public class WsRemoteEndpointImplServer extends WsRemoteEndpointImplBase {

    private static final StringManager sm = StringManager.getManager(WsRemoteEndpointImplServer.class);
    private final Log log = LogFactory.getLog(WsRemoteEndpointImplServer.class); // must not be static

    private final SocketWrapperBase<?> socketWrapper;
    private final UpgradeInfo upgradeInfo;
    private final WebConnection connection;
    private final WsWriteTimeout wsWriteTimeout;
    private volatile SendHandler handler = null;
    private volatile ByteBuffer[] buffers = null;

    private volatile long timeoutExpiry = -1;

    public WsRemoteEndpointImplServer(SocketWrapperBase<?> socketWrapper, UpgradeInfo upgradeInfo,
            WsServerContainer serverContainer, WebConnection connection) {
        this.socketWrapper = socketWrapper;
        this.upgradeInfo = upgradeInfo;
        this.connection = connection;
        this.wsWriteTimeout = serverContainer.getTimeout();
    }


    @Override
    protected final boolean isMasked() {
        return false;
    }


    /**
     * {@inheritDoc}
     * <p>
     * The close message is a special case. It needs to be blocking else implementing the clean-up that follows the
     * sending of the close message gets a lot more complicated. On the server, this creates additional complications as
     * a dead-lock may occur in the following scenario:
     * <ol>
     * <li>Application thread writes message using non-blocking</li>
     * <li>Write does not complete (write logic holds message pending lock)</li>
     * <li>Socket is added to poller (or equivalent) for write
     * <li>Client sends close message</li>
     * <li>Container processes received close message and tries to send close message in response</li>
     * <li>Container holds socket lock and is blocked waiting for message pending lock</li>
     * <li>Poller fires write possible event for socket</li>
     * <li>Container tries to process write possible event but is blocked waiting for socket lock</li>
     * <li>Processing of the WebSocket connection is dead-locked until the original message write times out</li>
     * </ol>
     * The purpose of this method is to break the above dead-lock. It does this by returning control of the processor to
     * the socket wrapper and releasing the socket lock while waiting for the pending message write to complete.
     * Normally, that would be a terrible idea as it creates the possibility that the processor is returned to the pool
     * more than once under various error conditions. In this instance it is safe because these are upgrade processors
     * (isUpgrade() returns {@code true}) and upgrade processors are never pooled.
     * <p>
     * TODO: Despite the complications it creates, it would be worth exploring the possibility of processing a received
     * close frame in a non-blocking manner.
     */
    @Override
    protected boolean acquireMessagePartInProgressSemaphore(byte opCode, long timeoutExpiry)
            throws InterruptedException {

        /*
         * Special handling is required only when all of the following are true:
         * - A close message is being sent
         * - This thread currently holds the socketWrapper lock (i.e. the thread is current processing a socket event)
         */
        if (!(opCode == Constants.OPCODE_CLOSE && socketWrapper.getLock().isHeldByCurrentThread())) {
            // Skip special handling
            return super.acquireMessagePartInProgressSemaphore(opCode, timeoutExpiry);
        }

        int socketWrapperLockCount = socketWrapper.getLock().getHoldCount();
        while (!messagePartInProgress.tryAcquire()) {
            if (timeoutExpiry < System.currentTimeMillis()) {
                return false;
            }
            try {
                // Release control of the processor
                socketWrapper.setCurrentProcessor(connection);
                // Release the per socket lock(s)
                for (int i = 0; i < socketWrapperLockCount; i++) {
                    socketWrapper.getLock().unlock();
                }
                // Provide opportunity for another thread to obtain the socketWrapper lock
                Thread.yield();
            } finally {
                // Re-obtain the per socket lock(s)
                for (int i = 0; i < socketWrapperLockCount; i++) {
                    socketWrapper.getLock().lock();
                }
                // Re-take control of the processor
                socketWrapper.takeCurrentProcessor();
            }
        }

        return true;
    }


    @Override
    protected void doWrite(SendHandler handler, long blockingWriteTimeoutExpiry, ByteBuffer... buffers) {
        if (socketWrapper.hasAsyncIO()) {
            final boolean block = (blockingWriteTimeoutExpiry != -1);
            long timeout = -1;
            if (block) {
                timeout = blockingWriteTimeoutExpiry - System.currentTimeMillis();
                if (timeout <= 0) {
                    SendResult sr = new SendResult(getSession(), new SocketTimeoutException());
                    handler.onResult(sr);
                    return;
                }
            } else {
                this.handler = handler;
                timeout = getSendTimeout();
                if (timeout > 0) {
                    // Register with timeout thread
                    timeoutExpiry = timeout + System.currentTimeMillis();
                    wsWriteTimeout.register(this);
                }
            }
            socketWrapper.write(block ? BlockingMode.BLOCK : BlockingMode.SEMI_BLOCK, timeout, TimeUnit.MILLISECONDS,
                    null, SocketWrapperBase.COMPLETE_WRITE_WITH_COMPLETION, new CompletionHandler<Long, Void>() {
                        @Override
                        public void completed(Long result, Void attachment) {
                            if (block) {
                                long timeout = blockingWriteTimeoutExpiry - System.currentTimeMillis();
                                if (timeout <= 0) {
                                    failed(new SocketTimeoutException(), null);
                                } else {
                                    handler.onResult(new SendResult(getSession()));
                                }
                            } else {
                                wsWriteTimeout.unregister(WsRemoteEndpointImplServer.this);
                                clearHandler(null, true);
                            }
                        }

                        @Override
                        public void failed(Throwable exc, Void attachment) {
                            if (block) {
                                SendResult sr = new SendResult(getSession(), exc);
                                handler.onResult(sr);
                            } else {
                                wsWriteTimeout.unregister(WsRemoteEndpointImplServer.this);
                                clearHandler(exc, true);
                                close();
                            }
                        }
                    }, buffers);
        } else {
            if (blockingWriteTimeoutExpiry == -1) {
                this.handler = handler;
                this.buffers = buffers;
                // This is definitely the same thread that triggered the write so a
                // dispatch will be required.
                onWritePossible(true);
            } else {
                // Blocking
                try {
                    for (ByteBuffer buffer : buffers) {
                        long timeout = blockingWriteTimeoutExpiry - System.currentTimeMillis();
                        if (timeout <= 0) {
                            SendResult sr = new SendResult(getSession(), new SocketTimeoutException());
                            handler.onResult(sr);
                            return;
                        }
                        socketWrapper.setWriteTimeout(timeout);
                        socketWrapper.write(true, buffer);
                    }
                    long timeout = blockingWriteTimeoutExpiry - System.currentTimeMillis();
                    if (timeout <= 0) {
                        SendResult sr = new SendResult(getSession(), new SocketTimeoutException());
                        handler.onResult(sr);
                        return;
                    }
                    socketWrapper.setWriteTimeout(timeout);
                    socketWrapper.flush(true);
                    handler.onResult(new SendResult(getSession()));
                } catch (IOException e) {
                    SendResult sr = new SendResult(getSession(), e);
                    handler.onResult(sr);
                }
            }
        }
    }


    @Override
    protected void updateStats(long payloadLength) {
        upgradeInfo.addMsgsSent(1);
        upgradeInfo.addBytesSent(payloadLength);
    }


    public void onWritePossible(boolean useDispatch) {
        // Note: Unused for async IO
        ByteBuffer[] buffers = this.buffers;
        if (buffers == null) {
            // Servlet 3.1 will call the write listener once even if nothing
            // was written
            return;
        }
        boolean complete = false;
        try {
            socketWrapper.flush(false);
            // If this is false there will be a call back when it is true
            while (socketWrapper.isReadyForWrite()) {
                complete = true;
                for (ByteBuffer buffer : buffers) {
                    if (buffer.hasRemaining()) {
                        complete = false;
                        socketWrapper.write(false, buffer);
                        break;
                    }
                }
                if (complete) {
                    socketWrapper.flush(false);
                    complete = socketWrapper.isReadyForWrite();
                    if (complete) {
                        wsWriteTimeout.unregister(this);
                        clearHandler(null, useDispatch);
                    }
                    break;
                }
            }
        } catch (IOException | IllegalStateException e) {
            wsWriteTimeout.unregister(this);
            clearHandler(e, useDispatch);
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
            // close() can be triggered by a wide range of scenarios. It is far
            // simpler just to always use a dispatch than it is to try and track
            // whether or not this method was called by the same thread that
            // triggered the write
            clearHandler(new EOFException(), true);
        }
        try {
            socketWrapper.close();
        } catch (Exception e) {
            if (log.isInfoEnabled()) {
                log.info(sm.getString("wsRemoteEndpointServer.closeFailed"), e);
            }
        }
        wsWriteTimeout.unregister(this);
    }


    protected long getTimeoutExpiry() {
        return timeoutExpiry;
    }


    /*
     * Currently this is only called from the background thread so we could just call clearHandler() with useDispatch ==
     * false but the method parameter was added in case other callers started to use this method to make sure that those
     * callers think through what the correct value of useDispatch is for them.
     */
    protected void onTimeout(boolean useDispatch) {
        if (handler != null) {
            clearHandler(new SocketTimeoutException(), useDispatch);
        }
        close();
    }


    @Override
    protected void setTransformation(Transformation transformation) {
        // Overridden purely so it is visible to other classes in this package
        super.setTransformation(transformation);
    }


    /**
     * @param t           The throwable associated with any error that occurred
     * @param useDispatch Should {@link SendHandler#onResult(SendResult)} be called from a new thread, keeping in mind
     *                        the requirements of {@link jakarta.websocket.RemoteEndpoint.Async}
     */
    void clearHandler(Throwable t, boolean useDispatch) {
        // Setting the result marks this (partial) message as
        // complete which means the next one may be sent which
        // could update the value of the handler. Therefore, keep a
        // local copy before signalling the end of the (partial)
        // message.
        SendHandler sh = handler;
        handler = null;
        buffers = null;
        if (sh != null) {
            if (useDispatch) {
                OnResultRunnable r = new OnResultRunnable(getSession(), sh, t);
                try {
                    socketWrapper.execute(r);
                } catch (RejectedExecutionException ree) {
                    // Can't use the executor so call the runnable directly.
                    // This may not be strictly specification compliant in all
                    // cases but during shutdown only close messages are going
                    // to be sent so there should not be the issue of nested
                    // calls leading to stack overflow as described in bug
                    // 55715. The issues with nested calls was the reason for
                    // the separate thread requirement in the specification.
                    r.run();
                }
            } else {
                if (t == null) {
                    sh.onResult(new SendResult(getSession()));
                } else {
                    sh.onResult(new SendResult(getSession(), t));
                }
            }
        }
    }


    @Override
    protected ReentrantLock getLock() {
        return socketWrapper.getLock();
    }


    private static class OnResultRunnable implements Runnable {

        private final WsSession session;
        private final SendHandler sh;
        private final Throwable t;

        private OnResultRunnable(WsSession session, SendHandler sh, Throwable t) {
            this.session = session;
            this.sh = sh;
            this.t = t;
        }

        @Override
        public void run() {
            if (t == null) {
                sh.onResult(new SendResult(session));
            } else {
                sh.onResult(new SendResult(session, t));
            }
        }
    }
}
