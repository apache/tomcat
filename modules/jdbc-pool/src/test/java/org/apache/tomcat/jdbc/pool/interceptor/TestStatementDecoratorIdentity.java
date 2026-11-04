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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;
import org.apache.tomcat.jdbc.pool.DataSource;

/**
 * Object-method contract of the statement and result set handles that
 * {@link StatementDecoratorInterceptor} hands out. A handle is an object
 * like any other: it must be equal to itself and to nothing else, its hash
 * code must be a stable value of its own, and both answers must survive the
 * close of the handle - a logging statement or a map lookup must not blow up
 * just because the work is done. Before this contract was honoured, the
 * proxies forwarded {@code equals} and {@code hashCode} to the driver object
 * underneath: the handle was not equal to itself, and after close there was
 * no delegate left, so every Object method surfaced as an
 * {@link UndeclaredThrowableException} wrapping "Statement closed.".
 *
 * <p>The pool always installs its statement facade outermost (the
 * {@code useStatementFacade} property that used to switch it off is
 * deprecated and has no effect), and the facade answers these methods on the
 * handle the application holds. The decorator's own proxies stay live inner
 * layers under the facade's handles - the facade wraps what the interceptor
 * chain returns - so the tests reach them through the facade's delegate and
 * pin the contract at both layers.</p>
 */
class TestStatementDecoratorIdentity extends JdbcPoolBaseTest {

    private DataSource decoratorPool() throws SQLException {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setJdbcInterceptors(
                StatementDecoratorInterceptor.class.getSimpleName());
        ds.createPool();
        return ds;
    }

    /* The interceptor's own statement handle behind the facade's. */
    private static Statement statementBehindFacade(Statement facadeHandle)
            throws ReflectiveOperationException {
        return (Statement) proxiedObject(facadeHandle);
    }

    /* The interceptor's own result set handle behind the facade's. */
    private static ResultSet resultSetBehindFacade(ResultSet facadeHandle)
            throws ReflectiveOperationException {
        return (ResultSet) proxiedObject(facadeHandle);
    }

    /* The contract of an open statement handle, for one layer. */
    private static void assertOpenStatementHandle(Statement handle,
            Statement other, Connection pooled, String layer) {
        Assertions.assertTrue(handle.equals(handle),
                layer + ": a statement handle must be equal to itself");
        Assertions.assertFalse(handle.equals(null),
                layer + ": a statement handle is not equal to null");
        Assertions.assertFalse(handle.equals(pooled),
                layer + ": a statement handle is not equal to its connection");
        Assertions.assertFalse(handle.equals(other),
                layer + ": two statements are two handles");
        Assertions.assertFalse(other.equals(handle),
                layer + ": and not in the other direction either");
        int hash = handle.hashCode();
        Assertions.assertEquals(hash, handle.hashCode(),
                layer + ": hashCode must be a stable value of the handle itself");
        Set<Statement> seen = new HashSet<>();
        seen.add(handle);
        Assertions.assertTrue(seen.contains(handle),
                layer + ": hashCode must agree with equals");
    }

    /* The contract of a closed statement handle, for one layer. */
    private static void assertClosedStatementHandle(Statement handle,
            int hashBeforeClose, Connection pooled, String layer) {
        // Neither method declares a checked exception, so a throwing handler
        // cannot reach the application as a SQLException - the proxy layer
        // wraps it. Both answers must simply be values.
        Assertions.assertDoesNotThrow(handle::hashCode,
                layer + ": hashCode on a closed handle must return, not throw");
        Assertions.assertEquals(hashBeforeClose, handle.hashCode(),
                layer + ": closing must not change the identity of a handle");
        Assertions.assertTrue(handle.equals(handle),
                layer + ": closing must not break reflexivity of a handle");
        Assertions.assertFalse(handle.equals(pooled),
                layer + ": a closed handle is still equal to nothing but itself");
        try {
            handle.hashCode();
        } catch (UndeclaredThrowableException e) {
            Assertions.fail(layer + ": hashCode on a closed handle must not "
                    + "surface as UndeclaredThrowableException: " + e.getCause());
        }
    }

    @SuppressWarnings("unlikely-arg-type") // Use of unlikely arg-type is deliberate
    @Test
    void aStatementHandleIsEqualOnlyToItselfWhileOpen() throws Exception {
        DataSource ds = decoratorPool();
        try (Connection pooled = ds.getConnection()) {
            try (Statement st = pooled.createStatement();
                    Statement other = pooled.createStatement()) {
                assertOpenStatementHandle(st, other, pooled, "the statement facade");
                assertOpenStatementHandle(statementBehindFacade(st),
                        statementBehindFacade(other), pooled,
                        "the decorator's own proxy");
            }
        }
    }

