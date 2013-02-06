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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import javax.websocket.EncodeException;
import javax.websocket.RemoteEndpoint;
import javax.websocket.SendHandler;
import javax.websocket.SendResult;

import org.apache.tomcat.util.res.StringManager;

public abstract class WsRemoteEndpointBase implements RemoteEndpoint {

    private static final StringManager sm =
            StringManager.getManager(Constants.PACKAGE_NAME);

    private final ReentrantLock writeLock = new ReentrantLock();
    private final Condition notInProgress = writeLock.newCondition();
    // Must hold writeLock above to modify state
    private final MessageSendStateMachine state = new MessageSendStateMachine();
    // Max size of WebSocket header is 14 bytes
    private final ByteBuffer headerBuffer = ByteBuffer.allocate(14);
    private final ByteBuffer outputBuffer = ByteBuffer.allocate(8192);
    private final CharsetEncoder encoder = Charset.forName("UTF8").newEncoder();
    private final ByteBuffer encoderBuffer = ByteBuffer.allocate(8192);
    private AtomicBoolean batchingAllowed = new AtomicBoolean(false);
    private volatile long asyncSendTimeout = -1;


    @Override
    public long getAsyncSendTimeout() {
        return asyncSendTimeout;
    }


    @Override
    public void setAsyncSendTimeout(long timeout) {
        this.asyncSendTimeout = timeout;
    }


    @Override
    public void setBatchingAllowed(boolean batchingAllowed) {
        boolean oldValue = this.batchingAllowed.getAndSet(batchingAllowed);

        if (oldValue && !batchingAllowed) {
            // Just disabled batched. Must flush.
            flushBatch();
        }
    }


    @Override
    public boolean getBatchingAllowed() {
        return batchingAllowed.get();
    }


    @Override
    public void flushBatch() {
        // Have to hold lock to flush output buffer
        writeLock.lock();
        try {
            while (state.isInProgress()) {
                notInProgress.await();
            }
            FutureToSendHandler f2sh = new FutureToSendHandler();
            doWrite(f2sh, outputBuffer);
            f2sh.get();
        } catch (InterruptedException | ExecutionException e) {
            // TODO Log this? Runtime exception? Something else?
        } finally {
            writeLock.unlock();
        }
    }


