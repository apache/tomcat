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
import java.io.InterruptedIOException;
import java.nio.ByteBuffer;

import org.apache.coyote.ActionCode;
import org.apache.coyote.OutputBuffer;
import org.apache.coyote.RequestInfo;
import org.apache.coyote.Response;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.jni.Socket;
import org.apache.tomcat.jni.Status;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.buf.HexUtils;
import org.apache.tomcat.util.http.HttpMessages;
import org.apache.tomcat.util.net.AbstractEndpoint.Handler.SocketState;
import org.apache.tomcat.util.net.AprEndpoint;
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
public class AjpAprProcessor extends AbstractAjpProcessor {


    /**
     * Logger.
     */
    private static final Log log = LogFactory.getLog(AjpAprProcessor.class);
    @Override
    protected Log getLog() {
        return log;
    }


    // ----------------------------------------------------------- Constructors


    public AjpAprProcessor(int packetSize, AprEndpoint endpoint) {

        super(packetSize, endpoint);

        response.setOutputBuffer(new SocketOutputBuffer());

        // Set the get body message buffer
        AjpMessage getBodyMessage = new AjpMessage(16);
        getBodyMessage.reset();
        getBodyMessage.appendByte(Constants.JK_AJP13_GET_BODY_CHUNK);
        // Adjust allowed size if packetSize != default (Constants.MAX_PACKET_SIZE)
        getBodyMessage.appendInt(Constants.MAX_READ_SIZE + packetSize - Constants.MAX_PACKET_SIZE);
        getBodyMessage.end();
        getBodyMessageBuffer =
            ByteBuffer.allocateDirect(getBodyMessage.getLen());
        getBodyMessageBuffer.put(getBodyMessage.getBuffer(), 0,
                                 getBodyMessage.getLen());

        // Allocate input and output buffers
        inputBuffer = ByteBuffer.allocateDirect(packetSize * 2);
        inputBuffer.limit(0);
        outputBuffer = ByteBuffer.allocateDirect(packetSize * 2);

        // Cause loading of HexUtils
        HexUtils.load();

        // Cause loading of HttpMessages
        HttpMessages.getMessage(200);

    }


    // ----------------------------------------------------- Instance Variables


    /**
     * Socket associated with the current connection.
     */
    protected SocketWrapper<Long> socket;


    /**
     * Direct buffer used for input.
     */
    protected ByteBuffer inputBuffer = null;


    /**
     * Direct buffer used for output.
     */
    protected ByteBuffer outputBuffer = null;


    /**
     * Direct buffer used for sending right away a get body message.
     */
    protected final ByteBuffer getBodyMessageBuffer;


    // --------------------------------------------------------- Public Methods


