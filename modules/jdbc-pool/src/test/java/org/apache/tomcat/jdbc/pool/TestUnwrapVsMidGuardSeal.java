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

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;

/**
 * The Wrapper branch of the tail interceptor's dispatch and its
 * {@code toString()} against the seal a concurrent close or abort makes, or
 * the pool's teardown {@code reset(null, null)} leaves, landing while a call
 * is in flight. Both used to read the session field a second time after the
 * closed guard had already checked it: a seal landing between the guard's
 * read and the branch's own read made {@code handle.unwrap(iface)} throw a
 * raw {@code NullPointerException} out of the dispatch - which nothing
 * converts on the pool's own take-downs, where the disposable facade the
 * call came through was never disposed - and made {@code toString()} answer
 * with an NPE where its contract asks for a stable string. The dispatch's
 * closed guard and the unwrap branch now share one snapshot of the field,
 * and the branch re-checks it before use the way the other guarded branches
 * do; {@code toString()} reads the field once. This class pins the seam
 * deterministically - the seal lands at a fixed point of the call, with no
 * timing to get right - and pins the released tail's direct answers.
 */
class TestUnwrapVsMidGuardSeal extends JdbcPoolBaseTest {

    /*
     * A pool whose borrow the abandoned-connection check claims on demand:
     * the timeout is one second and the tests push the borrow past it with
     * setTimestamp(0) rather than sleeping, so every run drives a real
     * release - disconnect(true) and the setHandler(null) walk that resets
     * every cached interceptor to (null, null) - without depending on a
     * timer thread.
     */
    private DataSource abandonedCheckPool() throws SQLException {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setInitialSize(0);
        ds.getPoolProperties().setRemoveAbandoned(true);
        ds.getPoolProperties().setRemoveAbandonedTimeout(1);
        ds.createPool();
        return ds;
    }

    /*
     * A pooled connection that stands in for the seal landing at a fixed
     * point of an in-flight call. The dispatch's guard consults the state
     * through the pooled connection it snapshotted - the discarded read is
     * where the guard still trusts the object it took - so a seal that
     * nulls the interceptor's session field while that read is in progress
     * is exactly the interleaving a close, an abort or the teardown walk
     * produces against the guard's own reads, with none of their timing to
     * get right. One shot: it fires on the first discarded read after the
     * test arms it, and the flag it returns stays false - the object the
     * guard snapshotted is not itself discarded, only the field is sealed,
     * which is what makes the guard pass on a state that is already gone.
     */
    private static class SealOnDiscardedRead extends PooledConnection {

        private final ProxyConnection tail;
        private volatile boolean armed;
        private final AtomicBoolean fired = new AtomicBoolean();

        SealOnDiscardedRead(PoolConfiguration prop, ConnectionPool parent, ProxyConnection tail) {
            super(prop, parent);
            this.tail = tail;
        }

        @Override
        public boolean isDiscarded() {
            if (armed && fired.compareAndSet(false, true)) {
                // The seal, written exactly as the close and abort
                // branches write it and as the teardown walk reaches it.
                tail.setConnection(null);
            }
            return false;
        }
    }

