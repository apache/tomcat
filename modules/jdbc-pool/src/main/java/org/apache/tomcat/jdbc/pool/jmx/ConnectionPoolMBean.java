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
 * <p>
 * Inheriting {@link PoolConfiguration} brings this surface the boxed-{@link Boolean} accessors
 * {@code isDefaultAutoCommit()} and {@code isDefaultReadOnly()}, and the JDK's MBean analyzer treats an {@code is...}
 * method as a getter only when it returns the primitive {@code boolean}: those two are therefore published as
 * operations alongside the {@code DefaultAutoCommit} and {@code DefaultReadOnly} attributes their {@code get...} pair
 * provides. Making either one a primitive {@code boolean} getter would hand that attribute two getters, and the MBean
 * could no longer be registered.
 */
public interface ConnectionPoolMBean extends PoolConfiguration {

    /**
     * Returns the number of connection slots the pool holds right now - the idle ones and the checked out ones
     * together.
     * <p>
     * This counts slots, not database sessions, and each of these counts reads the live state for itself. While
     * the pool is establishing connections a slot is claimed before its session is signed on, and releasing a
     * connection to a waiting borrower leaves a wake-up placeholder in the idle queue, so this count - and the idle
     * count - can include an entry with no session behind it. Two of these attributes read at the same moment can
     * still disagree: a {@code Size} against {@code Idle} plus {@code Active}, or an {@code Idle} against a
     * {@code NumIdle}, is a comparison of samples taken one after the other, not of one state read once.
     *
     * @return the number of connections the pool holds a slot for
     */
    int getSize();

    /**
     * Returns the number of idle connections in the pool, meaning the entries that sit in the idle queue - among which
     * there can be a wake-up placeholder with no session behind it. Like the other counts of this interface this is one
     * sample of live state, so {@link #getSize()} and {@link #getActive()} are read separately and need not add up to
     * it.
     *
     * @return idle connection count
     */
    int getIdle();

    /**
     * Returns the number of active (in-use) connections: the entries of the pool's busy set, which is what the pool
     * counts as checked out rather than idle. Like the other counts of this interface this is one sample of live state,
     * so it need not add up against {@link #getSize()} and {@link #getIdle()}, which are read separately.
     *
     * @return active connection count
     */
    int getActive();

    /**
     * Returns the number of idle connections in the pool. This is another name for {@link #getIdle()} - the
     * commons-dbcp naming - and reads the same count.
     *
     * @return idle connection count
     */
    int getNumIdle();

    /**
     * Returns the number of active (in-use) connections. This is another name for {@link #getActive()} - the
     * commons-dbcp naming - and reads the same count.
     *
     * @return active connection count
     */
    int getNumActive();

    /**
     * Returns the number of threads waiting for a connection. This is the count of borrowers parked in the wait loop of
     * the synchronous {@code getConnection}: a caller of {@code getConnectionAsync} is counted only while that call is
     * still running, and once it has been handed a future and waits on the queue behind it, that caller is no longer
     * counted here - the pool's own wake-up logic has to ask the queue about those waiters separately, because this
     * counter does not see them.
     *
     * @return wait count
     */
    int getWaitCount();

    /**
     * Returns the total number of connections borrowed since the pool was created or since the last call to
     * {@link #resetStats()}, whichever is more recent. A borrow is counted when the connection handle is handed to
     * the caller; a setup that fails before the hand-off is not counted as a borrow.
     *
     * @return borrowed count
     */
    long getBorrowedCount();

    /**
     * Returns the total number of connections returned since the pool was created or since the last call to
     * {@link #resetStats()}, whichever is more recent. A setup that fails before a connection reaches its borrower is
     * not counted as a return.
     *
     * @return returned count
     */
    long getReturnedCount();

    /**
     * Returns the total number of connections created since the pool was created or since the last call to
     * {@link #resetStats()}, whichever is more recent. This counts pooled objects the pool took into service, not
     * sessions that came up: an attempt to establish a connection that the database refused is counted here, and its
     * teardown is counted by {@link #getReleasedCount()}.
     *
     * @return created count
     */
    long getCreatedCount();