    @SuppressWarnings("unlikely-arg-type") // Use of unlikely arg-type is deliberate
    @Test
    void aClosedStatementHandleKeepsAnsweringWithItsOwnIdentity() throws Exception {
        DataSource ds = decoratorPool();
        try (Connection pooled = ds.getConnection()) {
            Statement st = pooled.createStatement();
            // The decorator's proxy is reached while the handle is still
            // open; it is the layer this test was written for.
            Statement decoratorHandle = statementBehindFacade(st);
            int facadeHash = st.hashCode();
            int decoratorHash = decoratorHandle.hashCode();
            st.close();

            assertClosedStatementHandle(st, facadeHash, pooled, "the statement facade");
            assertClosedStatementHandle(decoratorHandle, decoratorHash, pooled,
                    "the decorator's proxy");
        }
    }

    @SuppressWarnings("unlikely-arg-type") // Use of unlikely arg-type is deliberate
    @Test
    void aResultSetHandleIsAnHonestHandleOpenAndClosed() throws Exception {
        DataSource ds = decoratorPool();
        try (Connection pooled = ds.getConnection()) {
            Statement st = pooled.createStatement();
            ResultSet rs = st.executeQuery("SELECT 1");
            Assertions.assertTrue(rs.next());
            Statement decoratorSt = statementBehindFacade(st);
            ResultSet decoratorRs = resultSetBehindFacade(rs);

            ResultSet facadeRs = rs;
            for (int layer = 0; layer < 2; layer++) {
                String name = layer == 0 ? "the statement facade"
                        : "the decorator's own proxies";
                Statement stHandle = layer == 0 ? st : decoratorSt;
                ResultSet rsHandle = layer == 0 ? facadeRs : decoratorRs;

                Assertions.assertTrue(rsHandle.equals(rsHandle),
                        name + ": a result set handle must be equal to itself");
                Assertions.assertFalse(rsHandle.equals(null),
                        name + ": a result set handle is not equal to null");
                Assertions.assertFalse(rsHandle.equals(stHandle),
                        name + ": a result set handle is not equal to its statement");
                Assertions.assertFalse(rsHandle.equals(new Object()),
                        name + ": a result set handle is not equal to a foreign object");
                int hash = rsHandle.hashCode();
                Assertions.assertEquals(hash, rsHandle.hashCode(),
                        name + ": hashCode must be a stable value of the handle itself");
                // the handle a result set reports must answer to the
                // statement handle of the same layer
                Assertions.assertSame(stHandle, rsHandle.getStatement(),
                        name + ": the statement behind the result set must be "
                                + "the handle of the same layer");
                Assertions.assertTrue(rsHandle.getStatement().equals(stHandle),
                        name + ": the reported statement must be equal to the held handle");
                // toString is answered by the proxy, not forwarded to the
                // driver's cursor
                Assertions.assertTrue(rsHandle.toString().contains("ResultSetProxy"),
                        name + ": the handle must describe itself, was: " + rsHandle);
            }

            int facadeRsHash = facadeRs.hashCode();
            int decoratorRsHash = decoratorRs.hashCode();
            // Close through the application's handles; both layers see it.
            facadeRs.close();
            st.close();

            for (int layer = 0; layer < 2; layer++) {
                String name = layer == 0 ? "the statement facade"
                        : "the decorator's proxy";
                ResultSet rsHandle = layer == 0 ? facadeRs : decoratorRs;
                int hash = layer == 0 ? facadeRsHash : decoratorRsHash;

                Assertions.assertTrue(rsHandle.equals(rsHandle),
                        name + ": a closed result set handle must still be equal to itself");
                Assertions.assertEquals(hash, rsHandle.hashCode(),
                        name + ": closing must not change the identity of a handle");
                Assertions.assertDoesNotThrow(() -> Boolean.valueOf(rsHandle.equals(rsHandle)),
                        name + ": equals on a closed handle must return, not throw");
            }
        }
    }

