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
import java.lang.reflect.InvocationTargetException;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

import javax.naming.NamingException;

import jakarta.websocket.CloseReason;
import jakarta.websocket.CloseReason.CloseCodes;
import jakarta.websocket.DeploymentException;
import jakarta.websocket.EncodeException;
import jakarta.websocket.Encoder;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.RemoteEndpoint;
import jakarta.websocket.SendHandler;
import jakarta.websocket.SendResult;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.InstanceManager;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.buf.Utf8Encoder;
import org.apache.tomcat.util.res.StringManager;

public abstract class WsRemoteEndpointImplBase implements RemoteEndpoint {

    protected static final StringManager sm = StringManager.getManager(WsRemoteEndpointImplBase.class);

    private final Log log = LogFactory.getLog(WsRemoteEndpointImplBase.class); // must not be static

    private final StateMachine stateMachine = new StateMachine();

    private final IntermediateMessageHandler intermediateMessageHandler = new IntermediateMessageHandler(this);

    private Transformation transformation = null;
    protected final Semaphore messagePartInProgress = new Semaphore(1);
    private final Queue<MessagePart> messagePartQueue = new ArrayDeque<>();
    private final Object messagePartLock = new Object();

    // State
    private volatile boolean closed = false;
    private boolean fragmented = false;
    private boolean nextFragmented = false;
    private boolean text = false;
    private boolean nextText = false;

    // Max size of WebSocket header is 14 bytes
    private final ByteBuffer headerBuffer = ByteBuffer.allocate(14);
    private final ByteBuffer outputBuffer = ByteBuffer.allocate(Constants.DEFAULT_BUFFER_SIZE);
    private final CharsetEncoder encoder = new Utf8Encoder();
    private final ByteBuffer encoderBuffer = ByteBuffer.allocate(Constants.DEFAULT_BUFFER_SIZE);
    private final AtomicBoolean batchingAllowed = new AtomicBoolean(false);
    private volatile long sendTimeout = -1;
    private WsSession wsSession;
    private List<EncoderEntry> encoderEntries = new ArrayList<>();


    protected void setTransformation(Transformation transformation) {
        this.transformation = transformation;
    }


    public long getSendTimeout() {
        return sendTimeout;
    }


    public void setSendTimeout(long timeout) {
        this.sendTimeout = timeout;
    }


    protected WsSession getSession() {
        return wsSession;
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
        sendMessageBlock(Constants.INTERNAL_OPCODE_FLUSH, null, true);
    }


    public void sendBytes(ByteBuffer data) throws IOException {
        if (data == null) {
            throw new IllegalArgumentException(sm.getString("wsRemoteEndpoint.nullData"));
        }
        stateMachine.binaryStart();
        sendMessageBlock(Constants.OPCODE_BINARY, data, true);
        stateMachine.complete(true);
    }


    public Future<Void> sendBytesByFuture(ByteBuffer data) {
        FutureToSendHandler f2sh = new FutureToSendHandler(wsSession);
        sendBytesByCompletion(data, f2sh);
        return f2sh;
    }


    public void sendBytesByCompletion(ByteBuffer data, SendHandler handler) {
        if (data == null) {
            throw new IllegalArgumentException(sm.getString("wsRemoteEndpoint.nullData"));
        }
        if (handler == null) {
            throw new IllegalArgumentException(sm.getString("wsRemoteEndpoint.nullHandler"));
        }
        StateUpdateSendHandler sush = new StateUpdateSendHandler(handler, stateMachine);
        stateMachine.binaryStart();
        startMessage(Constants.OPCODE_BINARY, data, true, sush);
    }


    public void sendPartialBytes(ByteBuffer partialByte, boolean last) throws IOException {
        if (partialByte == null) {
            throw new IllegalArgumentException(sm.getString("wsRemoteEndpoint.nullData"));
        }
        stateMachine.binaryPartialStart();
        sendMessageBlock(Constants.OPCODE_BINARY, partialByte, last);
        stateMachine.complete(last);
    }


    @Override
    public void sendPing(ByteBuffer applicationData) throws IOException, IllegalArgumentException {
        if (applicationData.remaining() > 125) {
            throw new IllegalArgumentException(sm.getString("wsRemoteEndpoint.tooMuchData"));
        }
        sendMessageBlock(Constants.OPCODE_PING, applicationData, true);
    }


    @Override
    public void sendPong(ByteBuffer applicationData) throws IOException, IllegalArgumentException {
        if (applicationData.remaining() > 125) {
            throw new IllegalArgumentException(sm.getString("wsRemoteEndpoint.tooMuchData"));
        }
        sendMessageBlock(Constants.OPCODE_PONG, applicationData, true);
    }


