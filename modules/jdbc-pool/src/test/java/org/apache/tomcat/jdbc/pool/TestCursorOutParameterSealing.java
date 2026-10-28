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
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;

/**
 * Result sets that reach the application as a plain {@link Object} - a
 * REF cursor OUT-parameter read through
 * {@link CallableStatement#getObject(int)} or one of its overloads - are
 * statement facade handles like the ones {@code executeQuery},
 * {@code getResultSet} and {@code getGeneratedKeys} hand back: they must be
 * sealed to the borrow that opened them, or the holder keeps streaming a
 * server-side cursor over the physical session the pool has already re-lent
 * to the next borrower.
 *
 * <p>H2 has no cursor OUT-parameter feature, so the pool here is fed by a
 * small delegating provider: its callable statements answer
 * {@code getObject(...)} for a designated parameter with a real H2 result
 * set opened on the very physical session the pool borrowed, the way an
 * Oracle REF cursor or a PostgreSQL refcursor reaches the application. The
 * wrapping itself is decided by the return type, so the pins below are the
 * JDK-level surface faithfully: whatever call hands back a
 * {@code java.sql.ResultSet} arrives sealed, and whatever the
 * {@code Wrapper} methods hand back does not (see also
 * {@link TestStatementConnectionHandle#unwrapStillReachesTheDriverStatement()}
 * and {@link TestStatementWrapperContract}).</p>
 *
 * <p>One parameter goes one layer deeper, because that is where the same
 * decision had still not been made: the cursor that parameter hands back
 * answers a designated column with a second real H2 result set on the same
 * session, which is how a nested cursor - a REF cursor selected as a column
 * value - reaches the application through the result set handle's own
 * {@code ResultSet.getObject(...)}. The pins for that cursor column are the
 * same four properties as for the OUT-parameter cursor: it arrives as a
 * handle, it works during the borrow, it retires with the borrow, and the
 * driver's own object is still what {@code unwrap()} hands back.</p>
 */
class TestCursorOutParameterSealing extends JdbcPoolBaseTest {

    /*
     * The parameter this provider's callable statements answer with a
     * cursor, and the one they answer with a plain value, mirroring what a
     * driver with a REF cursor OUT-parameter and an ordinary OUT-parameter
     * does for CallableStatement.getObject(...). The third parameter is the
     * one-layer-deeper case: its cursor answers a designated column with a
     * cursor of its own.
     */
    private static final int CURSOR_PARAMETER = 1;
    private static final int VALUE_PARAMETER = 2;
    private static final int CURSOR_COLUMN_PARAMETER = 3;
    private static final Integer OUT_VALUE = Integer.valueOf(1234);
    private static final String CURSOR_SQL = "SELECT X FROM SYSTEM_RANGE(1,3)";

    /*
     * The column of that second cursor's owner which holds a cursor rather
     * than a value, and the nested cursor it hands out. The plain column of
     * the same result set stays a plain column, so the pins can tell the two
     * apart.
     */
    private static final int CURSOR_COLUMN = 2;
    private static final int PLAIN_COLUMN = 1;
    private static final String CURSOR_COLUMN_NAME = "CURSOR_VALUE";
    private static final String PLAIN_COLUMN_VALUE = "not a cursor";
    private static final String CURSOR_COLUMN_SQL = "SELECT X, '" + PLAIN_COLUMN_VALUE
            + "' " + CURSOR_COLUMN_NAME + " FROM SYSTEM_RANGE(1,3)";
    private static final String NESTED_CURSOR_SQL = "SELECT X FROM SYSTEM_RANGE(101,103)";
    private static final int NESTED_CURSOR_FIRST_ROW = 101;

    /*
     * Stands for the driver's own cursor interface. A real driver's cursor
     * class implements such an interface and implements ResultSet, and an
     * application that needs the driver-only cursor methods reaches it with
     * unwrap(); a pooled handle must hand that object back untouched rather
     * than a proxy of its own.
     */
    public interface DriverCursor {
    }

    /*
     * A connection provider shaped like a driver with cursor
     * OUT-parameters: its callable statements hand the cursor - a real H2
     * result set on the very physical session the pool holds - to
     * getObject(...) and to unwrap(DriverCursor.class), and forward every
     * other call straight to the H2 objects beneath.
     */
    private static final class CursorProvider implements javax.sql.DataSource {

        private final org.h2.jdbcx.JdbcDataSource delegate;

