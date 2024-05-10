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
package org.apache.catalina.tribes.transport;

import java.io.IOException;

public interface DataSender {

    /**
     * Connect.
     *
     * @throws IOException when an error occurs
     */
    void connect() throws IOException;

    /**
     * Disconnect.
     */
    void disconnect();

    /**
     * @return {@code true} if connected
     */
    boolean isConnected();

    /**
     * Set the receive buffer size.
     *
     * @param size the new size
     */
    void setRxBufSize(int size);

    /**
     * Set the transmit buffer size.
     *
     * @param size the new size
     */
    void setTxBufSize(int size);

    /**
     * Keepalive.
     *
     * @return {@code true} if kept alive
     */
    boolean keepalive();

    /**
     * Set the socket timeout.
     *
     * @param timeout in ms
     */
    void setTimeout(long timeout);

    /**
     * Set the amount of requests during which to keepalive.
     *
     * @param maxRequests the amount of requests
     */
    void setKeepAliveCount(int maxRequests);

    /**
     * Set the keepalive time.
     *
     * @param keepAliveTimeInMs the time in ms
     */
    void setKeepAliveTime(long keepAliveTimeInMs);

    /**
     * @return the request count
     */
    int getRequestCount();

    /**
     * @return the time to connect
     */
    long getConnectTime();

}