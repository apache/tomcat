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
package org.apache.catalina.tribes.group;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.tribes.Channel;
import org.apache.catalina.tribes.ChannelReceiver;
import org.apache.catalina.tribes.Member;
import org.apache.catalina.tribes.MembershipService;
import org.apache.catalina.tribes.membership.StaticMember;

/**
 * Unit tests for the ChannelCoordinator startup race condition fix.
 */
public class TestChannelCoordinatorStartupRace {

    @Test
    public void testWaitForReadyDefaultMethodReturnsImmediately() throws Exception {
        ChannelReceiver receiver = new ChannelReceiver() {
            @Override
            public void start() {
            }

            @Override
            public void stop() {
            }

            @Override
            public String getHost() {
                return "127.0.0.1";
            }

            @Override
            public int getPort() {
                return 4000;
            }

            @Override
            public int getSecurePort() {
                return -1;
            }

            @Override
            public int getUdpPort() {
                return -1;
            }

            @Override
            public void setMessageListener(org.apache.catalina.tribes.MessageListener listener) {
            }

            @Override
            public org.apache.catalina.tribes.MessageListener getMessageListener() {
                return null;
            }

            @Override
            public org.apache.catalina.tribes.Channel getChannel() {
                return null;
            }

            @Override
            public void setChannel(org.apache.catalina.tribes.Channel channel) {
            }

            @Override
            public void heartbeat() {
            }
        };

        long start = System.nanoTime();
        boolean ready = receiver.waitForReady(5000, TimeUnit.MILLISECONDS);
        long elapsed = System.nanoTime() - start;

        Assert.assertTrue("Default waitForReady should return true", ready);
        Assert.assertTrue("Default waitForReady should return immediately",
                elapsed < TimeUnit.SECONDS.toNanos(1));
    }

