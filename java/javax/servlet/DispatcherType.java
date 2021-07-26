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
 * Enumeration of dispatcher types. Used both to define filter mappings and by
 * Servlets to determine why they were called.
 *
 * @since Servlet 3.0
 */
public enum DispatcherType {

    /**
     * {@link RequestDispatcher#forward(ServletRequest, ServletResponse)}
     */
    FORWARD,

    /**
     * {@link RequestDispatcher#include(ServletRequest, ServletResponse)}
     */
    INCLUDE,

    /**
     * Normal (non-dispatched) requests.
     */
    REQUEST,

    /**
     * {@link AsyncContext#dispatch()}, {@link AsyncContext#dispatch(String)}
     * and
     * {@link AsyncContext#addListener(AsyncListener, ServletRequest, ServletResponse)}
     */
    ASYNC,

    /**
     * When the container has passed processing to the error handler mechanism
     * such as a defined error page.
     */
    ERROR
}
