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
import java.sql.SQLException;
import java.sql.Statement;
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
 */
class TestCursorOutParameterSealing extends JdbcPoolBaseTest {

    /*
     * The parameter this provider's callable statements answer with a
     * cursor, and the one they answer with a plain value, mirroring what a
     * driver with a REF cursor OUT-parameter and an ordinary OUT-parameter
     * does for CallableStatement.getObject(...).
     */
    private static final int CURSOR_PARAMETER = 1;
    private static final int VALUE_PARAMETER = 2;
    private static final Integer OUT_VALUE = Integer.valueOf(1234);
    private static final String CURSOR_SQL = "SELECT X FROM SYSTEM_RANGE(1,3)";

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
     * and getObject(...) for the value parameter returns the OUT-parameter
     * value. Every other call is forwarded to the H2 callable statement.
     */
    private static final class CallableStatementHandler implements InvocationHandler {

        private final Connection physical;
        private final CallableStatement delegate;
        /* Kept open with the cursor: closing the owner would close it. */
        private Statement cursorOwner;
        private ResultSet cursor;

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
}
