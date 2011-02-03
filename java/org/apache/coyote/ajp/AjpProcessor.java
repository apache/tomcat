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

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.Socket;
import java.util.concurrent.Executor;

import org.apache.coyote.ActionCode;
import org.apache.coyote.OutputBuffer;
import org.apache.coyote.Request;
import org.apache.coyote.RequestInfo;
import org.apache.coyote.Response;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.buf.HexUtils;
import org.apache.tomcat.util.http.HttpMessages;
import org.apache.tomcat.util.net.AbstractEndpoint.Handler.SocketState;
import org.apache.tomcat.util.net.JIoEndpoint;
import org.apache.tomcat.util.net.SocketStatus;
import org.apache.tomcat.util.net.SocketWrapper;


/**
 * Processes AJP requests.
 *
 * @author Remy Maucherat
 * @author Henri Gomez
 * @author Dan Milstein
 * @author Keith Wannamaker
 * @author Kevin Seguin
 * @author Costin Manolache
 * @author Bill Barker
 */
public class AjpProcessor extends AbstractAjpProcessor {


    /**
     * Logger.
     */
    private static final Log log = LogFactory.getLog(AjpProcessor.class);
    @Override
    protected Log getLog() {
        return log;
    }

    // ----------------------------------------------------------- Constructors


