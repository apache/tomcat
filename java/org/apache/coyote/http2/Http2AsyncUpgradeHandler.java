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
import java.nio.channels.CompletionHandler;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.servlet.http.WebConnection;

import org.apache.coyote.Adapter;
import org.apache.coyote.ProtocolException;
import org.apache.coyote.Request;
import org.apache.tomcat.util.http.MimeHeaders;
import org.apache.tomcat.util.net.SendfileState;
import org.apache.tomcat.util.net.SocketWrapperBase;
import org.apache.tomcat.util.net.SocketWrapperBase.BlockingMode;

public class Http2AsyncUpgradeHandler extends Http2UpgradeHandler {

    private static final ByteBuffer[] BYTEBUFFER_ARRAY = new ByteBuffer[0];
    // Ensures headers are generated and then written for one thread at a time.
    // Because of the compression used, headers need to be written to the
    // network in the same order they are generated.
    private final Object headerWriteLock = new Object();
    private final AtomicReference<Throwable> error = new AtomicReference<>();
    private final AtomicReference<IOException> applicationIOE = new AtomicReference<>();

    public Http2AsyncUpgradeHandler(Http2Protocol protocol, Adapter adapter,
            Request coyoteRequest) {
        super(protocol, adapter, coyoteRequest);
    }

    private final CompletionHandler<Long, Void> errorCompletion = new CompletionHandler<Long, Void>() {
        @Override
        public void completed(Long result, Void attachment) {
        }
        @Override
        public void failed(Throwable t, Void attachment) {
            error.set(t);
        }
    };
    private final CompletionHandler<Long, Void> applicationErrorCompletion = new CompletionHandler<Long, Void>() {
        @Override
        public void completed(Long result, Void attachment) {
        }
        @Override
        public void failed(Throwable t, Void attachment) {
            if (t instanceof IOException) {
                applicationIOE.set((IOException) t);
            }
            error.set(t);
        }
    };

    @Override
    protected Http2Parser getParser(String connectionId) {
        return new Http2AsyncParser(connectionId, this, this, socketWrapper, this);
    }


    @Override
    protected PingManager getPingManager() {
        return new AsyncPingManager();
    }


    @Override
    public boolean hasAsyncIO() {
        return true;
    }


    @Override
    protected void processConnection(WebConnection webConnection,
            Stream stream) {
        // The end of the processing will instead be an async callback
    }

    void processConnectionCallback(WebConnection webConnection,
            Stream stream) {
        super.processConnection(webConnection, stream);
    }


    @Override
    protected void writeSettings() {
        socketWrapper.write(BlockingMode.SEMI_BLOCK, protocol.getWriteTimeout(),
                TimeUnit.MILLISECONDS, null, SocketWrapperBase.COMPLETE_WRITE, errorCompletion,
                ByteBuffer.wrap(localSettings.getSettingsFrameForPending()),
                ByteBuffer.wrap(createWindowUpdateForSettings()));
        Throwable err = error.get();
        if (err != null) {
            String msg = sm.getString("upgradeHandler.sendPrefaceFail", connectionId);
            if (log.isDebugEnabled()) {
                log.debug(msg);
            }
            throw new ProtocolException(msg, err);
        }
    }


    @Override
    void sendStreamReset(StreamException se) throws IOException {
        if (log.isDebugEnabled()) {
            log.debug(sm.getString("upgradeHandler.rst.debug", connectionId,
                    Integer.toString(se.getStreamId()), se.getError(), se.getMessage()));
        }
        // Write a RST frame
        byte[] rstFrame = new byte[13];
        // Length
        ByteUtil.setThreeBytes(rstFrame, 0, 4);
        // Type
        rstFrame[3] = FrameType.RST.getIdByte();
        // No flags
        // Stream ID
        ByteUtil.set31Bits(rstFrame, 5, se.getStreamId());
        // Payload
        ByteUtil.setFourBytes(rstFrame, 9, se.getError().getCode());
        socketWrapper.write(BlockingMode.SEMI_BLOCK, protocol.getWriteTimeout(),
                TimeUnit.MILLISECONDS, null, SocketWrapperBase.COMPLETE_WRITE, errorCompletion,
                ByteBuffer.wrap(rstFrame));
        handleAsyncException();
    }


