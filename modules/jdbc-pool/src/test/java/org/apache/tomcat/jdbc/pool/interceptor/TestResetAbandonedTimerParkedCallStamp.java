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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.Statement;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;
import org.apache.tomcat.jdbc.pool.DataSource;
import org.apache.tomcat.jdbc.pool.JdbcInterceptor;
import org.apache.tomcat.jdbc.pool.PooledConnection;
import org.apache.tomcat.jdbc.pool.ProxyConnection;

/**
 * A call that entered the chain during one borrow can finish after that
 * borrow has ended - the application closed the handle from another thread
 * while the call was still down at the driver, which is the very situation
 * the abandonment machinery is configured against - and after the same
 * pooled connection has been handed to the next borrower. The stamp the
 * interceptor takes when such a call completes used to be taken while the
 * connection was checked out again, and the checked-out flag alone cannot
 * tell the two borrows apart: the stamp moves the
 * {@link PooledConnection#getTimestamp()} of the live borrower forward by a
 * full {@code removeAbandonedTimeout} and
 * {@link PooledConnection#setTimestamp(long)} clears that borrow's suspect
 * state, so neither the deadline nor the suspect report the abandoned check
 * computed for the current borrower survives the stranger's stamp.
 *
 * <p>The pins below drive exactly the scenario of the defect, at both
 * layers: a connection-level call is parked between the timer and the tail,
 * and a query is parked inside a small physical provider beneath the timer's
 * statement handle. The handle is closed, the session is re-borrowed, and
 * the parked call is then released to complete. Its stamp may answer only
 * for its own borrow; the live borrower's timestamp and suspect state must
 * not move. The controls pin that calls made during the current borrow keep
 * the timer they are made to protect.</p>
 */
class TestResetAbandonedTimerParkedCallStamp extends JdbcPoolBaseTest {

    /* The pool under test, for the forced teardown below. */
    private DataSource pool;

    /* The first test's release latch, for the same teardown. */
    private CountDownLatch blockerRelease;

    /*
     * These tests can end with the connection still borrowed, or with a
     * parked thread still waiting on a latch (a failed assertion between the
     * park and its release). Release the latches first, then force-close:
     * the regular data source teardown only disconnects idle connections, so
     * the session of a borrowed one would linger until the garbage collector
     * finalizes it.
     */
    @AfterEach
    void releaseAndDisconnect() {
        if (blockerRelease != null) {
            blockerRelease.countDown();
        }
        if (parkedProvider != null) {
            parkedProvider.release.countDown();
        }
        if (pool != null) {
            pool.close(true);
        }
    }

    /* The provider of the second test, for the latch release above. */
    private ParkingProvider parkedProvider;

    /*
     * A link that parks one named method on the way down and forwards
     * everything else. Spliced between the timer and the tail it stands for
     * a call still in flight when the application closes the handle, and it
     * lets the call continue to the tail afterwards, so it is the pool's own
     * borrow fence - and nothing test-made - that answers the released call.
     */
    private static final class Blocker extends JdbcInterceptor {
        private final String blocked;
        private final CountDownLatch inside;
        private final CountDownLatch release;

        Blocker(String blocked, CountDownLatch inside, CountDownLatch release) {
            this.blocked = blocked;
            this.inside = inside;
            this.release = release;
            setUseEquals(true);
        }

        @Override
        public void reset(org.apache.tomcat.jdbc.pool.ConnectionPool parent, PooledConnection con) {
            // The spliced link stands for the driver's part of the call, not
            // for an interceptor: the borrow walk must not arm or rearm it.
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (blocked.equals(method.getName())) {
                inside.countDown();
                release.await();
            }
            return getNext().invoke(proxy, method, args);
        }
    }

    /*
     * A physical provider shaped like a hung database: the first executeQuery
     * after the test arms it parks on the way to the H2 statement, standing
     * for a query still with the driver when the application gives the
     * handle back. Releasing it lets the very same call run - against the
     * still-open physical session, exactly as a late reply from a dead-slow
     * database arrives on a session the pool has long since re-served.
     */
    private static final class ParkingProvider implements javax.sql.DataSource {

        private final org.h2.jdbcx.JdbcDataSource delegate = new org.h2.jdbcx.JdbcDataSource();
        private final CountDownLatch inside = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);
        private final AtomicBoolean armed = new AtomicBoolean();

