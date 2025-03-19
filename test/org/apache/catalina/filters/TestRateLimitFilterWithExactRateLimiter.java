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

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.filters.TestRemoteIpFilter.MockFilterChain;
import org.apache.catalina.filters.TestRemoteIpFilter.MockHttpServletRequest;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.catalina.util.ExactRateLimiter;
import org.apache.tomcat.unittest.TesterResponse;
import org.apache.tomcat.util.descriptor.web.FilterDef;
import org.apache.tomcat.util.descriptor.web.FilterMap;

public class TestRateLimitFilterWithExactRateLimiter extends TomcatBaseTest {
    private void testRateLimitWith1Clients(boolean exposeHeaders, boolean enforce) throws Exception {

        int bucketRequests = 40;
        int bucketDuration = 4;

        FilterDef filterDef = new FilterDef();
        filterDef.addInitParameter("bucketRequests", String.valueOf(bucketRequests));
        filterDef.addInitParameter("bucketDuration", String.valueOf(bucketDuration));
        filterDef.addInitParameter("enforce", String.valueOf(enforce));
        filterDef.addInitParameter("exposeHeaders", String.valueOf(exposeHeaders));
        filterDef.addInitParameter("rateLimitClassName", "org.apache.catalina.util.ExactRateLimiter");

        Tomcat tomcat = getTomcatInstance();
        Context root = tomcat.addContext("", TEMP_DIR);

        MockFilterChain filterChain = new MockFilterChain();
        RateLimitFilter rateLimitFilter = testRateLimitFilter(filterDef, root);
        tomcat.start();

        ExactRateLimiter exactRateLimiter = (ExactRateLimiter) rateLimitFilter.rateLimiter;

        int allowedRequests = exactRateLimiter.getRequests();
        long sleepTime = exactRateLimiter.getBucketCounter().getMillisUntilNextBucket();
        System.out.printf("Sleeping %d millis for the next time bucket to start\n", Long.valueOf(sleepTime));
        Thread.sleep(sleepTime);

        TestClient tc1 = new TestClient(rateLimitFilter, filterChain, "10.20.20.5", 50, 5); // TPS: 5
        TestClient tc2 = new TestClient(rateLimitFilter, filterChain, "10.20.20.10", 100, 10); // TPS: 10

        TestClient tc3 = new TestClient(rateLimitFilter, filterChain, "10.20.20.20", 200, 20); // TPS: 20
        TestClient tc4 = new TestClient(rateLimitFilter, filterChain, "10.20.20.40", 400, 40); // TPS: 40
        tc1.join();
        tc2.join();
        tc3.join();
        tc4.join();
        Assert.assertEquals(200, tc1.results[24]); // only 25 requests made in 5 seconds, all allowed

        Assert.assertEquals(200, tc2.results[49]); // only 50 requests made in 5 seconds, all allowed

        Assert.assertEquals(200, tc3.results[39]); // first allowedRequests allowed

        if (enforce) {
            Assert.assertEquals(429, tc3.results[allowedRequests]); // subsequent requests dropped
        } else {
            Assert.assertEquals(200, tc3.results[allowedRequests]);
        }

        Assert.assertEquals(200, tc4.results[allowedRequests - 1]); // first allowedRequests allowed

        if (enforce) {
            Assert.assertEquals(429, tc4.results[allowedRequests]); // subsequent requests dropped
        } else {
            Assert.assertEquals(200, tc4.results[allowedRequests]);
        }

        if (exposeHeaders) {
            Assert.assertTrue(tc3.rlpHeader[24].contains("q=" + allowedRequests));
            Assert.assertTrue(tc3.rlpHeader[allowedRequests].contains("q=" + allowedRequests));
            if (enforce) {
                Assert.assertTrue(tc3.rlHeader[24].contains("r="));
                Assert.assertFalse(tc3.rlHeader[24].contains("r=0"));
                Assert.assertTrue(tc3.rlHeader[allowedRequests].contains("r=0"));
            }
        } else {
            Assert.assertTrue(tc3.rlpHeader[24] == null);
            Assert.assertTrue(tc3.rlHeader[24] == null);
            Assert.assertTrue(tc3.rlpHeader[allowedRequests] == null);
            Assert.assertTrue(tc3.rlHeader[allowedRequests] == null);
        }
        tomcat.stop();
    }

    @Test
    public void testExposeHeaderAndReferenceRateLimitWith4Clients() throws Exception {
        testRateLimitWith1Clients(true, false);
    }

    @Test
    public void testUnexposeHeaderAndReferenceRateLimitWith4Clients() throws Exception {
        testRateLimitWith1Clients(false, false);
    }

    @Test
    public void testExposeHeaderAndEnforceRateLimitWith4Clients() throws Exception {
        testRateLimitWith1Clients(true, true);
    }

    @Test
    public void testUnexposeHeaderAndEnforceRateLimitWith4Clients() throws Exception {
        testRateLimitWith1Clients(false, true);
    }

    private RateLimitFilter testRateLimitFilter(FilterDef filterDef, Context root) {

        RateLimitFilter rateLimitFilter = new RateLimitFilter();
        filterDef.setFilterClass(RateLimitFilter.class.getName());
        filterDef.setFilter(rateLimitFilter);
        filterDef.setFilterName(RateLimitFilter.class.getName());
        root.addFilterDef(filterDef);

        FilterMap filterMap = new FilterMap();
        filterMap.setFilterName(RateLimitFilter.class.getName());
        filterMap.addURLPatternDecoded("*");
        root.addFilterMap(filterMap);

        return rateLimitFilter;
    }

    static class TestClient extends Thread {
        RateLimitFilter filter;
        FilterChain filterChain;
        String ip;

        int requests;
        int timePerRequest;

        int[] results;
        volatile String[] rlpHeader;
        volatile String[] rlHeader;

        TestClient(RateLimitFilter filter, FilterChain filterChain, String ip, int requests, int rps) {
            this.filter = filter;
            this.filterChain = filterChain;
            this.ip = ip;
            this.requests = requests;
            this.timePerRequest = 1000 / rps;
            this.results = new int[requests];
            this.rlpHeader = new String[requests];
            this.rlHeader = new String[requests];
            super.setDaemon(true);
            super.start();
        }

        @Override
        public void run() {
            long start = System.nanoTime();

            try {
                for (int i = 0; i < requests; i++) {
                    MockHttpServletRequest request = new MockHttpServletRequest();
                    request.setRemoteAddr(ip);
                    TesterResponse response = new TesterResponseWithStatus();
                    response.setRequest(request);
                    filter.doFilter(request, response, filterChain);
                    results[i] = response.getStatus();

                    rlpHeader[i] = response.getHeader(RateLimitFilter.HEADER_RATE_LIMIT_POLICY);
                    rlHeader[i] = response.getHeader(RateLimitFilter.HEADER_RATE_LIMIT);

                    if (results[i] != 200) {
                        break;
                    }
                    /*
                     * Ensure requests are evenly spaced through time irrespective of how long each request takes to
                     * complete. Do comparisons in milliseconds.
                     */
                    long expectedDuration = (i + 1) * timePerRequest;
                    long duration = (System.nanoTime() - start)/1000000;
                    if (expectedDuration > duration) {
                        sleep(expectedDuration - duration);
                    }
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    static class TesterResponseWithStatus extends TesterResponse {

        int status = 200;
        String message = "OK";

        @Override
        public void sendError(int status, String message) throws IOException {
            this.status = status;
            this.message = message;
        }

        @Override
        public int getStatus() {
            return status;
        }
    }
}
