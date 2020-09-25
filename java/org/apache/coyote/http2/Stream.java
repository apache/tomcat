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
import org.apache.tomcat.util.net.WriteBuffer;
import org.apache.tomcat.util.res.StringManager;

class Stream extends AbstractNonZeroStream implements HeaderEmitter {

    private static final Log log = LogFactory.getLog(Stream.class);
    private static final StringManager sm = StringManager.getManager(Stream.class);

    private static final int HEADER_STATE_START = 0;
    private static final int HEADER_STATE_PSEUDO = 1;
    private static final int HEADER_STATE_REGULAR = 2;
    private static final int HEADER_STATE_TRAILER = 3;

    private static final MimeHeaders ACK_HEADERS;

    private static final Integer HTTP_UPGRADE_STREAM = Integer.valueOf(1);

    static {
        Response response =  new Response();
        response.setStatus(100);
        StreamProcessor.prepareHeaders(null, response, true, null, null);
        ACK_HEADERS = response.getMimeHeaders();
    }

    private volatile long contentLengthReceived = 0;

    private final Http2UpgradeHandler handler;
    private final WindowAllocationManager allocationManager = new WindowAllocationManager(this);

    // State machine would be too much overhead
    private int headerState = HEADER_STATE_START;
    private StreamException headerException = null;

    // These will be set to null once the Stream closes to reduce the memory
    // footprint.
    private volatile Request coyoteRequest;
    private volatile StringBuilder cookieHeader = null;
    private volatile Response coyoteResponse = new Response();
    private volatile StreamInputBuffer inputBuffer;
    private volatile StreamOutputBuffer streamOutputBuffer = new StreamOutputBuffer();
    private volatile Http2OutputBuffer http2OutputBuffer =
            new Http2OutputBuffer(coyoteResponse, streamOutputBuffer);


    Stream(Integer identifier, Http2UpgradeHandler handler) {
        this(identifier, handler, null);
    }


    Stream(Integer identifier, Http2UpgradeHandler handler, Request coyoteRequest) {
        super(handler.getConnectionId(), identifier);
        this.handler = handler;
        handler.addChild(this);
        setWindowSize(handler.getRemoteSettings().getInitialWindowSize());
        if (coyoteRequest == null) {
            // HTTP/2 new request
            this.coyoteRequest = new Request();
            this.inputBuffer = new StreamInputBuffer();
            this.coyoteRequest.setInputBuffer(inputBuffer);
        } else {
            // HTTP/2 Push or HTTP/1.1 upgrade
            this.coyoteRequest = coyoteRequest;
            this.inputBuffer = null;
            // Headers have been read by this point
            state.receivedStartOfHeaders();
            if (HTTP_UPGRADE_STREAM.equals(identifier)) {
                // Populate coyoteRequest from headers (HTTP/1.1 only)
                try {
                    prepareRequest();
                } catch (IllegalArgumentException iae) {
                    // Something in the headers is invalid
                    // Set correct return status
                    coyoteResponse.setStatus(400);
                    // Set error flag. This triggers error processing rather than
                    // the normal mapping
                    coyoteResponse.setError();
                }
            }
            // TODO Assuming the body has been read at this point is not valid
            state.receivedEndOfStream();
        }
        this.coyoteRequest.setSendfile(handler.hasAsyncIO() && handler.getProtocol().getUseSendfile());
        this.coyoteResponse.setOutputBuffer(http2OutputBuffer);
        this.coyoteRequest.setResponse(coyoteResponse);
        this.coyoteRequest.protocol().setString("HTTP/2.0");
        if (this.coyoteRequest.getStartTimeNanos() < 0) {
            this.coyoteRequest.setStartTimeNanos(System.nanoTime());
        }
    }


