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
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;
import org.apache.tomcat.jdbc.pool.DataSource;

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
}
