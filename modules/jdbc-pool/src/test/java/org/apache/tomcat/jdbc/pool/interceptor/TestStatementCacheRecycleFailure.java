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

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;
import org.apache.tomcat.jdbc.pool.DataSource;

/**
 * A failure while recycling a statement must not be able to evict a
 * different statement that happens to be parked under the same key. The
 * park path of {@code CachedStatement.closeInvoked()} runs a sequence of
 * resetting calls over the delegate (isCloseOnCompletion, getResultSet,
 * clearParameters, ...) before it offers the statement to the cache, and
 * its catch handler cleans up with {@code removeStatement(proxy)} on the
 * new, never-parked proxy. That removal was keyed only by the SQL and
 * arguments - so when the map held an innocent entry with an equal key (a
 * previously closed second handle of the same SQL: while one handle is
 * checked out its key is absent from the map, so a second open handle of
 * the same SQL is reachable single-threaded), the cleanup removed that
 * foreign entry and decremented the pool-wide counter for it. The
 * innocent entry's driver statement - still open - was no longer
 * referenced by the map, so nothing closed it any more, not even the
 * disconnect purge, which walks the map: a statement leaked on a live
 * pooled session for the session's whole life. Drivers really throw in
 * that reset sequence: H2 answers "The object is already closed
 * [90007-220]" for a statement of a dead session (see
 * {@link TestStatementCacheRacingClose}), and drivers throw
 * SQLFeatureNotSupportedException from isCloseOnCompletion.
 *
 * <p>The failing recycle is injected deterministically, without threads:
 * the cache is configured as a subclass whose {@code createDecorator}
 * override hands one prepared statement a driver wrapper that refuses
 * {@code clearParameters()} - the interleaving the driver exception uses
 * on production, landing before the only cache insert. Pre-fix, closing
 * that statement evicted the innocent entry parked under the same key;
 * post-fix the removal only takes effect when the entry still holds the
 * removing proxy itself, so the innocent statement stays cached and
 * usable and the failing statement is simply closed.</p>
 */
class TestStatementCacheRecycleFailure extends JdbcPoolBaseTest {

    /**
     * The statement cache with one hook: while armed, the next prepared
     * statement is decorated with a delegate whose {@code clearParameters()}
     * throws, so the close of that statement fails inside the recycle reset,
     * before the statement is ever offered to the cache. The pool builds
     * interceptors reflectively from the configured class name, so this is
     * a public static class with a no-arg constructor.
     */
    public static class FailingRecycleStatementCache extends StatementCache {

        private boolean failNextRecycleReset;

        /**
         * Arms the hook: the next {@code prepareStatement} meets a driver
         * that refuses the recycle reset of its statement.
         */
        void failNextRecycleReset() {
            failNextRecycleReset = true;
        }

        @Override
        protected Object createDecorator(Object proxy, Method method, Object[] args,
                Object statement, Constructor<?> constructor, String sql)
                throws InstantiationException, IllegalAccessException, InvocationTargetException {
            if (failNextRecycleReset && compare(PREPARE_STATEMENT, method.getName())) {
                failNextRecycleReset = false;
                statement = throwingClearParameters((PreparedStatement) statement);
            }
            return super.createDecorator(proxy, method, args, statement, constructor, sql);
        }
    }

    /*
     * A driver wrapper that refuses clearParameters(), the failure the reset
     * sequence can meet before the statement is offered to the cache - here
     * with the H2 message a statement of a dead session answers.
     */
    private static PreparedStatement throwingClearParameters(PreparedStatement delegate) {
        return (PreparedStatement) Proxy.newProxyInstance(
                TestStatementCacheRecycleFailure.class.getClassLoader(),
                new Class<?>[] { PreparedStatement.class },
                (proxy, method, args) -> {
                    if ("clearParameters".equals(method.getName())) {
                        throw new SQLException("The object is already closed [90007-220]");
                    }
                    try {
                        return args == null ? method.invoke(delegate) : method.invoke(delegate, args);
                    } catch (InvocationTargetException x) {
                        throw x.getCause();
                    }
                });
    }

    private DataSource cachedPool() {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setJdbcInterceptors(
                FailingRecycleStatementCache.class.getName() + "(prepared=true,max=5)");
        ds.getPoolProperties().setMaxActive(1);
        return ds;
    }