    @Override
    public void sendBytes(ByteBuffer data) throws IOException {
        Future<SendResult> f = sendBytesByFuture(data);
        try {
            SendResult sr = f.get();
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


    @Override
    public Future<SendResult> sendBytesByFuture(ByteBuffer data) {
        FutureToSendHandler f2sh = new FutureToSendHandler();
        sendBytesByCompletion(data, f2sh);
        return f2sh;
    }


    @Override
    public void sendBytesByCompletion(ByteBuffer data, SendHandler completion) {
        boolean locked = writeLock.tryLock();
        if (!locked) {
            throw new IllegalStateException(
                    sm.getString("wsRemoteEndpoint.concurrentMessageSend"));
        }
        try {
            byte opCode = Constants.OPCODE_BINARY;
            boolean isLast = true;
            sendMessage(opCode, data, isLast, completion);
        } finally {
            writeLock.unlock();
        }
    }


    @Override
    public void sendPartialBytes(ByteBuffer partialByte, boolean isLast)
            throws IOException {
        boolean locked = writeLock.tryLock();
        if (!locked) {
            throw new IllegalStateException(
                    sm.getString("wsRemoteEndpoint.concurrentMessageSend"));
        }
        try {
            byte opCode = Constants.OPCODE_BINARY;
            FutureToSendHandler f2sh = new FutureToSendHandler();
            sendMessage(opCode, partialByte, isLast, f2sh);
            f2sh.get();
        } catch (InterruptedException | ExecutionException e) {
            throw new IOException(e);
        } finally {
            writeLock.unlock();
        }
    }


    @Override
    public void sendPing(ByteBuffer applicationData) throws IOException,
            IllegalArgumentException {
        sendControlMessage(Constants.OPCODE_PING, applicationData);
    }


    @Override
    public void sendPong(ByteBuffer applicationData) throws IOException,
            IllegalArgumentException {
        sendControlMessage(Constants.OPCODE_PONG, applicationData);
    }


    @Override
    public void sendString(String text) throws IOException {
        Future<SendResult> f = sendStringByFuture(text);
        try {
            SendResult sr = f.get();
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


    @Override
    public Future<SendResult> sendStringByFuture(String text) {
        FutureToSendHandler f2sh = new FutureToSendHandler();
        sendStringByCompletion(text, f2sh);
        return f2sh;
    }


    @Override
    public void sendStringByCompletion(String text, SendHandler completion) {
        boolean locked = writeLock.tryLock();
        if (!locked) {
            throw new IllegalStateException(
                    sm.getString("wsRemoteEndpoint.concurrentMessageSend"));
        }
        try {
            TextMessageSendHandler tmsh = new TextMessageSendHandler(
                    completion, text, true, encoder, encoderBuffer, this);
            tmsh.write();
        } finally {
            writeLock.unlock();
        }
    }


    @Override
    public void sendPartialString(String fragment, boolean isLast)
            throws IOException {
        boolean locked = writeLock.tryLock();
        if (!locked) {
            throw new IllegalStateException(
                    sm.getString("wsRemoteEndpoint.concurrentMessageSend"));
        }
        try {
            FutureToSendHandler f2sh = new FutureToSendHandler();
            TextMessageSendHandler tmsh = new TextMessageSendHandler(
                    f2sh, fragment, isLast, encoder, encoderBuffer, this);
            tmsh.write();
            f2sh.get();
        } catch (InterruptedException | ExecutionException e) {
            throw new IOException(e);
        } finally {
            writeLock.unlock();
        }
    }



    /**
     * Sends a control message, blocking until the message is sent.
     */
    void sendControlMessage(byte opCode, ByteBuffer payload)
            throws IOException{

        // Close needs to be sent so disable batching. This will flush any
        // messages in the buffer
        if (opCode == Constants.OPCODE_CLOSE) {
            setBatchingAllowed(false);
        }

        writeLock.lock();
        try {
            if (state.isInProgress()) {
                notInProgress.await();
            }
            FutureToSendHandler f2sh = new FutureToSendHandler();
            sendMessage(opCode, payload, true, f2sh);
            f2sh.get();
        } catch (InterruptedException | ExecutionException e) {
            throw new IOException(e);
        } finally {
            notInProgress.signal();
            writeLock.unlock();
        }
    }


    private void sendMessage(byte opCode, ByteBuffer payload, boolean last,
            SendHandler completion) {

        if (!writeLock.isHeldByCurrentThread()) {
            // Coding problem
            throw new IllegalStateException(
                    "Must hold writeLock before calling this method");
        }

        state.startMessage(opCode, last);

        SendMessageSendHandler smsh =
                new SendMessageSendHandler(state, completion, this);

        byte[] mask;

        if (isMasked()) {
            mask = Util.generateMask();
        } else {
            mask = null;
        }

        headerBuffer.clear();
        writeHeader(headerBuffer, opCode, payload, state.isFirst(), last,
                isMasked(), mask);
        headerBuffer.flip();

        if (getBatchingAllowed() || isMasked()) {
            // Need to write via output buffer
            OutputBufferSendHandler obsh = new OutputBufferSendHandler(
                    smsh, headerBuffer, payload, mask, outputBuffer,
                    !getBatchingAllowed(), this);
            obsh.write();
        } else {
            // Can write directly
            doWrite(smsh, headerBuffer, payload);
        }
    }


    private void endMessage() {
        writeLock.lock();
        try {
            notInProgress.signal();
        } finally {
            writeLock.unlock();
        }
    }









    @Override
    public OutputStream getSendStream() throws IOException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Writer getSendWriter() throws IOException {
        // TODO Auto-generated method stub
        return null;
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


    private static class MessageSendStateMachine {
        private boolean closed = false;
        private boolean inProgress = false;
        private boolean fragmented = false;
        private boolean text = false;
        private boolean first = false;

        private boolean nextFragmented = false;
        private boolean nextText = false;

        public synchronized void startMessage(byte opCode, boolean isLast) {

            if (closed) {
                throw new IllegalStateException(
                        sm.getString("messageSendStateMachine.closed"));
            }

            if (inProgress) {
                throw new IllegalStateException(
                        sm.getString("messageSendStateMachine.inProgress"));
            }

            inProgress = true;

            // Control messages may be sent in the middle of fragmented message
            // so they have no effect on the fragmented or text flags
            if (Util.isControl(opCode)) {
                nextFragmented = fragmented;
                nextText = text;
                if (opCode == Constants.OPCODE_CLOSE) {
                    closed = true;
                }
                first = true;
                return;
            }

            boolean isText = Util.isText(opCode);

            if (fragmented) {
                // Currently fragmented
                if (text != isText) {
                    throw new IllegalStateException(
                            sm.getString("messageSendStateMachine.changeType"));
                }
                nextText = text;
                nextFragmented = !isLast;
                first = false;
            } else {
                // Wasn't fragmented. Might be now
                if (isLast) {
                    nextFragmented = false;
                } else {
                    nextFragmented = true;
                    nextText = isText;
                }
                first = true;
            }
        }

        public synchronized void endMessage() {
            inProgress = false;
            fragmented = nextFragmented;
            text = nextText;
        }

        public synchronized boolean isInProgress() {
            return inProgress;
        }

        public synchronized boolean isFirst() {
            return first;
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

        public TextMessageSendHandler(SendHandler handler, String message,
                boolean isLast, CharsetEncoder encoder,
                ByteBuffer encoderBuffer, WsRemoteEndpointBase endpoint) {
            this.handler = handler;
            this.message = CharBuffer.wrap(message);
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
            endpoint.sendMessage(Constants.OPCODE_TEXT, buffer,
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
     *  Wraps user provided {@link SendHandler} so that state is updated when
     *  the message completes.
     */
    private static class SendMessageSendHandler implements SendHandler {

        private final MessageSendStateMachine state;
        private final SendHandler handler;
        private final WsRemoteEndpointBase endpoint;

        public SendMessageSendHandler(MessageSendStateMachine state,
                SendHandler handler, WsRemoteEndpointBase endpoint) {
            this.state = state;
            this.handler = handler;
            this.endpoint = endpoint;
        }

        @Override
        public void setResult(SendResult result) {
            state.endMessage();
            if (state.closed) {
                endpoint.close();
            }
            handler.setResult(result);
            endpoint.endMessage();
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
}
