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
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;
import org.apache.tomcat.jdbc.pool.DataSource;

/**
 * What the application is told when the driver refuses
 * {@code Statement.close()}. The statement proxy of
 * {@link StatementDecoratorInterceptor} used to catch that
 * {@link SQLException} and discard it, then mark the proxy closed and drop the
 * delegate, so {@code close()} came back as a success: the application believed
 * its cursor was retired while the driver had left the statement open on the
 * physical session, the borrow went back to the pool, and the next borrower
 * inherited what the failed close had left behind. Nothing recorded the
 * failure either - a proxy already marked closed finalises as a no-op, so the
 * {@link StatementFinalizer} backstop never looked at it again.
 *
 * <p>The pool now reports the outcome to whoever asked for the close, the way
 * {@code Statement.close()} itself declares it. The handle is retired
 * whichever way the driver answered, so a failed close leaves no handle that
 * goes on answering calls. Where no application waits on the close - the
 * statement cache discarding statements it is dropping - the failure is
 * answered with a log line instead, so that one statement that will not close
 * cannot interrupt the walk over the cache.</p>
 *
 * <p>The application's handle is the one the pool's statement facade hands out,
 * which is always there; the facade forwards {@code close} to the
 * interceptor's proxy underneath, so the failure has to travel through it. The
 * refusal is injected at the delegate the proxy holds, through an interceptor
 * subclass that decorates one statement over a driver statement whose
 * {@code close()} throws - every other call of that wrapper reaches the real
 * H2 statement, so the assertions about the physical statement stay honest.</p>
 */
class TestStatementDecoratorCloseFailure extends JdbcPoolBaseTest {

    /*
     * A driver statement that refuses close(), the way a driver refuses it when
     * the session runs into an error underneath the statement. Every other call
     * is forwarded unchanged, and the attempt counter says whether the pool
     * really asked the driver - so an assertion that nothing was thrown cannot
     * pass because the close was never attempted.
     */
    private static final class RefusingClose implements InvocationHandler {
        private static final String MESSAGE = "injected: the driver refused to close the statement";

        private final Statement delegate;
        private int closeAttempts;

        private RefusingClose(Statement delegate) {
            this.delegate = delegate;
        }

        /* The wrapper, on the statement interface the pool works with. */
        private Statement proxy() {
            Class<?> iface = CallableStatement.class.isAssignableFrom(delegate.getClass())
                    ? CallableStatement.class
                    : PreparedStatement.class.isAssignableFrom(delegate.getClass())
                            ? PreparedStatement.class
                            : Statement.class;
            return (Statement) Proxy.newProxyInstance(RefusingClose.class.getClassLoader(),
                    new Class<?>[] { iface }, this);
        }

        private int closeAttempts() {
            return closeAttempts;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if ("close".equals(method.getName())) {
                closeAttempts++;
                throw new SQLException(MESSAGE);
            }
            try {
                return args == null ? method.invoke(delegate) : method.invoke(delegate, args);
            } catch (InvocationTargetException x) {
                throw x.getCause();
            }
        }
    }

    /*
     * The decorator with one hook: while armed, the next statement it decorates
     * is decorated over a driver statement that refuses to close. The pool
     * builds interceptors reflectively from the configured class name, so this
     * must stay a public static class with a no-arg constructor.
     */
    public static class RefusingCloseStatementDecorator extends StatementDecoratorInterceptor {
        private boolean failNextClose;
        private RefusingClose lastWrapped;

        /** Arms the hook: the next created statement refuses to close. */
        void failNextClose() {
            failNextClose = true;
        }

        /** How often the driver was asked to close the armed statement. */
        int closeAttempts() {
            return lastWrapped == null ? 0 : lastWrapped.closeAttempts();
        }

