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
package org.apache.tomcat.util.net;

import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;

import org.apache.tomcat.util.buf.ByteBufferUtils;

/**
 * Manages read and write {@link ByteBuffer} instances for a socket connection,
 * handling buffer state transitions between read and write modes.
 */
public class SocketBufferHandler {

    /**
     * A no-op instance with zero-length buffers used when buffering is not required.
     */
    static SocketBufferHandler EMPTY = new SocketBufferHandler(0, 0, false) {
        @Override
        public void expand(int newSize) {
            // NO-OP
        }

        /*
         * Http2AsyncParser$FrameCompletionHandler will return incomplete frame(s) to the buffer. If the previous frame
         * (or concurrent write to a stream) triggered a connection close this call would fail with a
         * BufferOverflowException as data can't be returned to a buffer of zero length. Override the method and make it
         * a NO-OP to avoid triggering the exception.
         */
        @Override
        public void unReadReadBuffer(ByteBuffer returnedData) {
            // NO-OP
        }
    };

    private volatile boolean readBufferConfiguredForWrite = true;
    private volatile ByteBuffer readBuffer;

    private volatile boolean writeBufferConfiguredForWrite = true;
    private volatile ByteBuffer writeBuffer;

    private final int readBufferSize;
    private final int writeBufferSize;
    private final boolean direct;

    /**
     * Creates a new SocketBufferHandler with the specified buffer sizes.
     * The buffers are allocated lazily, on first use: a handler whose
     * buffers are never touched allocates nothing, and a fresh (never
     * allocated) buffer is by definition empty and write-configured, the
     * same state a freshly allocated one would be in.
     * @param readBufferSize the size of the read buffer in bytes
     * @param writeBufferSize the size of the write buffer in bytes
     * @param direct whether to allocate direct (off-heap) buffers
     */
    public SocketBufferHandler(int readBufferSize, int writeBufferSize, boolean direct) {
        this.readBufferSize = readBufferSize;
        this.writeBufferSize = writeBufferSize;
        this.direct = direct;
    }

    private ByteBuffer allocate(int size) {
        return direct ? ByteBuffer.allocateDirect(size) : ByteBuffer.allocate(size);
    }

    private ByteBuffer readBuffer() {
        ByteBuffer buf = readBuffer;
        if (buf == null) {
            synchronized (this) {
                buf = readBuffer;
                if (buf == null) {
                    buf = allocate(readBufferSize);
                    readBuffer = buf;
                }
            }
        }
        return buf;
    }

    private ByteBuffer writeBuffer() {
        ByteBuffer buf = writeBuffer;
        if (buf == null) {
            synchronized (this) {
                buf = writeBuffer;
                if (buf == null) {
                    buf = allocate(writeBufferSize);
                    writeBuffer = buf;
                }
            }
        }
        return buf;
    }


    /**
     * Switches the read buffer into write mode.
     */
    public void configureReadBufferForWrite() {
        setReadBufferConfiguredForWrite(true);
    }


    /**
     * Switches the read buffer into read mode.
     */
    public void configureReadBufferForRead() {
        setReadBufferConfiguredForWrite(false);
    }


    private void setReadBufferConfiguredForWrite(boolean readBufferConFiguredForWrite) {
        // NO-OP if buffer is already in correct state
        if (this.readBufferConfiguredForWrite != readBufferConFiguredForWrite) {
            ByteBuffer rb = readBuffer();
            if (readBufferConFiguredForWrite) {
                // Switching to write
                int remaining = rb.remaining();
                if (remaining == 0) {
                    rb.clear();
                } else {
                    rb.compact();
                }
            } else {
                // Switching to read
                rb.flip();
            }
            this.readBufferConfiguredForWrite = readBufferConFiguredForWrite;
        }
    }


    /**
     * Returns the read buffer.
     * @return the read buffer
     */
    public ByteBuffer getReadBuffer() {
        return readBuffer();
    }


    /**
     * Checks whether the read buffer contains any data.
     * @return {@code true} if the read buffer is empty
     */
    public boolean isReadBufferEmpty() {
        // An unallocated buffer is empty; no point allocating one to find
        // that out.
        ByteBuffer rb = readBuffer;
        if (rb == null) {
            return true;
        }
        if (readBufferConfiguredForWrite) {
            return rb.position() == 0;
        } else {
            return rb.remaining() == 0;
        }
    }


