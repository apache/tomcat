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

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;
import org.apache.tomcat.jdbc.pool.DataSource;

/**
 * A nested cursor - a REF cursor selected as a column - reaches the
 * application through the {@code ResultSet.getObject(...)} overloads of the
 * result set handle that owns the column, which come back as a plain
 * {@link Object}. It is read row by row over the same physical session as the
 * cursor that handed it out, so the {@code ResetAbandonedTimer} interceptor
 * must keep the abandoned timer alive while it is read. The statement layer
 * already wraps every result set a call hands back, whatever the call was
 * called; the result set layer wrapped none of the ones a read hands back,
 * and the always installed statement facade then wrapped the driver's
 * untouched cursor, so an application iterating a cursor-valued column with
 * {@code removeAbandoned} enabled was abandoned mid-iteration. The pins below
 * are the interceptor counterpart, one layer down, of the facade's and the
 * statement layer's return-type rule (see {@code
 * TestCursorOutParameterSealing} and {@link
 * TestResetAbandonedTimerCursorOutParameter}): whatever a result set read
 * hands back that is a {@code java.sql.ResultSet} reaches the application as
 * the facade's handle over exactly one handle of this interceptor over the
 * driver's cursor, while what the {@code Wrapper} methods hand back stays the
 * driver's object and a plain column value stays itself.
 *
 * <p>H2 has no cursor-valued columns, so the pool here is fed by a small
 * delegating provider: a designated column of the result set its callable
 * statements hand out answers {@code getObject(...)} with a second real H2
 * result set, opened on the very physical session the pool borrowed, the way
 * a nested cursor reaches the application on the drivers that have them.</p>
 */
class TestResetAbandonedTimerNestedCursor extends JdbcPoolBaseTest {

    /*
     * Time the busy connection is kept streaming rows off the nested cursor.
     * It must stay well above removeAbandonedTimeout plus one sweep interval
     * so that the idle control connection - and, while the nested reads did
     * not reset the timer, the streaming one too - is certain to expire while
     * the iteration is still running.
     */
    private static final long STREAM_MILLIS = 2500;

    /*
     * Lower bound on streamed rows. An app iterating rows between the
     * per-row pauses makes far more than this; the bound only catches an
     * iteration that never really ran.
     */
    private static final int MIN_STREAM_ROWS = 20;

    /*
     * The parameter this provider's callable statements answer with the
     * cursor that owns the cursor-valued column, mirroring what a driver
     * with a REF cursor selected as a column hands the application.
     */
    private static final int CURSOR_COLUMN_PARAMETER = 1;

    /*
     * The column of that cursor which holds a nested cursor rather than a
     * value, and its plain neighbour, so the pins can tell the two apart.
     * The row count and the fetch size of the nested cursor keep the reads
     * pulling from the server across the whole iteration, so the iteration
     * stands for streaming a genuine server-side cursor.
     */
    private static final int CURSOR_COLUMN = 2;
    private static final int PLAIN_COLUMN = 1;
    private static final String CURSOR_COLUMN_NAME = "CURSOR_VALUE";
    private static final String PLAIN_COLUMN_VALUE = "not a cursor";
    private static final String CURSOR_CALL = "CALL 1";
    private static final String CURSOR_COLUMN_SQL = "SELECT X, '" + PLAIN_COLUMN_VALUE
            + "' " + CURSOR_COLUMN_NAME + " FROM SYSTEM_RANGE(1,3)";
    private static final String NESTED_CURSOR_SQL = "SELECT X FROM SYSTEM_RANGE(101,100000)";
    private static final int NESTED_CURSOR_FIRST_ROW = 101;
    private static final int NESTED_FETCH_SIZE = 10;

    /* The batch processing between the reads, as any per-row work stands for. */
    private static final long READ_PAUSE_MILLIS = 10;

    /*
     * Stands for the driver's own cursor interface. A real driver's cursor
     * class implements such an interface and implements ResultSet, and an
     * application that needs the driver-only cursor methods reaches it with
     * unwrap(); a pooled handle must hand that object back untouched rather
     * than a proxy of its own, whatever rule decides the wrapping.
     */
    public interface DriverCursor {
    }

