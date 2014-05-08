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
package org.apache.coyote.http11;

import java.io.EOFException;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.channels.CompletionHandler;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.servlet.RequestDispatcher;

import org.apache.coyote.InputBuffer;
import org.apache.coyote.Request;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.net.AbstractEndpoint;
import org.apache.tomcat.util.net.Nio2Channel;
import org.apache.tomcat.util.net.Nio2Endpoint;
import org.apache.tomcat.util.net.SocketStatus;
import org.apache.tomcat.util.net.SocketWrapper;

/**
 * Output buffer implementation for NIO2.
 */
public class InternalNio2InputBuffer extends AbstractNioInputBuffer<Nio2Channel> {

    private static final Log log =
            LogFactory.getLog(InternalNio2InputBuffer.class);

    // ----------------------------------------------------------- Constructors


    public InternalNio2InputBuffer(Request request, int headerBufferSize) {
        super(request, headerBufferSize);
        inputStreamInputBuffer = new SocketInputBuffer();
    }

    /**
     * Underlying socket.
     */
    private SocketWrapper<Nio2Channel> socket;

    /**
     * Track write interest
     */
    protected volatile boolean interest = false;

    /**
     * The completion handler used for asynchronous read operations
     */
    private CompletionHandler<Integer, SocketWrapper<Nio2Channel>> completionHandler;

    /**
     * The associated endpoint.
     */
    protected AbstractEndpoint<Nio2Channel> endpoint = null;

    /**
     * Read pending flag.
     */
    protected volatile boolean readPending = false;

    /**
     * Exception that occurred during writing.
     */
    protected IOException e = null;

    /**
     * Track if the byte buffer is flipped
     */
    protected volatile boolean flipped = false;

    // --------------------------------------------------------- Public Methods

    @Override
    protected final Log getLog() {
        return log;
    }


    /**
     * Recycle the input buffer. This should be called when closing the
     * connection.
     */
    @Override
    public void recycle() {
        super.recycle();
        socket = null;
        readPending = false;
        flipped = false;
        interest = false;
        e = null;
    }


    /**
     * End processing of current HTTP request.
     * Note: All bytes of the current request should have been already
     * consumed. This method only resets all the pointers so that we are ready
     * to parse the next HTTP request.
     */
    @Override
    public void nextRequest() {
        super.nextRequest();
        interest = false;
    }

    public boolean isPending() {
        return readPending;
    }

    // ------------------------------------------------------ Protected Methods

    @Override
    protected void init(SocketWrapper<Nio2Channel> socketWrapper,
            AbstractEndpoint<Nio2Channel> associatedEndpoint) throws IOException {

        endpoint = associatedEndpoint;
        socket = socketWrapper;
        if (socket == null) {
            // Socket has been closed in another thread
            throw new IOException(sm.getString("iib.socketClosed"));
        }
        socketReadBufferSize =
            socket.getSocket().getBufHandler().getReadBuffer().capacity();

        int bufLength = headerBufferSize + socketReadBufferSize;
        if (buf == null || buf.length < bufLength) {
            buf = new byte[bufLength];
        }

        // Initialize the completion handler
        this.completionHandler = new CompletionHandler<Integer, SocketWrapper<Nio2Channel>>() {

            @Override
            public void completed(Integer nBytes, SocketWrapper<Nio2Channel> attachment) {
                boolean notify = false;
                synchronized (completionHandler) {
                    if (nBytes.intValue() < 0) {
                        failed(new EOFException(sm.getString("iib.eof.error")), attachment);
                    } else {
                        readPending = false;
                        if ((request.getReadListener() == null || interest) && !Nio2Endpoint.isInline()) {
                            interest = false;
                            notify = true;
                        }
                    }
                }
                if (notify) {
                    endpoint.processSocket(attachment, SocketStatus.OPEN_READ, false);
                }
            }

            @Override
            public void failed(Throwable exc, SocketWrapper<Nio2Channel> attachment) {
                attachment.setError(true);
                if (exc instanceof IOException) {
                    e = (IOException) exc;
                } else {
                    e = new IOException(exc);
                }
                request.setAttribute(RequestDispatcher.ERROR_EXCEPTION, e);
                readPending = false;
                endpoint.processSocket(attachment, SocketStatus.OPEN_READ, true);
            }
        };
    }

