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
import java.nio.channels.CompletionHandler;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
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

    // TODO Make the size of this conversion buffer configurable
    private final ByteBuffer toBytes = ByteBuffer.allocate(8192);
    private final AtomicBoolean toBytesInProgress = new AtomicBoolean(false);
    private final CharsetEncoder encoder = Charset.forName("UTF8").newEncoder();
    private final MessageSendStateMachine state = new MessageSendStateMachine();

    private volatile long asyncSendTimeout = -1;

    // Max length for WebSocket frame header is 14 bytes
    protected final ByteBuffer header = ByteBuffer.allocate(14);
    protected ByteBuffer payload = null;


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
        // TODO Auto-generated method stub

    }


    @Override
    public boolean getBatchingAllowed() {
        // TODO Auto-generated method stub
        return false;
    }


    @Override
    public void flushBatch() {
        // TODO Auto-generated method stub

    }


    @Override
    public final void sendString(String text) throws IOException {
        sendPartialString(text, true);
    }


    @Override
    public final void sendBytes(ByteBuffer data) throws IOException {
        sendPartialBytes(data, true);
    }


    @Override
    public void sendPartialString(String fragment, boolean isLast)
            throws IOException {

        // The toBytes buffer needs to be protected from multiple threads and
        // the state check happens to late.
        if (!toBytesInProgress.compareAndSet(false, true)) {
            throw new IllegalStateException(sm.getString(
                    "wsRemoteEndpoint.concurrentMessageSend"));
        }

        try {
            encoder.reset();
            toBytes.clear();
            CharBuffer cb = CharBuffer.wrap(fragment);
            CoderResult cr = encoder.encode(cb, toBytes, true);
            toBytes.flip();
            while (cr.isOverflow()) {
                sendMessageBlocking(Constants.OPCODE_TEXT, toBytes, false);
                toBytes.clear();
                cr = encoder.encode(cb, toBytes, true);
                toBytes.flip();
            }
            sendMessageBlocking(Constants.OPCODE_TEXT, toBytes, isLast);
        } finally {
            // Make sure flag is reset before method exists
            toBytesInProgress.set(false);
        }
    }


    @Override
    public void sendPartialBytes(ByteBuffer partialByte, boolean isLast)
            throws IOException {
        sendMessageBlocking(Constants.OPCODE_BINARY, partialByte, isLast);
    }


    @Override
    public void sendPing(ByteBuffer applicationData) throws IOException {
        sendMessageBlocking(Constants.OPCODE_PING, applicationData, true);
    }


    @Override
    public void sendPong(ByteBuffer applicationData) throws IOException {
        sendMessageBlocking(Constants.OPCODE_PONG, applicationData, true);
    }


    @Override
    public Future<SendResult> sendBytesByFuture(ByteBuffer data) {
        this.payload = data;
        return sendMessageByFuture(Constants.OPCODE_BINARY, true);
    }


    @Override
    public void sendBytesByCompletion(ByteBuffer data, SendHandler completion) {
        this.payload = data;
        sendMessageByCompletion(Constants.OPCODE_BINARY, true,
                new WsCompletionHandler(this, completion, state, false));
    }







    protected void sendMessageBlocking(byte opCode, ByteBuffer payload,
            boolean isLast) throws IOException {

        this.payload = payload;

        Future<SendResult> f = sendMessageByFuture(opCode, isLast);
        SendResult sr = null;
        try {
            sr = f.get();
        } catch (InterruptedException | ExecutionException e) {
            throw new IOException(e);
        }

        if (!sr.isOK()) {
            throw new IOException(sr.getException());
        }
    }


    private Future<SendResult> sendMessageByFuture(byte opCode,
            boolean isLast) {

        WsCompletionHandler wsCompletionHandler = new WsCompletionHandler(
                this, state, opCode == Constants.OPCODE_CLOSE);
        sendMessageByCompletion(opCode, isLast, wsCompletionHandler);
        return wsCompletionHandler;
    }


    private void sendMessageByCompletion(byte opCode, boolean isLast,
            WsCompletionHandler handler) {

        boolean isFirst = state.startMessage(opCode, isLast);

        header.clear();
        byte first = 0;

        if (isLast) {
            // Set the fin bit
            first = -128;
        }

        if (isFirst) {
            // This is the first fragment of this message
            first = (byte) (first + opCode);
        }
        // If not the first fragment, it is a continuation with opCode of zero

        header.put(first);

        byte masked = getMasked();

        // Next write the mask && length length
        if (payload.limit() < 126) {
            header.put((byte) (payload.limit() | masked));
        } else if (payload.limit() < 65536) {
            header.put((byte) (126 | masked));
            header.put((byte) (payload.limit() >>> 8));
            header.put((byte) (payload.limit() & 0xFF));
        } else {
            // Will never be more than 2^31-1
            header.put((byte) (127 | masked));
            header.put((byte) 0);
            header.put((byte) 0);
            header.put((byte) 0);
            header.put((byte) 0);
            header.put((byte) (payload.limit() >>> 24));
            header.put((byte) (payload.limit() >>> 16));
            header.put((byte) (payload.limit() >>> 8));
            header.put((byte) (payload.limit() & 0xFF));
        }
        if (masked != 0) {
            // TODO Mask the data properly
            header.put((byte) 0);
            header.put((byte) 0);
            header.put((byte) 0);
            header.put((byte) 0);
        }
        header.flip();

        sendMessage(handler);
    }

    protected abstract byte getMasked();

    protected abstract void sendMessage(WsCompletionHandler handler);

    protected abstract void close();









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
    public Future<SendResult> sendStringByFuture(String text) {
        // TODO Auto-generated method stub
        return null;
    }


    @Override
    public void sendObject(Object o) throws IOException, EncodeException {
        // TODO Auto-generated method stub
    }


    @Override
    public void sendStringByCompletion(String text, SendHandler completion) {
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








    protected static class WsCompletionHandler implements Future<SendResult>,
            CompletionHandler<Long,Void> {

        private final WsRemoteEndpointBase wsRemoteEndpoint;
        private final MessageSendStateMachine state;
        private final SendHandler sendHandler;
        private final boolean close;
        private final CountDownLatch latch = new CountDownLatch(1);

        private volatile SendResult result = null;


        public WsCompletionHandler(WsRemoteEndpointBase wsRemoteEndpoint,
                MessageSendStateMachine state, boolean close) {
            this(wsRemoteEndpoint, null, state, close);
        }


        public WsCompletionHandler(WsRemoteEndpointBase wsRemoteEndpoint,
                SendHandler sendHandler, MessageSendStateMachine state,
                boolean close) {
            this.wsRemoteEndpoint = wsRemoteEndpoint;
            this.sendHandler = sendHandler;
            this.state = state;
            this.close = close;
        }


        // ------------------------------------------- CompletionHandler methods

        @Override
        public void completed(Long result, Void attachment) {
            state.endMessage();
            if (close) {
                wsRemoteEndpoint.close();
            }
            this.result = new SendResult();
            latch.countDown();
            if (sendHandler != null) {
                sendHandler.setResult(this.result);
            }
        }


        @Override
        public void failed(Throwable exc, Void attachment) {
            state.endMessage();
            if (close) {
                wsRemoteEndpoint.close();
            }
            this.result = new SendResult(exc);
            latch.countDown();
            if (sendHandler != null) {
                sendHandler.setResult(this.result);
            }
        }


        // ------------------------------------------------------ Future methods

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
        public SendResult get() throws InterruptedException, ExecutionException {
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


    private static class MessageSendStateMachine {
        private boolean closed = false;
        private boolean inProgress = false;
        private boolean fragmented = false;
        private boolean text = false;

        private boolean nextFragmented = false;
        private boolean nextText = false;

        public synchronized boolean startMessage(byte opCode, boolean isLast) {

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
                return true;
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
                return false;
            } else {
                // Wasn't fragmented. Might be now
                if (isLast) {
                    nextFragmented = false;
                } else {
                    nextFragmented = true;
                    nextText = isText;
                }
                return true;
            }
        }

        public synchronized void endMessage() {
            inProgress = false;
            fragmented = nextFragmented;
            text = nextText;
        }
    }
}
