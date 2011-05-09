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

import org.apache.coyote.OutputBuffer;
import org.apache.coyote.Response;
import org.apache.tomcat.util.MutableInteger;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.http.HttpMessages;
import org.apache.tomcat.util.net.NioChannel;
import org.apache.tomcat.util.net.NioEndpoint;
import org.apache.tomcat.util.net.NioSelectorPool;

/**
 * Output buffer.
 * 
 * @author <a href="mailto:remm@apache.org">Remy Maucherat</a>
 * @author Filip Hanik
 */
public class InternalNioOutputBuffer extends AbstractOutputBuffer {

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
     * Number of bytes last written
     */
    protected MutableInteger lastWrite = new MutableInteger(1);

    /**
     * Underlying socket.
     */
    protected NioChannel socket;
    
    /**
     * Selector pool, for blocking reads and blocking writes
     */
    protected NioSelectorPool pool;


    // ------------------------------------------------------------- Properties


    /**
     * Set the underlying socket.
     */
    public void setSocket(NioChannel socket) {
        this.socket = socket;
    }

    public void setSelectorPool(NioSelectorPool pool) { 
        this.pool = pool;
    }

    public NioSelectorPool getSelectorPool() {
        return pool;
    }    

    // --------------------------------------------------------- Public Methods


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
        flushBuffer();

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
    }


    /**
     * End request.
     * 
     * @throws IOException an underlying I/O error occurred
     */
    @Override
    public void endRequest() throws IOException {
        super.endRequest();
        flushBuffer();
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
        if ( flip ) bytebuffer.flip();

        int written = 0;
        NioEndpoint.KeyAttachment att = (NioEndpoint.KeyAttachment)socket.getAttachment(false);
        if ( att == null ) throw new IOException("Key must be cancelled");
        long writeTimeout = att.getTimeout();
        Selector selector = null;
        try {
            selector = getSelectorPool().get();
        } catch ( IOException x ) {
            //ignore
        }
        try {
            written = getSelectorPool().write(bytebuffer, socket, selector, writeTimeout, block,lastWrite);
            //make sure we are flushed 
            do {
                if (socket.flush(true,selector,writeTimeout,lastWrite)) break;
            }while ( true );
        }finally { 
            if ( selector != null ) getSelectorPool().put(selector);
        }
        if ( block ) bytebuffer.clear(); //only clear
        this.total = 0;
        return written;
    } 


    // ------------------------------------------------------ Protected Methods


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

    int total = 0;
    private synchronized void addToBB(byte[] buf, int offset, int length) throws IOException {
        while (length > 0) {
            int thisTime = length;
            if (socket.getBufHandler().getWriteBuffer().position() ==
                    socket.getBufHandler().getWriteBuffer().capacity()
                    || socket.getBufHandler().getWriteBuffer().remaining()==0) {
                flushBuffer();
            }
            if (thisTime > socket.getBufHandler().getWriteBuffer().remaining()) {
                thisTime = socket.getBufHandler().getWriteBuffer().remaining();
            }
            socket.getBufHandler().getWriteBuffer().put(buf, offset, thisTime);
            length = length - thisTime;
            offset = offset + thisTime;
            total += thisTime;
        }
        NioEndpoint.KeyAttachment ka = (NioEndpoint.KeyAttachment)socket.getAttachment(false);
        if ( ka!= null ) ka.access();//prevent timeouts for just doing client writes
    }


    /**
     * Callback to write data from the buffer.
     */
    protected void flushBuffer()
        throws IOException {

        //prevent timeout for async,
        SelectionKey key = socket.getIOChannel().keyFor(socket.getPoller().getSelector());
        if (key != null) {
            NioEndpoint.KeyAttachment attach = (NioEndpoint.KeyAttachment) key.attachment();
            attach.access();
        }

        //write to the socket, if there is anything to write
        if (socket.getBufHandler().getWriteBuffer().position() > 0) {
            socket.getBufHandler().getWriteBuffer().flip();
            writeToSocket(socket.getBufHandler().getWriteBuffer(),true, false);
        }
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


}
