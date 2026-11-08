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
package org.apache.tomcat.jdbc.pool;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.CountDownLatch;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;

/**
 * Closing a statement or result set handle from another thread, the way an
 * application interrupts a call it is blocking on: the close is forwarded and
 * nulls the delegate, and a call answered in the middle of that can pass its
 * open check on a still-live read of the field and then re-read it as null at
 * the forward. Answering such a call by invoking on the missing delegate
 * escapes to the application as a NullPointerException on a null receiver;
 * the closed contract owes it the SQLException instead. The racing tests are
 * the ones that pin that, and fail while the delegate is read again without a
 * snapshot; the tests before them guard the contract a completed cross-thread
 * close must leave behind, first of the proxies read straight through the
 * facade, then of the very handles a real borrow hands out.
 */
class TestStatementFacadeCrossThreadClose extends JdbcPoolBaseTest {

    private static final long JOIN_TIMEOUT_MS = 30000;

    private static final Method EXECUTE;
    private static final Method CLOSE;
    private static final Method IS_CLOSED;
    private static final Method NEXT;
    private static final Method RESULT_SET_CLOSE;
    private static final Method RESULT_SET_IS_CLOSED;

    static {
        try {
            EXECUTE = Statement.class.getMethod("execute", String.class);
            CLOSE = Statement.class.getMethod("close");
            IS_CLOSED = Statement.class.getMethod("isClosed");
            NEXT = ResultSet.class.getMethod("next");
            RESULT_SET_CLOSE = ResultSet.class.getMethod("close");
            RESULT_SET_IS_CLOSED = ResultSet.class.getMethod("isClosed");
        } catch (NoSuchMethodException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    /* A facade over a chain end that answers nothing: the state of the
     * handles under a closing thread is the subject, and nothing below them
     * is meant to run - reaching it would surface as this failure. */
    private static final class EmptyChain extends JdbcInterceptor {
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            throw new UnsupportedOperationException(method.getName());
        }

        @Override
        public void reset(ConnectionPool parent, PooledConnection con) {
            // the chain never runs in these tests
        }
    }

    /* Statement oracle: executing answers TRUE while it is open and fails
     * with a SQLException once the closer thread has closed it, so every
     * failure the borrower can see is one the proxy itself produced rather
     * than one the oracle conjures. */
    private static final class FakeStatement implements InvocationHandler {
        private volatile boolean closed;

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String name = method.getName();
            if ("execute".equals(name)) {
                if (closed) {
                    throw new SQLException("statement closed by the closer thread");
                }
                return Boolean.TRUE;
            }
            if ("close".equals(name)) {
                closed = true;
                return null;
            }
            if ("isClosed".equals(name)) {
                return Boolean.valueOf(closed);
            }
            if ("toString".equals(name)) {
                return "FakeStatement";
            }
            throw new UnsupportedOperationException(name);
        }
    }

