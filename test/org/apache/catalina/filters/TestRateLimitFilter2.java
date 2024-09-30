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
import java.time.Instant;

import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;

import org.apache.catalina.Context;
import org.apache.catalina.filters.TestRemoteIpFilter.MockFilterChain;
import org.apache.catalina.filters.TestRemoteIpFilter.MockHttpServletRequest;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.unittest.TesterResponse;
import org.apache.tomcat.util.descriptor.web.FilterDef;
import org.apache.tomcat.util.descriptor.web.FilterMap;
import org.junit.Assert;
import org.junit.Test;

public class TestRateLimitFilter2 extends TomcatBaseTest {

    private void testRateLimitWith1Clients(boolean exposeHeaders,boolean enforce) throws Exception {

        int bucketRequests = 40;
        int bucketDuration = 4;

        FilterDef filterDef = new FilterDef();
        filterDef.addInitParameter(RateLimitFilter2.PARAM_BUCKET_REQUESTS, String.valueOf(bucketRequests));
        filterDef.addInitParameter(RateLimitFilter2.PARAM_BUCKET_DURATION, String.valueOf(bucketDuration));
        filterDef.addInitParameter(RateLimitFilter2.PARAM_ENFORCE, String.valueOf(enforce));
        filterDef.addInitParameter(RateLimitFilter2.PARAM_EXPOSE_HEADERS, String.valueOf(exposeHeaders));

        Tomcat tomcat = getTomcatInstance();
        Context root = tomcat.addContext("", TEMP_DIR);
        tomcat.start();

        MockFilterChain filterChain = new MockFilterChain();
        RateLimitFilter2 rateLimitFilter = testRateLimitFilter(filterDef, root);
        long now = System.currentTimeMillis();
        long nowInSecond = now / 1000;
        long sleepTime = (nowInSecond / bucketDuration + 1L) * bucketDuration * 1000 - now;
        System.out.printf("Sleeping %d millis for the next time bucket to start\n", Long.valueOf(sleepTime));
        Thread.sleep(sleepTime);


        TestClient tc1 = new TestClient(rateLimitFilter, filterChain, "10.20.20.5", 200, 5); // TPS: 5
        TestClient tc2 = new TestClient(rateLimitFilter, filterChain, "10.20.20.10", 200, 10); // TPS: 10

        TestClient tc3 = new TestClient(rateLimitFilter, filterChain, "10.20.20.20", 200, 20); // TPS: 20
        TestClient tc4 = new TestClient(rateLimitFilter, filterChain, "10.20.20.40", 200, 40); // TPS: 40

        // Sleep for up to 5s for clients to complete
        int count = 0;
        while (count < 100 && (tc1.results[24] == 0 || tc2.results[49] == 0 || tc3.results[bucketRequests - 1] == 0 ||
                tc3.results[bucketRequests] == 0 || tc4.results[bucketRequests - 1] == 0 ||
                tc4.results[bucketRequests] == 0)) {
            Thread.sleep(100);
            count++;
        }
        Assert.assertEquals(200, tc1.results[24]); // only 25 requests made in 5 seconds, all allowed
        
        Assert.assertEquals(200, tc2.results[49]); // only 50 requests made in 5 seconds, all allowed

        Assert.assertEquals(200, tc3.results[39]); // first allowedRequests allowed
        
        if (enforce) {
            Assert.assertEquals(429, tc3.results[40]); // subsequent requests dropped
        } else {
            Assert.assertEquals(200, tc3.results[40]);
        }
        
        Assert.assertEquals(200, tc4.results[39]); // first allowedRequests allowed
        
        if (enforce) {
            Assert.assertEquals(enforce ? 429 : 200, tc4.results[40]); // subsequent requests dropped
        } else {
            Assert.assertEquals(200, tc4.results[40]);
        }
        
        if(exposeHeaders) {
            Assert.assertEquals(bucketDuration, tc1.durationHeader[24]);
            if(enforce) {
                Assert.assertTrue(tc1.remainingHeader[24]>=0);
                Assert.assertEquals(Integer.MIN_VALUE,tc1.currentHeader[24]);
                
                Assert.assertEquals(-1, tc3.remainingHeader[40]);
                Assert.assertEquals(Integer.MIN_VALUE, tc3.currentHeader[40]);
            } else {
                Assert.assertEquals(Integer.MIN_VALUE,tc1.remainingHeader[24]);
                Assert.assertTrue(tc1.currentHeader[24]>0);
                
                Assert.assertEquals(Integer.MIN_VALUE, tc3.remainingHeader[40]);
                Assert.assertTrue(tc3.currentHeader[40]>bucketDuration);
            }
        } else {
            Assert.assertEquals(Integer.MIN_VALUE, tc1.durationHeader[24]);
            Assert.assertEquals(Integer.MIN_VALUE, tc1.remainingHeader[24]);
            Assert.assertEquals(Integer.MIN_VALUE, tc1.currentHeader[24]);
        }
    }

