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

import java.util.Date;

import org.apache.catalina.Context;
import org.apache.catalina.filters.TestRateLimitFilterWithExactRateLimiter.TesterResponseWithStatus;
import org.apache.catalina.filters.TestRemoteIpFilter.MockFilterChain;
import org.apache.catalina.filters.TestRemoteIpFilter.MockHttpServletRequest;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.unittest.TesterResponse;
import org.apache.tomcat.util.descriptor.web.FilterDef;
import org.apache.tomcat.util.descriptor.web.FilterMap;
import org.junit.Test;

import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;

public class TesterRateLimitFilterPerformance extends TomcatBaseTest {

    @Test
    public void testLimiterPerformance() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        Context root = tomcat.addContext("", TEMP_DIR);
        int tps = 0, times = 3, durationUnit = 4, durationMaxRequests = 120, runnerSeconds = 60;

        int fastLimiterThroughout = 0;
        int exactLimiterThroughout = 0;
        int noLimiterThroughout = 0;

        tps = 0;
        for (int i = 0; i < times; i++) {
            tomcat.start();
            tps += runIt(root, null, durationMaxRequests, durationUnit, runnerSeconds);
            tomcat.stop();
        }
        noLimiterThroughout = tps / times;

        tps = 0;
        for (int i = 0; i < times; i++) {
            tomcat.start();
            tps += runIt(root, "org.apache.catalina.util.FastRateLimiter", durationMaxRequests, durationUnit,
                    runnerSeconds);
            tomcat.stop();
        }
        fastLimiterThroughout = tps / times;

        tps = 0;
        for (int i = 0; i < times; i++) {
            tomcat.start();
            tps += runIt(root, "org.apache.catalina.util.ExactRateLimiter", durationMaxRequests, durationUnit,
                    runnerSeconds);
            tomcat.stop();
        }
        exactLimiterThroughout = tps / times;


        System.out.println(new Date() + "\tNo Limiter TPS:\t" + noLimiterThroughout + "\tFast Limiter TPS:\t" +
                fastLimiterThroughout + "\tExact Limiter TPS:\t" + exactLimiterThroughout);
    }

    int runIt(Context root, String limiterImplClz, int bucketRequests, int bucketDuration, int totalSeconds)
            throws Exception {
        FilterDef filterDef = new FilterDef();
        filterDef.addInitParameter("bucketRequests", String.valueOf(bucketRequests));
        filterDef.addInitParameter("bucketDuration", String.valueOf(bucketDuration));
        filterDef.addInitParameter("enforce", String.valueOf(true));
        filterDef.addInitParameter("exposeHeaders", String.valueOf(false));
        filterDef.addInitParameter("rateLimiterClassName", limiterImplClz);

        MockFilterChain filterChain = new MockFilterChain();
        RateLimitFilter rateLimitFilter = null;
        if (limiterImplClz != null) {
            rateLimitFilter = buildRateLimitFilter(filterDef, root);
        }

        RequestClient[] clients = new RequestClient[8];
        for (int i = 0; i < 8; i++) {
            clients[i] = new RequestClient(rateLimitFilter, filterChain, 7, totalSeconds);
        }

        long now = System.currentTimeMillis();
        long nowInSecond = now / 1000L;
        long sleepTime = (nowInSecond / bucketDuration + 1L) * bucketDuration * 1000 - now;
        System.out.printf("Sleeping %d millis for the next time bucket to start, rateLimitFilter:%s\n",
                Long.valueOf(sleepTime), rateLimitFilter);
        Thread.sleep(sleepTime);

        for (int i = 0; i < 8; i++) {
            clients[i].start();
        }
        for (int i = 0; i < 8; i++) {
            clients[i].join();
        }

        if (rateLimitFilter != null) {
            rateLimitFilter.destroy();
        }
        removeRateLimitFilter(filterDef, root);

        long throughout = 0L;
        for (int i = 0; i < 8; i++) {
            throughout += clients[i].totalRequests;
        }

        return (int) (throughout / totalSeconds);
    }

    private void removeRateLimitFilter(FilterDef filterDef, Context root) throws ServletException {

        FilterMap[] maps = root.findFilterMaps();
        for (FilterMap map : maps) {
            if (RateLimitFilter.class.getName().equals(map.getFilterName())) {
                root.removeFilterMap(map);
            }
        }
        root.removeFilterDef(filterDef);
    }

    private RateLimitFilter buildRateLimitFilter(FilterDef filterDef, Context root) throws ServletException {

        RateLimitFilter rateLimitFilter = new RateLimitFilter();
        filterDef.setFilterClass(RateLimitFilter.class.getName());
        filterDef.setFilter(rateLimitFilter);
        filterDef.setFilterName(RateLimitFilter.class.getName());
        root.addFilterDef(filterDef);

        FilterMap filterMap = new FilterMap();
        filterMap.setFilterName(RateLimitFilter.class.getName());
        filterMap.addURLPatternDecoded("*");
        root.addFilterMap(filterMap);

        FilterConfig filterConfig = TesterFilterConfigs.generateFilterConfig(filterDef);

        rateLimitFilter.init(filterConfig);

        return rateLimitFilter;
    }

    private class RequestClient extends Thread {
        private int intervalInMillis;
        private int maxSeconds;
        RateLimitFilter filter;
        FilterChain filterChain;
        long totalRequests;

        public RequestClient(RateLimitFilter filter, FilterChain filterChain, int intervalInMillis, int maxSeconds) {
            this.filter = filter;
            this.filterChain = filterChain;
            this.intervalInMillis = intervalInMillis;
            this.maxSeconds = maxSeconds;
            setDaemon(true);
        }

        @Override
        public void run() {
            long now = System.currentTimeMillis();
            long maxTimestamp = now + maxSeconds * 1000L;
            for (totalRequests = 0; System.currentTimeMillis() < maxTimestamp; totalRequests++) {
                MockHttpServletRequest request = new MockHttpServletRequest();
                request.setRemoteAddr("192.168.0." + totalRequests % 32);
                TesterResponse response = new TesterResponseWithStatus();
                response.setRequest(request);

                try {
                    if (filter != null) {
                        filter.doFilter(request, response, filterChain);
                    } else {
                        filterChain.doFilter(request, response);
                    }

                    Thread.sleep(intervalInMillis);
                } catch (Throwable th) {
                    break;
                }
            }
        }
    }
}
