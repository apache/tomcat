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
import java.nio.charset.StandardCharsets;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.res.StringManager;

public class SNIExtractor {

    private static final Log log = LogFactory.getLog(SNIExtractor.class);
    private static final StringManager sm = StringManager.getManager(SNIExtractor.class);

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

            if (!isTLSHandshake(netInBuffer)) {
                return;
            }

            int recordSizeToRead = recordSizeToRead(netInBuffer);
            if (recordSizeToRead == -1) {
                // Not enough data in the buffer for the full record
                if (netInBuffer.limit() == netInBuffer.capacity()) {
                    // Buffer too small
                    result = SNIResult.UNDERFLOW;
                    return;
                } else {
                    // Need to read more data
                    result = SNIResult.NEED_READ;
                    return;
                }

            }

            if (!isClientHello(netInBuffer)) {
                return;
            }

            int clientHelloSizeToRead = clientHelloSize(netInBuffer);
            if (clientHelloSizeToRead == -1) {
                // Client hello can't have fitted into single TLS record.
                // Treat this as not present.
                log.warn(sm.getString("sniExtractor.clientHelloTooBig"));
                return;
            }

            // Protocol Version (2 bytes)
            netInBuffer.getChar();
            swallowRandom(netInBuffer);
            // Session ID
            swallowUnit8Vector(netInBuffer);
            swallowCipherSuites(netInBuffer);
            // Compression methods
            swallowUnit8Vector(netInBuffer);

            if (!netInBuffer.hasRemaining()) {
                // No more data means no extensions present
                return;
            }

            // Extension length
            netInBuffer.getChar();
            // Read th eextensions until we run out of data or find the SNI
            while (netInBuffer.hasRemaining() && sniValue == null) {
                sniValue = readSniExtension(netInBuffer);
            }
            if (sniValue != null) {
                result = SNIResult.FOUND;
            }
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


    private static boolean isTLSHandshake(ByteBuffer bb) {
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


    private static int recordSizeToRead(ByteBuffer bb) {
        // Next two bytes (unsigned) are the size of the record. We need all of
        // it.
        int size = bb.getChar();
        if (bb.remaining() < size) {
            return -1;
        }
        return size;
    }


    private static boolean isClientHello(ByteBuffer bb) {
        // Client hello is handshake type 1
        if (bb.get() == 1) {
            return true;
        }
        return false;
    }


    private static int clientHelloSize(ByteBuffer bb) {
        // Next three bytes (unsigned) are the size of the client hello. We need
        // all of it.
        int size = ((bb.get() & 0xFF) << 16) + ((bb.get() & 0xFF) << 8) + (bb.get() & 0xFF);
        if (bb.remaining() < size) {
            return -1;
        }
        return size;
    }

    private static void swallowRandom(ByteBuffer bb) {
        // 32 bytes total
        for (int i = 0; i < 4; i++) {
            bb.getLong();
        }
    }

    private static void swallowCipherSuites(ByteBuffer bb) {
        char c = bb.getChar();
        for (int i = 0; i < c; i++) {
            bb.get();
        }
    }


    private static void swallowUnit8Vector(ByteBuffer bb) {
        int b = bb.get() & 0xFF;
        for (int i = 0; i < b; i++) {
            bb.get();
        }
    }


    private static String readSniExtension(ByteBuffer bb) {
        // SNI extension is type 0
        char extensionType = bb.getChar();
        // Next byte is data size
        char extensionDataSize = bb.getChar();
        if (extensionType == 0) {
            // First 2 bytes are size of server name list (only expecting one)
            bb.getChar();
            // Next byte is type (0 for hostname)
            bb.get();
            // Next 2 bytes are length of host name
            char serverNameSize = bb.getChar();
            byte[] serverNameBytes = new byte[serverNameSize];
            bb.get(serverNameBytes);
            return new String(serverNameBytes, StandardCharsets.UTF_8);
        } else {
            for (int i = 0; i < extensionDataSize; i++) {
                bb.get();
            }
        }
        return null;
    }

    public static enum SNIResult {
        FOUND,
        NOT_PRESENT,
        UNDERFLOW,
        NEED_READ
    }
}
