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

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.net.JIoEndpoint;
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


    @Override
    protected void registerForEvent(boolean read, boolean write) {
        // NO-OP for BIO
    }

    @Override
    protected void resetTimeouts() {
        // NO-OP. The AJP BIO connector only uses the timeout value on the
        //        SocketWrapper for async timeouts.
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
    protected int output(byte[] src, int offset, int length, boolean block)
            throws IOException {
        output.write(src, offset, length);
        return length;
    }


    @Override
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
}
