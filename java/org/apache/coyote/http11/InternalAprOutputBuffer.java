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
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock.WriteLock;

import org.apache.coyote.ByteBufferHolder;
import org.apache.coyote.OutputBuffer;
import org.apache.coyote.Response;
import org.apache.tomcat.jni.Socket;
import org.apache.tomcat.jni.Status;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.net.AbstractEndpoint;
import org.apache.tomcat.util.net.AprEndpoint;
import org.apache.tomcat.util.net.SocketWrapper;

/**
 * Output buffer.
 *
 * @author <a href="mailto:remm@apache.org">Remy Maucherat</a>
 */
public class InternalAprOutputBuffer extends AbstractOutputBuffer<Long> {

    // ----------------------------------------------------------- Constructors

    /**
     * Default constructor.
     */
    public InternalAprOutputBuffer(Response response, int headerBufferSize) {

        super(response, headerBufferSize);

        if (headerBufferSize < (8 * 1024)) {
            bbuf = ByteBuffer.allocateDirect(6 * 1500);
        } else {
            bbuf = ByteBuffer.allocateDirect((headerBufferSize / 1500 + 1) * 1500);
        }

        outputStreamOutputBuffer = new SocketOutputBuffer();
    }


    // ----------------------------------------------------- Instance Variables


    /**
     * Underlying socket.
     */
    private long socket;


    private SocketWrapper<Long> wrapper;


    /**
     * Direct byte buffer used for writing.
     */
    private final ByteBuffer bbuf;


    /**
     * <code>false</code> if bbuf is ready to be written to and
     * <code>true</code> is ready to be read from.
     */
    private volatile boolean flipped = false;


    private AbstractEndpoint<Long> endpoint;


    // --------------------------------------------------------- Public Methods

    @Override
    public void init(SocketWrapper<Long> socketWrapper,
            AbstractEndpoint<Long> endpoint) throws IOException {

        wrapper = socketWrapper;
        socket = socketWrapper.getSocket().longValue();
        this.endpoint = endpoint;

        Socket.setsbb(this.socket, bbuf);
    }


    /**
     * Recycle the output buffer. This should be called when closing the
     * connection.
     */
    @Override
    public void recycle() {

        super.recycle();

        bbuf.clear();
        flipped = false;

        socket = 0;
        wrapper = null;
    }


    // ------------------------------------------------ HTTP/1.1 Output Methods

    /**
     * Send an acknowledgment.
     */
    @Override
    public void sendAck() throws IOException {
        if (!committed) {
            if (Socket.send(socket, Constants.ACK_BYTES, 0, Constants.ACK_BYTES.length) < 0)
                throw new IOException(sm.getString("iob.failedwrite.ack"));
        }
    }


    // ------------------------------------------------------ Protected Methods


    /**
     * Commit the response.
     *
     * @throws IOException an underlying I/O error occurred
     */
    @Override
    protected void commit() throws IOException {

        // The response is now committed
        committed = true;
        response.setCommitted(true);

        if (pos > 0) {
            // Sending the response header buffer
            bbuf.put(headerBuffer, 0, pos);
        }

    }


    private synchronized void addToBB(byte[] buf, int offset, int length)
            throws IOException {

        if (length == 0) return;

        // If bbuf is currently being used for writes, add this data to the
        // write buffer
        if (flipped) {
            addToBuffers(buf, offset, length);
            return;
        }

        // Keep writing until all the data is written or a non-blocking write
        // leaves data in the buffer
        while (length > 0) {
            int thisTime = length;
            if (bbuf.position() == bbuf.capacity()) {
                if (flushBuffer(isBlocking())) {
                    break;
                }
            }
            if (thisTime > bbuf.capacity() - bbuf.position()) {
                thisTime = bbuf.capacity() - bbuf.position();
            }
            bbuf.put(buf, offset, thisTime);
            length = length - thisTime;
            offset = offset + thisTime;
        }

        wrapper.access();

        if (!isBlocking() && length>0) {
            // Buffer the remaining data
            addToBuffers(buf, offset, length);
        }
    }


