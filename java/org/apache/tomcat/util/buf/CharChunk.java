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

import java.io.IOException;

/**
 * Utilities to manipulate char chunks. While String is the easiest way to
 * manipulate chars ( search, substrings, etc), it is known to not be the most
 * efficient solution - Strings are designed as immutable and secure objects.
 *
 * @author dac@sun.com
 * @author James Todd [gonzo@sun.com]
 * @author Costin Manolache
 * @author Remy Maucherat
 */
public final class CharChunk extends AbstractChunk implements CharSequence {

    private static final long serialVersionUID = 1L;

    /**
     * Input interface, used when the buffer is empty.
     */
    public static interface CharInputChannel {

        /**
         * Read new characters.
         *
         * @return The number of characters read
         *
         * @throws IOException If an I/O error occurs during reading
         */
        public int realReadChars() throws IOException;
    }

    /**
     * When we need more space we'll either grow the buffer ( up to the limit )
     * or send it to a channel.
     */
    public static interface CharOutputChannel {

        /**
         * Send the bytes ( usually the internal conversion buffer ). Expect 8k
         * output if the buffer is full.
         *
         * @param buf characters that will be written
         * @param off offset in the characters array
         * @param len length that will be written
         * @throws IOException If an I/O occurs while writing the characters
         */
        public void realWriteChars(char buf[], int off, int len) throws IOException;
    }

    // --------------------

    // char[]
    private char[] buff;

    // transient as serialization is primarily for values via, e.g. JMX
    private transient CharInputChannel in = null;
    private transient CharOutputChannel out = null;


    /**
     * Creates a new, uninitialized CharChunk object.
     */
    public CharChunk() {
    }


    public CharChunk(int initial) {
        allocate(initial, -1);
    }


    // --------------------

    @Override
    public Object clone() throws CloneNotSupportedException {
        return super.clone();
    }


    // -------------------- Setup --------------------

    public void allocate(int initial, int limit) {
        if (buff == null || buff.length < initial) {
            buff = new char[initial];
        }
        setLimit(limit);
        start = 0;
        end = 0;
        isSet = true;
        hasHashCode = false;
    }


    /**
     * Sets the buffer to the specified subarray of characters.
     *
     * @param c the characters
     * @param off the start offset of the characters
     * @param len the length of the characters
     */
    public void setChars(char[] c, int off, int len) {
        buff = c;
        start = off;
        end = start + len;
        isSet = true;
        hasHashCode = false;
    }


    /**
     * @return the buffer.
     */
    public char[] getChars() {
        return getBuffer();
    }


    /**
     * @return the buffer.
     */
    public char[] getBuffer() {
        return buff;
    }


    /**
     * When the buffer is empty, read the data from the input channel.
     *
     * @param in The input channel
     */
    public void setCharInputChannel(CharInputChannel in) {
        this.in = in;
    }


    /**
     * When the buffer is full, write the data to the output channel. Also used
     * when large amount of data is appended. If not set, the buffer will grow
     * to the limit.
     *
     * @param out The output channel
     */
    public void setCharOutputChannel(CharOutputChannel out) {
        this.out = out;
    }


    // -------------------- Adding data to the buffer --------------------

    public void append(char b) throws IOException {
        makeSpace(1);
        int limit = getLimitInternal();

        // couldn't make space
        if (end >= limit) {
            flushBuffer();
        }
        buff[end++] = b;
    }


    public void append(CharChunk src) throws IOException {
        append(src.getBuffer(), src.getOffset(), src.getLength());
    }


    /**
     * Add data to the buffer.
     *
     * @param src Char array
     * @param off Offset
     * @param len Length
     * @throws IOException Writing overflow data to the output channel failed
     */
    public void append(char src[], int off, int len) throws IOException {
        // will grow, up to limit
        makeSpace(len);
        int limit = getLimitInternal();

        // Optimize on a common case.
        // If the buffer is empty and the source is going to fill up all the
        // space in buffer, may as well write it directly to the output,
        // and avoid an extra copy
        if (len == limit && end == start && out != null) {
            out.realWriteChars(src, off, len);
            return;
        }

        // if we are below the limit
        if (len <= limit - end) {
            System.arraycopy(src, off, buff, end, len);
            end += len;
            return;
        }

        // Need more space than we can afford, need to flush buffer.

        // The buffer is already at (or bigger than) limit.

        // Optimization:
        // If len-avail < length (i.e. after we fill the buffer with what we
        // can, the remaining will fit in the buffer) we'll just copy the first
        // part, flush, then copy the second part - 1 write and still have some
        // space for more. We'll still have 2 writes, but we write more on the first.

        if (len + end < 2 * limit) {
            /*
             * If the request length exceeds the size of the output buffer,
             * flush the output buffer and then write the data directly. We
             * can't avoid 2 writes, but we can write more on the second
             */
            int avail = limit - end;
            System.arraycopy(src, off, buff, end, avail);
            end += avail;

            flushBuffer();

            System.arraycopy(src, off + avail, buff, end, len - avail);
            end += len - avail;

        } else { // len > buf.length + avail
            // long write - flush the buffer and write the rest
            // directly from source
            flushBuffer();

            out.realWriteChars(src, off, len);
        }
    }


