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
 * Callback interface to be able to expand buffers when buffer overflow
 * exceptions happen or to replace buffers
 */
public interface ApplicationBufferHandler {

    ByteBuffer EMPTY_BUFFER = ByteBuffer.allocate(0);

    ApplicationBufferHandler EMPTY = new ApplicationBufferHandler() {
        @Override
        public void expand(int newSize) {
        }
        @Override
        public void setByteBuffer(ByteBuffer buffer) {
        }
        @Override
        public ByteBuffer getByteBuffer() {
            return EMPTY_BUFFER;
        }
    };

    /**
     * Set the byte buffer.
     * @param buffer the byte buffer
     */
    void setByteBuffer(ByteBuffer buffer);

    /**
     * @return the byte buffer
     */
    ByteBuffer getByteBuffer();

    /**
     * Expand the byte buffer to at least the given size. Some implementations may not implement this.
     * @param size the desired size
     */
    void expand(int size);

}