    public void sendString(String text) throws IOException {
        if (text == null) {
            throw new IllegalArgumentException(sm.getString("wsRemoteEndpoint.nullData"));
        }
        stateMachine.textStart();
        sendMessageBlock(CharBuffer.wrap(text), true);
    }


    public Future<Void> sendStringByFuture(String text) {
        FutureToSendHandler f2sh = new FutureToSendHandler(wsSession);
        sendStringByCompletion(text, f2sh);
        return f2sh;
    }


    public void sendStringByCompletion(String text, SendHandler handler) {
        if (text == null) {
            throw new IllegalArgumentException(sm.getString("wsRemoteEndpoint.nullData"));
        }
        if (handler == null) {
            throw new IllegalArgumentException(sm.getString("wsRemoteEndpoint.nullHandler"));
        }
        stateMachine.textStart();
        TextMessageSendHandler tmsh = new TextMessageSendHandler(handler, CharBuffer.wrap(text), true, encoder,
                encoderBuffer, this);
        tmsh.write();
        // TextMessageSendHandler will update stateMachine when it completes
    }


    public void sendPartialString(String fragment, boolean isLast) throws IOException {
        if (fragment == null) {
            throw new IllegalArgumentException(sm.getString("wsRemoteEndpoint.nullData"));
        }
        stateMachine.textPartialStart();
        sendMessageBlock(CharBuffer.wrap(fragment), isLast);
    }


    public OutputStream getSendStream() {
        stateMachine.streamStart();
        return new WsOutputStream(this);
    }


    public Writer getSendWriter() {
        stateMachine.writeStart();
        return new WsWriter(this);
    }


    void sendMessageBlock(CharBuffer part, boolean last) throws IOException {
        long timeout = getBlockingSendTimeout();
        boolean isDone = false;
        while (!isDone) {
            encoderBuffer.clear();
            CoderResult cr = encoder.encode(part, encoderBuffer, true);
            if (cr.isError()) {
                throw new IllegalArgumentException(sm.getString("wsRemoteEndpoint.encoderError", cr));
            }
            isDone = !cr.isOverflow();
            encoderBuffer.flip();
            sendMessageBlock(Constants.OPCODE_TEXT, encoderBuffer, last && isDone, timeout);
        }
        stateMachine.complete(last);
    }


    void sendMessageBlock(byte opCode, ByteBuffer payload, boolean last) throws IOException {
        sendMessageBlock(opCode, payload, last, getBlockingSendTimeout());
    }


    void sendMessageBlock(byte opCode, ByteBuffer payload, boolean last, long timeout) throws IOException {
        /*
         *  Get the timeout before we send the message. The message may trigger a session close and depending on timing
         *  the client session may close before we can read the timeout.
         */
        sendMessageBlockInternal(opCode, payload, last, getTimeoutExpiry(timeout));
    }


    private long getTimeoutExpiry(long timeout) {
        if (timeout < 0) {
            return Long.MAX_VALUE;
        } else {
            return System.currentTimeMillis() + timeout;
        }
    }


    private void sendMessageBlockInternal(byte opCode, ByteBuffer payload, boolean last, long timeoutExpiry)
            throws IOException {
        wsSession.updateLastActiveWrite();

        BlockingSendHandler bsh = new BlockingSendHandler();

        List<MessagePart> messageParts = new ArrayList<>();
        messageParts.add(new MessagePart(last, 0, opCode, payload, bsh, bsh, timeoutExpiry));

        messageParts = transformation.sendMessagePart(messageParts);

        // Some extensions/transformations may buffer messages so it is possible
        // that no message parts will be returned. If this is the case simply
        // return.
        if (messageParts.size() == 0) {
            return;
        }

        try {
            if (!acquireMessagePartInProgressSemaphore(opCode, timeoutExpiry)) {
                String msg = sm.getString("wsRemoteEndpoint.acquireTimeout");
                wsSession.doClose(new CloseReason(CloseCodes.GOING_AWAY, msg),
                        new CloseReason(CloseCodes.CLOSED_ABNORMALLY, msg), true);
                throw new SocketTimeoutException(msg);
            }
        } catch (InterruptedException e) {
            String msg = sm.getString("wsRemoteEndpoint.sendInterrupt");
            wsSession.doClose(new CloseReason(CloseCodes.GOING_AWAY, msg),
                    new CloseReason(CloseCodes.CLOSED_ABNORMALLY, msg), true);
            throw new IOException(msg, e);
        }

        for (MessagePart mp : messageParts) {
            try {
                writeMessagePart(mp);
            } catch (Throwable t) {
                ExceptionUtils.handleThrowable(t);
                messagePartInProgress.release();
                wsSession.doClose(new CloseReason(CloseCodes.GOING_AWAY, t.getMessage()),
                        new CloseReason(CloseCodes.CLOSED_ABNORMALLY, t.getMessage()), true);
                throw t;
            }
            if (!bsh.getSendResult().isOK()) {
                messagePartInProgress.release();
                Throwable t = bsh.getSendResult().getException();
                wsSession.doClose(new CloseReason(CloseCodes.GOING_AWAY, t.getMessage()),
                        new CloseReason(CloseCodes.CLOSED_ABNORMALLY, t.getMessage()), true);
                throw new IOException(t);
            }
            // The BlockingSendHandler doesn't call end message so update the
            // flags.
            fragmented = nextFragmented;
            text = nextText;
        }

        if (payload != null) {
            payload.clear();
        }

        endMessage(null, null);
    }


