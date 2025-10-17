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
package org.apache.tomcat.dbcp.pool2.impl;

import java.time.Duration;

import org.apache.tomcat.dbcp.pool2.BaseObject;

/**
 * Provides the implementation for the common attributes shared by the sub-classes. New instances of this class will be created using the defaults defined by
 * the public constants.
 * <p>
 * This class is not thread-safe.
 * </p>
 *
 * @param <T> Type of element pooled.
 * @since 2.0
 */
public abstract class BaseObjectPoolConfig<T> extends BaseObject implements Cloneable {

    /**
     * The default value for the {@code lifo} configuration attribute: {@value}.
     *
     * @see GenericObjectPool#getLifo()
     * @see GenericKeyedObjectPool#getLifo()
     */
    public static final boolean DEFAULT_LIFO = true;

    /**
     * The default value for the {@code fairness} configuration attribute: {@value}.
     *
     * @see GenericObjectPool#getFairness()
     * @see GenericKeyedObjectPool#getFairness()
     */
    public static final boolean DEFAULT_FAIRNESS = false;

    /**
     * The default value for the {@code maxWait} configuration attribute: {@value}.
     *
     * @see GenericObjectPool#getMaxWaitDuration()
     * @see GenericKeyedObjectPool#getMaxWaitDuration()
     * @deprecated Use {@link #DEFAULT_MAX_WAIT}.
     */
    @Deprecated
    public static final long DEFAULT_MAX_WAIT_MILLIS = -1L;

    /**
     * The default value for the {@code maxWait} configuration attribute.
     *
     * @see GenericObjectPool#getMaxWaitDuration()
     * @see GenericKeyedObjectPool#getMaxWaitDuration()
     * @since 2.10.0
     */
    public static final Duration DEFAULT_MAX_WAIT = Duration.ofMillis(DEFAULT_MAX_WAIT_MILLIS);

    /**
     * The default value for the {@code minEvictableIdleDuration} configuration attribute: {@value}.
     *
     * @see GenericObjectPool#getMinEvictableIdleDuration()
     * @see GenericKeyedObjectPool#getMinEvictableIdleDuration()
     * @deprecated Use {@link #DEFAULT_MIN_EVICTABLE_IDLE_TIME}.
     */
    @Deprecated
    public static final long DEFAULT_MIN_EVICTABLE_IDLE_TIME_MILLIS = 1000L * 60L * 30L;

    /**
     * The default value for the {@code minEvictableIdleDuration} configuration attribute.
     *
     * @see GenericObjectPool#getMinEvictableIdleDuration()
     * @see GenericKeyedObjectPool#getMinEvictableIdleDuration()
     * @since 2.11.0
     */
    public static final Duration DEFAULT_MIN_EVICTABLE_IDLE_DURATION = Duration.ofMillis(DEFAULT_MIN_EVICTABLE_IDLE_TIME_MILLIS);

    /**
     * The default value for the {@code minEvictableIdleDuration} configuration attribute.
     *
     * @see GenericObjectPool#getMinEvictableIdleDuration()
     * @see GenericKeyedObjectPool#getMinEvictableIdleDuration()
     * @since 2.10.0
     * @deprecated Use {@link #DEFAULT_MIN_EVICTABLE_IDLE_DURATION}.
     */
    @Deprecated
    public static final Duration DEFAULT_MIN_EVICTABLE_IDLE_TIME = Duration.ofMillis(DEFAULT_MIN_EVICTABLE_IDLE_TIME_MILLIS);

    /**
     * The default value for the {@code softMinEvictableIdleTime} configuration attribute: {@value}.
     *
     * @see GenericObjectPool#getSoftMinEvictableIdleDuration()
     * @see GenericKeyedObjectPool#getSoftMinEvictableIdleDuration()
     * @deprecated Use {@link #DEFAULT_SOFT_MIN_EVICTABLE_IDLE_TIME}.
     */
    @Deprecated
    public static final long DEFAULT_SOFT_MIN_EVICTABLE_IDLE_TIME_MILLIS = -1;

    /**
     * The default value for the {@code softMinEvictableIdleTime} configuration attribute.
     *
     * @see GenericObjectPool#getSoftMinEvictableIdleDuration()
     * @see GenericKeyedObjectPool#getSoftMinEvictableIdleDuration()
     * @since 2.10.0
     * @deprecated Use {@link #DEFAULT_SOFT_MIN_EVICTABLE_IDLE_DURATION}.
     */
    @Deprecated
    public static final Duration DEFAULT_SOFT_MIN_EVICTABLE_IDLE_TIME = Duration.ofMillis(DEFAULT_SOFT_MIN_EVICTABLE_IDLE_TIME_MILLIS);

    /**
     * The default value for the {@code softMinEvictableIdleTime} configuration attribute.
     *
     * @see GenericObjectPool#getSoftMinEvictableIdleDuration()
     * @see GenericKeyedObjectPool#getSoftMinEvictableIdleDuration()
     * @since 2.11.0
     */
    public static final Duration DEFAULT_SOFT_MIN_EVICTABLE_IDLE_DURATION = Duration.ofMillis(DEFAULT_SOFT_MIN_EVICTABLE_IDLE_TIME_MILLIS);

