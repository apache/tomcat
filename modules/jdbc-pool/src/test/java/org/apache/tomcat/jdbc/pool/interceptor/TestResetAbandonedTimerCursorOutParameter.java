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
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Logger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;
import org.apache.tomcat.jdbc.pool.DataSource;

/**
 * A result set that reaches the application as a plain {@link Object} - a
 * cursor OUT-parameter read through {@link CallableStatement#getObject(int)}
 * or one of its overloads, the way a REF cursor arrives on the drivers that
 * have them - is read row by row over the same physical session as the ones
 * {@code executeQuery}, {@code getResultSet} and {@code getGeneratedKeys}
 * hand back, so the {@code ResetAbandonedTimer} interceptor must keep the
 * abandoned timer alive while it is read. Deciding what to wrap by the name
 * of the invoked method sees only the three calls the standard API names and
 * misses this one, and the application iterating the cursor was abandoned
 * mid-iteration exactly as before the streaming keep-alive existed. The pins
 * below are the interceptor counterpart of the facade's return-type rule
 * (see {@code TestCursorOutParameterSealing}): whatever call hands back a
 * {@code java.sql.ResultSet} gets the interceptor's handle, exactly one layer
 * of it beneath the facade's own, while what the {@code Wrapper} methods hand
 * back stays the driver's object and a plain OUT-parameter value stays itself.
 *
 * <p>H2 has no cursor OUT-parameter feature, so the pool here is fed by a
 * small delegating provider: its callable statements answer
 * {@code getObject(...)} for a designated parameter with a real H2 result set
 * opened on the very physical session the pool borrowed, the way an Oracle REF
 * cursor or a PostgreSQL refcursor reaches the application.</p>
 */
class TestResetAbandonedTimerCursorOutParameter extends JdbcPoolBaseTest {

    /*
     * Time the busy connection is kept streaming rows off the cursor. It
     * must stay well above removeAbandonedTimeout plus one sweep interval so
     * that the idle control connection - and, while the cursor reads did not
     * reset the timer, the streaming one too - is certain to expire while the
     * iteration is still running.
     */
    private static final long STREAM_MILLIS = 2500;

    /*
     * Lower bound on streamed rows. An app iterating rows between the
     * per-row pauses makes far more than this; the bound only catches an
     * iteration that never really ran.
     */
    private static final int MIN_STREAM_ROWS = 20;

    /*
     * The parameter this provider's callable statements answer with a cursor,
     * and the one they answer with a plain value, mirroring what a driver with
     * a REF cursor OUT-parameter and an ordinary OUT-parameter does for
     * CallableStatement.getObject(...).
     */
    private static final int CURSOR_PARAMETER = 1;
    private static final int VALUE_PARAMETER = 2;
    private static final Integer OUT_VALUE = Integer.valueOf(1234);

