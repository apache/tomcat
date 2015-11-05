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
package org.apache.coyote;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.apache.tomcat.util.net.AbstractEndpoint.Handler.SocketState;
import org.apache.tomcat.util.net.SSLSupport;
import org.apache.tomcat.util.net.SocketStatus;
import org.apache.tomcat.util.net.SocketWrapperBase;

/**
 * Common interface for processors of all protocols.
 */
public interface Processor {

    /**
     * Process a connection. This is called whenever an event occurs (e.g. more
     * data arrives) that allows processing to continue for a connection that is
     * not currently being processed.
     *
     * @param socketWrapper The connection to process
     * @param status The status of the connection that triggered this additional
     *               processing
     *
     * @return The state the caller should put the socket in when this method
     *         returns
     *
     * @throws IOException If an I/O error occurs during the processing of the
     *         request
     */
    SocketState process(SocketWrapperBase<?> socketWrapper, SocketStatus status) throws IOException;

    UpgradeToken getUpgradeToken();

    boolean isUpgrade();
    boolean isAsync();

    /**
     * Check this processor to see if the async timeout has expired and process
     * a timeout if that is that case.
     *
     * @param now The time (as returned by {@link System#currentTimeMillis()} to
     *            use as the current time to determine whether the async timeout
     *            has expired. If negative, the timeout will always be treated
     *            as if it has expired.
     */
    void timeoutAsync(long now);

    Request getRequest();

    /**
     * Recycle the processor, ready for the next request which may be on the
     * same connection or a different connection.
     */
    void recycle();

    void setSslSupport(SSLSupport sslSupport);

    /**
     * Allows retrieving additional input during the upgrade process
     * @return leftover bytes
     */
    ByteBuffer getLeftoverInput();

    /**
     * Informs the processor that the underlying I/O layer has stopped accepting
     * new connections. This is primarily intended to enable processors that
     * use multiplexed connections to prevent further 'streams' being added to
     * an existing multiplexed connection.
     */
    void pause();
}
