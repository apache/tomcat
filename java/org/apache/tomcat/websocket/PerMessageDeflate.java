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
package org.apache.tomcat.websocket;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

import javax.websocket.Extension;
import javax.websocket.Extension.Parameter;

import org.apache.tomcat.util.res.StringManager;

public class PerMessageDeflate implements Transformation {

    private static final StringManager sm = StringManager.getManager(Constants.PACKAGE_NAME);

    private static final String SERVER_NO_CONTEXT_TAKEOVER = "server_no_context_takeover";
    private static final String CLIENT_NO_CONTEXT_TAKEOVER = "client_no_context_takeover";
    private static final String SERVER_MAX_WINDOW_BITS = "server_max_window_bits";
    private static final String CLIENT_MAX_WINDOW_BITS = "client_max_window_bits";

    private static final int RSV_BITMASK = 0b100;
    private static final byte[] EOM_BYTES = new byte[] {0, 0, -1, -1};

    public static final String NAME = "permessage-deflate";

    private boolean serverContextTakeover = true;
    private boolean clientContextTakeover = true;

    private final Inflater inflator;
    private final ByteBuffer readBuffer = ByteBuffer.allocate(8192);

    private Transformation next;

    PerMessageDeflate(List<Parameter> params) {

        for (Parameter param : params) {
            if (SERVER_NO_CONTEXT_TAKEOVER.equals(param.getName())) {
                serverContextTakeover = false;
            } else if (CLIENT_NO_CONTEXT_TAKEOVER.equals(param.getName())) {
                clientContextTakeover = false;
            } else if (SERVER_MAX_WINDOW_BITS.equals(param.getName())) {
                int bits = Integer.parseInt(param.getValue());
                if (bits < 8 || bits > 15) {
                    throw new IllegalArgumentException(sm.getString(
                            "perMessageDeflate.invalidWindowSize",
                            SERVER_MAX_WINDOW_BITS, Integer.valueOf(bits)));
                }
                // Java SE API (as of Java 8) does not expose the API to control
                // the Window size so decline this option by not including it in
                // the response
            } else if (CLIENT_MAX_WINDOW_BITS.equals(param.getName())) {
                if (param.getValue() != null) {
                    int bits = Integer.parseInt(param.getValue());
                    if (bits < 8 || bits > 15) {
                        throw new IllegalArgumentException(sm.getString(
                                "perMessageDeflate.invalidWindowSize",
                                CLIENT_MAX_WINDOW_BITS, Integer.valueOf(bits)));
                    }
                }
                // Java SE API (as of Java 8) does not expose the API to control
                // the Window size so decline this option by not including it in
                // the response
            } else {
                // Unknown parameter
                throw new IllegalArgumentException(sm.getString(
                        "perMessageDeflate.unknownParameter", param.getName()));
            }
        }

        inflator = new Inflater(true);
    }

    @Override
    public boolean getMoreData(byte opCode, int rsv, ByteBuffer dest) throws IOException {

        // Control frames are never compressed
        if (Util.isControl(opCode) || (rsv & RSV_BITMASK) == 0) {
            return next.getMoreData(opCode, rsv, dest);
        }

        boolean endOfInputFrame = false;

        if (inflator.needsInput()) {
            readBuffer.clear();
            endOfInputFrame = next.getMoreData(opCode, (rsv ^ RSV_BITMASK), readBuffer);
            inflator.setInput(readBuffer.array(), readBuffer.arrayOffset(), readBuffer.position());
        }

        int written = 0;
        try {
            written = inflator.inflate(dest.array(), dest.arrayOffset() + dest.position(), dest.remaining());
            if (endOfInputFrame && !inflator.finished()) {
                inflator.setInput(EOM_BYTES);
                inflator.inflate(dest.array(), dest.arrayOffset() + dest.position(), dest.remaining());
            }
        } catch (DataFormatException e) {
            throw new IOException(sm.getString("perMessageDeflate.deflateFailed"), e);
        }
        dest.position(dest.position() + written);


        if (endOfInputFrame && !clientContextTakeover) {
            inflator.reset();
        }

        return endOfInputFrame;
    }

    @Override
    public boolean validateRsv(int rsv, byte opCode) {
        if (Util.isControl(opCode)) {
            if ((rsv & RSV_BITMASK) > 0) {
                return false;
            } else {
                if (next == null) {
                    return true;
                } else {
                    return next.validateRsv(rsv, opCode);
                }
            }
        } else {
            int rsvNext = rsv;
            if ((rsv & RSV_BITMASK) > 0) {
                rsvNext = rsv ^ RSV_BITMASK;
            }
            if (next == null) {
                return true;
            } else {
                return next.validateRsv(rsvNext, opCode);
            }
        }
    }

    @Override
    public Extension getExtensionResponse() {
        Extension result = new WsExtension(NAME);

        List<Extension.Parameter> params = result.getParameters();

        if (!serverContextTakeover) {
            params.add(new WsExtensionParameter(SERVER_NO_CONTEXT_TAKEOVER, null));
        }
        if (!clientContextTakeover) {
            params.add(new WsExtensionParameter(CLIENT_NO_CONTEXT_TAKEOVER, null));
        }

        return result;
    }

    @Override
    public void setNext(Transformation t) {
        if (next == null) {
            this.next = t;
        } else {
            next.setNext(t);
        }
    }

    @Override
    public boolean validateRsvBits(int i) {
        if ((i & RSV_BITMASK) > 0) {
            return false;
        }
        if (next == null) {
            return true;
        } else {
            return next.validateRsvBits(i | RSV_BITMASK);
        }
    }
}
