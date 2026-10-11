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

import org.junit.Assert;
import org.junit.Test;

public class TestTimeBucketCounter {

    static final double DELTA = 0.001;

    @Test
    public void testNextPowerOf2() {
        Assert.assertEquals(128, TimeBucketCounter.nextPowerOf2(100));
        Assert.assertEquals(128, TimeBucketCounter.nextPowerOf2(127));
        Assert.assertEquals(128, TimeBucketCounter.nextPowerOf2(128));
        Assert.assertEquals(256, TimeBucketCounter.nextPowerOf2(250));
        Assert.assertEquals(1024, TimeBucketCounter.nextPowerOf2(1000));
        Assert.assertEquals(1024, TimeBucketCounter.nextPowerOf2(1023));
        Assert.assertEquals(2048, TimeBucketCounter.nextPowerOf2(1025));
    }

    @Test
    public void testCalcRatioToNextPowerOf2() {
        Assert.assertEquals(256 / 256d, TimeBucketCounter.ratioToPowerOf2(256), DELTA);
        Assert.assertEquals(256 / 200d, TimeBucketCounter.ratioToPowerOf2(200), DELTA);
        Assert.assertEquals(65_536 / 60_000d, TimeBucketCounter.ratioToPowerOf2(60_000), DELTA);
    }

    @Test
    public void testTimeBucketCounter() {
        TimeBucketCounter tbc = new TimeBucketCounter(60, new java.util.concurrent.ScheduledThreadPoolExecutor(1));
        Assert.assertEquals(16, tbc.getNumBits());
        Assert.assertEquals(1.092, tbc.getRatio(), DELTA);
    }

    @Test
    public void testDurationAtMaximum() {
        // 1073741 seconds is the largest duration whose millisecond form, rounded up to the next power of 2
        // (2^30), still fits in an int
        TimeBucketCounter tbc = new TimeBucketCounter(1073741,
                new java.util.concurrent.ScheduledThreadPoolExecutor(1));
        Assert.assertEquals(30, tbc.getNumBits());
    }

    @Test
    public void testDurationTooLarge() {
        // Previously this failed with the misleading "must be a positive integer, was [0]" message because the
        // overflowed duration produced zero shift bits
        IllegalArgumentException iae = Assert.assertThrows(IllegalArgumentException.class, () -> new TimeBucketCounter(
                1073742, new java.util.concurrent.ScheduledThreadPoolExecutor(1)));
        // MessageFormat applies locale grouping to the numbers, e.g. "1,073,742" in en_US
        Assert.assertTrue("Error message should mention the rejected duration: " + iae.getMessage(),
                iae.getMessage().replace(",", "").replace(".", "").contains("1073742"));
    }

    @Test
    public void testDurationTooLargeAfterWrapAround() {
        // Previously this was silently accepted: 4294968 * 1000 wraps to 704 in int arithmetic, producing a
        // 1 second bucket with a ratio of 1.455 instead of the configured ~49.7 day window
        Assert.assertThrows(IllegalArgumentException.class, () -> new TimeBucketCounter(4294968,
                new java.util.concurrent.ScheduledThreadPoolExecutor(1)));
    }

    @Test
    public void testGetMillisUntilNextBucket() throws InterruptedException {
        long millis;
        long tb1, tb2;

        TimeBucketCounter tbc = new TimeBucketCounter(2, new java.util.concurrent.ScheduledThreadPoolExecutor(1));
        tb1 = tbc.getCurrentBucketPrefix();
        millis = tbc.getMillisUntilNextBucket();

        // sleep millis and get bucket
        Thread.sleep(millis);
        tb2 = tbc.getCurrentBucketPrefix();

        // ensure the new time bucket is one more than the previous one
        Assert.assertEquals(1, tb2 - tb1);

        tb1 = tb2;
        millis = tbc.getMillisUntilNextBucket();

        // sleep again
        Thread.sleep(millis);
        tb2 = tbc.getCurrentBucketPrefix();

        // ensure again
        Assert.assertEquals(1, tb2 - tb1);
    }
}
