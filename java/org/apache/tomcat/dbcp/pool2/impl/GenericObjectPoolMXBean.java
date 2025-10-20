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
 * <h2>Note</h2>
 * <p>
 * This interface exists only to define those attributes and methods that will be made available via JMX. It must not be implemented by clients as it is subject
 * to change between major, minor and patch version releases of commons pool. Clients that implement this interface may not, therefore, be able to upgrade to a
 * new minor or patch release without requiring code changes.
 * </p>
 *
 * @since 2.0
 */
public interface GenericObjectPoolMXBean {

    /**
     * See {@link GenericObjectPool#getBlockWhenExhausted()}.
     *
     * @return See {@link GenericObjectPool#getBlockWhenExhausted()}.
     */
    boolean getBlockWhenExhausted();

    /**
     * See {@link GenericObjectPool#getBorrowedCount()}.
     *
     * @return See {@link GenericObjectPool#getBorrowedCount()}.
     */
    long getBorrowedCount();

    /**
     * See {@link GenericObjectPool#getCreatedCount()}.
     *
     * @return See {@link GenericObjectPool#getCreatedCount()}.
     */
    long getCreatedCount();

    /**
     * See {@link GenericObjectPool#getCreationStackTrace()}.
     *
     * @return See {@link GenericObjectPool#getCreationStackTrace()}.
     */
    String getCreationStackTrace();

    /**
     * See {@link GenericObjectPool#getDestroyedByBorrowValidationCount()}.
     *
     * @return See {@link GenericObjectPool#getDestroyedByBorrowValidationCount()}.
     */
    long getDestroyedByBorrowValidationCount();

    /**
     * See {@link GenericObjectPool#getDestroyedByEvictorCount()}.
     *
     * @return See {@link GenericObjectPool#getDestroyedByEvictorCount()}.
     */
    long getDestroyedByEvictorCount();

    /**
     * See {@link GenericObjectPool#getDestroyedCount()}.
     *
     * @return See {@link GenericObjectPool#getDestroyedCount()}.
     */
    long getDestroyedCount();

    /**
     * See {@link GenericObjectPool#getFactoryType()}.
     *
     * @return See {@link GenericObjectPool#getFactoryType()}.
     */
    String getFactoryType();

    /**
     * See {@link GenericObjectPool#getLifo()}.
     *
     * @return See {@link GenericObjectPool#getLifo()}.
     */
    boolean getFairness();

    /**
     * See {@link GenericObjectPool#getFairness()}.
     *
     * @return See {@link GenericObjectPool#getFairness()}.
     */
    boolean getLifo();

    /**
     * See {@link GenericObjectPool#getLogAbandoned()}.
     *
     * @return See {@link GenericObjectPool#getLogAbandoned()}.
     */
    boolean getLogAbandoned();

    /**
     * See {@link GenericObjectPool#getMaxBorrowWaitTimeMillis()}.
     *
     * @return See {@link GenericObjectPool#getMaxBorrowWaitTimeMillis()}.
     */
    @SuppressWarnings("javadoc")
    long getMaxBorrowWaitTimeMillis();

    /**
     * See {@link GenericObjectPool#getMaxIdle()}.
     *
     * @return See {@link GenericObjectPool#getMaxIdle()}.
     */
    int getMaxIdle();

    /**
     * See {@link GenericObjectPool#getMaxTotal()}.
     *
     * @return See {@link GenericObjectPool#getMaxTotal()}.
     */
    int getMaxTotal();

    /**
     * See {@link GenericObjectPool#getMaxWaitDuration()}.
     *
     * @return See {@link GenericObjectPool#getMaxWaitDuration()}.
     */
    long getMaxWaitMillis();

    /**
     * See {@link GenericObjectPool#getMeanActiveTimeMillis()}.
     *
     * @return See {@link GenericObjectPool#getMeanActiveTimeMillis()}.
     */
    @SuppressWarnings("javadoc")
    long getMeanActiveTimeMillis();

    /**
     * See {@link GenericObjectPool#getMeanBorrowWaitTimeMillis()}.
     *
     * @return See {@link GenericObjectPool#getMeanBorrowWaitTimeMillis()}.
     */
    @SuppressWarnings("javadoc")
    long getMeanBorrowWaitTimeMillis();

