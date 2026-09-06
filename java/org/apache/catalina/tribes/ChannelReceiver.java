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

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * The <code>ChannelReceiver</code> interface is the data receiver component at the bottom layer, the IO layer (for
 * layers see the {@link Channel} interface). This class may optionally implement a thread pool for parallel processing
 * of incoming messages.
 */
public interface ChannelReceiver extends Heartbeat {
    int MAX_UDP_SIZE = 65535;

    /**
     * Default timeout (in milliseconds) for {@link #waitForReady(long, TimeUnit)}.
     */
    long DEFAULT_READY_TIMEOUT_MS = 5000;

    /**
     * Start listening for incoming messages on the host/port
     *
     * @throws IOException Listen failed
     */
    void start() throws IOException;

    /**
     * Stop listening for messages
     */
    void stop();

    /**
     * String representation of the IPv4 or IPv6 address that this host is listening to.
     *
     * @return the host that this receiver is listening to
     */
    String getHost();


    /**
     * Returns the listening port
     *
     * @return port
     */
    int getPort();

    /**
     * Returns the secure listening port
     *
     * @return port, -1 if a secure port is not activated
     */
    int getSecurePort();

    /**
     * Returns the UDP port
     *
     * @return port, -1 if the UDP port is not activated.
     */
    int getUdpPort();

    /**
     * Sets the message listener to receive notification of incoming
     *
     * @param listener MessageListener
     *
     * @see MessageListener
     */
    void setMessageListener(MessageListener listener);

    /**
     * Returns the message listener that is associated with this receiver
     *
     * @return MessageListener
     *
     * @see MessageListener
     */
    MessageListener getMessageListener();

    /**
     * Return the channel that is related to this ChannelReceiver
     *
     * @return Channel
     */
    Channel getChannel();

    /**
     * Set the channel that is related to this ChannelReceiver
     *
     * @param channel The channel
     */
    void setChannel(Channel channel);

    /**
     * Wait for the receiver to become fully ready (i.e. the listener thread has entered
     * the accept/select loop and is prepared to receive connections).
     * <p>
     * This method addresses a race condition that exists between {@link #start()} returning
     * and the receiver's background thread actually entering its listen loop. During this
     * window, calls that depend on the receiver being fully initialized (such as obtaining
     * the local member with resolved host/port properties) may observe inconsistent state.
     * <p>
     * The default implementation returns {@code true} immediately, preserving backward
     * compatibility for implementations that perform all initialization synchronously inside
     * {@link #start()}. Implementations that start an asynchronous listener thread should
     * override this method to block until the thread has signalled readiness.
     *
     * @param timeout the maximum time to wait for the receiver to become ready
     * @param unit    the time unit of the timeout argument
     *
     * @return {@code true} if the receiver became ready within the timeout; {@code false}
     *         if the waiting time elapsed before the receiver was ready
     *
     * @throws InterruptedException if the current thread was interrupted while waiting
     */
    default boolean waitForReady(long timeout, TimeUnit unit) throws InterruptedException {
        return true;
    }

}