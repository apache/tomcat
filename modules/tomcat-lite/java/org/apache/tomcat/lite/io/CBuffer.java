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
import java.nio.CharBuffer;


/**
 * Similar with StringBuilder or StringBuffer, but with access to the
 * raw buffer - this avoids copying the data.
 *
 * Utilities to manipluate char chunks. While String is the easiest way to
 * manipulate chars ( search, substrings, etc), it is known to not be the most
 * efficient solution - Strings are designed as imutable and secure objects.
 *
 * @author dac@sun.com
 * @author James Todd [gonzo@sun.com]
 * @author Costin Manolache
 * @author Remy Maucherat
 */
public class CBuffer extends CBucket implements Cloneable,
        Appendable {


    /**
     * Creates a new, uninitialized CharChunk object.
     */
    public static CBuffer newInstance() {
        return new CBuffer();
    }

    private CBuffer() {
    }

    /**
     * Resets the message bytes to an uninitialized state.
     */
    public void recycle() {
        dirty();
        start = 0;
        end = 0;
    }

    /**
     * Same as String
     */
    public int hashCode() {
        int h = 0;
        int off = start;
        char val[] = value;

        for (int i = start; i < end; i++) {
            h = 31*h + val[off++];
        }
        return h;
    }

    public String toString() {
        if (null == value) {
            return null;
        } else if (end - start == 0) {
            return "";
        }
        return new String(value, start, end - start);
    }

    public void wrap(char[] buff, int start, int end) {
        dirty();
        this.value = buff;
        this.start = start;
        this.end = end;
    }

    public void wrap(CBucket buff, int off, int srcEnd) {
        dirty();
        this.value = buff.value;
        this.start = buff.start + off;
        this.end = this.start + srcEnd - off;
    }


    // ----------- Used for IOWriter / conversion ---------

    public char[] array() {
        return value;
    }

    public int position() {
        return start;
    }

    CharBuffer getAppendCharBuffer() {
        makeSpace(16);
        if (cb == null || cb.array() != value) {
            cb = CharBuffer.wrap(value, end, value.length - end);
        } else {
            cb.position(end);
            cb.limit(value.length);
        }
        return cb;
    }

    void returnNioBuffer(CharBuffer c) {
        dirty();
        start = c.position();
    }

    void returnAppendCharBuffer(CharBuffer c) {
        dirty();
        end = c.position();
    }

    // -------- Delete / replace ---------------

    /**
     * 'Delete' all chars after offset.
     *
     * @param offset
     */
    public void delete(int offset) {
       dirty();
       end = start + offset;
    }

    // -------------------- Adding data --------------------

    /**
     * Append methods take start and end - similar with this one.
     * The source is not modified.
     */
    @Override
    public CBuffer append(CharSequence csq, int astart, int aend)
            throws IOException {
        makeSpace(aend - astart);

        for (int i = astart; i < aend; i++) {
            value[end++] = csq.charAt(i);
        }
        return this;
    }

    public CBuffer append(char b) {
        makeSpace(1);
        value[end++] = b;
        return this;
    }

    public CBuffer append(int i) {
        // TODO: can be optimizeed...
        append(Integer.toString(i));
        return this;
    }

    /**
     * Add data to the buffer
     */
    public CBuffer append(char src[], int srcStart, int srcEnd)  {
        int len = srcEnd - srcStart;
        if (len == 0) {
            return this;
        }
        // will grow, up to limit
        makeSpace(len);

        // assert: makeSpace made enough space
        System.arraycopy(src, srcStart, value, end, len);
        end += len;
        return this;
    }

    /**
     * Add data to the buffer
     */
    public CBuffer append(StringBuffer sb) {
        int len = sb.length();
        if (len == 0) {
            return this;
        }
        makeSpace(len);
        sb.getChars(0, len, value, end);
        end += len;
        return this;
    }

    /**
     * Append a string to the buffer
     */
    public CBuffer append(String s) {
        if (s == null || s.length() == 0) {
            return this;
        }
        append(s, 0, s.length());
        return this;
    }


    /**
     * Append a string to the buffer
     */
    public CBuffer append(String s, int off, int srcEnd) {
        if (s == null)
            return this;

        // will grow, up to limit
        makeSpace(srcEnd - off);

        // assert: makeSpace made enough space
        s.getChars(off, srcEnd, value, end);
        end += srcEnd - off;
        return this;
    }

    // TODO: long, int conversions -> get from harmony Long
    public CBuffer appendInt(int i) {
        // TODO: copy from harmony StringBuffer
        append(Integer.toString(i));
        return this;
    }


    public Appendable append(CharSequence cs) {
        if (cs instanceof CBuffer) {
            CBuffer src = (CBuffer) cs;
            append(src.value, src.start, src.end);
        } else if (cs instanceof String) {
            append((String) cs);
        } else {
            for (int i = 0; i < cs.length(); i++) {
                append(cs.charAt(i));
            }
        }
        return  this;
    }

    public CBuffer append(CBuffer src) {
        append(src.value, src.start, src.end);
        return  this;
    }


    public CBuffer append(BBucket bb) {
        byte[] bbuf = bb.array();
        int start = bb.position();
        appendAscii(bbuf, start, bb.remaining());
        return this;
    }

    public CBuffer appendAscii(byte[] bbuf, int start, int len) {
        makeSpace(len);
        char[] cbuf = value;
        for (int i = 0; i < len; i++) {
            cbuf[end + i] = (char) (bbuf[i + start] & 0xff);
        }
        end += len;
        return this;
    }


    public void toAscii(BBuffer bb) {
        for (int i = start; i < end; i++) {
            bb.append(value[i]);
        }
    }

    /**
     *  Append and advance CharBuffer.
     *
     * @param c
     */
    public CBuffer put(CharBuffer c) {
        append(c.array(), c.position(), c.limit());
        c.position(c.limit());
        return this;
    }

    // ------------- 'set' methods ---------------
    // equivalent with clean + append

    public CBuffer set(CBuffer csq, int off, int len) {
        recycle();
        append(csq.value, csq.start + off, csq.start + off + len);
        return this;
    }

    public CBuffer setChars(char[] c, int off, int len) {
        recycle();
        append(c, off, off + len);
        return this;
    }

    public CBuffer set(BBucket bb) {
        recycle();
        byte[] bbuf = bb.array();
        int start = bb.position();
        appendAscii(bbuf, start, bb.remaining());
        return this;
    }

    public CBuffer set(CharSequence csq) {
        recycle();
        append(csq);
        return this;
    }

    public CBuffer set(CBuffer csq) {
        recycle();
        append(csq);
        return this;
    }

    public CBuffer set(String csq) {
        recycle();
        append(csq);
        return this;
    }

    private void dirty() {
        hash = 0;
        strValue = null;
    }

    /**
     * Make space for len chars. If len is small, allocate a reserve space too.
     * Never grow bigger than limit.
     */
    private void makeSpace(int count) {
        dirty();
        char[] tmp = null;

        int newSize;
        int desiredSize = end + count;

        if (value == null) {
            if (desiredSize < 256)
                desiredSize = 256; // take a minimum
            value = new char[desiredSize];
        }

        // limit < buf.length ( the buffer is already big )
        // or we already have space XXX
        if (desiredSize <= value.length) {
            return;
        }
        // grow in larger chunks
        if (desiredSize < 2 * value.length) {
            newSize = value.length * 2;
            tmp = new char[newSize];
        } else {
            newSize = value.length * 2 + count;
            tmp = new char[newSize];
        }

        System.arraycopy(value, 0, tmp, 0, end);
        value = tmp;
        tmp = null;
    }

    public void toLower() {
        for (int i = start; i < end; i++) {
            char c = value[i];
            if (c < 0x7F) {
                if (BBuffer.isUpper(c)) {
                    value[i] = (char) BBuffer.toLower(c);
                }

            }
        }
    }


}
