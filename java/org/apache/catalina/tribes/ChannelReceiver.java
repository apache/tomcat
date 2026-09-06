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
package org.apache.catalina.tribes;

import java.util.concurrent.TimeUnit;

/**
 * Channel receiver interface. Receives messages from other nodes in the cluster.
 */
public interface ChannelReceiver extends Heartbeat {

    /**
     * Default timeout in milliseconds for waitForReady().
     */
    long DEFAULT_READY_TIMEOUT_MS = 5000;

    /**
     * Start the channel receiver.
     *
     * @throws java.io.IOException if an IO error occurs
     */
    void start() throws java.io.IOException;

    /**
     * Stop the channel receiver.
     */
    void stop();

    /**
     * Wait until the receiver is ready to accept connections, or the timeout expires.
     * <p>
     * The default implementation returns immediately, preserving backward compatibility
     * for receivers that do not implement readiness signaling. Implementations that
     * start background listener threads should override this method to block until
     * the listener thread has entered its accept/select loop.
     *
     * @param timeout the maximum time to wait
     * @param unit the time unit of the timeout argument
     * @return {@code true} if the receiver is ready; {@code false} if the timeout elapsed
     * @throws InterruptedException if the current thread is interrupted while waiting
     */
    default boolean waitForReady(long timeout, TimeUnit unit) throws InterruptedException {
        return true;
    }

    /**
     * Return the host that the receiver listens on.
     *
     * @return the host name
     */
    String getHost();

    /**
     * Return the port that the receiver listens on.
     *
     * @return the port number
     */
    int getPort();

    /**
     * Return the secure port that the receiver listens on.
     *
     * @return the secure port number
     */
    int getSecurePort();

    /**
     * Return the UDP port that the receiver listens on.
     *
     * @return the UDP port number
     */
    int getUdpPort();

    /**
     * Set the message listener.
     *
     * @param listener the message listener
     */
    void setMessageListener(MessageListener listener);

    /**
     * Return the message listener.
     *
     * @return the message listener
     */
    MessageListener getMessageListener();

    /**
     * Return the associated channel.
     *
     * @return the channel
     */
    Channel getChannel();

    /**
     * Set the associated channel.
     *
     * @param channel the channel
     */
    void setChannel(Channel channel);
}
