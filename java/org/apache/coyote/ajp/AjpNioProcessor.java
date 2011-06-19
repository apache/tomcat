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

package org.apache.coyote.ajp;

import java.io.EOFException;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.Selector;

import org.apache.coyote.ActionCode;
import org.apache.coyote.OutputBuffer;
import org.apache.coyote.RequestInfo;
import org.apache.coyote.Response;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.buf.HexUtils;
import org.apache.tomcat.util.http.HttpMessages;
import org.apache.tomcat.util.net.AbstractEndpoint.Handler.SocketState;
import org.apache.tomcat.util.net.NioChannel;
import org.apache.tomcat.util.net.NioEndpoint;
import org.apache.tomcat.util.net.NioEndpoint.KeyAttachment;
import org.apache.tomcat.util.net.NioSelectorPool;
import org.apache.tomcat.util.net.SocketStatus;


/**
 * Processes AJP requests using NIO.
 */
public class AjpNioProcessor extends AbstractAjpProcessor {


    /**
     * Logger.
     */
    private static final Log log = LogFactory.getLog(AjpNioProcessor.class);
    @Override
    protected Log getLog() {
        return log;
    }

    // ----------------------------------------------------------- Constructors


    public AjpNioProcessor(int packetSize, NioEndpoint endpoint) {

        super(packetSize, endpoint);

        response.setOutputBuffer(new SocketOutputBuffer());

        pool = endpoint.getSelectorPool();

        // Set the get body message buffer
        AjpMessage getBodyMessage = new AjpMessage(16);
        getBodyMessage.reset();
        getBodyMessage.appendByte(Constants.JK_AJP13_GET_BODY_CHUNK);
        // Adjust allowed size if packetSize != default (Constants.MAX_PACKET_SIZE)
        getBodyMessage.appendInt(Constants.MAX_READ_SIZE + packetSize - Constants.MAX_PACKET_SIZE);
        getBodyMessage.end();
        getBodyMessageArray = new byte[getBodyMessage.getLen()];
        System.arraycopy(getBodyMessage.getBuffer(), 0, getBodyMessageArray, 
                         0, getBodyMessage.getLen());

        // Cause loading of HexUtils
        HexUtils.load();

        // Cause loading of HttpMessages
        HttpMessages.getMessage(200);

    }


    // ----------------------------------------------------- Instance Variables


    /**
     * Socket associated with the current connection.
     */
    protected NioChannel socket;

    
    /**
     * Selector pool for the associated endpoint.
     */
    protected NioSelectorPool pool;


    /**
     * Direct buffer used for sending right away a get body message.
     */
    protected final byte[] getBodyMessageArray;


    // --------------------------------------------------------- Public Methods


    /**
     * Process pipelined HTTP requests using the specified input and output
     * streams.
     *
     * @throws IOException error during an I/O operation
     */
    public SocketState process(NioChannel socket)
        throws IOException {
        RequestInfo rp = request.getRequestProcessor();
        rp.setStage(org.apache.coyote.Constants.STAGE_PARSE);

        // Setting up the socket
        this.socket = socket;
        
        long soTimeout = endpoint.getSoTimeout();
        int keepAliveTimeout = endpoint.getKeepAliveTimeout();

        // Error flag
        error = false;

        final KeyAttachment ka = (KeyAttachment)socket.getAttachment(false);
        
        while (!error && !endpoint.isPaused()) {
            // Parsing the request header
            try {
                // Get first message of the request
                int bytesRead = readMessage(requestHeaderMessage, false);
                if (bytesRead == 0) {
                    rp.setStage(org.apache.coyote.Constants.STAGE_ENDED);
                    break;
                }
                // Set back timeout if keep alive timeout is enabled
                if (keepAliveTimeout > 0) {
                    ka.setTimeout(soTimeout);
                }
                // Check message type, process right away and break if
                // not regular request processing
                int type = requestHeaderMessage.getByte();
                if (type == Constants.JK_AJP13_CPING_REQUEST) {
                    try {
                        output(pongMessageArray, 0, pongMessageArray.length);
                    } catch (IOException e) {
                        error = true;
                    }
                    recycle();
                    continue;
                } else if(type != Constants.JK_AJP13_FORWARD_REQUEST) {
                    // Usually the servlet didn't read the previous request body
                    if(log.isDebugEnabled()) {
                        log.debug("Unexpected message: "+type);
                    }
                    recycle();
                    continue;
                }
                request.setStartTime(System.currentTimeMillis());
            } catch (IOException e) {
                error = true;
                break;
            } catch (Throwable t) {
                ExceptionUtils.handleThrowable(t);
                log.debug(sm.getString("ajpprocessor.header.error"), t);
                // 400 - Bad Request
                response.setStatus(400);
                adapter.log(request, response, 0);
                error = true;
            }

            if (!error) {
                // Setting up filters, and parse some request headers
                rp.setStage(org.apache.coyote.Constants.STAGE_PREPARE);
                try {
                    prepareRequest();
                } catch (Throwable t) {
                    ExceptionUtils.handleThrowable(t);
                    log.debug(sm.getString("ajpprocessor.request.prepare"), t);
                    // 400 - Internal Server Error
                    response.setStatus(400);
                    adapter.log(request, response, 0);
                    error = true;
                }
            }

            if (endpoint.isPaused()) {
                // 503 - Service unavailable
                response.setStatus(503);
                adapter.log(request, response, 0);
                error = true;
            }

            // Process the request in the adapter
            if (!error) {
                try {
                    rp.setStage(org.apache.coyote.Constants.STAGE_SERVICE);
                    adapter.service(request, response);
                } catch (InterruptedIOException e) {
                    error = true;
                } catch (Throwable t) {
                    ExceptionUtils.handleThrowable(t);
                    log.error(sm.getString("ajpprocessor.request.process"), t);
                    // 500 - Internal Server Error
                    response.setStatus(500);
                    adapter.log(request, response, 0);
                    error = true;
                }
            }
            
            if (isAsync() && !error) {
                break;
            }

            // Finish the response if not done yet
            if (!finished) {
                try {
                    finish();
                } catch (Throwable t) {
                    ExceptionUtils.handleThrowable(t);
                    error = true;
                }
            }

            // If there was an error, make sure the request is counted as
            // and error, and update the statistics counter
            if (error) {
                response.setStatus(500);
            }
            request.updateCounters();

            rp.setStage(org.apache.coyote.Constants.STAGE_KEEPALIVE);
            // Set keep alive timeout if enabled
            if (keepAliveTimeout > 0) {
                ka.setTimeout(keepAliveTimeout);
            }

            recycle();
        }
        
        rp.setStage(org.apache.coyote.Constants.STAGE_ENDED);

        if (!error && !endpoint.isPaused()) {
            if (isAsync()) {
                return SocketState.LONG;
            } else {
                return SocketState.OPEN;
            }
        } else {
            return SocketState.CLOSED;
        }
        
    }

    
    // ----------------------------------------------------- ActionHook Methods


