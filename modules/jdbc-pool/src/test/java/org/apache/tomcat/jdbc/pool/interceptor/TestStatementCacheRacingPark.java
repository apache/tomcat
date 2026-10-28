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
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;
import org.apache.tomcat.jdbc.pool.DataSource;
import org.apache.tomcat.jdbc.pool.PooledConnection;

/**
 * Two threads closing two statements of one SQL on one borrowed pooled
 * connection at the same time must park exactly one statement and must
 * count exactly one. The old {@code cacheStatement} was a non-atomic
 * check-then-act - {@code containsKey} first, {@code put} later - so both
 * closes could pass the duplicate check while the map was still empty,
 * both increment the pool-wide counter, and the second put OVERWRITE the
 * entry the first one had parked. The result was permanent: the
 * overwritten driver statement stayed open, unreachable - the disconnect
 * purge walks the map and never sees it - and the pool-wide counter stayed
 * one above the real parked count, shrinking the documented {@code max}
 * budget of the whole pool forever (the invariant
 * {@code TestStatementCacheReuse.theCacheLimitIsCountedOverTheWholePool}
 * pins).
 *
 * <p>How the interleaving is forced, deterministically and without sleeps:
 * the cache reads its map freshly from the pooled connection's attributes
 * on every call ({@code getCache()} → {@code attributes.get(...)}), so the
 * test swaps that attribute for {@link ParkRendezvousMap}, a cache map
 * that gates the park on two-party barriers. Gate one makes the two
 * closing threads stand together inside the duplicate check
 * ({@code containsKey} pre-fix, {@code putIfAbsent} post-fix); gate two,
 * at the old code's plain {@code put}, then holds every insert until both
 * threads have completed their check - so pre-fix both are guaranteed to
 * check against an empty map and both are then released to insert, one
 * necessarily overwriting the other: two increments, one entry, one
 * orphaned open statement, on every run whatever order the releases take.
 * Post-fix the threads only ever meet at gate one, where the map itself
 * arbitrates the atomic insert - whichever order the two delegations take,
 * exactly one close finds the key free.</p>
 */
class TestStatementCacheRacingPark extends JdbcPoolBaseTest {

    /**
     * The cache map, swapped into the pooled connection under
     * {@link StatementCache#STATEMENT_CACHE_ATTR}, at whose park points
     * the two closing threads rendezvous. Only the next two calls of the
     * duplicate-check operations ({@code containsKey} - the pre-fix check,
     * and {@code putIfAbsent} - the fixed atomic insert) and the next two
     * calls of {@code put} (the pre-fix insert) are intercepted; everything
     * else the cache does with the map (get, remove, size, clear) is the
     * plain ConcurrentHashMap underneath.
     */
    static final class ParkRendezvousMap extends ConcurrentHashMap<Object,Object> {

        private static final long serialVersionUID = 1L;

        private final CyclicBarrier bothAtTheCheck = new CyclicBarrier(2);
        private final AtomicInteger checksToMeet = new AtomicInteger(2);
        private final CyclicBarrier bothAtTheInsert = new CyclicBarrier(2);
        private final AtomicInteger insertsToMeet = new AtomicInteger(2);

        private void meet(CyclicBarrier barrier, AtomicInteger toMeet) {
            if (toMeet.getAndDecrement() <= 0) {
                return;
            }
            try {
                barrier.await(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            } catch (InterruptedException x) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted at the park rendezvous", x);
            } catch (BrokenBarrierException | TimeoutException x) {
                throw new IllegalStateException(
                        "the second racing close never reached the park point", x);
            }
        }

        @Override
        public boolean containsKey(Object key) {
            // the duplicate check of the pre-fix check-then-act
            meet(bothAtTheCheck, checksToMeet);
            boolean present = super.containsKey(key);
            // gate 2: no insert may land before BOTH threads have run the
            // check above - that is what forces the pre-fix race instead of
            // hoping for it (the fixed code never reaches this method)
            meet(bothAtTheInsert, insertsToMeet);
            return present;
        }

        @Override
        public Object putIfAbsent(Object key, Object value) {
            // the atomic insert of the fixed code, doubling as check
            meet(bothAtTheCheck, checksToMeet);
            return super.putIfAbsent(key, value);
        }

        @Override
        public Object put(Object key, Object value) {
            // the insert of the pre-fix check-then-act: held until both
            // closes have passed their containsKey (gate 1 released them
            // together and this gate 2 is reached only after that)
            meet(bothAtTheInsert, insertsToMeet);
            return super.put(key, value);
        }
    }

