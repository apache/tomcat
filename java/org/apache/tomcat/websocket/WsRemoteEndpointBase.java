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
package org.apache.tomcat.websocket;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.websocket.EncodeException;
import javax.websocket.RemoteEndpoint;
import javax.websocket.SendHandler;
import javax.websocket.SendResult;

import org.apache.tomcat.util.res.StringManager;

public abstract class WsRemoteEndpointBase implements RemoteEndpoint {

    private static final StringManager sm =
            StringManager.getManager(Constants.PACKAGE_NAME);

    private static org.apache.juli.logging.Log log =
            org.apache.juli.logging.LogFactory.getLog(
                    WsRemoteEndpointBase.class);

    private boolean messagePartInProgress = false;
    private Queue<MessagePart> messagePartQueue = new ArrayDeque<>();
    private final Object messagePartLock = new Object();
    private boolean dataMessageInProgress = false;

    // State
    private boolean closed = false;
    private boolean fragmented = false;
    private boolean nextFragmented = false;
    private boolean text = false;
    private boolean nextText = false;

    // Max size of WebSocket header is 14 bytes
    private final ByteBuffer headerBuffer = ByteBuffer.allocate(14);
    private final ByteBuffer outputBuffer = ByteBuffer.allocate(8192);
    private final CharsetEncoder encoder = Charset.forName("UTF8").newEncoder();
    private final ByteBuffer encoderBuffer = ByteBuffer.allocate(8192);
    private AtomicBoolean batchingAllowed = new AtomicBoolean(false);
    private volatile long asyncSendTimeout = -1;
    private WsSession wsSession;


    @Override
    public long getAsyncSendTimeout() {
        return asyncSendTimeout;
    }


    @Override
    public void setAsyncSendTimeout(long timeout) {
        this.asyncSendTimeout = timeout;
    }


    @Override
    public void setBatchingAllowed(boolean batchingAllowed) throws IOException {
        boolean oldValue = this.batchingAllowed.getAndSet(batchingAllowed);

        if (oldValue && !batchingAllowed) {
            flushBatch();
        }
    }


    @Override
    public boolean getBatchingAllowed() {
        return batchingAllowed.get();
    }


    @Override
    public void flushBatch() throws IOException {
        startMessageBlock(Constants.INTERNAL_OPCODE_FLUSH, null, true);
    }


    @Override
    public void sendBytes(ByteBuffer data) throws IOException {
        startMessageBlock(Constants.OPCODE_BINARY, data, true);
    }


    @Override
    public Future<SendResult> sendBytesByFuture(ByteBuffer data) {
        FutureToSendHandler f2sh = new FutureToSendHandler();
        sendBytesByCompletion(data, f2sh);
        return f2sh;
    }


    @Override
    public void sendBytesByCompletion(ByteBuffer data, SendHandler handler) {
        startMessage(Constants.OPCODE_BINARY, data, true, handler);
    }


    @Override
    public void sendPartialBytes(ByteBuffer partialByte, boolean last)
            throws IOException {
        startMessageBlock(Constants.OPCODE_BINARY, partialByte, last);
    }


    @Override
    public void sendPing(ByteBuffer applicationData) throws IOException,
            IllegalArgumentException {
        startMessageBlock(Constants.OPCODE_PING, applicationData, true);
    }


    @Override
    public void sendPong(ByteBuffer applicationData) throws IOException,
            IllegalArgumentException {
        startMessageBlock(Constants.OPCODE_PONG, applicationData, true);
    }


    @Override
    public void sendString(String text) throws IOException {
        sendPartialString(CharBuffer.wrap(text), true);
    }


    @Override
    public Future<SendResult> sendStringByFuture(String text) {
        FutureToSendHandler f2sh = new FutureToSendHandler();
        sendStringByCompletion(text, f2sh);
        return f2sh;
    }


    @Override
    public void sendStringByCompletion(String text, SendHandler handler) {
        TextMessageSendHandler tmsh = new TextMessageSendHandler(handler,
                CharBuffer.wrap(text), true, encoder, encoderBuffer, this);
        tmsh.write();
    }


    @Override
    public void sendPartialString(String fragment, boolean isLast)
            throws IOException {
        sendPartialString(CharBuffer.wrap(fragment), isLast);
    }


    @Override
    public OutputStream getSendStream() throws IOException {
        return new WsOuputStream(this);
    }


    @Override
    public Writer getSendWriter() throws IOException {
        return new WsWriter(this);
    }







    void sendPartialString(CharBuffer part, boolean last) throws IOException {
        try {
            FutureToSendHandler f2sh = new FutureToSendHandler();
            TextMessageSendHandler tmsh = new TextMessageSendHandler(f2sh, part,
                    last, encoder, encoderBuffer, this);
            tmsh.write();
            f2sh.get();
        } catch (InterruptedException | ExecutionException e) {
            throw new IOException(e);
        }
    }