    /**
     * The default value for {@code evictorShutdownTimeout} configuration attribute: {@value}.
     *
     * @see GenericObjectPool#getEvictorShutdownTimeoutDuration()
     * @see GenericKeyedObjectPool#getEvictorShutdownTimeoutDuration()
     * @deprecated Use {@link #DEFAULT_EVICTOR_SHUTDOWN_TIMEOUT}.
     */
    @Deprecated
    public static final long DEFAULT_EVICTOR_SHUTDOWN_TIMEOUT_MILLIS = 10L * 1000L;

    /**
     * The default value for {@code evictorShutdownTimeout} configuration attribute.
     *
     * @see GenericObjectPool#getEvictorShutdownTimeoutDuration()
     * @see GenericKeyedObjectPool#getEvictorShutdownTimeoutDuration()
     * @since 2.10.0
     */
    public static final Duration DEFAULT_EVICTOR_SHUTDOWN_TIMEOUT = Duration.ofMillis(DEFAULT_EVICTOR_SHUTDOWN_TIMEOUT_MILLIS);

    /**
     * The default value for the {@code numTestsPerEvictionRun} configuration attribute: {@value}.
     *
     * @see GenericObjectPool#getNumTestsPerEvictionRun()
     * @see GenericKeyedObjectPool#getNumTestsPerEvictionRun()
     */
    public static final int DEFAULT_NUM_TESTS_PER_EVICTION_RUN = 3;

    /**
     * The default value for the {@code testOnCreate} configuration attribute: {@value}.
     *
     * @see GenericObjectPool#getTestOnCreate()
     * @see GenericKeyedObjectPool#getTestOnCreate()
     * @since 2.2
     */
    public static final boolean DEFAULT_TEST_ON_CREATE = false;

    /**
     * The default value for the {@code testOnBorrow} configuration attribute: {@value}.
     *
     * @see GenericObjectPool#getTestOnBorrow()
     * @see GenericKeyedObjectPool#getTestOnBorrow()
     */
    public static final boolean DEFAULT_TEST_ON_BORROW = false;

    /**
     * The default value for the {@code testOnReturn} configuration attribute: {@value}.
     *
     * @see GenericObjectPool#getTestOnReturn()
     * @see GenericKeyedObjectPool#getTestOnReturn()
     */
    public static final boolean DEFAULT_TEST_ON_RETURN = false;

    /**
     * The default value for the {@code testWhileIdle} configuration attribute: {@value}.
     *
     * @see GenericObjectPool#getTestWhileIdle()
     * @see GenericKeyedObjectPool#getTestWhileIdle()
     */
    public static final boolean DEFAULT_TEST_WHILE_IDLE = false;

    /**
     * The default value for the {@code timeBetweenEvictionRuns} configuration attribute: {@value}.
     *
     * @see GenericObjectPool#getDurationBetweenEvictionRuns()
     * @see GenericKeyedObjectPool#getDurationBetweenEvictionRuns()
     * @deprecated Use {@link #DEFAULT_TIME_BETWEEN_EVICTION_RUNS}.
     */
    @Deprecated
    public static final long DEFAULT_TIME_BETWEEN_EVICTION_RUNS_MILLIS = -1L;

    /**
     * The default value for the {@code timeBetweenEvictionRuns} configuration attribute.
     *
     * @see GenericObjectPool#getDurationBetweenEvictionRuns()
     * @see GenericKeyedObjectPool#getDurationBetweenEvictionRuns()
     * @since 2.12.0
     */
    public static final Duration DEFAULT_DURATION_BETWEEN_EVICTION_RUNS = Duration.ofMillis(DEFAULT_TIME_BETWEEN_EVICTION_RUNS_MILLIS);

    /**
     * The default value for the {@code timeBetweenEvictionRuns} configuration attribute.
     *
     * @see GenericObjectPool#getDurationBetweenEvictionRuns()
     * @see GenericKeyedObjectPool#getDurationBetweenEvictionRuns()
     * @deprecated Use {@link #DEFAULT_DURATION_BETWEEN_EVICTION_RUNS}.
     */
    @Deprecated
    public static final Duration DEFAULT_TIME_BETWEEN_EVICTION_RUNS = Duration.ofMillis(DEFAULT_TIME_BETWEEN_EVICTION_RUNS_MILLIS);

    /**
     * The default value for the {@code blockWhenExhausted} configuration attribute: {@value}.
     *
     * @see GenericObjectPool#getBlockWhenExhausted()
     * @see GenericKeyedObjectPool#getBlockWhenExhausted()
     */
    public static final boolean DEFAULT_BLOCK_WHEN_EXHAUSTED = true;

    /**
     * The default value for enabling JMX for pools created with a configuration instance: {@value}.
     */
    public static final boolean DEFAULT_JMX_ENABLE = true;

    /**
     * The default value for the prefix used to name JMX enabled pools created with a configuration instance: {@value}.
     *
     * @see GenericObjectPool#getJmxName()
     * @see GenericKeyedObjectPool#getJmxName()
     */
    public static final String DEFAULT_JMX_NAME_PREFIX = "pool";

    /**
     * The default value for the base name to use to name JMX enabled pools created with a configuration instance. The default is {@code null} which means the
     * pool will provide the base name to use.
     *
     * @see GenericObjectPool#getJmxName()
     * @see GenericKeyedObjectPool#getJmxName()
     */
    public static final String DEFAULT_JMX_NAME_BASE = null;

