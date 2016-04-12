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
package org.apache.catalina.servlet4preview.http;


/**
 * Provides early access to some parts of the proposed Servlet 4.0 API.
 */
public class HttpServletRequestWrapper extends javax.servlet.http.HttpServletRequestWrapper
        implements HttpServletRequest {

    /**
     * Constructs a request object wrapping the given request.
     *
     * @param request The request to wrap
     *
     * @throws java.lang.IllegalArgumentException
     *             if the request is null
     */
    public HttpServletRequestWrapper(javax.servlet.http.HttpServletRequest request) {
        super(request);
    }

    private HttpServletRequest _getHttpServletRequest() {
        return (HttpServletRequest) super.getRequest();
    }

    /**
     * {@inheritDoc}
     * <p>
     * The default behavior of this method is to return
     * {@link HttpServletRequest#getMapping()} on the wrapped request object.
     *
     * @since Servlet 4.0
     */
    @Override
    public Mapping getMapping() {
        return this._getHttpServletRequest().getMapping();
    }

    /**
     * {@inheritDoc}
     * <p>
     * The default behavior of this method is to return
     * {@link HttpServletRequest#isPushSupported()} on the wrapped request object.
     *
     * @since Servlet 4.0
     */
    @Override
    public boolean isPushSupported() {
        return this._getHttpServletRequest().isPushSupported();
    }


    /**
     * {@inheritDoc}
     * <p>
     * The default behavior of this method is to return
     * {@link HttpServletRequest#getPushBuilder()} on the wrapped request object.
     *
     * @since Servlet 4.0
     */
    @Override
    public PushBuilder getPushBuilder() {
        return this._getHttpServletRequest().getPushBuilder();
    }
}
