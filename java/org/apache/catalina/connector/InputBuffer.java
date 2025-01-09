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
import java.io.Reader;
import java.net.SocketTimeoutException;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.servlet.ReadListener;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.coyote.ActionCode;
import org.apache.coyote.BadRequestException;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.buf.B2CConverter;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.collections.SynchronizedStack;
import org.apache.tomcat.util.net.ApplicationBufferHandler;
import org.apache.tomcat.util.res.StringManager;

/**
 * The buffer used by Tomcat request. This is a derivative of the Tomcat 3.3 OutputBuffer, adapted to handle input
 * instead of output. This allows complete recycling of the facade objects (the ServletInputStream and the
 * BufferedReader).
 *
 * @author Remy Maucherat
 */
public class InputBuffer extends Reader implements ByteChunk.ByteInputChannel, ApplicationBufferHandler {

    /**
     * The string manager for this package.
     */
    protected static final StringManager sm = StringManager.getManager(InputBuffer.class);

    private static final Log log = LogFactory.getLog(InputBuffer.class);

    private static final ByteBuffer EMPTY_BUFFER = ByteBuffer.allocate(0);

    public static final int DEFAULT_BUFFER_SIZE = 8 * 1024;

    // The buffer can be used for byte[] and char[] reading
    // ( this is needed to support ServletInputStream and BufferedReader )
    public final int INITIAL_STATE = 0;
    public final int CHAR_STATE = 1;
    public final int BYTE_STATE = 2;


    /**
     * Encoder cache.
     */
    private static final Map<Charset,SynchronizedStack<B2CConverter>> encoders = new ConcurrentHashMap<>();

    // ----------------------------------------------------- Instance Variables

    /*
     * The byte buffer. Data is always injected into this class by calling {@link #setByteBuffer(ByteBuffer)} rather
     * than copying data into any existing buffer. It is initialised to an empty buffer as there are code paths that
     * access the buffer when it is expected to be empty and an empty buffer gives cleaner code than lots of null
     * checks.
     */
    private ByteBuffer bb;


    /**
     * The char buffer.
     */
    private CharBuffer cb;


    /**
     * State of the output buffer.
     */
    private int state = 0;


    /**
     * Flag which indicates if the input buffer is closed.
     */
    private boolean closed = false;


    /**
     * Current byte to char converter.
     */
    protected B2CConverter conv;


    /**
     * Associated Coyote request.
     */
    private final org.apache.coyote.Request coyoteRequest;


    /**
     * Buffer position.
     */
    private int markPos = -1;


    /**
     * Char buffer limit.
     */
    private int readLimit;


    /**
     * Buffer size.
     */
    private final int size;


    // ----------------------------------------------------------- Constructors


    /**
     * Default constructor. Allocate the buffer with the default buffer size.
     *
     * @param coyoteRequest The associated Coyote request
     */
    public InputBuffer(org.apache.coyote.Request coyoteRequest) {
        this(DEFAULT_BUFFER_SIZE, coyoteRequest);
    }


    /**
     * Alternate constructor which allows specifying the initial buffer size.
     *
     * @param size          Buffer size to use
     * @param coyoteRequest The associated Coyote request
     */
    public InputBuffer(int size, org.apache.coyote.Request coyoteRequest) {
        this.size = size;
        // Will be replaced when there is data to read so initialise to empty buffer.
        bb = EMPTY_BUFFER;
        cb = CharBuffer.allocate(size);
        clear(cb);
        readLimit = size;

        this.coyoteRequest = coyoteRequest;
    }


    // --------------------------------------------------------- Public Methods

    /**
     * Recycle the output buffer.
     */
    public void recycle() {

        state = INITIAL_STATE;

        // If usage of mark made the buffer too big, reallocate it
        if (cb.capacity() > size) {
            cb = CharBuffer.allocate(size);
            clear(cb);
        } else {
            clear(cb);
        }
        readLimit = size;
        markPos = -1;
        /*
         * This buffer will have been replaced if there was data to read so re-initialise to an empty buffer to clear
         * any reference to an injected buffer.
         */
        bb = EMPTY_BUFFER;
        closed = false;

        if (conv != null) {
            conv.recycle();
            encoders.get(conv.getCharset()).push(conv);
            conv = null;
        }
    }


    @Override
    public void close() throws IOException {
        closed = true;
    }


    public int available() {
        int available = availableInThisBuffer();
        if (available == 0) {
            coyoteRequest.action(ActionCode.AVAILABLE, Boolean.valueOf(coyoteRequest.getReadListener() != null));
            available = (coyoteRequest.getAvailable() > 0) ? 1 : 0;
        }
        return available;
    }


    private int availableInThisBuffer() {
        int available = 0;
        if (state == BYTE_STATE) {
            available = bb.remaining();
        } else if (state == CHAR_STATE) {
            available = cb.remaining();
        }
        return available;
    }


