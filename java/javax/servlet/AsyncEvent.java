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
package javax.servlet;

/**
 * Used to pass data to the methods of {@link AsyncListener}.
 *
 * @since Servlet 3.0
 */
public class AsyncEvent {
    private final AsyncContext context;
    private final ServletRequest request;
    private final ServletResponse response;
    private final Throwable throwable;

    /**
     * Creates an instance using the provide parameters.
     *
     * @param context   The asynchronous context associated with the event
     */
    public AsyncEvent(AsyncContext context) {
        this.context = context;
        this.request = null;
        this.response = null;
        this.throwable = null;
    }

    /**
     * Creates an instance using the provide parameters.
     *
     * @param context   The asynchronous context associated with the event
     * @param request   The request associated with the event
     * @param response  The response associated with the event
     */
    public AsyncEvent(AsyncContext context, ServletRequest request,
            ServletResponse response) {
        this.context = context;
        this.request = request;
        this.response = response;
        this.throwable = null;
    }

    /**
     * Creates an instance using the provide parameters.
     *
     * @param context   The asynchronous context associated with the event
     * @param throwable The throwable associated with the event
     */
    public AsyncEvent(AsyncContext context, Throwable throwable) {
        this.context = context;
        this.throwable = throwable;
        this.request = null;
        this.response = null;
    }

    /**
     * Creates an instance using the provide parameters.
     *
     * @param context   The asynchronous context associated with the event
     * @param request   The request associated with the event
     * @param response  The response associated with the event
     * @param throwable The throwable associated with the event
     */
    public AsyncEvent(AsyncContext context, ServletRequest request,
            ServletResponse response, Throwable throwable) {
        this.context = context;
        this.request = request;
        this.response = response;
        this.throwable = throwable;
    }

    /**
     * Obtain the asynchronous context associated with the event.
     *
     * @return  The asynchronous context associated with the event or
     *          {@code null} if one was not specified
     */
    public AsyncContext getAsyncContext() {
        return context;
    }

    /**
     * Obtain the request associated with the event.
     *
     * @return  The request associated with the event or
     *          {@code null} if one was not specified
     */
    public ServletRequest getSuppliedRequest() {
        return request;
    }

    /**
     * Obtain the response associated with the event.
     *
     * @return  The response associated with the event or
     *          {@code null} if one was not specified
     */
    public ServletResponse getSuppliedResponse() {
        return response;
    }

    /**
     * Obtain the throwable associated with the event.
     *
     * @return  The throwable associated with the event or
     *          {@code null} if one was not specified
     */
    public Throwable getThrowable() {
        return throwable;
    }
}
