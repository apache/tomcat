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
import java.io.StringReader;
import java.util.Enumeration;

import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.http.parser.ContentRange;
import org.apache.tomcat.util.res.StringManager;

/**
 * <p>
 * Servlet filter that can help mitigate Denial of Service (DoS) by limiting the maximum file size of a partial PUT
 * request's Content-Range header that are allowed.
 */
public class RangedPutRequestBoundsFilter extends FilterBase {
    protected static final StringManager sm = StringManager.getManager(RangedPutRequestBoundsFilter.class);

    /**
     * default value for maximum size of a ranged PUT target resource.
     */
    private static final long DEFAULT_MAX = 1L << 32;
    private static final String PARAMETER_MAX_SIZE = "maxSize";

    /**
     * Maximum size of a ranged PUT target resource, 4GB by default. A value of -1 indicates no maximum.
     */
    private long maxSize = DEFAULT_MAX;

    /**
     * Gets the maximum size of destination resource that is allowed.
     *
     * @return The maximum size of destination resource that is allowed in bytes. A value of -1 indicates no maximum.
     */
    public long getMaxSize() {
        return maxSize;
    }

    /**
     * Sets the maximum size of destination resource that is allowed.
     *
     * @param maxSize The maximum size of destination resource that is allowed in bytes. A value of -1 indicates no
     *                    maximum.
     *
     * @throws IllegalArgumentException if the maxSize is unsupported
     */
    public void setMaxSize(long maxSize) {
        if (maxSize < 0 && maxSize != -1) {
            throw new IllegalArgumentException("Unsupported value: " + maxSize);
        }
        this.maxSize = maxSize;
    }

    private transient Log log = LogFactory.getLog(RangedPutRequestBoundsFilter.class);
    private String filterName = null;

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        this.filterName = filterConfig.getFilterName();
        for (Enumeration<String> names = filterConfig.getInitParameterNames(); names.hasMoreElements();) {
            String name = names.nextElement();
            String value = filterConfig.getInitParameter(name);
            if (PARAMETER_MAX_SIZE.equals(name)) {
                try {
                    setMaxSize(Long.valueOf(value));
                } catch (IllegalArgumentException e) {
                    throw new ServletException(sm.getString("rangedRequestBoundsFilter.invalidParameter", filterName,
                            PARAMETER_MAX_SIZE, value), e);
                }
            } else {
                throw new ServletException(
                        sm.getString("rangedRequestBoundsFilter.noSuchParameter", filterName, PARAMETER_MAX_SIZE));
            }
        }
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (getMaxSize() != -1 && request instanceof HttpServletRequest req) {
            if ("PUT".equals(req.getMethod())) {
                Enumeration<String> values = req.getHeaders("Content-Range");
                while (values.hasMoreElements()) {
                    String cr = values.nextElement();
                    if (!checkContentRangeBounds(req, cr)) {
                        ((HttpServletResponse) response).sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
                        return;
                    }
                }
            }
        }
        chain.doFilter(request, response);
    }

    /**
     * Checks the content-range header, returns false only if the content-range parsed successfully and the range
     * exceeds bounds.
     *
     * @param req                The servlet request we are processing.
     * @param contentRangeHeader The contentRange header from request
     *
     * @return false only if the content-range parsed successfully and the range exceeds bounds.
     */
    private boolean checkContentRangeBounds(HttpServletRequest req, String contentRangeHeader) {
        try {
            ContentRange contentRange = ContentRange.parse(new StringReader(contentRangeHeader));
            if (contentRange != null && getMaxSize() != -1 &&
                    (getMaxSize() <= contentRange.getEnd() || getMaxSize() < (contentRange.getLength()))) {
                if (log.isWarnEnabled()) {
                    log.warn(sm.getString("rangedRequestBoundsFilter.maxSizeExceeded", filterName, req.getRequestURI(),
                            req.getRemoteAddr(), contentRangeHeader, getMaxSize()));
                }
                return false;
            }
        } catch (IOException e) {
            // Ignore
        }
        return true;
    }

    @Override
    protected Log getLogger() {
        return log;
    }

    @Override
    protected boolean isConfigProblemFatal() {
        return true;
    }
}
