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
package org.apache.catalina.connector;

import java.io.IOException;
import java.io.Writer;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.coyote.ActionCode;
import org.apache.coyote.CloseNowException;
import org.apache.coyote.Response;
import org.apache.tomcat.util.buf.C2BConverter;
import org.apache.tomcat.util.buf.CharsetHolder;
import org.apache.tomcat.util.res.StringManager;

/**
 * The buffer used by Tomcat response. This is a derivative of the Tomcat 3.3 OutputBuffer, with the removal of some of
 * the state handling (which in Coyote is mostly the Processor's responsibility).
 *
 * @author Costin Manolache
 * @author Remy Maucherat
 */
public class OutputBuffer extends Writer {

    private static final StringManager sm = StringManager.getManager(OutputBuffer.class);

    public static final int DEFAULT_BUFFER_SIZE = 8 * 1024;

    /**
     * Encoder cache.
     */
    private final Map<Charset,C2BConverter> encoders = new HashMap<>();


    /**
     * Default buffer size.
     */
    private final int defaultBufferSize;

    // ----------------------------------------------------- Instance Variables

    /**
     * The byte buffer.
     */
    private ByteBuffer bb;


    /**
     * The char buffer.
     */
    private final CharBuffer cb;


    /**
     * State of the output buffer.
     */
    private boolean initial = true;


    /**
     * Number of bytes written.
     */
    private long bytesWritten = 0;


    /**
     * Number of chars written.
     */
    private long charsWritten = 0;


    /**
     * Flag which indicates if the output buffer is closed.
     */
    private volatile boolean closed = false;


    /**
     * Do a flush on the next operation.
     */
    private boolean doFlush = false;


    /**
     * Current char to byte converter.
     */
    protected C2BConverter conv;


    /**
     * Associated Coyote response.
     */
    private final Response coyoteResponse;


    /**
     * Suspended flag. All output bytes will be swallowed if this is true.
     */
    private volatile boolean suspended = false;


    // ----------------------------------------------------------- Constructors

    /**
     * Create the buffer with the specified initial size.
     *
     * @param size           Buffer size to use
     * @param coyoteResponse The associated Coyote response
     */
    public OutputBuffer(int size, Response coyoteResponse) {
        defaultBufferSize = size;
        bb = ByteBuffer.allocate(size);
        clear(bb);
        cb = CharBuffer.allocate(size);
        clear(cb);
        this.coyoteResponse = coyoteResponse;
    }


    // ------------------------------------------------------------- Properties

    /**
     * Is the response output suspended ?
     *
     * @return suspended flag value
     */
    public boolean isSuspended() {
        return this.suspended;
    }


    /**
     * Set the suspended flag.
     *
     * @param suspended New suspended flag value
     */
    public void setSuspended(boolean suspended) {
        this.suspended = suspended;
    }


    /**
     * Is the response output closed ?
     *
     * @return closed flag value
     */
    public boolean isClosed() {
        return this.closed;
    }


    // --------------------------------------------------------- Public Methods

    /**
     * Recycle the output buffer.
     */
    public void recycle() {

        initial = true;
        bytesWritten = 0;
        charsWritten = 0;

        if (bb.capacity() > 16 * defaultBufferSize) {
            // Discard buffers which are too large
            bb = ByteBuffer.allocate(defaultBufferSize);
        }
        clear(bb);
        clear(cb);
        closed = false;
        suspended = false;
        doFlush = false;

        if (conv != null) {
            conv.recycle();
            conv = null;
        }
    }


    /**
     * Close the output buffer. This tries to calculate the response size if the response has not been committed yet.
     *
     * @throws IOException An underlying IOException occurred
     */
    @Override
    public void close() throws IOException {

        if (closed) {
            return;
        }
        if (suspended) {
            return;
        }

        // If there are chars, flush all of them to the byte buffer now as bytes are used to
        // calculate the content-length (if everything fits into the byte buffer, of course).
        if (cb.remaining() > 0) {
            flushCharBuffer();
        }

        // Content length can be calculated if:
        // - the response has not been committed
        // AND
        // - the content length has not been explicitly set
        // AND
        // - some content has been written OR this is NOT a HEAD request
        if ((!coyoteResponse.isCommitted()) && (coyoteResponse.getContentLengthLong() == -1) &&
                ((bb.remaining() > 0 || !coyoteResponse.getRequest().method().equals("HEAD")))) {
            coyoteResponse.setContentLength(bb.remaining());
        }

        if (coyoteResponse.getStatus() == HttpServletResponse.SC_SWITCHING_PROTOCOLS) {
            doFlush(true);
        } else {
            doFlush(false);
        }
        closed = true;

        // The request should have been completely read by the time the response
        // is closed. Further reads of the input a) are pointless and b) really
        // confuse AJP (bug 50189) so close the input buffer to prevent them.
        Request req = (Request) coyoteResponse.getRequest().getNote(CoyoteAdapter.ADAPTER_NOTES);
        req.inputBuffer.close();

        coyoteResponse.action(ActionCode.CLOSE, null);
    }


