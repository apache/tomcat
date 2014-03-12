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
import org.apache.tomcat.util.net.SocketWrapper;

public class Nio2ServletInputStream extends AbstractServletInputStream {

    private final SocketWrapper<Nio2Channel> wrapper;
    private final Nio2Channel channel;
    private final CompletionHandler<Integer, SocketWrapper<Nio2Channel>> completionHandler;
    private boolean flipped = false;
    private volatile boolean readPending = false;

    public Nio2ServletInputStream(SocketWrapper<Nio2Channel> wrapper) {
        this.wrapper = wrapper;
        this.channel = wrapper.getSocket();
        this.completionHandler = new CompletionHandler<Integer, SocketWrapper<Nio2Channel>>() {
            @Override
            public void completed(Integer nBytes, SocketWrapper<Nio2Channel> attachment) {
                synchronized (completionHandler) {
                    if (nBytes.intValue() < 0) {
                        failed(new ClosedChannelException(), attachment);
                        return;
                    }
                    readPending = false;
                }
                if (nBytes.intValue() > 0) {
                    if (!Nio2Endpoint.isInline()) {
                        try {
                            onDataAvailable();
                        } catch (IOException e) {
                            failed(e, attachment);
                        }
                    }
                } else {
                    try {
                        onAllDataRead();
                    } catch (IOException e) {
                        failed(e, attachment);
                    }
                }
            }
            @Override
            public void failed(Throwable exc, SocketWrapper<Nio2Channel> attachment) {
                attachment.setError(true);
                readPending = false;
                if (exc instanceof AsynchronousCloseException) {
                    // If already closed, don't call onError and close again 
                    return;
                }
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
    protected boolean doIsReady() throws IOException {
        synchronized (completionHandler) {
            if (readPending) {
                return false;
            }
            ByteBuffer readBuffer = channel.getBufHandler().getReadBuffer();
            if (!flipped) {
                readBuffer.flip();
                flipped = true;
            }
            if (readBuffer.remaining() > 0) {
                return true;
            }

            readBuffer.clear();
            flipped = false;
            int nRead = fillReadBuffer(false);

            boolean isReady = nRead > 0;
            if (isReady) {
                if (!flipped) {
                    readBuffer.flip();
                    flipped = true;
                }
                try {
                    onDataAvailable();
                } catch (IOException e) {
                    onError(e);
                    try {
                        close();
                    } catch (IOException ioe) {
                        // Ignore
                    }
                }
            }
            return isReady;
        }
    }

    @Override
    protected int doRead(boolean block, byte[] b, int off, int len)
            throws IOException {

        synchronized (completionHandler) {
            if (readPending) {
                return 0;
            }

            ByteBuffer readBuffer = channel.getBufHandler().getReadBuffer();

            if (!flipped) {
                readBuffer.flip();
                flipped = true;
            }
            int remaining = readBuffer.remaining();
            // Is there enough data in the read buffer to satisfy this request?
            if (remaining >= len) {
                readBuffer.get(b, off, len);
                return len;
            }

            // Copy what data there is in the read buffer to the byte array
            int leftToWrite = len;
            int newOffset = off;
            if (remaining > 0) {
                readBuffer.get(b, off, remaining);
                leftToWrite -= remaining;
                newOffset += remaining;
            }

            // Fill the read buffer as best we can
            readBuffer.clear();
            flipped = false;
            int nRead = fillReadBuffer(block);

            // Full as much of the remaining byte array as possible with the data
            // that was just read
            if (nRead > 0) {
                if (!flipped) {
                    readBuffer.flip();
                    flipped = true;
                }
                if (nRead > leftToWrite) {
                    readBuffer.get(b, newOffset, leftToWrite);
                    leftToWrite = 0;
                } else {
                    readBuffer.get(b, newOffset, nRead);
                    leftToWrite -= nRead;
                }
            } else if (nRead == 0) {
                if (block) {
                    if (!flipped) {
                        readBuffer.flip();
                        flipped = true;
                    }
                }
            } else if (nRead == -1) {
                // TODO i18n
                throw new EOFException();
            }

            return len - leftToWrite;
        }
    }

    @Override
    protected void doClose() throws IOException {
        channel.close();
    }

    private int fillReadBuffer(boolean block) throws IOException {
        ByteBuffer readBuffer = channel.getBufHandler().getReadBuffer();
        int nRead = 0;
        if (block) {
            readPending = true;
            readBuffer.clear();
            flipped = false;
            try {
                nRead = channel.read(readBuffer)
                        .get(wrapper.getTimeout(), TimeUnit.MILLISECONDS).intValue();
                readPending = false;
            } catch (InterruptedException | ExecutionException
                    | TimeoutException e) {
                onError(e);
                throw new IOException(e);
            }
        } else {
            readPending = true;
            readBuffer.clear();
            flipped = false;
            Nio2Endpoint.startInline();
            channel.read(readBuffer,
                    wrapper.getTimeout(), TimeUnit.MILLISECONDS, wrapper, completionHandler);
            Nio2Endpoint.endInline();
            if (!readPending) {
                nRead = readBuffer.position();
            }
        }
        return nRead;
    }
}
