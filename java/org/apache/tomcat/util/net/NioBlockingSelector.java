/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tomcat.util.net;

import java.io.EOFException;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.util.concurrent.TimeUnit;

import org.apache.tomcat.util.net.NioEndpoint.KeyAttachment;

public class NioBlockingSelector {
    public NioBlockingSelector() {
    }

    /**
     * Performs a blocking write using the bytebuffer for data to be written
     * If the <code>selector</code> parameter is null, then it will perform a busy write that could
     * take up a lot of CPU cycles.
     * @param buf ByteBuffer - the buffer containing the data, we will write as long as <code>(buf.hasRemaining()==true)</code>
     * @param socket SocketChannel - the socket to write data to
     * @param writeTimeout long - the timeout for this write operation in milliseconds, -1 means no timeout
     * @return int - returns the number of bytes written
     * @throws EOFException if write returns -1
     * @throws SocketTimeoutException if the write times out
     * @throws IOException if an IO Exception occurs in the underlying socket logic
     */
    public static int write(ByteBuffer buf, NioChannel socket, long writeTimeout) throws IOException {
        SelectionKey key = socket.getIOChannel().keyFor(socket.getPoller().getSelector());
        int written = 0;
        boolean timedout = false;
        int keycount = 1; //assume we can write
        long time = System.currentTimeMillis(); //start the timeout timer
        if (socket.getBufHandler().getWriteBuffer() != buf) {
            socket.getBufHandler().getWriteBuffer().put(buf);
            buf = socket.getBufHandler().getWriteBuffer();
        }
        try {
            while ( (!timedout) && buf.hasRemaining()) {
                if (keycount > 0) { //only write if we were registered for a write
                    int cnt = socket.write(buf); //write the data
                    if (cnt == -1)
                        throw new EOFException();
                    written += cnt;
                    if (cnt > 0) {
                        time = System.currentTimeMillis(); //reset our timeout timer
                        continue; //we successfully wrote, try again without a selector
                    }
                }
                if ( key == null ) throw new IOException("Key no longer registered");
                KeyAttachment att = (KeyAttachment) key.attachment();
                try {
                    if ( att.getLatch()==null || att.getLatch().getCount()==0) att.startLatch(1,SelectionKey.OP_WRITE);
                    //only register for write if a write has not yet been issued
                    if ( (att.interestOps() & SelectionKey.OP_WRITE) == 0) socket.getPoller().add(socket,SelectionKey.OP_WRITE);
                    att.awaitLatch(writeTimeout,TimeUnit.MILLISECONDS,SelectionKey.OP_WRITE);
                }catch (InterruptedException ignore) {
                    Thread.interrupted();
                }
                if ( att.getLatch()!=null && att.getLatch().getCount()> 0) {
                    //we got interrupted, but we haven't received notification from the poller.
                    keycount = 0;
                }else {
                    //latch countdown has happened
                    keycount = 1;
                    att.resetLatch();
                }

                if (writeTimeout > 0 && (keycount == 0))
                    timedout = (System.currentTimeMillis() - time) >= writeTimeout;
            } //while
            if (timedout) 
                throw new SocketTimeoutException();
        } finally {
            if (timedout && key != null) {
                cancelKey(socket, key);
            }
        }
        return written;
    }

    private static void cancelKey(final NioChannel socket, final SelectionKey key) {
        socket.getPoller().addEvent(
            new Runnable() {
            public void run() {
                key.cancel();
            }
        });
    }

    /**
     * Performs a blocking read using the bytebuffer for data to be read
     * If the <code>selector</code> parameter is null, then it will perform a busy read that could
     * take up a lot of CPU cycles.
     * @param buf ByteBuffer - the buffer containing the data, we will read as until we have read at least one byte or we timed out
     * @param socket SocketChannel - the socket to write data to
     * @param selector Selector - the selector to use for blocking, if null then a busy read will be initiated
     * @param readTimeout long - the timeout for this read operation in milliseconds, -1 means no timeout
     * @return int - returns the number of bytes read
     * @throws EOFException if read returns -1
     * @throws SocketTimeoutException if the read times out
     * @throws IOException if an IO Exception occurs in the underlying socket logic
     */
    public static int read(ByteBuffer buf, NioChannel socket, long readTimeout) throws IOException {
        final SelectionKey key = socket.getIOChannel().keyFor(socket.getPoller().getSelector());
        int read = 0;
        boolean timedout = false;
        int keycount = 1; //assume we can write
        long time = System.currentTimeMillis(); //start the timeout timer
        try {
            while ( (!timedout) && read == 0) {
                if (keycount > 0) { //only read if we were registered for a read
                    int cnt = socket.read(buf);
                    if (cnt == -1)
                        throw new EOFException();
                    read += cnt;
                    if (cnt > 0)
                        break;
                }
                KeyAttachment att = (KeyAttachment) key.attachment();
                try {
                    if ( att.getLatch()==null || att.getLatch().getCount()==0) att.startLatch(1,SelectionKey.OP_READ);
                    if ( att.interestOps() == 0) socket.getPoller().add(socket,SelectionKey.OP_READ);
                    att.awaitLatch(readTimeout,TimeUnit.MILLISECONDS, SelectionKey.OP_READ);
                }catch (InterruptedException ignore) {
                    Thread.interrupted();
                }
                if ( att.getLatch()!=null && att.getLatch().getCount()> 0) {
                    //we got interrupted, but we haven't received notification from the poller.
                    keycount = 0;
                }else {
                    //latch countdown has happened
                    keycount = 1;
                    att.resetLatch();
                }
                if (readTimeout > 0 && (keycount == 0))
                    timedout = (System.currentTimeMillis() - time) >= readTimeout;
            } //while
            if (timedout)
                throw new SocketTimeoutException();
        } finally {
            if (timedout && key != null) {
                cancelKey(socket,key);
            }
        }
        return read;
    }

}