    /**
     * The default value for the {@code evictionPolicyClassName} configuration attribute.
     *
     * @see GenericObjectPool#getEvictionPolicyClassName()
     * @see GenericKeyedObjectPool#getEvictionPolicyClassName()
     */
    public static final String DEFAULT_EVICTION_POLICY_CLASS_NAME = DefaultEvictionPolicy.class.getName();

    private boolean lifo = DEFAULT_LIFO;

    private boolean fairness = DEFAULT_FAIRNESS;

    private Duration maxWaitDuration = DEFAULT_MAX_WAIT;

    private Duration minEvictableIdleDuration = DEFAULT_MIN_EVICTABLE_IDLE_TIME;

    private Duration evictorShutdownTimeoutDuration = DEFAULT_EVICTOR_SHUTDOWN_TIMEOUT;

    private Duration softMinEvictableIdleDuration = DEFAULT_SOFT_MIN_EVICTABLE_IDLE_TIME;

    private int numTestsPerEvictionRun = DEFAULT_NUM_TESTS_PER_EVICTION_RUN;

    private EvictionPolicy<T> evictionPolicy; // Only 2.6.0 applications set this

    private String evictionPolicyClassName = DEFAULT_EVICTION_POLICY_CLASS_NAME;

    private boolean testOnCreate = DEFAULT_TEST_ON_CREATE;

    private boolean testOnBorrow = DEFAULT_TEST_ON_BORROW;

    private boolean testOnReturn = DEFAULT_TEST_ON_RETURN;

    private boolean testWhileIdle = DEFAULT_TEST_WHILE_IDLE;

    private Duration durationBetweenEvictionRuns = DEFAULT_DURATION_BETWEEN_EVICTION_RUNS;

    private boolean blockWhenExhausted = DEFAULT_BLOCK_WHEN_EXHAUSTED;

    private boolean jmxEnabled = DEFAULT_JMX_ENABLE;

    // TODO Consider changing this to a single property for 3.x
    private String jmxNamePrefix = DEFAULT_JMX_NAME_PREFIX;

    private String jmxNameBase = DEFAULT_JMX_NAME_BASE;

    /**
     * Gets the value for the {@code blockWhenExhausted} configuration attribute for pools created with this configuration instance.
     *
     * @return The current setting of {@code blockWhenExhausted} for this configuration instance
     * @see GenericObjectPool#getBlockWhenExhausted()
     * @see GenericKeyedObjectPool#getBlockWhenExhausted()
     */
    public boolean getBlockWhenExhausted() {
        return blockWhenExhausted;
    }

    /**
     * Gets the value for the {@code timeBetweenEvictionRuns} configuration attribute for pools created with this configuration instance.
     *
     * @return The current setting of {@code timeBetweenEvictionRuns} for this configuration instance
     * @see GenericObjectPool#getDurationBetweenEvictionRuns()
     * @see GenericKeyedObjectPool#getDurationBetweenEvictionRuns()
     * @since 2.11.0
     */
    public Duration getDurationBetweenEvictionRuns() {
        return durationBetweenEvictionRuns;
    }

    /**
     * Gets the value for the {@code evictionPolicyClass} configuration attribute for pools created with this configuration instance.
     *
     * @return The current setting of {@code evictionPolicyClass} for this configuration instance
     * @see GenericObjectPool#getEvictionPolicy()
     * @see GenericKeyedObjectPool#getEvictionPolicy()
     * @since 2.6.0
     */
    public EvictionPolicy<T> getEvictionPolicy() {
        return evictionPolicy;
    }

    /**
     * Gets the value for the {@code evictionPolicyClassName} configuration attribute for pools created with this configuration instance.
     *
     * @return The current setting of {@code evictionPolicyClassName} for this configuration instance
     * @see GenericObjectPool#getEvictionPolicyClassName()
     * @see GenericKeyedObjectPool#getEvictionPolicyClassName()
     */
    public String getEvictionPolicyClassName() {
        return evictionPolicyClassName;
    }

    /**
     * Gets the value for the {@code evictorShutdownTimeout} configuration attribute for pools created with this configuration instance.
     *
     * @return The current setting of {@code evictorShutdownTimeout} for this configuration instance
     * @see GenericObjectPool#getEvictorShutdownTimeoutDuration()
     * @see GenericKeyedObjectPool#getEvictorShutdownTimeoutDuration()
     * @since 2.10.0
     * @deprecated Use {@link #getEvictorShutdownTimeoutDuration()}.
     */
    @Deprecated
    public Duration getEvictorShutdownTimeout() {
        return evictorShutdownTimeoutDuration;
    }

    /**
     * Gets the value for the {@code evictorShutdownTimeout} configuration attribute for pools created with this configuration instance.
     *
     * @return The current setting of {@code evictorShutdownTimeout} for this configuration instance
     * @see GenericObjectPool#getEvictorShutdownTimeoutDuration()
     * @see GenericKeyedObjectPool#getEvictorShutdownTimeoutDuration()
     * @since 2.11.0
     */
    public Duration getEvictorShutdownTimeoutDuration() {
        return evictorShutdownTimeoutDuration;
    }