    private void addToBuffers(byte[] buf, int offset, int length) {
        ByteBufferHolder holder = bufferedWrites.peekLast();
        if (holder==null || holder.isFlipped() || holder.getBuf().remaining()<length) {
            ByteBuffer buffer = ByteBuffer.allocate(Math.max(bufferedWriteSize,length));
            holder = new ByteBufferHolder(buffer,false);
            bufferedWrites.add(holder);
        }
        holder.getBuf().put(buf,offset,length);
    }


    @Override
    protected synchronized boolean flushBuffer(boolean block)
            throws IOException {

        wrapper.access();

        if (hasMoreDataToFlush()) {
            writeToSocket(block);
        }

        if (bufferedWrites.size() > 0) {
            Iterator<ByteBufferHolder> bufIter = bufferedWrites.iterator();
            while (!hasMoreDataToFlush() && bufIter.hasNext()) {
                ByteBufferHolder buffer = bufIter.next();
                buffer.flip();
                while (!hasMoreDataToFlush() && buffer.getBuf().remaining()>0) {
                    transfer(buffer.getBuf(), bbuf);
                    if (buffer.getBuf().remaining() == 0) {
                        bufIter.remove();
                    }
                    writeToSocket(block);
                    //here we must break if we didn't finish the write
                }
            }
        }

        return hasMoreDataToFlush();
    }


    private synchronized void writeToSocket(boolean block) throws IOException {

        Lock readLock = wrapper.getBlockingStatusReadLock();
        WriteLock writeLock = wrapper.getBlockingStatusWriteLock();

        readLock.lock();
        try {
            if (wrapper.getBlockingStatus() == block) {
                writeToSocket();
                return;
            }
        } finally {
            readLock.unlock();
        }

        writeLock.lock();
        try {
            // Set the current settings for this socket
            wrapper.setBlockingStatus(block);
            if (block) {
                Socket.timeoutSet(socket, endpoint.getSoTimeout() * 1000);
            } else {
                Socket.timeoutSet(socket, 0);
            }

            // Downgrade the lock
            readLock.lock();
            try {
                writeLock.unlock();
                writeToSocket();
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

    private synchronized void writeToSocket() throws IOException {
        if (!flipped) {
            flipped = true;
            bbuf.flip();
        }

        int written;

        do {
            written = Socket.sendbb(socket, bbuf.position(), bbuf.remaining());
            if (Status.APR_STATUS_IS_EAGAIN(-written)) {
                written = 0;
            } else if (written < 0) {
                throw new IOException("APR error: " + written);
            }
            bbuf.position(bbuf.position() + written);
        } while (written > 0 && bbuf.hasRemaining());

        if (bbuf.remaining() == 0) {
            bbuf.clear();
            flipped = false;
        }
        // If there is data left in the buffer the socket will be registered for
        // write further up the stack. This is to ensure the socket is only
        // registered for write once as both container and user code can trigger
        // write registration.
    }


    private void transfer(ByteBuffer from, ByteBuffer to) {
        int max = Math.min(from.remaining(), to.remaining());
        int fromLimit = from.limit();
        from.limit(from.position() + max);
        to.put(from);
        from.limit(fromLimit);
    }


    //-------------------------------------------------- Non-blocking IO methods

    @Override
    protected synchronized boolean hasMoreDataToFlush() {
        return (flipped && bbuf.remaining() > 0) ||
                (!flipped && bbuf.position() > 0);
    }


    @Override
    protected void registerWriteInterest() {
        ((AprEndpoint) endpoint).getPoller().add(socket, -1, false, true);
    }


    // ----------------------------------- OutputStreamOutputBuffer Inner Class

    /**
     * This class is an output buffer which will write data to an output
     * stream.
     */
    protected class SocketOutputBuffer implements OutputBuffer {


        /**
         * Write chunk.
         */
        @Override
        public int doWrite(ByteChunk chunk, Response res) throws IOException {

            int len = chunk.getLength();
            int start = chunk.getStart();
            byte[] b = chunk.getBuffer();
            addToBB(b, start, len);
            byteCount += chunk.getLength();
            return chunk.getLength();
        }

        @Override
        public long getBytesWritten() {
            return byteCount;
        }
    }
}
