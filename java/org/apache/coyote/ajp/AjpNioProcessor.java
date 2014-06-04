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
package org.apache.coyote.ajp;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.net.NioChannel;
import org.apache.tomcat.util.net.NioEndpoint;
import org.apache.tomcat.util.net.NioSelectorPool;
import org.apache.tomcat.util.net.SocketWrapper;

/**
 * Processes AJP requests using NIO.
 */
public class AjpNioProcessor extends AbstractAjpProcessor<NioChannel> {

    private static final Log log = LogFactory.getLog(AjpNioProcessor.class);
    @Override
    protected Log getLog() {
        return log;
    }


    public AjpNioProcessor(int packetSize, NioEndpoint endpoint) {

        super(packetSize, endpoint);

        response.setOutputBuffer(new SocketOutputBuffer());

        pool = endpoint.getSelectorPool();
    }


    /**
     * Selector pool for the associated endpoint.
     */
    protected final NioSelectorPool pool;


    @Override
    protected void registerForEvent(boolean read, boolean write) {
        final NioChannel socket = socketWrapper.getSocket();
        final NioEndpoint.KeyAttachment attach =
                (NioEndpoint.KeyAttachment) socket.getAttachment(false);
        if (attach == null) {
            return;
        }
        SelectionKey key = socket.getIOChannel().keyFor(socket.getPoller().getSelector());
        if (read) {
            attach.interestOps(attach.interestOps() | SelectionKey.OP_READ);
            key.interestOps(key.interestOps() | SelectionKey.OP_READ);
        }
        if (write) {
            attach.interestOps(attach.interestOps() | SelectionKey.OP_WRITE);
            key.interestOps(key.interestOps() | SelectionKey.OP_READ);
        }
    }


    @Override
    protected void resetTimeouts() {
        // The NIO connector uses the timeout configured on the wrapper in the
        // poller. Therefore, it needs to be reset once asycn processing has
        // finished.
        final NioEndpoint.KeyAttachment attach =
                (NioEndpoint.KeyAttachment)socketWrapper.getSocket().getAttachment(false);
        if (!getErrorState().isError() && attach != null &&
                asyncStateMachine.isAsyncDispatching()) {
            long soTimeout = endpoint.getSoTimeout();

            //reset the timeout
            if (keepAliveTimeout > 0) {
                attach.setTimeout(keepAliveTimeout);
            } else {
                attach.setTimeout(soTimeout);
            }
        }

    }


    @Override
    protected void setupSocket(SocketWrapper<NioChannel> socketWrapper)
            throws IOException {
        // NO-OP
    }


    @Override
    protected void setTimeout(SocketWrapper<NioChannel> socketWrapper,
            int timeout) throws IOException {
        socketWrapper.setTimeout(timeout);
    }


    @Override
    protected int output(byte[] src, int offset, int length, boolean block)
            throws IOException {

        NioEndpoint.KeyAttachment att =
                (NioEndpoint.KeyAttachment) socketWrapper.getSocket().getAttachment(false);
        if ( att == null ) throw new IOException("Key must be cancelled");

        ByteBuffer writeBuffer =
                socketWrapper.getSocket().getBufHandler().getWriteBuffer();

        writeBuffer.put(src, offset, length);

        writeBuffer.flip();

        long writeTimeout = att.getWriteTimeout();
        Selector selector = null;
        try {
            selector = pool.get();
        } catch (IOException x) {
            //ignore
        }
        try {
            return pool.write(writeBuffer, socketWrapper.getSocket(), selector,
                    writeTimeout, block);
        } finally {
            writeBuffer.clear();
            if (selector != null) {
                pool.put(selector);
            }
        }
    }


    @Override
    protected boolean read(byte[] buf, int pos, int n, boolean blockFirstRead)
        throws IOException {

        int read = 0;
        int res = 0;
        boolean block = blockFirstRead;

        while (read < n) {
            res = readSocket(buf, read + pos, n - read, block);
            if (res > 0) {
                read += res;
            } else if (res == 0 && !block) {
                return false;
            } else {
                throw new IOException(sm.getString("ajpprocessor.failedread"));
            }
            block = true;
        }
        return true;
    }


    private int readSocket(byte[] buf, int pos, int n, boolean block)
            throws IOException {
        int nRead = 0;
        ByteBuffer readBuffer =
                socketWrapper.getSocket().getBufHandler().getReadBuffer();
        readBuffer.clear();
        readBuffer.limit(n);
        if ( block ) {
            Selector selector = null;
            try {
                selector = pool.get();
            } catch ( IOException x ) {
                // Ignore
            }
            try {
                NioEndpoint.KeyAttachment att =
                        (NioEndpoint.KeyAttachment) socketWrapper.getSocket().getAttachment(false);
                if ( att == null ) throw new IOException("Key must be cancelled.");
                nRead = pool.read(readBuffer, socketWrapper.getSocket(),
                        selector, att.getTimeout());
            } catch ( EOFException eof ) {
                nRead = -1;
            } finally {
                if ( selector != null ) pool.put(selector);
            }
        } else {
            nRead = socketWrapper.getSocket().read(readBuffer);
        }
        if (nRead > 0) {
            readBuffer.flip();
            readBuffer.limit(nRead);
            readBuffer.get(buf, pos, nRead);
            return nRead;
        } else if (nRead == -1) {
            //return false;
            throw new EOFException(sm.getString("iib.eof.error"));
        } else {
            return 0;
        }
    }
}
