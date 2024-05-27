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
package org.apache.jasper.runtime;

import java.io.CharArrayReader;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;

import jakarta.servlet.jsp.JspWriter;
import jakarta.servlet.jsp.tagext.BodyContent;

import org.apache.jasper.compiler.Localizer;

/**
 * Write text to a character-output stream, buffering characters so as
 * to provide for the efficient writing of single characters, arrays,
 * and strings.
 *
 * Provide support for discarding for the output that has been buffered.
 *
 * @author Rajiv Mordani
 * @author Jan Luehe
 */
public class BodyContentImpl extends BodyContent {

    private final boolean limitBuffer;
    private final int tagBufferSize;

    private char[] cb;
    private int nextChar;
    private boolean closed;

    /**
     * Enclosed writer to which any output is written
     */
    private Writer writer;

    /**
     * Constructor.
     * @param enclosingWriter The wrapped writer
     * @param limitBuffer <code>true</code> to discard large buffers
     * @param tagBufferSize the buffer size
     */
    public BodyContentImpl(JspWriter enclosingWriter, boolean limitBuffer, int tagBufferSize) {
        super(enclosingWriter);
        this.limitBuffer = limitBuffer;
        this.tagBufferSize = tagBufferSize;
        cb = new char[tagBufferSize];
        bufferSize = cb.length;
        nextChar = 0;
        closed = false;
    }

    @Override
    public void write(int c) throws IOException {
        if (writer != null) {
            writer.write(c);
        } else {
            ensureOpen();
            if (nextChar >= bufferSize) {
                reAllocBuff (1);
            }
            cb[nextChar++] = (char) c;
        }
    }

    @Override
    public void write(char[] cbuf, int off, int len) throws IOException {
        if (writer != null) {
            writer.write(cbuf, off, len);
        } else {
            ensureOpen();

            if ((off < 0) || (off > cbuf.length) || (len < 0) ||
                    ((off + len) > cbuf.length) || ((off + len) < 0)) {
                throw new IndexOutOfBoundsException();
            } else if (len == 0) {
                return;
            }

            if (len >= bufferSize - nextChar) {
                reAllocBuff (len);
            }

            System.arraycopy(cbuf, off, cb, nextChar, len);
            nextChar+=len;
        }
    }

    @Override
    public void write(char[] buf) throws IOException {
        if (writer != null) {
            writer.write(buf);
        } else {
            write(buf, 0, buf.length);
        }
    }

    @Override
    public void write(String s, int off, int len) throws IOException {
        if (writer != null) {
            writer.write(s, off, len);
        } else {
            ensureOpen();
            if (len >= bufferSize - nextChar) {
                reAllocBuff(len);
            }

            s.getChars(off, off + len, cb, nextChar);
            nextChar += len;
        }
    }

    @Override
    public void write(String s) throws IOException {
        if (writer != null) {
            writer.write(s);
        } else {
            write(s, 0, s.length());
        }
    }

    @Override
    public void newLine() throws IOException {
        if (writer != null) {
            writer.write(System.lineSeparator());
        } else {
            write(System.lineSeparator());
        }
    }

    @Override
    public void print(boolean b) throws IOException {
        if (writer != null) {
            writer.write(b ? "true" : "false");
        } else {
            write(b ? "true" : "false");
        }
    }

    @Override
    public void print(char c) throws IOException {
        if (writer != null) {
            writer.write(String.valueOf(c));
        } else {
            write(String.valueOf(c));
        }
    }

    @Override
    public void print(int i) throws IOException {
        if (writer != null) {
            writer.write(String.valueOf(i));
        } else {
            write(String.valueOf(i));
        }
    }

    @Override
    public void print(long l) throws IOException {
        if (writer != null) {
            writer.write(String.valueOf(l));
        } else {
            write(String.valueOf(l));
        }
    }

    @Override
    public void print(float f) throws IOException {
        if (writer != null) {
            writer.write(String.valueOf(f));
        } else {
            write(String.valueOf(f));
        }
    }

    @Override
    public void print(double d) throws IOException {
        if (writer != null) {
            writer.write(String.valueOf(d));
        } else {
            write(String.valueOf(d));
        }
    }