    /*
     * A connection provider shaped like a driver with cursor-valued
     * columns: its callable statements hand the cursor that owns the column
     * - a real H2 result set on the very physical session the pool holds -
     * to getObject(...), that cursor hands its designated column out as a
     * second real H2 result set, and every other call goes straight to the
     * H2 objects beneath.
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
        public Connection getConnection(String username, String password) throws SQLException {
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
     * cursor-column behaviour below. Everything else - createStatement,
     * prepareStatement, close, the pool's own setup calls and the session
     * marker probes - goes to the H2 connection unchanged.
     */
    private static final class ConnectionHandler implements InvocationHandler {

        private final Connection physical;

        ConnectionHandler(Connection physical) {
            this.physical = physical;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            try {
                Object result = method.invoke(physical, args);
                if ("prepareCall".equals(method.getName())) {
                    return Proxy.newProxyInstance(
                            CursorProvider.class.getClassLoader(),
                            new Class<?>[] { CallableStatement.class },
                            new CallableStatementHandler(physical, (CallableStatement) result));
                }
                return result;
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        }
    }

    /*
     * The fictional cursor behaviour, and nothing else: getObject(...) for
     * the designated parameter returns the session's H2 result set that has
     * a cursor-valued column - its rows are H2's own, only the cursor column
     * is the fiction, and that lives in the handler below. Every other call
     * is forwarded to the H2 callable statement.
     */
    private static final class CallableStatementHandler implements InvocationHandler {

        private final Connection physical;
        private final CallableStatement delegate;
        /* Kept open with the cursor: closing the owner would close it. */
        private Statement cursorColumnOwner;
        private ResultSet cursorColumnCursor;

        CallableStatementHandler(Connection physical, CallableStatement delegate) {
            this.physical = physical;
            this.delegate = delegate;
        }

        /*
         * The session's cursor whose column holds a cursor, opened on the
         * physical session once.
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
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String name = method.getName();
            try {
                if ("getObject".equals(name) && args != null && args.length >= 1
                        && args[0] instanceof Integer) {
                    int parameter = ((Integer) args[0]).intValue();
                    if (parameter == CURSOR_COLUMN_PARAMETER) {
                        if (args.length == 1) {
                            return cursorWithCursorColumn();
                        }
                        if (args[1] instanceof Class
                                && ((Class<?>) args[1]).isInstance(cursorWithCursorColumn())) {
                            return cursorWithCursorColumn();
                        }
                    }
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
                nestedOwner.setFetchSize(NESTED_FETCH_SIZE);
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
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
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
                    @SuppressWarnings("null")
                    Class<?> iface = (Class<?>) args[0];
                    if (DriverCursor.class.equals(iface)) {
                        return nestedCursor();
                    }
                    return delegate.unwrap(iface);
                } else if ("isWrapperFor".equals(name)) {
                    @SuppressWarnings("null")
                    Class<?> iface = (Class<?>) args[0];
                    return Boolean.valueOf(DriverCursor.class.equals(iface) || delegate.isWrapperFor(iface));
                }
                return method.invoke(delegate, args);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        }
    }

    /*
     * A pool that serves its connections through the cursor provider and
     * abandons an idle one after a second, swept every 100ms - the same
     * setting the other abandoned-timer tests drive.
     */
    private DataSource nestedTimerPool() throws SQLException {
        DataSource ds = newDataSource();
        ds.setDataSource(new CursorProvider(getUrl()));
        ds.getPoolProperties().setJdbcInterceptors(
                "org.apache.tomcat.jdbc.pool.interceptor.ResetAbandonedTimer");
        ds.getPoolProperties().setRemoveAbandoned(true);
        ds.getPoolProperties().setRemoveAbandonedTimeout(1); // seconds
        ds.getPoolProperties().setTimeBetweenEvictionRunsMillis(100);
        ds.createPool();
        pool = ds;
        return ds;
    }

    /* The pool under test, for forced teardown. */
    private DataSource pool;

    /*
     * These tests deliberately end with connections still borrowed. The
     * regular data source teardown only disconnects idle connections, so the
     * sessions of the borrowed ones would linger until the garbage collector
     * finalizes them and leak their session counts into the starting point of
     * the next test; force-close disconnects the busy ones as well, right
     * away.
     */
    @AfterEach
    void disconnectLeakedBusyConnections() {
        if (pool != null) {
            pool.close(true);
        }
    }

