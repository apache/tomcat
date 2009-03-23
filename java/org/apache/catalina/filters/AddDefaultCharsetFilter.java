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

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;


/**
 * Filter that explicitly sets the default character set for media subtypes of
 * the "text" type to ISO-8859-1. RFC2616 explicitly states that browsers must
 * use ISO-8859-1 in these circumstances. However, browsers may attempt to
 * auto-detect the character set. This may be exploited by an attacker to
 * perform an XSS attack. Internet Explorer has this behaviour by default. Other
 * browsers have an option to enable it.
 * 
 * This filter prevents the attack by explicitly setting a character set. Unless
 * the provided character set is explicitly overridden by the user - in which
 * case they deserve everything they get - the browser will adhere to an
 * explicitly set character set, thus preventing the XSS attack.
 */
public class AddDefaultCharsetFilter implements Filter {

    public void destroy() {
        // NOOP
    }

    public void doFilter(ServletRequest request, ServletResponse response,
            FilterChain chain) throws IOException, ServletException {
        
        // Wrap the response
        if (response instanceof HttpServletResponse) {
            ResponseWrapper wrapped =
                new ResponseWrapper((HttpServletResponse)response);
            chain.doFilter(request, wrapped);
        } else {
            chain.doFilter(request, response);
        }
    }

    public void init(FilterConfig filterConfig) throws ServletException {
        // NOOP
    }

    /**
     * Wrapper that adds the default character set for text media types if no
     * character set is specified.
     */
    public class ResponseWrapper extends HttpServletResponseWrapper {

        @Override
        public void setContentType(String ct) {
            
            if (ct != null && ct.startsWith("text/") &&
                    ct.indexOf("charset=") < 0) {
                // Use getCharacterEncoding() in case the charset has already
                // been set by a separate call.
                super.setContentType(ct + ";charset=" + getCharacterEncoding());
            } else {
                super.setContentType(ct);
            }

        }

        public ResponseWrapper(HttpServletResponse response) {
            super(response);
        }
        
    }
}
