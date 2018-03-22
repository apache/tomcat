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
import java.nio.charset.StandardCharsets;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.Collections;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

import org.apache.coyote.ActionCode;
import org.apache.coyote.CloseNowException;
import org.apache.coyote.InputBuffer;
import org.apache.coyote.Request;
import org.apache.coyote.Response;
import org.apache.coyote.http11.HttpOutputBuffer;
import org.apache.coyote.http11.OutputFilter;
import org.apache.coyote.http2.HpackDecoder.HeaderEmitter;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.buf.MessageBytes;
import org.apache.tomcat.util.http.MimeHeaders;
import org.apache.tomcat.util.http.parser.Host;
import org.apache.tomcat.util.net.ApplicationBufferHandler;
import org.apache.tomcat.util.res.StringManager;

class Stream extends AbstractStream implements HeaderEmitter {

    private static final Log log = LogFactory.getLog(Stream.class);
    private static final StringManager sm = StringManager.getManager(Stream.class);

    private static final int HEADER_STATE_START = 0;
    private static final int HEADER_STATE_PSEUDO = 1;
    private static final int HEADER_STATE_REGULAR = 2;
    private static final int HEADER_STATE_TRAILER = 3;

    private static final MimeHeaders ACK_HEADERS;

    static {
        Response response =  new Response();
        response.setStatus(100);
        StreamProcessor.prepareHeaders(null, response, true, null, null);
        ACK_HEADERS = response.getMimeHeaders();
    }

    private volatile int weight = Constants.DEFAULT_WEIGHT;
    private volatile long contentLengthReceived = 0;

    private final Http2UpgradeHandler handler;
    private final StreamStateMachine state;
    // State machine would be too much overhead
    private int headerState = HEADER_STATE_START;
    private StreamException headerException = null;
    // TODO: null these when finished to reduce memory used by closed stream
    private final Request coyoteRequest;
    private StringBuilder cookieHeader = null;
    private final Response coyoteResponse = new Response();
    private final StreamInputBuffer inputBuffer;
    private final StreamOutputBuffer streamOutputBuffer = new StreamOutputBuffer();
    private final Http2OutputBuffer http2OutputBuffer =
            new Http2OutputBuffer(coyoteResponse, streamOutputBuffer);


    Stream(Integer identifier, Http2UpgradeHandler handler) {
        this(identifier, handler, null);
    }


