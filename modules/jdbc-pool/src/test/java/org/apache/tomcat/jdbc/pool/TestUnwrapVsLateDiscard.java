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
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;

/**
 * The Wrapper branches of the tail interceptor's dispatch against a discard
 * marking - {@code PooledConnection.setDiscarded(true)}, the first write of
 * every pool-side disposal of a borrowed session: the abandon path, a forced
 * close, the documented interceptor hook - landing while a dispatched
 * {@code unwrap} or {@code isWrapperFor} call is in flight. None of these
 * writers touches the session field, which they reach - if at all - only in
 * a later step of the take-down, so a state exists in which the field still
 * reads live while the handle already reports itself closed, and a Wrapper
 * answer taken from that state hands a caller the live physical session, the
 * raw driver connection, or the pool's own recycled pooled connection behind
 * a handle that refuses {@code createStatement}. The branches now answer
 * from the closed guard's snapshot, re-check it once for the two of them,
 * and the private overloads they call fence their own handout against a
 * marking that lands after that re-check. The pair stays agreed in this
 * state: the unwrap refuses with the closed failure and hands out nothing,
 * and the isWrapperFor - whose contract carries no failure in a boolean -
 * answers false, the answer the pair's released-state contract already
 * gives. A seal landing at the re-check is answered the closed failure too,
 * not the not-a-wrapper message that blamed the interface for a state of the
 * borrow.
 *
 * <p>The seam is driven deterministically, the way
 * {@link TestUnwrapVsMidGuardSeal} drives the seal, applied to the discarded
 * flag: a fabricated pooled connection whose {@code isDiscarded()} performs
 * the marking at a chosen read ordinal of one in-flight call and returns the
 * pre-marking value on the firing read - which is how a write racing in from
 * another thread presents to the read it overtakes. The ordinals of a
 * dispatched unwrap call are: #1 the closed guard's read, #2 the shared
 * Wrapper re-check, #3 the handout fence inside the snapshot helper.</p>
 *
 * <p>The interfaces the handle implements itself - java.sql.Connection,
 * javax.sql.PooledConnection, java.sql.Wrapper - are answered through that
 * same fence now too. Their answers used to be taken from the branch's
 * re-check alone, above the fenced helper that answers every other
 * interface, so a marking landing with that re-check left isWrapperFor
 * answering true - and unwrap handing the caller its own handle back - on
 * a handle that answers isClosed true, next to the closed failure the same
 * landing gave the session-valued interfaces below; the same call's answer
 * flipped on which read of the re-check the write overtook. Below the
 * fence these interfaces are refused like every other at ordinals #1 and
 * #2, and at the #3 landing past the fence's own read the pair is
 * live-consistent: true and the handle's own object, together.</p>
 */
class TestUnwrapVsLateDiscard extends JdbcPoolBaseTest {

    /*
     * A pooled connection that stands in for the discard marking landing at
     * a fixed read ordinal of an in-flight call. The dispatch and the two
     * snapshot helpers consult the state through the pooled connection they
     * snapshotted - the discarded read is where they still trust the object
     * they took - so a marking written while one of those reads is in
     * progress is exactly the interleaving the abandon, the forced close and
     * an interceptor's setDiscarded produce against the call's own reads,
     * with none of their timing to get right. One shot: it fires on the
     * fireAt-th discarded read after the test arms it, and the firing read
     * itself still answers false - the write lands with that read, too late
     * for it, which is what makes the check that ran it pass on a state that
     * is already gone; every read after it observes the marking, as it must.
     * The object also holds the real pooled connection's live physical
     * session, so the answer the refused call would once have returned is a
     * session a caller could really have worked on.
     */
    private static class DiscardOnDiscardedRead extends PooledConnection {

        private final Connection session;
        private final int fireAt;
        private volatile boolean armed;
        private final AtomicInteger reads = new AtomicInteger();
        private final AtomicBoolean fired = new AtomicBoolean();

        DiscardOnDiscardedRead(PoolConfiguration prop, ConnectionPool parent,
                Connection session, int fireAt) {
            super(prop, parent);
            this.session = session;
            this.fireAt = fireAt;
        }