    /**
     * Returns the total number of connections released since the pool was created or since the last call to
     * {@link #resetStats()}, whichever is more recent. This is the counterpart of {@link #getCreatedCount()}: it counts
     * each pooled object once as it goes out of service, whether or not it ever established a session.
     *
     * @return released count
     */
    long getReleasedCount();

    /**
     * Returns the total number of reconnections since the pool was created or since the last call to
     * {@link #resetStats()}, whichever is more recent. A reconnection is one session the pool replaced under a pooled
     * object it already had: the borrower came with different credentials, the session had passed its
     * {@code maxAge}, or it could not be handed out as it stood.
     *
     * @return reconnected count
     */
    long getReconnectedCount();

    /**
     * Returns the total number of connections removed due to abandonment since the pool was created or since the last
     * call to {@link #resetStats()}, whichever is more recent. Only the connections the pool actually took away are
     * counted here; a connection that was merely marked as suspect is not counted.
     *
     * @return abandoned count
     */
    long getRemoveAbandonedCount();

    /**
     * Returns the total number of idle connections released since the pool was created or since the last call to
     * {@link #resetStats()}, whichever is more recent. Any pass that takes an idle connection out of the pool counts
     * here - evicting one the configuration had kept idle too long, taking back one a purge had made stale, or
     * dropping one that did not survive the idle validation or reconnection - and that pass may be the pool cleaner
     * working on its own timer or an explicit {@link #checkIdle()}, {@link #testIdle()} or {@link #purge()} call.
     *
     * @return released idle count
     */
    long getReleasedIdleCount();

    /**
     * Runs the idle-eviction pass over the connections that are currently idle. An idle connection the configuration
     * says should be evicted - one that has been idle past {@code minEvictableIdleTimeMillis}, or one a
     * {@link #purgeOnReturn()} or {@link #purge()} call made stale - is released, and the pass normally stops at the
     * {@code minIdle} floor. A connection a purge made stale is released regardless of that floor, so an explicit
     * call after a purge can take the pool below it. An idle connection a borrower claims while this runs is left to
     * that borrower. This is the pass the pool cleaner runs on its own timer, run on demand, and what it releases is
     * counted by {@link #getReleasedIdleCount()}.
     */
    void checkIdle();

    /**
     * Looks through the connections that are currently checked out for ones that have been held too long. A
     * connection past the remove-abandoned timeout is taken from its borrower and removed only while
     * {@code removeAbandoned} is enabled and {@code abandonWhenPercentageFull} lets the pool's occupancy allow an
     * abandon; each such removal is counted by {@link #getRemoveAbandonedCount()} and reported through
     * {@link #NOTIFY_ABANDON}. A checked-out connection that is not removed - because removal is not allowed, or
     * because it has not passed that timeout - keeps its borrower: one held longer than {@code suspectTimeout} is only
     * marked as suspect and reported through {@link #SUSPECT_ABANDONED_NOTIFICATION}. With {@code suspectTimeout} unset
     * and removal off, this operation reports nothing at all.
     */
    void checkAbandoned();

    /**
     * Runs the pass over the idle connections that validates them and takes out of the pool the ones that do not
     * survive it. The validation only runs when {@code testWhileIdle} is set; with that flag unset nothing is put to
     * the database. Whatever the flag, a connection whose {@code maxAge} has expired is reconnected first, and a
     * connection is released when that reconnection fails, when the validation runs and fails it, or when it reaches
     * the validation with no session behind it at all. Those releases are counted by
     * {@link #getReleasedIdleCount()}, and an idle connection a borrower claims while this runs is left to that
     * borrower.
     */
    void testIdle();

    /**
     * Purges all connections in the pool. For connections currently in use, these connections will be purged when
     * returned on the pool. This call also purges connections that are idle and in the pool. To only purge used/active
     * connections see {@link #purgeOnReturn()}.
     */
    void purge();

