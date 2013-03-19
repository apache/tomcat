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
final class QuotedPrintableDecoder {

    /**
     * Set up the encoding table.
     */
    private static final byte[] ENCODING_TABLE = {
        (byte) '0', (byte) '1', (byte) '2', (byte) '3', (byte) '4', (byte) '5', (byte) '6', (byte) '7',
        (byte) '8', (byte) '9', (byte) 'A', (byte) 'B', (byte) 'C', (byte) 'D', (byte) 'E', (byte) 'F'
    };

    /**
     * The default number of byte shift for decode.
     */
    private static final int OUT_SHIFT = 4;

    /**
     * Set up the decoding table; this is indexed by a byte converted to an int,
     * so must be at least as large as the number of different byte values,
     * positive and negative and zero.
     */
    private static final byte[] DECODING_TABLE = new byte[Byte.MAX_VALUE - Byte.MIN_VALUE + 1];

    static {
        // initialize the decoding table
        for (int i = 0; i < ENCODING_TABLE.length; i++) {
            DECODING_TABLE[ENCODING_TABLE[i]] = (byte) i;
        }
    }

    /**
     * Hidden constructor, this class must not be instantiated.
     */
    private QuotedPrintableDecoder() {
        // do nothing
    }

    /**
     * Decode the unencoded byte data writing it to the given output stream.
     *
     * @param data   The array of byte data to decode.
     * @param off    Starting offset within the array.
     * @param length The length of data to encode.
     * @param out    The output stream used to return the decoded data.
     *
     * @return the number of bytes produced.
     * @exception IOException
     */
    public static int decode(byte[] data, int off, int length, OutputStream out) throws IOException {
        int endOffset = off + length;
        int bytesWritten = 0;

        while (off < endOffset) {
            byte ch = data[off++];

            // space characters were translated to '_' on encode, so we need to translate them back.
            if (ch == '_') {
                out.write(' ');
            } else if (ch == '=') {
                // we found an encoded character.  Reduce the 3 char sequence to one.
                // but first, make sure we have two characters to work with.
                if (off + 1 >= endOffset) {
                    throw new IOException("Invalid quoted printable encoding");
                }
                // convert the two bytes back from hex.
                byte b1 = data[off++];
                byte b2 = data[off++];

                // we've found an encoded carriage return.  The next char needs to be a newline
                if (b1 == '\r') {
                    if (b2 != '\n') {
                        throw new IOException("Invalid quoted printable encoding");
                    }
                    // this was a soft linebreak inserted by the encoding.  We just toss this away
                    // on decode.
                } else {
                    // this is a hex pair we need to convert back to a single byte.
                    byte c1 = DECODING_TABLE[b1];
                    byte c2 = DECODING_TABLE[b2];
                    out.write((c1 << OUT_SHIFT) | c2);
                    // 3 bytes in, one byte out
                    bytesWritten++;
                }
            } else {
                // simple character, just write it out.
                out.write(ch);
                bytesWritten++;
            }
        }

        return bytesWritten;
    }

}
