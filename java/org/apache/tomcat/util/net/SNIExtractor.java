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
package org.apache.tomcat.util.net;

import java.nio.ByteBuffer;

public class SNIExtractor {

    private final SNIResult result;
    private final String sniValue;

    private static final int TLS_RECORD_HEADER_LEN = 5;


    public SNIExtractor(ByteBuffer netInBuffer) {
        // TODO: Detect use of http on a secure connection and provide a simple
        //       error page.

        int pos = netInBuffer.position();
        SNIResult result = SNIResult.NOT_PRESENT;
        String sniValue = null;
        try {
            netInBuffer.flip();

            if (!isRecordHeaderComplete(netInBuffer)) {
                result = SNIResult.UNDERFLOW;
                return;
            }

            if (!isTLSClientHello(netInBuffer)) {
                return;
            }

            if (!isAllRecordPresent(netInBuffer)) {
                result = SNIResult.UNDERFLOW;
                return;
            }

            System.out.println("Looking good so far to find some SNI data");
            // TODO Parse the remainder of the data

        } finally {
            this.result = result;
            this.sniValue = sniValue;
            // Whatever happens, return the buffer to its original state
            netInBuffer.limit(netInBuffer.capacity());
            netInBuffer.position(pos);
        }
    }


    public SNIResult getResult() {
        return result;
    }


    public String getSNIValue() {
        if (result == SNIResult.FOUND) {
            return sniValue;
        } else {
            throw new IllegalStateException();
        }
    }


    private static boolean isRecordHeaderComplete(ByteBuffer bb) {
        if (bb.remaining() < TLS_RECORD_HEADER_LEN) {
            return false;
        }
        return true;
    }


    private static boolean isTLSClientHello(ByteBuffer bb) {
        // For a TLS client hello the first byte must be 22 - handshake
        if (bb.get() != 22) {
            return false;
        }
        // Next two bytes are major/minor version. We need at least 3.1.
        byte b2 = bb.get();
        byte b3 = bb.get();
        if (b2 < 3 || b2 == 3 && b3 == 0) {
            return false;
        }
        return true;
    }


    private static boolean isAllRecordPresent(ByteBuffer bb) {
        // Next two bytes (unsigned) are the size of the record. We need all of
        // it.
        if (bb.getChar() > bb.remaining()) {
            return false;
        }
        return true;
    }

    public static enum SNIResult {
        FOUND,
        NOT_PRESENT,
        UNDERFLOW,
        ERROR
    }
}
