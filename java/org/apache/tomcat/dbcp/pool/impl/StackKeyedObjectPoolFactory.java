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

package org.apache.tomcat.dbcp.pool.impl;

import org.apache.tomcat.dbcp.pool.KeyedObjectPool;
import org.apache.tomcat.dbcp.pool.KeyedObjectPoolFactory;
import org.apache.tomcat.dbcp.pool.KeyedPoolableObjectFactory;

/**
 * A factory for creating {@link StackKeyedObjectPool} instances.
 *
 * @param <K> the type of keys in this pool
 * @param <V> the type of objects held in this pool
 *
 * @see StackKeyedObjectPool
 * @see KeyedObjectPoolFactory
 *
 * @author Rodney Waldhoff
 * @since Pool 1.0
 */
public class StackKeyedObjectPoolFactory<K, V> implements KeyedObjectPoolFactory<K, V> {
    /**
     * Create a new StackKeyedObjectPoolFactory.
     *
     * @see StackKeyedObjectPool#StackKeyedObjectPool()
     */
    public StackKeyedObjectPoolFactory() {
        this(null,StackKeyedObjectPool.DEFAULT_MAX_SLEEPING,StackKeyedObjectPool.DEFAULT_INIT_SLEEPING_CAPACITY);
    }

    /**
     * Create a new StackKeyedObjectPoolFactory.
     *
     * @param maxSleeping cap on the number of "sleeping" instances in the pool.
     * @see StackKeyedObjectPool#StackKeyedObjectPool(int)
     */
    public StackKeyedObjectPoolFactory(int maxSleeping) {
        this(null,maxSleeping,StackKeyedObjectPool.DEFAULT_INIT_SLEEPING_CAPACITY);
    }

    /**
     * Create a new StackKeyedObjectPoolFactory.
     *
     * @param maxSleeping cap on the number of "sleeping" instances in the pool.
     * @param initialCapacity initial size of the pool (this specifies the size of the container,
     * it does not cause the pool to be pre-populated.)
     * @see StackKeyedObjectPool#StackKeyedObjectPool(int, int)
     */
    public StackKeyedObjectPoolFactory(int maxSleeping, int initialCapacity) {
        this(null,maxSleeping,initialCapacity);
    }

    /**
     * Create a new StackKeyedObjectPoolFactory.
     *
     * @param factory the KeyedPoolableObjectFactory used by created pools.
     * @see StackKeyedObjectPool#StackKeyedObjectPool(KeyedPoolableObjectFactory)
     */
    public StackKeyedObjectPoolFactory(KeyedPoolableObjectFactory<K, V> factory) {
        this(factory,StackKeyedObjectPool.DEFAULT_MAX_SLEEPING,StackKeyedObjectPool.DEFAULT_INIT_SLEEPING_CAPACITY);
    }

    /**
     * Create a new StackKeyedObjectPoolFactory.
     *
     * @param factory the KeyedPoolableObjectFactory used by created pools.
     * @param maxSleeping cap on the number of "sleeping" instances in the pool.
     * @see StackKeyedObjectPool#StackKeyedObjectPool(KeyedPoolableObjectFactory, int)
     */
    public StackKeyedObjectPoolFactory(KeyedPoolableObjectFactory<K, V> factory, int maxSleeping) {
        this(factory,maxSleeping,StackKeyedObjectPool.DEFAULT_INIT_SLEEPING_CAPACITY);
    }

    /**
     * Create a new StackKeyedObjectPoolFactory.
     *
     * @param factory the KeyedPoolableObjectFactory used by created pools.
     * @param maxSleeping cap on the number of "sleeping" instances in the pool.
     * @param initialCapacity initial size of the pool (this specifies the size of the container,
     * it does not cause the pool to be pre-populated.)
     * @see StackKeyedObjectPool#StackKeyedObjectPool(KeyedPoolableObjectFactory, int, int)
     */
    public StackKeyedObjectPoolFactory(KeyedPoolableObjectFactory<K, V> factory, int maxSleeping, int initialCapacity) {
        _factory = factory;
        _maxSleeping = maxSleeping;
        _initCapacity = initialCapacity;
    }

    /**
     * Create a StackKeyedObjectPool with current property settings.
     *
     * @return a new StackKeyedObjectPool with the configured factory, maxSleeping and initialCapacity
     */
    @Override
    public KeyedObjectPool<K, V> createPool() {
        return new StackKeyedObjectPool<K, V>(_factory,_maxSleeping,_initCapacity);
    }

    /**
     * KeyedPoolableObjectFactory used by StackKeyedObjectPools created by this factory
     * @deprecated to be removed in pool 2.0
     */
    @Deprecated
    protected KeyedPoolableObjectFactory<K, V> _factory = null;

    /**
     * Maximum number of idle instances in each keyed pool for StackKeyedObjectPools created by this factory
     * @deprecated to be removed in pool 2.0
     */
    @Deprecated
    protected int _maxSleeping = StackKeyedObjectPool.DEFAULT_MAX_SLEEPING;

    /**
     * Initial capacity of StackKeyedObjectPools created by this factory.
     * @deprecated to be removed in pool 2.0
     */
    @Deprecated
    protected int _initCapacity = StackKeyedObjectPool.DEFAULT_INIT_SLEEPING_CAPACITY;

    /**
     * Returns the KeyedPoolableObjectFactory used by StackKeyedObjectPools created by this factory
     *
     * @return factory setting for created pools
     * @since 1.5.5
     */
    public KeyedPoolableObjectFactory<K, V> getFactory() {
        return _factory;
    }

    /**
     * Returns the maximum number of idle instances in each keyed pool for StackKeyedObjectPools created by this factory
     *
     * @return maxSleeping setting for created pools
     * @since 1.5.5
     */
    public int getMaxSleeping() {
        return _maxSleeping;
    }

    /**
     * Returns the initial capacity of StackKeyedObjectPools created by this factory.
     *
     * @return initial capacity setting for created pools
     * @since 1.5.5
     */
    public int getInitialCapacity() {
        return _initCapacity;
    }

}
