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

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Array;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.HashMap;
import java.util.logging.Logger;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;

/**
 * The statement facade decides what to seal by a type rule - a value a
 * pooled handle hands back is sealed when the value <em>is</em> a
 * {@link java.sql.ResultSet} (the {@code Wrapper} methods excepted) and
 * passes through as the driver's own object when it is not (apart from
 * the connection a statement handle reports, which is the pool's own
 * handle) - and the boundary of that rule is the type itself, as the
 * class javadoc of {@link StatementFacade} states next to its
 * {@code unwrap()} caveat. A
 * {@link java.sql.Array} read for an ARRAY column is therefore the
 * driver's object, and the cursor its {@code getResultSet()} hands out
 * reaches the application with no facade layer over it: it is tied to no
 * borrow and no handle retires it, exactly like an object reached through
 * {@code unwrap()}.
 *
 * <p>These tests pin that documented posture. They pass against the code
 * that documents it - they pin the boundary, they do not reproduce a
 * defect: a future change that seals composite values, or one that widens
 * the hole beyond what the javadoc says, has to change a test here to
 * say so, the way the bounded-tracked-list pin holds what
 * {@code StatementFinalizer} documents.</p>
 *
 * <p>{@code Struct} and {@code Ref} are the sibling composites with the
 * same shape - {@code Struct.getAttributes()} and the deprecated
 * {@code Ref} each reach a cursor outside the facade in the same way -
 * and H2 hands out neither: it converts nothing to a
 * {@code java.sql.Struct} and has no {@code REF} value at all, so those
 * two routes cannot be exercised here; the refusals that hide them are
 * pinned as tripwires by
 * {@link #h2HandsOutNeitherStructNorRefValues()}. H2's one other
 * composite-valued column, {@code ROW}, is the boundary's other side and
 * is exercised: the driver hands a ROW value back <em>as</em> a result
 * set, so the type rule sees it and seals it. The facade seals values
 * that are cursors; what escapes is only values that carry one.</p>
 */
class TestCompositeValueBoundary extends JdbcPoolBaseTest {

    /*
     * The row behind every read here carries the two composite columns of
     * this driver: an ARRAY value and a ROW value. The ARRAY elements are
     * what the getResultSet cursor hands out as (ordinal, element) pairs.
     */
    private static final int ARRAY_COLUMN = 2;
    private static final int ROW_COLUMN = 3;
    private static final String ARRAY_LABEL = "ARR";
    private static final int[] ARRAY_VALUES = { 10, 20, 30 };
    private static final String SELECT_COMPOSITES = "SELECT ID, " + ARRAY_LABEL
            + ", CAST(ROW(1,'a') AS ROW(F1 INT, F2 VARCHAR)) AS ROWVAL FROM ";

    /* The parameter the delegating provider's callable statements answer
     * with an array, the way a driver with ARRAY OUT-parameters answers
     * getArray(int) and getObject(int) on the drivers that have them. */
    private static final int ARRAY_PARAMETER = 1;
    private static final int[] OUT_ARRAY_VALUES = { 7, 8 };

    /* One borrow at a time over one idle session, so the next borrow
     * demonstrably gets the very session the composite was read on - the
     * configuration the neighbouring facade tests use. */
    private DataSource sealingPool() throws SQLException {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setMaxActive(1);
        ds.getPoolProperties().setMaxIdle(1);
        ds.createPool();
        return ds;
    }

    private void createArrayTable(Connection c, String table) throws SQLException {
        try (Statement st = c.createStatement()) {
            st.execute("CREATE TABLE " + table + " (ID INT, " + ARRAY_LABEL + " INT ARRAY[4])");
            st.execute("INSERT INTO " + table + " VALUES (1, ARRAY[10,20,30])");
        }
    }

    /* The driver's own object: this project's driver's class, and not one
     * of the pool's proxies - no facade layer anywhere on the path. */
    private static void assertDriversOwnObject(Object value, String reachedBy) {
        Assertions.assertFalse(Proxy.isProxyClass(value.getClass()),
                reachedBy + " must not hand back a pooled handle, but said: "
                        + value.getClass().getName());
        Assertions.assertTrue(value.getClass().getName().startsWith("org.h2."),
                reachedBy + " must hand back the driver's own object, but said: "
                        + value.getClass().getName());
    }

    private static void assertDriversOwnCursor(ResultSet cursor, String reachedBy) {
        assertDriversOwnObject(cursor, reachedBy);
    }

