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
package org.apache.catalina.tribes.transport.nio;


/**
 * MBean interface for NIO receiver management.
 */
public interface NioReceiverMBean {

    // Receiver Attributes
    /**
     * Returns the bind address.
     *
     * @return the address string
     */
    String getAddress();

    /**
     * Returns whether direct buffers are used.
     *
     * @return {@code true} if direct buffers are used
     */
    boolean getDirect();

    /**
     * Returns the TCP listen port.
     *
     * @return the TCP port
     */
    int getPort();

    /**
     * Returns the auto-bind mode.
     *
     * @return the auto-bind mode
     */
    int getAutoBind();

    /**
     * Returns the secure port.
     *
     * @return the secure port
     */
    int getSecurePort();

    /**
     * Returns the UDP port.
     *
     * @return the UDP port
     */
    int getUdpPort();

    /**
     * Returns the selector timeout.
     *
     * @return the selector timeout in milliseconds
     */
    long getSelectorTimeout();

    /**
     * Returns the maximum number of threads.
     *
     * @return the max thread count
     */
    int getMaxThreads();

    /**
     * Returns the minimum number of threads.
     *
     * @return the min thread count
     */
    int getMinThreads();

    /**
     * Returns the maximum idle time.
     *
     * @return the max idle time in milliseconds
     */
    long getMaxIdleTime();

    /**
     * Returns whether out-of-band inline is enabled.
     *
     * @return {@code true} if OOB inline is enabled
     */
    boolean getOoBInline();

    /**
     * Returns the receive buffer size.
     *
     * @return the receive buffer size in bytes
     */
    int getRxBufSize();

    /**
     * Returns the transmit buffer size.
     *
     * @return the transmit buffer size in bytes
     */
    int getTxBufSize();

    /**
     * Returns the UDP receive buffer size.
     *
     * @return the UDP receive buffer size in bytes
     */
    int getUdpRxBufSize();

    /**
     * Returns the UDP transmit buffer size.
     *
     * @return the UDP transmit buffer size in bytes
     */
    int getUdpTxBufSize();

    /**
     * Returns whether SO_KEEPALIVE is enabled.
     *
     * @return {@code true} if keep-alive is enabled
     */
    boolean getSoKeepAlive();

    /**
     * Returns whether SO_LINGER is enabled.
     *
     * @return {@code true} if SO_LINGER is enabled
     */
    boolean getSoLingerOn();

    /**
     * Returns the SO_LINGER timeout.
     *
     * @return the SO_LINGER timeout in seconds
     */
    int getSoLingerTime();

    /**
     * Returns whether SO_REUSEADDR is enabled.
     *
     * @return {@code true} if SO_REUSEADDR is enabled
     */
    boolean getSoReuseAddress();

    /**
     * Returns whether TCP_NODELAY is enabled.
     *
     * @return {@code true} if TCP_NODELAY is enabled
     */
    boolean getTcpNoDelay();

    /**
     * Returns the socket timeout.
     *
     * @return the socket timeout in milliseconds
     */
    int getTimeout();

    /**
     * Returns whether the buffer pool is used.
     *
     * @return {@code true} if the buffer pool is used
     */
    boolean getUseBufferPool();

    /**
     * Returns whether the receiver is currently listening.
     *
     * @return {@code true} if listening
     */
    boolean isListening();

    // pool stats
    /**
     * Returns the current pool size.
     *
     * @return the pool size
     */
    int getPoolSize();

    /**
     * Returns the number of actively running tasks.
     *
     * @return the active task count
     */
    int getActiveCount();

    /**
     * Returns the total number of tasks submitted.
     *
     * @return the total task count
     */
    long getTaskCount();

    /**
     * Returns the total number of completed tasks.
     *
     * @return the completed task count
     */
    long getCompletedTaskCount();

}
