/* Licensed to the Apache Software Foundation (ASF) under one or more
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
package org.apache.tomcat.jdbc.pool.jmx;

import org.apache.tomcat.jdbc.pool.PoolConfiguration;

/**
 * JMX MBean interface for monitoring and managing a JDBC connection pool.
 */
public interface ConnectionPoolMBean extends PoolConfiguration  {

    //=================================================================
    //       POOL STATS
    //=================================================================

    /**
     * Returns the total number of connections in the pool.
     * @return total connection count
     */
    int getSize();

    /**
     * Returns the number of idle connections in the pool.
     * @return idle connection count
     */
    int getIdle();

    /**
     * Returns the number of active (in-use) connections.
     * @return active connection count
     */
    int getActive();

    /**
     * Returns the number of idle connections in the pool.
     * @return idle connection count
     */
    int getNumIdle();

    /**
     * Returns the number of active (in-use) connections.
     * @return active connection count
     */
    int getNumActive();

    /**
     * Returns the number of threads waiting for a connection.
     * @return wait count
     */
    int getWaitCount();

    /**
     * Returns the total number of connections borrowed since pool creation.
     * @return borrowed count
     */
    long getBorrowedCount();

    /**
     * Returns the total number of connections returned since pool creation.
     * @return returned count
     */
    long getReturnedCount();

    /**
     * Returns the total number of connections created since pool creation.
     * @return created count
     */
    long getCreatedCount();

    /**
     * Returns the total number of connections released since pool creation.
     * @return released count
     */
    long getReleasedCount();

    /**
     * Returns the total number of reconnections since pool creation.
     * @return reconnected count
     */
    long getReconnectedCount();

    /**
     * Returns the total number of connections removed due to abandonment.
     * @return abandoned count
     */
    long getRemoveAbandonedCount();

    /**
     * Returns the total number of idle connections released by the pool cleaner.
     * @return released idle count
     */
    long getReleasedIdleCount();

    //=================================================================
    //       POOL OPERATIONS
    //=================================================================
    /**
     * Checks and cleans up idle connections in the pool.
     */
    void checkIdle();

    /**
     * Checks for and removes abandoned connections from the pool.
     */
    void checkAbandoned();

    /**
     * Validates all idle connections in the pool.
     */
    void testIdle();

    /**
     * Purges all connections in the pool.
     * For connections currently in use, these connections will be
     * purged when returned on the pool. This call also
     * purges connections that are idle and in the pool
     * To only purge used/active connections see {@link #purgeOnReturn()}
     */
    void purge();

    /**
     * Purges connections when they are returned from the pool.
     * This call does not purge idle connections until they are used.
     * To purge idle connections see {@link #purge()}
     */
    void purgeOnReturn();

    /**
     * reset the statistics of this pool.
     */
    void resetStats();

    //=================================================================
    //       POOL NOTIFICATIONS
    //=================================================================


}