    /**
     * Acquire the semaphore that allows a message part to be written.
     *
     * @param opCode        The OPCODE for the message to be written
     * @param timeoutExpiry The time when the attempt to acquire the semaphore should expire
     *
     * @return {@code true} if the semaphore is obtained, otherwise {@code false}.
     *
     * @throws InterruptedException If the wait for the semaphore is interrupted
     */
    protected boolean acquireMessagePartInProgressSemaphore(byte opCode, long timeoutExpiry)
            throws InterruptedException {
        long timeout = timeoutExpiry - System.currentTimeMillis();
        return messagePartInProgress.tryAcquire(timeout, TimeUnit.MILLISECONDS);
    }


    void startMessage(byte opCode, ByteBuffer payload, boolean last, SendHandler handler) {

        wsSession.updateLastActiveWrite();

        List<MessagePart> messageParts = new ArrayList<>();
        messageParts.add(new MessagePart(last, 0, opCode, payload, intermediateMessageHandler,
                new EndMessageHandler(this, handler), -1));

        try {
            messageParts = transformation.sendMessagePart(messageParts);
        } catch (IOException ioe) {
            handler.onResult(new SendResult(getSession(), ioe));
            return;
        }

        // Some extensions/transformations may buffer messages so it is possible
        // that no message parts will be returned. If this is the case the
        // trigger the supplied SendHandler
        if (messageParts.size() == 0) {
            handler.onResult(new SendResult(getSession()));
            return;
        }

        MessagePart mp = messageParts.remove(0);

        boolean doWrite = false;
        synchronized (messagePartLock) {
            if (Constants.OPCODE_CLOSE == mp.getOpCode() && getBatchingAllowed()) {
                // Should not happen. To late to send batched messages now since
                // the session has been closed. Complain loudly.
                log.warn(sm.getString("wsRemoteEndpoint.flushOnCloseFailed"));
            }
            if (messagePartInProgress.tryAcquire()) {
                doWrite = true;
            } else {
                // When a control message is sent while another message is being
                // sent, the control message is queued. Chances are the
                // subsequent data message part will end up queued while the
                // control message is sent. The logic in this class (state
                // machine, EndMessageHandler, TextMessageSendHandler) ensures
                // that there will only ever be one data message part in the
                // queue. There could be multiple control messages in the queue.

                // Add it to the queue
                messagePartQueue.add(mp);
            }
            // Add any remaining messages to the queue
            messagePartQueue.addAll(messageParts);
        }
        if (doWrite) {
            // Actual write has to be outside sync block to avoid possible
            // deadlock between messagePartLock and writeLock in
            // o.a.coyote.http11.upgrade.AbstractServletOutputStream
            writeMessagePart(mp);
        }
    }


    void endMessage(SendHandler handler, SendResult result) {
        boolean doWrite = false;
        MessagePart mpNext = null;
        synchronized (messagePartLock) {

            fragmented = nextFragmented;
            text = nextText;

            mpNext = messagePartQueue.poll();
            if (mpNext == null) {
                messagePartInProgress.release();
            } else if (!closed) {
                // Session may have been closed unexpectedly in the middle of
                // sending a fragmented message closing the endpoint. If this
                // happens, clearly there is no point trying to send the rest of
                // the message.
                doWrite = true;
            }
        }
        if (doWrite) {
            // Actual write has to be outside sync block to avoid possible
            // deadlock between messagePartLock and writeLock in
            // o.a.coyote.http11.upgrade.AbstractServletOutputStream
            writeMessagePart(mpNext);
        }

        wsSession.updateLastActiveWrite();

        // Some handlers, such as the IntermediateMessageHandler, do not have a
        // nested handler so handler may be null.
        if (handler != null) {
            handler.onResult(result);
        }
    }