    /**
     * Gets the value for the {@code evictorShutdownTimeout} configuration attribute for pools created with this configuration instance.
     *
     * @return The current setting of {@code evictorShutdownTimeout} for this configuration instance
     * @see GenericObjectPool#getEvictorShutdownTimeoutDuration()
     * @see GenericKeyedObjectPool#getEvictorShutdownTimeoutDuration()
     * @deprecated Use {@link #getEvictorShutdownTimeout()}.
     */
    @Deprecated
    public long getEvictorShutdownTimeoutMillis() {
        return evictorShutdownTimeoutDuration.toMillis();
    }

    /**
     * Gets the value for the {@code fairness} configuration attribute for pools created with this configuration instance.
     *
     * @return The current setting of {@code fairness} for this configuration instance
     * @see GenericObjectPool#getFairness()
     * @see GenericKeyedObjectPool#getFairness()
     */
    public boolean getFairness() {
        return fairness;
    }

    /**
     * Gets the value of the flag that determines if JMX will be enabled for pools created with this configuration instance.
     *
     * @return The current setting of {@code jmxEnabled} for this configuration instance
     */
    public boolean getJmxEnabled() {
        return jmxEnabled;
    }

    /**
     * Gets the value of the JMX name base that will be used as part of the name assigned to JMX enabled pools created with this configuration instance. A value
     * of {@code null} means that the pool will define the JMX name base.
     *
     * @return The current setting of {@code jmxNameBase} for this configuration instance
     */
    public String getJmxNameBase() {
        return jmxNameBase;
    }

    /**
     * Gets the value of the JMX name prefix that will be used as part of the name assigned to JMX enabled pools created with this configuration instance.
     *
     * @return The current setting of {@code jmxNamePrefix} for this configuration instance
     */
    public String getJmxNamePrefix() {
        return jmxNamePrefix;
    }

    /**
     * Gets the value for the {@code lifo} configuration attribute for pools created with this configuration instance.
     *
     * @return The current setting of {@code lifo} for this configuration instance
     * @see GenericObjectPool#getLifo()
     * @see GenericKeyedObjectPool#getLifo()
     */
    public boolean getLifo() {
        return lifo;
    }

    /**
     * Gets the value for the {@code maxWait} configuration attribute for pools created with this configuration instance.
     *
     * @return The current setting of {@code maxWait} for this configuration instance
     * @see GenericObjectPool#getMaxWaitDuration()
     * @see GenericKeyedObjectPool#getMaxWaitDuration()
     * @since 2.11.0
     */
    public Duration getMaxWaitDuration() {
        return maxWaitDuration;
    }

    /**
     * Gets the value for the {@code maxWait} configuration attribute for pools created with this configuration instance.
     *
     * @return The current setting of {@code maxWait} for this configuration instance
     * @see GenericObjectPool#getMaxWaitDuration()
     * @see GenericKeyedObjectPool#getMaxWaitDuration()
     * @deprecated Use {@link #getMaxWaitDuration()}.
     */
    @Deprecated
    public long getMaxWaitMillis() {
        return maxWaitDuration.toMillis();
    }

    /**
     * Gets the value for the {@code minEvictableIdleTime} configuration attribute for pools created with this configuration instance.
     *
     * @return The current setting of {@code minEvictableIdleTime} for this configuration instance
     * @see GenericObjectPool#getMinEvictableIdleDuration()
     * @see GenericKeyedObjectPool#getMinEvictableIdleDuration()
     * @since 2.11.0
     */
    public Duration getMinEvictableIdleDuration() {
        return minEvictableIdleDuration;
    }

    /**
     * Gets the value for the {@code minEvictableIdleTime} configuration attribute for pools created with this configuration instance.
     *
     * @return The current setting of {@code minEvictableIdleTime} for this configuration instance
     * @see GenericObjectPool#getMinEvictableIdleDuration()
     * @see GenericKeyedObjectPool#getMinEvictableIdleDuration()
     * @since 2.10.0
     * @deprecated Use {@link #getMinEvictableIdleDuration()}.
     */
    @Deprecated
    public Duration getMinEvictableIdleTime() {
        return minEvictableIdleDuration;
    }

    /**
     * Gets the value for the {@code minEvictableIdleTime} configuration attribute for pools created with this configuration instance.
     *
     * @return The current setting of {@code minEvictableIdleTime} for this configuration instance
     * @see GenericObjectPool#getMinEvictableIdleDuration()
     * @see GenericKeyedObjectPool#getMinEvictableIdleDuration()
     * @deprecated Use {@link #getMinEvictableIdleTime()}.
     */
    @Deprecated
    public long getMinEvictableIdleTimeMillis() {
        return minEvictableIdleDuration.toMillis();
    }

    /**
     * Gets the value for the {@code numTestsPerEvictionRun} configuration attribute for pools created with this configuration instance.
     *
     * @return The current setting of {@code numTestsPerEvictionRun} for this configuration instance
     * @see GenericObjectPool#getNumTestsPerEvictionRun()
     * @see GenericKeyedObjectPool#getNumTestsPerEvictionRun()
     */
    public int getNumTestsPerEvictionRun() {
        return numTestsPerEvictionRun;
    }

