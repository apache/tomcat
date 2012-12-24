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

import javax.servlet.ServletInputStream;
import javax.websocket.CloseReason;
import javax.websocket.CloseReason.CloseCodes;
import javax.websocket.MessageHandler;
import javax.websocket.PongMessage;

import org.apache.tomcat.util.res.StringManager;

/**
 * Takes the ServletInputStream and converts the received data into WebSocket
 * frames.
 */
public class WsFrame {

    private static final StringManager sm =
            StringManager.getManager(Constants.PACKAGE_NAME);

    // Connection level attributes
    private final ServletInputStream sis;
    private final WsSession wsSession;
    private final byte[] inputBuffer;

    // Attributes of the current message
    private final ByteBuffer messageBuffer;
    private boolean continuationExpected = false;
    private boolean textMessage = false;

    // Attributes of the current frame
    private boolean fin = false;
    private int rsv = 0;
    private byte opCode = 0;
    private int frameStart = 0;
    private int headerLength = 0;
    private byte[] mask = new byte[4];
    private int maskIndex = 0;
    private long payloadLength = 0;
    private int payloadRead = 0;
    private long payloadWritten = 0;

    // Attributes tracking state
    private State state = State.NEW_FRAME;
    private int writePos = 0;

    public WsFrame(ServletInputStream sis, WsSession wsSession) {
        this.sis = sis;
        this.wsSession = wsSession;

        int readBufferSize =
                ServerContainerImpl.getServerContainer().getReadBufferSize();

        inputBuffer = new byte[readBufferSize];
        messageBuffer = ByteBuffer.allocate(readBufferSize);
    }


