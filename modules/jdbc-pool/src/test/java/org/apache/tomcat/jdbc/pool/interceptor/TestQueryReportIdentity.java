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

import java.lang.reflect.UndeclaredThrowableException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;
import org.apache.tomcat.jdbc.pool.DataSource;

/**
 * Object-method contract of the statement handles that the query report
 * interceptors - {@link SlowQueryReport} and {@link ResetAbandonedTimer},
 * both built on {@link AbstractQueryReport} - hand out. A handle is an
 * object like any other: it must be equal to itself and to nothing else,
 * its hash code must be a stable value of its own, {@code toString} must
 * describe it, and all three answers must survive the close of the handle -
 * a logging statement, an assertion message or a map lookup must not blow up
 * just because the work is done. Before this contract was honoured, the
 * proxies forwarded the {@code Object} methods to the driver object
 * underneath: the handle was not equal to itself, it claimed equality with
 * the raw statement it wrapped, and after close there was no delegate left
 * to answer, so every one of these methods surfaced as an
 * {@link UndeclaredThrowableException} wrapping "Statement closed.".
 *
 * <p>With the (default) statement facade enabled it sits outermost and
 * answers these methods itself, which masks the gap; these tests pin the
 * unfacaded configuration - {@code useStatementFacade=false} is a documented
 * configuration - and that both configurations answer identically.</p>
 */
class TestQueryReportIdentity extends JdbcPoolBaseTest {

    private DataSource queryReportPool(String interceptors, boolean useStatementFacade)
            throws SQLException {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setUseStatementFacade(useStatementFacade);
        ds.getPoolProperties().setJdbcInterceptors(interceptors);
        ds.createPool();
        return ds;
    }

    private DataSource slowQueryPool(boolean useStatementFacade) throws SQLException {
        return queryReportPool(SlowQueryReport.class.getSimpleName(), useStatementFacade);
    }

    @Test
    void aQueryReportStatementHandleIsEqualOnlyToItselfWhileOpen() throws Exception {
        DataSource ds = slowQueryPool(false);
        try (Connection pooled = ds.getConnection()) {
            try (Statement st = pooled.createStatement()) {
                Assertions.assertTrue(st.equals(st),
                        "a statement handle must be equal to itself");
                Assertions.assertFalse(st.equals(null),
                        "a statement handle is not equal to null");
                Assertions.assertFalse(st.equals(pooled),
                        "a statement handle is not equal to its connection");
                Assertions.assertFalse(st.equals(new Object()),
                        "a statement handle is not equal to a foreign object");
                // the driver object underneath is not the handle: an equality
                // the driver answers is the driver's own identity, not the
                // identity of the handle the application holds
                Assertions.assertFalse(st.equals(driverStatement(st)),
                        "a statement handle is not equal to the driver object below it");
                try (Statement other = pooled.createStatement()) {
                    Assertions.assertFalse(st.equals(other),
                            "two statements are two handles");
                    Assertions.assertFalse(other.equals(st));
                }
                int hash = st.hashCode();
                Assertions.assertEquals(hash, st.hashCode(),
                        "hashCode must be a stable value of the handle itself");
                Set<Statement> seen = new HashSet<>();
                seen.add(st);
                Assertions.assertTrue(seen.contains(st),
                        "hashCode must agree with equals");
            }
            try (PreparedStatement pst = pooled.prepareStatement("SELECT 1")) {
                Assertions.assertTrue(pst.equals(pst),
                        "a prepared statement handle must be equal to itself");
                Assertions.assertFalse(pst.equals(pooled.prepareStatement("SELECT 1")),
                        "two prepared statements are two handles");
                Assertions.assertEquals(pst.hashCode(), pst.hashCode(),
                        "hashCode must be a stable value of the handle itself");
            }
        }
    }

    @Test
    void aClosedQueryReportHandleKeepsAnsweringWithItsOwnIdentity() throws Exception {
        DataSource ds = slowQueryPool(false);
        Connection pooled = ds.getConnection();
        try {
            Statement st = pooled.createStatement();
            int hash = st.hashCode();
            Map<Statement, String> byHandle = new HashMap<>();
            byHandle.put(st, "the handle");
            st.close();

            // None of these three methods declares a checked exception, so a
            // throwing handler cannot reach the application as a SQLException
            // - the proxy machinery wraps it. All three answers must simply
            // be values.
            Assertions.assertDoesNotThrow(st::hashCode,
                    "hashCode on a closed handle must return, not throw");
            Assertions.assertEquals(hash, st.hashCode(),
                    "closing must not change the identity of a handle");
            Assertions.assertTrue(st.equals(st),
                    "closing must not break reflexivity of a handle");
            Assertions.assertFalse(st.equals(pooled),
                    "a closed handle is still equal to nothing but itself");
            Assertions.assertDoesNotThrow(() -> String.valueOf(st),
                    "toString on a closed handle must return, not throw");
            // the shapes that actually reach the defect in practice: hashing a
            // stale handle for a collection, and stringifying one for a log
            // line or an assertion message
            try {
                Assertions.assertEquals("the handle", byHandle.get(st),
                        "a closed handle must still find its entry in a map");
                Assertions.assertEquals(hash, st.hashCode());
                Assertions.assertTrue(String.valueOf(st).contains("Query="));
            } catch (UndeclaredThrowableException e) {
                Assertions.fail("a closed statement handle must not surface "
                        + "UndeclaredThrowableException: " + e.getCause());
            }
        } finally {
            pooled.close();
        }
    }

