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
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Wrapper;

import javax.management.ObjectName;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.jdbc.pool.ConnectionPool;
import org.apache.tomcat.jdbc.pool.PooledConnection;
import org.apache.tomcat.jdbc.pool.jmx.JmxUtil;

/**
 * Class that resets the abandoned timer on any activity on the Connection, on any query execution and on any read of a
 * {@link java.sql.ResultSet} returned by a statement obtained from the pooled connection, whether the operation
 * completed successfully or failed. Iterating a large result set therefore keeps the connection alive through the fetch
 * phase, not only until the query returns. Each reset happens when an individual call completes, so a single call that
 * itself blocks on the database for longer than
 * {@link org.apache.tomcat.jdbc.pool.PoolConfiguration#setRemoveAbandonedTimeout(int)} still gets the connection
 * abandoned. The statement facade is always used, so a result set handle that outlives its borrow is sealed off before
 * it can go on refreshing the abandoned timer of whoever holds that physical connection next. The handles this
 * interceptor hands out release what they wrap - the statement layer, the physical result set - once a close has been
 * forwarded to it, and answer any later call from the closed contract, as the statement facade's own handles do; a
 * handle the application keeps beyond its close therefore stops keeping the physical objects below it reachable. This
 * interceptor is useful for when you have a
 * {@link org.apache.tomcat.jdbc.pool.PoolConfiguration#setRemoveAbandonedTimeout(int)} that is fairly low, and you
 * want to reset the abandoned time each time any operation on the connection is performed This is useful for batch
 * processing programs that use connections for extensive amount of times.
 */
public class ResetAbandonedTimer extends AbstractQueryReport implements ResetAbandonedTimerMBean {

    private static final Log log = LogFactory.getLog(ResetAbandonedTimer.class);

    private PooledConnection pcon;

    private ObjectName oname = null;

    /**
     * Default constructor.
     */
    public ResetAbandonedTimer() {
    }

    @Override
    public void reset(ConnectionPool parent, PooledConnection con) {
        super.reset(parent, con);
        if (con == null) {
            this.pcon = null;
            if (oname != null) {
                JmxUtil.unregisterJmx(oname);
                oname = null;
            }
        } else {
            this.pcon = con;
            if (oname == null) {
                String keyprop = ",JdbcInterceptor=" + getClass().getSimpleName();
                oname = JmxUtil.registerJmx(pcon.getObjectName(), keyprop, this);
            }
        }
    }

    @Override
    public boolean resetTimer() {
        boolean result = false;
        if (pcon != null) {
            pcon.setTimestamp(System.currentTimeMillis());
            result = true;
        }
        return result;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        try {
            return super.invoke(proxy, method, args);
        } finally {
            // The operation was attempted on the connection, which counts
            // as activity even when it failed
            resetTimer();
        }
    }

    /**
     * Wraps the query reporting statement proxy so that the result sets handed to the application reset the abandoned
     * timer while they are read. Statements that this interceptor does not proxy are passed through unchanged.
     */
    @Override
    public Object createStatement(Object proxy, Method method, Object[] args, Object statement, long time) {
        Object result = super.createStatement(proxy, method, args, statement, time);
        if (result == statement) {
            // Not one of the statement types we proxy, or the proxy
            // creation above failed - nothing to wrap
            return result;
        }
        try {
            String name = method.getName();
            Class<?> type = Statement.class;
            int index = CREATE_STATEMENT_IDX;
            if (compare(PREPARE_STATEMENT, name)) {
                type = PreparedStatement.class;
                index = PREPARE_STATEMENT_IDX;
            } else if (compare(PREPARE_CALL, name)) {
                type = CallableStatement.class;
                index = PREPARE_CALL_IDX;
            }
            return getConstructor(index, type).newInstance(new Object[] { new TimerStatementProxy(result) });
        } catch (Exception x) {
            log.warn("Unable to create timer statement proxy for " + getClass().getName() + ".", x);
        }
        return result;
    }

    @Override
    protected void prepareCall(String query, long time) {
        resetTimer();
    }

    @Override
    protected void prepareStatement(String sql, long time) {
        resetTimer();

    }

    @Override
    public void closeInvoked() {
        resetTimer();
    }

    @Override
    protected String reportFailedQuery(String query, Object[] args, String name, long start, long delta,
            Throwable t) {
        // The timer only needs to know that the execution was attempted, so
        // it takes no interest in the start or the duration it reports
        resetTimer();
        return super.reportFailedQuery(query, args, name, start, delta, t);
    }