        ParkingProvider(String url) {
            delegate.setUrl(url);
            delegate.setUser("sa");
            delegate.setPassword("");
        }

        @Override
        public Connection getConnection() throws java.sql.SQLException {
            return (Connection) Proxy.newProxyInstance(ParkingProvider.class.getClassLoader(),
                    new Class<?>[] { Connection.class }, new ConnectionHandler(delegate.getConnection()));
        }

        @Override
        public Connection getConnection(String username, String password) throws java.sql.SQLException {
            // The credentials are the pool's configuration, not the
            // database's; the physical provider is the fixed H2 one.
            return getConnection();
        }

        @Override
        public java.io.PrintWriter getLogWriter() {
            return null;
        }

        @Override
        public void setLogWriter(java.io.PrintWriter out) {
            // no-op
        }

        @Override
        public void setLoginTimeout(int seconds) {
            // no-op
        }

        @Override
        public int getLoginTimeout() {
            return 0;
        }

        @Override
        public Logger getParentLogger() {
            return Logger.getLogger(ParkingProvider.class.getName());
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws java.sql.SQLException {
            return delegate.unwrap(iface);
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) throws java.sql.SQLException {
            return delegate.isWrapperFor(iface);
        }

        private final class ConnectionHandler implements java.lang.reflect.InvocationHandler {
            private final Connection physical;

            ConnectionHandler(Connection physical) {
                this.physical = physical;
            }

            @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                try {
                    Object result = method.invoke(physical, args);
                    if ("createStatement".equals(method.getName())) {
                        return Proxy.newProxyInstance(ParkingProvider.class.getClassLoader(),
                                new Class<?>[] { Statement.class }, new ParkingStatement((Statement) result));
                    }
                    return result;
                } catch (InvocationTargetException e) {
                    throw e.getCause();
                }
            }
        }

        private final class ParkingStatement implements java.lang.reflect.InvocationHandler {
            private final Statement delegate;

            ParkingStatement(Statement delegate) {
                this.delegate = delegate;
            }

