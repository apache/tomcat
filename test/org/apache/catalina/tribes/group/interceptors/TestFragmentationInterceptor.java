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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Assert;
import org.junit.Test;

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

public class TestFragmentationInterceptor {

    private static class CapturingSender extends ChannelInterceptorBase {

        private final List<ChannelMessage> captured = new ArrayList<>();

        @Override
        public void sendMessage(Member[] destination, ChannelMessage msg, InterceptorPayload payload)
                throws ChannelException {
            int len = msg.getMessage().getLength();
            byte[] copy = Arrays.copyOfRange(msg.getMessage().getBytesDirect(), 0, len);
            ChannelData clone = new ChannelData(false);
            clone.setUniqueId(Arrays.copyOf(msg.getUniqueId(), msg.getUniqueId().length));
            clone.setAddress(msg.getAddress());
            clone.setOptions(msg.getOptions());
            clone.setMessage(new XByteBuffer(copy, false));
            captured.add(clone);
        }
    }

    private static class DeliveringCollector extends ChannelInterceptorBase {

        private volatile ChannelMessage delivered;

        @Override
        public void messageReceived(ChannelMessage msg) {
            delivered = msg;
        }
    }

    private static ChannelData createMessage(byte[] payload, Member source) {
        return createMessage(payload, source, UUIDGenerator.randomUUID(false));
    }

    private static ChannelData createMessage(byte[] payload, Member source, byte[] uniqueId) {
        ChannelData data = new ChannelData(false);
        data.setUniqueId(uniqueId);
        data.setAddress(source);
        data.setMessage(new XByteBuffer(payload, false));
        return data;
    }

    private static byte[] bytesOf(ChannelMessage msg) {
        int len = msg.getMessage().getLength();
        return Arrays.copyOfRange(msg.getMessage().getBytesDirect(), 0, len);
    }

    @Test
    public void testSetMaxSizeRejectsInvalidValues() {
        FragmentationInterceptor interceptor = new FragmentationInterceptor();

        try {
            interceptor.setMaxSize(0);
            Assert.fail("A max size of zero would make fragmentation divide by zero");
        } catch (IllegalArgumentException expected) {
            // Expected
        }

        try {
            interceptor.setMaxSize(-1);
            Assert.fail("A negative max size would make fragmentation allocate a negative sized array");
        } catch (IllegalArgumentException expected) {
            // Expected
        }

        interceptor.setMaxSize(1024);
        Assert.assertEquals(1024, interceptor.getMaxSize());
    }

    @Test
    public void testSetExpireRejectsInvalidValues() {
        FragmentationInterceptor interceptor = new FragmentationInterceptor();

        try {
            interceptor.setExpire(0);
            Assert.fail("A non-positive expire would discard fragment collections before reassembly can complete");
        } catch (IllegalArgumentException expected) {
            // Expected
        }

        try {
            interceptor.setExpire(-1);
            Assert.fail("A negative expire would discard fragment collections before reassembly can complete");
        } catch (IllegalArgumentException expected) {
            // Expected
        }

        interceptor.setExpire(60000);
        Assert.assertEquals(60000, interceptor.getExpire());
    }

    @Test
    public void testFragExceedingMaxFragmentsFails() throws Exception {
        Member destination = new MemberImpl("localhost", 4000, -1);
        FragmentationInterceptor sender = new FragmentationInterceptor();
        sender.setMaxSize(1);
        sender.setChannel(new GroupChannel());
        sender.setNext(new CapturingSender());

        byte[] payload = new byte[FragmentationInterceptor.MAX_FRAGMENTS + 1];
        try {
            sender.sendMessage(new Member[] { destination }, createMessage(payload, destination), null);
            Assert.fail("Fragmenting into more than MAX_FRAGMENTS fragments should fail");
        } catch (ChannelException expected) {
            // Expected
        }
    }