    @Override
    protected String reportQuery(String query, Object[] args, String name, long start, long delta) {
        resetTimer();
        return super.reportQuery(query, args, name, start, delta);
    }

    @Override
    protected String reportSlowQuery(String query, Object[] args, String name, long start, long delta) {
        resetTimer();
        return super.reportSlowQuery(query, args, name, start, delta);
    }

    /**
     * Proxy for a statement that turns the result sets the statement hands out into {@link TimerResultSetProxy}
     * instances, whatever the method that returned one was called. Every other call is passed straight through to the
     * wrapped statement, and so is what the {@link java.sql.Wrapper} methods hand back: a driver object reached through
     * {@code unwrap()} stays the driver's own object, even one that implements {@link java.sql.ResultSet}.
     */
    private final class TimerStatementProxy implements InvocationHandler {
        /**
         * The statement proxy created by {@code AbstractQueryReport}, nulled
         * once a close has been forwarded so that a handle the application
         * keeps beyond its close stops pinning the statement layer beneath it.
         * A close can come from a thread other than the one invoking on the
         * handle - the pool's statement finalizer closes the statements it
         * tracks from whichever thread closes the connection - so the field is
         * read by more threads than the one that nulls it and, as in the
         * facade's and the query report proxy, must be volatile to reach them
         * at all.
         */
        private volatile Object delegate;

        TimerStatementProxy(Object delegate) {
            this.delegate = delegate;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            // Handle identity on this proxy, like the statement facade
            // does, rather than on the object below it: the application
            // must be able to rely on its own handles
            if (compare(TOSTRING_VAL, method)) {
                return toString();
            }
            if (compare(EQUALS_VAL, method)) {
                if (args[0] == null || !Proxy.isProxyClass(args[0].getClass())) {
                    return Boolean.FALSE;
                }
                return Boolean.valueOf(this.equals(Proxy.getInvocationHandler(args[0])));
            }
            if (compare(HASHCODE_VAL, method)) {
                return Integer.valueOf(this.hashCode());
            }
            // Read the delegate once for this call: the close that nulls it
            // can come from another thread, and the guards and the forward
            // must agree on the one value this call read. A call that finds
            // the delegate gone is answered from the closed contract below -
            // the same answers the query report proxy beneath gives for its
            // own closed state - rather than by invoking on a null receiver:
            // that is a NullPointerException, where the closed contract owes
            // the application a SQLException. The window this cannot close is
            // the race itself: a call that read the delegate while it was
            // still live goes to it, an outcome indistinguishable from having
            // simply outrun the close.
            final Object target = delegate;
            final boolean close = compare(CLOSE_VAL, method);
            // Allow close to be called multiple times, from either thread
            if (close && target == null) {
                return null;
            }
            if (compare(ISCLOSED_VAL, method) && target == null) {
                return Boolean.TRUE;
            }
            if (target == null) {
                throw new SQLException("Statement closed.");
            }
            Object result;
            try {
                // Execute the call on the delegate this call read as live:
                // the snapshot keeps a concurrent close from replacing the
                // receiver with null between the guards and the call
                result = method.invoke(target, args);
            } catch (InvocationTargetException x) {
                Throwable cause = x.getCause();
                if (cause == null) {
                    throw x;
                }
                throw cause;
            }
            // Perform close cleanup: the handle kept beyond this close now
            // holds nothing that can reach the statement beneath it
            if (close) {
                delegate = null;
            }
            // Wrap every result set the call hands back, whatever the method
            // that returned it was called, on the same rule the statement
            // facade uses: a decision made on the method name only sees the
            // three calls the standard API names, and misses the result sets
            // declared to come back as an Object - a cursor OUT-parameter
            // reaches the application through CallableStatement.getObject(...),
            // and iterating it keeps a cursor open on this connection just as
            // an executeQuery one does. The Wrapper methods are kept out of
            // this for the facade's reason: unwrap() is the documented way to
            // reach the driver's own object, and a driver's cursor type is
            // such an object, so what it hands back stays the driver's object
            // even where that object implements ResultSet.
            if (result instanceof ResultSet && method.getDeclaringClass() != Wrapper.class) {
                try {
                    return getConstructor(RESULTSET_IDX, ResultSet.class)
                            .newInstance(new Object[] { new TimerResultSetProxy(result, proxy) });
                } catch (Exception x) {
                    // The application just does not get the timer reset
                    // while reading this result set
                    log.warn("Unable to create timer result set proxy for " +
                            ResetAbandonedTimer.this.getClass().getName() + ".", x);
                }
            }
            return result;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(this);
        }

