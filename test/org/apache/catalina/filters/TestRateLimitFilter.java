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

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.filters.TestRemoteIpFilter.MockFilterChain;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.catalina.util.FastRateLimiter;
import org.apache.tomcat.util.descriptor.web.FilterDef;
import org.apache.tomcat.util.descriptor.web.FilterMap;

public class TestRateLimitFilter extends TomcatBaseTest {

    private void testRateLimitWith4Clients(boolean exposeHeaders, boolean enforce) throws Exception {

        int bucketRequests = 40;
        int bucketDuration = 4;

        FilterDef filterDef = new FilterDef();
        filterDef.addInitParameter("bucketRequests", String.valueOf(bucketRequests));
        filterDef.addInitParameter("bucketDuration", String.valueOf(bucketDuration));
        filterDef.addInitParameter("enforce", String.valueOf(enforce));
        filterDef.addInitParameter("exposeHeaders", String.valueOf(exposeHeaders));

        Tomcat tomcat = getTomcatInstance();
        Context root = tomcat.addContext("", TEMP_DIR);

        MockFilterChain filterChain = new MockFilterChain();
        RateLimitFilter rateLimitFilter = testRateLimitFilter(filterDef, root);

        tomcat.start();

        FastRateLimiter fastRateLimiter = (FastRateLimiter) rateLimitFilter.rateLimiter;

        int allowedRequests = fastRateLimiter.getRequests();
        long sleepTime = fastRateLimiter.getBucketCounter().getMillisUntilNextBucket();
        System.out.printf("Sleeping %d millis for the next time bucket to start\n", Long.valueOf(sleepTime));
        Thread.sleep(sleepTime);

        TestClient tc1 = new TestClient(rateLimitFilter, filterChain, "10.20.20.5", 200, 5);
        TestClient tc2 = new TestClient(rateLimitFilter, filterChain, "10.20.20.10", 200, 10);

        TestClient tc3 = new TestClient(rateLimitFilter, filterChain, "10.20.20.20", 200, 20);
        TestClient tc4 = new TestClient(rateLimitFilter, filterChain, "10.20.20.40", 200, 40);

        // Sleep for up to 10s for clients to complete
        int count = 0;
        while (count < 100 && (tc1.results[24] == 0 || tc2.results[49] == 0 || tc3.results[allowedRequests - 1] == 0 ||
                tc3.results[allowedRequests] == 0 || tc4.results[allowedRequests - 1] == 0 ||
                tc4.results[allowedRequests] == 0)) {
            Thread.sleep(100);
            count++;
        }

        // only 25 requests made, all allowed
        Assert.assertEquals(HttpServletResponse.SC_OK, tc1.results[24]);

        // only 25 requests made, all allowed
        Assert.assertEquals(HttpServletResponse.SC_OK, tc2.results[49]);

        // first allowedRequests allowed
        Assert.assertEquals(HttpServletResponse.SC_OK, tc3.results[allowedRequests - 1]);

        // first allowedRequests allowed
        Assert.assertEquals(HttpServletResponse.SC_OK, tc4.results[allowedRequests - 1]);
        if (enforce) {
            // subsequent requests dropped
            Assert.assertEquals(429, tc3.results[allowedRequests]);
            // subsequent requests dropped
            Assert.assertEquals(429, tc4.results[allowedRequests]);
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
    }

    @Test
    public void testExposeHeaderAndUnenforcedRateLimitWith4Clients() throws Exception {
        testRateLimitWith4Clients(true, false);
    }

    @Test
    public void testUnexposeHeaderAndUnenforcedRateLimitWith4Clients() throws Exception {
        testRateLimitWith4Clients(false, false);
    }

    @Test
    public void testExposeHeaderAndEnforcedRateLimitWith4Clients() throws Exception {
        testRateLimitWith4Clients(true, true);
    }

    @Test
    public void testUnexposeHeaderAndEnforcedRateLimitWith4Clients() throws Exception {
        testRateLimitWith4Clients(false, true);
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

    static class TestClient extends TesterRateLimitClientBase {

        int sleep;

        TestClient(RateLimitFilter filter, FilterChain filterChain, String ip, int requests, int rps) {
            super(filter, filterChain, ip, requests);
            this.sleep = 1000 / rps;
        }

        @Override
        void waitForNextRequest(long start, int requestIndex) throws Exception {
            sleep(sleep);
        }
    }
}
