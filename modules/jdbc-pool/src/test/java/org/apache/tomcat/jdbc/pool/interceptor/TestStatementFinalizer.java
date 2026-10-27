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
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;
import org.apache.tomcat.jdbc.pool.DataSource;

/**
 * What {@code StatementFinalizer} owes the application from the other
 * side of the coin: statements the application <em>did</em> close must be
 * forgotten the moment they are closed (the tracked list is bounded by
 * genuinely open statements, not by everything the borrow ever created),
 * and statements it <em>did not</em> close must survive garbage collection
 * so they can still be closed when the connection is returned.
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
        // Without the statement facade the tracker's wrapper is the first
        // proxy in front of the driver's own statement object, so it is the
        // handle the application compares, closes and reuses.
        ds.getPoolProperties().setUseStatementFacade(false);
        ds.createPool();

        try (Connection pooled = ds.getConnection()) {
            StatementFinalizer finalizer = interceptorOf(pooled, StatementFinalizer.class);
            Assertions.assertNotNull(finalizer);

            PreparedStatement ps = pooled.prepareStatement("SELECT 1");
            Assertions.assertTrue(ps.equals(ps), "the handle must be equal to itself");
            Assertions.assertFalse(ps.equals(pooled), "an unrelated object must not compare equal");
            java.util.Set<Statement> seen = new java.util.HashSet<>();
            seen.add(ps);
            Assertions.assertTrue(seen.contains(ps), "hashCode must agree with equals");
            try (ResultSet rs = ps.executeQuery()) {
                Assertions.assertTrue(rs.next());
            }

            CallableStatement call = pooled.prepareCall("CALL NOW()");
            try (ResultSet rs = call.executeQuery()) {
                Assertions.assertTrue(rs.next());
            }
            Assertions.assertEquals(2, tracked(finalizer),
                    "the callable statement must be tracked as well");

            ps.close();
            ps.close(); // JDBC demands that a repeated close is a no-op
            Assertions.assertEquals(1, tracked(finalizer), "the close must have been counted once");

            Assertions.assertThrows(SQLException.class, ps::getMoreResults,
                    "use after close must still be reported, not swallowed");
            call.close();
            Assertions.assertEquals(0, tracked(finalizer));
        }
    }
}
