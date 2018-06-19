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

import java.util.List;
import java.util.Map;

/**
 * Defines the methods that will be made available via JMX.
 *
 * NOTE: This interface exists only to define those attributes and methods that
 *       will be made available via JMX. It must not be implemented by clients
 *       as it is subject to change between major, minor and patch version
 *       releases of commons pool. Clients that implement this interface may
 *       not, therefore, be able to upgrade to a new minor or patch release
 *       without requiring code changes.
 *
 * @param <K> The type of keys maintained by the pool.
 *
 * @since 2.0
 */
public interface GenericKeyedObjectPoolMXBean<K> {

    // Expose getters for configuration settings

    /**
     * See {@link GenericKeyedObjectPool#getBlockWhenExhausted()}
     * @return See {@link GenericKeyedObjectPool#getBlockWhenExhausted()}
     */
    boolean getBlockWhenExhausted();

    /**
     * See {@link GenericKeyedObjectPool#getFairness()}
     * @return See {@link GenericKeyedObjectPool#getFairness()}
     */
    boolean getFairness();

    /**
     * See {@link GenericKeyedObjectPool#getLifo()}
     * @return See {@link GenericKeyedObjectPool#getLifo()}
     */
    boolean getLifo();

    /**
     * See {@link GenericKeyedObjectPool#getMaxIdlePerKey()}
     * @return See {@link GenericKeyedObjectPool#getMaxIdlePerKey()}
     */
    int getMaxIdlePerKey();

    /**
     * See {@link GenericKeyedObjectPool#getMaxTotal()}
     * @return See {@link GenericKeyedObjectPool#getMaxTotal()}
     */
    int getMaxTotal();

    /**
     * See {@link GenericKeyedObjectPool#getMaxTotalPerKey()}
     * @return See {@link GenericKeyedObjectPool#getMaxTotalPerKey()}
     */
    int getMaxTotalPerKey();

    /**
     * See {@link GenericKeyedObjectPool#getMaxWaitMillis()}
     * @return See {@link GenericKeyedObjectPool#getMaxWaitMillis()}
     */
    long getMaxWaitMillis();

    /**
     * See {@link GenericKeyedObjectPool#getMinEvictableIdleTimeMillis()}
     * @return See {@link GenericKeyedObjectPool#getMinEvictableIdleTimeMillis()}
     */
    long getMinEvictableIdleTimeMillis();

    /**
     * See {@link GenericKeyedObjectPool#getMinIdlePerKey()}
     * @return See {@link GenericKeyedObjectPool#getMinIdlePerKey()}
     */
    int getMinIdlePerKey();

    /**
     * See {@link GenericKeyedObjectPool#getNumActive()}
     * @return See {@link GenericKeyedObjectPool#getNumActive()}
     */
    int getNumActive();

    /**
     * See {@link GenericKeyedObjectPool#getNumIdle()}
     * @return See {@link GenericKeyedObjectPool#getNumIdle()}
     */
    int getNumIdle();

    /**
     * See {@link GenericKeyedObjectPool#getNumTestsPerEvictionRun()}
     * @return See {@link GenericKeyedObjectPool#getNumTestsPerEvictionRun()}
     */
    int getNumTestsPerEvictionRun();

    /**
     * See {@link GenericKeyedObjectPool#getTestOnCreate()}
     * @return See {@link GenericKeyedObjectPool#getTestOnCreate()}
     * @since 2.2
     */
    boolean getTestOnCreate();

    /**
     * See {@link GenericKeyedObjectPool#getTestOnBorrow()}
     * @return See {@link GenericKeyedObjectPool#getTestOnBorrow()}
     */
    boolean getTestOnBorrow();

    /**
     * See {@link GenericKeyedObjectPool#getTestOnReturn()}
     * @return See {@link GenericKeyedObjectPool#getTestOnReturn()}
     */
    boolean getTestOnReturn();

    /**
     * See {@link GenericKeyedObjectPool#getTestWhileIdle()}
     * @return See {@link GenericKeyedObjectPool#getTestWhileIdle()}
     */
    boolean getTestWhileIdle();

