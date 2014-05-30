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
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.CompletionHandler;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.tomcat.util.net.AbstractEndpoint;
import org.apache.tomcat.util.net.Nio2Channel;
import org.apache.tomcat.util.net.Nio2Endpoint;
import org.apache.tomcat.util.net.SocketStatus;
import org.apache.tomcat.util.net.SocketWrapper;

public class Nio2ServletOutputStream extends AbstractServletOutputStream<Nio2Channel> {

    private final AbstractEndpoint<Nio2Channel> endpoint;
    private final Nio2Channel channel;
    private final int maxWrite;
    private final CompletionHandler<Integer, ByteBuffer> completionHandler;
    private final Semaphore writePending = new Semaphore(1);

    public Nio2ServletOutputStream(SocketWrapper<Nio2Channel> socketWrapper0,
            int asyncWriteBufferSize, AbstractEndpoint<Nio2Channel> endpoint0) {
        super(socketWrapper0, asyncWriteBufferSize);
        this.endpoint = endpoint0;
        channel = socketWrapper0.getSocket();
        maxWrite = channel.getBufHandler().getWriteBuffer().capacity();
        this.completionHandler = new CompletionHandler<Integer, ByteBuffer>() {
            @Override
            public void completed(Integer nBytes, ByteBuffer attachment) {
                if (nBytes.intValue() < 0) {
                    failed(new EOFException(), attachment);
                } else if (attachment.hasRemaining()) {
                    channel.write(attachment, socketWrapper.getTimeout(),
                            TimeUnit.MILLISECONDS, attachment, completionHandler);
                } else {
                    writePending.release();
                    if (!Nio2Endpoint.isInline()) {
                        endpoint.processSocket(socketWrapper, SocketStatus.OPEN_WRITE, false);
                    }
                }
            }
            @Override
            public void failed(Throwable exc, ByteBuffer attachment) {
                socketWrapper.setError(true);
                writePending.release();
                if (exc instanceof AsynchronousCloseException) {
                    // If already closed, don't call onError and close again
                    return;
                }
                onError(exc);
                endpoint.processSocket(socketWrapper, SocketStatus.ERROR, true);
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
            if (!block && writePending.availablePermits() == 0) {
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
        if (block) {
            buffer.clear();
            buffer.put(b, off, len);
            buffer.flip();
            try {
                written = channel.write(buffer).get(socketWrapper.getTimeout(), TimeUnit.MILLISECONDS).intValue();
            } catch (ExecutionException e) {
                if (e.getCause() instanceof IOException) {
                    onError(e.getCause());
                    throw (IOException) e.getCause();
                } else {
                    onError(e);
                    throw new IOException(e);
                }
            } catch (InterruptedException e) {
                onError(e);
                throw new IOException(e);
            } catch (TimeoutException e) {
                SocketTimeoutException ex = new SocketTimeoutException();
                onError(ex);
                throw ex;
            }
        } else {
            if (writePending.tryAcquire()) {
                buffer.clear();
                buffer.put(b, off, len);
                buffer.flip();
                Nio2Endpoint.startInline();
                channel.write(buffer, socketWrapper.getTimeout(), TimeUnit.MILLISECONDS, buffer, completionHandler);
                Nio2Endpoint.endInline();
                written = len;
            }
        }
        return written;
    }

    @Override
    protected void doFlush() throws IOException {
        try {
            // Block until a possible non blocking write is done
            if (writePending.tryAcquire(socketWrapper.getTimeout(), TimeUnit.MILLISECONDS)) {
                writePending.release();
                channel.flush().get(socketWrapper.getTimeout(), TimeUnit.MILLISECONDS);
            } else {
                throw new TimeoutException();
            }
        } catch (ExecutionException e) {
            if (e.getCause() instanceof IOException) {
                onError(e.getCause());
                throw (IOException) e.getCause();
            } else {
                onError(e);
                throw new IOException(e);
            }
        } catch (InterruptedException e) {
            onError(e);
            throw new IOException(e);
        } catch (TimeoutException e) {
            SocketTimeoutException ex = new SocketTimeoutException();
            onError(ex);
            throw ex;
        }
    }

    @Override
    protected void doClose() throws IOException {
        channel.close(true);
    }

}
