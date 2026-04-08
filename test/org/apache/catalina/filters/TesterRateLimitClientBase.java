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

import org.apache.catalina.filters.TestRemoteIpFilter.MockHttpServletRequest;
import org.apache.tomcat.unittest.TesterResponse;
import org.apache.tomcat.unittest.TesterResponseWithStatus;

public abstract class TesterRateLimitClientBase extends Thread {
    RateLimitFilter filter;
    FilterChain filterChain;
    String ip;

    int requests;

    int[] results;
    volatile String[] rlpHeader;
    volatile String[] rlHeader;

    TesterRateLimitClientBase(RateLimitFilter filter, FilterChain filterChain, String ip, int requests) {
        this.filter = filter;
        this.filterChain = filterChain;
        this.ip = ip;
        this.requests = requests;
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
                //System.out.printf("%s %s: %s %d, Policy:%s, Current:%s\n", ip, Instant.now(),
                //        Integer.valueOf(i + 1), Integer.valueOf(response.getStatus()), rlpHeader[i], rlHeader[i]);

                waitForNextRequest(start, i);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }


    abstract void waitForNextRequest(long start, int requestIndex) throws Exception;
}
