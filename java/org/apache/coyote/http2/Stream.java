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
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.coyote.ActionCode;
import org.apache.coyote.CloseNowException;
import org.apache.coyote.InputBuffer;
import org.apache.coyote.Request;
import org.apache.coyote.Response;
import org.apache.coyote.http11.HttpOutputBuffer;
import org.apache.coyote.http11.OutputFilter;
import org.apache.coyote.http11.filters.SavedRequestInputFilter;
import org.apache.coyote.http11.filters.VoidOutputFilter;
import org.apache.coyote.http2.HpackDecoder.HeaderEmitter;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.buf.MessageBytes;
import org.apache.tomcat.util.http.MimeHeaders;
import org.apache.tomcat.util.http.parser.Host;
import org.apache.tomcat.util.http.parser.Priority;
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

    private static final Set<String> HTTP_CONNECTION_SPECIFIC_HEADERS = new HashSet<>();

    static {
        Response response = new Response();
        response.setStatus(100);
        StreamProcessor.prepareHeaders(null, response, true, null, null);
        ACK_HEADERS = response.getMimeHeaders();

        HTTP_CONNECTION_SPECIFIC_HEADERS.add("connection");
        HTTP_CONNECTION_SPECIFIC_HEADERS.add("proxy-connection");
        HTTP_CONNECTION_SPECIFIC_HEADERS.add("keep-alive");
        HTTP_CONNECTION_SPECIFIC_HEADERS.add("transfer-encoding");
        HTTP_CONNECTION_SPECIFIC_HEADERS.add("upgrade");
    }

    private volatile long contentLengthReceived = 0;

    private final Http2UpgradeHandler handler;
    private final WindowAllocationManager allocationManager = new WindowAllocationManager(this);
    private final Request coyoteRequest;
    private final Response coyoteResponse;
    private final StreamInputBuffer inputBuffer;
    private final StreamOutputBuffer streamOutputBuffer = new StreamOutputBuffer();
    private final Http2OutputBuffer http2OutputBuffer;
    private final AtomicBoolean removedFromActiveCount = new AtomicBoolean(false);

    // State machine would be too much overhead
    private int headerState = HEADER_STATE_START;
    private StreamException headerException = null;

    private volatile StringBuilder cookieHeader = null;
    private volatile boolean hostHeaderSeen = false;

    private Object pendingWindowUpdateForStreamLock = new Object();
    private int pendingWindowUpdateForStream = 0;

    private volatile int urgency = Priority.DEFAULT_URGENCY;
    private volatile boolean incremental = Priority.DEFAULT_INCREMENTAL;

    private final Object recycledLock = new Object();
    private volatile boolean recycled = false;


    Stream(Integer identifier, Http2UpgradeHandler handler) {
        this(identifier, handler, null);
    }


    Stream(Integer identifier, Http2UpgradeHandler handler, Request coyoteRequest) {
        super(handler.getConnectionId(), identifier);
        this.handler = handler;
        setWindowSize(handler.getRemoteSettings().getInitialWindowSize());

        if (coyoteRequest == null) {
            // HTTP/2 new request
            this.coyoteRequest = handler.getProtocol().popRequestAndResponse();
            this.coyoteResponse = this.coyoteRequest.getResponse();
            this.inputBuffer = new StandardStreamInputBuffer();
            this.coyoteRequest.setInputBuffer(inputBuffer);
        } else {
            // HTTP/1.1 upgrade
            /*
             * Implementation note. The request passed in is always newly created so it is safe to recycle it for re-use
             * in the Stream.recyle() method. Need to create a matching, new response.
             */
            this.coyoteRequest = coyoteRequest;
            this.coyoteResponse = new Response();
            this.coyoteRequest.setResponse(coyoteResponse);
            this.inputBuffer =
                    new SavedRequestStreamInputBuffer((SavedRequestInputFilter) this.coyoteRequest.getInputBuffer());
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
            // Request body, if any, has been read and buffered
            state.receivedEndOfStream();
        }
        this.coyoteRequest.setSendfile(handler.hasAsyncIO() && handler.getProtocol().getUseSendfile());
        http2OutputBuffer = new Http2OutputBuffer(this.coyoteResponse, streamOutputBuffer);
        this.coyoteResponse.setOutputBuffer(http2OutputBuffer);
        this.coyoteRequest.setResponse(coyoteResponse);
        this.coyoteRequest.protocol().setString("HTTP/2.0");
        this.coyoteRequest.setStartTimeNanos(System.nanoTime());
    }


    private void prepareRequest() {
        if (coyoteRequest.scheme().isNull()) {
            if (handler.getProtocol().getHttp11Protocol().isSSLEnabled()) {
                coyoteRequest.scheme().setString("https");
            } else {
                coyoteRequest.scheme().setString("http");
            }
        }
        MessageBytes hostValueMB = coyoteRequest.getMimeHeaders().getUniqueValue("host");
        if (hostValueMB == null) {
            throw new IllegalArgumentException();
        }
        // This processing expects bytes. Trigger a conversion if required.
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
        if (log.isTraceEnabled()) {
            log.trace(
                    sm.getString("stream.reset.receive", getConnectionId(), getIdAsString(), Long.toString(errorCode)));
        }
        // Set the new state first since read and write both check this
        state.receivedReset();
        // Reads wait internally so need to call a method to break the wait()
        inputBuffer.receiveReset();
        cancelAllocationRequests();
    }


    final void cancelAllocationRequests() {
        allocationManager.notifyAny();
    }


    @Override
    final void incrementWindowSize(int windowSizeIncrement) throws Http2Exception {
        windowAllocationLock.lock();
        try {
            // If this is zero then any thread that has been trying to write for
            // this stream will be waiting. Notify that thread it can continue. Use
            // notify all even though only one thread is waiting to be on the safe
            // side.
            boolean notify = getWindowSize() < 1;
            super.incrementWindowSize(windowSizeIncrement);
            if (notify && getWindowSize() > 0) {
                allocationManager.notifyStream();
            }
        } finally {
            windowAllocationLock.unlock();
        }
    }


    final int reserveWindowSize(int reservation, boolean block) throws IOException {
        windowAllocationLock.lock();
        try {
            long windowSize = getWindowSize();
            while (windowSize < 1) {
                if (!canWrite()) {
                    throw new CloseNowException(sm.getString("stream.notWritable", getConnectionId(), getIdAsString()));
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
        } finally {
            windowAllocationLock.unlock();
        }
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
        if (log.isTraceEnabled()) {
            log.trace(sm.getString("stream.header.debug", getConnectionId(), getIdAsString(), name, value));
        }

        // Header names must be lower case
        if (!name.toLowerCase(Locale.US).equals(name)) {
            throw new HpackException(sm.getString("stream.header.case", getConnectionId(), getIdAsString(), name));
        }

        if (HTTP_CONNECTION_SPECIFIC_HEADERS.contains(name)) {
            throw new HpackException(
                    sm.getString("stream.header.connection", getConnectionId(), getIdAsString(), name));
        }

        if ("te".equals(name)) {
            if (!"trailers".equals(value)) {
                throw new HpackException(sm.getString("stream.header.te", getConnectionId(), getIdAsString(), value));
            }
        }

        if (headerException != null) {
            // Don't bother processing the header since the stream is going to
            // be reset anyway
            return;
        }

        if (name.length() == 0) {
            throw new HpackException(sm.getString("stream.header.empty", getConnectionId(), getIdAsString()));
        }

        boolean pseudoHeader = name.charAt(0) == ':';

        if (pseudoHeader && headerState != HEADER_STATE_PSEUDO) {
            headerException = new StreamException(
                    sm.getString("stream.header.unexpectedPseudoHeader", getConnectionId(), getIdAsString(), name),
                    Http2Error.PROTOCOL_ERROR, getIdAsInt());
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
                    if ("HEAD".equals(value)) {
                        configureVoidOutputFilter();
                    }
                } else {
                    throw new HpackException(
                            sm.getString("stream.header.duplicate", getConnectionId(), getIdAsString(), ":method"));
                }
                break;
            }
            case ":scheme": {
                if (coyoteRequest.scheme().isNull()) {
                    coyoteRequest.scheme().setString(value);
                } else {
                    throw new HpackException(
                            sm.getString("stream.header.duplicate", getConnectionId(), getIdAsString(), ":scheme"));
                }
                break;
            }
            case ":path": {
                if (!coyoteRequest.requestURI().isNull()) {
                    throw new HpackException(
                            sm.getString("stream.header.duplicate", getConnectionId(), getIdAsString(), ":path"));
                }
                if (value.length() == 0) {
                    throw new HpackException(sm.getString("stream.header.noPath", getConnectionId(), getIdAsString()));
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
                // directory traversal attacks
                byte[] uriBytes = uri.getBytes(StandardCharsets.ISO_8859_1);
                coyoteRequest.requestURI().setBytes(uriBytes, 0, uriBytes.length);
                break;
            }
            case ":authority": {
                if (coyoteRequest.serverName().isNull()) {
                    parseAuthority(value, false);
                } else {
                    throw new HpackException(
                            sm.getString("stream.header.duplicate", getConnectionId(), getIdAsString(), ":authority"));
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
            case "host": {
                if (coyoteRequest.serverName().isNull()) {
                    // No :authority header. This is first host header. Use it.
                    hostHeaderSeen = true;
                    parseAuthority(value, true);
                } else if (!hostHeaderSeen) {
                    // First host header - must be consistent with :authority
                    hostHeaderSeen = true;
                    compareAuthority(value);
                } else {
                    // Multiple hosts headers - illegal
                    throw new HpackException(
                            sm.getString("stream.header.duplicate", getConnectionId(), getIdAsString(), "host"));
                }
                break;
            }
            case "priority": {
                try {
                    Priority p = Priority.parsePriority(new StringReader(value));
                    setUrgency(p.getUrgency());
                    setIncremental(p.getIncremental());
                } catch (IOException ioe) {
                    // Not possible with StringReader
                }
                break;
            }
            default: {
                if (headerState == HEADER_STATE_TRAILER && !handler.getProtocol().isTrailerHeaderAllowed(name)) {
                    break;
                }
                if ("expect".equals(name) && "100-continue".equals(value)) {
                    coyoteRequest.setExpectation(true);
                }
                if (pseudoHeader) {
                    headerException = new StreamException(
                            sm.getString("stream.header.unknownPseudoHeader", getConnectionId(), getIdAsString(), name),
                            Http2Error.PROTOCOL_ERROR, getIdAsInt());
                }

                if (headerState == HEADER_STATE_TRAILER) {
                    // HTTP/2 headers are already always lower case
                    coyoteRequest.getMimeTrailerFields().addValue(name).setString(value);
                } else {
                    coyoteRequest.getMimeHeaders().addValue(name).setString(value);
                }
            }
        }
    }


    void configureVoidOutputFilter() {
        addOutputFilter(new VoidOutputFilter());
        // Prevent further writes by the application
        streamOutputBuffer.closed = true;
    }

    private void parseAuthority(String value, boolean host) throws HpackException {
        int i;
        try {
            i = Host.parse(value);
        } catch (IllegalArgumentException iae) {
            // Host value invalid
            throw new HpackException(sm.getString("stream.header.invalid", getConnectionId(), getIdAsString(),
                    host ? "host" : ":authority", value));
        }
        if (i > -1) {
            coyoteRequest.serverName().setString(value.substring(0, i));
            coyoteRequest.setServerPort(Integer.parseInt(value.substring(i + 1)));
        } else {
            coyoteRequest.serverName().setString(value);
        }
    }


    private void compareAuthority(String value) throws HpackException {
        int i;
        try {
            i = Host.parse(value);
        } catch (IllegalArgumentException iae) {
            // Host value invalid
            throw new HpackException(
                    sm.getString("stream.header.invalid", getConnectionId(), getIdAsString(), "host", value));
        }
        if (i == -1 && (!value.equals(coyoteRequest.serverName().getString()) || coyoteRequest.getServerPort() != -1) ||
                i > -1 && ((!value.substring(0, i).equals(coyoteRequest.serverName().getString()) ||
                        Integer.parseInt(value.substring(i + 1)) != coyoteRequest.getServerPort()))) {
            // Host value inconsistent
            throw new HpackException(sm.getString("stream.host.inconsistent", getConnectionId(), getIdAsString(), value,
                    coyoteRequest.serverName().getString(), Integer.toString(coyoteRequest.getServerPort())));
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
        handler.getHpackDecoder().setHeaderEmitter(Http2UpgradeHandler.HEADER_SINK);
        throw headerException;
    }


    final boolean receivedEndOfHeaders() throws ConnectionException {
        if (coyoteRequest.method().isNull() || coyoteRequest.scheme().isNull() ||
                !coyoteRequest.method().equals("CONNECT") && coyoteRequest.requestURI().isNull()) {
            throw new ConnectionException(sm.getString("stream.header.required", getConnectionId(), getIdAsString()),
                    Http2Error.PROTOCOL_ERROR);
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
        boolean endOfStream = streamOutputBuffer.hasNoBody() && coyoteResponse.getTrailerFields() == null;
        handler.writeHeaders(this, coyoteResponse.getMimeHeaders(), endOfStream, Constants.DEFAULT_HEADERS_FRAME_SIZE);
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

        /*
         * Need a dedicated MimeHeaders for trailers as the MimeHeaders from the response needs to be retained in case
         * the access log needs to log header values.
         */
        MimeHeaders mimeHeaders = new MimeHeaders();

        Map<String,String> headerMap = supplier.get();
        if (headerMap == null) {
            headerMap = Collections.emptyMap();
        }

        // Copy the contents of the Map to the MimeHeaders
        // TODO: Is there benefit in refactoring this? Is MimeHeaders too
        // heavyweight? Can we reduce the copy/conversions?
        for (Map.Entry<String,String> headerEntry : headerMap.entrySet()) {
            MessageBytes mb = mimeHeaders.addValue(headerEntry.getKey());
            mb.setString(headerEntry.getValue());
        }

        handler.writeHeaders(this, mimeHeaders, true, Constants.DEFAULT_HEADERS_FRAME_SIZE);
    }


    final void writeAck() throws IOException {
        handler.writeHeaders(this, ACK_HEADERS, false, Constants.DEFAULT_HEADERS_ACK_FRAME_SIZE);
    }


    final void writeEarlyHints() throws IOException {
        MimeHeaders headers = coyoteResponse.getMimeHeaders();
        String originalStatus = headers.getHeader(":status");
        headers.setValue(":status").setString(Integer.toString(HttpServletResponse.SC_EARLY_HINTS));
        try {
            handler.writeHeaders(this, headers, false, Constants.DEFAULT_HEADERS_FRAME_SIZE);
        } finally {
            if (originalStatus == null) {
                headers.removeHeader(":status");
            } else {
                headers.setValue(":status").setString(originalStatus);
            }
        }
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


    @Override
    final ByteBuffer getInputByteBuffer(boolean create) {
        return inputBuffer.getInBuffer(create);
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
                handler.getHpackDecoder().setMaxHeaderCount(handler.getProtocol().getMaxTrailerCount());
                handler.getHpackDecoder().setMaxHeaderSize(handler.getProtocol().getMaxTrailerSize());
            } else {
                throw new ConnectionException(
                        sm.getString("stream.trailerHeader.noEndOfStream", getConnectionId(), getIdAsString()),
                        Http2Error.PROTOCOL_ERROR);
            }
        }
        // Parser will catch attempt to send a headers frame after the stream
        // has closed.
        state.receivedStartOfHeaders();
    }


    @Override
    final void receivedData(int payloadSize) throws Http2Exception {
        contentLengthReceived += payloadSize;
        long contentLengthHeader = coyoteRequest.getContentLengthLong();
        if (contentLengthHeader > -1 && contentLengthReceived > contentLengthHeader) {
            throw new ConnectionException(
                    sm.getString("stream.header.contentLength", getConnectionId(), getIdAsString(),
                            Long.valueOf(contentLengthHeader), Long.valueOf(contentLengthReceived)),
                    Http2Error.PROTOCOL_ERROR);
        }
    }


    final void receivedEndOfStream() throws ConnectionException {
        if (isContentLengthInconsistent()) {
            throw new ConnectionException(
                    sm.getString("stream.header.contentLength", getConnectionId(), getIdAsString(),
                            Long.valueOf(coyoteRequest.getContentLengthLong()), Long.valueOf(contentLengthReceived)),
                    Http2Error.PROTOCOL_ERROR);
        }
        state.receivedEndOfStream();
        inputBuffer.notifyEof();
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
                if (log.isTraceEnabled()) {
                    log.trace(sm.getString("stream.reset.send", getConnectionId(), getIdAsString(), se.getError()));
                }

                // Need to update state atomically with the sending of the RST
                // frame else other threads currently working with this stream
                // may see the state change and send a RST frame before the RST
                // frame triggered by this thread. If that happens the client
                // may see out of order RST frames which may hard to follow if
                // the client is unaware the RST frames may be received out of
                // order.
                handler.sendStreamReset(state, se);

                cancelAllocationRequests();
                inputBuffer.swallowUnread();
            } catch (IOException ioe) {
                ConnectionException ce =
                        new ConnectionException(sm.getString("stream.reset.fail", getConnectionId(), getIdAsString()),
                                Http2Error.PROTOCOL_ERROR, ioe);
                handler.closeConnection(ce);
            }
        } else {
            handler.closeConnection(http2Exception);
        }
        replace();
    }


    /*
     * This method calls the handler to replace this stream with an implementation that uses less memory. This is useful
     * because Stream instances are retained for a period after the Stream closes.
     */
    final void replace() {
        int remaining;
        // May be null if stream was closed before any DATA frames were processed.
        ByteBuffer inputByteBuffer = getInputByteBuffer(false);
        if (inputByteBuffer == null) {
            remaining = 0;
        } else {
            remaining = inputByteBuffer.remaining();
        }
        handler.replaceStream(this, new RecycledStream(getConnectionId(), getIdentifier(), state, remaining));
    }


    /*
     * This method is called recycle for consistency with the rest of the Tomcat code base. It does not recycle the
     * Stream since Stream objects are not re-used. It does recycle the request and response objects and ensures that
     * this is only done once.
     *
     * replace() should have been called before calling this method.
     *
     * It is important that this method is not called until any concurrent processing for the stream has completed. This
     * is currently achieved by:
     *
     * - only the StreamProcessor calls this method
     *
     * - the Http2UpgradeHandler does not call this method
     *
     * - this method is called once the StreamProcessor considers the Stream closed
     *
     * In theory, the protection against duplicate calls is not required in this method (the code in StreamProcessor
     * should be sufficient) but it is implemented as precaution along with the WARN level logging.
     */
    final void recycle() {
        if (recycled) {
            log.warn(sm.getString("stream.recycle.duplicate", getConnectionId(), getIdAsString()));
            return;
        }
        synchronized (recycledLock) {
            if (recycled) {
                log.warn(sm.getString("stream.recycle.duplicate", getConnectionId(), getIdAsString()));
                return;
            }
            recycled = true;
        }
        if (log.isTraceEnabled()) {
            log.trace(sm.getString("stream.recycle.first", getConnectionId(), getIdAsString()));
        }
        coyoteRequest.recycle();
        coyoteResponse.recycle();
        handler.getProtocol().pushRequestAndResponse(coyoteRequest);
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


    int getWindowUpdateSizeToWrite(int increment) {
        int result;
        int threshold = handler.getProtocol().getOverheadWindowUpdateThreshold();
        synchronized (pendingWindowUpdateForStreamLock) {
            if (increment > threshold) {
                result = increment + pendingWindowUpdateForStream;
                pendingWindowUpdateForStream = 0;
            } else {
                pendingWindowUpdateForStream += increment;
                if (pendingWindowUpdateForStream > threshold) {
                    result = pendingWindowUpdateForStream;
                    pendingWindowUpdateForStream = 0;
                } else {
                    result = 0;
                }
            }
        }
        return result;
    }


    public int getUrgency() {
        return urgency;
    }


    public void setUrgency(int urgency) {
        this.urgency = urgency;
    }


    public boolean getIncremental() {
        return incremental;
    }


    public void setIncremental(boolean incremental) {
        this.incremental = incremental;
    }


    int decrementAndGetActiveRemoteStreamCount() {
        /*
         * Protect against mis-counting of active streams. This method should only be called once per stream but since
         * the count of active streams is used to enforce the maximum concurrent streams limit, make sure each stream is
         * only removed from the active count exactly once.
         */
        if (removedFromActiveCount.compareAndSet(false, true)) {
            return handler.activeRemoteStreamCount.decrementAndGet();
        } else {
            return handler.activeRemoteStreamCount.get();
        }
    }


    class StreamOutputBuffer implements HttpOutputBuffer, WriteBuffer.Sink {

        private final Lock writeLock = new ReentrantLock();
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

        /*
         * The write methods share a common lock to ensure that only one thread at a time is able to access the buffer.
         * Without this protection, a client that performed concurrent writes could corrupt the buffer.
         */

        @Override
        public final int doWrite(ByteBuffer chunk) throws IOException {
            writeLock.lock();
            try {
                if (closed) {
                    throw new IOException(sm.getString("stream.closed", getConnectionId(), getIdAsString()));
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
            } finally {
                writeLock.unlock();
            }
        }

        final boolean flush(boolean block) throws IOException {
            writeLock.lock();
            try {
                /*
                 * Need to ensure that there is exactly one call to flush even when there is no data to write. Too few
                 * calls (i.e. zero) and the end of stream message is not sent for a completed asynchronous write. Too
                 * many calls and the end of stream message is sent too soon and trailer headers are not sent.
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
            } finally {
                writeLock.unlock();
            }
        }

        private boolean flush(boolean writeInProgress, boolean block) throws IOException {
            writeLock.lock();
            try {
                if (log.isTraceEnabled()) {
                    log.trace(sm.getString("stream.outputBuffer.flush.debug", getConnectionId(), getIdAsString(),
                            Integer.toString(buffer.position()), Boolean.toString(writeInProgress),
                            Boolean.toString(closed)));
                }
                if (buffer.position() == 0) {
                    if (closed && !endOfStreamSent) {
                        // Handling this special case here is simpler than trying
                        // to modify the following code to handle it.
                        handler.writeBody(Stream.this, buffer, 0, coyoteResponse.getTrailerFields() == null);
                    }
                    // Buffer is empty. Nothing to do.
                    return false;
                }
                buffer.flip();
                int left = buffer.remaining();
                while (left > 0) {
                    if (streamReservation == 0) {
                        streamReservation = reserveWindowSize(left, block);
                        if (streamReservation == 0) {
                            // Must be non-blocking.
                            // Note: Can't add to the writeBuffer here as the write
                            // may originate from the writeBuffer.
                            buffer.compact();
                            return true;
                        }
                    }
                    while (streamReservation > 0) {
                        int connectionReservation = handler.reserveWindowSize(Stream.this, streamReservation, block);
                        if (connectionReservation == 0) {
                            // Must be non-blocking.
                            // Note: Can't add to the writeBuffer here as the write
                            // may originate from the writeBuffer.
                            buffer.compact();
                            return true;
                        }
                        // Do the write
                        handler.writeBody(Stream.this, buffer, connectionReservation, !writeInProgress && closed &&
                                left == connectionReservation && coyoteResponse.getTrailerFields() == null);
                        streamReservation -= connectionReservation;
                        left -= connectionReservation;
                    }
                }
                buffer.clear();
                return false;
            } finally {
                writeLock.unlock();
            }
        }

        final boolean isReady() {
            writeLock.lock();
            try {
                // Bug 63682
                // Only want to return false if the window size is zero AND we are
                // already waiting for an allocation.
                if (getWindowSize() > 0 && allocationManager.isWaitingForStream() ||
                        handler.getWindowSize() > 0 && allocationManager.isWaitingForConnection() || dataLeft) {
                    return false;
                } else {
                    return true;
                }
            } finally {
                writeLock.unlock();
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
         * @return <code>true</code> if it is certain that the associated response has no body.
         */
        final boolean hasNoBody() {
            return ((written == 0) && closed);
        }

        @Override
        public void flush() throws IOException {
            /*
             * This method should only be called during blocking I/O. All the Servlet API calls that end up here are
             * illegal during non-blocking I/O. Servlet 5.4. However, the wording Servlet specification states that the
             * behaviour is undefined so we do the best we can which is to perform a flush using blocking I/O or
             * non-blocking I/O based depending which is currently in use.
             */
            flush(getCoyoteResponse().getWriteListener() == null);
        }

        @Override
        public boolean writeFromBuffer(ByteBuffer src, boolean blocking) throws IOException {
            writeLock.lock();
            try {
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
            } finally {
                writeLock.unlock();
            }
        }
    }


    abstract class StreamInputBuffer implements InputBuffer {

        abstract void receiveReset();

        abstract void swallowUnread() throws IOException;

        abstract void notifyEof();

        abstract ByteBuffer getInBuffer(boolean create);

        abstract void onDataAvailable() throws IOException;

        abstract boolean isReadyForRead();

        abstract boolean isRequestBodyFullyRead();

        abstract void insertReplayedBody(ByteChunk body);

        protected abstract boolean timeoutRead(long now);
    }


    class StandardStreamInputBuffer extends StreamInputBuffer {

        private final Lock readStateLock = new ReentrantLock();
        /*
         * Two buffers are required to avoid various multi-threading issues. These issues arise from the fact that the
         * Stream (or the Request/Response) used by the application is processed in one thread but the connection is
         * processed in another. Therefore it is possible that a request body frame could be received before the
         * application is ready to read it. If it isn't buffered, processing of the connection (and hence all streams)
         * would block until the application read the data. Hence the incoming data has to be buffered. If only one
         * buffer was used then it could become corrupted if the connection thread is trying to add to it at the same
         * time as the application is read it. While it should be possible to avoid this corruption by careful use of
         * the buffer it would still require the same copies as using two buffers and the behaviour would be less clear.
         *
         * The buffers are created lazily because they quickly add up to a lot of memory and most requests do not have
         * bodies.
         */
        // This buffer is used to populate the ByteChunk passed in to the read
        // method
        private byte[] outBuffer;
        // This buffer is the destination for incoming data. It is normally is
        // 'write mode'.
        private volatile ByteBuffer inBuffer;
        private volatile boolean readInterest;
        // If readInterest is true, data must be available to read no later than this time.
        private volatile long readTimeoutExpiry;
        private volatile boolean closed;
        private volatile boolean resetReceived;

        @Override
        public final int doRead(ApplicationBufferHandler applicationBufferHandler) throws IOException {

            ensureBuffersExist();

            int written = -1;

            // It is still possible that the stream has been closed and inBuffer
            // set to null between the call to ensureBuffersExist() above and
            // the sync below. The checks just before and just inside the sync
            // ensure we don't get any NPEs reported.
            ByteBuffer tmpInBuffer = inBuffer;
            if (tmpInBuffer == null) {
                return -1;
            }
            // Ensure that only one thread accesses inBuffer at a time
            synchronized (tmpInBuffer) {
                if (inBuffer == null) {
                    return -1;
                }
                boolean canRead = false;
                while (inBuffer.position() == 0 && (canRead = isActive() && !isInputFinished())) {
                    // Need to block until some data is written
                    try {
                        if (log.isTraceEnabled()) {
                            log.trace(sm.getString("stream.inputBuffer.empty"));
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
                            StreamException se = new StreamException(msg, Http2Error.ENHANCE_YOUR_CALM, getIdAsInt());
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
                    if (log.isTraceEnabled()) {
                        log.trace(sm.getString("stream.inputBuffer.copy", Integer.toString(written)));
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

            applicationBufferHandler.setByteBuffer(ByteBuffer.wrap(outBuffer, 0, written));

            // Increment client-side flow control windows by the number of bytes
            // read
            handler.writeWindowUpdate(Stream.this, written, true);

            return written;
        }


        @Override
        final boolean isReadyForRead() {
            ensureBuffersExist();

            readStateLock.lock();
            try {
                if (available() > 0) {
                    return true;
                }

                if (resetReceived) {
                    // Trigger ReadListener.onError()
                    getCoyoteRequest().setAttribute(RequestDispatcher.ERROR_EXCEPTION,
                            new IOException(sm.getString("stream.clientResetRequest")));
                    coyoteRequest.action(ActionCode.DISPATCH_ERROR, null);
                    coyoteRequest.action(ActionCode.DISPATCH_EXECUTE, null);

                    return false;
                }

                if (!isRequestBodyFullyRead()) {
                    readInterest = true;
                    long readTimeout = handler.getProtocol().getStreamReadTimeout();
                    if (readTimeout > 0) {
                        readTimeoutExpiry = System.currentTimeMillis() + readTimeout;
                    } else {
                        readTimeoutExpiry = Long.MAX_VALUE;
                    }
                }

                return false;
            } finally {
                readStateLock.unlock();
            }
        }

        @Override
        final boolean isRequestBodyFullyRead() {
            readStateLock.lock();
            try {
                return (inBuffer == null || inBuffer.position() == 0) && isInputFinished();
            } finally {
                readStateLock.unlock();
            }
        }


        @Override
        public final int available() {
            readStateLock.lock();
            try {
                if (inBuffer == null) {
                    return 0;
                }
                return inBuffer.position();
            } finally {
                readStateLock.unlock();
            }
        }


        /*
         * Called after placing some data in the inBuffer.
         */
        @Override
        final void onDataAvailable() throws IOException {
            readStateLock.lock();
            try {
                if (closed) {
                    swallowUnread();
                } else if (readInterest) {
                    if (log.isTraceEnabled()) {
                        log.trace(sm.getString("stream.inputBuffer.dispatch"));
                    }
                    readInterest = false;
                    coyoteRequest.action(ActionCode.DISPATCH_READ, null);
                    // Always need to dispatch since this thread is processing
                    // the incoming connection and streams are processed on their
                    // own.
                    coyoteRequest.action(ActionCode.DISPATCH_EXECUTE, null);
                } else {
                    if (log.isTraceEnabled()) {
                        log.trace(sm.getString("stream.inputBuffer.signal"));
                    }
                    synchronized (inBuffer) {
                        inBuffer.notifyAll();
                    }
                }
            } finally {
                readStateLock.unlock();
            }
        }


        @Override
        final ByteBuffer getInBuffer(boolean create) {
            if (create) {
                ensureBuffersExist();
            }
            return inBuffer;
        }


        @Override
        final void insertReplayedBody(ByteChunk body) {
            readStateLock.lock();
            try {
                inBuffer = ByteBuffer.wrap(body.getBytes(), body.getStart(), body.getLength());
            } finally {
                readStateLock.unlock();
            }
        }


        private void ensureBuffersExist() {
            if (inBuffer == null && !closed) {
                // The client must obey Tomcat's window size when sending so
                // this is the initial window size set by Tomcat that the client
                // uses (i.e. the local setting is required here).
                int size = handler.getLocalSettings().getInitialWindowSize();
                readStateLock.lock();
                try {
                    if (inBuffer == null && !closed) {
                        inBuffer = ByteBuffer.allocate(size);
                        outBuffer = new byte[size];
                    }
                } finally {
                    readStateLock.unlock();
                }
            }
        }


        @Override
        final void receiveReset() {
            if (inBuffer != null) {
                synchronized (inBuffer) {
                    resetReceived = true;
                    inBuffer.notifyAll();
                }
            }

            // If a read is in progress, cancel it.
            readStateLock.lock();
            try {
                if (readInterest) {
                    readInterest = false;
                }
            } finally {
                readStateLock.unlock();
            }

            // Trigger ReadListener.onError()
            getCoyoteRequest().setAttribute(RequestDispatcher.ERROR_EXCEPTION,
                    new IOException(sm.getString("stream.clientResetRequest")));
            coyoteRequest.action(ActionCode.DISPATCH_ERROR, null);
            coyoteRequest.action(ActionCode.DISPATCH_EXECUTE, null);
        }

        @Override
        final void notifyEof() {
            if (inBuffer != null) {
                synchronized (inBuffer) {
                    inBuffer.notifyAll();
                }
            }
        }

        @Override
        final void swallowUnread() throws IOException {
            readStateLock.lock();
            try {
                closed = true;
            } finally {
                readStateLock.unlock();
            }
            if (inBuffer != null) {
                int unreadByteCount = 0;
                synchronized (inBuffer) {
                    unreadByteCount = inBuffer.position();
                    if (log.isTraceEnabled()) {
                        log.trace(sm.getString("stream.inputBuffer.swallowUnread", Integer.valueOf(unreadByteCount)));
                    }
                    if (unreadByteCount > 0) {
                        inBuffer.position(0);
                        inBuffer.limit(inBuffer.limit() - unreadByteCount);
                    }
                }
                // Do this outside of the sync because:
                // - it doesn't need to be inside the sync
                // - if inside the sync it can trigger a deadlock
                // https://markmail.org/message/vbglzkvj6wxlhh3p
                if (unreadByteCount > 0) {
                    handler.onSwallowedDataFramePayload(getIdAsInt(), unreadByteCount);
                }
            }
        }


        @Override
        protected boolean timeoutRead(long now) {
            return readInterest && now > readTimeoutExpiry;
        }
    }


    class SavedRequestStreamInputBuffer extends StreamInputBuffer {

        private final SavedRequestInputFilter inputFilter;

        SavedRequestStreamInputBuffer(SavedRequestInputFilter inputFilter) {
            this.inputFilter = inputFilter;
        }


        @Override
        public int doRead(ApplicationBufferHandler handler) throws IOException {
            return inputFilter.doRead(handler);
        }

        @Override
        public int available() {
            return inputFilter.available();
        }

        @Override
        void receiveReset() {
            // NO-OP
        }

        @Override
        void swallowUnread() throws IOException {
            // NO-OP
        }

        @Override
        void notifyEof() {
            // NO-OP
        }

        @Override
        ByteBuffer getInBuffer(boolean create) {
            return null;
        }

        @Override
        void onDataAvailable() throws IOException {
            // NO-OP
        }

        @Override
        boolean isReadyForRead() {
            return true;
        }

        @Override
        boolean isRequestBodyFullyRead() {
            return inputFilter.isFinished();
        }

        @Override
        void insertReplayedBody(ByteChunk body) {
            // NO-OP
        }


        @Override
        protected boolean timeoutRead(long now) {
            // Reading from a saved request. Will never time out.
            return false;
        }
    }
}
