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

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * this class maintains a thread safe hash map that has timestamp-based buckets followed by a string for a key, and a
 * counter for a value. each time the increment() method is called it adds the key if it does not exist, increments its
 * value and returns it. a maintenance thread cleans up keys that are prefixed by previous timestamp buckets.
 */
public class TimeBucketCounter {

    /**
     * Map to hold the buckets
     */
    private final ConcurrentHashMap<String,AtomicInteger> map = new ConcurrentHashMap<>();

    /**
     * Milliseconds bucket size as a Power of 2 for bit shift math, e.g. 16 for 65_536ms which is about 1:05 minute
     */
    private final int numBits;

    /**
     * ratio of actual duration to config duration
     */
    private final double ratio;

    /**
     * flag for the maintenance thread
     */
    volatile boolean isRunning = false;

    /**
     * Creates a new TimeBucketCounter with the specified lifetime.
     *
     * @param bucketDuration duration in seconds, e.g. for 1 minute pass 60
     */
    public TimeBucketCounter(int bucketDuration) {

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
        Thread mt = new MaintenanceThread(durationMillis / cleanupsPerBucketDuration);
        mt.start();
    }

    /**
     * increments the counter for the passed identifier in the current time bucket and returns the new value
     *
     * @param identifier an identifier for which we want to maintain count, e.g. IP Address
     *
     * @return the count within the current time bucket
     */
    public final int increment(String identifier) {
        String key = getCurrentBucketPrefix() + "-" + identifier;

        AtomicInteger ai = map.get(key);
        if (ai == null) {
            // there is a small chance of a benign data race where we might not count a request or
            // two but as a tradeoff in favor of performance we do not synchronize this operation
            ai = new AtomicInteger();
            AtomicInteger currentValue = map.putIfAbsent(key, ai);
            if (currentValue != null) {
                ai = currentValue;
            }
        }

        return ai.incrementAndGet();
    }

    /**
     * calculates the current time bucket prefix by shifting bits for fast division, e.g. shift 16 bits is the same as
     * dividing by 65,536 which is about 1:05m
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
     * the actual duration may differ from the configured duration because it is set to the next power of 2 value in
     * order to perform very fast bit shift arithmetic
     *
     * @return the actual bucket duration in milliseconds
     */
    public int getActualDuration() {
        return (int) Math.pow(2, getNumBits());
    }

    /**
     * returns the ratio between the configured duration param and the actual duration which will be set to the next
     * power of 2. we then multiply the configured requests param by the same ratio in order to compensate for the added
     * time, if any
     *
     * @return the ratio, e.g. 1.092 if the actual duration is 65_536 for the configured duration of 60_000
     */
    public double getRatio() {
        return ratio;
    }

    /**
     * returns the ratio to the next power of 2 so that we can adjust the value
     */
    static double ratioToPowerOf2(int value) {
        double nextPO2 = nextPowerOf2(value);
        return Math.round((1000 * nextPO2 / value)) / 1000d;
    }

    /**
     * returns the next power of 2 given a value, e.g. 256 for 250, or 1024, for 1000
     */
    static int nextPowerOf2(int value) {
        int valueOfHighestBit = Integer.highestOneBit(value);
        if (valueOfHighestBit == value) {
            return value;
        }

        return valueOfHighestBit << 1;
    }

    /**
     * when we want to test a full bucket duration we need to sleep until the next bucket starts
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
     * sets isRunning to false to terminate the maintenance thread
     */
    public void destroy() {
        this.isRunning = false;
    }

    /**
     * this class runs a background thread to clean up old keys from the map
     */
    class MaintenanceThread extends Thread {

        final long sleeptime;

        MaintenanceThread(long sleeptime) {
            super.setDaemon(true);
            this.sleeptime = sleeptime;
        }

        @SuppressWarnings("sync-override")
        @Override
        public void start() {
            isRunning = true;
            super.start();
        }

        @Override
        public void run() {

            while (isRunning) {
                String currentBucketPrefix = String.valueOf(getCurrentBucketPrefix());
                Set<String> keys = map.keySet();

                for (String k : keys) {
                    if (!k.startsWith(currentBucketPrefix)) {
                        // the key is obsolete, remove it
                        keys.remove((k));
                    }
                }

                try {
                    Thread.sleep(sleeptime);
                } catch (InterruptedException e) {
                }
            }
        }
    }
}
