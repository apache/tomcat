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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;
import org.apache.tomcat.jdbc.pool.DataSource;
import org.apache.tomcat.jdbc.pool.PooledConnection;

/**
 * The documented order independence of the statement cache: a wrapping
 * interceptor listed <em>after</em> the cache sits beneath it in the chain,
 * and the cache then prepares its entry on that interceptor's proxy rather
 * than on the driver's statement. The proxy answers {@code getConnection()}
 * with the pooled handle of the borrow instead of forwarding the call to the
 * driver, so if the cache took that answer as the statement's physical
 * connection it would never match the session and every park would be
 * refused - the cache silently degrades to closing every closed statement,
 * exactly what {@code StatementCache}'s class documentation says does not
 * happen ("wrapping-only interceptors are unaffected"). These tests pin both
 * orderings: the cache must park and reuse through a wrapper beneath it, a
 * wrapper above it must keep working as before, and the protection against
 * parking a statement of a replaced session (which the driver's own answer
 * carries) must stay intact on both routes.
 */
class TestStatementCacheInterceptorOrder extends JdbcPoolBaseTest {

    private static final String CACHE_CONFIG =
            StatementCache.class.getName() + "(prepared=true,max=5)";
    private static final String WRAPPER_CONFIG =
            StatementDecoratorInterceptor.class.getName();
    private static final String WRAPPER_BEFORE_CACHE =
            WRAPPER_CONFIG + ";" + CACHE_CONFIG;

    /** The statement cache as the given borrower sees it. */
    private static StatementCache cacheOf(Connection pooled) {
        return interceptorOf(pooled, StatementCache.class);
    }

    /**
     * The defect: with the wrapper configured beneath the cache, closing one
     * prepared statement must still park it - the same park, the same reuse
     * and the same driver statement as with the cache alone (compare
     * {@link TestStatementCacheReuse}). Pre-fix the park was refused because
     * the connection recorded for the statement was the decorator's pooled
     * handle, never the physical session, so the cache size stayed at
     * {@code 0}, the driver statement came back closed, and the next prepare
     * of the same SQL was a real second prepare.
     */
    @Test
    void aWrappingInterceptorListedAfterTheCacheDoesNotStopTheCaching() throws Exception {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setJdbcInterceptors(CACHE_CONFIG + ";" + WRAPPER_CONFIG);
        ds.getPoolProperties().setMaxActive(1);
        String table = uniqueTable();
        ds.createPool();
        try (Connection admin = adminConnection(); Statement st = admin.createStatement()) {
            st.execute("CREATE TABLE " + table + " (v INT)");
        }
        String insert = "INSERT INTO " + table + " VALUES (?)";

        Connection first = ds.getConnection();
        // The configuration order must really put the wrapper beneath the
        // cache, otherwise this test pins nothing about the ordering.
        StatementCache cache = cacheOf(first);
        Assertions.assertTrue(cache.getNext() instanceof StatementDecoratorInterceptor,
                "an interceptor listed after the cache must sit beneath it in the chain");
        setSessionMarker(first, 41);
        PreparedStatement prepared = first.prepareStatement(insert);
        Statement physical = driverStatement(prepared);
        prepared.setInt(1, 1);
        Assertions.assertEquals(1, prepared.executeUpdate());
        prepared.close();
        Assertions.assertEquals(1, cache.getCacheSizePerConnection(),
                "a wrapper beneath the cache must not stop the statement from "
                        + "being parked (pre-fix the park was refused and the "
                        + "cache stayed empty)");
        Assertions.assertFalse(physical.isClosed(),
                "the parked statement must stay open - that is what saves the prepare");
        first.close();

        Connection second = ds.getConnection();
        try {
            Assertions.assertEquals(Integer.valueOf(41), sessionMarker(second),
                    "the same physical session must have been reused");
            Assertions.assertEquals(1, cacheOf(second).getCacheSizePerConnection(),
                    "the cache must survive the return to the pool");

            PreparedStatement reused = second.prepareStatement(insert);
            Assertions.assertSame(physical, driverStatement(reused),
                    "the next borrower must get the same physical statement, not a "
                            + "second prepare on the session");
            Assertions.assertSame(second, reused.getConnection(),
                    "the wrapper's own handle must not leak out as the statement's "
                            + "connection on reuse");
            reused.setInt(1, 2);
            Assertions.assertEquals(1, reused.executeUpdate());
            reused.close();
            Assertions.assertEquals(1, cacheOf(second).getCacheSizePerConnection(),
                    "the statement must go back into the cache when closed again");
        } finally {
            second.close();
        }
        Assertions.assertEquals(2, adminRowCount(table),
                "both executes must have reached the database");
    }

