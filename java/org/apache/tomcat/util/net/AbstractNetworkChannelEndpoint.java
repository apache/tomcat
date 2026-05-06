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
package org.apache.tomcat.util.net;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.Channel;
import java.nio.channels.NetworkChannel;

/**
 * Provides a base implementation for endpoints that use a {@link NetworkChannel}.
 *
 * @param <S> The channel type
 * @param <U> The network channel type
 */
public abstract class AbstractNetworkChannelEndpoint<S extends Channel, U extends NetworkChannel>
        extends AbstractEndpoint<S,U> {

    /**
     * Constructs a new endpoint.
     */
    public AbstractNetworkChannelEndpoint() {
    }

    /**
     * Returns the server socket used by this endpoint.
     *
     * @return the server socket
     */
    protected abstract NetworkChannel getServerSocket();

    /**
     * Creates the channel to be used by this endpoint.
     *
     * @param buffer The buffer handler to use for the channel
     *
     * @return the created channel
     */
    protected abstract S createChannel(SocketBufferHandler buffer);


    @Override
    protected final InetSocketAddress getLocalAddress() throws IOException {
        NetworkChannel serverSock = getServerSocket();
        if (serverSock == null) {
            return null;
        }
        SocketAddress sa = serverSock.getLocalAddress();
        if (sa instanceof InetSocketAddress) {
            return (InetSocketAddress) sa;
        }
        return null;
    }
}