    void startMessageBlock(byte opCode, ByteBuffer payload, boolean last)
            throws IOException {
        FutureToSendHandler f2sh = new FutureToSendHandler();
        startMessage(opCode, payload, last, f2sh);
        try {
            SendResult sr = f2sh.get();
            if (!sr.isOK()) {
                if (sr.getException() == null) {
                    throw new IOException();
                } else {
                    throw new IOException(sr.getException());
                }
            }
        } catch (InterruptedException | ExecutionException e) {
            throw new IOException(e);
        }
    }


    void startMessage(byte opCode, ByteBuffer payload, boolean last,
            SendHandler handler) {

        wsSession.updateLastActive();

        MessagePart mp = new MessagePart(opCode, payload, last, handler, this);

        synchronized (messagePartLock) {
            if (Constants.OPCODE_CLOSE == mp.getOpCode()) {
                try {
                    setBatchingAllowed(false);
                } catch (IOException e) {
                    log.warn(sm.getString(
                            "wsRemoteEndpoint.flushOnCloseFailed"), e);
                }
            }
            if (messagePartInProgress) {
                if (!Util.isControl(opCode)) {
                    if (dataMessageInProgress) {
                        throw new IllegalStateException(
                                sm.getString("wsRemoteEndpoint.inProgress"));
                    } else {
                        dataMessageInProgress = true;
                    }
                }
                messagePartQueue.add(mp);
            } else {
                messagePartInProgress = true;
                writeMessagePart(mp);
            }
        }
    }


    void endMessage(SendHandler handler, SendResult result,
            boolean dataMessage) {
        synchronized (messagePartLock) {

            if (closed) {
                close();
            } else {
                fragmented = nextFragmented;
                text = nextText;
            }

            if (dataMessage) {
                dataMessageInProgress = false;
            }
            MessagePart mpNext = messagePartQueue.poll();
            if (mpNext == null) {
                messagePartInProgress = false;
            } else {
                writeMessagePart(mpNext);
            }
        }

        wsSession.updateLastActive();

        handler.setResult(result);
    }


    void writeMessagePart(MessagePart mp) {

        if (closed) {
            throw new IllegalStateException(
                    sm.getString("wsRemoteEndpoint.closed"));
        }

        if (Constants.INTERNAL_OPCODE_FLUSH == mp.getOpCode()) {
            nextFragmented = fragmented;
            nextText = text;
            doWrite(mp.getHandler(), outputBuffer);
            return;
        }

        // Control messages may be sent in the middle of fragmented message
        // so they have no effect on the fragmented or text flags
        boolean first;
        if (Util.isControl(mp.getOpCode())) {
            nextFragmented = fragmented;
            nextText = text;
            if (mp.getOpCode() == Constants.OPCODE_CLOSE) {
                closed = true;
            }
            first = true;
        } else {
            boolean isText = Util.isText(mp.getOpCode());

            if (fragmented) {
                // Currently fragmented
                if (text != isText) {
                    throw new IllegalStateException(
                            sm.getString("wsRemoteEndpoint.changeType"));
                }
                nextText = text;
                nextFragmented = !mp.isLast();
                first = false;
            } else {
                // Wasn't fragmented. Might be now
                if (mp.isLast()) {
                    nextFragmented = false;
                } else {
                    nextFragmented = true;
                    nextText = isText;
                }
                first = true;
            }
        }

        byte[] mask;

        if (isMasked()) {
            mask = Util.generateMask();
        } else {
            mask = null;
        }

        headerBuffer.clear();
        writeHeader(headerBuffer, mp.getOpCode(), mp.getPayload(), first,
                mp.isLast(), isMasked(), mask);
        headerBuffer.flip();

        if (getBatchingAllowed() || isMasked()) {
            // Need to write via output buffer
            OutputBufferSendHandler obsh = new OutputBufferSendHandler(
                    mp.getHandler(), headerBuffer, mp.getPayload(), mask,
                    outputBuffer, !getBatchingAllowed(), this);
            obsh.write();
        } else {
            // Can write directly
            doWrite(mp.getHandler(), headerBuffer, mp.getPayload());
        }

    }


    private static class MessagePart {
        private final byte opCode;
        private final ByteBuffer payload;
        private final boolean last;
        private final SendHandler handler;

        public MessagePart(byte opCode, ByteBuffer payload, boolean last,
                SendHandler handler, WsRemoteEndpointBase endpoint) {
            this.opCode = opCode;
            this.payload = payload;
            this.last = last;
            this.handler = new EndMessageHandler(
                    endpoint, handler, !Util.isControl(opCode));
        }


        public byte getOpCode() {
            return opCode;
        }


