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
package org.apache.coyote.http2;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.Iterator;

import org.apache.coyote.ActionCode;
import org.apache.coyote.CloseNowException;
import org.apache.coyote.InputBuffer;
import org.apache.coyote.OutputBuffer;
import org.apache.coyote.Request;
import org.apache.coyote.Response;
import org.apache.coyote.http2.HpackDecoder.HeaderEmitter;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.net.ApplicationBufferHandler;
import org.apache.tomcat.util.res.StringManager;

class Stream extends AbstractStream implements HeaderEmitter {

    private static final Log log = LogFactory.getLog(Stream.class);
    private static final StringManager sm = StringManager.getManager(Stream.class);

    private static final int HEADER_STATE_START = 0;
    private static final int HEADER_STATE_PSEUDO = 1;
    private static final int HEADER_STATE_REGULAR = 2;
    private static final int HEADER_STATE_TRAILER = 3;

    private static final Response ACK_RESPONSE = new Response();

    static {
        ACK_RESPONSE.setStatus(100);
    }

    private volatile int weight = Constants.DEFAULT_WEIGHT;

    private final Http2UpgradeHandler handler;
    private final StreamStateMachine state;
    // State machine would be too much overhead
    private int headerState = HEADER_STATE_START;
    private String headerStateErrorMsg = null;
    // TODO: null these when finished to reduce memory used by closed stream
    private final Request coyoteRequest;
    private StringBuilder cookieHeader = null;
    private final Response coyoteResponse = new Response();
    private final StreamInputBuffer inputBuffer;
    private final StreamOutputBuffer outputBuffer = new StreamOutputBuffer();


    Stream(Integer identifier, Http2UpgradeHandler handler) {
        this(identifier, handler, null);
    }


    Stream(Integer identifier, Http2UpgradeHandler handler, Request coyoteRequest) {
        super(identifier);
        this.handler = handler;
        setParentStream(handler);
        setWindowSize(handler.getRemoteSettings().getInitialWindowSize());
        state = new StreamStateMachine(this);
        if (coyoteRequest == null) {
            // HTTP/2 new request
            this.coyoteRequest = new Request();
            this.inputBuffer = new StreamInputBuffer();
            this.coyoteRequest.setInputBuffer(inputBuffer);
        } else {
            // HTTP/1.1 upgrade
            this.coyoteRequest = coyoteRequest;
            this.inputBuffer = null;
            // Headers have been populated by this point
            state.receivedStartOfHeaders();
            // TODO Assuming the body has been read at this point is not valid
            state.recievedEndOfStream();
        }
        // No sendfile for HTTP/2 (it is enabled by default in the request)
        this.coyoteRequest.setSendfile(false);
        this.coyoteResponse.setOutputBuffer(outputBuffer);
        this.coyoteRequest.setResponse(coyoteResponse);
        this.coyoteRequest.protocol().setString("HTTP/2.0");
        // Configure HTTP/2 limits
        this.coyoteRequest.getCookies().setLimit(handler.getMaxCookieCount());
    }


    final void rePrioritise(AbstractStream parent, boolean exclusive, int weight) {
        if (log.isDebugEnabled()) {
            log.debug(sm.getString("stream.reprioritisation.debug",
                    getConnectionId(), getIdentifier(), Boolean.toString(exclusive),
                    parent.getIdentifier(), Integer.toString(weight)));
        }

        // Check if new parent is a descendant of this stream
        if (isDescendant(parent)) {
            parent.detachFromParent();
            getParentStream().addChild(parent);
        }

        if (exclusive) {
            // Need to move children of the new parent to be children of this
            // stream. Slightly convoluted to avoid concurrent modification.
            Iterator<AbstractStream> parentsChildren = parent.getChildStreams().iterator();
            while (parentsChildren.hasNext()) {
                AbstractStream parentsChild = parentsChildren.next();
                parentsChildren.remove();
                this.addChild(parentsChild);
            }
        }
        parent.addChild(this);
        this.weight = weight;
    }


    final void receiveReset(long errorCode) {
        if (log.isDebugEnabled()) {
            log.debug(sm.getString("stream.reset.debug", getConnectionId(), getIdentifier(),
                    Long.toString(errorCode)));
        }
        // Set the new state first since read and write both check this
        state.receivedReset();
        // Reads wait internally so need to call a method to break the wait()
        if (inputBuffer != null) {
            inputBuffer.receiveReset();
        }
        // Writes wait on Stream so we can notify directly
        synchronized (this) {
            this.notifyAll();
        }
    }


    final void checkState(FrameType frameType) throws Http2Exception {
        state.checkFrameType(frameType);
    }