    void writeMessagePart(MessagePart mp) {
        if (closed) {
            throw new IllegalStateException(sm.getString("wsRemoteEndpoint.closed"));
        }

        if (Constants.INTERNAL_OPCODE_FLUSH == mp.getOpCode()) {
            nextFragmented = fragmented;
            nextText = text;
            outputBuffer.flip();
            SendHandler flushHandler = new OutputBufferFlushSendHandler(outputBuffer, mp.getEndHandler());
            doWrite(flushHandler, mp.getBlockingWriteTimeoutExpiry(), outputBuffer);
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
                    throw new IllegalStateException(sm.getString("wsRemoteEndpoint.changeType"));
                }
                nextText = text;
                nextFragmented = !mp.isFin();
                first = false;
            } else {
                // Wasn't fragmented. Might be now
                if (mp.isFin()) {
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

        int payloadSize = mp.getPayload().remaining();
        headerBuffer.clear();
        writeHeader(headerBuffer, mp.isFin(), mp.getRsv(), mp.getOpCode(), isMasked(), mp.getPayload(), mask, first);
        headerBuffer.flip();

        if (getBatchingAllowed() || isMasked()) {
            // Need to write via output buffer
            OutputBufferSendHandler obsh = new OutputBufferSendHandler(mp.getEndHandler(),
                    mp.getBlockingWriteTimeoutExpiry(), headerBuffer, mp.getPayload(), mask, outputBuffer,
                    !getBatchingAllowed(), this);
            obsh.write();
        } else {
            // Can write directly
            doWrite(mp.getEndHandler(), mp.getBlockingWriteTimeoutExpiry(), headerBuffer, mp.getPayload());
        }

        updateStats(payloadSize);
    }


    /**
     * Hook for updating server side statistics. Called on every frame written (including when batching is enabled and
     * the frames are buffered locally until the buffer is full or is flushed).
     *
     * @param payloadLength Size of message payload
     */
    protected void updateStats(long payloadLength) {
        // NO-OP by default
    }


    private long getBlockingSendTimeout() {
        Object obj = wsSession.getUserProperties().get(Constants.BLOCKING_SEND_TIMEOUT_PROPERTY);
        Long userTimeout = null;
        if (obj instanceof Long) {
            userTimeout = (Long) obj;
        }
        if (userTimeout == null) {
            return Constants.DEFAULT_BLOCKING_SEND_TIMEOUT;
        } else {
            return userTimeout.longValue();
        }
    }


    /**
     * Wraps the user provided handler so that the end point is notified when the message is complete.
     */
    private static class EndMessageHandler implements SendHandler {

        private final WsRemoteEndpointImplBase endpoint;
        private final SendHandler handler;

        EndMessageHandler(WsRemoteEndpointImplBase endpoint, SendHandler handler) {
            this.endpoint = endpoint;
            this.handler = handler;
        }


        @Override
        public void onResult(SendResult result) {
            endpoint.endMessage(handler, result);
        }
    }


    /**
     * If a transformation needs to split a {@link MessagePart} into multiple {@link MessagePart}s, it uses this handler
     * as the end handler for each of the additional {@link MessagePart}s. This handler notifies this this class that
     * the {@link MessagePart} has been processed and that the next {@link MessagePart} in the queue should be started.
     * The final {@link MessagePart} will use the {@link EndMessageHandler} provided with the original
     * {@link MessagePart}.
     */
    private static class IntermediateMessageHandler implements SendHandler {

        private final WsRemoteEndpointImplBase endpoint;

        IntermediateMessageHandler(WsRemoteEndpointImplBase endpoint) {
            this.endpoint = endpoint;
        }


        @Override
        public void onResult(SendResult result) {
            endpoint.endMessage(null, result);
        }
    }


    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void sendObject(Object obj) throws IOException, EncodeException {
        if (obj == null) {
            throw new IllegalArgumentException(sm.getString("wsRemoteEndpoint.nullData"));
        }
        /*
         * Note that the implementation will convert primitives and their object equivalents by default but that users
         * are free to specify their own encoders and decoders for this if they wish.
         */
        Encoder encoder = findEncoder(obj);
        if (encoder == null && Util.isPrimitive(obj.getClass())) {
            String msg = obj.toString();
            sendString(msg);
            return;
        }
        if (encoder == null && byte[].class.isAssignableFrom(obj.getClass())) {
            ByteBuffer msg = ByteBuffer.wrap((byte[]) obj);
            sendBytes(msg);
            return;
        }

        if (encoder instanceof Encoder.Text) {
            String msg = ((Encoder.Text) encoder).encode(obj);
            sendString(msg);
        } else if (encoder instanceof Encoder.TextStream) {
            try (Writer w = getSendWriter()) {
                ((Encoder.TextStream) encoder).encode(obj, w);
            }
        } else if (encoder instanceof Encoder.Binary) {
            ByteBuffer msg = ((Encoder.Binary) encoder).encode(obj);
            sendBytes(msg);
        } else if (encoder instanceof Encoder.BinaryStream) {
            try (OutputStream os = getSendStream()) {
                ((Encoder.BinaryStream) encoder).encode(obj, os);
            }
        } else {
            throw new EncodeException(obj, sm.getString("wsRemoteEndpoint.noEncoder", obj.getClass()));
        }
    }


    public Future<Void> sendObjectByFuture(Object obj) {
        FutureToSendHandler f2sh = new FutureToSendHandler(wsSession);
        sendObjectByCompletion(obj, f2sh);
        return f2sh;
    }


    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void sendObjectByCompletion(Object obj, SendHandler completion) {

        if (obj == null) {
            throw new IllegalArgumentException(sm.getString("wsRemoteEndpoint.nullData"));
        }
        if (completion == null) {
            throw new IllegalArgumentException(sm.getString("wsRemoteEndpoint.nullHandler"));
        }

        /*
         * Note that the implementation will convert primitives and their object equivalents by default but that users
         * are free to specify their own encoders and decoders for this if they wish.
         */
        Encoder encoder = findEncoder(obj);
        if (encoder == null && Util.isPrimitive(obj.getClass())) {
            String msg = obj.toString();
            sendStringByCompletion(msg, completion);
            return;
        }
        if (encoder == null && byte[].class.isAssignableFrom(obj.getClass())) {
            ByteBuffer msg = ByteBuffer.wrap((byte[]) obj);
            sendBytesByCompletion(msg, completion);
            return;
        }

        try {
            if (encoder instanceof Encoder.Text) {
                String msg = ((Encoder.Text) encoder).encode(obj);
                sendStringByCompletion(msg, completion);
            } else if (encoder instanceof Encoder.TextStream) {
                try (Writer w = getSendWriter()) {
                    ((Encoder.TextStream) encoder).encode(obj, w);
                }
                completion.onResult(new SendResult(getSession()));
            } else if (encoder instanceof Encoder.Binary) {
                ByteBuffer msg = ((Encoder.Binary) encoder).encode(obj);
                sendBytesByCompletion(msg, completion);
            } else if (encoder instanceof Encoder.BinaryStream) {
                try (OutputStream os = getSendStream()) {
                    ((Encoder.BinaryStream) encoder).encode(obj, os);
                }
                completion.onResult(new SendResult(getSession()));
            } else {
                throw new EncodeException(obj, sm.getString("wsRemoteEndpoint.noEncoder", obj.getClass()));
            }
        } catch (Exception e) {
            SendResult sr = new SendResult(getSession(), e);
            completion.onResult(sr);
        }
    }


    protected void setSession(WsSession wsSession) {
        this.wsSession = wsSession;
    }


    protected void setEncoders(EndpointConfig endpointConfig) throws DeploymentException {
        encoderEntries.clear();
        for (Class<? extends Encoder> encoderClazz : endpointConfig.getEncoders()) {
            Encoder instance;
            InstanceManager instanceManager = wsSession.getInstanceManager();
            try {
                if (instanceManager == null) {
                    instance = encoderClazz.getConstructor().newInstance();
                } else {
                    instance = (Encoder) instanceManager.newInstance(encoderClazz);
                }
                instance.init(endpointConfig);
            } catch (ReflectiveOperationException | NamingException e) {
                throw new DeploymentException(sm.getString("wsRemoteEndpoint.invalidEncoder", encoderClazz.getName()),
                        e);
            }
            EncoderEntry entry = new EncoderEntry(Util.getEncoderType(encoderClazz), instance);
            encoderEntries.add(entry);
        }
    }


    private Encoder findEncoder(Object obj) {
        for (EncoderEntry entry : encoderEntries) {
            if (entry.getClazz().isAssignableFrom(obj.getClass())) {
                return entry.getEncoder();
            }
        }
        return null;
    }


    public final void close() {
        InstanceManager instanceManager = wsSession.getInstanceManager();
        for (EncoderEntry entry : encoderEntries) {
            entry.getEncoder().destroy();
            if (instanceManager != null) {
                try {
                    instanceManager.destroyInstance(entry);
                } catch (IllegalAccessException | InvocationTargetException e) {
                    log.warn(sm.getString("wsRemoteEndpoint.encoderDestoryFailed", encoder.getClass()), e);
                }
            }
        }
        // The transformation handles both input and output. It only needs to be
        // closed once so it is closed here on the output side.
        transformation.close();
        doClose();
    }


    protected abstract void doWrite(SendHandler handler, long blockingWriteTimeoutExpiry, ByteBuffer... data);

    protected abstract boolean isMasked();

    protected abstract void doClose();

    protected abstract ReentrantLock getLock();


    private static void writeHeader(ByteBuffer headerBuffer, boolean fin, int rsv, byte opCode, boolean masked,
            ByteBuffer payload, byte[] mask, boolean first) {

        byte b = 0;

        if (fin) {
            // Set the fin bit
            b -= 128;
        }

        b += (rsv << 4);

        if (first) {
            // This is the first fragment of this message
            b += opCode;
        }
        // If not the first fragment, it is a continuation with opCode of zero

        headerBuffer.put(b);

        if (masked) {
            b = (byte) 0x80;
        } else {
            b = 0;
        }

        // Next write the mask && length length
        if (payload.remaining() < 126) {
            headerBuffer.put((byte) (payload.remaining() | b));
        } else if (payload.remaining() < 65536) {
            headerBuffer.put((byte) (126 | b));
            headerBuffer.put((byte) (payload.remaining() >>> 8));
            headerBuffer.put((byte) (payload.remaining() & 0xFF));
        } else {
            // Will never be more than 2^31-1
            headerBuffer.put((byte) (127 | b));
            headerBuffer.put((byte) 0);
            headerBuffer.put((byte) 0);
            headerBuffer.put((byte) 0);
            headerBuffer.put((byte) 0);
            headerBuffer.put((byte) (payload.remaining() >>> 24));
            headerBuffer.put((byte) (payload.remaining() >>> 16));
            headerBuffer.put((byte) (payload.remaining() >>> 8));
            headerBuffer.put((byte) (payload.remaining() & 0xFF));
        }
        if (masked) {
            headerBuffer.put(mask[0]);
            headerBuffer.put(mask[1]);
            headerBuffer.put(mask[2]);
            headerBuffer.put(mask[3]);
        }
    }


    private class TextMessageSendHandler implements SendHandler {

        private final SendHandler handler;
        private final CharBuffer message;
        private final boolean isLast;
        private final CharsetEncoder encoder;
        private final ByteBuffer buffer;
        private final WsRemoteEndpointImplBase endpoint;
        private volatile boolean isDone = false;

        TextMessageSendHandler(SendHandler handler, CharBuffer message, boolean isLast, CharsetEncoder encoder,
                ByteBuffer encoderBuffer, WsRemoteEndpointImplBase endpoint) {
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
            endpoint.startMessage(Constants.OPCODE_TEXT, buffer, isDone && isLast, this);
        }

        @Override
        public void onResult(SendResult result) {
            if (isDone) {
                endpoint.stateMachine.complete(isLast);
                handler.onResult(result);
            } else if (!result.isOK()) {
                handler.onResult(result);
            } else if (closed) {
                SendResult sr = new SendResult(getSession(),
                        new IOException(sm.getString("wsRemoteEndpoint.closedDuringMessage")));
                handler.onResult(sr);
            } else {
                write();
            }
        }
    }


    /**
     * Used to write data to the output buffer, flushing the buffer if it fills up.
     */
    private static class OutputBufferSendHandler implements SendHandler {

        private final SendHandler handler;
        private final long blockingWriteTimeoutExpiry;
        private final ByteBuffer headerBuffer;
        private final ByteBuffer payload;
        private final byte[] mask;
        private final ByteBuffer outputBuffer;
        private final boolean flushRequired;
        private final WsRemoteEndpointImplBase endpoint;
        private volatile int maskIndex = 0;

        OutputBufferSendHandler(SendHandler completion, long blockingWriteTimeoutExpiry, ByteBuffer headerBuffer,
                ByteBuffer payload, byte[] mask, ByteBuffer outputBuffer, boolean flushRequired,
                WsRemoteEndpointImplBase endpoint) {
            this.blockingWriteTimeoutExpiry = blockingWriteTimeoutExpiry;
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
                outputBuffer.flip();
                endpoint.doWrite(this, blockingWriteTimeoutExpiry, outputBuffer);
                return;
            }

            // Write the payload
            int payloadLeft = payload.remaining();
            int payloadLimit = payload.limit();
            int outputSpace = outputBuffer.remaining();
            int toWrite = payloadLeft;

            if (payloadLeft > outputSpace) {
                toWrite = outputSpace;
                // Temporarily reduce the limit
                payload.limit(payload.position() + toWrite);
            }

            if (mask == null) {
                // Use a bulk copy
                outputBuffer.put(payload);
            } else {
                for (int i = 0; i < toWrite; i++) {
                    outputBuffer.put((byte) (payload.get() ^ (mask[maskIndex++] & 0xFF)));
                    if (maskIndex > 3) {
                        maskIndex = 0;
                    }
                }
            }

            if (payloadLeft > outputSpace) {
                // Restore the original limit
                payload.limit(payloadLimit);
                // Still more data to write, need to flush
                outputBuffer.flip();
                endpoint.doWrite(this, blockingWriteTimeoutExpiry, outputBuffer);
                return;
            }

            if (flushRequired) {
                outputBuffer.flip();
                if (outputBuffer.remaining() == 0) {
                    handler.onResult(new SendResult(endpoint.getSession()));
                } else {
                    endpoint.doWrite(this, blockingWriteTimeoutExpiry, outputBuffer);
                }
            } else {
                handler.onResult(new SendResult(endpoint.getSession()));
            }
        }

        // ------------------------------------------------- SendHandler methods
        @Override
        public void onResult(SendResult result) {
            if (result.isOK()) {
                if (outputBuffer.hasRemaining()) {
                    endpoint.doWrite(this, blockingWriteTimeoutExpiry, outputBuffer);
                } else {
                    outputBuffer.clear();
                    write();
                }
            } else {
                handler.onResult(result);
            }
        }
    }


