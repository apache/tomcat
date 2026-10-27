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

    /**
     * Notification type sent when the initialization of the pool fails,
     * because an interceptor could not be started or because an initial
     * connection of the pool could not be created and the failure is not
     * ignored through {@code ignoreExceptionOnPreLoad}. The notification
     * message carries the stack trace of the failure.
     */
    String NOTIFY_INIT = "INIT FAILED";

    /**
     * Notification type sent when the pool cannot establish a physical
     * connection to the database. The notification message carries the stack
     * trace of the connection failure.
     */
    String NOTIFY_CONNECT = "CONNECTION FAILED";

    /**
     * Notification type sent when a connection that has been checked out
     * longer than the remove-abandoned timeout is abandoned and removed from
     * the pool. The notification message carries the stack trace of where
     * the connection was borrowed, when the pool records those with
     * {@code logAbandoned}.
     */
    String NOTIFY_ABANDON = "CONNECTION ABANDONED";

    /**
     * Notification type sent when the {@code SlowQueryReportJmx} interceptor
     * reports a query that took longer than its configured threshold. This
     * pool MBean sends it only when the interceptor is configured with
     * {@code notifyPool=true}; otherwise the interceptor's own MBean sends
     * the notification.
     */
    String SLOW_QUERY_NOTIFICATION = "SLOW QUERY";

    /**
     * Notification type sent when the {@code SlowQueryReportJmx} interceptor
     * reports a query that failed. This pool MBean sends it only when the
     * interceptor is configured with {@code notifyPool=true}; otherwise the
     * interceptor's own MBean sends the notification.
     */
    String FAILED_QUERY_NOTIFICATION = "FAILED QUERY";

    /**
     * Notification type sent when the pool marks a connection as suspect
     * because it has been checked out longer than the suspect timeout and is
     * therefore suspected of having been abandoned. Unlike
     * {@link #NOTIFY_ABANDON} the connection is not removed from the pool.
     * The notification message carries the stack trace of where the
     * connection was borrowed, when the pool records those with
     * {@code logAbandoned}.
     */
    String SUSPECT_ABANDONED_NOTIFICATION = "SUSPECT CONNECTION ABANDONED";

    /**
     * Notification type sent when a borrow from the pool fails because the
     * pool is empty: the borrower was not willing to wait for a connection
     * or its wait timed out. It is sent once per failed borrow, so a pool
     * that stays exhausted sends one per failed borrow attempt.
     */
    String POOL_EMPTY = "POOL EMPTY";

    /**
     * Notification type sent when a connection that the pool had marked as
     * suspect is returned to the pool.
     */
    String SUSPECT_RETURNED_NOTIFICATION = "SUSPECT CONNECTION RETURNED";
}