    @Override
    public void print(char[] s) throws IOException {
        if (writer != null) {
            writer.write(s);
        } else {
            write(s);
        }
    }

    @Override
    public void print(String s) throws IOException {
        if (s == null) {
            s = "null";
        }
        if (writer != null) {
            writer.write(s);
        } else {
            write(s);
        }
    }

    @Override
    public void print(Object obj) throws IOException {
        if (writer != null) {
            writer.write(String.valueOf(obj));
        } else {
            write(String.valueOf(obj));
        }
    }

    @Override
    public void println() throws IOException {
        newLine();
    }

    @Override
    public void println(boolean x) throws IOException {
        print(x);
        println();
    }

    @Override
    public void println(char x) throws IOException {
        print(x);
        println();
    }

    @Override
    public void println(int x) throws IOException {
        print(x);
        println();
    }

    @Override
    public void println(long x) throws IOException {
        print(x);
        println();
    }

    @Override
    public void println(float x) throws IOException {
        print(x);
        println();
    }

    @Override
    public void println(double x) throws IOException{
        print(x);
        println();
    }

    @Override
    public void println(char x[]) throws IOException {
        print(x);
        println();
    }

    @Override
    public void println(String x) throws IOException {
        print(x);
        println();
    }

    @Override
    public void println(Object x) throws IOException {
        print(x);
        println();
    }

    @Override
    public void clear() throws IOException {
        if (writer != null) {
            throw new IOException();
        } else {
            nextChar = 0;
            if (limitBuffer && (cb.length > tagBufferSize)) {
                cb = new char[tagBufferSize];
                bufferSize = cb.length;
            }
        }
    }

    @Override
    public void clearBuffer() throws IOException {
        if (writer == null) {
            this.clear();
        }
    }

    @Override
    public void close() throws IOException {
        if (writer != null) {
            writer.close();
        } else {
            closed = true;
        }
    }

    @Override
    public int getBufferSize() {
        // According to the spec, the JspWriter returned by
        // JspContext.pushBody(java.io.Writer writer) must behave as
        // though it were unbuffered. This means that its getBufferSize()
        // must always return 0.
        return (writer == null) ? bufferSize : 0;
    }

    @Override
    public int getRemaining() {
        return (writer == null) ? bufferSize-nextChar : 0;
    }

    @Override
    public Reader getReader() {
        return (writer == null) ? new CharArrayReader (cb, 0, nextChar) : null;
    }

    @Override
    public String getString() {
        return (writer == null) ? new String(cb, 0, nextChar) : null;
    }

    @Override
    public void writeOut(Writer out) throws IOException {
        if (writer == null) {
            out.write(cb, 0, nextChar);
            // Flush not called as the writer passed could be a BodyContent and
            // it doesn't allow to flush.
        }
    }

    /**
     * Sets the writer to which all output is written.
     */
    void setWriter(Writer writer) {
        this.writer = writer;
        closed = false;
        if (writer == null) {
            clearBody();
        }
    }

    /**
     * This method shall "reset" the internal state of a BodyContentImpl,
     * releasing all internal references, and preparing it for potential
     * reuse by a later invocation of {@link PageContextImpl#pushBody(Writer)}.
     *
     * <p>Note, that BodyContentImpl instances are usually owned by a
     * PageContextImpl instance, and PageContextImpl instances are recycled
     * and reused.
     *
     * @see PageContextImpl#release()
     */
    protected void recycle() {
        this.writer = null;
        try {
            this.clear();
        } catch (IOException ex) {
            // ignore
        }
    }

    private void ensureOpen() throws IOException {
        if (closed) {
            throw new IOException(Localizer.getMessage("jsp.error.stream.closed"));
        }
    }

    /**
     * Reallocates buffer since the spec requires it to be unbounded.
     */
    private void reAllocBuff(int len) {

        if (bufferSize + len <= cb.length) {
            bufferSize = cb.length;
            return;
        }

        if (len < cb.length) {
            len = cb.length;
        }

        char[] tmp = new char[cb.length + len];
        System.arraycopy(cb, 0, tmp, 0, cb.length);
        cb = tmp;
        bufferSize = cb.length;
    }


}
