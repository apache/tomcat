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
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.coyote.OutputBuffer;
import org.apache.coyote.Response;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.http.HttpMessages;
import org.apache.tomcat.util.net.AbstractEndpoint;
import org.apache.tomcat.util.net.NioChannel;
import org.apache.tomcat.util.net.NioEndpoint;
import org.apache.tomcat.util.net.NioSelectorPool;
import org.apache.tomcat.util.net.SocketWrapper;

/**
 * Output buffer.
 *
 * @author <a href="mailto:remm@apache.org">Remy Maucherat</a>
 * @author Filip Hanik
 */
public class InternalNioOutputBuffer extends AbstractOutputBuffer<NioChannel> {

    // ----------------------------------------------------------- Constructors

    /**
     * Default constructor.
     */
    public InternalNioOutputBuffer(Response response, int headerBufferSize) {

        this.response = response;

        buf = new byte[headerBufferSize];

        outputStreamOutputBuffer = new SocketOutputBuffer();

        filterLibrary = new OutputFilter[0];
        activeFilters = new OutputFilter[0];
        lastActiveFilter = -1;

        committed = false;
        finished = false;

        // Cause loading of HttpMessages
        HttpMessages.getMessage(200);

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
     * Flag used only for Comet requests/responses
     */
    protected volatile boolean blocking = true;

    /**
     * Track if the byte buffer is flipped
     */
    protected volatile boolean flipped = false;

    /**
     * For "non-blocking" writes use an external buffer
     */
    protected volatile LinkedBlockingDeque<ByteBufferHolder> bufferedWrite = null;

    /**
     * The max size of the buffered write buffer
     */
    protected int bufferedWriteSize = 64*1024; //64k default write buffer

    /**
     * Number of bytes last written
     */
    protected final AtomicInteger lastWrite = new AtomicInteger(1);

    protected class ByteBufferHolder {
        private final ByteBuffer buf;
        private final AtomicBoolean flipped;
        public ByteBufferHolder(ByteBuffer buf, boolean flipped) {
           this.buf = buf;
           this.flipped = new AtomicBoolean(flipped);
        }
        public ByteBuffer getBuf() {
            return buf;
        }
        public boolean isFlipped() {
            return flipped.get();
        }

        public boolean flip() {
            if (flipped.compareAndSet(false, true)) {
                buf.flip();
                return true;
            } else {
                return false;
            }
        }

        public boolean hasData() {
            if (flipped.get()) {
                return buf.remaining()>0;
            } else {
                return buf.position()>0;
            }
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder(super.toString());
            builder.append("[flipped=");
            builder.append(isFlipped()?"true, remaining=" : "false, position=");
            builder.append(isFlipped()? buf.remaining(): buf.position());
            builder.append("]");
            return builder.toString();
        }

    }

    // --------------------------------------------------------- Public Methods

    @Override
    public boolean supportsNonBlocking() {
        return true;
    }

    /**
     * Flush the response.
     *
     * @throws IOException an underlying I/O error occurred
     *
     */
    @Override
    public void flush() throws IOException {

        super.flush();
        // Flush the current buffer
        flushBuffer(isBlocking());

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
        lastWrite.set(1);
        setBlocking(true);
        flipped = false;
    }


    /**
     * End request.
     *
     * @throws IOException an underlying I/O error occurred
     */
    @Override
    public void endRequest() throws IOException {
        super.endRequest();
        flushBuffer(true);
    }

    // ------------------------------------------------ HTTP/1.1 Output Methods


    /**
     * Send an acknowledgment.
     */
    @Override
    public void sendAck() throws IOException {

        if (!committed) {
            //Socket.send(socket, Constants.ACK_BYTES, 0, Constants.ACK_BYTES.length) < 0
            socket.getBufHandler() .getWriteBuffer().put(Constants.ACK_BYTES,0,Constants.ACK_BYTES.length);
            writeToSocket(socket.getBufHandler() .getWriteBuffer(),true,true);
        }

    }

    /**
     *
     * @param bytebuffer ByteBuffer
     * @param flip boolean
     * @return int
     * @throws IOException
     * TODO Fix non blocking write properly
     */
    private synchronized int writeToSocket(ByteBuffer bytebuffer, boolean block, boolean flip) throws IOException {
        if ( flip ) {
            bytebuffer.flip();
            flipped = true;
        }

        int written = 0;
        NioEndpoint.KeyAttachment att = (NioEndpoint.KeyAttachment)socket.getAttachment(false);
        if ( att == null ) throw new IOException("Key must be cancelled");
        long writeTimeout = att.getTimeout();
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
        }finally {
            if ( selector != null ) pool.put(selector);
        }
        if ( block || bytebuffer.remaining()==0) {
            //blocking writes must empty the buffer
            //and if remaining==0 then we did empty it
            bytebuffer.clear();
            flipped = false;
        }
        return written;
    }


