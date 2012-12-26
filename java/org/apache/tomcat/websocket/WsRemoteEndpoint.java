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
import java.io.OutputStream;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Future;

import javax.servlet.ServletOutputStream;
import javax.websocket.EncodeException;
import javax.websocket.RemoteEndpoint;
import javax.websocket.SendHandler;
import javax.websocket.SendResult;

public class WsRemoteEndpoint implements RemoteEndpoint {

    private final ServletOutputStream sos;
    private final WsSession wsSession;
    // Max length for outgoing WebSocket frame header is 10 bytes
    private final ByteBuffer header = ByteBuffer.allocate(10);

    private final ByteBuffer textToByte = ByteBuffer.allocate(8192);
    private final CharsetEncoder encoder = Charset.forName("UTF8").newEncoder();
    private volatile Boolean isText = null;
    private volatile CyclicBarrier writeBarrier = new CyclicBarrier(2);


    public WsRemoteEndpoint(WsSession wsSession, ServletOutputStream sos) {
        this.wsSession = wsSession;
        this.sos = sos;
    }


    @Override
    public void sendString(String text) throws IOException {
        if (isText != null) {
            // Another message is being sent using fragments
            // TODO i18n
            throw new IllegalStateException();
        }
        sendPartialString(text, true);
    }


    @Override
    public void sendBytes(ByteBuffer data) throws IOException {
        if (isText != null) {
            // Another message is being sent using fragments
            // TODO i18n
            throw new IllegalStateException();
        }
        sendPartialBytes(data, true);
    }


    @Override
    public void sendPartialString(String fragment, boolean isLast)
            throws IOException {

        if (isText != null && !isText.booleanValue()) {
            // Can't write a text fragment in the middle of a binary message
            // TODO i18n
            throw new IllegalStateException();
        }

        boolean first = (isText == null);
        encoder.reset();
        textToByte.clear();
        CharBuffer cb = CharBuffer.wrap(fragment);
        CoderResult cr = encoder.encode(cb, textToByte, true);
        textToByte.flip();
        while (cr.isOverflow()) {
            sendMessage(Constants.OPCODE_TEXT, textToByte, first, false);
            textToByte.clear();
            cr = encoder.encode(cb, textToByte, true);
            textToByte.flip();
            first = false;
        }
        sendMessage(Constants.OPCODE_TEXT, textToByte, first, isLast);
        if (!isLast) {
            isText = Boolean.TRUE;
        }
    }


    @Override
    public void sendPartialBytes(ByteBuffer partialByte, boolean isLast)
            throws IOException {

        if (isText != null && isText.booleanValue()) {
            // Can't write a binary fragment in the middle of a text message
            // TODO i18n
            throw new IllegalStateException();
        }

        boolean first = (isText == null);
        sendMessage(Constants.OPCODE_BINARY, partialByte, first, isLast);
        if (!isLast) {
            isText = Boolean.FALSE;
        }
    }


    @Override
    public OutputStream getSendStream() throws IOException {
        // TODO Auto-generated method stub
        return null;
    }


    @Override
    public Writer getSendWriter() throws IOException {
        // TODO Auto-generated method stub
        return null;
    }


    @Override
    public void sendObject(Object o) throws IOException, EncodeException {
        // TODO Auto-generated method stub
    }


    @Override
    public void sendStringByCompletion(String text, SendHandler completion) {
        // TODO Auto-generated method stub
    }


    @Override
    public Future<SendResult> sendStringByFuture(String text) {
        // TODO Auto-generated method stub
        return null;
    }


    @Override
    public Future<SendResult> sendBytesByFuture(ByteBuffer data) {
        // TODO Auto-generated method stub
        return null;
    }


    @Override
    public void sendBytesByCompletion(ByteBuffer data, SendHandler completion) {
        // TODO Auto-generated method stub
    }


    @Override
    public Future<SendResult> sendObjectByFuture(Object obj) {
        // TODO Auto-generated method stub
        return null;
    }


    @Override
    public void sendObjectByCompletion(Object obj, SendHandler completion) {
        // TODO Auto-generated method stub
    }


    @Override
    public void sendPing(ByteBuffer applicationData) {
        sendMessage(Constants.OPCODE_PING, applicationData, true, true);
    }


    @Override
    public void sendPong(ByteBuffer applicationData) {
        sendMessage(Constants.OPCODE_PONG, applicationData, true, true);
    }


    public void onWritePossible() {
        try {
            writeBarrier.await();
        } catch (InterruptedException | BrokenBarrierException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }


    protected void sendMessage(byte opCode, ByteBuffer message,
            boolean isFirstFragment, boolean isLastFragment) {
        // Clear header, ready for new message
        header.clear();
        byte first = 0;

        if (isLastFragment) {
            // Set the fin bit
            first = -128;
        }

        if (isFirstFragment) {
            // This is the first fragment of this message
            first = (byte) (first + opCode);
        }
        // If not the first fragment, it is a continuation with opCode of zero

        header.put(first);

        // Next write the length
        if (message.limit() < 126) {
            header.put((byte) message.limit());
        } else if (message.limit() < 65536) {
            header.put((byte) 126);
            header.put((byte) (message.limit() >>> 8));
            header.put((byte) (message.limit() & 0xFF));
        } else {
            // Will never be more than 2^31-1
            header.put((byte) 127);
            header.put((byte) 0);
            header.put((byte) 0);
            header.put((byte) 0);
            header.put((byte) 0);
            header.put((byte) (message.limit() >>> 24));
            header.put((byte) (message.limit() >>> 16));
            header.put((byte) (message.limit() >>> 8));
            header.put((byte) (message.limit() & 0xFF));
        }
        header.flip();

        doBlockingWrite(header);
        doBlockingWrite(message);
        try {
            sos.flush();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        if (Constants.OPCODE_CLOSE == opCode) {
            try {
                sos.close();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }


    private void doBlockingWrite(ByteBuffer data) {
        if (!sos.canWrite()) {
            try {
                writeBarrier.await();
            } catch (InterruptedException | BrokenBarrierException e) {
                wsSession.getLocalEndpoint().onError(wsSession, e);
            }
        }
        try {
            sos.write(data.array(), data.arrayOffset(), data.limit());
        } catch (IOException e) {
            wsSession.getLocalEndpoint().onError(wsSession, e);
        }
    }


    @Override
    public void setBatchingAllowed(boolean batchingAllowed) {
        // TODO Auto-generated method stub

    }


    @Override
    public boolean getBatchingAllowed() {
        // TODO Auto-generated method stub
        return false;
    }


    @Override
    public void flushBatch() {
        // TODO Auto-generated method stub

    }


    @Override
    public long getAsyncSendTimeout() {
        // TODO Auto-generated method stub
        return 0;
    }


    @Override
    public void setAsyncSendTimeout(long timeout) {
        // TODO Auto-generated method stub

    }
}
