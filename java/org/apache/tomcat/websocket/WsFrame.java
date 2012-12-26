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
import java.nio.CharBuffer;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;

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

    // Attributes for control messages
    // Control messages can appear in the middle of other messages so need
    // separate attributes
    private final ByteBuffer controlBufferBinary = ByteBuffer.allocate(125);
    private final CharBuffer controlBufferText = CharBuffer.allocate(125);

    // Attributes of the current message
    private final ByteBuffer messageBufferBinary;
    private final CharBuffer messageBufferText;
    private final CharsetDecoder utf8DecoderControl = new Utf8Decoder().
            onMalformedInput(CodingErrorAction.REPORT).
            onUnmappableCharacter(CodingErrorAction.REPORT);
    private final CharsetDecoder utf8DecoderMessage = new Utf8Decoder().
            onMalformedInput(CodingErrorAction.REPORT).
            onUnmappableCharacter(CodingErrorAction.REPORT);
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
        messageBufferBinary = ByteBuffer.allocate(readBufferSize);
        messageBufferText = CharBuffer.allocate(readBufferSize);
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
        if (isControl()) {
            if (!fin) {
                throw new WsIOException(new CloseReason(
                        CloseCodes.PROTOCOL_ERROR,
                        sm.getString("wsFrame.controlFragmented")));
            }
            if (opCode != Constants.OPCODE_PING &&
                    opCode != Constants.OPCODE_PONG &&
                    opCode != Constants.OPCODE_CLOSE) {
                throw new WsIOException(new CloseReason(
                        CloseCodes.PROTOCOL_ERROR,
                        sm.getString("wsFrame.invalidOpCode",
                                Integer.valueOf(opCode))));
            }
        } else {
            if (continuationExpected) {
                if (opCode != Constants.OPCODE_CONTINUATION) {
                    throw new WsIOException(new CloseReason(
                            CloseCodes.PROTOCOL_ERROR,
                            sm.getString("wsFrame.noContinuation")));
                }
            } else {
                if (opCode == Constants.OPCODE_BINARY) {
                    textMessage = false;
                } else if (opCode == Constants.OPCODE_TEXT) {
                    textMessage = true;
                } else {
                    throw new WsIOException(new CloseReason(
                            CloseCodes.PROTOCOL_ERROR,
                            sm.getString("wsFrame.invalidOpCode",
                                    Integer.valueOf(opCode))));
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
        if (isControl()) {
            return processDataControl();
        }

        // Unmask data
        appendPayloadToMessage(messageBufferBinary);

        if (textMessage) {
            // Convert the bytes to text as early as possible to catch any
            // conversion issues
            messageBufferBinary.flip();
            boolean last = false;
            while (true) {
                CoderResult cr = utf8DecoderMessage.decode(
                        messageBufferBinary, messageBufferText, last);
                if (cr.isError()) {
                    throw new WsIOException(new CloseReason(
                            CloseCodes.NOT_CONSISTENT,
                            sm.getString("wsFrame.invalidUtf8")));
                } else if (cr.isOverflow()) {
                    if (usePartial()) {
                        messageBufferText.flip();
                        sendMessageText(false);
                        messageBufferText.clear();
                    } else {
                        throw new WsIOException(new CloseReason(
                                CloseCodes.TOO_BIG,
                                sm.getString("wsFrame.textMessageTooBig")));
                    }
                } else if (cr.isUnderflow() && !last) {
                    // Need more data
                    messageBufferBinary.compact();
                    if (payloadWritten == payloadLength) {
                        if (continuationExpected) {
                            newFrame();
                            return true;
                        } else {
                            messageBufferBinary.flip();
                            last = true;
                        }
                    } else {
                        return false;
                    }
                } else {
                    // End of input
                    messageBufferText.flip();
                    sendMessageText(true);
                    messageBufferText.clear();
                    newMessage();
                    return true;
                }
            }
        } else {
            if (payloadWritten == payloadLength) {
                if (continuationExpected) {
                    if (usePartial()) {
                        messageBufferBinary.flip();
                        sendMessageBinary(false);
                        messageBufferBinary.clear();
                    }
                    newFrame();
                    return true;
                } else {
                    messageBufferBinary.flip();
                    sendMessageBinary(true);
                    newMessage();
                    return true;
                }
            } else {
                if (usePartial()) {
                    messageBufferBinary.flip();
                    sendMessageBinary(false);
                    messageBufferBinary.clear();
                }
                return false;
            }
        }
    }


    private boolean processDataControl() throws IOException {
        appendPayloadToMessage(controlBufferBinary);
        if (writePos < frameStart + headerLength + payloadLength) {
            return false;
        }
        controlBufferBinary.flip();
        if (opCode == Constants.OPCODE_CLOSE) {
            String reason = null;
            int code = CloseCodes.NORMAL_CLOSURE.getCode();
            if (controlBufferBinary.remaining() == 1) {
                controlBufferBinary.clear();
                // Payload must be zero or greater than 2
                throw new WsIOException(new CloseReason(
                        CloseCodes.PROTOCOL_ERROR,
                        sm.getString("wsFrame.oneByteCloseCode")));
            }
            if (controlBufferBinary.remaining() > 1) {
                code = controlBufferBinary.getShort();
                if (controlBufferBinary.remaining() > 0) {
                    CoderResult cr = utf8DecoderControl.decode(
                            controlBufferBinary, controlBufferText, true);
                    if (cr.isError()) {
                        controlBufferBinary.clear();
                        controlBufferText.clear();
                        throw new WsIOException(new CloseReason(
                                CloseCodes.PROTOCOL_ERROR,
                                sm.getString("wsFrame.invalidUtf8Close")));
                    }
                    reason = new String(controlBufferBinary.array(),
                            controlBufferBinary.arrayOffset() +
                                    controlBufferBinary.position(),
                            controlBufferBinary.remaining(), "UTF8");
                }
            }
            wsSession.onClose(
                    new CloseReason(Util.getCloseCode(code), reason));
        } else if (opCode == Constants.OPCODE_PING) {
            wsSession.getRemote().sendPong(controlBufferBinary);
        } else if (opCode == Constants.OPCODE_PONG) {
            MessageHandler.Basic<PongMessage> mhPong =
                    wsSession.getPongMessageHandler();
            if (mhPong != null) {
                mhPong.onMessage(new WsPongMessage(controlBufferBinary));
            }
        } else {
            // Should have caught this earlier but just in case...
            controlBufferBinary.clear();
            throw new WsIOException(new CloseReason(
                    CloseCodes.PROTOCOL_ERROR,
                    sm.getString("wsFrame.invalidOpCode",
                            Integer.valueOf(opCode))));
        }
        controlBufferBinary.clear();
        newFrame();
        return true;
    }


    @SuppressWarnings("unchecked")
    private void sendMessageText(boolean last) {
        MessageHandler mh = wsSession.getTextMessageHandler();
        if (mh != null) {
            if (mh instanceof MessageHandler.Async<?>) {
                ((MessageHandler.Async<String>) mh).onMessage(
                        messageBufferText.toString(), last);
            } else {
                ((MessageHandler.Basic<String>) mh).onMessage(
                        messageBufferText.toString());
            }
            messageBufferText.clear();
        }
    }


    @SuppressWarnings("unchecked")
    private void sendMessageBinary(boolean last) {
        MessageHandler mh = wsSession.getBinaryMessageHandler();
        if (mh != null) {
            if (mh instanceof MessageHandler.Async<?>) {
                ((MessageHandler.Async<ByteBuffer>) mh).onMessage(
                        messageBufferBinary, last);
            } else {
                ((MessageHandler.Basic<ByteBuffer>) mh).onMessage(
                        messageBufferBinary);
            }
        }
    }

    private void newMessage() {
        messageBufferBinary.clear();
        messageBufferText.clear();
        utf8DecoderMessage.reset();
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


    private void appendPayloadToMessage(ByteBuffer dest) {
        while (payloadWritten < payloadLength && payloadRead < writePos) {
            byte b = (byte) ((inputBuffer[payloadRead] ^ mask[maskIndex]) & 0xFF);
            maskIndex++;
            if (maskIndex == 4) {
                maskIndex = 0;
            }
            payloadRead++;
            payloadWritten++;
            dest.put(b);
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
