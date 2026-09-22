/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.catalina.tribes.transport.nio;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import javax.net.ssl.SSLEngineResult.Status;

final class TlsChannel implements ByteChannel {

    private static final ByteBuffer EMPTY = ByteBuffer.allocate(0);

    private final Socket socket;
    private final InputStream input;
    private final OutputStream output;
    private final SSLEngine engine;
    private ByteBuffer networkInput;
    private ByteBuffer networkOutput;
    private ByteBuffer applicationInput;

    TlsChannel(Socket socket, SSLEngine engine) throws IOException {
        this.socket = socket;
        input = socket.getInputStream();
        output = socket.getOutputStream();
        this.engine = engine;
        int packetSize = engine.getSession().getPacketBufferSize();
        networkInput = ByteBuffer.allocate(packetSize);
        networkOutput = ByteBuffer.allocate(packetSize);
        applicationInput = ByteBuffer.allocate(engine.getSession().getApplicationBufferSize());
        applicationInput.flip();
        engine.beginHandshake();
        handshake();
    }

    private void handshake() throws IOException {
        HandshakeStatus status = engine.getHandshakeStatus();
        while (status != HandshakeStatus.FINISHED && status != HandshakeStatus.NOT_HANDSHAKING) {
            switch (status) {
                case NEED_TASK -> {
                    Runnable task;
                    while ((task = engine.getDelegatedTask()) != null) {
                        task.run();
                    }
                }
                case NEED_WRAP -> {
                    networkOutput.clear();
                    SSLEngineResult result = engine.wrap(EMPTY, networkOutput);
                    status = result.getHandshakeStatus();
                    networkOutput.flip();
                    writeFully(networkOutput);
                    continue;
                }
                case NEED_UNWRAP, NEED_UNWRAP_AGAIN -> {
                    status = unwrapHandshake();
                    continue;
                }
                default -> {
                    // FINISHED and NOT_HANDSHAKING are handled by the loop condition.
                }
            }
            status = engine.getHandshakeStatus();
        }
        applicationInput.clear();
        applicationInput.flip();
    }

    @Override
    public int read(ByteBuffer destination) throws IOException {
        if (applicationInput.hasRemaining()) {
            return transfer(applicationInput, destination);
        }
        applicationInput.clear();
        while (true) {
            if (networkInput.position() == 0 && readEncrypted() < 0) {
                return -1;
            }
            networkInput.flip();
            SSLEngineResult result = engine.unwrap(networkInput, applicationInput);
            networkInput.compact();
            if (result.getStatus() == Status.BUFFER_OVERFLOW) {
                applicationInput = expand(applicationInput, engine.getSession().getApplicationBufferSize());
                continue;
            }
            if (result.getStatus() == Status.BUFFER_UNDERFLOW) {
                if (applicationInput.position() > 0) {
                    applicationInput.flip();
                    return transfer(applicationInput, destination);
                }
                if (!networkInput.hasRemaining()) {
                    networkInput = expand(networkInput, engine.getSession().getPacketBufferSize());
                }
                if (readEncrypted() < 0) {
                    return -1;
                }
                continue;
            }
            applicationInput.flip();
            if (result.getStatus() == Status.CLOSED) {
                return -1;
            }
            if (applicationInput.hasRemaining()) {
                return transfer(applicationInput, destination);
            }
        }
    }

    private HandshakeStatus unwrapHandshake() throws IOException {
        while (true) {
            HandshakeStatus handshakeStatus = engine.getHandshakeStatus();
            if (networkInput.position() == 0 && handshakeStatus != HandshakeStatus.NEED_UNWRAP_AGAIN &&
                    readEncrypted() < 0) {
                throw new EOFException();
            }
            networkInput.flip();
            applicationInput.clear();
            SSLEngineResult result = engine.unwrap(networkInput, applicationInput);
            networkInput.compact();
            if (result.getStatus() == Status.CLOSED) {
                throw new EOFException();
            }
            if (result.getStatus() == Status.BUFFER_OVERFLOW) {
                applicationInput = expand(applicationInput, engine.getSession().getApplicationBufferSize());
                continue;
            }
            if (result.getStatus() == Status.BUFFER_UNDERFLOW) {
                if (!networkInput.hasRemaining()) {
                    networkInput = expand(networkInput, engine.getSession().getPacketBufferSize());
                }
                if (readEncrypted() < 0) {
                    throw new EOFException();
                }
                continue;
            }
            return result.getHandshakeStatus();
        }
    }

    @Override
    public int write(ByteBuffer source) throws IOException {
        int start = source.remaining();
        while (source.hasRemaining()) {
            networkOutput.clear();
            SSLEngineResult result = engine.wrap(source, networkOutput);
            if (result.getStatus() == Status.CLOSED) {
                throw new EOFException();
            }
            networkOutput.flip();
            writeFully(networkOutput);
        }
        return start;
    }

    private void writeFully(ByteBuffer buffer) throws IOException {
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        output.write(bytes);
        output.flush();
    }

    private int readEncrypted() throws IOException {
        byte[] bytes = new byte[networkInput.remaining()];
        int read = input.read(bytes);
        if (read > 0) {
            networkInput.put(bytes, 0, read);
        }
        return read;
    }

    private static ByteBuffer expand(ByteBuffer input, int minimumCapacity) {
        int capacity = Math.max(input.capacity() * 2, minimumCapacity);
        ByteBuffer result = ByteBuffer.allocate(capacity);
        input.flip();
        result.put(input);
        return result;
    }

    private static int transfer(ByteBuffer source, ByteBuffer destination) {
        int length = Math.min(source.remaining(), destination.remaining());
        int limit = source.limit();
        source.limit(source.position() + length);
        destination.put(source);
        source.limit(limit);
        return length;
    }

    @Override
    public boolean isOpen() {
        return !socket.isClosed();
    }

    @Override
    public void close() throws IOException {
        try {
            engine.closeOutbound();
        } finally {
            socket.close();
        }
    }
}
