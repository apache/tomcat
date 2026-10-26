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

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;
import org.apache.tomcat.jdbc.pool.DataSource;

/**
 * Expected functionality of the pool's own statement cache: a prepared
 * statement an application closes is kept for the next prepare of the
 * same statement on the same physical session, so the session is not
 * asked to prepare it a second time. That is the whole point of the
 * feature, and the oracle here is the driver's own statement object
 * behind the pool's proxy: two handles onto the same instance mean one
 * physical prepare, and its {@code isClosed()} says what really happened
 * to it. The interceptor's published counters
 * ({@code getCacheSizePerConnection()}, {@code getCacheSize()}) and the
 * rows that reach the database carry the rest.
 *
 * <p>The rules asserted below are the documented ones: statements are
 * cached per connection, the {@code max} count is counted over the whole
 * pool, and once that count is reached further statements are not cached
 * and are closed immediately.</p>
 */
class TestStatementCacheReuse extends JdbcPoolBaseTest {

    private DataSource cachedPool(String properties, int maxActive) {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setJdbcInterceptors(
                StatementCache.class.getName() + "(" + properties + ")");
        ds.getPoolProperties().setMaxActive(maxActive);
        return ds;
    }

    /** The statement cache as the given borrower sees it. */
    private static StatementCache cacheOf(Connection pooled) {
        return interceptorOf(pooled, StatementCache.class);
    }

