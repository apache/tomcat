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

package org.apache.coyote.http11;

import org.apache.coyote.Adapter;
import org.apache.tomcat.util.net.AbstractEndpoint;
import org.apache.tomcat.util.net.AbstractEndpoint.Handler.SocketState;
import org.apache.tomcat.util.net.SocketStatus;
import org.apache.tomcat.util.net.SocketWrapper;

/**
 * Interface specific for protocols that negotiate at NPN level, like
 * SPDY. This is only available for APR, will replace the HTTP framing.
 */
public interface NpnHandler<S> {

    /**
     * Check if the socket has negotiated the right NPN and process it.
     *
     * @param socket
     * @param status
     * @return OPEN if the socket doesn't have the right npn.
     *    CLOSE if processing is done. LONG to request read polling.
     */
    SocketState process(SocketWrapper<S> socket, SocketStatus status);

    /**
     * Initialize the npn handler.
     *
     * @param ep
     * @param sslContext
     * @param adapter
     */
    public void init(final AbstractEndpoint<S> ep, long sslContext, Adapter adapter);

    /**
     * Called when a SSLSocket or SSLEngine are first used, to initialize
     * NPN extension.
     *
     * @param socket SSLEngine or SSLSocket
     */
    void onCreateEngine(Object socket);
}