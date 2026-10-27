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
import org.apache.tomcat.jdbc.pool.DataSource;
import org.apache.tomcat.jdbc.pool.PooledConnection;

/**
 * A cached statement belongs to the physical session it was prepared on, so
 * the cache-hit path must never hand out an entry of a replaced session -
 * even though the disconnect purge normally clears the cache when the pooled
 * connection is reconnected, an entry of the replaced session can survive
 * into the map behind that purge on production routes (a statement close
 * racing the reconnect parks one after the purge ran; a failed connect keeps
 * the next {@code disconnect(false)} from firing the purge event again, so
 * an interim entry survives into the new session).
 *
 * <p>This class pins the cache-hit guard on its own, as defense-in-depth:
 * the test injects a stale entry directly into the cache map after the
 * reconnect completed - the very entry the purge had force-closed, a state
 * production never re-parks by itself - and asserts the next borrower is not
 * served that dead-session statement. The genuine racing close behind these
 * entries is pinned faithfully by {@link TestStatementCacheRacingClose}.
 * The injection manipulates the map directly, which production code never
 * does and which leaves the pool-wide entry counter behind the map by
 * design; that counter is therefore deliberately not asserted here, only
 * the per-connection cache size.</p>
 */
class TestStatementCacheStaleEntryResurrection extends JdbcPoolBaseTest {

    @Test
    @SuppressWarnings("rawtypes")
    void anInjectedStaleEntryOfAReplacedSessionIsNotHandedToTheNextBorrower() throws Exception {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setJdbcInterceptors(
                StatementCache.class.getName() + "(prepared=true,max=5)");
        ds.getPoolProperties().setMaxActive(1);
        ds.getPoolProperties().setTimeBetweenEvictionRunsMillis(60000);
        String table = uniqueTable();
        ds.createPool();
        try (Connection admin = adminConnection(); Statement st = admin.createStatement()) {
            st.execute("CREATE TABLE " + table + " (v INT)");
        }
        String insert = "INSERT INTO " + table + " VALUES (?)";

        Connection first = ds.getConnection();
        java.sql.Connection oldPhysical =
                ((PooledConnection) first.unwrap(PooledConnection.class)).getConnection();
        setSessionMarker(first, 21);
        PreparedStatement prepared = first.prepareStatement(insert);
        Statement oldDriverStatement = driverStatement(prepared);
        prepared.setInt(1, 1);
        Assertions.assertEquals(1, prepared.executeUpdate());
        prepared.close();

        Map cacheMap = cacheOf(first).getCache();
        Assertions.assertEquals(1, cacheOf(first).getCacheSizePerConnection(),
                "the statement must be waiting in the cache");
        Map.Entry poisoned = (Map.Entry) cacheMap.entrySet().iterator().next();
        Object poisonedKey = poisoned.getKey();
        Object poisonedEntry = poisoned.getValue();

        // Replace the physical session underneath the pooled connection, as
        // any reconnect path does: the purge runs and clears the map.
        ((PooledConnection) first.unwrap(PooledConnection.class)).reconnect();
        Assertions.assertTrue(oldPhysical.isClosed(),
                "the replaced session must be closed by the reconnect");
        Assertions.assertEquals(0, cacheMap.size(),
                "the disconnect purge must have cleared the cache");
        awaitSessions(1);

        // Inject the stale entry directly: an entry of the replaced session -
        // the very one the purge force-closed - sitting in the map while the
        // pooled connection now sits on a new session. Production never
        // re-parks a force-closed proxy; the hit-path guard must nonetheless
        // refuse to hand it out.
        cacheMap.put(poisonedKey, poisonedEntry);

        first.close(); // returned to the pool on the new session
        Connection second = ds.getConnection();
        try {
            Assertions.assertNull(sessionMarker(second),
                    "the next borrower must sit on the new physical session");
            Assertions.assertEquals(1, cacheOf(second).getCacheSizePerConnection(),
                    "the poisoned entry is what the next borrower takes over");

            PreparedStatement reused = second.prepareStatement(insert);
            // Pre-fix this hands out the proxy of the dead session: every
            // call on it answers "Statement closed." instead of working.
            Assertions.assertNotSame(oldDriverStatement, driverStatement(reused),
                    "the statement of the replaced session must not be served");
            Assertions.assertTrue(oldDriverStatement.isClosed(),
                    "the driver statement of the discarded entry must be closed");
            Assertions.assertEquals(0, cacheOf(second).getCacheSizePerConnection(),
                    "the discarded entry must be out of this connection's cache "
                            + "(the pool-wide counter lags the map by the "
                            + "injected re-add and is deliberately not asserted)");
            reused.setInt(1, 2);
            Assertions.assertEquals(1, reused.executeUpdate(),
                    "the next borrower must get a statement that works on its session");
            reused.close();
            Assertions.assertEquals(1, cacheOf(second).getCacheSizePerConnection(),
                    "the new session caches its own statement");
        } finally {
            second.close();
        }
        Assertions.assertEquals(2, adminRowCount(table),
                "both executes must have reached the database");
    }

    /** The statement cache as the given borrower sees it. */
    private static StatementCache cacheOf(Connection pooled) {
        return interceptorOf(pooled, StatementCache.class);
    }
}
