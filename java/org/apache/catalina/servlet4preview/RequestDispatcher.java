/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.catalina.servlet4preview;

/**
 * Provides early access to some parts of the proposed Servlet 4.0 API.
 */
public interface RequestDispatcher extends javax.servlet.RequestDispatcher {

    /**
     * The name of the request attribute that should be set by the container
     * when the {@link #forward(javax.servlet.ServletRequest,
     * javax.servlet.ServletResponse)} method is called. It provides the
     * original value of a path-related property of the request. See the chapter
     * "Forwarded Request Parameters" in the Servlet Specification for details.
     *
     * @since Servlet 4.0
     */
    static final String FORWARD_MAPPING = "javax.servlet.forward.mapping";

    /**
     * The name of the request attribute that should be set by the container
     * when the {@link #include(javax.servlet.ServletRequest,
     * javax.servlet.ServletResponse)} method is called on the
     * {@code RequestDispatcher} obtained by a path and not by a name.
     * It provides information on the path that was used to obtain the
     * {@code RequestDispatcher} instance for this include call. See the chapter
     * "Included Request Parameters" in the Servlet Specification for details.
     *
     * @since Servlet 4.0
     */
    static final String INCLUDE_MAPPING = "javax.servlet.include.mapping";
}
