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
 * A cache hit that loses the removal race to a disconnect purge must not hand
 * out the entry the purge is closing, and a concurrent force-close must not
 * leave the application close path dereferencing a null delegate.
 */
class TestStatementCacheLostRemovalRace extends JdbcPoolBaseTest {

    public static class StaleHitStatementCache extends StatementCache {

        private CachedStatement forcedHit;

        void forceNextHit(CachedStatement stale) {
            forcedHit = stale;
        }

        CachedStatement parked(Method method, Object[] args) {
            return super.isCached(method, args);
        }

        @Override
        public CachedStatement isCached(Method method, Object[] args) {
            CachedStatement stale = forcedHit;
            if (stale != null) {
                forcedHit = null;
                return stale;
            }
            return super.isCached(method, args);
        }
    }

    private static final String SQL = "SELECT 1";

    private static Method prepareMethod() throws Exception {
        return Connection.class.getMethod("prepareStatement", String.class);
    }

    private DataSource newCachingDataSource() throws Exception {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setJdbcInterceptors(
                StaleHitStatementCache.class.getName() + "(prepared=true,max=5)");
        ds.getPoolProperties().setMaxActive(1);
        ds.getPoolProperties().setTimeBetweenEvictionRunsMillis(60000);
        ds.createPool();
        return ds;
    }

    @Test
    void aHitThatLostTheRemovalRaceIsNotHandedOut() throws Exception {
        DataSource ds = newCachingDataSource();
        try (Connection connection = ds.getConnection()) {
            StaleHitStatementCache cache = interceptorOf(connection, StaleHitStatementCache.class);
            Method prepare = prepareMethod();
            Object[] args = new Object[] { SQL };

            PreparedStatement first = connection.prepareStatement(SQL);
            first.close();
            StatementCache.CachedStatement stale = cache.parked(prepare, args);
            Assertions.assertNotNull(stale, "precondition: the statement must be parked");
            Statement staleDriver = driverStatement((Statement) stale.getActualProxy());

            // Simulate the purge winning the removal race: the entry is gone
            // before the hit tries to take it.
            Assertions.assertTrue(cache.removeStatement(stale),
                    "precondition: the test must own the removal");

            cache.forceNextHit(stale);
            PreparedStatement fresh = connection.prepareStatement(SQL);
            try {
                Assertions.assertNotSame(stale.getActualProxy(), fresh,
                        "a hit that did not win the removal must not hand out the entry");
                Assertions.assertFalse(staleDriver.isClosed(),
                        "the refused hit must not close an entry it did not own");
                Assertions.assertTrue(fresh.execute());
            } finally {
                fresh.close();
                stale.forceClose();
            }
        } finally {
            ds.close();
        }
    }

    @Test
    void aCloseAfterAConcurrentForceCloseDoesNotDereferenceANullDelegate() throws Exception {
        DataSource ds = newCachingDataSource();
        try (Connection connection = ds.getConnection()) {
            StaleHitStatementCache cache = interceptorOf(connection, StaleHitStatementCache.class);
            Method prepare = prepareMethod();
            Object[] args = new Object[] { SQL };

            PreparedStatement statement = connection.prepareStatement(SQL);
            statement.close();
            StatementCache.CachedStatement stale = cache.parked(prepare, args);
            Assertions.assertNotNull(stale, "precondition: the statement must be parked");

            stale.forceClose();
            Assertions.assertDoesNotThrow(stale::closeInvoked,
                    "closing a handle a concurrent force-close already retired must not throw");
        } finally {
            ds.close();
        }
    }
}