    @Override
    final synchronized void incrementWindowSize(int windowSizeIncrement) throws Http2Exception {
        // If this is zero then any thread that has been trying to write for
        // this stream will be waiting. Notify that thread it can continue. Use
        // notify all even though only one thread is waiting to be on the safe
        // side.
        boolean notify = getWindowSize() < 1;
        super.incrementWindowSize(windowSizeIncrement);
        if (notify && getWindowSize() > 0) {
            notifyAll();
        }
    }


    private final synchronized int reserveWindowSize(int reservation, boolean block)
            throws IOException {
        long windowSize = getWindowSize();
        while (windowSize < 1) {
            if (!canWrite()) {
                throw new CloseNowException(sm.getString("stream.notWritable",
                        getConnectionId(), getIdentifier()));
            }
            try {
                if (block) {
                    wait();
                } else {
                    return 0;
                }
            } catch (InterruptedException e) {
                // Possible shutdown / rst or similar. Use an IOException to
                // signal to the client that further I/O isn't possible for this
                // Stream.
                throw new IOException(e);
            }
            windowSize = getWindowSize();
        }
        int allocation;
        if (windowSize < reservation) {
            allocation = (int) windowSize;
        } else {
            allocation = reservation;
        }
        decrementWindowSize(allocation);
        return allocation;
    }


    @Override
    public final void emitHeader(String name, String value) {
        if (log.isDebugEnabled()) {
            log.debug(sm.getString("stream.header.debug", getConnectionId(), getIdentifier(),
                    name, value));
        }

        if (headerStateErrorMsg != null) {
            // Don't bother processing the header since the stream is going to
            // be reset anyway
            return;
        }

        boolean pseudoHeader = name.charAt(0) == ':';

        if (pseudoHeader && headerState != HEADER_STATE_PSEUDO) {
            headerStateErrorMsg = sm.getString("stream.header.unexpectedPseudoHeader",
                    getConnectionId(), getIdentifier(), name);
            // No need for further processing. The stream will be reset.
            return;
        }

        if (headerState == HEADER_STATE_PSEUDO && !pseudoHeader) {
            headerState = HEADER_STATE_REGULAR;
        }

        switch(name) {
        case ":method": {
            coyoteRequest.method().setString(value);
            break;
        }
        case ":scheme": {
            coyoteRequest.scheme().setString(value);
            break;
        }
        case ":path": {
            int queryStart = value.indexOf('?');
            if (queryStart == -1) {
                coyoteRequest.requestURI().setString(value);
                coyoteRequest.decodedURI().setString(coyoteRequest.getURLDecoder().convert(value, false));
            } else {
                String uri = value.substring(0, queryStart);
                String query = value.substring(queryStart + 1);
                coyoteRequest.requestURI().setString(uri);
                coyoteRequest.decodedURI().setString(coyoteRequest.getURLDecoder().convert(uri, false));
                coyoteRequest.queryString().setString(coyoteRequest.getURLDecoder().convert(query, true));
            }
            break;
        }
        case ":authority": {
            int i = value.lastIndexOf(':');
            if (i > -1) {
                coyoteRequest.serverName().setString(value.substring(0, i));
                coyoteRequest.setServerPort(Integer.parseInt(value.substring(i + 1)));
            } else {
                coyoteRequest.serverName().setString(value);
            }
            break;
        }
        case "cookie": {
            // Cookie headers need to be concatenated into a single header
            // See RFC 7540 8.1.2.5
            if (cookieHeader == null) {
                cookieHeader = new StringBuilder();
            } else {
                cookieHeader.append("; ");
            }
            cookieHeader.append(value);
            break;
        }
        default: {
            if (headerState == HEADER_STATE_TRAILER && !handler.isTrailerHeaderAllowed(name)) {
                break;
            }
            if ("expect".equals(name) && "100-continue".equals(value)) {
                coyoteRequest.setExpectation(true);
            }
            if (pseudoHeader) {
                headerStateErrorMsg = sm.getString("stream.header.unknownPseudoHeader",
                        getConnectionId(), getIdentifier(), name);
            }
            // Assume other HTTP header
            coyoteRequest.getMimeHeaders().addValue(name).setString(value);
        }
        }
    }


    @Override
    public void validateHeaders() throws StreamException {
        if (headerStateErrorMsg == null) {
            return;
        }

        throw new StreamException(headerStateErrorMsg, Http2Error.PROTOCOL_ERROR,
                getIdentifier().intValue());
    }


