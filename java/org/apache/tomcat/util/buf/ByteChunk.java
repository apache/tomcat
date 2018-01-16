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
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

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

// TODO: This class could either extend ByteBuffer, or better a ByteBuffer
// inside this way it could provide the search/etc on ByteBuffer, as a helper.

/**
 * This class is used to represent a chunk of bytes, and utilities to manipulate
 * byte[].
 *
 * The buffer can be modified and used for both input and output.
 *
 * There are 2 modes: The chunk can be associated with a sink - ByteInputChannel
 * or ByteOutputChannel, which will be used when the buffer is empty (on input)
 * or filled (on output). For output, it can also grow. This operating mode is
 * selected by calling setLimit() or allocate(initial, limit) with limit != -1.
 *
 * Various search and append method are defined - similar with String and
 * StringBuffer, but operating on bytes.
 *
 * This is important because it allows processing the http headers directly on
 * the received bytes, without converting to chars and Strings until the strings
 * are needed. In addition, the charset is determined later, from headers or
 * user code.
 *
 * @author dac@sun.com
 * @author James Todd [gonzo@sun.com]
 * @author Costin Manolache
 * @author Remy Maucherat
 */
public final class ByteChunk extends AbstractChunk {

    private static final long serialVersionUID = 1L;

    /**
     * Input interface, used when the buffer is empty.
     *
     * Same as java.nio.channels.ReadableByteChannel
     */
    public static interface ByteInputChannel {

        /**
         * Read new bytes.
         *
         * @return The number of bytes read
         *
         * @throws IOException If an I/O error occurs during reading
         */
        public int realReadBytes() throws IOException;
    }

    /**
     * When we need more space we'll either grow the buffer ( up to the limit )
     * or send it to a channel.
     *
     * Same as java.nio.channel.WritableByteChannel.
     */
    public static interface ByteOutputChannel {

        /**
         * Send the bytes ( usually the internal conversion buffer ). Expect 8k
         * output if the buffer is full.
         *
         * @param buf bytes that will be written
         * @param off offset in the bytes array
         * @param len length that will be written
         * @throws IOException If an I/O occurs while writing the bytes
         */
        public void realWriteBytes(byte buf[], int off, int len) throws IOException;


        /**
         * Send the bytes ( usually the internal conversion buffer ). Expect 8k
         * output if the buffer is full.
         *
         * @param from bytes that will be written
         * @throws IOException If an I/O occurs while writing the bytes
         */
        public void realWriteBytes(ByteBuffer from) throws IOException;
    }

    // --------------------

    /**
     * Default encoding used to convert to strings. It should be UTF8, as most
     * standards seem to converge, but the servlet API requires 8859_1, and this
     * object is used mostly for servlets.
     */
    public static final Charset DEFAULT_CHARSET = StandardCharsets.ISO_8859_1;

    private transient Charset charset;

    // byte[]
    private byte[] buff;

    // transient as serialization is primarily for values via, e.g. JMX
    private transient ByteInputChannel in = null;
    private transient ByteOutputChannel out = null;


    /**
     * Creates a new, uninitialized ByteChunk object.
     */
    public ByteChunk() {
    }


    public ByteChunk(int initial) {
        allocate(initial, -1);
    }


    private void writeObject(ObjectOutputStream oos) throws IOException {
        oos.defaultWriteObject();
        oos.writeUTF(getCharset().name());
    }


    private void readObject(ObjectInputStream ois) throws ClassNotFoundException, IOException {
        ois.defaultReadObject();
        this.charset = Charset.forName(ois.readUTF());
    }


    @Override
    public Object clone() throws CloneNotSupportedException {
        return super.clone();
    }


    @Override
    public void recycle() {
        super.recycle();
        charset = null;
    }


    // -------------------- Setup --------------------

    public void allocate(int initial, int limit) {
        if (buff == null || buff.length < initial) {
            buff = new byte[initial];
        }
        setLimit(limit);
        start = 0;
        end = 0;
        isSet = true;
        hasHashCode = false;
    }


    /**
     * Sets the buffer to the specified subarray of bytes.
     *
     * @param b the ascii bytes
     * @param off the start offset of the bytes
     * @param len the length of the bytes
     */
    public void setBytes(byte[] b, int off, int len) {
        buff = b;
        start = off;
        end = start + len;
        isSet = true;
        hasHashCode = false;
    }


    public void setCharset(Charset charset) {
        this.charset = charset;
    }


    public Charset getCharset() {
        if (charset == null) {
            charset = DEFAULT_CHARSET;
        }
        return charset;
    }


    /**
     * @return the buffer.
     */
    public byte[] getBytes() {
        return getBuffer();
    }


    /**
     * @return the buffer.
     */
    public byte[] getBuffer() {
        return buff;
    }


    /**
     * When the buffer is empty, read the data from the input channel.
     *
     * @param in The input channel
     */
    public void setByteInputChannel(ByteInputChannel in) {
        this.in = in;
    }


    /**
     * When the buffer is full, write the data to the output channel. Also used
     * when large amount of data is appended. If not set, the buffer will grow
     * to the limit.
     *
     * @param out The output channel
     */
    public void setByteOutputChannel(ByteOutputChannel out) {
        this.out = out;
    }


