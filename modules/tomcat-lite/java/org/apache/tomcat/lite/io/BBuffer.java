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

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;

/*
 * In a server it is very important to be able to operate on
 * the original byte[] without converting everything to chars.
 * Some protocols are ASCII only, and some allow different
 * non-UNICODE encodings. The encoding is not known beforehand,
 * and can even change during the execution of the protocol.
 * ( for example a multipart message may have parts with different
 *  encoding )
 *
 * For HTTP it is not very clear how the encoding of RequestURI
 * and mime values can be determined, but it is a great advantage
 * to be able to parse the request without converting to string.
 */

// Renamed from ByteChunk to make it easier to write code using both

/**
 * This class is used to represent a chunk of bytes, and utilities to manipulate
 * byte[].
 *
 * The buffer can be modified and used for both input and output.
 *
 * There are 2 modes: The chunk can be associated with a sink - ByteInputChannel
 * or ByteOutputChannel, which will be used when the buffer is empty ( on input
 * ) or filled ( on output ). For output, it can also grow. This operating mode
 * is selected by calling setLimit() or allocate(initial, limit) with limit !=
 * -1.
 *
 * Various search and append method are defined - similar with String and
 * StringBuffer, but operating on bytes.
 *
 * This is important because it allows processing the http headers directly on
 * the received bytes, without converting to chars and Strings until the strings
 * are needed. In addition, the charset is determined later, from headers or
 * user code.
 *
 *
 * @author dac@sun.com
 * @author James Todd [gonzo@sun.com]
 * @author Costin Manolache
 * @author Remy Maucherat
 */
