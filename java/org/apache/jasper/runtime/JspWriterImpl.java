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

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;

import jakarta.servlet.ServletResponse;
import jakarta.servlet.jsp.JspWriter;

import org.apache.jasper.Constants;
import org.apache.jasper.compiler.Localizer;

/**
 * Write text to a character-output stream, buffering characters so as
 * to provide for the efficient writing of single characters, arrays,
 * and strings.
 *
 * Provide support for discarding for the output that has been
 * buffered.
 *
 * This needs revisiting when the buffering problems in the JSP spec
 * are fixed -akv
 *
 * @author Anil K. Vijendran
 */
public class JspWriterImpl extends JspWriter {

    private Writer out;
    private ServletResponse response;
    private char cb[];
    private int nextChar;
    private boolean flushed = false;
    private boolean closed = false;

    public JspWriterImpl() {
        super( Constants.DEFAULT_BUFFER_SIZE, true );
    }

    /**
     * Create a new buffered character-output stream that uses an output
     * buffer of the given size.
     *
     * @param  response A Servlet Response
     * @param  sz       Output-buffer size, a positive integer
     * @param autoFlush <code>true</code> to automatically flush on buffer
     *  full, <code>false</code> to throw an overflow exception in that case
     * @exception  IllegalArgumentException  If sz is &lt;= 0
     */
    public JspWriterImpl(ServletResponse response, int sz,
            boolean autoFlush) {
        super(sz, autoFlush);
        if (sz < 0) {
            throw new IllegalArgumentException(Localizer.getMessage("jsp.error.negativeBufferSize"));
        }
        this.response = response;
        cb = sz == 0 ? null : new char[sz];
        nextChar = 0;
    }

    void init( ServletResponse response, int sz, boolean autoFlush ) {
        this.response= response;
        if( sz > 0 && ( cb == null || sz > cb.length ) ) {
            cb=new char[sz];
        }
        nextChar = 0;
        this.autoFlush=autoFlush;
        this.bufferSize=sz;
    }

    /**
     * Package-level access
     */
    void recycle() {
        flushed = false;
        closed = false;
        out = null;
        nextChar = 0;
        response = null;
    }

    /**
     * Flush the output buffer to the underlying character stream, without
     * flushing the stream itself.  This method is non-private only so that it
     * may be invoked by PrintStream.
     * @throws IOException Error writing buffered data
     */
    protected final void flushBuffer() throws IOException {
        if (bufferSize == 0) {
            return;
        }
        flushed = true;
        ensureOpen();
        if (nextChar == 0) {
            return;
        }
        initOut();
        out.write(cb, 0, nextChar);
        nextChar = 0;
    }

    private void initOut() throws IOException {
        if (out == null) {
            try {
                out = response.getWriter();
            } catch (IllegalStateException e) {
                /*
                 * At some point in the processing something (most likely the default servlet as the target of a
                 * <jsp:forward ... /> action) wrote directly to the OutputStream rather than the Writer. Wrap the
                 * OutputStream in a Writer so the JSP engine can use the Writer it is expecting to use.
                 */
                out = new PrintWriter(response.getOutputStream());
            }
        }
    }

    @Override
    public final void clear() throws IOException {
        if ((bufferSize == 0) && (out != null)) {
            // clear() is illegal after any unbuffered output (JSP.5.5)
            throw new IllegalStateException(
                    Localizer.getMessage("jsp.error.ise_on_clear"));
        }
        if (flushed) {
            throw new IOException(
                    Localizer.getMessage("jsp.error.attempt_to_clear_flushed_buffer"));
        }
        ensureOpen();
        nextChar = 0;
    }

    @Override
    public void clearBuffer() throws IOException {
        if (bufferSize == 0) {
            throw new IllegalStateException(
                    Localizer.getMessage("jsp.error.ise_on_clear"));
        }
        ensureOpen();
        nextChar = 0;
    }

    private void bufferOverflow() throws IOException {
        throw new IOException(Localizer.getMessage("jsp.error.overflow"));
    }

    @Override
    public void flush()  throws IOException {
        flushBuffer();
        if (out != null) {
            out.flush();
        }
    }