        @Override
        protected Object createDecorator(Object proxy, Method method, Object[] args, Object statement,
                Constructor<?> constructor, String sql)
                throws InstantiationException, IllegalAccessException, InvocationTargetException {
            if (failNextClose) {
                failNextClose = false;
                lastWrapped = new RefusingClose((Statement) statement);
                statement = lastWrapped.proxy();
            }
            return super.createDecorator(proxy, method, args, statement, constructor, sql);
        }
    }

    /* The same hook on the statement cache, which adds the parking path. */
    public static class RefusingCloseStatementCache extends StatementCache {
        private boolean failNextClose;
        private RefusingClose lastWrapped;

        /** Arms the hook: the next prepared statement refuses to close. */
        void failNextClose() {
            failNextClose = true;
        }

        /** How often the driver was asked to close the armed statement. */
        int closeAttempts() {
            return lastWrapped == null ? 0 : lastWrapped.closeAttempts();
        }

        @Override
        protected Object createDecorator(Object proxy, Method method, Object[] args, Object statement,
                Constructor<?> constructor, String sql)
                throws InstantiationException, IllegalAccessException, InvocationTargetException {
            if (failNextClose) {
                failNextClose = false;
                lastWrapped = new RefusingClose((Statement) statement);
                statement = lastWrapped.proxy();
            }
            return super.createDecorator(proxy, method, args, statement, constructor, sql);
        }
    }

    private DataSource poolOf(String interceptor) throws SQLException {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setJdbcInterceptors(interceptor);
        ds.createPool();
        return ds;
    }

    @Test
    void aFailedDriverCloseIsReportedToTheApplicationThroughTheFacade() throws Exception {
        DataSource ds = poolOf(RefusingCloseStatementDecorator.class.getName());
        Connection pooled = ds.getConnection();
        try {
            RefusingCloseStatementDecorator decorator =
                    interceptorOf(pooled, RefusingCloseStatementDecorator.class);

            decorator.failNextClose();
            Statement failing = pooled.createStatement();
            // The interceptor's own handle, underneath the facade's, and the
            // H2 statement underneath both of them.
            Statement decoratorHandle = (Statement) proxiedObject(failing);
            Statement physical = driverStatement(failing);

            SQLException thrown = Assertions.assertThrows(SQLException.class, failing::close,
                    "a driver that fails the close must not be answered with a successful close");
            Assertions.assertEquals(RefusingClose.MESSAGE, thrown.getMessage(),
                    "the application must receive the driver's own failure, not a message of the pool's");
            Assertions.assertEquals(1, decorator.closeAttempts(),
                    "the report must come from an actual attempt at the driver");

            // The hazard the swallowed exception hid, now visible to the
            // application that was told about it: the physical statement is
            // still open on this session.
            Assertions.assertFalse(physical.isClosed(),
                    "the refused close must have left the driver statement open");

            // And the handle is retired all the same: the caller was told, and
            // what is left of the statement belongs to the driver now.
            Assertions.assertTrue(failing.isClosed(),
                    "the facade must report the failed handle as closed");
            Assertions.assertTrue(decoratorHandle.isClosed(),
                    "and so must the interceptor's own proxy underneath it");
            SQLException after = Assertions.assertThrows(SQLException.class,
                    () -> failing.executeQuery("SELECT 1"),
                    "a handle whose close failed must not go on answering calls");
            Assertions.assertEquals("Statement closed.", after.getMessage());
            Assertions.assertDoesNotThrow(failing::close,
                    "a second close is the no-op a closed handle is allowed to be");
        } finally {
            // Returning the borrow after a failed close is an ordinary return.
            pooled.close();
        }
    }

