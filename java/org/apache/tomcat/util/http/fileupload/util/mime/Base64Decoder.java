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
     * Bytes per undecoded block.
     */
    private static final int BYTES_PER_UNENCODED_BLOCK = 3;

    /**
     * 2 bits mask.
     */
    private static final int MASK_2BITS = 2;

    /**
     * 4 bits mask.
     */
    private static final int MASK_4BITS = 4;

    /**
     * 6 bits mask.
     */
    private static final int MASK_6BITS = 6;

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
        (byte) 'v',
        (byte) 'w', (byte) 'x', (byte) 'y', (byte) 'z',
        (byte) '0', (byte) '1', (byte) '2', (byte) '3', (byte) '4', (byte) '5', (byte) '6',
        (byte) '7', (byte) '8', (byte) '9',
        (byte) '+', (byte) '/'
    };

    /**
     * The padding byte.
     */
    private static final byte PADDING = (byte) '=';

    /**
     * The decoding table size.
     */
    private static final int DECODING_TABLE_SIZE = 256;

    /**
     * Set up the decoding table.
     */
    private static final byte[] DECODING_TABLE = new byte[DECODING_TABLE_SIZE];

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
     *
     * @param c the char has to be checked.
     * @return true, if the input char has to be checked, false otherwise.
     */
    private static boolean ignore(char c) {
        return (c == '\n' || c == '\r' || c == '\t' || c == ' ');
    }

    /**
     * Decode the base 64 encoded byte data writing it to the given output stream,
     * whitespace characters will be ignored.
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
        int  finish = end - MASK_4BITS;

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

            out.write((b1 << MASK_2BITS) | (b2 >> MASK_4BITS));
            out.write((b2 << MASK_4BITS) | (b3 >> MASK_2BITS));
            out.write((b3 << MASK_6BITS) | b4);

            outLen += BYTES_PER_UNENCODED_BLOCK;
        }

        if (data[end - MASK_2BITS] == PADDING) {
            b1 = DECODING_TABLE[data[end - MASK_4BITS]];
            b2 = DECODING_TABLE[data[end - BYTES_PER_UNENCODED_BLOCK]];

            out.write((b1 << MASK_2BITS) | (b2 >> MASK_4BITS));

            outLen += 1;
        } else if (data[end - 1] == PADDING) {
            b1 = DECODING_TABLE[data[end - MASK_4BITS]];
            b2 = DECODING_TABLE[data[end - BYTES_PER_UNENCODED_BLOCK]];
            b3 = DECODING_TABLE[data[end - MASK_2BITS]];

            out.write((b1 << MASK_2BITS) | (b2 >> MASK_4BITS));
            out.write((b2 << MASK_4BITS) | (b3 >> MASK_2BITS));

            outLen += MASK_2BITS;
        } else {
            b1 = DECODING_TABLE[data[end - MASK_4BITS]];
            b2 = DECODING_TABLE[data[end - BYTES_PER_UNENCODED_BLOCK]];
            b3 = DECODING_TABLE[data[end - MASK_2BITS]];
            b4 = DECODING_TABLE[data[end - 1]];

            out.write((b1 << MASK_2BITS) | (b2 >> MASK_4BITS));
            out.write((b2 << MASK_4BITS) | (b3 >> MASK_2BITS));
            out.write((b3 << MASK_6BITS) | b4);

            outLen += BYTES_PER_UNENCODED_BLOCK;
        }

        return outLen;
    }

}