    final boolean receivedEndOfHeaders() {
        // Cookie headers need to be concatenated into a single header
        // See RFC 7540 8.1.2.5
        // Can only do this once the headers are fully received
        if (cookieHeader != null) {
            coyoteRequest.getMimeHeaders().addValue("cookie").setString(cookieHeader.toString());
        }
        return headerState == HEADER_STATE_REGULAR || headerState == HEADER_STATE_PSEUDO;
    }


    final void writeHeaders() throws IOException {
        // TODO: Is 1k the optimal value?
        handler.writeHeaders(this, coyoteResponse, 1024);
    }

    final void writeAck() throws IOException {
        // TODO: Is 64 too big? Just the status header with compression
        handler.writeHeaders(this, ACK_RESPONSE, 64);
    }


    final void flushData() throws IOException {
        if (log.isDebugEnabled()) {
            log.debug(sm.getString("stream.write", getConnectionId(), getIdentifier()));
        }
        outputBuffer.flush(true);
    }


    @Override
    final String getConnectionId() {
        return getParentStream().getConnectionId();
    }


    @Override
    final int getWeight() {
        return weight;
    }


    final Request getCoyoteRequest() {
        return coyoteRequest;
    }


    final Response getCoyoteResponse() {
        return coyoteResponse;
    }


    final ByteBuffer getInputByteBuffer() {
        return inputBuffer.getInBuffer();
    }


    final void receivedStartOfHeaders(boolean headersEndStream) throws Http2Exception {
        if (headerState == HEADER_STATE_START) {
            headerState = HEADER_STATE_PSEUDO;
            handler.getHpackDecoder().setMaxHeaderCount(handler.getMaxHeaderCount());
            handler.getHpackDecoder().setMaxHeaderSize(handler.getMaxHeaderSize());
        } else if (headerState == HEADER_STATE_PSEUDO || headerState == HEADER_STATE_REGULAR) {
            // Trailer headers MUST include the end of stream flag
            if (headersEndStream) {
                headerState = HEADER_STATE_TRAILER;
                handler.getHpackDecoder().setMaxHeaderCount(handler.getMaxTrailerCount());
                handler.getHpackDecoder().setMaxHeaderSize(handler.getMaxTrailerSize());
            } else {
                throw new ConnectionException(sm.getString("stream.trialerHeader.noEndOfStream",
                        getConnectionId(), getIdentifier()), Http2Error.PROTOCOL_ERROR);
            }
        }
        // Parser will catch attempt to send a headers frame after the stream
        // has closed.
        state.receivedStartOfHeaders();
    }


    final void receivedEndOfStream() {
        synchronized (inputBuffer) {
            inputBuffer.notifyAll();
        }
        state.recievedEndOfStream();
    }


    final void sentEndOfStream() {
        outputBuffer.endOfStreamSent = true;
        state.sentEndOfStream();
    }


    final StreamInputBuffer getInputBuffer() {
        return inputBuffer;
    }


    final StreamOutputBuffer getOutputBuffer() {
        return outputBuffer;
    }


    final void sentPushPromise() {
        state.sentPushPromise();
    }


    final boolean isActive() {
        return state.isActive();
    }


    final boolean canWrite() {
        return state.canWrite();
    }


    final boolean isClosedFinal() {
        return state.isClosedFinal();
    }


    final void closeIfIdle() {
        state.closeIfIdle();
    }


    private final boolean isInputFinished() {
        return !state.isFrameTypePermitted(FrameType.DATA);
    }


    final void close(Http2Exception http2Exception) {
        if (http2Exception instanceof StreamException) {
            try {
                StreamException se = (StreamException) http2Exception;
                receiveReset(se.getError().getCode());
                handler.sendStreamReset(se);
            } catch (IOException ioe) {
                ConnectionException ce = new ConnectionException(
                        sm.getString("stream.reset.fail"), Http2Error.PROTOCOL_ERROR);
                ce.initCause(ioe);
                handler.closeConnection(ce);
            }
        } else {
            handler.closeConnection(http2Exception);
        }
    }


    final boolean isPushSupported() {
        return handler.getRemoteSettings().getEnablePush();
    }


    final boolean push(Request request) throws IOException {
        if (!isPushSupported()) {
            return false;
        }
        // Set the special HTTP/2 headers
        request.getMimeHeaders().addValue(":method").duplicate(request.method());
        request.getMimeHeaders().addValue(":scheme").duplicate(request.scheme());
        StringBuilder path = new StringBuilder(request.requestURI().toString());
        if (!request.queryString().isNull()) {
            path.append('?');
            path.append(request.queryString().toString());
        }
        request.getMimeHeaders().addValue(":path").setString(path.toString());

        // Authority needs to include the port only if a non-standard port is
        // being used.
        if (!(request.scheme().equals("http") && request.getServerPort() == 80) &&
                !(request.scheme().equals("https") && request.getServerPort() == 443)) {
            request.getMimeHeaders().addValue(":authority").setString(
                    request.serverName().getString() + ":" + request.getServerPort());
        } else {
            request.getMimeHeaders().addValue(":authority").duplicate(request.serverName());
        }

        push(handler, request, this);

        return true;
    }


