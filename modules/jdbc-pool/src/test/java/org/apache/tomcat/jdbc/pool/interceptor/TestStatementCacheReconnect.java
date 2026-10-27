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
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;
import org.apache.tomcat.jdbc.pool.ConnectionPool;
import org.apache.tomcat.jdbc.pool.DataSource;
import org.apache.tomcat.jdbc.pool.JdbcInterceptor;
import org.apache.tomcat.jdbc.pool.PooledConnection;

/**
 * The statement cache is a cache of statements of one physical session: a
 * cached statement is only valid while the session it was prepared on is
 * the session the pooled connection is using. Every path that replaces the
 * physical connection of a pooled connection - the reconnect of an expired
 * session at return time, at borrow time, or from the idle sweeper - must
 * therefore leave an empty cache behind, or the next borrower is handed a
 * statement of a dead session as a cache hit.
 *
 * <p>The purge runs through the {@code disconnected()} callback, which a
 * misbehaving interceptor can only survive because the disconnect event
 * isolates every link of the chain (pinned generally by
 * {@code TestThrowingDisconnectedInterceptor}). These tests pin the same
 * guarantee for the statement cache specifically: an interceptor declared
 * ahead of the cache that always throws from {@code disconnected()} must
 * not keep the cache from being purged when the session is replaced.</p>
 */
class TestStatementCacheReconnect extends JdbcPoolBaseTest {

    /**
     * Declared behind the cache; records the size of the statement cache as
     * it is when the disconnect walk passes this link - i.e. after the
     * cache itself has been notified. Records nothing at all when the walk
     * never reaches behind the throwing link.
     */
    public static class CacheAtDisconnectInterceptor extends JdbcInterceptor {
        static volatile Integer cacheSizeAtDisconnect;

        @Override
        public void reset(ConnectionPool parent, PooledConnection con) {
            // empty
        }

        @Override
        @SuppressWarnings("rawtypes")
        public void disconnected(ConnectionPool parent, PooledConnection con,
                boolean finalizing) {
            Object cache = con.getAttributes().get(StatementCache.STATEMENT_CACHE_ATTR);
            cacheSizeAtDisconnect =
                    Integer.valueOf(cache == null ? 0 : ((Map) cache).size());
        }
    }

    private DataSource cachePoolBehindThrower(int maxActive, long maxAgeMillis) {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setJdbcInterceptors(
                BoomInterceptor.class.getName() + ";"
                        + StatementCache.class.getName() + "(prepared=true,max=5);"
                        + CacheAtDisconnectInterceptor.class.getName());
        ds.getPoolProperties().setMaxActive(maxActive);
        ds.getPoolProperties().setMaxAge(maxAgeMillis);
        // keep the background sweeper out of the way: these tests trigger
        // the reconnect paths themselves (return, borrow, explicit
        // testAllIdle) and pin the reconnect count exactly
        ds.getPoolProperties().setTimeBetweenEvictionRunsMillis(60000);
        return ds;
    }

    /** The statement cache as the given borrower sees it. */
    private static StatementCache cacheOf(Connection pooled) {
        return interceptorOf(pooled, StatementCache.class);
    }

    /**
     * What every physical-connection replacement must leave behind: no
     * cached statement of the replaced session, a closed purge of it, and
     * a fresh prepare for the next borrower. The assertions are true
     * regardless of which path did the replacing, so the helper pins the
     * invariant, not one call site.
     */
    private void assertCacheDoesNotCrossSessions(DataSource ds, Connection second,
            java.sql.Connection oldPhysical, Statement oldStatement, String insert,
            int expectedInserts) throws Exception {
        Assertions.assertNull(sessionMarker(second),
                "the next borrower must sit on a new physical session");
        Assertions.assertEquals(0, cacheOf(second).getCacheSizePerConnection(),
                "the cache must not carry a statement across a replaced session");
        Assertions.assertTrue(oldPhysical.isClosed(),
                "the replaced physical session must be closed by the reconnect");
        Assertions.assertTrue(oldStatement.isClosed(),
                "the purge must close the statement of the replaced session");
        awaitSessions(1);
        PreparedStatement fresh = second.prepareStatement(insert);
        Assertions.assertNotSame(oldStatement, driverStatement(fresh),
                "the next borrower must get a fresh prepare, not the statement "
                        + "of the dead session");
        fresh.setInt(1, expectedInserts);
        Assertions.assertEquals(1, fresh.executeUpdate());
        fresh.close();
        Assertions.assertEquals(1, cacheOf(second).getCacheSizePerConnection(),
                "the new session must be able to cache its own statement");
    }