    @Test
    void aQueryReportHandleDescribesItselfRatherThanTheDriverBelowIt() throws Exception {
        DataSource ds = slowQueryPool(false);
        try (Connection pooled = ds.getConnection()) {
            PreparedStatement pst = pooled.prepareStatement("SELECT 1");
            String open = Assertions.assertDoesNotThrow(pst::toString,
                    "toString on an open handle must return, not throw");
            Assertions.assertTrue(open.contains(SlowQueryReport.class.getName()),
                    "the handle must say which interceptor produced it, was: " + open);
            Assertions.assertTrue(open.contains("SELECT 1"),
                    "the handle must name its query, was: " + open);

            pst.close();
            String closed = Assertions.assertDoesNotThrow(pst::toString,
                    "toString on a closed handle must return, not throw");
            Assertions.assertTrue(closed.contains("SELECT 1"),
                    "a closed handle must still name its query, was: " + closed);
            // the delegate is gone, and toString must not need it
            Assertions.assertTrue(closed.contains("Delegate=null"),
                    "a closed handle must say so, was: " + closed);
        }
    }

    /*
     * Answering the Object methods on the proxy must not soften the closed
     * contract for the methods that do declare the failure.
     */
    @Test
    void aClosedQueryReportHandleStillRefusesTheWorkItWasCreatedFor() throws Exception {
        DataSource ds = slowQueryPool(false);
        Connection pooled = ds.getConnection();
        try {
            Statement st = pooled.createStatement();
            st.close();

            SQLException onExecute = Assertions.assertThrows(SQLException.class,
                    () -> st.execute("SELECT 1"));
            Assertions.assertTrue(onExecute.getMessage().contains("closed"),
                    "the closed contract still reports a closed statement: "
                            + onExecute.getMessage());
            Assertions.assertTrue(st.isClosed(),
                    "isClosed must report the closed handle");
            Assertions.assertThrows(SQLException.class, st::getConnection,
                    "a closed handle still reports its connection as gone");
            Assertions.assertThrows(SQLException.class, () -> st.unwrap(Statement.class),
                    "unwrap on a closed handle still fails");
            Assertions.assertThrows(SQLException.class, () -> st.isWrapperFor(Statement.class),
                    "isWrapperFor on a closed handle still fails");
            Assertions.assertThrows(SQLException.class, () -> st.setFetchSize(1),
                    "a business method on a closed handle still fails");
        } finally {
            pooled.close();
        }
    }

    /*
     * The facade absorbs the Object methods from outside the interceptor, and
     * so does the abandoned timer's own proxy; neither configuration and
     * neither interceptor may change an answer the application gets.
     */
    @Test
    void everyConfigurationAnswersTheSameWay() throws Exception {
        String[] interceptors = { SlowQueryReport.class.getSimpleName(),
                ResetAbandonedTimer.class.getSimpleName() };
        for (String interceptor : interceptors) {
            for (boolean facade : new boolean[] { false, true }) {
                String config = interceptor + " " + (facade ? "with" : "without");
                DataSource ds = queryReportPool(interceptor, facade);
                try (Connection pooled = ds.getConnection()) {
                    Statement st = pooled.createStatement();
                    try (PreparedStatement pst = pooled.prepareStatement("SELECT 1")) {
                        Assertions.assertTrue(pst.equals(pst),
                                config + " the facade a prepared handle is equal to itself");
                        Assertions.assertFalse(pst.equals(st),
                                config + " the facade two statements are two handles");
                    }

                    Assertions.assertTrue(st.equals(st),
                            config + " the facade a statement handle is equal to itself");
                    Assertions.assertFalse(st.equals(null),
                            config + " the facade equals(null) is false");
                    int hash = st.hashCode();
                    Map<Statement, String> byHandle = new HashMap<>();
                    byHandle.put(st, "the handle");

                    st.close();

                    Assertions.assertTrue(st.equals(st),
                            config + " the facade a closed handle stays equal to itself");
                    Assertions.assertFalse(st.equals(pooled),
                            config + " the facade a closed handle is equal to nothing else");
                    Assertions.assertEquals(hash, st.hashCode(),
                            config + " the facade the hash survives the close");
                    Assertions.assertEquals("the handle", byHandle.get(st),
                            config + " the facade a closed handle still hashes into its map");
                    Assertions.assertNotNull(String.valueOf(st),
                            config + " the facade the closed handle still describes itself");
                }
            }
        }
    }
}
