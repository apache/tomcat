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
 * @since FileUpload 1.3
 */
final class QuotedPrintableDecoder {

    /**
     * Carriage return character '{@value}'.
     */
    private static final char CR = '\r';

    /**
     * Equal character '{@value}'.
     */
    private static final char EQUAL = '=';

    /**
     * Line feed character '{@value}'.
     */
    private static final char LF = '\n';

    /**
     * Space character '{@value}'.
     */
    private static final char SP = ' ';

    /**
     * Underscore character '{@value}'.
     */
    private static final char UNDERSCORE = '_';

    /**
     * The shift value required to create the upper nibble
     * from the first of 2 byte values converted from ASCII hex.
     */
    private static final int UPPER_NIBBLE_SHIFT = Byte.SIZE / 2;

    /**
     * Decode the encoded byte data writing it to the given output stream.
     *
     * @param data   The array of byte data to decode.
     * @param out    The output stream used to return the decoded data.
     *
     * @return the number of bytes produced.
     * @throws IOException if an IO error occurs
     */
    public static int decode(final byte[] data, final OutputStream out) throws IOException {
        int off = 0;
        final int length = data.length;
        final int endOffset = off + length;
        int bytesWritten = 0;

        while (off < endOffset) {
            final byte ch = data[off++];

            // space characters were translated to '_' on encode, so we need to translate them back.
            if (ch == UNDERSCORE) {
                out.write(SP);
            } else if (ch == EQUAL) {
                // we found an encoded character.  Reduce the 3 char sequence to one.
                // but first, make sure we have two characters to work with.
                if (off + 1 >= endOffset) {
                    throw new IOException("Invalid quoted printable encoding; truncated escape sequence");
                }

                final byte b1 = data[off++];
                final byte b2 = data[off++];

                // we've found an encoded carriage return.  The next char needs to be a newline
                if (b1 == CR) {
                    if (b2 != LF) {
                        throw new IOException("Invalid quoted printable encoding; CR must be followed by LF");
                    }
                    // this was a soft linebreak inserted by the encoding.  We just toss this away
                    // on decode.
                } else {
                    // this is a hex pair we need to convert back to a single byte.
                    final int c1 = hexToBinary(b1);
                    final int c2 = hexToBinary(b2);
                    out.write(c1 << UPPER_NIBBLE_SHIFT | c2);
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

    /**
     * Convert a hex digit to the binary value it represents.
     *
     * @param b the ASCII hex byte to convert (0-0, A-F, a-f)
     * @return the int value of the hex byte, 0-15
     * @throws IOException if the byte is not a valid hex digit.
     */
    private static int hexToBinary(final byte b) throws IOException {
        // CHECKSTYLE IGNORE MagicNumber FOR NEXT 1 LINE
        final int i = Character.digit((char) b, 16);
        if (i == -1) {
            throw new IOException("Invalid quoted printable encoding: not a valid hex digit: " + b);
        }
        return i;
    }

    /**
     * Hidden constructor, this class must not be instantiated.
     */
    private QuotedPrintableDecoder() {
        // do nothing
    }

}