    @Override
    protected void writeGoAwayFrame(int maxStreamId, long errorCode, byte[] debugMsg)
            throws IOException {
        byte[] fixedPayload = new byte[8];
        ByteUtil.set31Bits(fixedPayload, 0, maxStreamId);
        ByteUtil.setFourBytes(fixedPayload, 4, errorCode);
        int len = 8;
        if (debugMsg != null) {
            len += debugMsg.length;
        }
        byte[] payloadLength = new byte[3];
        ByteUtil.setThreeBytes(payloadLength, 0, len);
        if (debugMsg != null) {
            socketWrapper.write(BlockingMode.SEMI_BLOCK, protocol.getWriteTimeout(),
                    TimeUnit.MILLISECONDS, null, SocketWrapperBase.COMPLETE_WRITE, errorCompletion,
                    ByteBuffer.wrap(payloadLength), ByteBuffer.wrap(GOAWAY),
                    ByteBuffer.wrap(fixedPayload), ByteBuffer.wrap(debugMsg));
        } else {
            socketWrapper.write(BlockingMode.SEMI_BLOCK, protocol.getWriteTimeout(),
                    TimeUnit.MILLISECONDS, null, SocketWrapperBase.COMPLETE_WRITE, errorCompletion,
                    ByteBuffer.wrap(payloadLength), ByteBuffer.wrap(GOAWAY),
                    ByteBuffer.wrap(fixedPayload));
        }
        handleAsyncException();
    }


    @Override
    void writeHeaders(Stream stream, int pushedStreamId, MimeHeaders mimeHeaders,
            boolean endOfStream, int payloadSize) throws IOException {
        synchronized (headerWriteLock) {
            AsyncHeaderFrameBuffers headerFrameBuffers = (AsyncHeaderFrameBuffers)
                    doWriteHeaders(stream, pushedStreamId, mimeHeaders, endOfStream, payloadSize);
            if (headerFrameBuffers != null) {
                socketWrapper.write(BlockingMode.SEMI_BLOCK, protocol.getWriteTimeout(),
                        TimeUnit.MILLISECONDS, null, SocketWrapperBase.COMPLETE_WRITE,
                        applicationErrorCompletion, headerFrameBuffers.bufs.toArray(BYTEBUFFER_ARRAY));
                handleAsyncException();
            }
        }
        if (endOfStream) {
            stream.sentEndOfStream();
        }
    }


    @Override
    protected HeaderFrameBuffers getHeaderFrameBuffers(int initialPayloadSize) {
        return new AsyncHeaderFrameBuffers(initialPayloadSize);
    }


    @Override
    void writeBody(Stream stream, ByteBuffer data, int len, boolean finished) throws IOException {
        if (log.isDebugEnabled()) {
            log.debug(sm.getString("upgradeHandler.writeBody", connectionId, stream.getIdAsString(),
                    Integer.toString(len), Boolean.valueOf(finished)));
        }
        // Need to check this now since sending end of stream will change this.
        boolean writeable = stream.canWrite();
        byte[] header = new byte[9];
        ByteUtil.setThreeBytes(header, 0, len);
        header[3] = FrameType.DATA.getIdByte();
        if (finished) {
            header[4] = FLAG_END_OF_STREAM;
            stream.sentEndOfStream();
            if (!stream.isActive()) {
                setConnectionTimeoutForStreamCount(activeRemoteStreamCount.decrementAndGet());
            }
        }
        if (writeable) {
            ByteUtil.set31Bits(header, 5, stream.getIdAsInt());
            int orgLimit = data.limit();
            data.limit(data.position() + len);
            socketWrapper.write(BlockingMode.BLOCK, protocol.getWriteTimeout(),
                    TimeUnit.MILLISECONDS, null, SocketWrapperBase.COMPLETE_WRITE,
                    applicationErrorCompletion, ByteBuffer.wrap(header), data);
            data.limit(orgLimit);
            handleAsyncException();
        }
    }


    @Override
    void writeWindowUpdate(AbstractNonZeroStream stream, int increment, boolean applicationInitiated)
            throws IOException {
        // Build window update frame for stream 0
        byte[] frame = new byte[13];
        ByteUtil.setThreeBytes(frame, 0,  4);
        frame[3] = FrameType.WINDOW_UPDATE.getIdByte();
        ByteUtil.set31Bits(frame, 9, increment);
        // No need to send update from closed stream
        if  (stream instanceof Stream && ((Stream) stream).canWrite()) {
            // Change stream Id
            byte[] frame2 = new byte[13];
            ByteUtil.setThreeBytes(frame2, 0,  4);
            frame2[3] = FrameType.WINDOW_UPDATE.getIdByte();
            ByteUtil.set31Bits(frame2, 9, increment);
            ByteUtil.set31Bits(frame2, 5, stream.getIdAsInt());
            socketWrapper.write(BlockingMode.SEMI_BLOCK, protocol.getWriteTimeout(),
                    TimeUnit.MILLISECONDS, null, SocketWrapperBase.COMPLETE_WRITE, errorCompletion,
                    ByteBuffer.wrap(frame), ByteBuffer.wrap(frame2));
        } else {
            socketWrapper.write(BlockingMode.SEMI_BLOCK, protocol.getWriteTimeout(),
                    TimeUnit.MILLISECONDS, null, SocketWrapperBase.COMPLETE_WRITE, errorCompletion,
                    ByteBuffer.wrap(frame));
        }
        handleAsyncException();
    }


