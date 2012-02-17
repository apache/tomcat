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

/**
 * This class does not represent the complete frame header, just those parts of
 * it that need to be exposed more widely than the {@link WsInputStream}.
 */
public class WsFrameHeader {

    private final boolean fin;
    private final boolean rsv1;
    private final boolean rsv2;
    private final boolean rsv3;
    private final byte opCode;

    public WsFrameHeader(int b) {
        fin = (b & 0x80) > 0;

        rsv1 = (b & 0x40) > 0;
        rsv2 = (b & 0x20) > 0;
        rsv3 = (b & 0x10) > 0;

        opCode = (byte) (b & 0x0F);
    }

    public boolean getFin() {
        return fin;
    }

    public boolean getRsv1() {
        return rsv1;
    }

    public boolean getRsv2() {
        return rsv2;
    }

    public boolean getRsv3() {
        return rsv3;
    }

    public byte getOpCode() {
        return opCode;
    }


}
