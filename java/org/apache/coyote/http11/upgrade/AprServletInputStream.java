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
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock.WriteLock;

import org.apache.tomcat.jni.OS;
import org.apache.tomcat.jni.Socket;
import org.apache.tomcat.jni.Status;
import org.apache.tomcat.util.net.SocketWrapper;

public class AprServletInputStream extends AbstractServletInputStream {

    private final SocketWrapper<Long> wrapper;
    private final long socket;
    private volatile boolean eagain = false;
    private volatile boolean closed = false;


    public AprServletInputStream(SocketWrapper<Long> wrapper) {
        this.wrapper = wrapper;
        this.socket = wrapper.getSocket().longValue();
    }


    @Override
    protected int doRead(boolean block, byte[] b, int off, int len)
            throws IOException {

        Lock readLock = wrapper.getBlockingStatusReadLock();
        WriteLock writeLock = wrapper.getBlockingStatusWriteLock();

        boolean readDone = false;
        int result = 0;
        try {
            readLock.lock();
            if (wrapper.getBlockingStatus() == block) {
                if (closed) {
                    throw new IOException(sm.getString("apr.closed", Long.valueOf(socket)));
                }
                result = Socket.recv(socket, b, off, len);
                readDone = true;
            }
        } finally {
            readLock.unlock();
        }

        if (!readDone) {
            try {
                writeLock.lock();
                wrapper.setBlockingStatus(block);
                // Set the current settings for this socket
                Socket.optSet(socket, Socket.APR_SO_NONBLOCK, (block ? 0 : 1));
                // Downgrade the lock
                try {
                    readLock.lock();
                    writeLock.unlock();
                    if (closed) {
                        throw new IOException(sm.getString("apr.closed", Long.valueOf(socket)));
                    }
                    result = Socket.recv(socket, b, off, len);
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

        if (result > 0) {
            eagain = false;
            return result;
        } else if (-result == Status.EAGAIN) {
            eagain = true;
            return 0;
        } else if ((OS.IS_WIN32 || OS.IS_WIN64) &&
                (-result == Status.APR_OS_START_SYSERR + 10053)) {
            // 10053 on Windows is connection aborted
            throw new EOFException(sm.getString("apr.clientAbort"));
        } else {
            throw new IOException(sm.getString("apr.read.error",
                    Integer.valueOf(-result), Long.valueOf(socket)));
        }
    }


    @Override
    protected boolean doIsReady() {
        return !eagain;
    }


    @Override
    protected void doClose() throws IOException {
        closed = true;
        // AbstractProcessor needs to trigger the close as multiple closes for
        // APR/native sockets will cause problems.
    }
}
