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
import java.nio.channels.CompletionHandler;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.net.Nio2Channel;
import org.apache.tomcat.util.net.Nio2Endpoint;
import org.apache.tomcat.util.net.SocketStatus;
import org.apache.tomcat.util.net.SocketWrapper;

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
     * The completion handler used for asynchronous write operations
     */
    protected CompletionHandler<Integer, SocketWrapper<Nio2Channel>> writeCompletionHandler;

    /**
     * Flipped flag for read buffer.
     */
    protected boolean flipped = false;

    /**
     * Write pending flag.
     */
    protected volatile boolean writePending = false;

    public AjpNio2Processor(int packetSize, Nio2Endpoint endpoint0) {
        super(packetSize, endpoint0);
        response.setOutputBuffer(new SocketOutputBuffer());
        this.writeCompletionHandler = new CompletionHandler<Integer, SocketWrapper<Nio2Channel>>() {
            @Override
            public void completed(Integer nBytes, SocketWrapper<Nio2Channel> attachment) {
                boolean notify = false;
                synchronized (writeCompletionHandler) {
                    if (nBytes.intValue() < 0) {
                        failed(new IOException(sm.getString("ajpprocessor.failedsend")), attachment);
                        return;
                    }
                    writePending = false;
                    if (!Nio2Endpoint.isInline()) {
                        notify = true;
                    }
                }
                if (notify) {
                    endpoint.processSocket(attachment, SocketStatus.OPEN_WRITE, false);
                }
            }
            @Override
            public void failed(Throwable exc, SocketWrapper<Nio2Channel> attachment) {
                attachment.setError(true);
                writePending = false;
                endpoint.processSocket(attachment, SocketStatus.DISCONNECT, true);
            }
        };
    }

    @Override
    public void recycle(boolean socketClosing) {
        super.recycle(socketClosing);
        writePending = false;
        flipped = false;
    }

    @Override
    protected void registerForEvent(boolean read, boolean write) {
        // Nothing to do here, the appropriate operations should
        // already be pending
    }

    @Override
    protected void resetTimeouts() {
        // The NIO connector uses the timeout configured on the wrapper in the
        // poller. Therefore, it needs to be reset once asycn processing has
        // finished.
        if (!getErrorState().isError() && socketWrapper != null &&
                asyncStateMachine.isAsyncDispatching()) {
            long soTimeout = endpoint.getSoTimeout();

            //reset the timeout
            if (keepAliveTimeout > 0) {
                socketWrapper.setTimeout(keepAliveTimeout);
            } else {
                socketWrapper.setTimeout(soTimeout);
            }
        }

    }


    @Override
    protected void setupSocket(SocketWrapper<Nio2Channel> socketWrapper)
            throws IOException {
        // NO-OP
    }


    @Override
    protected void setTimeout(SocketWrapper<Nio2Channel> socketWrapper,
            int timeout) throws IOException {
        socketWrapper.setTimeout(timeout);
    }


    @Override
    protected int output(byte[] src, int offset, int length, boolean block)
            throws IOException {

        if (socketWrapper == null || socketWrapper.getSocket() == null)
            return -1;

        ByteBuffer writeBuffer =
                socketWrapper.getSocket().getBufHandler().getWriteBuffer();

        int result = 0;
        if (block) {
            writeBuffer.clear();
            writeBuffer.put(src, offset, length);
            writeBuffer.flip();
            try {
                result = socketWrapper.getSocket().write(writeBuffer)
                        .get(socketWrapper.getTimeout(), TimeUnit.MILLISECONDS).intValue();
            } catch (InterruptedException | ExecutionException
                    | TimeoutException e) {
                throw new IOException(sm.getString("ajpprocessor.failedsend"), e);
            }
        } else {
            synchronized (writeCompletionHandler) {
                if (!writePending) {
                    writeBuffer.clear();
                    writeBuffer.put(src, offset, length);
                    writeBuffer.flip();
                    writePending = true;
                    Nio2Endpoint.startInline();
                    socketWrapper.getSocket().write(writeBuffer, socketWrapper.getTimeout(),
                            TimeUnit.MILLISECONDS, socketWrapper, writeCompletionHandler);
                    Nio2Endpoint.endInline();
                    result = length;
                }
            }
        }
        return result;
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
