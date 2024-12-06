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
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Basic implementation of {@link RateLimiter}, provides runtime data maintenance mechanism monitor.
 */
public abstract class RateLimiterBase implements RateLimiter {

    private static final AtomicInteger index = new AtomicInteger();

    TimeBucketCounter bucketCounter;

    int actualRequests;

    int actualDuration;

    // Initial policy name can be rewritten by setPolicyName()
    private String policyName = null;

    /**
     * If policy name has not been specified, the first call of {@link #getPolicyName()} returns a auto-generated policy
     * name using the default policy name as prefix and followed by auto-increase index.
     *
     * @return default policy name, as a prefix of auto-generated policy name.
     */
    protected abstract String getDefaultPolicyName();

    @Override
    public String getPolicyName() {
        if (policyName == null) {
            policyName = getDefaultPolicyName() + "-" + index.incrementAndGet();
        }
        return policyName;
    }

    @Override
    public void setPolicyName(String name) {
        Objects.requireNonNull(name);
        this.policyName = name;
    }

    @Override
    public int getDuration() {
        return actualDuration;
    }

    @Override
    public int getRequests() {
        return actualRequests;
    }

    @Override
    public int increment(String identifier) {
        return bucketCounter.increment(identifier);
    }

    @Override
    public void destroy() {
        bucketCounter.destroy();
    }

    /**
     * Instantiate an instance of {@link TimeBucketCounter} for specific time bucket size. Concrete classes determine
     * its counter policy by returning different implementation instance.
     *
     * @param utilityExecutor the executor
     * @param duration        size of each time bucket in seconds
     *
     * @return counter instance of {@link TimeBucketCounter}
     */
    protected abstract TimeBucketCounter newCounterInstance(ScheduledExecutorService utilityExecutor, int duration);

    @Override
    public void initialize(ScheduledExecutorService utilityExecutor, int duration, int requests) {
        if (bucketCounter != null) {
            bucketCounter.destroy();
        }

        bucketCounter = newCounterInstance(utilityExecutor, duration);

        actualDuration = bucketCounter.getBucketDuration();
        actualRequests = (int) Math.round(bucketCounter.getRatio() * requests);
    }

    /**
     * Returns the internal instance of {@link TimeBucketCounter}
     *
     * @return instance of {@link TimeBucketCounter}
     */
    public TimeBucketCounter getBucketCounter() {
        return bucketCounter;
    }
}