    /**
     * Flush bytes or chars contained in the buffer.
     *
     * @throws IOException An underlying IOException occurred
     */
    @Override
    public void flush() throws IOException {
        doFlush(true);
    }


    /**
     * Flush bytes or chars contained in the buffer.
     *
     * @param realFlush <code>true</code> if this should also cause a real network flush
     *
     * @throws IOException An underlying IOException occurred
     */
    protected void doFlush(boolean realFlush) throws IOException {

        if (suspended) {
            return;
        }

        try {
            doFlush = true;
            if (initial) {
                coyoteResponse.commit();
                initial = false;
            }
            if (cb.remaining() > 0) {
                flushCharBuffer();
            }
            if (bb.remaining() > 0) {
                flushByteBuffer();
            }
        } finally {
            doFlush = false;
        }

        if (realFlush) {
            coyoteResponse.action(ActionCode.CLIENT_FLUSH, null);
            // If some exception occurred earlier, or if some IOE occurred
            // here, notify the servlet with an IOE
            if (coyoteResponse.isExceptionPresent()) {
                throw new ClientAbortException(coyoteResponse.getErrorException());
            }
        }

    }


    // ------------------------------------------------- Bytes Handling Methods

    /**
     * Sends the buffer data to the client output, checking the state of Response and calling the right interceptors.
     *
     * @param buf the ByteBuffer to be written to the response
     *
     * @throws IOException An underlying IOException occurred
     */
    public void realWriteBytes(ByteBuffer buf) throws IOException {

        if (closed) {
            return;
        }

        // If we really have something to write
        if (buf.remaining() > 0) {
            // real write to the adapter
            try {
                coyoteResponse.doWrite(buf);
            } catch (CloseNowException e) {
                // Catch this sub-class as it requires specific handling.
                // Examples where this exception is thrown:
                // - HTTP/2 stream timeout
                // Prevent further output for this response
                closed = true;
                throw e;
            } catch (IOException e) {
                // An IOException on a write is almost always due to
                // the remote client aborting the request. Wrap this
                // so that it can be handled better by the error dispatcher.
                throw new ClientAbortException(e);
            }
        }

    }


    public void write(byte b[], int off, int len) throws IOException {

        if (suspended) {
            return;
        }

        writeBytes(b, off, len);

    }


    public void write(ByteBuffer from) throws IOException {

        if (suspended) {
            return;
        }

        writeBytes(from);

    }


    private void writeBytes(byte b[], int off, int len) throws IOException {

        if (closed) {
            throw new IOException(sm.getString("outputBuffer.closed"));
        }

        append(b, off, len);
        updateBytesWritten(len);

        // if called from within flush(), then immediately flush
        // remaining bytes
        if (doFlush) {
            flushByteBuffer();
        }

    }


    private void writeBytes(ByteBuffer from) throws IOException {

        if (closed) {
            throw new IOException(sm.getString("outputBuffer.closed"));
        }

        int remaining = from.remaining();
        append(from);
        updateBytesWritten(remaining);

        // if called from within flush(), then immediately flush
        // remaining bytes
        if (doFlush) {
            flushByteBuffer();
        }

    }


    public void writeByte(int b) throws IOException {

        if (closed) {
            throw new IOException(sm.getString("outputBuffer.closed"));
        }

        if (suspended) {
            return;
        }

        if (isFull(bb)) {
            flushByteBuffer();
        }

        transfer((byte) b, bb);
        updateBytesWritten(1);
    }


    private void updateBytesWritten(int increment) throws IOException {
        bytesWritten += increment;
        int contentLength = coyoteResponse.getContentLength();

        /*
         * Handle the requirements of section 5.7 of the Servlet specification - Closure of the Response Object.
         *
         * Currently this just handles the simple case. There is work in progress to better define what should happen if
         * an attempt is made to write > content-length bytes. When that work is complete, this is likely where the
         * implementation will end up.
         */
        if (contentLength != -1 && bytesWritten >= contentLength) {
            close();
        }
    }


    // ------------------------------------------------- Chars Handling Methods


