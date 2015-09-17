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
import java.util.ArrayList;
import java.util.List;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.net.jsse.openssl.Cipher;
import org.apache.tomcat.util.res.StringManager;

/**
 * This class extracts the SNI host name from a TLS client-hello message.
 */
public class TLSClientHelloExtractor {

    private static final Log log = LogFactory.getLog(TLSClientHelloExtractor.class);
    private static final StringManager sm = StringManager.getManager(TLSClientHelloExtractor.class);

    private final ExtractorResult result;
    private final List<Cipher> clientRequestedCiphers;
    private final String sniValue;

    private static final int TLS_RECORD_HEADER_LEN = 5;


    /**
     * Creates the instance of the parser and processes the provided buffer. The
     * buffer position and limit will be modified during the execution of this
     * method but they will be returned to the original values before the method
     * exits.
     *
     * @param netInBuffer The buffer containing the TLS data to process
     */
    public TLSClientHelloExtractor(ByteBuffer netInBuffer) {
        // TODO: Detect use of http on a secure connection and provide a simple
        //       error page.

        // Buffer is in write mode at this point. Record the current position so
        // the buffer state can be restored at the end of this method.
        int pos = netInBuffer.position();
        int limit = netInBuffer.limit();
        ExtractorResult result = ExtractorResult.NOT_PRESENT;
        List<Cipher> clientRequestedCiphers = new ArrayList<>();
        String sniValue = null;
        try {
            // Switch to read mode.
            netInBuffer.flip();

            // A complete TLS record header is required before we can figure out
            // how many bytes there are in the record.
            if (!isAvailable(netInBuffer, TLS_RECORD_HEADER_LEN)) {
                result = handleIncompleteRead(netInBuffer);
                return;
            }

            if (!isTLSHandshake(netInBuffer)) {
                return;
            }

            if (!isAllRecordAvailable(netInBuffer)) {
                result = handleIncompleteRead(netInBuffer);
                return;
            }

            if (!isClientHello(netInBuffer)) {
                return;
            }

            if (!isAllClientHelloAvailable(netInBuffer)) {
                // Client hello didn't fit into single TLS record.
                // Treat this as not present.
                log.warn(sm.getString("sniExtractor.clientHelloTooBig"));
                return;
            }

            // Protocol Version
            skipBytes(netInBuffer, 2);
            // Random
            skipBytes(netInBuffer, 32);
            // Session ID (single byte for length)
            skipBytes(netInBuffer, (netInBuffer.get() & 0xFF));

            // Cipher Suites
            // (2 bytes for length, each cipher ID is 2 bytes)
            int cipherCount = netInBuffer.getChar() / 2;
            for (int i = 0; i < cipherCount; i++) {
                int cipherId = netInBuffer.getChar();
                clientRequestedCiphers.add(Cipher.valueOf(cipherId));
            }

            // Compression methods (single byte for length)
            skipBytes(netInBuffer, (netInBuffer.get() & 0xFF));

            if (!netInBuffer.hasRemaining()) {
                // No more data means no extensions present
                return;
            }

            // Extension length
            skipBytes(netInBuffer, 2);
            // Read the extensions until we run out of data or find the SNI
            while (netInBuffer.hasRemaining() && sniValue == null) {
                sniValue = readSniExtension(netInBuffer);
            }
            if (sniValue != null) {
                result = ExtractorResult.COMPLETE;
            }
        } finally {
            this.result = result;
            this.clientRequestedCiphers = clientRequestedCiphers;
            this.sniValue = sniValue;
            // Whatever happens, return the buffer to its original state
            netInBuffer.limit(limit);
            netInBuffer.position(pos);
        }
    }


    public ExtractorResult getResult() {
        return result;
    }


    public String getSNIValue() {
        if (result == ExtractorResult.COMPLETE) {
            return sniValue;
        } else {
            throw new IllegalStateException();
        }
    }


    public List<Cipher> getClientRequestedCiphers() {
        if (result == ExtractorResult.COMPLETE || result == ExtractorResult.NOT_PRESENT) {
            return clientRequestedCiphers;
        } else {
            throw new IllegalStateException();
        }
    }


    private static ExtractorResult handleIncompleteRead(ByteBuffer bb) {
        if (bb.limit() == bb.capacity()) {
            // Buffer not big enough
            return ExtractorResult.UNDERFLOW;
        } else {
            // Need to read more data into buffer
            return ExtractorResult.NEED_READ;
        }
    }


    private static boolean isAvailable(ByteBuffer bb, int size) {
        if (bb.remaining() < size) {
            bb.position(bb.limit());
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


    private static boolean isAllRecordAvailable(ByteBuffer bb) {
        // Next two bytes (unsigned) are the size of the record. We need all of
        // it.
        int size = bb.getChar();
        return isAvailable(bb, size);
    }


    private static boolean isClientHello(ByteBuffer bb) {
        // Client hello is handshake type 1
        if (bb.get() == 1) {
            return true;
        }
        return false;
    }


    private static boolean isAllClientHelloAvailable(ByteBuffer bb) {
        // Next three bytes (unsigned) are the size of the client hello. We need
        // all of it.
        int size = ((bb.get() & 0xFF) << 16) + ((bb.get() & 0xFF) << 8) + (bb.get() & 0xFF);
        return isAvailable(bb, size);
    }


    private static void skipBytes(ByteBuffer bb, int size) {
        bb.position(bb.position() + size);
    }


    private static String readSniExtension(ByteBuffer bb) {
        // SNI extension is type 0
        char extensionType = bb.getChar();
        // Next byte is data size
        char extensionDataSize = bb.getChar();
        if (extensionType == 0) {
            // First 2 bytes are size of server name list (only expecting one)
            // Next byte is type (0 for hostname)
            skipBytes(bb, 3);
            // Next 2 bytes are length of host name
            char serverNameSize = bb.getChar();
            byte[] serverNameBytes = new byte[serverNameSize];
            bb.get(serverNameBytes);
            return new String(serverNameBytes, StandardCharsets.UTF_8);
        } else {
            skipBytes(bb, extensionDataSize);
        }
        return null;
    }


    public static enum ExtractorResult {
        COMPLETE,
        NOT_PRESENT,
        UNDERFLOW,
        NEED_READ
    }
}