    Stream(Integer identifier, Http2UpgradeHandler handler, Request coyoteRequest) {
        super(identifier);
        this.handler = handler;
        handler.addChild(this);
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
            state.receivedEndOfStream();
        }
        this.coyoteRequest.setSendfile(handler.hasAsyncIO() && handler.getProtocol().getUseSendfile());
        this.coyoteResponse.setOutputBuffer(http2OutputBuffer);
        this.coyoteRequest.setResponse(coyoteResponse);
        this.coyoteRequest.protocol().setString("HTTP/2.0");
        if (this.coyoteRequest.getStartTime() < 0) {
            this.coyoteRequest.setStartTime(System.currentTimeMillis());
        }
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
            // Cast is always safe since any descendant of this stream must be
            // an instance of Stream
            getParentStream().addChild((Stream) parent);
        }

        if (exclusive) {
            // Need to move children of the new parent to be children of this
            // stream. Slightly convoluted to avoid concurrent modification.
            Iterator<Stream> parentsChildren = parent.getChildStreams().iterator();
            while (parentsChildren.hasNext()) {
                Stream parentsChild = parentsChildren.next();
                parentsChildren.remove();
                this.addChild(parentsChild);
            }
        }
        detachFromParent();
        parent.addChild(this);
        this.weight = weight;
    }


    /*
     * Used when removing closed streams from the tree and we know there is no
     * need to check for circular references.
     */
    final void rePrioritise(AbstractStream parent, int weight) {
        if (log.isDebugEnabled()) {
            log.debug(sm.getString("stream.reprioritisation.debug",
                    getConnectionId(), getIdentifier(), Boolean.FALSE,
                    parent.getIdentifier(), Integer.toString(weight)));
        }

        parent.addChild(this);
        this.weight = weight;
    }


    final void receiveReset(long errorCode) {
        if (log.isDebugEnabled()) {
            log.debug(sm.getString("stream.reset.receive", getConnectionId(), getIdentifier(),
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


    final synchronized int reserveWindowSize(int reservation, boolean block)
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
    public final void emitHeader(String name, String value) throws HpackException {
        if (log.isDebugEnabled()) {
            log.debug(sm.getString("stream.header.debug", getConnectionId(), getIdentifier(),
                    name, value));
        }

        // Header names must be lower case
        if (!name.toLowerCase(Locale.US).equals(name)) {
            throw new HpackException(sm.getString("stream.header.case",
                    getConnectionId(), getIdentifier(), name));
        }

        if ("connection".equals(name)) {
            throw new HpackException(sm.getString("stream.header.connection",
                    getConnectionId(), getIdentifier()));
        }

        if ("te".equals(name)) {
            if (!"trailers".equals(value)) {
                throw new HpackException(sm.getString("stream.header.te",
                        getConnectionId(), getIdentifier(), value));
            }
        }

        if (headerException != null) {
            // Don't bother processing the header since the stream is going to
            // be reset anyway
            return;
        }

        boolean pseudoHeader = name.charAt(0) == ':';

        if (pseudoHeader && headerState != HEADER_STATE_PSEUDO) {
            headerException = new StreamException(sm.getString(
                    "stream.header.unexpectedPseudoHeader", getConnectionId(), getIdentifier(),
                    name), Http2Error.PROTOCOL_ERROR, getIdentifier().intValue());
            // No need for further processing. The stream will be reset.
            return;
        }

        if (headerState == HEADER_STATE_PSEUDO && !pseudoHeader) {
            headerState = HEADER_STATE_REGULAR;
        }

        switch(name) {
        case ":method": {
            if (coyoteRequest.method().isNull()) {
                coyoteRequest.method().setString(value);
            } else {
                throw new HpackException(sm.getString("stream.header.duplicate",
                        getConnectionId(), getIdentifier(), ":method" ));
            }
            break;
        }
        case ":scheme": {
            if (coyoteRequest.scheme().isNull()) {
                coyoteRequest.scheme().setString(value);
            } else {
                throw new HpackException(sm.getString("stream.header.duplicate",
                        getConnectionId(), getIdentifier(), ":scheme" ));
            }
            break;
        }
        case ":path": {
            if (!coyoteRequest.requestURI().isNull()) {
                throw new HpackException(sm.getString("stream.header.duplicate",
                        getConnectionId(), getIdentifier(), ":path" ));
            }
            if (value.length() == 0) {
                throw new HpackException(sm.getString("stream.header.noPath",
                        getConnectionId(), getIdentifier()));
            }
            int queryStart = value.indexOf('?');
            String uri;
            if (queryStart == -1) {
                uri = value;
            } else {
                uri = value.substring(0, queryStart);
                String query = value.substring(queryStart + 1);
                coyoteRequest.queryString().setString(query);
            }
            // Bug 61120. Set the URI as bytes rather than String so:
            // - any path parameters are correctly processed
            // - the normalization security checks are performed that prevent
            //   directory traversal attacks
            byte[] uriBytes = uri.getBytes(StandardCharsets.ISO_8859_1);
            coyoteRequest.requestURI().setBytes(uriBytes, 0, uriBytes.length);
            break;
        }
        case ":authority": {
            if (coyoteRequest.serverName().isNull()) {
                int i;
                try {
                    i = Host.parse(value);
                } catch (IllegalArgumentException iae) {
                    // Host value invalid
                    throw new HpackException(sm.getString("stream.header.invalid",
                            getConnectionId(), getIdentifier(), ":authority", value));
                }
                if (i > -1) {
                    coyoteRequest.serverName().setString(value.substring(0, i));
                    coyoteRequest.setServerPort(Integer.parseInt(value.substring(i + 1)));
                } else {
                    coyoteRequest.serverName().setString(value);
                }
            } else {
                throw new HpackException(sm.getString("stream.header.duplicate",
                        getConnectionId(), getIdentifier(), ":authority" ));
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
            if (headerState == HEADER_STATE_TRAILER &&
                    !handler.getProtocol().isTrailerHeaderAllowed(name)) {
                break;
            }
            if ("expect".equals(name) && "100-continue".equals(value)) {
                coyoteRequest.setExpectation(true);
            }
            if (pseudoHeader) {
                headerException = new StreamException(sm.getString(
                        "stream.header.unknownPseudoHeader", getConnectionId(), getIdentifier(),
                        name), Http2Error.PROTOCOL_ERROR, getIdentifier().intValue());
            }

            if (headerState == HEADER_STATE_TRAILER) {
                // HTTP/2 headers are already always lower case
                coyoteRequest.getTrailerFields().put(name, value);
            } else {
                coyoteRequest.getMimeHeaders().addValue(name).setString(value);
            }
        }
        }
    }


    @Override
    public void setHeaderException(StreamException streamException) {
        if (headerException == null) {
            headerException = streamException;
        }
    }


    @Override
    public void validateHeaders() throws StreamException {
        if (headerException == null) {
            return;
        }

        throw headerException;
    }


    final boolean receivedEndOfHeaders() throws ConnectionException {
        if (coyoteRequest.method().isNull() || coyoteRequest.scheme().isNull() ||
                coyoteRequest.requestURI().isNull()) {
            throw new ConnectionException(sm.getString("stream.header.required",
                    getConnectionId(), getIdentifier()), Http2Error.PROTOCOL_ERROR);
        }
        // Cookie headers need to be concatenated into a single header
        // See RFC 7540 8.1.2.5
        // Can only do this once the headers are fully received
        if (cookieHeader != null) {
            coyoteRequest.getMimeHeaders().addValue("cookie").setString(cookieHeader.toString());
        }
        return headerState == HEADER_STATE_REGULAR || headerState == HEADER_STATE_PSEUDO;
    }


    final void writeHeaders() throws IOException {
        boolean endOfStream = streamOutputBuffer.hasNoBody() &&
                coyoteResponse.getTrailerFields() == null;
        handler.writeHeaders(this, 0, coyoteResponse.getMimeHeaders(), endOfStream, Constants.DEFAULT_HEADERS_FRAME_SIZE);
    }


    final void addOutputFilter(OutputFilter filter) {
        http2OutputBuffer.addFilter(filter);
    }


    final void writeTrailers() throws IOException {
        Supplier<Map<String,String>> supplier = coyoteResponse.getTrailerFields();
        if (supplier == null) {
            // No supplier was set, end of stream will already have been sent
            return;
        }

        // We can re-use the MimeHeaders from the response since they have
        // already been processed by the encoder at this point
        MimeHeaders mimeHeaders = coyoteResponse.getMimeHeaders();
        mimeHeaders.recycle();

        Map<String,String> headerMap = supplier.get();
        if (headerMap == null) {
            headerMap = Collections.emptyMap();
        }

        // Copy the contents of the Map to the MimeHeaders
        // TODO: Is there benefit in refactoring this? Is MimeHeaders too
        //       heavyweight? Can we reduce the copy/conversions?
        for (Map.Entry<String, String> headerEntry : headerMap.entrySet()) {
            MessageBytes mb = mimeHeaders.addValue(headerEntry.getKey());
            mb.setString(headerEntry.getValue());
        }

        handler.writeHeaders(this, 0, mimeHeaders, true, Constants.DEFAULT_HEADERS_FRAME_SIZE);
    }


    final void writeAck() throws IOException {
        handler.writeHeaders(this, 0, ACK_HEADERS, false, Constants.DEFAULT_HEADERS_ACK_FRAME_SIZE);
    }


    @Override
    final String getConnectionId() {
        return handler.getConnectionId();
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
            handler.getHpackDecoder().setMaxHeaderCount(handler.getProtocol().getMaxHeaderCount());
            handler.getHpackDecoder().setMaxHeaderSize(handler.getProtocol().getMaxHeaderSize());
        } else if (headerState == HEADER_STATE_PSEUDO || headerState == HEADER_STATE_REGULAR) {
            // Trailer headers MUST include the end of stream flag
            if (headersEndStream) {
                headerState = HEADER_STATE_TRAILER;
                handler.getHpackDecoder().setMaxHeaderCount(
                        handler.getProtocol().getMaxTrailerCount());
                handler.getHpackDecoder().setMaxHeaderSize(
                        handler.getProtocol().getMaxTrailerSize());
            } else {
                throw new ConnectionException(sm.getString("stream.trailerHeader.noEndOfStream",
                        getConnectionId(), getIdentifier()), Http2Error.PROTOCOL_ERROR);
            }
        }
        // Parser will catch attempt to send a headers frame after the stream
        // has closed.
        state.receivedStartOfHeaders();
    }


    final void receivedData(int payloadSize) throws ConnectionException {
        contentLengthReceived += payloadSize;
        long contentLengthHeader = coyoteRequest.getContentLengthLong();
        if (contentLengthHeader > -1 && contentLengthReceived > contentLengthHeader) {
            throw new ConnectionException(sm.getString("stream.header.contentLength",
                    getConnectionId(), getIdentifier(), Long.valueOf(contentLengthHeader),
                    Long.valueOf(contentLengthReceived)), Http2Error.PROTOCOL_ERROR);
        }
    }


    final void receivedEndOfStream() throws ConnectionException {
        long contentLengthHeader = coyoteRequest.getContentLengthLong();
        if (contentLengthHeader > -1 && contentLengthReceived != contentLengthHeader) {
            throw new ConnectionException(sm.getString("stream.header.contentLength",
                    getConnectionId(), getIdentifier(), Long.valueOf(contentLengthHeader),
                    Long.valueOf(contentLengthReceived)), Http2Error.PROTOCOL_ERROR);
        }
        state.receivedEndOfStream();
        if (inputBuffer != null) {
            inputBuffer.notifyEof();
        }
    }


    final void sentEndOfStream() {
        streamOutputBuffer.endOfStreamSent = true;
        state.sentEndOfStream();
    }


    final boolean isReady() {
        return streamOutputBuffer.isReady();
    }


    final boolean flush(boolean block) throws IOException {
        return streamOutputBuffer.flush(block);
    }


    final StreamInputBuffer getInputBuffer() {
        return inputBuffer;
    }


    final HttpOutputBuffer getOutputBuffer() {
        return http2OutputBuffer;
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
                if (log.isDebugEnabled()) {
                    log.debug(sm.getString("stream.reset.send", getConnectionId(), getIdentifier(),
                            se.getError()));
                }
                state.sendReset();
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


    final void push(Request request) throws IOException {
        // Can only push when supported and from a peer initiated stream
        if (!isPushSupported() || getIdentifier().intValue() % 2 == 0) {
            return;
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
    }


    boolean isTrailerFieldsReady() {
        // Once EndOfStream has been received, canRead will be false
        return !state.canRead();
    }


    boolean isTrailerFieldsSupported() {
        return !streamOutputBuffer.endOfStreamSent;
    }


    private static void push(final Http2UpgradeHandler handler, final Request request,
            final Stream stream) throws IOException {
        if (org.apache.coyote.Constants.IS_SECURITY_ENABLED) {
            try {
                AccessController.doPrivileged(new PrivilegedPush(handler, request, stream));
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


    private static class PrivilegedPush implements PrivilegedExceptionAction<Void> {

        private final Http2UpgradeHandler handler;
        private final Request request;
        private final Stream stream;

        public PrivilegedPush(Http2UpgradeHandler handler, Request request,
                Stream stream) {
            this.handler = handler;
            this.request = request;
            this.stream = stream;
        }

        @Override
        public Void run() throws IOException {
            handler.push(request, stream);
            return null;
        }
    }


    class StreamOutputBuffer implements HttpOutputBuffer {

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
                    handler.writeBody(Stream.this, buffer, 0,
                            coyoteResponse.getTrailerFields() == null);
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
                            !writeInProgress && closed && left == connectionReservation &&
                            coyoteResponse.getTrailerFields() == null);
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

        @Override
        public final void end() throws IOException {
            closed = true;
            flush(true);
            writeTrailers();
        }

        /**
         * @return <code>true</code> if it is certain that the associated
         *         response has no body.
         */
        final boolean hasNoBody() {
            return ((written == 0) && closed);
        }

        @Override
        public void flush() throws IOException {
            flush(true);
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
                boolean canRead = false;
                while (inBuffer.position() == 0 && (canRead = isActive() && !isInputFinished())) {
                    // Need to block until some data is written
                    try {
                        if (log.isDebugEnabled()) {
                            log.debug(sm.getString("stream.inputBuffer.empty"));
                        }
                        inBuffer.wait();
                        if (reset) {
                            throw new IOException(sm.getString("stream.inputBuffer.reset"));
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
                } else if (!canRead) {
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
            if (inBuffer != null) {
                synchronized (inBuffer) {
                    readInterest = true;
                }
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

        private final void notifyEof() {
            if (inBuffer != null) {
                synchronized (inBuffer) {
                    inBuffer.notifyAll();
                }
            }
        }
    }
}
