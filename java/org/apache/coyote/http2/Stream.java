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
import java.util.Iterator;
import java.util.Locale;

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

public class Stream extends AbstractStream implements HeaderEmitter {

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
        StreamProcessor.prepareHeaders(null, response, null, null);
        ACK_HEADERS = response.getMimeHeaders();
    }

    private volatile int weight = Constants.DEFAULT_WEIGHT;
    private volatile long contentLengthReceived = 0;

    private final Http2UpgradeHandler handler;
    private final StreamStateMachine state;
    private final WindowAllocationManager allocationManager = new WindowAllocationManager(this);

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


    public Stream(Integer identifier, Http2UpgradeHandler handler) {
        this(identifier, handler, null);
    }


    public Stream(Integer identifier, Http2UpgradeHandler handler, Request coyoteRequest) {
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
        // No sendfile for HTTP/2 (it is enabled by default in the request)
        this.coyoteRequest.setSendfile(false);
        this.coyoteResponse.setOutputBuffer(http2OutputBuffer);
        this.coyoteRequest.setResponse(coyoteResponse);
        this.coyoteRequest.protocol().setString("HTTP/2.0");
        if (this.coyoteRequest.getStartTime() < 0) {
            this.coyoteRequest.setStartTime(System.currentTimeMillis());
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


    void rePrioritise(AbstractStream parent, boolean exclusive, int weight) {
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


    void receiveReset(long errorCode) {
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
        cancelAllocationRequests();
    }


    final void cancelAllocationRequests() {
        allocationManager.notifyAny();
    }


    void checkState(FrameType frameType) throws Http2Exception {
        state.checkFrameType(frameType);
    }


    @Override
    protected synchronized void incrementWindowSize(int windowSizeIncrement) throws Http2Exception {
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


    private synchronized int reserveWindowSize(int reservation, boolean block) throws IOException {
        long windowSize = getWindowSize();
        while (windowSize < 1) {
            if (!canWrite()) {
                throw new CloseNowException(sm.getString("stream.notWritable",
                        getConnectionId(), getIdentifier()));
            }
            if (block) {
                try {
                    long writeTimeout = handler.getProtocol().getStreamWriteTimeout();
                    allocationManager.waitForStream(writeTimeout);
                    windowSize = getWindowSize();
                    if (windowSize == 0) {
                        doWriteTimeout();
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


    void doWriteTimeout() throws CloseNowException {
        String msg = sm.getString("stream.writeTimeout");
        StreamException se = new StreamException(
                msg, Http2Error.ENHANCE_YOUR_CALM, getIdAsInt());
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
    @Deprecated
    protected synchronized void doNotifyAll() {
        // NO-OP. Unused.
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
            if (headerState == HEADER_STATE_TRAILER && !handler.isTrailerHeaderAllowed(name)) {
                break;
            }
            if ("expect".equals(name) && "100-continue".equals(value)) {
                coyoteRequest.setExpectation(true);
            }
            if (pseudoHeader) {
                headerException = new StreamException(sm.getString(
                        "stream.header.unknownPseudoHeader", getConnectionId(), getIdentifier(),
                        name), Http2Error.PROTOCOL_ERROR, getIdAsInt());
            }
            // Assume other HTTP header
            coyoteRequest.getMimeHeaders().addValue(name).setString(value);
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


    void writeHeaders() throws IOException {
        boolean endOfStream = streamOutputBuffer.hasNoBody();
        handler.writeHeaders(this, 0, coyoteResponse.getMimeHeaders(), endOfStream, Constants.DEFAULT_HEADERS_FRAME_SIZE);
    }


    final void addOutputFilter(OutputFilter filter) {
        http2OutputBuffer.addFilter(filter);
    }


    void writeAck() throws IOException {
        handler.writeHeaders(this, 0, ACK_HEADERS, false, Constants.DEFAULT_HEADERS_ACK_FRAME_SIZE);
    }


    @Override
    protected final String getConnectionId() {
        return handler.getConnectionId();
    }


    @Override
    protected int getWeight() {
        return weight;
    }


    Request getCoyoteRequest() {
        return coyoteRequest;
    }


    Response getCoyoteResponse() {
        return coyoteResponse;
    }


    ByteBuffer getInputByteBuffer() {
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


    final void sentHeaders() {
        state.sentStartOfHeaders();
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


    StreamInputBuffer getInputBuffer() {
        return inputBuffer;
    }


    final HttpOutputBuffer getOutputBuffer() {
        return http2OutputBuffer;
    }


    void sentPushPromise() {
        state.sentPushPromise();
    }


    boolean isActive() {
        return state.isActive();
    }


    boolean canWrite() {
        return state.canWrite();
    }


    boolean isClosedFinal() {
        return state.isClosedFinal();
    }


    void closeIfIdle() {
        state.closeIfIdle();
    }


    boolean isInputFinished() {
        return !state.isFrameTypePermitted(FrameType.DATA);
    }


    void close(Http2Exception http2Exception) {
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
        // Reads wait internally so need to call a method to break the wait()
        if (inputBuffer != null) {
            inputBuffer.receiveReset();
        }
    }


    boolean isPushSupported() {
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
        private volatile int streamReservation = 0;
        private volatile boolean closed = false;
        private volatile StreamException reset = null;
        private volatile boolean endOfStreamSent = false;

        /* The write methods are synchronized to ensure that only one thread at
         * a time is able to access the buffer. Without this protection, a
         * client that performed concurrent writes could corrupt the buffer.
         */

        /**
         * @deprecated Unused. Will be removed in Tomcat 9. Use
         *             {@link #doWrite(ByteBuffer)}
         */
        @Deprecated
        @Override
        public synchronized int doWrite(ByteChunk chunk) throws IOException {
            if (closed) {
                throw new IllegalStateException(
                        sm.getString("stream.closed", getConnectionId(), getIdentifier()));
            }
            if (!coyoteResponse.isCommitted()) {
                coyoteResponse.sendHeaders();
            }
            int len = chunk.getLength();
            int offset = 0;
            while (len > 0) {
                int thisTime = Math.min(buffer.remaining(), len);
                buffer.put(chunk.getBytes(), chunk.getOffset() + offset, thisTime);
                offset += thisTime;
                len -= thisTime;
                if (len > 0 && !buffer.hasRemaining()) {
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

        @Override
        public synchronized int doWrite(ByteBuffer chunk) throws IOException {
            if (closed) {
                throw new IllegalStateException(
                        sm.getString("stream.closed", getConnectionId(), getIdentifier()));
            }
            int totalThisTime = 0;
            if (writeBuffer.isEmpty()) {
                int chunkLimit = chunk.limit();
                while (chunk.remaining() > 0) {
                    int thisTime = Math.min(buffer.remaining(), chunk.remaining());
                    chunk.limit(chunk.position() + thisTime);
                    buffer.put(chunk);
                    chunk.limit(chunkLimit);
                    totalThisTime += thisTime;
                    if (chunk.remaining() > 0 && !buffer.hasRemaining()) {
                        // Only flush if we have more data to write and the buffer
                        // is full
                        if (flush(true, coyoteResponse.getWriteListener() == null)) {
                            totalThisTime = chunk.remaining();
                            writeBuffer.add(chunk);
                            dataLeft = true;
                            break;
                        }
                    }
                }
            } else {
                totalThisTime = chunk.remaining();
                writeBuffer.add(chunk);
            }
            written += totalThisTime;
            return totalThisTime;
        }

        public synchronized boolean flush(boolean block) throws IOException {
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

        private synchronized boolean flush(boolean writeInProgress, boolean block)
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
                            !writeInProgress && closed && left == connectionReservation);
                    streamReservation -= connectionReservation;
                    left -= connectionReservation;
                }
            }
            buffer.clear();
            return false;
        }

        synchronized boolean isReady() {
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
        public long getBytesWritten() {
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
            }
        }

        public boolean isClosed() {
            return closed;
        }

        /**
         * @return <code>true</code> if it is certain that the associated
         *         response has no body.
         */
        public boolean hasNoBody() {
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

        /**
         * @deprecated Unused. Will be removed in Tomcat 9. Use
         *             {@link #doRead(ApplicationBufferHandler)}
         */
        @Deprecated
        @Override
        public int doRead(ByteChunk chunk) throws IOException {

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

                        if (inBuffer.position() == 0) {
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

            chunk.setBytes(outBuffer, 0,  written);

            // Increment client-side flow control windows by the number of bytes
            // read
            handler.writeWindowUpdate(Stream.this, written, true);

            return written;
        }

        @Override
        public int doRead(ApplicationBufferHandler applicationBufferHandler) throws IOException {

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

        synchronized boolean isRequestBodyFullyRead() {
            return (inBuffer == null || inBuffer.position() == 0) && isInputFinished();
        }


        synchronized int available() {
            if (inBuffer == null) {
                return 0;
            }
            return inBuffer.position();
        }


        /*
         * Called after placing some data in the inBuffer.
         */
        synchronized boolean onDataAvailable() {
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


        public ByteBuffer getInBuffer() {
            ensureBuffersExist();
            return inBuffer;
        }


        protected synchronized void insertReplayedBody(ByteChunk body) {
            inBuffer = ByteBuffer.wrap(body.getBytes(),  body.getOffset(),  body.getLength());
        }


        private void ensureBuffersExist() {
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


        protected void receiveReset() {
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
