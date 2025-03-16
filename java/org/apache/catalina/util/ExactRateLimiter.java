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

/**
 * A RateLimiter that compromises efficiency for accuracy in order to provide exact rate limiting.
 */
public class ExactRateLimiter extends RateLimiterBase {

    @Override
    protected String getDefaultPolicyName() {
        return "exact";
    }


    @Override
    protected TimeBucketCounterBase newCounterInstance(int duration, ScheduledExecutorService executorService) {
        return new ExactTimeBucketCounter(duration, executorService);
    }


    /**
     * An accurate counter with exact bucket index, but slightly less efficient than another fast counter provided with
     * the {@link FastRateLimiter}.
     */
    static class ExactTimeBucketCounter extends TimeBucketCounterBase {

        ExactTimeBucketCounter(int bucketDuration, ScheduledExecutorService executorService) {
            super(bucketDuration, executorService);
        }

        @Override
        public long getBucketIndex(long timestamp) {
            return (timestamp / 1000) / getBucketDuration();
        }

        @Override
        public double getRatio() {
            // Actual value is exactly the same as declared
            return 1.0d;
        }

        @Override
        public long getMillisUntilNextBucket() {
            long millis = System.currentTimeMillis();
            long nextTimeBucketMillis = (getBucketIndex(millis) + 1) * getBucketDuration() * 1000;
            return nextTimeBucketMillis - millis;
        }
    }
}
