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

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.res.StringManager;
import org.apache.tomcat.util.threads.ScheduledThreadPoolExecutor;

import jakarta.servlet.FilterConfig;

/**
 * An accurate fixed window limiter.
 */
public class ExactRateLimiter implements RateLimiter {

    private static final AtomicInteger refIndex = new AtomicInteger();

    public ExactRateLimiter() {
        super();
        setPolicyName("exact-fixed" + refIndex.incrementAndGet());
    }

    private String policyName = null;

    @Override
    public String getPolicyName() {
        return policyName;
    }

    @Override
    public void setPolicyName(String name) {
        this.policyName = name;
    }

    private static final Log log = LogFactory.getLog(ExactRateLimiter.class);
    private static final StringManager sm = StringManager.getManager(ExactRateLimiter.class);

    private int duration;

    private int requests;

    private static final String BUCKET_KEY_DELIMITER = "-";

    @Override
    public int getDuration() {
        return duration;
    }

    @Override
    public void setDuration(int duration) {
        this.duration = duration;
    }

    @Override
    public int getRequests() {
        return requests;
    }

    @Override
    public void setRequests(int requests) {
        this.requests = requests;
    }

    @Override
    public int increment(String identifier) {
        String key = getBucket(System.currentTimeMillis()/1000L) + BUCKET_KEY_DELIMITER + identifier;
        AtomicInteger ai = map.computeIfAbsent(key, v -> new AtomicInteger());
        return ai.incrementAndGet();
    }

    /**
     * @param nowInSeconds
     * @return the bucket of specified seconds.
     */
    protected long getBucket(long nowInSeconds) {
        return nowInSeconds / duration;
    }

    @Override
    public void destroy() {
        // Stop our thread
        if (monitorFuture != null) {
            monitorFuture.cancel(true);
            monitorFuture = null;
        }
        if (maintenanceFuture != null) {
            maintenanceFuture.cancel(true);
            maintenanceFuture = null;
        }
        map.clear();
    }

    @Override
    public void setFilterConfig(FilterConfig filterConfig) {
        ScheduledExecutorService executorService = (ScheduledExecutorService) filterConfig.getServletContext()
                .getAttribute(ScheduledThreadPoolExecutor.class.getName());

        if (executorService == null) {
            executorService = new java.util.concurrent.ScheduledThreadPoolExecutor(1);
        }
        this.executorService = executorService;
        monitorFuture = executorService.scheduleWithFixedDelay(new MaintenanceMonitor(), 0, 180, TimeUnit.SECONDS);
    }

    /**
     * Map to hold the buckets
     */
    private final ConcurrentHashMap<String,AtomicInteger> map = new ConcurrentHashMap<>();

    /**
     * The future allowing control of the background processor.
     */
    private ScheduledFuture<?> maintenanceFuture;

    private ScheduledFuture<?> monitorFuture;

    private ScheduledExecutorService executorService;


    /**
     * Maintainer to cleanup rate limiter entries in runtime.
     */
    private class Maintenance implements Runnable {
        @Override
        public void run() {
            final long minBucket = getBucket(System.currentTimeMillis() / 1000L);
            // to avoid extreme case: 999999-xxx vs 1000000-xxx
            final long maxBucket = minBucket + 1;
            final String minBucketPrefix = minBucket + BUCKET_KEY_DELIMITER;
            final String maxBucketPrefix = maxBucket + BUCKET_KEY_DELIMITER;
            // remove obsolete keys
            map.keySet().removeIf(k -> k.compareTo(minBucketPrefix) < 0 && k.compareTo(maxBucketPrefix) < 0);
        }
    }

    /**
     * Maintainer scheduler monitor.
     */
    private class MaintenanceMonitor implements Runnable {

        private static final int DEFAULT_SLEEP_SECONDS = 2;

        @Override
        public void run() {
            if ((maintenanceFuture == null || maintenanceFuture.isDone())) {
                if (maintenanceFuture != null && maintenanceFuture.isDone()) {
                    // There was an error executing the scheduled task, get it and log it
                    try {
                        maintenanceFuture.get();
                    } catch (InterruptedException | ExecutionException e) {
                        log.error(sm.getString("exactRateLimiter.maintenance.error"), e);
                    }
                }

                int sleeptime = duration > DEFAULT_SLEEP_SECONDS ? duration : DEFAULT_SLEEP_SECONDS;
                maintenanceFuture = executorService.scheduleWithFixedDelay(new Maintenance(), sleeptime, sleeptime,
                        TimeUnit.SECONDS);
            }
        }
    }
}
