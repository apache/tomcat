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
package org.apache.tomcat.util.buf;

import java.io.ByteArrayOutputStream;
import java.io.CharConversionException;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.apache.tomcat.util.res.StringManager;

/**
 *  All URL decoding happens here. This way we can reuse, review, optimize
 *  without adding complexity to the buffers.
 *
 *  The conversion will modify the original buffer.
 *
 *  @author Costin Manolache
 */
public final class UDecoder {

    private static final StringManager sm = StringManager.getManager(UDecoder.class);

    private static class DecodeException extends CharConversionException {
        private static final long serialVersionUID = 1L;
        public DecodeException(String s) {
            super(s);
        }

        @Override
        public synchronized Throwable fillInStackTrace() {
            // This class does not provide a stack trace
            return this;
        }
    }

    /** Unexpected end of data. */
    private static final IOException EXCEPTION_EOF = new DecodeException(sm.getString("uDecoder.eof"));

    /** %xx with not-hex digit */
    private static final IOException EXCEPTION_NOT_HEX_DIGIT = new DecodeException(
            "isHexDigit");

    /** %-encoded slash is forbidden in resource path */
    private static final IOException EXCEPTION_SLASH = new DecodeException(
            "noSlash");


    /**
     * URLDecode, will modify the source. Assumes source bytes are encoded using
     * a superset of US-ASCII as per RFC 7230. "%2f" will be rejected unless the
     * input is a query string.
     *
     * @param mb    The URL encoded bytes
     * @param query {@code true} if this is a query string. For a query string
     *                  '+' will be decoded to ' '
     *
     * @throws IOException Invalid %xx URL encoding
     */
    public void convert(ByteChunk mb, boolean query) throws IOException {
        if (query) {
            convert(mb, true, EncodedSolidusHandling.DECODE);
        } else {
            convert(mb, false, EncodedSolidusHandling.REJECT);
        }
    }


    /**
     * URLDecode, will modify the source. Assumes source bytes are encoded using
     * a superset of US-ASCII as per RFC 7230.
     *
     * @param mb                        The URL encoded bytes
     * @param encodedSolidusHandling    How should the %2f sequence handled by
     *                                      the decoder? For query strings this
     *                                      parameter will be ignored and the
     *                                      %2f sequence will be decoded
     * @throws IOException Invalid %xx URL encoding
     */
    public void convert(ByteChunk mb, EncodedSolidusHandling encodedSolidusHandling) throws IOException {
        convert(mb, false, encodedSolidusHandling);
    }


    private void convert(ByteChunk mb, boolean query, EncodedSolidusHandling encodedSolidusHandling) throws IOException {

        int start=mb.getOffset();
        byte buff[]=mb.getBytes();
        int end=mb.getEnd();

        int idx= ByteChunk.findByte( buff, start, end, (byte) '%' );
        int idx2=-1;
        if( query ) {
            idx2= ByteChunk.findByte( buff, start, (idx >= 0 ? idx : end), (byte) '+' );
        }
        if( idx<0 && idx2<0 ) {
            return;
        }

        // idx will be the smallest positive index ( first % or + )
        if( (idx2 >= 0 && idx2 < idx) || idx < 0 ) {
            idx=idx2;
        }

        for( int j=idx; j<end; j++, idx++ ) {
            if( buff[ j ] == '+' && query) {
                buff[idx]= (byte)' ' ;
            } else if( buff[ j ] != '%' ) {
                buff[idx]= buff[j];
            } else {
                // read next 2 digits
                if( j+2 >= end ) {
                    throw EXCEPTION_EOF;
                }
                byte b1= buff[j+1];
                byte b2=buff[j+2];
                if( !isHexDigit( b1 ) || ! isHexDigit(b2 )) {
                    throw EXCEPTION_NOT_HEX_DIGIT;
                }

                j+=2;
                int res=x2c( b1, b2 );
                if (res == '/') {
                    switch (encodedSolidusHandling) {
                    case DECODE: {
                        buff[idx]=(byte)res;
                        break;
                    }
                    case REJECT: {
                        throw EXCEPTION_SLASH;
                    }
                    case PASS_THROUGH: {
                        buff[idx++] = buff[j-2];
                        buff[idx++] = buff[j-1];
                        buff[idx] = buff[j];
                    }
                    }
                } else {
                    buff[idx]=(byte)res;
                }
            }
        }

        mb.setEnd( idx );
    }

    // -------------------- Additional methods --------------------

    /**
     * Decode and return the specified URL-encoded String. It is assumed the
     * string is not a query string.
     *
     * @param str The url-encoded string
     * @param charset The character encoding to use; if null, UTF-8 is used.
     * @return the decoded string
     * @exception IllegalArgumentException if a '%' character is not followed
     * by a valid 2-digit hexadecimal number
     */
    public static String URLDecode(String str, Charset charset) {
        if (str == null) {
            return null;
        }

        if (str.indexOf('%') == -1) {
            // No %nn sequences, so return string unchanged
            return str;
        }

        if (charset == null) {
            charset = StandardCharsets.UTF_8;
        }

        /*
         * Decoding is required.
         *
         * Potential complications:
         * - The source String may be partially decoded so it is not valid to
         *   assume that the source String is ASCII.
         * - Have to process as characters since there is no guarantee that the
         *   byte sequence for '%' is going to be the same in all character
         *   sets.
         * - We don't know how many '%nn' sequences are required for a single
         *   character. It varies between character sets and some use a variable
         *   length.
         */

        // This isn't perfect but it is a reasonable guess for the size of the
        // array required
        ByteArrayOutputStream baos = new ByteArrayOutputStream(str.length() * 2);

        OutputStreamWriter osw = new OutputStreamWriter(baos, charset);

        char[] sourceChars = str.toCharArray();
        int len = sourceChars.length;
        int ix = 0;

        try {
            while (ix < len) {
                char c = sourceChars[ix++];
                if (c == '%') {
                    osw.flush();
                    if (ix + 2 > len) {
                        throw new IllegalArgumentException(
                                sm.getString("uDecoder.urlDecode.missingDigit", str));
                    }
                    char c1 = sourceChars[ix++];
                    char c2 = sourceChars[ix++];
                    if (isHexDigit(c1) && isHexDigit(c2)) {
                        baos.write(x2c(c1, c2));
                    } else {
                        throw new IllegalArgumentException(
                                sm.getString("uDecoder.urlDecode.missingDigit", str));
                    }
                } else {
                    osw.append(c);
                }
            }
            osw.flush();

            return baos.toString(charset.name());
        } catch (IOException ioe) {
            throw new IllegalArgumentException(
                    sm.getString("uDecoder.urlDecode.conversionError", str, charset.name()), ioe);
        }
    }


    private static boolean isHexDigit( int c ) {
        return ( ( c>='0' && c<='9' ) ||
                 ( c>='a' && c<='f' ) ||
                 ( c>='A' && c<='F' ));
    }


    private static int x2c( byte b1, byte b2 ) {
        int digit= (b1>='A') ? ( (b1 & 0xDF)-'A') + 10 :
            (b1 -'0');
        digit*=16;
        digit +=(b2>='A') ? ( (b2 & 0xDF)-'A') + 10 :
            (b2 -'0');
        return digit;
    }


    private static int x2c( char b1, char b2 ) {
        int digit= (b1>='A') ? ( (b1 & 0xDF)-'A') + 10 :
            (b1 -'0');
        digit*=16;
        digit +=(b2>='A') ? ( (b2 & 0xDF)-'A') + 10 :
            (b2 -'0');
        return digit;
    }
}