    /* Result set oracle, the same contract one layer down. */
    private static final class FakeResultSet implements InvocationHandler {
        private volatile boolean closed;

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String name = method.getName();
            if ("next".equals(name)) {
                if (closed) {
                    throw new SQLException("result set closed by the closer thread");
                }
                return Boolean.TRUE;
            }
            if ("close".equals(name)) {
                closed = true;
                return null;
            }
            if ("isClosed".equals(name)) {
                return Boolean.valueOf(closed);
            }
            if ("toString".equals(name)) {
                return "FakeResultSet";
            }
            throw new UnsupportedOperationException(name);
        }
    }

    private static StatementFacade facade() {
        return new StatementFacade(new EmptyChain());
    }

    private static Statement openStatement(FakeStatement oracle) {
        return (Statement) Proxy.newProxyInstance(
                TestStatementFacadeCrossThreadClose.class.getClassLoader(),
                new Class<?>[] { Statement.class }, oracle);
    }

    private static ResultSet openResultSet(FakeResultSet oracle) {
        return (ResultSet) Proxy.newProxyInstance(
                TestStatementFacadeCrossThreadClose.class.getClassLoader(),
                new Class<?>[] { ResultSet.class }, oracle);
    }

    private static void awaitGo(CountDownLatch go) {
        try {
            go.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /*
     * Once the other thread's close() has completed, every answer on this
     * thread must report the closed statement: the refusal carries the
     * closed contract, the handle says closed, a repeated close stays a
     * no-op, and the delegate underneath really was closed.
     */
    @Test
    void aCloseCompletedByAnotherThreadIsAnsweredAsClosedAfterwards() throws Throwable {
        StatementFacade facade = facade();
        FakeStatement oracle = new FakeStatement();
        StatementFacade.StatementProxy proxy =
                facade.new StatementProxy(openStatement(oracle), "SELECT 1", new Object());

        Throwable[] closerFailure = new Throwable[1];
        Thread closer = new Thread(() -> {
            try {
                proxy.invoke(null, CLOSE, null);
            } catch (Throwable t) {
                closerFailure[0] = t;
            }
        });
        closer.start();
        closer.join(JOIN_TIMEOUT_MS);
        Assertions.assertFalse(closer.isAlive(), "the closing thread must finish");
        Assertions.assertNull(closerFailure[0], "closing the proxy must not fail: " + closerFailure[0]);

        Assertions.assertEquals(Boolean.TRUE, proxy.invoke(null, IS_CLOSED, null),
                "isClosed() must report the close that completed on the other thread");
        SQLException refused = Assertions.assertThrows(SQLException.class,
                () -> proxy.invoke(null, EXECUTE, new Object[] { "SELECT 1" }),
                "a call after the close must be refused, not served and not a NullPointerException");
        Assertions.assertTrue(refused.getMessage().contains("closed"),
                "the refusal must carry the closed contract, was: " + refused.getMessage());
        Assertions.assertNull(proxy.invoke(null, CLOSE, null),
                "a second close remains a no-op");
        Assertions.assertTrue(oracle.closed, "the close must still have reached the statement");
    }

    /*
     * The race itself: a borrower executing in a loop while another thread
     * closes the proxy, in the watchdog's own sequence - ask whether it is
     * closed, then close it. A call may outrun the close and be served, or
     * lose to it and be refused with a SQLException, from the proxy's guard
     * or from the statement underneath; what it must never see is an invoke
     * on a null delegate. Bounded rounds, no sleeps: the assertions are
     * checked outcomes, so a failure names the round that produced it.
     */
    @Test
    void aBorrowerRacingACloseFromAnotherThreadNeverFindsANullDelegate() throws Throwable {
        final int rounds = 150;
        final int executions = 250;
        for (int round = 0; round < rounds; round++) {
            FakeStatement oracle = new FakeStatement();
            StatementFacade.StatementProxy proxy =
                    facade().new StatementProxy(openStatement(oracle), "SELECT 1", new Object());
            final CountDownLatch ready = new CountDownLatch(2);
            final CountDownLatch go = new CountDownLatch(1);
            final Throwable[] borrower = new Throwable[1];
            final Throwable[] closer = new Throwable[1];

            Thread executing = new Thread(() -> {
                ready.countDown();
                awaitGo(go);
                try {
                    for (int i = 0; i < executions; i++) {
                        Object served = proxy.invoke(null, EXECUTE, new Object[] { "SELECT 1" });
                        if (!Boolean.TRUE.equals(served)) {
                            borrower[0] = new IllegalStateException("execute() answered " + served);
                            return;
                        }
                    }
                } catch (Throwable t) {
                    borrower[0] = t;
                }
            });
            Thread closing = new Thread(() -> {
                ready.countDown();
                awaitGo(go);
                try {
                    if (Boolean.FALSE.equals(proxy.invoke(null, IS_CLOSED, null))) {
                        proxy.invoke(null, CLOSE, null);
                    }
                } catch (Throwable t) {
                    closer[0] = t;
                }
            });

            executing.start();
            closing.start();
            ready.await();
            go.countDown();
            executing.join(JOIN_TIMEOUT_MS);
            closing.join(JOIN_TIMEOUT_MS);
            Assertions.assertFalse(executing.isAlive() || closing.isAlive(),
                    "round " + round + ": both threads must finish");

            Assertions.assertTrue(borrower[0] == null || borrower[0] instanceof SQLException,
                    "round " + round + ": the borrower may be served or refused with a SQLException, "
                            + "but saw: " + borrower[0]);
            Assertions.assertTrue(closer[0] == null || closer[0] instanceof SQLException,
                    "round " + round + ": the closer may close or find the statement closed, "
                            + "but saw: " + closer[0]);
            Assertions.assertEquals(Boolean.TRUE, proxy.invoke(null, IS_CLOSED, null),
                    "round " + round + ": the joined close must have closed the proxy for everyone");
        }
    }

    /*
     * The result set handle's version of the completed close: the refusal
     * carries its own closed contract, the handle says closed, a repeated
     * close stays a no-op, and the cursor underneath really was closed.
     */
    @Test
    void aResultSetCloseCompletedByAnotherThreadIsAnsweredAsClosedAfterwards() throws Throwable {
        StatementFacade facade = facade();
        FakeResultSet oracle = new FakeResultSet();
        StatementFacade.ResultSetProxy proxy =
                facade.new ResultSetProxy(openResultSet(oracle), new Object());

        Throwable[] closerFailure = new Throwable[1];
        Thread closer = new Thread(() -> {
            try {
                proxy.invoke(null, RESULT_SET_CLOSE, null);
            } catch (Throwable t) {
                closerFailure[0] = t;
            }
        });
        closer.start();
        closer.join(JOIN_TIMEOUT_MS);
        Assertions.assertFalse(closer.isAlive(), "the closing thread must finish");
        Assertions.assertNull(closerFailure[0], "closing the proxy must not fail: " + closerFailure[0]);

        Assertions.assertEquals(Boolean.TRUE, proxy.invoke(null, RESULT_SET_IS_CLOSED, null),
                "isClosed() must report the close that completed on the other thread");
        SQLException refused = Assertions.assertThrows(SQLException.class,
                () -> proxy.invoke(null, NEXT, null),
                "a read after the close must be refused, not served and not a NullPointerException");
        Assertions.assertTrue(refused.getMessage().contains("closed"),
                "the refusal must carry the closed contract, was: " + refused.getMessage());
        Assertions.assertNull(proxy.invoke(null, RESULT_SET_CLOSE, null),
                "a second close remains a no-op");
        Assertions.assertTrue(oracle.closed, "the close must still have reached the result set");
    }

    /*
     * The same race one layer down: a reader calling next() in a loop while
     * another thread closes the result set handle out from under it, as the
     * thread that owns the statement does when it gives up on the read.
     */
    @Test
    void aResultSetReaderRacingACloseFromAnotherThreadNeverFindsANullDelegate() throws Throwable {
        final int rounds = 150;
        final int reads = 250;
        for (int round = 0; round < rounds; round++) {
            FakeResultSet oracle = new FakeResultSet();
            StatementFacade.ResultSetProxy proxy =
                    facade().new ResultSetProxy(openResultSet(oracle), new Object());
            final CountDownLatch ready = new CountDownLatch(2);
            final CountDownLatch go = new CountDownLatch(1);
            final Throwable[] reader = new Throwable[1];
            final Throwable[] closer = new Throwable[1];

            Thread reading = new Thread(() -> {
                ready.countDown();
                awaitGo(go);
                try {
                    for (int i = 0; i < reads; i++) {
                        Object served = proxy.invoke(null, NEXT, null);
                        if (!Boolean.TRUE.equals(served)) {
                            reader[0] = new IllegalStateException("next() answered " + served);
                            return;
                        }
                    }
                } catch (Throwable t) {
                    reader[0] = t;
                }
            });
            Thread closing = new Thread(() -> {
                ready.countDown();
                awaitGo(go);
                try {
                    if (Boolean.FALSE.equals(proxy.invoke(null, RESULT_SET_IS_CLOSED, null))) {
                        proxy.invoke(null, RESULT_SET_CLOSE, null);
                    }
                } catch (Throwable t) {
                    closer[0] = t;
                }
            });

            reading.start();
            closing.start();
            ready.await();
            go.countDown();
            reading.join(JOIN_TIMEOUT_MS);
            closing.join(JOIN_TIMEOUT_MS);
            Assertions.assertFalse(reading.isAlive() || closing.isAlive(),
                    "round " + round + ": both threads must finish");

            Assertions.assertTrue(reader[0] == null || reader[0] instanceof SQLException,
                    "round " + round + ": the reader may be served or refused with a SQLException, "
                            + "but saw: " + reader[0]);
            Assertions.assertTrue(closer[0] == null || closer[0] instanceof SQLException,
                    "round " + round + ": the closer may close or find the result set closed, "
                            + "but saw: " + closer[0]);
            Assertions.assertEquals(Boolean.TRUE, proxy.invoke(null, RESULT_SET_IS_CLOSED, null),
                    "round " + round + ": the joined close must have closed the handle for everyone");
        }
    }

    /*
     * The same completed cross-thread close over the very handles a real
     * borrow hands out, so the guards are read through the proxies the pool
     * itself builds: the refusal must be the facade's own closed contract -
     * its exact wording, not the driver's - on both the statement and the
     * cursor read from it, while the borrow itself keeps serving.
     */
    @Test
    void handlesClosedByAnotherThreadDuringABorrowAreAnsweredFromTheClosedContract()
            throws Exception {
        DataSource ds = newDataSource();
        try (Connection pooled = ds.getConnection();
                Statement st = pooled.createStatement()) {
            ResultSet rs = st.executeQuery("SELECT 1");

            Thread closer = new Thread(() -> {
                try {
                    rs.close();
                    st.close();
                } catch (SQLException e) {
                    // the close is the subject of the assertions below
                }
            });
            closer.start();
            try {
                closer.join(JOIN_TIMEOUT_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("the closing thread must finish", e);
            }
            Assertions.assertFalse(closer.isAlive(), "the closing thread must finish");

            Assertions.assertTrue(st.isClosed(), "the statement must report the foreign close");
            Assertions.assertTrue(rs.isClosed(), "the result set must report the foreign close");
            SQLException refused = Assertions.assertThrows(SQLException.class,
                    () -> st.execute("SELECT 1"),
                    "a call after the foreign close must be refused, not served and not a "
                            + "NullPointerException");
            Assertions.assertEquals("Statement closed.", refused.getMessage(),
                    "the refusal must be the facade's closed contract, not the driver's wording");
            SQLException refusedRead = Assertions.assertThrows(SQLException.class, rs::next,
                    "a read after the foreign close must be refused, not served and not a "
                            + "NullPointerException");
            Assertions.assertEquals("ResultSet closed.", refusedRead.getMessage(),
                    "the refusal must be the facade's closed contract, not the driver's wording");

            try (Statement probe = pooled.createStatement()) {
                Assertions.assertTrue(probe.execute("SELECT 1"),
                        "the borrow itself must keep serving its own handles");
            }
            rs.close();
            st.close();
        }
    }
}
