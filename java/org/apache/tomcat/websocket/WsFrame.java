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

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;

import javax.servlet.ServletInputStream;
import javax.websocket.MessageHandler;
import javax.websocket.PongMessage;

import org.apache.tomcat.util.res.StringManager;

/**
 * Takes the ServletInputStream and converts the received data into WebSocket
 * frames.
 */
public class WsFrame {

    private static StringManager sm = StringManager.getManager(Constants.PACKAGE_NAME);
    private final ServletInputStream sis;
    private final WsSession wsSession;
    private final byte[] inputBuffer;
    private int pos = 0;
    private State state = State.NEW_FRAME;
    private int headerLength = 0;
    private boolean continutationExpected = false;
    private boolean textMessage = false;
    private long payloadSent = 0;
    private long payloadLength = 0;
    private boolean fin;
    private int rsv;
    private byte opCode;
    private byte[] mask = new byte[4];
    int maskIndex = 0;


    public WsFrame(ServletInputStream sis, WsSession wsSession) {
        this.sis = sis;
        this.wsSession = wsSession;
        inputBuffer = new byte[8192];
    }


    /**
     * Called when there is data in the ServletInputStream to process.
     */
    public void onDataAvailable() throws IOException {
        while (sis.isReady()) {
            // Fill up the input buffer with as much data as we can
            int read = sis.read(inputBuffer, pos, inputBuffer.length - pos);
            if (read == 0) {
                return;
            }
            if (read == -1) {
                throw new EOFException();
            }
            pos += read;
            while (true) {
                if (state == State.NEW_FRAME) {
                    if (!processInitialHeader()) {
                        break;
                    }
                }
                if (state == State.PARTIAL_HEADER) {
                    if (!processRemainingHeader()) {
                        break;
                    }
                }
                if (state == State.DATA) {
                    if (!processData()) {
                        break;
                    }
                }
            }
        }
    }


    /**
     * @return <code>true</code> if sufficient data was present to process all
     *         of the initial header
     */
    private boolean processInitialHeader() throws IOException {
        // Need at least two bytes of data to do this
        if (pos < 2) {
            return false;
        }
        int b = inputBuffer[0];
        fin = (b & 0x80) > 0;
        rsv = (b & 0x70) >>> 4;
        opCode = (byte) (b & 0x0F);
        if (!isControl()) {
            if (continutationExpected) {
                if (opCode != Constants.OPCODE_CONTINUATION) {
                    // TODO i18n
                    throw new IllegalStateException();
                }
            } else {
                if (opCode == Constants.OPCODE_BINARY) {
                    textMessage = false;
                } else if (opCode == Constants.OPCODE_TEXT) {
                    textMessage = true;
                } else {
                    // TODO i18n
                    throw new UnsupportedOperationException();
                }
            }
            continutationExpected = !fin;
        }
        b = inputBuffer[1];
        // Client data must be masked
        if ((b & 0x80) == 0) {
            throw new IOException(sm.getString("wsFrame.notMasked"));
        }
        payloadLength = b & 0x7F;
        state = State.PARTIAL_HEADER;
        return true;
    }


    /**
     * @return <code>true</code> if sufficient data was present to complete the
     *         processing of the header
     */
    private boolean processRemainingHeader() throws IOException {
        // Initial 2 bytes already read + 4 for the mask
        headerLength = 6;
        // Add additional bytes depending on length
        if (payloadLength == 126) {
            headerLength += 2;
        } else if (payloadLength == 127) {
            headerLength += 8;
        }
        if (pos < headerLength) {
            return false;
        }
        // Calculate new payload length if necessary
        if (payloadLength == 126) {
            payloadLength = byteArrayToLong(inputBuffer, 2, 2);
        } else if (payloadLength == 127) {
            payloadLength = byteArrayToLong(inputBuffer, 2, 8);
        }
        if (isControl()) {
            if (payloadLength > 125) {
                throw new IOException(sm.getString(
                        "wsFrame.controlPayloadTooBig",
                        Long.valueOf(payloadLength)));
            }
            if (!fin) {
                throw new IOException("wsFrame.controlNoFin");
            }
        }
        System.arraycopy(inputBuffer, headerLength - 4, mask, 0, 4);
        state = State.DATA;
        return true;
    }