            @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                try {
                    if ("executeQuery".equals(method.getName()) && armed.compareAndSet(true, false)) {
                        inside.countDown();
                        release.await();
                    }
                    return method.invoke(delegate, args);
                } catch (InvocationTargetException e) {
                    throw e.getCause();
                }
            }
        }
    }

    /* A pool running nothing but the timer interceptor, one session max. */
    private DataSource timerPool() {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setJdbcInterceptors(ResetAbandonedTimer.class.getName());
        ds.getPoolProperties().setInitialSize(0);
        ds.getPoolProperties().setMinIdle(0);
        ds.getPoolProperties().setMaxActive(1);
        pool = ds;
        return ds;
    }

    /*
     * The defect at the connection layer: a parked connection-level call
     * that completes after its borrow ended must not stamp the borrow that
     * holds the session now. The call's refusal by the tail's fence is what
     * proves the scenario ran - it was refused precisely because its own
     * borrow is over - and its stamp is what used to land on the new
     * borrower anyway.
     */
    @Test
    void aCallInFlightAtBorrowEndDoesNotStampTheNextBorrower() throws Exception {
        DataSource ds = timerPool();

        Connection first = ds.getConnection();
        ResetAbandonedTimer timer = interceptorOf(first, ResetAbandonedTimer.class);
        Assertions.assertNotNull(timer, "precondition: the chain carries the timer interceptor");
        JdbcInterceptor tail = timer.getNext();
        PooledConnection pcon = interceptorOf(first, ProxyConnection.class).getConnection();

        CountDownLatch inside = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Blocker blocker = new Blocker("getAutoCommit", inside, release);
        blocker.setNext(tail);
        timer.setNext(blocker);

        AtomicReference<Throwable> parkedFailure = new AtomicReference<>();
        Thread parked = new Thread(() -> {
            try {
                first.getAutoCommit();
            } catch (Throwable t) {
                parkedFailure.set(t);
            }
        }, "parked-call");
        parked.setDaemon(true);
        parked.start();
        Assertions.assertTrue(inside.await(5, TimeUnit.SECONDS),
                "precondition: the connection-level call reached the link below the timer");

        // The parked call's borrower gives the session back, and it is handed
        // out again - the same pooled connection, under a new borrow.
        first.close();
        Connection second = ds.getConnection();
        Assertions.assertSame(pcon, interceptorOf(second, ProxyConnection.class).getConnection(),
                "precondition: the parked call's session is the one held now");

        // A reset that cannot be told apart from the stamp it replaced would
        // prove nothing either way, so separate the two reads in time.
        sleep(50);
        long before = pcon.getTimestamp();
        pcon.setSuspect(true);

        release.countDown();
        parked.join(5000);
        Assertions.assertFalse(parked.isAlive(), "precondition: the parked call completed");
        Assertions.assertNotNull(parkedFailure.get(),
                "precondition: the tail's fence refused the parked call - that refusal is what shows the call "
                        + "completed after its own borrow ended");

        Assertions.assertEquals(before, pcon.getTimestamp(),
                "the parked call's stamp landed on the borrow that holds the session now: its timestamp was "
                        + before + " and is " + pcon.getTimestamp() + " after the call completed");
        Assertions.assertTrue(pcon.isSuspect(),
                "setTimestamp clears the suspect flag, and the parked call wiped the suspect state recorded"
                        + " about the current borrow");

        // The control: a call of the current borrow still keeps the timer -
        // the borrow gate refuses stale borrows' stamps, not this one's.
        timer.setNext(tail);
        sleep(50);
        long held = pcon.getTimestamp();
        second.getAutoCommit();
        Assertions.assertTrue(pcon.getTimestamp() > held,
                "a call made during the current borrow must still reset the timer of the connection it is"
                        + " made on");

        second.close();
    }

    /*
     * The defect at the handle layer, which is where it bites in earnest:
     * the query a hung database answers after its borrower already gave the
     * handle back. The call had passed the pool's own borrow fence long
     * before, so nothing below this interceptor can tell it belongs to a
     * finished borrow; only the gate on the stamp knows.
     */
    @Test
    void aQueryInFlightAtBorrowEndDoesNotStampTheNextBorrower() throws Exception {
        ParkingProvider provider = new ParkingProvider(getUrl());
        parkedProvider = provider;
        DataSource ds = timerPool();
        ds.setDataSource(provider);

        Connection first = ds.getConnection();
        PooledConnection pcon = interceptorOf(first, ProxyConnection.class).getConnection();
        Statement statement = first.createStatement();

        provider.armed.set(true);
        AtomicReference<Throwable> parkedFailure = new AtomicReference<>();
        Thread parked = new Thread(() -> {
            try {
                statement.executeQuery("CALL 1");
            } catch (Throwable t) {
                parkedFailure.set(t);
            }
        }, "parked-query");
        parked.setDaemon(true);
        parked.start();
        Assertions.assertTrue(provider.inside.await(5, TimeUnit.SECONDS),
                "precondition: the query reached the provider below the statement handle");

        // The borrower times out and gives the handle back; the session goes
        // straight to the next borrower, physical session still open.
        first.close();
        Connection second = ds.getConnection();
        Assertions.assertSame(pcon, interceptorOf(second, ProxyConnection.class).getConnection(),
                "precondition: the parked query's session is the one held now");

        sleep(50);
        long before = pcon.getTimestamp();
        pcon.setSuspect(true);

        provider.release.countDown();
        parked.join(5000);
        Assertions.assertFalse(parked.isAlive(), "precondition: the parked query completed");
        Assertions.assertNull(parkedFailure.get(),
                "precondition: the parked query ran to its end on the still-open session - a reply arriving that"
                        + " late is the scenario this interceptor exists for, and it never passes the fence again");

        Assertions.assertEquals(before, pcon.getTimestamp(),
                "the parked query's stamp landed on the borrow that holds the session now: its timestamp was "
                        + before + " and is " + pcon.getTimestamp() + " after the query completed");
        Assertions.assertTrue(pcon.isSuspect(),
                "the parked query wiped the suspect state recorded about the current borrow");

        // The control, on the very path the execution stamp moved onto: a
        // query executed on the current borrow keeps the timer of the borrow
        // it runs on.
        sleep(50);
        long held = pcon.getTimestamp();
        try (Statement live = second.createStatement()) {
            Assertions.assertNotNull(live.executeQuery("CALL 1"));
        }
        Assertions.assertTrue(pcon.getTimestamp() > held,
                "a query executed on the current borrow must still reset the timer of the connection it runs"
                        + " on");

        second.close();
    }
}
