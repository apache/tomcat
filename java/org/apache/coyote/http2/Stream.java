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
import org.apache.coyote.InputBuffer;
import org.apache.coyote.OutputBuffer;
import org.apache.coyote.Request;
import org.apache.coyote.Response;
import org.apache.coyote.http2.HpackDecoder.HeaderEmitter;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.res.StringManager;

public class Stream extends AbstractStream implements HeaderEmitter {

    private static final Log log = LogFactory.getLog(Stream.class);
    private static final StringManager sm = StringManager.getManager(Stream.class);

    private static final Response ACK_RESPONSE = new Response();

    static {
        ACK_RESPONSE.setStatus(100);
    }

    private volatile int weight = Constants.DEFAULT_WEIGHT;

    private final Http2UpgradeHandler handler;
    private final StreamStateMachine state;
    // TODO: null these when finished to reduce memory used by closed stream
    private final Request coyoteRequest;
    private final Response coyoteResponse = new Response();
    private final StreamInputBuffer inputBuffer;
    private final StreamOutputBuffer outputBuffer = new StreamOutputBuffer();


    public Stream(Integer identifier, Http2UpgradeHandler handler) {
        this(identifier, handler, null);
    }


    public Stream(Integer identifier, Http2UpgradeHandler handler, Request coyoteRequest) {
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
    }


    public void rePrioritise(AbstractStream parent, boolean exclusive, int weight) {
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


    public void reset(long errorCode) {
        if (log.isDebugEnabled()) {
            log.debug(sm.getString("stream.reset.debug", getConnectionId(), getIdentifier(),
                    Long.toString(errorCode)));
        }
        state.receiveReset();
    }


    void checkState(FrameType frameType) throws Http2Exception {
        state.checkFrameType(frameType);
    }


    @Override
    public synchronized void incrementWindowSize(int windowSizeIncrement) throws Http2Exception {
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


    private synchronized int reserveWindowSize(int reservation, boolean block) throws IOException {
        long windowSize = getWindowSize();
        while (windowSize < 1) {
            if (!canWrite()) {
                throw new IOException(sm.getString("stream.notWritable", getConnectionId(),
                        getIdentifier()));
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
    protected synchronized void doNotifyAll() {
        if (coyoteResponse.getWriteListener() == null) {
            // Blocking IO so thread will be waiting. Release it.
            // Use notifyAll() to be safe (should be unnecessary)
            this.notifyAll();
        } else {
            if (outputBuffer.isRegisteredForWrite()) {
                coyoteResponse.action(ActionCode.DISPATCH_WRITE, null);
            }
        }
    }


    @Override
    public void emitHeader(String name, String value, boolean neverIndex) {
        if (log.isDebugEnabled()) {
            log.debug(sm.getString("stream.header.debug", getConnectionId(), getIdentifier(),
                    name, value));
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
                boolean secure = Boolean.parseBoolean(handler.getProperty("secure"));
                if (secure) {
                    coyoteRequest.setServerPort(443);
                } else {
                    coyoteRequest.setServerPort(80);
                }
            }
            break;
        }
        default: {
            if ("expect".equals(name) && "100-continue".equals(value)) {
                coyoteRequest.setExpectation(true);
            }
            // Assume other HTTP header
            coyoteRequest.getMimeHeaders().addValue(name).setString(value);
        }
        }
    }


    void writeHeaders() throws IOException {
        // TODO: Is 1k the optimal value?
        handler.writeHeaders(this, coyoteResponse, 1024);
    }

    void writeAck() throws IOException {
        // TODO: Is 64 too big? Just the status header with compression
        handler.writeHeaders(this, ACK_RESPONSE, 64);
    }



    void flushData() throws IOException {
        if (log.isDebugEnabled()) {
            log.debug(sm.getString("stream.write", getConnectionId(), getIdentifier()));
        }
        outputBuffer.flush(true);
    }


    @Override
    protected final String getConnectionId() {
        return getParentStream().getConnectionId();
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


    void receivedStartOfHeaders() {
        state.receivedStartOfHeaders();
    }


    void receivedEndOfStream() {
        state.recievedEndOfStream();
    }


    void sentEndOfStream() {
        outputBuffer.endOfStreamSent = true;
        state.sentEndOfStream();
    }


    StreamInputBuffer getInputBuffer() {
        return inputBuffer;
    }


    StreamOutputBuffer getOutputBuffer() {
        return outputBuffer;
    }


    /**
     * Marks the stream as reset. This method will not change the stream state
     * if:
     * <ul>
     * <li>The stream is already reset</li>
     * <li>The stream is already closed</li>
     *
     * @throws IllegalStateException If the stream is in a state that does not
     *         permit resets
     */
    void sendReset() {
        state.sendReset();
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
                handler.resetStream((StreamException) http2Exception);
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


    void push(Request request) throws IOException {
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


    private static void push(Http2UpgradeHandler handler, Request request, Stream stream)
            throws IOException {
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
        private volatile boolean writeInterest = false;

        /* The write methods are synchronized to ensure that only one thread at
         * a time is able to access the buffer. Without this protection, a
         * client that performed concurrent writes could corrupt the buffer.
         */

        @Override
        public synchronized int doWrite(ByteChunk chunk) throws IOException {
            if (closed) {
                throw new IllegalStateException(
                        sm.getString("stream.closed", getConnectionId(), getIdentifier()));
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

        public synchronized boolean flush(boolean block) throws IOException {
            return flush(false, block);
        }

        private synchronized boolean flush(boolean writeInProgress, boolean block)
                throws IOException {
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("stream.outputBuffer.flush.debug", getConnectionId(),
                        getIdentifier(), Integer.toString(buffer.position()),
                        Boolean.toString(writeInProgress), Boolean.toString(closed)));
            }
            if (!coyoteResponse.isCommitted()) {
                coyoteResponse.sendHeaders();
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
                    buffer.position(buffer.position() + connectionReservation);
                }
            }
            buffer.clear();
            return false;
        }

        synchronized void reset() {
            buffer.clear();
        }

        synchronized boolean isReady() {
            if (getWindowSize() > 0 && handler.getWindowSize() > 0) {
                return true;
            } else {
                writeInterest = true;
                return false;
            }
        }

        synchronized boolean isRegisteredForWrite() {
            if (writeInterest) {
                writeInterest = false;
                return true;
            } else {
                return false;
            }
        }

        @Override
        public long getBytesWritten() {
            return written;
        }

        public void close() throws IOException {
            closed = true;
            flushData();
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

        @Override
        public int doRead(ByteChunk chunk) throws IOException {

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

            chunk.setBytes(outBuffer, 0,  written);

            // Increment client-side flow control windows by the number of bytes
            // read
            handler.writeWindowUpdate(Stream.this, written, true);

            return written;
        }


        void registerReadInterest() {
            synchronized (inBuffer) {
                readInterest = true;
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
    }
}
