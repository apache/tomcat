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
 * This class maintains a thread safe hash map that has timestamp-based buckets followed by a string for a key, and a
 * counter for a value. each time the increment() method is called it adds the key if it does not exist, increments its
 * value and returns it. a maintenance thread cleans up keys that are prefixed by previous timestamp buckets.
 */
public class TimeBucketCounter {

    private static final Log log = LogFactory.getLog(TimeBucketCounter.class);
    private static final StringManager sm = StringManager.getManager(TimeBucketCounter.class);

    /**
     * Map to hold the buckets
     */
    private final ConcurrentHashMap<String,AtomicInteger> map = new ConcurrentHashMap<>();

    /**
     * Milliseconds bucket size as a Power of 2 for bit shift math, e.g. 16 for 65_536ms which is about 1:05 minute
     */
    private final int numBits;

    /**
     * Ratio of actual duration to config duration
     */
    private final double ratio;

    /**
     * The future allowing control of the background processor.
     */
    private ScheduledFuture<?> maintenanceFuture;
    private ScheduledFuture<?> monitorFuture;
    private final ScheduledExecutorService executorService;
    private final long sleeptime;

    /**
     * Creates a new TimeBucketCounter with the specified lifetime.
     *
     * @param bucketDuration  duration in seconds, e.g. for 1 minute pass 60
     * @param executorService the executor service which will be used to run the maintenance
     */
    public TimeBucketCounter(int bucketDuration, ScheduledExecutorService executorService) {

        this.executorService = executorService;

        int durationMillis = bucketDuration * 1000;

        int bits = 0;
        int pof2 = nextPowerOf2(durationMillis);
        int bitCheck = pof2;
        while (bitCheck > 1) {
            bitCheck = pof2 >> ++bits;
        }

        this.numBits = bits;

        this.ratio = ratioToPowerOf2(durationMillis);

        int cleanupsPerBucketDuration = (durationMillis >= 60_000) ? 6 : 3;
        sleeptime = durationMillis / cleanupsPerBucketDuration;

        // Start our thread
        if (sleeptime > 0) {
            monitorFuture = executorService.scheduleWithFixedDelay(new MaintenanceMonitor(), 0, 60, TimeUnit.SECONDS);
        }
    }

    /**
     * Increments the counter for the passed identifier in the current time bucket and returns the new value.
     *
     * @param identifier an identifier for which we want to maintain count, e.g. IP Address
     *
     * @return the count within the current time bucket
     */
    public final int increment(String identifier) {
        String key = getCurrentBucketPrefix() + "-" + identifier;
        AtomicInteger ai = map.computeIfAbsent(key, v -> new AtomicInteger());
        return ai.incrementAndGet();
    }

    /**
     * Calculates the current time bucket prefix by shifting bits for fast division, e.g. shift 16 bits is the same as
     * dividing by 65,536 which is about 1:05m.
     *
     * @return The current bucket prefix.
     */
    public final int getCurrentBucketPrefix() {
        return (int) (System.currentTimeMillis() >> this.numBits);
    }

    public int getNumBits() {
        return numBits;
    }

    /**
     * The actual duration may differ from the configured duration because it is set to the next power of 2 value in
     * order to perform very fast bit shift arithmetic.
     *
     * @return the actual bucket duration in milliseconds
     */
    public int getActualDuration() {
        return (int) Math.pow(2, getNumBits());
    }

    /**
     * Returns the ratio between the configured duration param and the actual duration which will be set to the next
     * power of 2. We then multiply the configured requests param by the same ratio in order to compensate for the added
     * time, if any.
     *
     * @return the ratio, e.g. 1.092 if the actual duration is 65_536 for the configured duration of 60_000
     */
    public double getRatio() {
        return ratio;
    }

    /**
     * Returns the ratio to the next power of 2 so that we can adjust the value.
     */
    static double ratioToPowerOf2(int value) {
        double nextPO2 = nextPowerOf2(value);
        return Math.round((1000 * nextPO2 / value)) / 1000d;
    }

    /**
     * Returns the next power of 2 given a value, e.g. 256 for 250, or 1024, for 1000.
     */
    static int nextPowerOf2(int value) {
        int valueOfHighestBit = Integer.highestOneBit(value);
        if (valueOfHighestBit == value) {
            return value;
        }

        return valueOfHighestBit << 1;
    }

    /**
     * When we want to test a full bucket duration we need to sleep until the next bucket starts.
     *
     * @return the number of milliseconds until the next bucket
     */
    public long getMillisUntilNextBucket() {
        long millis = System.currentTimeMillis();
        long nextTimeBucketMillis = ((millis + (long) Math.pow(2, numBits)) >> numBits) << numBits;
        long delta = nextTimeBucketMillis - millis;
        return delta;
    }

    /**
     * Sets isRunning to false to terminate the maintenance thread.
     */
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
    }

    private class Maintenance implements Runnable {
        @Override
        public void run() {
            String currentBucketPrefix = String.valueOf(getCurrentBucketPrefix());
            ConcurrentHashMap.KeySetView<String,AtomicInteger> keys = map.keySet();
            // remove obsolete keys
            keys.removeIf(k -> !k.startsWith(currentBucketPrefix));
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