    // -------------------- Adding data to the buffer --------------------

    public void append(byte b) throws IOException {
        makeSpace(1);
        int limit = getLimitInternal();

        // couldn't make space
        if (end >= limit) {
            flushBuffer();
        }
        buff[end++] = b;
    }


    public void append(ByteChunk src) throws IOException {
        append(src.getBytes(), src.getStart(), src.getLength());
    }


    /**
     * Add data to the buffer.
     *
     * @param src Bytes array
     * @param off Offset
     * @param len Length
     * @throws IOException Writing overflow data to the output channel failed
     */
    public void append(byte src[], int off, int len) throws IOException {
        // will grow, up to limit
        makeSpace(len);
        int limit = getLimitInternal();

        // Optimize on a common case.
        // If the buffer is empty and the source is going to fill up all the
        // space in buffer, may as well write it directly to the output,
        // and avoid an extra copy
        if (len == limit && end == start && out != null) {
            out.realWriteBytes(src, off, len);
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

        // We chunk the data into slices fitting in the buffer limit, although
        // if the data is written directly if it doesn't fit.

        int avail = limit - end;
        System.arraycopy(src, off, buff, end, avail);
        end += avail;

        flushBuffer();

        int remain = len - avail;

        while (remain > (limit - end)) {
            out.realWriteBytes(src, (off + len) - remain, limit - end);
            remain = remain - (limit - end);
        }

        System.arraycopy(src, (off + len) - remain, buff, end, remain);
        end += remain;
    }


    /**
     * Add data to the buffer.
     *
     * @param from the ByteBuffer with the data
     * @throws IOException Writing overflow data to the output channel failed
     */
    public void append(ByteBuffer from) throws IOException {
        int len = from.remaining();

        // will grow, up to limit
        makeSpace(len);
        int limit = getLimitInternal();

        // Optimize on a common case.
        // If the buffer is empty and the source is going to fill up all the
        // space in buffer, may as well write it directly to the output,
        // and avoid an extra copy
        if (len == limit && end == start && out != null) {
            out.realWriteBytes(from);
            from.position(from.limit());
            return;
        }
        // if we have limit and we're below
        if (len <= limit - end) {
            // makeSpace will grow the buffer to the limit,
            // so we have space
            from.get(buff, end, len);
            end += len;
            return;
        }

        // need more space than we can afford, need to flush
        // buffer

        // the buffer is already at ( or bigger than ) limit

        // We chunk the data into slices fitting in the buffer limit, although
        // if the data is written directly if it doesn't fit

        int avail = limit - end;
        from.get(buff, end, avail);
        end += avail;

        flushBuffer();

        int fromLimit = from.limit();
        int remain = len - avail;
        avail = limit - end;
        while (remain >= avail) {
            from.limit(from.position() + avail);
            out.realWriteBytes(from);
            from.position(from.limit());
            remain = remain - avail;
        }

        from.limit(fromLimit);
        from.get(buff, end, remain);
        end += remain;
    }


    // -------------------- Removing data from the buffer --------------------

    public int substract() throws IOException {
        if (checkEof()) {
            return -1;
        }
        return buff[start++] & 0xFF;
    }


    public byte substractB() throws IOException {
        if (checkEof()) {
            return -1;
        }
        return buff[start++];
    }


    public int substract(byte dest[], int off, int len) throws IOException {
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


    /**
     * Transfers bytes from the buffer to the specified ByteBuffer. After the
     * operation the position of the ByteBuffer will be returned to the one
     * before the operation, the limit will be the position incremented by the
     * number of the transfered bytes.
     *
     * @param to the ByteBuffer into which bytes are to be written.
     * @return an integer specifying the actual number of bytes read, or -1 if
     *         the end of the stream is reached
     * @throws IOException if an input or output exception has occurred
     */
    public int substract(ByteBuffer to) throws IOException {
        if (checkEof()) {
            return -1;
        }
        int n = Math.min(to.remaining(), getLength());
        to.put(buff, start, n);
        to.limit(to.position());
        to.position(to.position() - n);
        start += n;
        return n;
    }


    private boolean checkEof() throws IOException {
        if ((end - start) == 0) {
            if (in == null) {
                return true;
            }
            int n = in.realReadBytes();
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
        out.realWriteBytes(buff, start, end - start);
        end = start;
    }


    /**
     * Make space for len bytes. If len is small, allocate a reserve space too.
     * Never grow bigger than the limit or {@link AbstractChunk#ARRAY_MAX_SIZE}.
     *
     * @param count The size
     */
    public void makeSpace(int count) {
        byte[] tmp = null;

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
            buff = new byte[(int) desiredSize];
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
        tmp = new byte[(int) newSize];

        // Compacts buffer
        System.arraycopy(buff, start, tmp, 0, end - start);
        buff = tmp;
        tmp = null;
        end = end - start;
        start = 0;
    }


    // -------------------- Conversion and getters --------------------

    @Override
    public String toString() {
        if (null == buff) {
            return null;
        } else if (end - start == 0) {
            return "";
        }
        return StringCache.toString(this);
    }


    public String toStringInternal() {
        if (charset == null) {
            charset = DEFAULT_CHARSET;
        }
        // new String(byte[], int, int, Charset) takes a defensive copy of the
        // entire byte array. This is expensive if only a small subset of the
        // bytes will be used. The code below is from Apache Harmony.
        CharBuffer cb = charset.decode(ByteBuffer.wrap(buff, start, end - start));
        return new String(cb.array(), cb.arrayOffset(), cb.length());
    }


    public long getLong() {
        return Ascii.parseLong(buff, start, end - start);
    }


    // -------------------- equals --------------------

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof ByteChunk) {
            return equals((ByteChunk) obj);
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
        // XXX ENCODING - this only works if encoding is UTF8-compat
        // ( ok for tomcat, where we compare ascii - header names, etc )!!!

        byte[] b = buff;
        int len = end - start;
        if (b == null || len != s.length()) {
            return false;
        }
        int off = start;
        for (int i = 0; i < len; i++) {
            if (b[off++] != s.charAt(i)) {
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
        byte[] b = buff;
        int len = end - start;
        if (b == null || len != s.length()) {
            return false;
        }
        int off = start;
        for (int i = 0; i < len; i++) {
            if (Ascii.toLower(b[off++]) != Ascii.toLower(s.charAt(i))) {
                return false;
            }
        }
        return true;
    }


    public boolean equals(ByteChunk bb) {
        return equals(bb.getBytes(), bb.getStart(), bb.getLength());
    }


    public boolean equals(byte b2[], int off2, int len2) {
        byte b1[] = buff;
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


    public boolean equals(CharChunk cc) {
        return equals(cc.getChars(), cc.getStart(), cc.getLength());
    }


    public boolean equals(char c2[], int off2, int len2) {
        // XXX works only for enc compatible with ASCII/UTF !!!
        byte b1[] = buff;
        if (c2 == null && b1 == null) {
            return true;
        }

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


    /**
     * Returns true if the buffer starts with the specified string.
     *
     * @param s the string
     * @param pos The position
     *
     * @return <code>true</code> if the start matches
     */
    public boolean startsWithIgnoreCase(String s, int pos) {
        byte[] b = buff;
        int len = s.length();
        if (b == null || len + pos > end - start) {
            return false;
        }
        int off = start + pos;
        for (int i = 0; i < len; i++) {
            if (Ascii.toLower(b[off++]) != Ascii.toLower(s.charAt(i))) {
                return false;
            }
        }
        return true;
    }


    @Override
    protected int getBufferElement(int index) {
        return buff[index];
    }


    /**
     * Returns the first instance of the given character in this ByteChunk
     * starting at the specified byte. If the character is not found, -1 is
     * returned. <br>
     * NOTE: This only works for characters in the range 0-127.
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
     * Returns the first instance of the given character in the given byte array
     * between the specified start and end. <br>
     * NOTE: This only works for characters in the range 0-127.
     *
     * @param bytes The array to search
     * @param start The point to start searching from in the array
     * @param end The point to stop searching in the array
     * @param s The character to search for
     * @return The position of the first instance of the character or -1 if the
     *         character is not found.
     */
    public static int indexOf(byte bytes[], int start, int end, char s) {
        int offset = start;

        while (offset < end) {
            byte b = bytes[offset];
            if (b == s) {
                return offset;
            }
            offset++;
        }
        return -1;
    }


    /**
     * Returns the first instance of the given byte in the byte array between
     * the specified start and end.
     *
     * @param bytes The byte array to search
     * @param start The point to start searching from in the byte array
     * @param end The point to stop searching in the byte array
     * @param b The byte to search for
     * @return The position of the first instance of the byte or -1 if the byte
     *         is not found.
     */
    public static int findByte(byte bytes[], int start, int end, byte b) {
        int offset = start;
        while (offset < end) {
            if (bytes[offset] == b) {
                return offset;
            }
            offset++;
        }
        return -1;
    }


    /**
     * Returns the first instance of any of the given bytes in the byte array
     * between the specified start and end.
     *
     * @param bytes The byte array to search
     * @param start The point to start searching from in the byte array
     * @param end The point to stop searching in the byte array
     * @param b The array of bytes to search for
     * @return The position of the first instance of the byte or -1 if the byte
     *         is not found.
     */
    public static int findBytes(byte bytes[], int start, int end, byte b[]) {
        int blen = b.length;
        int offset = start;
        while (offset < end) {
            for (int i = 0; i < blen; i++) {
                if (bytes[offset] == b[i]) {
                    return offset;
                }
            }
            offset++;
        }
        return -1;
    }


    /**
     * Convert specified String to a byte array. This ONLY WORKS for ascii, UTF
     * chars will be truncated.
     *
     * @param value to convert to byte array
     * @return the byte array value
     */
    public static final byte[] convertToBytes(String value) {
        byte[] result = new byte[value.length()];
        for (int i = 0; i < value.length(); i++) {
            result[i] = (byte) value.charAt(i);
        }
        return result;
    }
}