    /**
     * Ensures that the output buffer is cleared after it has been flushed.
     */
    private static class OutputBufferFlushSendHandler implements SendHandler {

        private final ByteBuffer outputBuffer;
        private final SendHandler handler;

        OutputBufferFlushSendHandler(ByteBuffer outputBuffer, SendHandler handler) {
            this.outputBuffer = outputBuffer;
            this.handler = handler;
        }

        @Override
        public void onResult(SendResult result) {
            if (result.isOK()) {
                outputBuffer.clear();
            }
            handler.onResult(result);
        }
    }


    private static class WsOutputStream extends OutputStream {

        private final WsRemoteEndpointImplBase endpoint;
        private final ByteBuffer buffer = ByteBuffer.allocate(Constants.DEFAULT_BUFFER_SIZE);
        private final Object closeLock = new Object();
        private volatile boolean closed = false;
        private volatile boolean used = false;

        WsOutputStream(WsRemoteEndpointImplBase endpoint) {
            this.endpoint = endpoint;
        }

        @Override
        public void write(int b) throws IOException {
            if (closed) {
                throw new IllegalStateException(sm.getString("wsRemoteEndpoint.closedOutputStream"));
            }

            used = true;
            if (buffer.remaining() == 0) {
                flush();
            }
            buffer.put((byte) b);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            if (closed) {
                throw new IllegalStateException(sm.getString("wsRemoteEndpoint.closedOutputStream"));
            }
            if ((off < 0) || (off > b.length) || (len < 0) || ((off + len) > b.length) || ((off + len) < 0)) {
                throw new IndexOutOfBoundsException();
            }

            used = true;

            if (len == 0) {
                return;
            }

            if (buffer.remaining() == 0) {
                flush();
            }
            int remaining = buffer.remaining();
            int written = 0;

            while (remaining < len - written) {
                buffer.put(b, off + written, remaining);
                written += remaining;
                flush();
                remaining = buffer.remaining();
            }
            buffer.put(b, off + written, len - written);
        }