    @Test
    public void testAssembleIfCompleteIsOneShotUnderConcurrency() throws Exception {
        Member source = new MemberImpl("localhost", 4000, -1);
        byte[] uniqueId = UUIDGenerator.randomUUID(false);

        XByteBuffer init = new XByteBuffer(16, false);
        init.append("xx".getBytes(StandardCharsets.UTF_8), 0, 2);
        init.append(2);
        ChannelData initMessage = createMessage(new byte[0], source, uniqueId);
        initMessage.setMessage(init);
        FragmentationInterceptor.FragCollection coll =
                new FragmentationInterceptor.FragCollection(initMessage);

        XByteBuffer fragData0 = new XByteBuffer(16, false);
        fragData0.append("a".getBytes(StandardCharsets.UTF_8), 0, 1);
        fragData0.append(0);
        fragData0.append(2);
        ChannelData frag0 = createMessage(new byte[0], source, uniqueId);
        frag0.setMessage(fragData0);

        XByteBuffer fragData1 = new XByteBuffer(16, false);
        fragData1.append("b".getBytes(StandardCharsets.UTF_8), 0, 1);
        fragData1.append(1);
        fragData1.append(2);
        ChannelData frag1 = createMessage(new byte[0], source, uniqueId);
        frag1.setMessage(fragData1);

        CyclicBarrier barrier = new CyclicBarrier(2);
        AtomicInteger assembled = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (ChannelMessage frag : Arrays.asList(frag0, frag1)) {
                futures.add(executor.submit(() -> {
                    coll.addMessage(frag);
                    try {
                        // Both threads have added their fragment once the barrier trips,
                        // so both observe the collection as complete
                        barrier.await(10, TimeUnit.SECONDS);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                    if (coll.assembleIfComplete() != null) {
                        assembled.incrementAndGet();
                    }
                }));
            }
            for (Future<?> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        Assert.assertEquals("A concurrently completed message must be assembled exactly once",
                1, assembled.get());
    }

    @Test
    public void testFragmentRoundTrip() throws Exception {
        Member destination = new MemberImpl("localhost", 4000, -1);
        byte[] payload = "The quick brown fox jumps over the lazy dog.".getBytes(StandardCharsets.UTF_8);

        FragmentationInterceptor sender = new FragmentationInterceptor();
        sender.setMaxSize(8);
        CapturingSender capture = new CapturingSender();
        sender.setChannel(new GroupChannel());
        sender.setNext(capture);
        sender.sendMessage(new Member[] { destination }, createMessage(payload, destination), null);
        Assert.assertEquals(6, capture.captured.size());

        FragmentationInterceptor receiver = new FragmentationInterceptor();
        DeliveringCollector delivery = new DeliveringCollector();
        receiver.setChannel(new GroupChannel());
        receiver.setPrevious(delivery);
        for (ChannelMessage frag : capture.captured) {
            receiver.messageReceived(frag);
        }

        Assert.assertNotNull("Assembled message was not delivered", delivery.delivered);
        Assert.assertArrayEquals("Assembled message does not match the original payload",
                payload, bytesOf(delivery.delivered));
    }

    @Test
    public void testHugeFragmentCountIsDiscarded() throws Exception {
        Member destination = new MemberImpl("localhost", 4000, -1);
        FragmentationInterceptor receiver = new FragmentationInterceptor();
        DeliveringCollector delivery = new DeliveringCollector();
        receiver.setChannel(new GroupChannel());
        receiver.setPrevious(delivery);

        XByteBuffer buffer = new XByteBuffer(16, false);
        buffer.append("data".getBytes(StandardCharsets.UTF_8), 0, 4);
        buffer.append(Integer.MAX_VALUE);
        buffer.append(true);
        ChannelData frag = createMessage(new byte[0], destination);
        frag.setMessage(buffer);

        receiver.messageReceived(frag);

        Assert.assertNull("Message with a huge fragment count must not be delivered", delivery.delivered);
    }

    @Test
    public void testNegativeFragmentCountIsDiscarded() throws Exception {
        Member destination = new MemberImpl("localhost", 4000, -1);
        FragmentationInterceptor receiver = new FragmentationInterceptor();
        DeliveringCollector delivery = new DeliveringCollector();
        receiver.setChannel(new GroupChannel());
        receiver.setPrevious(delivery);

        XByteBuffer buffer = new XByteBuffer(16, false);
        buffer.append("data".getBytes(StandardCharsets.UTF_8), 0, 4);
        buffer.append(-5);
        buffer.append(true);
        ChannelData frag = createMessage(new byte[0], destination);
        frag.setMessage(buffer);

        receiver.messageReceived(frag);

        Assert.assertNull("Message with a negative fragment count must not be delivered", delivery.delivered);
    }

    @Test
    public void testOutOfRangeFragmentNumberIsDiscarded() throws Exception {
        Member destination = new MemberImpl("localhost", 4000, -1);
        FragmentationInterceptor receiver = new FragmentationInterceptor();
        DeliveringCollector delivery = new DeliveringCollector();
        receiver.setChannel(new GroupChannel());
        receiver.setPrevious(delivery);

        XByteBuffer buffer = new XByteBuffer(16, false);
        buffer.append("data".getBytes(StandardCharsets.UTF_8), 0, 4);
        buffer.append(5);
        buffer.append(1);
        buffer.append(true);
        ChannelData frag = createMessage(new byte[0], destination);
        frag.setMessage(buffer);

        receiver.messageReceived(frag);

        Assert.assertNull("Fragment with an out of range number must not be delivered", delivery.delivered);
    }

    @Test
    public void testTooShortFragmentIsDiscarded() throws Exception {
        Member destination = new MemberImpl("localhost", 4000, -1);
        FragmentationInterceptor receiver = new FragmentationInterceptor();
        DeliveringCollector delivery = new DeliveringCollector();
        receiver.setChannel(new GroupChannel());
        receiver.setPrevious(delivery);

        XByteBuffer buffer = new XByteBuffer(16, false);
        buffer.append("d".getBytes(StandardCharsets.UTF_8), 0, 1);
        buffer.append(true);
        ChannelData frag = createMessage(new byte[0], destination);
        frag.setMessage(buffer);

        receiver.messageReceived(frag);

        Assert.assertNull("Fragment that is too short must not be delivered", delivery.delivered);
    }

    @Test
    public void testShortNonFirstFragmentIsDiscarded() throws Exception {
        Member destination = new MemberImpl("localhost", 4000, -1);
        FragmentationInterceptor receiver = new FragmentationInterceptor();
        DeliveringCollector delivery = new DeliveringCollector();
        receiver.setChannel(new GroupChannel());
        receiver.setPrevious(delivery);

        byte[] uniqueId = UUIDGenerator.randomUUID(false);

        XByteBuffer first = new XByteBuffer(16, false);
        first.append("data".getBytes(StandardCharsets.UTF_8), 0, 4);
        first.append(0);
        first.append(2);
        first.append(true);
        ChannelData firstFrag = createMessage(new byte[0], destination, uniqueId);
        firstFrag.setMessage(first);
        receiver.messageReceived(firstFrag);

        XByteBuffer shortFrag = new XByteBuffer(16, false);
        shortFrag.append("x".getBytes(StandardCharsets.UTF_8), 0, 1);
        shortFrag.append(true);
        ChannelData shortData = createMessage(new byte[0], destination, uniqueId);
        shortData.setMessage(shortFrag);
        receiver.messageReceived(shortData);

        Assert.assertNull("Incomplete message must not be delivered", delivery.delivered);
    }

    @Test
    public void testReassemblyAfterMalformedFirstFragment() throws Exception {
        Member destination = new MemberImpl("localhost", 4000, -1);
        FragmentationInterceptor receiver = new FragmentationInterceptor();
        DeliveringCollector delivery = new DeliveringCollector();
        receiver.setChannel(new GroupChannel());
        receiver.setPrevious(delivery);

        byte[] uniqueId = UUIDGenerator.randomUUID(false);

        XByteBuffer malformed = new XByteBuffer(16, false);
        malformed.append("data".getBytes(StandardCharsets.UTF_8), 0, 4);
        malformed.append(Integer.MAX_VALUE);
        malformed.append(true);
        ChannelData malformedFrag = createMessage(new byte[0], destination, uniqueId);
        malformedFrag.setMessage(malformed);
        receiver.messageReceived(malformedFrag);

        XByteBuffer first = new XByteBuffer(16, false);
        first.append("ab".getBytes(StandardCharsets.UTF_8), 0, 2);
        first.append(0);
        first.append(2);
        first.append(true);
        ChannelData firstFrag = createMessage(new byte[0], destination, uniqueId);
        firstFrag.setMessage(first);
        receiver.messageReceived(firstFrag);

        XByteBuffer second = new XByteBuffer(16, false);
        second.append("cd".getBytes(StandardCharsets.UTF_8), 0, 2);
        second.append(1);
        second.append(2);
        second.append(true);
        ChannelData secondFrag = createMessage(new byte[0], destination, uniqueId);
        secondFrag.setMessage(second);
        receiver.messageReceived(secondFrag);

        Assert.assertNotNull("Message must still be reassembled after a malformed fragment",
                delivery.delivered);
        Assert.assertArrayEquals("Assembled message does not match the expected content",
                "abcd".getBytes(StandardCharsets.UTF_8), bytesOf(delivery.delivered));
    }
}
