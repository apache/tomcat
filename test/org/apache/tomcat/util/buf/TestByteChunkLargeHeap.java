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
package org.apache.tomcat.util.buf;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.junit.Assert;
import org.junit.Test;

import org.apache.tomcat.util.buf.ByteChunk.ByteOutputChannel;

/**
 * Test cases for {@link ByteChunk} that require a large heap.
 */
public class TestByteChunkLargeHeap {

    @Test
    public void testAppend() throws Exception {
        ByteChunk bc = new ByteChunk();
        bc.setByteOutputChannel(new Sink());
        // Defaults to no limit

        byte data[] = new byte[32 * 1024 * 1024];

        for (int i = 0; i < 100; i++) {
            bc.append(data, 0, data.length);
        }

        Assert.assertEquals(AbstractChunk.ARRAY_MAX_SIZE, bc.getBuffer().length);
    }


    public static class Sink implements ByteOutputChannel {

        @Override
        public void realWriteBytes(byte[] cbuf, int off, int len) throws IOException {
            // NO-OP
        }

        @Override
        public void realWriteBytes(ByteBuffer from) throws IOException {
            // NO-OP
        }
    }
}
