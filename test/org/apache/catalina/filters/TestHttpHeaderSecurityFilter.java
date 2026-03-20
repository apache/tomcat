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
import jakarta.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Test;

import org.apache.tomcat.util.descriptor.web.FilterDef;

public class TestHttpHeaderSecurityFilter {

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

    private HttpServletResponse doFilter(FilterDef filterDef, boolean secure)
        throws ServletException, IOException {
        TesterHttpServletRequest request = new TesterHttpServletRequest();
        request.setSecure(secure);
        TesterHttpServletResponse response = new TesterHttpServletResponse();

        HttpHeaderSecurityFilter filter = new HttpHeaderSecurityFilter();
        filterDef.setFilterName(HttpHeaderSecurityFilter.class.getName());
        filterDef.setFilterClass(HttpHeaderSecurityFilter.class.getName());
        filter.init(TesterFilterConfigs.generateFilterConfig(filterDef));
        filter.doFilter(request, response, filterChain);

        return response;
    }



}