        @Override
        public Connection getConnection() {
            return session;
        }

        @Override
        public boolean isDiscarded() {
            if (armed && !fired.get() && reads.incrementAndGet() == fireAt) {
                fired.set(true);
                // The marking, written exactly as the pool's take-downs
                // write it: the flag first, the seal only later.
                setDiscarded(true);
                return false;
            }
            return super.isDiscarded();
        }
    }

    /*
     * The same fabrication for the other late writer: the seal a close or an
     * abort stores into the interceptor's own session field, landing at a
     * chosen discarded read of the call. One shot, and the firing read
     * answers false like the marking fabric's, so the check that ran it
     * passes; the handout fence that reads the field afterwards is what
     * observes the seal.
     */
    private static class SealOnDiscardedRead extends PooledConnection {

        private final ProxyConnection tail;
        private final Connection session;
        private final int fireAt;
        private volatile boolean armed;
        private final AtomicInteger reads = new AtomicInteger();
        private final AtomicBoolean fired = new AtomicBoolean();

        SealOnDiscardedRead(PoolConfiguration prop, ConnectionPool parent,
                ProxyConnection tail, Connection session, int fireAt) {
            super(prop, parent);
            this.tail = tail;
            this.session = session;
            this.fireAt = fireAt;
        }

        @Override
        public Connection getConnection() {
            return session;
        }

        @Override
        public boolean isDiscarded() {
            if (armed && !fired.get() && reads.incrementAndGet() == fireAt) {
                fired.set(true);
                // The seal, written exactly as the close and abort branches
                // write it.
                tail.setConnection(null);
            }
            return false;
        }
    }

    @Test
    void aMarkingAtTheWrapperReCheckRefusesTheDispatchedUnwrapAndHandsOutNothing() throws Exception {
        // The heart of the reported hole: the branch re-checks its snapshot
        // and then reached the session through the *public* helper, whose
        // fresh read of the field was checked against nothing - so a marking
        // landing with the re-check's own read still let the helper hand out
        // what that unchecked read found. All three session-valued routes
        // are pinned at the re-check ordinal (read #2): the physical session
        // behind java.sql.Connection, the raw driver object, and the pool's
        // own pooled connection - through which a caller could once reach
        // that same live session again at an arbitrary later time, from an
        // object the pool recycles to other borrowers.
        DataSource ds = newDataSource();
        ds.createPool();
        ConnectionPool pool = ds.getPool();

        Connection held = ds.getConnection();
        ProxyConnection tail = interceptorOf(held, ProxyConnection.class);
        Assertions.assertNotNull(tail, "the fixture must reach the tail of the chain");
        PooledConnection real = tail.getConnection();
        Assertions.assertNotNull(real, "precondition: the borrow is armed");
        Connection physical = real.getConnection();
        Assertions.assertNotNull(physical, "precondition: the session is live");
        Assertions.assertSame(held, tail.getBorrowHandle(),
                "precondition: the tail serves this handle");

        for (Class<?> iface : new Class<?>[] { Connection.class,
                org.h2.jdbc.JdbcConnection.class, PooledConnection.class }) {
            DiscardOnDiscardedRead marker = new DiscardOnDiscardedRead(
                    ds.getPoolProperties(), pool, physical, 2);
            tail.setConnection(marker);
            marker.armed = true;
            try {
                SQLException thrown = Assertions.assertThrows(SQLException.class,
                        () -> held.unwrap(iface),
                        iface.getName() + ": a marking landing at the Wrapper "
                        + "re-check must refuse the dispatched unwrap rather "
                        + "than hand out the session behind it");
                Assertions.assertTrue(thrown.getMessage().contains("already been closed"),
                        iface.getName() + ": the failure must be the closed-connection "
                        + "answer, not the not-a-wrapper message: " + thrown.getMessage());
                Assertions.assertTrue(marker.fired.get(),
                        "precondition: the call must have reached the seam - the "
                        + "re-check's discarded read met the marking");
            } finally {
                marker.armed = false;
                tail.setConnection(real);
            }
        }

        // A refusal hands out nothing: the refused calls returned no object
        // at all, the session stays the pool's own, and the borrow the
        // fabrics stood in for is undamaged - a refused Wrapper call is not
        // a closed handle.
        Assertions.assertEquals(1, poolSessions(),
                "the refused calls must not have disturbed the session");
        Assertions.assertFalse(held.isClosed(),
                "the refused calls must leave the borrow live");
        Assertions.assertSame(physical, held.unwrap(Connection.class),
                "the live borrow must still unwrap to the session");

        held.close();
        Assertions.assertEquals(0, pool.getActive());
        Assertions.assertEquals(1, pool.getIdle());
        ds.close();
        awaitSessions(0);
    }

