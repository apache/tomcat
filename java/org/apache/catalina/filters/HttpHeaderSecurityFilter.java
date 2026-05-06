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
import java.net.URI;
import java.net.URISyntaxException;

import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

/**
 * Provides a single configuration point for security measures that required the addition of one or more HTTP headers to
 * the response.
 */
public class HttpHeaderSecurityFilter extends FilterBase {

    /**
     * Creates a new instance of the filter.
     */
    public HttpHeaderSecurityFilter() {
        super();
    }


    // Log must be non-static as loggers are created per class-loader and this
    // Filter may be used in multiple class loaders
    private final Log log = LogFactory.getLog(HttpHeaderSecurityFilter.class); // must not be static

    // HSTS
    private static final String HSTS_HEADER_NAME = "Strict-Transport-Security";
    private boolean hstsEnabled = true;
    private int hstsMaxAgeSeconds = 0;
    private boolean hstsIncludeSubDomains = false;
    private boolean hstsPreload = false;
    private String hstsHeaderValue;

    // Click-jacking protection
    private static final String ANTI_CLICK_JACKING_HEADER_NAME = "X-Frame-Options";
    private boolean antiClickJackingEnabled = true;
    private XFrameOption antiClickJackingOption = XFrameOption.DENY;
    private URI antiClickJackingUri;
    private String antiClickJackingHeaderValue;

    // Block content sniffing
    private static final String BLOCK_CONTENT_TYPE_SNIFFING_HEADER_NAME = "X-Content-Type-Options";
    private static final String BLOCK_CONTENT_TYPE_SNIFFING_HEADER_VALUE = "nosniff";
    private boolean blockContentTypeSniffingEnabled = true;

    // Cross-site scripting filter protection
    @Deprecated
    private static final String XSS_PROTECTION_HEADER_NAME = "X-XSS-Protection";
    @Deprecated
    private static final String XSS_PROTECTION_HEADER_VALUE = "1; mode=block";
    @Deprecated
    private boolean xssProtectionEnabled = false;

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        super.init(filterConfig);

        // Build HSTS header value
        StringBuilder hstsValue = new StringBuilder("max-age=");
        hstsValue.append(hstsMaxAgeSeconds);
        if (hstsIncludeSubDomains) {
            hstsValue.append(";includeSubDomains");
        }
        if (hstsPreload) {
            hstsValue.append(";preload");
        }
        hstsHeaderValue = hstsValue.toString();