    /**
     * Gets the value for the {@code softMinEvictableIdleTime} configuration attribute for pools created with this configuration instance.
     *
     * @return The current setting of {@code softMinEvictableIdleTime} for this configuration instance
     * @see GenericObjectPool#getSoftMinEvictableIdleDuration()
     * @see GenericKeyedObjectPool#getSoftMinEvictableIdleDuration()
     * @since 2.11.0
     */
    public Duration getSoftMinEvictableIdleDuration() {
        return softMinEvictableIdleDuration;
    }

    /**
     * Gets the value for the {@code softMinEvictableIdleTime} configuration attribute for pools created with this configuration instance.
     *
     * @return The current setting of {@code softMinEvictableIdleTime} for this configuration instance
     * @see GenericObjectPool#getSoftMinEvictableIdleDuration()
     * @see GenericKeyedObjectPool#getSoftMinEvictableIdleDuration()
     * @since 2.10.0
     * @deprecated Use {@link #getSoftMinEvictableIdleDuration()}.
     */
    @Deprecated
    public Duration getSoftMinEvictableIdleTime() {
        return softMinEvictableIdleDuration;
    }

    /**
     * Gets the value for the {@code softMinEvictableIdleTime} configuration attribute for pools created with this configuration instance.
     *
     * @return The current setting of {@code softMinEvictableIdleTime} for this configuration instance
     * @see GenericObjectPool#getSoftMinEvictableIdleDuration()
     * @see GenericKeyedObjectPool#getSoftMinEvictableIdleDuration()
     * @deprecated Use {@link #getSoftMinEvictableIdleDuration()}.
     */
    @Deprecated
    public long getSoftMinEvictableIdleTimeMillis() {
        return softMinEvictableIdleDuration.toMillis();
    }

    /**
     * Gets the value for the {@code testOnBorrow} configuration attribute for pools created with this configuration instance.
     *
     * @return The current setting of {@code testOnBorrow} for this configuration instance
     * @see GenericObjectPool#getTestOnBorrow()
     * @see GenericKeyedObjectPool#getTestOnBorrow()
     */
    public boolean getTestOnBorrow() {
        return testOnBorrow;
    }

    /**
     * Gets the value for the {@code testOnCreate} configuration attribute for pools created with this configuration instance.
     *
     * @return The current setting of {@code testOnCreate} for this configuration instance
     * @see GenericObjectPool#getTestOnCreate()
     * @see GenericKeyedObjectPool#getTestOnCreate()
     * @since 2.2
     */
    public boolean getTestOnCreate() {
        return testOnCreate;
    }

    /**
     * Gets the value for the {@code testOnReturn} configuration attribute for pools created with this configuration instance.
     *
     * @return The current setting of {@code testOnReturn} for this configuration instance
     * @see GenericObjectPool#getTestOnReturn()
     * @see GenericKeyedObjectPool#getTestOnReturn()
     */
    public boolean getTestOnReturn() {
        return testOnReturn;
    }

    /**
     * Gets the value for the {@code testWhileIdle} configuration attribute for pools created with this configuration instance.
     *
     * @return The current setting of {@code testWhileIdle} for this configuration instance
     * @see GenericObjectPool#getTestWhileIdle()
     * @see GenericKeyedObjectPool#getTestWhileIdle()
     */
    public boolean getTestWhileIdle() {
        return testWhileIdle;
    }

    /**
     * Gets the value for the {@code timeBetweenEvictionRuns} configuration attribute for pools created with this configuration instance.
     *
     * @return The current setting of {@code timeBetweenEvictionRuns} for this configuration instance
     * @see GenericObjectPool#getDurationBetweenEvictionRuns()
     * @see GenericKeyedObjectPool#getDurationBetweenEvictionRuns()
     * @since 2.10.0
     * @deprecated Use {@link #getDurationBetweenEvictionRuns()}.
     */
    @Deprecated
    public Duration getTimeBetweenEvictionRuns() {
        return durationBetweenEvictionRuns;
    }

    /**
     * Gets the value for the {@code timeBetweenEvictionRuns} configuration attribute for pools created with this configuration instance.
     *
     * @return The current setting of {@code timeBetweenEvictionRuns} for this configuration instance
     * @see GenericObjectPool#getDurationBetweenEvictionRuns()
     * @see GenericKeyedObjectPool#getDurationBetweenEvictionRuns()
     * @deprecated Use {@link #getDurationBetweenEvictionRuns()}.
     */
    @Deprecated
    public long getTimeBetweenEvictionRunsMillis() {
        return durationBetweenEvictionRuns.toMillis();
    }

    /**
     * Sets the value for the {@code blockWhenExhausted} configuration attribute for pools created with this configuration instance.
     *
     * @param blockWhenExhausted The new setting of {@code blockWhenExhausted} for this configuration instance
     * @see GenericObjectPool#getBlockWhenExhausted()
     * @see GenericKeyedObjectPool#getBlockWhenExhausted()
     */
    public void setBlockWhenExhausted(final boolean blockWhenExhausted) {
        this.blockWhenExhausted = blockWhenExhausted;
    }

    /**
     * Sets the value for the {@code evictionPolicyClass} configuration attribute for pools created with this configuration instance.
     *
     * @param evictionPolicy The new setting of {@code evictionPolicyClass} for this configuration instance
     * @see GenericObjectPool#getEvictionPolicy()
     * @see GenericKeyedObjectPool#getEvictionPolicy()
     * @since 2.6.0
     */
    public void setEvictionPolicy(final EvictionPolicy<T> evictionPolicy) {
        this.evictionPolicy = evictionPolicy;
    }

