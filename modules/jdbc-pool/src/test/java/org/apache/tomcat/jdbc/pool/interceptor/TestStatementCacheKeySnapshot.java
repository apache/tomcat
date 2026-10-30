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

import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;
import org.apache.tomcat.jdbc.pool.DataSource;

/**
 * The cache key of a parked statement must be an immutable snapshot of the
 * prepare call it was made for. The {@code prepareStatement(String, int[])}
 * and {@code prepareStatement(String, String[])} overloads that reach the
 * interceptor carry the application's own array, and the key that parks the
 * statement is built from exactly that live data: if it kept the arrays by
 * reference, an application that reuses and mutates such an array after the
 * prepare would move the parked entry's hash bucket under the map - the key
 * re-computes {@code Arrays.deepHashCode} over the live array on every call
 * - stranding the entry: unreachable to lookups, its driver statement still
 * open, its slot still counted against the pool-wide limit, until the
 * physical session itself is discarded. The oracle is again the driver's own
 * statement object: a mutated caller array must neither hide the parked
 * statement from the next prepare nor let the drift consume more cache slots.
 */
class TestStatementCacheKeySnapshot extends JdbcPoolBaseTest {

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

    /**
     * The column-index array of {@code prepareStatement(String, int[])} is
     * the caller's own. After the statement was closed into the cache, the
     * application mutates that array before preparing the same statement
     * again: the lookup is made with a fresh array holding the original
     * content, and must still find the statement that is parked - the same
     * physical statement, with no second prepare on the session.
     */
    @Test
    void aMutatedColumnIndexArrayMustNotHideTheParkedStatement() throws Exception {
        DataSource ds = cachedPool("prepared=true,max=5", 1);
        ds.createPool();

        try (Connection pooled = ds.getConnection()) {
            StatementCache cache = cacheOf(pooled);

            int[] columnIndexes = { 1 };
            PreparedStatement prepared = pooled.prepareStatement("SELECT 1", columnIndexes);
            Statement physical = driverStatement(prepared);
            prepared.close();
            Assertions.assertEquals(1, cache.getCacheSizePerConnection(),
                    "the closed statement must be waiting in the cache");

            // The application reuses its own array and changes it
            columnIndexes[0] = 2;

            PreparedStatement reused = pooled.prepareStatement("SELECT 1", new int[] { 1 });
            Assertions.assertSame(physical, driverStatement(reused),
                    "mutating the caller's array after the close must not hide the parked "
                            + "statement from the next prepare of the same SQL");
            Assertions.assertEquals(0, cache.getCacheSizePerConnection(),
                    "the statement must have come out of the cache");

            reused.close();
            Assertions.assertEquals(1, cache.getCacheSizePerConnection(),
                    "the statement must go back into the cache, alone");
            Assertions.assertEquals(1, cache.getCacheSize(),
                    "the pool-wide count must still see exactly one statement");
        }
    }

    /**
     * The same with the column-name array of
     * {@code prepareStatement(String, String[])}: the array the caller hands
     * in is stored in the key only as a snapshot, so a later mutation of it
     * must neither turn the cache lookup into a miss nor leave a second
     * physical statement behind.
     */
    @Test
    void aMutatedColumnNameArrayMustNotHideTheParkedStatement() throws Exception {
        DataSource ds = cachedPool("prepared=true,max=5", 1);
        ds.createPool();

        try (Connection pooled = ds.getConnection()) {
            StatementCache cache = cacheOf(pooled);

            String[] columnNames = { "ID" };
            PreparedStatement prepared = pooled.prepareStatement("SELECT 1", columnNames);
            Statement physical = driverStatement(prepared);
            prepared.close();
            Assertions.assertEquals(1, cache.getCacheSizePerConnection());

            // The application reuses its own array and changes it
            columnNames[0] = "NAME";

            PreparedStatement reused = pooled.prepareStatement("SELECT 1", new String[] { "ID" });
            Assertions.assertSame(physical, driverStatement(reused),
                    "mutating the caller's name array after the close must not hide the "
                            + "parked statement from the next prepare of the same SQL");
            reused.close();
            Assertions.assertEquals(1, cache.getCacheSizePerConnection(),
                    "the statement must go back into the cache, alone");
            Assertions.assertEquals(1, cache.getCacheSize());
        }
    }

