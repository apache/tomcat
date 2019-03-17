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

import org.apache.tomcat.dbcp.pool.ObjectPool;
import org.apache.tomcat.dbcp.pool.ObjectPoolFactory;
import org.apache.tomcat.dbcp.pool.PoolableObjectFactory;

/**
 * A factory for creating {@link StackObjectPool} instances.
 *
 * @param <T> the type of objects held in this pool
 *
 * @see StackObjectPool
 * @see StackKeyedObjectPoolFactory
 *
 * @author Rodney Waldhoff
 * @since Pool 1.0
 */
public class StackObjectPoolFactory<T> implements ObjectPoolFactory<T> {
    /**
     * Create a new StackObjectPoolFactory.
     *
     * @see StackObjectPool#StackObjectPool()
     * @deprecated to be removed in pool 2.0 - use {@link #StackObjectPoolFactory(PoolableObjectFactory)}
     */
    @Deprecated
    public StackObjectPoolFactory() {
        this(null,StackObjectPool.DEFAULT_MAX_SLEEPING,StackObjectPool.DEFAULT_INIT_SLEEPING_CAPACITY);
    }

    /**
     * Create a new StackObjectPoolFactory.
     *
     * @param maxIdle cap on the number of "sleeping" instances in the pool.
     * @see StackObjectPool#StackObjectPool(int)
     * @deprecated to be removed in pool 2.0 - use {@link #StackObjectPoolFactory(PoolableObjectFactory, int)}
     */
    @Deprecated
    public StackObjectPoolFactory(int maxIdle) {
        this(null,maxIdle,StackObjectPool.DEFAULT_INIT_SLEEPING_CAPACITY);
    }

    /**
     * Create a new StackObjectPoolFactory.
     *
     * @param maxIdle cap on the number of "sleeping" instances in the pool.
     * @param initIdleCapacity - initial size of the pool (this specifies the size of the container,
     * it does not cause the pool to be pre-populated.)
     * @see StackObjectPool#StackObjectPool(int, int)
     * @deprecated to be removed in pool 2.0 - use {@link #StackObjectPoolFactory(PoolableObjectFactory, int, int)}
     */
    @Deprecated
    public StackObjectPoolFactory(int maxIdle, int initIdleCapacity) {
        this(null,maxIdle,initIdleCapacity);
    }

    /**
     * Create a new StackObjectPoolFactory.
     *
     * @param factory the PoolableObjectFactory used by created pools.
     * @see StackObjectPool#StackObjectPool(PoolableObjectFactory)
     */
    public StackObjectPoolFactory(PoolableObjectFactory<T> factory) {
        this(factory,StackObjectPool.DEFAULT_MAX_SLEEPING,StackObjectPool.DEFAULT_INIT_SLEEPING_CAPACITY);
    }

    /**
     * Create a new StackObjectPoolFactory.
     *
     * @param factory the PoolableObjectFactory used by created pools.
     * @param maxIdle cap on the number of "sleeping" instances in the pool.
     */
    public StackObjectPoolFactory(PoolableObjectFactory<T> factory, int maxIdle) {
        this(factory,maxIdle,StackObjectPool.DEFAULT_INIT_SLEEPING_CAPACITY);
    }

    /**
     * Create a new StackObjectPoolFactory.
     *
     * @param factory the PoolableObjectFactory used by created pools.
     * @param maxIdle cap on the number of "sleeping" instances in the pool.
     * @param initIdleCapacity - initial size of the pool (this specifies the size of the container,
     * it does not cause the pool to be pre-populated.)
     */
    public StackObjectPoolFactory(PoolableObjectFactory<T> factory, int maxIdle, int initIdleCapacity) {
        _factory = factory;
        _maxSleeping = maxIdle;
        _initCapacity = initIdleCapacity;
    }

    /**
     * Create a StackObjectPool.
     *
     * @return a new StackObjectPool with the configured factory, maxIdle and initial capacity settings
     */
    @Override
    public ObjectPool<T> createPool() {
        return new StackObjectPool<T>(_factory,_maxSleeping,_initCapacity);
    }

    /**
     * The PoolableObjectFactory used by created pools.
     * @deprecated to be made private in pool 2.0
     */
    @Deprecated
    protected PoolableObjectFactory<T> _factory = null;

    /**
     * The maximum number of idle instances in created pools.
     * @deprecated to be made private in pool 2.0
     */
    @Deprecated
    protected int _maxSleeping = StackObjectPool.DEFAULT_MAX_SLEEPING;

    /**
     * The initial size of created pools.
     * @deprecated to be made private in pool 2.0
     */
    @Deprecated
    protected int _initCapacity = StackObjectPool.DEFAULT_INIT_SLEEPING_CAPACITY;

    /**
     * Returns the factory used by created pools.
     *
     * @return the PoolableObjectFactory used by created pools
     * @since 1.5.5
     */
    public PoolableObjectFactory<T> getFactory() {
        return _factory;
    }

    /**
     * Returns the maxIdle setting for created pools.
     *
     * @return the maximum number of idle instances in created pools
     * @since 1.5.5
     */
    public int getMaxSleeping() {
        return _maxSleeping;
    }

    /**
     * Returns the initial capacity of created pools.
     *
     * @return size of created containers (created pools are not pre-populated)
     * @since 1.5.5
     */
    public int getInitCapacity() {
        return _initCapacity;
    }

}
