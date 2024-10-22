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

import java.util.concurrent.ScheduledExecutorService;

import jakarta.servlet.FilterConfig;

import org.apache.tomcat.util.threads.ScheduledThreadPoolExecutor;

/**
 * A RateLimiter that compromises accuracy for speed in order to provide maximum throughput.
 */
public class FastRateLimiter implements RateLimiter {

    TimeBucketCounter bucketCounter;

    int duration;

    int requests;

    int actualRequests;

    int actualDuration;

    @Override
    public int getDuration() {
        return actualDuration;
    }

    @Override
    public void setDuration(int duration) {
        this.duration = duration;
    }

    @Override
    public int getRequests() {
        return actualRequests;
    }

    @Override
    public void setRequests(int requests) {
        this.requests = requests;
    }

    @Override
    public int increment(String ipAddress) {
        return bucketCounter.increment(ipAddress);
    }

    @Override
    public void destroy() {
        bucketCounter.destroy();
    }

    @Override
    public void setFilterConfig(FilterConfig filterConfig) {

        ScheduledExecutorService executorService = (ScheduledExecutorService) filterConfig.getServletContext()
                .getAttribute(ScheduledThreadPoolExecutor.class.getName());

        if (executorService == null) {
            executorService = new java.util.concurrent.ScheduledThreadPoolExecutor(1);
        }

        bucketCounter = new TimeBucketCounter(duration, executorService);
        actualRequests = (int) Math.round(bucketCounter.getRatio() * requests);
        actualDuration = bucketCounter.getActualDuration() / 1000;
    }

    public TimeBucketCounter getBucketCounter() {
        return bucketCounter;
    }
}
