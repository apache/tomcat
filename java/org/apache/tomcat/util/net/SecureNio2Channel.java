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
package org.apache.tomcat.util.net;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.nio.channels.ReadPendingException;
import java.nio.channels.WritePendingException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import javax.net.ssl.SSLEngineResult.Status;
import javax.net.ssl.SSLException;

import org.apache.tomcat.util.res.StringManager;

/**
 * Implementation of a secure socket channel for NIO2.
 */
public class SecureNio2Channel extends Nio2Channel  {

    protected static final StringManager sm = StringManager.getManager(
            SecureNio2Channel.class.getPackage().getName());

    protected ByteBuffer netInBuffer;
    protected ByteBuffer netOutBuffer;

    protected SSLEngine sslEngine;
    protected final Nio2Endpoint endpoint;

    protected boolean handshakeComplete;
    protected HandshakeStatus handshakeStatus; //gets set by handshake

    protected boolean closed;
    protected boolean closing;
    protected volatile boolean readPending;
    protected volatile boolean writePending;

    private CompletionHandler<Integer, SocketWrapperBase<Nio2Channel>> handshakeReadCompletionHandler;
    private CompletionHandler<Integer, SocketWrapperBase<Nio2Channel>> handshakeWriteCompletionHandler;

    public SecureNio2Channel(SSLEngine engine, SocketBufferHandler bufHandler,
            Nio2Endpoint endpoint0) {
        super(bufHandler);
        sslEngine = engine;
        endpoint = endpoint0;
        int netBufSize = sslEngine.getSession().getPacketBufferSize();
        if (endpoint.getSocketProperties().getDirectSslBuffer()) {
            netInBuffer = ByteBuffer.allocateDirect(netBufSize);
            netOutBuffer = ByteBuffer.allocateDirect(netBufSize);
        } else {
            netInBuffer = ByteBuffer.allocate(netBufSize);
            netOutBuffer = ByteBuffer.allocate(netBufSize);
        }
        handshakeReadCompletionHandler = new CompletionHandler<Integer, SocketWrapperBase<Nio2Channel>>() {
            @Override
            public void completed(Integer result, SocketWrapperBase<Nio2Channel> attachment) {
                if (result.intValue() < 0) {
                    failed(new EOFException(), attachment);
                    return;
                }
                endpoint.processSocket(attachment, SocketStatus.OPEN_READ, false);
            }
            @Override
            public void failed(Throwable exc, SocketWrapperBase<Nio2Channel> attachment) {
                endpoint.closeSocket(attachment);
            }
        };
        handshakeWriteCompletionHandler = new CompletionHandler<Integer, SocketWrapperBase<Nio2Channel>>() {
            @Override
            public void completed(Integer result, SocketWrapperBase<Nio2Channel> attachment) {
                if (result.intValue() < 0) {
                    failed(new EOFException(), attachment);
                    return;
                }
                endpoint.processSocket(attachment, SocketStatus.OPEN_WRITE, false);
            }
            @Override
            public void failed(Throwable exc, SocketWrapperBase<Nio2Channel> attachment) {
                endpoint.closeSocket(attachment);
            }
        };
    }

    public void setSSLEngine(SSLEngine engine) {
        this.sslEngine = engine;
    }

    @Override
    public void reset(AsynchronousSocketChannel channel, SocketWrapperBase<Nio2Channel> socket)
            throws IOException {
        super.reset(channel, socket);
        netOutBuffer.position(0);
        netOutBuffer.limit(0);
        netInBuffer.position(0);
        netInBuffer.limit(0);
        handshakeComplete = false;
        closed = false;
        closing = false;
        readPending = false;
        writePending = false;
        //initiate handshake
        sslEngine.beginHandshake();
        handshakeStatus = sslEngine.getHandshakeStatus();
    }


