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

import java.io.IOException;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock.WriteLock;

import org.apache.tomcat.jni.Socket;
import org.apache.tomcat.jni.Status;
import org.apache.tomcat.util.net.AprEndpoint;
import org.apache.tomcat.util.net.SocketWrapper;

public class AprServletOutputStream extends AbstractServletOutputStream {

    private final AprEndpoint endpoint;
    private final SocketWrapper<Long> wrapper;
    private final long socket;
    private volatile boolean closed = false;

    public AprServletOutputStream(SocketWrapper<Long> wrapper,
            AprEndpoint endpoint) {
        this.endpoint = endpoint;
        this.wrapper = wrapper;
        this.socket = wrapper.getSocket().longValue();
    }


    @Override
    protected int doWrite(boolean block, byte[] b, int off, int len)
            throws IOException {

        Lock readLock = wrapper.getBlockingStatusReadLock();
        WriteLock writeLock = wrapper.getBlockingStatusWriteLock();

        try {
            readLock.lock();
            if (wrapper.getBlockingStatus() == block) {
                if (closed) {
                    throw new IOException(sm.getString("apr.closed"));
                }
                return doWriteInternal(b, off, len);
            }
        } finally {
            readLock.unlock();
        }

        try {
            writeLock.lock();
            // Set the current settings for this socket
            wrapper.setBlockingStatus(block);
            if (block) {
                Socket.timeoutSet(socket, endpoint.getSoTimeout() * 1000);
            } else {
                Socket.timeoutSet(socket, 0);
            }

            // Downgrade the lock
            try {
                readLock.lock();
                writeLock.unlock();
                if (closed) {
                    throw new IOException(sm.getString("apr.closed"));
                }
                return doWriteInternal(b, off, len);
            } finally {
                readLock.unlock();
            }
        } finally {
            // Should have been released above but may not have been on some
            // exception paths
            if (writeLock.isHeldByCurrentThread()) {
                writeLock.unlock();
            }
        }
    }


    private int doWriteInternal(byte[] b, int off, int len) throws IOException {

        int start = off;
        int left = len;
        int written;

        do {
            written = Socket.send(socket, b, start, left);
            if (Status.APR_STATUS_IS_EAGAIN(-written)) {
                endpoint.getPoller().add(socket, -1, false, true);
                written = 0;
            } else if (written < 0) {
                throw new IOException(sm.getString("apr.write.error",
                        Integer.valueOf(-written)));
            }
            start += written;
            left -= written;
        } while (written > 0 && left > 0);

        if (left > 0) {
            endpoint.getPoller().add(socket, -1, false, true);
        }
        return len - left;
    }


    @Override
    protected void doFlush() throws IOException {
        // TODO Auto-generated method stub
    }


    @Override
    protected void doClose() throws IOException {
        closed = true;
        // AbstractProcessor needs to trigger the close as multiple closes for
        // APR/native sockets will cause problems.
    }
}
