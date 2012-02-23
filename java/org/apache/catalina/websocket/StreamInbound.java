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
import java.nio.charset.CodingErrorAction;
import java.nio.charset.MalformedInputException;
import java.nio.charset.UnmappableCharacterException;

import org.apache.coyote.http11.upgrade.UpgradeInbound;
import org.apache.coyote.http11.upgrade.UpgradeOutbound;
import org.apache.coyote.http11.upgrade.UpgradeProcessor;
import org.apache.tomcat.util.buf.B2CConverter;
import org.apache.tomcat.util.net.AbstractEndpoint.Handler.SocketState;

public abstract class StreamInbound implements UpgradeInbound {

    private UpgradeProcessor<?> processor = null;
    private WsOutbound outbound;

    @Override
    public void setUpgradeOutbound(UpgradeOutbound upgradeOutbound) {
        outbound = new WsOutbound(upgradeOutbound);
    }


    @Override
    public void setUpgradeProcessor(UpgradeProcessor<?> processor) {
        this.processor = processor;
    }

    public WsOutbound getOutbound() {
        return outbound;
    }

    @Override
    public SocketState onData() throws IOException {
        // Must be start the start of a frame or series of frames

        try {
            WsInputStream wsIs = new WsInputStream(processor, outbound);

            WsFrame frame = wsIs.getFrame();

            // TODO User defined extensions may define values for rsv
            if (frame.getRsv() > 0) {
                getOutbound().close(1002, null);
                return SocketState.CLOSED;
            }

            byte opCode = frame.getOpCode();

            if (opCode == Constants.OPCODE_BINARY) {
                onBinaryData(wsIs);
                return SocketState.UPGRADED;
            } else if (opCode == Constants.OPCODE_TEXT) {
                InputStreamReader r = new InputStreamReader(wsIs,
                        B2CConverter.UTF_8.newDecoder()
                            .onMalformedInput(CodingErrorAction.REPORT)
                            .onUnmappableCharacter(CodingErrorAction.REPORT));
                onTextData(r);
                return SocketState.UPGRADED;
            }

            if (opCode == Constants.OPCODE_CLOSE){
                doClose(frame);
                return SocketState.CLOSED;
            } else if (opCode == Constants.OPCODE_PING) {
                doPing(frame);
                return SocketState.UPGRADED;
            } else if (opCode == Constants.OPCODE_PONG) {
                // NO-OP
                return SocketState.UPGRADED;
            }

            // Unknown OpCode
            getOutbound().close(1002, null);
            return SocketState.CLOSED;
        } catch (MalformedInputException mie) {
            // Invalid UTF-8
            getOutbound().close(1007, null);
            return SocketState.CLOSED;
        } catch (UnmappableCharacterException uce) {
            // Invalid UTF-8
            getOutbound().close(1007, null);
            return SocketState.CLOSED;
        } catch (IOException ioe) {
            // Given something must have gone to reach this point, this might
            // not work but try it anyway.
            getOutbound().close(1002, null);
            return SocketState.CLOSED;
        }
    }

    private void doClose(WsFrame frame) throws IOException {
        if (frame.getPayLoadLength() > 0) {
            // Must be status (2 bytes) plus optional message
            if (frame.getPayLoadLength() == 1) {
                throw new IOException();
            }
            int status = (frame.getPayLoad().get() & 0xFF) << 8;
            status += frame.getPayLoad().get() & 0xFF;
            getOutbound().close(status, frame.getPayLoad());
        } else {
            // No status
            getOutbound().close(0, null);
        }
    }

    private void doPing(WsFrame frame) throws IOException {
        getOutbound().pong(frame.getPayLoad());
    }

    protected abstract void onBinaryData(InputStream is) throws IOException;
    protected abstract void onTextData(Reader r) throws IOException;
}
