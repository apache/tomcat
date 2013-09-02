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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

import org.apache.coyote.ActionCode;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.net.JIoEndpoint;
import org.apache.tomcat.util.net.SocketStatus;
import org.apache.tomcat.util.net.SocketWrapper;

/**
 * Processes AJP requests.
 *
 * @author Remy Maucherat
 * @author Henri Gomez
 * @author Dan Milstein
 * @author Keith Wannamaker
 * @author Kevin Seguin
 * @author Costin Manolache
 * @author Bill Barker
 */
public class AjpProcessor extends AbstractAjpProcessor<Socket> {

    private static final Log log = LogFactory.getLog(AjpProcessor.class);
    @Override
    protected Log getLog() {
        return log;
    }


    public AjpProcessor(int packetSize, JIoEndpoint endpoint) {

        super(packetSize, endpoint);

        response.setOutputBuffer(new SocketOutputBuffer());
    }


    protected InputStream input;

    protected OutputStream output;


    @Override
    public void recycle(boolean socketClosing) {
        super.recycle(socketClosing);
        if (socketClosing) {
            input = null;
            output = null;
        }
    }


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
                ((JIoEndpoint)endpoint).processSocketAsync(this.socketWrapper,
                        SocketStatus.OPEN_READ);
            }
        } else if (actionCode == ActionCode.ASYNC_SETTIMEOUT) {
            if (param == null) return;
            long timeout = ((Long)param).longValue();
            // if we are not piggy backing on a worker thread, set the timeout
            socketWrapper.setTimeout(timeout);
        } else if (actionCode == ActionCode.ASYNC_DISPATCH) {
            if (asyncStateMachine.asyncDispatch()) {
                ((JIoEndpoint)endpoint).processSocketAsync(this.socketWrapper,
                        SocketStatus.OPEN_READ);
            }
        }
    }


    @Override
    protected void setupSocket(SocketWrapper<Socket> socketWrapper)
            throws IOException {
        input = socketWrapper.getSocket().getInputStream();
        output = socketWrapper.getSocket().getOutputStream();
    }


    @Override
    protected void setTimeout(SocketWrapper<Socket> socketWrapper,
            int timeout) throws IOException {
        socketWrapper.getSocket().setSoTimeout(timeout);
    }


    @Override
    protected void output(byte[] src, int offset, int length)
            throws IOException {
        output.write(src, offset, length);
    }


    /**
     * Read at least the specified amount of bytes, and place them
     * in the input buffer.
     *
     * @param buf   Buffer to read data into
     * @param pos   Start position
     * @param n     Number of bytes to read
     * @param blockFirstRead    Should the first read block?
     *
     * @return If blockFirstRead is false, the connector supports non-blocking
     *         IO and the first read does not return any data, this method
     *         reads no data into the buffer returns false. Otherwise, blocking
     *         reads are used read the specified number of bytes into the
     *         buffer.
     * @throws IOException
     */
    protected boolean read(byte[] buf, int pos, int n, boolean blockFirstRead)
        throws IOException {

        int read = 0;
        int res = 0;
        while (read < n) {
            res = input.read(buf, read + pos, n - read);
            if (res > 0) {
                read += res;
            } else {
                throw new IOException(sm.getString("ajpprocessor.failedread"));
            }
        }

        return true;
    }


    @Override
    protected boolean readMessage(AjpMessage message, boolean block)
        throws IOException {

        byte[] buf = message.getBuffer();
        int headerLength = message.getHeaderLength();

        if (!read(buf, 0, headerLength, block)) {
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
