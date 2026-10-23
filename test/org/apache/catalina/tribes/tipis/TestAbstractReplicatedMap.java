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
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map.Entry;
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
import org.apache.tomcat.unittest.TesterSerializableKey;

public class TestAbstractReplicatedMap {

    /*
     * The channel resolves any looked up member directly, so that a member can be added to the map members without
     * starting a channel.
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
     * A value whose first serialization blocks until it is released, to simulate the long lasting serialization of a
     * full map content.
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
     * Whether another thread could enter the given monitor while the current thread stays inside the operation that is
     * expected to run outside of that monitor.
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
     * The expiry handling of the ping aborts the entries of a timed out member, which sends to the remaining members
     * and may block until they acknowledge. It must not run while holding the map members lock, since every session
     * operation needs that lock.
     */
    @Test
    public void testPingExpiresOutsideMapMembersLock() throws Exception {
        Member member = new MemberImpl("localhost", 4011, -1);
        AtomicInteger disappeared = new AtomicInteger();
        AtomicBoolean lockAvailable = new AtomicBoolean();

        LazyReplicatedMap<String,String> map = new LazyReplicatedMap<>((key, value) -> {
            // Not made primary here
        }, new TestChannel(), 1000, "TestAbstractReplicatedMap", null) {

            @Override
            public void memberDisappeared(Member disappearedMember) {
                disappeared.incrementAndGet();
                lockAvailable.set(monitorAvailable(mapMembers, 2000));
                super.memberDisappeared(disappearedMember);
            }
        };

        map.memberAlive(member);
        Thread.sleep(10);
        // A timeout of zero expires every member that was not refreshed just now
        map.ping(0);

        Assert.assertEquals(1, disappeared.get());
        Assert.assertTrue("The ping must expire members outside the map members lock", lockAvailable.get());
    }

    @Test
    public void testPingDoesNotRemoveRefreshedMember() throws Exception {
        Member member = new MemberImpl("localhost", 4014, -1);

        LazyReplicatedMap<String,String> map = new LazyReplicatedMap<>((key, value) -> {
            // Not made primary here
        }, new TestChannel(), 1000, "TestAbstractReplicatedMap", null) {

            @Override
            public void memberDisappeared(Member disappearedMember) {
                memberAlive(disappearedMember);
                super.memberDisappeared(disappearedMember);
            }
        };

        map.memberAlive(member);
        Thread.sleep(10);
        map.ping(0);

        Assert.assertTrue(map.inSet(member, map.getMapMembers()));
    }

