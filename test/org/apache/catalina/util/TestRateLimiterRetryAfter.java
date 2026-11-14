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
package org.apache.catalina.util;

import java.util.Collections;
import java.util.Enumeration;

import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletContext;

import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import org.apache.tomcat.unittest.TesterServletContext;

public class TestRateLimiterRetryAfter {

    private RateLimiterBase limiter;


    @After
    public void tearDown() {
        if (limiter != null) {
            limiter.destroy();
        }
    }


    private RateLimiterBase newLimiter(RateLimiterBase base, int duration) {
        base.setDuration(duration);
        base.setRequests(10);
        base.setFilterConfig(new TesterFilterConfig());
        limiter = base;
        return base;
    }


    /*
     * The time bucket can change between reading the time and getting the delay, in which case the delay is not known.
     * Buckets of this length make that very unlikely, so try again if it happens.
     */
    private static int getRetryAfter(RateLimiter limiter) {
        int retryAfter = -1;
        for (int i = 0; i < 3 && retryAfter == -1; i++) {
            retryAfter = limiter.getRetryAfter(System.currentTimeMillis());
        }
        return retryAfter;
    }


    @Test
    public void testFastRateLimiterWithinTheBucketDuration() {
        RateLimiterBase rl = newLimiter(new FastRateLimiter(), 60);
        int retryAfter = getRetryAfter(rl);
        // 60s is rounded up to 65.536s
        Assert.assertTrue(Integer.toString(retryAfter), retryAfter >= 1 && retryAfter <= 66);
    }


    @Test
    public void testExactRateLimiterWithinTheBucketDuration() {
        RateLimiterBase rl = newLimiter(new ExactRateLimiter(), 60);
        int retryAfter = getRetryAfter(rl);
        Assert.assertTrue(Integer.toString(retryAfter), retryAfter >= 1 && retryAfter <= 60);
    }


    @Test
    public void testRoundsUpSoTheClientNeverRetriesBeforeTheBucketEnds() {
        RateLimiterBase rl = newLimiter(new ExactRateLimiter(), 3600);
        long start = System.currentTimeMillis();
        int retryAfter = getRetryAfter(rl);
        long untilNext = rl.getBucketCounter().getMillisUntilNextBucket();
        long elapsed = System.currentTimeMillis() - start;
        Assert.assertTrue(retryAfter >= 1);
        // The comparison below is not valid if the bucket ended since the delay was calculated
        TimeBucketCounterBase counter = rl.getBucketCounter();
        Assume.assumeTrue(counter.getBucketIndex(start) == counter.getCurrentBucketPrefix());
        Assert.assertTrue(retryAfter * 1000L >= untilNext);
        Assert.assertTrue(retryAfter * 1000L < untilNext + elapsed + 1000);
    }


    /*
     * If the bucket changed after the request was counted it is not known which bucket counted it, so the delay is not
     * known.
     */
    @Test
    public void testExactBucketChangedWhileCounting() {
        RateLimiterBase rl = newLimiter(new ExactRateLimiter(), 60);
        Assert.assertEquals(-1, rl.getRetryAfter(System.currentTimeMillis() - 60_000));
    }


    @Test
    public void testFastBucketChangedWhileCounting() {
        RateLimiterBase rl = newLimiter(new FastRateLimiter(), 60);
        Assert.assertEquals(-1, rl.getRetryAfter(System.currentTimeMillis() - 66_000));
    }


    @Test
    public void testDefaultImplementationDoesNotKnowTheDelay() {
        // A RateLimiter that does not extend RateLimiterBase
        RateLimiter minimal = new RateLimiter() {
            @Override
            public int getDuration() {
                return 1;
            }

            @Override
            public void setDuration(int duration) {
            }

            @Override
            public int getRequests() {
                return 1;
            }

            @Override
            public void setRequests(int requests) {
            }

            @Override
            public int increment(String identifier) {
                return 1;
            }

            @Override
            public void destroy() {
            }

            @Override
            public void setFilterConfig(FilterConfig filterConfig) {
            }

            @Override
            public String getPolicyName() {
                return "minimal";
            }

            @Override
            public void setPolicyName(String name) {
            }
        };
        Assert.assertEquals(-1, minimal.getRetryAfter(System.currentTimeMillis()));
    }


    private static class TesterFilterConfig implements FilterConfig {

        private final ServletContext context = new TesterServletContext();

        @Override
        public String getFilterName() {
            return "test";
        }

        @Override
        public ServletContext getServletContext() {
            return context;
        }

        @Override
        public String getInitParameter(String name) {
            return null;
        }

        @Override
        public Enumeration<String> getInitParameterNames() {
            return Collections.emptyEnumeration();
        }
    }
}