    private boolean processData() throws IOException {
        if (isControl()) {
            if (!isPayloadComplete()) {
                return false;
            }
            if (opCode == Constants.OPCODE_CLOSE) {
                wsSession.close();
            } else if (opCode == Constants.OPCODE_PING) {
                wsSession.getRemote().sendPong(getPayloadBinary());
            } else if (opCode == Constants.OPCODE_PONG) {
                MessageHandler.Basic<PongMessage> mhPong = wsSession.getPongMessageHandler();
                if (mhPong != null) {
                    mhPong.onMessage(new WsPongMessage(getPayloadBinary()));
                }
            } else {
                // TODO i18n
                throw new UnsupportedOperationException();
            }
            return true;
        }
        if (!isPayloadComplete()) {
            if (usePartial()) {
                sendPayload(false);
                return false;
            } else {
                if (inputBuffer.length - pos > 0) {
                    return false;
                }
                throw new UnsupportedOperationException();
            }
        } else {
            sendPayload(true);
        }
        state = State.NEW_FRAME;
        payloadLength = 0;
        payloadSent = 0;
        maskIndex = 0;
        return true;
    }


    @SuppressWarnings("unchecked")
    private void sendPayload(boolean last) {
        if (textMessage) {
            String payload = getPayloadText();
            MessageHandler mh = wsSession.getTextMessageHandler();
            if (mh != null) {
                if (mh instanceof MessageHandler.Async<?>) {
                    ((MessageHandler.Async<String>) mh).onMessage(payload, last);
                } else {
                    ((MessageHandler.Basic<String>) mh).onMessage(payload);
                }
            }
        } else {
            ByteBuffer payload = getPayloadBinary();
            MessageHandler mh = wsSession.getBinaryMessageHandler();
            if (mh != null) {
                if (mh instanceof MessageHandler.Async<?>) {
                    ((MessageHandler.Async<ByteBuffer>) mh).onMessage(payload,
                            last);
                } else {
                    ((MessageHandler.Basic<ByteBuffer>) mh).onMessage(payload);
                }
            }
        }
    }


    private boolean isControl() {
        return (opCode & 0x08) > 0;
    }


    private boolean isPayloadComplete() {
        return (payloadSent + pos - headerLength) >= payloadLength;
    }


    private boolean usePartial() {
        if (opCode == Constants.OPCODE_BINARY) {
            MessageHandler mh = wsSession.getBinaryMessageHandler();
            if (mh != null) {
                return mh instanceof MessageHandler.Async<?>;
            }
            return false;
        } else if (opCode == Constants.OPCODE_TEXT) {
            MessageHandler mh = wsSession.getTextMessageHandler();
            if (mh != null) {
                return mh instanceof MessageHandler.Async<?>;
            }
            return false;
        } else {
            // All other OpCodes require the full payload to be present
            return false;
        }
    }


    private ByteBuffer getPayloadBinary() {
        int end;
        if (isPayloadComplete()) {
            end = (int) (payloadLength - payloadSent) + headerLength;
        } else {
            end = pos;
        }
        ByteBuffer result = ByteBuffer.allocate(end - headerLength);
        for (int i = headerLength; i < end; i++) {
            result.put(i - headerLength,
                    (byte) ((inputBuffer[i] ^ mask[maskIndex]) & 0xFF));
            maskIndex++;
            if (maskIndex == 4) {
                maskIndex = 0;
            }
        }
        // May have read past end of current frame into next
        pos = 0;
        headerLength = 0;
        return result;
    }


    private String getPayloadText() {
        ByteBuffer bb = getPayloadBinary();
        return new String(bb.array(), Charset.forName("UTF-8"));
    }


    protected static long byteArrayToLong(byte[] b, int start, int len)
            throws IOException {
        if (len > 8) {
            throw new IOException(sm.getString("wsFrame.byteToLongFail",
                    Long.valueOf(len)));
        }
        int shift = 0;
        long result = 0;
        for (int i = start + len - 1; i >= start; i--) {
            result = result + ((b[i] & 0xFF) << shift);
            shift += 8;
        }
        return result;
    }

    private static enum State {
        NEW_FRAME, PARTIAL_HEADER, DATA
    }
}