        // Anti click-jacking
        StringBuilder cjValue = new StringBuilder(antiClickJackingOption.headerValue);
        if (antiClickJackingOption == XFrameOption.ALLOW_FROM) {
            cjValue.append(' ');
            cjValue.append(antiClickJackingUri);
        }
        antiClickJackingHeaderValue = cjValue.toString();
    }


    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        if (response instanceof HttpServletResponse) {
            HttpServletResponse httpResponse = (HttpServletResponse) response;

            if (response.isCommitted()) {
                throw new ServletException(sm.getString("httpHeaderSecurityFilter.committed"));
            }

            // HSTS
            if (hstsEnabled && request.isSecure()) {
                httpResponse.setHeader(HSTS_HEADER_NAME, hstsHeaderValue);
            }

            // anti click-jacking
            if (antiClickJackingEnabled) {
                httpResponse.setHeader(ANTI_CLICK_JACKING_HEADER_NAME, antiClickJackingHeaderValue);
            }

            // Block content type sniffing
            if (blockContentTypeSniffingEnabled) {
                httpResponse.setHeader(BLOCK_CONTENT_TYPE_SNIFFING_HEADER_NAME,
                        BLOCK_CONTENT_TYPE_SNIFFING_HEADER_VALUE);
            }

            // cross-site scripting filter protection
            if (xssProtectionEnabled) {
                httpResponse.setHeader(XSS_PROTECTION_HEADER_NAME, XSS_PROTECTION_HEADER_VALUE);
            }
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


    /**
     * Returns whether HSTS is enabled.
     *
     * @return {@code true} if HSTS is enabled
     */
    public boolean isHstsEnabled() {
        return hstsEnabled;
    }


    /**
     * Enables or disables HSTS.
     *
     * @param hstsEnabled {@code true} to enable HSTS
     */
    public void setHstsEnabled(boolean hstsEnabled) {
        this.hstsEnabled = hstsEnabled;
    }


    /**
     * Returns the maximum age in seconds for the HSTS header.
     *
     * @return the maximum age in seconds
     */
    public int getHstsMaxAgeSeconds() {
        return hstsMaxAgeSeconds;
    }


    /**
     * Sets the maximum age in seconds for the HSTS header.
     *
     * @param hstsMaxAgeSeconds the maximum age in seconds
     */
    public void setHstsMaxAgeSeconds(int hstsMaxAgeSeconds) {
        this.hstsMaxAgeSeconds = Math.max(hstsMaxAgeSeconds, 0);
    }


    /**
     * Returns whether subdomains are included in the HSTS header.
     *
     * @return {@code true} if subdomains are included
     */
    public boolean isHstsIncludeSubDomains() {
        return hstsIncludeSubDomains;
    }


    /**
     * Sets whether subdomains should be included in the HSTS header.
     *
     * @param hstsIncludeSubDomains {@code true} to include subdomains
     */
    public void setHstsIncludeSubDomains(boolean hstsIncludeSubDomains) {
        this.hstsIncludeSubDomains = hstsIncludeSubDomains;
    }


    /**
     * Returns whether the preload directive is included in the HSTS header.
     *
     * @return {@code true} if preload is enabled
     */
    public boolean isHstsPreload() {
        return hstsPreload;
    }


    /**
     * Sets whether the preload directive should be included in the HSTS header.
     *
     * @param hstsPreload {@code true} to include preload
     */
    public void setHstsPreload(boolean hstsPreload) {
        this.hstsPreload = hstsPreload;
    }


    /**
     * Returns whether anti-click-jacking protection is enabled.
     *
     * @return {@code true} if anti-click-jacking is enabled
     */
    public boolean isAntiClickJackingEnabled() {
        return antiClickJackingEnabled;
    }


    /**
     * Enables or disables anti-click-jacking protection.
     *
     * @param antiClickJackingEnabled {@code true} to enable anti-click-jacking
     */
    public void setAntiClickJackingEnabled(boolean antiClickJackingEnabled) {
        this.antiClickJackingEnabled = antiClickJackingEnabled;
    }


    /**
     * Returns the X-Frame-Options value.
     *
     * @return the X-Frame-Options value
     */
    public String getAntiClickJackingOption() {
        return antiClickJackingOption.toString();
    }


    /**
     * Sets the X-Frame-Options value for click-jacking protection.
     *
     * @param antiClickJackingOption the X-Frame-Options value (DENY, SAMEORIGIN, or ALLOW-FROM)
     */
    public void setAntiClickJackingOption(String antiClickJackingOption) {
        for (XFrameOption option : XFrameOption.values()) {
            if (option.getHeaderValue().equalsIgnoreCase(antiClickJackingOption)) {
                this.antiClickJackingOption = option;
                return;
            }
        }
        throw new IllegalArgumentException(
                sm.getString("httpHeaderSecurityFilter.clickjack.invalid", antiClickJackingOption));
    }


    /**
     * Returns the URI used with the ALLOW_FROM X-Frame-Options directive.
     *
     * @return the ALLOW_FROM URI
     */
    public String getAntiClickJackingUri() {
        return antiClickJackingUri.toString();
    }


    /**
     * Returns whether content type sniffing protection is enabled.
     *
     * @return {@code true} if content type sniffing protection is enabled
     */
    public boolean isBlockContentTypeSniffingEnabled() {
        return blockContentTypeSniffingEnabled;
    }


    /**
     * Enables or disables content type sniffing protection.
     *
     * @param blockContentTypeSniffingEnabled {@code true} to enable protection
     */
    public void setBlockContentTypeSniffingEnabled(boolean blockContentTypeSniffingEnabled) {
        this.blockContentTypeSniffingEnabled = blockContentTypeSniffingEnabled;
    }


    /**
     * Sets the URI used with the ALLOW_FROM X-Frame-Options directive.
     *
     * @param antiClickJackingUri the URI for ALLOW_FROM
     */
    public void setAntiClickJackingUri(String antiClickJackingUri) {
        URI uri;
        try {
            uri = new URI(antiClickJackingUri);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(e);
        }
        this.antiClickJackingUri = uri;
    }


    @Deprecated
    public boolean isXssProtectionEnabled() {
        return xssProtectionEnabled;
    }


    @Deprecated
    public void setXssProtectionEnabled(boolean xssProtectionEnabled) {
        this.xssProtectionEnabled = xssProtectionEnabled;
    }


    private enum XFrameOption {
        DENY("DENY"),
        SAME_ORIGIN("SAMEORIGIN"),
        ALLOW_FROM("ALLOW-FROM");


        private final String headerValue;

        XFrameOption(String headerValue) {
            this.headerValue = headerValue;
        }

        public String getHeaderValue() {
            return headerValue;
        }
    }
}