    @Test
    public void testExposeHeaderAndRerferenceRateLimitWith4Clients() throws Exception {
        testRateLimitWith1Clients(true,false);
    }
    @Test
    public void testUnexposeHeaderAndRerferenceRateLimitWith4Clients() throws Exception {
        testRateLimitWith1Clients(false,false);
    }
    @Test
    public void testExposeHeaderAndEnforceRateLimitWith4Clients() throws Exception {
        testRateLimitWith1Clients(true,true);
    }
    @Test
    public void testUnexposeHeaderAndEnforceRateLimitWith4Clients() throws Exception {
        testRateLimitWith1Clients(false,true);
    }
    private RateLimitFilter2 testRateLimitFilter(FilterDef filterDef, Context root) throws ServletException {

        RateLimitFilter2 rateLimitFilter = new RateLimitFilter2();
        filterDef.setFilterClass(RateLimitFilter2.class.getName());
        filterDef.setFilter(rateLimitFilter);
        filterDef.setFilterName(RateLimitFilter2.class.getName());
        root.addFilterDef(filterDef);

        FilterMap filterMap = new FilterMap();
        filterMap.setFilterName(RateLimitFilter2.class.getName());
        filterMap.addURLPatternDecoded("*");
        root.addFilterMap(filterMap);

        FilterConfig filterConfig = TesterFilterConfigs.generateFilterConfig(filterDef);

        rateLimitFilter.init(filterConfig);

        return rateLimitFilter;
    }

    static class TestClient extends Thread {
        RateLimitFilter2 filter;
        FilterChain filterChain;
        String ip;

        int requests;
        int sleep;

        int[] results;
        int[] durationHeader;
        int[] remainingHeader;
        int[] currentHeader;

        TestClient(RateLimitFilter2 filter, FilterChain filterChain, String ip, int requests, int rps) {
            this.filter = filter;
            this.filterChain = filterChain;
            this.ip = ip;
            this.requests = requests;
            this.sleep = 1000 / rps;
            this.results = new int[requests];
            this.durationHeader = new int[requests];
            this.remainingHeader = new int[requests];
            this.currentHeader = new int[requests];
            super.setDaemon(true);
            super.start();
        }

        @Override
        public void run() {
            try {
                for (int i = 0; i < requests; i++) {
                    MockHttpServletRequest request = new MockHttpServletRequest();
                    request.setRemoteAddr(ip);
                    TesterResponse response = new TesterResponseWithStatus();
                    response.setRequest(request);
                    filter.doFilter(request, response, filterChain);
                    results[i] = response.getStatus();

                    durationHeader[i] =
                            tryParseIntHeader(response, RateLimitFilter2.HEADER_WINDOW_SECOND, Integer.MIN_VALUE);
                    remainingHeader[i] =
                            tryParseIntHeader(response, RateLimitFilter2.HEADER_REMAINING, Integer.MIN_VALUE);
                    currentHeader[i] = tryParseIntHeader(response, RateLimitFilter2.HEADER_CURRENT, Integer.MIN_VALUE);

                    System.out.printf("%s %s: %s %d\n", ip, Instant.now(), Integer.valueOf(i + 1),
                            Integer.valueOf(response.getStatus()));
                    sleep(sleep);
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }

        protected int tryParseIntHeader(TesterResponse resp, String header, int defaultValue) {
            try {
                return Integer.parseInt(resp.getHeader(header));
            } catch (NumberFormatException e) {
                return defaultValue;
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
