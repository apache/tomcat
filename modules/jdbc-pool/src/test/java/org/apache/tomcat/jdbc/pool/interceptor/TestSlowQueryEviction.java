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

import java.sql.Connection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;
import org.apache.tomcat.jdbc.pool.DataSource;
import org.apache.tomcat.jdbc.pool.interceptor.SlowQueryReport.QueryStats;

/**
 * Eviction from the pool-wide query statistics map. The map is shared by
 * every connection of a pool, so an eviction races with the inserts and
 * removals of the other borrower threads: the map can still be over
 * capacity once the eviction has worked through its own snapshot. Running
 * past the end of the snapshot used to throw an
 * {@code IndexOutOfBoundsException} into the reporting code, which reaches
 * the application through the unguarded report call site - for a query that
 * had already been executed successfully.
 */
class TestSlowQueryEviction extends JdbcPoolBaseTest {

    /*
     * Stands in for the other borrower threads: every entry the eviction
     * removes is replaced by a freshly inserted one, so the map is still
     * over capacity once the snapshot the eviction works through has been
     * exhausted.
     */
    private static final class GrowingMap extends ConcurrentHashMap<String, QueryStats> {
        private static final long serialVersionUID = 1L;
        private final AtomicInteger inserts = new AtomicInteger();
        private final int limit;

        GrowingMap(int inserts) {
            this.limit = inserts;
        }

        @Override
        public QueryStats remove(Object key) {
            QueryStats removed = super.remove(key);
            int inserted = inserts.getAndIncrement();
            if (inserted < limit) {
                String sql = "raced-" + inserted;
                QueryStats stats = new QueryStats(sql);
                stats.add(1, 10000 + inserted);
                put(sql, stats);
            }
            return removed;
        }
    }

    @Test
    void evictionGivesUpOnceItsSnapshotIsWorkedThrough() {
        SlowQueryReport report = new SlowQueryReport();
        report.setMaxQueries(4);
        GrowingMap stats = new GrowingMap(10);
        for (int i = 0; i < 5; i++) {
            QueryStats entry = new QueryStats("sql-" + i);
            entry.add(1, 1000 + i);
            stats.put("sql-" + i, entry);
        }

        Assertions.assertDoesNotThrow(() -> report.removeOldest(stats),
                "an eviction that cannot catch up must not fail the caller");
        Assertions.assertTrue(stats.size() <= 15,
                "the map must stay bounded, was " + stats.size());
    }

    @Test
    void evictionRemovesTheLeastRecentlyInvokedQueries() {
        SlowQueryReport report = new SlowQueryReport();
        report.setMaxQueries(2);
        ConcurrentHashMap<String, QueryStats> stats = new ConcurrentHashMap<>();
        QueryStats oldStats = new QueryStats("old");
        oldStats.add(1, 1000);
        QueryStats recentStats = new QueryStats("recent");
        recentStats.add(1, 2000);
        QueryStats neverUsed = new QueryStats("never-used");
        stats.put("old", oldStats);
        stats.put("recent", recentStats);
        stats.put("never-used", neverUsed);

        report.removeOldest(stats);

        Assertions.assertEquals(2, stats.size(), "down to the configured maximum");
        Assertions.assertTrue(stats.containsKey("never-used"),
                "a query that was never invoked counts as the newest of all");
        Assertions.assertTrue(stats.containsKey("recent"), "the more recent one survives");
        Assertions.assertFalse(stats.containsKey("old"), "the least recent one is evicted");
    }

    /*
     * The eviction path as the borrowers actually use it: the statistics
     * collector of a running pool, hammered with distinct SQL from several
     * threads at a tiny capacity so that evictions overlap constantly. The
     * statistics are the pool's own, taken from the interceptor chain of a
     * borrowed connection.
     */
    @Test
    void concurrentEvictionNeverThrowsOutOfTheStatisticsCollector()
            throws Exception {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setJdbcInterceptors(
                "org.apache.tomcat.jdbc.pool.interceptor.SlowQueryReport"
                        + "(maxQueries=16)");
        ds.createPool();
        String poolName = ds.getPoolProperties().getPoolName();

        int threads = 48;
        int perThread = 1000;
        ConcurrentHashMap<String, QueryStats> stats;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CopyOnWriteArrayList<Throwable> failures = new CopyOnWriteArrayList<>();
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        SlowQueryReport report;
        try (Connection c = ds.getConnection()) {
            report = interceptorOf(c, SlowQueryReport.class);
            Assertions.assertNotNull(report, "the interceptor must be in the chain");
            for (int t = 0; t < threads; t++) {
                int id = t;
                pool.execute(() -> {
                    ready.countDown();
                    try {
                        start.await();
                        for (int i = 0; i < perThread; i++) {
                            report.getQueryStats("SELECT 1 /* " + id + '-' + i
                                    + " */");
                        }
                    } catch (Throwable e) {
                        failures.add(e);
                    }
                });
            }
            ready.await();
            start.countDown();
            pool.shutdown();
            Assertions.assertTrue(pool.awaitTermination(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS),
                    "the borrowers must not wedge each other");
        } finally {
            pool.shutdownNow();
        }

        if (!failures.isEmpty()) {
            Throwable first = failures.get(0);
            throw new AssertionError("collecting query statistics must never "
                    + "fail the caller, " + failures.size() + " thread(s) "
                    + "failed, first: " + first, first);
        }
        int size = SlowQueryReport.getPoolStats(poolName).size();
        // Eviction can only do as much work as its own snapshot allows, so a
        // burst of concurrent inserts may leave the map over capacity, and two
        // evictions that both see one entry too many may take it below. What
        // has to hold is that the map neither runs away nor is drained; the
        // margins below are comfortably wide rather than derived limits.
        Assertions.assertTrue(size <= 16 + threads,
                "the map must stay near its configured capacity, was " + size);
        Assertions.assertTrue(size > 0, "the map must still hold statistics, was " + size);
    }
}
