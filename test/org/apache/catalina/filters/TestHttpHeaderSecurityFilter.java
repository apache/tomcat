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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Globals;
import org.apache.tomcat.unittest.TesterResponse;
import org.apache.tomcat.util.descriptor.web.FilterDef;

public class TestHttpHeaderSecurityFilter {

    // Policy wrapped over several lines, as from a multi-line <param-value> in web.xml, plus surrounding whitespace
    private static final String CSP_MULTI_LINE_POLICY =
            "  default-src 'none';\n    style-src 'self';\r\n\timg-src 'self'  ";
    private static final String CSP_MULTI_LINE_POLICY_HEADER = "default-src 'none'; style-src 'self'; img-src 'self'";

    private static final String CSP = "Content-Security-Policy";
    private static final String CSP_REPORT_ONLY = "Content-Security-Policy-Report-Only";
    private static final String CSP_FILTER_POLICY = "default-src 'self'";
    private static final String CSP_APP_POLICY = "script-src 'none'";
    private static final String CSP_APP_POLICY_2 = "img-src 'none'";
    private static final String CSP_NONCE_POLICY = "script-src 'self' 'nonce-{nonce}'";
    // 16 random bytes encoded with the standard, padded Base64 alphabet
    private static final Pattern CSP_NONCE_PATTERN = Pattern.compile("[A-Za-z0-9+/]{22}==");

    private final FilterChain filterChain = new TesterFilterChain();

    @Test
    public void testDefaultsNonSecure() throws IOException, ServletException {
        HttpServletResponse response = doFilter(new FilterDef(), false);

        Assert.assertNull(response.getHeader("Strict-Transport-Security"));
        Assert.assertEquals("DENY", response.getHeader("X-Frame-Options"));
        Assert.assertEquals("nosniff", response.getHeader("X-Content-Type-Options"));
    }

    @Test
    public void testDefaultsSecure() throws IOException, ServletException {
        HttpServletResponse response = doFilter(new FilterDef(), true);

        Assert.assertEquals("max-age=0", response.getHeader("Strict-Transport-Security"));
        Assert.assertEquals("DENY", response.getHeader("X-Frame-Options"));
        Assert.assertEquals("nosniff", response.getHeader("X-Content-Type-Options"));
    }

    @Test
    public void testHstsMaxAgeAndSubDomains() throws IOException, ServletException {
        FilterDef filterDef = new FilterDef();
        filterDef.addInitParameter("hstsMaxAgeSeconds", "63072000");
        filterDef.addInitParameter("hstsIncludeSubDomains", "true");

        HttpServletResponse response = doFilter(filterDef, true);

        Assert.assertEquals("max-age=63072000;includeSubDomains", response.getHeader("Strict-Transport-Security"));
    }

    @Test
    public void testHstsPreload() throws IOException, ServletException {
        FilterDef filterDef = new FilterDef();
        filterDef.addInitParameter("hstsMaxAgeSeconds", "63072000");
        filterDef.addInitParameter("hstsIncludeSubDomains", "true");
        filterDef.addInitParameter("hstsPreload", "true");

        HttpServletResponse response = doFilter(filterDef, true);

        Assert.assertEquals("max-age=63072000;includeSubDomains;preload", response.getHeader("Strict-Transport-Security"));
    }

    @Test
    public void testHstsDisabled() throws IOException, ServletException {
        FilterDef filterDef = new FilterDef();
        filterDef.addInitParameter("hstsEnabled", "false");

        HttpServletResponse response = doFilter(filterDef, true);

        Assert.assertNull(response.getHeader("Strict-Transport-Security"));
    }

    @Test
    public void testHstsNegativeMaxAge() throws IOException, ServletException {
        FilterDef filterDef = new FilterDef();
        filterDef.addInitParameter("hstsMaxAgeSeconds", "-1");

        HttpServletResponse response = doFilter(filterDef, true);

        Assert.assertEquals("max-age=0", response.getHeader("Strict-Transport-Security"));
    }

