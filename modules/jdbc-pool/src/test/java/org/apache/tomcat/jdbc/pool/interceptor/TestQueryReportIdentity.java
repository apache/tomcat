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
 * <p>The pool always installs its statement facade outermost (the
 * {@code useStatementFacade} property that used to switch it off is
 * deprecated and has no effect), and the facade answers these methods on the
 * handle the application holds. The interceptor's own proxies stay live
 * inner layers under the facade's handles, so the tests reach them through
 * the facade's delegate and pin the contract at both layers.</p>
 */
class TestQueryReportIdentity extends JdbcPoolBaseTest {

    private DataSource queryReportPool(String interceptors) throws SQLException {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setJdbcInterceptors(interceptors);
        ds.createPool();
        return ds;
    }

    private DataSource slowQueryPool() throws SQLException {
        return queryReportPool(SlowQueryReport.class.getSimpleName());
    }

    /* The interceptor's own statement handle behind the facade's. */
    private static Statement statementBehindFacade(Statement facadeHandle)
            throws ReflectiveOperationException {
        return (Statement) proxiedObject(facadeHandle);
    }

    /* The contract of an open statement handle, for one layer. */
    private static void assertOpenStatementHandle(Statement handle,
            Connection pooled, String layer) throws SQLException {
        Assertions.assertTrue(handle.equals(handle),
                layer + ": a statement handle must be equal to itself");
        Assertions.assertFalse(handle.equals(null),
                layer + ": a statement handle is not equal to null");
        Assertions.assertFalse(handle.equals(pooled),
                layer + ": a statement handle is not equal to its connection");
        Assertions.assertFalse(handle.equals(new Object()),
                layer + ": a statement handle is not equal to a foreign object");
        // the driver object underneath is not the handle: an equality the
        // driver answers is the driver's own identity, not the identity of
        // the handle above it
        Assertions.assertFalse(handle.equals(driverStatement(handle)),
                layer + ": a statement handle is not equal to the driver object below it");
        int hash = handle.hashCode();
        Assertions.assertEquals(hash, handle.hashCode(),
                layer + ": hashCode must be a stable value of the handle itself");
        Set<Statement> seen = new HashSet<>();
        seen.add(handle);
        Assertions.assertTrue(seen.contains(handle),
                layer + ": hashCode must agree with equals");
    }

    @Test
    void aQueryReportStatementHandleIsEqualOnlyToItselfWhileOpen() throws Exception {
        DataSource ds = slowQueryPool();
        try (Connection pooled = ds.getConnection()) {
            try (Statement st = pooled.createStatement()) {
                assertOpenStatementHandle(st, pooled, "the statement facade");
                assertOpenStatementHandle(statementBehindFacade(st), pooled,
                        "the query report proxy");
                try (Statement other = pooled.createStatement()) {
                    Assertions.assertFalse(st.equals(other),
                            "two statements are two handles at the facade layer");
                    Assertions.assertFalse(other.equals(st));
                    Assertions.assertFalse(statementBehindFacade(st).equals(other),
                            "and the proxy of one statement is not the handle of another");
                    Assertions.assertFalse(statementBehindFacade(st)
                            .equals(statementBehindFacade(other)),
                            "two statements are two handles at the interceptor layer");
                }
            }
            try (PreparedStatement pst = pooled.prepareStatement("SELECT 1")) {
                Assertions.assertTrue(pst.equals(pst),
                        "a prepared statement handle must be equal to itself");
                Assertions.assertFalse(pst.equals(pooled.prepareStatement("SELECT 1")),
                        "two prepared statements are two handles");
                Assertions.assertEquals(pst.hashCode(), pst.hashCode(),
                        "hashCode must be a stable value of the handle itself");
                Statement inner = statementBehindFacade(pst);
                Assertions.assertTrue(inner.equals(inner),
                        "the interceptor's prepared statement proxy is equal to itself");
            }
        }
    }

