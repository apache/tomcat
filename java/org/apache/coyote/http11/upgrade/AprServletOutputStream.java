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
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.WriteLock;

import org.apache.tomcat.jni.Socket;
import org.apache.tomcat.jni.Status;
import org.apache.tomcat.util.net.AprEndpoint;
import org.apache.tomcat.util.net.SocketWrapper;

public class AprServletOutputStream extends AbstractServletOutputStream {

    private final AprEndpoint endpoint;
    private final SocketWrapper<Long> wrapper;
    private final long socket;
    private final Lock blockingStatusReadLock;
    private final WriteLock blockingStatusWriteLock;

    public AprServletOutputStream(SocketWrapper<Long> wrapper,
            AprEndpoint endpoint) {
        this.endpoint = endpoint;
        this.wrapper = wrapper;
        this.socket = wrapper.getSocket().longValue();
        ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
        this.blockingStatusReadLock = lock.readLock();
        this.blockingStatusWriteLock =lock.writeLock();
    }


    @Override
    protected int doWrite(boolean block, byte[] b, int off, int len)
            throws IOException {

        boolean writeDone = false;
        int result = 0;
        try {
            blockingStatusReadLock.lock();
            if (wrapper.getBlockingStatus() == block) {
                result = Socket.send(socket, b, off, len);
                writeDone = true;
            }
        } finally {
            blockingStatusReadLock.unlock();
        }

        if (!writeDone) {
            try {
                blockingStatusWriteLock.lock();
                wrapper.setBlockingStatus(block);
                // Set the current settings for this socket
                Socket.optSet(socket, Socket.APR_SO_NONBLOCK, (block ? -1 : 0));
                // Downgrade the lock
                try {
                    blockingStatusReadLock.lock();
                    blockingStatusWriteLock.unlock();
                    result = Socket.send(socket, b, off, len);
                } finally {
                    blockingStatusReadLock.unlock();
                }
            } finally {
                // Should have been released above but may not have been on some
                // exception paths
                if (blockingStatusWriteLock.isHeldByCurrentThread()) {
                    blockingStatusWriteLock.unlock();
                }
            }
        }

        if (result >= 0) {
            if (result < len) {
                endpoint.getPoller().add(socket, -1, false, true);
            }
            return result;
        }
        else if (-result == Status.EAGAIN) {
            endpoint.getPoller().add(socket, -1, false, true);
            return 0;
        }

        throw new IOException(sm.getString("apr.write.error",
                Integer.valueOf(-result)));

    }

    @Override
    protected void doFlush() throws IOException {
        // TODO Auto-generated method stub
    }


    @Override
    protected void doClose() throws IOException {
        // NO-OP
        // Let AbstractProcessor trigger the close
    }
}
