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

public class SocketBufferHandler {

    static SocketBufferHandler EMPTY = new SocketBufferHandler(0, 0, false) {
        @Override
        public void expand(int newSize) {
        }
        /*
         * Http2AsyncParser$FrameCompletionHandler will return incomplete
         * frame(s) to the buffer. If the previous frame (or concurrent write to
         * a stream) triggered a connection close this call would fail with a
         * BufferOverflowException as data can't be returned to a buffer of zero
         * length. Override the method and make it a NO-OP to avoid triggering
         * the exception.
         */
        @Override
        public void unReadReadBuffer(ByteBuffer returnedData) {
        }
    };

    private volatile boolean readBufferConfiguredForWrite = true;
    private volatile ByteBuffer readBuffer;

    private volatile boolean writeBufferConfiguredForWrite = true;
    private volatile ByteBuffer writeBuffer;

    private final boolean direct;

    public SocketBufferHandler(int readBufferSize, int writeBufferSize,
            boolean direct) {
        this.direct = direct;
        if (direct) {
            readBuffer = ByteBuffer.allocateDirect(readBufferSize);
            writeBuffer = ByteBuffer.allocateDirect(writeBufferSize);
        } else {
            readBuffer = ByteBuffer.allocate(readBufferSize);
            writeBuffer = ByteBuffer.allocate(writeBufferSize);
        }
    }


    public void configureReadBufferForWrite() {
        setReadBufferConfiguredForWrite(true);
    }


    public void configureReadBufferForRead() {
        setReadBufferConfiguredForWrite(false);
    }


    private void setReadBufferConfiguredForWrite(boolean readBufferConFiguredForWrite) {
        // NO-OP if buffer is already in correct state
        if (this.readBufferConfiguredForWrite != readBufferConFiguredForWrite) {
            if (readBufferConFiguredForWrite) {
                // Switching to write
                int remaining = readBuffer.remaining();
                if (remaining == 0) {
                    readBuffer.clear();
                } else {
                    readBuffer.compact();
                }
            } else {
                // Switching to read
                readBuffer.flip();
            }
            this.readBufferConfiguredForWrite = readBufferConFiguredForWrite;
        }
    }


    public ByteBuffer getReadBuffer() {
        return readBuffer;
    }


    public boolean isReadBufferEmpty() {
        if (readBufferConfiguredForWrite) {
            return readBuffer.position() == 0;
        } else {
            return readBuffer.remaining() == 0;
        }
    }


    public void unReadReadBuffer(ByteBuffer returnedData) {
        if (isReadBufferEmpty()) {
            configureReadBufferForWrite();
            readBuffer.put(returnedData);
        } else {
            int bytesReturned = returnedData.remaining();
            if (readBufferConfiguredForWrite) {
                // Writes always start at position zero
                if ((readBuffer.position() + bytesReturned) > readBuffer.capacity()) {
                    throw new BufferOverflowException();
                } else {
                    // Move the bytes up to make space for the returned data
                    for (int i = 0; i < readBuffer.position(); i++) {
                        readBuffer.put(i + bytesReturned, readBuffer.get(i));
                    }
                    // Insert the bytes returned
                    for (int i = 0; i < bytesReturned; i++) {
                        readBuffer.put(i, returnedData.get());
                    }
                    // Update the position
                    readBuffer.position(readBuffer.position() + bytesReturned);
                }
            } else {
                // Reads will start at zero but may have progressed
                int shiftRequired = bytesReturned - readBuffer.position();
                if (shiftRequired > 0) {
                    if ((readBuffer.capacity() - readBuffer.limit()) < shiftRequired) {
                        throw new BufferOverflowException();
                    }
                    // Move the bytes up to make space for the returned data
                    int oldLimit = readBuffer.limit();
                    readBuffer.limit(oldLimit + shiftRequired);
                    for (int i = readBuffer.position(); i < oldLimit; i++) {
                        readBuffer.put(i + shiftRequired, readBuffer.get(i));
                    }
                } else {
                    shiftRequired = 0;
                }
                // Insert the returned bytes
                int insertOffset = readBuffer.position() + shiftRequired - bytesReturned;
                for (int i = insertOffset; i < bytesReturned + insertOffset; i++) {
                    readBuffer.put(i, returnedData.get());
                }
                readBuffer.position(insertOffset);
            }
        }
    }


    public void configureWriteBufferForWrite() {
        setWriteBufferConfiguredForWrite(true);
    }


    public void configureWriteBufferForRead() {
        setWriteBufferConfiguredForWrite(false);
    }


    private void setWriteBufferConfiguredForWrite(boolean writeBufferConfiguredForWrite) {
        // NO-OP if buffer is already in correct state
        if (this.writeBufferConfiguredForWrite != writeBufferConfiguredForWrite) {
            if (writeBufferConfiguredForWrite) {
                // Switching to write
                int remaining = writeBuffer.remaining();
                if (remaining == 0) {
                    writeBuffer.clear();
                } else {
                    writeBuffer.compact();
                    writeBuffer.position(remaining);
                    writeBuffer.limit(writeBuffer.capacity());
                }
            } else {
                // Switching to read
                writeBuffer.flip();
            }
            this.writeBufferConfiguredForWrite = writeBufferConfiguredForWrite;
        }
    }


    public boolean isWriteBufferWritable() {
        if (writeBufferConfiguredForWrite) {
            return writeBuffer.hasRemaining();
        } else {
            return writeBuffer.remaining() == 0;
        }
    }


    public ByteBuffer getWriteBuffer() {
        return writeBuffer;
    }


    public boolean isWriteBufferEmpty() {
        if (writeBufferConfiguredForWrite) {
            return writeBuffer.position() == 0;
        } else {
            return writeBuffer.remaining() == 0;
        }
    }


    public void reset() {
        readBuffer.clear();
        readBufferConfiguredForWrite = true;
        writeBuffer.clear();
        writeBufferConfiguredForWrite = true;
    }


    public void expand(int newSize) {
        configureReadBufferForWrite();
        readBuffer = ByteBufferUtils.expand(readBuffer, newSize);
        configureWriteBufferForWrite();
        writeBuffer = ByteBufferUtils.expand(writeBuffer, newSize);
    }

    public void free() {
        if (direct) {
            ByteBufferUtils.cleanDirectBuffer(readBuffer);
            ByteBufferUtils.cleanDirectBuffer(writeBuffer);
        }
    }

}
