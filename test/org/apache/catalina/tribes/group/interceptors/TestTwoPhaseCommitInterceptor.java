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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.tribes.ChannelException;
import org.apache.catalina.tribes.ChannelMessage;
import org.apache.catalina.tribes.ErrorHandler;
import org.apache.catalina.tribes.Member;
import org.apache.catalina.tribes.UniqueId;
import org.apache.catalina.tribes.group.ChannelInterceptorBase;
import org.apache.catalina.tribes.group.GroupChannel;
import org.apache.catalina.tribes.group.InterceptorPayload;
import org.apache.catalina.tribes.io.ChannelData;
import org.apache.catalina.tribes.io.XByteBuffer;
import org.apache.catalina.tribes.membership.MemberImpl;
import org.apache.catalina.tribes.util.UUIDGenerator;

public class TestTwoPhaseCommitInterceptor {

    private static class RecordingSendChain extends ChannelInterceptorBase {

        private final List<ChannelMessage> messages = Collections.synchronizedList(new ArrayList<>());
        private final List<InterceptorPayload> payloads = Collections.synchronizedList(new ArrayList<>());

        @Override
        public void sendMessage(Member[] destination, ChannelMessage msg, InterceptorPayload payload)
                throws ChannelException {
            messages.add(msg);
            payloads.add(payload);
        }
    }

    private static InterceptorPayload createPayload() {
        InterceptorPayload payload = new InterceptorPayload();
        payload.setErrorHandler(new ErrorHandler() {
            @Override
            public void handleError(ChannelException x, UniqueId id) {
                // Not used by these tests
            }

            @Override
            public void handleCompletion(UniqueId id) {
                // Not used by these tests
            }
        });
        return payload;
    }

    // Upstream stand-in: records the messages the interceptor releases
    private static class RecordingReceiveChain extends ChannelInterceptorBase {

        private final List<ChannelMessage> messages = Collections.synchronizedList(new ArrayList<>());

        @Override
        public void messageReceived(ChannelMessage msg) {
            messages.add(msg);
        }
    }

    /*
     * Runs a message through a sender-side interceptor and returns the confirmation message
     * the interceptor generated for it.
     */
    private static ChannelMessage buildConfirmation(Member sender, ChannelData original) throws Exception {
        TwoPhaseCommitInterceptor senderInterceptor = new TwoPhaseCommitInterceptor();
        RecordingSendChain chain = new RecordingSendChain();
        GroupChannel channel = new GroupChannel();
        senderInterceptor.setChannel(channel);
        senderInterceptor.setNext(chain);
        senderInterceptor.sendMessage(new Member[] { sender }, original, null);
        return chain.messages.get(1);
    }

    private static ChannelData createMessage(Member source) {
        ChannelData data = new ChannelData(false);
        data.setUniqueId(UUIDGenerator.randomUUID(false));
        data.setAddress(source);
        XByteBuffer buffer = new XByteBuffer(64, false);
        byte[] content = "payload-data".getBytes();
        buffer.append(content, 0, content.length);
        data.setMessage(buffer);
        return data;
    }

    @Test
    public void testPayloadAppliesToOriginalMessageOnly() throws Exception {
        TwoPhaseCommitInterceptor interceptor = new TwoPhaseCommitInterceptor();
        RecordingSendChain chain = new RecordingSendChain();
        GroupChannel channel = new GroupChannel();
        interceptor.setChannel(channel);
        interceptor.setNext(chain);

        MemberImpl member = new MemberImpl("localhost", 4000, -1);
        InterceptorPayload payload = createPayload();
        ChannelData msg = createMessage(member);
        byte[] originalId = msg.getUniqueId();

        interceptor.sendMessage(new Member[] { member }, msg, payload);

        Assert.assertEquals("The original message and the confirmation must both be sent",
                2, chain.messages.size());
        Assert.assertSame("The original message must be sent first", msg, chain.messages.get(0));
        Assert.assertSame("The application payload must be forwarded with the original message",
                payload, chain.payloads.get(0));
        Assert.assertNull("The confirmation must be sent without the application payload",
                chain.payloads.get(1));
        Assert.assertFalse("The confirmation must carry its own id",
                Arrays.equals(originalId, chain.messages.get(1).getUniqueId()));
    }

    @Test
    public void testNullPayloadStaysNull() throws Exception {
        TwoPhaseCommitInterceptor interceptor = new TwoPhaseCommitInterceptor();
        RecordingSendChain chain = new RecordingSendChain();
        GroupChannel channel = new GroupChannel();
        interceptor.setChannel(channel);
        interceptor.setNext(chain);

        MemberImpl member = new MemberImpl("localhost", 4000, -1);
        interceptor.sendMessage(new Member[] { member }, createMessage(member), null);

        Assert.assertEquals(2, chain.messages.size());
        Assert.assertNull(chain.payloads.get(0));
        Assert.assertNull(chain.payloads.get(1));
    }

