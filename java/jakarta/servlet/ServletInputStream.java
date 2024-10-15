/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package jakarta.servlet;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * Provides an input stream for reading binary data from a client request, including an efficient <code>readLine</code>
 * method for reading data one line at a time. With some protocols, such as HTTP POST and PUT, a
 * <code>ServletInputStream</code> object can be used to read data sent from the client.
 * <p>
 * A <code>ServletInputStream</code> object is normally retrieved via the {@link ServletRequest#getInputStream} method.
 * <p>
 * This is an abstract class that a servlet container implements. Subclasses of this class must implement the
 * <code>java.io.InputStream.read()</code> method.
 *
 * @see ServletRequest
 */
public abstract class ServletInputStream extends InputStream {

    /**
     * Does nothing, because this is an abstract class.
     */
    protected ServletInputStream() {
        // NOOP
    }

    /**
     * Reads from the input stream into the given buffer.
     * <p>
     * If the input stream is in non-blocking mode, before each invocation of this method {@link #isReady()} must be
     * called and must return {@code true} or the {@link ReadListener#onDataAvailable()} call back must indicate that
     * data is available to read else an {@link IllegalStateException} must be thrown.
     * <p>
     * Otherwise, if this method is called when {@code buffer} has no space remaining, the method returns {@code 0}
     * immediately and {@code buffer} is unchanged.
     * <p>
     * If the input stream is in blocking mode and {@code buffer} has space remaining, this method blocks until at least
     * one byte has been read, end of stream is reached or an exception is thrown.
     * <p>
     * Returns the number of bytes read or {@code -1} if the end of stream is reached without reading any data.
     * <p>
     * When the method returns, and if data has been read, the buffer's position will be unchanged from the value when
     * passed to this method and the limit will be the position incremented by the number of bytes read.
     * <p>
     * Subclasses are strongly encouraged to override this method and provide a more efficient implementation.
     *
     * @param buffer The buffer into which the data is read.
     *
     * @return The number of bytes read or {@code -1} if the end of the stream has been reached.
     *
     * @exception IllegalStateException If the input stream is in non-blocking mode and this method is called without
     *                                      first calling {@link #isReady()} and that method has returned {@code true}
     *                                      or {@link ReadListener#onDataAvailable()} has not signalled that data is
     *                                      available to read.
     * @exception IOException           If data cannot be read for any reason other than the end of stream being
     *                                      reached, the input stream has been closed or if some other I/O error occurs.
     * @exception NullPointerException  If buffer is null.
     *
     * @since Servlet 6.1
     */
    public int read(ByteBuffer buffer) throws IOException {
        Objects.requireNonNull(buffer);

        if (!isReady()) {
            throw new IllegalStateException();
        }

        if (buffer.remaining() == 0) {
            return 0;
        }

        byte[] b = new byte[buffer.remaining()];

        int result = read(b);
        if (result == -1) {
            return -1;
        }

        int position = buffer.position();

        buffer.put(b, 0, result);

        buffer.position(position);
        buffer.limit(position + result);

        return result;
    }

    /**
     * Reads the input stream, one line at a time. Starting at an offset, reads bytes into an array, until it reads a
     * certain number of bytes or reaches a newline character, which it reads into the array as well.
     * <p>
     * This method returns -1 if it reaches the end of the input stream before reading the maximum number of bytes.
     * <p>
     * This method may only be used when the input stream is in blocking mode.
     *
     * @param b   an array of bytes into which data is read
     * @param off an integer specifying the character at which this method begins reading
     * @param len an integer specifying the maximum number of bytes to read
     *
     * @return an integer specifying the actual number of bytes read, or -1 if the end of the stream is reached
     *
     * @exception IllegalStateException If this method is called when the input stream is in non-blocking mode.
     * @exception IOException           if an input or output exception has occurred
     */
    public int readLine(byte[] b, int off, int len) throws IOException {

        if (len <= 0) {
            return 0;
        }
        int count = 0, c;

        while ((c = read()) != -1) {
            b[off++] = (byte) c;
            count++;
            if (c == '\n' || count == len) {
                break;
            }
        }
        return count > 0 ? count : -1;
    }

    /**
     * Has the end of this InputStream been reached?
     *
     * @return <code>true</code> if all the data has been read from the stream, else <code>false</code>
     *
     * @since Servlet 3.1
     */
    public abstract boolean isFinished();

    /**
     * Returns {@code true} if it is allowable to call a {@code read()} method. In blocking mode, this method will
     * always return {@code true}, but a subsequent call to a {@code read()} method may block awaiting data. In
     * non-blocking mode this method may return {@code false}, in which case it is illegal to call a {@code read()}
     * method and an {@link IllegalStateException} MUST be thrown. When {@link ReadListener#onDataAvailable()} is
     * called, a call to this method that returned {@code true} is implicit.
     * <p>
     * If this method returns {@code false} and a {@link ReadListener} has been set via
     * {@link #setReadListener(ReadListener)}, then the container will subsequently invoke
     * {@link ReadListener#onDataAvailable()} (or {@link ReadListener#onAllDataRead()}) once data (or EOF) has become
     * available. Other than the initial call {@link ReadListener#onDataAvailable()} will only be called if and only if
     * this method is called and returns false.
     *
     * @return {@code true} if data can be obtained without blocking, otherwise returns {@code false}.
     *
     * @since Servlet 3.1
     */
    public abstract boolean isReady();

    /**
     * Sets the {@link ReadListener} for this {@link ServletInputStream} and thereby switches to non-blocking IO. It is
     * only valid to switch to non-blocking IO within async processing or HTTP upgrade processing.
     *
     * @param listener The non-blocking IO read listener
     *
     * @throws IllegalStateException If this method is called if neither async nor HTTP upgrade is in progress or if the
     *                                   {@link ReadListener} has already been set
     * @throws NullPointerException  If listener is null
     *
     * @since Servlet 3.1
     */
    public abstract void setReadListener(ReadListener listener);

    /**
     * {@inheritDoc}
     * <p>
     * This method may only be used when the input stream is in blocking mode.
     *
     * @exception IllegalStateException If this method is called when the input stream is in non-blocking mode.
     */
    @Override
    public byte[] readAllBytes() throws IOException {
        return super.readAllBytes();
    }

    /**
     * {@inheritDoc}
     * <p>
     * This method may only be used when the input stream is in blocking mode.
     *
     * @exception IllegalStateException If this method is called when the input stream is in non-blocking mode.
     */
    @Override
    public byte[] readNBytes(int len) throws IOException {
        return super.readNBytes(len);
    }

    /**
     * {@inheritDoc}
     * <p>
     * This method may only be used when the input stream is in blocking mode.
     *
     * @exception IllegalStateException If this method is called when the input stream is in non-blocking mode.
     */
    @Override
    public int readNBytes(byte[] b, int off, int len) throws IOException {
        return super.readNBytes(b, off, len);
    }
}
