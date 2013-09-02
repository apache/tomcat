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
import java.nio.channels.Selector;

import org.apache.coyote.ActionCode;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.net.NioChannel;
import org.apache.tomcat.util.net.NioEndpoint;
import org.apache.tomcat.util.net.NioEndpoint.KeyAttachment;
import org.apache.tomcat.util.net.NioSelectorPool;
import org.apache.tomcat.util.net.SocketStatus;
import org.apache.tomcat.util.net.SocketWrapper;


/**
 * Processes AJP requests using NIO.
 */
public class AjpNioProcessor extends AbstractAjpProcessor<NioChannel> {


    /**
     * Logger.
     */
    private static final Log log = LogFactory.getLog(AjpNioProcessor.class);
    @Override
    protected Log getLog() {
        return log;
    }

    // ----------------------------------------------------------- Constructors


    public AjpNioProcessor(int packetSize, NioEndpoint endpoint) {

        super(packetSize, endpoint);

        response.setOutputBuffer(new SocketOutputBuffer());

        pool = endpoint.getSelectorPool();
    }


    // ----------------------------------------------------- Instance Variables

    /**
     * Selector pool for the associated endpoint.
     */
    protected final NioSelectorPool pool;


    // ----------------------------------------------------- ActionHook Methods

    /**
     * Send an action to the connector.
     *
     * @param actionCode Type of the action
     * @param param Action parameter
     */
    @Override
    protected void actionInternal(ActionCode actionCode, Object param) {

        if (actionCode == ActionCode.ASYNC_COMPLETE) {
            if (asyncStateMachine.asyncComplete()) {
                ((NioEndpoint)endpoint).dispatchForEvent(
                        socketWrapper.getSocket(), SocketStatus.OPEN_READ, false);
            }
        } else if (actionCode == ActionCode.ASYNC_SETTIMEOUT) {
            if (param == null) return;
            long timeout = ((Long)param).longValue();
            final KeyAttachment ka =
                    (KeyAttachment)socketWrapper.getSocket().getAttachment(false);
            if (keepAliveTimeout > 0) {
                ka.setTimeout(timeout);
            }
        } else if (actionCode == ActionCode.ASYNC_DISPATCH) {
            if (asyncStateMachine.asyncDispatch()) {
                ((NioEndpoint)endpoint).dispatchForEvent(
                        socketWrapper.getSocket(), SocketStatus.OPEN_READ, true);            }
            }
        }


    // ------------------------------------------------------ Protected Methods

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
    protected void output(byte[] src, int offset, int length)
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
        } catch ( IOException x ) {
            //ignore
        }
        try {
            pool.write(writeBuffer, socketWrapper.getSocket(), selector,
                    writeTimeout, true);
        }finally {
            writeBuffer.clear();
            if ( selector != null ) pool.put(selector);
        }
    }


    /**
     * Read the specified amount of bytes, and place them in the input buffer.
     */
    protected int read(byte[] buf, int pos, int n, boolean blockFirstRead)
        throws IOException {

        int read = 0;
        int res = 0;
        boolean block = blockFirstRead;

        while (read < n) {
            res = readSocket(buf, read + pos, n, block);
            if (res > 0) {
                read += res;
            } else if (res == 0 && !block) {
                break;
            } else {
                throw new IOException(sm.getString("ajpprocessor.failedread"));
            }
            block = true;
        }
        return read;
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


    /**
     * Read an AJP message.
     *
     * @return <code>true</code> if a message was read, otherwise false
     *
     * @throws IOException any other failure, including incomplete reads
     */
    @Override
    protected boolean readMessage(AjpMessage message, boolean blockFirstRead)
        throws IOException {

        byte[] buf = message.getBuffer();
        int headerLength = message.getHeaderLength();

        int bytesRead = read(buf, 0, headerLength, blockFirstRead);

        if (bytesRead == 0) {
            return false;
        }

        int messageLength = message.processHeader(true);
        if (messageLength < 0) {
            // Invalid AJP header signature
            throw new IOException(sm.getString("ajpmessage.invalidLength",
                    Integer.valueOf(messageLength)));
        }
        else if (messageLength == 0) {
            // Zero length message.
            return true;
        }
        else {
            if (messageLength > buf.length) {
                // Message too long for the buffer
                // Need to trigger a 400 response
                throw new IllegalArgumentException(sm.getString(
                        "ajpprocessor.header.tooLong",
                        Integer.valueOf(messageLength),
                        Integer.valueOf(buf.length)));
            }
            read(buf, headerLength, messageLength, true);
            return true;
        }
    }


}
