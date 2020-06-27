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
import java.nio.charset.Charset;

import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;


/**
 * Filter that explicitly sets the default character set for media subtypes of
 * the "text" type to ISO-8859-1, or another user defined character set. RFC2616
 * explicitly states that browsers must use ISO-8859-1 if no character set is
 * defined for media with subtype "text". However, browsers may attempt to
 * auto-detect the character set. This may be exploited by an attacker to
 * perform an XSS attack. Internet Explorer has this behaviour by default. Other
 * browsers have an option to enable it.<br>
 *
 * This filter prevents the attack by explicitly setting a character set. Unless
 * the provided character set is explicitly overridden by the user - in which
 * case they deserve everything they get - the browser will adhere to an
 * explicitly set character set, thus preventing the XSS attack.
 */
public class AddDefaultCharsetFilter extends FilterBase {

    // Log must be non-static as loggers are created per class-loader and this
    // Filter may be used in multiple class loaders
    private final Log log = LogFactory.getLog(AddDefaultCharsetFilter.class); // must not be static

    private static final String DEFAULT_ENCODING = "ISO-8859-1";

    private String encoding;

    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }

    @Override
    protected Log getLogger() {
        return log;
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        super.init(filterConfig);
        if (encoding == null || encoding.length() == 0 ||
                encoding.equalsIgnoreCase("default")) {
            encoding = DEFAULT_ENCODING;
        } else if (encoding.equalsIgnoreCase("system")) {
            encoding = Charset.defaultCharset().name();
        } else if (!Charset.isSupported(encoding)) {
            throw new IllegalArgumentException(sm.getString(
                    "addDefaultCharset.unsupportedCharset", encoding));
        }
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
            FilterChain chain) throws IOException, ServletException {

        // Wrap the response
        if (response instanceof HttpServletResponse) {
            ResponseWrapper wrapped =
                new ResponseWrapper((HttpServletResponse)response, encoding);
            chain.doFilter(request, wrapped);
        } else {
            chain.doFilter(request, response);
        }
    }

    /**
     * Wrapper that adds a character set for text media types if no character
     * set is specified.
     */
    public static class ResponseWrapper extends HttpServletResponseWrapper {

        private String encoding;

        public ResponseWrapper(HttpServletResponse response, String encoding) {
            super(response);
            this.encoding = encoding;
        }

        @Override
        public void setContentType(String contentType) {

            if (contentType != null && contentType.startsWith("text/")) {
                if (!contentType.contains("charset=")) {
                    super.setContentType(contentType + ";charset=" + encoding);
                } else {
                    super.setContentType(contentType);
                    encoding = getCharacterEncoding();
                }
            } else {
                super.setContentType(contentType);
            }

        }

        @Override
        public void setHeader(String name, String value) {
            if (name.trim().equalsIgnoreCase("content-type")) {
                setContentType(value);
            } else {
                super.setHeader(name, value);
            }
        }

        @Override
        public void addHeader(String name, String value) {
            if (name.trim().equalsIgnoreCase("content-type")) {
                setContentType(value);
            } else {
                super.addHeader(name, value);
            }
        }

        @Override
        public void setCharacterEncoding(String charset) {
            super.setCharacterEncoding(charset);
            encoding = charset;
        }
    }
}