    // Getters for monitoring attributes

    /**
     * See {@link GenericObjectPool#getMeanIdleTimeMillis()}.
     *
     * @return See {@link GenericObjectPool#getMeanIdleTimeMillis()}.
     */
    @SuppressWarnings("javadoc")
    long getMeanIdleTimeMillis();

    /**
     * See {@link GenericObjectPool#getMinEvictableIdleDuration()}.
     *
     * @return See {@link GenericObjectPool#getMinEvictableIdleDuration()}.
     */
    long getMinEvictableIdleTimeMillis();

    /**
     * See {@link GenericObjectPool#getMinIdle()}.
     *
     * @return See {@link GenericObjectPool#getMinIdle()}.
     */
    int getMinIdle();

    /**
     * See {@link GenericObjectPool#getNumActive()}.
     *
     * @return See {@link GenericObjectPool#getNumActive()}.
     */
    int getNumActive();

    /**
     * See {@link GenericObjectPool#getNumIdle()}.
     *
     * @return See {@link GenericObjectPool#getNumIdle()}.
     */
    int getNumIdle();

    /**
     * See {@link GenericObjectPool#getNumTestsPerEvictionRun()}.
     *
     * @return See {@link GenericObjectPool#getNumTestsPerEvictionRun()}.
     */
    int getNumTestsPerEvictionRun();

    /**
     * See {@link GenericObjectPool#getNumWaiters()}.
     *
     * @return See {@link GenericObjectPool#getNumWaiters()}.
     */
    int getNumWaiters();

    /**
     * See {@link GenericObjectPool#getRemoveAbandonedOnBorrow()}.
     *
     * @return See {@link GenericObjectPool#getRemoveAbandonedOnBorrow()}.
     */
    boolean getRemoveAbandonedOnBorrow();

    /**
     * See {@link GenericObjectPool#getRemoveAbandonedOnMaintenance()}.
     *
     * @return See {@link GenericObjectPool#getRemoveAbandonedOnMaintenance()}.
     */
    boolean getRemoveAbandonedOnMaintenance();

    /**
     * See {@link GenericObjectPool#getRemoveAbandonedTimeoutDuration()}.
     *
     * @return See {@link GenericObjectPool#getRemoveAbandonedTimeoutDuration()}.
     */
    int getRemoveAbandonedTimeout();

    /**
     * See {@link GenericObjectPool#getReturnedCount()}.
     *
     * @return See {@link GenericObjectPool#getReturnedCount()}.
     */
    long getReturnedCount();

    /**
     * See {@link GenericObjectPool#getTestOnBorrow()}.
     *
     * @return See {@link GenericObjectPool#getTestOnBorrow()}.
     */
    boolean getTestOnBorrow();

    // Getters for abandoned object removal configuration

    /**
     * See {@link GenericObjectPool#getTestOnCreate()}.
     *
     * @return See {@link GenericObjectPool#getTestOnCreate()}.
     * @since 2.2
     */
    boolean getTestOnCreate();

    /**
     * See {@link GenericObjectPool#getTestOnReturn()}.
     *
     * @return See {@link GenericObjectPool#getTestOnReturn()}.
     */
    boolean getTestOnReturn();

    /**
     * See {@link GenericObjectPool#getTestWhileIdle()}.
     *
     * @return See {@link GenericObjectPool#getTestWhileIdle()}.
     */
    boolean getTestWhileIdle();

    /**
     * See {@link GenericObjectPool#getDurationBetweenEvictionRuns()}.
     *
     * @return See {@link GenericObjectPool#getDurationBetweenEvictionRuns()}.
     */
    long getTimeBetweenEvictionRunsMillis();

    /**
     * See {@link GenericObjectPool#isAbandonedConfig()}.
     *
     * @return See {@link GenericObjectPool#isAbandonedConfig()}.
     */
    boolean isAbandonedConfig();

    /**
     * See {@link GenericObjectPool#isClosed()}.
     *
     * @return See {@link GenericObjectPool#isClosed()}.
     */
    boolean isClosed();

    /**
     * See {@link GenericObjectPool#listAllObjects()}.
     *
     * @return See {@link GenericObjectPool#listAllObjects()}.
     */
    Set<DefaultPooledObjectInfo> listAllObjects();
}
