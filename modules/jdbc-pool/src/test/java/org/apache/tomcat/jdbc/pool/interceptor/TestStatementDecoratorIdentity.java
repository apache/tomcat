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
 * <p>With the (default) statement facade enabled it sits outermost and
 * answers these methods itself, which masks the gap; these tests pin the
 * unfacaded configuration - {@code useStatementFacade=false} is a documented
 * configuration - and that both configurations answer identically.</p>
 */
class TestStatementDecoratorIdentity extends JdbcPoolBaseTest {

    private DataSource decoratorPool(boolean useStatementFacade) throws SQLException {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setUseStatementFacade(useStatementFacade);
        ds.getPoolProperties().setJdbcInterceptors(
                StatementDecoratorInterceptor.class.getSimpleName());
        ds.createPool();
        return ds;
    }

    @Test
    void aStatementHandleIsEqualOnlyToItselfWhileOpen() throws Exception {
        DataSource ds = decoratorPool(false);
        try (Connection pooled = ds.getConnection()) {
            try (Statement st = pooled.createStatement()) {
                Assertions.assertTrue(st.equals(st),
                        "a statement handle must be equal to itself");
                Assertions.assertFalse(st.equals(null),
                        "a statement handle is not equal to null");
                Assertions.assertFalse(st.equals(pooled),
                        "a statement handle is not equal to its connection");
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
        }
    }

    @Test
    void aClosedStatementHandleKeepsAnsweringWithItsOwnIdentity() throws Exception {
        DataSource ds = decoratorPool(false);
        Connection pooled = ds.getConnection();
        try {
            Statement st = pooled.createStatement();
            int hash = st.hashCode();
            st.close();

            // Neither method declares a checked exception, so a throwing
            // handler cannot reach the application as a SQLException - the
            // proxy layer wraps it. Both answers must simply be values.
            Assertions.assertDoesNotThrow(st::hashCode,
                    "hashCode on a closed handle must return, not throw");
            Assertions.assertEquals(hash, st.hashCode(),
                    "closing must not change the identity of a handle");
            Assertions.assertTrue(st.equals(st),
                    "closing must not break reflexivity of a handle");
            Assertions.assertFalse(st.equals(pooled),
                    "a closed handle is still equal to nothing but itself");
            try {
                st.hashCode();
            } catch (UndeclaredThrowableException e) {
                Assertions.fail("hashCode on a closed handle must not surface as "
                        + "UndeclaredThrowableException: " + e.getCause());
            }
        } finally {
            pooled.close();
        }
    }

    @Test
    void aResultSetHandleIsAnHonestHandleOpenAndClosed() throws Exception {
        DataSource ds = decoratorPool(false);
        try (Connection pooled = ds.getConnection()) {
            try (Statement st = pooled.createStatement()) {
                ResultSet rs = st.executeQuery("SELECT 1");
                Assertions.assertTrue(rs.next());

                Assertions.assertTrue(rs.equals(rs),
                        "a result set handle must be equal to itself");
                Assertions.assertFalse(rs.equals(null),
                        "a result set handle is not equal to null");
                Assertions.assertFalse(rs.equals(st),
                        "a result set handle is not equal to its statement");
                Assertions.assertFalse(rs.equals(new Object()),
                        "a result set handle is not equal to a foreign object");
                int hash = rs.hashCode();
                Assertions.assertEquals(hash, rs.hashCode(),
                        "hashCode must be a stable value of the handle itself");
                // the handle a result set reports must answer to the
                // statement handle the application holds
                Assertions.assertSame(st, rs.getStatement());
                Assertions.assertTrue(rs.getStatement().equals(st),
                        "the reported statement must be equal to the held handle");
                // toString is answered by the proxy, not forwarded to the
                // driver's cursor
                Assertions.assertTrue(rs.toString().contains("ResultSetProxy"),
                        "the handle must describe itself, was: " + rs);

                rs.close();
                st.close();

                Assertions.assertTrue(rs.equals(rs),
                        "a closed result set handle must still be equal to itself");
                Assertions.assertEquals(hash, rs.hashCode(),
                        "closing must not change the identity of a handle");
                Assertions.assertDoesNotThrow(() -> rs.equals(rs),
                        "equals on a closed handle must return, not throw");
            }
        }
    }

    @Test
    void aReusedCachedStatementKeepsTheIdentityContract() throws Exception {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setUseStatementFacade(false);
        ds.getPoolProperties().setJdbcInterceptors(
                StatementCache.class.getName() + "(prepared=true,callable=false,max=10)");
        ds.createPool();

        try (Connection pooled = ds.getConnection()) {
            PreparedStatement first = pooled.prepareStatement("SELECT 1");
            int firstHash = first.hashCode();
            first.close(); // recycled into the cache

            PreparedStatement reused = pooled.prepareStatement("SELECT 1");
            Assertions.assertNotSame(first, reused,
                    "the cache hands the reused statement out under a fresh handle");
            Assertions.assertTrue(reused.equals(reused),
                    "a reused handle must be equal to itself");
            Assertions.assertFalse(reused.equals(first),
                    "the disposed handle of the previous use stays unequal");
            Assertions.assertFalse(first.equals(reused));
            Assertions.assertNotEquals(firstHash, reused.hashCode(),
                    "two handles must not share an identity hash");
            Assertions.assertEquals(reused.hashCode(), reused.hashCode(),
                    "hashCode must be stable on a reused handle");
            try (ResultSet rs = reused.executeQuery()) {
                Assertions.assertTrue(rs.next());
            }
            int reusedHash = reused.hashCode();
            reused.close();
            Assertions.assertEquals(reusedHash, reused.hashCode(),
                    "re-closing must not change the identity of a handle");
            Assertions.assertTrue(reused.equals(reused),
                    "a handle recycled a second time must still be equal to itself");
        }
    }

    /*
     * The facade absorbs the Object methods from outside the decorator, so
     * the configuration switch must not change any answer the application
     * gets.
     */
    @Test
    void bothConfigurationsAnswerTheSameWay() throws Exception {
        for (boolean facade : new boolean[] { false, true }) {
            String config = facade ? "with" : "without";
            DataSource ds = decoratorPool(facade);
            try (Connection pooled = ds.getConnection()) {
                Statement st = pooled.createStatement();
                ResultSet rs = st.executeQuery("SELECT 1");
                Assertions.assertTrue(rs.next(), config + " the facade");

                Assertions.assertTrue(st.equals(st),
                        config + " the facade a statement handle is equal to itself");
                Assertions.assertFalse(st.equals(null),
                        config + " the facade equals(null) is false");
                Assertions.assertFalse(st.equals(rs),
                        config + " the facade a statement is not its result set");
                Assertions.assertTrue(rs.equals(rs),
                        config + " the facade a result set handle is equal to itself");
                Assertions.assertFalse(rs.equals(st),
                        config + " the facade a result set is not its statement");
                Assertions.assertTrue(rs.getStatement().equals(st),
                        config + " the facade the reported statement answers to the held handle");

                int stHash = st.hashCode();
                int rsHash = rs.hashCode();
                rs.close();
                st.close();

                Assertions.assertTrue(st.equals(st),
                        config + " the facade a closed statement stays equal to itself");
                Assertions.assertTrue(rs.equals(rs),
                        config + " the facade a closed result set stays equal to itself");
                Assertions.assertEquals(stHash, st.hashCode(),
                        config + " the facade the statement hash survives the close");
                Assertions.assertEquals(rsHash, rs.hashCode(),
                        config + " the facade the result set hash survives the close");
            }
        }
    }
}