    /*
     * Borrows an idle control connection - left borrowed and untouched, it
     * exists only to prove the abandoned sweep is running - and a busy
     * connection, each identified by a session marker, and pins the session
     * count to two while both are still live. The caller must have waited out
     * any sessions dying from a previous test first: with a one-second
     * abandoned timeout there is no time to absorb stale sessions once these
     * connections have started their countdown. Returns the busy connection.
     */
    private Connection borrowBusyWithIdleControl(DataSource ds) throws SQLException {
        Connection idle = ds.getConnection();
        setSessionMarker(idle, 2);
        Connection busy = ds.getConnection();
        setSessionMarker(busy, 1);
        Assertions.assertEquals(2, poolSessions(),
                "both borrowed connections must start on live physical sessions");
        return busy;
    }

    /*
     * The busy connection ends with one live physical session carrying the
     * marker it was given before the iteration, and the idle control
     * connection has lost its session to the abandoned sweep.
     */
    private void assertBusySurvivedAndIdleAbandoned(Connection busy) throws SQLException {
        Integer marker = sessionMarker(busy);
        Assertions.assertNotNull(marker, "the session of the busy connection must still be alive");
        Assertions.assertEquals(1, marker.intValue(), "the busy connection must still own its original session");
        awaitSessions(1);
    }

    /*
     * The composition the keep-alive depends on, pinned without waiting on a
     * sweep: the nested cursor handed back by a result set read must reach
     * the application as the facade's handle over exactly one handle of this
     * interceptor over the driver's own cursor - the same three-layer shape
     * the statement layer's rule gives a cursor OUT-parameter, and no layer
     * of it doubled.
     */
    private static void assertTimerHandleBeneathTheFacade(ResultSet handle, Object driverCursor,
            String path) throws ReflectiveOperationException {
        Assertions.assertTrue(Proxy.isProxyClass(handle.getClass()),
                path + ": the application's result set must be a handle, was " + handle.getClass().getName());
        Assertions.assertTrue(Proxy.getInvocationHandler(handle).getClass().getName()
                .contains("StatementFacade"),
                path + ": the outermost handle must be the facade's, found "
                        + Proxy.getInvocationHandler(handle).getClass().getName());
        Object interceptorHandle = proxiedObject(handle);
        Assertions.assertTrue(Proxy.isProxyClass(interceptorHandle.getClass()),
                path + ": the facade must sit over this interceptor's own handle, found "
                        + interceptorHandle.getClass().getName());
        Assertions.assertTrue(Proxy.getInvocationHandler(interceptorHandle).getClass().getName()
                .contains(ResetAbandonedTimer.class.getSimpleName()),
                path + ": the handle beneath the facade must be this interceptor's, found "
                        + Proxy.getInvocationHandler(interceptorHandle).getClass().getName());
        Object driver = proxiedObject(interceptorHandle);
        Assertions.assertFalse(Proxy.isProxyClass(driver.getClass()),
                path + ": this interceptor must wrap the driver's cursor once, not its own handle again");
        Assertions.assertSame(driverCursor, driver,
                path + ": the handle must wrap exactly this session's driver cursor");
    }

    /*
     * Iterates the nested cursor this pool's result set handles hand out
     * through getObject(int) with a pause between the reads - the batch
     * processing between fetches that the abandoned timeout is meant to look
     * over - for STREAM_MILLIS, and returns the number of rows read.
     */
    private int streamNestedCursorSlowly(Connection busy) throws SQLException {
        int rows = 0;
        try (CallableStatement cs = busy.prepareCall(CURSOR_CALL)) {
            cs.execute();
            try (ResultSet outer = (ResultSet) cs.getObject(CURSOR_COLUMN_PARAMETER)) {
                Assertions.assertTrue(outer.next(), "the cursor that owns the column is live");
                try (ResultSet nested = (ResultSet) outer.getObject(CURSOR_COLUMN)) {
                    long deadline = System.currentTimeMillis() + STREAM_MILLIS;
                    while (System.currentTimeMillis() < deadline && nested.next()) {
                        rows++;
                        Assertions.assertEquals(NESTED_CURSOR_FIRST_ROW + rows - 1, nested.getInt(1),
                                "the nested cursor read through getObject must hand out its own rows");
                        sleep(READ_PAUSE_MILLIS);
                    }
                }
            }
        }
        return rows;
    }