    @Override
    public void settingsEnd(boolean ack) throws IOException {
        if (ack) {
            if (!localSettings.ack()) {
                // Ack was unexpected
                log.warn(sm.getString("upgradeHandler.unexpectedAck", connectionId, getIdAsString()));
            }
        } else {
            socketWrapper.write(BlockingMode.SEMI_BLOCK, protocol.getWriteTimeout(),
                    TimeUnit.MILLISECONDS, null, SocketWrapperBase.COMPLETE_WRITE, errorCompletion,
                    ByteBuffer.wrap(SETTINGS_ACK));
        }
        handleAsyncException();
    }


    private void handleAsyncException() throws IOException {
        IOException ioe = applicationIOE.getAndSet(null);
        if (ioe != null) {
            handleAppInitiatedIOException(ioe);
        } else {
            Throwable err = this.error.getAndSet(null);
            if (err != null) {
                if (err instanceof IOException) {
                    throw (IOException) err;
                } else {
                    throw new IOException(err);
                }
            }
        }
    }

    @Override
    protected SendfileState processSendfile(SendfileData sendfile) {
        if (sendfile != null) {
            try {
                try (FileChannel channel = FileChannel.open(sendfile.path, StandardOpenOption.READ)) {
                    sendfile.mappedBuffer = channel.map(MapMode.READ_ONLY, sendfile.pos, sendfile.end - sendfile.pos);
                }
                // Reserve as much as possible right away
                int reservation = (sendfile.end - sendfile.pos > Integer.MAX_VALUE) ? Integer.MAX_VALUE : (int) (sendfile.end - sendfile.pos);
                sendfile.streamReservation  = sendfile.stream.reserveWindowSize(reservation, true);
                sendfile.connectionReservation = reserveWindowSize(sendfile.stream, sendfile.streamReservation, true);
            } catch (IOException e) {
                return SendfileState.ERROR;
            }
            // Actually perform the write
            int frameSize = Integer.min(getMaxFrameSize(), sendfile.connectionReservation);
            boolean finished = (frameSize == sendfile.left) && sendfile.stream.getCoyoteResponse().getTrailerFields() == null;

            // Need to check this now since sending end of stream will change this.
            boolean writeable = sendfile.stream.canWrite();
            byte[] header = new byte[9];
            ByteUtil.setThreeBytes(header, 0, frameSize);
            header[3] = FrameType.DATA.getIdByte();
            if (finished) {
                header[4] = FLAG_END_OF_STREAM;
                sendfile.stream.sentEndOfStream();
                if (!sendfile.stream.isActive()) {
                    setConnectionTimeoutForStreamCount(activeRemoteStreamCount.decrementAndGet());
                }
            }
            if (writeable) {
                ByteUtil.set31Bits(header, 5, sendfile.stream.getIdAsInt());
                sendfile.mappedBuffer.limit(sendfile.mappedBuffer.position() + frameSize);
                socketWrapper.write(BlockingMode.SEMI_BLOCK, protocol.getWriteTimeout(),
                        TimeUnit.MILLISECONDS, sendfile, SocketWrapperBase.COMPLETE_WRITE_WITH_COMPLETION,
                        new SendfileCompletionHandler(), ByteBuffer.wrap(header), sendfile.mappedBuffer);
                try {
                    handleAsyncException();
                } catch (IOException e) {
                    return SendfileState.ERROR;
                }
            }
            return SendfileState.PENDING;
        } else {
            return SendfileState.DONE;
        }
    }

