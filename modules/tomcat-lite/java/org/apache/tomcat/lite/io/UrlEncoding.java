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

package org.apache.tomcat.lite.io;

import java.io.CharConversionException;
import java.io.IOException;
import java.nio.charset.CharsetEncoder;
import java.util.BitSet;


/**
 * Support for %xx URL encoding.
 *
 * @author Costin Manolache
 */
public final class UrlEncoding {

    protected static final boolean ALLOW_ENCODED_SLASH =
        Boolean.valueOf(
                System.getProperty(
                        "org.apache.tomcat.util.buf.UDecoder.ALLOW_ENCODED_SLASH",
                "false")).booleanValue();

    public UrlEncoding() {
    }

    // Utilities for URL encoding.
    static BitSet SAFE_CHARS_URL = new BitSet(128);
    static BitSet SAFE_CHARS = new BitSet(128);
    BBuffer tmpBuffer = BBuffer.allocate(1024);
    CBuffer tmpCharBuffer = CBuffer.newInstance();

    public void urlEncode(CBuffer url, CBuffer encoded, IOWriter enc) {
        tmpBuffer.recycle();
        urlEncode(url, tmpBuffer, encoded, enc.getEncoder("UTF-8"),
                SAFE_CHARS_URL, true, enc);
    }

    public void urlEncode(String url, CBuffer encoded, IOWriter enc) {
        tmpCharBuffer.recycle();
        tmpCharBuffer.append(url);
        urlEncode(tmpCharBuffer, encoded, enc);
    }

    /** Only works for UTF-8 or charsets preserving ascii.
     *
     * @param url
     * @param tmpBuffer
     * @param encoded
     * @param utf8Enc
     * @param safeChars
     */
    public void urlEncode(CBuffer url,
            BBuffer tmpBuffer,
            CBuffer encoded,
            CharsetEncoder utf8Enc,
            BitSet safeChars, boolean last, IOWriter enc) {
        // tomcat charset-encoded each character first. I don't think
        // this is needed.

        // TODO: space to +
        enc.encodeAll(url, tmpBuffer, utf8Enc, last);
        byte[] array = tmpBuffer.array();
        for (int i = tmpBuffer.position(); i < tmpBuffer.limit(); i++) {
            int c = array[i];
            if (safeChars.get(c)) {
                encoded.append((char) c);
            } else {
                encoded.append('%');
                char ch = Character.forDigit((c >> 4) & 0xF, 16);
                encoded.append(ch);
                ch = Character.forDigit(c & 0xF, 16);
                encoded.append(ch);
            }
        }
    }

    static {
        initSafeChars(SAFE_CHARS);
        initSafeChars(SAFE_CHARS_URL);
        SAFE_CHARS_URL.set('/');
    }

    private static void initSafeChars(BitSet safeChars) {
        int i;
        for (i = 'a'; i <= 'z'; i++) {
            safeChars.set(i);
        }
        for (i = 'A'; i <= 'Z'; i++) {
            safeChars.set(i);
        }
        for (i = '0'; i <= '9'; i++) {
            safeChars.set(i);
        }
        // safe
        safeChars.set('-');
        safeChars.set('_');
        safeChars.set('.');

        // Dangerous: someone may treat this as " "
        // RFC1738 does allow it, it's not reserved
        // safeChars.set('+');
        // extra
        safeChars.set('*');
        // tomcat has them - not sure if this is correct
        safeChars.set('$'); // ?
        safeChars.set('!'); // ?
        safeChars.set('\''); // ?
        safeChars.set('('); // ?
        safeChars.set(')'); // ?
        safeChars.set(','); // ?
    }

    public void urlDecode(BBuffer bb, CBuffer dest, boolean q,
            IOReader charDec) throws IOException {
        // Replace %xx
        tmpBuffer.append(bb);
        urlDecode(tmpBuffer, q);
        charDec.decodeAll(bb, dest);
    }


    public void urlDecode(BBuffer bb, CBuffer dest,
            IOReader charDec) throws IOException {
        // Replace %xx
        tmpBuffer.append(bb);
        urlDecode(tmpBuffer, true);
        charDec.decodeAll(bb, dest);
    }


    /**
     * URLDecode, will modify the source. This is only at byte level -
     * it needs conversion to chars using the right charset.
     *
     * @param query Converts '+' to ' ' and allow '/'
     */
    public void urlDecode(BBuffer mb, boolean query) throws IOException {
        int start = mb.getOffset();
        byte buff[] = mb.array();
        int end = mb.getEnd();

        int idx = BBuffer.indexOf(buff, start, end, '%');
        int idx2 = -1;
        if (query)
            idx2 = BBuffer.indexOf(buff, start, end, '+');
        if (idx < 0 && idx2 < 0) {
            return;
        }

        // idx will be the smallest positive inxes ( first % or + )
        if (idx2 >= 0 && idx2 < idx)
            idx = idx2;
        if (idx < 0)
            idx = idx2;

        //boolean noSlash = !query;

        for (int j = idx; j < end; j++, idx++) {
            if (buff[j] == '+' && query) {
                buff[idx] = (byte) ' ';
            } else if (buff[j] != '%') {
                buff[idx] = buff[j];
            } else {
                // read next 2 digits
                if (j + 2 >= end) {
                    throw new CharConversionException("EOF");
                }
                byte b1 = buff[j + 1];
                byte b2 = buff[j + 2];
                if (!isHexDigit(b1) || !isHexDigit(b2))
                    throw new CharConversionException("isHexDigit");

                j += 2;
                int res = x2c(b1, b2);
//                if (noSlash && (res == '/')) {
//                    throw new CharConversionException("noSlash " + mb);
//                }
                buff[idx] = (byte) res;
            }
        }

        mb.setEnd(idx);

        return;
    }


    private static boolean isHexDigit(int c) {
        return ((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F'));
    }

    private static int x2c(byte b1, byte b2) {
        int digit = (b1 >= 'A') ? ((b1 & 0xDF) - 'A') + 10 : (b1 - '0');
        digit *= 16;
        digit += (b2 >= 'A') ? ((b2 & 0xDF) - 'A') + 10 : (b2 - '0');
        return digit;
    }

}
