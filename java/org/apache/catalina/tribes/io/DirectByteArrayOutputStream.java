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
package org.apache.catalina.tribes.io;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Byte array output stream that exposes the byte array directly
 */
public class DirectByteArrayOutputStream extends OutputStream {

    private final XByteBuffer buffer;

    /**
     * Construct a new DirectByteArrayOutputStream with the given initial size.
     *
     * @param size Initial size of the internal buffer
     */
    public DirectByteArrayOutputStream(int size) {
        buffer = new XByteBuffer(size, false);
    }

    @Override
    public void write(int b) throws IOException {
        buffer.append((byte) b);
    }

    /**
     * Get the current number of bytes written to this stream.
     *
     * @return Number of bytes written
     */
    public int size() {
        return buffer.getLength();
    }

    /**
     * Get the underlying byte array directly without copying.
     *
     * @return Direct byte array reference
     */
    public byte[] getArrayDirect() {
        return buffer.getBytesDirect();
    }

    /**
     * Get a copy of the bytes written to this stream.
     *
     * @return Copy of the byte array
     */
    public byte[] getArray() {
        return buffer.getBytes();
    }


}