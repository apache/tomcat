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
package org.apache.tomcat.util.http.fileupload.util.mime;

import java.io.IOException;
import java.io.OutputStream;

/**
 * @since 1.3
 */
final class Base64Decoder {

    /**
     * Set up the encoding table.
     */
    private static final byte[] ENCODING_TABLE = {
        (byte) 'A', (byte) 'B', (byte) 'C', (byte) 'D', (byte) 'E', (byte) 'F', (byte) 'G',
        (byte) 'H', (byte) 'I', (byte) 'J', (byte) 'K', (byte) 'L', (byte) 'M', (byte) 'N',
        (byte) 'O', (byte) 'P', (byte) 'Q', (byte) 'R', (byte) 'S', (byte) 'T', (byte) 'U',
        (byte) 'V', (byte) 'W', (byte) 'X', (byte) 'Y', (byte) 'Z',
        (byte) 'a', (byte) 'b', (byte) 'c', (byte) 'd', (byte) 'e', (byte) 'f', (byte) 'g',
        (byte) 'h', (byte) 'i', (byte) 'j', (byte) 'k', (byte) 'l', (byte) 'm', (byte) 'n',
        (byte) 'o', (byte) 'p', (byte) 'q', (byte) 'r', (byte) 's', (byte) 't', (byte) 'u',
        (byte) 'v', (byte) 'w', (byte) 'x', (byte) 'y', (byte) 'z',
        (byte) '0', (byte) '1', (byte) '2', (byte) '3', (byte) '4', (byte) '5', (byte) '6',
        (byte) '7', (byte) '8', (byte) '9',
        (byte) '+', (byte) '/'
    };

    /**
     * The padding byte.
     */
    private static final byte PADDING = (byte) '=';

    /**
     * Set up the decoding table; this is indexed by a byte converted to an int,
     * so must be at least as large as the number of different byte values,
     * positive and negative and zero.
     */
    private static final byte[] DECODING_TABLE = new byte[Byte.MAX_VALUE - Byte.MIN_VALUE + 1];

    static {
        for (int i = 0; i < ENCODING_TABLE.length; i++) {
            DECODING_TABLE[ENCODING_TABLE[i]] = (byte) i;
        }
    }

    /**
     * Hidden constructor, this class must not be instantiated.
     */
    private Base64Decoder() {
        // do nothing
    }

    /**
     * Checks if the input char must be skipped from the decode.
     * The method skips whitespace characters LF, CR, horizontal tab and space.
     *
     * @param c the char to be checked.
     * @return true, if the input char has to be skipped, false otherwise.
     */
    private static boolean ignore(char c) {
        return (c == '\n' || c == '\r' || c == '\t' || c == ' ');
    }

    /**
     * Decode the base 64 encoded byte data writing it to the given output stream,
     * whitespace characters will be ignored.
     *
     * @param data the buffer containing the Base64-encoded data
     * @param off the start offset (zero-based)
     * @param length the number of bytes to convert
     * @param out the output stream to hold the decoded bytes
     *
     * @return the number of bytes produced.
     */
    public static int decode(byte[] data, int off, int length, OutputStream out) throws IOException {
        byte    b1, b2, b3, b4;
        int        outLen = 0;

        int        end = off + length;

        while (end > 0) {
            if (!ignore((char) data[end - 1])) {
                break;
            }

            end--;
        }

        int  i = off;
        // CHECKSTYLE IGNORE MagicNumber FOR NEXT 1 LINE
        int  finish = end - 4; // last set of 4 bytes might include padding

        while (i < finish) {
            while ((i < finish) && ignore((char) data[i])) {
                i++;
            }

            b1 = DECODING_TABLE[data[i++]];

            while ((i < finish) && ignore((char) data[i])) {
                i++;
            }

            b2 = DECODING_TABLE[data[i++]];

            while ((i < finish) && ignore((char) data[i])) {
                i++;
            }

            b3 = DECODING_TABLE[data[i++]];

            while ((i < finish) && ignore((char) data[i])) {
                i++;
            }

            b4 = DECODING_TABLE[data[i++]];

            // Convert 4 6-bit bytes to 3 8-bit bytes
            // CHECKSTYLE IGNORE MagicNumber FOR NEXT 3 LINES
            out.write((b1 << 2) | (b2 >> 4)); // 6 bits of b1 plus 2 bits of b2
            out.write((b2 << 4) | (b3 >> 2)); // 4 bits of b2 plus 4 bits of b3
            out.write((b3 << 6) | b4);        // 2 bits of b3 plus 6 bits of b4

            // CHECKSTYLE IGNORE MagicNumber FOR NEXT 1 LINE
            outLen += 3;
        }

        // Get the last 4 bytes; only last two can be padding
        b1 = DECODING_TABLE[data[i++]];
        b2 = DECODING_TABLE[data[i++]];

        // always write the first byte
        // CHECKSTYLE IGNORE MagicNumber FOR NEXT 1 LINE
        out.write((b1 << 2) | (b2 >> 4)); // 6 bits of b1 plus 2 bits of b2
        outLen++;

        byte p1 = data[i++];
        byte p2 = data[i++];

        b3 = DECODING_TABLE[p1]; // may be needed later

        if (p1 != PADDING) { // Nothing more to do if p1 == PADDING
            // CHECKSTYLE IGNORE MagicNumber FOR NEXT 1 LINE
            out.write((b2 << 4) | (b3 >> 2)); // 4 bits of b2 plus 4 bits of b3
            outLen++;
        } else if (p2 != PADDING) { // Nothing more to do if p2 == PADDING
            b4 = DECODING_TABLE[p2];
            // CHECKSTYLE IGNORE MagicNumber FOR NEXT 1 LINE
            out.write((b3 << 6) | b4);        // 2 bits of b3 plus 6 bits of b4
            outLen++;
        }

        return outLen;
    }

}