    @Test
    void aFailedCloseOfACheckedOutCachedStatementIsReportedAndTheCacheKeepsWorking() throws Exception {
        DataSource ds = poolOf(RefusingCloseStatementCache.class.getName() + "(prepared=true,max=1)");
        Connection pooled = ds.getConnection();
        try {
            RefusingCloseStatementCache cache = interceptorOf(pooled, RefusingCloseStatementCache.class);

            cache.failNextClose();
            PreparedStatement failing = pooled.prepareStatement("SELECT 1");
            Statement physical = driverStatement(failing);

            // Fill the single slot with a statement that closes properly; from
            // here on a close is no longer a park, it goes to the driver.
            PreparedStatement parked = pooled.prepareStatement("SELECT 2");
            Statement parkedPhysical = driverStatement(parked);
            parked.close();
            Assertions.assertEquals(1, cache.getCacheSizePerConnection());
            Assertions.assertEquals(1, cache.getCacheSize());

            SQLException thrown = Assertions.assertThrows(SQLException.class, failing::close,
                    "a cache that cannot park a statement must report the driver's failed close");
            Assertions.assertEquals(RefusingClose.MESSAGE, thrown.getMessage());
            Assertions.assertEquals(1, cache.closeAttempts());
            Assertions.assertFalse(physical.isClosed());
            Assertions.assertTrue(failing.isClosed(),
                    "the handle of the failed close must be closed, cached or not");

            // None of the failed close reached the cache: it was never parked,
            // and it cannot un-park the statement that sits in the only slot.
            Assertions.assertEquals(1, cache.getCacheSizePerConnection(),
                    "the parked statement must still be in the cache");
            Assertions.assertEquals(1, cache.getCacheSize());
            PreparedStatement reused = pooled.prepareStatement("SELECT 2");
            Assertions.assertSame(parkedPhysical, driverStatement(reused),
                    "and the next prepare of that SQL must still be served by it");
            reused.close();
        } finally {
            pooled.close();
        }
    }

    @Test
    void theCachePurgeReportsARefusedCloseInItsLogAndGoesOnWithTheRest() throws Exception {
        DataSource ds = poolOf(RefusingCloseStatementCache.class.getName() + "(prepared=true,max=5)");
        Connection pooled = ds.getConnection();
        try {
            RefusingCloseStatementCache cache = interceptorOf(pooled, RefusingCloseStatementCache.class);
            Method prepare = Connection.class.getMethod("prepareStatement", String.class);

            // Two statements parked in the cache. A park never asks the driver
            // to close, so the armed refusal is still waiting for the purge.
            cache.failNextClose();
            PreparedStatement refusing = pooled.prepareStatement("SELECT 1");
            refusing.close();
            PreparedStatement ordinary = pooled.prepareStatement("SELECT 2");
            Statement ordinaryPhysical = driverStatement(ordinary);
            ordinary.close();
            Assertions.assertEquals(2, cache.getCacheSizePerConnection());

            // The route the disconnect purge takes, entry by entry: nobody
            // waits on the answer, so the refusal must not come back as an
            // exception - the walk has to reach the entries after it.
            StatementCache.CachedStatement refusingEntry =
                    cache.isCached(prepare, new Object[] { "SELECT 1" });
            Assertions.assertNotNull(refusingEntry, "the statement must be waiting in the cache");
            Assertions.assertDoesNotThrow(() -> cache.closeStatement(refusingEntry),
                    "a statement the driver refuses to close must not be thrown at the purge");
            Assertions.assertEquals(1, cache.closeAttempts(),
                    "the purge must still have asked the driver to close it");
            Assertions.assertEquals(1, cache.getCacheSizePerConnection(),
                    "the refused statement must still have left the cache");
            Assertions.assertEquals(1, cache.getCacheSize());

            StatementCache.CachedStatement ordinaryEntry =
                    cache.isCached(prepare, new Object[] { "SELECT 2" });
            Assertions.assertNotNull(ordinaryEntry, "the entry after it must still be reachable");
            cache.closeStatement(ordinaryEntry);
            Assertions.assertTrue(ordinaryPhysical.isClosed(),
                    "and it must still have been closed by the purge");
            Assertions.assertEquals(0, cache.getCacheSizePerConnection());
            Assertions.assertEquals(0, cache.getCacheSize());
        } finally {
            pooled.close();
        }
    }
}