    @Override
    public void close() throws IOException {
        if (response == null || closed) {
            // multiple calls to close is OK
            return;
        }
        flush();
        if (out != null) {
            out.close();
        }
        out = null;
        closed = true;
    }

    @Override
    public int getRemaining() {
        return bufferSize - nextChar;
    }

    /** check to make sure that the stream has not been closed */
    private void ensureOpen() throws IOException {
        if (response == null || closed) {
            throw new IOException(Localizer.getMessage("jsp.error.stream.closed"));
        }
    }


    @Override
    public void write(int c) throws IOException {
        ensureOpen();
        if (bufferSize == 0) {
            initOut();
            out.write(c);
        } else {
            if (nextChar >= bufferSize) {
                if (autoFlush) {
                    flushBuffer();
                } else {
                    bufferOverflow();
                }
            }
            cb[nextChar++] = (char) c;
        }
    }

    /**
     * Our own little min method, to avoid loading java.lang.Math if we've run
     * out of file descriptors and we're trying to print a stack trace.
     */
    private static int min(int a, int b) {
        if (a < b) {
            return a;
        }
        return b;
    }

    @Override
    public void write(char cbuf[], int off, int len) throws IOException {
        ensureOpen();

        if (bufferSize == 0) {
            initOut();
            out.write(cbuf, off, len);
            return;
        }

        if ((off < 0) || (off > cbuf.length) || (len < 0) ||
                ((off + len) > cbuf.length) || ((off + len) < 0)) {
            throw new IndexOutOfBoundsException();
        } else if (len == 0) {
            return;
        }

        if (len >= bufferSize) {
            /* If the request length exceeds the size of the output buffer,
             flush the buffer and then write the data directly.  In this
             way buffered streams will cascade harmlessly. */
            if (autoFlush) {
                flushBuffer();
            } else {
                bufferOverflow();
            }
            initOut();
            out.write(cbuf, off, len);
            return;
        }

        int b = off, t = off + len;
        while (b < t) {
            int d = min(bufferSize - nextChar, t - b);
            System.arraycopy(cbuf, b, cb, nextChar, d);
            b += d;
            nextChar += d;
            if (nextChar >= bufferSize) {
                if (autoFlush) {
                    flushBuffer();
                } else {
                    bufferOverflow();
                }
            }
        }

    }

    @Override
    public void write(char buf[]) throws IOException {
        write(buf, 0, buf.length);
    }

    @Override
    public void write(String s, int off, int len) throws IOException {
        ensureOpen();
        if (bufferSize == 0) {
            initOut();
            out.write(s, off, len);
            return;
        }
        int b = off, t = off + len;
        while (b < t) {
            int d = min(bufferSize - nextChar, t - b);
            s.getChars(b, b + d, cb, nextChar);
            b += d;
            nextChar += d;
            if (nextChar >= bufferSize) {
                if (autoFlush) {
                    flushBuffer();
                } else {
                    bufferOverflow();
                }
            }
        }
    }


    @Override
    public void newLine() throws IOException {
        write(System.lineSeparator());
    }


    /* Methods that do not terminate lines */

    @Override
    public void print(boolean b) throws IOException {
        write(b ? "true" : "false");
    }

    @Override
    public void print(char c) throws IOException {
        write(String.valueOf(c));
    }

    @Override
    public void print(int i) throws IOException {
        write(String.valueOf(i));
    }

    @Override
    public void print(long l) throws IOException {
        write(String.valueOf(l));
    }

    @Override
    public void print(float f) throws IOException {
        write(String.valueOf(f));
    }

    @Override
    public void print(double d) throws IOException {
        write(String.valueOf(d));
    }

    @Override
    public void print(char s[]) throws IOException {
        write(s);
    }

    @Override
    public void print(String s) throws IOException {
        if (s == null) {
            s = "null";
        }
        write(s);
    }

    @Override
    public void print(Object obj) throws IOException {
        write(String.valueOf(obj));
    }

    /* Methods that do terminate lines */

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
    public void println(double x) throws IOException {
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

}
