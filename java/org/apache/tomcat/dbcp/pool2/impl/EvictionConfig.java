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

/**
 * This class is used by pool implementations to pass configuration information
 * to {@link EvictionPolicy} instances. The {@link EvictionPolicy} may also have
 * its own specific configuration attributes.
 * <p>
 * This class is immutable and thread-safe.
 * </p>
 *
 * @since 2.0
 */
public class EvictionConfig {

    private static final Duration MAX_DURATION = Duration.ofMillis(Long.MAX_VALUE);
    private final Duration idleEvictDuration;
    private final Duration idleSoftEvictDuration;
    private final int minIdle;

    /**
     * Creates a new eviction configuration with the specified parameters.
     * Instances are immutable.
     *
     * @param idleEvictDuration Expected to be provided by
     *        {@link BaseGenericObjectPool#getMinEvictableIdleDuration()}
     * @param idleSoftEvictDuration Expected to be provided by
     *        {@link BaseGenericObjectPool#getSoftMinEvictableIdleDuration()}
     * @param minIdle Expected to be provided by
     *        {@link GenericObjectPool#getMinIdle()} or
     *        {@link GenericKeyedObjectPool#getMinIdlePerKey()}
     * @since 2.10.0
     */
    public EvictionConfig(final Duration idleEvictDuration, final Duration idleSoftEvictDuration, final int minIdle) {
        this.idleEvictDuration = PoolImplUtils.isPositive(idleEvictDuration) ? idleEvictDuration : MAX_DURATION;
        this.idleSoftEvictDuration = PoolImplUtils.isPositive(idleSoftEvictDuration) ? idleSoftEvictDuration : MAX_DURATION;
        this.minIdle = minIdle;
    }

    /**
     * Creates a new eviction configuration with the specified parameters.
     * Instances are immutable.
     *
     * @param poolIdleEvictMillis Expected to be provided by
     *        {@link BaseGenericObjectPool#getMinEvictableIdleDuration()}
     * @param poolIdleSoftEvictMillis Expected to be provided by
     *        {@link BaseGenericObjectPool#getSoftMinEvictableIdleDuration()}
     * @param minIdle Expected to be provided by
     *        {@link GenericObjectPool#getMinIdle()} or
     *        {@link GenericKeyedObjectPool#getMinIdlePerKey()}
     * @deprecated Use {@link #EvictionConfig(Duration, Duration, int)}.
     */
    @Deprecated
    public EvictionConfig(final long poolIdleEvictMillis, final long poolIdleSoftEvictMillis, final int minIdle) {
        this(Duration.ofMillis(poolIdleEvictMillis), Duration.ofMillis(poolIdleSoftEvictMillis), minIdle);
    }

    /**
     * Gets the {@code idleEvictTime} for this eviction configuration
     * instance.
     * <p>
     * How the evictor behaves based on this value will be determined by the
     * configured {@link EvictionPolicy}.
     * </p>
     *
     * @return The {@code idleEvictTime}.
     * @since 2.11.0
     */
    public Duration getIdleEvictDuration() {
        return idleEvictDuration;
    }

    /**
     * Gets the {@code idleEvictTime} for this eviction configuration
     * instance.
     * <p>
     * How the evictor behaves based on this value will be determined by the
     * configured {@link EvictionPolicy}.
     * </p>
     *
     * @return The {@code idleEvictTime} in milliseconds
     * @deprecated Use {@link #getIdleEvictDuration()}.
     */
    @Deprecated
    public long getIdleEvictTime() {
        return idleEvictDuration.toMillis();
    }

    /**
     * Gets the {@code idleEvictTime} for this eviction configuration
     * instance.
     * <p>
     * How the evictor behaves based on this value will be determined by the
     * configured {@link EvictionPolicy}.
     * </p>
     *
     * @return The {@code idleEvictTime}.
     * @since 2.10.0
     * @deprecated Use {@link #getIdleEvictDuration()}.
     */
    @Deprecated
    public Duration getIdleEvictTimeDuration() {
        return idleEvictDuration;
    }

    /**
     * Gets the {@code idleSoftEvictTime} for this eviction configuration
     * instance.
     * <p>
     * How the evictor behaves based on this value will be determined by the
     * configured {@link EvictionPolicy}.
     * </p>
     *
     * @return The (@code idleSoftEvictTime} in milliseconds
     * @since 2.11.0
     */
    public Duration getIdleSoftEvictDuration() {
        return idleSoftEvictDuration;
    }

    /**
     * Gets the {@code idleSoftEvictTime} for this eviction configuration
     * instance.
     * <p>
     * How the evictor behaves based on this value will be determined by the
     * configured {@link EvictionPolicy}.
     * </p>
     *
     * @return The (@code idleSoftEvictTime} in milliseconds
     * @deprecated Use {@link #getIdleSoftEvictDuration()}.
     */
    @Deprecated
    public long getIdleSoftEvictTime() {
        return idleSoftEvictDuration.toMillis();
    }

    /**
     * Gets the {@code idleSoftEvictTime} for this eviction configuration
     * instance.
     * <p>
     * How the evictor behaves based on this value will be determined by the
     * configured {@link EvictionPolicy}.
     * </p>
     *
     * @return The (@code idleSoftEvictTime} in milliseconds
     * @deprecated Use {@link #getIdleSoftEvictDuration()}.
     */
    @Deprecated
    public Duration getIdleSoftEvictTimeDuration() {
        return idleSoftEvictDuration;
    }

    /**
     * Gets the {@code minIdle} for this eviction configuration instance.
     * <p>
     * How the evictor behaves based on this value will be determined by the
     * configured {@link EvictionPolicy}.
     * </p>
     *
     * @return The {@code minIdle}
     */
    public int getMinIdle() {
        return minIdle;
    }

    /**
     * @since 2.4
     */
    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("EvictionConfig [idleEvictDuration=");
        builder.append(idleEvictDuration);
        builder.append(", idleSoftEvictDuration=");
        builder.append(idleSoftEvictDuration);
        builder.append(", minIdle=");
        builder.append(minIdle);
        builder.append("]");
        return builder.toString();
    }
}
