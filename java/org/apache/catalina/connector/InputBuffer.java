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
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.servlet.ReadListener;

import org.apache.catalina.security.SecurityUtil;
import org.apache.coyote.ActionCode;
import org.apache.coyote.ContainerThreadMarker;
import org.apache.coyote.Request;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.buf.B2CConverter;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.collections.SynchronizedStack;
import org.apache.tomcat.util.net.ApplicationBufferHandler;
import org.apache.tomcat.util.res.StringManager;

/**
 * The buffer used by Tomcat request. This is a derivative of the Tomcat 3.3
 * OutputBuffer, adapted to handle input instead of output. This allows
 * complete recycling of the facade objects (the ServletInputStream and the
 * BufferedReader).
 *
 * @author Remy Maucherat
 */
public class InputBuffer extends Reader
    implements ByteChunk.ByteInputChannel, ApplicationBufferHandler {

    /**
     * The string manager for this package.
     */
    protected static final StringManager sm = StringManager.getManager(InputBuffer.class);

    private static final Log log = LogFactory.getLog(InputBuffer.class);

    public static final int DEFAULT_BUFFER_SIZE = 8 * 1024;

    // The buffer can be used for byte[] and char[] reading
    // ( this is needed to support ServletInputStream and BufferedReader )
    public final int INITIAL_STATE = 0;
    public final int CHAR_STATE = 1;
    public final int BYTE_STATE = 2;


    /**
     * Encoder cache.
     */
    private static final ConcurrentMap<Charset, SynchronizedStack<B2CConverter>> encoders = new ConcurrentHashMap<>();

    // ----------------------------------------------------- Instance Variables

    /**
     * The byte buffer.
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
     * Encoding to use.
     */
    private String enc;


    /**
     * Current byte to char converter.
     */
    protected B2CConverter conv;


    /**
     * Associated Coyote request.
     */
    private Request coyoteRequest;


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
     */
    public InputBuffer() {

        this(DEFAULT_BUFFER_SIZE);

    }


    /**
     * Alternate constructor which allows specifying the initial buffer size.
     *
     * @param size Buffer size to use
     */
    public InputBuffer(int size) {

        this.size = size;
        bb = ByteBuffer.allocate(size);
        clear(bb);
        cb = CharBuffer.allocate(size);
        clear(cb);
        readLimit = size;

    }


    // ------------------------------------------------------------- Properties


    /**
     * Associated Coyote request.
     *
     * @param coyoteRequest Associated Coyote request
     */
    public void setRequest(Request coyoteRequest) {
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
        clear(bb);
        closed = false;

        if (conv != null) {
            conv.recycle();
            encoders.get(conv.getCharset()).push(conv);
            conv = null;
        }

        enc = null;
    }


    /**
     * Close the input buffer.
     *
     * @throws IOException An underlying IOException occurred
     */
    @Override
    public void close() throws IOException {
        closed = true;
    }


    public int available() {
        int available = availableInThisBuffer();
        if (available == 0) {
            coyoteRequest.action(ActionCode.AVAILABLE,
                    Boolean.valueOf(coyoteRequest.getReadListener() != null));
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

        // The container is responsible for the first call to
        // listener.onDataAvailable(). If isReady() returns true, the container
        // needs to call listener.onDataAvailable() from a new thread. If
        // isReady() returns false, the socket will be registered for read and
        // the container will call listener.onDataAvailable() once data arrives.
        // Must call isFinished() first as a call to isReady() if the request
        // has been finished will register the socket for read interest and that
        // is not required.
        if (!coyoteRequest.isFinished() && isReady()) {
            coyoteRequest.action(ActionCode.DISPATCH_READ, null);
            if (!ContainerThreadMarker.isContainerThread()) {
                // Not on a container thread so need to execute the dispatch
                coyoteRequest.action(ActionCode.DISPATCH_EXECUTE, null);
            }
        }
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
            return false;
        }
        if (isFinished()) {
            // If this is a non-container thread, need to trigger a read
            // which will eventually lead to a call to onAllDataRead() via a
            // container thread.
            if (!ContainerThreadMarker.isContainerThread()) {
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

        AtomicBoolean result = new AtomicBoolean();
        coyoteRequest.action(ActionCode.NB_READ_INTEREST, result);
        return result.get();
    }


    boolean isBlocking() {
        return coyoteRequest.getReadListener() == null;
    }


    // ------------------------------------------------- Bytes Handling Methods

    /**
     * Reads new bytes in the byte chunk.
     *
     * @throws IOException An underlying IOException occurred
     */
    @Override
    public int realReadBytes() throws IOException {
        if (closed) {
            return -1;
        }
        if (coyoteRequest == null) {
            return -1;
        }

        if (state == INITIAL_STATE) {
            state = BYTE_STATE;
        }

        try {
            return coyoteRequest.doRead(this);
        } catch (IOException ioe) {
            coyoteRequest.setErrorException(ioe);
            // An IOException on a read is almost always due to
            // the remote client aborting the request.
            throw new ClientAbortException(ioe);
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
     * Transfers bytes from the buffer to the specified ByteBuffer. After the
     * operation the position of the ByteBuffer will be returned to the one
     * before the operation, the limit will be the position incremented by
     * the number of the transferred bytes.
     *
     * @param to the ByteBuffer into which bytes are to be written.
     * @return an integer specifying the actual number of bytes read, or -1 if
     *         the end of the stream is reached
     * @throws IOException if an input or output exception has occurred
     */
    public int read(ByteBuffer to) throws IOException {
        throwIfClosed();

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


    /**
     * @param s     New encoding value
     *
     * @deprecated This method will be removed in Tomcat 9.0.x
     */
    @Deprecated
    public void setEncoding(String s) {
        enc = s;
    }


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

        Charset charset = null;

        if (coyoteRequest != null) {
            charset = coyoteRequest.getCharset();
        }

        if (charset == null) {
            if (enc == null) {
                charset = org.apache.coyote.Constants.DEFAULT_BODY_CHARSET;
            } else {
                charset = B2CConverter.getCharset(enc);
            }
        }

        SynchronizedStack<B2CConverter> stack = encoders.get(charset);
        if (stack == null) {
            stack = new SynchronizedStack<>();
            encoders.putIfAbsent(charset, stack);
            stack = encoders.get(charset);
        }
        conv = stack.pop();

        if (conv == null) {
            conv = createConverter(charset);
        }
    }


    private static B2CConverter createConverter(final Charset charset) throws IOException {
        if (SecurityUtil.isPackageProtectionEnabled()) {
            try {
                return AccessController.doPrivileged(new PrivilegedExceptionAction<B2CConverter>() {

                    @Override
                    public B2CConverter run() throws IOException {
                        return new B2CConverter(charset);
                    }
                });
            } catch (PrivilegedActionException ex) {
                Exception e = ex.getException();
                if (e instanceof IOException) {
                    throw (IOException) e;
                } else {
                    throw new IOException(e);
                }
            }
        } else {
            return new B2CConverter(charset);
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
        if(desiredSize > readLimit) {
            desiredSize = readLimit;
        }

        if(desiredSize <= cb.capacity()) {
            return;
        }

        int newSize = 2 * cb.capacity();
        if(desiredSize >= newSize) {
            newSize= 2 * cb.capacity() + count;
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