    @Test
    void aSealLandingBetweenTheGuardAndTheUnwrapBranchIsAnsweredClosedNotAsANullPointerException()
            throws Exception {
        // The unwrap of an interface the handle implements itself - here
        // java.sql.Connection - is the question whose branch dereferences
        // the session behind the field to tell "the handle provides it"
        // from "the physical connection does". That dereference used to be
        // a fresh read of the field, made after the closed guard had
        // already read it once: the seal landing in between left the
        // branch dereferencing the null and the caller's unwrap met a raw
        // NullPointerException. The branch now acts on the snapshot the
        // guard checked and re-checks it before use, so the same landing
        // is answered the way every use of an ended handle is.
        DataSource ds = newDataSource();
        ds.createPool();
        ConnectionPool pool = ds.getPool();

        Connection held = ds.getConnection();
        ProxyConnection tail = interceptorOf(held, ProxyConnection.class);
        Assertions.assertNotNull(tail, "the fixture must reach the tail of the chain");
        PooledConnection real = tail.getConnection();
        Assertions.assertNotNull(real, "precondition: the borrow is armed");
        Assertions.assertSame(held, tail.getBorrowHandle(),
                "precondition: the tail serves this handle");

        SealOnDiscardedRead sealer =
                new SealOnDiscardedRead(ds.getPoolProperties(), pool, tail);
        // Stand in for the instant the race offers: the borrow still reads
        // live to the call's guard, but its seal is armed to land at the
        // guard's own discarded read.
        tail.setConnection(sealer);
        sealer.armed = true;
        try {
            SQLException thrown = Assertions.assertThrows(SQLException.class,
                    () -> held.unwrap(Connection.class),
                    "a seal landing between the guard's read and the branch's "
                    + "own read of the field must answer the closed failure, "
                    + "not a raw NullPointerException");
            Assertions.assertTrue(thrown.getMessage().contains("already been closed"),
                    "the failure must be the closed-connection answer: "
                    + thrown.getMessage());
            Assertions.assertTrue(sealer.fired.get(),
                    "precondition: the call must have reached the seam - the "
                    + "guard's discarded read met the seal");
        } finally {
            sealer.armed = false;
            tail.setConnection(real);
        }

        // Nothing the refused call passed through was damaged: the borrow
        // the fabricated seal stood down ends as any borrow does.
        held.close();
        Assertions.assertEquals(0, pool.getActive());
        Assertions.assertEquals(1, pool.getIdle());

        ds.close();
        awaitSessions(0);
    }

    @Test
    void theReleasedTailAnswersTheDispatchedUnwrapWithTheClosedFailure() throws Exception {
        // The branch's other end, driven through a real teardown rather a
        // fabrication: once the abandoned check has walked the chain to
        // (null, null), a call still descending with the ended handle is
        // refused by the closed guard itself - the state the seal leaves is
        // answered the closed failure whatever the interface asked, and the
        // snapshot shared with the branch never stands in front of a
        // dereference it did not check.
        DataSource ds = abandonedCheckPool();
        ConnectionPool pool = ds.getPool();

        Connection held = ds.getConnection();
        ProxyConnection tail = interceptorOf(held, ProxyConnection.class);
        tail.getConnection().setTimestamp(0L); // past the one-second abandon timeout

        pool.checkAbandoned();

        Assertions.assertNull(tail.getConnection(),
                "precondition: the teardown walk must clear the session field");
        Assertions.assertNull(tail.getBorrowHandle(),
                "precondition: and the borrow handle");

        SQLException thrown = Assertions.assertThrows(SQLException.class,
                () -> tail.invoke(held, Connection.class.getMethod("unwrap", Class.class),
                        new Object[] { Connection.class }),
                "the dispatched unwrap on a released tail must answer the "
                + "closed failure, never a NullPointerException");
        Assertions.assertTrue(thrown.getMessage().contains("already been closed"),
                "the failure must be the closed-connection answer: "
                + thrown.getMessage());
        awaitSessions(0);
    }

    @Test
    void theTornDownTailKeepsAnsweringTheSameStableString() throws Exception {
        // toString() read the field twice - the null test on one read, the
        // string on the next - so a seal landing between them answered the
        // handle's string with a NullPointerException. No single-threaded
        // call can reproduce the old failure (nothing runs between the two
        // reads but the seal itself), so what is pinned here is the answer
        // the single read must keep giving: the torn-down tail describes
        // itself with the null fallback, identically on every call, and the
        // handle still fronted by the facade the teardown never disposed
        // reaches the same answer through the dispatch's toString branch.
        DataSource ds = abandonedCheckPool();
        ConnectionPool pool = ds.getPool();

        Connection held = ds.getConnection();
        ProxyConnection tail = interceptorOf(held, ProxyConnection.class);
        tail.getConnection().setTimestamp(0L);

        pool.checkAbandoned();

        String torn = Assertions.assertDoesNotThrow(tail::toString,
                "a released tail's string must never be a NullPointerException");
        Assertions.assertEquals("ProxyConnection[null]", torn,
                "the torn-down tail answers with the null fallback");
        Assertions.assertEquals(torn, tail.toString(),
                "and the answer is stable across calls");
        Assertions.assertEquals(torn, Assertions.assertDoesNotThrow(held::toString),
                "the handle, still fronted by the undisposed facade, reaches "
                + "the same answer through the chain");
        awaitSessions(0);
    }
}
