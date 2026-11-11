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

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.jdbc.JdbcPoolBaseTest;
import org.apache.tomcat.jdbc.pool.ConnectionPool;
import org.apache.tomcat.jdbc.pool.DataSource;
import org.apache.tomcat.jdbc.pool.JdbcInterceptor;
import org.apache.tomcat.jdbc.pool.PoolProperties;
import org.apache.tomcat.jdbc.pool.PooledConnection;

/**
 * What {@code StatementFinalizer} owes the application from the other
 * side of the coin: statements the application <em>did</em> close must be
 * forgotten the moment they are closed (the tracked list is bounded by
 * genuinely open statements closed through its handle, not by everything
 * the borrow ever created), and statements it <em>did not</em> close must survive garbage collection
 * so they can still be closed when the connection is returned. What it owes
 * on top of that is a handle that answers {@code equals} and
 * {@code hashCode} on itself, like every other statement handle the pool
 * hands out, so that the tracker's own bookkeeping is what decides which
 * statements are retired and the handle's identity is what decides where it
 * sits in a hash container.
 */
class TestStatementFinalizer extends JdbcPoolBaseTest {

    private DataSource finalizerPool() {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setJdbcInterceptors(StatementFinalizer.class.getName());
        return ds;
    }

    /*
     * The tracker's own list, read reflectively so the assertions are about
     * the tracker's state rather than about any particular field or entry
     * type it happens to use internally.
     */
    @SuppressWarnings("unchecked")
    private static List<Object> trackedEntries(StatementFinalizer finalizer) throws Exception {
        Field field = StatementFinalizer.class.getDeclaredField("statements");
        field.setAccessible(true);
        return (List<Object>) field.get(finalizer);
    }

    private static int tracked(StatementFinalizer finalizer) throws Exception {
        return trackedEntries(finalizer).size();
    }

    /* The statement a tracked entry remembers; null if it let go of it. */
    private static Statement trackedStatement(Object entry) throws Exception {
        try {
            return (Statement) entry.getClass().getMethod("getStatement").invoke(entry);
        } catch (java.lang.reflect.InvocationTargetException e) {
            throw new AssertionError(e.getCause());
        }
    }