    /**
     * Sets the value for the {@code evictionPolicyClassName} configuration attribute for pools created with this configuration instance.
     *
     * @param evictionPolicyClassName The new setting of {@code evictionPolicyClassName} for this configuration instance
     * @see GenericObjectPool#getEvictionPolicyClassName()
     * @see GenericKeyedObjectPool#getEvictionPolicyClassName()
     */
    public void setEvictionPolicyClassName(final String evictionPolicyClassName) {
        this.evictionPolicyClassName = evictionPolicyClassName;
    }

    /**
     * Sets the value for the {@code evictorShutdownTimeout} configuration attribute for pools created with this configuration instance.
     *
     * @param evictorShutdownTimeoutDuration The new setting of {@code evictorShutdownTimeout} for this configuration instance
     * @see GenericObjectPool#getEvictorShutdownTimeoutDuration()
     * @see GenericKeyedObjectPool#getEvictorShutdownTimeoutDuration()
     * @since 2.11.0
     */
    public void setEvictorShutdownTimeout(final Duration evictorShutdownTimeoutDuration) {
        this.evictorShutdownTimeoutDuration = PoolImplUtils.nonNull(evictorShutdownTimeoutDuration, DEFAULT_EVICTOR_SHUTDOWN_TIMEOUT);
    }

    /**
     * Sets the value for the {@code evictorShutdownTimeout} configuration attribute for pools created with this configuration instance.
     *
     * @param evictorShutdownTimeout The new setting of {@code evictorShutdownTimeout} for this configuration instance
     * @see GenericObjectPool#getEvictorShutdownTimeoutDuration()
     * @see GenericKeyedObjectPool#getEvictorShutdownTimeoutDuration()
     * @since 2.10.0
     * @deprecated Use {@link #setEvictorShutdownTimeout(Duration)}.
     */
    @Deprecated
    public void setEvictorShutdownTimeoutMillis(final Duration evictorShutdownTimeout) {
        setEvictorShutdownTimeout(evictorShutdownTimeout);
    }

    /**
     * Sets the value for the {@code evictorShutdownTimeout} configuration attribute for pools created with this configuration instance.
     *
     * @param evictorShutdownTimeoutMillis The new setting of {@code evictorShutdownTimeout} for this configuration instance
     * @see GenericObjectPool#getEvictorShutdownTimeoutDuration()
     * @see GenericKeyedObjectPool#getEvictorShutdownTimeoutDuration()
     * @deprecated Use {@link #setEvictorShutdownTimeout(Duration)}.
     */
    @Deprecated
    public void setEvictorShutdownTimeoutMillis(final long evictorShutdownTimeoutMillis) {
        setEvictorShutdownTimeout(Duration.ofMillis(evictorShutdownTimeoutMillis));
    }

    /**
     * Sets the value for the {@code fairness} configuration attribute for pools created with this configuration instance.
     *
     * @param fairness The new setting of {@code fairness} for this configuration instance
     * @see GenericObjectPool#getFairness()
     * @see GenericKeyedObjectPool#getFairness()
     */
    public void setFairness(final boolean fairness) {
        this.fairness = fairness;
    }

    /**
     * Sets the value of the flag that determines if JMX will be enabled for pools created with this configuration instance.
     *
     * @param jmxEnabled The new setting of {@code jmxEnabled} for this configuration instance
     */
    public void setJmxEnabled(final boolean jmxEnabled) {
        this.jmxEnabled = jmxEnabled;
    }

    /**
     * Sets the value of the JMX name base that will be used as part of the name assigned to JMX enabled pools created with this configuration instance. A value
     * of {@code null} means that the pool will define the JMX name base.
     *
     * @param jmxNameBase The new setting of {@code jmxNameBase} for this configuration instance
     */
    public void setJmxNameBase(final String jmxNameBase) {
        this.jmxNameBase = jmxNameBase;
    }

    /**
     * Sets the value of the JMX name prefix that will be used as part of the name assigned to JMX enabled pools created with this configuration instance.
     *
     * @param jmxNamePrefix The new setting of {@code jmxNamePrefix} for this configuration instance
     */
    public void setJmxNamePrefix(final String jmxNamePrefix) {
        this.jmxNamePrefix = jmxNamePrefix;
    }

    /**
     * Sets the value for the {@code lifo} configuration attribute for pools created with this configuration instance.
     *
     * @param lifo The new setting of {@code lifo} for this configuration instance
     * @see GenericObjectPool#getLifo()
     * @see GenericKeyedObjectPool#getLifo()
     */
    public void setLifo(final boolean lifo) {
        this.lifo = lifo;
    }

    /**
     * Sets the value for the {@code maxWait} configuration attribute for pools created with this configuration instance.
     *
     * @param maxWaitDuration The new setting of {@code maxWaitDuration} for this configuration instance
     * @see GenericObjectPool#getMaxWaitDuration()
     * @see GenericKeyedObjectPool#getMaxWaitDuration()
     * @since 2.11.0
     */
    public void setMaxWait(final Duration maxWaitDuration) {
        this.maxWaitDuration = PoolImplUtils.nonNull(maxWaitDuration, DEFAULT_MAX_WAIT);
    }