    @Test
    public void testAntiClickJackingSameOrigin() throws IOException, ServletException {
        FilterDef filterDef = new FilterDef();
        filterDef.addInitParameter("antiClickJackingOption", "SAMEORIGIN");

        HttpServletResponse response = doFilter(filterDef, false);

        Assert.assertEquals("SAMEORIGIN", response.getHeader("X-Frame-Options"));
    }

    @Test
    public void testAntiClickJackingAllowFrom() throws IOException, ServletException {
        FilterDef filterDef = new FilterDef();
        filterDef.addInitParameter("antiClickJackingOption", "ALLOW-FROM");
        filterDef.addInitParameter("antiClickJackingUri", "https://example.com");

        HttpServletResponse response = doFilter(filterDef, false);

        Assert.assertEquals("ALLOW-FROM https://example.com", response.getHeader("X-Frame-Options"));
    }

    @Test
    public void testAntiClickJackingDisabled() throws IOException, ServletException {
        FilterDef filterDef = new FilterDef();
        filterDef.addInitParameter("antiClickJackingEnabled", "false");

        HttpServletResponse response = doFilter(filterDef, false);

        Assert.assertNull(response.getHeader("X-Frame-Options"));
    }

    @Test
    public void testBlockContentTypeSniffingDisabled() throws IOException, ServletException {
        FilterDef filterDef = new FilterDef();
        filterDef.addInitParameter("blockContentTypeSniffingEnabled", "false");

        HttpServletResponse response = doFilter(filterDef, false);

        Assert.assertNull(response.getHeader("X-Content-Type-Options"));
    }

    @Test
    public void testAllDisabled() throws IOException, ServletException {
        FilterDef filterDef = new FilterDef();
        filterDef.addInitParameter("hstsEnabled", "false");
        filterDef.addInitParameter("antiClickJackingEnabled", "false");
        filterDef.addInitParameter("blockContentTypeSniffingEnabled", "false");

        HttpServletResponse response = doFilter(filterDef, true);

        Assert.assertNull(response.getHeader("Strict-Transport-Security"));
        Assert.assertNull(response.getHeader("X-Frame-Options"));
        Assert.assertNull(response.getHeader("X-Content-Type-Options"));
    }

    @Test(expected = ServletException.class)
    public void testAntiClickJackingInvalidOption() throws ServletException {
        FilterDef filterDef = new FilterDef();
        filterDef.addInitParameter("antiClickJackingOption", "INVALID");

        HttpHeaderSecurityFilter filter = new HttpHeaderSecurityFilter();
        filterDef.setFilterName(HttpHeaderSecurityFilter.class.getName());
        filterDef.setFilterClass(HttpHeaderSecurityFilter.class.getName());
        filter.init(TesterFilterConfigs.generateFilterConfig(filterDef));
    }

