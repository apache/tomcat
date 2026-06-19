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

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import org.apache.catalina.tribes.Channel;
import org.apache.catalina.tribes.ChannelReceiver;
import org.apache.catalina.tribes.Member;
import org.apache.catalina.tribes.MembershipProvider;
import org.apache.catalina.tribes.MessageListener;
import org.apache.catalina.tribes.membership.MemberImpl;
import org.apache.catalina.tribes.membership.MembershipServiceBase;
import org.apache.catalina.tribes.transport.ReceiverBase;

public class TestGroupChannelStartStop {
    private GroupChannel channel = null;
    private int udpPort = 45543;

    @Before
    public void setUp() throws Exception {
        channel = new GroupChannel();
        ((ReceiverBase) channel.getChannelReceiver()).setHost("localhost");
    }

    @After
    public void tearDown() throws Exception {
        try {
            channel.stop(Channel.DEFAULT);
        } catch (Exception ignore) {
            // Ignore
        }
    }

    @Test
    public void testDoubleFullStart() throws Exception {
        int count = 0;
        try {
            channel.start(Channel.DEFAULT);
            count++;
        } catch ( Exception x){x.printStackTrace();}
        try {
            channel.start(Channel.DEFAULT);
            count++;
        } catch ( Exception x){x.printStackTrace();}
        Assert.assertEquals(count,2);
        channel.stop(Channel.DEFAULT);
    }

    @Test
    public void testScrap() throws Exception {
        System.out.println(channel.getChannelReceiver().getClass());
        ((ReceiverBase)channel.getChannelReceiver()).setMaxThreads(1);
    }

    @Test
    public void testDoublePartialStart() throws Exception {
        //try to double start the RX
        int count = 0;
        try {
            channel.start(Channel.SND_RX_SEQ);
            channel.start(Channel.MBR_RX_SEQ);
            count++;
        } catch ( Exception x){x.printStackTrace();}
        try {
            channel.start(Channel.MBR_RX_SEQ);
            count++;
        } catch ( Exception x){
            // expected
        }
        Assert.assertEquals(count,1);
        channel.stop(Channel.DEFAULT);
        //double the membership sender
        count = 0;
        try {
            channel.start(Channel.SND_RX_SEQ);
            channel.start(Channel.MBR_TX_SEQ);
            count++;
        } catch ( Exception x){x.printStackTrace();}
        try {
            channel.start(Channel.MBR_TX_SEQ);
            count++;
        } catch ( Exception x){
            // expected
        }
        Assert.assertEquals(count,1);
        channel.stop(Channel.DEFAULT);

        count = 0;
        try {
            channel.start(Channel.SND_RX_SEQ);
            count++;
        } catch ( Exception x){x.printStackTrace();}
        try {
            channel.start(Channel.SND_RX_SEQ);
            count++;
        } catch ( Exception x){
            // expected
        }
        Assert.assertEquals(count,1);
        channel.stop(Channel.DEFAULT);

        count = 0;
        try {
            channel.start(Channel.SND_TX_SEQ);
            count++;
        } catch ( Exception x){x.printStackTrace();}
        try {
            channel.start(Channel.SND_TX_SEQ);
            count++;
        } catch ( Exception x){
            // expected
        }
        Assert.assertEquals(count,1);
        channel.stop(Channel.DEFAULT);
    }

    @Test
    public void testFalseOption() throws Exception {
        int flag = 0xFFF0;//should get ignored by the underlying components
        int count = 0;
        try {
            channel.start(flag);
            count++;
        } catch ( Exception x){x.printStackTrace();}
        try {
            channel.start(flag);
            count++;
        } catch ( Exception x){
            // expected
        }
        Assert.assertEquals(count,2);
        channel.stop(Channel.DEFAULT);
    }

    @Test
    public void testUdpReceiverStart() throws Exception {
        ReceiverBase rb = (ReceiverBase)channel.getChannelReceiver();
        rb.setUdpPort(udpPort);
        channel.start(Channel.DEFAULT);
        Thread.sleep(1000);
        channel.stop(Channel.DEFAULT);
    }

    @Test
    public void testLocalMemberPropertiesAreSetAfterReceiverStartCompletes() throws Exception {
        BlockingChannelReceiver receiver = new BlockingChannelReceiver();
        TestMembershipService membershipService = new TestMembershipService(receiver);
        channel.setChannelReceiver(receiver);
        channel.setMembershipService(membershipService);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> startFuture = executor.submit(() -> {
                try {
                    channel.start(Channel.SND_RX_SEQ);
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            });

            Assert.assertTrue(receiver.awaitStartEntered.await(5, TimeUnit.SECONDS));
            Assert.assertFalse(membershipService.localMemberRequested.await(250, TimeUnit.MILLISECONDS));

            receiver.allowStart.countDown();
            startFuture.get(5, TimeUnit.SECONDS);

            Assert.assertTrue(membershipService.localMemberRequested.await(0, TimeUnit.MILLISECONDS));
            Assert.assertTrue(membershipService.localMemberPropertiesSet);
        } finally {
            receiver.allowStart.countDown();
            executor.shutdownNow();
        }
    }

    private static class BlockingChannelReceiver implements ChannelReceiver {

        private final CountDownLatch awaitStartEntered = new CountDownLatch(1);
        private final CountDownLatch allowStart = new CountDownLatch(1);
        private final AtomicBoolean awaitStartCompleted = new AtomicBoolean(false);
        private MessageListener listener;
        private Channel channel;

        @Override
        public void start() {
            // NO-OP
        }

        @Override
        public void awaitStart() throws IOException {
            awaitStartEntered.countDown();
            try {
                if (!allowStart.await(5, TimeUnit.SECONDS)) {
                    throw new IOException("Timed out waiting to complete receiver start");
                }
                awaitStartCompleted.set(true);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException(e);
            }
        }

        @Override
        public void stop() {
            // NO-OP
        }

        @Override
        public String getHost() {
            return "localhost";
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
        public void setMessageListener(MessageListener listener) {
            this.listener = listener;
        }

        @Override
        public MessageListener getMessageListener() {
            return listener;
        }

        @Override
        public Channel getChannel() {
            return channel;
        }

        @Override
        public void setChannel(Channel channel) {
            this.channel = channel;
        }

        @Override
        public void heartbeat() {
            // NO-OP
        }
    }

    private static class TestMembershipService extends MembershipServiceBase {

        private final BlockingChannelReceiver receiver;
        private final CountDownLatch localMemberRequested = new CountDownLatch(1);
        private final Member localMember;
        private volatile boolean localMemberPropertiesSet = false;

        TestMembershipService(BlockingChannelReceiver receiver) throws IOException {
            this.receiver = receiver;
            localMember = new MemberImpl("localhost", 4000, 0);
        }

        @Override
        public void start(int level) {
            // NO-OP
        }

        @Override
        public void stop(int level) {
            // NO-OP
        }

        @Override
        public Member getLocalMember(boolean incAliveTime) {
            Assert.assertTrue(receiver.awaitStartCompleted.get());
            localMemberRequested.countDown();
            return localMember;
        }

        @Override
        public void setLocalMemberProperties(String listenHost, int listenPort, int securePort, int udpPort) {
            Assert.assertTrue(receiver.awaitStartCompleted.get());
            localMemberPropertiesSet = true;
        }

        @Override
        public void setPayload(byte[] payload) {
            // NO-OP
        }

        @Override
        public void setDomain(byte[] domain) {
            // NO-OP
        }

        @Override
        public MembershipProvider getMembershipProvider() {
            return null;
        }
    }
}