    /*
     * The call the provider's statements are prepared with, and the cursor it
     * hands out. The row count and the fetch size keep the reads pulling from
     * the server across the whole iteration, so the iteration stands for
     * streaming a genuine server-side cursor.
     */
    private static final String CURSOR_CALL = "CALL 1";
    private static final String CURSOR_SQL = "SELECT X FROM SYSTEM_RANGE(1,100000)";
    private static final int CURSOR_FETCH_SIZE = 10;

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
     * Intercepts prepareCall to wrap the H2 callable statement in the cursor
     * behaviour below. Everything else - createStatement, prepareStatement,
     * close, the pool's own setup calls and the session marker probes - goes
     * to the H2 connection unchanged.
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
     * The fictional cursor behaviour, and nothing else: getObject(...) for the
     * cursor parameter returns the session's H2 result set - the same object
     * every request and an unwrap(DriverCursor.class) hands back - and
     * getObject(...) for the value parameter returns the OUT-parameter value.
     * Every other call is forwarded to the H2 callable statement.
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
                cursorOwner.setFetchSize(CURSOR_FETCH_SIZE);
                cursor = cursorOwner.executeQuery(CURSOR_SQL);
            }
            return cursor;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String name = method.getName();
            try {
                if ("getObject".equals(name) && args != null && args.length >= 1
                        && args[0] instanceof Integer) {
                    int parameter = ((Integer) args[0]).intValue();
                    if (parameter == CURSOR_PARAMETER) {
                        if (args.length == 1) {
                            return cursor();
                        }
                        if (args[1] instanceof Class && ((Class<?>) args[1]).isInstance(cursor())) {
                            return cursor();
                        }
                    } else if (parameter == VALUE_PARAMETER
                            && (args.length == 1 || ((Class<?>) args[1]).isInstance(OUT_VALUE))) {
                        return OUT_VALUE;
                    }
                } else if ("unwrap".equals(name)) {
                    Class<?> iface = (Class<?>) args[0];
                    if (DriverCursor.class.equals(iface)) {
                        // A driver's own cursor method hands back its cursor
                        // object, which implements ResultSet; this provider
                        // answers such a reach the same way, and the
                        // interceptor must not put a proxy of its own in
                        // front of it.
                        return cursor();
                    }
                    return delegate.unwrap(iface);
                } else if ("isWrapperFor".equals(name)) {
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
    private DataSource cursorTimerPool() throws SQLException {
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
     * Iterates the cursor this pool's callable statements hand out through
     * getObject(int) with a pause between the reads - the batch processing
     * between fetches that the abandoned timeout is meant to look over - for
     * STREAM_MILLIS, and returns the number of rows read.
     */
    private int streamCursorSlowly(Connection busy) throws SQLException {
        int rows = 0;
        try (CallableStatement cs = busy.prepareCall(CURSOR_CALL)) {
            cs.execute();
            try (ResultSet cursor = (ResultSet) cs.getObject(CURSOR_PARAMETER)) {
                long deadline = System.currentTimeMillis() + STREAM_MILLIS;
                while (System.currentTimeMillis() < deadline && cursor.next()) {
                    rows++;
                    Assertions.assertEquals(rows, cursor.getInt(1),
                            "the cursor read through getObject must hand out its own rows");
                    sleep(READ_PAUSE_MILLIS);
                }
            }
        }
        return rows;
    }

    /*
     * An application streaming a REF cursor is active for as long as it reads
     * it, exactly as it is when it iterates a result set that
     * {@code executeQuery} returned. The abandoned timer must be reset by the
     * reads made through the cursor OUT-parameter - through the always
     * installed statement facade, over the interceptor's own handle, over the
     * driver's cursor - and not only until {@code getObject} returned.
     */
    @Test
    void streamingACursorOutParameterKeepsConnectionAlive() throws Exception {
        awaitSessions(0);
        DataSource ds = cursorTimerPool();
        Connection busy = borrowBusyWithIdleControl(ds);

        int rows = streamCursorSlowly(busy);
        Assertions.assertTrue(rows >= MIN_STREAM_ROWS,
                "the streaming loop must have run, only " + rows + " rows");

        assertBusySurvivedAndIdleAbandoned(busy);
    }

    /*
     * The composition the keep-alive above depends on, pinned without waiting
     * on a sweep: the cursor handed back by getObject(...) must reach the
     * application as the facade's handle over exactly one handle of this
     * interceptor over the driver's own cursor - the same three-layer shape the
     * three named calls have, and no layer of it doubled.
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

    /**
     * The cursor OUT-parameter arrives through {@code getObject(int)} and
     * through the typed {@code getObject(int, Class)} of JDBC 4.0, and both
     * must produce the interceptor's handle rather than the driver's raw
     * result set, so that reading it keeps this borrow alive. It must still
     * behave like every other handle while it is read: the application's own
     * statement as its statement, and its own rows coming through. The three
     * calls the name rule did wrap keep exactly the same composition, which is
     * to say the type rule adds a layer to the cursor without doubling one
     * anywhere.
     */
    @Test
    void aCursorOutParameterArrivesAsATimerHandleBeneathTheFacade() throws Exception {
        DataSource ds = cursorTimerPool();
        try (Connection pooled = ds.getConnection()) {
            try (CallableStatement cs = pooled.prepareCall(CURSOR_CALL)) {
                cs.execute();
                Object driverCursor = cs.unwrap(DriverCursor.class);
                Assertions.assertTrue(driverCursor instanceof ResultSet,
                        "precondition: the driver's cursor object implements ResultSet");

                ResultSet cursor = (ResultSet) cs.getObject(CURSOR_PARAMETER);
                assertTimerHandleBeneathTheFacade(cursor, driverCursor, "getObject(int)");
                Assertions.assertSame(cs, cursor.getStatement(),
                        "the handle must report the application's own statement");
                for (int expected = 1; expected <= 3; expected++) {
                    Assertions.assertTrue(cursor.next(), "the cursor is live during the borrow");
                    Assertions.assertEquals(expected, cursor.getInt(1),
                            "the cursor's own rows must come through the handle");
                }
            }

            try (CallableStatement cs = pooled.prepareCall(CURSOR_CALL)) {
                cs.execute();
                Object driverCursor = cs.unwrap(DriverCursor.class);

                ResultSet cursor = cs.getObject(CURSOR_PARAMETER, ResultSet.class);
                assertTimerHandleBeneathTheFacade(cursor, driverCursor, "getObject(int, Class)");
                Assertions.assertTrue(cursor.next(), "the cursor is live during the borrow");
            }

            try (Statement st = pooled.createStatement()) {
                try (ResultSet rs = st.executeQuery(CURSOR_SQL)) {
                    assertTimerHandleBeneathTheFacade(rs, rs.unwrap(ResultSet.class), "executeQuery");
                    Assertions.assertTrue(rs.next());
                    Assertions.assertEquals(1, rs.getInt(1));
                }

                Assertions.assertTrue(st.execute(CURSOR_SQL));
                try (ResultSet rs = st.getResultSet()) {
                    Assertions.assertNotNull(rs, "the query result must be reachable through getResultSet");
                    assertTimerHandleBeneathTheFacade(rs, rs.unwrap(ResultSet.class), "getResultSet");
                    Assertions.assertTrue(rs.next());
                }
            }
        }
    }

    /**
     * The documented escape hatch, pinned at exactly the point where a
     * wrap-by-return-type rule could break it: this driver's cursor type
     * implements ResultSet, so a blanket rule would silently wrap what
     * {@code unwrap()} hands back - and with the interceptor installed the
     * driver's object passes through this layer, not only through the facade's.
     * {@code unwrap()} must still arrive at the driver's very own cursor
     * object: never a proxy, the same object on repeated calls, still usable.
     */
    @Test
    void unwrapStillHandsBackTheDriversOwnCursorThroughTheInterceptor() throws Exception {
        DataSource ds = cursorTimerPool();
        try (Connection pooled = ds.getConnection();
                CallableStatement cs = pooled.prepareCall(CURSOR_CALL)) {
            cs.execute();
            Assertions.assertTrue(cs.isWrapperFor(DriverCursor.class),
                    "the provider must answer its own cursor interface honestly, as a driver does");

            Object cursor = cs.unwrap(DriverCursor.class);

            Assertions.assertTrue(cursor instanceof ResultSet,
                    "precondition: the object reached by unwrapping implements ResultSet");
            Assertions.assertFalse(Proxy.isProxyClass(cursor.getClass()),
                    "unwrap() must hand back the driver's object, not a handle: " + cursor.getClass().getName());
            Assertions.assertTrue(cursor.getClass().getName().startsWith("org.h2."),
                    "unwrap() must arrive at the driver object through every layer: "
                            + cursor.getClass().getName());
            Assertions.assertSame(cursor, cs.unwrap(DriverCursor.class),
                    "a repeated unwrap() must return the same object, not a new wrapper");

            try (ResultSet raw = (ResultSet) cursor) {
                Assertions.assertTrue(raw.next(),
                        "what the escape hatch hands back must still work");
            }
            Assertions.assertInstanceOf(org.h2.jdbc.JdbcCallableStatement.class, cs.unwrap(Statement.class),
                    "the ordinary statement unwrap must stay unaffected");
        }
    }

    /**
     * The rule fires on the return type, so it must leave everything the same
     * {@code getObject(...)} family hands back alone: an ordinary
     * OUT-parameter value passes through untouched, in the plain and the typed
     * overload alike, and an ordinary column read through a handle is not
     * touched either.
     */
    @Test
    void aPlainOutParameterPassesThroughTheInterceptorUnchanged() throws Exception {
        DataSource ds = cursorTimerPool();
        try (Connection pooled = ds.getConnection();
                CallableStatement cs = pooled.prepareCall(CURSOR_CALL)) {
            cs.execute();
            Assertions.assertSame(OUT_VALUE, cs.getObject(VALUE_PARAMETER),
                    "a plain OUT-parameter value must reach the application untouched");
            Assertions.assertSame(OUT_VALUE, cs.getObject(VALUE_PARAMETER, Object.class),
                    "the typed overload must pass the plain value through unchanged too");

            try (Statement st = pooled.createStatement();
                    ResultSet rs = st.executeQuery("SELECT 7")) {
                Assertions.assertTrue(rs.next());
                Assertions.assertEquals(7, rs.getInt(1), "an ordinary column read must be untouched");
                Object column = rs.getObject(1);
                Assertions.assertFalse(Proxy.isProxyClass(column.getClass()),
                        "an ordinary column value must not become a handle: " + column.getClass().getName());
            }
        }
    }
}