    /* The array's elements as its getResultSet cursor hands them out, as
     * (ordinal, element) pairs. */
    private static void readArrayRows(ResultSet rows) throws SQLException {
        int read = 0;
        while (rows.next()) {
            Assertions.assertEquals(read + 1, rows.getInt(1), "the array element's ordinal");
            Assertions.assertEquals(ARRAY_VALUES[read], rows.getInt(2), "the array element");
            read++;
        }
        Assertions.assertEquals(ARRAY_VALUES.length, read,
                "every element of the array is readable");
    }

    /**
     * Every route the Array is read by - the Object-typed getObject
     * overloads, the Array-typed getArray overloads - hands it to the
     * application as the driver's own object through the sealed handle,
     * and all four of its getResultSet overloads hand out the driver's
     * own result set, likewise with no facade layer over it.
     */
    @Test
    void anArrayReadThroughASealedHandleIsTheDriversOwnObject() throws Exception {
        DataSource ds = sealingPool();
        try (Connection pooled = ds.getConnection()) {
            String table = uniqueTable();
            createArrayTable(pooled, table);
            try (Statement st = pooled.createStatement();
                    ResultSet rs = st.executeQuery(SELECT_COMPOSITES + table)) {
                Assertions.assertTrue(rs.next(), "precondition: the row is there");
                Assertions.assertTrue(Proxy.isProxyClass(rs.getClass()),
                        "precondition: the handle it is read through is sealed");

                Array fromGetObject = (Array) rs.getObject(ARRAY_COLUMN);
                Array fromTypedGetObject = rs.getObject(ARRAY_COLUMN, Array.class);
                Array fromGetArray = rs.getArray(ARRAY_COLUMN);
                Array fromGetArrayByLabel = rs.getArray(ARRAY_LABEL);
                assertDriversOwnObject(fromGetObject, "ResultSet.getObject(int)");
                assertDriversOwnObject(fromTypedGetObject,
                        "ResultSet.getObject(int, Array.class)");
                assertDriversOwnObject(fromGetArray, "ResultSet.getArray(int)");
                assertDriversOwnObject(fromGetArrayByLabel, "ResultSet.getArray(String)");
                Assertions.assertEquals(Arrays.toString(ARRAY_VALUES),
                        Arrays.toString((Object[]) fromGetObject.getArray()),
                        "the array's elements come through");

                assertDriversOwnCursor(fromGetObject.getResultSet(), "Array.getResultSet()");
                assertDriversOwnCursor(
                        fromGetObject.getResultSet(new HashMap<String, Class<?>>()),
                        "Array.getResultSet(Map)");
                assertDriversOwnCursor(fromGetObject.getResultSet(1, ARRAY_VALUES.length),
                        "Array.getResultSet(long, int)");
                assertDriversOwnCursor(fromGetObject.getResultSet(1, ARRAY_VALUES.length,
                                new HashMap<String, Class<?>>()),
                        "Array.getResultSet(long, int, Map)");

                try (ResultSet rows = fromGetObject.getResultSet()) {
                    readArrayRows(rows);
                }
            }
        }
    }

