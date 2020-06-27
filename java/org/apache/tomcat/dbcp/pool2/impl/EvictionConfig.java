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

    private final long idleEvictTime;
    private final long idleSoftEvictTime;
    private final int minIdle;

    /**
     * Create a new eviction configuration with the specified parameters.
     * Instances are immutable.
     *
     * @param poolIdleEvictTime Expected to be provided by
     *        {@link BaseGenericObjectPool#getMinEvictableIdleTimeMillis()}
     * @param poolIdleSoftEvictTime Expected to be provided by
     *        {@link BaseGenericObjectPool#getSoftMinEvictableIdleTimeMillis()}
     * @param minIdle Expected to be provided by
     *        {@link GenericObjectPool#getMinIdle()} or
     *        {@link GenericKeyedObjectPool#getMinIdlePerKey()}
     */
    public EvictionConfig(final long poolIdleEvictTime, final long poolIdleSoftEvictTime,
            final int minIdle) {
        if (poolIdleEvictTime > 0) {
            idleEvictTime = poolIdleEvictTime;
        } else {
            idleEvictTime = Long.MAX_VALUE;
        }
        if (poolIdleSoftEvictTime > 0) {
            idleSoftEvictTime = poolIdleSoftEvictTime;
        } else {
            idleSoftEvictTime  = Long.MAX_VALUE;
        }
        this.minIdle = minIdle;
    }

    /**
     * Obtain the {@code idleEvictTime} for this eviction configuration
     * instance.
     * <p>
     * How the evictor behaves based on this value will be determined by the
     * configured {@link EvictionPolicy}.
     *
     * @return The {@code idleEvictTime} in milliseconds
     */
    public long getIdleEvictTime() {
        return idleEvictTime;
    }

    /**
     * Obtain the {@code idleSoftEvictTime} for this eviction configuration
     * instance.
     * <p>
     * How the evictor behaves based on this value will be determined by the
     * configured {@link EvictionPolicy}.
     *
     * @return The (@code idleSoftEvictTime} in milliseconds
     */
    public long getIdleSoftEvictTime() {
        return idleSoftEvictTime;
    }

    /**
     * Obtain the {@code minIdle} for this eviction configuration instance.
     * <p>
     * How the evictor behaves based on this value will be determined by the
     * configured {@link EvictionPolicy}.
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
        builder.append("EvictionConfig [idleEvictTime=");
        builder.append(idleEvictTime);
        builder.append(", idleSoftEvictTime=");
        builder.append(idleSoftEvictTime);
        builder.append(", minIdle=");
        builder.append(minIdle);
        builder.append("]");
        return builder.toString();
    }
}
