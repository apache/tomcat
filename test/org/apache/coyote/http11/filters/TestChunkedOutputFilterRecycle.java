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

import org.junit.Assert;
import org.junit.Test;

import org.apache.coyote.Response;
import org.apache.coyote.http11.HttpOutputBuffer;

public class TestChunkedOutputFilterRecycle {

    private static final byte[] END_CHUNK_BYTES = { (byte) '0', (byte) '\r', (byte) '\n', (byte) '\r', (byte) '\n' };


    @Test
    public void testRecycleAfterIncompleteEndChunkWrite() throws IOException {
        ChunkedOutputFilter filter = new ChunkedOutputFilter();
        TesterHttpOutputBuffer outputBuffer = new TesterHttpOutputBuffer();
        filter.setBuffer(outputBuffer);
        filter.setResponse(new Response());

        outputBuffer.failOnNextWrite();
        Assert.assertThrows(IOException.class, filter::end);

        filter.recycle();
        outputBuffer.reset();
        filter.setResponse(new Response());
        filter.end();

        Assert.assertArrayEquals(END_CHUNK_BYTES, outputBuffer.toByteArray());
    }


    @Test
    public void testRecycleResetsChunkBuffers() {
        ChunkedOutputFilter filter = new ChunkedOutputFilter();
        filter.lastChunk.position(filter.lastChunk.limit());
        filter.crlfChunk.position(filter.crlfChunk.limit());
        filter.endChunk.position(filter.endChunk.limit());

        filter.recycle();

        Assert.assertEquals(0, filter.lastChunk.position());
        Assert.assertEquals(filter.lastChunk.capacity(), filter.lastChunk.limit());
        Assert.assertEquals(0, filter.crlfChunk.position());
        Assert.assertEquals(filter.crlfChunk.capacity(), filter.crlfChunk.limit());
        Assert.assertEquals(0, filter.endChunk.position());
        Assert.assertEquals(filter.endChunk.capacity(), filter.endChunk.limit());
    }


    private static class TesterHttpOutputBuffer implements HttpOutputBuffer {

        private final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        private boolean failOnNextWrite;


        @Override
        public int doWrite(ByteBuffer chunk) throws IOException {
            int length = chunk.remaining();
            if (failOnNextWrite) {
                length = Math.min(2, length);
            }

            byte[] bytes = new byte[length];
            chunk.get(bytes);
            outputStream.write(bytes, 0, length);

            if (failOnNextWrite) {
                failOnNextWrite = false;
                throw new IOException();
            }

            return length;
        }


        @Override
        public long getBytesWritten() {
            return outputStream.size();
        }


        @Override
        public void end() {
            // NO-OP
        }


        @Override
        public void flush() {
            // NO-OP
        }


        void failOnNextWrite() {
            failOnNextWrite = true;
        }


        void reset() {
            outputStream.reset();
        }


        byte[] toByteArray() {
            return outputStream.toByteArray();
        }
    }
}
