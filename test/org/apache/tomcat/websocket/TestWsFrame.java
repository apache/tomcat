/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.tomcat.websocket;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.zip.Deflater;

import javax.websocket.Extension.Parameter;
import javax.websocket.MessageHandler;
import javax.websocket.RemoteEndpoint;

import org.junit.Assert;
import org.junit.Test;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.easymock.EasyMock;

public class TestWsFrame {

    @Test
    public void testByteArrayToLong() throws IOException {
        Assert.assertEquals(0L, WsFrameBase.byteArrayToLong(new byte[] { 0 }, 0, 1));
        Assert.assertEquals(1L, WsFrameBase.byteArrayToLong(new byte[] { 1 }, 0, 1));
        Assert.assertEquals(0xFF, WsFrameBase.byteArrayToLong(new byte[] { -1 }, 0, 1));
        Assert.assertEquals(0xFFFF, WsFrameBase.byteArrayToLong(new byte[] { -1, -1 }, 0, 2));
        Assert.assertEquals(0xFFFFFF, WsFrameBase.byteArrayToLong(new byte[] { -1, -1, -1 }, 0, 3));
        Assert.assertEquals(0xFFFFFFFFL, WsFrameBase.byteArrayToLong(new byte[] { -1, -1, -1, -1 }, 0, 4));
        Assert.assertEquals(0xFFFFFFFFFFL, WsFrameBase.byteArrayToLong(new byte[] { -1, -1, -1, -1, -1 }, 0, 5));
        Assert.assertEquals(0xFFFFFFFFFFFFL, WsFrameBase.byteArrayToLong(new byte[] { -1, -1, -1, -1, -1, -1 }, 0, 6));
        Assert.assertEquals(0xFFFFFFFFFFFFFFL,
                WsFrameBase.byteArrayToLong(new byte[] { -1, -1, -1, -1, -1, -1, -1 }, 0, 7));
        Assert.assertEquals(0x7FFFFFFFFFFFFFFFL,
                WsFrameBase.byteArrayToLong(new byte[] { 127, -1, -1, -1, -1, -1, -1, -1 }, 0, 8));
        Assert.assertEquals(-1, WsFrameBase.byteArrayToLong(new byte[] { -1, -1, -1, -1, -1, -1, -1, -1 }, 0, 8));
    }


    @Test
    public void testByteArrayToLongOffset() throws IOException {
        Assert.assertEquals(0L, WsFrameBase.byteArrayToLong(new byte[] { 20, 0 }, 1, 1));
        Assert.assertEquals(1L, WsFrameBase.byteArrayToLong(new byte[] { 20, 1 }, 1, 1));
        Assert.assertEquals(0xFF, WsFrameBase.byteArrayToLong(new byte[] { 20, -1 }, 1, 1));
        Assert.assertEquals(0xFFFF, WsFrameBase.byteArrayToLong(new byte[] { 20, -1, -1 }, 1, 2));
        Assert.assertEquals(0xFFFFFF, WsFrameBase.byteArrayToLong(new byte[] { 20, -1, -1, -1 }, 1, 3));
        Assert.assertEquals(0xFFFFFFFFL, WsFrameBase.byteArrayToLong(new byte[] { 20, -1, -1, -1, -1 }, 1, 4));
        Assert.assertEquals(0xFFFFFFFFFFL, WsFrameBase.byteArrayToLong(new byte[] { 20, -1, -1, -1, -1, -1 }, 1, 5));
        Assert.assertEquals(0xFFFFFFFFFFFFL,
                WsFrameBase.byteArrayToLong(new byte[] { 20, -1, -1, -1, -1, -1, -1 }, 1, 6));
        Assert.assertEquals(0xFFFFFFFFFFFFFFL,
                WsFrameBase.byteArrayToLong(new byte[] { 20, -1, -1, -1, -1, -1, -1, -1 }, 1, 7));
        Assert.assertEquals(0x7FFFFFFFFFFFFFFFL,
                WsFrameBase.byteArrayToLong(new byte[] { 20, 127, -1, -1, -1, -1, -1, -1, -1 }, 1, 8));
        Assert.assertEquals(-1, WsFrameBase.byteArrayToLong(new byte[] { 20, -1, -1, -1, -1, -1, -1, -1, -1 }, 1, 8));
    }


    @Test
    public void testAutomaticPongAfterCloseStarted() throws Exception {
        doTestAutomaticPongFailure(new IllegalStateException(), true, true);
    }


    @Test
    public void testAutomaticPongISEWhileOpen() throws Exception {
        doTestAutomaticPongFailure(new IllegalStateException(), false, false);
    }


