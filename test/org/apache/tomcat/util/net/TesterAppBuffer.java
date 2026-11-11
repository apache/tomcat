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
package org.apache.tomcat.util.net;

import java.nio.ByteBuffer;

/**
 * An application read buffer for the secure channel tests. Its
 * {@code expand(size)} either does nothing or moves the limit up to
 * {@code size}, as {@code Http11InputBuffer.expand()} does when the buffer is
 * already large enough.
 */
public class TesterAppBuffer implements ApplicationBufferHandler {

    private final boolean moveLimit;
    private ByteBuffer buffer;


    public TesterAppBuffer(int capacity, int position, int limit, boolean moveLimit) {
        this.buffer = ByteBuffer.allocate(capacity);
        this.buffer.limit(limit);
        this.buffer.position(position);
        this.moveLimit = moveLimit;
    }


    @Override
    public void setByteBuffer(ByteBuffer buffer) {
        this.buffer = buffer;
    }


    @Override
    public ByteBuffer getByteBuffer() {
        return buffer;
    }


    @Override
    public void expand(int size) {
        if (moveLimit) {
            buffer.limit(Math.min(size, buffer.capacity()));
        }
    }
}