    protected class SendfileCompletionHandler implements CompletionHandler<Long, SendfileData> {
        @Override
        public void completed(Long nBytes, SendfileData sendfile) {
            long bytesWritten = nBytes.longValue() - 9;
            sendfile.left -= bytesWritten;
            if (sendfile.left == 0) {
                try {
                    sendfile.stream.getOutputBuffer().end();
                } catch (IOException e) {
                    failed(e, sendfile);
                }
                return;
            }
            sendfile.streamReservation -= bytesWritten;
            sendfile.connectionReservation -= bytesWritten;
            sendfile.pos += bytesWritten;
            try {
                if (sendfile.connectionReservation == 0) {
                    if (sendfile.streamReservation == 0) {
                        int reservation = (sendfile.end - sendfile.pos > Integer.MAX_VALUE) ? Integer.MAX_VALUE : (int) (sendfile.end - sendfile.pos);
                        sendfile.streamReservation = sendfile.stream.reserveWindowSize(reservation, true);
                    }
                    sendfile.connectionReservation = reserveWindowSize(sendfile.stream, sendfile.streamReservation, true);
                }
            } catch (IOException e) {
                failed (e, sendfile);
                return;
            }
            int frameSize = Integer.min(getMaxFrameSize(), sendfile.streamReservation);
            boolean finished = (frameSize == sendfile.left) && sendfile.stream.getCoyoteResponse().getTrailerFields() == null;

            // Need to check this now since sending end of stream will change this.
            boolean writeable = sendfile.stream.canWrite();
            byte[] header = new byte[9];
            ByteUtil.setThreeBytes(header, 0, frameSize);
            header[3] = FrameType.DATA.getIdByte();
            if (finished) {
                header[4] = FLAG_END_OF_STREAM;
                sendfile.stream.sentEndOfStream();
                if (!sendfile.stream.isActive()) {
                    setConnectionTimeoutForStreamCount(activeRemoteStreamCount.decrementAndGet());
                }
            }
            if (writeable) {
                ByteUtil.set31Bits(header, 5, sendfile.stream.getIdAsInt());
                sendfile.mappedBuffer.limit(sendfile.mappedBuffer.position() + frameSize);
                socketWrapper.write(BlockingMode.SEMI_BLOCK, protocol.getWriteTimeout(),
                        TimeUnit.MILLISECONDS, sendfile, SocketWrapperBase.COMPLETE_WRITE_WITH_COMPLETION,
                        this, ByteBuffer.wrap(header), sendfile.mappedBuffer);
                try {
                    handleAsyncException();
                } catch (IOException e) {
                    failed(e, sendfile);
                }
            }
        }

        @Override
        public void failed(Throwable t, SendfileData sendfile) {
            applicationErrorCompletion.failed(t, null);
        }
    }

    protected class AsyncPingManager extends PingManager {
        @Override
        public void sendPing(boolean force) throws IOException {
            if (initiateDisabled) {
                return;
            }
            long now = System.nanoTime();
            if (force || now - lastPingNanoTime > pingIntervalNano) {
                lastPingNanoTime = now;
                byte[] payload = new byte[8];
                int sentSequence = ++sequence;
                PingRecord pingRecord = new PingRecord(sentSequence, now);
                inflightPings.add(pingRecord);
                ByteUtil.set31Bits(payload, 4, sentSequence);
                socketWrapper.write(BlockingMode.SEMI_BLOCK, protocol.getWriteTimeout(),
                        TimeUnit.MILLISECONDS, null, SocketWrapperBase.COMPLETE_WRITE,
                        errorCompletion, ByteBuffer.wrap(PING), ByteBuffer.wrap(payload));
                handleAsyncException();
            }
        }

        @Override
        public void receivePing(byte[] payload, boolean ack) throws IOException {
            if (ack) {
                super.receivePing(payload, ack);
            } else {
                // Client originated ping. Echo it back.
                socketWrapper.write(BlockingMode.SEMI_BLOCK, protocol.getWriteTimeout(),
                        TimeUnit.MILLISECONDS, null, SocketWrapperBase.COMPLETE_WRITE,
                        errorCompletion, ByteBuffer.wrap(PING_ACK), ByteBuffer.wrap(payload));
                handleAsyncException();
            }
        }

    }


    private static class AsyncHeaderFrameBuffers implements HeaderFrameBuffers {

        int payloadSize;

        private byte[] header;
        private ByteBuffer payload;

        private final List<ByteBuffer> bufs = new ArrayList<>();

        public AsyncHeaderFrameBuffers(int initialPayloadSize) {
            this.payloadSize = initialPayloadSize;
        }

        @Override
        public void startFrame() {
            header = new byte[9];
            payload = ByteBuffer.allocate(payloadSize);
        }

        @Override
        public void endFrame() throws IOException {
            bufs.add(ByteBuffer.wrap(header));
            bufs.add(payload);
        }

        @Override
        public void endHeaders() throws IOException {
        }

        @Override
        public byte[] getHeader() {
            return header;
        }

        @Override
        public ByteBuffer getPayload() {
            return payload;
        }

        @Override
        public void expandPayload() {
            payloadSize = payloadSize * 2;
            payload = ByteBuffer.allocate(payloadSize);
        }
    }
}