        CursorProvider(String url) {
            delegate = new org.h2.jdbcx.JdbcDataSource();
            delegate.setUrl(url);
            delegate.setUser("sa");
            delegate.setPassword("");
        }

        @Override
        public Connection getConnection() throws SQLException {
            return (Connection) Proxy.newProxyInstance(
                    CursorProvider.class.getClassLoader(),
                    new Class<?>[] { Connection.class },
                    new ConnectionHandler(delegate.getConnection()));
        }

        @Override
        public Connection getConnection(String username, String password)
                throws SQLException {
            // The credentials are the pool's configuration, not the
            // database's; the physical provider is the fixed H2 one. (Same
            // posture as the provider in TestXaConnectionLeak.)
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
            return Logger.getLogger(CursorProvider.class.getName());
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
     * Intercepts prepareCall to wrap the H2 callable statement in the
     * cursor behaviour below. Everything else - createStatement,
     * prepareStatement, close, the pool's own setup calls - goes to the H2
     * connection unchanged.
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
                            CursorProvider.class.getClassLoader(),
                            new Class<?>[] { CallableStatement.class },
                            new CallableStatementHandler(physical,
                                    (CallableStatement) result));
                }
                return result;
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        }
    }

    /*
     * The fictional cursor behaviour, and nothing else: getObject(...) for
     * the cursor parameter returns the session's H2 result set - the same
     * object every request and an unwrap(DriverCursor.class) hand back -
     * getObject(...) for the cursor-column parameter returns the session's
     * H2 result set that has a cursor-valued column, and getObject(...) for
     * the value parameter returns the OUT-parameter value. Every other call
     * is forwarded to the H2 callable statement.
     */
    private static final class CallableStatementHandler implements InvocationHandler {

        private final Connection physical;
        private final CallableStatement delegate;
        /* Kept open with the cursor: closing the owner would close it. */
        private Statement cursorOwner;
        private ResultSet cursor;
        /* Likewise for the cursor that has a cursor-valued column. */
        private Statement cursorColumnOwner;
        private ResultSet cursorColumnCursor;

        CallableStatementHandler(Connection physical, CallableStatement delegate) {
            this.physical = physical;
            this.delegate = delegate;
        }

        /* The session's cursor, opened on the physical session once. */
        private ResultSet cursor() throws SQLException {
            if (cursor == null) {
                cursorOwner = physical.createStatement();
                cursor = cursorOwner.executeQuery(CURSOR_SQL);
            }
            return cursor;
        }

        /*
         * The session's cursor whose column holds a cursor, opened on the
         * physical session once. Its rows are H2's own; only the cursor
         * column is this provider's fiction, so that behaviour lives in the
         * handler below and nowhere else.
         */
        private ResultSet cursorWithCursorColumn() throws SQLException {
            if (cursorColumnCursor == null) {
                cursorColumnOwner = physical.createStatement();
                cursorColumnCursor = (ResultSet) Proxy.newProxyInstance(
                        CursorProvider.class.getClassLoader(),
                        new Class<?>[] { ResultSet.class },
                        new CursorColumnResultSetHandler(physical,
                                cursorColumnOwner.executeQuery(CURSOR_COLUMN_SQL)));
            }
            return cursorColumnCursor;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args)
                throws Throwable {
            String name = method.getName();
            try {
                if ("getObject".equals(name) && args != null
                        && args.length >= 1 && args[0] instanceof Integer) {
                    int parameter = ((Integer) args[0]).intValue();
                    if (parameter == CURSOR_PARAMETER) {
                        if (args.length == 1) {
                            return cursor();
                        }
                        if (args[1] instanceof Class
                                && ((Class<?>) args[1]).isInstance(cursor())) {
                            return cursor();
                        }
                    } else if (parameter == CURSOR_COLUMN_PARAMETER) {
                        if (args.length == 1) {
                            return cursorWithCursorColumn();
                        }
                        if (args[1] instanceof Class
                                && ((Class<?>) args[1]).isInstance(
                                        cursorWithCursorColumn())) {
                            return cursorWithCursorColumn();
                        }
                    } else if (parameter == VALUE_PARAMETER) {
                        if (args.length == 1
                                || ((Class<?>) args[1]).isInstance(OUT_VALUE)) {
                            return OUT_VALUE;
                        }
                    }
                } else if ("unwrap".equals(name)) {
                    Class<?> iface = (Class<?>) args[0];
                    if (DriverCursor.class.equals(iface)) {
                        // A driver's own cursor method hands back its
                        // cursor object, which implements ResultSet; this
                        // provider answers such a reach the same way, and
                        // the pool must not put a proxy of its own in front
                        // of it.
                        return cursor();
                    }
                    return delegate.unwrap(iface);
                } else if ("isWrapperFor".equals(name)) {
                    Class<?> iface = (Class<?>) args[0];
                    return Boolean.valueOf(DriverCursor.class.equals(iface)
                            || delegate.isWrapperFor(iface));
                }
                return method.invoke(delegate, args);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        }
    }

    /*
     * The one-layer-deeper fiction, and nothing else: the result set the
     * callable statement hands out has one column that is itself a cursor -
     * the nested cursor a driver reports for a cursor-valued column - and
     * reading that column through any of the ResultSet.getObject(...)
     * overloads hands out a second real H2 result set, opened on the very
     * same physical session. The plain column, the conversions the driver
     * cannot make here, the metadata and every other call go to the H2
     * result set beneath, and unwrap(DriverCursor.class) hands out that same
     * nested cursor object, the way the driver's own cursor method would.
     */
    private static final class CursorColumnResultSetHandler implements InvocationHandler {

        private final Connection physical;
        private final ResultSet delegate;
        /* Kept open with the nested cursor: closing it closes the cursor. */
        private Statement nestedOwner;
        private ResultSet nested;

        CursorColumnResultSetHandler(Connection physical, ResultSet delegate) {
            this.physical = physical;
            this.delegate = delegate;
        }

        /* The session's nested cursor, opened on the physical session once. */
        private ResultSet nestedCursor() throws SQLException {
            if (nested == null) {
                nestedOwner = physical.createStatement();
                nested = nestedOwner.executeQuery(NESTED_CURSOR_SQL);
            }
            return nested;
        }

        private static boolean isCursorColumn(Object column) {
            return (column instanceof Integer
                    && ((Integer) column).intValue() == CURSOR_COLUMN)
                    || CURSOR_COLUMN_NAME.equals(column);
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args)
                throws Throwable {
            String name = method.getName();
            try {
                if ("getObject".equals(name) && args != null && args.length >= 1
                        && isCursorColumn(args[0])) {
                    // The column is a cursor, so every one of the six
                    // overloads the standard API declares to come back as an
                    // Object hands it out - the typed one only where the
                    // requested type is one the cursor satisfies, the way a
                    // driver's conversion would.
                    if (args.length == 1 || args[1] instanceof Map) {
                        return nestedCursor();
                    }
                    if (args[1] instanceof Class
                            && ((Class<?>) args[1]).isInstance(nestedCursor())) {
                        return nestedCursor();
                    }
                } else if ("unwrap".equals(name)) {
                    Class<?> iface = (Class<?>) args[0];
                    if (DriverCursor.class.equals(iface)) {
                        return nestedCursor();
                    }
                    return delegate.unwrap(iface);
                } else if ("isWrapperFor".equals(name)) {
                    Class<?> iface = (Class<?>) args[0];
                    return Boolean.valueOf(DriverCursor.class.equals(iface)
                            || delegate.isWrapperFor(iface));
                }
                return method.invoke(delegate, args);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        }
    }

    /*
     * A pool that serves its connections through the cursor provider, one
     * borrow at a time over one idle session, so the next borrow
     * demonstrably gets the very session the cursor was opened on.
     */
    private DataSource cursorPool() throws SQLException {
        DataSource ds = newDataSource();
        ds.setDataSource(new CursorProvider(getUrl()));
        ds.getPoolProperties().setMaxActive(1);
        ds.getPoolProperties().setMaxIdle(1);
        ds.createPool();
        return ds;
    }

    /**
     * The cursor read from a getObject(...) OUT-parameter must arrive as a
     * facade handle over the driver's cursor - not as the driver's result
     * set itself - and that handle must behave like every other sealed
     * result set during the borrow: rows readable, getStatement() answering
     * with the application's statement, unwrap() reaching the driver object,
     * close() sticking.
     */
    @Test
    void aCursorOutParameterArrivesAsAFacadeHandle() throws Exception {
        DataSource ds = cursorPool();
        try (Connection pooled = ds.getConnection();
                CallableStatement cs = pooled.prepareCall("CALL 1")) {
            cs.execute();
            Object driverCursor = cs.unwrap(DriverCursor.class);
            Assertions.assertTrue(driverCursor instanceof ResultSet,
                    "precondition: the driver's cursor object implements ResultSet");

            ResultSet cursor = (ResultSet) cs.getObject(CURSOR_PARAMETER);

            // What the application holds must not be the driver's object:
            // a handle of this borrow, sealing exactly the driver's cursor.
            Assertions.assertNotSame(driverCursor, cursor,
                    "the cursor handed to the application must be a handle, not the "
                            + "driver's result set");
            Assertions.assertTrue(Proxy.isProxyClass(cursor.getClass()),
                    "the handle must be a facade proxy, was "
                            + cursor.getClass().getName());
            Assertions.assertSame(driverCursor, proxiedObject(cursor),
                    "the handle must wrap exactly this session's driver cursor");
            Assertions.assertSame(cs, cursor.getStatement(),
                    "the handle must report the application's own statement");

            // During the borrow it works like any other result set handle.
            Assertions.assertTrue(cursor.next(), "the cursor is live during the borrow");
            Assertions.assertEquals(1, cursor.getInt(1));
            Assertions.assertSame(driverCursor, cursor.unwrap(ResultSet.class),
                    "unwrap() through the handle must still arrive at the driver object");
            cursor.close();
            Assertions.assertTrue(cursor.isClosed(),
                    "the handle reports itself closed after close()");
            Assertions.assertThrows(SQLException.class, cursor::next,
                    "the handle of a closed result set must refuse reads");
            cursor.close(); // the no-op a closed result set's close() is
        }
    }

    /**
     * The consequence that makes the wrap worth having: a cursor handle
     * kept past its borrow must be retired with it. After the connection
     * went back and the next borrower demonstrably holds the very same
     * physical session, the old handle must report itself closed, refuse to
     * read with the facade's retirement wording, and provably not have
     * touched that session.
     */
    @Test
    void aCursorHandleRetiresWithTheBorrowThatOpenedIt() throws Exception {
        DataSource ds = cursorPool();

        Connection first = ds.getConnection();
        setSessionMarker(first, 5150);
        CallableStatement cs = first.prepareCall("CALL 1");
        cs.execute();
        ResultSet survivor = (ResultSet) cs.getObject(CURSOR_PARAMETER);
        Assertions.assertTrue(survivor.next(), "the cursor is live during the borrow");
        first.close(); // returned to the pool

        Connection second = ds.getConnection();
        Assertions.assertEquals(Integer.valueOf(5150), sessionMarker(second),
                "precondition: the pool re-armed the same physical session");

        Assertions.assertTrue(survivor.isClosed(),
                "a cursor whose borrow ended must report itself closed");
        SQLException thrown = Assertions.assertThrows(SQLException.class, survivor::next,
                "a cursor whose borrow ended must not keep streaming on the next "
                        + "borrower's session");
        Assertions.assertTrue(thrown.getMessage().contains(
                "result set was read from has been returned to the pool"),
                "the failure must be the facade's retirement wording, but said: "
                        + thrown.getMessage());
        Assertions.assertEquals(Integer.valueOf(5150), sessionMarker(second),
                "the next borrower's session must be untouched");

        // The current borrower keeps full use of its session.
        try (Statement st = second.createStatement();
                ResultSet rs = st.executeQuery("SELECT 1")) {
            Assertions.assertTrue(rs.next(), "the current borrower must get working handles");
        }
        survivor.close(); // the no-op a retired handle's close() is
        survivor.close();
        second.close();
    }

    /*
     * With no interceptor configured, exactly one facade layer sits over
     * the driver's own result set: what the handle wraps must be the
     * driver's object, not another facade handle. The type-based rule
     * must not nest handles for calls the name matching already wrapped.
     */
    private static void assertDriverObjectBeneathTheFacadeHandle(ResultSet handle,
            String path) throws ReflectiveOperationException {
        Assertions.assertTrue(Proxy.isProxyClass(handle.getClass()),
                path + ": the application's result set must be a handle, was "
                        + handle.getClass().getName());
        Object inner = proxiedObject(handle);
        Assertions.assertFalse(
                Proxy.isProxyClass(inner.getClass())
                        && Proxy.getInvocationHandler(inner).getClass().getName()
                                .contains("StatementFacade"),
                path + ": the facade must wrap the driver object once, not its own "
                        + "handle again");
        Assertions.assertTrue(inner.getClass().getName().startsWith("org.h2."),
                path + ": the layer beneath the facade must be the driver's own result "
                        + "set, found " + inner.getClass().getName());
    }

    /**
     * The three access paths that were always wrapped must keep behaving
     * exactly as before under the type-based rule: one facade layer over
     * the driver object (no added nesting), the application's statement as
     * the result set's statement, stable across repeated calls, and the
     * rows coming through.
     */
    @Test
    void theStandardResultSetPathsKeepExactlyOneFacadeLayer() throws Exception {
        DataSource ds = cursorPool();
        try (Connection con = ds.getConnection()) {
            String table = uniqueTable();
            try (Statement st = con.createStatement()) {
                try (ResultSet rs = st.executeQuery("SELECT X FROM SYSTEM_RANGE(1,2)")) {
                    assertDriverObjectBeneathTheFacadeHandle(rs, "executeQuery");
                    Assertions.assertSame(st, rs.getStatement());
                    int rows = 0;
                    while (rs.next()) {
                        rows++;
                    }
                    Assertions.assertEquals(2, rows);
                }

                Assertions.assertTrue(st.execute("SELECT X FROM SYSTEM_RANGE(1,2)"));
                ResultSet first = st.getResultSet();
                assertDriverObjectBeneathTheFacadeHandle(first, "getResultSet");
                Assertions.assertSame(st, first.getStatement());
                first.close();
                // Reading the same current result set again gets the same
                // single layer again.
                ResultSet again = st.getResultSet();
                assertDriverObjectBeneathTheFacadeHandle(again, "getResultSet, repeatedly");
                again.close();

                st.execute("CREATE TABLE " + table
                        + "(X INT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY)");
            }
            try (PreparedStatement ins = con.prepareStatement(
                    "INSERT INTO " + table + "(X) VALUES (DEFAULT)",
                    Statement.RETURN_GENERATED_KEYS)) {
                Assertions.assertEquals(1, ins.executeUpdate());
                try (ResultSet keys = ins.getGeneratedKeys()) {
                    assertDriverObjectBeneathTheFacadeHandle(keys, "getGeneratedKeys");
                    Assertions.assertTrue(keys.next(),
                            "the generated key must come through the handle");
                }
            }
            try (Statement st = con.createStatement()) {
                st.execute("DROP TABLE " + table);
            }
        }
    }

    /**
     * The documented escape hatch, pinned at exactly the point where a
     * wrap-by-type rule could break it: this driver's cursor type
     * implements ResultSet, so a blanket rule would silently wrap what
     * unwrap() hands back. unwrap() must still arrive at the driver's very
     * own cursor object - the same object on repeated calls, never a
     * proxy, and still usable.
     */
    @Test
    void unwrapStillHandsBackTheDriversOwnCursorObject() throws Exception {
        DataSource ds = cursorPool();
        try (Connection pooled = ds.getConnection()) {
            CallableStatement cs = pooled.prepareCall("CALL 1");
            Assertions.assertTrue(cs.isWrapperFor(DriverCursor.class),
                    "the provider must answer its own cursor interface honestly, as a "
                            + "driver does");

            Object cursor = cs.unwrap(DriverCursor.class);

            Assertions.assertTrue(cursor instanceof ResultSet,
                    "precondition: the object reached by unwrapping implements ResultSet");
            Assertions.assertFalse(Proxy.isProxyClass(cursor.getClass()),
                    "unwrap() must hand back the driver's object, not a pool handle: "
                            + cursor.getClass().getName());
            Assertions.assertTrue(cursor.getClass().getName().startsWith("org.h2."),
                    "unwrap() must arrive at the driver object through every layer: "
                            + cursor.getClass().getName());
            Assertions.assertSame(cursor, cs.unwrap(DriverCursor.class),
                    "a repeated unwrap() must return the same object, not a new wrapper");

            // What the escape hatch hands back is the driver's own object
            // and stays usable as such - that it outlives the borrow is the
            // documented posture, see TestStatementWrapperContract.
            try (ResultSet raw = (ResultSet) cursor) {
                Assertions.assertTrue(raw.next(),
                        "the driver's object returned by unwrap() must still work");
            }
            Assertions.assertInstanceOf(org.h2.jdbc.JdbcCallableStatement.class,
                    cs.unwrap(Statement.class),
                    "the ordinary statement unwrap must stay unaffected");
            cs.close();
        }
    }

    /**
     * The rule fires on the return type, so it must not disturb the plain
     * values that arrive through the same getObject(...) family: an
     * ordinary OUT-parameter value passes through untouched, in the plain
     * and the typed overload alike, and ordinary column reads through a
     * sealed result set are not touched either.
     */
    @Test
    void aPlainOutParameterPassesThroughUnchanged() throws Exception {
        DataSource ds = cursorPool();
        try (Connection pooled = ds.getConnection();
                CallableStatement cs = pooled.prepareCall("CALL 1")) {
            cs.execute();
            Object value = cs.getObject(VALUE_PARAMETER);
            Assertions.assertSame(OUT_VALUE, value,
                    "a plain OUT-parameter value must reach the application untouched");
            Assertions.assertSame(OUT_VALUE, cs.getObject(VALUE_PARAMETER, Object.class),
                    "the typed overload must pass the plain value through unchanged too");

            try (Statement st = pooled.createStatement();
                    ResultSet rs = st.executeQuery("SELECT 7")) {
                Assertions.assertTrue(rs.next());
                Assertions.assertEquals(7, rs.getInt(1),
                        "an ordinary column read through a handle must be untouched");
            }
        }
    }

    /**
     * One layer deeper, and the layer the sealing rule had not reached: the
     * cursor that a result set handle's own {@code getObject(...)} hands out
     * as a column value must arrive as a handle of this borrow over the
     * driver's nested cursor, not as the driver's result set itself. All six
     * {@code getObject} overloads a result set declares come back as an
     * Object, so the rule has to see every one of them, and the handle must
     * behave like every other sealed result set during the borrow: rows
     * readable, and closing it closes the nested cursor and nothing else -
     * above all not the result set that handed it out.
     */
    @Test
    void aCursorColumnValueArrivesAsAFacadeHandle() throws Exception {
        DataSource ds = cursorPool();
        try (Connection pooled = ds.getConnection();
                CallableStatement cs = pooled.prepareCall("CALL 1")) {
            cs.execute();
            ResultSet outer = (ResultSet) cs.getObject(CURSOR_COLUMN_PARAMETER);
            Assertions.assertTrue(outer.next(), "the cursor that owns the column is live");
            Object driverNested = outer.unwrap(DriverCursor.class);
            Assertions.assertTrue(driverNested instanceof ResultSet,
                    "precondition: the driver's nested cursor implements ResultSet");

            ResultSet nested = (ResultSet) outer.getObject(CURSOR_COLUMN);

            // What the application holds must not be the driver's object: a
            // handle of this borrow, sealing exactly the driver's cursor, and
            // exactly one facade layer of it.
            assertDriverObjectBeneathTheFacadeHandle(nested, "getObject(cursor column)");
            Assertions.assertNotSame(driverNested, nested,
                    "the cursor column must reach the application as a handle, not as the "
                            + "driver's result set");
            Assertions.assertSame(driverNested, proxiedObject(nested),
                    "the handle must wrap exactly this session's nested cursor");

            // During the borrow it works like any other result set handle.
            Assertions.assertTrue(nested.next(), "the nested cursor is live during the borrow");
            Assertions.assertEquals(NESTED_CURSOR_FIRST_ROW, nested.getInt(1),
                    "the nested cursor's own rows must come through the handle");
            Assertions.assertEquals(1, outer.getInt(PLAIN_COLUMN),
                    "the plain column of the same row must be untouched");

            // All six getObject(...) overloads a result set declares can
            // carry the column, so the return-type rule has to see each of
            // them; each arrives as a handle over the same driver cursor.
            Map<String, Class<?>> map = new HashMap<>();
            ResultSet[] fromEveryOverload = {
                    nested,
                    (ResultSet) outer.getObject(CURSOR_COLUMN_NAME),
                    (ResultSet) outer.getObject(CURSOR_COLUMN, Object.class),
                    (ResultSet) outer.getObject(CURSOR_COLUMN_NAME, Object.class),
                    (ResultSet) outer.getObject(CURSOR_COLUMN, map),
                    (ResultSet) outer.getObject(CURSOR_COLUMN_NAME, map),
            };
            for (ResultSet handle : fromEveryOverload) {
                Assertions.assertTrue(Proxy.isProxyClass(handle.getClass()),
                        "a cursor column read through getObject(...) must arrive as a handle, "
                                + "was " + handle.getClass().getName());
                Assertions.assertNotSame(driverNested, handle,
                        "a cursor column read through getObject(...) must not arrive as the "
                                + "driver's result set");
                Assertions.assertSame(driverNested, proxiedObject(handle),
                        "a cursor column read through getObject(...) must wrap exactly the "
                                + "driver's nested cursor");
            }

            // Closing the nested handle closes that cursor, and only that
            // cursor: the handle that handed it out is untouched, and so is
            // the row the application was reading.
            nested.close();
            Assertions.assertTrue(nested.isClosed(),
                    "the handle reports itself closed after close()");
            Assertions.assertThrows(SQLException.class, nested::next,
                    "the handle of a closed result set must refuse reads");
            nested.close(); // the no-op a closed result set's close() is
            Assertions.assertTrue(((ResultSet) driverNested).isClosed(),
                    "the close must reach the driver's own nested cursor");
            Assertions.assertFalse(outer.isClosed(),
                    "closing a cursor column must not close the cursor that handed it out");
            Assertions.assertTrue(outer.next(), "the owning handle still reads its rows");
            Assertions.assertEquals(2, outer.getInt(PLAIN_COLUMN));
        }
    }

    /**
     * getStatement() answers the application's own statement for every result
     * set the facade hands out, which is the Bugzilla-60764-era contract the
     * result set handler already guarantees for the standard paths. A handle
     * sealed from a cursor column is therefore parented to the same
     * statement, not to the result set that handed it out: the method is
     * declared to return a Statement, so answering the handle of a result set
     * would break the call site rather than the assertion, and the borrow it
     * reports retirement with is the statement's.
     */
    @Test
    void aNestedCursorHandleReportsTheStatementItWasReadFrom() throws Exception {
        DataSource ds = cursorPool();
        try (Connection pooled = ds.getConnection();
                CallableStatement cs = pooled.prepareCall("CALL 1")) {
            cs.execute();
            ResultSet outer = (ResultSet) cs.getObject(CURSOR_COLUMN_PARAMETER);
            ResultSet nested = outer.getObject(CURSOR_COLUMN, ResultSet.class);

            Assertions.assertSame(cs, nested.getStatement(),
                    "the nested handle must report the application's own statement");
            Assertions.assertSame(outer.getStatement(), nested.getStatement(),
                    "both handles were read from one statement, so both must report it");
            Assertions.assertTrue(nested.next(), "the nested cursor is live during the borrow");
            // Answerable after the reads began, as it is for any other handle.
            Assertions.assertSame(cs, nested.getStatement(),
                    "getStatement() stays answerable as a Statement through the borrow");
            nested.close();
            Assertions.assertThrows(SQLException.class, nested::getStatement,
                    "a closed handle answers getStatement() as a closed result set does");
        }
    }

    /**
     * The consequence that makes the deeper wrap worth having: a handle over
     * a cursor column kept past its borrow must be retired with it. The
     * driver's own nested cursor is the control here - it is still open on
     * the session the pool re-lent, and still reads rows off it, so the
     * refusal below cannot be the cursor having been closed for some
     * unrelated reason.
     */
    @Test
    void aNestedCursorHandleRetiresWithTheBorrowThatOpenedIt() throws Exception {
        DataSource ds = cursorPool();

        Connection first = ds.getConnection();
        setSessionMarker(first, 5250);
        CallableStatement cs = first.prepareCall("CALL 1");
        cs.execute();
        ResultSet outer = (ResultSet) cs.getObject(CURSOR_COLUMN_PARAMETER);
        ResultSet nested = (ResultSet) outer.getObject(CURSOR_COLUMN);
        ResultSet driverNested = (ResultSet) outer.unwrap(DriverCursor.class);
        Assertions.assertTrue(nested.next(), "the nested cursor is live during the borrow");
        first.close(); // returned to the pool

        Connection second = ds.getConnection();
        Assertions.assertEquals(Integer.valueOf(5250), sessionMarker(second),
                "precondition: the pool re-armed the same physical session");
        Assertions.assertFalse(driverNested.isClosed(),
                "precondition: the driver's own nested cursor is still open on that session");
        Assertions.assertTrue(driverNested.next(),
                "precondition: the driver's own nested cursor still reads rows off the session, "
                        + "which is exactly what the handle must not do");

        Assertions.assertTrue(nested.isClosed(),
                "a nested cursor whose borrow ended must report itself closed");
        SQLException thrown = Assertions.assertThrows(SQLException.class, nested::next,
                "a nested cursor whose borrow ended must not keep streaming on the next "
                        + "borrower's session");
        Assertions.assertTrue(thrown.getMessage().contains(
                "result set was read from has been returned to the pool"),
                "the failure must be the facade's retirement wording, but said: "
                        + thrown.getMessage());
        Assertions.assertEquals(Integer.valueOf(5250), sessionMarker(second),
                "the next borrower's session must be untouched");

        // The current borrower keeps full use of its session.
        try (Statement st = second.createStatement();
                ResultSet rs = st.executeQuery("SELECT 1")) {
            Assertions.assertTrue(rs.next(), "the current borrower must get working handles");
        }
        nested.close(); // the no-op a retired handle's close() is
        nested.close();
        driverNested.close();
        second.close();
    }

    /**
     * The documented escape hatch one layer down, pinned at the point where
     * the deeper rule could break it: this driver's cursor type implements
     * ResultSet, so a blanket rule at the result set layer would silently
     * wrap what unwrap() hands back. Reaching the driver's nested cursor
     * through unwrap() on a result set handle must still arrive at the
     * driver's very own object - never a proxy, the same object on repeated
     * calls, still usable - and so must unwrap() through the nested handle
     * itself.
     */
    @Test
    void unwrapAtTheResultSetLayerStillHandsBackTheDriversOwnCursor() throws Exception {
        DataSource ds = cursorPool();
        try (Connection pooled = ds.getConnection();
                CallableStatement cs = pooled.prepareCall("CALL 1")) {
            cs.execute();
            ResultSet outer = (ResultSet) cs.getObject(CURSOR_COLUMN_PARAMETER);
            Assertions.assertTrue(outer.isWrapperFor(DriverCursor.class),
                    "the provider must answer its own cursor interface honestly, as a driver "
                            + "does");

            Object nested = outer.unwrap(DriverCursor.class);

            Assertions.assertTrue(nested instanceof ResultSet,
                    "precondition: the object reached by unwrapping implements ResultSet");
            Assertions.assertFalse(Proxy.isProxyClass(nested.getClass()),
                    "unwrap() on a result set handle must hand back the driver's object, not a "
                            + "pool handle: " + nested.getClass().getName());
            Assertions.assertTrue(nested.getClass().getName().startsWith("org.h2."),
                    "unwrap() must arrive at the driver object through every layer: "
                            + nested.getClass().getName());
            Assertions.assertSame(nested, outer.unwrap(DriverCursor.class),
                    "a repeated unwrap() must return the same object, not a new wrapper");

            // The same carve-out holds one layer further in: what the nested
            // handle's unwrap() reaches is the driver's nested cursor, the
            // object the sealed handle happens to be made of.
            ResultSet handle = (ResultSet) outer.getObject(CURSOR_COLUMN);
            Assertions.assertSame(nested, handle.unwrap(ResultSet.class),
                    "unwrap() through the nested handle must still arrive at the driver object");
            Assertions.assertTrue(((ResultSet) nested).next(),
                    "what the escape hatch hands back must still work");
            handle.close();
        }
    }

    /**
     * The deeper rule fires on the return type, so it must leave everything
     * else the same getObject(...) family hands back alone: an ordinary
     * column value, in the plain and in the typed overload, a conversion of
     * the cursor column that the driver answers with its plain value, and
     * the result set metadata, which is no result set at all.
     */
    @Test
    void aPlainColumnValuePassesThroughTheCursorColumnUnchanged() throws Exception {
        DataSource ds = cursorPool();
        try (Connection pooled = ds.getConnection();
                CallableStatement cs = pooled.prepareCall("CALL 1")) {
            cs.execute();
            ResultSet outer = (ResultSet) cs.getObject(CURSOR_COLUMN_PARAMETER);
            Assertions.assertTrue(outer.next(), "the cursor that owns the column is live");

            Object plain = outer.getObject(PLAIN_COLUMN);
            Assertions.assertFalse(Proxy.isProxyClass(plain.getClass()),
                    "an ordinary column value must reach the application untouched, was "
                            + plain.getClass().getName());
            Assertions.assertEquals(1, ((Number) plain).intValue());
            Assertions.assertEquals(Integer.valueOf(1),
                    outer.getObject(PLAIN_COLUMN, Integer.class),
                    "the typed overload must pass the plain value through unchanged too");
            Assertions.assertEquals(PLAIN_COLUMN_VALUE,
                    outer.getObject(CURSOR_COLUMN, String.class),
                    "a conversion the driver answers with a value must not become a cursor");

            ResultSetMetaData meta = outer.getMetaData();
            Assertions.assertFalse(Proxy.isProxyClass(meta.getClass()),
                    "metadata is not a result set and must not be sealed: "
                            + meta.getClass().getName());
            Assertions.assertEquals(2, meta.getColumnCount(),
                    "the metadata must still answer through the handle");
        }
    }
}
