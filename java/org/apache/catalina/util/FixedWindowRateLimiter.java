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


/**
 * This class maintains a thread safe hash map that has an identifier key, and an value of runtime
 * {@link LimiterRuntimeEntry}. Each time the {@link #incrementAndGet(String)} method or {@link #tryAcquire(String)} is
 * called, it adds the key if it does not exist. A maintenance thread cleans up items whose fixed window is obsolete.
 * 
 * @author Chenjp
 */
public class FixedWindowRateLimiter {
    private static final Log log = LogFactory.getLog(FixedWindowRateLimiter.class);
    private static final StringManager sm = StringManager.getManager(TimeBucketCounter.class);

    private final long windowUnit;
    private final long windowUnitInMillis;
    private final int accessTimesThreshold;
    private final boolean enforce;




    /**
     * Map to hold the buckets
     */
    private final ConcurrentHashMap<String,LimiterRuntimeEntry> map = new ConcurrentHashMap<>();

    /**
     * The future allowing control of the background processor.
     */
    private ScheduledFuture<?> maintenanceFuture;

    private ScheduledFuture<?> monitorFuture;
    
    private final ScheduledExecutorService executorService;
    
    private long sleeptime;
    
    /**
     * Creates a new FixedWindowRateLimiter with the specified lifetime.
     * 
     * @param fixedWindowInSecond    seconds of fixed window duration, e.g. for 1 minute pass 60
     * @param requestNumberThreshold request threshold inner the fixed window.
     * @param enforce                termination indicator when request number exceeds nrOfRequestsThreshold.
     * @param executorService        the executor service which will be used to run the maintenance.
     */
    public FixedWindowRateLimiter(int fixedWindowInSecond, int nrOfRequestsThreshold, boolean enforce,
            ScheduledExecutorService executorService) {
        this.windowUnit = fixedWindowInSecond;
        this.windowUnitInMillis = fixedWindowInSecond * 1000L;
        this.accessTimesThreshold = nrOfRequestsThreshold;
        this.enforce = enforce;
        this.executorService = executorService;
        this.sleeptime = windowUnitInMillis >> 1;
        monitorFuture = executorService.scheduleWithFixedDelay(new MaintenanceMonitor(), 0, 180, TimeUnit.SECONDS);
    }

    /**
     * @param identifier target key of the {@link FixedWindowRateLimiter} effect on.
     * 
     * @return the increased nrOfRequests. Value <code>1</code> returned if newWindow start.
     */
    public int increment(String identifier) {
        return map.computeIfAbsent(identifier, v -> new LimiterRuntimeEntry(windowUnit)).incrementAndGet();
    }

    /**
     * Attempts to acquire for an access permit.
     * 
     * @param identifier target key of the {@link FixedWindowRateLimiter} effect on.
     * 
     * @return a negative value on failure of <tt>enforce and rate exceeded</tt>; a positive value indicates access
     *             times inner current fixed window. The returned value may exceed <tt>threshold</tt> only when
     *             <tt>enforce</tt> is <tt>false</tt>.
     */
    public int tryAcquire(String identifier) {
        return map.computeIfAbsent(identifier, v -> new LimiterRuntimeEntry(windowUnit))
                .tryAcquire(accessTimesThreshold, enforce);
    }

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

    static class LimiterRuntimeEntry {
        protected final long windowUnitInMillis;
        /**
         * Access time counter inner same fixed windowTimestamp / windowUnitInMillis.
         */
        protected AtomicInteger requestsCounter;
        private final Object lock = new Object();
        private volatile boolean rateExceed = false;
        private volatile long windowTimestamp;

        /**
         * @param windowUnit           in seconds.
         * @param accessTimesThreshold of requests.
         * @param enforce              request termination indicator when
         */
        LimiterRuntimeEntry(long windowUnit) {
            this.windowUnitInMillis = windowUnit * 1000L;
            this.requestsCounter = new AtomicInteger();
            this.rateExceed = false;
        }

        /**
         * Increases and returns the number of requests occur within the same latest window.
         * 
         * @return the nrOfRequests occur within the same latest window.
         */
        public int incrementAndGet() {
            long now = System.currentTimeMillis();
            if (now - windowTimestamp >= windowUnitInMillis) {
                // new window start.
                long newWindowTimestamp = now / windowUnitInMillis * windowUnitInMillis;
                synchronized (lock) {
                    if (windowTimestamp != newWindowTimestamp) {
                        windowTimestamp = newWindowTimestamp;
                        rateExceed = false;
                        requestsCounter.set(1);
                        return 1;
                    }
                }
            }
            return requestsCounter.incrementAndGet();
        }

        /**
         * Attempts to acquire for an access permit. Decide to take control information as arguments instead of fields
         * to minimize memory cost.
         * 
         * @param nrOfRequestsThreshold limitation effected when <code>enforce</code> is <code>true</code>.
         * @param enforce               termination indicator when request number exceeds nrOfRequestsThreshold.
         * 
         * @return a negative value on failure of <tt>enforce and rate exceeded</tt>; a positive value indicates access
         *             times inner current fixed window. The returned value may exceed <tt>threshold</tt> only when
         *             <tt>enforce</tt> is <tt>false</tt>.
         */
        public int tryAcquire(final int nrOfRequestsThreshold, final boolean enforce) {
            if (!enforce) {
                return incrementAndGet();
            }
            long now = System.currentTimeMillis();
            if (now - windowTimestamp >= windowUnitInMillis) {
                // new window start.
                long newWindowTimestamp = now / windowUnitInMillis * windowUnitInMillis;
                synchronized (lock) {
                    if (windowTimestamp != newWindowTimestamp) {
                        windowTimestamp = newWindowTimestamp;
                        rateExceed = false;
                        requestsCounter.set(1);
                        return 1;
                    }
                }
            }
            int acquire;
            if (rateExceed) {
                acquire = -1;
            } else {
                int currentAccessTimes = requestsCounter.incrementAndGet();
                if (currentAccessTimes > nrOfRequestsThreshold) {
                    rateExceed = true;
                    acquire = -1;
                } else {
                    acquire = currentAccessTimes;
                }
            }
            return acquire;
        }
    }

    /**
     * Maintainer to cleanup rate limiter entries in runtime.
     */
    private class Maintenance implements Runnable {
        @Override
        public void run() {
            // window time stamp older than 4 fixed windows ago(roughly.), regarded as obsoleted.
            final long obsoleteWindowInMillis = System.currentTimeMillis() - (windowUnitInMillis << 2);
            map.values().removeIf(e -> e.windowTimestamp < obsoleteWindowInMillis);
        }
    }

    /**
     * Maintainer scheduler monitor.
     */
    private class MaintenanceMonitor implements Runnable {
        @Override
        public void run() {
            if ((maintenanceFuture == null || maintenanceFuture.isDone())) {
                if (maintenanceFuture != null && maintenanceFuture.isDone()) {
                    // There was an error executing the scheduled task, get it and log it
                    try {
                        maintenanceFuture.get();
                    } catch (InterruptedException | ExecutionException e) {
                        log.error(sm.getString("timebucket.maintenance.error"), e);
                    }
                }
                maintenanceFuture = executorService.scheduleWithFixedDelay(new Maintenance(), sleeptime, sleeptime, TimeUnit.SECONDS);
            }
        }
    }

}