    @Test
    void aMarkingAtTheGuardReadIsCaughtByTheWrapperReCheck() throws Exception {
        // The control that pins which check owns which landing: a marking
        // that lands with the closed guard's read (#1) is already visible to
        // the shared Wrapper re-check (#2), which refuses the unwrap with
        // the closed failure - the landing the re-check at the head of the
        // Wrapper block was written for.
        DataSource ds = newDataSource();
        ds.createPool();
        ConnectionPool pool = ds.getPool();

        Connection held = ds.getConnection();
        ProxyConnection tail = interceptorOf(held, ProxyConnection.class);
        PooledConnection real = tail.getConnection();
        Connection physical = real.getConnection();

        DiscardOnDiscardedRead marker = new DiscardOnDiscardedRead(
                ds.getPoolProperties(), pool, physical, 1);
        tail.setConnection(marker);
        marker.armed = true;
        try {
            SQLException thrown = Assertions.assertThrows(SQLException.class,
                    () -> held.unwrap(Connection.class),
                    "a marking landing at the guard's read must be caught by "
                    + "the Wrapper re-check");
            Assertions.assertTrue(thrown.getMessage().contains("already been closed"),
                    "the failure must be the closed-connection answer: " + thrown.getMessage());
            Assertions.assertTrue(marker.fired.get(),
                    "precondition: the call must have reached the seam");
        } finally {
            marker.armed = false;
            tail.setConnection(real);
        }

        held.close();
        ds.close();
        awaitSessions(0);
    }

    @Test
    void aMarkingAtEitherCheckedReadAnswersIsWrapperForWithFalse() throws Exception {
        // The ISWRAPPERFOR branch never re-checked at all: its whole answer
        // rested on the guard's single read, and the public helper it
        // delegated to consults neither the discarded flag nor the borrow
        // handle - so the pre-flight test a caller runs before unwrapping
        // answered true for a handle reporting itself closed. Pinned here at
        // both landings - the shared re-check (#2) and the handout fence
        // behind it (#3) - each answered false, the pair's released-state
        // answer, and each paired with the unwrap's closed failure at the
        // same ordinal so the two never disagree on this state.
        DataSource ds = newDataSource();
        ds.createPool();
        ConnectionPool pool = ds.getPool();

        Connection held = ds.getConnection();
        ProxyConnection tail = interceptorOf(held, ProxyConnection.class);
        PooledConnection real = tail.getConnection();
        Connection physical = real.getConnection();

        for (int ordinal : new int[] { 1, 2 }) {
            DiscardOnDiscardedRead marker = new DiscardOnDiscardedRead(
                    ds.getPoolProperties(), pool, physical, ordinal);
            tail.setConnection(marker);
            marker.armed = true;
            try {
                Assertions.assertFalse(held.isWrapperFor(org.h2.jdbc.JdbcConnection.class),
                        "a marking landing at read " + ordinal + " must answer a "
                        + "retiring handle's isWrapperFor with false, not true");
                Assertions.assertTrue(marker.fired.get(),
                        "precondition: the call must have reached the seam at read " + ordinal);
                Assertions.assertTrue(held.isClosed(),
                        "precondition: the marking must have reached the handle");

                // The pair, same landing, same state: where isWrapperFor
                // says false, the unwrap refuses.
                DiscardOnDiscardedRead pair = new DiscardOnDiscardedRead(
                        ds.getPoolProperties(), pool, physical, ordinal);
                tail.setConnection(pair);
                pair.armed = true;
                try {
                    Assertions.assertThrows(SQLException.class,
                            () -> held.unwrap(org.h2.jdbc.JdbcConnection.class),
                            "the unwrap must agree with the false its pair "
                            + "answered at read " + ordinal);
                } finally {
                    pair.armed = false;
                }
            } finally {
                marker.armed = false;
                tail.setConnection(real);
            }
        }

        Assertions.assertTrue(held.isWrapperFor(org.h2.jdbc.JdbcConnection.class),
                "live behaviour unchanged: a live handle is a wrapper of the "
                + "driver's own interface");

        held.close();
        ds.close();
        awaitSessions(0);
    }