public class BBuffer implements Cloneable, Serializable,
    BBucket {

    /**
     * Default encoding used to convert to strings. It should be UTF8, but:
     * - the servlet API requires 8859_1 as default
     * -
     */
    public static final String DEFAULT_CHARACTER_ENCODING = "ISO-8859-1";

    // byte[]
    private byte[] buff;

    private int start = 0;

    private int end;

    private ByteBuffer byteBuffer;

    public static final String CRLF = "\r\n";

    /* Various constant "strings" */
    public static final byte[] CRLF_BYTES = convertToBytes(BBuffer.CRLF);

    /**
     * HT.
     */
    public static final byte HT = (byte) '\t';

    /**
     * SP.
     */
    public static final byte SP = (byte) ' ';

    /**
     * LF.
     */
    public static final byte LF = (byte) '\n';

    /**
     * CR.
     */
    public static final byte CR = (byte) '\r';

    //private int useCount;


    private static final boolean[] isDigit = new boolean[256];

    static Charset UTF8;

    public static final byte A = (byte) 'A';

    public static final byte Z = (byte) 'Z';

    public static final byte a = (byte) 'a';

    public static final byte LC_OFFSET = A - a;
    private static final byte[] toLower = new byte[256];
    private static final boolean[] isUpper = new boolean[256];

    static {
        for (int i = 0; i < 256; i++) {
            toLower[i] = (byte)i;
        }

        for (int lc = 'a'; lc <= 'z'; lc++) {
            int uc = lc + 'A' - 'a';
            toLower[uc] = (byte)lc;
            isUpper[uc] = true;
        }
    }

    static {
        for (int d = '0'; d <= '9'; d++) {
            isDigit[d] = true;
        }
        UTF8 = Charset.forName("UTF-8");
    }

    public static BBuffer allocate() {
        return new BBuffer();
    }

    public static BBuffer allocate(int initial) {
        return new BBuffer().makeSpace(initial);
    }


    public static BBuffer allocate(String msg) {
        BBuffer bc = allocate();
        byte[] data = msg.getBytes();
        bc.append(data, 0, data.length);
        return bc;
    }

    public static BBuffer wrapper(String msg) {
        BBuffer bc = new IOBucketWrap();
        byte[] data = msg.getBytes();
        bc.setBytes(data, 0, data.length);
        return bc;
    }

    public static BBuffer wrapper() {
        return new IOBucketWrap();
    }

    public static BBuffer wrapper(BBuffer bb) {
        BBuffer res = new IOBucketWrap();
        res.setBytes(bb.array(), bb.position(), bb.remaining());
        return res;
    }

    public static BBuffer wrapper(byte b[], int off, int len) {
        BBuffer res = new IOBucketWrap();
        res.setBytes(b, off, len);
        return res;
    }

    public static BBuffer wrapper(BBucket bb, int start, int len) {
        BBuffer res = new IOBucketWrap();
        res.setBytes(bb.array(), bb.position() + start, len);
        return res;
    }

    /**
     * Creates a new, uninitialized ByteChunk object.
     */
    private BBuffer() {
    }

    public void append(BBuffer src) {
        append(src.array(), src.getStart(), src.getLength());
    }

    /**
     * Add data to the buffer
     */
    public void append(byte src[], int off, int len) {
        // will grow, up to limit
        makeSpace(len);

        // assert: makeSpace made enough space
        System.arraycopy(src, off, buff, end, len);
        end += len;
        return;
    }

    // -------------------- Adding data to the buffer --------------------
    /**
     * Append a char, by casting it to byte. This IS NOT intended for unicode.
     *
     * @param c
     */
    public void append(char c) {
        put((byte) c);
    }

    // -------------------- Removing data from the buffer --------------------

    /**
     * Returns the message bytes.
     */
    @Override
    public byte[] array() {
        return buff;
    }

    public int capacity() {
        return buff.length;
    }

    public boolean equals(BBuffer bb) {
        return equals(bb.array(), bb.getStart(), bb.getLength());
    }

    public boolean equals(byte b2[], int off2, int len2) {
        byte b1[] = buff;
        if (b1 == null && b2 == null)
            return true;

        int len = end - start;
        if (len2 != len || b1 == null || b2 == null)
            return false;

        int off1 = start;

        while (len-- > 0) {
            if (b1[off1++] != b2[off2++]) {
                return false;
            }
        }
        return true;
    }


    public boolean equals(char c2[], int off2, int len2) {
        // XXX works only for enc compatible with ASCII/UTF !!!
        byte b1[] = buff;
        if (c2 == null && b1 == null)
            return true;

        if (b1 == null || c2 == null || end - start != len2) {
            return false;
        }
        int off1 = start;
        int len = end - start;

        while (len-- > 0) {
            if ((char) b1[off1++] != c2[off2++]) {
                return false;
            }
        }
        return true;
    }

    // -------------------- Conversion and getters --------------------

    /**
     * Compares the message bytes to the specified String object.
     *
     * @param s
     *            the String to compare
     * @return true if the comparison succeeded, false otherwise
     */
    public boolean equals(String s) {
        // XXX ENCODING - this only works if encoding is UTF8-compat
        // ( ok for tomcat, where we compare ascii - header names, etc )!!!

        byte[] b = buff;
        int blen = end - start;
        if (b == null || blen != s.length()) {
            return false;
        }
        int boff = start;
        for (int i = 0; i < blen; i++) {
            if (b[boff++] != s.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Compares the message bytes to the specified String object.
     *
     * @param s
     *            the String to compare
     * @return true if the comparison succeeded, false otherwise
     */
    public boolean equalsIgnoreCase(String s) {
        byte[] b = buff;
        int blen = end - start;
        if (b == null || blen != s.length()) {
            return false;
        }
        int boff = start;
        for (int i = 0; i < blen; i++) {
            if (toLower(b[boff++]) != toLower(s.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    public int get(int off) {
        if (start + off >= end) {
            throw new ArrayIndexOutOfBoundsException();
        }
        return buff[start + off] & 0xFF;
    }

    /**
     * Return a byte buffer. Changes in the ByteBuffer position will
     * not be reflected in the IOBucket
     * @return
     */
    public ByteBuffer getByteBuffer() {
        if (byteBuffer == null || byteBuffer.array() != buff) {
            byteBuffer = ByteBuffer.wrap(buff, start, end - start);
        } else {
            byteBuffer.position(start);
            byteBuffer.limit(end);
        }
        return byteBuffer;
    }

    // --------------------
    public BBuffer getClone() {
        try {
            return (BBuffer) this.clone();
        } catch (Exception ex) {
            return null;
        }
    }

    public int getEnd() {
        return end;
    }

    public int getInt() {
        return parseInt(buff, start, end - start);
    }
    /**
     * Returns the length of the bytes. XXX need to clean this up
     */
    public int getLength() {
        return end - start;
    }

    public long getLong() {
        return parseLong(buff, start, end - start);
    }

    public int getOffset() {
        return start;
    }

    // -------------------- equals --------------------

    /**
     * Returns the start offset of the bytes. For output this is the end of the
     * buffer.
     */
    public int getStart() {
        return start;
    }

    public ByteBuffer getWriteByteBuffer(int space) {
        if (space == 0) {
            space = 16;
        }
        makeSpace(space);
        if (byteBuffer == null || byteBuffer.array() != buff) {
            byteBuffer = ByteBuffer.wrap(buff, end, buff.length);
        } else {
            byteBuffer.position(end);
            byteBuffer.limit(buff.length);
        }
        return byteBuffer;
    }

    // -------------------- Hash code --------------------
    public int hashCode() {
        return hashBytes(buff, start, end - start);
    }

    public boolean hasLFLF() {
        return hasLFLF(this);
    }

    public boolean hasRemaining() {
        return start < end;
    }

    /**
     * Returns true if the message bytes starts with the specified string.
     *
     * @param s
     *            the string
     */
//    public boolean startsWith(String s) {
//        // Works only if enc==UTF
//        byte[] b = buff;
//        int blen = s.length();
//        if (b == null || blen > end - start) {
//            return false;
//        }
//        int boff = start;
//        for (int i = 0; i < blen; i++) {
//            if (b[boff++] != s.charAt(i)) {
//                return false;
//            }
//        }
//        return true;
//    }

    /* Returns true if the message bytes start with the specified byte array */
//    public boolean startsWith(byte[] b2) {
//        byte[] b1 = buff;
//        if (b1 == null && b2 == null) {
//            return true;
//        }
//
//        int len = end - start;
//        if (b1 == null || b2 == null || b2.length > len) {
//            return false;
//        }
//        for (int i = start, j = 0; i < end && j < b2.length;) {
//            if (b1[i++] != b2[j++])
//                return false;
//        }
//        return true;
//    }

    /**
     * Returns true if the message bytes starts with the specified string.
     *
     * @param c
     *            the character
     * @param starting
     *            The start position
     */
    public int indexOf(char c, int starting) {
        int ret = indexOf(buff, start + starting, end, c);
        return (ret >= start) ? ret - start : -1;
    }

    /**
     * Returns true if the message bytes starts with the specified string.
     *
     * @param s
     *            the string
     * @param pos
     *            The position
     */
//    public boolean startsWithIgnoreCase(String s, int pos) {
//        byte[] b = buff;
//        int len = s.length();
//        if (b == null || len + pos > end - start) {
//            return false;
//        }
//        int off = start + pos;
//        for (int i = 0; i < len; i++) {
//            if (Ascii.toLower(b[off++]) != Ascii.toLower(s.charAt(i))) {
//                return false;
//            }
//        }
//        return true;
//    }
    public int indexOf(String src) {
        return indexOf(src, 0, src.length(), 0);
    }

    public int indexOf(String src, int srcOff, int srcLen, int myOff) {
        if ("".equals(src)) {
            return myOff;
        }
        char first = src.charAt(srcOff);

        // Look for first char
        int srcEnd = srcOff + srcLen;

        for (int i = myOff + start; i <= (end - srcLen); i++) {
            if (buff[i] != first)
                continue;
            // found first char, now look for a match
            int myPos = i + 1;
            for (int srcPos = srcOff + 1; srcPos < srcEnd;) {
                if (buff[myPos++] != src.charAt(srcPos++))
                    break;
                if (srcPos == srcEnd)
                    return i - start; // found it
            }
        }
        return -1;
    }

    // hash ignoring case
//    public int hashIgnoreCase() {
//        return hashBytesIC(buff, start, end - start);
//    }

    public boolean isNull() {
        return start == end;
    }

//    private static int hashBytesIC(byte bytes[], int start, int bytesLen) {
//        int max = start + bytesLen;
//        byte bb[] = bytes;
//        int code = 0;
//        for (int i = start; i < max; i++) {
//            code = code * 37 + Ascii.toLower(bb[i]);
//        }
//        return code;
//    }

    @Override
    public int limit() {
        return end;
    }

    public void limit(int newEnd) {
        end = newEnd;
    }

    /**
     * Make space for len chars.
     * If len is small, allocate a reserve space too.
     */
    public BBuffer makeSpace(int count) {
        byte[] tmp = null;

        int newSize;
        int desiredSize = end + count;

        if (buff == null) {
            if (desiredSize < 16)
                desiredSize = 16; // take a minimum
            buff = new byte[desiredSize];
            start = 0;
            end = 0;
            return this;
        }

        // limit < buf.length ( the buffer is already big )
        // or we already have space XXX
        if (desiredSize <= buff.length) {
            return this;
        }
        // grow in larger chunks
        if (desiredSize < 2 * buff.length) {
            newSize = buff.length * 2;
            tmp = new byte[newSize];
        } else {
            newSize = buff.length * 2 + count;
            tmp = new byte[newSize];
        }

        System.arraycopy(buff, start, tmp, 0, end - start);
        buff = tmp;
        tmp = null;
        end = end - start;
        start = 0;
        return this;
    }

//    /**
//     * Find a character, no side effects.
//     *
//     * @return index of char if found, -1 if not
//     */
//    public static int findChars(byte buf[], int start, int end, byte c[]) {
//        int clen = c.length;
//        int offset = start;
//        while (offset < end) {
//            for (int i = 0; i < clen; i++)
//                if (buf[offset] == c[i]) {
//                    return offset;
//                }
//            offset++;
//        }
//        return -1;
//    }

//    /**
//     * Find the first character != c
//     *
//     * @return index of char if found, -1 if not
//     */
//    public static int findNotChars(byte buf[], int start, int end, byte c[]) {
//        int clen = c.length;
//        int offset = start;
//        boolean found;
//
//        while (offset < end) {
//            found = true;
//            for (int i = 0; i < clen; i++) {
//                if (buf[offset] == c[i]) {
//                    found = false;
//                    break;
//                }
//            }
//            if (found) { // buf[offset] != c[0..len]
//                return offset;
//            }
//            offset++;
//        }
//        return -1;
//    }

    @Override
    public int position() {
        return start;
    }

    public void advance(int len) {
        start += len;
    }

    @Override
    public void position(int newStart) {
        start = newStart;
    }

    public void put(byte b) {
        makeSpace(1);
        buff[end++] = b;
    }

    public void putByte(int b) {
        makeSpace(1);
        buff[end++] = (byte) b;
    }

    public int read(BBuffer res) {
        res.setBytes(buff, start, remaining());
        end = start;
        return res.remaining();
    }

    /**
     * Read a chunk from is.
     *
     * You don't need to use buffered input stream, we do the
     * buffering.
     */
    public int read(InputStream is) throws IOException {
        makeSpace(1024);
        int res = is.read(buff, end, buff.length - end);
        if (res > 0) {
            end += res;
        }
        return res;
    }

    public int readAll(InputStream is) throws IOException {
        int size = 0;
        while (true) {
            int res = read(is);
            if (res < 0) {
                return size;
            }
            size += res;
        }
    }

    public int readByte() {
        if (start == end) {
            return -1;
        }
        return buff[start++];
    }


    /**
     *  Read a line - excluding the line terminator, which is consummed as
     *  well but not included in the response.
     *
     *  Line can end with CR, LF or CR/LF
     *
     * @param res
     * @return number of bytes read, or -1 if line ending not found in buffer.
     */
    public int readLine(BBuffer res) {
        int cstart = start;
        while(start < end) {
            byte chr = buff[start++];
            if (chr == CR || chr == LF) {
                res.setBytes(buff, cstart, start - cstart -1);
                if (chr == CR) {
                    if (start < end) {
                        byte chr2 = buff[start];
                        if (chr2 == LF) {
                            start++;
                        }
                    }
                }
                return res.remaining();
            }
        }
        start = cstart;
        return -1;
    }
    /**
     * Consume up to but not including delim.
     *
     */
    public final int readToDelimOrSpace(byte delim,
            BBuffer res) {
        int resStart = start;
        while (true) {
            if (start >= end) {
                break;
            }
            byte chr = buff[start];
            if (chr == delim || chr == SP || chr == HT) {
                break;
            }
            start++;
        }
        res.setBytes(buff, resStart, start - resStart);
        return res.remaining();
    }


    /**
     * Consume all up to the first space or \t, which will be the
     * first character in the buffer.
     *
     * Consumed data is wrapped in res.
     */
    public int readToSpace(BBuffer res) {
        int resStart = start;
        while (true) {
          if (start >= end) {
              break;
          }
          if (buff[start] == SP
                  || buff[start] == HT) {
              break;
          }
          start++;
        }
        res.setBytes(buff, resStart, start - resStart);
        return res.remaining();
    }
    /**
     * Resets the message buff to an uninitialized state.
     */
    public void recycle() {
        start = 0;
        end = 0;
    }
    @Override
    public void release() {
//        synchronized (this) {
//            useCount--;
//            if (useCount == -1) {
//                // all slices have been released -
//                // TODO: callback, return to pool
//            }
//        }
    }
    public int remaining() {
        return end - start;
    }

    public void reset() {
        buff = null;
    }

    // -------------------- Setup --------------------
    /**
     * Sets the message bytes to the specified subarray of bytes.
     *
     * @param b
     *            the ascii bytes
     * @param off
     *            the start offset of the bytes
     * @param len
     *            the length of the bytes
     */
    public void setBytes(byte[] b, int off, int len) {
        throw new RuntimeException("Can't setBytes on allocated buffer");
    }

    public void wrap(BBucket b) {
        setBytes(b.array(), b.position(), b.remaining());
    }

    public void wrap(ByteBuffer b) {
        setBytes(b.array(), b.position(), b.remaining());
    }

    protected void setBytesInternal(byte[] b, int off, int len) {
        buff = b;
        start = off;
        end = start + len;
    }

//    public final void lowerCase() {
//        while (start < end) {
//            byte chr = buff[start];
//            if ((chr >= A) && (chr <= Z)) {
//                buff[start] = (byte) (chr - LC_OFFSET);
//            }
//            start++;
//        }
//    }

    public void setEnd(int i) {
        end = i;
    }

    /**
     * The old code from MessageBytes, used for setContentLength
     * and setStatus.
     * TODO: just use StringBuilder, the method is faster.
     */
    public void setLong(long l) {
        if (array() == null) {
            makeSpace(20);
        }
        long current = l;
        byte[] buf = array();
        int start = 0;
        int end = 0;
        if (l == 0) {
            buf[end++] = (byte) '0';
        } else if (l < 0) {
            current = -l;
            buf[end++] = (byte) '-';
        }
        while (current > 0) {
            int digit = (int) (current % 10);
            current = current / 10;
            buf[end++] = Hex.HEX[digit];
        }
        setOffset(0);
        setEnd(end);
        // Inverting buffer
        end--;
        if (l < 0) {
            start++;
        }
        while (end > start) {
            byte temp = buf[start];
            buf[start] = buf[end];
            buf[end] = temp;
            start++;
            end--;
        }
    }

    public void setOffset(int off) {
        if (end < off)
            end = off;
        start = off;
    }


    public int skipEmptyLines() {
        int resStart = start;
        while (buff[start] == CR || buff[start] == LF) {
            start++;
            if (start == end) {
                break;
            }
        }
        return start - resStart;
    }

    public int skipSpace() {
        int cstart = start;
        while (true) {
          if (start >= end) {
            return start - cstart;
          }
          if ((buff[start] == SP) || (buff[start] == HT)) {
            start++;
          } else {
            return start - cstart;
          }
        }
    }

    public int read() {
        if (end  == start) {
            return -1;
        }
        return (buff[start++] & 0xFF);

    }

    public int substract(BBuffer src) {

        if (end == start) {
            return -1;
        }

        int len = getLength();
        src.append(buff, start, len);
        start = end;
        return len;

    }

    public int substract(byte src[], int off, int len)  {

        if ((end - start) == 0) {
            return -1;
        }

        int n = len;
        if (len > getLength()) {
            n = getLength();
        }
        System.arraycopy(buff, start, src, off, n);
        start += n;
        return n;

    }

    public String toString() {
        return toString(DEFAULT_CHARACTER_ENCODING);
    }

    public String toString(String enc) {
        if (null == buff) {
            return null;
        } else if (end == start) {
            return "";
        }

        String strValue = null;
        try {
            if (enc == null) {
                enc = DEFAULT_CHARACTER_ENCODING;
            }

            strValue = new String(buff, start, end - start, enc);
            /*
             * Does not improve the speed too much on most systems, it's safer
             * to use the "clasical" new String().
             *
             * Most overhead is in creating char[] and copying, the internal
             * implementation of new String() is very close to what we do. The
             * decoder is nice for large buffers and if we don't go to String (
             * so we can take advantage of reduced GC)
             *
             * // Method is commented out, in: return B2CConverter.decodeString(
             * enc );
             */
        } catch (java.io.UnsupportedEncodingException e) {
            // Use the platform encoding in that case; the usage of a bad
            // encoding will have been logged elsewhere already
            strValue = new String(buff, start, end - start);
        }
        return strValue;
    }

    public void wrapTo(BBuffer res) {
        res.setBytes(buff, start, remaining());
    }

    /**
     * Convert specified String to a byte array. This ONLY WORKS for ascii, UTF
     * chars will be truncated.
     *
     * @param value
     *            to convert to byte array
     * @return the byte array value
     */
    public static final byte[] convertToBytes(String value) {
        byte[] result = new byte[value.length()];
        for (int i = 0; i < value.length(); i++) {
            result[i] = (byte) value.charAt(i);
        }
        return result;
    }

    /**
     * Find a character, no side effects.
     *
     * @return index of char if found, -1 if not
     */
    public static int findChar(byte buf[], int start, int end, char c) {
        byte b = (byte) c;
        int offset = start;
        while (offset < end) {
            if (buf[offset] == b) {
                return offset;
            }
            offset++;
        }
        return -1;
    }
    private static int hashBytes(byte buff[], int start, int bytesLen) {
        int max = start + bytesLen;
        byte bb[] = buff;
        int code = 0;
        for (int i = start; i < max; i++) {
            code = code * 31 + bb[i];
            // TODO: if > 0x7F, convert to chars / switch to UTF8
        }
        return code;
    }

    public static boolean hasLFLF(BBucket bucket) {
        int pos = bucket.position();
        int lastValid = bucket.limit();
        byte[] buf = bucket.array();

        for (int i = pos; i < lastValid; i++) {
            byte chr = buf[i];
            if (chr == LF) {
                if (i + 1 < lastValid && buf[i + 1] == CR) {
                    // \n\r\n
                    i++;
                }
                if (i + 1 < lastValid && buf[i + 1] == LF) {
                    return true; // \n\n
                }
            } else if (chr == CR) {
                if (i + 1 < lastValid && buf[i + 1] == CR) {
                    return true; // \r\r
                }
                if (i + 1 < lastValid && buf[i + 1] == LF) {
                        // \r\n
                    i++; // skip LF
                    if (i + 1 < lastValid && buf[i + 1] == CR &&
                            i + 2 < lastValid && buf[i + 2] == LF) {
                        i++;
                        return true;
                    }
                }

            }
        }
        return false;
    }

    public static int indexOf(byte bytes[], int off, int end, char qq) {
        // Works only for UTF
        while (off < end) {
            byte b = bytes[off];
            if (b == qq)
                return off;
            off++;
        }
        return -1;
    }

    /**
     * Returns true if the specified ASCII character is a digit.
     */

    public static boolean isDigit(int c) {
        return isDigit[c & 0xff];
    }

    /**
     * Parses an unsigned integer from the specified subarray of bytes.
     * @param b the bytes to parse
     * @param off the start offset of the bytes
     * @param len the length of the bytes
     * @exception NumberFormatException if the integer format was invalid
     */
    public static int parseInt(byte[] b, int off, int len)
        throws NumberFormatException
    {
        int c;

        if (b == null || len <= 0 || !isDigit(c = b[off++])) {
            throw new NumberFormatException();
        }

        int n = c - '0';

        while (--len > 0) {
            if (!isDigit(c = b[off++])) {
                throw new NumberFormatException();
            }
            n = n * 10 + c - '0';
        }

        return n;
    }

    /**
     * Parses an unsigned long from the specified subarray of bytes.
     * @param b the bytes to parse
     * @param off the start offset of the bytes
     * @param len the length of the bytes
     * @exception NumberFormatException if the long format was invalid
     */
    public static long parseLong(byte[] b, int off, int len)
        throws NumberFormatException
    {
        int c;

        if (b == null || len <= 0 || !isDigit(c = b[off++])) {
            throw new NumberFormatException();
        }

        long n = c - '0';
        long m;

        while (--len > 0) {
            if (!isDigit(c = b[off++])) {
                throw new NumberFormatException();
            }
            m = n * 10 + c - '0';

            if (m < n) {
                // Overflow
                throw new NumberFormatException();
            } else {
                n = m;
            }
        }

        return n;
    }



    /**
     * Returns the lower case equivalent of the specified ASCII character.
     */
    public static int toLower(int c) {
        if (c > 0x7f) return c;
        return toLower[c & 0xff] & 0xff;
    }

    /**
     * Returns true if the specified ASCII character is upper case.
     */

    public static boolean isUpper(int c) {
        return c < 0x7f && isUpper[c];
    }

    /**
     * A slice of a bucket, holding reference to a parent bucket.
     *
     * This is used when a filter splits a bucket - the original
     * will be replaced with 1 or more slices. When all slices are
     * released, the parent will also be released.
     *
     * It is not possible to add data.
     *
     * @author Costin Manolache
     */
    static class IOBucketWrap extends BBuffer {
        //IOBucket parent;


        public BBuffer makeSpace(int count) {
            throw new RuntimeException("Attempting to change buffer " +
            		"on a wrapped BBuffer");
        }

        public void release() {
//            if (parent != null) {
//                parent.release();
//            }
        }

        public void setBytes(byte[] b, int off, int len) {
            super.setBytesInternal(b, off, len);
        }
    }


}