    /**
     * The boundary at the end of the borrow. The sealed handle retires:
     * it reports itself closed and refuses calls with the facade's
     * wording. The driver's Array does not: it and the cursors it hands
     * out are still the driver's own objects, bound to the physical
     * session the pool has re-armed under the next borrower, and their
     * reading leaves that session untouched. H2 materialises arrays
     * client-side, so the post-borrow reads below prove the posture -
     * nothing retires what left the facade - rather than a live
     * server-side stream. That is the posture the class javadoc states,
     * pinned so it cannot drift out of the javadoc's saying, in either
     * direction.
     */
    @Test
    void theCursorInsideAnArrayOutlivesTheBorrowThatReadIt() throws Exception {
        DataSource ds = sealingPool();

        Connection first = ds.getConnection();
        setSessionMarker(first, 13);
        String table = uniqueTable();
        createArrayTable(first, table);
        Statement st = first.createStatement();
        ResultSet rs = st.executeQuery(SELECT_COMPOSITES + table);
        Assertions.assertTrue(rs.next(), "precondition: the row is there");
        Array arr = rs.getObject(ARRAY_COLUMN, Array.class);
        ResultSet nested = arr.getResultSet();
        Assertions.assertTrue(nested.next(),
                "precondition: the array's cursor is live during the borrow");
        first.close(); // returned to the pool

        Connection second = ds.getConnection();
        Assertions.assertEquals(13, sessionMarker(second).intValue(),
                "precondition: the pool re-armed the same physical session");

        // What the facade sealed retires with the borrow, as every
        // neighbouring pin asserts of a handle.
        Assertions.assertTrue(rs.isClosed(),
                "a result set whose borrow ended must report itself closed");
        SQLException thrown = Assertions.assertThrows(SQLException.class, rs::next,
                "a result set whose borrow ended must not read on");
        Assertions.assertTrue(thrown.getMessage().contains(
                "The pooled connection this result set was read from has "
                        + "been returned to the pool."),
                "the failure must be the facade's wording, but said: "
                        + thrown.getMessage());

        // What the facade never sealed is retired by nothing: the Array
        // and its cursor are still the driver's objects, and the cursor
        // kept across the borrow still advances.
        assertDriversOwnObject(arr, "the Array kept across the borrow");
        Assertions.assertTrue(nested.next(),
                "a cursor kept out of the facade is not retired by the "
                        + "borrow: it still advances");
        assertDriversOwnCursor(arr.getResultSet(), "getResultSet() after the borrow ended");
        try (ResultSet rows = arr.getResultSet()) {
            readArrayRows(rows);
        }
        Assertions.assertEquals(Arrays.toString(ARRAY_VALUES),
                Arrays.toString((Object[]) arr.getArray()),
                "the Array's elements are still readable after the borrow");
        nested.close(); // the driver's own close on the driver's own object

        // The next borrower's session goes untouched by all of it: the
        // marker still says nobody wrote to it, and its own reads work.
        Assertions.assertEquals(13, sessionMarker(second).intValue(),
                "the post-borrow reads must not have disturbed the session");
        try (Statement probe = second.createStatement();
                ResultSet live = probe.executeQuery("SELECT 1")) {
            Assertions.assertTrue(live.next(),
                    "the current borrower must get working handles");
        }
        second.close();
    }

    /**
     * The same query's ROW column is the boundary's other side: H2 hands
     * a ROW value back as a result set, so the type rule sees it and
     * seals it - the handle over it retires with the borrow the way a
     * nested cursor's does. Together with the Array pins this says where
     * the line lies: the facade passes values that are not result sets
     * through as they came, and seals what is one.
     */
    @Test
    void aRowCompositeArrivesAsAResultSetAndIsSealed() throws Exception {
        DataSource ds = sealingPool();

        Connection first = ds.getConnection();
        setSessionMarker(first, 14);
        String table = uniqueTable();
        createArrayTable(first, table);
        Statement st = first.createStatement();
        ResultSet rs = st.executeQuery(SELECT_COMPOSITES + table);
        Assertions.assertTrue(rs.next(), "precondition: the row is there");
        ResultSet row = (ResultSet) rs.getObject(ROW_COLUMN);
        Assertions.assertTrue(Proxy.isProxyClass(row.getClass()),
                "the ROW column value, being a result set, arrives as a sealed handle");
        Assertions.assertTrue(proxiedObject(row).getClass().getName().startsWith("org.h2."),
                "and it is a handle over the driver's own row value");
        Assertions.assertTrue(row.next(), "the row value's handle is live during the borrow");
        Assertions.assertEquals(1, row.getInt(1), "the row value's first field");
        Assertions.assertEquals("a", row.getString(2), "the row value's second field");
        Assertions.assertFalse(row.next(), "the row value is one row");
        first.close();

        Connection second = ds.getConnection();
        Assertions.assertEquals(14, sessionMarker(second).intValue(),
                "precondition: the pool re-armed the same physical session");

        Assertions.assertTrue(row.isClosed(),
                "the sealed row-value handle retires with the borrow");
        SQLException thrown = Assertions.assertThrows(SQLException.class, row::next,
                "the sealed row-value handle must not read on");
        Assertions.assertTrue(thrown.getMessage().contains(
                "The pooled connection this result set was read from has "
                        + "been returned to the pool."),
                "the failure must be the facade's wording, but said: "
                        + thrown.getMessage());
        second.close();
    }

