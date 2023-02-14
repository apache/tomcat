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

public interface ConnectionPoolMBean extends PoolConfiguration  {

    //=================================================================
    //       POOL STATS
    //=================================================================

    int getSize();

    int getIdle();

    int getActive();

    int getNumIdle();

    int getNumActive();

    int getWaitCount();

    long getBorrowedCount();

    long getReturnedCount();

    long getCreatedCount();

    long getReleasedCount();

    long getReconnectedCount();

    long getRemoveAbandonedCount();

    long getReleasedIdleCount();

    //=================================================================
    //       POOL OPERATIONS
    //=================================================================
    void checkIdle();

    void checkAbandoned();

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