        @Override
        public void flush() throws IOException {
            if (closed) {
                throw new IllegalStateException(sm.getString("wsRemoteEndpoint.closedOutputStream"));
            }

            // Optimisation. If there is no data to flush then do not send an
            // empty message.
            if (buffer.position() > 0) {
                doWrite(false);
            }
        }

        @Override
        public void close() throws IOException {
            synchronized (closeLock) {
                if (closed) {
                    return;
                }
                closed = true;
            }

            doWrite(true);
        }

        private void doWrite(boolean last) throws IOException {
            if (used) {
                buffer.flip();
                endpoint.sendMessageBlock(Constants.OPCODE_BINARY, buffer, last);
            }
            endpoint.stateMachine.complete(last);
            buffer.clear();
        }
    }


    private static class WsWriter extends Writer {

        private final WsRemoteEndpointImplBase endpoint;
        private final CharBuffer buffer = CharBuffer.allocate(Constants.DEFAULT_BUFFER_SIZE);
        private final Object closeLock = new Object();
        private volatile boolean closed = false;
        private volatile boolean used = false;

        WsWriter(WsRemoteEndpointImplBase endpoint) {
            this.endpoint = endpoint;
        }

        @Override
        public void write(char[] cbuf, int off, int len) throws IOException {
            if (closed) {
                throw new IllegalStateException(sm.getString("wsRemoteEndpoint.closedWriter"));
            }
            if ((off < 0) || (off > cbuf.length) || (len < 0) || ((off + len) > cbuf.length) || ((off + len) < 0)) {
                throw new IndexOutOfBoundsException();
            }

            used = true;

            if (len == 0) {
                return;
            }

            if (buffer.remaining() == 0) {
                flush();
            }
            int remaining = buffer.remaining();
            int written = 0;

            while (remaining < len - written) {
                buffer.put(cbuf, off + written, remaining);
                written += remaining;
                flush();
                remaining = buffer.remaining();
            }
            buffer.put(cbuf, off + written, len - written);
        }

