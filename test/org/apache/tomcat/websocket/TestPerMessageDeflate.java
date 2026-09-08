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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.zip.Deflater;

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

        PerMessageDeflate perMessageDeflate = PerMessageDeflate.build(preferences, true);
        perMessageDeflate.setNext(new TesterTransformation());

        ByteBuffer bb1 = ByteBuffer.wrap("A".getBytes(StandardCharsets.UTF_8));
        MessagePart mp1 = new MessagePart(true, 0, Constants.OPCODE_TEXT, bb1, null, null, false, Long.MAX_VALUE);

        List<MessagePart> uncompressedParts1 = new ArrayList<>();
        uncompressedParts1.add(mp1);
        perMessageDeflate.sendMessagePart(uncompressedParts1);

        ByteBuffer bb2 = ByteBuffer.wrap("".getBytes(StandardCharsets.UTF_8));
        MessagePart mp2 = new MessagePart(true, 0, Constants.OPCODE_TEXT, bb2, null, null, false, Long.MAX_VALUE);

        List<MessagePart> uncompressedParts2 = new ArrayList<>();
        uncompressedParts2.add(mp2);
        perMessageDeflate.sendMessagePart(uncompressedParts2);
    }


    /*
     * https://bz.apache.org/bugzilla/show_bug.cgi?id=65317
     */
    @Test
    public void testMessagePartThatFillsBuffer() throws IOException {

        // Set up the extension using defaults
        List<Parameter> parameters = Collections.emptyList();
        List<List<Parameter>> preferences = new ArrayList<>();
        preferences.add(parameters);

        // Set up the compression and sending of the message.
        PerMessageDeflate perMessageDeflateTx = PerMessageDeflate.build(preferences, true);
        perMessageDeflateTx.setNext(new TesterTransformation());

        byte[] data = new byte[8192];

        ByteBuffer bb = ByteBuffer.wrap(data);
        long writeTimeoutExpiry = System.currentTimeMillis() + 10000;
        MessagePart mp = new MessagePart(true, 0, Constants.OPCODE_BINARY, bb, null, null, true, writeTimeoutExpiry);

        List<MessagePart> uncompressedParts = new ArrayList<>();
        uncompressedParts.add(mp);
        List<MessagePart> compressedParts = perMessageDeflateTx.sendMessagePart(uncompressedParts);

        MessagePart compressedPart = compressedParts.get(0);
        Assert.assertTrue(compressedPart.isBlocking());
        Assert.assertEquals(writeTimeoutExpiry, compressedPart.getWriteTimeoutExpiry());

        // Set up the decompression and process the received message
        PerMessageDeflate perMessageDeflateRx = PerMessageDeflate.build(preferences, true);
        perMessageDeflateRx.setNext(new TesterTransformation(compressedPart.getPayload()));

        ByteBuffer received = ByteBuffer.allocate(8192);

        TransformationResult tr = perMessageDeflateRx.getMoreData(compressedPart.getOpCode(), compressedPart.isFin(),
                compressedPart.getRsv(), received);

        Assert.assertEquals(8192, received.position());
        Assert.assertEquals(TransformationResult.END_OF_FRAME, tr);
    }


    @Test
    public void testMessagePartThatOverfillsBuffer() throws IOException {

        List<Parameter> parameters = Collections.emptyList();
        List<List<Parameter>> preferences = new ArrayList<>();
        preferences.add(parameters);

        List<String> truncated = new ArrayList<>();

        for (int size = 8192 + 1; size <= 8192 + 512; size++) {

            // Compress `size` identical (highly compressible) bytes as one binary message.
            PerMessageDeflate perMessageDeflateTx = PerMessageDeflate.build(preferences, true);
            perMessageDeflateTx.setNext(new TesterTransformation());
            byte[] data = new byte[size];
            Arrays.fill(data, (byte) 0x80);
            List<MessagePart> uncompressedParts = new ArrayList<>();
            uncompressedParts.add(new MessagePart(true, 0, Constants.OPCODE_BINARY, ByteBuffer.wrap(data), null, null,
                    false, Long.MAX_VALUE));
            MessagePart compressedPart = perMessageDeflateTx.sendMessagePart(uncompressedParts).get(0);

            // Decompress the way WsFrameBase.processDataBinary does: fill an 8192
            // byte buffer, loop on OVERFLOW, stop on END_OF_FRAME.
            PerMessageDeflate perMessageDeflateRx = PerMessageDeflate.build(preferences, true);
            perMessageDeflateRx.setNext(new TesterTransformation(compressedPart.getPayload()));

            int total = 0;
            ByteBuffer received = ByteBuffer.allocate(8192);
            TransformationResult tr;
            do {
                tr = perMessageDeflateRx.getMoreData(compressedPart.getOpCode(), compressedPart.isFin(),
                        compressedPart.getRsv(), received);
                total += received.position();
                received.clear();
            } while (tr == TransformationResult.OVERFLOW);

            if (total != size) {
                truncated.add("size=" + size + " recovered=" + total);
            }
        }

        Assert.assertTrue("permessage-deflate dropped trailing bytes for: " + truncated, truncated.isEmpty());
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
        PerMessageDeflate perMessageDeflateTx = PerMessageDeflate.build(preferences, true);
        perMessageDeflateTx.setNext(new TesterTransformation());

        List<MessagePart> uncompressedParts = new ArrayList<>();

        // First message part
        byte[] data = new byte[1024];
        ByteBuffer bb = ByteBuffer.wrap(data);
        MessagePart mp1 = new MessagePart(true, 0, Constants.OPCODE_BINARY, bb, null, null, false, Long.MAX_VALUE);
        uncompressedParts.add(mp1);

        // Flush message (replicates result of calling flushBatch()
        MessagePart mp2 =
                new MessagePart(true, 0, Constants.INTERNAL_OPCODE_FLUSH, null, null, null, false, Long.MAX_VALUE);
        uncompressedParts.add(mp2);

        List<MessagePart> compressedParts = perMessageDeflateTx.sendMessagePart(uncompressedParts);

        Assert.assertEquals(2, compressedParts.size());

        // Check the first compressed part
        MessagePart compressedPart1 = compressedParts.get(0);

        // Set up the decompression and process the received message
        PerMessageDeflate perMessageDeflateRx = PerMessageDeflate.build(preferences, true);
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
     * RFC 7692 section 7.2.1 explicitly permits an endpoint to compress a single message using multiple DEFLATE blocks,
     * of any type, with any mix of BFINAL values - including multiple, independently terminated (BFINAL=1) DEFLATE
     * blocks/streams concatenated together. This is not something PerMessageDeflate's own compressor produces (it
     * always uses a single, sync-flush terminated stream), so the payload has to be constructed by hand to reproduce
     * it. A compliant receiver must still recover the full, correctly ordered, decompressed content of the message.
     */
    @Test
    public void testMultipleFinalDeflateBlocksInOneMessage() throws IOException {

        byte[] part1 = "Hello, this is the FIRST part of the message.".getBytes(StandardCharsets.UTF_8);
        byte[] part2 = "And this is the SECOND, independently terminated part.".getBytes(StandardCharsets.UTF_8);

        // Two separate raw DEFLATE streams, each terminated with BFINAL=1, concatenated together.
        byte[] compressedPayload = concat(rawDeflateFinished(part1), rawDeflateFinished(part2));

        List<Parameter> parameters = Collections.emptyList();
        List<List<Parameter>> preferences = new ArrayList<>();
        preferences.add(parameters);

        PerMessageDeflate perMessageDeflateRx = PerMessageDeflate.build(preferences, true);
        perMessageDeflateRx.setNext(new TesterTransformation(ByteBuffer.wrap(compressedPayload)));

        // RSV1 (the permessage-deflate compression bit) set, RSV2/RSV3 clear.
        int rsv = 0b100;

        ByteArrayOutputStream received = new ByteArrayOutputStream();
        ByteBuffer buf = ByteBuffer.allocate(8192);
        TransformationResult tr;
        do {
            buf.clear();
            tr = perMessageDeflateRx.getMoreData(Constants.OPCODE_BINARY, true, rsv, buf);
            received.write(buf.array(), 0, buf.position());
        } while (tr == TransformationResult.OVERFLOW);

        Assert.assertEquals(TransformationResult.END_OF_FRAME, tr);

        byte[] expected = concat(part1, part2);

        Assert.assertArrayEquals("Expected the concatenation of both DEFLATE blocks' decompressed content, got: " +
                received.size() + " bytes: [" + received.toString("UTF-8") + "]", expected, received.toByteArray());
    }


    /*
     * As testMultipleFinalDeflateBlocksInOneMessage, but with three independently terminated (BFINAL=1) DEFLATE blocks
     * concatenated together. This requires two recoveries in getMoreData() back to back, with no intervening
     * readBuffer.clear()-based fetch between them - which is what exposes an offset bug that a single-recovery
     * (two-block) message cannot: computing the second recovery's offset from readBuffer.arrayOffset() (fixed) rather
     * than from where the first recovery's input segment actually started.
     */
    @Test
    public void testThreeFinalDeflateBlocksInOneMessage() throws IOException {

        byte[] part1 = "First part.".getBytes(StandardCharsets.UTF_8);
        byte[] part2 = "Second, somewhat longer part.".getBytes(StandardCharsets.UTF_8);
        byte[] part3 = "Third and final part.".getBytes(StandardCharsets.UTF_8);

        byte[] compressedPayload =
                concat(concat(rawDeflateFinished(part1), rawDeflateFinished(part2)), rawDeflateFinished(part3));

        List<Parameter> parameters = Collections.emptyList();
        List<List<Parameter>> preferences = new ArrayList<>();
        preferences.add(parameters);

        PerMessageDeflate perMessageDeflateRx = PerMessageDeflate.build(preferences, true);
        perMessageDeflateRx.setNext(new TesterTransformation(ByteBuffer.wrap(compressedPayload)));

        int rsv = 0b100;

        ByteArrayOutputStream received = new ByteArrayOutputStream();
        ByteBuffer buf = ByteBuffer.allocate(8192);
        TransformationResult tr;
        do {
            buf.clear();
            tr = perMessageDeflateRx.getMoreData(Constants.OPCODE_BINARY, true, rsv, buf);
            received.write(buf.array(), 0, buf.position());
        } while (tr == TransformationResult.OVERFLOW);

        Assert.assertEquals(TransformationResult.END_OF_FRAME, tr);

        byte[] expected = concat(concat(part1, part2), part3);

        Assert.assertArrayEquals(
                "Expected the concatenation of all three DEFLATE blocks' decompressed content, " + "got: " +
                        received.size() + " bytes: [" + received.toString("UTF-8") + "]",
                expected, received.toByteArray());
    }


    /*
     * A message whose real content is a single, exactly-fitting, independently BFINAL=1 terminated block (nothing
     * else) finishes cleanly via the normal needsInput()==true path and, as part of that, has the RFC 7692 section
     * 7.2.2 EOM_BYTES fed to an already-finished Inflater - which silently ignores them, leaving finished()==true
     * with those 4 bytes stuck in getRemaining(). With context takeover enabled, endFrame() must not leave the
     * Inflater in that state: otherwise the next message's recovery logic computes its first offset from this
     * stale, unrelated leftover count.
     */
    @Test
    public void testMessageEndingInCleanBfinalBlockDoesNotPoisonNextMessage() throws IOException {
        byte[] message1 = "First message, a single clean BFINAL block.".getBytes(StandardCharsets.UTF_8);
        byte[] compressed1 = rawDeflateFinished(message1);

        List<Parameter> parameters = Collections.emptyList();
        List<List<Parameter>> preferences = new ArrayList<>();
        preferences.add(parameters);

        /*
         *  Context takeover is enabled by default (no *_no_context_takeover parameter) - the same PerMessageDeflate
        * instance, and therefore the same Inflater, must be reused across messages, exactly as it would be for a
        * real connection. setNext() on PerMessageDeflate delegates to the existing next's setNext() once next is
        * already set, so a single mutable source (rather than two separate TesterTransformation instances) is used
        * to supply both messages' bytes in turn.
        */
        PerMessageDeflate perMessageDeflateRx = PerMessageDeflate.build(preferences, true);
        MutableTesterTransformation source = new MutableTesterTransformation(ByteBuffer.wrap(compressed1));
        perMessageDeflateRx.setNext(source);
        int rsv = 0b100;

        ByteArrayOutputStream received1 = new ByteArrayOutputStream();
        ByteBuffer buf = ByteBuffer.allocate(8192);
        TransformationResult tr;
        do {
            buf.clear();
            tr = perMessageDeflateRx.getMoreData(Constants.OPCODE_BINARY, true, rsv, buf);
            received1.write(buf.array(), 0, buf.position());
        } while (tr == TransformationResult.OVERFLOW);
        Assert.assertEquals(TransformationResult.END_OF_FRAME, tr);
        Assert.assertArrayEquals(message1, received1.toByteArray());

        // A completely separate, ordinary message on the same connection/Transformation instance.
        byte[] message2 = "Second message on the same connection.".getBytes(StandardCharsets.UTF_8);
        byte[] compressed2 = rawDeflateFinished(message2);
        source.data = ByteBuffer.wrap(compressed2);
        source.delivered = false;

        ByteArrayOutputStream received2 = new ByteArrayOutputStream();
        do {
            buf.clear();
            tr = perMessageDeflateRx.getMoreData(Constants.OPCODE_BINARY, true, rsv, buf);
            received2.write(buf.array(), 0, buf.position());
        } while (tr == TransformationResult.OVERFLOW);
        Assert.assertEquals(TransformationResult.END_OF_FRAME, tr);
        Assert.assertArrayEquals("Second message must decompress correctly; the first message's clean BFINAL "
                + "ending must not poison the shared Inflater's state", message2, received2.toByteArray());
    }


    private static byte[] rawDeflateFinished(byte[] data) {
        @SuppressWarnings("resource") // False positive
        Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION, true);
        try {
            deflater.setInput(data);
            deflater.finish();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            while (!deflater.finished()) {
                int n = deflater.deflate(buf);
                baos.write(buf, 0, n);
            }
            return baos.toByteArray();
        } finally {
            deflater.end();
        }
    }


    private static byte[] concat(byte[] a, byte[] b) {
        byte[] result = new byte[a.length + b.length];
        System.arraycopy(a, 0, result, 0, a.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }


    /*
     * RFC 7692 section 5.1 requires a permessage-deflate offer that contains an invalid extension parameter to be
     * declined so the handshake continues without compression. The offer must not fail the handshake.
     */
    @Test
    public void testInvalidParameterDeclinesOffer() {
        // client_max_window_bits with an out-of-range value
        assertDeclined("client_max_window_bits", "16");
        // client_max_window_bits with a non-numeric value
        assertDeclined("client_max_window_bits", "x");
        // server_max_window_bits offered without a value
        assertDeclined("server_max_window_bits", null);
    }


    private static void assertDeclined(String name, String value) {
        List<Parameter> parameters = new ArrayList<>();
        parameters.add(new WsExtensionParameter(name, value));
        List<List<Parameter>> preferences = new ArrayList<>();
        preferences.add(parameters);

        Assert.assertNull(PerMessageDeflate.build(preferences, true));
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


    /*
     * Like TesterTransformation, but the source ByteBuffer can be swapped out between messages, to exercise reuse of
     * a single PerMessageDeflate instance (and therefore its Inflater) across multiple messages, as happens on a
     * real connection with context takeover enabled.
     */
    private static class MutableTesterTransformation implements Transformation {

        ByteBuffer data;
        boolean delivered;

        MutableTesterTransformation(ByteBuffer data) {
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
        public TransformationResult getMoreData(byte opCode, boolean fin, int rsv, ByteBuffer dest) {
            if (delivered) {
                return TransformationResult.END_OF_FRAME;
            }
            dest.put(data);
            delivered = true;
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