    @Test
    void closedStatementsAreForgottenTheMomentTheyAreClosed() throws Exception {
        DataSource ds = finalizerPool();
        ds.createPool();

        try (Connection pooled = ds.getConnection()) {
            StatementFinalizer finalizer = interceptorOf(pooled, StatementFinalizer.class);
            Assertions.assertNotNull(finalizer,
                    "the interceptor must sit in the chain of the borrowed connection");

            for (int i = 0; i < 250; i++) {
                try (Statement st = pooled.createStatement();
                        ResultSet rs = st.executeQuery("SELECT 1")) {
                    rs.next();
                }
                try (PreparedStatement ps = pooled.prepareStatement("SELECT ?")) {
                    ps.setInt(1, i);
                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                    }
                }
            }

            Assertions.assertEquals(0, tracked(finalizer),
                    "a borrow that closed every statement must not be charged for them at connection return");

            Statement openOne = pooled.createStatement();
            PreparedStatement openTwo = pooled.prepareStatement("SELECT 1");
            try (ResultSet rs = openOne.executeQuery("SELECT 1")) {
                rs.next();
            }
            try (ResultSet rs = openTwo.executeQuery()) {
                rs.next();
            }
            Assertions.assertEquals(2, tracked(finalizer),
                    "exactly the statements that are still open must be tracked");

            openOne.close();
            Assertions.assertEquals(1, tracked(finalizer),
                    "closing must remove the entry, not wait for the return");
            openTwo.close();
            Assertions.assertEquals(0, tracked(finalizer),
                    "closing must remove the entry, not wait for the return");
        }
    }

    @Test
    void aStatementTheApplicationLeakedIsStillTrackedAfterGarbageCollection() throws Exception {
        DataSource ds = finalizerPool();
        ds.createPool();

        Connection pooled = ds.getConnection();
        StatementFinalizer finalizer = interceptorOf(pooled, StatementFinalizer.class);
        Assertions.assertNotNull(finalizer);

        Statement leaked = pooled.createStatement();
        try (ResultSet rs = leaked.executeQuery("SELECT 1")) {
            rs.next();
        }

        leaked = null;
        // Best-effort collection: the contract this test pins down is the
        // close-at-return asserted below, which holds whatever the garbage
        // collector does. The collection attempt is what makes the null
        // check above bite a weak-reference implementation.
        for (int i = 0; i < 3; i++) {
            System.gc();
        }

        List<Object> entries = trackedEntries(finalizer);
        Assertions.assertEquals(1, entries.size(),
                "the leaked statement must still be tracked at the return");
        // A handle taken only after the collection: before the fix the
        // referent could be gone by then, and the interceptor would have
        // nothing left to close when the connection returns.
        Statement stillTracked = trackedStatement(entries.get(0));
        Assertions.assertNotNull(stillTracked,
                "finalisation must not depend on the leaked statement still being reachable");

        pooled.close();
        Assertions.assertTrue(stillTracked.isClosed(),
                "the leaked statement must be closed when its connection goes back");
    }

    @SuppressWarnings("unlikely-arg-type") // Use of unlikely arg-type is deliberate
    @Test
    void theTrackingWrapperStaysHonestAboutTheStatementItWraps() throws Exception {
        DataSource ds = finalizerPool();
        ds.createPool();

        try (Connection pooled = ds.getConnection()) {
            StatementFinalizer finalizer = interceptorOf(pooled, StatementFinalizer.class);
            Assertions.assertNotNull(finalizer);

            PreparedStatement ps = pooled.prepareStatement("SELECT 1");
            // The application handle is the statement facade's proxy, which
            // is always installed. The tracking wrapper this interceptor
            // puts in front of the driver's statement sits right beneath it,
            // and that wrapper is what forgets the tracked entry at close -
            // so its own contract is what the battery below pins down.
            PreparedStatement wrapper =
                    (PreparedStatement) proxiedObject(ps);
            Assertions.assertNotSame(ps, wrapper,
                    "the tracking wrapper must still sit between the facade and the driver");

            Assertions.assertTrue(wrapper.equals(wrapper), "the handle must be equal to itself");
            Assertions.assertFalse(wrapper.equals(pooled), "an unrelated object must not compare equal");
            Statement driver = driverStatement(ps);
            Assertions.assertFalse(wrapper.equals(driver),
                    "the wrapper is a handle of its own, not the statement it wraps");
            Assertions.assertFalse(driver.equals(wrapper),
                    "and the two must not compare equal in either direction");
            Set<Statement> seen = new HashSet<>();
            seen.add(wrapper);
            Assertions.assertTrue(seen.contains(wrapper), "hashCode must agree with equals");
            try (ResultSet rs = wrapper.executeQuery()) {
                Assertions.assertTrue(rs.next());
            }

            CallableStatement call = pooled.prepareCall("CALL NOW()");
            try (ResultSet rs = call.executeQuery()) {
                Assertions.assertTrue(rs.next());
            }
            Assertions.assertEquals(2, tracked(finalizer),
                    "the callable statement must be tracked as well");

            wrapper.close();
            wrapper.close(); // JDBC demands that a repeated close is a no-op
            Assertions.assertEquals(1, tracked(finalizer), "the close must have been counted once");

            Assertions.assertThrows(SQLException.class, wrapper::getMoreResults,
                    "use after close must still be reported, not swallowed");
            call.close();
            Assertions.assertEquals(0, tracked(finalizer));
        }
    }

    @SuppressWarnings("unlikely-arg-type") // Use of unlikely arg-type is deliberate
    @Test
    void theTrackingHandleAnswersObjectEqualityOnItself() throws Exception {
        DataSource ds = finalizerPool();
        ds.createPool();

        try (Connection pooled = ds.getConnection()) {
            StatementFinalizer finalizer = interceptorOf(pooled, StatementFinalizer.class);
            Assertions.assertNotNull(finalizer);

            PreparedStatement ps = pooled.prepareStatement("SELECT 1");
            PreparedStatement handle = (PreparedStatement) proxiedObject(ps);
            Statement driver = driverStatement(ps);
            Assertions.assertNotSame(handle, ps, "the tracking handle was reached");
            Assertions.assertNotSame(handle, driver, "the tracking handle is not the driver's statement");

            // The handle is itself and nothing else. It used to answer equal
            // to the statement it wraps while that statement - answering by
            // its own equality, which is identity for a driver statement -
            // did not answer equal to the handle back, and that one-sided
            // relation is what Object.equals(Object) rules out.
            Assertions.assertTrue(handle.equals(handle), "the handle must be equal to itself");
            Assertions.assertFalse(handle.equals(driver),
                    "the handle must not claim the statement it wraps as itself");
            Assertions.assertFalse(driver.equals(handle),
                    "and the relation must hold in the other direction too");
            Assertions.assertFalse(handle.equals(null),
                    "equals must answer a null argument rather than throw");
            Assertions.assertFalse(handle.equals("not a statement"),
                    "equals must answer a foreign argument rather than throw");

            // The hash that goes with that equality: the handle's own, so it
            // occupies one slot whatever else a container holds.
            Assertions.assertEquals(Integer.valueOf(System.identityHashCode(handle)),
                    Integer.valueOf(handle.hashCode()), "hashCode must be the handle's own identity hash");

            // The pair whose membership used to be decided by insertion order:
            // the same two objects, hashing alike, made one element or two.
            Set<Statement> driverFirst = new HashSet<>();
            Assertions.assertTrue(driverFirst.add(driver));
            Assertions.assertTrue(driverFirst.add(handle),
                    "the handle must be a second element next to the statement it wraps");
            Set<Statement> handleFirst = new HashSet<>();
            Assertions.assertTrue(handleFirst.add(handle));
            Assertions.assertTrue(handleFirst.add(driver),
                    "the same two objects must make two elements in the other order as well");
            Assertions.assertEquals(2, driverFirst.size(),
                    "membership must not depend on which of the two was added first");
            Assertions.assertEquals(2, handleFirst.size(),
                    "membership must not depend on which of the two was added first");

            Set<Statement> seen = new HashSet<>();
            Assertions.assertTrue(seen.add(handle));
            Assertions.assertFalse(seen.add(handle), "the handle must occupy exactly one slot");
            Assertions.assertTrue(seen.contains(handle), "hashCode must agree with equals");

            handle.close();
            Assertions.assertEquals(0, tracked(finalizer),
                    "answering the Object methods on the handle must not cost the tracking its entry");
        }
    }

    @Test
    void aStatementClosedThroughTheUnwrappedDriverHandleLingersUntilTheReturn() throws Exception {
        DataSource ds = finalizerPool();
        ds.createPool();

        Connection pooled = ds.getConnection();
        StatementFinalizer finalizer = interceptorOf(pooled, StatementFinalizer.class);
        Assertions.assertNotNull(finalizer);

        // The result set back door stays on the handled route: the facade
        // the pool always installs answers getStatement() with the pooled
        // handle, so closing through it still passes through the tracking
        // proxy, and the entry is forgotten right there.
        Statement viaResultSet = pooled.createStatement();
        try (ResultSet rs = viaResultSet.executeQuery("SELECT 1")) {
            rs.next();
            rs.getStatement().close();
        }
        Assertions.assertEquals(0, tracked(finalizer),
                "a close through a result set's handle must still be noticed");

        // The unwrap route leaves the handled path: the driver's own
        // statement takes the close, and the tracking proxy - the one place
        // the entry is removed - never sees it. The entry cannot observe a
        // close performed through another handle, so the tracked list is
        // bounded only by the statements closed through the handle this
        // interceptor returns, and this one lingers until the return.
        Statement viaUnwrap = pooled.createStatement();
        Statement driver = driverStatement(viaUnwrap);
        driver.close();
        Assertions.assertTrue(driver.isClosed(),
                "precondition: the driver's own statement is closed");
        Assertions.assertEquals(1, tracked(finalizer),
                "a close that bypassed the handle must not be noticed as removal");

        // The return reclaims the stale entry, and the renewed close attempt
        // there meets a statement that is already closed: a harmless no-op
        // that must neither fail the return nor leave the entry behind.
        Statement trackedStatement = trackedStatement(trackedEntries(finalizer).get(0));
        pooled.close();
        Assertions.assertEquals(0, tracked(finalizer),
                "the connection return must clear the stale entry");
        Assertions.assertTrue(trackedStatement.isClosed(),
                "the statement behind the stale entry is closed all the same");
    }

    @Test
    void twoTrackingHandlesOnOneStatementDoNotCompareEqual() throws Exception {
        DataSource ds = finalizerPool();
        // Two trackers in the chain: the outer one wraps - and tracks - the
        // handle the inner one hands out, so one driver statement ends up
        // behind two distinct tracking handles.
        String tracker = StatementFinalizer.class.getName();
        ds.getPoolProperties().setJdbcInterceptors(tracker + ";" + tracker);
        ds.createPool();

        try (Connection pooled = ds.getConnection()) {
            StatementFinalizer outer = interceptorOf(pooled, StatementFinalizer.class);
            Assertions.assertNotNull(outer);
            StatementFinalizer inner = Assertions.assertInstanceOf(StatementFinalizer.class, outer.getNext(),
                    "the second tracker must sit directly beneath the first");

            PreparedStatement ps = pooled.prepareStatement("SELECT 1");
            Statement outerHandle = (Statement) proxiedObject(ps);
            Statement innerHandle = trackedStatement(trackedEntries(outer).get(0));
            Statement driver = driverStatement(ps);
            Assertions.assertNotSame(outerHandle, innerHandle,
                    "the two trackers must hand out distinct handles");
            Assertions.assertNotSame(innerHandle, driver);
            Assertions.assertEquals(1, tracked(outer), "the outer tracker must track the inner handle");
            Assertions.assertEquals(1, tracked(inner), "the inner tracker must track the driver statement");
            Assertions.assertSame(driver, trackedStatement(trackedEntries(inner).get(0)),
                    "both handles must front the very same driver statement");

            // Neither handle may absorb the other: the outer one used to
            // delegate its equality to the inner one, which made the outer
            // handle equal to the inner handle while the inner one knew
            // nothing of the outer - one more pair a set resolved by the
            // order its elements arrived in.
            Assertions.assertTrue(outerHandle.equals(outerHandle));
            Assertions.assertTrue(innerHandle.equals(innerHandle));
            Assertions.assertFalse(outerHandle.equals(innerHandle),
                    "a tracking handle must not absorb the handle beneath it");
            Assertions.assertFalse(innerHandle.equals(outerHandle),
                    "and the relation must hold in the other direction too");
            Assertions.assertFalse(outerHandle.equals(driver));
            Assertions.assertFalse(driver.equals(outerHandle));

            Set<Statement> innerFirst = new HashSet<>();
            Assertions.assertTrue(innerFirst.add(innerHandle));
            Assertions.assertTrue(innerFirst.add(outerHandle), "the two handles are two elements");
            Set<Statement> outerFirst = new HashSet<>();
            Assertions.assertTrue(outerFirst.add(outerHandle));
            Assertions.assertTrue(outerFirst.add(innerHandle), "in either order");

            // Both handles still drive the same statement, and closing the
            // handle the application holds still retires the entry at both
            // layers: the list removal keys on the entry, not on any answer
            // to equals.
            ps.close();
            Assertions.assertTrue(driver.isClosed(), "the close must still reach the driver statement");
            Assertions.assertEquals(0, tracked(outer), "the outer tracker must forget its entry");
            Assertions.assertEquals(0, tracked(inner), "the inner tracker must forget its entry");
        }
    }

    /*
     * A statement handle the test holds while pinning whether the tracker
     * ever asked its driver object to close.
     */
    private static Statement fakedStatement() {
        final AtomicBoolean closed = new AtomicBoolean();
        return (Statement) Proxy.newProxyInstance(
                TestStatementFinalizer.class.getClassLoader(),
                new Class<?>[] { Statement.class },
                (p, m, a) -> {
                    switch (m.getName()) {
                        case "close":
                            closed.set(true);
                            return null;
                        case "isClosed":
                            return Boolean.valueOf(closed.get());
                        case "equals":
                            return Boolean.valueOf(p == (a == null || a.length == 0 ? null : a[0]));
                        case "hashCode":
                            return Integer.valueOf(System.identityHashCode(p));
                        default:
                            return null;
                    }
                });
    }

    private static boolean closeWasAsked(Statement fake) {
        // The fake records the close on the object itself; re-reading
        // isClosed() through the handle is what the interceptor would see.
        try {
            return fake.isClosed();
        } catch (SQLException x) {
            throw new IllegalStateException(x);
        }
    }

    @Test
    void aStatementRegisteringAfterTheReturnHasDrainedIsClosedWhereItArrives() throws Exception {
        // The interleaving the race produces, driven at the hook level so
        // it is deterministic: the driver create has completed (the
        // statement is open in the hand of the tracker), the connection
        // return drains the list before the registration lands, and the
        // late entry must not sit on a list that no return will ever read
        // again.
        StatementFinalizer finalizer = new StatementFinalizer();
        Method create = Connection.class.getMethod("createStatement");

        finalizer.reset(null, null); // the borrow starts, creating is accepted
        finalizer.closeInvoked();    // the return drains the (empty) list

        Statement late = fakedStatement();
        Object handle = finalizer.createStatement(null, create, null, late, 0L);

        Assertions.assertTrue(closeWasAsked(late),
                "a create that raced the return must be closed where it registers - it would otherwise "
                        + "survive untracked on a session the pool hands to the next borrower");
        Assertions.assertSame(late, handle,
                "nothing was tracked, so the handle is the statement itself");
        Assertions.assertEquals(0, tracked(finalizer),
                "the closed late statement must not enter the list");

        // The next borrow re-arms the tracker: ordinary creates are tracked
        // and reclaimed at the return again.
        Statement fresh = fakedStatement();
        finalizer.reset(null, null);
        finalizer.createStatement(null, create, null, fresh, 0L);
        Assertions.assertEquals(1, tracked(finalizer), "creating must be accepted again at the next borrow");
        finalizer.closeInvoked();
        Assertions.assertTrue(closeWasAsked(fresh), "the return must still reclaim ordinary statements");
        Assertions.assertEquals(0, tracked(finalizer));
    }

    /*
     * A fake statement whose close is always refused by the driver, while
     * isClosed() honestly says it never closed.
     */
    private static Statement fakedRefusingStatement() {
        return (Statement) Proxy.newProxyInstance(
                TestStatementFinalizer.class.getClassLoader(),
                new Class<?>[] { Statement.class },
                (p, m, a) -> {
                    switch (m.getName()) {
                        case "close":
                            throw new SQLException("driver refuses to close");
                        case "isClosed":
                            return Boolean.FALSE;
                        case "equals":
                            return Boolean.valueOf(p == (a == null || a.length == 0 ? null : a[0]));
                        case "hashCode":
                            return Integer.valueOf(System.identityHashCode(p));
                        default:
                            return null;
                    }
                });
    }

    /* A fake statement whose close fails with a non-fatal Error. */
    private static Statement fakedErrorStatement() {
        return (Statement) Proxy.newProxyInstance(
                TestStatementFinalizer.class.getClassLoader(),
                new Class<?>[] { Statement.class },
                (p, m, a) -> {
                    switch (m.getName()) {
                        case "close":
                            throw new NoClassDefFoundError("simulated stopped context in close()");
                        case "isClosed":
                            return Boolean.FALSE;
                        case "equals":
                            return Boolean.valueOf(p == (a == null || a.length == 0 ? null : a[0]));
                        case "hashCode":
                            return Integer.valueOf(System.identityHashCode(p));
                        default:
                            return null;
                    }
                });
    }

    @Test
    void anErrorFromOneCloseDoesNotAbortTheDrain() throws Exception {
        StatementFinalizer finalizer = new StatementFinalizer();
        Method create = Connection.class.getMethod("createStatement");
        finalizer.reset(null, null);

        Statement failing = fakedErrorStatement();
        Statement following = fakedStatement();
        finalizer.createStatement(null, create, null, failing, 0L);
        finalizer.createStatement(null, create, null, following, 0L);

        Assertions.assertDoesNotThrow(finalizer::closeInvoked,
                "a non-fatal Error from one statement close must not escape the drain");
        Assertions.assertTrue(closeWasAsked(following),
                "the statement after the failing one must still be closed");
        Assertions.assertEquals(1, tracked(finalizer),
                "the entry whose close failed must stay on the list for the next attempt");
    }

    @Test
    void aCloseTheDriverRefusedStaysOnTheListForANextAttempt() throws Exception {
        // A return-time close the driver refused must not be dropped: the
        // session goes back to the pool and the statement would sit open on
        // it with no later attempt, contradicting what the proxy does for a
        // close that fails through the application's handle (keep the entry,
        // let the return retry).
        StatementFinalizer finalizer = new StatementFinalizer();
        Method create = Connection.class.getMethod("createStatement");

        finalizer.reset(null, null);
        Statement refusing = fakedRefusingStatement();
        finalizer.createStatement(null, create, null, refusing, 0L);

        finalizer.closeInvoked(); // the return tries, and the driver refuses
        Assertions.assertEquals(1, tracked(finalizer),
                "a close that failed must stay on the list - the next borrow's reset makes another attempt");

        // A teardown carries no live session: the physical close that runs
        // before the teardown reset has released the statement, so the reset
        // drops the list without another attempt.
        finalizer.reset(null, null);
        Assertions.assertEquals(0, tracked(finalizer),
                "a teardown drops the list; the statements die with the destroyed session");
    }

    /*
     * A statement from a driver that cannot answer whether it is closed:
     * isClosed() always throws, while an attempted close is taken.
     */
    private static Statement fakedDriverThatCannotSay(AtomicBoolean closeWasTaken) {
        return (Statement) Proxy.newProxyInstance(
                TestStatementFinalizer.class.getClassLoader(),
                new Class<?>[] { Statement.class },
                (p, m, a) -> {
                    switch (m.getName()) {
                        case "close":
                            closeWasTaken.set(true);
                            return null;
                        case "isClosed":
                            throw new SQLException("this driver cannot say");
                        case "equals":
                            return Boolean.valueOf(p == (a == null || a.length == 0 ? null : a[0]));
                        case "hashCode":
                            return Integer.valueOf(System.identityHashCode(p));
                        default:
                            return null;
                    }
                });
    }

    @Test
    void aRetryCloseFailureDuringTheNextBorrowStaysOnTheList() throws Exception {
        DataSource ds = finalizerPool();
        ds.createPool();
        try (Connection pooled = ds.getConnection()) {
            StatementFinalizer finalizer = interceptorOf(pooled, StatementFinalizer.class);
            PooledConnection pcon = internalOf(pooled);
            Method create = Connection.class.getMethod("createStatement");

            finalizer.reset(ds.getPool(), pcon);
            Statement refusing = fakedRefusingStatement();
            finalizer.createStatement(null, create, null, refusing, 0L);
            finalizer.closeInvoked();
            Assertions.assertEquals(1, tracked(finalizer),
                    "precondition: the return-time close failed and kept the entry");

            // The next borrow retries, and the driver refuses again. The entry
            // must survive for the attempt after that; the statement may still
            // be open on the session the new borrower is using.
            finalizer.reset(ds.getPool(), pcon);
            Assertions.assertEquals(1, tracked(finalizer),
                    "a failed retry close must stay on the list instead of being dropped");
        }
    }

    @Test
    void traceReportsOnlyWhatTheDriverCouldAnswer() throws Exception {
        // With trace on, the interceptor tells the operator which statements
        // the application left open. A driver that cannot answer isClosed()
        // must still be forced through the close - a statement that may hold
        // server-side state must not be skipped - but that forced attempt is
        // no evidence that the application leaked the statement: one that was
        // already closed takes the extra close as the no-op JDBC demands of
        // it. So the report keeps its silence on a driver that could not
        // answer, and has to keep speaking where the driver did.
        StatementFinalizer finalizer = new StatementFinalizer();
        Map<String, PoolProperties.InterceptorProperty> properties = new HashMap<>();
        properties.put("trace", new PoolProperties.InterceptorProperty("trace", "true"));
        finalizer.setProperties(properties);

        Method create = Connection.class.getMethod("createStatement");
        finalizer.reset(null, null);
        Statement leftOpen = fakedStatement(); // answers honestly, and is left open
        AtomicBoolean blindCloseTaken = new AtomicBoolean();
        Statement muteAboutClosure = fakedDriverThatCannotSay(blindCloseTaken);
        finalizer.createStatement(null, create, null, leftOpen, 0L);
        finalizer.createStatement(null, create, null, muteAboutClosure, 0L);

        try (NotClosedReports reports = new NotClosedReports()) {
            finalizer.closeInvoked();
            List<LogRecord> seen = reports.seen();
            Assertions.assertEquals(1, seen.size(),
                    "only the statement the driver admitted leaving open may be reported, saw: " +
                            seen.stream().map(LogRecord::getMessage).collect(Collectors.toList()));
            LogRecord report = seen.get(0);
            Assertions.assertEquals(Level.WARNING.intValue(), report.getLevel().intValue(),
                    "the report is a warning, not something quieter: " + report.getMessage());
            Assertions.assertNotNull(report.getThrown(),
                    "the report has to name where the statement was created");
            Assertions.assertTrue(blindCloseTaken.get(),
                    "the driver that could not answer must still have been asked to close");
        }
    }

    /**
     * Captures the unclosed-statement reports this interceptor logs,
     * following the handler idiom of the pool's other diagnostic tests. The
     * canary record - written through the very JULI logger the interceptor
     * logs through, not through JUL directly - proves the wiring observes
     * those records before the finalizer under test gets its chance to log.
     */
    private static final class NotClosedReports implements AutoCloseable {
        private static final String CANARY = "statement-finalizer-trace-handler-wiring-canary";
        private static final String REPORT = "was not closed";

        private final Logger logger = Logger.getLogger(StatementFinalizer.class.getName());
        private final Level originalLevel = logger.getLevel();
        private final List<LogRecord> records = new ArrayList<>();
        private final Handler handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                synchronized (records) {
                    records.add(record);
                }
            }

            @Override
            public void flush() {
                // no buffering
            }

            @Override
            public void close() {
                // nothing to release
            }
        };

        NotClosedReports() {
            logger.addHandler(handler);
            logger.setLevel(Level.ALL);
            LogFactory.getLog(StatementFinalizer.class).error(CANARY);
            synchronized (records) {
                if (records.stream().noneMatch(r -> CANARY.equals(r.getMessage()))) {
                    close();
                    throw new AssertionError("the test handler must observe what this class logs");
                }
            }
        }

        /* The unclosed-statement reports seen so far; the canary is not one. */
        List<LogRecord> seen() {
            synchronized (records) {
                return records.stream()
                        .filter(r -> r.getMessage() != null && r.getMessage().contains(REPORT))
                        .collect(Collectors.toList());
            }
        }

        @Override
        public void close() {
            logger.removeHandler(handler);
            logger.setLevel(originalLevel);
        }
    }

    private DataSource finalizerPoolGated() {
        DataSource ds = finalizerPool();
        ds.getPoolProperties().setJdbcInterceptors(StatementFinalizer.class.getName() + ';'
                + StatementGateInterceptor.class.getName());
        ds.getPoolProperties().setMaxActive(1);
        ds.getPoolProperties().setMaxIdle(1);
        return ds;
    }

    @Test
    void aFailedReturnTimeCloseIsRetriedBeforeTheNextBorrowerUsesTheSession() throws Exception {
        // A driver close that fails once at the return - a transient failure
        // is the realistic case - is retried when the session is next
        // borrowed, before that borrower can inherit the open statement.
        DataSource ds = finalizerPoolGated();
        ds.createPool();

        Connection first = ds.getConnection();
        setSessionMarker(first, 21);
        StatementFinalizer finalizer = interceptorOf(first, StatementFinalizer.class);
        Assertions.assertNotNull(finalizer);

        StatementGateInterceptor.failFirstClose();
        first.createStatement(); // its first close is refused
        Statement rawLeaked = StatementGateInterceptor.lastRaw;
        StatementGateInterceptor.disarm();

        first.close(); // the return tries the close once; the failure must not fail the return
        Assertions.assertEquals(1, tracked(finalizer),
                "the refused close must be kept for another attempt");
        Assertions.assertFalse(rawLeaked.isClosed(),
                "precondition: the refused close left the driver statement open");

        Connection second = ds.getConnection();
        try {
            Assertions.assertEquals(Integer.valueOf(21), sessionMarker(second),
                    "precondition: the same physical session, same interceptor instance");
            Assertions.assertSame(finalizer, interceptorOf(second, StatementFinalizer.class));
            Assertions.assertTrue(rawLeaked.isClosed(),
                    "the retry at the borrow must have closed the driver statement before this "
                            + "borrower runs");
            Assertions.assertEquals(0, tracked(finalizer),
                    "the successful retry leaves nothing behind");
        } finally {
            second.close();
        }
    }

    @Test
    void aCloseThatNeverSucceedsKeepsItsEntryForALaterAttempt() throws Exception {
        // A driver that cannot close at all must not lose the entry: the
        // statement may still be open on the session the next borrower is
        // using, so the failed retry at the borrow keeps it for the return
        // path, which retries on every return as well. One leaked statement
        // produces one entry, not one per borrow.
        DataSource ds = finalizerPoolGated();
        ds.createPool();

        Connection first = ds.getConnection();
        StatementFinalizer finalizer = interceptorOf(first, StatementFinalizer.class);
        Assertions.assertNotNull(finalizer);

        StatementGateInterceptor.failCloses();
        first.createStatement(); // every close of it is refused
        Statement rawLeaked = StatementGateInterceptor.lastRaw;
        StatementGateInterceptor.disarm();

        first.close();
        Assertions.assertEquals(1, tracked(finalizer), "the refused close waits for the retry");
        Assertions.assertFalse(rawLeaked.isClosed(),
                "precondition: the refused close left the driver statement open");

        Connection second = ds.getConnection();
        try {
            Assertions.assertEquals(1, tracked(finalizer),
                    "a failed retry at the borrow must keep the entry: the statement may still be "
                            + "open on the session the new borrower is using");
            Assertions.assertFalse(rawLeaked.isClosed(), "precondition: the driver never took the close");
        } finally {
            second.close();
        }
        Assertions.assertEquals(1, tracked(finalizer),
                "the return-time retry also fails, so the entry stays for the next attempt");
    }

    @Test
    void aCreateStillInFlightWhileTheConnectionIsReturnedDoesNotReachTheNextBorrower() throws Exception {
        // The same interleaving through the whole chain and a real driver:
        // a leaked statement parks the return inside the close that the
        // tracker itself issues, and while the return is held there - after
        // its drain, before the session is handed back - another thread's
        // in-flight create lands.
        DataSource ds = finalizerPool();
        ds.getPoolProperties().setJdbcInterceptors(StatementFinalizer.class.getName() + ';'
                + StatementGateInterceptor.class.getName());
        ds.getPoolProperties().setMaxActive(1);
        ds.getPoolProperties().setMaxIdle(1);
        ds.createPool();

        Connection first = ds.getConnection();
        setSessionMarker(first, 11);
        StatementFinalizer finalizer = interceptorOf(first, StatementFinalizer.class);
        Assertions.assertNotNull(finalizer);

        StatementGateInterceptor.holdFirstClose();
        @SuppressWarnings("unused")
        Statement leaked = first.createStatement(); // its return-time close is the parking post

        CountDownLatch createEntered = new CountDownLatch(1);
        CountDownLatch createRelease = new CountDownLatch(1);
        StatementGateInterceptor.holdCreateOf("in-flight-prepare", createEntered, createRelease);

        final AtomicReference<PreparedStatement> holder = new AtomicReference<>();
        final AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread creator = new Thread(() -> {
            try {
                holder.set(first.prepareStatement("SELECT 42"));
            } catch (Throwable x) {
                failure.set(x);
            }
        }, "in-flight-prepare");

        final AtomicReference<Throwable> closeFailure = new AtomicReference<>();
        CountDownLatch closeParked = new CountDownLatch(1);
        CountDownLatch closeRelease = new CountDownLatch(1);
        StatementGateInterceptor.parkFirstCloseOnReturn(closeParked, closeRelease);
        Thread closer = new Thread(() -> {
            try {
                first.close();
            } catch (Throwable x) {
                closeFailure.set(x);
            }
        }, "returning-thread");

        creator.start();
        Assertions.assertTrue(createEntered.await(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS),
                "the prepare is in flight inside the chain");
        closer.start();
        Assertions.assertTrue(closeParked.await(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS),
                "the return is parked inside the tracker's close of the leaked statement");

        createRelease.countDown();
        creator.join(AWAIT_TIMEOUT_MS);
        Assertions.assertFalse(creator.isAlive(), "the prepare completed within the timeout");
        Assertions.assertNull(failure.get(), "the in-flight create must not fail: " + failure.get());
        Assertions.assertNotNull(holder.get(), "precondition: the late create completed");
        Statement rawLate = StatementGateInterceptor.lastRaw;
        Assertions.assertNotNull(rawLate, "precondition: the late create reached the driver");
        Assertions.assertEquals(0, tracked(finalizer),
                "the create that landed behind the drain must not sit on the tracker - no later return "
                        + "will ever read it");

        closeRelease.countDown();
        closer.join(AWAIT_TIMEOUT_MS);
        Assertions.assertFalse(closer.isAlive(), "the return completed within the timeout");
        Assertions.assertNull(closeFailure.get(), "the return must not fail: " + closeFailure.get());
        StatementGateInterceptor.disarm();

        Assertions.assertTrue(rawLate.isClosed(),
                "the statement that raced the return must be closed where it registers, not left "
                        + "open on the session that goes back to the pool");
        Assertions.assertEquals(0, tracked(finalizer),
                "nothing may be left on the tracker after the return");

        Connection second = ds.getConnection();
        try {
            Assertions.assertEquals(Integer.valueOf(11), sessionMarker(second),
                    "precondition: the same physical session serves the next borrower");
            Assertions.assertEquals(0, tracked(interceptorOf(second, StatementFinalizer.class)),
                    "the next borrow must start with an empty tracker");
            try (ResultSet rs = ((PreparedStatement) rawLate).executeQuery()) {
                Assertions.fail("the statement that raced the return still executes: "
                        + (rs.next() ? Integer.valueOf(rs.getInt(1)) : "?"));
            } catch (SQLException expected) {
                // a closed statement refuses use - the point of the assertion
            }
        } finally {
            second.close();
        }
    }

    /**
     * Statement gate for the race test: wraps every statement the chain
     * creates so the test can park a close issued from inside the pool
     * (the tracker's own return-time close), and can hold one named
     * thread's statement creation in flight while the return is parked.
     * Armed through statics because the pool instantiates configured
     * interceptors itself; hence public static with a no-arg constructor.
     */
    public static class StatementGateInterceptor extends JdbcInterceptor {

        private static final AtomicBoolean holdFirstCloseArmed = new AtomicBoolean();
        private static volatile CountDownLatch closeParked;
        private static volatile CountDownLatch closeRelease;
        private static final AtomicBoolean closeHoldTaken = new AtomicBoolean();
        private static final AtomicBoolean failFirstCloseArmed = new AtomicBoolean();
        private static final AtomicBoolean firstFailTaken = new AtomicBoolean();
        private static volatile boolean failClosesArmed;
        private static volatile String createHoldThread;
        private static volatile CountDownLatch createEntered;
        private static volatile CountDownLatch createRelease;
        /* The driver's own statement behind the most recently wrapped one. */
        static volatile Statement lastRaw;

        static void holdFirstClose() {
            closeHoldTaken.set(false);
            holdFirstCloseArmed.set(true);
        }

        static void failFirstClose() {
            firstFailTaken.set(false);
            failFirstCloseArmed.set(true);
        }

        static void failCloses() {
            failClosesArmed = true;
        }

        static void parkFirstCloseOnReturn(CountDownLatch parked, CountDownLatch release) {
            closeParked = parked;
            closeRelease = release;
        }

        static void holdCreateOf(String threadName, CountDownLatch entered, CountDownLatch release) {
            createHoldThread = threadName;
            createEntered = entered;
            createRelease = release;
        }

        static void disarm() {
            closeParked = null;
            closeRelease = null;
            createHoldThread = null;
            createEntered = null;
            createRelease = null;
            holdFirstCloseArmed.set(false);
            closeHoldTaken.set(false);
            failFirstCloseArmed.set(false);
            firstFailTaken.set(false);
            failClosesArmed = false;
        }

        @Override
        public void reset(ConnectionPool parent, PooledConnection con) {
            // no per-borrow state
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String name = method.getName();
            boolean isCreate = "createStatement".equals(name) || "prepareStatement".equals(name)
                    || "prepareCall".equals(name);
            CountDownLatch entered = createEntered;
            if (isCreate && entered != null && Thread.currentThread().getName().equals(createHoldThread)) {
                entered.countDown();
                CountDownLatch release = createRelease;
                if (release != null) {
                    release.await(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                }
            }
            Object result = super.invoke(proxy, method, args);
            if (isCreate && result instanceof Statement) {
                boolean parkItsClose = holdFirstCloseArmed.get() && closeHoldTaken.compareAndSet(false, true);
                boolean failItsFirstClose = failFirstCloseArmed.get() && firstFailTaken.compareAndSet(false, true);
                boolean failItsCloses = failClosesArmed;
                Statement created = (Statement) result;
                lastRaw = created;
                Class<?> iface = created instanceof CallableStatement ? CallableStatement.class
                        : created instanceof PreparedStatement ? PreparedStatement.class : Statement.class;
                return Proxy.newProxyInstance(StatementGateInterceptor.class.getClassLoader(),
                        new Class<?>[] { iface },
                        new StatementGate(created, parkItsClose, failItsFirstClose, failItsCloses));
            }
            return result;
        }

        static class StatementGate implements InvocationHandler {
            private final Statement delegate;
            private final boolean parkClose;
            private final boolean failFirstClose;
            private final boolean failCloses;
            private final AtomicBoolean closeParkedOnce = new AtomicBoolean();
            private final AtomicBoolean closeFailedOnce = new AtomicBoolean();

            StatementGate(Statement delegate, boolean parkClose, boolean failFirstClose, boolean failCloses) {
                this.delegate = delegate;
                this.parkClose = parkClose;
                this.failFirstClose = failFirstClose;
                this.failCloses = failCloses;
            }

            @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                if ("close".equals(method.getName())) {
                    if (failCloses) {
                        throw new SQLException("gate refuses to close the statement");
                    }
                    if (failFirstClose && closeFailedOnce.compareAndSet(false, true)) {
                        throw new SQLException("gate refuses the first close of the statement");
                    }
                    if (parkClose && closeParkedOnce.compareAndSet(false, true)) {
                        CountDownLatch parked = closeParked;
                        CountDownLatch release = closeRelease;
                        if (parked != null) {
                            parked.countDown();
                            if (release != null) {
                                release.await(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                            }
                        }
                    }
                }
                try {
                    return method.invoke(delegate, args);
                } catch (InvocationTargetException x) {
                    throw x.getCause() != null ? x.getCause() : x;
                }
            }
        }
    }
}