    private static void push(final Http2UpgradeHandler handler, final Request request,
            final Stream stream) throws IOException {
        if (org.apache.coyote.Constants.IS_SECURITY_ENABLED) {
            try {
                AccessController.doPrivileged(
                        new PrivilegedExceptionAction<Void>() {
                            @Override
                            public Void run() throws IOException {
                                handler.push(request, stream);
                                return null;
                            }
                        });
            } catch (PrivilegedActionException ex) {
                Exception e = ex.getException();
                if (e instanceof IOException) {
                    throw (IOException) e;
                } else {
                    throw new IOException(ex);
                }
            }

        } else {
            handler.push(request, stream);
        }
    }

    class StreamOutputBuffer implements OutputBuffer {

        private final ByteBuffer buffer = ByteBuffer.allocate(8 * 1024);
        private volatile long written = 0;
        private volatile boolean closed = false;
        private volatile boolean endOfStreamSent = false;

        /* The write methods are synchronized to ensure that only one thread at
         * a time is able to access the buffer. Without this protection, a
         * client that performed concurrent writes could corrupt the buffer.
         */

        @Override
        public final synchronized int doWrite(ByteBuffer chunk) throws IOException {
            if (closed) {
                throw new IllegalStateException(
                        sm.getString("stream.closed", getConnectionId(), getIdentifier()));
            }
            if (!coyoteResponse.isCommitted()) {
                coyoteResponse.sendHeaders();
            }
            int chunkLimit = chunk.limit();
            int offset = 0;
            while (chunk.remaining() > 0) {
                int thisTime = Math.min(buffer.remaining(), chunk.remaining());
                chunk.limit(chunk.position() + thisTime);
                buffer.put(chunk);
                chunk.limit(chunkLimit);
                offset += thisTime;
                if (chunk.remaining() > 0 && !buffer.hasRemaining()) {
                    // Only flush if we have more data to write and the buffer
                    // is full
                    if (flush(true, coyoteResponse.getWriteListener() == null)) {
                        break;
                    }
                }
            }
            written += offset;
            return offset;
        }

        final synchronized boolean flush(boolean block) throws IOException {
            return flush(false, block);
        }