    @Test
    void twoConcurrentParksOfOneKeyParkOneStatementAndCountOne() throws Exception {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setJdbcInterceptors(
                StatementCache.class.getName() + "(prepared=true,max=5)");
        ds.getPoolProperties().setMaxActive(1);
        // keep the background sweeper out of the way during the race
        ds.getPoolProperties().setTimeBetweenEvictionRunsMillis(60000);
        String table = uniqueTable();
        ds.createPool();
        try (Connection admin = adminConnection(); Statement st = admin.createStatement()) {
            st.execute("CREATE TABLE " + table + " (v INT)");
        }
        String sql = "INSERT INTO " + table + " VALUES (?)";

        Connection shared = ds.getConnection();
        try {
            StatementCache cache = interceptorOf(shared, StatementCache.class);
            // getCache() re-reads the map from the attributes on every
            // call, so this swap is what the next closes will meet
            PooledConnection pcon = (PooledConnection) shared.unwrap(PooledConnection.class);
            ParkRendezvousMap parkPoint = new ParkRendezvousMap();
            pcon.getAttributes().put(StatementCache.STATEMENT_CACHE_ATTR, parkPoint);

            // Two simultaneously open handles of one SQL: two physical
            // statements with equal keys, as any double-open of the same
            // SQL on one session produces.
            PreparedStatement closingA = shared.prepareStatement(sql);
            Statement driverA = driverStatement(closingA);
            PreparedStatement closingB = shared.prepareStatement(sql);
            Statement driverB = driverStatement(closingB);
            Assertions.assertNotSame(driverA, driverB,
                    "the second handle must be a real second prepare");
            Assertions.assertEquals(0, cache.getCacheSize(),
                    "nothing parked before the race");

            // Both closes rendezvous at the park point inside
            // cacheStatement and only then check/insert.
            final Throwable[] failed = new Throwable[2];
            Thread threadA = new Thread(() -> {
                try {
                    closingA.close();
                } catch (Throwable t) {
                    failed[0] = t;
                }
            }, "racing-park-A");
            Thread threadB = new Thread(() -> {
                try {
                    closingB.close();
                } catch (Throwable t) {
                    failed[1] = t;
                }
            }, "racing-park-B");
            threadA.start();
            threadB.start();
            threadA.join(AWAIT_TIMEOUT_MS);
            threadB.join(AWAIT_TIMEOUT_MS);
            Assertions.assertFalse(threadA.isAlive() || threadB.isAlive(),
                    "the racing closes must have finished");
            Assertions.assertNull(failed[0], "close A must not fail: " + failed[0]);
            Assertions.assertNull(failed[1], "close B must not fail: " + failed[1]);

            // Take every observation up front so the message of whichever
            // assertion trips - as the pre-fix code trips below - reports
            // the whole corrupted state at once.
            int mapSize = parkPoint.size();
            int perConnection = cache.getCacheSizePerConnection();
            int poolCount = cache.getCacheSize();
            boolean aClosed = driverA.isClosed();
            boolean bClosed = driverB.isClosed();
            String observed = "observed: map entries=" + mapSize
                    + ", pool-wide count=" + poolCount
                    + ", per-connection count=" + perConnection
                    + ", close A left it " + (aClosed ? "closed" : "OPEN")
                    + ", close B left it " + (bClosed ? "closed" : "OPEN");

            Assertions.assertEquals(1, mapSize,
                    "exactly one statement may sit in the cache after two "
                            + "racing parks of one key; " + observed);
            Assertions.assertEquals(1, perConnection,
                    "the parked count of this connection must be one; " + observed);
            // Pre-fix the loser's park had overwritten the winner's entry:
            // the map held one statement while the pool-wide counter had
            // been incremented twice and never came back down.
            Assertions.assertEquals(1, poolCount,
                    "the pool-wide counter must count exactly what is parked "
                            + "(pre-fix: an overwritten park was counted too, "
                            + "permanently shrinking the max budget); " + observed);

            // The refused statement was never in the map and its close
            // must have closed it. Pre-fix both closes believed they had
            // parked, neither closed its delegate, and the overwritten one
            // stayed open with nothing left that could ever reach it.
            Assertions.assertNotEquals(aClosed, bClosed,
                    "exactly one of the two racing statements may stay open: "
                            + "the winner of the park, not the loser (pre-fix "
                            + "both stayed open and the loser leaked); " + observed);
            Statement winner = aClosed ? driverB : driverA;
            Statement loser = aClosed ? driverA : driverB;
            Assertions.assertTrue(loser.isClosed(),
                    "the close that lost the race must have closed its statement");
            Assertions.assertFalse(winner.isClosed(),
                    "the parked statement must stay open - that is what saves the prepare");
            StatementCache.CachedStatement parked =
                    (StatementCache.CachedStatement) parkPoint.values().iterator().next();
            Assertions.assertSame(winner, parked.getDelegate(),
                    "the map must hold the very statement the winning close parked");

            // The parked winner behaves like any cached statement: the next
            // prepare is served by it, and closing it again parks it back
            // without disturbing the accounting.
            PreparedStatement reused = shared.prepareStatement(sql);
            Assertions.assertSame(winner, driverStatement(reused),
                    "the next prepare of the SQL must be served by the parked statement");
            reused.close();
            Assertions.assertEquals(1, cache.getCacheSizePerConnection());
            Assertions.assertEquals(1, cache.getCacheSize(),
                    "the counter must still equal the parked count");
        } finally {
            shared.close();
        }
    }
}