        @Override
        public boolean equals(Object obj) {
            return this == obj;
        }

        @Override
        public String toString() {
            return ResetAbandonedTimer.this.getClass().getName() + "[Proxy=" + hashCode() + "; Delegate=" + delegate +
                    ']';
        }
    }

    /**
     * Proxy for a result set that resets the abandoned timer on every read, so that an application iterating a result
     * set - or doing its per-row work between the reads - counts as active and is not abandoned mid-iteration.
     */
    private final class TimerResultSetProxy implements InvocationHandler {
        /**
         * The result set being read, nulled once a close has been forwarded so
         * that a handle the application keeps beyond its close stops pinning
         * the physical result set. Read by other threads than the one that
         * nulls it - a close can come from a foreign thread, as in the
         * statement proxy - so, as in the facade and the query report proxy,
         * volatile.
         */
        private volatile Object delegate;
        /**
         * The statement handle in use by this interceptor, returned by {@link java.sql.ResultSet#getStatement()}. It
         * keeps the timer reset like the handle the application holds, but it is not necessarily that same outermost
         * handle: the statement facade substitutes its own handle from outside this interceptor, and any interceptor
         * configured around this one may too. Nulled along with {@link #delegate} once a close has been forwarded, so
         * that the handle of a closed result set stops pinning the statement layer either; read in the same snapshot as
         * the delegate, so it is volatile for the same reason.
         */
        private volatile Object statement;

        TimerResultSetProxy(Object delegate, Object statement) {
            this.delegate = delegate;
            this.statement = statement;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            // Handle identity on this proxy, like the statement facade
            // does, rather than on the object below it: the application
            // must be able to rely on its own handles
            if (compare(TOSTRING_VAL, method)) {
                return toString();
            }
            if (compare(EQUALS_VAL, method)) {
                if (args[0] == null || !Proxy.isProxyClass(args[0].getClass())) {
                    return Boolean.FALSE;
                }
                return Boolean.valueOf(this.equals(Proxy.getInvocationHandler(args[0])));
            }
            if (compare(HASHCODE_VAL, method)) {
                return Integer.valueOf(this.hashCode());
            }
            try {
                // Read both fields once for this call: the close that nulls
                // them can come from another thread, and the guards and the
                // forward must agree on the one value this call read. The
                // close nulls the delegate before the statement, so reading
                // the statement first keeps the snapshot coherent: a call that
                // read the delegate as live cannot have missed the statement.
                final Object stmt = statement;
                final Object target = delegate;
                final boolean close = compare(CLOSE_VAL, method);
                // Allow close to be called multiple times, from either thread
                if (close && target == null) {
                    return null;
                }
                if (compare(ISCLOSED_VAL, method) && target == null) {
                    return Boolean.TRUE;
                }
                if (target == null) {
                    // Answered from the closed contract, as the facade's own
                    // result set handle does, rather than by invoking on a
                    // null receiver: that is a NullPointerException, where the
                    // closed contract owes the application a SQLException
                    throw new SQLException("ResultSet closed.");
                }
                if (compare(GET_STATEMENT, method)) {
                    return stmt;
                }
                Object result;
                try {
                    // Invoke on the delegate this call read as live: the
                    // snapshot keeps a concurrent close from replacing the
                    // receiver with null between the guards and the call
                    result = method.invoke(target, args);
                } catch (InvocationTargetException x) {
                    Throwable cause = x.getCause();
                    if (cause == null) {
                        throw x;
                    }
                    throw cause;
                }
                // Perform close cleanup: the handle kept beyond this close now
                // holds nothing that can reach the cursor or the statement
                if (close) {
                    delegate = null;
                    statement = null;
                }
                return result;
            } finally {
                // Reading the result set is activity on the connection,
                // even when the read failed. The guards above sit inside
                // this block, so a read refused on a handle whose delegate
                // has been released counts as activity too, like every
                // other attempted operation on this connection.
                resetTimer();
            }
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(this);
        }

        @Override
        public boolean equals(Object obj) {
            return this == obj;
        }

        @Override
        public String toString() {
            return ResetAbandonedTimer.this.getClass().getName() + "[Proxy=" + hashCode() + "; Delegate=" + delegate +
                    ']';
        }
    }
}