    private void prepareRequest() {
        MessageBytes hostValueMB = coyoteRequest.getMimeHeaders().getUniqueValue("host");
        if (hostValueMB == null) {
            throw new IllegalArgumentException();
        }
        // This processing expects bytes. Server push will have used a String
        // to trigger a conversion if required.
        hostValueMB.toBytes();
        ByteChunk valueBC = hostValueMB.getByteChunk();
        byte[] valueB = valueBC.getBytes();
        int valueL = valueBC.getLength();
        int valueS = valueBC.getStart();

        int colonPos = Host.parse(hostValueMB);
        if (colonPos != -1) {
            int port = 0;
            for (int i = colonPos + 1; i < valueL; i++) {
                char c = (char) valueB[i + valueS];
                if (c < '0' || c > '9') {
                    throw new IllegalArgumentException();
                }
                port = port * 10 + c - '0';
            }
            coyoteRequest.setServerPort(port);

            // Only need to copy the host name up to the :
            valueL = colonPos;
        }

        // Extract the host name
        char[] hostNameC = new char[valueL];
        for (int i = 0; i < valueL; i++) {
            hostNameC[i] = (char) valueB[i + valueS];
        }
        coyoteRequest.serverName().setChars(hostNameC, 0, valueL);
    }


    final void receiveReset(long errorCode) {
        if (log.isDebugEnabled()) {
            log.debug(sm.getString("stream.reset.receive", getConnectionId(), getIdAsString(),
                    Long.toString(errorCode)));
        }
        // Set the new state first since read and write both check this
        state.receivedReset();
        // Reads wait internally so need to call a method to break the wait()
        if (inputBuffer != null) {
            inputBuffer.receiveReset();
        }
        cancelAllocationRequests();
    }


