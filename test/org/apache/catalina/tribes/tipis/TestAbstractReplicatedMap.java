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
package org.apache.catalina.tribes.tipis;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.tribes.ErrorHandler;
import org.apache.catalina.tribes.Member;
import org.apache.catalina.tribes.UniqueId;
import org.apache.catalina.tribes.group.GroupChannel;
import org.apache.catalina.tribes.io.XByteBuffer;
import org.apache.catalina.tribes.membership.MemberImpl;
import org.apache.catalina.tribes.tipis.AbstractReplicatedMap.MapMessage;

public class TestAbstractReplicatedMap {

    /*
     * The channel resolves any looked up member directly, so that a member can be added to the map
     * members without starting a channel.
     */
    private static final class TestChannel extends GroupChannel {

        private final AtomicInteger sendCalls = new AtomicInteger();

        @Override
        public Member getMember(Member mbr) {
            return mbr;
        }

        @Override
        public UniqueId send(Member[] destination, Serializable msg, int options, ErrorHandler handler) {
            sendCalls.incrementAndGet();
            return new UniqueId();
        }
    }

    /*
     * A value whose first serialization blocks until it is released, to simulate the long lasting
     * serialization of a full map content.
     */
    private static final class BlockingValue implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        private final transient CountDownLatch release;
        private final transient CountDownLatch serializationStarted = new CountDownLatch(1);
        private transient boolean serializedOnce = false;

        BlockingValue(CountDownLatch release) {
            this.release = release;
        }

