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

import java.util.Objects;
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
 * This class maintains a thread safe hash map that has timestamp-based buckets followed by a string for a key, and a
 * counter for an integer value. Each time the increment() method is called it adds the key if it does not exist,
 * increments its value and returns it.
 */
public abstract class TimeBucketCounterBase {

    private static final Log log = LogFactory.getLog(TimeBucketCounterBase.class);
    private static final StringManager sm = StringManager.getManager(TimeBucketCounterBase.class);

    private static final String BUCKET_KEY_DELIMITER = "-";

    // Map to hold the buckets
    private final ConcurrentHashMap<String,AtomicInteger> map = new ConcurrentHashMap<>();

    // The future allowing control of the background processor.
    private ScheduledFuture<?> maintenanceFuture;
    private ScheduledFuture<?> monitorFuture;
    private final ScheduledExecutorService executorService;
    private final long sleeptime;
    private final int bucketDuration;


    /**
     * Creates a new TimeBucketCounter with the specified lifetime.
     *
     * @param bucketDuration  duration in seconds, e.g. for 1 minute pass 60
     * @param executorService the executor service that will be used to run the maintenance task
     *
     * @throws NullPointerException if executorService is <code>null</code>.
     */
    public TimeBucketCounterBase(int bucketDuration, ScheduledExecutorService executorService) {
        Objects.requireNonNull(executorService);
        this.executorService = executorService;
        this.bucketDuration = bucketDuration;

        int cleanupsPerBucketDuration = (bucketDuration >= 60) ? 6 : 3;
        sleeptime = bucketDuration * 1000L / cleanupsPerBucketDuration;

        // Start our thread
        if (sleeptime > 0) {
            monitorFuture = executorService.scheduleWithFixedDelay(new MaintenanceMonitor(), 0, 60, TimeUnit.SECONDS);
        }
    }


    /**
     * @return bucketDuration in seconds
     */
    public int getBucketDuration() {
        return bucketDuration;
    }


    /**
     * Returns the ratio between the configured duration param and the actual duration.
     *
     * @return the ratio between the configured duration param and the actual duration.
     */
    public abstract double getRatio();


    /**
     * Increments the counter for the passed identifier in the current time bucket and returns the new value.
     *
     * @param identifier an identifier for which we want to maintain count, e.g. IP Address
     *
     * @return the count within the current time bucket
     */
    public final int increment(String identifier) {
        String key = genKey(identifier);
        AtomicInteger ai = map.computeIfAbsent(key, v -> new AtomicInteger());
        return ai.incrementAndGet();
    }


    /**
     * Generates the key of timeBucket counter maps with the specific identifier, and the timestamp is implicitly
     * equivalent to "now".
     *
     * @param identifier an identifier for which we want to maintain count
     *
     * @return key of timeBucket counter maps
     */
    protected final String genKey(String identifier) {
        return genKey(identifier, System.currentTimeMillis());
    }


    /**
     * Generates the key of timeBucket counter maps with the specific identifier and timestamp.
     *
     * @param identifier of target request
     * @param timestamp  when target request received
     *
     * @return key of timeBucket counter maps
     */
    protected final String genKey(String identifier, long timestamp) {
        return getBucketIndex(timestamp) + BUCKET_KEY_DELIMITER + identifier;
    }


    /**
     * Calculate the bucket index for the specific timestamp.
     *
     * @param timestamp the specific timestamp in milliseconds
     *
     * @return prefix the bucket key prefix for the specific timestamp
     */
    protected abstract long getBucketIndex(long timestamp);


    /**
     * Returns current bucket prefix
     *
     * @return bucket index
     */
    public int getCurrentBucketPrefix() {
        return (int) getBucketIndex(System.currentTimeMillis());
    }


    /**
     * When we want to test a full bucket duration we need to sleep until the next bucket starts.
     * <p>
     * <strong>WARNING:</strong> This method is used for test purpose.
     *
     * @return the number of milliseconds until the next bucket
     */
    public abstract long getMillisUntilNextBucket();


    /**
     * Stops threads created by this object and cleans up resources.
     */
    public void destroy() {
        map.clear();
        if (monitorFuture != null) {
            monitorFuture.cancel(true);
            monitorFuture = null;
        }
        if (maintenanceFuture != null) {
            maintenanceFuture.cancel(true);
            maintenanceFuture = null;
        }
    }


    /**
     * Periodic evict, perform removal of obsolete bucket items. Absence of this operation may result in OOM after a
     * long run.
     */
    public void periodicEvict() {
        /*
         * The implementation of this method assumes that the time taken for eviction is less than 1 bucket duration.
         * It is possible that the eviction process starts in one bucket but finishes in another. Therefore, keys for
         * the current bucket and the next bucket when the eviction process starts are excluded from eviction.
         */
        long currentBucketIndex = getCurrentBucketPrefix();
        String currentBucketPrefix = String.valueOf(currentBucketIndex);
        String nextBucketPrefix = String.valueOf(currentBucketIndex + 1);
        ConcurrentHashMap.KeySetView<String,AtomicInteger> keys = map.keySet();
        // remove obsolete keys
        keys.removeIf(k -> !k.startsWith(currentBucketPrefix) && !k.startsWith(nextBucketPrefix));
    }


    private class Maintenance implements Runnable {
        @Override
        public void run() {
            periodicEvict();
        }
    }

    private class MaintenanceMonitor implements Runnable {
        @Override
        public void run() {
            if (sleeptime > 0 && (maintenanceFuture == null || maintenanceFuture.isDone())) {
                if (maintenanceFuture != null && maintenanceFuture.isDone()) {
                    // There was an error executing the scheduled task, get it and log it
                    try {
                        maintenanceFuture.get();
                    } catch (InterruptedException | ExecutionException e) {
                        log.error(sm.getString("timebucket.maintenance.error"), e);
                    }
                }
                maintenanceFuture = executorService.scheduleWithFixedDelay(new Maintenance(), sleeptime, sleeptime,
                        TimeUnit.MILLISECONDS);
            }
        }
    }
}