    /**
     * Sets the value for the {@code maxWait} configuration attribute for pools created with this configuration instance.
     *
     * @param maxWaitMillis The new setting of {@code maxWaitMillis} for this configuration instance
     * @see GenericObjectPool#getMaxWaitDuration()
     * @see GenericKeyedObjectPool#getMaxWaitDuration()
     * @deprecated Use {@link #setMaxWait(Duration)}.
     */
    @Deprecated
    public void setMaxWaitMillis(final long maxWaitMillis) {
        setMaxWait(Duration.ofMillis(maxWaitMillis));
    }

    /**
     * Sets the value for the {@code minEvictableIdleTime} configuration attribute for pools created with this configuration instance.
     *
     * @param minEvictableIdleTime The new setting of {@code minEvictableIdleTime} for this configuration instance
     * @see GenericObjectPool#getMinEvictableIdleDuration()
     * @see GenericKeyedObjectPool#getMinEvictableIdleDuration()
     * @since 2.12.0
     */
    public void setMinEvictableIdleDuration(final Duration minEvictableIdleTime) {
        this.minEvictableIdleDuration = PoolImplUtils.nonNull(minEvictableIdleTime, DEFAULT_MIN_EVICTABLE_IDLE_TIME);
    }

    /**
     * Sets the value for the {@code minEvictableIdleTime} configuration attribute for pools created with this configuration instance.
     *
     * @param minEvictableIdleTime The new setting of {@code minEvictableIdleTime} for this configuration instance
     * @see GenericObjectPool#getMinEvictableIdleDuration()
     * @see GenericKeyedObjectPool#getMinEvictableIdleDuration()
     * @since 2.10.0
     * @deprecated Use {@link #setMinEvictableIdleDuration(Duration)}.
     */
    @Deprecated
    public void setMinEvictableIdleTime(final Duration minEvictableIdleTime) {
        this.minEvictableIdleDuration = PoolImplUtils.nonNull(minEvictableIdleTime, DEFAULT_MIN_EVICTABLE_IDLE_TIME);
    }

    /**
     * Sets the value for the {@code minEvictableIdleTime} configuration attribute for pools created with this configuration instance.
     *
     * @param minEvictableIdleTimeMillis The new setting of {@code minEvictableIdleTime} for this configuration instance
     * @see GenericObjectPool#getMinEvictableIdleDuration()
     * @see GenericKeyedObjectPool#getMinEvictableIdleDuration()
     * @deprecated Use {@link #setMinEvictableIdleDuration(Duration)}.
     */
    @Deprecated
    public void setMinEvictableIdleTimeMillis(final long minEvictableIdleTimeMillis) {
        this.minEvictableIdleDuration = Duration.ofMillis(minEvictableIdleTimeMillis);
    }

    /**
     * Sets the value for the {@code numTestsPerEvictionRun} configuration attribute for pools created with this configuration instance.
     *
     * @param numTestsPerEvictionRun The new setting of {@code numTestsPerEvictionRun} for this configuration instance
     * @see GenericObjectPool#getNumTestsPerEvictionRun()
     * @see GenericKeyedObjectPool#getNumTestsPerEvictionRun()
     */
    public void setNumTestsPerEvictionRun(final int numTestsPerEvictionRun) {
        this.numTestsPerEvictionRun = numTestsPerEvictionRun;
    }

    /**
     * Sets the value for the {@code softMinEvictableIdleTime} configuration attribute for pools created with this configuration instance.
     *
     * @param softMinEvictableIdleTime The new setting of {@code softMinEvictableIdleTime} for this configuration instance
     * @see GenericObjectPool#getSoftMinEvictableIdleDuration()
     * @see GenericKeyedObjectPool#getSoftMinEvictableIdleDuration()
     * @since 2.12.0
     */
    public void setSoftMinEvictableIdleDuration(final Duration softMinEvictableIdleTime) {
        this.softMinEvictableIdleDuration = PoolImplUtils.nonNull(softMinEvictableIdleTime, DEFAULT_SOFT_MIN_EVICTABLE_IDLE_TIME);
    }

    /**
     * Sets the value for the {@code softMinEvictableIdleTime} configuration attribute for pools created with this configuration instance.
     *
     * @param softMinEvictableIdleTime The new setting of {@code softMinEvictableIdleTime} for this configuration instance
     * @see GenericObjectPool#getSoftMinEvictableIdleDuration()
     * @see GenericKeyedObjectPool#getSoftMinEvictableIdleDuration()
     * @since 2.10.0
     * @deprecated Use {@link #setSoftMinEvictableIdleDuration(Duration)}.
     */
    @Deprecated
    public void setSoftMinEvictableIdleTime(final Duration softMinEvictableIdleTime) {
        this.softMinEvictableIdleDuration = PoolImplUtils.nonNull(softMinEvictableIdleTime, DEFAULT_SOFT_MIN_EVICTABLE_IDLE_TIME);
    }