    /**
     * Inserts previously read data back into the read buffer so it can be read again.
     * @param returnedData the data to insert back into the buffer
     * @throws java.nio.BufferOverflowException if the buffer cannot accommodate the returned data
     */
    public void unReadReadBuffer(ByteBuffer returnedData) {
        if (isReadBufferEmpty()) {
            configureReadBufferForWrite();
            readBuffer().put(returnedData);
        } else {
            ByteBuffer rb = readBuffer;
            int bytesReturned = returnedData.remaining();
            if (readBufferConfiguredForWrite) {
                // Writes always start at position zero
                if ((rb.position() + bytesReturned) > rb.capacity()) {
                    throw new BufferOverflowException();
                } else {
                    // Move the bytes up to make space for the returned data.
                    // Copy backwards so that, when the source and destination
                    // regions overlap, the source bytes are not overwritten
                    // before they have been read.
                    for (int i = rb.position() - 1; i >= 0; i--) {
                        rb.put(i + bytesReturned, rb.get(i));
                    }
                    // Insert the bytes returned
                    for (int i = 0; i < bytesReturned; i++) {
                        rb.put(i, returnedData.get());
                    }
                    // Update the position
                    rb.position(rb.position() + bytesReturned);
                }
            } else {
                // Reads will start at zero but may have progressed
                int shiftRequired = bytesReturned - rb.position();
                if (shiftRequired > 0) {
                    if ((rb.capacity() - rb.limit()) < shiftRequired) {
                        throw new BufferOverflowException();
                    }
                    // Move the bytes up to make space for the returned data.
                    // Copy backwards so that, when the source and destination
                    // regions overlap, the source bytes are not overwritten
                    // before they have been read.
                    int oldLimit = rb.limit();
                    rb.limit(oldLimit + shiftRequired);
                    for (int i = oldLimit - 1; i >= rb.position(); i--) {
                        rb.put(i + shiftRequired, rb.get(i));
                    }
                } else {
                    shiftRequired = 0;
                }
                // Insert the returned bytes
                int insertOffset = rb.position() + shiftRequired - bytesReturned;
                for (int i = insertOffset; i < bytesReturned + insertOffset; i++) {
                    rb.put(i, returnedData.get());
                }
                rb.position(insertOffset);
            }
        }
    }


    /**
     * Switches the write buffer into write mode.
     */
    public void configureWriteBufferForWrite() {
        setWriteBufferConfiguredForWrite(true);
    }


    /**
     * Switches the write buffer into read mode.
     */
    public void configureWriteBufferForRead() {
        setWriteBufferConfiguredForWrite(false);
    }


    private void setWriteBufferConfiguredForWrite(boolean writeBufferConfiguredForWrite) {
        // NO-OP if buffer is already in correct state
        if (this.writeBufferConfiguredForWrite != writeBufferConfiguredForWrite) {
            ByteBuffer wb = writeBuffer();
            if (writeBufferConfiguredForWrite) {
                // Switching to write
                int remaining = wb.remaining();
                if (remaining == 0) {
                    wb.clear();
                } else {
                    wb.compact();
                    wb.position(remaining);
                    wb.limit(wb.capacity());
                }
            } else {
                // Switching to read
                wb.flip();
            }
            this.writeBufferConfiguredForWrite = writeBufferConfiguredForWrite;
        }
    }


    /**
     * Checks whether the write buffer has space for additional data.
     * @return {@code true} if the write buffer can accept more data
     */
    public boolean isWriteBufferWritable() {
        // An unallocated buffer is empty and, if it has any size at all,
        // writable; no point allocating one to find that out.
        ByteBuffer wb = writeBuffer;
        if (wb == null) {
            return writeBufferSize > 0;
        }
        if (writeBufferConfiguredForWrite) {
            return wb.hasRemaining();
        } else {
            return wb.remaining() == 0;
        }
    }


    /**
     * Returns the write buffer.
     * @return the write buffer
     */
    public ByteBuffer getWriteBuffer() {
        return writeBuffer();
    }


    /**
     * Checks whether the write buffer contains any data.
     * @return {@code true} if the write buffer is empty
     */
    public boolean isWriteBufferEmpty() {
        // An unallocated buffer is empty; no point allocating one to find
        // that out.
        ByteBuffer wb = writeBuffer;
        if (wb == null) {
            return true;
        }
        if (writeBufferConfiguredForWrite) {
            return wb.position() == 0;
        } else {
            return wb.remaining() == 0;
        }
    }


    /**
     * Resets both read and write buffers to their initial empty state.
     */
    public void reset() {
        ByteBuffer rb = readBuffer;
        if (rb != null) {
            rb.clear();
        }
        readBufferConfiguredForWrite = true;
        ByteBuffer wb = writeBuffer;
        if (wb != null) {
            wb.clear();
        }
        writeBufferConfiguredForWrite = true;
    }


    /**
     * Expands both read and write buffers to the specified size.
     * @param newSize the new buffer size in bytes
     */
    public void expand(int newSize) {
        configureReadBufferForWrite();
        readBuffer = ByteBufferUtils.expand(readBuffer(), newSize);
        configureWriteBufferForWrite();
        writeBuffer = ByteBufferUtils.expand(writeBuffer(), newSize);
    }

    /**
     * Releases native resources for direct buffers, if applicable.
     */
    public void free() {
        if (direct) {
            ByteBuffer rb = readBuffer;
            if (rb != null) {
                ByteBufferUtils.cleanDirectBuffer(rb);
            }
            ByteBuffer wb = writeBuffer;
            if (wb != null) {
                ByteBufferUtils.cleanDirectBuffer(wb);
            }
        }
    }

}