    /**
     * Append a string to the buffer.
     *
     * @param s The string
     * @throws IOException Writing overflow data to the output channel failed
     */
    public void append(String s) throws IOException {
        append(s, 0, s.length());
    }


    /**
     * Append a string to the buffer.
     *
     * @param s The string
     * @param off Offset
     * @param len Length
     * @throws IOException Writing overflow data to the output channel failed
     */
    public void append(String s, int off, int len) throws IOException {
        if (s == null) {
            return;
        }

        // will grow, up to limit
        makeSpace(len);
        int limit = getLimitInternal();

        int sOff = off;
        int sEnd = off + len;
        while (sOff < sEnd) {
            int d = min(limit - end, sEnd - sOff);
            s.getChars(sOff, sOff + d, buff, end);
            sOff += d;
            end += d;
            if (end >= limit) {
                flushBuffer();
            }
        }
    }


    // -------------------- Removing data from the buffer --------------------

    public int substract() throws IOException {
        if (checkEof()) {
            return -1;
        }
        return buff[start++];
    }


    public int substract(char dest[], int off, int len) throws IOException {
        if (checkEof()) {
            return -1;
        }
        int n = len;
        if (len > getLength()) {
            n = getLength();
        }
        System.arraycopy(buff, start, dest, off, n);
        start += n;
        return n;
    }


    private boolean checkEof() throws IOException {
        if ((end - start) == 0) {
            if (in == null) {
                return true;
            }
            int n = in.realReadChars();
            if (n < 0) {
                return true;
            }
        }
        return false;
    }


    /**
     * Send the buffer to the sink. Called by append() when the limit is
     * reached. You can also call it explicitly to force the data to be written.
     *
     * @throws IOException Writing overflow data to the output channel failed
     */
    public void flushBuffer() throws IOException {
        // assert out!=null
        if (out == null) {
            throw new IOException("Buffer overflow, no sink " + getLimit() + " " + buff.length);
        }
        out.realWriteChars(buff, start, end - start);
        end = start;
    }


    /**
     * Make space for len chars. If len is small, allocate a reserve space too.
     * Never grow bigger than the limit or {@link AbstractChunk#ARRAY_MAX_SIZE}.
     *
     * @param count The size
     */
    public void makeSpace(int count) {
        char[] tmp = null;

        int limit = getLimitInternal();

        long newSize;
        long desiredSize = end + count;

        // Can't grow above the limit
        if (desiredSize > limit) {
            desiredSize = limit;
        }

        if (buff == null) {
            if (desiredSize < 256) {
                desiredSize = 256; // take a minimum
            }
            buff = new char[(int) desiredSize];
        }

        // limit < buf.length (the buffer is already big)
        // or we already have space XXX
        if (desiredSize <= buff.length) {
            return;
        }
        // grow in larger chunks
        if (desiredSize < 2L * buff.length) {
            newSize = buff.length * 2L;
        } else {
            newSize = buff.length * 2L + count;
        }

        if (newSize > limit) {
            newSize = limit;
        }
        tmp = new char[(int) newSize];

        // Some calling code assumes buffer will not be compacted
        System.arraycopy(buff, 0, tmp, 0, end);
        buff = tmp;
        tmp = null;
    }


    // -------------------- Conversion and getters --------------------

    @Override
    public String toString() {
        if (isNull()) {
            return null;
        } else if (end - start == 0) {
            return "";
        }
        return StringCache.toString(this);
    }


    public String toStringInternal() {
        return new String(buff, start, end - start);
    }