    /**
     * The statement layer's mirror rule, pinned for the composite: an
     * ARRAY OUT-parameter read from a callable statement hands the
     * driver's Array through unsealed at that layer too - and the
     * statement handle itself is sealed, so the same call that reached
     * the Array during the borrow is refused once the borrow has ended,
     * while the Array reached during it keeps working outside the
     * facade.
     */
    @Test
    void anArrayReachedAtTheStatementLayerReachesUnsealed() throws Exception {
        DataSource ds = arrayOutParameterPool();

        Connection first = ds.getConnection();
        setSessionMarker(first, 17);
        CallableStatement cs = first.prepareCall("CALL 1");
        cs.execute();
        Array arr = (Array) cs.getObject(ARRAY_PARAMETER);
        assertDriversOwnObject(arr, "CallableStatement.getObject(int)");
        assertDriversOwnObject(cs.getArray(ARRAY_PARAMETER), "CallableStatement.getArray(int)");
        assertDriversOwnObject(cs.getObject(ARRAY_PARAMETER, Array.class),
                "CallableStatement.getObject(int, Array.class)");
        ResultSet nested = arr.getResultSet();
        assertDriversOwnCursor(nested, "the OUT-parameter array's getResultSet()");
        Assertions.assertTrue(nested.next(),
                "precondition: the array's cursor is live during the borrow");
        first.close();

        Connection second = ds.getConnection();
        Assertions.assertEquals(17, sessionMarker(second).intValue(),
                "precondition: the pool re-armed the same physical session");

        // The handle is sealed: the call that reached the Array during
        // the borrow is refused now, whatever the Array itself does.
        SQLException thrown = Assertions.assertThrows(SQLException.class,
                () -> cs.getArray(ARRAY_PARAMETER),
                "the retired statement handle must not reach the OUT-parameter again");
        Assertions.assertTrue(thrown.getMessage().contains(
                "The pooled connection this statement was created from has "
                        + "been returned to the pool."),
                "the failure must be the facade's wording, but said: "
                        + thrown.getMessage());

        // The Array the handle reached during the borrow is outside the
        // facade and nothing retires it: it still hands out its driver's
        // own cursor, and that cursor still reads.
        assertDriversOwnObject(arr, "the array kept across the borrow");
        assertDriversOwnCursor(arr.getResultSet(), "getResultSet() after the borrow ended");
        try (ResultSet rows = arr.getResultSet()) {
            Assertions.assertTrue(rows.next(),
                    "the OUT-parameter array still reads after the borrow");
            Assertions.assertEquals(OUT_ARRAY_VALUES[0], rows.getInt(2), "its first element");
        }
        Assertions.assertEquals(17, sessionMarker(second).intValue(),
                "the post-borrow reads must not have disturbed the session");
        second.close();
    }

    /**
     * The sibling composites the javadoc's boundary covers but this
     * driver cannot hand out, pinned as refusals: H2 converts nothing to
     * a {@code java.sql.Struct} and has no {@code REF} value at all, so
     * {@code Struct.getAttributes()} and the deprecated {@code Ref} have
     * nothing here to exercise - the hole through them is stated in the
     * javadoc but cannot be pinned further than that with this driver.
     * A driver that ever starts producing these values fails this test,
     * and the boundary above must then be pinned for the new route too.
     */
    @Test
    void h2HandsOutNeitherStructNorRefValues() throws Exception {
        try (Connection pooled = sealingPool().getConnection();
                Statement st = pooled.createStatement()) {
            try (ResultSet rs = st.executeQuery(
                    "SELECT CAST(ROW(1,'a') AS ROW(F1 INT, F2 VARCHAR))")) {
                Assertions.assertTrue(rs.next(), "precondition: the ROW value is there");
                SQLException struct = Assertions.assertThrows(SQLException.class,
                        () -> rs.getObject(1, java.sql.Struct.class),
                        "if H2 ever converts to Struct, the sibling route "
                                + "above must be pinned too");
                Assertions.assertTrue(struct.getMessage().contains("java.sql.Struct"),
                        "the refusal must name the type it will not convert to, but said: "
                                + struct.getMessage());
            }
            Assertions.assertThrows(SQLException.class,
                    () -> st.executeQuery("SELECT CAST(NULL AS REF)"),
                    "if H2 ever has REF values, the sibling route above must be pinned too");
        }
    }

    /*
     * A connection provider shaped like a driver with ARRAY
     * OUT-parameters, because H2 has none: its callable statements hand
     * a java.sql.Array the driver made on the very physical session the
     * pool holds to getArray(...) and getObject(...) for the designated
     * parameter, and forward every other call straight to the H2 objects
     * beneath. The wrapping decision is the same one the facade makes on
     * the return type, so this is the JDK-level surface of an ARRAY
     * OUT-parameter faithfully - the way the REF cursor OUT-parameter is
     * shaped in TestCursorOutParameterSealing.
     */
    private static final class ArrayProvider implements javax.sql.DataSource {

        private final org.h2.jdbcx.JdbcDataSource delegate;

