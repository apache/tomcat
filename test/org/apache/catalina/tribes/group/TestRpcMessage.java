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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.charset.StandardCharsets;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.tribes.group.RpcMessage.NoRpcChannelReply;

public class TestRpcMessage {

    private static Object roundTrip(Object original) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(bytes)) {
            oos.writeObject(original);
        }
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return ois.readObject();
        }
    }

    @Test
    public void testRoundTrip() throws Exception {
        RpcMessage original = new RpcMessage(new byte[] { 1 }, new byte[] { 2 }, "PAYLOAD");
        RpcMessage copy = (RpcMessage) roundTrip(original);
        Assert.assertArrayEquals(new byte[] { 1 }, copy.rpcId);
        Assert.assertArrayEquals(new byte[] { 2 }, copy.uuid);
        Assert.assertEquals("PAYLOAD", copy.message);
        Assert.assertFalse(copy.reply);
    }

    @Test
    public void testNullArraysDoNotBreakSerialization() throws Exception {
        RpcMessage original = new RpcMessage(null, null, "PAYLOAD");
        RpcMessage copy = (RpcMessage) roundTrip(original);
        Assert.assertEquals(0, copy.rpcId.length);
        Assert.assertEquals(0, copy.uuid.length);
        Assert.assertEquals("PAYLOAD", copy.message);
    }

    @Test
    public void testMixedNullArraysDoNotBreakSerialization() throws Exception {
        RpcMessage original = new RpcMessage(new byte[] { 1 }, null, "PAYLOAD");
        RpcMessage copy = (RpcMessage) roundTrip(original);
        Assert.assertArrayEquals(new byte[] { 1 }, copy.rpcId);
        Assert.assertEquals(0, copy.uuid.length);
        Assert.assertEquals("PAYLOAD", copy.message);
    }

    @Test
    public void testNoRpcChannelReplyNullArraysDoNotBreakSerialization() throws Exception {
        NoRpcChannelReply original = new NoRpcChannelReply(null, null);
        NoRpcChannelReply copy = (NoRpcChannelReply) roundTrip(original);
        Assert.assertEquals(0, copy.rpcId.length);
        Assert.assertEquals(0, copy.uuid.length);
        Assert.assertTrue(copy.reply);
    }

    @Test
    public void testNoRpcChannelReplyRoundTrip() throws Exception {
        byte[] id = "id".getBytes(StandardCharsets.UTF_8);
        NoRpcChannelReply original = new NoRpcChannelReply(id, id);
        NoRpcChannelReply copy = (NoRpcChannelReply) roundTrip(original);
        Assert.assertArrayEquals(id, copy.rpcId);
        Assert.assertArrayEquals(id, copy.uuid);
        Assert.assertTrue(copy.reply);
    }
}