    /**
     * Process pipelined HTTP requests using the specified input and output
     * streams.
     *
     * @throws IOException error during an I/O operation
     */
    public SocketState process(SocketWrapper<Long> socket)
        throws IOException {
        RequestInfo rp = request.getRequestProcessor();
        rp.setStage(org.apache.coyote.Constants.STAGE_PARSE);

        // Setting up the socket
        this.socket = socket;
        long socketRef = socket.getSocket().longValue();
        Socket.setrbb(socketRef, inputBuffer);
        Socket.setsbb(socketRef, outputBuffer);

        // Error flag
        error = false;

        boolean keptAlive = false;

        while (!error && !endpoint.isPaused()) {

            // Parsing the request header
            try {
                // Get first message of the request
                if (!readMessage(requestHeaderMessage, true, keptAlive)) {
                    // This means that no data is available right now
                    // (long keepalive), so that the processor should be recycled
                    // and the method should return true
                    rp.setStage(org.apache.coyote.Constants.STAGE_ENDED);
                    break;
                }
                // Check message type, process right away and break if
                // not regular request processing
                int type = requestHeaderMessage.getByte();
                if (type == Constants.JK_AJP13_CPING_REQUEST) {
                    if (Socket.send(socketRef, pongMessageArray, 0,
                            pongMessageArray.length) < 0) {
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

                keptAlive = true;
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
        
        if (error || endpoint.isPaused()) {
            return SocketState.CLOSED;
        } else if (isAsync()) {
            return SocketState.LONG;
        } else {
            // Add the socket to the poller
            ((AprEndpoint)endpoint).getPoller().add(socketRef);
            return SocketState.OPEN;
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
                ((AprEndpoint)endpoint).processSocketAsync(this.socket,
                        SocketStatus.OPEN);
            }
        } else if (actionCode == ActionCode.ASYNC_SETTIMEOUT) {
            if (param==null) return;
            long timeout = ((Long)param).longValue();
            socket.setTimeout(timeout);
        } else if (actionCode == ActionCode.ASYNC_DISPATCH) {
            if (asyncStateMachine.asyncDispatch()) {
                ((AprEndpoint)endpoint).processSocketAsync(this.socket,
                        SocketStatus.OPEN);
            }
        }


    }


    // ------------------------------------------------------ Protected Methods


    @Override
    protected void output(byte[] src, int offset, int length)
            throws IOException {
        outputBuffer.put(src, offset, length);
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
        if (outputBuffer.position() + endMessageArray.length > outputBuffer.capacity()) {
            flush(false);
        }
        outputBuffer.put(endMessageArray);
        flush(false);

    }


    /**
     * Read at least the specified amount of bytes, and place them
     * in the input buffer.
     */
    protected boolean read(int n)
        throws IOException {

        if (inputBuffer.capacity() - inputBuffer.limit() <=
                n - inputBuffer.remaining()) {
            inputBuffer.compact();
            inputBuffer.limit(inputBuffer.position());
            inputBuffer.position(0);
        }
        int nRead;
        while (inputBuffer.remaining() < n) {
            nRead = Socket.recvbb
                (socket.getSocket().longValue(), inputBuffer.limit(),
                        inputBuffer.capacity() - inputBuffer.limit());
            if (nRead > 0) {
                inputBuffer.limit(inputBuffer.limit() + nRead);
            } else {
                throw new IOException(sm.getString("ajpprotocol.failedread"));
            }
        }

        return true;

    }


    /**
     * Read at least the specified amount of bytes, and place them
     * in the input buffer.
     */
    protected boolean readt(int n, boolean useAvailableData)
        throws IOException {

        if (useAvailableData && inputBuffer.remaining() == 0) {
            return false;
        }
        if (inputBuffer.capacity() - inputBuffer.limit() <=
                n - inputBuffer.remaining()) {
            inputBuffer.compact();
            inputBuffer.limit(inputBuffer.position());
            inputBuffer.position(0);
        }
        int nRead;
        while (inputBuffer.remaining() < n) {
            nRead = Socket.recvbb
                (socket.getSocket().longValue(), inputBuffer.limit(),
                    inputBuffer.capacity() - inputBuffer.limit());
            if (nRead > 0) {
                inputBuffer.limit(inputBuffer.limit() + nRead);
            } else {
                if ((-nRead) == Status.ETIMEDOUT || (-nRead) == Status.TIMEUP) {
                    return false;
                } else {
                    throw new IOException(sm.getString("ajpprotocol.failedread"));
                }
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
        if (!readMessage(bodyMessage, false, false)) {
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
        Socket.sendb(socket.getSocket().longValue(), getBodyMessageBuffer, 0,
                getBodyMessageBuffer.position());

        boolean moreData = receive();
        if( !moreData ) {
            endOfStream = true;
        }
        return moreData;
    }


    /**
     * Read an AJP message.
     *
     * @param first is true if the message is the first in the request, which
     *        will cause a short duration blocking read
     * @return true if the message has been read, false if the short read
     *         didn't return anything
     * @throws IOException any other failure, including incomplete reads
     */
    protected boolean readMessage(AjpMessage message, boolean first,
            boolean useAvailableData)
        throws IOException {

        int headerLength = message.getHeaderLength();

        if (first) {
            if (!readt(headerLength, useAvailableData)) {
                return false;
            }
        } else {
            read(headerLength);
        }
        inputBuffer.get(message.getBuffer(), 0, headerLength);
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
            if (messageLength > message.getBuffer().length) {
                // Message too long for the buffer
                // Need to trigger a 400 response
                throw new IllegalArgumentException(sm.getString(
                        "ajpprocessor.header.tooLong",
                        Integer.valueOf(messageLength),
                        Integer.valueOf(message.getBuffer().length)));
            }
            read(messageLength);
            inputBuffer.get(message.getBuffer(), headerLength, messageLength);
            return true;
        }

    }


    /**
     * Recycle the processor.
     */
    @Override
    public void recycle() {
        super.recycle();

        inputBuffer.clear();
        inputBuffer.limit(0);
        outputBuffer.clear();

    }


    /**
     * Callback to write data from the buffer.
     */
    @Override
    protected void flush(boolean explicit) throws IOException {
        
        long socketRef = socket.getSocket().longValue();
        
        if (outputBuffer.position() > 0) {
            if (Socket.sendbb(socketRef, 0, outputBuffer.position()) < 0) {
                throw new IOException(sm.getString("ajpprocessor.failedsend"));
            }
            outputBuffer.clear();
        }
        // Send explicit flush message
        if (explicit && !finished) {
            if (Socket.send(socketRef, flushMessageArray, 0,
                    flushMessageArray.length) < 0) {
                throw new IOException(sm.getString("ajpprocessor.failedflush"));
            }
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
                if (outputBuffer.position() + thisTime +
                    Constants.H_SIZE + 4 > outputBuffer.capacity()) {
                    flush(false);
                }
                outputBuffer.put((byte) 0x41);
                outputBuffer.put((byte) 0x42);
                outputBuffer.putShort((short) (thisTime + 4));
                outputBuffer.put(Constants.JK_AJP13_SEND_BODY_CHUNK);
                outputBuffer.putShort((short) thisTime);
                outputBuffer.put(chunk.getBytes(), chunk.getOffset() + off, thisTime);
                outputBuffer.put((byte) 0x00);
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