    /**
     * See {@link GenericKeyedObjectPool#getTimeBetweenEvictionRunsMillis()}
     * @return See {@link GenericKeyedObjectPool#getTimeBetweenEvictionRunsMillis()}
     */
    long getTimeBetweenEvictionRunsMillis();

    /**
     * See {@link GenericKeyedObjectPool#isClosed()}
     * @return See {@link GenericKeyedObjectPool#isClosed()}
     */
    boolean isClosed();

    // Expose getters for monitoring attributes

    /**
     * See {@link GenericKeyedObjectPool#getNumActivePerKey()}
     * @return See {@link GenericKeyedObjectPool#getNumActivePerKey()}
     */
    Map<String,Integer> getNumActivePerKey();

    /**
     * See {@link GenericKeyedObjectPool#getBorrowedCount()}
     * @return See {@link GenericKeyedObjectPool#getBorrowedCount()}
     */
    long getBorrowedCount();

    /**
     * See {@link GenericKeyedObjectPool#getReturnedCount()}
     * @return See {@link GenericKeyedObjectPool#getReturnedCount()}
     */
    long getReturnedCount();

    /**
     * See {@link GenericKeyedObjectPool#getCreatedCount()}
     * @return See {@link GenericKeyedObjectPool#getCreatedCount()}
     */
    long getCreatedCount();

    /**
     * See {@link GenericKeyedObjectPool#getDestroyedCount()}
     * @return See {@link GenericKeyedObjectPool#getDestroyedCount()}
     */
    long getDestroyedCount();

    /**
     * See {@link GenericKeyedObjectPool#getDestroyedByEvictorCount()}
     * @return See {@link GenericKeyedObjectPool#getDestroyedByEvictorCount()}
     */
    long getDestroyedByEvictorCount();

    /**
     * See {@link GenericKeyedObjectPool#getDestroyedByBorrowValidationCount()}
     * @return See {@link GenericKeyedObjectPool#getDestroyedByBorrowValidationCount()}
     */
    long getDestroyedByBorrowValidationCount();

    /**
     * See {@link GenericKeyedObjectPool#getMeanActiveTimeMillis()}
     * @return See {@link GenericKeyedObjectPool#getMeanActiveTimeMillis()}
     */
    long getMeanActiveTimeMillis();

    /**
     * See {@link GenericKeyedObjectPool#getMeanIdleTimeMillis()}
     * @return See {@link GenericKeyedObjectPool#getMeanIdleTimeMillis()}
     */
    long getMeanIdleTimeMillis();

    /**
     * See {@link GenericKeyedObjectPool#getMaxBorrowWaitTimeMillis()}
     * @return See {@link GenericKeyedObjectPool#getMaxBorrowWaitTimeMillis()}
     */
    long getMeanBorrowWaitTimeMillis();

    /**
     * See {@link GenericKeyedObjectPool#getMaxBorrowWaitTimeMillis()}
     * @return See {@link GenericKeyedObjectPool#getMaxBorrowWaitTimeMillis()}
     */
    long getMaxBorrowWaitTimeMillis();

    /**
     * See {@link GenericKeyedObjectPool#getCreationStackTrace()}
     * @return See {@link GenericKeyedObjectPool#getCreationStackTrace()}
     */
    String getCreationStackTrace();

    /**
     * See {@link GenericKeyedObjectPool#getNumWaiters()}
     * @return See {@link GenericKeyedObjectPool#getNumWaiters()}
     */
    int getNumWaiters();

    /**
     * See {@link GenericKeyedObjectPool#getNumWaitersByKey()}
     * @return See {@link GenericKeyedObjectPool#getNumWaitersByKey()}
     */
    Map<String,Integer> getNumWaitersByKey();

    /**
     * See {@link GenericKeyedObjectPool#listAllObjects()}
     * @return See {@link GenericKeyedObjectPool#listAllObjects()}
     */
    Map<String,List<DefaultPooledObjectInfo>> listAllObjects();
}