    /**
     * The stranding is a slow leak, so the pin is the repeated cycle rather
     * than one round of it: an application that keeps flipping one reused
     * argument array between prepares must keep being served the same
     * physical statement, and neither the per-session cache nor the pool-wide
     * count may grow - each drifted copy of the key would otherwise park
     * another unreachable entry holding another open statement until max is
     * reached, and keep those slots lost for the life of the session.
     */
    @Test
    void aReusedMutatedArgumentArrayMustNotConsumeMoreThanOneSlot() throws Exception {
        DataSource ds = cachedPool("prepared=true,max=5", 1);
        ds.createPool();

        try (Connection pooled = ds.getConnection()) {
            StatementCache cache = cacheOf(pooled);

            int[] columnIndexes = { 5 };
            PreparedStatement prepared = pooled.prepareStatement("SELECT 1", columnIndexes);
            Statement physical = driverStatement(prepared);
            prepared.close();
            Assertions.assertEquals(1, cache.getCacheSizePerConnection());

            columnIndexes[0] = 6;
            for (int cycle = 1; cycle <= 3; cycle++) {
                PreparedStatement reused = pooled.prepareStatement("SELECT 1", new int[] { 5 });
                Assertions.assertSame(physical, driverStatement(reused),
                        "cycle " + cycle + ": every prepare must still be served the one "
                                + "physical statement, not a fresh prepare");
                reused.close();
                Assertions.assertEquals(1, cache.getCacheSizePerConnection(),
                        "cycle " + cycle + ": the drifted keys must not pile entries up on "
                                + "the session");
                Assertions.assertEquals(1, cache.getCacheSize(),
                        "cycle " + cycle + ": and must not eat more than one slot of the "
                                + "pool-wide count");
            }
        }
    }

    /**
     * The same contract at the level the key itself is built: a key created
     * through {@code createCacheKey} must keep answering to the arguments it
     * was created with no matter what the caller does to the array - the
     * outer argument array itself as well as the array elements inside it -
     * afterwards, and a null element must survive the snapshot untouched.
     */
    @Test
    void aCacheKeyMustOutliveAnyMutationOfTheArrayItWasBuiltFrom() throws Exception {
        StatementCache cache = new StatementCache();
        Method method = Connection.class.getMethod("prepareStatement", String.class, int[].class);

        Object[] args = new Object[] { "SELECT 1", new int[] { 1 }, null };
        Object key = cache.createCacheKey(method, args);
        Assertions.assertNotNull(key);

        // The caller reuses the live array wholesale: a replaced SQL slot,
        // mutated content in the nested array, and the null slot filled in
        args[0] = "SELECT 2";
        ((int[]) args[1])[0] = 9;
        args[2] = "was null";

        Object equal = cache.createCacheKey(method,
                new Object[] { "SELECT 1", new int[] { 1 }, null });
        Assertions.assertEquals(equal, key,
                "the key must still answer to the arguments it was created from");
        Assertions.assertEquals(key.hashCode(), equal.hashCode(),
                "and with a hash the map can actually find it under");

        // Callable keys are built the same way, and arrays handed to them
        // are snapshotted as well
        Object[] callArgs = new Object[] { "{call X()}", new String[] { "A" } };
        Object callKey = cache.createCacheKey("prepareCall", callArgs);
        callArgs[1] = new String[] { "B" };
        Object equalCall = cache.createCacheKey("prepareCall",
                new Object[] { "{call X()}", new String[] { "A" } });
        Assertions.assertEquals(equalCall, callKey);
        Assertions.assertEquals(callKey.hashCode(), equalCall.hashCode());
    }
}
