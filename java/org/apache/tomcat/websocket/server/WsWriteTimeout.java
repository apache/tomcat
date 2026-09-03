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
package org.apache.tomcat.websocket.server;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.tomcat.websocket.BackgroundProcess;
import org.apache.tomcat.websocket.BackgroundProcessManager;

/**
 * Provides timeouts for asynchronous web socket writes. On the server side we only have access to
 * {@link jakarta.servlet.ServletOutputStream} and {@link jakarta.servlet.ServletInputStream} so there is no way to set
 * a timeout for writes to the client.
 */
public class WsWriteTimeout implements BackgroundProcess {

    /**
     * Default constructor.
     */
    public WsWriteTimeout() {
    }

    private final Set<WsRemoteEndpointImplServer> endpoints = new HashSet<>();
    private final ReentrantLock backgroundProcessLock = new ReentrantLock();
    private int count = 0;

    private volatile int backgroundProcessCount = 0;
    private volatile int processPeriod = 1;

    @Override
    public void backgroundProcess() {
        // This method gets called once a second.
        backgroundProcessCount++;

        if (backgroundProcessCount >= processPeriod) {
            backgroundProcessCount = 0;

            Set<WsRemoteEndpointImplServer> endpointsForTimeoutCheck = new HashSet<>();
            backgroundProcessLock.lock();
            try {
                endpointsForTimeoutCheck.addAll(endpoints);
            } finally {
                backgroundProcessLock.unlock();
            }

            /*
             * The timeout expiry is not fixed. A completed write or a new write can change the expiry at any point.
             * Since it is not possible to order the endpoints by expiry time and process the endpoints in expiry time
             * order, every endpoint is checked.
             */
            long now = System.currentTimeMillis();
            for (WsRemoteEndpointImplServer endpoint : endpointsForTimeoutCheck) {
                if (endpoint.getTimeoutExpiry() < now) {
                    // Background thread, not the thread that triggered the write, so no need to use a dispatch
                    endpoint.onTimeout(false, now);
                }
            }
        }
    }


    @Override
    public void setProcessPeriod(int period) {
        this.processPeriod = period;
    }


    /**
     * {@inheritDoc} The default value is 1 which means asynchronous write timeouts are processed every 1 second.
     */
    @Override
    public int getProcessPeriod() {
        return processPeriod;
    }


    /**
     * Registers an endpoint for timeout tracking.
     *
     * @param endpoint the endpoint to register
     */
    public void register(WsRemoteEndpointImplServer endpoint) {
        backgroundProcessLock.lock();
        try {
            boolean result = endpoints.add(endpoint);
            if (result) {
                if (count == 0) {
                    BackgroundProcessManager.getInstance().register(this);
                }
                count++;
            }
        } finally {
            backgroundProcessLock.unlock();
        }
    }


    /**
     * Unregisters an endpoint from timeout tracking.
     *
     * @param endpoint the endpoint to unregister
     */
    public void unregister(WsRemoteEndpointImplServer endpoint) {
        backgroundProcessLock.lock();
        try {
            boolean result = endpoints.remove(endpoint);
            if (result) {
                count--;
                if (count == 0) {
                    BackgroundProcessManager.getInstance().unregister(this);
                }
            }
        } finally {
            backgroundProcessLock.unlock();
        }
    }
}
