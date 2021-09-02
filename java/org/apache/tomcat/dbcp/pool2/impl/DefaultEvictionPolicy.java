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

import org.apache.tomcat.dbcp.pool2.PooledObject;

/**
 * Provides the default implementation of {@link EvictionPolicy} used by the pools.
 * <p>
 * Objects will be evicted if the following conditions are met:
 * </p>
 * <ul>
 * <li>The object has been idle longer than
 *     {@link GenericObjectPool#getMinEvictableIdleDuration()} /
 *     {@link GenericKeyedObjectPool#getMinEvictableIdleDuration()}</li>
 * <li>There are more than {@link GenericObjectPool#getMinIdle()} /
 *     {@link GenericKeyedObjectPoolConfig#getMinIdlePerKey()} idle objects in
 *     the pool and the object has been idle for longer than
 *     {@link GenericObjectPool#getSoftMinEvictableIdleDuration()} /
 *     {@link GenericKeyedObjectPool#getSoftMinEvictableIdleDuration()}
 * </ul>
 * <p>
 * This class is immutable and thread-safe.
 * </p>
 *
 * @param <T> the type of objects in the pool.
 *
 * @since 2.0
 */
public class DefaultEvictionPolicy<T> implements EvictionPolicy<T> {

    @Override
    public boolean evict(final EvictionConfig config, final PooledObject<T> underTest, final int idleCount) {
        // @formatter:off
        return (config.getIdleSoftEvictDuration().compareTo(underTest.getIdleDuration()) < 0 &&
                config.getMinIdle() < idleCount) ||
                config.getIdleEvictDuration().compareTo(underTest.getIdleDuration()) < 0;
        // @formatter:on
    }
}
