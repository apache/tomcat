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

import java.util.Iterator;
import java.util.concurrent.ScheduledFuture;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import org.apache.catalina.tribes.Channel;
import org.apache.catalina.tribes.ChannelInterceptor;
import org.apache.catalina.tribes.membership.McastService;
import org.apache.catalina.tribes.transport.ReceiverBase;
import org.apache.catalina.tribes.transport.ReplicationTransmitter;

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
    public void testNoMonitorLeakOnRepeatedStart() throws Exception {
        channel.start(Channel.DEFAULT);
        ScheduledFuture<?> firstMonitor = channel.monitorFuture;
        Assert.assertNotNull(firstMonitor);
        // a second start must not schedule a second monitor task
        channel.start(Channel.DEFAULT);
        Assert.assertSame(firstMonitor, channel.monitorFuture);
        channel.stop(Channel.DEFAULT);
    }

    @Test
    public void testPartialStopKeepsHeartbeatAlive() throws Exception {
        channel.start(Channel.DEFAULT);
        // the monitor task runs immediately and schedules the heartbeat
        long deadline = System.currentTimeMillis() + 10000;
        while (channel.heartbeatFuture == null && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        ScheduledFuture<?> monitor = channel.monitorFuture;
        ScheduledFuture<?> heartbeat = channel.heartbeatFuture;
        Assert.assertNotNull(heartbeat);
        // a partial stop must not cancel the monitor or the heartbeat
        channel.stop(Channel.SND_RX_SEQ);
        Assert.assertSame(monitor, channel.monitorFuture);
        Assert.assertFalse(monitor.isCancelled());
        Assert.assertFalse(heartbeat.isCancelled());
        Assert.assertNotNull(channel.utilityExecutor);
        // a full stop cancels them
        channel.stop(Channel.DEFAULT);
        Assert.assertNull(channel.monitorFuture);
        Assert.assertNull(channel.heartbeatFuture);
        Assert.assertTrue(monitor.isCancelled());
        Assert.assertTrue(heartbeat.isCancelled());
    }

    @Test
    public void testPartialStopKeepsInterceptorChannel() throws Exception {
        channel.start(Channel.DEFAULT);
        channel.stop(Channel.SND_RX_SEQ);
        // services are still running, the interceptors must still be attached to the channel
        for (Iterator<ChannelInterceptor> it = channel.getInterceptors(); it.hasNext();) {
            ChannelInterceptor interceptor = it.next();
            Assert.assertSame(interceptor.getClass().getName(), channel, interceptor.getChannel());
        }
        channel.stop(Channel.SND_TX_SEQ | Channel.MBR_RX_SEQ | Channel.MBR_TX_SEQ);
        for (Iterator<ChannelInterceptor> it = channel.getInterceptors(); it.hasNext();) {
            ChannelInterceptor interceptor = it.next();
            Assert.assertNull(interceptor.getClass().getName(), interceptor.getChannel());
        }
    }

    @Test
    public void testOverlappingPartialStart() throws Exception {
        // start one service, then start everything: the already
        // started service must not be started a second time
        ReceiverBase rb = (ReceiverBase) channel.getChannelReceiver();
        rb.setPort(45588);
        // one bind attempt only, so a second start on the same port fails
        rb.setAutoBind(0);
        int count = 0;
        try {
            channel.start(Channel.SND_RX_SEQ);
            count++;
        } catch ( Exception x){x.printStackTrace();}
        try {
            channel.start(Channel.DEFAULT);
            count++;
        } catch ( Exception x){x.printStackTrace();}
        Assert.assertEquals(2,count);
        channel.stop(Channel.DEFAULT);
    }

    @Test
    public void testFailedStartRecordsServicesThatStarted() throws Exception {
        CountingSender sender = new CountingSender();
        FailingMembershipService membershipService = new FailingMembershipService();
        channel.setChannelSender(sender);
        channel.setMembershipService(membershipService);
        channel.coordinator.setChannel(channel);

        int services = Channel.SND_TX_SEQ | Channel.MBR_RX_SEQ;
        try {
            channel.coordinator.internalStart(services);
            Assert.fail("The first membership start should fail");
        } catch (Exception expected) {
            // Expected
        }
        Assert.assertEquals(Channel.SND_TX_SEQ, channel.coordinator.getStartLevel());
        Assert.assertEquals(1, sender.startCount);

        channel.coordinator.internalStart(services);
        Assert.assertEquals(services, channel.coordinator.getStartLevel());
        Assert.assertEquals(1, sender.startCount);
        channel.coordinator.internalStop(services);
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

    private static class CountingSender extends ReplicationTransmitter {

        private volatile int startCount;

        @Override
        public synchronized void start() {
            startCount++;
        }

        @SuppressWarnings("sync-override")
        @Override
        public void stop() {
            // NO-OP
        }
    }

    private static class FailingMembershipService extends McastService {

        private boolean fail = true;

        @Override
        public void start(int level) throws Exception {
            if (fail) {
                fail = false;
                throw new Exception("Test failure");
            }
        }

        @Override
        public void stop(int level) {
            // NO-OP
        }
    }
}
