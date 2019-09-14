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

import java.util.Set;

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
 * @since 2.0
 */
public interface GenericObjectPoolMXBean {

    // Getters for basic configuration settings

    /**
     * @return See {@link GenericObjectPool#getBlockWhenExhausted()}
     */
    boolean getBlockWhenExhausted();

    /**
     * @return See {@link GenericObjectPool#getLifo()}
     */
    boolean getFairness();

    /**
     * @return See {@link GenericObjectPool#getFairness()}
     */
    boolean getLifo();

    /**
     * @return See {@link GenericObjectPool#getMaxIdle()}
     */
    int getMaxIdle();

    /**
     * See {@link GenericObjectPool#getMaxTotal()}
     * @return See {@link GenericObjectPool#getMaxTotal()}
     */
    int getMaxTotal();

    /**
     * @return See {@link GenericObjectPool#getMaxWaitMillis()}
     */
    long getMaxWaitMillis();

    /**
     * @return See {@link GenericObjectPool#getMinEvictableIdleTimeMillis()}
     */
    long getMinEvictableIdleTimeMillis();

    /**
     * @return See {@link GenericObjectPool#getMinIdle()}
     */
    int getMinIdle();

    /**
     * @return See {@link GenericObjectPool#getNumActive()}
     */
    int getNumActive();

    /**
     * @return See {@link GenericObjectPool#getNumIdle()}
     */
    int getNumIdle();

    /**
     * @return See {@link GenericObjectPool#getNumTestsPerEvictionRun()}
     */
    int getNumTestsPerEvictionRun();

    /**
     * @return See {@link GenericObjectPool#getTestOnCreate()}
     * @since 2.2
     */
    boolean getTestOnCreate();

    /**
     * @return See {@link GenericObjectPool#getTestOnBorrow()}
     */
    boolean getTestOnBorrow();

    /**
     * @return See {@link GenericObjectPool#getTestOnReturn()}
     */
    boolean getTestOnReturn();

    /**
     * @return See {@link GenericObjectPool#getTestWhileIdle()}
     */
    boolean getTestWhileIdle();

    /**
     * @return See {@link GenericObjectPool#getTimeBetweenEvictionRunsMillis()}
     */
    long getTimeBetweenEvictionRunsMillis();

    /**
     * @return See {@link GenericObjectPool#isClosed()}
     */
    boolean isClosed();

    // Getters for monitoring attributes

    /**
     * @return See {@link GenericObjectPool#getBorrowedCount()}
     */
    long getBorrowedCount();

    /**
     * @return See {@link GenericObjectPool#getReturnedCount()}
     */
    long getReturnedCount();

    /**
     * @return See {@link GenericObjectPool#getCreatedCount()}
     */
    long getCreatedCount();

    /**
     * @return See {@link GenericObjectPool#getDestroyedCount()}
     */
    long getDestroyedCount();

    /**
     * @return See {@link GenericObjectPool#getDestroyedByEvictorCount()}
     */
    long getDestroyedByEvictorCount();

    /**
     * @return See {@link GenericObjectPool#getDestroyedByBorrowValidationCount()}
     */
    long getDestroyedByBorrowValidationCount();

    /**
     * @return See {@link GenericObjectPool#getMeanActiveTimeMillis()}
     */
    long getMeanActiveTimeMillis();

    /**
     * @return See {@link GenericObjectPool#getMeanIdleTimeMillis()}
     */
    long getMeanIdleTimeMillis();

    /**
     * @return See {@link GenericObjectPool#getMeanBorrowWaitTimeMillis()}
     */
    long getMeanBorrowWaitTimeMillis();

    /**
     * @return See {@link GenericObjectPool#getMaxBorrowWaitTimeMillis()}
     */
    long getMaxBorrowWaitTimeMillis();

    /**
     * @return See {@link GenericObjectPool#getCreationStackTrace()}
     */
    String getCreationStackTrace();

    /**
     * @return See {@link GenericObjectPool#getNumWaiters()}
     */
    int getNumWaiters();

    // Getters for abandoned object removal configuration

    /**
     * @return See {@link GenericObjectPool#isAbandonedConfig()}
     */
    boolean isAbandonedConfig();

    /**
     * @return See {@link GenericObjectPool#getLogAbandoned()}
     */
    boolean getLogAbandoned();

    /**
     * @return See {@link GenericObjectPool#getRemoveAbandonedOnBorrow()}
     */
    boolean getRemoveAbandonedOnBorrow();

    /**
     * @return See {@link GenericObjectPool#getRemoveAbandonedOnMaintenance()}
     */
    boolean getRemoveAbandonedOnMaintenance();

    /**
     * @return See {@link GenericObjectPool#getRemoveAbandonedTimeout()}
     */
    int getRemoveAbandonedTimeout();

    /**
     * @return See {@link GenericObjectPool#getFactoryType()}
     */
    String getFactoryType();

    /**
     * @return See {@link GenericObjectPool#listAllObjects()}
     */
    Set<DefaultPooledObjectInfo> listAllObjects();
}