    @Test
    public void testShallowCloneKeepsOriginalUniqueIdIntact() throws Exception {
        TwoPhaseCommitInterceptor interceptor = new TwoPhaseCommitInterceptor();
        interceptor.setDeepclone(false);
        RecordingSendChain chain = new RecordingSendChain();
        GroupChannel channel = new GroupChannel();
        interceptor.setChannel(channel);
        interceptor.setNext(chain);

        MemberImpl member = new MemberImpl("localhost", 4000, -1);
        ChannelData msg = createMessage(member);
        byte[] originalId = msg.getUniqueId();
        byte[] savedId = originalId.clone();

        interceptor.sendMessage(new Member[] { member }, msg, null);

        Assert.assertEquals(2, chain.messages.size());
        Assert.assertArrayEquals("Building the confirmation must not modify the original id in place",
                savedId, msg.getUniqueId());
        Assert.assertNotSame("The clone must not share the id array with the original",
                originalId, chain.messages.get(1).getUniqueId());
        Assert.assertFalse("The confirmation must carry a different id",
                Arrays.equals(savedId, chain.messages.get(1).getUniqueId()));
    }

    @Test
    public void testConfirmationArrivingBeforeOriginalReleasesLateOriginal() throws Exception {
        MemberImpl member = new MemberImpl("localhost", 4000, -1);
        ChannelData original = createMessage(member);
        ChannelMessage confirmation = buildConfirmation(member, original);

        TwoPhaseCommitInterceptor receiver = new TwoPhaseCommitInterceptor();
        RecordingReceiveChain released = new RecordingReceiveChain();
        receiver.setPrevious(released);

        receiver.messageReceived(confirmation);
        Assert.assertFalse("The confirmation must be held for the original",
                receiver.earlyConfirmations.isEmpty());
        Assert.assertTrue("Nothing may be released before the original arrives",
                released.messages.isEmpty());

        receiver.messageReceived(original);
        Assert.assertEquals("The late original must be released immediately",
                1, released.messages.size());
        Assert.assertTrue("No bookkeeping may remain",
                receiver.messages.isEmpty() && receiver.earlyConfirmations.isEmpty());
    }

    @Test
    public void testOriginalThenConfirmationDeliveredOnce() throws Exception {
        MemberImpl member = new MemberImpl("localhost", 4000, -1);
        ChannelData original = createMessage(member);
        ChannelMessage confirmation = buildConfirmation(member, original);

        TwoPhaseCommitInterceptor receiver = new TwoPhaseCommitInterceptor();
        RecordingReceiveChain released = new RecordingReceiveChain();
        receiver.setPrevious(released);

        receiver.messageReceived(original);
        Assert.assertTrue("The original must be held for the confirmation",
                released.messages.isEmpty());

        receiver.messageReceived(confirmation);
        Assert.assertEquals("The confirmed original must be released",
                1, released.messages.size());
        Assert.assertTrue("No bookkeeping may remain",
                receiver.messages.isEmpty() && receiver.earlyConfirmations.isEmpty());

        // A duplicate confirmation for the same id must not deliver again
        receiver.messageReceived(confirmation);
        Assert.assertEquals(1, released.messages.size());
    }

    @Test
    public void testEarlyConfirmationExpiresWithoutOriginal() throws Exception {
        MemberImpl member = new MemberImpl("localhost", 4000, -1);
        ChannelData original = createMessage(member);
        ChannelMessage confirmation = buildConfirmation(member, original);

        TwoPhaseCommitInterceptor receiver = new TwoPhaseCommitInterceptor();
        receiver.setExpire(50);
        RecordingReceiveChain released = new RecordingReceiveChain();
        receiver.setPrevious(released);

        receiver.messageReceived(confirmation);
        Assert.assertEquals(1, receiver.earlyConfirmations.size());

        Thread.sleep(200);
        receiver.heartbeat();
        Assert.assertTrue("The stale confirmation must be dropped",
                receiver.earlyConfirmations.isEmpty());

        // The original is no longer matched against the expired confirmation
        receiver.messageReceived(original);
        Assert.assertTrue("A message after the expired confirmation must not be released",
                released.messages.isEmpty());
    }

    @Test
    public void testOriginalSendFailureSkipsConfirmation() throws Exception {
        TwoPhaseCommitInterceptor interceptor = new TwoPhaseCommitInterceptor();
        RecordingSendChain chain = new RecordingSendChain() {
            @Override
            public void sendMessage(Member[] destination, ChannelMessage msg, InterceptorPayload payload)
                    throws ChannelException {
                throw new ChannelException("Induced failure");
            }
        };
        GroupChannel channel = new GroupChannel();
        interceptor.setChannel(channel);
        interceptor.setNext(chain);

        MemberImpl member = new MemberImpl("localhost", 4000, -1);
        try {
            interceptor.sendMessage(new Member[] { member }, createMessage(member), createPayload());
            Assert.fail("The send must fail");
        } catch (ChannelException expected) {
            // Expected: the failure of the original send reaches the caller and the
            // error handler of the payload that was forwarded for the original
        }
        Assert.assertEquals("No confirmation may be sent after the original failed",
                0, chain.messages.size());
    }
}
