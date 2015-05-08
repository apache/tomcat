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

import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

/**
 * Provides a single configuration point for security measures that required the
 * addition of one or more HTTP headers to the response.
 */
public class HttpHeaderSecurityFilter extends FilterBase {

    private static final Log log = LogFactory.getLog(HttpHeaderSecurityFilter.class);

    // HSTS
    private static final String HSTS_HEADER_NAME = "Strict-Transport-Security";
    private boolean hstsEnabled = true;
    private int hstsMaxAgeSeconds = 0;
    private boolean hstsIncludeSubDomains = false;
    private String hstsHeaderValue;


    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        super.init(filterConfig);

        // Build HSTS header value
        StringBuilder hstsValue = new StringBuilder("max-age=");
        hstsValue.append(hstsMaxAgeSeconds);
        if (hstsIncludeSubDomains) {
            hstsValue.append(";includeSubDomains");
        }
        hstsHeaderValue = hstsValue.toString();
    }


    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
            FilterChain chain) throws IOException, ServletException {

        if (response.isCommitted()) {
            throw new ServletException(sm.getString("httpHeaderSecurityFilter.committed"));
        }

        // HSTS
        if (hstsEnabled && request.isSecure() && response instanceof HttpServletResponse) {
            ((HttpServletResponse) response).addHeader(HSTS_HEADER_NAME, hstsHeaderValue);
        }

        chain.doFilter(request, response);
    }


    @Override
    protected Log getLogger() {
        return log;
    }


    @Override
    protected boolean isConfigProblemFatal() {
        // This filter is security related to configuration issues always
        // trigger a failure.
        return true;
    }


    public boolean isHstsEnabled() {
        return hstsEnabled;
    }


    public void setHstsEnabled(boolean hstsEnabled) {
        this.hstsEnabled = hstsEnabled;
    }


    public int getHstsMaxAgeSeconds() {
        return hstsMaxAgeSeconds;
    }


    public void setHstsMaxAgeSeconds(int hstsMaxAgeSeconds) {
        if (hstsMaxAgeSeconds < 0) {
            hstsMaxAgeSeconds = 0;
        } else {
            this.hstsMaxAgeSeconds = hstsMaxAgeSeconds;
        }
    }


    public boolean isHstsIncludeSubDomains() {
        return hstsIncludeSubDomains;
    }


    public void setHstsIncludeSubDomains(boolean hstsIncludeSubDomains) {
        this.hstsIncludeSubDomains = hstsIncludeSubDomains;
    }
}