    @Test
    void aClosedQueryReportHandleKeepsAnsweringWithItsOwnIdentity() throws Exception {
        DataSource ds = slowQueryPool();
        try (Connection pooled = ds.getConnection()) {
            Statement st = pooled.createStatement();
            Statement reportHandle = statementBehindFacade(st);
            int facadeHash = st.hashCode();
            int reportHash = reportHandle.hashCode();
            Map<Statement, String> byFacadeHandle = new HashMap<>();
            byFacadeHandle.put(st, "the handle");
            Map<Statement, String> byReportHandle = new HashMap<>();
            byReportHandle.put(reportHandle, "the handle");
            st.close();

            for (int layer = 0; layer < 2; layer++) {
                String name = layer == 0 ? "the statement facade"
                        : "the query report proxy";
                Statement handle = layer == 0 ? st : reportHandle;
                int hash = layer == 0 ? facadeHash : reportHash;
                Map<Statement, String> byHandle = layer == 0 ? byFacadeHandle : byReportHandle;

                // None of these three methods declares a checked exception,
                // so a throwing handler cannot reach the application as a
                // SQLException - the proxy machinery wraps it. All three
                // answers must simply be values.
                Assertions.assertDoesNotThrow(handle::hashCode,
                        name + ": hashCode on a closed handle must return, not throw");
                Assertions.assertEquals(hash, handle.hashCode(),
                        name + ": closing must not change the identity of a handle");
                Assertions.assertTrue(handle.equals(handle),
                        name + ": closing must not break reflexivity of a handle");
                Assertions.assertFalse(handle.equals(pooled),
                        name + ": a closed handle is still equal to nothing but itself");
                Assertions.assertDoesNotThrow(() -> String.valueOf(handle),
                        name + ": toString on a closed handle must return, not throw");
                // the shapes that actually reach the defect in practice:
                // hashing a stale handle for a collection, and stringifying
                // one for a log line or an assertion message
                try {
                    Assertions.assertEquals("the handle", byHandle.get(handle),
                            name + ": a closed handle must still find its entry in a map");
                    Assertions.assertEquals(hash, handle.hashCode());
                    Assertions.assertTrue(String.valueOf(handle).contains("Query="));
                } catch (UndeclaredThrowableException e) {
                    Assertions.fail(name + ": a closed statement handle must not surface "
                            + "UndeclaredThrowableException: " + e.getCause());
                }
            }
        }
    }

