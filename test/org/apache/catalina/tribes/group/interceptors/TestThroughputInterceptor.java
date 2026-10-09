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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

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

public class TestThroughputInterceptor {

    /*
     * Chain end that either blocks until released or fails the send, so that a send can
     * be made to fail deterministically while another send is still in flight.
     */
    private static class GatedFailureChain extends ChannelInterceptorBase {

        private final CountDownLatch slowStarted = new CountDownLatch(1);
        private final CountDownLatch releaseSlow = new CountDownLatch(1);

        private volatile boolean failNext = false;

        @Override
        public void sendMessage(Member[] destination, ChannelMessage msg, InterceptorPayload payload)
                throws ChannelException {
            if (failNext) {
                throw new ChannelException("Induced send failure");
            }
            slowStarted.countDown();
            try {
                releaseSlow.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException x) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static class PassThrough extends ChannelInterceptorBase {
    }

    private static ChannelData createMessage(Member source) {
        ChannelData data = new ChannelData(false);
        data.setUniqueId(UUIDGenerator.randomUUID(false));
        data.setAddress(source);
        data.setMessage(new XByteBuffer(64, false));
        return data;
    }

    @Test
    public void testConcurrentSendFailureReleasesInFlightSlot() throws Exception {
        ThroughputInterceptor interceptor = new ThroughputInterceptor();
        GatedFailureChain chain = new GatedFailureChain();
        interceptor.setChannel(new GroupChannel());
        interceptor.setNext(chain);

        MemberImpl member = new MemberImpl("localhost", 4000, -1);
        AtomicReference<Throwable> slowFailure = new AtomicReference<>();
        AtomicReference<Throwable> failingOutcome = new AtomicReference<>();

        Thread slowSender = new Thread(() -> {
            try {
                interceptor.sendMessage(new Member[] { member }, createMessage(member), null);
            } catch (ChannelException x) {
                slowFailure.set(x);
            }
        });
        slowSender.start();
        Assert.assertTrue("Blocking send did not start", chain.slowStarted.await(10, TimeUnit.SECONDS));

        // A second send fails while the first one is still in flight
        Thread failingSender = new Thread(() -> {
            chain.failNext = true;
            try {
                interceptor.sendMessage(new Member[] { member }, createMessage(member), null);
                failingOutcome.set(new IllegalStateException("The second send should have failed"));
            } catch (ChannelException expected) {
                // Expected
            }
        });
        failingSender.start();
        failingSender.join(10000);
        Assert.assertFalse("Failing send thread did not finish", failingSender.isAlive());

        chain.releaseSlow.countDown();
        slowSender.join(10000);
        Assert.assertFalse("Slow send thread did not finish", slowSender.isAlive());

        Assert.assertNull("The slow send should not have failed", slowFailure.get());
        Assert.assertNull("The failing send should have thrown ChannelException", failingOutcome.get());
        Assert.assertEquals("A failed send must release the in-flight counter slot",
                0, interceptor.access.get());
        Assert.assertEquals("The failed send must be counted as an error",
                1, interceptor.getMsgTxErr().get());
    }

    @Test
    public void testSuccessfulSendReleasesInFlightSlot() throws Exception {
        ThroughputInterceptor interceptor = new ThroughputInterceptor();
        GatedFailureChain chain = new GatedFailureChain();
        interceptor.setChannel(new GroupChannel());
        interceptor.setNext(chain);

        MemberImpl member = new MemberImpl("localhost", 4000, -1);
        AtomicReference<Throwable> sendFailure = new AtomicReference<>();

        Thread sender = new Thread(() -> {
            try {
                interceptor.sendMessage(new Member[] { member }, createMessage(member), null);
            } catch (ChannelException x) {
                sendFailure.set(x);
            }
        });
        sender.start();
        Assert.assertTrue("Blocking send did not start", chain.slowStarted.await(10, TimeUnit.SECONDS));
        chain.releaseSlow.countDown();
        sender.join(10000);
        Assert.assertFalse("Send thread did not finish", sender.isAlive());

        Assert.assertNull("The send should not have failed", sendFailure.get());
        Assert.assertEquals("A successful send must release the in-flight counter slot",
                0, interceptor.access.get());
    }

    @Test
    public void testConcurrentSendReceiveAndReport() throws Exception {
        ThroughputInterceptor interceptor = new ThroughputInterceptor();
        interceptor.setChannel(new GroupChannel());
        interceptor.setNext(new PassThrough());
        // Small interval so the periodic report paths inside sendMessage and
        // messageReceived are exercised in addition to the direct calls below
        interceptor.setInterval(10);

        MemberImpl member = new MemberImpl("localhost", 4000, -1);
        int threadCount = 4;
        int iterations = 250;
        CountDownLatch done = new CountDownLatch(threadCount);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        for (int i = 0; i < threadCount; i++) {
            Thread thread = new Thread(() -> {
                try {
                    for (int j = 0; j < iterations; j++) {
                        interceptor.sendMessage(new Member[] { member }, createMessage(member), null);
                        interceptor.messageReceived(createMessage(member));
                        interceptor.report(interceptor.getTimeTx());
                    }
                } catch (Throwable x) {
                    failure.compareAndSet(null, x);
                } finally {
                    done.countDown();
                }
            });
            thread.start();
        }

        Assert.assertTrue("Traffic threads did not finish", done.await(30, TimeUnit.SECONDS));
        Assert.assertNull("Concurrent traffic and reporting must not throw: " + failure.get(), failure.get());
        Assert.assertEquals("Every completed send must release the in-flight counter slot",
                0, interceptor.access.get());
        Assert.assertEquals("All successful sends must be counted",
                threadCount * iterations, interceptor.getMsgTxCnt().get() - 1);
        Assert.assertEquals("All receives must be counted",
                (long) threadCount * iterations, interceptor.getMsgRxCnt().get());
    }
}
