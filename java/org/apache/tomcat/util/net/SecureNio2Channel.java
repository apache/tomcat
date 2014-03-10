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
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.CompletionHandler;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import javax.net.ssl.SSLEngineResult.Status;
import javax.net.ssl.SSLException;

/**
 * Implementation of a secure socket channel for NIO2.
 */
public class SecureNio2Channel extends Nio2Channel  {

    protected ByteBuffer netInBuffer;
    protected ByteBuffer netOutBuffer;

    protected SSLEngine sslEngine;
    protected final Nio2Endpoint endpoint;
    protected SocketWrapper<Nio2Channel> socket;

    protected boolean handshakeComplete = false;
    protected HandshakeStatus handshakeStatus; //gets set by handshake

    protected boolean closed = false;
    protected boolean closing = false;
    protected boolean readPending = false;
    protected boolean writePending = false;

    private CompletionHandler<Integer, SocketWrapper<Nio2Channel>> handshakeReadCompletionHandler;
    private CompletionHandler<Integer, SocketWrapper<Nio2Channel>> handshakeWriteCompletionHandler;

    public SecureNio2Channel(AsynchronousSocketChannel channel, SSLEngine engine,
            ApplicationBufferHandler bufHandler, Nio2Endpoint endpoint0) throws IOException {
        super(channel, bufHandler);
        this.sslEngine = engine;
        this.endpoint = endpoint0;
        int appBufSize = sslEngine.getSession().getApplicationBufferSize();
        int netBufSize = sslEngine.getSession().getPacketBufferSize();
        //allocate network buffers - TODO, add in optional direct non-direct buffers
        netInBuffer = ByteBuffer.allocateDirect(netBufSize);
        netOutBuffer = ByteBuffer.allocateDirect(netBufSize);

        handshakeReadCompletionHandler = new CompletionHandler<Integer, SocketWrapper<Nio2Channel>>() {
            @Override
            public void completed(Integer result, SocketWrapper<Nio2Channel> attachment) {
                if (result < 0) {
                    failed(new IOException("Error"), attachment);
                    return;
                }
                endpoint.processSocket(attachment, SocketStatus.OPEN_READ, false);
            }
            @Override
            public void failed(Throwable exc, SocketWrapper<Nio2Channel> attachment) {
                endpoint.closeSocket(attachment, SocketStatus.ERROR);
            }
        };
        handshakeWriteCompletionHandler = new CompletionHandler<Integer, SocketWrapper<Nio2Channel>>() {
            @Override
            public void completed(Integer result, SocketWrapper<Nio2Channel> attachment) {
                if (result < 0) {
                    failed(new IOException("Error"), attachment);
                    return;
                }
                endpoint.processSocket(attachment, SocketStatus.OPEN_WRITE, false);
            }
            @Override
            public void failed(Throwable exc, SocketWrapper<Nio2Channel> attachment) {
                endpoint.closeSocket(attachment, SocketStatus.ERROR);
            }
        };

        //ensure that the application has a large enough read/write buffers
        //by doing this, we should not encounter any buffer overflow errors
        // FIXME: this does nothing, so it is in the NIO2 endpoint
        bufHandler.expand(bufHandler.getReadBuffer(), appBufSize);
        reset();
    }

    void setSocket(SocketWrapper<Nio2Channel> socket) {
        this.socket = socket;
    }

    public void reset(SSLEngine engine) throws IOException {
        this.sslEngine = engine;
        reset();
    }

    @Override
    public void reset() throws IOException {
        super.reset();
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

    @Override
    public int getBufferSize() {
        int size = super.getBufferSize();
        size += netInBuffer!=null?netInBuffer.capacity():0;
        size += netOutBuffer!=null?netOutBuffer.capacity():0;
        return size;
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
            int result = integer.get();
            return result >= 0;
        }
        @Override
        public Boolean get(long timeout, TimeUnit unit)
                throws InterruptedException, ExecutionException,
                TimeoutException {
            int result = integer.get(timeout, unit);
            return result >= 0;
        }
    };