    /**
     * An application streaming a nested cursor is active for as long as it
     * reads it, exactly as it is when it iterates the cursor that handed the
     * column out. The abandoned timer must be reset by the reads made
     * through the nested cursor - through the always installed statement
     * facade, over the interceptor's own handle, over the driver's cursor -
     * and not only until the getObject(...) that produced it returned.
     */
    @Test
    void streamingANestedCursorKeepsConnectionAlive() throws Exception {
        awaitSessions(0);
        DataSource ds = nestedTimerPool();
        Connection busy = borrowBusyWithIdleControl(ds);

        int rows = streamNestedCursorSlowly(busy);
        Assertions.assertTrue(rows >= MIN_STREAM_ROWS,
                "the streaming loop must have run, only " + rows + " rows");

        assertBusySurvivedAndIdleAbandoned(busy);
    }

    /**
     * The nested cursor arrives through {@code getObject(int)}, the named
     * and the typed overloads alike, and every one of them must produce the
     * interceptor's handle beneath the facade's rather than the facade
     * standing directly over the driver's raw cursor, so that reading it
     * keeps this borrow alive. It must still behave like every other handle
     * while it is read: the application's own statement as its statement,
     * and its own rows coming through. The calls the statement layer already
     * wrapped keep exactly the same composition, which is to say the result
     * set layer's type rule adds a layer to the nested cursor without
     * doubling one anywhere.
     */
    @Test
    void aNestedCursorArrivesAsATimerHandleBeneathTheFacade() throws Exception {
        DataSource ds = nestedTimerPool();
        try (Connection pooled = ds.getConnection()) {
            try (CallableStatement cs = pooled.prepareCall(CURSOR_CALL)) {
                cs.execute();
                ResultSet outer = (ResultSet) cs.getObject(CURSOR_COLUMN_PARAMETER);
                Assertions.assertTrue(Proxy.getInvocationHandler(outer).getClass().getName()
                        .contains("StatementFacade"),
                        "the cursor that owns the column must arrive as the facade's handle");
                Object outerBeneath = proxiedObject(outer);
                Assertions.assertTrue(Proxy.isProxyClass(outerBeneath.getClass())
                        && Proxy.getInvocationHandler(outerBeneath).getClass().getName()
                                .contains(ResetAbandonedTimer.class.getSimpleName()),
                        "the facade must sit over this interceptor's own handle");
                Assertions.assertTrue(outer.next(), "the cursor that owns the column is live");
                Object driverNested = outer.unwrap(DriverCursor.class);
                Assertions.assertTrue(driverNested instanceof ResultSet,
                        "precondition: the driver's nested cursor implements ResultSet");

                ResultSet nested = (ResultSet) outer.getObject(CURSOR_COLUMN);
                assertTimerHandleBeneathTheFacade(nested, driverNested, "getObject(int)");
                Assertions.assertSame(cs, nested.getStatement(),
                        "the handle must report the application's own statement");
                Assertions.assertTrue(nested.next(), "the nested cursor is live during the borrow");
                Assertions.assertEquals(NESTED_CURSOR_FIRST_ROW, nested.getInt(1),
                        "the nested cursor's own rows must come through the handle");
            }

            // The named and typed overloads carry the column too, and a
            // return-type rule has to see every one of them.
            try (CallableStatement cs = pooled.prepareCall(CURSOR_CALL)) {
                cs.execute();
                ResultSet outer = (ResultSet) cs.getObject(CURSOR_COLUMN_PARAMETER);
                Assertions.assertTrue(outer.next(), "the cursor that owns the column is live");
                Object driverNested = outer.unwrap(DriverCursor.class);

                assertTimerHandleBeneathTheFacade(
                        (ResultSet) outer.getObject(CURSOR_COLUMN_NAME), driverNested, "getObject(String)");
                assertTimerHandleBeneathTheFacade(
                        (ResultSet) outer.getObject(CURSOR_COLUMN, new HashMap<>()), driverNested,
                        "getObject(int, Map)");
                assertTimerHandleBeneathTheFacade(
                        outer.getObject(CURSOR_COLUMN, ResultSet.class), driverNested, "getObject(int, Class)");
            }

            try (Statement st = pooled.createStatement();
                    ResultSet rs = st.executeQuery(NESTED_CURSOR_SQL)) {
                assertTimerHandleBeneathTheFacade(rs, rs.unwrap(ResultSet.class), "executeQuery");
                Assertions.assertTrue(rs.next());
                Assertions.assertEquals(NESTED_CURSOR_FIRST_ROW, rs.getInt(1));
            }
        }
    }