    // ------------------------------------------------------ Protected Methods

    @Override
    public void init(SocketWrapper<NioChannel> socketWrapper,
            AbstractEndpoint endpoint) throws IOException {

        socket = socketWrapper.getSocket();
        pool = ((NioEndpoint)endpoint).getSelectorPool();
    }


    /**
     * Commit the response.
     *
     * @throws IOException an underlying I/O error occurred
     */
    @Override
    protected void commit()
        throws IOException {

        // The response is now committed
        committed = true;
        response.setCommitted(true);

        if (pos > 0) {
            // Sending the response header buffer
            addToBB(buf, 0, pos);
        }

    }


    private synchronized void addToBB(byte[] buf, int offset, int length) throws IOException {
        //try to write to socket first
        if (length==0) return;

        boolean dataLeft = flushBuffer(isBlocking());

        while (!dataLeft && length>0) {
            int thisTime = transfer(buf,offset,length,socket.getBufHandler().getWriteBuffer());
            length = length - thisTime;
            offset = offset + thisTime;
            writeToSocket(socket.getBufHandler().getWriteBuffer(), isBlocking(), true);
            dataLeft = flushBuffer(isBlocking());
        }

        NioEndpoint.KeyAttachment ka = (NioEndpoint.KeyAttachment)socket.getAttachment(false);
        if ( ka!= null ) ka.access();//prevent timeouts for just doing client writes

        if (!isBlocking() && length>0) {
            //we must buffer as long as it fits
            //ByteBufferHolder tail = bufferedWrite.
            addToBuffers(buf, offset, length);
    }
    }

    private void addToBuffers(byte[] buf, int offset, int length) {
        ByteBufferHolder holder = bufferedWrite.peekLast();
        if (holder==null || holder.isFlipped() || holder.getBuf().remaining()<length) {
            ByteBuffer buffer = ByteBuffer.allocate(Math.max(bufferedWriteSize,length));
            holder = new ByteBufferHolder(buffer,false);
            bufferedWrite.add(holder);
        }
        holder.getBuf().put(buf,offset,length);
    }


    /**
     * Callback to write data from the buffer.
     */
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

        if (!dataLeft && bufferedWrite!=null) {
            Iterator<ByteBufferHolder> bufIter = bufferedWrite.iterator();
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

        dataLeft = hasMoreDataToFlush();

        return dataLeft;
    }

    private boolean hasMoreDataToFlush() {
        return (flipped && socket.getBufHandler().getWriteBuffer().remaining()>0) ||
        (!flipped && socket.getBufHandler().getWriteBuffer().position() > 0);
    }

    private int transfer(byte[] from, int offset, int length, ByteBuffer to) {
        int max = Math.min(length, to.remaining());
        to.put(from, offset, max);
        return max;
    }

    private int transfer(ByteBuffer from, ByteBuffer to) {
        int max = Math.min(from.remaining(), to.remaining());
        ByteBuffer tmp = from.duplicate ();
        tmp.limit (tmp.position() + max);
        to.put (tmp);
        from.position(from.position() + max);
        return max;
    }


    // ----------------------------------- OutputStreamOutputBuffer Inner Class

    /**
     * This class is an output buffer which will write data to an output
     * stream.
     */
    protected class SocketOutputBuffer
        implements OutputBuffer {


        /**
         * Write chunk.
         */
        @Override
        public int doWrite(ByteChunk chunk, Response res)
            throws IOException {

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

    //----------------------------------------non blocking writes -----------------
    public void setBlocking(boolean blocking) {
        this.blocking = blocking;
        if (blocking)
            bufferedWrite = null;
        else
            bufferedWrite = new LinkedBlockingDeque<>();
}

    public void setBufferedWriteSize(int bufferedWriteSize) {
        this.bufferedWriteSize = bufferedWriteSize;
    }

    public boolean isBlocking() {
        return blocking;
    }

    private boolean hasBufferedData() {
        boolean result = false;
        if (bufferedWrite!=null) {
            Iterator<ByteBufferHolder> iter = bufferedWrite.iterator();
            while (!result && iter.hasNext()) {
                result = iter.next().hasData();
            }
        }
        return result;
    }

    public boolean hasDataToWrite() {
        return hasMoreDataToFlush() || hasBufferedData();
    }

    public int getBufferedWriteSize() {
        return bufferedWriteSize;
    }

    public boolean isWritable() {
        return !hasDataToWrite();
    }
}
