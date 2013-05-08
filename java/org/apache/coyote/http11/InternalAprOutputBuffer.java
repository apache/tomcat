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

import org.apache.coyote.OutputBuffer;
import org.apache.coyote.Response;
import org.apache.tomcat.jni.Socket;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.http.HttpMessages;
import org.apache.tomcat.util.net.AbstractEndpoint;
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

        this.response = response;

        headerBuffer = new byte[headerBufferSize];
        if (headerBufferSize < (8 * 1024)) {
            bbuf = ByteBuffer.allocateDirect(6 * 1500);
        } else {
            bbuf = ByteBuffer.allocateDirect((headerBufferSize / 1500 + 1) * 1500);
        }

        outputStreamOutputBuffer = new SocketOutputBuffer();

        filterLibrary = new OutputFilter[0];
        activeFilters = new OutputFilter[0];
        lastActiveFilter = -1;

        committed = false;
        finished = false;

        // Cause loading of HttpMessages
        HttpMessages.getMessage(200);

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


    // --------------------------------------------------------- Public Methods

    @Override
    public void init(SocketWrapper<Long> socketWrapper,
            AbstractEndpoint endpoint) throws IOException {

        wrapper = socketWrapper;
        socket = socketWrapper.getSocket().longValue();
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
        wrapper = null;
    }


    // ------------------------------------------------ HTTP/1.1 Output Methods

    /**
     * Send an acknowledgment.
     */
    @Override
    public void sendAck()
        throws IOException {

        if (!committed) {
            if (Socket.send(socket, Constants.ACK_BYTES, 0, Constants.ACK_BYTES.length) < 0)
                throw new IOException(sm.getString("iib.failedwrite"));
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

        // Try to flush any data in the socket's write buffer first
        boolean dataLeft = flushBuffer(isBlocking());

        // Keep writing until all the data is written or a non-blocking write
        // leaves data in the buffer
        while (!dataLeft && length > 0) {
            int thisTime = length;
            if (bbuf.position() == bbuf.capacity()) {
                flushBuffer(isBlocking());
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


    /**
     * Callback to write data from the buffer.
     */
    @Override
    protected boolean flushBuffer(boolean block) throws IOException {

        wrapper.access();

        boolean dataLeft = hasMoreDataToFlush();

        if (dataLeft) {
            writeToSocket();
        }

        if (!dataLeft && bufferedWrites!=null) {
            Iterator<ByteBufferHolder> bufIter = bufferedWrites.iterator();
            while (!hasMoreDataToFlush() && bufIter.hasNext()) {
                ByteBufferHolder buffer = bufIter.next();
                buffer.flip();
                while (!hasMoreDataToFlush() && buffer.getBuf().remaining()>0) {
                    transfer(buffer.getBuf(), bbuf);
                    if (buffer.getBuf().remaining() == 0) {
                        bufIter.remove();
                    }
                    writeToSocket();
                    //here we must break if we didn't finish the write
                }
            }
        }

        dataLeft = hasMoreDataToFlush();

        return hasMoreDataToFlush();
    }


    private void writeToSocket() throws IOException {
        // TODO Implement non-blocking writes
        if (Socket.sendbb(socket, 0, bbuf.position()) < 0) {
            throw new IOException();
        }
        bbuf.clear();

    }


    private void transfer(ByteBuffer from, ByteBuffer to) {
        int max = Math.min(from.remaining(), to.remaining());
        ByteBuffer tmp = from.duplicate ();
        tmp.limit (tmp.position() + max);
        to.put (tmp);
        from.position(from.position() + max);
    }


    //-------------------------------------------------- Non-blocking IO methods

    @Override
    protected boolean hasMoreDataToFlush() {
        return bbuf.position() > 0;
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
            addToBB(b, start,len);
            byteCount += chunk.getLength();
            return chunk.getLength();
        }

        @Override
        public long getBytesWritten() {
            return byteCount;
        }
    }
}
