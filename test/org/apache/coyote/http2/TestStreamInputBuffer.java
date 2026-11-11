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
package org.apache.coyote.http2;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Assert;
import org.junit.Test;

public class TestStreamInputBuffer {

    /*
     * The buffer check and the end-of-input check in isRequestBodyFullyRead() must not combine an old "buffer empty"
     * observation with a newer "input finished" observation. Otherwise a body that still has unread data can be
     * reported as fully read.
     */
    @Test
    public void testRequestBodyFullyReadRace() throws Exception {
        Http2UpgradeHandler handler = new Http2UpgradeHandler(new Http2Protocol(), null, null);

        for (int i = 0; i < 20; i++) {
            Stream stream = new Stream(Integer.valueOf(3), handler);
            stream.state.receivedStartOfHeaders();
            stream.getCoyoteRequest().setContentLength(1);
            ByteBuffer data = stream.getInputByteBuffer(true);
            Assert.assertFalse(stream.getInputBuffer().isRequestBodyFullyRead());

            AtomicReference<Boolean> result = new AtomicReference<>();
            Thread consumer =
                    new Thread(() -> result.set(Boolean.valueOf(stream.getInputBuffer().isRequestBodyFullyRead())));

            // Hold the state monitor so that the consumer blocks part way through the check
            synchronized (stream.state) {
                consumer.start();
                long timeout = System.nanoTime() + 5_000_000_000L;
                while (consumer.getState() != Thread.State.BLOCKED) {
                    Assert.assertTrue("Consumer did not block", System.nanoTime() < timeout);
                    Thread.sleep(1);
                }
                // Producer: one byte of body followed by end of stream
                synchronized (data) {
                    stream.receivedData(1);
                    data.put((byte) 'A');
                    stream.receivedEndOfStream();
                }
            }
            consumer.join(5000);
            Assert.assertFalse(consumer.isAlive());

            Assert.assertEquals(1, data.position());
            Assert.assertEquals("Body reported as fully read with unread data", Boolean.FALSE, result.get());
            Assert.assertFalse(stream.getInputBuffer().isRequestBodyFullyRead());
        }
    }

    /*
     * If data arrives between the initial available() check and the completion check in isReadyForRead(), the method
     * should report that data is available rather than registering read interest (which would trigger an unnecessary
     * dispatch).
     */
    @Test
    public void testIsReadyForReadDataArrivesDuringCheck() throws Exception {
        Http2UpgradeHandler handler = new Http2UpgradeHandler(new Http2Protocol(), null, null);

        Stream stream = new Stream(Integer.valueOf(3), handler);
        stream.state.receivedStartOfHeaders();
        stream.getCoyoteRequest().setContentLength(1);
        ByteBuffer data = stream.getInputByteBuffer(true);

        AtomicReference<Boolean> result = new AtomicReference<>();
        Thread consumer = new Thread(() -> result.set(Boolean.valueOf(stream.getInputBuffer().isReadyForRead())));

        synchronized (stream.state) {
            consumer.start();
            long timeout = System.nanoTime() + 5_000_000_000L;
            while (consumer.getState() != Thread.State.BLOCKED) {
                Assert.assertTrue("Consumer did not block", System.nanoTime() < timeout);
                Thread.sleep(1);
            }
            synchronized (data) {
                stream.receivedData(1);
                data.put((byte) 'A');
                stream.receivedEndOfStream();
            }
        }
        consumer.join(5000);
        Assert.assertFalse(consumer.isAlive());

        Assert.assertEquals("Data arrived during the check so the stream should be ready for read", Boolean.TRUE,
                result.get());
    }
}
