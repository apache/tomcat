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
     * @return See {@link GenericKeyedObjectPool#getBlockWhenExhausted()}
     */
    boolean getBlockWhenExhausted();

    /**
     * @return See {@link GenericKeyedObjectPool#getFairness()}
     */
    boolean getFairness();

    /**
     * @return See {@link GenericKeyedObjectPool#getLifo()}
     */
    boolean getLifo();

    /**
     * @return See {@link GenericKeyedObjectPool#getMaxIdlePerKey()}
     */
    int getMaxIdlePerKey();

    /**
     * @return See {@link GenericKeyedObjectPool#getMaxTotal()}
     */
    int getMaxTotal();

    /**
     * @return See {@link GenericKeyedObjectPool#getMaxTotalPerKey()}
     */
    int getMaxTotalPerKey();

    /**
     * @return See {@link GenericKeyedObjectPool#getMaxWaitMillis()}
     */
    long getMaxWaitMillis();

    /**
     * @return See {@link GenericKeyedObjectPool#getMinEvictableIdleTimeMillis()}
     */
    long getMinEvictableIdleTimeMillis();

    /**
     * @return See {@link GenericKeyedObjectPool#getMinIdlePerKey()}
     */
    int getMinIdlePerKey();

    /**
     * @return See {@link GenericKeyedObjectPool#getNumActive()}
     */
    int getNumActive();

    /**
     * @return See {@link GenericKeyedObjectPool#getNumIdle()}
     */
    int getNumIdle();

    /**
     * @return See {@link GenericKeyedObjectPool#getNumTestsPerEvictionRun()}
     */
    int getNumTestsPerEvictionRun();

    /**
     * @return See {@link GenericKeyedObjectPool#getTestOnCreate()}
     * @since 2.2
     */
    boolean getTestOnCreate();

    /**
     * @return See {@link GenericKeyedObjectPool#getTestOnBorrow()}
     */
    boolean getTestOnBorrow();

    /**
     * @return See {@link GenericKeyedObjectPool#getTestOnReturn()}
     */
    boolean getTestOnReturn();

    /**
     * @return See {@link GenericKeyedObjectPool#getTestWhileIdle()}
     */
    boolean getTestWhileIdle();

    /**
     * @return See {@link GenericKeyedObjectPool#getTimeBetweenEvictionRunsMillis()}
     */
    long getTimeBetweenEvictionRunsMillis();

    /**
     * @return See {@link GenericKeyedObjectPool#isClosed()}
     */
    boolean isClosed();

    // Expose getters for monitoring attributes

    /**
     * @return See {@link GenericKeyedObjectPool#getNumActivePerKey()}
     */
    Map<String,Integer> getNumActivePerKey();

    /**
     * @return See {@link GenericKeyedObjectPool#getBorrowedCount()}
     */
    long getBorrowedCount();

    /**
     * @return See {@link GenericKeyedObjectPool#getReturnedCount()}
     */
    long getReturnedCount();

    /**
     * @return See {@link GenericKeyedObjectPool#getCreatedCount()}
     */
    long getCreatedCount();

    /**
     * @return See {@link GenericKeyedObjectPool#getDestroyedCount()}
     */
    long getDestroyedCount();

    /**
     * @return See {@link GenericKeyedObjectPool#getDestroyedByEvictorCount()}
     */
    long getDestroyedByEvictorCount();

    /**
     * @return See {@link GenericKeyedObjectPool#getDestroyedByBorrowValidationCount()}
     */
    long getDestroyedByBorrowValidationCount();

    /**
     * @return See {@link GenericKeyedObjectPool#getMeanActiveTimeMillis()}
     */
    long getMeanActiveTimeMillis();

    /**
     * @return See {@link GenericKeyedObjectPool#getMeanIdleTimeMillis()}
     */
    long getMeanIdleTimeMillis();

    /**
     * @return See {@link GenericKeyedObjectPool#getMaxBorrowWaitTimeMillis()}
     */
    long getMeanBorrowWaitTimeMillis();

    /**
     * @return See {@link GenericKeyedObjectPool#getMaxBorrowWaitTimeMillis()}
     */
    long getMaxBorrowWaitTimeMillis();

    /**
     * @return See {@link GenericKeyedObjectPool#getCreationStackTrace()}
     */
    String getCreationStackTrace();

    /**
     * @return See {@link GenericKeyedObjectPool#getNumWaiters()}
     */
    int getNumWaiters();

    /**
     * @return See {@link GenericKeyedObjectPool#getNumWaitersByKey()}
     */
    Map<String,Integer> getNumWaitersByKey();

    /**
     * @return See {@link GenericKeyedObjectPool#listAllObjects()}
     */
    Map<String,List<DefaultPooledObjectInfo>> listAllObjects();
}
