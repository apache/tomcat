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
package org.apache.tomcat.lite.io;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.CharBuffer;


/**
 * Cut&pasted from Harmony buffered reader ( apache license ).
 * Changes:
 * - additional method to recycle to avoid re-allocating on
 * each request.
 */
public class BufferedIOReader extends BufferedReader {

    // Not recycled - the buffer is tied to the message/IOReader
    IOReader in;

    private String enc;
    boolean closed;
    private char[] buf;
    private int marklimit = -1;

    private int count;

    private int markpos = -1;

    private int pos;

    public BufferedIOReader(IOReader realReader) {
        // we're not using super - we override all methods, but need the
        // signature
        super(DUMMY_READER, 1);
        this.in = realReader;
        buf = new char[8192];
    }

    public void recycle() {
        enc = null;
        closed = false;

        if (in != null) {
            in.recycle();
        }
        marklimit = -1;
        count = 0;
        markpos = -1;
        pos = 0;
    }

    private void checkClosed() throws IOException {
        if (closed) throw new IOException("closed");
    }

    public int read(CharBuffer target) throws IOException {
        checkClosed();
        int len = target.remaining();
        int n = read(target.array(), target.position(), target.remaining());
        if (n > 0)
            target.position(target.position() + n);
        return n;
    }


    public int read(char[] cbuf) throws IOException {
        return read(cbuf, 0, cbuf.length);
    }


    /**
     * Closes this reader. This implementation closes the buffered source reader
     * and releases the buffer. Nothing is done if this reader has already been
     * closed.
     *
     * @throws IOException
     *             if an error occurs while closing this reader.
     */
    @Override
    public void close() throws IOException {
        synchronized (lock) {
            if (!isClosed()) {
                in.close();
                closed = true;
                // buf remains
            }
        }
    }

    private int fillbuf() throws IOException {
        if (markpos == -1 || (pos - markpos >= marklimit)) {
            /* Mark position not set or exceeded readlimit */
            int result = in.read(buf, 0, buf.length);
            if (result > 0) {
                markpos = -1;
                pos = 0;
                count = result == -1 ? 0 : result;
            }
            return result;
        }
        if (markpos == 0 && marklimit > buf.length) {
            /* Increase buffer size to accommodate the readlimit */
            int newLength = buf.length * 2;
            if (newLength > marklimit) {
                newLength = marklimit;
            }
            char[] newbuf = new char[newLength];
            System.arraycopy(buf, 0, newbuf, 0, buf.length);
            buf = newbuf;
        } else if (markpos > 0) {
            System.arraycopy(buf, markpos, buf, 0, buf.length - markpos);
        }

        /* Set the new position and mark position */
        pos -= markpos;
        count = markpos = 0;
        int charsread = in.read(buf, pos, buf.length - pos);
        count = charsread == -1 ? pos : pos + charsread;
        return charsread;
    }

    private boolean isClosed() {
        return closed;
    }

    @Override
    public void mark(int readlimit) throws IOException {
        if (readlimit < 0) {
            throw new IllegalArgumentException();
        }
        synchronized (lock) {
            checkClosed();
            marklimit = readlimit;
            markpos = pos;
        }
    }

    @Override
    public boolean markSupported() {
        return true;
    }

    @Override
    public int read() throws IOException {
        synchronized (lock) {
            checkClosed();
            /* Are there buffered characters available? */
            if (pos < count || fillbuf() != -1) {
                return buf[pos++];
            }
            markpos = -1;
            return -1;
        }
    }

    @Override
    public int read(char[] buffer, int offset, int length) throws IOException {
        synchronized (lock) {
            checkClosed();
            if (offset < 0 || offset > buffer.length - length || length < 0) {
                throw new IndexOutOfBoundsException();
            }
            if (length == 0) {
                return 0;
            }
            int required;
            if (pos < count) {
                /* There are bytes available in the buffer. */
                int copylength = count - pos >= length ? length : count - pos;
                System.arraycopy(buf, pos, buffer, offset, copylength);
                pos += copylength;
                if (copylength == length || !in.ready()) {
                    return copylength;
                }
                offset += copylength;
                required = length - copylength;
            } else {
                required = length;
            }

            while (true) {
                int read;
                /*
                 * If we're not marked and the required size is greater than the
                 * buffer, simply read the bytes directly bypassing the buffer.
                 */
                if (markpos == -1 && required >= buf.length) {
                    read = in.read(buffer, offset, required);
                    if (read == -1) {
                        return required == length ? -1 : length - required;
                    }
                } else {
                    if (fillbuf() == -1) {
                        return required == length ? -1 : length - required;
                    }
                    read = count - pos >= required ? required : count - pos;
                    System.arraycopy(buf, pos, buffer, offset, read);
                    pos += read;
                }
                required -= read;
                if (required == 0) {
                    return length;
                }
                if (!in.ready()) {
                    return length - required;
                }
                offset += read;
            }
        }
    }

