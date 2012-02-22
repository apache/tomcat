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
        WsInputStream wsIs = new WsInputStream(processor);

        WsFrameHeader header = wsIs.getFrameHeader();

        // TODO User defined extensions may define values for rsv
        if (header.getRsv() > 0) {
            getOutbound().close(1002, null);
            return SocketState.CLOSED;
        }

        byte opCode = header.getOpCode();

        if (opCode == Constants.OPCODE_BINARY) {
            onBinaryData(wsIs);
            return SocketState.UPGRADED;
        } else if (opCode == Constants.OPCODE_TEXT) {
            InputStreamReader r =
                    new InputStreamReader(wsIs, B2CConverter.UTF_8);
            onTextData(r);
            return SocketState.UPGRADED;
        }

        // Must be a control frame and control frames:
        // - have a limited payload length
        // - must not be fragmented
        if (wsIs.getPayloadLength() > 125 || !wsIs.getFrameHeader().getFin()) {
            getOutbound().close(1002, null);
            return SocketState.CLOSED;
        }

        if (opCode == Constants.OPCODE_CLOSE){
            doClose(wsIs);
            return SocketState.CLOSED;
        } else if (opCode == Constants.OPCODE_PING) {
            doPing(wsIs);
            return SocketState.UPGRADED;
        } else if (opCode == Constants.OPCODE_PONG) {
            doPong(wsIs);
            return SocketState.UPGRADED;
        }

        getOutbound().close(1002, null);
        return SocketState.CLOSED;
    }

    private void doClose(InputStream is) throws IOException {
        // Control messages have a max size of 125 bytes. Need to try and read
        // one more so we reach end of stream (less 2 for the status). Note that
        // the 125 byte limit is enforced in #onData() before this method is
        // ever called.
        ByteBuffer data = ByteBuffer.allocate(124);

        int status = is.read();
        if (status != -1) {
            status = status << 8;
            int i = is.read();
            if (i == -1) {
                // EOF during middle of close message. Closing anyway but set
                // close code to protocol error
                status = 1002;
            } else {
                status = status + i;
                int read = 0;
                while (read > -1) {
                    data.position(data.position() + read);
                    read = is.read(data.array(), data.position(),
                            data.remaining());
                }
            }
        } else {
            status = 0;
        }
        data.flip();
        getOutbound().close(status, data);
    }

    private void doPing(InputStream is) throws IOException {
        // Control messages have a max size of 125 bytes. Need to try and read
        // one more so we reach end of stream. Note that the 125 byte limit is
        // enforced in #onData() before this method is ever called.
        ByteBuffer data = ByteBuffer.allocate(126);

        int read = 0;
        while (read > -1) {
            data.position(data.position() + read);
            read = is.read(data.array(), data.position(), data.remaining());
        }

        data.flip();
        getOutbound().pong(data);
    }

    private void doPong(InputStream is) throws IOException {
        // Unsolicited pong - swallow it
        // Control messages have a max size of 125 bytes. Note that the 125 byte
        // limit is enforced in #onData() before this method is ever called so
        // the loop below is not unbounded.
        int read = 0;
        while (read > -1) {
            read = is.read();
        }
    }

    protected abstract void onBinaryData(InputStream is) throws IOException;
    protected abstract void onTextData(Reader r) throws IOException;
}
