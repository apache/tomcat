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
package jakarta.servlet;

/**
 * Provides the context for asynchronous request handling
 *
 * @since Servlet 3.0
 */
public interface AsyncContext {

    /**
     * The attribute name for the URI of the async request
     */
    public static final String ASYNC_REQUEST_URI =
            "jakarta.servlet.async.request_uri";

    /**
     * The attribute name for the Context Path of the async request
     */
    public static final String ASYNC_CONTEXT_PATH  =
            "jakarta.servlet.async.context_path";

    /**
     * The attribute name for the Mapping of the async request
     */
    public static final String ASYNC_MAPPING =
            "jakarta.servlet.async.mapping";

    /**
     * The attribute name for the Path Info of the async request
     */
    public static final String ASYNC_PATH_INFO =
            "jakarta.servlet.async.path_info";

    /**
     * The attribute name for the Servlet Path of the async request
     */
    public static final String ASYNC_SERVLET_PATH =
            "jakarta.servlet.async.servlet_path";

    /**
     * The attribute name for the Query String of the async request
     */
    public static final String ASYNC_QUERY_STRING =
            "jakarta.servlet.async.query_string";

    /**
     * @return a reference to the ServletRequest object
     */
    ServletRequest getRequest();

    /**
     * @return a reference to the ServletResponse object
     */
    ServletResponse getResponse();

    /**
     * @return true if the Request and Response are the original ones
     */
    boolean hasOriginalRequestAndResponse();

    /**
     * @throws IllegalStateException if this method is called when the request
     * is not in asynchronous mode. The request is in asynchronous mode after
     * {@link jakarta.servlet.http.HttpServletRequest#startAsync()} or
     * {@link jakarta.servlet.http.HttpServletRequest#startAsync(ServletRequest,
     * ServletResponse)} has been called and before {@link #complete()} or any
     * other dispatch() method has been called.
     */
    void dispatch();

    /**
     * @param path The path to which the request/response should be dispatched
     *             relative to the {@link ServletContext} from which this async
     *             request was started.
     *
     * @throws IllegalStateException if this method is called when the request
     * is not in asynchronous mode. The request is in asynchronous mode after
     * {@link jakarta.servlet.http.HttpServletRequest#startAsync()} or
     * {@link jakarta.servlet.http.HttpServletRequest#startAsync(ServletRequest,
     * ServletResponse)} has been called and before {@link #complete()} or any
     * other dispatch() method has been called.
     */
    void dispatch(String path);

    /**
     * @param path The path to which the request/response should be dispatched
     *             relative to the specified {@link ServletContext}.
     * @param context The {@link ServletContext} to which the request/response
     *                should be dispatched.
     *
     * @throws IllegalStateException if this method is called when the request
     * is not in asynchronous mode. The request is in asynchronous mode after
     * {@link jakarta.servlet.http.HttpServletRequest#startAsync()} or
     * {@link jakarta.servlet.http.HttpServletRequest#startAsync(ServletRequest,
     * ServletResponse)} has been called and before {@link #complete()} or any
     * other dispatch() method has been called.
     */
    void dispatch(ServletContext context, String path);

    /**
     * Completes the async request processing and closes the response stream
     */
    void complete();

    /**
     * Starts a new thread to process the asynchronous request
     *
     * @param run a Runnable that the new thread will run
     */
    void start(Runnable run);

    /**
     * Adds an event listener that will be called for different AsyncEvents fire
     *
     * @param listener an AsyncListener that will be called with AsyncEvent objects
     */
    void addListener(AsyncListener listener);

    /**
     * Adds an event listener that will be called when different AsyncEvents fire
     *
     * @param listener an AsyncListener that will be called with AsyncEvent objects
     * @param request the ServletRequest that will be passed with the AsyncEvent
     * @param response the ServletResponse that will be passed with the AsyncEvent
     */
    void addListener(AsyncListener listener, ServletRequest request,
            ServletResponse response);

    /**
     * Creates and returns an AsyncListener object
     *
     * @param <T> The type to create that extends AsyncListener
     * @param clazz The class to instantiate to create the listener
     * @return the newly created AsyncListener object
     * @throws ServletException if the listener cannot be created
     */
    <T extends AsyncListener> T createListener(Class<T> clazz)
    throws ServletException;

    /**
     * Set the timeout.
     *
     * @param timeout The timeout in milliseconds. 0 or less indicates no
     *                timeout.
     */
    void setTimeout(long timeout);

    /**
     * Get the current timeout.
     *
     * @return The timeout in milliseconds. 0 or less indicates no timeout.
     */
    long getTimeout();
}