    /**
     * Convert the chars to bytes, then send the data to the client.
     *
     * @param from Char buffer to be written to the response
     *
     * @throws IOException An underlying IOException occurred
     */
    public void realWriteChars(CharBuffer from) throws IOException {

        while (from.remaining() > 0) {
            conv.convert(from, bb);
            if (bb.remaining() == 0) {
                // Break out of the loop if more chars are needed to produce any output
                break;
            }
            if (from.remaining() > 0) {
                flushByteBuffer();
            } else if (conv.isUndeflow() && bb.limit() > bb.capacity() - 4) {
                // Handle an edge case. There are no more chars to write at the
                // moment but there is a leftover character in the converter
                // which must be part of a surrogate pair. The byte buffer does
                // not have enough space left to output the bytes for this pair
                // once it is complete )it will require 4 bytes) so flush now to
                // prevent the bytes for the leftover char and the rest of the
                // surrogate pair yet to be written from being lost.
                // See TestOutputBuffer#testUtf8SurrogateBody()
                flushByteBuffer();
            }
        }

    }

    @Override
    public void write(int c) throws IOException {

        if (suspended) {
            return;
        }

        if (isFull(cb)) {
            flushCharBuffer();
        }

        transfer((char) c, cb);
        charsWritten++;

    }


    @Override
    public void write(char c[]) throws IOException {

        if (suspended) {
            return;
        }

        write(c, 0, c.length);

    }


    @Override
    public void write(char c[], int off, int len) throws IOException {

        if (suspended) {
            return;
        }

        append(c, off, len);
        charsWritten += len;

    }


    /**
     * Append a string to the buffer
     */
    @Override
    public void write(String s, int off, int len) throws IOException {

        if (suspended) {
            return;
        }

        if (s == null) {
            throw new NullPointerException(sm.getString("outputBuffer.writeNull"));
        }

        int sOff = off;
        int sEnd = off + len;
        while (sOff < sEnd) {
            int n = transfer(s, sOff, sEnd - sOff, cb);
            sOff += n;
            if (sOff < sEnd && isFull(cb)) {
                flushCharBuffer();
            }
        }

        charsWritten += len;
    }


    @Override
    public void write(String s) throws IOException {

        if (suspended) {
            return;
        }

        if (s == null) {
            s = "null";
        }
        write(s, 0, s.length());
    }


    public void checkConverter() throws IOException {
        if (conv != null) {
            return;
        }

        CharsetHolder charsetHolder = coyoteResponse.getCharsetHolder();
        // setCharacterEncoding() was called with an invalid character set
        // Trigger an UnsupportedEncodingException
        charsetHolder.validate();
        Charset charset = charsetHolder.getCharset();

        if (charset == null) {
            charset = org.apache.coyote.Constants.DEFAULT_BODY_CHARSET;
        }

        conv = encoders.get(charset);

        if (conv == null) {
            conv = new C2BConverter(charset);
            encoders.put(charset, conv);
        }
    }


    // -------------------- BufferedOutputStream compatibility

    public long getContentWritten() {
        return bytesWritten + charsWritten;
    }

    /**
     * Has this buffer been used at all?
     *
     * @return true if no chars or bytes have been added to the buffer since the last call to {@link #recycle()}
     */
    public boolean isNew() {
        return (bytesWritten == 0) && (charsWritten == 0);
    }


    public void setBufferSize(int size) {
        if (size > bb.capacity()) {
            bb = ByteBuffer.allocate(size);
            clear(bb);
        }
    }


    public void reset() {
        reset(false);
    }

    public void reset(boolean resetWriterStreamFlags) {
        clear(bb);
        clear(cb);
        bytesWritten = 0;
        charsWritten = 0;
        if (resetWriterStreamFlags) {
            if (conv != null) {
                conv.recycle();
            }
            conv = null;
        }
        initial = true;
    }


    public int getBufferSize() {
        return bb.capacity();
    }


    /*
     * All the non-blocking write state information is held in the Response so it is visible / accessible to all the
     * code that needs it.
     */

    public boolean isReady() {
        return coyoteResponse.isReady();
    }


    public void setWriteListener(WriteListener listener) {
        coyoteResponse.setWriteListener(listener);
    }


    public boolean isBlocking() {
        return coyoteResponse.getWriteListener() == null;
    }

    public void checkRegisterForWrite() {
        coyoteResponse.checkRegisterForWrite();
    }

    /**
     * Add data to the buffer.
     *
     * @param src Bytes array
     * @param off Offset
     * @param len Length
     *
     * @throws IOException Writing overflow data to the output channel failed
     */
    public void append(byte src[], int off, int len) throws IOException {
        if (bb.remaining() == 0) {
            appendByteArray(src, off, len);
        } else {
            int n = transfer(src, off, len, bb);
            len = len - n;
            off = off + n;
            if (len > 0 && isFull(bb)) {
                flushByteBuffer();
                appendByteArray(src, off, len);
            }
        }
    }