    /**
     * Called when there is data in the ServletInputStream to process.
     */
    public void onDataAvailable() throws IOException {
        while (sis.isReady()) {
            // Fill up the input buffer with as much data as we can
            int read = sis.read(inputBuffer, writePos,
                    inputBuffer.length - writePos);
            if (read == 0) {
                return;
            }
            if (read == -1) {
                throw new EOFException();
            }
            writePos += read;
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
        if (writePos - frameStart < 2) {
            return false;
        }
        int b = inputBuffer[frameStart];
        fin = (b & 0x80) > 0;
        rsv = (b & 0x70) >>> 4;
        if (rsv != 0) {
            // TODO Extensions may use rsv bits
            throw new WsIOException(new CloseReason(
                    CloseCodes.PROTOCOL_ERROR,
                    sm.getString("wsFrame.wrongRsv", Integer.valueOf(rsv))));
        }
        opCode = (byte) (b & 0x0F);
        if (!isControl()) {
            if (continuationExpected) {
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
            continuationExpected = !fin;
        }
        b = inputBuffer[frameStart + 1];
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
        if (writePos - frameStart < headerLength) {
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
                CloseReason cr = new CloseReason(
                        CloseCodes.PROTOCOL_ERROR,
                        sm.getString("wsFrame.controlPayloadTooBig",
                                Long.valueOf(payloadLength)));
                throw new WsIOException(cr);
            }
            if (!fin) {
                throw new IOException("wsFrame.controlNoFin");
            }
        }
        System.arraycopy(inputBuffer, frameStart + headerLength - 4, mask, 0, 4);
        state = State.DATA;
        payloadRead = frameStart + headerLength;
        return true;
    }


    private boolean processData() throws IOException {
        checkRoomPayload();
        appendPayloadToMessage();
        if (isControl()) {
            if (writePos < frameStart + headerLength + payloadLength) {
                return false;
            }
            if (opCode == Constants.OPCODE_CLOSE) {
                messageBuffer.flip();
                String reason = null;
                int code = CloseCodes.NORMAL_CLOSURE.getCode();
                if (messageBuffer.remaining() > 1) {
                    code = messageBuffer.getShort();
                    if (messageBuffer.remaining() > 0) {
                         reason = new String(messageBuffer.array(),
                                messageBuffer.arrayOffset() + messageBuffer.position(),
                                messageBuffer.remaining(), "UTF8");
                    }
                }
                wsSession.onClose(
                        new CloseReason(Util.getCloseCode(code), reason));
            } else if (opCode == Constants.OPCODE_PING) {
                messageBuffer.flip();
                wsSession.getRemote().sendPong(messageBuffer);
            } else if (opCode == Constants.OPCODE_PONG) {
                MessageHandler.Basic<PongMessage> mhPong = wsSession.getPongMessageHandler();
                if (mhPong != null) {
                    messageBuffer.flip();
                    mhPong.onMessage(new WsPongMessage(messageBuffer));
                }
            } else {
                // TODO i18n
                throw new UnsupportedOperationException();
            }
            newMessage();
            return true;
        }
        if (payloadWritten == payloadLength) {
            if (continuationExpected) {
                if (usePartial()) {
                    messageBuffer.flip();
                    sendMessage(false);
                    messageBuffer.clear();
                }
                newFrame();
                return true;
            } else {
                messageBuffer.flip();
                sendMessage(true);
                newMessage();
                return true;
            }
        } else {
            if (usePartial()) {
                messageBuffer.flip();
                sendMessage(false);
                messageBuffer.clear();
            }
            return false;
        }
    }


    @SuppressWarnings("unchecked")
    private void sendMessage(boolean last) {
        if (textMessage) {
            String payload =
                    new String(messageBuffer.array(), 0, messageBuffer.limit());
            MessageHandler mh = wsSession.getTextMessageHandler();
            if (mh != null) {
                if (mh instanceof MessageHandler.Async<?>) {
                    ((MessageHandler.Async<String>) mh).onMessage(payload, last);
                } else {
                    ((MessageHandler.Basic<String>) mh).onMessage(payload);
                }
            }
        } else {
            MessageHandler mh = wsSession.getBinaryMessageHandler();
            if (mh != null) {
                if (mh instanceof MessageHandler.Async<?>) {
                    ((MessageHandler.Async<ByteBuffer>) mh).onMessage(
                            messageBuffer, last);
                } else {
                    ((MessageHandler.Basic<ByteBuffer>) mh).onMessage(
                            messageBuffer);
                }
            }
        }
    }


    private void newMessage() {
        messageBuffer.clear();
        continuationExpected = false;
        newFrame();
    }


    private void newFrame() {
        if (frameStart + headerLength + payloadLength == writePos) {
            frameStart = 0;
            writePos = 0;
        } else {
            frameStart = frameStart + headerLength + (int) payloadLength;
        }

        // These get reset in processInitialHeader()
        // fin, rsv, opCode, headerLength, payloadLength, mask
        maskIndex = 0;
        payloadRead = 0;
        payloadWritten = 0;
        state = State.NEW_FRAME;
        checkRoomHeaders();
    }


    private void checkRoomHeaders() {
        // Is the start of the current frame too near the end of the input
        // buffer?
        if (inputBuffer.length - frameStart < 131) {
            // Limit based on a control frame with a full payload
            makeRoom();
        }
    }


    private void checkRoomPayload() throws IOException {
        long frameSize = headerLength + payloadLength;
        if (inputBuffer.length - frameStart - frameSize < 0) {
            if (isControl()) {
                makeRoom();
                return;
            }
            // Might not be enough room
            if (usePartial()) {
                // Not a problem - can use partial messages
                return;
            }
            if (inputBuffer.length < frameSize) {
                // TODO i18n - buffer too small
                CloseReason cr = new CloseReason(CloseCodes.TOO_BIG,
                        "Buffer size: [" + inputBuffer.length +
                        "], frame size: [" + frameSize + "]");
                wsSession.close(cr);
                wsSession.onClose(cr);
                throw new IOException(cr.getReasonPhrase());
            }
            makeRoom();
        }
    }


    private void makeRoom() {
        System.arraycopy(inputBuffer, frameStart, inputBuffer, 0,
                writePos - frameStart);
        writePos = writePos - frameStart;
        payloadRead = payloadRead - frameStart;
        frameStart = 0;
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


    private void appendPayloadToMessage() {
        while (payloadWritten < payloadLength && payloadRead < writePos) {
            byte b = (byte) ((inputBuffer[payloadRead] ^ mask[maskIndex]) & 0xFF);
            maskIndex++;
            if (maskIndex == 4) {
                maskIndex = 0;
            }
            payloadRead++;
            payloadWritten++;
            messageBuffer.put(b);
        }
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


    private boolean isControl() {
        return (opCode & 0x08) > 0;
    }


    private static enum State {
        NEW_FRAME, PARTIAL_HEADER, DATA
    }
}