    public AjpProcessor(int packetSize, JIoEndpoint endpoint) {

        this.endpoint = endpoint;

        request = new Request();
        request.setInputBuffer(new SocketInputBuffer());

        response = new Response();
        response.setHook(this);
        response.setOutputBuffer(new SocketOutputBuffer());
        request.setResponse(response);

        this.packetSize = packetSize;
        requestHeaderMessage = new AjpMessage(packetSize);
        responseHeaderMessage = new AjpMessage(packetSize);
        bodyMessage = new AjpMessage(packetSize);

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
    protected SocketWrapper<Socket> socket;

    
    /**
     * Input stream.
     */
    protected InputStream input;
    
    
    /**
     * Output stream.
     */
    protected OutputStream output;
    

    /**
     * Direct buffer used for sending right away a get body message.
     */
    protected final byte[] getBodyMessageArray;


    /**
     * Direct buffer used for sending right away a pong message.
     */
    protected static final byte[] pongMessageArray;


    /**
     * End message array.
     */
    protected static final byte[] endMessageArray;


    /**
     * Flush message array.
     */
    protected static final byte[] flushMessageArray;
    
    // ----------------------------------------------------- Static Initializer


    static {

        // Set the read body message buffer
        AjpMessage pongMessage = new AjpMessage(16);
        pongMessage.reset();
        pongMessage.appendByte(Constants.JK_AJP13_CPONG_REPLY);
        pongMessage.end();
        pongMessageArray = new byte[pongMessage.getLen()];
        System.arraycopy(pongMessage.getBuffer(), 0, pongMessageArray, 
                0, pongMessage.getLen());

        // Allocate the end message array
        AjpMessage endMessage = new AjpMessage(16);
        endMessage.reset();
        endMessage.appendByte(Constants.JK_AJP13_END_RESPONSE);
        endMessage.appendByte(1);
        endMessage.end();
        endMessageArray = new byte[endMessage.getLen()];
        System.arraycopy(endMessage.getBuffer(), 0, endMessageArray, 0,
                endMessage.getLen());

        // Allocate the flush message array
        AjpMessage flushMessage = new AjpMessage(16);
        flushMessage.reset();
        flushMessage.appendByte(Constants.JK_AJP13_SEND_BODY_CHUNK);
        flushMessage.appendInt(0);
        flushMessage.appendByte(0);
        flushMessage.end();
        flushMessageArray = new byte[flushMessage.getLen()];
        System.arraycopy(flushMessage.getBuffer(), 0, flushMessageArray, 0,
                flushMessage.getLen());

    }


    // ------------------------------------------------------------- Properties


    /**
     * The number of milliseconds Tomcat will wait for a subsequent request
     * before closing the connection. The default is the same as for
     * Apache HTTP Server (15 000 milliseconds).
     */
    protected int keepAliveTimeout = -1;
    public int getKeepAliveTimeout() { return keepAliveTimeout; }
    public void setKeepAliveTimeout(int timeout) { keepAliveTimeout = timeout; }


    // --------------------------------------------------------- Public Methods


    /**
     * Process pipelined HTTP requests using the specified input and output
     * streams.
     *
     * @throws IOException error during an I/O operation
     */
    public SocketState process(SocketWrapper<Socket> socket)
        throws IOException {
        RequestInfo rp = request.getRequestProcessor();
        rp.setStage(org.apache.coyote.Constants.STAGE_PARSE);

        // Setting up the socket
        this.socket = socket;
        input = socket.getSocket().getInputStream();
        output = socket.getSocket().getOutputStream();
        int soTimeout = -1;
        if (keepAliveTimeout > 0) {
            soTimeout = socket.getSocket().getSoTimeout();
        }

        // Error flag
        error = false;

        while (!error && !endpoint.isPaused()) {

            // Parsing the request header
            try {
                // Set keep alive timeout if enabled
                if (keepAliveTimeout > 0) {
                    socket.getSocket().setSoTimeout(keepAliveTimeout);
                }
                // Get first message of the request
                if (!readMessage(requestHeaderMessage)) {
                    // This means a connection timeout
                    rp.setStage(org.apache.coyote.Constants.STAGE_ENDED);
                    break;
                }
                // Set back timeout if keep alive timeout is enabled
                if (keepAliveTimeout > 0) {
                    socket.getSocket().setSoTimeout(soTimeout);
                }
                // Check message type, process right away and break if
                // not regular request processing
                int type = requestHeaderMessage.getByte();
                if (type == Constants.JK_AJP13_CPING_REQUEST) {
                    try {
                        output.write(pongMessageArray);
                    } catch (IOException e) {
                        error = true;
                    }
                    continue;
                } else if(type != Constants.JK_AJP13_FORWARD_REQUEST) {
                    // Usually the servlet didn't read the previous request body
                    if(log.isDebugEnabled()) {
                        log.debug("Unexpected message: "+type);
                    }
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
            recycle();

        }
        
        rp.setStage(org.apache.coyote.Constants.STAGE_ENDED);

        if (isAsync() && !error && !endpoint.isPaused()) {
            return SocketState.LONG;
        } else {
            recycle();
            input = null;
            output = null;
            return SocketState.CLOSED;
        }
        
    }

    public SocketState asyncDispatch(SocketStatus status) {

        RequestInfo rp = request.getRequestProcessor();
        try {
            rp.setStage(org.apache.coyote.Constants.STAGE_SERVICE);
            error = !adapter.asyncDispatch(request, response, status);
        } catch (InterruptedIOException e) {
            error = true;
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            log.error(sm.getString("http11processor.request.process"), t);
            // 500 - Internal Server Error
            response.setStatus(500);
            adapter.log(request, response, 0);
            error = true;
        }

        rp.setStage(org.apache.coyote.Constants.STAGE_ENDED);

        if (isAsync()) {
            if (error) {
                response.setStatus(500);
                request.updateCounters();
                recycle();
                input = null;
                output = null;
                return SocketState.CLOSED;
            } else {
                return SocketState.LONG;
            }
        } else {
            if (error) {
                response.setStatus(500);
            }
            request.updateCounters();
            recycle();
            input = null;
            output = null;
            return SocketState.CLOSED;
        }


    }

    
    @Override
    public Executor getExecutor() {
        return endpoint.getExecutor();
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
                ((JIoEndpoint)endpoint).processSocketAsync(this.socket,
                        SocketStatus.OPEN);
            }
        } else if (actionCode == ActionCode.ASYNC_SETTIMEOUT) {
            if (param == null) return;
            long timeout = ((Long)param).longValue();
            // if we are not piggy backing on a worker thread, set the timeout
            socket.setTimeout(timeout);
        } else if (actionCode == ActionCode.ASYNC_DISPATCH) {
            if (asyncStateMachine.asyncDispatch()) {
                ((JIoEndpoint)endpoint).processSocketAsync(this.socket,
                        SocketStatus.OPEN);
            }
        }
    }


    // ------------------------------------------------------ Protected Methods

    @Override
    protected void output(byte[] src, int offset, int length)
            throws IOException {
        output.write(src, offset, length);
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
        output.write(endMessageArray);

    }


    /**
     * Read at least the specified amount of bytes, and place them
     * in the input buffer.
     */
    protected boolean read(byte[] buf, int pos, int n)
        throws IOException {

        int read = 0;
        int res = 0;
        while (read < n) {
            res = input.read(buf, read + pos, n - read);
            if (res > 0) {
                read += res;
            } else {
                throw new IOException(sm.getString("ajpprotocol.failedread"));
            }
        }
        
        return true;

    }


    /** Receive a chunk of data. Called to implement the
     *  'special' packet in ajp13 and to receive the data
     *  after we send a GET_BODY packet
     */
    @Override
    public boolean receive() throws IOException {

        first = false;
        bodyMessage.reset();
        if (!readMessage(bodyMessage)) {
            // Invalid message
            return false;
        }
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
        output.write(getBodyMessageArray);

        boolean moreData = receive();
        if( !moreData ) {
            endOfStream = true;
        }
        return moreData;
    }


    /**
     * Read an AJP message.
     *
     * @return true if the message has been read, false if the short read
     *         didn't return anything
     * @throws IOException any other failure, including incomplete reads
     */
    protected boolean readMessage(AjpMessage message)
        throws IOException {

        byte[] buf = message.getBuffer();
        int headerLength = message.getHeaderLength();

        read(buf, 0, headerLength);

        int messageLength = message.processHeader();
        if (messageLength < 0) {
            // Invalid AJP header signature
            // TODO: Throw some exception and close the connection to frontend.
            return false;
        }
        else if (messageLength == 0) {
            // Zero length message.
            return true;
        }
        else {
            read(buf, headerLength, messageLength);
            return true;
        }
    }


    /**
     * Callback to write data from the buffer.
     */
    @Override
    protected void flush(boolean explicit) throws IOException {
        if (!finished) {
            // Send the flush message
            output.write(flushMessageArray);
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
                output.write(responseHeaderMessage.getBuffer(), 0, responseHeaderMessage.getLen());

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
