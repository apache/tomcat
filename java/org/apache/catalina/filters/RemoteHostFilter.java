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
package org.apache.catalina.filters;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

/**
 * Concrete implementation of <code>RequestFilter</code> that filters based on the remote client's host name.
 *
 * @author Craig R. McClanahan
 */
public final class RemoteHostFilter extends RequestFilter {

    // Log must be non-static as loggers are created per class-loader and this
    // Filter may be used in multiple class loaders
    private final Log log = LogFactory.getLog(RemoteHostFilter.class); // must not be static


    /**
     * Extract the desired request property, and pass it (along with the specified request and response objects and
     * associated filter chain) to the protected <code>process()</code> method to perform the actual filtering.
     *
     * @param request  The servlet request to be processed
     * @param response The servlet response to be created
     * @param chain    The filter chain for this request
     *
     * @exception IOException      if an input/output error occurs
     * @exception ServletException if a servlet error occurs
     */
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        process(request.getRemoteHost(), request, response, chain);

    }

    @Override
    protected Log getLogger() {
        return log;
    }
}
