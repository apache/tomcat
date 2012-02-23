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
package org.apache.catalina.websocket;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;

/**
 * Base implementation of the class used to process WebSocket connections based
 * on messages. Applications should extend this class to provide application
 * specific functionality. Applications that wish to operate on a stream basis
 * rather than a message basis should use {@link StreamInbound}.
 */

public abstract class MessageInbound extends StreamInbound {

    // 2MB - like maxPostSize
    private int byteBufferMaxSize = 2097152;
    private int charBufferMaxSize = 2097152;

    private ByteBuffer bb = ByteBuffer.allocate(8192);
    private CharBuffer cb = CharBuffer.allocate(8192);

    @Override
    protected void onBinaryData(InputStream is) throws IOException {
        int read = 0;
        while (read > -1) {
            bb.position(bb.position() + read);
            if (bb.remaining() == 0) {
                resizeByteBuffer();
            }
            read = is.read(bb.array(), bb.position(), bb.remaining());
        }
        bb.flip();
        onBinaryMessage(bb);
        bb.clear();
    }

    @Override
    protected void onTextData(Reader r) throws IOException {
        int read = 0;
        while (read > -1) {
            cb.position(cb.position() + read);
            if (cb.remaining() == 0) {
                resizeCharBuffer();
            }
            // TODO This should fail on invalid UTF-8 input but doesn't
            read = r.read(cb.array(), cb.position(), cb.remaining());
        }
        cb.flip();
        onTextMessage(cb);
        cb.clear();
    }

    private void resizeByteBuffer() throws IOException {
        int maxSize = getByteBufferMaxSize();
        if (bb.limit() >= maxSize) {
            // TODO i18n
            throw new IOException("Buffer not big enough for message");
        }

        long newSize = bb.limit() * 2;
        if (newSize > maxSize) {
            newSize = maxSize;
        }

        // Cast is safe. newSize < maxSize and maxSize is an int
        ByteBuffer newBuffer = ByteBuffer.allocate((int) newSize);
        bb.rewind();
        newBuffer.put(bb);
        bb = newBuffer;
    }

    private void resizeCharBuffer() throws IOException {
        int maxSize = getCharBufferMaxSize();
        if (cb.limit() >= maxSize) {
            // TODO i18n
            throw new IOException("Buffer not big enough for message");
        }

        long newSize = cb.limit() * 2;
        if (newSize > maxSize) {
            newSize = maxSize;
        }

        // Cast is safe. newSize < maxSize and maxSize is an int
        CharBuffer newBuffer = CharBuffer.allocate((int) newSize);
        cb.rewind();
        newBuffer.put(cb);
        cb = newBuffer;
    }

    public int getByteBufferMaxSize() {
        return byteBufferMaxSize;
    }

    public void setByteBufferMaxSize(int byteBufferMaxSize) {
        this.byteBufferMaxSize = byteBufferMaxSize;
    }

    public int getCharBufferMaxSize() {
        return charBufferMaxSize;
    }

    public void setCharBufferMaxSize(int charBufferMaxSize) {
        this.charBufferMaxSize = charBufferMaxSize;
    }

    protected abstract void onBinaryMessage(ByteBuffer message)
            throws IOException;
    protected abstract void onTextMessage(CharBuffer message)
            throws IOException;
}