    @Test
    void aFailedRecycleDoesNotEvictTheStatementParkedUnderTheSameKey() throws Exception {
        DataSource ds = cachedPool();
        ds.createPool();

        Connection pooled = ds.getConnection();
        try {
            FailingRecycleStatementCache cache =
                    interceptorOf(pooled, FailingRecycleStatementCache.class);
            String sql = "SELECT 1";

            // Two simultaneously open handles of one SQL, prepared while the
            // first handle's key is still absent from the map: two physical
            // statements with equal keys. The close of the first is armed to
            // throw inside the recycle reset.
            cache.failNextRecycleReset();
            PreparedStatement failing = pooled.prepareStatement(sql);
            Statement failingDriverStatement = driverStatement(failing);
            PreparedStatement parked = pooled.prepareStatement(sql);
            Statement innocent = driverStatement(parked);

            // Park the innocent one first, then let the other close fail.
            parked.close();
            Assertions.assertEquals(1, cache.getCacheSizePerConnection(),
                    "the innocent statement must be waiting in the cache");
            Assertions.assertEquals(1, cache.getCacheSize());

            // Throws at clearParameters, before the cache is ever reached:
            // this statement is not parked, and the catch must not be able
            // to un-park anybody else.
            failing.close();

            Assertions.assertEquals(1, cache.getCacheSizePerConnection(),
                    "the statement parked under the same key must still be in the cache "
                            + "(pre-fix the failing close evicted it by key alone)");
            Assertions.assertEquals(1, cache.getCacheSize(),
                    "the pool-wide counter must not have moved (pre-fix it was "
                            + "decremented for the entry of a foreign statement)");
            // Pre-fix this combination was the leak: the innocent driver
            // statement was open but no longer in the map, so nothing would
            // ever close it - not even the disconnect purge, which walks the
            // map - for the whole life of the pooled session.
            Assertions.assertFalse(innocent.isClosed(),
                    "the innocent statement must not have been closed by the failing close");
            Assertions.assertTrue(failingDriverStatement.isClosed(),
                    "the statement whose recycle failed must still be closed normally");

            // The innocent entry is not merely present, it is still the
            // cached statement it was: the next prepare is served from it.
            PreparedStatement reused = pooled.prepareStatement(sql);
            Assertions.assertSame(innocent, driverStatement(reused),
                    "the next prepare of the SQL must be served by the parked statement, "
                            + "not by a second physical prepare");
            Assertions.assertEquals(0, cache.getCacheSizePerConnection(),
                    "taking it out of the cache must remove its own entry");
            reused.close();
            Assertions.assertEquals(1, cache.getCacheSizePerConnection(),
                    "and closing it again must park it back under its key");
        } finally {
            pooled.close();
        }
    }

    @Test
    void aParkedStatementStillRemovesItselfWhenForceClosed() throws Exception {
        DataSource ds = cachedPool();
        ds.createPool();

        Connection pooled = ds.getConnection();
        try {
            FailingRecycleStatementCache cache =
                    interceptorOf(pooled, FailingRecycleStatementCache.class);
            String sql = "SELECT 1";

            PreparedStatement parked = pooled.prepareStatement(sql);
            Statement physical = driverStatement(parked);
            parked.close();
            Assertions.assertEquals(1, cache.getCacheSizePerConnection(),
                    "the statement must be waiting in the cache");
            Assertions.assertEquals(1, cache.getCacheSize());

            Method prepareMethod = Connection.class.getMethod("prepareStatement", String.class);
            StatementCache.CachedStatement entry =
                    cache.isCached(prepareMethod, new Object[] { sql });
            Assertions.assertNotNull(entry, "the parked statement must be reachable under its key");

            // The identity-aware removal must still match when the mapped
            // value IS the removing proxy - the route the disconnect purge
            // and the stale-hit guard take.
            cache.closeStatement(entry);

            Assertions.assertTrue(physical.isClosed(),
                    "the forced close must close the driver statement");
            Assertions.assertEquals(0, cache.getCacheSizePerConnection(),
                    "the forced close must remove the entry the proxy sits in");
            Assertions.assertEquals(0, cache.getCacheSize(),
                    "and decrement the pool-wide counter exactly once");

            PreparedStatement again = pooled.prepareStatement(sql);
            Assertions.assertNotSame(physical, driverStatement(again),
                    "the closed statement must not come back; the SQL is prepared again");
            again.close();
            Assertions.assertEquals(1, cache.getCacheSizePerConnection(),
                    "the freed slot must be reusable");
        } finally {
            pooled.close();
        }
    }
}
