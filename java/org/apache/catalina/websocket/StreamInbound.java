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

import org.apache.catalina.util.Conversions;
import org.apache.coyote.http11.UpgradeInbound;
import org.apache.coyote.http11.UpgradeOutbound;
import org.apache.tomcat.util.buf.B2CConverter;
import org.apache.tomcat.util.net.AbstractEndpoint.Handler.SocketState;

public abstract class StreamInbound implements UpgradeInbound {

    // These attributes apply to the current frame being processed
    private boolean fin = true;
    private boolean rsv1 = false;
    private boolean rsv2 = false;
    private boolean rsv3 = false;
    private int opCode = -1;
    private long payloadLength = -1;

    // These attributes apply to the message that may be spread over multiple
    // frames
    // TODO

    private InputStream is = null;
    private WsOutbound outbound;

    @Override
    public void setUpgradeOutbound(UpgradeOutbound upgradeOutbound) {
        outbound = new WsOutbound(upgradeOutbound);
    }


    @Override
    public void setInputStream(InputStream is) {
        this.is = is;
    }

    public WsOutbound getStreamOutbound() {
        return outbound;
    }

    @Override
    public SocketState onData() throws IOException {
        // Must be start the start of a frame

        // Read the first byte
        int i = is.read();

        fin = (i & 0x80) > 0;

        rsv1 = (i & 0x40) > 0;
        rsv2 = (i & 0x20) > 0;
        rsv3 = (i & 0x10) > 0;

        if (rsv1 || rsv2 || rsv3) {
            // TODO: Not supported.
        }

        opCode = (i & 0x0F);
        validateOpCode(opCode);

        // Read the next byte
        i = is.read();

        // Client data must be masked and this isn't
        if ((i & 0x80) == 0) {
            // TODO: Better message
            throw new IOException();
        }

        payloadLength = i & 0x7F;
        if (payloadLength == 126) {
            byte[] extended = new byte[2];
            is.read(extended);
            payloadLength = Conversions.byteArrayToLong(extended);
        } else if (payloadLength == 127) {
            byte[] extended = new byte[8];
            is.read(extended);
            payloadLength = Conversions.byteArrayToLong(extended);
        }

        byte[] mask = new byte[4];
        is.read(mask);

        if (opCode == 1 || opCode == 2) {
            WsInputStream wsIs = new WsInputStream(is, mask, payloadLength);
            if (opCode == 2) {
                onBinaryData(wsIs);
            } else {
                InputStreamReader r =
                        new InputStreamReader(wsIs, B2CConverter.UTF_8);
                onTextData(r);
            }
        }

        // TODO: Doesn't currently handle multi-frame messages. That will need
        //       some refactoring.

        // TODO: Per frame extension handling is not currently supported.

        // TODO: Handle other control frames.

        // TODO: Handle control frames appearing in the middle of a multi-frame
        //       message

        return SocketState.UPGRADE;
    }

    protected abstract void onBinaryData(InputStream is) throws IOException;
    protected abstract void onTextData(Reader r) throws IOException;

    private void validateOpCode(int opCode) throws IOException {
        switch (opCode) {
        case 0:
        case 1:
        case 2:
        case 8:
        case 9:
        case 10:
            break;
        default:
            // TODO: Message
            throw new IOException();
        }
    }
}