        @Override
        public void flush() throws IOException {
            if (closed) {
                throw new IllegalStateException(sm.getString("wsRemoteEndpoint.closedWriter"));
            }

            if (buffer.position() > 0) {
                doWrite(false);
            }
        }

        @Override
        public void close() throws IOException {
            synchronized (closeLock) {
                if (closed) {
                    return;
                }
                closed = true;
            }

            doWrite(true);
        }

        private void doWrite(boolean last) throws IOException {
            if (used) {
                buffer.flip();
                endpoint.sendMessageBlock(buffer, last);
                buffer.clear();
            } else {
                endpoint.stateMachine.complete(last);
            }
        }
    }


    private static class EncoderEntry {

        private final Class<?> clazz;
        private final Encoder encoder;

        EncoderEntry(Class<?> clazz, Encoder encoder) {
            this.clazz = clazz;
            this.encoder = encoder;
        }

        public Class<?> getClazz() {
            return clazz;
        }

        public Encoder getEncoder() {
            return encoder;
        }
    }


    private enum State {
        OPEN,
        STREAM_WRITING,
        WRITER_WRITING,
        BINARY_PARTIAL_WRITING,
        BINARY_PARTIAL_READY,
        BINARY_FULL_WRITING,
        TEXT_PARTIAL_WRITING,
        TEXT_PARTIAL_READY,
        TEXT_FULL_WRITING
    }


