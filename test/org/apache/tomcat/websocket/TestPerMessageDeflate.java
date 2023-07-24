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
package org.apache.tomcat.websocket;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import jakarta.websocket.Extension;
import jakarta.websocket.Extension.Parameter;

import org.junit.Assert;
import org.junit.Test;

public class TestPerMessageDeflate {

    /*
     * https://bz.apache.org/bugzilla/show_bug.cgi?id=61491
     */
    @Test
    public void testSendEmptyMessagePartWithContextTakeover() throws IOException {

        // Set up the extension using defaults
        List<Parameter> parameters = Collections.emptyList();
        List<List<Parameter>> preferences = new ArrayList<>();
        preferences.add(parameters);

        PerMessageDeflate perMessageDeflate = PerMessageDeflate.negotiate(preferences, true);
        perMessageDeflate.setNext(new TesterTransformation());

        ByteBuffer bb1 = ByteBuffer.wrap("A".getBytes(StandardCharsets.UTF_8));
        MessagePart mp1 = new MessagePart(true, 0, Constants.OPCODE_TEXT, bb1, null, null, -1);

        List<MessagePart> uncompressedParts1 = new ArrayList<>();
        uncompressedParts1.add(mp1);
        perMessageDeflate.sendMessagePart(uncompressedParts1);

        ByteBuffer bb2 = ByteBuffer.wrap("".getBytes(StandardCharsets.UTF_8));
        MessagePart mp2 = new MessagePart(true, 0, Constants.OPCODE_TEXT, bb2, null, null, -1);

        List<MessagePart> uncompressedParts2 = new ArrayList<>();
        uncompressedParts2.add(mp2);
        perMessageDeflate.sendMessagePart(uncompressedParts2);
    }


    /*
     * https://bz.apache.org/bugzilla/show_bug.cgi?id=65317
     */
    @Test
    public void testMessagePartThatFillsBufffer() throws IOException {

        // Set up the extension using defaults
        List<Parameter> parameters = Collections.emptyList();
        List<List<Parameter>> preferences = new ArrayList<>();
        preferences.add(parameters);

        // Set up the compression and sending of the message.
        PerMessageDeflate perMessageDeflateTx = PerMessageDeflate.negotiate(preferences, true);
        perMessageDeflateTx.setNext(new TesterTransformation());

        byte[] data = new byte[8192];

        ByteBuffer bb = ByteBuffer.wrap(data);
        MessagePart mp = new MessagePart(true, 0, Constants.OPCODE_BINARY, bb, null, null, -1);

        List<MessagePart> uncompressedParts = new ArrayList<>();
        uncompressedParts.add(mp);
        List<MessagePart> compressedParts = perMessageDeflateTx.sendMessagePart(uncompressedParts);

        MessagePart compressedPart = compressedParts.get(0);

        // Set up the decompression and process the received message
        PerMessageDeflate perMessageDeflateRx = PerMessageDeflate.negotiate(preferences, true);
        perMessageDeflateRx.setNext(new TesterTransformation(compressedPart.getPayload()));

        ByteBuffer received = ByteBuffer.allocate(8192);

        TransformationResult tr = perMessageDeflateRx.getMoreData(compressedPart.getOpCode(), compressedPart.isFin(),
                compressedPart.getRsv(), received);

        Assert.assertEquals(8192, received.position());
        Assert.assertEquals(TransformationResult.END_OF_FRAME, tr);
    }


    /*
     * https://bz.apache.org/bugzilla/show_bug.cgi?id=66681
     */
    @Test
    public void testFlushBatchMessagePart() throws IOException {
        // Set up the extension using defaults
        List<Parameter> parameters = Collections.emptyList();
        List<List<Parameter>> preferences = new ArrayList<>();
        preferences.add(parameters);

        // Set up the compression and sending of the message.
        PerMessageDeflate perMessageDeflateTx = PerMessageDeflate.negotiate(preferences, true);
        perMessageDeflateTx.setNext(new TesterTransformation());

        List<MessagePart> uncompressedParts = new ArrayList<>();

        // First message part
        byte[] data = new byte[1024];
        ByteBuffer bb = ByteBuffer.wrap(data);
        MessagePart mp1 = new MessagePart(true, 0, Constants.OPCODE_BINARY, bb, null, null, -1);
        uncompressedParts.add(mp1);

        // Flush message (replicates result of calling flushBatch()
        MessagePart mp2 = new MessagePart(true, 0, Constants.INTERNAL_OPCODE_FLUSH, null, null, null, -1);
        uncompressedParts.add(mp2);

        List<MessagePart> compressedParts = perMessageDeflateTx.sendMessagePart(uncompressedParts);

        Assert.assertEquals(2,  compressedParts.size());

        // Check the first compressed part
        MessagePart compressedPart1 = compressedParts.get(0);

        // Set up the decompression and process the received message
        PerMessageDeflate perMessageDeflateRx = PerMessageDeflate.negotiate(preferences, true);
        perMessageDeflateRx.setNext(new TesterTransformation(compressedPart1.getPayload()));

        ByteBuffer received = ByteBuffer.allocate(8192);

        TransformationResult tr = perMessageDeflateRx.getMoreData(compressedPart1.getOpCode(), compressedPart1.isFin(),
                compressedPart1.getRsv(), received);

        Assert.assertEquals(1024, received.position());
        Assert.assertEquals(TransformationResult.END_OF_FRAME, tr);

        // Check the second compressed part (should be passed through unchanged)
        Assert.assertEquals(mp2, compressedParts.get(1));
    }

    /*
     * Minimal implementation to enable other transformations to be tested. It is NOT robust.
     */
    private static class TesterTransformation implements Transformation {

        final ByteBuffer data;

        TesterTransformation() {
            this(null);
        }

        TesterTransformation(ByteBuffer data) {
            this.data = data;
        }

        @Override
        public boolean validateRsvBits(int i) {
            return false;
        }

        @Override
        public boolean validateRsv(int rsv, byte opCode) {
            return false;
        }

        @Override
        public void setNext(Transformation t) {
        }

        @Override
        public List<MessagePart> sendMessagePart(List<MessagePart> messageParts) {
            return messageParts;
        }

        @Override
        public TransformationResult getMoreData(byte opCode, boolean fin, int rsv, ByteBuffer dest) throws IOException {
            if (data == null) {
                return TransformationResult.UNDERFLOW;
            }
            dest.put(data);
            return TransformationResult.END_OF_FRAME;
        }

        @Override
        public Extension getExtensionResponse() {
            return null;
        }

        @Override
        public void close() {
        }
    }
}