    @Test
    void aQueryReportHandleDescribesItselfRatherThanTheDriverBelowIt() throws Exception {
        DataSource ds = slowQueryPool();
        try (Connection pooled = ds.getConnection()) {
            PreparedStatement pst = pooled.prepareStatement("SELECT 1");
            // The shape with the interceptor's name belongs to the query
            // report proxy below the facade; the facade's own handle
            // describes the facade instead. Neither may leak the driver.
            Statement reportHandle = statementBehindFacade(pst);

            String open = Assertions.assertDoesNotThrow(reportHandle::toString,
                    "toString on an open handle must return, not throw");
            Assertions.assertTrue(open.contains(SlowQueryReport.class.getName()),
                    "the handle must say which interceptor produced it, was: " + open);
            Assertions.assertTrue(open.contains("SELECT 1"),
                    "the handle must name its query, was: " + open);

            String facadeSide = Assertions.assertDoesNotThrow(pst::toString,
                    "toString on the application's handle must return, not throw");
            Assertions.assertTrue(facadeSide.contains("StatementFacade"),
                    "the facade handle must say what it is, was: " + facadeSide);
            Assertions.assertTrue(facadeSide.contains("SELECT 1"),
                    "and name its query, was: " + facadeSide);

            pst.close();
            String closed = Assertions.assertDoesNotThrow(reportHandle::toString,
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
        DataSource ds = slowQueryPool();
        try (Connection pooled = ds.getConnection()) {
            Statement st = pooled.createStatement();
            Statement reportHandle = statementBehindFacade(st);
            st.close();

            for (int layer = 0; layer < 2; layer++) {
                String name = layer == 0 ? "the statement facade"
                        : "the query report proxy";
                Statement handle = layer == 0 ? st : reportHandle;

                SQLException onExecute = Assertions.assertThrows(SQLException.class,
                        () -> handle.execute("SELECT 1"),
                        name + ": a closed handle must refuse work");
                Assertions.assertTrue(onExecute.getMessage().contains("closed"),
                        name + ": the closed contract still reports a closed statement: "
                                + onExecute.getMessage());
                Assertions.assertTrue(handle.isClosed(),
                        name + ": isClosed must report the closed handle");
                Assertions.assertThrows(SQLException.class, handle::getConnection,
                        name + ": a closed handle still reports its connection as gone");
                Assertions.assertThrows(SQLException.class, () -> handle.unwrap(Statement.class),
                        name + ": unwrap on a closed handle still fails");
                Assertions.assertThrows(SQLException.class,
                        () -> handle.isWrapperFor(Statement.class),
                        name + ": isWrapperFor on a closed handle still fails");
                Assertions.assertThrows(SQLException.class, () -> handle.setFetchSize(1),
                        name + ": a business method on a closed handle still fails");
            }
        }
    }

    /*
     * The facade always absorbs the Object methods from outside the
     * interceptor, and the abandoned timer wraps once more on top of the
     * query report proxy; neither interceptor and neither layer may change
     * an answer the contract requires.
     */
    private static String layerName(int layer) {
        return layer == 0 ? "facade handle" : "interceptor handle";
    }

    @Test
    void everyQueryReportInterceptorAnswersTheSameWayAtBothLayers() throws Exception {
        String[] interceptors = { SlowQueryReport.class.getSimpleName(),
                ResetAbandonedTimer.class.getSimpleName() };
        for (String interceptor : interceptors) {
            DataSource ds = queryReportPool(interceptor);
            try (Connection pooled = ds.getConnection()) {
                Statement st = pooled.createStatement();
                Statement inner = statementBehindFacade(st);
                try (PreparedStatement pst = pooled.prepareStatement("SELECT 1")) {
                    Assertions.assertTrue(pst.equals(pst),
                            interceptor + ": a prepared handle is equal to itself");
                    Assertions.assertFalse(pst.equals(st),
                            interceptor + ": two statements are two handles");
                    Statement innerPst = statementBehindFacade(pst);
                    Assertions.assertTrue(innerPst.equals(innerPst),
                            interceptor + ": the interceptor's own prepared proxy is equal to itself");
                    Assertions.assertFalse(innerPst.equals(inner),
                            interceptor + ": two proxies are two handles below the facade");
                }

                Statement[] handles = { st, inner };
                int[] hashes = new int[2];
                @SuppressWarnings("unchecked")
                Map<Statement, String>[] byHandle = new Map[] {
                        new HashMap<Statement, String>(), new HashMap<Statement, String>() };
                for (int layer = 0; layer < 2; layer++) {
                    Assertions.assertTrue(handles[layer].equals(handles[layer]),
                            interceptor + " " + layerName(layer) + ": a statement handle is equal to itself");
                    Assertions.assertFalse(handles[layer].equals(null),
                            interceptor + " " + layerName(layer) + ": equals(null) is false");
                    hashes[layer] = handles[layer].hashCode();
                    byHandle[layer].put(handles[layer], "the handle");
                }

                st.close(); // both layers then face the same closed state

                for (int layer = 0; layer < 2; layer++) {
                    String name = interceptor + " " + layerName(layer);
                    Statement handle = handles[layer];

                    Assertions.assertTrue(handle.equals(handle),
                            name + ": a closed handle stays equal to itself");
                    Assertions.assertFalse(handle.equals(pooled),
                            name + ": a closed handle is equal to nothing else");
                    Assertions.assertEquals(hashes[layer], handle.hashCode(),
                            name + ": the hash survives the close");
                    Assertions.assertEquals("the handle", byHandle[layer].get(handle),
                            name + ": a closed handle still hashes into its map");
                    Assertions.assertNotNull(String.valueOf(handle),
                            name + ": the closed handle still describes itself");
                }
            }
        }
    }
}
