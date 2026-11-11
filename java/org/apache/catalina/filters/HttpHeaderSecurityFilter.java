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
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;

import org.apache.catalina.Globals;
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

    // Content Security Policy
    private static final String CSP_HEADER_NAME = "Content-Security-Policy";
    private static final String CSP_REPORT_ONLY_HEADER_NAME = "Content-Security-Policy-Report-Only";
    private static final String CSP_NONCE_PLACEHOLDER = "{nonce}";
    private static final int CSP_NONCE_BYTES = 16;
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");
    private boolean cspEnabled = false;
    private boolean cspReportOnly = false;
    private boolean cspAllowOverride = true;
    private String cspPolicy = "";
    private String cspHeaderValue;
    private boolean cspNonceRequired;
    private SecureRandom cspNonceRandom;

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
            if (antiClickJackingUri == null) {
                throw new IllegalArgumentException(sm.getString("httpHeaderSecurityFilter.nullAntiClickJackingUri"));
            }
            cjValue.append(' ');
            cjValue.append(antiClickJackingUri);
        }
        antiClickJackingHeaderValue = cjValue.toString();

        // Content Security Policy
        // Collapse whitespace, including line breaks, so a policy wrapped over several lines in web.xml is sent as
        // a single line. CR and LF are not permitted in HTTP/2 field values.
        cspHeaderValue = cspPolicy == null ? "" : WHITESPACE_PATTERN.matcher(cspPolicy.strip()).replaceAll(" ");
        if (cspEnabled && cspHeaderValue.isEmpty()) {
            throw new IllegalArgumentException(sm.getString("httpHeaderSecurityFilter.csp.invalid"));
        }
        // Only generate nonces if the policy uses them
        cspNonceRequired = cspHeaderValue.contains(CSP_NONCE_PLACEHOLDER);
        if (cspEnabled && cspNonceRequired) {
            cspNonceRandom = new SecureRandom();
        }
    }


    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        ServletRequest chainRequest = request;
        ServletResponse chainResponse = response;

        if (response instanceof HttpServletResponse httpResponse) {
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

            // Content Security Policy. The container ignores header changes made by an included resource.
            if (cspEnabled && request.getDispatcherType() != DispatcherType.INCLUDE) {
                String cspName = cspReportOnly ? CSP_REPORT_ONLY_HEADER_NAME : CSP_HEADER_NAME;
                String cspValue = cspHeaderValue;
                if (cspNonceRequired) {
                    cspValue = cspValue.replace(CSP_NONCE_PLACEHOLDER, getCspNonce(request));
                }
                // Set the policy now so it is present even if the response is committed early. The wrapper controls
                // how the rest of the chain sees and changes it.
                httpResponse.setHeader(cspName, cspValue);
                /*
                 * The wrapper detects RequestDispatcher.include() from the dispatcher type of the request passed down
                 * the chain. The container inserts the request of an include beneath the last request wrapper, so
                 * wrap the request to make sure that the include is visible through it.
                 */
                if (request instanceof HttpServletRequest httpRequest) {
                    chainRequest = new HttpServletRequestWrapper(httpRequest);
                }
                chainResponse = new CspResponseWrapper(httpResponse, chainRequest, cspName, cspValue,
                        cspAllowOverride, cspNonceRequired ? getNotModifiedExclusions(request) : null);
            }
        }

        chain.doFilter(chainRequest, chainResponse);
    }


    /*
     * Returns the nonce to use for the current request. A nonce already stored in the request, e.g. by another
     * instance of this filter earlier in the filter chain, is reused so every policy sent for the request accepts the
     * same nonce.
     */
    private String getCspNonce(ServletRequest request) {
        if (request.getAttribute(Globals.CSP_NONCE_ATTR) instanceof String existing && !existing.isEmpty()) {
            return existing;
        }
        byte[] bytes = new byte[CSP_NONCE_BYTES];
        cspNonceRandom.nextBytes(bytes);
        String nonce = Base64.getEncoder().encodeToString(bytes);
        request.setAttribute(Globals.CSP_NONCE_ATTR, nonce);
        return nonce;
    }


    /*
     * Returns the list of header values that the connector must not send with a 304 response, creating it if
     * necessary. One list is shared by every instance of this filter that processes the request. A list set by
     * another component that may not be modifiable is replaced by a modifiable copy, which keeps its elements.
     */
    @SuppressWarnings("unchecked")
    private static List<Object> getNotModifiedExclusions(ServletRequest request) {
        Object existing = request.getAttribute(Globals.NOT_MODIFIED_EXCLUDED_HEADERS_ATTR);
        if (existing instanceof ArrayList<?> list) {
            return (List<Object>) list;
        }
        List<Object> exclusions = new ArrayList<>();
        if (existing instanceof Collection<?> collection) {
            exclusions.addAll(collection);
        }
        request.setAttribute(Globals.NOT_MODIFIED_EXCLUDED_HEADERS_ATTR, exclusions);
        return exclusions;
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
        return antiClickJackingUri != null ? antiClickJackingUri.toString() : null;
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
     * Returns whether Content-Security-Policy is enabled.
     *
     * @return {@code true} if CSP is enabled
     */
    public boolean isCspEnabled() {
        return cspEnabled;
    }


    /**
     * Enables or disables Content-Security-Policy.
     *
     * @param cspEnabled {@code true} to enable CSP
     */
    public void setCspEnabled(boolean cspEnabled) {
        this.cspEnabled = cspEnabled;
    }


    /**
     * Returns whether the policy is sent in report-only mode.
     *
     * @return {@code true} if report-only
     */
    public boolean isCspReportOnly() {
        return cspReportOnly;
    }


    /**
     * Sets whether the policy is sent as Content-Security-Policy-Report-Only.
     *
     * @param cspReportOnly {@code true} for report-only
     */
    public void setCspReportOnly(boolean cspReportOnly) {
        this.cspReportOnly = cspReportOnly;
    }


    /**
     * Returns whether the application may replace the policy set by this filter.
     *
     * @return {@code true} if the policy is a default that the application may replace
     */
    public boolean isCspAllowOverride() {
        return cspAllowOverride;
    }


    /**
     * Sets whether the application may replace the policy set by this filter. If {@code true}, the policy is a default
     * that the first policy set by the application replaces. If {@code false}, the policy is always sent and any
     * policy set by the application is sent in addition to it, so the application can make the effective policy
     * stricter but never more permissive. In both cases, the policy set by this filter is not visible to the
     * application through {@code containsHeader()}, {@code getHeader()}, {@code getHeaders()} and
     * {@code getHeaderNames()}, so components that only set a policy if none is present still set theirs.
     *
     * @param cspAllowOverride {@code true} to allow the application to replace the policy
     */
    public void setCspAllowOverride(boolean cspAllowOverride) {
        this.cspAllowOverride = cspAllowOverride;
    }


    /**
     * Returns the configured Content-Security-Policy value.
     *
     * @return the policy string
     */
    public String getCspPolicy() {
        return cspPolicy;
    }


    /**
     * Sets the Content-Security-Policy value. Required when CSP is enabled. Every occurrence of {@code {nonce}} is
     * replaced with a nonce for the current request that is also stored in the request attribute
     * {@link Globals#CSP_NONCE_ATTR}.
     *
     * @param cspPolicy the policy string
     */
    public void setCspPolicy(String cspPolicy) {
        this.cspPolicy = cspPolicy;
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


    /*
     * Hides the policy set by the filter from the rest of the chain, so components that only set a policy if none is
     * present still set theirs, and applies cspAllowOverride to any policy the rest of the chain sets.
     *
     * The wrapper records whether the filter's policy is present, and the policies set by the application, rather
     * than recognising them by their text: a policy set by the application, or by another instance of this filter, may
     * have the same text as the filter's policy, and getHeaders() may not return repeated values. It does not record
     * calls that the container ignores: those made by a resource included with RequestDispatcher.include() and those
     * made once the response has been committed. If the policy contains a nonce, the wrapper also lists the filter's
     * policy, while it is present, in the request attribute that asks the connector to leave that header out of a 304
     * response.
     */
    private static final class CspResponseWrapper extends HttpServletResponseWrapper {

        private final ServletRequest request;
        private final String cspName;
        private final String cspValue;
        private final boolean allowOverride;
        /*
         * The header values the connector must not send with a 304 response, shared by every instance of this filter
         * that processes the request and possibly by other components, and the entry for this filter's policy. Both
         * are null if the policy does not contain a nonce.
         */
        private final List<Object> notModifiedExclusions;
        private final Map.Entry<String,String> notModifiedExclusion;
        /*
         * Is the filter's policy present? If the application may override it, the first policy the application sets
         * replaces it. Otherwise, it is always present.
         */
        private boolean filterPolicyPresent = true;
        // The policies set by the application. Only used if the application may not override the filter's policy.
        private final List<String> applicationValues = new ArrayList<>();

        CspResponseWrapper(HttpServletResponse response, ServletRequest request, String cspName, String cspValue,
                boolean allowOverride, List<Object> notModifiedExclusions) {
            super(response);
            this.request = request;
            this.cspName = cspName;
            this.cspValue = cspValue;
            this.allowOverride = allowOverride;
            this.notModifiedExclusions = notModifiedExclusions;
            if (notModifiedExclusions == null) {
                notModifiedExclusion = null;
            } else {
                notModifiedExclusion = Map.entry(cspName, cspValue);
                // The filter's policy is present
                notModifiedExclusions.add(notModifiedExclusion);
            }
        }


        @Override
        public void setHeader(String name, String value) {
            if (!isCspHeader(name) || isIgnoredByContainer()) {
                super.setHeader(name, value);
            } else if (allowOverride) {
                // Replaces every policy present, including the filter's policy
                super.setHeader(cspName, value);
                setFilterPolicyPresent(false);
            } else {
                // Replace the application's policies but keep the filter's policy
                applicationValues.clear();
                if (value != null) {
                    applicationValues.add(value);
                }
                writePolicies();
            }
        }


        @Override
        public void addHeader(String name, String value) {
            if (!isCspHeader(name) || value == null || isIgnoredByContainer()) {
                super.addHeader(name, value);
            } else if (!allowOverride) {
                applicationValues.add(value);
                super.addHeader(cspName, value);
            } else if (filterPolicyPresent) {
                // The first policy set by the application replaces the filter's policy
                super.setHeader(cspName, value);
                setFilterPolicyPresent(false);
            } else {
                super.addHeader(cspName, value);
            }
        }


        @Override
        public boolean containsHeader(String name) {
            if (isCspHeader(name)) {
                return !getVisibleValues().isEmpty();
            }
            return super.containsHeader(name);
        }


        @Override
        public String getHeader(String name) {
            if (isCspHeader(name)) {
                List<String> values = getVisibleValues();
                return values.isEmpty() ? null : values.get(0);
            }
            return super.getHeader(name);
        }


        @Override
        public Collection<String> getHeaders(String name) {
            if (isCspHeader(name)) {
                return getVisibleValues();
            }
            return super.getHeaders(name);
        }


        @Override
        public Collection<String> getHeaderNames() {
            Collection<String> names = super.getHeaderNames();
            if (names == null || !filterPolicyPresent) {
                return names;
            }
            /*
             * Remove the name of the filter's policy. Some containers, including Tomcat, list a name once for each
             * value. Others list each name once. Removing occurrences until no more remain than the application has
             * set values handles both.
             */
            int excess = -applicationValues.size();
            for (String name : names) {
                if (isCspHeader(name)) {
                    excess++;
                }
            }
            if (excess <= 0) {
                return names;
            }
            List<String> result = new ArrayList<>(names.size());
            for (String name : names) {
                if (excess > 0 && isCspHeader(name)) {
                    excess--;
                } else {
                    result.add(name);
                }
            }
            return result;
        }


        @Override
        public void reset() {
            super.reset();
            // Restore the filter's policy unless the container ignored the call
            if (!isIgnoredByContainer()) {
                applicationValues.clear();
                setFilterPolicyPresent(true);
                super.setHeader(cspName, cspValue);
            }
        }


        private boolean isCspHeader(String name) {
            return cspName.equalsIgnoreCase(name);
        }


        /*
         * The container ignores changes to the headers and the status, and calls to reset(), made by a resource
         * included with RequestDispatcher.include(), and changes made once the response has been committed.
         */
        private boolean isIgnoredByContainer() {
            return isCommitted() || request.getDispatcherType() == DispatcherType.INCLUDE;
        }


        /*
         * Writes the filter's policy followed by the policies set by the application. Only used if the application may
         * not override the filter's policy.
         */
        private void writePolicies() {
            super.setHeader(cspName, cspValue);
            for (String value : applicationValues) {
                super.addHeader(cspName, value);
            }
        }


        /*
         * Records whether the filter's policy is present, and keeps the list of header values the connector must not
         * send with a 304 response in step with it.
         */
        private void setFilterPolicyPresent(boolean present) {
            if (present == filterPolicyPresent) {
                return;
            }
            filterPolicyPresent = present;
            if (notModifiedExclusions != null) {
                if (present) {
                    notModifiedExclusions.add(notModifiedExclusion);
                } else {
                    // Remove this filter's entry, not an equal entry of another instance of this filter
                    notModifiedExclusions.removeIf(entry -> entry == notModifiedExclusion);
                }
            }
        }


        /*
         * The policies visible to the rest of the chain: all policies except the filter's policy.
         */
        private List<String> getVisibleValues() {
            if (!allowOverride) {
                return new ArrayList<>(applicationValues);
            }
            if (filterPolicyPresent) {
                // Until the application sets a policy, the filter's policy is the only one present
                return new ArrayList<>();
            }
            Collection<String> present = super.getHeaders(cspName);
            return present == null ? new ArrayList<>() : new ArrayList<>(present);
        }
    }
}
