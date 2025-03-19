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
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;

import javax.servlet.FilterConfig;

/**
 * Base implementation of {@link RateLimiter}, provides runtime data maintenance mechanism monitor.
 */
public abstract class RateLimiterBase implements RateLimiter {

    private static final AtomicInteger index = new AtomicInteger();

    TimeBucketCounterBase bucketCounter;

    int requests;
    int actualRequests;

    int duration;
    int actualDuration;

    // Initial policy name can be rewritten by setPolicyName()
    private String policyName = null;

    /*
     * The self-owned utility executor, will be instantiated only when ScheduledThreadPoolExecutor is absent during
     * filter configure phase.
     */
    private ScheduledThreadPoolExecutor internalExecutorService = null;

    /**
     * If policy name has not been specified, the first call of {@link #getPolicyName()} returns an auto-generated policy
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
    public int increment(String identifier) {
        return bucketCounter.increment(identifier);
    }


    @Override
    public void destroy() {
        bucketCounter.destroy();
        if (internalExecutorService != null) {
            try {
                internalExecutorService.shutdown();
            } catch (SecurityException e) {
                // ignore
            }
        }
    }


    /**
     * Instantiate an instance of {@link TimeBucketCounterBase} for specific time bucket size. Concrete classes
     * determine its counter policy by returning different implementation instances.
     *
     * @param duration        size of each time bucket in seconds
     * @param utilityExecutor the executor
     *
     * @return counter instance of {@link TimeBucketCounterBase}
     */
    protected abstract TimeBucketCounterBase newCounterInstance(int duration, ScheduledExecutorService utilityExecutor);


    @Override
    public void setFilterConfig(FilterConfig filterConfig) {

        ScheduledExecutorService executorService = (ScheduledExecutorService) filterConfig.getServletContext()
                .getAttribute(ScheduledThreadPoolExecutor.class.getName());

        if (executorService == null) {
            internalExecutorService = new java.util.concurrent.ScheduledThreadPoolExecutor(1);
            executorService = internalExecutorService;
        }

        bucketCounter = newCounterInstance(duration, executorService);
        actualDuration = bucketCounter.getBucketDuration();
        actualRequests = (int) Math.round(bucketCounter.getRatio() * requests);
    }


    /**
     * Returns the internal instance of {@link TimeBucketCounterBase}.
     *
     * @return instance of {@link TimeBucketCounterBase}
     */
    public TimeBucketCounterBase getBucketCounter() {
        return bucketCounter;
    }
}