    /**
     * Returns the next line of text available from this reader. A line is
     * represented by zero or more characters followed by {@code '\n'},
     * {@code '\r'}, {@code "\r\n"} or the end of the reader. The string does
     * not include the newline sequence.
     *
     * @return the contents of the line or {@code null} if no characters were
     *         read before the end of the reader has been reached.
     * @throws IOException
     *             if this reader is closed or some other I/O error occurs.
     */
    public String readLine() throws IOException {
        synchronized (lock) {
            checkClosed();
            /* Are there buffered characters available? */
            if ((pos >= count) && (fillbuf() == -1)) {
                return null;
            }
            for (int charPos = pos; charPos < count; charPos++) {
                char ch = buf[charPos];
                if (ch > '\r') {
                    continue;
                }
                if (ch == '\n') {
                    String res = new String(buf, pos, charPos - pos);
                    pos = charPos + 1;
                    return res;
                } else if (ch == '\r') {
                    String res = new String(buf, pos, charPos - pos);
                    pos = charPos + 1;
                    if (((pos < count) || (fillbuf() != -1))
                            && (buf[pos] == '\n')) {
                        pos++;
                    }
                    return res;
                }
            }

            char eol = '\0';
            StringBuilder result = new StringBuilder(80);
            /* Typical Line Length */

            result.append(buf, pos, count - pos);
            pos = count;
            while (true) {
                /* Are there buffered characters available? */
                if (pos >= count) {
                    if (eol == '\n') {
                        return result.toString();
                    }
                    // attempt to fill buffer
                    if (fillbuf() == -1) {
                        // characters or null.
                        return result.length() > 0 || eol != '\0' ? result
                                .toString() : null;
                    }
                }
                for (int charPos = pos; charPos < count; charPos++) {
                    if (eol == '\0') {
                        if ((buf[charPos] == '\n' || buf[charPos] == '\r')) {
                            eol = buf[charPos];
                        }
                    } else if (eol == '\r' && (buf[charPos] == '\n')) {
                        if (charPos > pos) {
                            result.append(buf, pos, charPos - pos - 1);
                        }
                        pos = charPos + 1;
                        return result.toString();
                    } else {
                        if (charPos > pos) {
                            result.append(buf, pos, charPos - pos - 1);
                        }
                        pos = charPos;
                        return result.toString();
                    }
                }
                if (eol == '\0') {
                    result.append(buf, pos, count - pos);
                } else {
                    result.append(buf, pos, count - pos - 1);
                }
                pos = count;
            }
        }

    }


    @Override
    public boolean ready() throws IOException {
        synchronized (lock) {
            checkClosed();
            return ((count - pos) > 0) || in.ready();
        }
    }

    @Override
    public void reset() throws IOException {
        synchronized (lock) {
            checkClosed();
            if (markpos == -1) {
                throw new IOException("No mark");
            }
            pos = markpos;
        }
    }

    @Override
    public long skip(long amount) throws IOException {
        if (amount < 0) {
            throw new IllegalArgumentException();
        }
        synchronized (lock) {
            checkClosed();
            if (amount < 1) {
                return 0;
            }
            if (count - pos >= amount) {
                pos += amount;
                return amount;
            }

            long read = count - pos;
            pos = count;
            while (read < amount) {
                if (fillbuf() == -1) {
                    return read;
                }
                if (count - pos >= amount - read) {
                    pos += amount - read;
                    return amount;
                }
                // Couldn't get all the characters, skip what we read
                read += (count - pos);
                pos = count;
            }
            return amount;
        }
    }

    private static Reader DUMMY_READER = new Reader() {
        @Override
        public void close() throws IOException {
        }

        @Override
        public int read(char[] cbuf, int off, int len) throws IOException {
            return 0;
        }
    };


}
