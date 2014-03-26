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
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Iterator;

import org.apache.coyote.ByteBufferHolder;
import org.apache.coyote.OutputBuffer;
import org.apache.coyote.Response;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.net.AbstractEndpoint;
import org.apache.tomcat.util.net.NioChannel;
import org.apache.tomcat.util.net.NioEndpoint;
import org.apache.tomcat.util.net.NioSelectorPool;
import org.apache.tomcat.util.net.SocketWrapper;

/**
 * Output buffer.
 *
 * @author <a href="mailto:remm@apache.org">Remy Maucherat</a>
 */
public class InternalNioOutputBuffer extends AbstractOutputBuffer<NioChannel> {

    // ----------------------------------------------------------- Constructors

    /**
     * Default constructor.
     */
    public InternalNioOutputBuffer(Response response, int headerBufferSize) {

        super(response, headerBufferSize);

        outputStreamOutputBuffer = new SocketOutputBuffer();
    }


    /**
     * Underlying socket.
     */
    private NioChannel socket;

    /**
     * Selector pool, for blocking reads and blocking writes
     */
    private NioSelectorPool pool;

    /**
     * Track if the byte buffer is flipped
     */
    protected volatile boolean flipped = false;


    // --------------------------------------------------------- Public Methods

    @Override
    public void init(SocketWrapper<NioChannel> socketWrapper,
            AbstractEndpoint<NioChannel> endpoint) throws IOException {

        socket = socketWrapper.getSocket();
        pool = ((NioEndpoint)endpoint).getSelectorPool();
    }


    /**
     * Recycle the output buffer. This should be called when closing the
     * connection.
     */
    @Override
    public void recycle() {
        super.recycle();
        if (socket != null) {
            socket.getBufHandler().getWriteBuffer().clear();
            socket = null;
        }
        flipped = false;
    }


    // ------------------------------------------------ HTTP/1.1 Output Methods

    /**
     * Send an acknowledgment.
     */
    @Override
    public void sendAck() throws IOException {
        if (!committed) {
            socket.getBufHandler().getWriteBuffer().put(
                    Constants.ACK_BYTES, 0, Constants.ACK_BYTES.length);
            int result = writeToSocket(socket.getBufHandler().getWriteBuffer(), true, true);
            if (result < 0) {
                throw new IOException(sm.getString("iob.failedwrite.ack"));
            }
        }
    }

    /**
     *
     * @param bytebuffer ByteBuffer
     * @param flip boolean
     * @return int
     * @throws IOException
     */
    private synchronized int writeToSocket(ByteBuffer bytebuffer, boolean block, boolean flip) throws IOException {
        if ( flip ) {
            bytebuffer.flip();
            flipped = true;
        }

        int written = 0;
        NioEndpoint.KeyAttachment att = (NioEndpoint.KeyAttachment)socket.getAttachment(false);
        if ( att == null ) throw new IOException("Key must be cancelled");
        long writeTimeout = att.getWriteTimeout();
        Selector selector = null;
        try {
            selector = pool.get();
        } catch ( IOException x ) {
            //ignore
        }
        try {
            written = pool.write(bytebuffer, socket, selector, writeTimeout, block);
            //make sure we are flushed
            do {
                if (socket.flush(true,selector,writeTimeout)) break;
            }while ( true );
        } finally {
            if ( selector != null ) pool.put(selector);
        }
        if ( block || bytebuffer.remaining()==0) {
            //blocking writes must empty the buffer
            //and if remaining==0 then we did empty it
            bytebuffer.clear();
            flipped = false;
        }
        // If there is data left in the buffer the socket will be registered for
        // write further up the stack. This is to ensure the socket is only
        // registered for write once as both container and user code can trigger
        // write registration.
        return written;
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
            addToBB(headerBuffer, 0, pos);
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
            int thisTime = transfer(buf,offset,length,socket.getBufHandler().getWriteBuffer());
            length = length - thisTime;
            offset = offset + thisTime;
            int written = writeToSocket(socket.getBufHandler().getWriteBuffer(),
                    isBlocking(), true);
            if (written == 0) {
                dataLeft = true;
            } else {
                dataLeft = flushBuffer(isBlocking());
            }
        }

        NioEndpoint.KeyAttachment ka = (NioEndpoint.KeyAttachment)socket.getAttachment(false);
        if (ka != null) ka.access();//prevent timeouts for just doing client writes

        if (!isBlocking() && length > 0) {
            // Remaining data must be buffered
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

        //prevent timeout for async,
        SelectionKey key = socket.getIOChannel().keyFor(socket.getPoller().getSelector());
        if (key != null) {
            NioEndpoint.KeyAttachment attach = (NioEndpoint.KeyAttachment) key.attachment();
            attach.access();
        }

        boolean dataLeft = hasMoreDataToFlush();

        //write to the socket, if there is anything to write
        if (dataLeft) {
            writeToSocket(socket.getBufHandler().getWriteBuffer(),block, !flipped);
        }

        dataLeft = hasMoreDataToFlush();

        if (!dataLeft && bufferedWrites.size() > 0) {
            Iterator<ByteBufferHolder> bufIter = bufferedWrites.iterator();
            while (!hasMoreDataToFlush() && bufIter.hasNext()) {
                ByteBufferHolder buffer = bufIter.next();
                buffer.flip();
                while (!hasMoreDataToFlush() && buffer.getBuf().remaining()>0) {
                    transfer(buffer.getBuf(), socket.getBufHandler().getWriteBuffer());
                    if (buffer.getBuf().remaining() == 0) {
                        bufIter.remove();
                    }
                    writeToSocket(socket.getBufHandler().getWriteBuffer(),block, true);
                    //here we must break if we didn't finish the write
                }
            }
        }

        return hasMoreDataToFlush();
    }


    @Override
    protected boolean hasMoreDataToFlush() {
        return (flipped && socket.getBufHandler().getWriteBuffer().remaining()>0) ||
        (!flipped && socket.getBufHandler().getWriteBuffer().position() > 0);
    }


    @Override
    protected void registerWriteInterest() throws IOException {
        NioEndpoint.KeyAttachment att = (NioEndpoint.KeyAttachment)socket.getAttachment(false);
        if (att == null) {
            throw new IOException("Key must be cancelled");
        }
        att.getPoller().add(socket, SelectionKey.OP_WRITE);
    }


    private int transfer(byte[] from, int offset, int length, ByteBuffer to) {
        int max = Math.min(length, to.remaining());
        to.put(from, offset, max);
        return max;
    }


    private void transfer(ByteBuffer from, ByteBuffer to) {
        int max = Math.min(from.remaining(), to.remaining());
        ByteBuffer tmp = from.duplicate ();
        tmp.limit (tmp.position() + max);
        to.put (tmp);
        from.position(from.position() + max);
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
