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
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.descriptor.web.FilterDef;
import org.apache.tomcat.util.descriptor.web.FilterMap;
import org.junit.Test;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class TesterRateLimitFilterPerformance extends TomcatBaseTest {

    // int durationUnit = 4, durationMaxRequests = 300, runnerSeconds = 60;
    // Thu Oct 10 18:09:36 CST 2024 No Limiter TPS: 2643 Fast Limiter TPS: 1389 Exact Limiter TPS: 1369
    // int durationUnit = 4, durationMaxRequests = 180, runnerSeconds = 60;
    // Thu Oct 10 18:18:36 CST 2024 No Limiter TPS: 2439 Fast Limiter TPS: 1325 Exact Limiter TPS: 1322

    int durationUnit = 4, durationMaxRequests = 180, runnerSeconds = 60;
    @Override
    public void setUp() throws Exception {
        // TODO Auto-generated method stub
        super.setUp();
        Tomcat tomcat = getTomcatInstance();
        Context root = tomcat.addContext("", TEMP_DIR);

        HttpServlet mockServlet = new MyServlet();
        Tomcat.addServlet(root, mockServlet.getClass().getName(), mockServlet);
        root.addServletMappingDecoded("/mock/*", mockServlet.getClass().getName());
        addRateLimitFilter(root, "org.apache.catalina.util.FastRateLimiter");
        addRateLimitFilter(root, "org.apache.catalina.util.ExactRateLimiter");
    }

    protected void addRateLimitFilter(Context root, String rateLimiterClassName) {
        String filterName = rateLimiterClassName + "-Filter";

        FilterDef filterDef;
        filterDef = new FilterDef();
        filterDef.addInitParameter("bucketRequests", String.valueOf(durationMaxRequests));
        filterDef.addInitParameter("bucketDuration", String.valueOf(durationUnit));
        filterDef.addInitParameter("enforce", String.valueOf(true));
        filterDef.addInitParameter("exposeHeaders", String.valueOf(false));
        filterDef.addInitParameter("rateLimiterClassName", rateLimiterClassName);
        filterDef.setFilterClass(RateLimitFilterOfPerformance.class.getName());
        filterDef.setFilterName(filterName);
        root.addFilterDef(filterDef);

        FilterMap filterMap = new FilterMap();
        filterMap.setFilterName(filterName);
        filterMap.addURLPatternDecoded("/mock/" + rateLimiterClassName + "/*");
        root.addFilterMap(filterMap);

    }

    @Test
    public void testLimiterPerformance() throws Exception {
        int tps = 0, times = 3;

        int fastLimiterThroughout = 0;
        int exactLimiterThroughout = 0;
        int noLimiterThroughout = 0;

        Tomcat tomcat = getTomcatInstance();
        tomcat.start();

        tps = 0;
        for (int i = 0; i < times; i++) {
            tps += runIt(null, runnerSeconds);
        }
        noLimiterThroughout = tps / times;

        tps = 0;
        for (int i = 0; i < times; i++) {
            tps += runIt("org.apache.catalina.util.FastRateLimiter", runnerSeconds);
        }
        fastLimiterThroughout = tps / times;

        tps = 0;
        for (int i = 0; i < times; i++) {
            tps += runIt("org.apache.catalina.util.ExactRateLimiter", runnerSeconds);
        }
        exactLimiterThroughout = tps / times;

        tomcat.stop();
        System.out.println(new Date() + "\tNo Limiter TPS:\t" + noLimiterThroughout + "\tFast Limiter TPS:\t" +
                fastLimiterThroughout + "\tExact Limiter TPS:\t" + exactLimiterThroughout);
    }

    int runIt(String limiterImplClz, int totalSeconds) throws Exception {
        RequestClient[] clients = new RequestClient[8];
        if (limiterImplClz == null || limiterImplClz.trim().isBlank()) {
            limiterImplClz = "x";
        }
        for (int i = 0; i < 8; i++) {
            clients[i] = new RequestClient("/mock/" + limiterImplClz, totalSeconds);
        }

        long now = System.currentTimeMillis();
        long nowInSecond = now / 1000L;
        long sleepTime = (nowInSecond / durationUnit + 1L) * durationUnit * 1000 - now;
        System.out.printf("Sleeping %d millis for the next time bucket to start\n", Long.valueOf(sleepTime));
        Thread.sleep(sleepTime);

        for (int i = 0; i < 8; i++) {
            clients[i].start();
        }
        for (int i = 0; i < 8; i++) {
            clients[i].join();
        }


        long throughout = 0L;
        for (int i = 0; i < 8; i++) {
            throughout += clients[i].totalRequests;
        }

        return (int) (throughout / totalSeconds);
    }

    private class RequestClient extends Thread {
        private int maxSeconds;
        long totalRequests;
        private String urlPrefix;

        public RequestClient(String urlPrefix, int maxSeconds) {
            this.urlPrefix = urlPrefix;
            this.maxSeconds = maxSeconds;
            setDaemon(true);
        }

        @Override
        public void run() {
            long now = System.currentTimeMillis();
            long maxTimestamp = now + maxSeconds * 1000L;
            for (totalRequests = 0; System.currentTimeMillis() < maxTimestamp; totalRequests++) {
                try {
                    Map<String,List<String>> reqHeader = new HashMap<>();
                    List<String> remoteIps = new ArrayList<>();
                    remoteIps.add("192.168.0." + (totalRequests % 16 + 1));
                    reqHeader.put(X_CUST_IP, remoteIps);
                    String url = "http://localhost:" + getPort() + urlPrefix + "/test" + totalRequests;
                    getUrl(url, new ByteChunk(), reqHeader, new HashMap<String,List<String>>());
                } catch (Throwable th) {
                    break;
                }
            }
        }
    }

    static final String X_CUST_IP = "x-req-ip";

    static class MyServlet extends HttpServlet {
        /**
         * 
         */
        private static final long serialVersionUID = 1L;
        Random r = new Random();

        @Override
        protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            StringBuffer buf = new StringBuffer();
            buf.append("Random value:").append(r.nextLong()).append("; Http headers:").append('\n');
            req.getHeaderNames().asIterator().forEachRemaining(k -> {
                buf.append("Key=").append(k).append("; Value=").append(req.getHeader(k)).append('\n');
            });
            resp.getWriter().write(buf.toString());
        }
    }
}
