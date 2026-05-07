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
package org.apache.catalina.tribes.io;

import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

import org.apache.catalina.tribes.ChannelMessage;
import org.apache.catalina.tribes.transport.Constants;
import org.apache.catalina.tribes.util.StringManager;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;


/**
 * The object reader object is an object used in conjunction with java.nio TCP messages. This object stores the message
 * bytes in a <code>XByteBuffer</code> until a full package has been received. This object uses an XByteBuffer which is
 * an extendable object buffer that also allows for message encoding and decoding.
 */
public class ObjectReader {

    private static final Log log = LogFactory.getLog(ObjectReader.class);
    /**
     * String manager for internationalization.
     */
    protected static final StringManager sm = StringManager.getManager(ObjectReader.class);

    private XByteBuffer buffer;

    /**
     * Timestamp of the last access.
     */
    protected long lastAccess = System.currentTimeMillis();

    /**
     * Whether this reader is currently being accessed.
     */
    protected boolean accessed = false;
    private volatile boolean cancelled;

    /**
     * Creates an ObjectReader with the specified packet size.
     *
     * @param packetSize The initial packet buffer size
     */
    public ObjectReader(int packetSize) {
        this.buffer = new XByteBuffer(packetSize, true);
    }

    /**
     * Creates an <code>ObjectReader</code> for a TCP NIO socket channel
     *
     * @param channel - the channel to be read.
     */
    public ObjectReader(SocketChannel channel) {
        this(channel.socket());
    }

    /**
     * Creates an <code>ObjectReader</code> for a TCP socket
     *
     * @param socket Socket
     */
    public ObjectReader(Socket socket) {
        try {
            this.buffer = new XByteBuffer(socket.getReceiveBufferSize(), true);
        } catch (IOException ioe) {
            // unable to get buffer size
            log.warn(sm.getString("objectReader.retrieveFailed.socketReceiverBufferSize",
                    Integer.toString(Constants.DEFAULT_CLUSTER_MSG_BUFFER_SIZE)), ioe);
            this.buffer = new XByteBuffer(Constants.DEFAULT_CLUSTER_MSG_BUFFER_SIZE, true);
        }
    }

    /**
     * Marks this reader as being accessed.
     */
    public synchronized void access() {
        this.accessed = true;
        this.lastAccess = System.currentTimeMillis();
    }

    /**
     * Marks this reader as no longer being accessed.
     */
    public synchronized void finish() {
        this.accessed = false;
        this.lastAccess = System.currentTimeMillis();
    }

    /**
     * Checks if this reader is currently being accessed.
     *
     * @return true if the reader is being accessed
     */
    public synchronized boolean isAccessed() {
        return this.accessed;
    }

    /**
     * Append new bytes to buffer.
     *
     * @see XByteBuffer#countPackages()
     *
     * @param data  new transfer buffer
     * @param len   length in buffer
     * @param count whether to return the count
     *
     * @return number of messages that was sent to callback (or -1 if count == false)
     */
    public int append(ByteBuffer data, int len, boolean count) {
        buffer.append(data, len);
        int pkgCnt = -1;
        if (count) {
            pkgCnt = buffer.countPackages();
        }
        return pkgCnt;
    }

    /**
     * Appends new bytes to the buffer.
     *
     * @param data The byte array
     * @param off The offset in the array
     * @param len The length of data
     * @param count Whether to count packages
     * @return Number of messages sent to callback, or -1 if count is false
     */
    public int append(byte[] data, int off, int len, boolean count) {
        buffer.append(data, off, len);
        int pkgCnt = -1;
        if (count) {
            pkgCnt = buffer.countPackages();
        }
        return pkgCnt;
    }

    /**
     * Send buffer to cluster listener (callback). Is message complete receiver send message to callback?
     *
     * @see org.apache.catalina.tribes.transport.ReceiverBase#messageDataReceived(ChannelMessage)
     * @see XByteBuffer#doesPackageExist()
     * @see XByteBuffer#extractPackage(boolean)
     *
     * @return array of received packages/messages
     */
    public ChannelMessage[] execute() {
        int pkgCnt = buffer.countPackages();
        ChannelMessage[] result = new ChannelMessage[pkgCnt];
        for (int i = 0; i < pkgCnt; i++) {
            ChannelMessage data = buffer.extractPackage(true);
            result[i] = data;
        }
        return result;
    }

    /**
     * Returns the current buffer size.
     *
     * @return The buffer length
     */
    public int bufferSize() {
        return buffer.getLength();
    }


    /**
     * Checks if there is a complete package available.
     *
     * @return true if a complete package exists
     */
    public boolean hasPackage() {
        return buffer.countPackages(true) > 0;
    }

    /**
     * Returns the number of packages that the reader has read
     *
     * @return int
     */
    public int count() {
        return buffer.countPackages();
    }

    /**
     * Closes this reader and releases the buffer.
     */
    public void close() {
        this.buffer = null;
    }

    /**
     * Returns the timestamp of the last access.
     *
     * @return The last access timestamp
     */
    public synchronized long getLastAccess() {
        return lastAccess;
    }

    /**
     * Checks if this reader has been cancelled.
     *
     * @return true if cancelled
     */
    public boolean isCancelled() {
        return cancelled;
    }

    /**
     * Sets the timestamp of the last access.
     *
     * @param lastAccess The last access timestamp
     */
    public synchronized void setLastAccess(long lastAccess) {
        this.lastAccess = lastAccess;
    }

    /**
     * Sets the cancelled state of this reader.
     *
     * @param cancelled The cancelled state
     */
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

}