        public ByteBuffer getPayload() {
            return payload;
        }


        public boolean isLast() {
            return last;
        }


        public SendHandler getHandler() {
            return handler;
        }
    }


    /**
     * Wraps the user provided handler so that the end point is notified when
     * the message is complete.
     */
    private static class EndMessageHandler implements SendHandler {

        private final WsRemoteEndpointBase endpoint;
        private final SendHandler handler;
        private final boolean dataMessage;

        public EndMessageHandler(WsRemoteEndpointBase endpoint,
                SendHandler handler, boolean dataMessage) {
            this.endpoint = endpoint;
            this.handler = handler;
            this.dataMessage = dataMessage;
        }


        @Override
        public void setResult(SendResult result) {
            endpoint.endMessage(handler, result, dataMessage);
        }
    }


    @Override
    public void sendObject(Object o) throws IOException, EncodeException {
        // TODO Auto-generated method stub

    }

    @Override
    public Future<SendResult> sendObjectByFuture(Object obj) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void sendObjectByCompletion(Object obj, SendHandler completion) {
        // TODO Auto-generated method stub

    }


    protected void setSession(WsSession wsSession) {
        this.wsSession = wsSession;
    }


    protected abstract void doWrite(SendHandler handler, ByteBuffer... data);
    protected abstract boolean isMasked();
    protected abstract void close();


    private static void writeHeader(ByteBuffer headerBuffer, byte opCode,
            ByteBuffer payload, boolean first, boolean last, boolean masked,
            byte[] mask) {

        byte b = 0;

        if (last) {
            // Set the fin bit
            b = -128;
        }

        if (first) {
            // This is the first fragment of this message
            b = (byte) (b + opCode);
        }
        // If not the first fragment, it is a continuation with opCode of zero

        headerBuffer.put(b);

        if (masked) {
            b = (byte) 0x80;
        } else {
            b = 0;
        }

        // Next write the mask && length length
        if (payload.limit() < 126) {
            headerBuffer.put((byte) (payload.limit() | b));
        } else if (payload.limit() < 65536) {
            headerBuffer.put((byte) (126 | b));
            headerBuffer.put((byte) (payload.limit() >>> 8));
            headerBuffer.put((byte) (payload.limit() & 0xFF));
        } else {
            // Will never be more than 2^31-1
            headerBuffer.put((byte) (127 | b));
            headerBuffer.put((byte) 0);
            headerBuffer.put((byte) 0);
            headerBuffer.put((byte) 0);
            headerBuffer.put((byte) 0);
            headerBuffer.put((byte) (payload.limit() >>> 24));
            headerBuffer.put((byte) (payload.limit() >>> 16));
            headerBuffer.put((byte) (payload.limit() >>> 8));
            headerBuffer.put((byte) (payload.limit() & 0xFF));
        }
        if (masked) {
            headerBuffer.put(mask[0]);
            headerBuffer.put(mask[1]);
            headerBuffer.put(mask[2]);
            headerBuffer.put(mask[3]);
        }
    }


    private static class TextMessageSendHandler implements SendHandler {

        private final SendHandler handler;
        private final CharBuffer message;
        private final boolean isLast;
        private final CharsetEncoder encoder;
        private final ByteBuffer buffer;
        private final WsRemoteEndpointBase endpoint;
        private volatile boolean isDone = false;

        public TextMessageSendHandler(SendHandler handler, CharBuffer message,
                boolean isLast, CharsetEncoder encoder,
                ByteBuffer encoderBuffer, WsRemoteEndpointBase endpoint) {
            this.handler = handler;
            this.message = message;
            this.isLast = isLast;
            this.encoder = encoder.reset();
            this.buffer = encoderBuffer;
            this.endpoint = endpoint;
        }

        public void write() {
            buffer.clear();
            CoderResult cr = encoder.encode(message, buffer, true);
            if (cr.isError()) {
                throw new IllegalArgumentException(cr.toString());
            }
            isDone = !cr.isOverflow();
            buffer.flip();
            endpoint.startMessage(Constants.OPCODE_TEXT, buffer,
                    isDone && isLast, this);
        }

        @Override
        public void setResult(SendResult result) {
            if (isDone || !result.isOK()) {
                handler.setResult(result);
            } else {
                write();
            }
        }
    }


    /**
     * Used to write data to the output buffer, flushing the buffer if it fills
     * up.
     */
    private static class OutputBufferSendHandler implements SendHandler {

        private final SendHandler handler;
        private final ByteBuffer headerBuffer;
        private final ByteBuffer payload;
        private final byte[] mask;
        private final ByteBuffer outputBuffer;
        private volatile boolean flushRequired;
        private final WsRemoteEndpointBase endpoint;
        private int maskIndex = 0;