    final void cancelAllocationRequests() {
        allocationManager.notifyAny();
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
            allocationManager.notifyStream();
        }
    }


    final synchronized int reserveWindowSize(int reservation, boolean block)
            throws IOException {
        long windowSize = getWindowSize();
        while (windowSize < 1) {
            if (!canWrite()) {
                throw new CloseNowException(sm.getString("stream.notWritable",
                        getConnectionId(), getIdAsString()));
            }
            if (block) {
                try {
                    long writeTimeout = handler.getProtocol().getStreamWriteTimeout();
                    allocationManager.waitForStream(writeTimeout);
                    windowSize = getWindowSize();
                    if (windowSize == 0) {
                        doStreamCancel(sm.getString("stream.writeTimeout"), Http2Error.ENHANCE_YOUR_CALM);
                    }
                } catch (InterruptedException e) {
                    // Possible shutdown / rst or similar. Use an IOException to
                    // signal to the client that further I/O isn't possible for this
                    // Stream.
                    throw new IOException(e);
                }
            } else {
                allocationManager.waitForStreamNonBlocking();
                return 0;
            }
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


    void doStreamCancel(String msg, Http2Error error) throws CloseNowException {
        StreamException se = new StreamException(msg, error, getIdAsInt());
        // Prevent the application making further writes
        streamOutputBuffer.closed = true;
        // Prevent Tomcat's error handling trying to write
        coyoteResponse.setError();
        coyoteResponse.setErrorReported();
        // Trigger a reset once control returns to Tomcat
        streamOutputBuffer.reset = se;
        throw new CloseNowException(msg, se);
    }


    void waitForConnectionAllocation(long timeout) throws InterruptedException {
        allocationManager.waitForConnection(timeout);
    }


    void waitForConnectionAllocationNonBlocking() {
        allocationManager.waitForConnectionNonBlocking();
    }


    void notifyConnection() {
        allocationManager.notifyConnection();
    }


    @Override
    public final void emitHeader(String name, String value) throws HpackException {
        if (log.isDebugEnabled()) {
            log.debug(sm.getString("stream.header.debug", getConnectionId(), getIdAsString(),
                    name, value));
        }

        // Header names must be lower case
        if (!name.toLowerCase(Locale.US).equals(name)) {
            throw new HpackException(sm.getString("stream.header.case",
                    getConnectionId(), getIdAsString(), name));
        }

        if ("connection".equals(name)) {
            throw new HpackException(sm.getString("stream.header.connection",
                    getConnectionId(), getIdAsString()));
        }

        if ("te".equals(name)) {
            if (!"trailers".equals(value)) {
                throw new HpackException(sm.getString("stream.header.te",
                        getConnectionId(), getIdAsString(), value));
            }
        }

        if (headerException != null) {
            // Don't bother processing the header since the stream is going to
            // be reset anyway
            return;
        }

        if (name.length() == 0) {
            throw new HpackException(sm.getString("stream.header.empty",
                    getConnectionId(), getIdAsString()));
        }

        boolean pseudoHeader = name.charAt(0) == ':';

        if (pseudoHeader && headerState != HEADER_STATE_PSEUDO) {
            headerException = new StreamException(sm.getString(
                    "stream.header.unexpectedPseudoHeader", getConnectionId(), getIdAsString(),
                    name), Http2Error.PROTOCOL_ERROR, getIdAsInt());
            // No need for further processing. The stream will be reset.
            return;
        }

        if (headerState == HEADER_STATE_PSEUDO && !pseudoHeader) {
            headerState = HEADER_STATE_REGULAR;
        }

        switch (name) {
        case ":method": {
            if (coyoteRequest.method().isNull()) {
                coyoteRequest.method().setString(value);
            } else {
                throw new HpackException(sm.getString("stream.header.duplicate",
                        getConnectionId(), getIdAsString(), ":method" ));
            }
            break;
        }
        case ":scheme": {
            if (coyoteRequest.scheme().isNull()) {
                coyoteRequest.scheme().setString(value);
            } else {
                throw new HpackException(sm.getString("stream.header.duplicate",
                        getConnectionId(), getIdAsString(), ":scheme" ));
            }
            break;
        }
        case ":path": {
            if (!coyoteRequest.requestURI().isNull()) {
                throw new HpackException(sm.getString("stream.header.duplicate",
                        getConnectionId(), getIdAsString(), ":path" ));
            }
            if (value.length() == 0) {
                throw new HpackException(sm.getString("stream.header.noPath",
                        getConnectionId(), getIdAsString()));
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
                            getConnectionId(), getIdAsString(), ":authority", value));
                }
                if (i > -1) {
                    coyoteRequest.serverName().setString(value.substring(0, i));
                    coyoteRequest.setServerPort(Integer.parseInt(value.substring(i + 1)));
                } else {
                    coyoteRequest.serverName().setString(value);
                }
            } else {
                throw new HpackException(sm.getString("stream.header.duplicate",
                        getConnectionId(), getIdAsString(), ":authority" ));
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
                        "stream.header.unknownPseudoHeader", getConnectionId(), getIdAsString(),
                        name), Http2Error.PROTOCOL_ERROR, getIdAsInt());
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
                    getConnectionId(), getIdAsString()), Http2Error.PROTOCOL_ERROR);
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


    final Request getCoyoteRequest() {
        return coyoteRequest;
    }


    final Response getCoyoteResponse() {
        return coyoteResponse;
    }


    final ByteBuffer getInputByteBuffer() {
        // Avoid NPE if Stream has been closed on Stream specific thread
        StreamInputBuffer inputBuffer = this.inputBuffer;
        if (inputBuffer == null) {
            return null;
        }
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
                        getConnectionId(), getIdAsString()), Http2Error.PROTOCOL_ERROR);
            }
        }
        // Parser will catch attempt to send a headers frame after the stream
        // has closed.
        state.receivedStartOfHeaders();
    }


    final void receivedData(int payloadSize) throws ConnectionException {
        contentLengthReceived += payloadSize;
        Request coyoteRequest = this.coyoteRequest;
        // Avoid NPE if Stream has been closed on Stream specific thread
        if (coyoteRequest == null) {
            return;
        }
        long contentLengthHeader = coyoteRequest.getContentLengthLong();
        if (contentLengthHeader > -1 && contentLengthReceived > contentLengthHeader) {
            throw new ConnectionException(sm.getString("stream.header.contentLength",
                    getConnectionId(), getIdAsString(), Long.valueOf(contentLengthHeader),
                    Long.valueOf(contentLengthReceived)), Http2Error.PROTOCOL_ERROR);
        }
    }


    final void receivedEndOfStream() throws ConnectionException {
        if (isContentLengthInconsistent()) {
            throw new ConnectionException(sm.getString("stream.header.contentLength",
                    getConnectionId(), getIdAsString(),
                    Long.valueOf(coyoteRequest.getContentLengthLong()),
                    Long.valueOf(contentLengthReceived)), Http2Error.PROTOCOL_ERROR);
        }
        state.receivedEndOfStream();
        if (inputBuffer != null) {
            inputBuffer.notifyEof();
        }
    }


    final boolean isContentLengthInconsistent() {
        long contentLengthHeader = coyoteRequest.getContentLengthLong();
        if (contentLengthHeader > -1 && contentLengthReceived != contentLengthHeader) {
            return true;
        }
        return false;
    }


    final void sentHeaders() {
        state.sentHeaders();
    }


    final void sentEndOfStream() {
        streamOutputBuffer.endOfStreamSent = true;
        state.sentEndOfStream();
    }


    final boolean isReadyForWrite() {
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


    final void closeIfIdle() {
        state.closeIfIdle();
    }


    final boolean isInputFinished() {
        return !state.isFrameTypePermitted(FrameType.DATA);
    }


    final void close(Http2Exception http2Exception) {
        if (http2Exception instanceof StreamException) {
            try {
                StreamException se = (StreamException) http2Exception;
                if (log.isDebugEnabled()) {
                    log.debug(sm.getString("stream.reset.send", getConnectionId(), getIdAsString(),
                            se.getError()));
                }
                state.sendReset();
                cancelAllocationRequests();
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
        recycle();
    }


    /*
     * This method is called recycle for consistency with the rest of the Tomcat
     * code base. Currently, it calls the handler to replace this stream with an
     * implementation that uses less memory. It does not fully recycle the
     * Stream ready for re-use since Stream objects are not re-used. This is
     * useful because Stream instances are retained for a period after the
     * Stream closes.
     */
    final void recycle() {
        if (log.isDebugEnabled()) {
            log.debug(sm.getString("stream.recycle", getConnectionId(), getIdAsString()));
        }
        handler.replaceStream(this, new RecycledStream(getConnectionId(), getIdentifier(), state));
    }


    final boolean isPushSupported() {
        return handler.getRemoteSettings().getEnablePush();
    }


    final void push(Request request) throws IOException {
        // Can only push when supported and from a peer initiated stream
        if (!isPushSupported() || getIdAsInt() % 2 == 0) {
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


    StreamException getResetException() {
        return streamOutputBuffer.reset;
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


    class StreamOutputBuffer implements HttpOutputBuffer, WriteBuffer.Sink {

        private final ByteBuffer buffer = ByteBuffer.allocate(8 * 1024);
        private final WriteBuffer writeBuffer = new WriteBuffer(32 * 1024);
        // Flag that indicates that data was left over on a previous
        // non-blocking write. Once set, this flag stays set until all the data
        // has been written.
        private boolean dataLeft;
        private volatile long written = 0;
        private int streamReservation = 0;
        private volatile boolean closed = false;
        private volatile StreamException reset = null;
        private volatile boolean endOfStreamSent = false;

        /* The write methods are synchronized to ensure that only one thread at
         * a time is able to access the buffer. Without this protection, a
         * client that performed concurrent writes could corrupt the buffer.
         */

        @Override
        public final synchronized int doWrite(ByteBuffer chunk) throws IOException {
            if (closed) {
                throw new IllegalStateException(
                        sm.getString("stream.closed", getConnectionId(), getIdAsString()));
            }
            // chunk is always fully written
            int result = chunk.remaining();
            if (writeBuffer.isEmpty()) {
                int chunkLimit = chunk.limit();
                while (chunk.remaining() > 0) {
                    int thisTime = Math.min(buffer.remaining(), chunk.remaining());
                    chunk.limit(chunk.position() + thisTime);
                    buffer.put(chunk);
                    chunk.limit(chunkLimit);
                    if (chunk.remaining() > 0 && !buffer.hasRemaining()) {
                        // Only flush if we have more data to write and the buffer
                        // is full
                        if (flush(true, coyoteResponse.getWriteListener() == null)) {
                            writeBuffer.add(chunk);
                            dataLeft = true;
                            break;
                        }
                    }
                }
            } else {
                writeBuffer.add(chunk);
            }
            written += result;
            return result;
        }

        final synchronized boolean flush(boolean block) throws IOException {
            /*
             * Need to ensure that there is exactly one call to flush even when
             * there is no data to write.
             * Too few calls (i.e. zero) and the end of stream message is not
             * sent for a completed asynchronous write.
             * Too many calls and the end of stream message is sent too soon and
             * trailer headers are not sent.
             */
            boolean dataInBuffer = buffer.position() > 0;
            boolean flushed = false;

            if (dataInBuffer) {
                dataInBuffer = flush(false, block);
                flushed = true;
            }

            if (dataInBuffer) {
                dataLeft = true;
            } else {
                if (writeBuffer.isEmpty()) {
                    // Both buffer and writeBuffer are empty.
                    if (flushed) {
                        dataLeft = false;
                    } else {
                        dataLeft = flush(false, block);
                    }
                } else {
                    dataLeft = writeBuffer.write(this, block);
                }
            }

            return dataLeft;
        }

        private final synchronized boolean flush(boolean writeInProgress, boolean block)
                throws IOException {
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("stream.outputBuffer.flush.debug", getConnectionId(),
                        getIdAsString(), Integer.toString(buffer.position()),
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
                if (streamReservation == 0) {
                    streamReservation  = reserveWindowSize(left, block);
                    if (streamReservation == 0) {
                        // Must be non-blocking.
                        // Note: Can't add to the writeBuffer here as the write
                        // may originate from the writeBuffer.
                        buffer.compact();
                        return true;
                    }
                }
                while (streamReservation > 0) {
                    int connectionReservation =
                                handler.reserveWindowSize(Stream.this, streamReservation, block);
                    if (connectionReservation == 0) {
                        // Must be non-blocking.
                        // Note: Can't add to the writeBuffer here as the write
                        // may originate from the writeBuffer.
                        buffer.compact();
                        return true;
                    }
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
            // Bug 63682
            // Only want to return false if the window size is zero AND we are
            // already waiting for an allocation.
            if (getWindowSize() > 0 && allocationManager.isWaitingForStream() ||
                    handler.getWindowSize() > 0 && allocationManager.isWaitingForConnection() ||
                    dataLeft) {
                return false;
            } else {
                return true;
            }
        }

        @Override
        public final long getBytesWritten() {
            return written;
        }

        @Override
        public final void end() throws IOException {
            if (reset != null) {
                throw new CloseNowException(reset);
            }
            if (!closed) {
                closed = true;
                flush(true);
                writeTrailers();
            }
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
            /*
             * This method should only be called during blocking I/O. All the
             * Servlet API calls that end up here are illegal during
             * non-blocking I/O. Servlet 5.4.
             * However, the wording Servlet specification states that the
             * behaviour is undefined so we do the best we can which is to
             * perform a flush using blocking I/O or non-blocking I/O based
             * depending which is currently in use.
             */
            flush(getCoyoteResponse().getWriteListener() == null);
        }

        @Override
        public synchronized boolean writeFromBuffer(ByteBuffer src, boolean blocking) throws IOException {
            int chunkLimit = src.limit();
            while (src.remaining() > 0) {
                int thisTime = Math.min(buffer.remaining(), src.remaining());
                src.limit(src.position() + thisTime);
                buffer.put(src);
                src.limit(chunkLimit);
                if (flush(false, blocking)) {
                    return true;
                }
            }
            return false;
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
        private boolean resetReceived = false;

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

                        long readTimeout = handler.getProtocol().getStreamReadTimeout();
                        if (readTimeout < 0) {
                            inBuffer.wait();
                        } else {
                            inBuffer.wait(readTimeout);
                        }

                        if (resetReceived) {
                            throw new IOException(sm.getString("stream.inputBuffer.reset"));
                        }

                        if (inBuffer.position() == 0 && isActive() && !isInputFinished()) {
                            String msg = sm.getString("stream.inputBuffer.readTimeout");
                            StreamException se = new StreamException(
                                    msg, Http2Error.ENHANCE_YOUR_CALM, getIdAsInt());
                            // Trigger a reset once control returns to Tomcat
                            coyoteResponse.setError();
                            streamOutputBuffer.reset = se;
                            throw new CloseNowException(msg, se);
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


        final boolean isReadyForRead() {
            ensureBuffersExist();

            synchronized (this) {
                if (available() > 0) {
                    return true;
                }

                if (!isRequestBodyFullyRead()) {
                    readInterest = true;
                }

                return false;
            }
        }

        final synchronized boolean isRequestBodyFullyRead() {
            return (inBuffer == null || inBuffer.position() == 0) && isInputFinished();
        }


        @Override
        public final synchronized int available() {
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
                    resetReceived = true;
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
