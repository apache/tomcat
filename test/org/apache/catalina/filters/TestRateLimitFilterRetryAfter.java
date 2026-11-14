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

import jakarta.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.filters.TestRemoteIpFilter.MockFilterChain;
import org.apache.catalina.filters.TestRemoteIpFilter.MockHttpServletRequest;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.catalina.util.ExactRateLimiter;
import org.apache.catalina.util.FastRateLimiter;
import org.apache.catalina.util.RateLimiterBase;
import org.apache.tomcat.unittest.TesterResponse;
import org.apache.tomcat.unittest.TesterResponseWithStatus;
import org.apache.tomcat.util.descriptor.web.FilterDef;
import org.apache.tomcat.util.descriptor.web.FilterMap;

public class TestRateLimitFilterRetryAfter extends TomcatBaseTest {

    // Long enough for the time bucket not to change while a test runs
    private static final int LONG_DURATION = 3600;

    // Short enough to wait for the time bucket to change
    private static final int SHORT_DURATION = 1;


    /*
     * Counts the request in the time bucket that follows the one in which the filter read the time, and goes over the
     * limit.
     */
    public static class CrossingExactRateLimiter extends ExactRateLimiter {

        @Override
        public int increment(String identifier) {
            waitForNextBucket(this);
            super.increment(identifier);
            return super.increment(identifier);
        }
    }


    public static class CrossingFastRateLimiter extends FastRateLimiter {

        @Override
        public int increment(String identifier) {
            waitForNextBucket(this);
            super.increment(identifier);
            return super.increment(identifier);
        }
    }


    public static class UnknownDelayRateLimiter extends ExactRateLimiter {

        @Override
        public int getRetryAfter(long timestamp) {
            return -1;
        }
    }


    private static void waitForNextBucket(RateLimiterBase limiter) {
        try {
            Thread.sleep(limiter.getBucketCounter().getMillisUntilNextBucket() + 20);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }


    @Test
    public void testExactRetryAfterSent() throws Exception {
        TesterResponse response = rejectedResponse(ExactRateLimiter.class, LONG_DURATION, 429);
        assertRetryAfter(response, LONG_DURATION);
    }


    @Test
    public void testFastRetryAfterSent() throws Exception {
        TesterResponse response = rejectedResponse(FastRateLimiter.class, LONG_DURATION, 429);
        // 3600s is rounded up to 4194.304s
        assertRetryAfter(response, 4195);
    }


    @Test
    public void testNoRetryAfterWithOtherStatusCode() throws Exception {
        TesterResponse response = rejectedResponse(ExactRateLimiter.class, LONG_DURATION,
                HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        Assert.assertNull(response.getHeader("Retry-After"));
    }


    @Test
    public void testNoRetryAfterWhenTheDelayIsNotKnown() throws Exception {
        TesterResponse response = rejectedResponse(UnknownDelayRateLimiter.class, LONG_DURATION, 429);
        Assert.assertNull(response.getHeader("Retry-After"));
    }


    /*
     * The time bucket changed after the filter read the time, so the request may have been counted in either bucket.
     * Retry-After 1 would be too short if it was the new one.
     */
    @Test
    public void testNoRetryAfterWhenTheBucketChangesWhileCountingExact() throws Exception {
        TesterResponse response = rejectedResponse(CrossingExactRateLimiter.class, SHORT_DURATION, 429);
        Assert.assertNull(response.getHeader("Retry-After"));
    }


    @Test
    public void testNoRetryAfterWhenTheBucketChangesWhileCountingFast() throws Exception {
        TesterResponse response = rejectedResponse(CrossingFastRateLimiter.class, SHORT_DURATION, 429);
        Assert.assertNull(response.getHeader("Retry-After"));
    }


    private static void assertRetryAfter(TesterResponse response, int maxSeconds) {
        String retryAfter = response.getHeader("Retry-After");
        Assert.assertNotNull(retryAfter);
        int seconds = Integer.parseInt(retryAfter);
        Assert.assertTrue(retryAfter, seconds >= 1 && seconds <= maxSeconds);
    }


    /*
     * With a limit of one request, the second request is rejected.
     */
    private TesterResponse rejectedResponse(Class<? extends RateLimiterBase> limiter, int duration, int statusCode)
            throws Exception {
        FilterDef filterDef = new FilterDef();
        filterDef.addInitParameter("bucketRequests", "1");
        filterDef.addInitParameter("bucketDuration", String.valueOf(duration));
        filterDef.addInitParameter("exposeHeaders", "true");
        filterDef.addInitParameter("statusCode", String.valueOf(statusCode));
        filterDef.addInitParameter("rateLimitClassName", limiter.getName());

        Tomcat tomcat = getTomcatInstance();
        Context root = tomcat.addContext("", TEMP_DIR);

        RateLimitFilter filter = new RateLimitFilter();
        filterDef.setFilterClass(RateLimitFilter.class.getName());
        filterDef.setFilter(filter);
        filterDef.setFilterName(RateLimitFilter.class.getName());
        root.addFilterDef(filterDef);

        FilterMap filterMap = new FilterMap();
        filterMap.setFilterName(RateLimitFilter.class.getName());
        filterMap.addURLPattern("*");
        root.addFilterMap(filterMap);

        tomcat.start();

        TesterResponse response = null;
        for (int i = 0; i < 2; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRemoteAddr("10.20.20.5");
            response = new TesterResponseWithStatus();
            response.setRequest(request);
            filter.doFilter(request, response, new MockFilterChain());
        }
        Assert.assertEquals(statusCode, response.getStatus());
        return response;
    }
}
