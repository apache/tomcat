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
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;
import org.apache.tomcat.jdbc.pool.DataSource;
import org.apache.tomcat.jdbc.pool.PooledConnection;

/**
 * A statement close racing a session replacement must not leave the closed
 * statement of the old session parked in the cache of the new one. The
 * close cleans up the live delegate first and only then inserts into the
 * map; a disconnect/reconnect can land in between, so the purge of the old
 * session has already run while the map insert of the dead session's
 * statement is still pending. Without a session check on the way in, that
 * insert parks a dead-session entry and the next borrower's cache hit is
 * served its statement.
 *
 * <p>The race is emulated deterministically with production code only: the
 * cache is configured as a subclass whose {@code cacheStatement} override
 * performs the reconnect first and runs the real caching afterwards -
 * exactly the interleaving the racing close and the racing reconnect take:
 * cleanup on the live delegate done, session replaced, map insert pending.
 * Pre-fix (no session check in {@code cacheStatement}) the override let
 * that insert succeed, and the next borrower's cache hit returned the same
 * driver statement of the dead session, whose every call answers an H2
 * "The object is already closed [90007-220]"; post-fix the entry is
 * refused, the closing statement is closed normally, the cache stays empty
 * and the next borrower gets a fresh working prepare on the new session.</p>
 */
class TestStatementCacheRacingClose extends JdbcPoolBaseTest {

    /**
     * The statement cache with one hook: while armed, the next call to
     * {@code cacheStatement} first replaces the physical session of the
     * pooled connection - the window a racing disconnect/reconnect uses to
     * land between the close's cleanup and its map insert. The pool builds
     * interceptors reflectively from the configured class name, so this is
     * a public static class with a no-arg constructor.
     */
    public static class RacingCloseStatementCache extends StatementCache {

        private PooledConnection raceReconnectOnNextPark;

        /**
         * Arms the hook: the next statement parked through
         * {@code cacheStatement} meets a replacement of this pooled
         * connection's session inside that call.
         *
         * @param pcon the pooled connection whose session is to be replaced
         */
        void raceNextParkWithReconnect(PooledConnection pcon) {
            raceReconnectOnNextPark = pcon;
        }

        @Override
        public boolean cacheStatement(CachedStatement proxy) {
            PooledConnection raced = raceReconnectOnNextPark;
            if (raced != null) {
                raceReconnectOnNextPark = null;
                try {
                    raced.reconnect();
                } catch (SQLException x) {
                    throw new IllegalStateException("the racing reconnect failed", x);
                }
            }
            return super.cacheStatement(proxy);
        }
    }

    @Test
    void aStatementClosedWhileTheSessionIsReplacedIsNotParkedInTheNewSessionsCache()
            throws Exception {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setJdbcInterceptors(
                RacingCloseStatementCache.class.getName() + "(prepared=true,max=5)");
        ds.getPoolProperties().setMaxActive(1);
        // keep the background sweeper out of the way: the racing close
        // performs the reconnect itself here
        ds.getPoolProperties().setTimeBetweenEvictionRunsMillis(60000);
        String oldTable = uniqueTable();
        String racingTable = uniqueTable();
        ds.createPool();
        try (Connection admin = adminConnection(); Statement st = admin.createStatement()) {
            st.execute("CREATE TABLE " + oldTable + " (v INT)");
            st.execute("CREATE TABLE " + racingTable + " (v INT)");
        }
        String parkedSql = "INSERT INTO " + oldTable + " VALUES (?)";
        String racingSql = "INSERT INTO " + racingTable + " VALUES (?)";

        Connection first = ds.getConnection();
        PooledConnection pcon = (PooledConnection) first.unwrap(PooledConnection.class);
        RacingCloseStatementCache cache =
                interceptorOf(first, RacingCloseStatementCache.class);
        setSessionMarker(first, 31);

        // one entry parked normally, so the purge the racing close runs
        // into has real work to do
        PreparedStatement parked = first.prepareStatement(parkedSql);
        parked.setInt(1, 1);
        Assertions.assertEquals(1, parked.executeUpdate());
        parked.close();
        Assertions.assertEquals(1, cache.getCacheSizePerConnection(),
                "the statement must be waiting in the cache");

        // the statement whose close races the session replacement
        Connection oldPhysical = pcon.getConnection();
        PreparedStatement racing = first.prepareStatement(racingSql);
        Statement racingDriverStatement = driverStatement(racing);
        racing.setInt(1, 2);
        Assertions.assertEquals(1, racing.executeUpdate());

        cache.raceNextParkWithReconnect(pcon);
        // on the live delegate the close's cleanup has just run; the
        // session is replaced inside cacheStatement, before the map insert
        racing.close();

        Assertions.assertTrue(oldPhysical.isClosed(),
                "the racing reconnect must have replaced the physical session");
        awaitSessions(1);
        Assertions.assertEquals(0, cache.getCacheSizePerConnection(),
                "cacheStatement must refuse the closed statement of the "
                        + "replaced session (pre-fix it parked it here, "
                        + "after the purge had already run)");
        Assertions.assertTrue(racingDriverStatement.isClosed(),
                "the refused statement must have been closed by its close");

        first.close(); // back to the pool on the new session
        Connection second = ds.getConnection();
        try {
            Assertions.assertNull(sessionMarker(second),
                    "the next borrower must sit on the new physical session");
            PreparedStatement reused = second.prepareStatement(racingSql);
            // Pre-fix this is the cache hit on the dead-session entry: the
            // same driver statement is served again, and every call on it
            // answers the H2 error for a statement of a dead session.
            Assertions.assertNotSame(racingDriverStatement, driverStatement(reused),
                    "the statement of the replaced session must not be served");
            reused.setInt(1, 3);
            Assertions.assertEquals(1, reused.executeUpdate(),
                    "the next borrower must get a statement that works on its session");
            reused.close();
            Assertions.assertEquals(1, cache.getCacheSizePerConnection(),
                    "the new session must be able to park its own statement");
        } finally {
            second.close();
        }
        Assertions.assertEquals(1, adminRowCount(oldTable),
                "the parked statement's execute must have reached the database");
        Assertions.assertEquals(2, adminRowCount(racingTable),
                "both executes of the racing SQL must have reached the database");
    }
}
