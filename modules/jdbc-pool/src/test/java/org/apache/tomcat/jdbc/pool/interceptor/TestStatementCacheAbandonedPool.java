/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.tomcat.jdbc.pool.interceptor;

import java.lang.ref.WeakReference;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;
import org.apache.tomcat.jdbc.pool.ConnectionPool;
import org.apache.tomcat.jdbc.pool.PoolProperties;

/**
 * The static per-pool cache-size registry must not pin a pool that is
 * abandoned without closing. Held strongly, the pool stays reachable with its
 * properties, driver class loader and pooled connections for the life of the
 * JVM.
 */
class TestStatementCacheAbandonedPool extends JdbcPoolBaseTest {

    private static ConnectionPool abandonedPool() throws Exception {
        PoolProperties properties = new PoolProperties();
        properties.setUrl(getUrl());
        properties.setDriverClassName("org.h2.Driver");
        properties.setUsername("sa");
        properties.setPassword("");
        properties.setJmxEnabled(false);
        properties.setInitialSize(0);
        properties.setMinIdle(0);
        // no cleaner thread to retain the abandoned pool through its task
        properties.setTimeBetweenEvictionRunsMillis(0);
        properties.setJdbcInterceptors(
                StatementCache.class.getName() + "(prepared=true,max=5)");
        return new ConnectionPool(properties);
    }

    private static boolean stillReachable(WeakReference<ConnectionPool> ref) throws InterruptedException {
        long deadline = System.currentTimeMillis() + AWAIT_TIMEOUT_MS;
        while (ref.get() != null && System.currentTimeMillis() < deadline) {
            System.gc();
            Thread.sleep(20);
        }
        return ref.get() != null;
    }

    @Test
    void anAbandonedPoolIsCollectedInsteadOfPinnedByTheCacheSizeMap() throws Exception {
        ConnectionPool pool = abandonedPool();
        WeakReference<ConnectionPool> probe = new WeakReference<>(pool);
        pool = null;

        Assertions.assertFalse(stillReachable(probe),
                "the static cache-size registry must not pin a pool that was abandoned"
                        + " without closing");
    }
}