    /**
     * Flush the channel.
     *
     * @return <code>true</code> if the network buffer has been flushed out and
     *         is empty else <code>false</code> (as a future)
     * @throws IOException
     */
    @Override
    public Future<Boolean> flush()
            throws IOException {
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
                    throw new IOException("NOT_HANDSHAKING during handshake");
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
                                throw new IOException("Handshake error");
                            }
                        }
                        return Nio2Endpoint.OP_WRITE;
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
                        throw new IOException("Unexpected status:" + handshake.getStatus() + " during handshake WRAP.");
                    }
                    if (handshakeStatus != HandshakeStatus.NEED_UNWRAP || netOutBuffer.remaining() > 0) {
                        //should actually return OP_READ if we have NEED_UNWRAP
                        if (async) {
                            sc.write(netOutBuffer, socket, handshakeWriteCompletionHandler);
                        } else {
                            try {
                                sc.write(netOutBuffer).get(endpoint.getSoTimeout(), TimeUnit.MILLISECONDS);
                            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                                throw new IOException("Handshake error");
                            }
                        }
                        return Nio2Endpoint.OP_WRITE;
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
                                throw new IOException("Handshake error");
                            }
                        }
                        return Nio2Endpoint.OP_READ;
                    } else {
                        throw new IOException("Invalid handshake status:"+handshakeStatus+" during handshake UNWRAP.");
                    }
                    break;
                }
                case NEED_TASK: {
                    handshakeStatus = tasks();
                    break;
                }
                default: throw new IllegalStateException("Invalid handshake status:"+handshakeStatus);
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
     * @throws SocketTimeoutException - if a socket operation timed out
     */
    public void rehandshake() throws IOException {
        //validate the network buffers are empty
        if (netInBuffer.position() > 0 && netInBuffer.position()<netInBuffer.limit()) throw new IOException("Network input buffer still contains data. Handshake will fail.");
        if (netOutBuffer.position() > 0 && netOutBuffer.position()<netOutBuffer.limit()) throw new IOException("Network output buffer still contains data. Handshake will fail.");
        if (getBufHandler().getReadBuffer().position()>0 && getBufHandler().getReadBuffer().position()<getBufHandler().getReadBuffer().limit()) throw new IOException("Application input buffer still contains data. Data would have been lost.");
        if (getBufHandler().getWriteBuffer().position()>0 && getBufHandler().getWriteBuffer().position()<getBufHandler().getWriteBuffer().limit()) throw new IOException("Application output buffer still contains data. Data would have been lost.");
        reset();
        boolean handshaking = true;
        try {
            while (handshaking) {
                int hsStatus = handshakeInternal(false);
                switch (hsStatus) {
                    case -1 : throw new EOFException("EOF during handshake.");
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
     * @param doWrite boolean
     * @return SSLEngineResult
     * @throws IOException
     */
    protected SSLEngineResult handshakeWrap() throws IOException {
        //this should never be called with a network buffer that contains data
        //so we can clear it here.
        netOutBuffer.clear();
        //perform the wrap
        SSLEngineResult result = sslEngine.wrap(bufHandler.getWriteBuffer(), netOutBuffer);
        //prepare the results to be written
        netOutBuffer.flip();
        //set the status
        handshakeStatus = result.getHandshakeStatus();
        return result;
    }

    /**
     * Perform handshake unwrap
     * @param doread boolean
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
     *   while (isOpen() && !myTimeoutFunction()) Thread.sleep(25);
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
            if (!flush().get(endpoint.getSoTimeout(), TimeUnit.MILLISECONDS)) {
                throw new IOException("Remaining data in the network buffer, can't send SSL close message, force a close with close(true) instead");
            }
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            throw new IOException("Remaining data in the network buffer, can't send SSL close message, force a close with close(true) instead", e);
        }
        //prep the buffer for the close message
        netOutBuffer.clear();
        //perform the close, since we called sslEngine.closeOutbound
        SSLEngineResult handshake = sslEngine.wrap(getEmptyBuf(), netOutBuffer);
        //we should be in a close state
        if (handshake.getStatus() != SSLEngineResult.Status.CLOSED) {
            throw new IOException("Invalid close state, will not send network data.");
        }
        //prepare the buffer for writing
        netOutBuffer.flip();
        //if there is data to be written
        flush();

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
        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return false;
        }
        @Override
        public boolean isCancelled() {
            return false;
        }
        @Override
        public boolean isDone() {
            return true;
        }
        @Override
        public Integer get() throws InterruptedException, ExecutionException {
            return unwrap(netInBuffer.position());
        }
        @Override
        public Integer get(long timeout, TimeUnit unit)
                throws InterruptedException, ExecutionException,
                TimeoutException {
            return unwrap(netInBuffer.position());
        }
        protected Integer unwrap(int netread) throws ExecutionException {
            //are we in the middle of closing or closed?
            if (closing || closed)
                return -1;
            //did we reach EOF? if so send EOF up one layer.
            if (netread == -1)
                return -1;
            //the data read
            int read = 0;
            //the SSL engine result
            SSLEngineResult unwrap;
            do {
                //prepare the buffer
                netInBuffer.flip();
                //unwrap the data
                try {
                    unwrap = sslEngine.unwrap(netInBuffer, bufHandler.getReadBuffer());
                } catch (SSLException e) {
                    throw new ExecutionException(e);
                }
                //compact the buffer
                netInBuffer.compact();
                if (unwrap.getStatus()==Status.OK || unwrap.getStatus()==Status.BUFFER_UNDERFLOW) {
                    //we did receive some data, add it to our total
                    read += unwrap.bytesProduced();
                    //perform any tasks if needed
                    if (unwrap.getHandshakeStatus() == HandshakeStatus.NEED_TASK)
                        tasks();
                    //if we need more network data, then bail out for now.
                    if (unwrap.getStatus() == Status.BUFFER_UNDERFLOW)
                        break;
                } else if (unwrap.getStatus()==Status.BUFFER_OVERFLOW && read > 0) {
                    //buffer overflow can happen, if we have read data, then
                    //empty out the dst buffer before we do another read
                    break;
                } else {
                    //here we should trap BUFFER_OVERFLOW and call expand on the buffer
                    //for now, throw an exception, as we initialized the buffers
                    //in the constructor
                    throw new ExecutionException(new IOException("Unable to unwrap data, invalid status: " + unwrap.getStatus()));
                }
            } while ((netInBuffer.position() != 0)); //continue to unwrapping as long as the input buffer has stuff
            return (read);
        }
    }

    private class FutureNetRead extends FutureRead {
        private Future<Integer> integer;
        protected FutureNetRead() {
            this.integer = sc.read(netInBuffer);
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
        public Integer get() throws InterruptedException, ExecutionException {
            int netread = integer.get();
            return unwrap(netread);
        }
        @Override
        public Integer get(long timeout, TimeUnit unit)
                throws InterruptedException, ExecutionException,
                TimeoutException {
            int netread = integer.get(timeout, unit);
            return unwrap(netread);
        }
    }

    /**
     * Reads a sequence of bytes from this channel into the given buffer.
     *
     * @param dst The buffer into which bytes are to be transferred
     * @return The number of bytes read, possibly zero, or <tt>-1</tt> if the channel has reached end-of-stream
     * @throws IOException If some other I/O error occurs
     * @throws IllegalArgumentException if the destination buffer is different than bufHandler.getReadBuffer()
     */
    @Override
    public Future<Integer> read(ByteBuffer dst) {
        //did we finish our handshake?
        if (!handshakeComplete)
            throw new IllegalStateException("Handshake incomplete, you must complete handshake before reading data.");
        if (netInBuffer.position() > 0) {
            return new FutureRead();
        } else {
            return new FutureNetRead();
        }
    }

    private class FutureWrite implements Future<Integer> {
        private Future<Integer> integer = null;
        private int written = 0;
        private Throwable t = null;
        protected FutureWrite() {
            //are we closing or closed?
            if (closing || closed) {
                t = new IOException("Channel is in closing state.");
                return;
            }
            //The data buffer should be empty, we can reuse the entire buffer.
            netOutBuffer.clear();
            try {
                SSLEngineResult result = sslEngine.wrap(bufHandler.getWriteBuffer(), netOutBuffer);
                written = result.bytesConsumed();
                netOutBuffer.flip();
                if (result.getStatus() == Status.OK) {
                    if (result.getHandshakeStatus() == HandshakeStatus.NEED_TASK)
                        tasks();
                } else {
                    t = new IOException("Unable to wrap data, invalid engine state: " +result.getStatus());
                }
                integer = sc.write(netOutBuffer);
            } catch (SSLException e) {
                t = e;
            }
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
        public Integer get() throws InterruptedException, ExecutionException {
            if (t != null) {
                throw new ExecutionException(t);
            }
            integer.get();
            return written;
        }
        @Override
        public Integer get(long timeout, TimeUnit unit)
                throws InterruptedException, ExecutionException,
                TimeoutException {
            if (t != null) {
                throw new ExecutionException(t);
            }
            integer.get(timeout, unit);
            return written;
        }
    }

    /**
     * Writes a sequence of bytes to this channel from the given buffer.
     *
     * @param src The buffer from which bytes are to be retrieved
     * @return The number of bytes written, possibly zero
     * @throws IOException If some other I/O error occurs
     */
    @Override
    public Future<Integer> write(ByteBuffer src) {
        return new FutureWrite();
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
            if (nBytes < 0) {
                handler.failed(new ClosedChannelException(), attach);
                return;
            }
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
                        if (unwrap.getStatus() == Status.BUFFER_UNDERFLOW)
                            break;
                    } else if (unwrap.getStatus() == Status.BUFFER_OVERFLOW && read > 0) {
                        //buffer overflow can happen, if we have read data, then
                        //empty out the dst buffer before we do another read
                        break;
                    } else {
                        //here we should trap BUFFER_OVERFLOW and call expand on the buffer
                        //for now, throw an exception, as we initialized the buffers
                        //in the constructor
                        throw new IOException("Unable to unwrap data, invalid status: " + unwrap.getStatus());
                    }
                } while ((netInBuffer.position() != 0)); //continue to unwrapping as long as the input buffer has stuff
                // If everything is OK, so complete
                handler.completed(read, attach);
            } catch (Exception e) {
                // The operation must fails
                handler.failed(e, attach);
            }
        }
        @Override
        public void failed(Throwable exc, A attach) {
            handler.failed(exc, attach);
        }
    }

    @Override
    public <A> void read(final ByteBuffer dst,
            long timeout, TimeUnit unit, final A attachment,
            final CompletionHandler<Integer, ? super A> handler) {
        //are we in the middle of closing or closed?
        if (closing || closed) {
            handler.completed(-1, attachment);
            return;
        }
        //did we finish our handshake?
        if (!handshakeComplete)
            throw new IllegalStateException("Handshake incomplete, you must complete handshake before reading data.");
        ReadCompletionHandler<A> readCompletionHandler = new ReadCompletionHandler<A>(dst, handler);
        if (netInBuffer.position() > 0 ) {
            readCompletionHandler.completed(netInBuffer.position(), attachment);
        } else {
            sc.read(netInBuffer, timeout, unit, attachment, readCompletionHandler);
        }
    }

    @Override
    public <A> void write(final ByteBuffer src, long timeout, TimeUnit unit, final A attachment,
            final CompletionHandler<Integer, ? super A> handler) {
        //are we closing or closed?
        if (closing || closed) {
            handler.failed(new IOException("Channel is in closing state."), attachment);
            return;
        }

        try {
            // Prepare the output buffer
            this.netOutBuffer.clear();
            // Wrap the source data into the internal buffer
            SSLEngineResult result = sslEngine.wrap(bufHandler.getWriteBuffer(), netOutBuffer);
            final int written = result.bytesConsumed();
            netOutBuffer.flip();
            if (result.getStatus() == Status.OK) {
                if (result.getHandshakeStatus() == HandshakeStatus.NEED_TASK)
                    tasks();
            } else {
                handler.failed(new IOException("Unable to wrap data, invalid engine state: " +result.getStatus()), attachment);
                return;
            }
            // Write data to the channel
            sc.write(this.netOutBuffer, timeout, unit, attachment,
                    new CompletionHandler<Integer, A>() {
                @Override
                public void completed(Integer nBytes, A attach) {
                    if (nBytes < 0) {
                        handler.failed(new ClosedChannelException(), attach);
                    } else {
                        // Call the handler completed method with the
                        // consumed bytes number
                        handler.completed(written, attach);
                    }
                }
                @Override
                public void failed(Throwable exc, A attach) {
                    handler.failed(exc, attach);
                }
            });
        } catch (Throwable exp) {
            handler.failed(exp, attachment);
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
            this.pos = offset + 1;
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
            if (nBytes < 0) {
                state.handler.failed(new ClosedChannelException(), state.attachment);
            } else {
                if (state.pos == state.offset + state.length) {
                    state.handler.completed(state.writeCount, state.attachment);
                    return;
                }
                try {
                    // Prepare the output buffer
                    netOutBuffer.clear();
                    // Wrap the source data into the internal buffer
                    SSLEngineResult result = sslEngine.wrap(state.srcs[state.offset], netOutBuffer);
                    state.writeCount += result.bytesConsumed();
                    netOutBuffer.flip();
                    if (result.getStatus() == Status.OK) {
                        if (result.getHandshakeStatus() == HandshakeStatus.NEED_TASK)
                            tasks();
                    } else {
                        failed(new IOException("Unable to wrap data, invalid engine state: " +result.getStatus()), attachment);
                        return;
                    }
                    state.offset++;
                    // Write data to the channel
                    sc.write(netOutBuffer, state.timeout, state.unit, state, this);
                } catch (Throwable exp) {
                    failed(exp, attachment);
                }
            }
        }
        @Override
        public void failed(Throwable exc, GatherState<A> attachment) {
            state.handler.failed(exc, state.attachment);
        }
    }

    @Override
    public <A> void write(ByteBuffer[] srcs, int offset, int length,
            long timeout, TimeUnit unit, A attachment,
            CompletionHandler<Long, ? super A> handler) {
        if ((offset < 0) || (length < 0) || (offset > srcs.length - length)) {
            throw new IndexOutOfBoundsException();
        }
        //are we closing or closed?
        if (closing || closed) {
            handler.failed(new IOException("Channel is in closing state."), attachment);
            return;
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
                if (result.getHandshakeStatus() == HandshakeStatus.NEED_TASK)
                    tasks();
            } else {
                handler.failed(new IOException("Unable to wrap data, invalid engine state: " +result.getStatus()), attachment);
                return;
            }
            // Write data to the channel
            sc.write(netOutBuffer, timeout, unit, state, new GatherCompletionHandler<A>(state));
        } catch (Throwable exp) {
            handler.failed(exp, attachment);
        }
   }

    /**
     * Callback interface to be able to expand buffers
     * when buffer overflow exceptions happen
     */
    public static interface ApplicationBufferHandler {
        public ByteBuffer expand(ByteBuffer buffer, int remaining);
        public ByteBuffer getReadBuffer();
        public ByteBuffer getWriteBuffer();
    }

    @Override
    public ApplicationBufferHandler getBufHandler() {
        return bufHandler;
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

    public void setBufHandler(ApplicationBufferHandler bufHandler) {
        this.bufHandler = bufHandler;
    }

    @Override
    public AsynchronousSocketChannel getIOChannel() {
        return sc;
    }

}