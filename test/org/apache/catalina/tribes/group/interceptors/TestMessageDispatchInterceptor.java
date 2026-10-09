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
package org.apache.catalina.tribes.group.interceptors;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.tribes.Channel;
import org.apache.catalina.tribes.ChannelException;
import org.apache.catalina.tribes.ChannelMessage;
import org.apache.catalina.tribes.Member;
import org.apache.catalina.tribes.group.ChannelInterceptorBase;
import org.apache.catalina.tribes.group.GroupChannel;
import org.apache.catalina.tribes.group.InterceptorPayload;
import org.apache.catalina.tribes.io.ChannelData;
import org.apache.catalina.tribes.io.XByteBuffer;
import org.apache.catalina.tribes.membership.MemberImpl;

public class TestMessageDispatchInterceptor {

    /*
     * Captures the message on the dispatch thread, but only after the caller thread has simulated the reuse of the
     * original (pooled) buffer that GroupChannel.send() returns to the BufferPool.
     */
    private static class GatedCollector extends ChannelInterceptorBase {

        private final CountDownLatch dispatchStarted = new CountDownLatch(1);
        private final CountDownLatch bufferReused = new CountDownLatch(1);
        private final CountDownLatch collected = new CountDownLatch(1);

        private volatile XByteBuffer capturedBuffer;
        private volatile byte[] capturedData;

        @Override
        public void sendMessage(Member[] destination, ChannelMessage msg, InterceptorPayload payload)
                throws ChannelException {
            dispatchStarted.countDown();
            try {
                if (!bufferReused.await(10, TimeUnit.SECONDS)) {
                    throw new ChannelException("Timed out waiting for buffer reuse");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ChannelException(e);
            }
            capturedBuffer = msg.getMessage();
            int len = capturedBuffer.getLength();
            capturedData = Arrays.copyOfRange(capturedBuffer.getBytesDirect(), 0, len);
            collected.countDown();
        }
    }

    @Test
    public void testAsyncSendWithoutDeepCloneDetachesPooledBuffer() throws Exception {
        byte[] original = "ORIGINAL".getBytes(StandardCharsets.UTF_8);
        byte[] reused = "OVERWRITTEN-BY-POOL".getBytes(StandardCharsets.UTF_8);

        GroupChannel channel = new GroupChannel();
        MessageDispatchInterceptor interceptor = new MessageDispatchInterceptor();
        interceptor.setUseDeepClone(false);
        GatedCollector collector = new GatedCollector();
        interceptor.setChannel(channel);
        interceptor.setNext(collector);
        interceptor.startQueue();
        try {
            ChannelData data = new ChannelData(false);
            data.setOptions(Channel.SEND_OPTIONS_ASYNCHRONOUS);
            XByteBuffer buffer = new XByteBuffer(original.length + 128, false);
            buffer.append(original, 0, original.length);
            data.setMessage(buffer);
            Member[] destination = new Member[] { new MemberImpl("localhost", 4000, -1) };

            interceptor.sendMessage(destination, data, null);
            Assert.assertTrue("Dispatch thread did not start",
                    collector.dispatchStarted.await(10, TimeUnit.SECONDS));
            // Simulate GroupChannel.send() returning the buffer to the pool for reuse
            buffer.clear();
            buffer.append(reused, 0, reused.length);
            collector.bufferReused.countDown();
            Assert.assertTrue("Dispatch thread did not collect the message",
                    collector.collected.await(10, TimeUnit.SECONDS));

            Assert.assertNotSame("Queued message must not share the pooled buffer",
                    buffer, collector.capturedBuffer);
            Assert.assertArrayEquals("Queued message content was corrupted by buffer reuse",
                    original, collector.capturedData);
        } finally {
            interceptor.stopQueue();
        }
    }

    @Test
    public void testSyncSendPassesMessageThrough() throws Exception {
        GroupChannel channel = new GroupChannel();
        MessageDispatchInterceptor interceptor = new MessageDispatchInterceptor();
        RecordingCollector collector = new RecordingCollector();
        interceptor.setChannel(channel);
        interceptor.setNext(collector);

        ChannelData data = new ChannelData(false);
        XByteBuffer buffer = new XByteBuffer(64, false);
        byte[] payload = "SYNC".getBytes(StandardCharsets.UTF_8);
        buffer.append(payload, 0, payload.length);
        data.setMessage(buffer);
        Member[] destination = new Member[] { new MemberImpl("localhost", 4000, -1) };

        interceptor.sendMessage(destination, data, null);

        Assert.assertSame("Synchronous sends must pass the original message through", data, collector.message);
    }

    private static class RecordingCollector extends ChannelInterceptorBase {

        private volatile ChannelMessage message;

        @Override
        public void sendMessage(Member[] destination, ChannelMessage msg, InterceptorPayload payload)
                throws ChannelException {
            message = msg;
        }
    }
}
