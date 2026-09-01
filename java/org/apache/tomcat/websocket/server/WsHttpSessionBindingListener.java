/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tomcat.websocket.server;

import java.io.Serializable;

import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpSessionBindingEvent;
import jakarta.servlet.http.HttpSessionBindingListener;

/**
 * Listener used to track when an HTTP session expires and notifies the WebSocket container so any associated WebSocket
 * session can be closed.
 */
public class WsHttpSessionBindingListener implements HttpSessionBindingListener, Serializable {

    private static final long serialVersionUID = 1L;

    private final String key;

    /**
     * Create a new listener.
     *
     * @param key The key that identifies the HTTP session
     */
    public WsHttpSessionBindingListener(String key) {
        this.key = key;
    }


    public String getKey() {
        return key;
    }


    @Override
    public void valueUnbound(HttpSessionBindingEvent event) {
        HttpSession httpSession = event.getSession();
        /*
         * During replication this event will be triggered when the attribute is updated. Updates should not trigger a
         * call to the WebSocket server container. If this is an update, the session will still be valid.
         */
        try {
            httpSession.getCreationTime();
            // No exception. Session is valid. Nothing to do.
            return;
        } catch (IllegalStateException ise) {
            // Ignore

        }
        Object obj = httpSession.getServletContext().getAttribute(Constants.SERVER_CONTAINER_SERVLET_CONTEXT_ATTRIBUTE);
        if (obj instanceof WsServerContainer) {
            ((WsServerContainer) obj).handleHttpSessionKeyUnbound(key);
        }
    }
}
