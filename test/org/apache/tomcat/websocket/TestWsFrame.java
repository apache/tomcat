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

import java.io.IOException;
import java.nio.ByteBuffer;

import jakarta.websocket.RemoteEndpoint;

import org.easymock.EasyMock;
import org.junit.Assert;
import org.junit.Test;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

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
        WsSession wsSession = EasyMock.createNiceMock(WsSession.class);
        RemoteEndpoint.Basic basicRemote = EasyMock.createMock(RemoteEndpoint.Basic.class);
        EasyMock.expect(wsSession.isOpen()).andReturn(Boolean.TRUE);
        EasyMock.expect(wsSession.getBasicRemote()).andReturn(basicRemote);
        basicRemote.sendPong(EasyMock.anyObject(ByteBuffer.class));
        EasyMock.expectLastCall().andThrow(new IllegalStateException());
        EasyMock.expect(wsSession.isClosing()).andReturn(Boolean.TRUE);
        EasyMock.replay(wsSession, basicRemote);

        TestFrame frame = new TestFrame(wsSession);
        frame.processPing();

        EasyMock.verify(wsSession, basicRemote);
    }


    @Test
    public void testAutomaticPongFailureWhileOpen() throws Exception {
        WsSession wsSession = EasyMock.createNiceMock(WsSession.class);
        RemoteEndpoint.Basic basicRemote = EasyMock.createMock(RemoteEndpoint.Basic.class);
        EasyMock.expect(wsSession.isOpen()).andReturn(Boolean.TRUE);
        EasyMock.expect(wsSession.getBasicRemote()).andReturn(basicRemote);
        basicRemote.sendPong(EasyMock.anyObject(ByteBuffer.class));
        EasyMock.expectLastCall().andThrow(new IllegalStateException());
        EasyMock.expect(wsSession.isClosing()).andReturn(Boolean.FALSE);
        EasyMock.replay(wsSession, basicRemote);

        TestFrame frame = new TestFrame(wsSession);
        try {
            frame.processPing();
            Assert.fail();
        } catch (IllegalStateException expected) {
            // Expected.
        }

        EasyMock.verify(wsSession, basicRemote);
    }


    private static class TestFrame extends WsFrameBase {

        TestFrame(WsSession wsSession) {
            super(wsSession, null);
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
