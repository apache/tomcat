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
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.net.Nio2Channel;
import org.apache.tomcat.util.net.Nio2Endpoint;
import org.apache.tomcat.util.net.SocketWrapperBase;

/**
 * Processes AJP requests using NIO2.
 */
public class AjpNio2Processor extends AbstractAjpProcessor<Nio2Channel> {

    private static final Log log = LogFactory.getLog(AjpNio2Processor.class);
    @Override
    protected Log getLog() {
        return log;
    }

    /**
     * Flipped flag for read buffer.
     */
    protected boolean flipped = false;

    public AjpNio2Processor(int packetSize, Nio2Endpoint endpoint0) {
        super(packetSize, endpoint0);
        response.setOutputBuffer(new SocketOutputBuffer());
    }

    @Override
    public void recycle(boolean socketClosing) {
        super.recycle(socketClosing);
        flipped = false;
    }

    @Override
    protected void registerForEvent(boolean read, boolean write) {
        // Nothing to do here, the appropriate operations should
        // already be pending
    }


    @Override
    protected void setupSocket(SocketWrapperBase<Nio2Channel> socketWrapper)
            throws IOException {
        // NO-OP
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

        if (block) {
            if (!flipped) {
                readBuffer.flip();
                flipped = true;
            }
            if (readBuffer.remaining() > 0) {
                nRead = Math.min(n, readBuffer.remaining());
                readBuffer.get(buf, pos, nRead);
                if (readBuffer.remaining() == 0) {
                    readBuffer.clear();
                    flipped = false;
                }
            } else {
                readBuffer.clear();
                flipped = false;
                readBuffer.limit(n);
                try {
                    nRead = socketWrapper.getSocket().read(readBuffer)
                            .get(socketWrapper.getTimeout(), TimeUnit.MILLISECONDS).intValue();
                } catch (InterruptedException | ExecutionException
                        | TimeoutException e) {
                    throw new IOException(sm.getString("ajpprocessor.failedread"), e);
                }
                if (nRead > 0) {
                    if (!flipped) {
                        readBuffer.flip();
                        flipped = true;
                    }
                    nRead = Math.min(n, readBuffer.remaining());
                    readBuffer.get(buf, pos, nRead);
                    if (readBuffer.remaining() == 0) {
                        readBuffer.clear();
                        flipped = false;
                    }
                }
            }
        } else {
            if (!flipped) {
                readBuffer.flip();
                flipped = true;
            }
            if (readBuffer.remaining() > 0) {
                nRead = Math.min(n, readBuffer.remaining());
                readBuffer.get(buf, pos, nRead);
                if (readBuffer.remaining() == 0) {
                    readBuffer.clear();
                    flipped = false;
                }
            } else {
                readBuffer.clear();
                flipped = false;
                readBuffer.limit(n);
            }
        }
        return nRead;
    }
}
