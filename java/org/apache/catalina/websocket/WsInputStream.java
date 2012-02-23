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

import org.apache.coyote.http11.upgrade.UpgradeProcessor;

/**
 * This class is used to read WebSocket frames from the underlying socket and
 * makes the payload available for reading as an {@link InputStream}. It only
 * makes the number of bytes declared in the payload length available for
 * reading even if more bytes are available from the socket.
 */
public class WsInputStream extends java.io.InputStream {

    private UpgradeProcessor<?> processor;
    private WsFrame frame;

    private long remaining;
    private long readThisFragment;

    public WsInputStream(UpgradeProcessor<?> processor) throws IOException {
        this.processor = processor;
        processFrame();
    }

    public WsFrame getFrame() {
        return frame;
    }

    private void processFrame() throws IOException {
        frame = new WsFrame(processor);
        readThisFragment = 0;
        remaining = frame.getPayLoadLength();
    }


    // ----------------------------------------------------- InputStream methods

    @Override
    public int read() throws IOException {
        while (remaining == 0 && !getFrame().getFin()) {
            // Need more data - process next frame
            processFrame();

            if (getFrame().getOpCode() != Constants.OPCODE_CONTINUATION) {
                // TODO i18n
                throw new IOException("Not a continuation frame");
            }
        }

        if (remaining == 0) {
            return -1;
        }

        remaining--;
        readThisFragment++;

        int masked = processor.read();
        if(masked == -1) {
            return -1;
        }
        return masked ^ frame.getMask()[(int) ((readThisFragment - 1) % 4)];
    }
}