    public void setReadListener(ReadListener listener) {
        coyoteRequest.setReadListener(listener);
    }


    public boolean isFinished() {
        int available = 0;
        if (state == BYTE_STATE) {
            available = bb.remaining();
        } else if (state == CHAR_STATE) {
            available = cb.remaining();
        }
        if (available > 0) {
            return false;
        } else {
            return coyoteRequest.isFinished();
        }
    }


    public boolean isReady() {
        if (coyoteRequest.getReadListener() == null) {
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("inputBuffer.requiresNonBlocking"));
            }
            return true;
        }
        if (isFinished()) {
            // If this is a non-container thread, need to trigger a read
            // which will eventually lead to a call to onAllDataRead() via a
            // container thread.
            if (!coyoteRequest.isRequestThread()) {
                coyoteRequest.action(ActionCode.DISPATCH_READ, null);
                coyoteRequest.action(ActionCode.DISPATCH_EXECUTE, null);
            }
            return false;
        }
        // Checking for available data at the network level and registering for
        // read can be done sequentially for HTTP/1.x and AJP as there is only
        // ever a single thread processing the socket at any one time. However,
        // for HTTP/2 there is one thread processing the connection and separate
        // threads for each stream. For HTTP/2 the two operations have to be
        // performed atomically else it is possible for the connection thread to
        // read more data in to the buffer after the stream thread checks for
        // available network data but before it registers for read.
        if (availableInThisBuffer() > 0) {
            return true;
        }

        return coyoteRequest.isReady();
    }


    boolean isBlocking() {
        return coyoteRequest.getReadListener() == null;
    }


    // ------------------------------------------------- Bytes Handling Methods

    @Override
    public int realReadBytes() throws IOException {
        if (closed) {
            return -1;
        }

        if (state == INITIAL_STATE) {
            state = BYTE_STATE;
        }

        try {
            return coyoteRequest.doRead(this);
        } catch (BadRequestException bre) {
            // Make the exception visible to the application
            handleReadException(bre);
            throw bre;
        } catch (IOException ioe) {
            handleReadException(ioe);
            // Any other IOException on a read is almost always due to the remote client aborting the request.
            // Make the exception visible to the application
            throw new ClientAbortException(ioe);
        }
    }


    private void handleReadException(Exception e) throws IOException {
        // Set flag used by asynchronous processing to detect errors on non-container threads
        coyoteRequest.setErrorException(e);
        // In synchronous processing, this exception may be swallowed by the application so set error flags here.
        Request request = (Request) coyoteRequest.getNote(CoyoteAdapter.ADAPTER_NOTES);
        Response response = request.getResponse();
        request.setAttribute(RequestDispatcher.ERROR_EXCEPTION, e);
        if (e instanceof SocketTimeoutException) {
            try {
                response.sendError(HttpServletResponse.SC_REQUEST_TIMEOUT);
            } catch (IllegalStateException ex) {
                // Response already committed
                response.setStatus(HttpServletResponse.SC_REQUEST_TIMEOUT);
                response.setError();
            }
        } else {
            try {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            } catch (IllegalStateException ex) {
                // Response already committed
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                response.setError();
            }
        }
    }


    public int readByte() throws IOException {
        throwIfClosed();

        if (checkByteBufferEof()) {
            return -1;
        }
        return bb.get() & 0xFF;
    }


    public int read(byte[] b, int off, int len) throws IOException {
        throwIfClosed();

        if (checkByteBufferEof()) {
            return -1;
        }
        int n = Math.min(len, bb.remaining());
        bb.get(b, off, n);
        return n;
    }


    /**
     * Transfers bytes from the buffer to the specified ByteBuffer. After the operation the position of the ByteBuffer
     * will be returned to the one before the operation, the limit will be the position incremented by the number of the
     * transferred bytes.
     *
     * @param to the ByteBuffer into which bytes are to be written.
     *
     * @return an integer specifying the actual number of bytes read, or -1 if the end of the stream is reached
     *
     * @throws IOException if an input or output exception has occurred
     */
    public int read(ByteBuffer to) throws IOException {
        throwIfClosed();

        if (to.remaining() == 0) {
            return 0;
        }

        if (checkByteBufferEof()) {
            return -1;
        }
        int n = Math.min(to.remaining(), bb.remaining());
        int orgLimit = bb.limit();
        bb.limit(bb.position() + n);
        to.put(bb);
        bb.limit(orgLimit);
        to.limit(to.position()).position(to.position() - n);
        return n;
    }


    // ------------------------------------------------- Chars Handling Methods

    public int realReadChars() throws IOException {
        checkConverter();

        boolean eof = false;

        if (bb.remaining() <= 0) {
            int nRead = realReadBytes();
            if (nRead < 0) {
                eof = true;
            }
        }

        if (markPos == -1) {
            clear(cb);
        } else {
            // Make sure there's enough space in the worst case
            makeSpace(bb.remaining());
            if ((cb.capacity() - cb.limit()) == 0 && bb.remaining() != 0) {
                // We went over the limit
                clear(cb);
                markPos = -1;
            }
        }

        state = CHAR_STATE;
        conv.convert(bb, cb, this, eof);

        if (cb.remaining() == 0 && eof) {
            return -1;
        } else {
            return cb.remaining();
        }
    }


    @Override
    public int read() throws IOException {
        throwIfClosed();

        if (checkCharBufferEof()) {
            return -1;
        }
        return cb.get();
    }


    @Override
    public int read(char[] cbuf) throws IOException {
        throwIfClosed();
        return read(cbuf, 0, cbuf.length);
    }


    @Override
    public int read(char[] cbuf, int off, int len) throws IOException {
        throwIfClosed();

        if (checkCharBufferEof()) {
            return -1;
        }
        int n = Math.min(len, cb.remaining());
        cb.get(cbuf, off, n);
        return n;
    }


    @Override
    public long skip(long n) throws IOException {
        throwIfClosed();

        if (n < 0) {
            throw new IllegalArgumentException();
        }

        long nRead = 0;
        while (nRead < n) {
            if (cb.remaining() >= n) {
                cb.position(cb.position() + (int) n);
                nRead = n;
            } else {
                nRead += cb.remaining();
                cb.position(cb.limit());
                int nb = realReadChars();
                if (nb < 0) {
                    break;
                }
            }
        }
        return nRead;
    }


    @Override
    public boolean ready() throws IOException {
        throwIfClosed();
        if (state == INITIAL_STATE) {
            state = CHAR_STATE;
        }
        return (available() > 0);
    }


    @Override
    public boolean markSupported() {
        return true;
    }


    @Override
    public void mark(int readAheadLimit) throws IOException {

        throwIfClosed();

        if (cb.remaining() <= 0) {
            clear(cb);
        } else {
            if ((cb.capacity() > (2 * size)) && (cb.remaining()) < (cb.position())) {
                cb.compact();
                cb.flip();
            }
        }
        readLimit = cb.position() + readAheadLimit + size;
        markPos = cb.position();
    }


    @Override
    public void reset() throws IOException {

        throwIfClosed();

        if (state == CHAR_STATE) {
            if (markPos < 0) {
                clear(cb);
                markPos = -1;
                IOException ioe = new IOException();
                coyoteRequest.setErrorException(ioe);
                throw ioe;
            } else {
                cb.position(markPos);
            }
        } else {
            clear(bb);
        }
    }


    private void throwIfClosed() throws IOException {
        if (closed) {
            IOException ioe = new IOException(sm.getString("inputBuffer.streamClosed"));
            coyoteRequest.setErrorException(ioe);
            throw ioe;
        }
    }

    public void checkConverter() throws IOException {
        if (conv != null) {
            return;
        }

        Charset charset = coyoteRequest.getCharsetHolder().getValidatedCharset();

        if (charset == null) {
            charset = org.apache.coyote.Constants.DEFAULT_BODY_CHARSET;
        }

        SynchronizedStack<B2CConverter> stack = encoders.get(charset);
        if (stack == null) {
            stack = new SynchronizedStack<>();
            encoders.putIfAbsent(charset, stack);
            stack = encoders.get(charset);
        }
        conv = stack.pop();

        if (conv == null) {
            conv = new B2CConverter(charset);
        }
    }


    @Override
    public void setByteBuffer(ByteBuffer buffer) {
        bb = buffer;
    }


    @Override
    public ByteBuffer getByteBuffer() {
        return bb;
    }


    @Override
    public void expand(int size) {
        // no-op
    }


    private boolean checkByteBufferEof() throws IOException {
        if (bb.remaining() == 0) {
            int n = realReadBytes();
            if (n < 0) {
                return true;
            }
        }
        return false;
    }

    private boolean checkCharBufferEof() throws IOException {
        if (cb.remaining() == 0) {
            int n = realReadChars();
            if (n < 0) {
                return true;
            }
        }
        return false;
    }

    private void clear(Buffer buffer) {
        buffer.rewind().limit(0);
    }

    private void makeSpace(int count) {
        int desiredSize = cb.limit() + count;
        if (desiredSize > readLimit) {
            desiredSize = readLimit;
        }

        if (desiredSize <= cb.capacity()) {
            return;
        }

        int newSize = 2 * cb.capacity();
        if (desiredSize >= newSize) {
            newSize = 2 * cb.capacity() + count;
        }

        if (newSize > readLimit) {
            newSize = readLimit;
        }

        CharBuffer tmp = CharBuffer.allocate(newSize);
        int oldPosition = cb.position();
        cb.position(0);
        tmp.put(cb);
        tmp.flip();
        tmp.position(oldPosition);
        cb = tmp;
        tmp = null;
    }
}