    private static class StateMachine {
        private State state = State.OPEN;

        public synchronized void streamStart() {
            checkState(State.OPEN);
            state = State.STREAM_WRITING;
        }

        public synchronized void writeStart() {
            checkState(State.OPEN);
            state = State.WRITER_WRITING;
        }

        public synchronized void binaryPartialStart() {
            checkState(State.OPEN, State.BINARY_PARTIAL_READY);
            state = State.BINARY_PARTIAL_WRITING;
        }

        public synchronized void binaryStart() {
            checkState(State.OPEN);
            state = State.BINARY_FULL_WRITING;
        }

        public synchronized void textPartialStart() {
            checkState(State.OPEN, State.TEXT_PARTIAL_READY);
            state = State.TEXT_PARTIAL_WRITING;
        }

        public synchronized void textStart() {
            checkState(State.OPEN);
            state = State.TEXT_FULL_WRITING;
        }

        public synchronized void complete(boolean last) {
            if (last) {
                checkState(State.TEXT_PARTIAL_WRITING, State.TEXT_FULL_WRITING, State.BINARY_PARTIAL_WRITING,
                        State.BINARY_FULL_WRITING, State.STREAM_WRITING, State.WRITER_WRITING);
                state = State.OPEN;
            } else {
                checkState(State.TEXT_PARTIAL_WRITING, State.BINARY_PARTIAL_WRITING, State.STREAM_WRITING,
                        State.WRITER_WRITING);
                if (state == State.TEXT_PARTIAL_WRITING) {
                    state = State.TEXT_PARTIAL_READY;
                } else if (state == State.BINARY_PARTIAL_WRITING) {
                    state = State.BINARY_PARTIAL_READY;
                } else if (state == State.WRITER_WRITING) {
                    // NO-OP. Leave state as is.
                } else if (state == State.STREAM_WRITING) {
                    // NO-OP. Leave state as is.
                }
            }
        }

        private void checkState(State... required) {
            for (State state : required) {
                if (this.state == state) {
                    return;
                }
            }
            throw new IllegalStateException(sm.getString("wsRemoteEndpoint.wrongState", this.state));
        }
    }


    private static class StateUpdateSendHandler implements SendHandler {

        private final SendHandler handler;
        private final StateMachine stateMachine;

        StateUpdateSendHandler(SendHandler handler, StateMachine stateMachine) {
            this.handler = handler;
            this.stateMachine = stateMachine;
        }

        @Override
        public void onResult(SendResult result) {
            if (result.isOK()) {
                stateMachine.complete(true);
            }
            handler.onResult(result);
        }
    }


    private static class BlockingSendHandler implements SendHandler {

        private volatile SendResult sendResult = null;

        @Override
        public void onResult(SendResult result) {
            sendResult = result;
        }

        public SendResult getSendResult() {
            return sendResult;
        }
    }
}