    /*
     * Adding a member selects backups for the primary entries that have none, which blocks until the selected backups
     * acknowledge. That must not run while holding the state lock, since the state transfer of joining members needs
     * that lock.
     */
    @Test
    public void testMapMemberAddedPublishesOutsideStateLock() throws Exception {
        AtomicBoolean probeOnPublish = new AtomicBoolean();
        AtomicBoolean lockAvailable = new AtomicBoolean();
        AtomicInteger published = new AtomicInteger();

        LazyReplicatedMap<String,String> map = new LazyReplicatedMap<>((key, value) -> {
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

    @Test
    public void testConcurrentMemberAddsPublishBackupOnce() throws Exception {
        CountDownLatch firstPublishStarted = new CountDownLatch(1);
        CountDownLatch releaseFirstPublish = new CountDownLatch(1);
        AtomicBoolean probeOnPublish = new AtomicBoolean();
        AtomicInteger published = new AtomicInteger();
        Member backup = new MemberImpl("localhost", 4015, -1);

        LazyReplicatedMap<String,String> map = new LazyReplicatedMap<>((key, value) -> {
            // Not made primary here
        }, new TestChannel(), 1000, "TestAbstractReplicatedMap", null) {

            @Override
            protected Member[] publishEntryInfo(Object key, Object value) {
                if (!probeOnPublish.get()) {
                    return new Member[0];
                }
                if (published.incrementAndGet() == 1) {
                    firstPublishStarted.countDown();
                    try {
                        releaseFirstPublish.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        Assert.fail(e.getMessage());
                    }
                }
                return new Member[] { backup };
            }
        };

        map.put("key", "value");
        probeOnPublish.set(true);
        Member firstMember = new MemberImpl("localhost", 4016, -1);
        Member secondMember = new MemberImpl("localhost", 4017, -1);
        Thread first = new Thread(() -> map.mapMemberAdded(firstMember));
        Thread second = new Thread(() -> map.mapMemberAdded(secondMember));
        first.setDaemon(true);
        second.setDaemon(true);
        first.start();
        Assert.assertTrue(firstPublishStarted.await(5, TimeUnit.SECONDS));
        second.start();
        try {
            second.join(1000);
        } finally {
            releaseFirstPublish.countDown();
        }
        first.join(5000);
        second.join(5000);

        Assert.assertFalse(first.isAlive());
        Assert.assertFalse(second.isAlive());
        Assert.assertEquals(1, published.get());
    }

    /*
     * Answering a state request serializes the whole map content, which must not happen while holding the state lock,
     * since it blocks the state transfer of other joining members for the duration of that serialization.
     */
    @Test
    public void testStateRequestSerializesOutsideStateLock() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        BlockingValue value = new BlockingValue(release);

        LazyReplicatedMap<String,BlockingValue> map = new LazyReplicatedMap<>((key, val) -> {
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

    @Test
    public void testStateRequestUsesStableEntrySnapshot() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        BlockingValue blockingValue = new BlockingValue(release);
        LazyReplicatedMap<String,Serializable> map = new LazyReplicatedMap<>((key, value) -> {
            // Not made primary here
        }, new TestChannel(), 1000, "TestAbstractReplicatedMap", null);
        map.put("first", "value");
        map.put("second", "old");

        Iterator<Entry<String,AbstractReplicatedMap.MapEntry<String,Serializable>>> entries =
                map.innerMap.entrySet().iterator();
        Entry<String,AbstractReplicatedMap.MapEntry<String,Serializable>> first = entries.next();
        Entry<String,AbstractReplicatedMap.MapEntry<String,Serializable>> second = entries.next();
        first.getValue().setValue(blockingValue);
        Serializable originalSecondValue = second.getValue().getValue();

        MapMessage stateRequest = new MapMessage("TestAbstractReplicatedMap".getBytes(StandardCharsets.UTF_8),
                MapMessage.MSG_STATE_COPY, false, null, null, null, null, null);
        AtomicReference<Serializable> reply = new AtomicReference<>();
        Member sender = new MemberImpl("localhost", 4018, -1);
        Thread replyThread = new Thread(() -> reply.set(map.replyRequest(stateRequest, sender)));
        replyThread.setDaemon(true);
        replyThread.start();

        try {
            Assert.assertTrue(blockingValue.serializationStarted.await(5, TimeUnit.SECONDS));
            synchronized (map.stateMutex) {
                second.getValue().setValue("new");
            }
        } finally {
            release.countDown();
        }
        replyThread.join(5000);

        Assert.assertFalse(replyThread.isAlive());
        MapMessage replyMessage = (MapMessage) reply.get();
        @SuppressWarnings("unchecked")
        ArrayList<MapMessage> state = (ArrayList<MapMessage>) replyMessage.getValue();
        MapMessage secondMessage =
                state.stream().filter(message -> second.getKey().equals(message.getKey())).findFirst().orElseThrow();
        Assert.assertEquals(originalSecondValue, secondMessage.getValue());
    }

    /*
     * The put silently accepts keys that cannot be replicated, so the remove must not fail on such a key either, but
     * just skip the notification to the other members. The removal previously threw an unchecked exception from the
     * message construction, which also escaped a put that replaced such an entry before the new value was stored.
     */
    @Test
    public void testRemoveNonSerializableKeySkipsNotification() throws Exception {
        TestChannel channel = new TestChannel();
        LazyReplicatedMap<Object,String> map = new LazyReplicatedMap<>((key, value) -> {
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
     * A put stores an entry with a null value, so the size must count it like the entry and key views do. The size
     * previously filtered such an entry out, breaking the map contract that the size equals the size of the entry set,
     * and the isEmpty inherited the inconsistency.
     */
    @Test
    public void testSizeCountsActiveEntriesWithNullValue() throws Exception {
        LazyReplicatedMap<String,String> map = new LazyReplicatedMap<>((key, value) -> {
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
        Assert.assertTrue(map.containsValue(null));
        Assert.assertTrue(map.containsValue("value"));
        Assert.assertFalse(map.containsValue("missing"));
    }

    /*
     * A received backup request carries its key only in serialized form. The reply must deserialize that key with the
     * loaders of the map, like the other receive paths do, and must fail the request without throwing when the key
     * cannot be deserialized, so that the requester gets a normal timeout instead of being answered by a failure of the
     * responder.
     */
    @Test
    public void testBackupRequestDeserializesKeyOfReceivedMessage() throws Exception {
        URL testClasses = TesterSerializableKey.class.getProtectionDomain().getCodeSource().getLocation();
        try (URLClassLoader externalLoader =
                new URLClassLoader(new URL[] { testClasses }, ClassLoader.getPlatformClassLoader())) {
            Class<?> keyClass = externalLoader.loadClass(TesterSerializableKey.class.getName());
            Serializable key = (Serializable) keyClass.getConstructor(String.class).newInstance("key");
            LazyReplicatedMap<Object,String> map = new LazyReplicatedMap<>((mapKey, value) -> {
                // Not made primary here
            }, new TestChannel(), 1000, "TestAbstractReplicatedMap", new ClassLoader[] { externalLoader });
            map.put(key, "value");
            Member sender = new MemberImpl("localhost", 4017, -1);

            // Round trip over the serialization to get a message with the key data but no live key
            byte[] wire =
                    XByteBuffer.serialize(new MapMessage("TestAbstractReplicatedMap".getBytes(StandardCharsets.UTF_8),
                            MapMessage.MSG_RETRIEVE_BACKUP, false, key, null, null, null, null));

            MapMessage received = (MapMessage) XByteBuffer.deserialize(wire);
            Serializable reply = map.replyRequest(received, sender);
            Assert.assertTrue(reply instanceof MapMessage);
            Assert.assertEquals("value", ((MapMessage) reply).getValue());

            // Corrupt the class name inside the serialized key, so it cannot be deserialized
            byte[] corrupted = wire.clone();
            int at = indexOf(corrupted, "TesterSerializableKey".getBytes(StandardCharsets.US_ASCII));
            Assert.assertTrue(at > 0);
            corrupted[at] = 'X';
            MapMessage broken = (MapMessage) XByteBuffer.deserialize(corrupted);
            Assert.assertNull(map.replyRequest(broken, sender));
        }
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

    /*
     * A key may implement Serializable and still fail to serialize, which the message construction reports as an
     * unchecked exception. That must not abort the removal either.
     */
    @Test
    public void testRemoveUnserializableKeyDoesNotThrow() throws Exception {
        TestChannel channel = new TestChannel();
        LazyReplicatedMap<Object,String> map = new LazyReplicatedMap<>((key, value) -> {
            // Not made primary here
        }, channel, 1000, "TestAbstractReplicatedMap", null);
        map.memberAlive(new MemberImpl("localhost", 4019, -1));

        // The put keeps the entry on this node, as it cannot be published to a backup
        Object key = new FailingKey();
        map.put(key, "value");
        Assert.assertEquals(0, channel.sendCalls.get());

        Assert.assertEquals("value", map.remove(key));
        Assert.assertNull(map.getInternal(key));
        Assert.assertEquals(0, channel.sendCalls.get());
    }

    @Test
    public void testPutUnserializableKeyReplicatedMapKeepsEntryLocal() throws Exception {
        TestChannel channel = new TestChannel();
        ReplicatedMap<Object,String> map = new ReplicatedMap<>((key, value) -> {
            // Not made primary here
        }, channel, 1000, "TestAbstractReplicatedMap", null);
        Member first = new MemberImpl("localhost", 4020, -1);
        Member second = new MemberImpl("localhost", 4021, -1);
        map.memberAlive(first);
        map.memberAlive(second);

        Object key = new FailingKey();
        map.put(key, "value");

        Assert.assertEquals("value", map.get(key));
        Assert.assertEquals(0, channel.sendCalls.get());
        map.memberDisappeared(first);
        Assert.assertEquals("value", map.get(key));
        Assert.assertEquals(0, channel.sendCalls.get());
        Assert.assertEquals("value", map.remove(key));
    }

    @Test
    public void testMemberDisappearedSkipsNonSerializableEntry() throws Exception {
        TestChannel channel = new TestChannel();
        ReplicatedMap<Object,String> map = new ReplicatedMap<>((key, value) -> {
            // Not made primary here
        }, channel, 1000, "TestAbstractReplicatedMap", null);
        Member first = new MemberImpl("localhost", 4024, -1);
        Member second = new MemberImpl("localhost", 4025, -1);
        map.memberAlive(first);
        map.memberAlive(second);

        Object key = new Object();
        map.put(key, "value");
        map.put("good", "value");

        map.memberDisappeared(first);

        Assert.assertEquals("value", map.get(key));
        Assert.assertEquals("value", map.get("good"));
    }

    @Test
    public void testStateRequestSkipsUnserializableEntry() throws Exception {
        LazyReplicatedMap<Object,String> map = new LazyReplicatedMap<>((key, value) -> {
            // Not made primary here
        }, new TestChannel(), 1000, "TestAbstractReplicatedMap", null);
        map.put("good", "value");
        map.put(new FailingKey(), "local");

        MapMessage stateRequest = new MapMessage("TestAbstractReplicatedMap".getBytes(StandardCharsets.UTF_8),
                MapMessage.MSG_STATE_COPY, false, null, null, null, null, null);
        Serializable reply = map.replyRequest(stateRequest, new MemberImpl("localhost", 4022, -1));

        Assert.assertTrue(reply instanceof MapMessage);
        Object value = ((MapMessage) reply).getValue();
        Assert.assertTrue(value instanceof ArrayList);
        ArrayList<?> state = (ArrayList<?>) value;
        Assert.assertEquals(1, state.size());
        Assert.assertEquals("good", ((MapMessage) state.get(0)).getKey());
    }

    @Test
    public void testBackupRequestSkipsUnserializableValue() throws Exception {
        LazyReplicatedMap<String,Object> map = new LazyReplicatedMap<>((key, value) -> {
            // Not made primary here
        }, new TestChannel(), 1000, "TestAbstractReplicatedMap", null);
        map.put("key", new FailingKey());

        MapMessage backupRequest = new MapMessage("TestAbstractReplicatedMap".getBytes(StandardCharsets.UTF_8),
                MapMessage.MSG_RETRIEVE_BACKUP, false, "key", null, null, null, null);

        Assert.assertNull(map.replyRequest(backupRequest, new MemberImpl("localhost", 4023, -1)));
    }

    private static final class FailingKey implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        @Serial
        private void writeObject(@SuppressWarnings("unused") ObjectOutputStream out) throws IOException {
            throw new IOException("Cannot be serialized");
        }
    }
}
