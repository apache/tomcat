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
package org.apache.tomcat.websocket;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.CompletionHandler;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLException;

/**
 * This is a wrapper for a {@link java.nio.channels.AsynchronousSocketChannel} that limits the methods available thereby
 * simplifying the process of implementing SSL/TLS support since there are fewer methods to intercept.
 */
public interface AsyncChannelWrapper {

    /**
     * Reads data from the channel into the given buffer.
     *
     * @param dst the buffer into which data is to be read
     * @return a Future holding the number of bytes read
     */
    Future<Integer> read(ByteBuffer dst);

    /**
     * Reads data from the channel into the given buffer with a completion handler.
     *
     * @param dst the buffer into which data is to be read
     * @param attachment the object to attach to the task
     * @param handler the handler to use on completion
     * @param <B> the attachment type
     * @param <A> the attachment subtype
     */
    <B, A extends B> void read(ByteBuffer dst, A attachment, CompletionHandler<Integer,B> handler);

    /**
     * Writes data from the given buffer to the channel.
     *
     * @param src the buffer from which data is to be written
     * @return a Future holding the number of bytes written
     */
    Future<Integer> write(ByteBuffer src);

    /**
     * Writes data from the given buffers to the channel with a completion handler.
     *
     * @param srcs the buffers from which data is to be written
     * @param offset the offset within the buffer array
     * @param length the number of buffers to write
     * @param timeout the maximum time to wait
     * @param unit the time unit of the timeout
     * @param attachment the object to attach to the task
     * @param handler the handler to use on completion
     * @param <B> the attachment type
     * @param <A> the attachment subtype
     */
    <B, A extends B> void write(ByteBuffer[] srcs, int offset, int length, long timeout, TimeUnit unit, A attachment,
            CompletionHandler<Long,B> handler);

    /**
     * Closes the underlying channel.
     */
    void close();

    /**
     * Performs the SSL/TLS handshake.
     *
     * @return a Future that completes when the handshake is finished
     * @throws SSLException if an SSL error occurs
     */
    Future<Void> handshake() throws SSLException;

    /**
     * Returns the local address to which the channel is bound.
     *
     * @return the local socket address, or null if not bound
     * @throws IOException if an I/O error occurs
     */
    SocketAddress getLocalAddress() throws IOException;
}
