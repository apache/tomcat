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
 * MBean interface for monitoring a PooledParallelSender.
 */
public interface PooledParallelSenderMBean {

    // Transport Attributes
    /**
     * Returns the receive buffer size.
     * @return the receive buffer size
     */
    int getRxBufSize();

    /**
     * Returns the transmit buffer size.
     * @return the transmit buffer size
     */
    int getTxBufSize();

    /**
     * Returns the UDP receive buffer size.
     * @return the UDP receive buffer size
     */
    int getUdpRxBufSize();

    /**
     * Returns the UDP transmit buffer size.
     * @return the UDP transmit buffer size
     */
    int getUdpTxBufSize();

    /**
     * Returns whether direct buffers are used.
     * @return true if direct buffers are used
     */
    boolean getDirectBuffer();

    /**
     * Returns the number of keep-alive messages before a connection check.
     * @return the keep-alive count
     */
    int getKeepAliveCount();

    /**
     * Returns the keep-alive interval in milliseconds.
     * @return the keep-alive time
     */
    long getKeepAliveTime();

    /**
     * Returns the socket timeout in milliseconds.
     * @return the timeout
     */
    long getTimeout();

    /**
     * Returns the maximum number of retry attempts.
     * @return the maximum retry attempts
     */
    int getMaxRetryAttempts();

    /**
     * Returns whether out-of-band data is processed inline.
     * @return true if OOB data is processed inline
     */
    boolean getOoBInline();

    /**
     * Returns whether SO_KEEPALIVE is enabled.
     * @return true if SO_KEEPALIVE is enabled
     */
    boolean getSoKeepAlive();

    /**
     * Returns whether SO_LINGER is enabled.
     * @return true if SO_LINGER is enabled
     */
    boolean getSoLingerOn();

    /**
     * Returns the SO_LINGER timeout value.
     * @return the SO_LINGER time
     */
    int getSoLingerTime();

    /**
     * Returns whether SO_REUSEADDR is enabled.
     * @return true if SO_REUSEADDR is enabled
     */
    boolean getSoReuseAddress();

    /**
     * Returns the IP traffic class value.
     * @return the traffic class
     */
    int getSoTrafficClass();

    /**
     * Returns whether TCP_NODELAY is enabled.
     * @return true if TCP_NODELAY is enabled
     */
    boolean getTcpNoDelay();

    /**
     * Returns whether an exception is thrown on failed acknowledgement.
     * @return true if exceptions are thrown on failed ack
     */
    boolean getThrowOnFailedAck();

    // PooledSender Attributes
    /**
     * Returns the size of the sender pool.
     * @return the pool size
     */
    int getPoolSize();

    /**
     * Returns the maximum wait time in milliseconds for a sender from the pool.
     * @return the maximum wait time
     */
    long getMaxWait();

    // Operation
    /**
     * Returns whether the sender is currently connected.
     * @return true if connected
     */
    boolean isConnected();

    /**
     * Returns the number of senders currently available in the pool.
     * @return the number of senders in the pool
     */
    int getInPoolSize();

    /**
     * Returns the number of senders currently in use.
     * @return the number of senders in use
     */
    int getInUsePoolSize();

}