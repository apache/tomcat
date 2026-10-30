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

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.CountDownLatch;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Closing a statement from another thread, the way the pool does it: the
 * statement finalizer closes the tracked statements from whichever thread
 * closes the connection, while a borrower may still be executing on the
 * query report proxy of the same statement. The close writes two fields -
 * the flag and the delegate it nulls - and a call answered in the middle of
 * that can find the flag open and the delegate gone. Answering such a call
 * by invoking on the missing delegate escapes to the application as a
 * NullPointerException on a null receiver; the closed contract owes it the
 * SQLException instead. The racing test is the one that pins that, and fails
 * while the two fields are read without a snapshot; the test before it
 * guards the contract a completed cross-thread close must leave behind.
 */
class TestQueryReportCrossThreadClose {

    private static final long JOIN_TIMEOUT_MS = 30000;

    private static final Method EXECUTE;
    private static final Method CLOSE;
    private static final Method IS_CLOSED;

    static {
        try {
            EXECUTE = Statement.class.getMethod("execute", String.class);
            CLOSE = Statement.class.getMethod("close");
            IS_CLOSED = Statement.class.getMethod("isClosed");
        } catch (NoSuchMethodException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    /* A query report with the reporting hooks silenced: the state of the
     * proxy under a closing thread is the subject, not what gets reported. */
    private static class SilentQueryReport extends AbstractQueryReport {
        @Override
        protected void prepareStatement(String sql, long time) {
            // nothing is reported in these tests
        }

        @Override
        protected void prepareCall(String query, long time) {
            // nothing is reported in these tests
        }

        @Override
        public void closeInvoked() {
            // no borrow ends here; the close under test is the statement's
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

    private static Statement openStatement(FakeStatement oracle) {
        return (Statement) Proxy.newProxyInstance(
                TestQueryReportCrossThreadClose.class.getClassLoader(),
                new Class<?>[] { Statement.class }, oracle);
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
     * closed contract, the flag says closed, a repeated close stays a
     * no-op, and the delegate underneath really was closed.
     */
    @Test
    void aCloseCompletedByAnotherThreadIsAnsweredAsClosedAfterwards() throws Throwable {
        SilentQueryReport report = new SilentQueryReport();
        FakeStatement oracle = new FakeStatement();
        AbstractQueryReport.StatementProxy proxy =
                report.new StatementProxy(openStatement(oracle), "SELECT 1");

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
     * closes the proxy, in the statement finalizer's own sequence - ask
     * whether it is closed, then close it. A call may outrun the close and
     * be served, or lose to it and be refused with a SQLException, from the
     * proxy's guard or from the statement underneath; what it must never
     * see is an invoke on a null delegate. Bounded rounds, no sleeps: the
     * assertions are checked outcomes, so a failure names the round that
     * produced it.
     */
    @Test
    void aBorrowerRacingACloseFromAnotherThreadNeverFindsANullDelegate() throws Throwable {
        SilentQueryReport report = new SilentQueryReport();
        final int rounds = 150;
        final int executions = 250;
        for (int round = 0; round < rounds; round++) {
            FakeStatement oracle = new FakeStatement();
            AbstractQueryReport.StatementProxy proxy =
                    report.new StatementProxy(openStatement(oracle), "SELECT 1");
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
}