    // -------------------- equals --------------------

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof CharChunk) {
            return equals((CharChunk) obj);
        }
        return false;
    }


    /**
     * Compares the message bytes to the specified String object.
     *
     * @param s the String to compare
     * @return <code>true</code> if the comparison succeeded, <code>false</code>
     *         otherwise
     */
    public boolean equals(String s) {
        char[] c = buff;
        int len = end - start;
        if (c == null || len != s.length()) {
            return false;
        }
        int off = start;
        for (int i = 0; i < len; i++) {
            if (c[off++] != s.charAt(i)) {
                return false;
            }
        }
        return true;
    }


    /**
     * Compares the message bytes to the specified String object.
     *
     * @param s the String to compare
     * @return <code>true</code> if the comparison succeeded, <code>false</code>
     *         otherwise
     */
    public boolean equalsIgnoreCase(String s) {
        char[] c = buff;
        int len = end - start;
        if (c == null || len != s.length()) {
            return false;
        }
        int off = start;
        for (int i = 0; i < len; i++) {
            if (Ascii.toLower(c[off++]) != Ascii.toLower(s.charAt(i))) {
                return false;
            }
        }
        return true;
    }


    public boolean equals(CharChunk cc) {
        return equals(cc.getChars(), cc.getOffset(), cc.getLength());
    }


    public boolean equals(char b2[], int off2, int len2) {
        char b1[] = buff;
        if (b1 == null && b2 == null) {
            return true;
        }

        int len = end - start;
        if (len != len2 || b1 == null || b2 == null) {
            return false;
        }

        int off1 = start;

        while (len-- > 0) {
            if (b1[off1++] != b2[off2++]) {
                return false;
            }
        }
        return true;
    }


    /**
     * @return <code>true</code> if the message bytes starts with the specified
     *         string.
     * @param s The string
     */
    public boolean startsWith(String s) {
        char[] c = buff;
        int len = s.length();
        if (c == null || len > end - start) {
            return false;
        }
        int off = start;
        for (int i = 0; i < len; i++) {
            if (c[off++] != s.charAt(i)) {
                return false;
            }
        }
        return true;
    }


    /**
     * Returns true if the buffer starts with the specified string.
     *
     * @param s the string
     * @param pos The position
     *
     * @return <code>true</code> if the start matches
     */
    public boolean startsWithIgnoreCase(String s, int pos) {
        char[] c = buff;
        int len = s.length();
        if (c == null || len + pos > end - start) {
            return false;
        }
        int off = start + pos;
        for (int i = 0; i < len; i++) {
            if (Ascii.toLower(c[off++]) != Ascii.toLower(s.charAt(i))) {
                return false;
            }
        }
        return true;
    }


    /**
     * @return <code>true</code> if the message bytes end with the specified
     *         string.
     * @param s The string
     */
    public boolean endsWith(String s) {
        char[] c = buff;
        int len = s.length();
        if (c == null || len > end - start) {
            return false;
        }
        int off = end - len;
        for (int i = 0; i < len; i++) {
            if (c[off++] != s.charAt(i)) {
                return false;
            }
        }
        return true;
    }


    @Override
    protected int getBufferElement(int index) {
        return buff[index];
    }


    public int indexOf(char c) {
        return indexOf(c, start);
    }


    /**
     * Returns the first instance of the given character in this CharChunk
     * starting at the specified char. If the character is not found, -1 is
     * returned. <br>
     *
     * @param c The character
     * @param starting The start position
     * @return The position of the first instance of the character or -1 if the
     *         character is not found.
     */
    public int indexOf(char c, int starting) {
        int ret = indexOf(buff, start + starting, end, c);
        return (ret >= start) ? ret - start : -1;
    }


    /**
     * Returns the first instance of the given character in the given char array
     * between the specified start and end. <br>
     *
     * @param chars The array to search
     * @param start The point to start searching from in the array
     * @param end The point to stop searching in the array
     * @param s The character to search for
     * @return The position of the first instance of the character or -1 if the
     *         character is not found.
     */
    public static int indexOf(char chars[], int start, int end, char s) {
        int offset = start;

        while (offset < end) {
            char c = chars[offset];
            if (c == s) {
                return offset;
            }
            offset++;
        }
        return -1;
    }


    // -------------------- utils
    private int min(int a, int b) {
        if (a < b) {
            return a;
        }
        return b;
    }


    // Char sequence impl

    @Override
    public char charAt(int index) {
        return buff[index + start];
    }


    @Override
    public CharSequence subSequence(int start, int end) {
        try {
            CharChunk result = (CharChunk) this.clone();
            result.setOffset(this.start + start);
            result.setEnd(this.start + end);
            return result;
        } catch (CloneNotSupportedException e) {
            // Cannot happen
            return null;
        }
    }


    @Override
    public int length() {
        return end - start;
    }

    /**
     * NO-OP.
     *
     * @param optimizedWrite Ignored
     *
     * @deprecated Unused code. This is now a NO-OP and will be removed without
     *             replacement in Tomcat 10.
     */
    @Deprecated
    public void setOptimizedWrite(boolean optimizedWrite) {
    }
}
