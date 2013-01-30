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

import java.util.Comparator;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;

/**
 * Provides timeouts for asynchronous web socket writes. On the server side we
 * only have access to {@link javax.servlet.ServletOutputStream} and
 * {@link javax.servlet.ServletInputStream} so there is no way to set a timeout
 * for writes to the client. Hence the separate thread.
 */
public class WsTimeout implements Runnable {

    public static final String THREAD_NAME_PREFIX = "Websocket Timeout - ";

    private final Set<WsRemoteEndpointServer> endpoints =
            new ConcurrentSkipListSet<>(new EndpointComparator());
    private volatile boolean running = true;

    public void stop() {
        running = false;
        synchronized (this) {
            this.notify();
        }
    }


    @Override
    public void run() {
        while (running) {
            // Wait for one second - no need for timeouts more frequently than
            // that
            synchronized (this) {
                try {
                    wait(1000);
                } catch (InterruptedException e) {
                    // Ignore
                }
            }

            long now = System.currentTimeMillis();
            Iterator<WsRemoteEndpointServer> iter = endpoints.iterator();
            while (iter.hasNext()) {
                WsRemoteEndpointServer endpoint = iter.next();
                if (endpoint.getTimeoutExpiry() < now) {
                    System.out.println(now);
                    endpoint.onTimeout();
                } else {
                    // Endpoints are ordered by timeout expiry so we reach this
                    // point there is no need to check the remaining endpoints
                    break;
                }
            }
        }
    }


    public void register(WsRemoteEndpointServer endpoint) {
        endpoints.add(endpoint);
    }


    public void unregister(WsRemoteEndpointServer endpoint) {
        endpoints.remove(endpoint);
    }


    /**
     * Note: this comparator imposes orderings that are inconsistent with equals
     */
    private static class EndpointComparator implements
            Comparator<WsRemoteEndpointServer> {

        @Override
        public int compare(WsRemoteEndpointServer o1,
                WsRemoteEndpointServer o2) {

            long t1 = o1.getTimeoutExpiry();
            long t2 = o2.getTimeoutExpiry();

            if (t1 < t2) {
                return -1;
            } else if (t1 == t2) {
                return 0;
            } else {
                return 1;
            }
        }
    }
}