    @Test
    void aThrowingInterceptorAheadOfTheCacheDoesNotSurviveTheReturnTimeReconnect()
            throws Exception {
        CacheAtDisconnectInterceptor.cacheSizeAtDisconnect = null;
        DataSource ds = cachePoolBehindThrower(1, 1);
        String table = uniqueTable();
        ds.createPool();
        try (Connection admin = adminConnection(); Statement st = admin.createStatement()) {
            st.execute("CREATE TABLE " + table + " (v INT)");
        }
        String insert = "INSERT INTO " + table + " VALUES (?)";

        Connection first = ds.getConnection();
        java.sql.Connection oldPhysical =
                ((PooledConnection) first.unwrap(PooledConnection.class)).getConnection();
        setSessionMarker(first, 11);
        PreparedStatement prepared = first.prepareStatement(insert);
        Statement oldStatement = driverStatement(prepared);
        prepared.setInt(1, 1);
        Assertions.assertEquals(1, prepared.executeUpdate());
        prepared.close();
        Assertions.assertEquals(1, cacheOf(first).getCacheSizePerConnection(),
                "the statement must be waiting in the cache");

        sleep(50); // the session is now unambiguously past its max age
        first.close(); // the return reconnects it

        Assertions.assertEquals(Integer.valueOf(0),
                CacheAtDisconnectInterceptor.cacheSizeAtDisconnect,
                "the disconnect walk must carry on past the throwing link and "
                        + "reach the cache with the purge already done");
        Connection second = ds.getConnection();
        try {
            assertCacheDoesNotCrossSessions(ds, second, oldPhysical, oldStatement, insert, 2);
        } finally {
            second.close();
        }
        Assertions.assertEquals(2, adminRowCount(table),
                "both executes must have reached the database");
    }

    @Test
    void aThrowingInterceptorAheadOfTheCacheDoesNotSurviveTheBorrowTimeReconnect()
            throws Exception {
        CacheAtDisconnectInterceptor.cacheSizeAtDisconnect = null;
        long maxAge = 300;
        DataSource ds = cachePoolBehindThrower(1, maxAge);
        String table = uniqueTable();
        ds.createPool();
        try (Connection admin = adminConnection(); Statement st = admin.createStatement()) {
            st.execute("CREATE TABLE " + table + " (v INT)");
        }
        String insert = "INSERT INTO " + table + " VALUES (?)";

        Connection first = ds.getConnection();
        java.sql.Connection oldPhysical =
                ((PooledConnection) first.unwrap(PooledConnection.class)).getConnection();
        setSessionMarker(first, 12);
        PreparedStatement prepared = first.prepareStatement(insert);
        Statement oldStatement = driverStatement(prepared);
        prepared.setInt(1, 1);
        Assertions.assertEquals(1, prepared.executeUpdate());
        prepared.close();
        Assertions.assertEquals(1, cacheOf(first).getCacheSizePerConnection(),
                "the statement must be waiting in the cache");
        first.close(); // still within max age: goes back to the pool on its own session
        long reconnectionsBeforeBorrow = ds.getPool().getReconnectedCount();

        sleep(maxAge * 2); // the idle session expires

        Connection second = ds.getConnection();
        try {
            Assertions.assertEquals(reconnectionsBeforeBorrow + 1,
                    ds.getPool().getReconnectedCount(),
                    "the borrow must be what reconnected the expired session");
            Assertions.assertEquals(Integer.valueOf(0),
                    CacheAtDisconnectInterceptor.cacheSizeAtDisconnect,
                    "the disconnect walk of the borrow-time reconnect must "
                            + "reach the cache with the purge already done");
            assertCacheDoesNotCrossSessions(ds, second, oldPhysical, oldStatement, insert, 2);
        } finally {
            second.close();
        }
        Assertions.assertEquals(2, adminRowCount(table),
                "both executes must have reached the database");
    }

    @Test
    void aThrowingInterceptorAheadOfTheCacheDoesNotSurviveTheSweeperReconnect()
            throws Exception {
        CacheAtDisconnectInterceptor.cacheSizeAtDisconnect = null;
        long maxAge = 300;
        DataSource ds = cachePoolBehindThrower(1, maxAge);
        String table = uniqueTable();
        ds.createPool();
        try (Connection admin = adminConnection(); Statement st = admin.createStatement()) {
            st.execute("CREATE TABLE " + table + " (v INT)");
        }
        String insert = "INSERT INTO " + table + " VALUES (?)";

        Connection first = ds.getConnection();
        java.sql.Connection oldPhysical =
                ((PooledConnection) first.unwrap(PooledConnection.class)).getConnection();
        setSessionMarker(first, 13);
        PreparedStatement prepared = first.prepareStatement(insert);
        Statement oldStatement = driverStatement(prepared);
        prepared.setInt(1, 1);
        Assertions.assertEquals(1, prepared.executeUpdate());
        prepared.close();
        Assertions.assertEquals(1, cacheOf(first).getCacheSizePerConnection(),
                "the statement must be waiting in the cache");
        first.close(); // still within max age: goes back to the pool on its own session
        long reconnectionsBeforeSweep = ds.getPool().getReconnectedCount();

        sleep(maxAge * 2); // the idle session expires

        ds.getPool().testAllIdle(); // the idle sweeper's body reconnects it

        Assertions.assertEquals(reconnectionsBeforeSweep + 1,
                ds.getPool().getReconnectedCount(),
                "testAllIdle must have reconnected the expired session");
        Assertions.assertEquals(Integer.valueOf(0),
                CacheAtDisconnectInterceptor.cacheSizeAtDisconnect,
                "the disconnect walk of the sweeper reconnect must reach the "
                        + "cache with the purge already done");
        Connection second = ds.getConnection();
        try {
            assertCacheDoesNotCrossSessions(ds, second, oldPhysical, oldStatement, insert, 2);
        } finally {
            second.close();
        }
        Assertions.assertEquals(2, adminRowCount(table),
                "both executes must have reached the database");
    }
}
