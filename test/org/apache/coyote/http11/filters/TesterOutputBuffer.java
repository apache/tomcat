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
package org.apache.coyote.http11.filters;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

import org.apache.coyote.OutputBuffer;
import org.apache.coyote.Response;
import org.apache.coyote.http11.Http11OutputBuffer;
import org.apache.tomcat.util.net.SocketWrapperBase;

/**
 * Output buffer for use in unit tests. This is a minimal implementation.
 */
public class TesterOutputBuffer extends Http11OutputBuffer {

    /**
     * Underlying output stream.
     */
    private ByteArrayOutputStream outputStream = new ByteArrayOutputStream();


    public TesterOutputBuffer(Response response, int headerBufferSize) {
        super(response, headerBufferSize);
        outputStreamOutputBuffer = new OutputStreamOutputBuffer();
    }


    // --------------------------------------------------------- Public Methods

    @Override
    public void init(SocketWrapperBase<?> socketWrapper) {
        // NO-OP: Unused
    }


    /**
     * Recycle the output buffer. This should be called when closing the
     * connection.
     */
    @Override
    public void recycle() {
        super.recycle();
        outputStream = null;
    }


    // ------------------------------------------------ HTTP/1.1 Output Methods

    /**
     * Send an acknowledgement.
     */
    @Override
    public void sendAck() {
        // NO-OP: Unused
    }


    @Override
    protected void commit() {
        // NO-OP: Unused
    }


    @Override
    protected boolean flushBuffer(boolean block) throws IOException {
        // Blocking IO so ignore block parameter as this will always use
        // blocking IO.
        // Always blocks so never any data left over.
        return false;
    }


    /*
     * Expose data written for use by unit tests.
     */
    byte[] toByteArray() {
        return outputStream.toByteArray();
    }


    /**
     * This class is an output buffer which will write data to an output
     * stream.
     */
    protected class OutputStreamOutputBuffer implements OutputBuffer {

        @Override
        public int doWrite(ByteBuffer chunk) throws IOException {
            int length = chunk.remaining();
            outputStream.write(chunk.array(), chunk.arrayOffset() + chunk.position(), length);
            byteCount += length;
            return length;
        }

        @Override
        public long getBytesWritten() {
            return byteCount;
        }
    }
}
