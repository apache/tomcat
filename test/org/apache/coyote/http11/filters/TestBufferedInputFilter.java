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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import org.junit.Assert;
import org.junit.Test;

import org.apache.coyote.InputBuffer;
import org.apache.coyote.Request;
import org.apache.tomcat.util.net.ApplicationBufferHandler;

public class TestBufferedInputFilter {

    /*
     * The filter must report the buffered body length as available until the
     * body has been delivered to the caller and must then report no data as
     * available rather than continuing to report the delivered body length.
     */
    @Test
    public void testAvailableAfterBodyDelivered() throws IOException {
        byte[] body = "0123456789".getBytes(StandardCharsets.ISO_8859_1);

        BufferedInputFilter filter = new BufferedInputFilter(-1);
        filter.setLimit(1024);
        filter.setBuffer(new FixedSizeBodySource(body, 4, 0));

        // Buffer the body, as Http11InputBuffer.addActiveFilter() does
        filter.setRequest(new Request());

        Assert.assertFalse(filter.isFinished());
        Assert.assertEquals(body.length, filter.available());

        // Deliver the body to the caller
        DeliveryTarget target = new DeliveryTarget();
        Assert.assertEquals(body.length, filter.doRead(target));
        Assert.assertArrayEquals(body, target.getBytes());

        // The body has been delivered. Further reads and available() must
        // report the end of the body. available() is expected to keep
        // reporting zero when called repeatedly.
        Assert.assertTrue(filter.isFinished());
        Assert.assertEquals(0, filter.available());
        Assert.assertEquals(0, filter.available());
        Assert.assertEquals(-1, filter.doRead(target));
    }


    /*
     * A filter with an empty body must report no data as available rather
     * than falling through to the downstream buffer whose data does not
     * belong to the body of this request.
     */
    @Test
    public void testAvailableWithEmptyBody() {
        BufferedInputFilter filter = new BufferedInputFilter(-1);
        filter.setLimit(1024);
        // The body is empty but the downstream buffer has data, for example
        // from a pipelined next request
        filter.setBuffer(new FixedSizeBodySource(new byte[0], 4, 10));

        // Buffer the body, as Http11InputBuffer.addActiveFilter() does
        filter.setRequest(new Request());

        Assert.assertTrue(filter.isFinished());
        Assert.assertEquals(0, filter.available());
    }


    /*
     * Feeds the given body to the filter in fixed size chunks followed by
     * EOF. The value the available() method reports after the body has been
     * drained can be configured.
     */
    private static class FixedSizeBodySource implements InputBuffer {

        private final ByteBuffer source;
        private final int chunkSize;
        private final int availableAfterDrain;

        FixedSizeBodySource(byte[] body, int chunkSize, int availableAfterDrain) {
            source = ByteBuffer.wrap(body);
            this.chunkSize = chunkSize;
            this.availableAfterDrain = availableAfterDrain;
        }

        @Override
        public int doRead(ApplicationBufferHandler handler) throws IOException {
            if (!source.hasRemaining()) {
                return -1;
            }
            int len = Math.min(chunkSize, source.remaining());
            byte[] bytes = new byte[len];
            source.get(bytes);
            handler.setByteBuffer(ByteBuffer.wrap(bytes));
            return len;
        }

        @Override
        public int available() {
            return source.remaining() > 0 ? source.remaining() : availableAfterDrain;
        }
    }


    private static class DeliveryTarget implements ApplicationBufferHandler {

        private ByteBuffer buffer;

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
            // The buffer is never expanded
        }

        byte[] getBytes() {
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            return bytes;
        }
    }
}
