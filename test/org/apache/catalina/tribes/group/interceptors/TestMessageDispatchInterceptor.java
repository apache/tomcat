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
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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
import org.apache.catalina.tribes.util.UUIDGenerator;

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
            Assert.assertTrue("Dispatch thread did not start", collector.dispatchStarted.await(10, TimeUnit.SECONDS));
            // Simulate GroupChannel.send() returning the buffer to the pool for reuse
            buffer.clear();
            buffer.append(reused, 0, reused.length);
            collector.bufferReused.countDown();
            Assert.assertTrue("Dispatch thread did not collect the message",
                    collector.collected.await(10, TimeUnit.SECONDS));

            Assert.assertNotSame("Queued message must not share the pooled buffer", buffer, collector.capturedBuffer);
            Assert.assertArrayEquals("Queued message content was corrupted by buffer reuse", original,
                    collector.capturedData);
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

    @Test
    public void testQueueSizeReturnsToZeroWhenDownstreamMutatesPayload() throws Exception {
        GroupChannel channel = new GroupChannel();
        MessageDispatchInterceptor interceptor = new MessageDispatchInterceptor();
        MutatingCollector collector = new MutatingCollector();
        interceptor.setChannel(channel);
        interceptor.setNext(collector);
        interceptor.startQueue();
        try {
            byte[] payloadBytes = "PAYLOAD".getBytes(StandardCharsets.UTF_8);
            Member destination = new MemberImpl("localhost", 4000, -1);
            Member[] dest = new Member[] { destination };
            int count = 20;
            for (int i = 0; i < count; i++) {
                ChannelData data = new ChannelData(false);
                data.setUniqueId(UUIDGenerator.randomUUID(false));
                data.setAddress(destination);
                data.setOptions(Channel.SEND_OPTIONS_ASYNCHRONOUS);
                XByteBuffer buffer = new XByteBuffer(64, false);
                buffer.append(payloadBytes, 0, payloadBytes.length);
                data.setMessage(buffer);
                interceptor.sendMessage(dest, data, null);
            }

            long deadline = System.currentTimeMillis() + 10000;
            long size = interceptor.getCurrentSize();
            while (size != 0 && System.currentTimeMillis() < deadline) {
                Thread.sleep(50);
                size = interceptor.getCurrentSize();
            }
            Assert.assertEquals(
                    "Queue size accounting drifted when a downstream interceptor changed the message length", 0, size);
        } finally {
            interceptor.stopQueue();
        }
    }

    @Test
    public void testPublicAddToQueueAccountsForQueueSize() throws Exception {
        GroupChannel channel = new GroupChannel();
        MessageDispatchInterceptor interceptor = new MessageDispatchInterceptor();
        MutatingCollector collector = new MutatingCollector();
        interceptor.setChannel(channel);
        interceptor.setNext(collector);
        interceptor.startQueue();
        try {
            Member destination = new MemberImpl("localhost", 4000, -1);
            ChannelData data = new ChannelData(false);
            data.setUniqueId(UUIDGenerator.randomUUID(false));
            data.setAddress(destination);
            data.setOptions(Channel.SEND_OPTIONS_ASYNCHRONOUS);
            XByteBuffer buffer = new XByteBuffer(64, false);
            byte[] payloadBytes = "PAYLOAD".getBytes(StandardCharsets.UTF_8);
            buffer.append(payloadBytes, 0, payloadBytes.length);
            data.setMessage(buffer);

            Assert.assertTrue(interceptor.addToQueue(data, new Member[] { destination }, null));

            long deadline = System.currentTimeMillis() + 10000;
            long size = interceptor.getCurrentSize();
            while (size != 0 && System.currentTimeMillis() < deadline) {
                Thread.sleep(50);
                size = interceptor.getCurrentSize();
            }
            Assert.assertEquals("Queue size must return to zero, not drift negative", 0, size);
        } finally {
            interceptor.stopQueue();
        }
    }

    @Test
    public void testPublicAddToQueueRollsBackWhenRejected() throws Exception {
        GroupChannel channel = new GroupChannel();
        MessageDispatchInterceptor interceptor = new MessageDispatchInterceptor();
        interceptor.setChannel(channel);
        interceptor.setNext(new MutatingCollector());
        interceptor.startQueue();
        // A stopped queue rejects new tasks
        interceptor.stopQueue();

        Member destination = new MemberImpl("localhost", 4000, -1);
        ChannelData data = new ChannelData(false);
        XByteBuffer buffer = new XByteBuffer(64, false);
        byte[] payloadBytes = "PAYLOAD".getBytes(StandardCharsets.UTF_8);
        buffer.append(payloadBytes, 0, payloadBytes.length);
        data.setMessage(buffer);

        Assert.assertFalse(interceptor.addToQueue(data, new Member[] { destination }, null));
        Assert.assertEquals(0, interceptor.getCurrentSize());
    }

    @Test
    public void testRejectedMessageIsNotCloned() throws Exception {
        byte[] payload = "PAYLOAD".getBytes(StandardCharsets.UTF_8);
        MessageDispatchInterceptor interceptor = new MessageDispatchInterceptor();
        interceptor.setAlwaysSend(false);
        interceptor.setMaxQueueSize(payload.length - 1);
        interceptor.setChannel(new GroupChannel());
        interceptor.setNext(new MutatingCollector());
        interceptor.startQueue();
        try {
            Member destination = new MemberImpl("localhost", 4000, -1);
            AtomicInteger clones = new AtomicInteger();
            ChannelData data = new ChannelData(false) {
                @Override
                public Object deepclone() {
                    clones.incrementAndGet();
                    return super.deepclone();
                }
            };
            data.setUniqueId(UUIDGenerator.randomUUID(false));
            data.setAddress(destination);
            data.setOptions(Channel.SEND_OPTIONS_ASYNCHRONOUS);
            data.setMessage(new XByteBuffer(payload, false));

            try {
                interceptor.sendMessage(new Member[] { destination }, data, null);
                Assert.fail("The message does not fit in the queue");
            } catch (ChannelException expected) {
                // Expected
            }
            Assert.assertEquals("A message that cannot fit must be rejected before it is cloned", 0, clones.get());
            Assert.assertEquals(0, interceptor.getCurrentSize());
        } finally {
            interceptor.stopQueue();
        }
    }

    @Test
    public void testConcurrentSendsCannotExceedMaxQueueSize() throws Exception {
        byte[] payload = "PAYLOAD".getBytes(StandardCharsets.UTF_8);
        CyclicBarrier clonesReady = new CyclicBarrier(2);
        MessageDispatchInterceptor interceptor = new MessageDispatchInterceptor();
        interceptor.setAlwaysSend(false);
        interceptor.setMaxQueueSize(payload.length);
        GatedCollector collector = new GatedCollector();
        interceptor.setChannel(new GroupChannel());
        interceptor.setNext(collector);
        interceptor.startQueue();
        ExecutorService senders = Executors.newFixedThreadPool(2);
        try {
            Member destination = new MemberImpl("localhost", 4000, -1);
            Member[] destinations = new Member[] { destination };
            Future<Boolean> first = senders.submit(() -> Boolean
                    .valueOf(send(interceptor, destinations, createMessage(payload, destination, clonesReady))));
            Future<Boolean> second = senders.submit(() -> Boolean
                    .valueOf(send(interceptor, destinations, createMessage(payload, destination, clonesReady))));

            Boolean firstSent = first.get(10, TimeUnit.SECONDS);
            Boolean secondSent = second.get(10, TimeUnit.SECONDS);
            Assert.assertNotEquals("Exactly one message must fit in the queue", firstSent, secondSent);
            Assert.assertEquals(payload.length, interceptor.getCurrentSize());
        } finally {
            collector.bufferReused.countDown();
            senders.shutdownNow();
            interceptor.stopQueue();
        }
    }

    private static boolean send(MessageDispatchInterceptor interceptor, Member[] destinations, ChannelData message) {
        try {
            interceptor.sendMessage(destinations, message, null);
            return true;
        } catch (ChannelException e) {
            return false;
        }
    }

    private static ChannelData createMessage(byte[] payload, Member destination, CyclicBarrier clonesReady) {
        ChannelData data = new ChannelData(false) {

            @Override
            public Object deepclone() {
                try {
                    clonesReady.await(10, TimeUnit.SECONDS);
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
                return super.deepclone();
            }
        };
        data.setUniqueId(UUIDGenerator.randomUUID(false));
        data.setAddress(destination);
        data.setOptions(Channel.SEND_OPTIONS_ASYNCHRONOUS);
        data.setMessage(new XByteBuffer(payload, false));
        return data;
    }

    /*
     * Simulates interceptors such as EncryptInterceptor or FragmentationInterceptor that replace or extend the message
     * payload while the message is being sent downstream.
     */
    private static class MutatingCollector extends ChannelInterceptorBase {

        @Override
        public void sendMessage(Member[] destination, ChannelMessage msg, InterceptorPayload payload)
                throws ChannelException {
            msg.getMessage().append(true);
        }
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
