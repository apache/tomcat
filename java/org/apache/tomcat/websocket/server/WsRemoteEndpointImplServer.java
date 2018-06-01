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
import java.nio.channels.InterruptedByTimeoutException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import javax.websocket.SendHandler;
import javax.websocket.SendResult;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.net.SocketWrapperBase;
import org.apache.tomcat.util.net.SocketWrapperBase.BlockingMode;
import org.apache.tomcat.util.res.StringManager;
import org.apache.tomcat.websocket.Transformation;
import org.apache.tomcat.websocket.WsRemoteEndpointImplBase;

/**
 * This is the server side {@link javax.websocket.RemoteEndpoint} implementation
 * - i.e. what the server uses to send data to the client.
 */
public class WsRemoteEndpointImplServer extends WsRemoteEndpointImplBase {

    private static final StringManager sm =
            StringManager.getManager(WsRemoteEndpointImplServer.class);
    private final Log log = LogFactory.getLog(WsRemoteEndpointImplServer.class); // must not be static

    private final SocketWrapperBase<?> socketWrapper;
    private final WsWriteTimeout wsWriteTimeout;
    private volatile SendHandler handler = null;
    private volatile ByteBuffer[] buffers = null;

    private volatile long timeoutExpiry = -1;
    private volatile boolean close;

    public WsRemoteEndpointImplServer(SocketWrapperBase<?> socketWrapper,
            WsServerContainer serverContainer) {
        this.socketWrapper = socketWrapper;
        this.wsWriteTimeout = serverContainer.getTimeout();
    }


    @Override
    protected final boolean isMasked() {
        return false;
    }

    @Override
    protected void doWrite(SendHandler handler, long blockingWriteTimeoutExpiry,
            ByteBuffer... buffers) {
        if (socketWrapper.hasAsyncIO()) {
            final boolean block = (blockingWriteTimeoutExpiry != -1);
            long timeout = -1;
            if (block) {
                timeout = blockingWriteTimeoutExpiry - System.currentTimeMillis();
                if (timeout <= 0) {
                    SendResult sr = new SendResult(new SocketTimeoutException());
                    handler.onResult(sr);
                    return;
                }
            } else {
                this.handler = handler;
                if (timeout > 0) {
                    // Register with timeout thread
                    timeoutExpiry = timeout + System.currentTimeMillis();
                    wsWriteTimeout.register(this);
                }
                timeout = getSendTimeout();
            }
            socketWrapper.write(block ? BlockingMode.BLOCK : BlockingMode.SEMI_BLOCK, timeout,
                    TimeUnit.MILLISECONDS, null, SocketWrapperBase.COMPLETE_WRITE_WITH_COMPLETION,
                    new CompletionHandler<Long, Void>() {
                        @Override
                        public void completed(Long result, Void attachment) {
                            if (block) {
                                long timeout = blockingWriteTimeoutExpiry - System.currentTimeMillis();
                                if (timeout <= 0) {
                                    failed(new SocketTimeoutException(), null);
                                } else {
                                    handler.onResult(SENDRESULT_OK);
                                }
                            } else {
                                wsWriteTimeout.unregister(WsRemoteEndpointImplServer.this);
                                clearHandler(null, true);
                                if (close) {
                                    close();
                                }
                            }
                        }
                        @Override
                        public void failed(Throwable exc, Void attachment) {
                            if (exc instanceof InterruptedByTimeoutException) {
                                exc = new SocketTimeoutException();
                            }
                            if (block) {
                                SendResult sr = new SendResult(exc);
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
                            SendResult sr = new SendResult(new SocketTimeoutException());
                            handler.onResult(sr);
                            return;
                        }
                        socketWrapper.setWriteTimeout(timeout);
                        socketWrapper.write(true, buffer);
                    }
                    long timeout = blockingWriteTimeoutExpiry - System.currentTimeMillis();
                    if (timeout <= 0) {
                        SendResult sr = new SendResult(new SocketTimeoutException());
                        handler.onResult(sr);
                        return;
                    }
                    socketWrapper.setWriteTimeout(timeout);
                    socketWrapper.flush(true);
                    handler.onResult(SENDRESULT_OK);
                } catch (IOException e) {
                    SendResult sr = new SendResult(e);
                    handler.onResult(sr);
                }
            }
        }
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
                        if (close) {
                            close();
                        }
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


    /*
     * Currently this is only called from the background thread so we could just
     * call clearHandler() with useDispatch == false but the method parameter
     * was added in case other callers started to use this method to make sure
     * that those callers think through what the correct value of useDispatch is
     * for them.
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
     *
     * @param t             The throwable associated with any error that
     *                      occurred
     * @param useDispatch   Should {@link SendHandler#onResult(SendResult)} be
     *                      called from a new thread, keeping in mind the
     *                      requirements of
     *                      {@link javax.websocket.RemoteEndpoint.Async}
     */
    private void clearHandler(Throwable t, boolean useDispatch) {
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
                OnResultRunnable r = new OnResultRunnable(sh, t);
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
                    sh.onResult(new SendResult());
                } else {
                    sh.onResult(new SendResult(t));
                }
            }
        }
    }


    private static class OnResultRunnable implements Runnable {

        private final SendHandler sh;
        private final Throwable t;

        private OnResultRunnable(SendHandler sh, Throwable t) {
            this.sh = sh;
            this.t = t;
        }

        @Override
        public void run() {
            if (t == null) {
                sh.onResult(new SendResult());
            } else {
                sh.onResult(new SendResult(t));
            }
        }
    }
}