    @Test(expected = ServletException.class)
    public void testAntiClickJackingInvalidUri() throws ServletException {
        FilterDef filterDef = new FilterDef();
        filterDef.addInitParameter("antiClickJackingOption", "ALLOW-FROM");
        filterDef.addInitParameter("antiClickJackingUri", "not a valid uri :{}");

        HttpHeaderSecurityFilter filter = new HttpHeaderSecurityFilter();
        filterDef.setFilterName(HttpHeaderSecurityFilter.class.getName());
        filterDef.setFilterClass(HttpHeaderSecurityFilter.class.getName());
        filter.init(TesterFilterConfigs.generateFilterConfig(filterDef));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAntiClickJackingNoUri() throws ServletException {
        FilterDef filterDef = new FilterDef();
        filterDef.addInitParameter("antiClickJackingOption", "ALLOW-FROM");

        HttpHeaderSecurityFilter filter = new HttpHeaderSecurityFilter();
        filterDef.setFilterName(HttpHeaderSecurityFilter.class.getName());
        filterDef.setFilterClass(HttpHeaderSecurityFilter.class.getName());
        filter.init(TesterFilterConfigs.generateFilterConfig(filterDef));
    }

    @Test
    public void testCspDefaultDisabled() throws IOException, ServletException {
        HttpServletResponse response = doFilter(new FilterDef(), false);
        Assert.assertNull(response.getHeader("Content-Security-Policy"));
        Assert.assertNull(response.getHeader("Content-Security-Policy-Report-Only"));
    }

    @Test
    public void testCspEnabled() throws IOException, ServletException {
        FilterDef filterDef = new FilterDef();
        filterDef.addInitParameter("cspEnabled", "true");
        filterDef.addInitParameter("cspPolicy", "default-src 'none'");
        HttpServletResponse response = doFilter(filterDef, false);
        Assert.assertEquals("default-src 'none'", response.getHeader("Content-Security-Policy"));
        Assert.assertNull(response.getHeader("Content-Security-Policy-Report-Only"));
    }

    @Test
    public void testCspReportOnly() throws IOException, ServletException {
        FilterDef filterDef = new FilterDef();
        filterDef.addInitParameter("cspEnabled", "true");
        filterDef.addInitParameter("cspReportOnly", "true");
        filterDef.addInitParameter("cspPolicy", "default-src 'none'");
        HttpServletResponse response = doFilter(filterDef, false);
        Assert.assertEquals("default-src 'none'",
                response.getHeader("Content-Security-Policy-Report-Only"));
        Assert.assertNull(response.getHeader("Content-Security-Policy"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCspEnabledBlankPolicy() throws ServletException {
        FilterDef filterDef = new FilterDef();
        filterDef.addInitParameter("cspEnabled", "true");
        HttpHeaderSecurityFilter filter = new HttpHeaderSecurityFilter();
        filterDef.setFilterName(HttpHeaderSecurityFilter.class.getName());
        filterDef.setFilterClass(HttpHeaderSecurityFilter.class.getName());
        filter.init(TesterFilterConfigs.generateFilterConfig(filterDef));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCspEnabledWhitespaceOnlyPolicy() throws ServletException {
        FilterDef filterDef = new FilterDef();
        filterDef.addInitParameter("cspEnabled", "true");
        filterDef.addInitParameter("cspPolicy", " \n\t ");
        HttpHeaderSecurityFilter filter = new HttpHeaderSecurityFilter();
        filterDef.setFilterName(HttpHeaderSecurityFilter.class.getName());
        filterDef.setFilterClass(HttpHeaderSecurityFilter.class.getName());
        filter.init(TesterFilterConfigs.generateFilterConfig(filterDef));
    }

    @Test
    public void testCspMultiLinePolicy() throws IOException, ServletException {
        FilterDef filterDef = new FilterDef();
        filterDef.addInitParameter("cspEnabled", "true");
        filterDef.addInitParameter("cspPolicy", CSP_MULTI_LINE_POLICY);
        HttpServletResponse response = doFilter(filterDef, false);
        Assert.assertEquals(CSP_MULTI_LINE_POLICY_HEADER, response.getHeader("Content-Security-Policy"));
        Assert.assertNull(response.getHeader("Content-Security-Policy-Report-Only"));
    }

    @Test
    public void testCspReportOnlyMultiLinePolicy() throws IOException, ServletException {
        FilterDef filterDef = new FilterDef();
        filterDef.addInitParameter("cspEnabled", "true");
        filterDef.addInitParameter("cspReportOnly", "true");
        filterDef.addInitParameter("cspPolicy", CSP_MULTI_LINE_POLICY);
        HttpServletResponse response = doFilter(filterDef, false);
        Assert.assertEquals(CSP_MULTI_LINE_POLICY_HEADER,
                response.getHeader("Content-Security-Policy-Report-Only"));
        Assert.assertNull(response.getHeader("Content-Security-Policy"));
    }

    @Test
    public void testCspDisabledResponseNotWrapped() throws IOException, ServletException {
        TesterResponse response = new TesterResponse();
        AtomicReference<ServletResponse> chainResponse = new AtomicReference<>();
        createFilter(new FilterDef()).doFilter(new TesterHttpServletRequest(), response,
                (req, resp) -> chainResponse.set(resp));
        Assert.assertSame(response, chainResponse.get());
    }

    @Test
    public void testCspAllowOverrideApplicationSetsNothing() throws IOException, ServletException {
        doTestCspApplicationSetsNothing(true);
    }

    @Test
    public void testCspEnforcedApplicationSetsNothing() throws IOException, ServletException {
        doTestCspApplicationSetsNothing(false);
    }

    private void doTestCspApplicationSetsNothing(boolean allowOverride) throws IOException, ServletException {
        TesterResponse response = doCspFilter(allowOverride, resp -> {
            // The filter's policy is not visible to the application
            Assert.assertFalse(resp.containsHeader(CSP));
            Assert.assertNull(resp.getHeader(CSP));
            Assert.assertTrue(resp.getHeaders(CSP).isEmpty());
            Assert.assertEquals(0, countIgnoreCase(resp.getHeaderNames(), CSP));
            // Other headers are
            Assert.assertTrue(resp.containsHeader("X-Frame-Options"));
            Assert.assertEquals(1, countIgnoreCase(resp.getHeaderNames(), "X-Frame-Options"));
        });
        Assert.assertEquals(List.of(CSP_FILTER_POLICY), getSentValues(response, CSP));
    }

    @Test
    public void testCspAllowOverrideApplicationSetHeader() throws IOException, ServletException {
        TesterResponse response = doCspFilter(true, resp -> {
            resp.setHeader(CSP, CSP_APP_POLICY);
            Assert.assertTrue(resp.containsHeader(CSP));
            Assert.assertEquals(CSP_APP_POLICY, resp.getHeader(CSP));
            Assert.assertEquals(List.of(CSP_APP_POLICY), new ArrayList<>(resp.getHeaders(CSP)));
            Assert.assertEquals(1, countIgnoreCase(resp.getHeaderNames(), CSP));
        });
        Assert.assertEquals(List.of(CSP_APP_POLICY), getSentValues(response, CSP));
    }

    @Test
    public void testCspEnforcedApplicationSetHeader() throws IOException, ServletException {
        TesterResponse response = doCspFilter(false, resp -> {
            resp.setHeader(CSP, CSP_APP_POLICY);
            Assert.assertTrue(resp.containsHeader(CSP));
            Assert.assertEquals(CSP_APP_POLICY, resp.getHeader(CSP));
            Assert.assertEquals(List.of(CSP_APP_POLICY), new ArrayList<>(resp.getHeaders(CSP)));
            Assert.assertEquals(1, countIgnoreCase(resp.getHeaderNames(), CSP));
        });
        Assert.assertEquals(List.of(CSP_FILTER_POLICY, CSP_APP_POLICY), getSentValues(response, CSP));
    }

    @Test
    public void testCspAllowOverrideApplicationAddHeader() throws IOException, ServletException {
        TesterResponse response = doCspFilter(true, resp -> {
            resp.addHeader(CSP, CSP_APP_POLICY);
            resp.addHeader(CSP, CSP_APP_POLICY_2);
            Assert.assertEquals(List.of(CSP_APP_POLICY, CSP_APP_POLICY_2), new ArrayList<>(resp.getHeaders(CSP)));
        });
        Assert.assertEquals(List.of(CSP_APP_POLICY, CSP_APP_POLICY_2), getSentValues(response, CSP));
    }

    @Test
    public void testCspEnforcedApplicationAddHeader() throws IOException, ServletException {
        TesterResponse response = doCspFilter(false, resp -> {
            resp.addHeader(CSP, CSP_APP_POLICY);
            resp.addHeader(CSP, CSP_APP_POLICY_2);
            Assert.assertEquals(CSP_APP_POLICY, resp.getHeader(CSP));
            Assert.assertEquals(List.of(CSP_APP_POLICY, CSP_APP_POLICY_2), new ArrayList<>(resp.getHeaders(CSP)));
            Assert.assertEquals(2, countIgnoreCase(resp.getHeaderNames(), CSP));
        });
        Assert.assertEquals(List.of(CSP_FILTER_POLICY, CSP_APP_POLICY, CSP_APP_POLICY_2),
                getSentValues(response, CSP));
    }

    @Test
    public void testCspEnforcedApplicationSetHeaderReplacesApplicationPolicies() throws IOException, ServletException {
        TesterResponse response = doCspFilter(false, resp -> {
            resp.addHeader(CSP, CSP_APP_POLICY);
            resp.setHeader(CSP, CSP_APP_POLICY_2);
            Assert.assertEquals(List.of(CSP_APP_POLICY_2), new ArrayList<>(resp.getHeaders(CSP)));
        });
        Assert.assertEquals(List.of(CSP_FILTER_POLICY, CSP_APP_POLICY_2), getSentValues(response, CSP));
    }

    @Test
    public void testCspAllowOverrideApplicationSetsPolicyIfAbsent() throws IOException, ServletException {
        doTestCspApplicationSetsPolicyIfAbsent(true, List.of(CSP_APP_POLICY));
    }

    @Test
    public void testCspEnforcedApplicationSetsPolicyIfAbsent() throws IOException, ServletException {
        doTestCspApplicationSetsPolicyIfAbsent(false, List.of(CSP_FILTER_POLICY, CSP_APP_POLICY));
    }

    private void doTestCspApplicationSetsPolicyIfAbsent(boolean allowOverride, List<String> expected)
            throws IOException, ServletException {
        TesterResponse response = doCspFilter(allowOverride, resp -> {
            // Frameworks such as Spring Security only set a policy if none is present
            if (!resp.containsHeader(CSP)) {
                resp.setHeader(CSP, CSP_APP_POLICY);
            }
        });
        Assert.assertEquals(expected, getSentValues(response, CSP));
    }

    @Test
    public void testCspAllowOverrideApplicationSetHeaderNull() throws IOException, ServletException {
        TesterResponse response = doCspFilter(true, resp -> {
            resp.setHeader(CSP, null);
            Assert.assertFalse(resp.containsHeader(CSP));
        });
        // Tomcat removes a header that is set to null
        Assert.assertEquals(List.of(), getSentValues(response, CSP));
    }

    @Test
    public void testCspEnforcedApplicationSetHeaderNull() throws IOException, ServletException {
        TesterResponse response = doCspFilter(false, resp -> {
            resp.addHeader(CSP, CSP_APP_POLICY);
            resp.setHeader(CSP, null);
            Assert.assertFalse(resp.containsHeader(CSP));
            Assert.assertTrue(resp.getHeaders(CSP).isEmpty());
        });
        Assert.assertEquals(List.of(CSP_FILTER_POLICY), getSentValues(response, CSP));
    }

    @Test
    public void testCspAllowOverrideHeaderNameCaseInsensitive() throws IOException, ServletException {
        doTestCspHeaderNameCaseInsensitive(true, List.of(CSP_APP_POLICY));
    }

    @Test
    public void testCspEnforcedHeaderNameCaseInsensitive() throws IOException, ServletException {
        doTestCspHeaderNameCaseInsensitive(false, List.of(CSP_FILTER_POLICY, CSP_APP_POLICY));
    }

    private void doTestCspHeaderNameCaseInsensitive(boolean allowOverride, List<String> expected)
            throws IOException, ServletException {
        TesterResponse response = doCspFilter(allowOverride, resp -> {
            Assert.assertFalse(resp.containsHeader("content-security-policy"));
            resp.addHeader("CONTENT-SECURITY-POLICY", CSP_APP_POLICY);
            Assert.assertEquals(CSP_APP_POLICY, resp.getHeader(CSP));
        });
        Assert.assertEquals(expected, getSentValues(response, CSP));
    }

    @Test
    public void testCspAllowOverrideReset() throws IOException, ServletException {
        doTestCspReset(true, List.of(CSP_APP_POLICY_2));
    }

    @Test
    public void testCspEnforcedReset() throws IOException, ServletException {
        doTestCspReset(false, List.of(CSP_FILTER_POLICY, CSP_APP_POLICY_2));
    }

    private void doTestCspReset(boolean allowOverride, List<String> expected) throws IOException, ServletException {
        HttpHeaderSecurityFilter filter = createFilter(createCspFilterDef(CSP_FILTER_POLICY, allowOverride));
        TesterResponse response = new TesterResponse();
        doCspFilter(filter, new TesterHttpServletRequest(), response, resp -> {
            resp.setHeader(CSP, CSP_APP_POLICY);
            resp.reset();
            // The filter's policy is restored and hidden again
            Assert.assertEquals(List.of(CSP_FILTER_POLICY), getSentValues(response, CSP));
            Assert.assertFalse(resp.containsHeader(CSP));
            Assert.assertTrue(resp.getHeaders(CSP).isEmpty());
            resp.addHeader(CSP, CSP_APP_POLICY_2);
            Assert.assertEquals(List.of(CSP_APP_POLICY_2), new ArrayList<>(resp.getHeaders(CSP)));
        });
        Assert.assertEquals(expected, getSentValues(response, CSP));
    }

    @Test
    public void testCspReportOnlyApplicationHeaders() throws IOException, ServletException {
        FilterDef filterDef = createCspFilterDef(CSP_FILTER_POLICY, false);
        filterDef.addInitParameter("cspReportOnly", "true");
        TesterResponse response = doCspFilter(createFilter(filterDef), new TesterHttpServletRequest(), resp -> {
            Assert.assertFalse(resp.containsHeader(CSP_REPORT_ONLY));
            resp.addHeader(CSP_REPORT_ONLY, CSP_APP_POLICY);
            // The filter does not manage the enforced policy in report-only mode
            Assert.assertFalse(resp.containsHeader(CSP));
            resp.setHeader(CSP, CSP_APP_POLICY_2);
            Assert.assertEquals(CSP_APP_POLICY_2, resp.getHeader(CSP));
        });
        Assert.assertEquals(List.of(CSP_FILTER_POLICY, CSP_APP_POLICY), getSentValues(response, CSP_REPORT_ONLY));
        Assert.assertEquals(List.of(CSP_APP_POLICY_2), getSentValues(response, CSP));
    }

    @Test
    public void testCspAllowOverrideOtherHeadersUnaffected() throws IOException, ServletException {
        doTestCspOtherHeadersUnaffected(true);
    }

    @Test
    public void testCspEnforcedOtherHeadersUnaffected() throws IOException, ServletException {
        doTestCspOtherHeadersUnaffected(false);
    }

    private void doTestCspOtherHeadersUnaffected(boolean allowOverride) throws IOException, ServletException {
        TesterResponse response = doCspFilter(allowOverride, resp -> {
            resp.addHeader("X-Test", "a");
            resp.addHeader("X-Test", "b");
            resp.setHeader("X-Frame-Options", "SAMEORIGIN");
            // Only the header the filter sets is managed
            resp.setHeader(CSP_REPORT_ONLY, CSP_APP_POLICY);
            Assert.assertTrue(resp.containsHeader("X-Test"));
            Assert.assertEquals("a", resp.getHeader("X-Test"));
            Assert.assertEquals(List.of("a", "b"), new ArrayList<>(resp.getHeaders("X-Test")));
            Assert.assertEquals("SAMEORIGIN", resp.getHeader("X-Frame-Options"));
            Assert.assertEquals(CSP_APP_POLICY, resp.getHeader(CSP_REPORT_ONLY));
        });
        Assert.assertEquals(List.of("a", "b"), getSentValues(response, "X-Test"));
        Assert.assertEquals(List.of("SAMEORIGIN"), getSentValues(response, "X-Frame-Options"));
        Assert.assertEquals(List.of(CSP_APP_POLICY), getSentValues(response, CSP_REPORT_ONLY));
        Assert.assertEquals(List.of(CSP_FILTER_POLICY), getSentValues(response, CSP));
    }

    @Test
    public void testCspNonce() throws IOException, ServletException {
        TesterHttpServletRequest request = new TesterHttpServletRequest();
        AtomicReference<Object> chainNonce = new AtomicReference<>();
        TesterResponse response = doCspFilter(createFilter(createCspFilterDef(CSP_NONCE_POLICY, true)), request,
                resp -> chainNonce.set(request.getAttribute(Globals.CSP_NONCE_ATTR)));

        String nonce = getNonce(request);
        Assert.assertTrue(nonce, CSP_NONCE_PATTERN.matcher(nonce).matches());
        // Available to the application
        Assert.assertEquals(nonce, chainNonce.get());
        Assert.assertEquals(List.of("script-src 'self' 'nonce-" + nonce + "'"), getSentValues(response, CSP));
    }

    @Test
    public void testCspNonceNewForEachRequest() throws IOException, ServletException {
        HttpHeaderSecurityFilter filter = createFilter(createCspFilterDef(CSP_NONCE_POLICY, true));
        TesterHttpServletRequest request1 = new TesterHttpServletRequest();
        TesterResponse response1 = doCspFilter(filter, request1, resp -> {});
        TesterHttpServletRequest request2 = new TesterHttpServletRequest();
        TesterResponse response2 = doCspFilter(filter, request2, resp -> {});

        Assert.assertNotEquals(getNonce(request1), getNonce(request2));
        Assert.assertEquals(List.of("script-src 'self' 'nonce-" + getNonce(request1) + "'"),
                getSentValues(response1, CSP));
        Assert.assertEquals(List.of("script-src 'self' 'nonce-" + getNonce(request2) + "'"),
                getSentValues(response2, CSP));
    }

    @Test
    public void testCspNonceMultiplePlaceholders() throws IOException, ServletException {
        TesterHttpServletRequest request = new TesterHttpServletRequest();
        TesterResponse response = doCspFilter(
                createFilter(createCspFilterDef("script-src 'nonce-{nonce}'; style-src 'nonce-{nonce}'", true)),
                request, resp -> {});

        String nonce = getNonce(request);
        Assert.assertEquals(List.of("script-src 'nonce-" + nonce + "'; style-src 'nonce-" + nonce + "'"),
                getSentValues(response, CSP));
    }

    @Test
    public void testCspNoNoncePlaceholder() throws IOException, ServletException {
        TesterHttpServletRequest request = new TesterHttpServletRequest();
        TesterResponse response =
                doCspFilter(createFilter(createCspFilterDef(CSP_FILTER_POLICY, true)), request, resp -> {});

        Assert.assertNull(request.getAttribute(Globals.CSP_NONCE_ATTR));
        Assert.assertEquals(List.of(CSP_FILTER_POLICY), getSentValues(response, CSP));
    }

    @Test
    public void testCspNonceReused() throws IOException, ServletException {
        TesterHttpServletRequest request = new TesterHttpServletRequest();
        request.setAttribute(Globals.CSP_NONCE_ATTR, "c2V0IGVhcmxpZXI=");
        TesterResponse response =
                doCspFilter(createFilter(createCspFilterDef(CSP_NONCE_POLICY, true)), request, resp -> {});

        Assert.assertEquals("c2V0IGVhcmxpZXI=", request.getAttribute(Globals.CSP_NONCE_ATTR));
        Assert.assertEquals(List.of("script-src 'self' 'nonce-c2V0IGVhcmxpZXI='"), getSentValues(response, CSP));
    }

    @Test
    public void testCspNonceEmptyNotReused() throws IOException, ServletException {
        TesterHttpServletRequest request = new TesterHttpServletRequest();
        request.setAttribute(Globals.CSP_NONCE_ATTR, "");
        TesterResponse response =
                doCspFilter(createFilter(createCspFilterDef(CSP_NONCE_POLICY, true)), request, resp -> {});

        String nonce = getNonce(request);
        Assert.assertTrue(nonce, CSP_NONCE_PATTERN.matcher(nonce).matches());
        Assert.assertEquals(List.of("script-src 'self' 'nonce-" + nonce + "'"), getSentValues(response, CSP));
    }

    @Test
    public void testCspMultipleFilters() throws IOException, ServletException {
        // Two definitions of the filter mapped to overlapping URL patterns. The outer policy is always sent.
        HttpHeaderSecurityFilter outer =
                createFilter(createCspFilterDef("default-src 'none'; script-src 'nonce-{nonce}'", false));
        HttpHeaderSecurityFilter inner =
                createFilter(createCspFilterDef("default-src 'self'; style-src 'nonce-{nonce}'", true));
        TesterHttpServletRequest request = new TesterHttpServletRequest();
        TesterResponse response = new TesterResponse();
        AtomicBoolean innerChainInvoked = new AtomicBoolean();
        outer.doFilter(request, response, (req, resp) -> inner.doFilter(req, resp, (req2, resp2) -> {
            Assert.assertFalse(((HttpServletResponse) resp2).containsHeader(CSP));
            innerChainInvoked.set(true);
        }));
        Assert.assertTrue(innerChainInvoked.get());

        // Both policies accept the same nonce
        String nonce = getNonce(request);
        Assert.assertEquals(List.of("default-src 'none'; script-src 'nonce-" + nonce + "'",
                "default-src 'self'; style-src 'nonce-" + nonce + "'"), getSentValues(response, CSP));
    }

    private HttpServletResponse doFilter(FilterDef filterDef, boolean secure)
        throws ServletException, IOException {
        TesterHttpServletRequest request = new TesterHttpServletRequest();
        request.setSecure(secure);
        TesterHttpServletResponse response = new TesterHttpServletResponse();

        HttpHeaderSecurityFilter filter = createFilter(filterDef);
        filter.doFilter(request, response, filterChain);

        return response;
    }

    private static HttpHeaderSecurityFilter createFilter(FilterDef filterDef) throws ServletException {
        HttpHeaderSecurityFilter filter = new HttpHeaderSecurityFilter();
        filterDef.setFilterName(HttpHeaderSecurityFilter.class.getName());
        filterDef.setFilterClass(HttpHeaderSecurityFilter.class.getName());
        filter.init(TesterFilterConfigs.generateFilterConfig(filterDef));
        return filter;
    }

    private static FilterDef createCspFilterDef(String policy, boolean allowOverride) {
        FilterDef filterDef = new FilterDef();
        filterDef.addInitParameter("cspEnabled", "true");
        filterDef.addInitParameter("cspPolicy", policy);
        filterDef.addInitParameter("cspAllowOverride", Boolean.toString(allowOverride));
        return filterDef;
    }

    private static TesterResponse doCspFilter(boolean allowOverride, ApplicationAction action)
            throws IOException, ServletException {
        return doCspFilter(createFilter(createCspFilterDef(CSP_FILTER_POLICY, allowOverride)),
                new TesterHttpServletRequest(), action);
    }

    private static TesterResponse doCspFilter(HttpHeaderSecurityFilter filter, ServletRequest request,
            ApplicationAction action) throws IOException, ServletException {
        TesterResponse response = new TesterResponse();
        doCspFilter(filter, request, response, action);
        return response;
    }

    private static void doCspFilter(HttpHeaderSecurityFilter filter, ServletRequest request, TesterResponse response,
            ApplicationAction action) throws IOException, ServletException {
        AtomicBoolean actionRun = new AtomicBoolean();
        filter.doFilter(request, response, (req, resp) -> {
            action.run((HttpServletResponse) resp);
            actionRun.set(true);
        });
        Assert.assertTrue(actionRun.get());
    }

    /*
     * Returns every value of the header that will be sent to the client, in order and including duplicates.
     * HttpServletResponse.getHeaders() removes duplicates.
     */
    private static List<String> getSentValues(TesterResponse response, String name) {
        return Collections.list(response.getCoyoteResponse().getMimeHeaders().values(name));
    }

    private static int countIgnoreCase(Collection<String> names, String name) {
        int count = 0;
        for (String n : names) {
            if (n.equalsIgnoreCase(name)) {
                count++;
            }
        }
        return count;
    }

    private static String getNonce(ServletRequest request) {
        Object nonce = request.getAttribute(Globals.CSP_NONCE_ATTR);
        Assert.assertTrue(nonce instanceof String);
        return (String) nonce;
    }

    /*
     * The application code that the filter chain runs after the filter.
     */
    @FunctionalInterface
    private interface ApplicationAction {
        void run(HttpServletResponse response) throws IOException;
    }



}