    /**
     * Add data to the buffer.
     *
     * @param src Char array
     * @param off Offset
     * @param len Length
     *
     * @throws IOException Writing overflow data to the output channel failed
     */
    public void append(char src[], int off, int len) throws IOException {
        // if we have limit and we're below
        if (len <= cb.capacity() - cb.limit()) {
            transfer(src, off, len, cb);
            return;
        }

        // Optimization:
        // If len-avail < length ( i.e. after we fill the buffer with
        // what we can, the remaining will fit in the buffer ) we'll just
        // copy the first part, flush, then copy the second part - 1 write
        // and still have some space for more. We'll still have 2 writes, but
        // we write more on the first.
        if (len + cb.limit() < 2 * cb.capacity()) {
            /*
             * If the request length exceeds the size of the output buffer, flush the output buffer and then write the
             * data directly. We can't avoid 2 writes, but we can write more on the second
             */
            int n = transfer(src, off, len, cb);

            flushCharBuffer();

            transfer(src, off + n, len - n, cb);
        } else {
            // long write - flush the buffer and write the rest
            // directly from source
            flushCharBuffer();

            realWriteChars(CharBuffer.wrap(src, off, len));
        }
    }


    public void append(ByteBuffer from) throws IOException {
        if (bb.remaining() == 0) {
            appendByteBuffer(from);
        } else {
            transfer(from, bb);
            if (from.hasRemaining() && isFull(bb)) {
                flushByteBuffer();
                appendByteBuffer(from);
            }
        }
    }


    public void setErrorException(Exception e) {
        coyoteResponse.setErrorException(e);
    }


    private void appendByteArray(byte src[], int off, int len) throws IOException {
        if (len == 0) {
            return;
        }

        int limit = bb.capacity();
        while (len > limit) {
            realWriteBytes(ByteBuffer.wrap(src, off, limit));
            len = len - limit;
            off = off + limit;
        }

        if (len > 0) {
            transfer(src, off, len, bb);
        }
    }

    private void appendByteBuffer(ByteBuffer from) throws IOException {
        if (from.remaining() == 0) {
            return;
        }

        int limit = bb.capacity();
        int fromLimit = from.limit();
        while (from.remaining() > limit) {
            from.limit(from.position() + limit);
            realWriteBytes(from.slice());
            from.position(from.limit());
            from.limit(fromLimit);
        }

        if (from.remaining() > 0) {
            transfer(from, bb);
        }
    }

    private void flushByteBuffer() throws IOException {
        realWriteBytes(bb.slice());
        clear(bb);
    }

    private void flushCharBuffer() throws IOException {
        realWriteChars(cb.slice());
        clear(cb);
    }

    private void transfer(byte b, ByteBuffer to) {
        toWriteMode(to);
        to.put(b);
        toReadMode(to);
    }

    private void transfer(char b, CharBuffer to) {
        toWriteMode(to);
        to.put(b);
        toReadMode(to);
    }

    private int transfer(byte[] buf, int off, int len, ByteBuffer to) {
        toWriteMode(to);
        int max = Math.min(len, to.remaining());
        if (max > 0) {
            to.put(buf, off, max);
        }
        toReadMode(to);
        return max;
    }

    private int transfer(char[] buf, int off, int len, CharBuffer to) {
        toWriteMode(to);
        int max = Math.min(len, to.remaining());
        if (max > 0) {
            to.put(buf, off, max);
        }
        toReadMode(to);
        return max;
    }

    private int transfer(String s, int off, int len, CharBuffer to) {
        toWriteMode(to);
        int max = Math.min(len, to.remaining());
        if (max > 0) {
            to.put(s, off, off + max);
        }
        toReadMode(to);
        return max;
    }

    private void transfer(ByteBuffer from, ByteBuffer to) {
        toWriteMode(to);
        int max = Math.min(from.remaining(), to.remaining());
        if (max > 0) {
            int fromLimit = from.limit();
            from.limit(from.position() + max);
            to.put(from);
            from.limit(fromLimit);
        }
        toReadMode(to);
    }

    private void clear(Buffer buffer) {
        buffer.rewind().limit(0);
    }

    private boolean isFull(Buffer buffer) {
        return buffer.limit() == buffer.capacity();
    }

    private void toReadMode(Buffer buffer) {
        buffer.limit(buffer.position()).reset();
    }

    private void toWriteMode(Buffer buffer) {
        buffer.mark().position(buffer.limit()).limit(buffer.capacity());
    }
}
