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
package org.apache.catalina.websocket;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.nio.charset.MalformedInputException;
import java.nio.charset.UnmappableCharacterException;

import org.apache.coyote.http11.upgrade.UpgradeInbound;
import org.apache.coyote.http11.upgrade.UpgradeOutbound;
import org.apache.coyote.http11.upgrade.UpgradeProcessor;
import org.apache.tomcat.util.net.AbstractEndpoint.Handler.SocketState;

/**
 * Base implementation of the class used to process WebSocket connections based
 * on streams. Applications should extend this class to provide application
 * specific functionality. Applications that wish to operate on a message basis
 * rather than a stream basis should use {@link MessageInbound}.
 */
public abstract class StreamInbound implements UpgradeInbound {

    private UpgradeProcessor<?> processor = null;
    private WsOutbound outbound;
    private int outboundByteBufferSize = WsOutbound.DEFAULT_BUFFER_SIZE;
    private int outboundCharBufferSize = WsOutbound.DEFAULT_BUFFER_SIZE;



    public int getOutboundByteBufferSize() {
        return outboundByteBufferSize;
    }


    /**
     * This only applies to the {@link WsOutbound} instance returned from
     * {@link #getWsOutbound()} created by a subsequent call to
     * {@link #setUpgradeOutbound(UpgradeOutbound)}. The current
     * {@link WsOutbound} instance, if any, is not affected.
     *
     * @param outboundByteBufferSize
     */
    public void setOutboundByteBufferSize(int outboundByteBufferSize) {
        this.outboundByteBufferSize = outboundByteBufferSize;
    }


    public int getOutboundCharBufferSize() {
        return outboundCharBufferSize;
    }


    /**
     * This only applies to the {@link WsOutbound} instance returned from
     * {@link #getWsOutbound()} created by a subsequent call to
     * {@link #setUpgradeOutbound(UpgradeOutbound)}. The current
     * {@link WsOutbound} instance, if any, is not affected.
     *
     * @param outboundCharBufferSize
     */
    public void setOutboundCharBufferSize(int outboundCharBufferSize) {
        this.outboundCharBufferSize = outboundCharBufferSize;
    }


    @Override
    public final void setUpgradeOutbound(UpgradeOutbound upgradeOutbound) {
        outbound = new WsOutbound(upgradeOutbound, outboundByteBufferSize,
                outboundCharBufferSize);
    }


    @Override
    public final void setUpgradeProcessor(UpgradeProcessor<?> processor) {
        this.processor = processor;
    }


    /**
     * Obtain the outbound side of this WebSocket connection used for writing
     * data to the client.
     */
    public final WsOutbound getWsOutbound() {
        return outbound;
    }


    @Override
    public final SocketState onData() throws IOException {
        // Must be start the start of a message (which may consist of multiple
        // frames)

        WsInputStream wsIs = new WsInputStream(processor, getWsOutbound());
        WsFrame frame = wsIs.nextFrame(true);

        while (frame != null) {
            try {
                // TODO User defined extensions may define values for rsv
                if (frame.getRsv() > 0) {
                    closeOutboundConnection(
                            Constants.STATUS_PROTOCOL_ERROR, null);
                    return SocketState.CLOSED;
                }

                byte opCode = frame.getOpCode();

                if (opCode == Constants.OPCODE_BINARY) {
                    onBinaryData(wsIs);
                } else if (opCode == Constants.OPCODE_TEXT) {
                    InputStreamReader r =
                            new InputStreamReader(wsIs, new Utf8Decoder());
                    onTextData(r);
                } else if (opCode == Constants.OPCODE_CLOSE){
                    closeOutboundConnection(frame);
                    return SocketState.CLOSED;
                } else if (opCode == Constants.OPCODE_PING) {
                    getWsOutbound().pong(frame.getPayLoad());
                } else if (opCode == Constants.OPCODE_PONG) {
                    // NO-OP
                } else {
                    // Unknown OpCode
                    closeOutboundConnection(
                            Constants.STATUS_PROTOCOL_ERROR, null);
                    return SocketState.CLOSED;
                }
            } catch (MalformedInputException mie) {
                // Invalid UTF-8
                closeOutboundConnection(Constants.STATUS_BAD_DATA, null);
                return SocketState.CLOSED;
            } catch (UnmappableCharacterException uce) {
                // Invalid UTF-8
                closeOutboundConnection(Constants.STATUS_BAD_DATA, null);
                return SocketState.CLOSED;
            } catch (IOException ioe) {
                // Given something must have gone to reach this point, this
                // might not work but try it anyway.
                closeOutboundConnection(Constants.STATUS_PROTOCOL_ERROR, null);
                return SocketState.CLOSED;
            }
            frame = wsIs.nextFrame(false);
        }
        return SocketState.UPGRADED;
    }

    private void closeOutboundConnection(int status, ByteBuffer data) throws IOException {
        try {
            getWsOutbound().close(status, data);
        } finally {
            onClose(status);
        }
    }

    private void closeOutboundConnection(WsFrame frame) throws IOException {
        try {
            getWsOutbound().close(frame);
        } finally {
            onClose(Constants.OPCODE_CLOSE);
        }
    }

    @Override
    public void onUpgradeComplete() {
        onOpen(outbound);
    }

    /**
     * Intended to be overridden by sub-classes that wish to be notified
     * when the outbound connection is established. The default implementation
     * is a NO-OP.
     *
     * @param outbound    The outbound WebSocket connection.
     */
    protected void onOpen(WsOutbound outbound) {
        // NO-OP
    }

    /**
     * Intended to be overridden by sub-classes that wish to be notified
     * when the outbound connection is closed. The default implementation
     * is a NO-OP.
     *
     * @param status    The status code of the close reason.
     */
    protected void onClose(int status) {
        // NO-OP
    }


    /**
     * This method is called when there is a binary WebSocket message available
     * to process. The message is presented via a stream and may be formed from
     * one or more frames. The number of frames used to transmit the message is
     * not made visible to the application.
     *
     * @param is    The WebSocket message
     *
     * @throws IOException  If a problem occurs processing the message. Any
     *                      exception will trigger the closing of the WebSocket
     *                      connection.
     */
    protected abstract void onBinaryData(InputStream is) throws IOException;


    /**
     * This method is called when there is a textual WebSocket message available
     * to process. The message is presented via a reader and may be formed from
     * one or more frames. The number of frames used to transmit the message is
     * not made visible to the application.
     *
     * @param r     The WebSocket message
     *
     * @throws IOException  If a problem occurs processing the message. Any
     *                      exception will trigger the closing of the WebSocket
     *                      connection.
     */
    protected abstract void onTextData(Reader r) throws IOException;
}