    private static void doTestAutomaticPongFailure(Exception failure, boolean closing, boolean swallowed)
            throws Exception {
        WsSession wsSession = EasyMock.createNiceMock(WsSession.class);
        RemoteEndpoint.Basic basicRemote = EasyMock.createMock(RemoteEndpoint.Basic.class);
        EasyMock.expect(Boolean.valueOf(wsSession.isOpen())).andReturn(Boolean.TRUE);
        EasyMock.expect(wsSession.getBasicRemote()).andReturn(basicRemote);
        basicRemote.sendPong(EasyMock.anyObject(ByteBuffer.class));
        EasyMock.expectLastCall().andThrow(failure);
        EasyMock.expect(Boolean.valueOf(wsSession.isClosing())).andStubReturn(Boolean.valueOf(closing));
        EasyMock.replay(wsSession, basicRemote);

        TestFrame frame = new TestFrame(wsSession);
        if (swallowed) {
            frame.processPing();
        } else {
            try {
                frame.processPing();
                Assert.fail();
            } catch (Exception actual) {
                Assert.assertSame(failure, actual);
            }
        }

        EasyMock.verify(wsSession, basicRemote);
    }


    /*
     * With permessage-deflate and context takeover, the LZ77 window includes all previous messages. A compressed
     * message for which there is no message handler must still be decompressed (and discarded) so that the window
     * is correct for the messages that follow.
     */
    @Test
    public void testCompressedMessageWithoutHandlerKeepsWindowBinary() throws Exception {
        doTestCompressedMessageWithoutHandler(Constants.OPCODE_BINARY, true);
    }


    @Test
    public void testUncompressedMessageWithoutHandler() throws Exception {
        doTestCompressedMessageWithoutHandler(Constants.OPCODE_BINARY, false);
    }


    private static void doTestCompressedMessageWithoutHandler(byte skippedOpCode, boolean compressSkipped)
            throws Exception {
        String repeated = "THE QUICK BROWN FOX JUMPS OVER THE LAZY DOG. ";
        byte[] skipped = (repeated + repeated + "first").getBytes(StandardCharsets.UTF_8);
        String expected = repeated + repeated + "second";

        // Compress with a single Deflater so the second message can refer back to the first
        Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION, true);
        byte[] skippedFrame;
        byte[] textFrame;
        try {
            if (compressSkipped) {
                skippedFrame = createFrame(skippedOpCode, true, deflateMessage(deflater, skipped));
            } else {
                // Uncompressed messages are not part of the LZ77 window
                skippedFrame = createFrame(skippedOpCode, false, skipped);
            }
            byte[] second = expected.getBytes(StandardCharsets.UTF_8);
            byte[] compressedSecond = deflateMessage(deflater, second);
            if (compressSkipped) {
                // Check the test is exercising back-references into the first message
                Assert.assertTrue(compressedSecond.length < second.length / 3);
            }
            textFrame = createFrame(Constants.OPCODE_TEXT, true, compressedSecond);
        } finally {
            deflater.end();
        }

        List<List<Parameter>> preferences = new ArrayList<>();
        preferences.add(Collections.emptyList());
        // Receiving as a client so the frames do not need to be masked
        PerMessageDeflate perMessageDeflate = PerMessageDeflate.build(preferences, false);
        Assert.assertNotNull(perMessageDeflate);

        List<String> received = new ArrayList<>();
        MessageHandler.Whole<String> textHandler = received::add;

        WsSession wsSession = EasyMock.createNiceMock(WsSession.class);
        EasyMock.expect(Integer.valueOf(wsSession.getMaxBinaryMessageBufferSize())).andStubReturn(Integer.valueOf(8192));
        EasyMock.expect(Integer.valueOf(wsSession.getMaxTextMessageBufferSize())).andStubReturn(Integer.valueOf(8192));
        EasyMock.expect(wsSession.getBinaryMessageHandler()).andStubReturn(null);
        EasyMock.expect(wsSession.getTextMessageHandler()).andStubReturn(textHandler);
        EasyMock.replay(wsSession);

        TestFrame frame = new TestFrame(wsSession, perMessageDeflate);
        frame.process(skippedFrame);
        Assert.assertTrue("No handler so no message expected", received.isEmpty());
        frame.process(textFrame);

