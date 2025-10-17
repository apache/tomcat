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

import org.junit.Assert;
import org.junit.Test;

import org.apache.tomcat.util.buf.CharChunk.CharOutputChannel;

/**
 * Test cases for {@link CharChunk}.
 */
public class TestCharChunkLargeHeap {

    @Test
    public void testAppend() throws Exception {
        CharChunk cc = new CharChunk();
        cc.setCharOutputChannel(new Sink());
        // Defaults to no limit

        char data[] = new char[32 * 1024 * 1024];

        for (int i = 0; i < 100; i++) {
            cc.append(data, 0, data.length);
        }

        Assert.assertEquals(AbstractChunk.ARRAY_MAX_SIZE, cc.getBuffer().length);
    }


    public static class Sink implements CharOutputChannel {

        @Override
        public void realWriteChars(char[] cbuf, int off, int len) throws IOException {
            // NO-OP
        }
    }

}
