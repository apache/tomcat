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
package org.apache.coyote.http11.upgrade;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.CompletionHandler;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.tomcat.util.net.Nio2Channel;
import org.apache.tomcat.util.net.Nio2Endpoint;
import org.apache.tomcat.util.net.Nio2Endpoint.Nio2SocketWrapper;
import org.apache.tomcat.util.net.SocketWrapper;

public class Nio2ServletOutputStream extends AbstractServletOutputStream<Nio2Channel> {

    private final Nio2Channel channel;
    private final int maxWrite;
    private final CompletionHandler<Integer, SocketWrapper<Nio2Channel>> completionHandler;
    private volatile boolean writePending = false;

    public Nio2ServletOutputStream(SocketWrapper<Nio2Channel> socketWrapper) {
        super(socketWrapper);
        channel = socketWrapper.getSocket();
        maxWrite = channel.getBufHandler().getWriteBuffer().capacity();
        this.completionHandler = new CompletionHandler<Integer, SocketWrapper<Nio2Channel>>() {
            @Override
            public void completed(Integer nBytes, SocketWrapper<Nio2Channel> attachment) {
                synchronized (completionHandler) {
                    if (nBytes.intValue() < 0) {
                        failed(new ClosedChannelException(), attachment);
                        return;
                    }
                    writePending = false;
                }
                if (!Nio2Endpoint.isInline()) {
                    try {
                        onWritePossible();
                    } catch (IOException e) {
                        failed(e, attachment);
                    }
                }
            }
            @Override
            public void failed(Throwable exc, SocketWrapper<Nio2Channel> attachment) {
                attachment.setError(true);
                writePending = false;
                onError(exc);
                try {
                    close();
                } catch (IOException e) {
                    // Ignore
                }
            }
        };
    }

    @Override
    protected int doWrite(boolean block, byte[] b, int off, int len)
            throws IOException {
        int leftToWrite = len;
        int count = 0;
        int offset = off;

        while (leftToWrite > 0) {
            int writeThisLoop;
            int writtenThisLoop;

            if (leftToWrite > maxWrite) {
                writeThisLoop = maxWrite;
            } else {
                writeThisLoop = leftToWrite;
            }

            writtenThisLoop = doWriteInternal(block, b, offset, writeThisLoop);
            if (writtenThisLoop < 0) {
                throw new EOFException();
            }
            count += writtenThisLoop;
            if (!block && writePending) {
                // Prevent concurrent writes in non blocking mode,
                // leftover data has to be buffered
                return count;
            }
            offset += writtenThisLoop;
            leftToWrite -= writtenThisLoop;

            if (writtenThisLoop < writeThisLoop) {
                break;
            }
        }

        return count;
    }

    private int doWriteInternal(boolean block, byte[] b, int off, int len)
            throws IOException {
        ByteBuffer buffer = channel.getBufHandler().getWriteBuffer();
        int written = 0;
        long writeTimeout = ((Nio2SocketWrapper) socketWrapper).getWriteTimeout();
        if (block) {
            buffer.clear();
            buffer.put(b, off, len);
            buffer.flip();
            try {
                written = channel.write(buffer).get(writeTimeout, TimeUnit.MILLISECONDS).intValue();
            } catch (InterruptedException | ExecutionException
                    | TimeoutException e) {
                onError(e);
                throw new IOException(e);
            }
        } else {
            synchronized (completionHandler) {
                if (!writePending) {
                    buffer.clear();
                    buffer.put(b, off, len);
                    buffer.flip();
                    writePending = true;
                    Nio2Endpoint.startInline();
                    channel.write(buffer, writeTimeout, TimeUnit.MILLISECONDS, socketWrapper, completionHandler);
                    Nio2Endpoint.endInline();
                    written = len;
                }
            }
        }
        return written;
    }

    @Override
    protected void doFlush() throws IOException {
        long writeTimeout = ((Nio2SocketWrapper) socketWrapper).getWriteTimeout();
        try {
            if (!writePending) {
                channel.flush().get(writeTimeout, TimeUnit.MILLISECONDS);
            }
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            onError(e);
            throw new IOException(e);
        }
    }

    @Override
    protected void doClose() throws IOException {
        try {
            channel.close();
        } catch (AsynchronousCloseException e) {
            // Ignore
        }
    }
}
