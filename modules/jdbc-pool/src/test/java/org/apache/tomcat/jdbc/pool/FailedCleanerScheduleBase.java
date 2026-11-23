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
package org.apache.tomcat.jdbc.pool;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;

/**
 * A cleaner whose schedule is refused has not started. If the pool still
 * publishes it as its cleaner, {@code reconcilePoolCleaner()} sees a task of
 * the configured interval and skips re-arming, so the pool silently loses its
 * eviction/abandoned maintenance until it closes or a different interval is
 * configured. A failed start must leave the reference empty, so the next
 * reconciliation retries.
 */
abstract class FailedCleanerScheduleBase extends JdbcPoolBaseTest {

    @Test
    void aFailedScheduleLeavesNoCleanerReferenceAndTheNextReconcileRetries() throws Exception {
        PoolProperties properties = new PoolProperties();
        properties.setJmxEnabled(false);
        properties.setInitialSize(0);
        properties.setMinIdle(0);
        properties.setTimeBetweenEvictionRunsMillis(-1);
        ConnectionPool pool = new ConnectionPool(properties);
        try {
            Field timerField = ConnectionPool.class.getDeclaredField("poolCleanTimer");
            timerField.setAccessible(true);
            Field cleanersField = ConnectionPool.class.getDeclaredField("cleaners");
            cleanersField.setAccessible(true);

            Timer savedTimer = (Timer) timerField.get(null);
            @SuppressWarnings("unchecked")
            Set<TimerTask> savedCleaners = new HashSet<>((Set<TimerTask>) cleanersField.get(null));

            PoolProperties sweeper = new PoolProperties();
            sweeper.setTimeBetweenEvictionRunsMillis(1000);
            sweeper.setMinEvictableIdleTimeMillis(60000);

            Timer refusingTimer = new Timer("refusing-pool-timer", true) {
                @Override
                public void schedule(TimerTask task, long delay, long period) {
                    throw new RuntimeException("refused schedule");
                }
            };
            try {
                timerField.set(null, refusingTimer);
                Assertions.assertThrows(RuntimeException.class,
                        () -> pool.initializePoolCleaner(sweeper),
                        "the refusing timer must make the cleaner start fail");
            } finally {
                timerField.set(null, savedTimer);
                @SuppressWarnings("unchecked")
                Set<TimerTask> registered = (Set<TimerTask>) cleanersField.get(null);
                registered.clear();
                registered.addAll(savedCleaners);
                refusingTimer.cancel();
            }

            int baseline = ConnectionPool.getPoolCleaners().size();
            pool.reconcilePoolCleaner(sweeper);
            Assertions.assertEquals(baseline + 1, ConnectionPool.getPoolCleaners().size(),
                    "a failed schedule must leave the cleaner reference empty so the next reconcile retries");
        } finally {
            pool.close(true);
        }
    }
}
