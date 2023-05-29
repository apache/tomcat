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

import jakarta.servlet.Filter;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.connector.Request;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.unittest.TesterResponse;
import org.apache.tomcat.util.descriptor.web.FilterDef;
import org.apache.tomcat.util.descriptor.web.FilterMap;

public class TestRemoteCIDRFilter extends TomcatBaseTest {

    @Test
    public void testAllowOnly() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        Context root = tomcat.addContext("", TEMP_DIR);
        tomcat.start();

        TestRemoteIpFilter.MockFilterChain filterChain = new TestRemoteIpFilter.MockFilterChain();

        FilterDef filterDef = new FilterDef();
        filterDef.addInitParameter("allow", "192.168.10.0/24, 192.168.20.0/24");
        Filter filter = createTestFilter(filterDef, RemoteCIDRFilter.class, root, "*");

        String ipAddr;
        Request request;
        TesterResponse response;
        int expected;

        for (int i = 0; i < 256; i++) {
            for (int j = 0; j < 256; j += 11) {
                ipAddr = String.format("192.168.%s.%s", Integer.valueOf(i), Integer.valueOf(j));
                request = new TestRemoteIpFilter.MockHttpServletRequest(ipAddr);
                response = new TestRateLimitFilter.TesterResponseWithStatus();
                expected = (i == 10 || i == 20) ? HttpServletResponse.SC_OK : HttpServletResponse.SC_FORBIDDEN;
                filter.doFilter(request, response, filterChain);
                Assert.assertEquals(expected, response.getStatus());
            }
        }
    }

    @Test
    public void testDenyOnly() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        Context root = tomcat.addContext("", TEMP_DIR);
        tomcat.start();

        TestRemoteIpFilter.MockFilterChain filterChain = new TestRemoteIpFilter.MockFilterChain();

        FilterDef filterDef = new FilterDef();
        filterDef.addInitParameter("deny", "192.168.10.0/24, 192.168.20.0/24");
        Filter filter = createTestFilter(filterDef, RemoteCIDRFilter.class, root, "*");

        String ipAddr;
        Request request;
        TesterResponse response;
        int expected;

        for (int i = 0; i < 256; i++) {
            for (int j = 0; j < 256; j += 11) {
                ipAddr = String.format("192.168.%s.%s", Integer.valueOf(i), Integer.valueOf(j));
                request = new TestRemoteIpFilter.MockHttpServletRequest(ipAddr);
                response = new TestRateLimitFilter.TesterResponseWithStatus();
                expected = (i != 10 && i != 20) ? HttpServletResponse.SC_OK : HttpServletResponse.SC_FORBIDDEN;
                filter.doFilter(request, response, filterChain);
                Assert.assertEquals(expected, response.getStatus());
            }
        }
    }

    @Test
    public void testAllowDeny() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        Context root = tomcat.addContext("", TEMP_DIR);
        tomcat.start();

        TestRemoteIpFilter.MockFilterChain filterChain = new TestRemoteIpFilter.MockFilterChain();

        FilterDef filterDef = new FilterDef();
        filterDef.addInitParameter("allow", "10.10.0.0/16");
        filterDef.addInitParameter("deny", "10.10.10.0/24, 10.10.20.0/24");
        Filter filter = createTestFilter(filterDef, RemoteCIDRFilter.class, root, "*");

        String ipAddr;
        Request request;
        TesterResponse response;
        int expected;

        for (int i = 0; i < 256; i++) {
            for (int j = 0; j < 256; j += 11) {
                ipAddr = String.format("10.10.%s.%s", Integer.valueOf(i), Integer.valueOf(j));
                request = new TestRemoteIpFilter.MockHttpServletRequest(ipAddr);
                response = new TestRateLimitFilter.TesterResponseWithStatus();
                expected = (i != 10 && i != 20) ? HttpServletResponse.SC_OK : HttpServletResponse.SC_FORBIDDEN;
                filter.doFilter(request, response, filterChain);
                Assert.assertEquals(expected, response.getStatus());
            }
        }
    }

    private Filter createTestFilter(FilterDef filterDef, Class<?> testFilterClass, Context root, String urlPattern)
            throws ServletException {

        RemoteCIDRFilter remoteCIDRFilter = new RemoteCIDRFilter();

        filterDef.setFilterClass(testFilterClass.getName());
        filterDef.setFilterName(testFilterClass.getName());
        filterDef.setFilter(remoteCIDRFilter);
        root.addFilterDef(filterDef);

        root.addFilterDef(filterDef);

        FilterMap filterMap = new FilterMap();
        filterMap.setFilterName(testFilterClass.getName());
        filterMap.addURLPatternDecoded(urlPattern);
        root.addFilterMap(filterMap);

        FilterConfig filterConfig = TesterFilterConfigs.generateFilterConfig(filterDef);

        remoteCIDRFilter.init(filterConfig);

        return remoteCIDRFilter;
    }

}
