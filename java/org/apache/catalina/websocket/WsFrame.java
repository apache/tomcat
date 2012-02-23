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
import java.nio.ByteBuffer;

import org.apache.catalina.util.Conversions;
import org.apache.coyote.http11.upgrade.UpgradeProcessor;

/**
 * Represents a WebSocket frame with the exception of the payload for
 * non-control frames.
 */
public class WsFrame {

    private final boolean fin;
    private final int rsv;
    private final byte opCode;
    private int[] mask = new int[4];
    private long payloadLength;
    private ByteBuffer payload;

    public WsFrame(UpgradeProcessor<?> processor) throws IOException {

        int b = processorRead(processor);
        fin = (b & 0x80) > 0;
        rsv = (b & 0x70) >>> 4;
        opCode = (byte) (b & 0x0F);

        b = processorRead(processor);
        // Client data must be masked
        if ((b & 0x80) == 0) {
            // TODO: StringManager / i18n
            throw new IOException("Client frame not masked");
        }

        payloadLength = b & 0x7F;
        if (payloadLength == 126) {
            byte[] extended = new byte[2];
            processorRead(processor, extended);
            payloadLength = Conversions.byteArrayToLong(extended);
        } else if (payloadLength == 127) {
            byte[] extended = new byte[8];
            processorRead(processor, extended);
            payloadLength = Conversions.byteArrayToLong(extended);
        }

        boolean isControl = (opCode & 0x08) > 0;

        if (isControl) {
            if (payloadLength > 125) {
                throw new IOException();
            }
            if (!fin) {
                throw new IOException();
            }
        }

        for (int j = 0; j < mask.length; j++) {
            mask[j] = processorRead(processor) & 0xFF;
        }

        if (isControl) {
            // Note: Payload limited to <= 125 bytes by test above
            payload = ByteBuffer.allocate((int) payloadLength);
            processorRead(processor, payload);
        } else {
            payload = null;
        }
    }

    public boolean getFin() {
        return fin;
    }

    public int getRsv() {
        return rsv;
    }

    public byte getOpCode() {
        return opCode;
    }

    public int[] getMask() {
        return mask;
    }

    public long getPayLoadLength() {
        return payloadLength;
    }

    public ByteBuffer getPayLoad() {
        return payload;
    }


    // ----------------------------------- Guaranteed read methods for processor

    private int processorRead(UpgradeProcessor<?> processor)
            throws IOException {
        int result = processor.read();
        if (result == -1) {
            // TODO i18n
            throw new IOException("End of stream before end of frame");
        }
        return result;
    }


    private void processorRead(UpgradeProcessor<?> processor, byte[] bytes)
            throws IOException {
        int read = 0;
        int last = 0;
        while (read < bytes.length) {
            last = processor.read(bytes, read, bytes.length - read);
            if (last == -1) {
                // TODO i18n
                throw new IOException("End of stream before end of frame");
            }
            read += last;
        }
    }


    /*
     * Intended to read whole payload. Therefore able to unmask.
     */
    private void processorRead(UpgradeProcessor<?> processor, ByteBuffer bb)
            throws IOException {
        int last = 0;
        while (bb.hasRemaining()) {
            last = processor.read();
            if (last == -1) {
                // TODO i18n
                throw new IOException("End of stream before end of frame");
            }
            bb.put((byte) (last ^ mask[bb.position() % 4]));
        }
        bb.flip();
    }
}
