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
import java.util.Iterator;
import java.util.concurrent.Executor;

import javax.servlet.http.HttpUpgradeHandler;

import org.apache.tomcat.util.net.AbstractEndpoint.Handler.SocketState;
import org.apache.tomcat.util.net.DispatchType;
import org.apache.tomcat.util.net.SSLSupport;
import org.apache.tomcat.util.net.SocketStatus;
import org.apache.tomcat.util.net.SocketWrapperBase;


/**
 * Common interface for processors of all protocols.
 */
public interface Processor {

    Executor getExecutor();

    /**
     * Process a connection. This is called whenever an event occurs (e.g. more
     * data arrives) that allows processing to continue for a connection that is
     * not currently being processed.
     *
     * @param socketWrapper The connection to process
     *
     * @return The state the caller should put the socket in when this method
     *         returns
     *
     * @throws IOException If an I/O error occurs during the processing of the
     *         request
     */
    SocketState process(SocketWrapperBase<?> socketWrapper) throws IOException;

    /**
     * Service a 'standard' HTTP request. This method is called for both new
     * requests and for requests that have partially read the HTTP request line
     * or HTTP headers. Once the headers have been fully read this method is not
     * called again until there is a new HTTP request to process. Note that the
     * request type may change during processing which may result in one or more
     * calls to {@link #dispatch(SocketStatus)}.
     *
     * @param socketWrapper The connection to process
     *
     * @return The state the caller should put the socket in when this method
     *         returns
     *
     * @throws IOException If an I/O error occurs during the processing of the
     *         request
     */
    SocketState service(SocketWrapperBase<?> socketWrapper) throws IOException;

    /**
     * Process an in-progress request that is not longer in standard HTTP mode.
     * Uses currently include Servlet 3.0 Async and HTTP upgrade connections.
     * Further uses may be added in the future. These will typically start as
     * HTTP requests.
     */
    SocketState dispatch(SocketStatus status);

    SocketState asyncPostProcess();

    HttpUpgradeHandler getHttpUpgradeHandler();

    void errorDispatch();

    boolean isAsync();
    boolean isUpgrade();

    Request getRequest();

    /**
     * Recycle the processor, ready for the next request which may be on the
     * same connection or a different connection.
     */
    void recycle();

    /**
     * When client certificate information is presented in a form other than
     * instances of {@link java.security.cert.X509Certificate} it needs to be
     * converted before it can be used and this property controls which JSSE
     * provider is used to perform the conversion. For example it is used with
     * the AJP connectors, the HTTP APR connector and with the
     * {@link org.apache.catalina.valves.SSLValve}. If not specified, the
     * default provider will be used.
     *
     * @return The name of the JSSE provider to use for certificate
     *         transformation if required
     */
    String getClientCertProvider();

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

    void addDispatch(DispatchType dispatchType);

    Iterator<DispatchType> getIteratorAndClearDispatches();
}