        public OutputBufferSendHandler(SendHandler completion,
                ByteBuffer headerBuffer, ByteBuffer payload, byte[] mask,
                ByteBuffer outputBuffer, boolean flushRequired,
                WsRemoteEndpointBase endpoint) {
            this.handler = completion;
            this.headerBuffer = headerBuffer;
            this.payload = payload;
            this.mask = mask;
            this.outputBuffer = outputBuffer;
            this.flushRequired = flushRequired;
            this.endpoint = endpoint;
        }

        public void write() {
            // Write the header
            while (headerBuffer.hasRemaining() && outputBuffer.hasRemaining()) {
                outputBuffer.put(headerBuffer.get());
            }
            if (headerBuffer.hasRemaining()) {
                // Still more headers to write, need to flush
                flushRequired = true;
                outputBuffer.flip();
                endpoint.doWrite(this, outputBuffer);
                return;
            }

            // Write the payload
            while (payload.hasRemaining() && outputBuffer.hasRemaining()) {
                outputBuffer.put(
                        (byte) (payload.get() ^ (mask[maskIndex++] & 0xFF)));
                if (maskIndex > 3) {
                    maskIndex = 0;
                }
            }
            if (payload.hasRemaining()) {
                // Still more headers to write, need to flush
                flushRequired = true;
                outputBuffer.flip();
                endpoint.doWrite(this, outputBuffer);
                return;
            }

            if (flushRequired) {
                outputBuffer.flip();
                endpoint.doWrite(this, outputBuffer);
                flushRequired = false;
                return;
            } else {
                handler.setResult(new SendResult());
            }
        }

        // ------------------------------------------------- SendHandler methods
        @Override
        public void setResult(SendResult result) {
            outputBuffer.clear();
            if (result.isOK()) {
                write();
            } else {
                handler.setResult(result);
            }
        }
    }

    /**
     * Converts a Future to a SendHandler.
     */
    private static class FutureToSendHandler
            implements Future<SendResult>, SendHandler {

        private final CountDownLatch latch = new CountDownLatch(1);
        private volatile SendResult result = null;

        // --------------------------------------------------------- SendHandler

        @Override
        public void setResult(SendResult result) {
            this.result = result;
            latch.countDown();
        }


        // -------------------------------------------------------------- Future

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            // Cancelling the task is not supported
            return false;
        }

        @Override
        public boolean isCancelled() {
            // Cancelling the task is not supported
            return false;
        }

        @Override
        public boolean isDone() {
            return latch.getCount() == 0;
        }

        @Override
        public SendResult get() throws InterruptedException,
        ExecutionException {
            latch.await();
            return result;
        }

        @Override
        public SendResult get(long timeout, TimeUnit unit)
                throws InterruptedException, ExecutionException,
                TimeoutException {
            latch.await(timeout, unit);
            return result;
        }
    }


    private static class WsOuputStream extends OutputStream {

        private final WsRemoteEndpointBase endpoint;
        private final ByteBuffer buffer = ByteBuffer.allocate(8192);

        public WsOuputStream(WsRemoteEndpointBase endpoint) {
            this.endpoint = endpoint;
        }

        @Override
        public void write(int b) throws IOException {
            if (buffer.remaining() == 0) {
                flush();
            }
            buffer.put((byte) b);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            int remaining = buffer.remaining();
            int written = 0;

            while (remaining < len) {
                buffer.put(b, off + written, remaining);
                written += remaining;
                flush();
                remaining = buffer.remaining();
            }
            buffer.put(b, off + written, len - written);
        }

        @Override
        public void flush() throws IOException {
            doWrite(false);
        }

        @Override
        public void close() throws IOException {
            doWrite(true);
        }

        private void doWrite(boolean last) throws IOException {
            endpoint.sendPartialBytes(buffer, last);
            buffer.clear();
        }
    }


    private static class WsWriter extends Writer {

        private final WsRemoteEndpointBase endpoint;
        private final CharBuffer buffer = CharBuffer.allocate(8192);

        public WsWriter(WsRemoteEndpointBase endpoint) {
            this.endpoint = endpoint;
        }

        @Override
        public void write(char[] cbuf, int off, int len) throws IOException {
            int remaining = buffer.remaining();
            int written = 0;

            while (remaining < len) {
                buffer.put(cbuf, off + written, remaining);
                written += remaining;
                flush();
                remaining = buffer.remaining();
            }
            buffer.put(cbuf, off + written, len - written);
        }

        @Override
        public void flush() throws IOException {
            doWrite(false);
        }

        @Override
        public void close() throws IOException {
            doWrite(true);
        }

        private void doWrite(boolean last) throws IOException {
            buffer.flip();
            endpoint.sendPartialString(buffer, last);
            buffer.clear();
        }
    }
}