    /**
     * Sets the value for the {@code softMinEvictableIdleTime} configuration attribute for pools created with this configuration instance.
     *
     * @param softMinEvictableIdleTimeMillis The new setting of {@code softMinEvictableIdleTime} for this configuration instance
     * @see GenericObjectPool#getSoftMinEvictableIdleDuration()
     * @see GenericKeyedObjectPool#getSoftMinEvictableIdleDuration()
     * @deprecated Use {@link #setSoftMinEvictableIdleDuration(Duration)}.
     */
    @Deprecated
    public void setSoftMinEvictableIdleTimeMillis(final long softMinEvictableIdleTimeMillis) {
        setSoftMinEvictableIdleTime(Duration.ofMillis(softMinEvictableIdleTimeMillis));
    }

    /**
     * Sets the value for the {@code testOnBorrow} configuration attribute for pools created with this configuration instance.
     *
     * @param testOnBorrow The new setting of {@code testOnBorrow} for this configuration instance
     * @see GenericObjectPool#getTestOnBorrow()
     * @see GenericKeyedObjectPool#getTestOnBorrow()
     */
    public void setTestOnBorrow(final boolean testOnBorrow) {
        this.testOnBorrow = testOnBorrow;
    }

    /**
     * Sets the value for the {@code testOnCreate} configuration attribute for pools created with this configuration instance.
     *
     * @param testOnCreate The new setting of {@code testOnCreate} for this configuration instance
     * @see GenericObjectPool#getTestOnCreate()
     * @see GenericKeyedObjectPool#getTestOnCreate()
     * @since 2.2
     */
    public void setTestOnCreate(final boolean testOnCreate) {
        this.testOnCreate = testOnCreate;
    }

    /**
     * Sets the value for the {@code testOnReturn} configuration attribute for pools created with this configuration instance.
     *
     * @param testOnReturn The new setting of {@code testOnReturn} for this configuration instance
     * @see GenericObjectPool#getTestOnReturn()
     * @see GenericKeyedObjectPool#getTestOnReturn()
     */
    public void setTestOnReturn(final boolean testOnReturn) {
        this.testOnReturn = testOnReturn;
    }

    /**
     * Sets the value for the {@code testWhileIdle} configuration attribute for pools created with this configuration instance.
     *
     * @param testWhileIdle The new setting of {@code testWhileIdle} for this configuration instance
     * @see GenericObjectPool#getTestWhileIdle()
     * @see GenericKeyedObjectPool#getTestWhileIdle()
     */
    public void setTestWhileIdle(final boolean testWhileIdle) {
        this.testWhileIdle = testWhileIdle;
    }

    /**
     * Sets the value for the {@code timeBetweenEvictionRuns} configuration attribute for pools created with this configuration instance.
     *
     * @param timeBetweenEvictionRuns The new setting of {@code timeBetweenEvictionRuns} for this configuration instance
     * @see GenericObjectPool#getDurationBetweenEvictionRuns()
     * @see GenericKeyedObjectPool#getDurationBetweenEvictionRuns()
     * @since 2.10.0
     */
    public void setTimeBetweenEvictionRuns(final Duration timeBetweenEvictionRuns) {
        this.durationBetweenEvictionRuns = PoolImplUtils.nonNull(timeBetweenEvictionRuns, DEFAULT_DURATION_BETWEEN_EVICTION_RUNS);
    }

    /**
     * Sets the value for the {@code timeBetweenEvictionRuns} configuration attribute for pools created with this configuration instance.
     *
     * @param timeBetweenEvictionRunsMillis The new setting of {@code timeBetweenEvictionRuns} for this configuration instance
     * @see GenericObjectPool#getDurationBetweenEvictionRuns()
     * @see GenericKeyedObjectPool#getDurationBetweenEvictionRuns()
     * @deprecated Use {@link #setTimeBetweenEvictionRuns(Duration)}.
     */
    @Deprecated
    public void setTimeBetweenEvictionRunsMillis(final long timeBetweenEvictionRunsMillis) {
        setTimeBetweenEvictionRuns(Duration.ofMillis(timeBetweenEvictionRunsMillis));
    }

    @Override
    protected void toStringAppendFields(final StringBuilder builder) {
        builder.append("lifo=");
        builder.append(lifo);
        builder.append(", fairness=");
        builder.append(fairness);
        builder.append(", maxWaitDuration=");
        builder.append(maxWaitDuration);
        builder.append(", minEvictableIdleTime=");
        builder.append(minEvictableIdleDuration);
        builder.append(", softMinEvictableIdleTime=");
        builder.append(softMinEvictableIdleDuration);
        builder.append(", numTestsPerEvictionRun=");
        builder.append(numTestsPerEvictionRun);
        builder.append(", evictionPolicyClassName=");
        builder.append(evictionPolicyClassName);
        builder.append(", testOnCreate=");
        builder.append(testOnCreate);
        builder.append(", testOnBorrow=");
        builder.append(testOnBorrow);
        builder.append(", testOnReturn=");
        builder.append(testOnReturn);
        builder.append(", testWhileIdle=");
        builder.append(testWhileIdle);
        builder.append(", timeBetweenEvictionRuns=");
        builder.append(durationBetweenEvictionRuns);
        builder.append(", blockWhenExhausted=");
        builder.append(blockWhenExhausted);
        builder.append(", jmxEnabled=");
        builder.append(jmxEnabled);
        builder.append(", jmxNamePrefix=");
        builder.append(jmxNamePrefix);
        builder.append(", jmxNameBase=");
        builder.append(jmxNameBase);
    }
}