    @Test
    void aReusedCachedStatementKeepsTheIdentityContract() throws Exception {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setJdbcInterceptors(
                StatementCache.class.getName() + "(prepared=true,callable=false,max=10)");
        ds.createPool();

        try (Connection pooled = ds.getConnection()) {
            PreparedStatement first = pooled.prepareStatement("SELECT 1");
            Statement decoratorFirst = statementBehindFacade(first);
            int decoratorFirstHash = decoratorFirst.hashCode();
            first.close(); // recycled into the cache

            PreparedStatement reused = pooled.prepareStatement("SELECT 1");
            Statement decoratorReused = statementBehindFacade(reused);
            // The facade hands out a fresh handle of its own for every
            // borrow, and the cache re-parks the statement under a fresh
            // proxy of its own; the reused statement must arrive with new
            // identity at both layers.
            Assertions.assertNotSame(first, reused,
                    "the cache hands the reused statement out under a fresh handle");
            Assertions.assertNotSame(decoratorFirst, decoratorReused,
                    "and the proxy below the facade is fresh as well");
            Assertions.assertTrue(reused.equals(reused),
                    "a reused handle must be equal to itself at the facade layer");
            Assertions.assertTrue(decoratorReused.equals(decoratorReused),
                    "a reused handle must be equal to itself at the cache layer");
            Assertions.assertFalse(reused.equals(first),
                    "the disposed handle of the previous use stays unequal");
            Assertions.assertFalse(first.equals(reused));
            Assertions.assertFalse(decoratorReused.equals(decoratorFirst),
                    "and the disposed proxy of the previous use stays unequal "
                            + "below the facade");
            Assertions.assertFalse(decoratorFirst.equals(decoratorReused));
            Assertions.assertNotEquals(decoratorFirstHash, decoratorReused.hashCode(),
                    "two proxies must not share an identity hash");
            Assertions.assertEquals(decoratorReused.hashCode(), decoratorReused.hashCode(),
                    "hashCode must be stable on a reused handle");
            try (ResultSet rs = reused.executeQuery()) {
                Assertions.assertTrue(rs.next());
            }
            int decoratorReusedHash = decoratorReused.hashCode();
            reused.close();
            Assertions.assertEquals(decoratorReusedHash, decoratorReused.hashCode(),
                    "re-closing must not change the identity of a handle");
            Assertions.assertTrue(decoratorReused.equals(decoratorReused),
                    "a handle recycled a second time must still be equal to itself");
        }
    }

    /*
     * The facade answers the Object methods from outside the decorator, and
     * since it is always there the application can never be routed around
     * it - but the decorator's proxies stay live underneath, and the answers
     * both layers give must obey the same contract.
     */
    @SuppressWarnings("unlikely-arg-type") // Use of unlikely arg-type is deliberate
    @Test
    void facadeHandleAndDecoratorHandleObeyTheSameContract() throws Exception {
        DataSource ds = decoratorPool();
        try (Connection pooled = ds.getConnection()) {
            Statement st = pooled.createStatement();
            ResultSet rs = st.executeQuery("SELECT 1");
            Assertions.assertTrue(rs.next());
            Statement[] statements = { st, statementBehindFacade(st) };
            ResultSet[] resultSets = { rs, resultSetBehindFacade(rs) };

            for (int layer = 0; layer < 2; layer++) {
                String name = layer == 0 ? "the statement facade"
                        : "the decorator's own proxies";
                Statement s = statements[layer];
                ResultSet r = resultSets[layer];

                Assertions.assertTrue(s.equals(s),
                        name + ": a statement handle is equal to itself");
                Assertions.assertFalse(s.equals(null),
                        name + ": equals(null) is false");
                Assertions.assertFalse(s.equals(r),
                        name + ": a statement is not its result set");
                Assertions.assertTrue(r.equals(r),
                        name + ": a result set handle is equal to itself");
                Assertions.assertFalse(r.equals(s),
                        name + ": a result set is not its statement");
                Assertions.assertTrue(r.getStatement().equals(s),
                        name + ": the reported statement answers to the held handle");
            }

            int[] stHashes = { statements[0].hashCode(), statements[1].hashCode() };
            int[] rsHashes = { resultSets[0].hashCode(), resultSets[1].hashCode() };
            // Close through the application's handles; both layers then face
            // the same closed state.
            resultSets[0].close();
            statements[0].close();

            for (int layer = 0; layer < 2; layer++) {
                String name = layer == 0 ? "the statement facade"
                        : "the decorator's own proxies";

                Assertions.assertTrue(statements[layer].equals(statements[layer]),
                        name + ": a closed statement stays equal to itself");
                Assertions.assertTrue(resultSets[layer].equals(resultSets[layer]),
                        name + ": a closed result set stays equal to itself");
                Assertions.assertEquals(stHashes[layer], statements[layer].hashCode(),
                        name + ": the statement hash survives the close");
                Assertions.assertEquals(rsHashes[layer], resultSets[layer].hashCode(),
                        name + ": the result set hash survives the close");
            }
        }
    }
}