        Assert.assertEquals(1, received.size());
        Assert.assertEquals(expected, received.get(0));
    }


    /*
     * The skipped message is fragmented over several frames and the data is delivered a few bytes at a time so that
     * the frames are split across reads.
     */
    @Test
    public void testFragmentedCompressedMessageWithoutHandler() throws Exception {
        doTestCompressedMessagesWithoutHandler(3, 7, 1);
    }


    /*
     * The decompressed size of the skipped message is much larger than the buffer used to discard the data.
     */
    @Test
    public void testLargeCompressedMessageWithoutHandler() throws Exception {
        doTestCompressedMessagesWithoutHandler(1, Integer.MAX_VALUE, 2000);
    }


    private static void doTestCompressedMessagesWithoutHandler(int fragments, int chunkSize, int repeat)
            throws Exception {
        String repeated = "THE QUICK BROWN FOX JUMPS OVER THE LAZY DOG. ";
        StringBuilder sb = new StringBuilder(repeated.length() * repeat);
        for (int i = 0; i < repeat; i++) {
            sb.append(repeated);
        }
        byte[] skipped = (sb.toString() + "first").getBytes(StandardCharsets.UTF_8);
        String expected = repeated + "second";

        Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION, true);
        ByteArrayOutputStream wire = new ByteArrayOutputStream();
        try {
            byte[] compressed = deflateMessage(deflater, skipped);
            int fragmentSize = (compressed.length + fragments - 1) / fragments;
            for (int i = 0; i < fragments; i++) {
                int start = i * fragmentSize;
                int end = Math.min(compressed.length, start + fragmentSize);
                byte[] fragment = java.util.Arrays.copyOfRange(compressed, start, end);
                boolean first = i == 0;
                boolean last = i == fragments - 1;
                wire.write(createFrame(first ? Constants.OPCODE_BINARY : Constants.OPCODE_CONTINUATION, last, first,
                        fragment));
            }
            wire.write(createFrame(Constants.OPCODE_TEXT, true,
                    deflateMessage(deflater, expected.getBytes(StandardCharsets.UTF_8))));
        } finally {
            deflater.end();
        }

        List<List<Parameter>> preferences = new ArrayList<>();
        preferences.add(Collections.emptyList());
        PerMessageDeflate perMessageDeflate = PerMessageDeflate.build(preferences, false);
        Assert.assertNotNull(perMessageDeflate);

        List<String> received = new ArrayList<>();
        MessageHandler.Whole<String> textHandler = received::add;

        WsSession wsSession = EasyMock.createNiceMock(WsSession.class);
        EasyMock.expect(Integer.valueOf(wsSession.getMaxBinaryMessageBufferSize())).andStubReturn(Integer.valueOf(8192));
        EasyMock.expect(Integer.valueOf(wsSession.getMaxTextMessageBufferSize())).andStubReturn(Integer.valueOf(8192));
        EasyMock.expect(wsSession.getBinaryMessageHandler()).andStubReturn(null);
        EasyMock.expect(wsSession.getTextMessageHandler()).andStubReturn(textHandler);
        EasyMock.replay(wsSession);

        TestFrame frame = new TestFrame(wsSession, perMessageDeflate);
        byte[] data = wire.toByteArray();
        for (int i = 0; i < data.length; i += chunkSize) {
            frame.process(java.util.Arrays.copyOfRange(data, i, (int) Math.min(data.length, (long) i + chunkSize)));
        }

        Assert.assertEquals(1, received.size());
        Assert.assertEquals(expected, received.get(0));
    }


    private static byte[] deflateMessage(Deflater deflater, byte[] data) {
        deflater.setInput(data);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[1024];
        int n;
        while ((n = deflater.deflate(buf, 0, buf.length, Deflater.SYNC_FLUSH)) > 0) {
            baos.write(buf, 0, n);
        }
        byte[] result = baos.toByteArray();
        // Remove the trailing 0x00 0x00 0xFF 0xFF added by the sync flush (RFC 7692, section 7.2.1)
        Assert.assertTrue(result.length >= 4);
        return java.util.Arrays.copyOf(result, result.length - 4);
    }


    private static byte[] createFrame(int opCode, boolean compressed, byte[] payload) {
        return createFrame(opCode, true, compressed, payload);
    }


    private static byte[] createFrame(int opCode, boolean fin, boolean compressed, byte[] payload) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write((fin ? 0x80 : 0x00) | (compressed ? 0x40 : 0x00) | opCode);
        if (payload.length <= 125) {
            baos.write(payload.length);
        } else {
            baos.write(126);
            baos.write(payload.length >>> 8);
            baos.write(payload.length & 0xFF);
        }
        baos.write(payload, 0, payload.length);
        return baos.toByteArray();
    }


    private static class TestFrame extends WsFrameBase {

        TestFrame(WsSession wsSession) {
            super(wsSession, null);
        }

        TestFrame(WsSession wsSession, Transformation transformation) {
            super(wsSession, transformation);
        }

        void process(byte[] data) throws IOException {
            // Retain any data from previous calls that has not yet been processed
            inputBuffer.compact();
            inputBuffer.put(data);
            inputBuffer.flip();
            processInputBuffer();
        }

        void processPing() throws IOException {
            inputBuffer.clear();
            inputBuffer.put((byte) 0x89);
            inputBuffer.put((byte) 0x00);
            inputBuffer.flip();
            processInputBuffer();
        }

        @Override
        protected boolean isMasked() {
            return false;
        }

        @Override
        protected Log getLog() {
            return LogFactory.getLog(TestFrame.class);
        }

        @Override
        protected void resumeProcessing() {
            // NO-OP
        }
    }
}