    @Test
    void aClosedStatementServesTheNextBorrowerWithoutPreparingAgain() throws Exception {
        DataSource ds = cachedPool("prepared=true,max=5", 1);
        String table = uniqueTable();
        ds.createPool();
        try (Connection admin = adminConnection(); Statement st = admin.createStatement()) {
            st.execute("CREATE TABLE " + table + " (v INT)");
        }
        String insert = "INSERT INTO " + table + " VALUES (?)";

        Connection first = ds.getConnection();
        setSessionMarker(first, 17);
        PreparedStatement prepared = first.prepareStatement(insert);
        Statement physical = driverStatement(prepared);
        prepared.setInt(1, 1);
        Assertions.assertEquals(1, prepared.executeUpdate());
        prepared.close();
        Assertions.assertEquals(1, cacheOf(first).getCacheSizePerConnection(),
                "a closed statement must be waiting in the cache of its session");
        first.close();

        Connection second = ds.getConnection();
        try {
            Assertions.assertEquals(Integer.valueOf(17), sessionMarker(second),
                    "the same physical session must have been reused");
            Assertions.assertEquals(1, cacheOf(second).getCacheSizePerConnection(),
                    "the cache must survive the return to the pool");

            PreparedStatement reused = second.prepareStatement(insert);
            Assertions.assertEquals(0, cacheOf(second).getCacheSizePerConnection(),
                    "preparing the same statement again must take it out of the cache");
            Assertions.assertSame(physical, driverStatement(reused),
                    "the next borrower must get the same physical statement, not a "
                            + "second prepare on the session");

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

    @Test
    void eachSessionCachesItsOwnStatements() throws Exception {
        DataSource ds = cachedPool("prepared=true,max=5", 2);
        String table = uniqueTable();
        ds.createPool();
        try (Connection admin = adminConnection(); Statement st = admin.createStatement()) {
            st.execute("CREATE TABLE " + table + " (v INT)");
        }
        String insert = "INSERT INTO " + table + " VALUES (1)";

        Connection first = ds.getConnection();
        Connection second = ds.getConnection();
        try {
            Assertions.assertEquals(2, poolSessions(), "two borrowers must hold two sessions");

            PreparedStatement onFirst = first.prepareStatement(insert);
            Statement physicalOnFirst = driverStatement(onFirst);
            onFirst.executeUpdate();
            onFirst.close();

            PreparedStatement onSecond = second.prepareStatement(insert);
            Statement physicalOnSecond = driverStatement(onSecond);
            onSecond.executeUpdate();
            onSecond.close();

            Assertions.assertEquals(1, cacheOf(first).getCacheSizePerConnection(),
                    "the first session caches its own statement");
            Assertions.assertEquals(1, cacheOf(second).getCacheSizePerConnection(),
                    "the second session caches its own statement");
            Assertions.assertEquals(2, cacheOf(first).getCacheSize().get(),
                    "the pool-wide count must see both");
            Assertions.assertNotSame(physicalOnFirst, physicalOnSecond,
                    "a cached statement belongs to the session it was prepared on and "
                            + "must never be handed to another session");
        } finally {
            first.close();
            second.close();
        }
        Assertions.assertEquals(2, adminRowCount(table));
    }

    @Test
    void theCacheLimitIsCountedOverTheWholePool() throws Exception {
        DataSource ds = cachedPool("prepared=true,max=1", 2);
        ds.createPool();

        Connection first = ds.getConnection();
        PreparedStatement onFirst = first.prepareStatement("SELECT 1");
        Statement physicalOnFirst = driverStatement(onFirst);
        onFirst.close();
        Assertions.assertEquals(1, cacheOf(first).getCacheSizePerConnection());
        Assertions.assertEquals(1, cacheOf(first).getCacheSize().get());

        // The limit is reached, so the second session's statement cannot be
        // cached: the documentation says it is closed immediately.
        Connection second = ds.getConnection();
        PreparedStatement onSecond = second.prepareStatement("SELECT 2");
        Statement physicalOnSecond = driverStatement(onSecond);
        onSecond.close();
        Assertions.assertTrue(physicalOnSecond.isClosed(),
                "once the pool-wide count is at max, a further statement must not be "
                        + "cached but closed immediately");
        Assertions.assertEquals(0, cacheOf(second).getCacheSizePerConnection());
        Assertions.assertEquals(1, cacheOf(first).getCacheSize().get(),
                "the count must not grow past max");

        // Taking the cached statement out of the cache frees the slot for
        // the other session.
        PreparedStatement reused = first.prepareStatement("SELECT 1");
        Assertions.assertSame(physicalOnFirst, driverStatement(reused),
                "the first session's statement must still come from the cache");
        reused.close();
        Assertions.assertEquals(1, cacheOf(first).getCacheSizePerConnection());
        Assertions.assertEquals(1, cacheOf(first).getCacheSize().get(),
                "putting it back must not exceed the limit either");

        first.close();
        second.close();
    }

    @Test
    void aStatementOverTheLimitIsRepreparedOnDemand() throws Exception {
        DataSource ds = cachedPool("prepared=true,max=1", 1);
        ds.createPool();

        try (Connection pooled = ds.getConnection()) {
            StatementCache cache = cacheOf(pooled);

            PreparedStatement inCache = pooled.prepareStatement("SELECT 1");
            Statement physicalInCache = driverStatement(inCache);
            inCache.close();
            Assertions.assertEquals(1, cache.getCacheSizePerConnection());
            Assertions.assertFalse(physicalInCache.isClosed(),
                    "a cached statement must stay open - that is what saves the prepare");

            PreparedStatement overLimit = pooled.prepareStatement("SELECT 2");
            Statement physicalOverLimit = driverStatement(overLimit);
            overLimit.close();
            Assertions.assertTrue(physicalOverLimit.isClosed(),
                    "the statement that did not fit must be closed, not parked");
            Assertions.assertEquals(1, cache.getCacheSizePerConnection(),
                    "the cache must not grow past the configured limit");

            // and preparing that statement again works, on a fresh statement
            PreparedStatement again = pooled.prepareStatement("SELECT 2");
            Assertions.assertNotSame(physicalOverLimit, driverStatement(again),
                    "the closed statement cannot come back; a new one must be prepared");
            Assertions.assertTrue(again.execute(), "the re-prepared statement must work");
            again.close();
        }
    }

    @Test
    void theCacheGoesAwayWithTheSessionItWasBuiltOn() throws Exception {
        DataSource ds = cachedPool("prepared=true,max=5", 1);
        String table = uniqueTable();
        ds.createPool();
        try (Connection admin = adminConnection(); Statement st = admin.createStatement()) {
            st.execute("CREATE TABLE " + table + " (v INT)");
        }
        String insert = "INSERT INTO " + table + " VALUES (1)";

        Connection pooled = ds.getConnection();
        PreparedStatement prepared = pooled.prepareStatement(insert);
        Statement physical = driverStatement(prepared);
        prepared.executeUpdate();
        prepared.close();
        Assertions.assertEquals(1, cacheOf(pooled).getCacheSizePerConnection());
        pooled.close();

        // the physical session is retired, with everything hanging off it
        ds.purge();
        try (Connection next = ds.getConnection()) {
            Assertions.assertEquals(0, cacheOf(next).getCacheSizePerConnection(),
                    "a fresh session must start with an empty statement cache");
            try (PreparedStatement fresh = next.prepareStatement(insert)) {
                Assertions.assertNotSame(physical, driverStatement(fresh),
                        "the statement of the retired session must not be reachable");
                Assertions.assertEquals(1, fresh.executeUpdate(),
                        "the statement must simply be prepared again on the new session");
            }
        }
        Assertions.assertEquals(2, adminRowCount(table));
    }

    @Test
    void onlyTheStatementTypesTheConfigurationAsksForAreCached() throws Exception {
        DataSource ds = cachedPool("prepared=false,callable=true,max=5", 1);
        ds.createPool();

        try (Connection pooled = ds.getConnection()) {
            StatementCache cache = cacheOf(pooled);

            PreparedStatement notCached = pooled.prepareStatement("SELECT 1");
            Statement physical = driverStatement(notCached);
            notCached.close();
            Assertions.assertEquals(0, cache.getCacheSizePerConnection(),
                    "prepared statements must not be cached when prepared=false");
            try (PreparedStatement again = pooled.prepareStatement("SELECT 1")) {
                Assertions.assertNotSame(physical, driverStatement(again),
                        "so preparing again means a real prepare");
            }

            CallableStatement cached = pooled.prepareCall("{call ROUND(2.5, 0)}");
            Statement physicalCallable = driverStatement(cached);
            Assertions.assertTrue(cached.execute(), "the procedure must run");
            cached.close();
            Assertions.assertEquals(1, cache.getCacheSizePerConnection(),
                    "callable statements must be cached when callable=true");
            try (CallableStatement again = pooled.prepareCall("{call ROUND(2.5, 0)}")) {
                Assertions.assertSame(physicalCallable, driverStatement(again),
                        "and the cached one must be handed out again");
            }
        }
    }
}
