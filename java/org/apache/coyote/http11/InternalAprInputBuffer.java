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

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock.WriteLock;

import org.apache.coyote.InputBuffer;
import org.apache.coyote.Request;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.jni.Socket;
import org.apache.tomcat.jni.Status;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.net.AbstractEndpoint;
import org.apache.tomcat.util.net.SocketWrapperBase;

/**
 * Implementation of InputBuffer which provides HTTP request header parsing as
 * well as transfer decoding.
 *
 * @author <a href="mailto:remm@apache.org">Remy Maucherat</a>
 */
public class InternalAprInputBuffer extends AbstractNioInputBuffer<Long> {

    private static final Log log =
        LogFactory.getLog(InternalAprInputBuffer.class);

    // ----------------------------------------------------------- Constructors


    /**
     * Alternate constructor.
     */
    public InternalAprInputBuffer(Request request, int headerBufferSize) {
        super(request, headerBufferSize);

        if (headerBufferSize < (8 * 1024)) {
            bbuf = ByteBuffer.allocateDirect(6 * 1500);
        } else {
            bbuf = ByteBuffer.allocateDirect((headerBufferSize / 1500 + 1) * 1500);
        }

        inputStreamInputBuffer = new SocketInputBuffer();
    }


    // ----------------------------------------------------- Instance Variables


    /**
     * Direct byte buffer used to perform actual reading.
     */
    private final ByteBuffer bbuf;


    /**
     * Underlying socket.
     */
    private long socket;


    private SocketWrapperBase<Long> wrapper;


    // --------------------------------------------------------- Public Methods

    /**
     * Recycle the input buffer. This should be called when closing the
     * connection.
     */
    @Override
    public void recycle() {
        socket = 0;
        wrapper = null;
        super.recycle();
    }


    // ------------------------------------------------------ Protected Methods

    @Override
    protected final Log getLog() {
        return log;
    }


    @Override
    protected void init(SocketWrapperBase<Long> socketWrapper,
            AbstractEndpoint<Long> endpoint) throws IOException {

        socket = socketWrapper.getSocket().longValue();
        wrapper = socketWrapper;

        int bufLength = headerBufferSize + bbuf.capacity();
        if (buf == null || buf.length < bufLength) {
            buf = new byte[bufLength];
        }

        Socket.setrbb(this.socket, bbuf);
    }


    @Override
    protected boolean fill(boolean block) throws IOException {

        int nRead = 0;

        if (parsingHeader) {
            if (lastValid == buf.length) {
                throw new IllegalArgumentException
                    (sm.getString("iib.requestheadertoolarge.error"));
            }
        } else {
            if (buf.length - end < 4500) {
                // In this case, the request header was really large, so we allocate a
                // brand new one; the old one will get GCed when subsequent requests
                // clear all references
                buf = new byte[buf.length];
                end = 0;
            }
            pos = end;
            lastValid = pos;
        }

        bbuf.clear();

        nRead = doReadSocket(block);
        if (nRead > 0) {
            bbuf.limit(nRead);
            bbuf.get(buf, pos, nRead);
            lastValid = pos + nRead;
        } else if (-nRead == Status.EAGAIN) {
            return false;
        } else if ((-nRead) == Status.ETIMEDOUT || (-nRead) == Status.TIMEUP) {
            if (block) {
                throw new SocketTimeoutException(
                        sm.getString("iib.readtimeout"));
            } else {
                // Attempting to read from the socket when the poller
                // has not signalled that there is data to read appears
                // to behave like a blocking read with a short timeout
                // on OSX rather than like a non-blocking read. If no
                // data is read, treat the resulting timeout like a
                // non-blocking read that returned no data.
                return false;
            }
        } else if (nRead == 0) {
            // APR_STATUS_IS_EOF, since native 1.1.22
            return false;
        } else {
            throw new IOException(sm.getString("iib.failedread.apr",
                    Integer.valueOf(-nRead)));
        }

        return (nRead > 0);
    }


    private int doReadSocket(boolean block) {

        Lock readLock = wrapper.getBlockingStatusReadLock();
        WriteLock writeLock = wrapper.getBlockingStatusWriteLock();

        boolean readDone = false;
        int result = 0;
        readLock.lock();
        try {
            if (wrapper.getBlockingStatus() == block) {
                result = Socket.recvbb(socket, 0, buf.length - lastValid);
                readDone = true;
            }
        } finally {
            readLock.unlock();
        }

        if (!readDone) {
            writeLock.lock();
            try {
                wrapper.setBlockingStatus(block);
                // Set the current settings for this socket
                if (block) {
                    Socket.optSet(socket, Socket.APR_SO_NONBLOCK, 0);
                } else {
                    Socket.optSet(socket, Socket.APR_SO_NONBLOCK, 1);
                    Socket.timeoutSet(socket, 0);
                }
                // Downgrade the lock
                readLock.lock();
                try {
                    writeLock.unlock();
                    result = Socket.recvbb(socket, 0, buf.length - lastValid);
                } finally {
                    readLock.unlock();
                }
            } finally {
                // Should have been released above but may not have been on some
                // exception paths
                if (writeLock.isHeldByCurrentThread()) {
                    writeLock.unlock();
                }
            }
        }

        return result;
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
                if (!fill(true))
                    return -1;
            }

            int length = lastValid - pos;
            chunk.setBytes(buf, pos, length);
            pos = lastValid;

            return (length);
        }
    }
}
