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

import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.buf.HexUtils;
import org.apache.tomcat.util.http.parser.HttpParser;
import org.apache.tomcat.util.net.openssl.ciphers.Cipher;
import org.apache.tomcat.util.res.StringManager;

/**
 * This class extracts the SNI host name and ALPN protocols from a TLS
 * client-hello message.
 */
public class TLSClientHelloExtractor {

    private static final Log log = LogFactory.getLog(TLSClientHelloExtractor.class);
    private static final StringManager sm = StringManager.getManager(TLSClientHelloExtractor.class);

    private final ExtractorResult result;
    private final List<Cipher> clientRequestedCiphers;
    private final List<String> clientRequestedCipherNames;
    private final String sniValue;
    private final List<String> clientRequestedApplicationProtocols;
    private final List<String> clientRequestedProtocols;

    private static final int TLS_RECORD_HEADER_LEN = 5;

    private static final int TLS_EXTENSION_SERVER_NAME = 0;
    private static final int TLS_EXTENSION_ALPN = 16;
    private static final int TLS_EXTENSION_SUPPORTED_VERSION = 43;

    public static byte[] USE_TLS_RESPONSE = ("HTTP/1.1 400 \r\n" +
            "Content-Type: text/plain;charset=UTF-8\r\n" +
            "Connection: close\r\n" +
            "\r\n" +
            "Bad Request\r\n" +
            "This combination of host and port requires TLS.\r\n").getBytes(StandardCharsets.UTF_8);