        ArrayProvider(String url) {
            delegate = new org.h2.jdbcx.JdbcDataSource();
            delegate.setUrl(url);
            delegate.setUser("sa");
            delegate.setPassword("");
        }

        @Override
        public Connection getConnection() throws SQLException {
            return (Connection) Proxy.newProxyInstance(
                    ArrayProvider.class.getClassLoader(),
                    new Class<?>[] { Connection.class },
                    new ConnectionHandler(delegate.getConnection()));
        }

        @Override
        public Connection getConnection(String username, String password)
                throws SQLException {
            // The credentials are the pool's configuration, not the
            // database's; the physical provider is the fixed H2 one. (Same
            // posture as the provider in TestCursorOutParameterSealing.)
            return getConnection();
        }

        @Override
        public java.io.PrintWriter getLogWriter() {
            return null;
        }

        @Override
        public void setLogWriter(java.io.PrintWriter out) {
            // no-op
        }

        @Override
        public void setLoginTimeout(int seconds) {
            // no-op
        }

        @Override
        public int getLoginTimeout() {
            return 0;
        }

        @Override
        public Logger getParentLogger() {
            return Logger.getLogger(ArrayProvider.class.getName());
        }

        // javax.sql.DataSource is a Wrapper: answer it honestly, from the
        // H2 provider beneath, as a real driver's data source does.
        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            return delegate.unwrap(iface);
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) throws SQLException {
            return delegate.isWrapperFor(iface);
        }
    }

    /*
     * Forwards every call to the H2 connection; only the object a
     * prepareCall hands back is wrapped, so its OUT-parameter reads show
     * the array behaviour below. Everything else - createStatement,
     * prepareStatement, close, the pool's own setup calls - goes to the
     * H2 connection unchanged.
     */
    private static final class ConnectionHandler implements InvocationHandler {

        private final Connection physical;

        ConnectionHandler(Connection physical) {
            this.physical = physical;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args)
                throws Throwable {
            try {
                Object result = method.invoke(physical, args);
                if ("prepareCall".equals(method.getName())) {
                    return Proxy.newProxyInstance(
                            ArrayProvider.class.getClassLoader(),
                            new Class<?>[] { CallableStatement.class },
                            new ArrayOutParameterHandler(physical,
                                    (CallableStatement) result));
                }
                return result;
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        }
    }

    /*
     * The fictional ARRAY OUT-parameter, and nothing else: getArray and
     * getObject for the designated parameter hand back the session's
     * java.sql.Array - a real driver object, made on the very physical
     * session the pool holds, the way an Oracle or PostgreSQL driver
     * hands back an ARRAY OUT-parameter - and every other call goes to
     * the H2 callable statement beneath.
     */
    private static final class ArrayOutParameterHandler implements InvocationHandler {

        private final Connection physical;
        private final CallableStatement delegate;
        /* Made on the physical session once, like a driver's one array
         * per OUT-parameter slot. */
        private Array array;

        ArrayOutParameterHandler(Connection physical, CallableStatement delegate) {
            this.physical = physical;
            this.delegate = delegate;
        }

        private Array array() throws SQLException {
            if (array == null) {
                Object[] elements = new Object[OUT_ARRAY_VALUES.length];
                for (int i = 0; i < elements.length; i++) {
                    elements[i] = Integer.valueOf(OUT_ARRAY_VALUES[i]);
                }
                array = physical.createArrayOf("INTEGER", elements);
            }
            return array;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args)
                throws Throwable {
            String name = method.getName();
            try {
                if (("getArray".equals(name) || "getObject".equals(name)) && args != null
                        && args.length >= 1 && args[0] instanceof Integer
                        && ((Integer) args[0]).intValue() == ARRAY_PARAMETER) {
                    // The parameter is an array, so the Array-typed call
                    // and every Object-typed conversion that accepts an
                    // array hand it out, the way a driver's conversion
                    // would.
                    if (args.length == 1
                            || (args[1] instanceof Class
                                    && ((Class<?>) args[1]).isInstance(array()))) {
                        return array();
                    }
                }
                return method.invoke(delegate, args);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        }
    }

    /*
     * A pool that serves its connections through the array provider, one
     * borrow at a time over one idle session, so the next borrow
     * demonstrably holds the very session the driver's array was made
     * on.
     */
    private DataSource arrayOutParameterPool() throws SQLException {
        DataSource ds = newDataSource();
        ds.setDataSource(new ArrayProvider(getUrl()));
        ds.getPoolProperties().setMaxActive(1);
        ds.getPoolProperties().setMaxIdle(1);
        ds.createPool();
        return ds;
    }
}