    /**
     * Send an action to the connector.
     *
     * @param actionCode Type of the action
     * @param param Action parameter
     */
    @Override
    protected void actionInternal(ActionCode actionCode, Object param) {

        if (actionCode == ActionCode.ASYNC_COMPLETE) {
            if (asyncStateMachine.asyncComplete()) {
                ((NioEndpoint)endpoint).processSocket(this.socket,
                        SocketStatus.OPEN, false);
            }
        } else if (actionCode == ActionCode.ASYNC_SETTIMEOUT) {
            if (param == null) return;
            long timeout = ((Long)param).longValue();
            final KeyAttachment ka = (KeyAttachment)socket.getAttachment(false);
            if (keepAliveTimeout > 0) {
                ka.setTimeout(timeout);
            }
        } else if (actionCode == ActionCode.ASYNC_DISPATCH) {
            if (asyncStateMachine.asyncDispatch()) {
                ((NioEndpoint)endpoint).processSocket(this.socket,
                        SocketStatus.OPEN, true);
            }
        }
    }


    // ------------------------------------------------------ Protected Methods

    @Override
    protected void output(byte[] src, int offset, int length)
            throws IOException {
        ByteBuffer writeBuffer = socket.getBufHandler() .getWriteBuffer();

        writeBuffer.put(src, offset, length);
        
        writeBuffer.flip();
        
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
            pool.write(writeBuffer, socket, selector, writeTimeout, true,
                    null);
        }finally { 
            if ( selector != null ) pool.put(selector);
        }
        writeBuffer.clear();
    }

    /**
     * Finish AJP response.
     */
    @Override
    protected void finish() throws IOException {

        if (!response.isCommitted()) {
            // Validate and write response headers
            try {
                prepareResponse();
            } catch (IOException e) {
                // Set error flag
                error = true;
            }
        }

        if (finished)
            return;

        finished = true;

        // Add the end message
        output(endMessageArray, 0, endMessageArray.length);
    }


    /**
     * Read the specified amount of bytes, and place them in the input buffer.
     */
    protected int read(byte[] buf, int pos, int n, boolean blockFirstRead)
        throws IOException {

        int read = 0;
        int res = 0;
        boolean block = blockFirstRead;
        
        while (read < n) {
            res = readSocket(buf, read + pos, n, block);
            if (res > 0) {
                read += res;
            } else if (res == 0 && !block) {
                break;
            } else {
                throw new IOException(sm.getString("ajpprotocol.failedread"));
            }
            block = true;
        }
        return read;
    }

    private int readSocket(byte[] buf, int pos, int n, boolean block)
            throws IOException {
        int nRead = 0;
        socket.getBufHandler().getReadBuffer().clear();
        socket.getBufHandler().getReadBuffer().limit(n);
        if ( block ) {
            Selector selector = null;
            try {
                selector = pool.get();
            } catch ( IOException x ) {
                // Ignore
            }
            try {
                NioEndpoint.KeyAttachment att = (NioEndpoint.KeyAttachment)socket.getAttachment(false);
                if ( att == null ) throw new IOException("Key must be cancelled.");
                nRead = pool.read(socket.getBufHandler().getReadBuffer(),socket,selector,att.getTimeout());
            } catch ( EOFException eof ) {
                nRead = -1;
            } finally { 
                if ( selector != null ) pool.put(selector);
            }
        } else {
            nRead = socket.read(socket.getBufHandler().getReadBuffer());
        }
        if (nRead > 0) {
            socket.getBufHandler().getReadBuffer().flip();
            socket.getBufHandler().getReadBuffer().limit(nRead);
            socket.getBufHandler().getReadBuffer().get(buf, pos, nRead);
            return nRead;
        } else if (nRead == -1) {
            //return false;
            throw new EOFException(sm.getString("iib.eof.error"));
        } else {
            return 0;
        }
    }


    /** Receive a chunk of data. Called to implement the
     *  'special' packet in ajp13 and to receive the data
     *  after we send a GET_BODY packet
     */
    @Override
    public boolean receive() throws IOException {

        first = false;
        bodyMessage.reset();
        
        readMessage(bodyMessage, true);

        // No data received.
        if (bodyMessage.getLen() == 0) {
            // just the header
            // Don't mark 'end of stream' for the first chunk.
            return false;
        }
        int blen = bodyMessage.peekInt();
        if (blen == 0) {
            return false;
        }

        bodyMessage.getBytes(bodyBytes);
        empty = false;
        return true;
    }

    /**
     * Get more request body data from the web server and store it in the
     * internal buffer.
     *
     * @return true if there is more data, false if not.
     */
    @Override
    protected boolean refillReadBuffer() throws IOException {
        // If the server returns an empty packet, assume that that end of
        // the stream has been reached (yuck -- fix protocol??).
        // FORM support
        if (replay) {
            endOfStream = true; // we've read everything there is
        }
        if (endOfStream) {
            return false;
        }

        // Request more data immediately
        output(getBodyMessageArray, 0, getBodyMessageArray.length);

        boolean moreData = receive();
        if( !moreData ) {
            endOfStream = true;
        }
        return moreData;
    }


    /**
     * Read an AJP message.
     *
     * @return The number of bytes read
     * @throws IOException any other failure, including incomplete reads
     */
    protected int readMessage(AjpMessage message, boolean blockFirstRead)
        throws IOException {

        byte[] buf = message.getBuffer();
        int headerLength = message.getHeaderLength();

        int bytesRead = read(buf, 0, headerLength, blockFirstRead);

        if (bytesRead == 0) {
            return 0;
        }
        
        int messageLength = message.processHeader();
        if (messageLength < 0) {
            // Invalid AJP header signature
            throw new IOException(sm.getString("ajpmessage.invalidLength",
                    Integer.valueOf(messageLength)));
        }
        else if (messageLength == 0) {
            // Zero length message.
            return bytesRead;
        }
        else {
            if (messageLength > buf.length) {
                // Message too long for the buffer
                // Need to trigger a 400 response
                throw new IllegalArgumentException(sm.getString(
                        "ajpprocessor.header.tooLong",
                        Integer.valueOf(messageLength),
                        Integer.valueOf(buf.length)));
            }
            bytesRead += read(buf, headerLength, messageLength, true);
            return bytesRead;
        }
    }


    /**
     * Callback to write data from the buffer.
     */
    @Override
    protected void flush(boolean explicit) throws IOException {
        if (explicit && !finished) {
            // Send the flush message
            output(flushMessageArray, 0, flushMessageArray.length);
        }
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
        public int doWrite(ByteChunk chunk, Response res)
            throws IOException {

            if (!response.isCommitted()) {
                // Validate and write response headers
                try {
                    prepareResponse();
                } catch (IOException e) {
                    // Set error flag
                    error = true;
                }
            }

            int len = chunk.getLength();
            // 4 - hardcoded, byte[] marshaling overhead
            // Adjust allowed size if packetSize != default (Constants.MAX_PACKET_SIZE)
            int chunkSize = Constants.MAX_SEND_SIZE + packetSize - Constants.MAX_PACKET_SIZE;
            int off = 0;
            while (len > 0) {
                int thisTime = len;
                if (thisTime > chunkSize) {
                    thisTime = chunkSize;
                }
                len -= thisTime;
                responseHeaderMessage.reset();
                responseHeaderMessage.appendByte(Constants.JK_AJP13_SEND_BODY_CHUNK);
                responseHeaderMessage.appendBytes(chunk.getBytes(), chunk.getOffset() + off, thisTime);
                responseHeaderMessage.end();
                output(responseHeaderMessage.getBuffer(), 0, responseHeaderMessage.getLen());

                off += thisTime;
            }

            byteCount += chunk.getLength();
            return chunk.getLength();
        }

        @Override
        public long getBytesWritten() {
            return byteCount;
        }
    }
}
