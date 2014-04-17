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
package org.apache.coyote.http11;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Selector;

import org.apache.coyote.InputBuffer;
import org.apache.coyote.Request;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.net.AbstractEndpoint;
import org.apache.tomcat.util.net.NioChannel;
import org.apache.tomcat.util.net.NioEndpoint;
import org.apache.tomcat.util.net.NioSelectorPool;
import org.apache.tomcat.util.net.SocketWrapper;

/**
 * Implementation of InputBuffer which provides HTTP request header parsing as
 * well as transfer decoding.
 */
public class InternalNioInputBuffer extends AbstractNioInputBuffer<NioChannel> {

    private static final Log log =
            LogFactory.getLog(InternalNioInputBuffer.class);

    // ----------------------------------------------------------- Constructors


    /**
     * Alternate constructor.
     */
    public InternalNioInputBuffer(Request request, int headerBufferSize) {
        super(request, headerBufferSize);
        inputStreamInputBuffer = new SocketInputBuffer();
    }

    /**
     * Underlying socket.
     */
    private NioChannel socket;

    /**
     * Selector pool, for blocking reads and blocking writes
     */
    private NioSelectorPool pool;


    // --------------------------------------------------------- Public Methods

    @Override
    protected final Log getLog() {
        return log;
    }


    /**
     * Recycle the input buffer. This should be called when closing the
     * connection.
     */
    @Override
    public void recycle() {
        super.recycle();
        socket = null;
    }

    // ------------------------------------------------------ Protected Methods

    @Override
    protected void init(SocketWrapper<NioChannel> socketWrapper,
            AbstractEndpoint<NioChannel> endpoint) throws IOException {

        socket = socketWrapper.getSocket();
        if (socket == null) {
            // Socket has been closed in another thread
            throw new IOException(sm.getString("iib.socketClosed"));
        }
        socketReadBufferSize =
            socket.getBufHandler().getReadBuffer().capacity();

        int bufLength = headerBufferSize + socketReadBufferSize;
        if (buf == null || buf.length < bufLength) {
            buf = new byte[bufLength];
        }

        pool = ((NioEndpoint)endpoint).getSelectorPool();
    }


    @Override
    protected boolean fill(boolean block) throws IOException, EOFException {
        if (parsingHeader) {
            if (lastValid > headerBufferSize) {
                throw new IllegalArgumentException
                    (sm.getString("iib.requestheadertoolarge.error"));
            }
        } else {
            lastValid = pos = end;
        }
        int nRead = 0;
        ByteBuffer readBuffer = socket.getBufHandler().getReadBuffer();
        readBuffer.clear();
        if ( block ) {
            Selector selector = null;
            try {
                selector = pool.get();
            } catch ( IOException x ) {
                // Ignore
            }
            try {
                NioEndpoint.KeyAttachment att =
                        (NioEndpoint.KeyAttachment) socket.getAttachment(false);
                if (att == null) {
                    throw new IOException("Key must be cancelled.");
                }
                nRead = pool.read(readBuffer,
                        socket, selector,
                        socket.getIOChannel().socket().getSoTimeout());
            } catch ( EOFException eof ) {
                nRead = -1;
            } finally {
                if ( selector != null ) pool.put(selector);
            }
        } else {
            nRead = socket.read(readBuffer);
        }
        if (nRead > 0) {
            readBuffer.flip();
            readBuffer.limit(nRead);
            expand(nRead + pos);
            readBuffer.get(buf, pos, nRead);
            lastValid = pos + nRead;
        } else if (nRead == -1) {
            //return false;
            throw new EOFException(sm.getString("iib.eof.error"));
        }
        return nRead > 0;
    }


    // ------------------------------------- InputStreamInputBuffer Inner Class


    /**
     * This class is an input buffer which will read its data from an input
     * stream.
     */
    protected class SocketInputBuffer
        implements InputBuffer {


        /**
         * Read bytes into the specified chunk.
         */
        @Override
        public int doRead(ByteChunk chunk, Request req )
            throws IOException {

            if (pos >= lastValid) {
                if (!fill(true)) //read body, must be blocking, as the thread is inside the app
                    return -1;
            }

            int length = lastValid - pos;
            chunk.setBytes(buf, pos, length);
            pos = lastValid;

            return (length);
        }
    }
}
