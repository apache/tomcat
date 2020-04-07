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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.LinkedBlockingDeque;

import org.apache.tomcat.util.buf.ByteBufferHolder;

/**
 * Provides an expandable set of buffers for writes. Non-blocking writes can be
 * of any size and may not be able to be written immediately or wholly contained
 * in the buffer used to perform the writes to the next layer. This class
 * provides a buffering capability to allow such writes to return immediately
 * and also allows for the user provided buffers to be re-used / recycled as
 * required.
 */
public class WriteBuffer {

    private final int bufferSize;

    private final LinkedBlockingDeque<ByteBufferHolder> buffers = new LinkedBlockingDeque<>();

    public WriteBuffer(int bufferSize) {
        this.bufferSize = bufferSize;
    }


    void add(byte[] buf, int offset, int length) {
        ByteBufferHolder holder = getByteBufferHolder(length);
        holder.getBuf().put(buf, offset, length);
    }


    public void add(ByteBuffer from) {
        ByteBufferHolder holder = getByteBufferHolder(from.remaining());
        holder.getBuf().put(from);
    }


    private ByteBufferHolder getByteBufferHolder(int capacity) {
        ByteBufferHolder holder = buffers.peekLast();
        if (holder == null || holder.isFlipped() || holder.getBuf().remaining() < capacity) {
            ByteBuffer buffer = ByteBuffer.allocate(Math.max(bufferSize, capacity));
            holder = new ByteBufferHolder(buffer, false);
            buffers.add(holder);
        }
        return holder;
    }


    public boolean isEmpty() {
        return buffers.isEmpty();
    }


    /**
     * Create an array of ByteBuffers from the current WriteBuffer, prefixing
     * that array with the provided ByteBuffers.
     *
     * @param prefixes The additional ByteBuffers to add to the start of the
     *                 array
     *
     * @return an array of ByteBuffers from the current WriteBuffer prefixed by
     *         the provided ByteBuffers
     */
    ByteBuffer[] toArray(ByteBuffer... prefixes) {
        List<ByteBuffer> result = new ArrayList<>();
        for (ByteBuffer prefix : prefixes) {
            if (prefix.hasRemaining()) {
                result.add(prefix);
            }
        }
        for (ByteBufferHolder buffer : buffers) {
            buffer.flip();
            result.add(buffer.getBuf());
        }
        buffers.clear();
        return result.toArray(new ByteBuffer[0]);
    }


    boolean write(SocketWrapperBase<?> socketWrapper, boolean blocking) throws IOException {
        Iterator<ByteBufferHolder> bufIter = buffers.iterator();
        boolean dataLeft = false;
        while (!dataLeft && bufIter.hasNext()) {
            ByteBufferHolder buffer = bufIter.next();
            buffer.flip();
            if (blocking) {
                socketWrapper.writeBlocking(buffer.getBuf());
            } else {
                socketWrapper.writeNonBlockingInternal(buffer.getBuf());
            }
            if (buffer.getBuf().remaining() == 0) {
                bufIter.remove();
            } else {
                dataLeft = true;
            }
        }
        return dataLeft;
    }


    public boolean write(Sink sink, boolean blocking) throws IOException {
        Iterator<ByteBufferHolder> bufIter = buffers.iterator();
        boolean dataLeft = false;
        while (!dataLeft && bufIter.hasNext()) {
            ByteBufferHolder buffer = bufIter.next();
            buffer.flip();
            dataLeft = sink.writeFromBuffer(buffer.getBuf(), blocking);
            if (!dataLeft) {
                bufIter.remove();
            }
        }
        return dataLeft;
    }


    /**
     * Interface implemented by clients of the WriteBuffer to enable data to be
     * written back out from the buffer.
     */
    public interface Sink {
        boolean writeFromBuffer(ByteBuffer buffer, boolean block) throws IOException;
    }
}
