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
package org.apache.tomcat.manager2;

import java.io.IOException;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;


/**
 * Adds the security headers that {@link org.apache.catalina.filters.HttpHeaderSecurityFilter} does not cover (a
 * Content-Security-Policy and a Referrer-Policy) to every response. The web application uses no inline scripts, no
 * external resources and no frames, so the policy does not need any 'unsafe-*' directives.
 */
public class HeadersFilter implements Filter {


    private static final String CSP = "default-src 'self'; script-src 'self'; style-src 'self'; " +
            "img-src 'self' data:; connect-src 'self'; frame-ancestors 'none'; " +
            "base-uri 'self'; form-action 'self'";

    private static final String REFERRER_POLICY = "no-referrer";


    @Override
    public void init(FilterConfig filterConfig) {
        // Nothing to initialize
    }


    @Override
    public void destroy() {
        // Nothing to destroy
    }


    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletResponse resp = (HttpServletResponse) response;
        resp.setHeader("Content-Security-Policy", CSP);
        resp.setHeader("Referrer-Policy", REFERRER_POLICY);

        chain.doFilter(request, response);
    }
}