    /**
     * The documented escape hatch, pinned one layer down at exactly the
     * point where a wrap-by-return-type rule could break it: this driver's
     * nested cursor implements ResultSet, so a blanket rule would silently
     * wrap what {@code unwrap()} hands back - and with the interceptor
     * installed the driver's object passes through this layer, not only
     * through the facade's. {@code unwrap()} on a result set handle must
     * still arrive at the driver's very own nested cursor, and so must
     * {@code unwrap()} through the nested handle itself.
     */
    @Test
    void unwrapAtTheResultSetLayerStillHandsBackTheDriversOwnCursor() throws Exception {
        DataSource ds = nestedTimerPool();
        try (Connection pooled = ds.getConnection();
                CallableStatement cs = pooled.prepareCall(CURSOR_CALL)) {
            cs.execute();
            ResultSet outer = (ResultSet) cs.getObject(CURSOR_COLUMN_PARAMETER);
            Assertions.assertTrue(outer.isWrapperFor(DriverCursor.class),
                    "the provider must answer its own cursor interface honestly, as a driver does");

            Object nested = outer.unwrap(DriverCursor.class);

            Assertions.assertTrue(nested instanceof ResultSet,
                    "precondition: the object reached by unwrapping implements ResultSet");
            Assertions.assertFalse(Proxy.isProxyClass(nested.getClass()),
                    "unwrap() must hand back the driver's object, not a handle: " + nested.getClass().getName());
            Assertions.assertTrue(nested.getClass().getName().startsWith("org.h2."),
                    "unwrap() must arrive at the driver object through every layer: "
                            + nested.getClass().getName());
            Assertions.assertSame(nested, outer.unwrap(DriverCursor.class),
                    "a repeated unwrap() must return the same object, not a new wrapper");

            // The same carve-out holds one layer further in: what the nested
            // handle's unwrap() reaches is the driver's nested cursor, the
            // object the interceptor's handle happens to be made of.
            ResultSet handle = outer.getObject(CURSOR_COLUMN, ResultSet.class);
            Assertions.assertSame(nested, handle.unwrap(ResultSet.class),
                    "unwrap() through the nested handle must still arrive at the driver object");
            Assertions.assertTrue(((ResultSet) nested).next(),
                    "what the escape hatch hands back must still work");
            handle.close();
        }
    }

    /**
     * The rule fires on the return type, so it must leave everything the
     * same {@code getObject(...)} family hands back alone: an ordinary
     * column value, in the plain and in the typed overload, a conversion of
     * the cursor column that the driver answers with its plain value, and
     * the result set metadata, which is no result set at all.
     */
    @Test
    void aPlainColumnValuePassesThroughTheNestedCursorRuleUnchanged() throws Exception {
        DataSource ds = nestedTimerPool();
        try (Connection pooled = ds.getConnection();
                CallableStatement cs = pooled.prepareCall(CURSOR_CALL)) {
            cs.execute();
            ResultSet outer = (ResultSet) cs.getObject(CURSOR_COLUMN_PARAMETER);
            Assertions.assertTrue(outer.next(), "the cursor that owns the column is live");

            Object plain = outer.getObject(PLAIN_COLUMN);
            Assertions.assertFalse(Proxy.isProxyClass(plain.getClass()),
                    "an ordinary column value must reach the application untouched, was "
                            + plain.getClass().getName());
            Assertions.assertEquals(1, ((Number) plain).intValue());
            Assertions.assertEquals(Integer.valueOf(1), outer.getObject(PLAIN_COLUMN, Integer.class),
                    "the typed overload must pass the plain value through unchanged too");
            Assertions.assertEquals(PLAIN_COLUMN_VALUE, outer.getObject(CURSOR_COLUMN, String.class),
                    "a conversion the driver answers with a value must not become a cursor");

            ResultSetMetaData meta = outer.getMetaData();
            Assertions.assertFalse(Proxy.isProxyClass(meta.getClass()),
                    "metadata is not a result set and must not be wrapped: " + meta.getClass().getName());
            Assertions.assertEquals(2, meta.getColumnCount(), "the metadata must still answer through the handle");
        }
    }
}