    @Override
    protected boolean fill(boolean block) throws IOException, EOFException {
        if (e != null) {
            throw e;
        }
        if (parsingHeader) {
            if (lastValid > headerBufferSize) {
                throw new IllegalArgumentException
                    (sm.getString("iib.requestheadertoolarge.error"));
            }
        } else {
            lastValid = pos = end;
        }
        // Now fill the internal buffer
        int nRead = 0;
        ByteBuffer byteBuffer = socket.getSocket().getBufHandler().getReadBuffer();
        if (block) {
            if (!flipped) {
                byteBuffer.flip();
                flipped = true;
            }
            int nBytes = byteBuffer.remaining();
            // This case can happen when a blocking read follows a non blocking
            // fill that completed asynchronously
            if (nBytes > 0) {
                expand(nBytes + pos);
                byteBuffer.get(buf, pos, nBytes);
                lastValid = pos + nBytes;
                byteBuffer.clear();
                flipped = false;
                return true;
            } else {
                byteBuffer.clear();
                flipped = false;
                try {
                    nRead = socket.getSocket().read(byteBuffer)
                            .get(socket.getTimeout(), TimeUnit.MILLISECONDS).intValue();
                } catch (ExecutionException e) {
                    if (e.getCause() instanceof IOException) {
                        throw (IOException) e.getCause();
                    } else {
                        throw new IOException(e);
                    }
                } catch (InterruptedException e) {
                    throw new IOException(e);
                } catch (TimeoutException e) {
                    throw new SocketTimeoutException();
                }
                if (nRead > 0) {
                    if (!flipped) {
                        byteBuffer.flip();
                        flipped = true;
                    }
                    expand(nRead + pos);
                    byteBuffer.get(buf, pos, nRead);
                    lastValid = pos + nRead;
                    return true;
                } else if (nRead == -1) {
                    //return false;
                    throw new EOFException(sm.getString("iib.eof.error"));
                } else {
                    return false;
                }
            }
        } else {
            synchronized (completionHandler) {
                if (!readPending) {
                    if (!flipped) {
                        byteBuffer.flip();
                        flipped = true;
                    }
                    int nBytes = byteBuffer.remaining();
                    if (nBytes > 0) {
                        expand(nBytes + pos);
                        byteBuffer.get(buf, pos, nBytes);
                        lastValid = pos + nBytes;
                        byteBuffer.clear();
                        flipped = false;
                    } else {
                        byteBuffer.clear();
                        flipped = false;
                        readPending = true;
                        Nio2Endpoint.startInline();
                        socket.getSocket().read(byteBuffer, socket.getTimeout(),
                                    TimeUnit.MILLISECONDS, socket, completionHandler);
                        Nio2Endpoint.endInline();
                        // Return the number of bytes that have been placed into the buffer
                        if (!readPending) {
                            // If the completion handler completed immediately
                            if (!flipped) {
                                byteBuffer.flip();
                                flipped = true;
                            }
                            nBytes = byteBuffer.remaining();
                            if (nBytes > 0) {
                                expand(nBytes + pos);
                                byteBuffer.get(buf, pos, nBytes);
                                lastValid = pos + nBytes;
                            }
                            byteBuffer.clear();
                            flipped = false;
                        }
                    }
                    return (lastValid - pos) > 0;
                } else {
                    return false;
                }
            }
        }
    }


    public void registerReadInterest() {
        synchronized (completionHandler) {
            if (readPending) {
                interest = true;
            } else {
                // If no read is pending, notify
                endpoint.processSocket(socket, SocketStatus.OPEN_READ, true);
            }
        }
    }


    // ------------------------------------- InputStreamInputBuffer Inner Class


    /**
     * This class is an input buffer which will read its data from an input
     * stream.
     */
    protected class SocketInputBuffer
        implements InputBuffer {


        /**
         * Read bytes into the specified chunk.
         */
        @Override
        public int doRead(ByteChunk chunk, Request req )
            throws IOException {

            if (pos >= lastValid) {
                if (!fill(true)) //read body, must be blocking, as the thread is inside the app
                    return -1;
            }
            if (isBlocking()) {
                int length = lastValid - pos;
                chunk.setBytes(buf, pos, length);
                pos = lastValid;
                return (length);
            } else {
                synchronized (completionHandler) {
                    int length = lastValid - pos;
                    chunk.setBytes(buf, pos, length);
                    pos = lastValid;
                    return (length);
                }
            }
        }
    }
}