    /**
     * Purges connections when they are returned from the pool. This call does not purge idle connections until they are
     * used. To purge idle connections see {@link #purge()}.
     */
    void purgeOnReturn();

    /**
     * Resets the statistics of this pool: the seven counters of this interface - {@link #getBorrowedCount()},
     * {@link #getReturnedCount()}, {@link #getCreatedCount()}, {@link #getReleasedCount()},
     * {@link #getReconnectedCount()}, {@link #getRemoveAbandonedCount()} and {@link #getReleasedIdleCount()} - are
     * brought back to zero. Everything else about the pool - its connections, its configuration, its notifications -
     * is left as it is.
     */
    void resetStats();

    /**
     * Notification type sent when the initialization of the pool fails, because an interceptor could not be started or
     * because an initial connection of the pool could not be created and the failure is not ignored through
     * {@code ignoreExceptionOnPreLoad}. The notification message carries the stack trace of the failure.
     */
    String NOTIFY_INIT = "INIT FAILED";

    /**
     * Notification type sent when the pool cannot establish a physical connection to the database through the driver
     * it instantiated itself, that being the only route on which the pool meets the failure. A pool that takes its
     * sessions from a {@code DataSource} - one set through {@code dataSource} or resolved from {@code dataSourceJNDI}
     * - asks that data source for a session instead, and never sends this notification. The notification message
     * carries the stack trace of the connection failure.
     */
    String NOTIFY_CONNECT = "CONNECTION FAILED";

    /**
     * Notification type sent when a connection that has been checked out longer than the remove-abandoned timeout is
     * abandoned and removed from the pool. The notification message carries the stack trace of where the connection was
     * borrowed, when the pool records those with {@code logAbandoned}.
     */
    String NOTIFY_ABANDON = "CONNECTION ABANDONED";

    /**
     * Notification type sent when the {@code SlowQueryReportJmx} interceptor reports a query that took longer than its
     * configured threshold. Nothing reaches a listener that the interceptor does not report, so its
     * {@code logSlow} flag - on by default - switches this notification off as well, and a negative
     * {@code threshold} keeps every execution out of the slow report. This pool MBean sends it only when the
     * interceptor is configured with {@code notifyPool=true}; otherwise the interceptor's own MBean sends the
     * notification.
     */
    String SLOW_QUERY_NOTIFICATION = "SLOW QUERY";

    /**
     * Notification type sent when the {@code SlowQueryReportJmx} interceptor reports a query that failed - a failed
     * execution is reported whatever the threshold is. Nothing reaches a listener that the interceptor does not
     * report, so its {@code logFailed} flag, which is off by default, switches this notification off with it. This
     * pool MBean sends it only when the interceptor is configured with {@code notifyPool=true}; otherwise the
     * interceptor's own MBean sends the notification.
     */
    String FAILED_QUERY_NOTIFICATION = "FAILED QUERY";

    /**
     * Notification type sent when the pool marks a connection as suspect because it has been checked out longer than
     * the suspect timeout and is therefore suspected of having been abandoned. Unlike {@link #NOTIFY_ABANDON} the
     * connection is not removed from the pool. The notification message carries the stack trace of where the connection
     * was borrowed, when the pool records those with {@code logAbandoned}.
     */
    String SUSPECT_ABANDONED_NOTIFICATION = "SUSPECT CONNECTION ABANDONED";

    /**
     * Notification type sent when a borrow from the pool fails because the pool is empty: the borrower was not willing
     * to wait for a connection or its wait timed out. It is sent once per failed borrow, so a pool that stays exhausted
     * reports one per failed borrow attempt. Delivery is best effort: the notification is accepted for delivery or
     * dropped whole, and the queue it is handed to has a fixed capacity, so a pool that reports these faster than its
     * listeners drain them loses notifications - logging each loss - rather than holding them up or growing without
     * limit.
     */
    String POOL_EMPTY = "POOL EMPTY";

    /**
     * Notification type sent when a connection that the pool had marked as suspect is returned to the pool.
     */
    String SUSPECT_RETURNED_NOTIFICATION = "SUSPECT CONNECTION RETURNED";
}