    private class FutureFlush implements Future<Boolean> {
        private Future<Integer> integer;
        protected FutureFlush(Future<Integer> integer) {
            this.integer = integer;
        }
        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return integer.cancel(mayInterruptIfRunning);
        }
        @Override
        public boolean isCancelled() {
            return integer.isCancelled();
        }
        @Override
        public boolean isDone() {
            return integer.isDone();
        }
        @Override
        public Boolean get() throws InterruptedException,
                ExecutionException {
            try {
                int result = integer.get().intValue();
                return Boolean.valueOf(result >= 0);
            } finally {
                writePending = false;
            }
        }
        @Override
        public Boolean get(long timeout, TimeUnit unit)
                throws InterruptedException, ExecutionException,
                TimeoutException {
            try {
                int result = integer.get(timeout, unit).intValue();
                return Boolean.valueOf(result >= 0);
            } finally {
                writePending = false;
            }
        }
    }

    /**
     * Flush the channel.
     *
     * @return <code>true</code> if the network buffer has been flushed out and
     *         is empty else <code>false</code> (as a future)
     */
    @Override
    public Future<Boolean> flush() {
        if (writePending) {
            throw new WritePendingException();
        } else {
            writePending = true;
        }
        return new FutureFlush(sc.write(netOutBuffer));
    }

    /**
     * Performs SSL handshake, non blocking, but performs NEED_TASK on the same thread.<br>
     * Hence, you should never call this method using your Acceptor thread, as you would slow down
     * your system significantly.<br>
     * The return for this operation is 0 if the handshake is complete and a positive value if it is not complete.
     * In the event of a positive value coming back, reregister the selection key for the return values interestOps.
     *
     * @return int - 0 if hand shake is complete, otherwise it returns a SelectionKey interestOps value
     * @throws IOException
     */
    @Override
    public int handshake() throws IOException {
        return handshakeInternal(true);
    }

    protected int handshakeInternal(boolean async) throws IOException {
        if (handshakeComplete)
            return 0; //we have done our initial handshake

        SSLEngineResult handshake = null;

        while (!handshakeComplete) {
            switch (handshakeStatus) {
                case NOT_HANDSHAKING: {
                    //should never happen
                    throw new IOException(sm.getString("channel.nio.ssl.notHandshaking"));
                }
                case FINISHED: {
                    //we are complete if we have delivered the last package
                    handshakeComplete = !netOutBuffer.hasRemaining();
                    //return 0 if we are complete, otherwise we still have data to write
                    if (handshakeComplete) {
                        return 0;
                    } else {
                        if (async) {
                            sc.write(netOutBuffer, socket, handshakeWriteCompletionHandler);
                        } else {
                            try {
                                sc.write(netOutBuffer).get(endpoint.getSoTimeout(), TimeUnit.MILLISECONDS);
                            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                                throw new IOException(sm.getString("channel.nio.ssl.handhakeError"));
                            }
                        }
                        return 1;
                    }
                }
                case NEED_WRAP: {
                    //perform the wrap function
                    handshake = handshakeWrap();
                    if (handshake.getStatus() == Status.OK){
                        if (handshakeStatus == HandshakeStatus.NEED_TASK)
                            handshakeStatus = tasks();
                    } else {
                        //wrap should always work with our buffers
                        throw new IOException(sm.getString("channel.nio.ssl.unexpectedStatusDuringWrap", handshake.getStatus()));
                    }
                    if (handshakeStatus != HandshakeStatus.NEED_UNWRAP || netOutBuffer.remaining() > 0) {
                        //should actually return OP_READ if we have NEED_UNWRAP
                        if (async) {
                            sc.write(netOutBuffer, socket, handshakeWriteCompletionHandler);
                        } else {
                            try {
                                sc.write(netOutBuffer).get(endpoint.getSoTimeout(), TimeUnit.MILLISECONDS);
                            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                                throw new IOException(sm.getString("channel.nio.ssl.handhakeError"));
                            }
                        }
                        return 1;
                    }
                    //fall down to NEED_UNWRAP on the same call, will result in a
                    //BUFFER_UNDERFLOW if it needs data
                }
                //$FALL-THROUGH$
                case NEED_UNWRAP: {
                    //perform the unwrap function
                    handshake = handshakeUnwrap();
                    if (handshake.getStatus() == Status.OK) {
                        if (handshakeStatus == HandshakeStatus.NEED_TASK)
                            handshakeStatus = tasks();
                    } else if (handshake.getStatus() == Status.BUFFER_UNDERFLOW) {
                        //read more data, reregister for OP_READ
                        if (async) {
                            sc.read(netInBuffer, socket, handshakeReadCompletionHandler);
                        } else {
                            try {
                                sc.read(netInBuffer).get(endpoint.getSoTimeout(), TimeUnit.MILLISECONDS);
                            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                                throw new IOException(sm.getString("channel.nio.ssl.handhakeError"));
                            }
                        }
                        return 1;
                    } else {
                        throw new IOException(sm.getString("channel.nio.ssl.unexpectedStatusDuringUnwrap", handshakeStatus));
                    }
                    break;
                }
                case NEED_TASK: {
                    handshakeStatus = tasks();
                    break;
                }
                default: throw new IllegalStateException(sm.getString("channel.nio.ssl.invalidStatus", handshakeStatus));
            }
        }
        //return 0 if we are complete, otherwise recurse to process the task
        return handshakeComplete ? 0 : handshakeInternal(async);
    }

    /**
     * Force a blocking handshake to take place for this key.
     * This requires that both network and application buffers have been emptied out prior to this call taking place, or a
     * IOException will be thrown.
     * @throws IOException - if an IO exception occurs or if application or network buffers contain data
     * @throws java.net.SocketTimeoutException - if a socket operation timed out
     */
    public void rehandshake() throws IOException {
        //validate the network buffers are empty
        if (netInBuffer.position() > 0 && netInBuffer.position() < netInBuffer.limit()) throw new IOException(sm.getString("channel.nio.ssl.netInputNotEmpty"));
        if (netOutBuffer.position() > 0 && netOutBuffer.position() < netOutBuffer.limit()) throw new IOException(sm.getString("channel.nio.ssl.netOutputNotEmpty"));
        if (!getBufHandler().isReadBufferEmpty()) throw new IOException(sm.getString("channel.nio.ssl.appInputNotEmpty"));
        if (!getBufHandler().isWriteBufferEmpty()) throw new IOException(sm.getString("channel.nio.ssl.appOutputNotEmpty"));

        netOutBuffer.position(0);
        netOutBuffer.limit(0);
        netInBuffer.position(0);
        netInBuffer.limit(0);
        getBufHandler().reset();

        handshakeComplete = false;
        //initiate handshake
        sslEngine.beginHandshake();
        handshakeStatus = sslEngine.getHandshakeStatus();

        boolean handshaking = true;
        try {
            while (handshaking) {
                int hsStatus = handshakeInternal(false);
                switch (hsStatus) {
                    case -1 : throw new EOFException(sm.getString("channel.nio.ssl.eofDuringHandshake"));
                    case  0 : handshaking = false; break;
                    default : // Some blocking IO occurred, so iterate
                }
            }
        } catch (IOException x) {
            throw x;
        } catch (Exception cx) {
            IOException x = new IOException(cx);
            throw x;
        }
    }


    /**
     * Executes all the tasks needed on the same thread.
     * @return HandshakeStatus
     */
    protected SSLEngineResult.HandshakeStatus tasks() {
        Runnable r = null;
        while ( (r = sslEngine.getDelegatedTask()) != null) {
            r.run();
        }
        return sslEngine.getHandshakeStatus();
    }

    /**
     * Performs the WRAP function
     * @return SSLEngineResult
     * @throws IOException
     */
    protected SSLEngineResult handshakeWrap() throws IOException {
        //this should never be called with a network buffer that contains data
        //so we can clear it here.
        netOutBuffer.clear();
        //perform the wrap
        bufHandler.configureWriteBufferForRead();
        SSLEngineResult result = sslEngine.wrap(bufHandler.getWriteBuffer(), netOutBuffer);
        //prepare the results to be written
        netOutBuffer.flip();
        //set the status
        handshakeStatus = result.getHandshakeStatus();
        return result;
    }

    /**
     * Perform handshake unwrap
     * @return SSLEngineResult
     * @throws IOException
     */
    protected SSLEngineResult handshakeUnwrap() throws IOException {

        if (netInBuffer.position() == netInBuffer.limit()) {
            //clear the buffer if we have emptied it out on data
            netInBuffer.clear();
        }
        SSLEngineResult result;
        boolean cont = false;
        //loop while we can perform pure SSLEngine data
        do {
            //prepare the buffer with the incoming data
            netInBuffer.flip();
            //call unwrap
            bufHandler.configureReadBufferForWrite();
            result = sslEngine.unwrap(netInBuffer, bufHandler.getReadBuffer());
            //compact the buffer, this is an optional method, wonder what would happen if we didn't
            netInBuffer.compact();
            //read in the status
            handshakeStatus = result.getHandshakeStatus();
            if (result.getStatus() == SSLEngineResult.Status.OK &&
                 result.getHandshakeStatus() == HandshakeStatus.NEED_TASK) {
                //execute tasks if we need to
                handshakeStatus = tasks();
            }
            //perform another unwrap?
            cont = result.getStatus() == SSLEngineResult.Status.OK &&
                   handshakeStatus == HandshakeStatus.NEED_UNWRAP;
        } while (cont);
        return result;
    }

    /**
     * Sends a SSL close message, will not physically close the connection here.<br>
     * To close the connection, you could do something like
     * <pre><code>
     *   close();
     *   while (isOpen() &amp;&amp; !myTimeoutFunction()) Thread.sleep(25);
     *   if ( isOpen() ) close(true); //forces a close if you timed out
     * </code></pre>
     * @throws IOException if an I/O error occurs
     * @throws IOException if there is data on the outgoing network buffer and we are unable to flush it
     */
    @Override
    public void close() throws IOException {
        if (closing) return;
        closing = true;
        sslEngine.closeOutbound();

        try {
            if (!flush().get(endpoint.getSoTimeout(), TimeUnit.MILLISECONDS).booleanValue()) {
                throw new IOException(sm.getString("channel.nio.ssl.remainingDataDuringClose"));
            }
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            throw new IOException(sm.getString("channel.nio.ssl.remainingDataDuringClose"), e);
        } catch (WritePendingException e) {
            throw new IOException(sm.getString("channel.nio.ssl.pendingWriteDuringClose"), e);
        }
        //prep the buffer for the close message
        netOutBuffer.clear();
        //perform the close, since we called sslEngine.closeOutbound
        SSLEngineResult handshake = sslEngine.wrap(getEmptyBuf(), netOutBuffer);
        //we should be in a close state
        if (handshake.getStatus() != SSLEngineResult.Status.CLOSED) {
            throw new IOException(sm.getString("channel.nio.ssl.invalidCloseState"));
        }
        //prepare the buffer for writing
        netOutBuffer.flip();
        //if there is data to be written
        try {
            if (!flush().get(endpoint.getSoTimeout(), TimeUnit.MILLISECONDS).booleanValue()) {
                throw new IOException(sm.getString("channel.nio.ssl.remainingDataDuringClose"));
            }
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            throw new IOException(sm.getString("channel.nio.ssl.remainingDataDuringClose"), e);
        } catch (WritePendingException e) {
            throw new IOException(sm.getString("channel.nio.ssl.pendingWriteDuringClose"), e);
        }

        //is the channel closed?
        closed = (!netOutBuffer.hasRemaining() && (handshake.getHandshakeStatus() != HandshakeStatus.NEED_WRAP));
    }

    /**
     * Force a close, can throw an IOException
     * @param force boolean
     * @throws IOException
     */
    @Override
    public void close(boolean force) throws IOException {
        try {
            close();
        } finally {
            if ( force || closed ) {
                closed = true;
                sc.close();
            }
        }
    }

    private class FutureRead implements Future<Integer> {
        private final ByteBuffer dst;
        private final Future<Integer> integer;
        public FutureRead(ByteBuffer dst) {
            this.dst = dst;
            this.integer = sc.read(netInBuffer);
        }
        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            readPending = false;
            return integer.cancel(mayInterruptIfRunning);
        }
        @Override
        public boolean isCancelled() {
            return integer.isCancelled();
        }
        @Override
        public boolean isDone() {
            return integer.isDone();
        }
        @Override
        public Integer get() throws InterruptedException, ExecutionException {
            try {
                return unwrap(integer.get().intValue());
            } finally {
                readPending = false;
            }
        }
        @Override
        public Integer get(long timeout, TimeUnit unit)
                throws InterruptedException, ExecutionException,
                TimeoutException {
            try {
                return unwrap(integer.get(timeout, unit).intValue());
            } finally {
                readPending = false;
            }
        }
        private Integer unwrap(int nRead) throws ExecutionException {
            //are we in the middle of closing or closed?
            if (closing || closed)
                return Integer.valueOf(-1);
            //did we reach EOF? if so send EOF up one layer.
            if (nRead < 0)
                return Integer.valueOf(-1);
            //the data read
            int read = 0;
            //the SSL engine result
            SSLEngineResult unwrap;
            do {
                //prepare the buffer
                netInBuffer.flip();
                //unwrap the data
                try {
                    unwrap = sslEngine.unwrap(netInBuffer, dst);
                } catch (SSLException e) {
                    throw new ExecutionException(e);
                }
                //compact the buffer
                netInBuffer.compact();
                if (unwrap.getStatus() == Status.OK || unwrap.getStatus() == Status.BUFFER_UNDERFLOW) {
                    //we did receive some data, add it to our total
                    read += unwrap.bytesProduced();
                    //perform any tasks if needed
                    if (unwrap.getHandshakeStatus() == HandshakeStatus.NEED_TASK) {
                        tasks();
                    }
                    //if we need more network data, then bail out for now.
                    if (unwrap.getStatus() == Status.BUFFER_UNDERFLOW) {
                        break;
                    }
                } else if (unwrap.getStatus() == Status.BUFFER_OVERFLOW && read > 0) {
                    //buffer overflow can happen, if we have read data, then
                    //empty out the dst buffer before we do another read
                    break;
                } else {
                    //here we should trap BUFFER_OVERFLOW and call expand on the buffer
                    //for now, throw an exception, as we initialized the buffers
                    //in the constructor
                    throw new ExecutionException(new IOException(sm.getString("channel.nio.ssl.unwrapFail", unwrap.getStatus())));
                }
            } while ((netInBuffer.position() != 0)); //continue to unwrapping as long as the input buffer has stuff
            return Integer.valueOf(read);
        }
    }

    /**
     * Reads a sequence of bytes from this channel into the given buffer.
     *
     * @param dst The buffer into which bytes are to be transferred
     * @return The number of bytes read, possibly zero, or <tt>-1</tt> if the channel has reached end-of-stream
     * @throws IllegalStateException if the handshake was not completed
     */
    @Override
    public Future<Integer> read(ByteBuffer dst) {
        if (!handshakeComplete) {
            throw new IllegalStateException(sm.getString("channel.nio.ssl.incompleteHandshake"));
        }
        if (readPending) {
            throw new ReadPendingException();
        } else {
            readPending = true;
        }
        return new FutureRead(dst);
    }

    private class FutureWrite implements Future<Integer> {
        private ByteBuffer src;
        private Future<Integer> integer = null;
        private int written = 0;
        private Throwable t = null;
        protected FutureWrite(ByteBuffer src) {
            //are we closing or closed?
            if (closing || closed) {
                t = new IOException(sm.getString("channel.nio.ssl.closing"));
                return;
            }
            this.src = src;
            wrap();
        }
        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            writePending = false;
            return integer.cancel(mayInterruptIfRunning);
        }
        @Override
        public boolean isCancelled() {
            return integer.isCancelled();
        }
        @Override
        public boolean isDone() {
            return integer.isDone();
        }
        @Override
        public Integer get() throws InterruptedException, ExecutionException {
            if (t != null) {
                writePending = false;
                throw new ExecutionException(t);
            }
            if (integer.get().intValue() > 0 && written == 0) {
                wrap();
                return get();
            } else if (netOutBuffer.hasRemaining()) {
                integer = sc.write(netOutBuffer);
                return get();
            } else {
                writePending = false;
                return Integer.valueOf(written);
            }
        }
        @Override
        public Integer get(long timeout, TimeUnit unit)
                throws InterruptedException, ExecutionException,
                TimeoutException {
            if (t != null) {
                writePending = false;
                throw new ExecutionException(t);
            }
            if (integer.get(timeout, unit).intValue() > 0 && written == 0) {
                wrap();
                return get(timeout, unit);
            } else if (netOutBuffer.hasRemaining()) {
                integer = sc.write(netOutBuffer);
                return get(timeout, unit);
            } else {
                writePending = false;
                return Integer.valueOf(written);
            }
        }
        protected void wrap() {
            try {
                if (!netOutBuffer.hasRemaining()) {
                    netOutBuffer.clear();
                    SSLEngineResult result = sslEngine.wrap(src, netOutBuffer);
                    written = result.bytesConsumed();
                    netOutBuffer.flip();
                    if (result.getStatus() == Status.OK) {
                        if (result.getHandshakeStatus() == HandshakeStatus.NEED_TASK)
                            tasks();
                    } else {
                        t = new IOException(sm.getString("channel.nio.ssl.wrapFail", result.getStatus()));
                    }
                }
                integer = sc.write(netOutBuffer);
            } catch (SSLException e) {
                t = e;
            }
        }
    }

    /**
     * Writes a sequence of bytes to this channel from the given buffer.
     *
     * @param src The buffer from which bytes are to be retrieved
     * @return The number of bytes written, possibly zero
     */
    @Override
    public Future<Integer> write(ByteBuffer src) {
        if (writePending) {
            throw new WritePendingException();
        } else {
            writePending = true;
        }
        return new FutureWrite(src);
    }

    private class ReadCompletionHandler<A> implements CompletionHandler<Integer, A> {
        protected ByteBuffer dst;
        protected CompletionHandler<Integer, ? super A> handler;
        protected ReadCompletionHandler(ByteBuffer dst, CompletionHandler<Integer, ? super A> handler) {
            this.dst = dst;
            this.handler = handler;
        }

        @Override
        public void completed(Integer nBytes, A attach) {
            if (nBytes.intValue() < 0) {
                failed(new EOFException(), attach);
            } else {
                try {
                    //the data read
                    int read = 0;
                    //the SSL engine result
                    SSLEngineResult unwrap;
                    do {
                        //prepare the buffer
                        netInBuffer.flip();
                        //unwrap the data
                        unwrap = sslEngine.unwrap(netInBuffer, dst);
                        //compact the buffer
                        netInBuffer.compact();
                        if (unwrap.getStatus() == Status.OK || unwrap.getStatus() == Status.BUFFER_UNDERFLOW) {
                            //we did receive some data, add it to our total
                            read += unwrap.bytesProduced();
                            //perform any tasks if needed
                            if (unwrap.getHandshakeStatus() == HandshakeStatus.NEED_TASK)
                                tasks();
                            //if we need more network data, then bail out for now.
                            if (unwrap.getStatus() == Status.BUFFER_UNDERFLOW) {
                                break;
                            }
                        } else if (unwrap.getStatus() == Status.BUFFER_OVERFLOW && read > 0) {
                            //buffer overflow can happen, if we have read data, then
                            //empty out the dst buffer before we do another read
                            break;
                        } else {
                            //here we should trap BUFFER_OVERFLOW and call expand on the buffer
                            //for now, throw an exception, as we initialized the buffers
                            //in the constructor
                            throw new IOException(sm.getString("channel.nio.ssl.unwrapFail", unwrap.getStatus()));
                        }
                    } while ((netInBuffer.position() != 0)); //continue to unwrapping as long as the input buffer has stuff
                    // If everything is OK, so complete
                    readPending = false;
                    handler.completed(Integer.valueOf(read), attach);
                } catch (Exception e) {
                    failed(e, attach);
                }
            }
        }
        @Override
        public void failed(Throwable exc, A attach) {
            readPending = false;
            handler.failed(exc, attach);
        }
    }

    @Override
    public <A> void read(final ByteBuffer dst,
            long timeout, TimeUnit unit, final A attachment,
            final CompletionHandler<Integer, ? super A> handler) {
        // Check state
        if (closing || closed) {
            handler.completed(Integer.valueOf(-1), attachment);
            return;
        }
        if (!handshakeComplete) {
            throw new IllegalStateException(sm.getString("channel.nio.ssl.incompleteHandshake"));
        }
        if (readPending) {
            throw new ReadPendingException();
        } else {
            readPending = true;
        }
        sc.read(netInBuffer, timeout, unit, attachment, new ReadCompletionHandler<>(dst, handler));
    }

    // TODO: Possible optimization for scatter
    @Override
    public <A> void read(ByteBuffer[] dsts, int offset, int length,
            long timeout, TimeUnit unit, A attachment,
            final CompletionHandler<Long, ? super A> handler) {
        if (offset < 0 || dsts == null || (offset + length) > dsts.length) {
            throw new IllegalArgumentException();
        }
        ByteBuffer dst = null;
        // Find the first buffer with space
        for (int i = 0; i < length; i++) {
            ByteBuffer current = dsts[offset + i];
            if (current.position() < current.limit()) {
                dst = current;
            }
        }
        if (dst == null) {
            throw new IllegalArgumentException();
        }
        CompletionHandler<Integer, ? super A> handlerWrapper = new CompletionHandler<Integer, A>() {
            @Override
            public void completed(Integer result, A attachment) {
                handler.completed(Long.valueOf(result.longValue()), attachment);
            }
            @Override
            public void failed(Throwable exc, A attachment) {
                handler.failed(exc, attachment);
            }
        };
        read(dst, timeout, unit, attachment, handlerWrapper);
    }

    @Override
    public <A> void write(final ByteBuffer src, final long timeout, final TimeUnit unit,
            final A attachment, final CompletionHandler<Integer, ? super A> handler) {
        // Check state
        if (closing || closed) {
            handler.failed(new IOException(sm.getString("channel.nio.ssl.closing")), attachment);
            return;
        }
        if (writePending) {
            throw new WritePendingException();
        } else {
            writePending = true;
        }

        try {
            // Prepare the output buffer
            netOutBuffer.clear();
            // Wrap the source data into the internal buffer
            SSLEngineResult result = sslEngine.wrap(src, netOutBuffer);
            final int written = result.bytesConsumed();
            netOutBuffer.flip();
            if (result.getStatus() == Status.OK) {
                if (result.getHandshakeStatus() == HandshakeStatus.NEED_TASK) {
                    tasks();
                }
                // Write data to the channel
                sc.write(netOutBuffer, timeout, unit, attachment,
                        new CompletionHandler<Integer, A>() {
                    @Override
                    public void completed(Integer nBytes, A attach) {
                        if (nBytes.intValue() < 0) {
                            failed(new EOFException(), attach);
                        } else if (netOutBuffer.hasRemaining()) {
                            sc.write(netOutBuffer, timeout, unit, attachment, this);
                        } else if (written == 0) {
                            // Special case, start over to avoid code duplication
                            writePending = false;
                            write(src, timeout, unit, attachment, handler);
                        } else {
                            // Call the handler completed method with the
                            // consumed bytes number
                            writePending = false;
                            handler.completed(Integer.valueOf(written), attach);
                        }
                    }
                    @Override
                    public void failed(Throwable exc, A attach) {
                        writePending = false;
                        handler.failed(exc, attach);
                    }
                });
            } else {
                throw new IOException(sm.getString("channel.nio.ssl.wrapFail", result.getStatus()));
            }
        } catch (Exception e) {
            writePending = false;
            handler.failed(e, attachment);
        }
    }

    private class GatherState<A> {
        public ByteBuffer[] srcs;
        public int offset;
        public int length;
        public A attachment;
        public long timeout;
        public TimeUnit unit;
        public CompletionHandler<Long, ? super A> handler;
        protected GatherState(ByteBuffer[] srcs, int offset, int length,
                long timeout, TimeUnit unit, A attachment,
                CompletionHandler<Long, ? super A> handler) {
            this.srcs = srcs;
            this.offset = offset;
            this.length = length;
            this.timeout = timeout;
            this.unit = unit;
            this.attachment = attachment;
            this.handler = handler;
            this.pos = offset;
        }
        public long writeCount = 0;
        public int pos;
    }

    private class GatherCompletionHandler<A> implements CompletionHandler<Integer, GatherState<A>> {
        protected GatherState<A> state;
        protected GatherCompletionHandler(GatherState<A> state) {
            this.state = state;
        }
        @Override
        public void completed(Integer nBytes, GatherState<A> attachment) {
            if (nBytes.intValue() < 0) {
                failed(new EOFException(), attachment);
            } else {
                if (state.pos == state.offset + state.length) {
                    writePending = false;
                    state.handler.completed(Long.valueOf(state.writeCount), state.attachment);
                } else if (netOutBuffer.hasRemaining()) {
                    sc.write(netOutBuffer, state.timeout, state.unit, state, this);
                } else {
                    try {
                        // Prepare the output buffer
                        netOutBuffer.clear();
                        // Wrap the source data into the internal buffer
                        SSLEngineResult result = sslEngine.wrap(state.srcs[state.pos], netOutBuffer);
                        int written = result.bytesConsumed();
                        state.writeCount += written;
                        netOutBuffer.flip();
                        if (result.getStatus() == Status.OK) {
                            if (result.getHandshakeStatus() == HandshakeStatus.NEED_TASK) {
                                tasks();
                            }
                            if (!state.srcs[state.pos].hasRemaining()) {
                                state.pos++;
                            }
                            // Write data to the channel
                            sc.write(netOutBuffer, state.timeout, state.unit, state, this);
                        } else {
                            throw new IOException(sm.getString("channel.nio.ssl.wrapFail", result.getStatus()));
                        }
                    } catch (Exception e) {
                        failed(e, attachment);
                    }
                }
            }
        }
        @Override
        public void failed(Throwable exc, GatherState<A> attachment) {
            writePending = false;
            state.handler.failed(exc, state.attachment);
        }
    }

    // TODO: Possible optimization for gather
    @Override
    public <A> void write(ByteBuffer[] srcs, int offset, int length,
            long timeout, TimeUnit unit, A attachment,
            CompletionHandler<Long, ? super A> handler) {
        // Check state
        if ((offset < 0) || (length < 0) || (offset > srcs.length - length)) {
            throw new IndexOutOfBoundsException();
        }
        if (closing || closed) {
            handler.failed(new IOException(sm.getString("channel.nio.ssl.closing")), attachment);
            return;
        }
        if (writePending) {
            throw new WritePendingException();
        } else {
            writePending = true;
        }
        try {
            GatherState<A> state = new GatherState<>(srcs, offset, length,
                    timeout, unit, attachment, handler);
            // Prepare the output buffer
            netOutBuffer.clear();
            // Wrap the source data into the internal buffer
            SSLEngineResult result = sslEngine.wrap(srcs[offset], netOutBuffer);
            state.writeCount += result.bytesConsumed();
            netOutBuffer.flip();
            if (result.getStatus() == Status.OK) {
                if (result.getHandshakeStatus() == HandshakeStatus.NEED_TASK) {
                    tasks();
                }
                if (!srcs[offset].hasRemaining()) {
                    state.pos++;
                }
                // Write data to the channel
                sc.write(netOutBuffer, timeout, unit, state, new GatherCompletionHandler<>(state));
            } else {
                throw new IOException(sm.getString("channel.nio.ssl.wrapFail", result.getStatus()));
            }
        } catch (Exception e) {
            writePending = false;
            handler.failed(e, attachment);
        }
   }

    @Override
    public boolean isHandshakeComplete() {
        return handshakeComplete;
    }

    @Override
    public boolean isClosing() {
        return closing;
    }

    public SSLEngine getSslEngine() {
        return sslEngine;
    }

    public ByteBuffer getEmptyBuf() {
        return emptyBuf;
    }

    @Override
    public AsynchronousSocketChannel getIOChannel() {
        return sc;
    }

}