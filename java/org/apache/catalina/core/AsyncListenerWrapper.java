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
package org.apache.catalina.core;

import java.io.IOException;

import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;

/**
 * Wrapper for an {@link AsyncListener} that supports custom request and response objects
 * for event customization.
 */
public class AsyncListenerWrapper {

    private AsyncListener listener = null;
    private ServletRequest servletRequest = null;
    private ServletResponse servletResponse = null;

    /**
     * Default constructor.
     */
    public AsyncListenerWrapper() {
    }

    /**
     * Fires the onStartAsync event on the wrapped listener.
     *
     * @param event The async event
     * @throws IOException if an I/O error occurs
     */
    public void fireOnStartAsync(AsyncEvent event) throws IOException {
        listener.onStartAsync(customizeEvent(event));
    }

    /**
     * Fires the onComplete event on the wrapped listener.
     *
     * @param event The async event
     * @throws IOException if an I/O error occurs
     */
    public void fireOnComplete(AsyncEvent event) throws IOException {
        listener.onComplete(customizeEvent(event));
    }

    /**
     * Fires the onTimeout event on the wrapped listener.
     *
     * @param event The async event
     * @throws IOException if an I/O error occurs
     */
    public void fireOnTimeout(AsyncEvent event) throws IOException {
        listener.onTimeout(customizeEvent(event));
    }

    /**
     * Fires the onError event on the wrapped listener.
     *
     * @param event The async event
     * @throws IOException if an I/O error occurs
     */
    public void fireOnError(AsyncEvent event) throws IOException {
        listener.onError(customizeEvent(event));
    }

    /**
     * Returns the wrapped async listener.
     *
     * @return the async listener
     */
    public AsyncListener getListener() {
        return listener;
    }

    /**
     * Sets the async listener to wrap.
     *
     * @param listener the async listener
     */
    public void setListener(AsyncListener listener) {
        this.listener = listener;
    }

    /**
     * Sets the custom servlet request to use for events.
     *
     * @param servletRequest the servlet request
     */
    public void setServletRequest(ServletRequest servletRequest) {
        this.servletRequest = servletRequest;
    }

    /**
     * Sets the custom servlet response to use for events.
     *
     * @param servletResponse the servlet response
     */
    public void setServletResponse(ServletResponse servletResponse) {
        this.servletResponse = servletResponse;
    }


    private AsyncEvent customizeEvent(AsyncEvent event) {
        if (servletRequest != null && servletResponse != null) {
            return new AsyncEvent(event.getAsyncContext(), servletRequest, servletResponse, event.getThrowable());
        } else {
            return event;
        }
    }
}