        private void writeObject(ObjectOutputStream out) throws IOException {
            out.defaultWriteObject();
            if (!serializedOnce) {
                serializedOnce = true;
                serializationStarted.countDown();
                try {
                    release.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException(e);
                }
            }
        }
    }

    /*
     * Whether another thread could enter the given monitor while the current thread stays inside
     * the operation that is expected to run outside of that monitor.
     */
    private static boolean monitorAvailable(Object monitor, long timeoutMs) {
        CountDownLatch acquired = new CountDownLatch(1);
        Thread probe = new Thread(() -> {
            synchronized (monitor) {
                acquired.countDown();
            }
        });
        probe.setDaemon(true);
        probe.start();
        try {
            return acquired.await(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /*
     * The expiry handling of the ping aborts the entries of a timed out member, which sends to the
     * remaining members and may block until they acknowledge. It must not run while holding the
     * map members lock, since every session operation needs that lock.
     */
    @Test
    public void testPingExpiresOutsideMapMembersLock() throws Exception {
        Member member = new MemberImpl("localhost", 4011, -1);
        AtomicInteger disappeared = new AtomicInteger();
        AtomicBoolean lockAvailable = new AtomicBoolean();

        LazyReplicatedMap<String,String> map = new LazyReplicatedMap<String,String>(
                (key, value) -> {
                    // Not made primary here
                }, new TestChannel(), 1000, "TestAbstractReplicatedMap", null) {

            @Override
            public void memberDisappeared(Member disappearedMember) {
                disappeared.incrementAndGet();
                lockAvailable.set(monitorAvailable(mapMembers, 2000));
            }
        };

        map.memberAlive(member);
        Thread.sleep(10);
        // A timeout of zero expires every member that was not refreshed just now
        map.ping(0);

        Assert.assertEquals(1, disappeared.get());
        Assert.assertTrue("The ping must expire members outside the map members lock", lockAvailable.get());
    }

    /*
     * Adding a member selects backups for the primary entries that have none, which blocks until
     * the selected backups acknowledge. That must not run while holding the state lock, since the
     * state transfer of joining members needs that lock.
     */
    @Test
    public void testMapMemberAddedPublishesOutsideStateLock() throws Exception {
        AtomicBoolean probeOnPublish = new AtomicBoolean();
        AtomicBoolean lockAvailable = new AtomicBoolean();
        AtomicInteger published = new AtomicInteger();

        LazyReplicatedMap<String,String> map = new LazyReplicatedMap<String,String>(
                (key, value) -> {
                    // Not made primary here
                }, new TestChannel(), 1000, "TestAbstractReplicatedMap", null) {

            @Override
            protected Member[] publishEntryInfo(Object key, Object value) {
                if (probeOnPublish.get()) {
                    lockAvailable.set(monitorAvailable(stateMutex, 2000));
                }
                published.incrementAndGet();
                return new Member[0];
            }
        };

        // Stores a primary entry without a backup, without probing the publication it attempts
        map.put("key", "value");
        probeOnPublish.set(true);
        map.mapMemberAdded(new MemberImpl("localhost", 4012, -1));

        Assert.assertEquals(2, published.get());
        Assert.assertTrue("The member addition must publish backups outside the state lock", lockAvailable.get());
    }

    /*
     * Answering a state request serializes the whole map content, which must not happen while
     * holding the state lock, since it blocks the state transfer of other joining members for the
     * duration of that serialization.
     */
    @Test
    public void testStateRequestSerializesOutsideStateLock() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        BlockingValue value = new BlockingValue(release);

        LazyReplicatedMap<String,BlockingValue> map = new LazyReplicatedMap<String,BlockingValue>(
                (key, val) -> {
                    // Not made primary here
                }, new TestChannel(), 1000, "TestAbstractReplicatedMap", null);
        map.put("key", value);

        MapMessage stateRequest = new MapMessage("TestAbstractReplicatedMap".getBytes(StandardCharsets.UTF_8),
                MapMessage.MSG_STATE_COPY, false, null, null, null, null, null);
        AtomicReference<Serializable> reply = new AtomicReference<>();
        Member sender = new MemberImpl("localhost", 4013, -1);
        Thread replyThread = new Thread(() -> reply.set(map.replyRequest(stateRequest, sender)));
        replyThread.setDaemon(true);
        replyThread.start();

        // Once the value serialization has started, the map scan is complete
        try {
            Assert.assertTrue("The state request must serialize the map content",
                    value.serializationStarted.await(5, TimeUnit.SECONDS));
            boolean available = monitorAvailable(map.stateMutex, 2000);
            Assert.assertTrue("The state request must serialize outside the state lock", available);
        } finally {
            release.countDown();
        }
        replyThread.join(5000);
        Assert.assertTrue(reply.get() instanceof MapMessage);
        Object list = ((MapMessage) reply.get()).getValue();
        Assert.assertTrue(list instanceof ArrayList);
        Assert.assertEquals(1, ((ArrayList<?>) list).size());
        Assert.assertTrue(((ArrayList<?>) list).get(0) instanceof MapMessage);
        Assert.assertEquals(MapMessage.MSG_COPY, ((MapMessage) ((ArrayList<?>) list).get(0)).getMsgType());
    }

    /*
     * The put silently accepts keys that cannot be replicated, so the remove must not fail on such
     * a key either, but just skip the notification to the other members. The removal previously
     * threw an unchecked exception from the message construction, which also escaped a put that
     * replaced such an entry before the new value was stored.
     */
    @Test
    public void testRemoveNonSerializableKeySkipsNotification() throws Exception {
        TestChannel channel = new TestChannel();
        LazyReplicatedMap<Object,String> map = new LazyReplicatedMap<Object,String>(
                (key, value) -> {
                    // Not made primary here
                }, channel, 1000, "TestAbstractReplicatedMap", null);
        map.memberAlive(new MemberImpl("localhost", 4016, -1));

        Object key = new Object();
        map.put(key, "value");

        Assert.assertEquals("value", map.remove(key));
        Assert.assertNull(map.getInternal(key));
        Assert.assertEquals(0, channel.sendCalls.get());
    }

    /*
     * A put stores an entry with a null value, so the size must count it like the entry and key
     * views do. The size previously filtered such an entry out, breaking the map contract that the
     * size equals the size of the entry set, and the isEmpty inherited the inconsistency.
     */
    @Test
    public void testSizeCountsActiveEntriesWithNullValue() throws Exception {
        LazyReplicatedMap<String,String> map = new LazyReplicatedMap<String,String>(
                (key, value) -> {
                    // Not made primary here
                }, new TestChannel(), 1000, "TestAbstractReplicatedMap", null);
        map.put("key", "value");
        map.put("other", null);

        Assert.assertEquals(2, map.size());
        Assert.assertEquals(2, map.entrySet().size());
        Assert.assertEquals(2, map.keySet().size());
        Assert.assertEquals(2, map.values().size());
        Assert.assertFalse(map.isEmpty());
        Assert.assertTrue(map.values().contains(null));
    }

    private static final class SerialKey implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        private final String id;

        SerialKey(String id) {
            this.id = id;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof SerialKey && id.equals(((SerialKey) o).id);
        }

        @Override
        public int hashCode() {
            return id.hashCode();
        }
    }

    /*
     * A received backup request carries its key only in serialized form. The reply must deserialize
     * that key with the loaders of the map, like the other receive paths do, and must fail the
     * request without throwing when the key cannot be deserialized, so that the requester gets a
     * normal timeout instead of being answered by a failure of the responder.
     */
    @Test
    public void testBackupRequestDeserializesKeyOfReceivedMessage() throws Exception {
        LazyReplicatedMap<Object,String> map = new LazyReplicatedMap<Object,String>(
                (key, value) -> {
                    // Not made primary here
                }, new TestChannel(), 1000, "TestAbstractReplicatedMap", null);
        map.put(new SerialKey("key"), "value");
        Member sender = new MemberImpl("localhost", 4017, -1);

        // Round trip over the serialization to get a message with the key data but no live key
        byte[] wire = XByteBuffer.serialize(new MapMessage(
                "TestAbstractReplicatedMap".getBytes(StandardCharsets.UTF_8), MapMessage.MSG_RETRIEVE_BACKUP, false,
                new SerialKey("key"), null, null, null, null));

        MapMessage received = (MapMessage) XByteBuffer.deserialize(wire);
        Serializable reply = map.replyRequest(received, sender);
        Assert.assertTrue(reply instanceof MapMessage);
        Assert.assertEquals("value", ((MapMessage) reply).getValue());

        // Corrupt the class name inside the serialized key, so it cannot be deserialized
        byte[] corrupted = wire.clone();
        int at = indexOf(corrupted, "SerialKey".getBytes(StandardCharsets.US_ASCII));
        Assert.assertTrue(at > 0);
        corrupted[at] = 'X';
        MapMessage broken = (MapMessage) XByteBuffer.deserialize(corrupted);
        Assert.assertNull(map.replyRequest(broken, sender));
    }

    private static int indexOf(byte[] data, byte[] pattern) {
        outer:
        for (int i = 0; i <= data.length - pattern.length; i++) {
            for (int j = 0; j < pattern.length; j++) {
                if (data[i + j] != pattern[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }
}
