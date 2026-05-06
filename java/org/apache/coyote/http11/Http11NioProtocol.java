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

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.net.NioChannel;
import org.apache.tomcat.util.net.NioEndpoint;


/**
 * HTTP/1.1 protocol implementation using NIO.
 */
public class Http11NioProtocol extends AbstractHttp11Protocol<NioChannel> {

    private static final Log log = LogFactory.getLog(Http11NioProtocol.class);


    /**
     * Constructs a new Http11NioProtocol with a default NIO endpoint.
     */
    public Http11NioProtocol() {
        this(new NioEndpoint());
    }


    /**
     * Constructs a new Http11NioProtocol with the specified endpoint.
     *
     * @param endpoint the NIO endpoint to use
     */
    public Http11NioProtocol(NioEndpoint endpoint) {
        super(endpoint);
    }


    @Override
    protected Log getLog() {
        return log;
    }


    // -------------------- Pool setup --------------------

     /**
     * Sets the selector timeout for the NIO endpoint.
     *
     * @param timeout the selector timeout in milliseconds
     */
    public void setSelectorTimeout(long timeout) {
        ((NioEndpoint) getEndpoint()).setSelectorTimeout(timeout);
    }

    /**
     * Gets the selector timeout for the NIO endpoint.
     *
     * @return the selector timeout in milliseconds
     */
    public long getSelectorTimeout() {
        return ((NioEndpoint) getEndpoint()).getSelectorTimeout();
    }

    /**
     * Sets the poller thread priority for the NIO endpoint.
     *
     * @param threadPriority the poller thread priority
     */
    public void setPollerThreadPriority(int threadPriority) {
        ((NioEndpoint) getEndpoint()).setPollerThreadPriority(threadPriority);
    }

    /**
     * Gets the poller thread priority for the NIO endpoint.
     *
     * @return the poller thread priority
     */
    public int getPollerThreadPriority() {
        return ((NioEndpoint) getEndpoint()).getPollerThreadPriority();
    }


    @Override
    protected String getNamePrefix() {
        if (isSSLEnabled()) {
            return "https-" + getSslImplementationShortName() + "-nio";
        } else {
            return "http-nio";
        }
    }
}