    /**
     * The reverse order, which worked before the defect was addressed: the
     * wrapper above the cache sees every prepare (its proxy wraps even a
     * statement served from the cache) and the cache prepares directly on
     * the driver's statement, whose connection answer is the physical
     * session. This must keep park-and-reusing, so a fix aimed at the other
     * ordering cannot quietly change this one.
     */
    @Test
    void aWrappingInterceptorListedBeforeTheCacheKeepsWorkingAsBefore() throws Exception {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setJdbcInterceptors(WRAPPER_BEFORE_CACHE);
        ds.getPoolProperties().setMaxActive(1);
        String table = uniqueTable();
        ds.createPool();
        try (Connection admin = adminConnection(); Statement st = admin.createStatement()) {
            st.execute("CREATE TABLE " + table + " (v INT)");
        }
        String insert = "INSERT INTO " + table + " VALUES (?)";

        Connection first = ds.getConnection();
        StatementCache cache = cacheOf(first);
        Assertions.assertFalse(cache.getNext() instanceof StatementDecoratorInterceptor,
                "an interceptor listed before the cache must not sit beneath it");
        setSessionMarker(first, 42);
        PreparedStatement prepared = first.prepareStatement(insert);
        Statement physical = driverStatement(prepared);
        prepared.setInt(1, 1);
        Assertions.assertEquals(1, prepared.executeUpdate());
        prepared.close();
        Assertions.assertEquals(1, cache.getCacheSizePerConnection(),
                "the wrapper above the cache must not keep the statement out of the cache");
        Assertions.assertFalse(physical.isClosed(),
                "the parked statement must stay open");
        first.close();

        Connection second = ds.getConnection();
        try {
            Assertions.assertEquals(Integer.valueOf(42), sessionMarker(second),
                    "the same physical session must have been reused");
            PreparedStatement reused = second.prepareStatement(insert);
            Assertions.assertSame(physical, driverStatement(reused),
                    "the next borrower must get the same physical statement");
            reused.setInt(1, 2);
            Assertions.assertEquals(1, reused.executeUpdate());
            reused.close();
            Assertions.assertEquals(1, cacheOf(second).getCacheSizePerConnection(),
                    "the statement must go back into the cache when closed again");
        } finally {
            second.close();
        }
        Assertions.assertEquals(2, adminRowCount(table),
                "both executes must have reached the database");
    }

    /**
     * The protection the driver's answer carries must survive the fix on the
     * route where the answer is intercepted: a statement close racing a
     * session replacement must still not be parked in the new session's
     * cache. Same deterministic race as
     * {@link TestStatementCacheRacingClose} (the reconnect runs inside
     * {@code cacheStatement}), but with the wrapping interceptor beneath the
     * cache, so the statement's own {@code getConnection()} can never answer
     * the old session - a fix that resolved the intercepted answer from the
     * session current at park time would park the dead statement here.
     */
    @Test
    void aStatementClosedWhileTheSessionIsReplacedIsStillNotParkedWithAWrapperBelow()
            throws Exception {
        DataSource ds = newDataSource();
        String racingCache =
                TestStatementCacheRacingClose.RacingCloseStatementCache.class.getName();
        ds.getPoolProperties().setJdbcInterceptors(
                racingCache + "(prepared=true,max=5);" + WRAPPER_CONFIG);
        ds.getPoolProperties().setMaxActive(1);
        // keep the background sweeper out of the way: the racing close
        // performs the reconnect itself here
        ds.getPoolProperties().setTimeBetweenEvictionRunsMillis(60000);
        String racingTable = uniqueTable();
        ds.createPool();
        try (Connection admin = adminConnection(); Statement st = admin.createStatement()) {
            st.execute("CREATE TABLE " + racingTable + " (v INT)");
        }
        String racingSql = "INSERT INTO " + racingTable + " VALUES (?)";

        Connection first = ds.getConnection();
        PooledConnection pcon = (PooledConnection) first.unwrap(PooledConnection.class);
        TestStatementCacheRacingClose.RacingCloseStatementCache cache =
                interceptorOf(first, TestStatementCacheRacingClose.RacingCloseStatementCache.class);
        setSessionMarker(first, 43);

        Connection oldPhysical = pcon.getConnection();
        PreparedStatement racing = first.prepareStatement(racingSql);
        Statement racingDriverStatement = driverStatement(racing);
        racing.setInt(1, 2);
        Assertions.assertEquals(1, racing.executeUpdate());

        cache.raceNextParkWithReconnect(pcon);
        racing.close();

        Assertions.assertTrue(oldPhysical.isClosed(),
                "the racing reconnect must have replaced the physical session");
        awaitSessions(1);
        Assertions.assertEquals(0, cache.getCacheSizePerConnection(),
                "the statement of the replaced session must not be parked through "
                        + "the intercepted connection answer");
        Assertions.assertTrue(racingDriverStatement.isClosed(),
                "the refused statement must have been closed by its close");

        first.close(); // back to the pool on the new session
        Connection second = ds.getConnection();
        try {
            Assertions.assertNull(sessionMarker(second),
                    "the next borrower must sit on the new physical session");
            PreparedStatement fresh = second.prepareStatement(racingSql);
            Assertions.assertNotSame(racingDriverStatement, driverStatement(fresh),
                    "the statement of the replaced session must not be served");
            fresh.setInt(1, 3);
            Assertions.assertEquals(1, fresh.executeUpdate(),
                    "the next borrower must get a statement that works on its session");
            fresh.close();
            Assertions.assertEquals(1, cache.getCacheSizePerConnection(),
                    "the new session must be able to park its own statement");
        } finally {
            second.close();
        }
        Assertions.assertEquals(2, adminRowCount(racingTable),
                "both executes of the racing SQL must have reached the database");
    }
}