    @Test
    public void testNioReceiverReadyLatchContract() throws Exception {
        Class<?> nioReceiverClass = Class.forName(
                "org.apache.catalina.tribes.transport.nio.NioReceiver");
        Field latchField = nioReceiverClass.getDeclaredField("readyLatch");
        latchField.setAccessible(true);

        Object receiver = nioReceiverClass.getDeclaredConstructor().newInstance();
        CountDownLatch initialLatch = (CountDownLatch) latchField.get(receiver);
        Assert.assertEquals("Initial readyLatch should have count 0", 0, initialLatch.getCount());

        Method waitForReady = nioReceiverClass.getMethod("waitForReady", long.class, TimeUnit.class);
        boolean ready = (boolean) waitForReady.invoke(receiver, 100, TimeUnit.MILLISECONDS);
        Assert.assertTrue("waitForReady on unstarted receiver should return true", ready);

        CountDownLatch freshLatch = new CountDownLatch(1);
        latchField.set(receiver, freshLatch);
        Assert.assertEquals("After simulated start(), latch should have count 1", 1, freshLatch.getCount());

        AtomicBoolean waitResult = new AtomicBoolean(false);
        Thread waiter = new Thread(() -> {
            try {
                waitResult.set((boolean) waitForReady.invoke(receiver, 500, TimeUnit.MILLISECONDS));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        waiter.start();

        Thread.sleep(100);
        Assert.assertFalse("waitForReady should still be blocking before countdown", waitResult.get());

        freshLatch.countDown();

        waiter.join(2000);
        Assert.assertTrue("waitForReady should return true after countdown", waitResult.get());
        Assert.assertEquals("Latch should have count 0 after countdown", 0, freshLatch.getCount());
    }

    @Test
    public void testChannelCoordinatorWaitsForReceiverBeforeLocalMember() throws Exception {
        AtomicBoolean waitForReadyCalled = new AtomicBoolean(false);
        AtomicBoolean localMemberAccessedBeforeReady = new AtomicBoolean(false);
        AtomicReference<String> operationOrder = new AtomicReference<>("");

        ChannelReceiver mockReceiver = new ChannelReceiver() {
            @Override
            public void start() {
                operationOrder.set(operationOrder.get() + "start();");
            }

            @Override
            public void stop() {
            }

            @Override
            public String getHost() {
                return "127.0.0.1";
            }

            @Override
            public int getPort() {
                return 4000;
            }

            @Override
            public int getSecurePort() {
                return -1;
            }

            @Override
            public int getUdpPort() {
                return -1;
            }

            @Override
            public void setMessageListener(org.apache.catalina.tribes.MessageListener listener) {
            }

            @Override
            public org.apache.catalina.tribes.MessageListener getMessageListener() {
                return null;
            }

            @Override
            public org.apache.catalina.tribes.Channel getChannel() {
                return null;
            }

            @Override
            public void setChannel(org.apache.catalina.tribes.Channel channel) {
            }

            @Override
            public void heartbeat() {
            }

            @Override
            public boolean waitForReady(long timeout, TimeUnit unit) {
                waitForReadyCalled.set(true);
                operationOrder.set(operationOrder.get() + "waitForReady();");
                return true;
            }
        };

        StaticMember localMember = new StaticMember();
        localMember.setHost("127.0.0.1");
        localMember.setPort(4000);

        MembershipService mockMembership = new MembershipService() {
            @Override
            public void setProperties(java.util.Properties properties) {
            }

            @Override
            public java.util.Properties getProperties() {
                return new java.util.Properties();
            }

            @Override
            public void start() {
            }

            @Override
            public void start(int level) {
            }

            @Override
            public void stop(int level) {
            }

            @Override
            public boolean hasMembers() {
                return false;
            }

            @Override
            public Member getMember(Member mbr) {
                return null;
            }

            @Override
            public Member[] getMembers() {
                return new Member[0];
            }

            @Override
            public Member getLocalMember(boolean incAliveTime) {
                operationOrder.set(operationOrder.get() + "getLocalMember();");
                if (!waitForReadyCalled.get()) {
                    localMemberAccessedBeforeReady.set(true);
                }
                return localMember;
            }

            @Override
            public String[] getMembersByName() {
                return new String[0];
            }

            @Override
            public Member findMemberByName(String name) {
                return null;
            }

            @Override
            public void setLocalMemberProperties(String listenHost, int listenPort,
                    int securePort, int udpPort) {
                operationOrder.set(operationOrder.get() + "setLocalMemberProperties();");
            }

            @Override
            public void setMembershipListener(org.apache.catalina.tribes.MembershipListener listener) {
            }

            @Override
            public void removeMembershipListener() {
            }

            @Override
            public void setPayload(byte[] payload) {
            }

            @Override
            public void setDomain(byte[] domain) {
            }

            @Override
            public void broadcast(org.apache.catalina.tribes.ChannelMessage message) {
            }

            @Override
            public org.apache.catalina.tribes.Channel getChannel() {
                return null;
            }

            @Override
            public void setChannel(org.apache.catalina.tribes.Channel channel) {
            }

            @Override
            public org.apache.catalina.tribes.MembershipProvider getMembershipProvider() {
                return null;
            }
        };

        org.apache.catalina.tribes.ChannelSender mockSender =
                new org.apache.catalina.tribes.ChannelSender() {
                    @Override
                    public void start() {
                    }

                    @Override
                    public void stop() {
                    }

                    @Override
                    public void heartbeat() {
                    }

                    @Override
                    public void add(org.apache.catalina.tribes.Member member) {
                    }

                    @Override
                    public void remove(org.apache.catalina.tribes.Member member) {
                    }

                    @Override
                    public void sendMessage(org.apache.catalina.tribes.ChannelMessage message,
                            org.apache.catalina.tribes.Member[] destination) {
                    }

                    @Override
                    public org.apache.catalina.tribes.Channel getChannel() {
                        return null;
                    }

                    @Override
                    public void setChannel(org.apache.catalina.tribes.Channel channel) {
                    }
                };

        ChannelCoordinator coordinator = new ChannelCoordinator(
                mockReceiver, mockSender, mockMembership);

        Class<?> interceptorBase = Class.forName(
                "org.apache.catalina.tribes.group.ChannelInterceptorBase");
        Field channelField = interceptorBase.getDeclaredField("channel");
        channelField.setAccessible(true);

        org.apache.catalina.tribes.Channel mockChannel =
                new org.apache.catalina.tribes.Channel() {
                    @Override
                    public void addInterceptor(org.apache.catalina.tribes.ChannelInterceptor interceptor) {
                    }

                    @Override
                    public void start(int svc) {
                    }

                    @Override
                    public void stop(int svc) {
                    }

                    @Override
                    public org.apache.catalina.tribes.UniqueId send(Member[] destination,
                            java.io.Serializable msg, int options) {
                        return null;
                    }

                    @Override
                    public org.apache.catalina.tribes.UniqueId send(Member[] destination,
                            java.io.Serializable msg, int options,
                            org.apache.catalina.tribes.ErrorHandler handler) {
                        return null;
                    }

                    @Override
                    public void heartbeat() {
                    }

                    @Override
                    public void setHeartbeat(boolean enable) {
                    }

                    @Override
                    public void addMembershipListener(
                            org.apache.catalina.tribes.MembershipListener listener) {
                    }

                    @Override
                    public void addChannelListener(org.apache.catalina.tribes.ChannelListener listener) {
                    }

                    @Override
                    public void removeMembershipListener(
                            org.apache.catalina.tribes.MembershipListener listener) {
                    }

                    @Override
                    public void removeChannelListener(org.apache.catalina.tribes.ChannelListener listener) {
                    }

                    @Override
                    public boolean hasMembers() {
                        return false;
                    }

                    @Override
                    public Member[] getMembers() {
                        return new Member[0];
                    }

                    @Override
                    public Member getLocalMember(boolean incAlive) {
                        return mockMembership.getLocalMember(incAlive);
                    }

                    @Override
                    public Member getMember(Member mbr) {
                        return null;
                    }

                    @Override
                    public String getName() {
                        return "test-channel";
                    }

                    @Override
                    public void setName(String name) {
                    }

                    @Override
                    public java.util.concurrent.ScheduledExecutorService getUtilityExecutor() {
                        return null;
                    }

                    @Override
                    public void setUtilityExecutor(
                            java.util.concurrent.ScheduledExecutorService utilityExecutor) {
                    }
                };
        channelField.set(coordinator, mockChannel);

        Method internalStart = ChannelCoordinator.class.getDeclaredMethod("internalStart", int.class);
        internalStart.setAccessible(true);
        internalStart.invoke(coordinator, Channel.SND_RX_SEQ);

        Assert.assertTrue("waitForReady must be called during startup", waitForReadyCalled.get());
        Assert.assertFalse("getLocalMember must NOT be accessed before waitForReady",
                localMemberAccessedBeforeReady.get());

        String order = operationOrder.get();
        Assert.assertEquals(
                "start();waitForReady();getLocalMember();setLocalMemberProperties();", order);
    }

    @Test
    public void testChannelCoordinatorThrowsWhenReceiverNotReady() throws Exception {
        ChannelReceiver slowReceiver = new ChannelReceiver() {
            @Override
            public void start() {
            }

            @Override
            public void stop() {
            }

            @Override
            public String getHost() {
                return "127.0.0.1";
            }

            @Override
            public int getPort() {
                return 4000;
            }

            @Override
            public int getSecurePort() {
                return -1;
            }

            @Override
            public int getUdpPort() {
                return -1;
            }

            @Override
            public void setMessageListener(org.apache.catalina.tribes.MessageListener listener) {
            }

            @Override
            public org.apache.catalina.tribes.MessageListener getMessageListener() {
                return null;
            }

            @Override
            public org.apache.catalina.tribes.Channel getChannel() {
                return null;
            }

            @Override
            public void setChannel(org.apache.catalina.tribes.Channel channel) {
            }

            @Override
            public void heartbeat() {
            }

            @Override
            public boolean waitForReady(long timeout, TimeUnit unit) {
                return false;
            }
        };

        MembershipService mockMembership = new MembershipService() {
            @Override
            public void setProperties(java.util.Properties properties) {
            }

            @Override
            public java.util.Properties getProperties() {
                return new java.util.Properties();
            }

            @Override
            public void start() {
            }

            @Override
            public void start(int level) {
            }

            @Override
            public void stop(int level) {
            }

            @Override
            public boolean hasMembers() {
                return false;
            }

            @Override
            public Member getMember(Member mbr) {
                return null;
            }

            @Override
            public Member[] getMembers() {
                return new Member[0];
            }

            @Override
            public Member getLocalMember(boolean incAliveTime) {
                Assert.fail("getLocalMember should NOT be called when receiver is not ready");
                return null;
            }

            @Override
            public String[] getMembersByName() {
                return new String[0];
            }

            @Override
            public Member findMemberByName(String name) {
                return null;
            }

            @Override
            public void setLocalMemberProperties(String listenHost, int listenPort,
                    int securePort, int udpPort) {
            }

            @Override
            public void setMembershipListener(org.apache.catalina.tribes.MembershipListener listener) {
            }

            @Override
            public void removeMembershipListener() {
            }

            @Override
            public void setPayload(byte[] payload) {
            }

            @Override
            public void setDomain(byte[] domain) {
            }

            @Override
            public void broadcast(org.apache.catalina.tribes.ChannelMessage message) {
            }

            @Override
            public org.apache.catalina.tribes.Channel getChannel() {
                return null;
            }

            @Override
            public void setChannel(org.apache.catalina.tribes.Channel channel) {
            }

            @Override
            public org.apache.catalina.tribes.MembershipProvider getMembershipProvider() {
                return null;
            }
        };

        org.apache.catalina.tribes.ChannelSender mockSender =
                new org.apache.catalina.tribes.ChannelSender() {
                    @Override
                    public void start() {
                    }

                    @Override
                    public void stop() {
                    }

                    @Override
                    public void heartbeat() {
                    }

                    @Override
                    public void add(org.apache.catalina.tribes.Member member) {
                    }

                    @Override
                    public void remove(org.apache.catalina.tribes.Member member) {
                    }

                    @Override
                    public void sendMessage(org.apache.catalina.tribes.ChannelMessage message,
                            org.apache.catalina.tribes.Member[] destination) {
                    }

                    @Override
                    public org.apache.catalina.tribes.Channel getChannel() {
                        return null;
                    }

                    @Override
                    public void setChannel(org.apache.catalina.tribes.Channel channel) {
                    }
                };

        ChannelCoordinator coordinator = new ChannelCoordinator(
                slowReceiver, mockSender, mockMembership);

        Class<?> interceptorBase = Class.forName(
                "org.apache.catalina.tribes.group.ChannelInterceptorBase");
        Field channelField = interceptorBase.getDeclaredField("channel");
        channelField.setAccessible(true);
        channelField.set(coordinator, new org.apache.catalina.tribes.Channel() {
            @Override
            public void addInterceptor(org.apache.catalina.tribes.ChannelInterceptor interceptor) {
            }

            @Override
            public void start(int svc) {
            }

            @Override
            public void stop(int svc) {
            }

            @Override
            public org.apache.catalina.tribes.UniqueId send(Member[] destination,
                    java.io.Serializable msg, int options) {
                return null;
            }

            @Override
            public org.apache.catalina.tribes.UniqueId send(Member[] destination,
                    java.io.Serializable msg, int options,
                    org.apache.catalina.tribes.ErrorHandler handler) {
                return null;
            }

            @Override
            public void heartbeat() {
            }

            @Override
            public void setHeartbeat(boolean enable) {
            }

            @Override
            public void addMembershipListener(org.apache.catalina.tribes.MembershipListener listener) {
            }

            @Override
            public void addChannelListener(org.apache.catalina.tribes.ChannelListener listener) {
            }

            @Override
            public void removeMembershipListener(org.apache.catalina.tribes.MembershipListener listener) {
            }

            @Override
            public void removeChannelListener(org.apache.catalina.tribes.ChannelListener listener) {
            }

            @Override
            public boolean hasMembers() {
                return false;
            }

            @Override
            public Member[] getMembers() {
                return new Member[0];
            }

            @Override
            public Member getLocalMember(boolean incAlive) {
                return mockMembership.getLocalMember(incAlive);
            }

            @Override
            public Member getMember(Member mbr) {
                return null;
            }

            @Override
            public String getName() {
                return "test";
            }

            @Override
            public void setName(String name) {
            }

            @Override
            public java.util.concurrent.ScheduledExecutorService getUtilityExecutor() {
                return null;
            }

            @Override
            public void setUtilityExecutor(java.util.concurrent.ScheduledExecutorService utilityExecutor) {
            }
        });

        Method internalStart = ChannelCoordinator.class.getDeclaredMethod("internalStart", int.class);
        internalStart.setAccessible(true);

        try {
            internalStart.invoke(coordinator, Channel.SND_RX_SEQ);
            Assert.fail("Expected ChannelException when receiver fails to become ready");
        } catch (java.lang.reflect.InvocationTargetException e) {
            Throwable cause = e.getCause();
            Assert.assertTrue("Cause should be ChannelException, was: " + cause.getClass(),
                    cause instanceof org.apache.catalina.tribes.ChannelException);
        }
    }

    @Test
    public void testChannelCoordinatorHandlesInterruptedException() throws Exception {
        AtomicBoolean interrupted = new AtomicBoolean(false);

        ChannelReceiver interruptingReceiver = new ChannelReceiver() {
            @Override
            public void start() {
            }

            @Override
            public void stop() {
            }

            @Override
            public String getHost() {
                return "127.0.0.1";
            }

            @Override
            public int getPort() {
                return 4000;
            }

            @Override
            public int getSecurePort() {
                return -1;
            }

            @Override
            public int getUdpPort() {
                return -1;
            }

            @Override
            public void setMessageListener(org.apache.catalina.tribes.MessageListener listener) {
            }

            @Override
            public org.apache.catalina.tribes.MessageListener getMessageListener() {
                return null;
            }

            @Override
            public org.apache.catalina.tribes.Channel getChannel() {
                return null;
            }

            @Override
            public void setChannel(org.apache.catalina.tribes.Channel channel) {
            }

            @Override
            public void heartbeat() {
            }

            @Override
            public boolean waitForReady(long timeout, TimeUnit unit) throws InterruptedException {
                interrupted.set(true);
                throw new InterruptedException("Simulated interrupt during waitForReady");
            }
        };

        MembershipService mockMembership = new MembershipService() {
            @Override
            public void setProperties(java.util.Properties properties) {
            }

            @Override
            public java.util.Properties getProperties() {
                return new java.util.Properties();
            }

            @Override
            public void start() {
            }

            @Override
            public void start(int level) {
            }

            @Override
            public void stop(int level) {
            }

            @Override
            public boolean hasMembers() {
                return false;
            }

            @Override
            public Member getMember(Member mbr) {
                return null;
            }

            @Override
            public Member[] getMembers() {
                return new Member[0];
            }

            @Override
            public Member getLocalMember(boolean incAliveTime) {
                return null;
            }

            @Override
            public String[] getMembersByName() {
                return new String[0];
            }

            @Override
            public Member findMemberByName(String name) {
                return null;
            }

            @Override
            public void setLocalMemberProperties(String listenHost, int listenPort,
                    int securePort, int udpPort) {
            }

            @Override
            public void setMembershipListener(org.apache.catalina.tribes.MembershipListener listener) {
            }

            @Override
            public void removeMembershipListener() {
            }

            @Override
            public void setPayload(byte[] payload) {
            }

            @Override
            public void setDomain(byte[] domain) {
            }

            @Override
            public void broadcast(org.apache.catalina.tribes.ChannelMessage message) {
            }

            @Override
            public org.apache.catalina.tribes.Channel getChannel() {
                return null;
            }

            @Override
            public void setChannel(org.apache.catalina.tribes.Channel channel) {
            }

            @Override
            public org.apache.catalina.tribes.MembershipProvider getMembershipProvider() {
                return null;
            }
        };

        org.apache.catalina.tribes.ChannelSender mockSender =
                new org.apache.catalina.tribes.ChannelSender() {
                    @Override
                    public void start() {
                    }

                    @Override
                    public void stop() {
                    }

                    @Override
                    public void heartbeat() {
                    }

                    @Override
                    public void add(org.apache.catalina.tribes.Member member) {
                    }

                    @Override
                    public void remove(org.apache.catalina.tribes.Member member) {
                    }

                    @Override
                    public void sendMessage(org.apache.catalina.tribes.ChannelMessage message,
                            org.apache.catalina.tribes.Member[] destination) {
                    }

                    @Override
                    public org.apache.catalina.tribes.Channel getChannel() {
                        return null;
                    }

                    @Override
                    public void setChannel(org.apache.catalina.tribes.Channel channel) {
                    }
                };

        ChannelCoordinator coordinator = new ChannelCoordinator(
                interruptingReceiver, mockSender, mockMembership);

        Class<?> interceptorBase = Class.forName(
                "org.apache.catalina.tribes.group.ChannelInterceptorBase");
        Field channelField = interceptorBase.getDeclaredField("channel");
        channelField.setAccessible(true);
        channelField.set(coordinator, new org.apache.catalina.tribes.Channel() {
            @Override
            public void addInterceptor(org.apache.catalina.tribes.ChannelInterceptor interceptor) {
            }

            @Override
            public void start(int svc) {
            }

            @Override
            public void stop(int svc) {
            }

            @Override
            public org.apache.catalina.tribes.UniqueId send(Member[] destination,
                    java.io.Serializable msg, int options) {
                return null;
            }

            @Override
            public org.apache.catalina.tribes.UniqueId send(Member[] destination,
                    java.io.Serializable msg, int options,
                    org.apache.catalina.tribes.ErrorHandler handler) {
                return null;
            }

            @Override
            public void heartbeat() {
            }

            @Override
            public void setHeartbeat(boolean enable) {
            }

            @Override
            public void addMembershipListener(org.apache.catalina.tribes.MembershipListener listener) {
            }

            @Override
            public void addChannelListener(org.apache.catalina.tribes.ChannelListener listener) {
            }

            @Override
            public void removeMembershipListener(org.apache.catalina.tribes.MembershipListener listener) {
            }

            @Override
            public void removeChannelListener(org.apache.catalina.tribes.ChannelListener listener) {
            }

            @Override
            public boolean hasMembers() {
                return false;
            }

            @Override
            public Member[] getMembers() {
                return new Member[0];
            }

            @Override
            public Member getLocalMember(boolean incAlive) {
                return null;
            }

            @Override
            public Member getMember(Member mbr) {
                return null;
            }

            @Override
            public String getName() {
                return "test";
            }

            @Override
            public void setName(String name) {
            }

            @Override
            public java.util.concurrent.ScheduledExecutorService getUtilityExecutor() {
                return null;
            }

            @Override
            public void setUtilityExecutor(java.util.concurrent.ScheduledExecutorService utilityExecutor) {
            }
        });

        Method internalStart = ChannelCoordinator.class.getDeclaredMethod("internalStart", int.class);
        internalStart.setAccessible(true);

        try {
            internalStart.invoke(coordinator, Channel.SND_RX_SEQ);
            Assert.fail("Expected ChannelException when waitForReady is interrupted");
        } catch (java.lang.reflect.InvocationTargetException e) {
            Throwable cause = e.getCause();
            Assert.assertTrue("Cause should be ChannelException, was: " + cause.getClass(),
                    cause instanceof org.apache.catalina.tribes.ChannelException);
            Assert.assertTrue("Interrupt should have been triggered", interrupted.get());
        }
    }
}