    @Test
    void aSealAtTheWrapperReCheckAnswersTheDispatchedUnwrapWithTheClosedFailure() throws Exception {
        // The message choice on the branch's late-seal path. A seal landing
        // at the guard's read is caught by the Wrapper re-check itself -
        // pinned in TestUnwrapVsMidGuardSeal. A seal that lands one read
        // later, with the re-check's own read of the discarded flag, is now
        // caught by the handout fence inside the snapshot helper, and both
        // answer the closed-connection failure. It used to be the public
        // helper's fresh read of the field, now null under the seal, that
        // ended the call - with "Not a wrapper of java.sql.Connection": a
        // refusal that blamed the interface and invited the caller to try
        // the next interface of a session that is gone.
        DataSource ds = newDataSource();
        ds.createPool();
        ConnectionPool pool = ds.getPool();

        Connection held = ds.getConnection();
        ProxyConnection tail = interceptorOf(held, ProxyConnection.class);
        PooledConnection real = tail.getConnection();
        Connection physical = real.getConnection();

        SealOnDiscardedRead sealer = new SealOnDiscardedRead(
                ds.getPoolProperties(), pool, tail, physical, 2);
        tail.setConnection(sealer);
        sealer.armed = true;
        try {
            SQLException thrown = Assertions.assertThrows(SQLException.class,
                    () -> held.unwrap(Connection.class),
                    "a seal landing at the Wrapper re-check must answer the "
                    + "closed failure, not the not-a-wrapper refusal");
            Assertions.assertTrue(thrown.getMessage().contains("already been closed"),
                    "the failure must be the closed-connection answer: " + thrown.getMessage());
            Assertions.assertTrue(sealer.fired.get(),
                    "precondition: the call must have reached the seam - the "
                    + "re-check's discarded read met the seal");
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
    void aMarkingAtEitherCheckedReadAnswersTheSelfInterfacesAsReleased() throws Exception {
        // The self-interface legs of the merged Wrapper branch - the
        // interfaces the handle implements itself - used to answer above
        // the handout fence the other half of the pair descends into: a
        // marking landing with the branch's re-check (read #2, the landing
        // this class pins as must-refuse) left isWrapperFor answering true
        // for java.sql.Connection, javax.sql.PooledConnection and
        // java.sql.Wrapper on a handle answering isClosed true, while the
        // paired unwrap threw the closed failure - and a marking landing
        // with the guard's read (#1), which the re-check does see, answered
        // false. One handle, one landing kind, two pair-agreements on which
        // read of the same re-check the write overtook. Both answers now
        // come from below the fence: at either checked read the pair gives
        // the released-state answer - false, paired with the unwrap's
        // closed failure - for these interfaces exactly as for the
        // driver's own.
        DataSource ds = newDataSource();
        ds.createPool();
        ConnectionPool pool = ds.getPool();

        Connection held = ds.getConnection();
        ProxyConnection tail = interceptorOf(held, ProxyConnection.class);
        PooledConnection real = tail.getConnection();
        Connection physical = real.getConnection();

        Class<?>[] selfInterfaces = { Connection.class,
                javax.sql.PooledConnection.class, java.sql.Wrapper.class };
        for (int ordinal : new int[] { 1, 2 }) {
            for (Class<?> iface : selfInterfaces) {
                DiscardOnDiscardedRead marker = new DiscardOnDiscardedRead(
                        ds.getPoolProperties(), pool, physical, ordinal);
                tail.setConnection(marker);
                marker.armed = true;
                try {
                    Assertions.assertFalse(held.isWrapperFor(iface),
                            iface.getName() + ": a marking landing at read "
                            + ordinal + " must answer the handle's own "
                            + "interface with false, not the true that "
                            + "promises an unwrap the pair refuses");
                    Assertions.assertTrue(marker.fired.get(),
                            "precondition: the call must have reached the "
                            + "seam at read " + ordinal);
                    Assertions.assertTrue(held.isClosed(),
                            "precondition: the marking must have reached "
                            + "the handle");
                } finally {
                    marker.armed = false;
                    tail.setConnection(real);
                }

                // The pair, same landing, fresh fabric: where isWrapperFor
                // said false, the unwrap refuses with the closed failure.
                DiscardOnDiscardedRead pair = new DiscardOnDiscardedRead(
                        ds.getPoolProperties(), pool, physical, ordinal);
                tail.setConnection(pair);
                pair.armed = true;
                try {
                    SQLException thrown = Assertions.assertThrows(SQLException.class,
                            () -> held.unwrap(iface),
                            iface.getName() + ": the unwrap must agree with "
                            + "the false its pair answered at read " + ordinal);
                    Assertions.assertTrue(thrown.getMessage().contains("already been closed"),
                            iface.getName() + ": the failure must be the "
                            + "closed-connection answer: " + thrown.getMessage());
                    Assertions.assertTrue(pair.fired.get(),
                            "precondition: the unwrapping call must have "
                            + "reached the seam at read " + ordinal);
                } finally {
                    pair.armed = false;
                    tail.setConnection(real);
                }
            }
        }

        // Live behaviour unchanged: the handle is still a wrapper of the
        // interfaces it implements itself, and each keeps its established
        // unwrap answer.
        Assertions.assertTrue(held.isWrapperFor(Connection.class));
        Assertions.assertSame(physical, held.unwrap(Connection.class));
        Assertions.assertTrue(held.isWrapperFor(javax.sql.PooledConnection.class));
        Assertions.assertSame(held, held.unwrap(javax.sql.PooledConnection.class));
        Assertions.assertTrue(held.isWrapperFor(java.sql.Wrapper.class));
        Assertions.assertSame(physical, held.unwrap(java.sql.Wrapper.class));

        held.close();
        Assertions.assertEquals(0, pool.getActive());
        Assertions.assertEquals(1, pool.getIdle());
        ds.close();
        awaitSessions(0);
    }

    @Test
    void aSealAtTheWrapperReCheckAnswersTheSelfInterfacesAsReleased() throws Exception {
        // The other late writer, same pair: a close's seal of the session
        // field landing with the branch's re-check used to leave the
        // self-interface answers above the fence - isWrapperFor true, and
        // unwrap handing out the handle itself on a borrow whose seal the
        // field already shows. Below the fence the seal is exactly what the
        // fence's own field read observes, and both halves answer the
        // refusal.
        DataSource ds = newDataSource();
        ds.createPool();
        ConnectionPool pool = ds.getPool();

        Connection held = ds.getConnection();
        ProxyConnection tail = interceptorOf(held, ProxyConnection.class);
        PooledConnection real = tail.getConnection();
        Connection physical = real.getConnection();

        Class<?>[] selfInterfaces = { Connection.class,
                javax.sql.PooledConnection.class, java.sql.Wrapper.class };
        for (Class<?> iface : selfInterfaces) {
            SealOnDiscardedRead sealer = new SealOnDiscardedRead(
                    ds.getPoolProperties(), pool, tail, physical, 2);
            tail.setConnection(sealer);
            sealer.armed = true;
            try {
                Assertions.assertFalse(held.isWrapperFor(iface),
                        iface.getName() + ": a seal landing at the Wrapper "
                        + "re-check must answer the handle's own interface "
                        + "with false");
                Assertions.assertTrue(sealer.fired.get(),
                        "precondition: the call must have reached the seam");
            } finally {
                sealer.armed = false;
                tail.setConnection(real);
            }

            SealOnDiscardedRead pair = new SealOnDiscardedRead(
                    ds.getPoolProperties(), pool, tail, physical, 2);
            tail.setConnection(pair);
            pair.armed = true;
            try {
                SQLException thrown = Assertions.assertThrows(SQLException.class,
                        () -> held.unwrap(iface),
                        iface.getName() + ": the unwrap must agree with the "
                        + "false its pair answered on the same seal landing");
                Assertions.assertTrue(thrown.getMessage().contains("already been closed"),
                        iface.getName() + ": the failure must be the "
                        + "closed-connection answer: " + thrown.getMessage());
                Assertions.assertTrue(pair.fired.get(),
                        "precondition: the unwrapping call must have "
                        + "reached the seam");
            } finally {
                pair.armed = false;
                tail.setConnection(real);
            }
        }

        // The seal fabrics stood down without damaging the borrow: the
        // field they sealed is restored, and the handle ends as any other.
        held.close();
        Assertions.assertEquals(0, pool.getActive());
        Assertions.assertEquals(1, pool.getIdle());
        ds.close();
        awaitSessions(0);
    }

    @Test
    void aMarkingOvertakingTheHandoutFenceKeepsTheSelfInterfacePairLiveConsistent()
            throws Exception {
        // The landing past the fence's own read (#3) is the class's
        // documented boundary - a write that outruns the last check reads
        // as a call that simply came first. What must not change is the
        // pair's agreement at that landing: where the self-interface
        // unwrap hands the caller its handle (javax.sql.PooledConnection)
        // or its session (java.sql.Connection and java.sql.Wrapper, which
        // the physical connection provides too), isWrapperFor says true -
        // the same pair the live handle gives - never the one-sided true
        // over a closed failure the unfenced legs gave the landing before.
        DataSource ds = newDataSource();
        ds.createPool();
        ConnectionPool pool = ds.getPool();

        Connection held = ds.getConnection();
        ProxyConnection tail = interceptorOf(held, ProxyConnection.class);
        PooledConnection real = tail.getConnection();
        Connection physical = real.getConnection();

        // The object each interface's boundary answer hands out.
        Object[][] cases = { { javax.sql.PooledConnection.class, held },
                { Connection.class, physical }, { java.sql.Wrapper.class, physical } };
        for (Object[] one : cases) {
            Class<?> iface = (Class<?>) one[0];
            Object expected = one[1];
            DiscardOnDiscardedRead marker = new DiscardOnDiscardedRead(
                    ds.getPoolProperties(), pool, physical, 3);
            tail.setConnection(marker);
            marker.armed = true;
            Object unwrapped;
            try {
                unwrapped = held.unwrap(iface);
            } finally {
                marker.armed = false;
                tail.setConnection(real);
            }
            Assertions.assertTrue(marker.fired.get(),
                    "precondition: the unwrapping call must have reached "
                    + "the seam at the fence's own read");

            DiscardOnDiscardedRead pair = new DiscardOnDiscardedRead(
                    ds.getPoolProperties(), pool, physical, 3);
            tail.setConnection(pair);
            pair.armed = true;
            boolean wrapperFor;
            try {
                wrapperFor = held.isWrapperFor(iface);
            } finally {
                pair.armed = false;
                tail.setConnection(real);
            }
            Assertions.assertTrue(pair.fired.get(),
                    "precondition: the querying call must have reached the "
                    + "seam at the fence's own read");
            Assertions.assertTrue(wrapperFor,
                    iface.getName() + ": past the fence the pair answers "
                    + "live together, not one-sided");
            Assertions.assertSame(expected, unwrapped,
                    iface.getName() + ": the unwrap at the fence's own read "
                    + "hands out the answer the live handle gives");
        }

        // No fabric survived into the borrow's own life: the real session
        // was never marked, and the handle ends clean.
        Assertions.assertFalse(real.isDiscarded(),
                "the fabrics marked only themselves");
        held.close();
        Assertions.assertEquals(0, pool.getActive());
        Assertions.assertEquals(1, pool.getIdle());
        ds.close();
        awaitSessions(0);
    }
}