    /**
     * Creates the instance of the parser and processes the provided buffer. The
     * buffer position and limit will be modified during the execution of this
     * method but they will be returned to the original values before the method
     * exits.
     *
     * @param netInBuffer The buffer containing the TLS data to process
     * @throws IOException If the client hello message is malformed
     */
    public TLSClientHelloExtractor(ByteBuffer netInBuffer) throws IOException {
        // Buffer is in write mode at this point. Record the current position so
        // the buffer state can be restored at the end of this method.
        int pos = netInBuffer.position();
        int limit = netInBuffer.limit();
        ExtractorResult result = ExtractorResult.NOT_PRESENT;
        List<Cipher> clientRequestedCiphers = new ArrayList<>();
        List<String> clientRequestedCipherNames = new ArrayList<>();
        List<String> clientRequestedApplicationProtocols = new ArrayList<>();
        List<String> clientRequestedProtocols = new ArrayList<>();
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
                // Is the client trying to use clear text HTTP?
                if (isHttp(netInBuffer)) {
                    result = ExtractorResult.NON_SECURE;
                }
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
            String legacyVersion = readProtocol(netInBuffer);
            // Random
            skipBytes(netInBuffer, 32);
            // Session ID (single byte for length)
            skipBytes(netInBuffer, (netInBuffer.get() & 0xFF));

            // Cipher Suites
            // (2 bytes for length, each cipher ID is 2 bytes)
            int cipherCount = netInBuffer.getChar() / 2;
            for (int i = 0; i < cipherCount; i++) {
                char cipherId = netInBuffer.getChar();
                Cipher c = Cipher.valueOf(cipherId);
                // Some clients transmit grease values (see RFC 8701)
                if (c == null) {
                    clientRequestedCipherNames.add("Unknown(0x" + HexUtils.toHexString(cipherId) + ")");
                } else {
                    clientRequestedCiphers.add(c);
                    clientRequestedCipherNames.add(c.name());
                }
            }

            // Compression methods (single byte for length)
            skipBytes(netInBuffer, (netInBuffer.get() & 0xFF));

            if (!netInBuffer.hasRemaining()) {
                // No more data means no extensions present
                return;
            }

            // Extension length
            skipBytes(netInBuffer, 2);
            // Read the extensions until we run out of data or find the data
            // we need
            while (netInBuffer.hasRemaining() && (sniValue == null ||
                    clientRequestedApplicationProtocols.isEmpty() || clientRequestedProtocols.isEmpty())) {
                // Extension type is two byte
                char extensionType = netInBuffer.getChar();
                // Extension size is another two bytes
                char extensionDataSize = netInBuffer.getChar();
                switch (extensionType) {
                case TLS_EXTENSION_SERVER_NAME: {
                    sniValue = readSniExtension(netInBuffer);
                    break;
                }
                case TLS_EXTENSION_ALPN:
                    readAlpnExtension(netInBuffer, clientRequestedApplicationProtocols);
                    break;
                case TLS_EXTENSION_SUPPORTED_VERSION:
                    readSupportedVersions(netInBuffer, clientRequestedProtocols);
                    break;
                default: {
                    skipBytes(netInBuffer, extensionDataSize);
                }
                }
            }
            if (clientRequestedProtocols.isEmpty()) {
                clientRequestedProtocols.add(legacyVersion);
            }
            result = ExtractorResult.COMPLETE;
        } catch (BufferUnderflowException | IllegalArgumentException e) {
            throw new IOException(sm.getString("sniExtractor.clientHelloInvalid"), e);
        } finally {
            this.result = result;
            this.clientRequestedCiphers = clientRequestedCiphers;
            this.clientRequestedCipherNames = clientRequestedCipherNames;
            this.clientRequestedApplicationProtocols = clientRequestedApplicationProtocols;
            this.sniValue = sniValue;
            this.clientRequestedProtocols = clientRequestedProtocols;
            // Whatever happens, return the buffer to its original state
            netInBuffer.limit(limit);
            netInBuffer.position(pos);
        }
    }


    public ExtractorResult getResult() {
        return result;
    }


    /**
     * @return The SNI value provided by the client converted to lower case if
     *         not already lower case.
     */
    public String getSNIValue() {
        if (result == ExtractorResult.COMPLETE) {
            return sniValue;
        } else {
            throw new IllegalStateException(sm.getString("sniExtractor.tooEarly"));
        }
    }


    public List<Cipher> getClientRequestedCiphers() {
        if (result == ExtractorResult.COMPLETE || result == ExtractorResult.NOT_PRESENT) {
            return clientRequestedCiphers;
        } else {
            throw new IllegalStateException(sm.getString("sniExtractor.tooEarly"));
        }
    }


    public List<String> getClientRequestedCipherNames() {
        if (result == ExtractorResult.COMPLETE || result == ExtractorResult.NOT_PRESENT) {
            return clientRequestedCipherNames;
        } else {
            throw new IllegalStateException(sm.getString("sniExtractor.tooEarly"));
        }
    }


    public List<String> getClientRequestedApplicationProtocols() {
        if (result == ExtractorResult.COMPLETE || result == ExtractorResult.NOT_PRESENT) {
            return clientRequestedApplicationProtocols;
        } else {
            throw new IllegalStateException(sm.getString("sniExtractor.tooEarly"));
        }
    }


    public List<String> getClientRequestedProtocols() {
        if (result == ExtractorResult.COMPLETE || result == ExtractorResult.NOT_PRESENT) {
            return clientRequestedProtocols;
        } else {
            throw new IllegalStateException(sm.getString("sniExtractor.tooEarly"));
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


    private static boolean isHttp(ByteBuffer bb) {
        // Based on code in Http11InputBuffer
        // Note: The actual request is not important. This code only checks that
        //       the buffer contains a correctly formatted HTTP request line.
        //       The method, target and protocol are not validated.
        byte chr = 0;
        bb.position(0);

        // Skip blank lines
        do {
            if (!bb.hasRemaining()) {
                return false;
            }
            chr = bb.get();
        } while (chr == '\r' || chr == '\n');

        // Read the method
        do {
            if (!HttpParser.isToken(chr) || !bb.hasRemaining()) {
                return false;
            }
            chr = bb.get();
        } while (chr != ' ' && chr != '\t');

        // Whitespace between method and target
        while (chr == ' ' || chr == '\t') {
            if (!bb.hasRemaining()) {
                return false;
            }
            chr = bb.get();
        }

        // Read the target
        while (chr != ' ' && chr != '\t') {
            if (HttpParser.isNotRequestTarget(chr) || !bb.hasRemaining()) {
                return false;
            }
            chr = bb.get();
        }

        // Whitespace between target and protocol
        while (chr == ' ' || chr == '\t') {
            if (!bb.hasRemaining()) {
                return false;
            }
            chr = bb.get();
        }

        // Read protocol
        do {
            if (!HttpParser.isHttpProtocol(chr) || !bb.hasRemaining()) {
                return false;
            }
            chr = bb.get();

        } while (chr != '\r' && chr != '\n');

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


    private static String readProtocol(ByteBuffer bb) {
        char protocol = bb.getChar();
        switch (protocol) {
            case 0x0300: {
                return Constants.SSL_PROTO_SSLv3;
            }
            case 0x0301: {
                return Constants.SSL_PROTO_TLSv1_0;
            }
            case 0x0302: {
                return Constants.SSL_PROTO_TLSv1_1;
            }
            case 0x0303: {
                return Constants.SSL_PROTO_TLSv1_2;
            }
            case 0x0304: {
                return Constants.SSL_PROTO_TLSv1_3;
            }
            default:
                return "Unknown(0x" + HexUtils.toHexString(protocol) + ")";
        }
    }


    private static String readSniExtension(ByteBuffer bb) {
        // First 2 bytes are size of server name list (only expecting one)
        // Next byte is type (0 for hostname)
        skipBytes(bb, 3);
        // Next 2 bytes are length of host name
        char serverNameSize = bb.getChar();
        byte[] serverNameBytes = new byte[serverNameSize];
        bb.get(serverNameBytes);
        return new String(serverNameBytes, StandardCharsets.UTF_8).toLowerCase(Locale.ENGLISH);
    }


    private static void readAlpnExtension(ByteBuffer bb, List<String> protocolNames) {
        // First 2 bytes are size of the protocol list
        char toRead = bb.getChar();
        byte[] inputBuffer = new byte[255];
        while (toRead > 0) {
            // Each list entry has one byte for length followed by a string of
            // that length
            int len = bb.get() & 0xFF;
            bb.get(inputBuffer, 0, len);
            protocolNames.add(new String(inputBuffer, 0, len, StandardCharsets.UTF_8));
            toRead--;
            toRead -= len;
        }
    }


    private static void readSupportedVersions(ByteBuffer bb, List<String> protocolNames) {
        // First byte is the size of the list in bytes
        int count = (bb.get() & 0xFF) / 2;
        // Then the list of protocols
        for (int i = 0; i < count; i++) {
            protocolNames.add(readProtocol(bb));
        }
    }


    public enum ExtractorResult {
        COMPLETE,
        NOT_PRESENT,
        UNDERFLOW,
        NEED_READ,
        NON_SECURE
    }
}
