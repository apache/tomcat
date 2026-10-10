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

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.tribes.Channel;
import org.apache.catalina.tribes.ChannelException;
import org.apache.catalina.tribes.ErrorHandler;
import org.apache.catalina.tribes.Member;
import org.apache.catalina.tribes.UniqueId;
import org.apache.catalina.tribes.membership.MemberImpl;

public class TestRpcChannel {

    private static class RecordingChannel extends GroupChannel {

        private final List<Serializable> sent = new ArrayList<>();

        @Override
        public UniqueId send(Member[] destination, Serializable msg, int options) throws ChannelException {
            sent.add(msg);
            return null;
        }

        @Override
        public UniqueId send(Member[] destination, Serializable msg, int options, ErrorHandler handler)
                throws ChannelException {
            sent.add(msg);
            return null;
        }
    }

    private static class FixedCallback implements RpcCallback {

        private final Serializable reply;

        FixedCallback(Serializable reply) {
            this.reply = reply;
        }

        @Override
        public Serializable replyRequest(Serializable msg, Member sender) {
            return reply;
        }

        @Override
        public void leftOver(Serializable msg, Member sender) {
            // NO-OP
        }
    }

    private RpcChannel createRpcChannel(RecordingChannel channel, RpcCallback callback) {
        return new RpcChannel(new byte[] { 1 }, channel, callback);
    }

    @Test
    public void testNullReplySendsNothing() throws Exception {
        RecordingChannel channel = new RecordingChannel();
        RpcChannel rpcChannel = createRpcChannel(channel, new FixedCallback(null));
        Member sender = new MemberImpl("localhost", 4000, -1);
        RpcMessage request =
                new RpcMessage(rpcChannel.getRpcId(), "uuid".getBytes(StandardCharsets.UTF_8), "REQUEST");

        rpcChannel.messageReceived(request, sender);

        Assert.assertTrue("A null reply means no reply should be sent", channel.sent.isEmpty());
    }

    private static class RecordingExtendedCallback extends FixedCallback implements ExtendedRpcCallback {

        private volatile int succeededCount;
        private volatile Serializable succeededRequest;
        private volatile Serializable succeededResponse;

        RecordingExtendedCallback(Serializable reply) {
            super(reply);
        }

        @Override
        public void replyFailed(Serializable request, Serializable response, Member sender, Exception reason) {
            // NO-OP
        }

        @Override
        public void replySucceeded(Serializable request, Serializable response, Member sender) {
            succeededRequest = request;
            succeededResponse = response;
            succeededCount++;
        }
    }

    @Test
    public void testNullReplySynchronousExtendedCallbackNotified() throws Exception {
        RecordingChannel channel = new RecordingChannel();
        RecordingExtendedCallback callback = new RecordingExtendedCallback(null);
        RpcChannel rpcChannel = createRpcChannel(channel, callback);
        Member sender = new MemberImpl("localhost", 4000, -1);
        RpcMessage request =
                new RpcMessage(rpcChannel.getRpcId(), "uuid".getBytes(StandardCharsets.UTF_8), "REQUEST");

        rpcChannel.messageReceived(request, sender);

        Assert.assertTrue(channel.sent.isEmpty());
        Assert.assertEquals(1, callback.succeededCount);
        Assert.assertSame(request, callback.succeededRequest);
        Assert.assertNull(callback.succeededResponse);
    }

    @Test
    public void testNullReplyAsynchronousExtendedCallbackNotNotified() throws Exception {
        RecordingChannel channel = new RecordingChannel();
        RecordingExtendedCallback callback = new RecordingExtendedCallback(null);
        RpcChannel rpcChannel = createRpcChannel(channel, callback);
        rpcChannel.setReplyMessageOptions(Channel.SEND_OPTIONS_ASYNCHRONOUS);
        Member sender = new MemberImpl("localhost", 4000, -1);
        RpcMessage request =
                new RpcMessage(rpcChannel.getRpcId(), "uuid".getBytes(StandardCharsets.UTF_8), "REQUEST");

        rpcChannel.messageReceived(request, sender);

        Assert.assertTrue(channel.sent.isEmpty());
        Assert.assertEquals(0, callback.succeededCount);
    }

    @Test
    public void testNonNullReplyIsSent() throws Exception {
        RecordingChannel channel = new RecordingChannel();
        RpcChannel rpcChannel = createRpcChannel(channel, new FixedCallback("RESPONSE"));
        Member sender = new MemberImpl("localhost", 4000, -1);
        RpcMessage request =
                new RpcMessage(rpcChannel.getRpcId(), "uuid".getBytes(StandardCharsets.UTF_8), "REQUEST");

        rpcChannel.messageReceived(request, sender);

        Assert.assertEquals(1, channel.sent.size());
        Object sent = channel.sent.get(0);
        Assert.assertTrue(sent instanceof RpcMessage);
        RpcMessage reply = (RpcMessage) sent;
        Assert.assertTrue(reply.reply);
        Assert.assertEquals("RESPONSE", reply.message);
    }
}
