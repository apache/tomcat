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
package org.apache.coyote.http11.upgrade;

import jakarta.servlet.http.HttpUpgradeHandler;

import org.apache.tomcat.util.net.AbstractEndpoint.Handler.SocketState;
import org.apache.tomcat.util.net.SSLSupport;
import org.apache.tomcat.util.net.SocketEvent;
import org.apache.tomcat.util.net.SocketWrapperBase;


/**
 * This Tomcat specific interface is implemented by handlers that require direct access to Tomcat's I/O layer rather
 * than going through the Servlet API.
 */
public interface InternalHttpUpgradeHandler extends HttpUpgradeHandler {

    /**
     * Process the specified event.
     *
     * @param status the event
     *
     * @return the status following the event
     */
    SocketState upgradeDispatch(SocketEvent status);

    /**
     * Check for a possible timeout.
     *
     * @param now the time to use for the timeout check
     */
    void timeoutAsync(long now);

    /**
     * Associate with the specified socket.
     *
     * @param wrapper the socket
     */
    void setSocketWrapper(SocketWrapperBase<?> wrapper);

    /**
     * Associate with the specified SSL support.
     *
     * @param sslSupport the SSL support
     */
    void setSslSupport(SSLSupport sslSupport);

    /**
     * Pause processing for the connection.
     */
    void pause();

    /**
     * @return {@code true} if able to process asynchronous IO, default is {@code false}
     */
    default boolean hasAsyncIO() {
        return false;
    }

    /**
     * @return the associated upgrade information used to collect statistics for the connection
     */
    default UpgradeInfo getUpgradeInfo() {
        return null;
    }
}