        private final synchronized boolean flush(boolean writeInProgress, boolean block)
                throws IOException {
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("stream.outputBuffer.flush.debug", getConnectionId(),
                        getIdentifier(), Integer.toString(buffer.position()),
                        Boolean.toString(writeInProgress), Boolean.toString(closed)));
            }
            if (buffer.position() == 0) {
                if (closed && !endOfStreamSent) {
                    // Handling this special case here is simpler than trying
                    // to modify the following code to handle it.
                    handler.writeBody(Stream.this, buffer, 0, true);
                }
                // Buffer is empty. Nothing to do.
                return false;
            }
            buffer.flip();
            int left = buffer.remaining();
            while (left > 0) {
                int streamReservation  = reserveWindowSize(left, block);
                if (streamReservation == 0) {
                    // Must be non-blocking
                    buffer.compact();
                    return true;
                }
                while (streamReservation > 0) {
                    int connectionReservation =
                                handler.reserveWindowSize(Stream.this, streamReservation);
                    // Do the write
                    handler.writeBody(Stream.this, buffer, connectionReservation,
                            !writeInProgress && closed && left == connectionReservation);
                    streamReservation -= connectionReservation;
                    left -= connectionReservation;
                }
            }
            buffer.clear();
            return false;
        }

        final synchronized boolean isReady() {
            if (getWindowSize() > 0 && handler.getWindowSize() > 0) {
                return true;
            } else {
                return false;
            }
        }

        @Override
        public final long getBytesWritten() {
            return written;
        }

        final void close() throws IOException {
            closed = true;
            flushData();
        }

        /**
         * @return <code>true</code> if it is certain that the associated
         *         response has no body.
         */
        final boolean hasNoBody() {
            return ((written == 0) && closed);
        }
    }


    class StreamInputBuffer implements InputBuffer {

        /* Two buffers are required to avoid various multi-threading issues.
         * These issues arise from the fact that the Stream (or the
         * Request/Response) used by the application is processed in one thread
         * but the connection is processed in another. Therefore it is possible
         * that a request body frame could be received before the application
         * is ready to read it. If it isn't buffered, processing of the
         * connection (and hence all streams) would block until the application
         * read the data. Hence the incoming data has to be buffered.
         * If only one buffer was used then it could become corrupted if the
         * connection thread is trying to add to it at the same time as the
         * application is read it. While it should be possible to avoid this
         * corruption by careful use of the buffer it would still require the
         * same copies as using two buffers and the behaviour would be less
         * clear.
         *
         * The buffers are created lazily because they quickly add up to a lot
         * of memory and most requests do not have bodies.
         */
        // This buffer is used to populate the ByteChunk passed in to the read
        // method
        private byte[] outBuffer;
        // This buffer is the destination for incoming data. It is normally is
        // 'write mode'.
        private volatile ByteBuffer inBuffer;
        private volatile boolean readInterest;
        private boolean reset = false;

        @Override
        public final int doRead(ApplicationBufferHandler applicationBufferHandler)
                throws IOException {

            ensureBuffersExist();

            int written = -1;

            // Ensure that only one thread accesses inBuffer at a time
            synchronized (inBuffer) {
                while (inBuffer.position() == 0 && !isInputFinished()) {
                    // Need to block until some data is written
                    try {
                        if (log.isDebugEnabled()) {
                            log.debug(sm.getString("stream.inputBuffer.empty"));
                        }
                        inBuffer.wait();
                        if (reset) {
                            // TODO: i18n
                            throw new IOException("HTTP/2 Stream reset");
                        }
                    } catch (InterruptedException e) {
                        // Possible shutdown / rst or similar. Use an
                        // IOException to signal to the client that further I/O
                        // isn't possible for this Stream.
                        throw new IOException(e);
                    }
                }

                if (inBuffer.position() > 0) {
                    // Data is available in the inBuffer. Copy it to the
                    // outBuffer.
                    inBuffer.flip();
                    written = inBuffer.remaining();
                    if (log.isDebugEnabled()) {
                        log.debug(sm.getString("stream.inputBuffer.copy",
                                Integer.toString(written)));
                    }
                    inBuffer.get(outBuffer, 0, written);
                    inBuffer.clear();
                } else if (isInputFinished()) {
                    return -1;
                } else {
                    // Should never happen
                    throw new IllegalStateException();
                }
            }

            applicationBufferHandler.setByteBuffer(ByteBuffer.wrap(outBuffer, 0,  written));

            // Increment client-side flow control windows by the number of bytes
            // read
            handler.writeWindowUpdate(Stream.this, written, true);

            return written;
        }


        final void registerReadInterest() {
            synchronized (inBuffer) {
                readInterest = true;
            }
        }


        final synchronized boolean isRequestBodyFullyRead() {
            return (inBuffer == null || inBuffer.position() == 0) && isInputFinished();
        }


        final synchronized int available() {
            if (inBuffer == null) {
                return 0;
            }
            return inBuffer.position();
        }


        /*
         * Called after placing some data in the inBuffer.
         */
        final synchronized boolean onDataAvailable() {
            if (readInterest) {
                if (log.isDebugEnabled()) {
                    log.debug(sm.getString("stream.inputBuffer.dispatch"));
                }
                readInterest = false;
                coyoteRequest.action(ActionCode.DISPATCH_READ, null);
                // Always need to dispatch since this thread is processing
                // the incoming connection and streams are processed on their
                // own.
                coyoteRequest.action(ActionCode.DISPATCH_EXECUTE, null);
                return true;
            } else {
                if (log.isDebugEnabled()) {
                    log.debug(sm.getString("stream.inputBuffer.signal"));
                }
                synchronized (inBuffer) {
                    inBuffer.notifyAll();
                }
                return false;
            }
        }


        private final ByteBuffer getInBuffer() {
            ensureBuffersExist();
            return inBuffer;
        }


        final synchronized void insertReplayedBody(ByteChunk body) {
            inBuffer = ByteBuffer.wrap(body.getBytes(),  body.getOffset(),  body.getLength());
        }


        private final void ensureBuffersExist() {
            if (inBuffer == null) {
                // The client must obey Tomcat's window size when sending so
                // this is the initial window size set by Tomcat that the client
                // uses (i.e. the local setting is required here).
                int size = handler.getLocalSettings().getInitialWindowSize();
                synchronized (this) {
                    if (inBuffer == null) {
                        inBuffer = ByteBuffer.allocate(size);
                        outBuffer = new byte[size];
                    }
                }
            }
        }


        private final void receiveReset() {
            if (inBuffer != null) {
                synchronized (inBuffer) {
                    reset = true;
                    inBuffer.notifyAll();
                }
            }
        }
    }
}
