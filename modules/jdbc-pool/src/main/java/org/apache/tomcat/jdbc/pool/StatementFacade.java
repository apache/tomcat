/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tomcat.jdbc.pool;

import java.lang.reflect.Constructor;
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

import org.apache.tomcat.jdbc.pool.interceptor.AbstractCreateStatementInterceptor;
import org.apache.tomcat.jdbc.pool.util.ExceptionUtils;
import org.apache.tomcat.jdbc.pool.util.MessageFactory;

/**
 * Facade that creates statement and result set proxies.
 * <p>
 * The handles implement only the standard JDBC interfaces, so the driver's own classes are never visible through a cast.
 * Every result set handed back - including cursors returned through {@code getObject(...)} - is sealed to the borrow.
 * The {@code Wrapper} methods are delegated to the driver; an unwrapped driver object is not retired when the borrow
 * ends, so statements underneath are kept alive only by {@link org.apache.tomcat.jdbc.pool.interceptor.StatementFinalizer}
 * or by the application closing them.
 * <p>
 * Sealing follows the declared type: a value that is not itself a result set is passed through unchanged, so cursors
 * carried inside an {@link java.sql.Array}, {@link java.sql.Struct} or {@link java.sql.Ref} are not sealed.
 * If the proxy for a statement or result set cannot be built, the driver handle is closed and the call fails with a
 * controlled exception rather than returning the unsealed handle.
 */
public class StatementFacade extends AbstractCreateStatementInterceptor {

    /**
     * Whether the borrow this facade was created for has ended. The pool creates one facade per borrow, so this says
     * when every handle this instance handed out stopped belonging to a connection the application still holds.
     */
    private volatile boolean borrowEnded = false;

    /**
     * Constructs a StatementFacade.
     *
     * @param interceptor the next interceptor in the chain
     */
    protected StatementFacade(JdbcInterceptor interceptor) {
        setUseEquals(interceptor.isUseEquals());
        setNext(interceptor);
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        // The facade above ends the borrow on close and on abort alike, but
        // the base class forwards only close to closeInvoked(), so seal the
        // handles here for abort as well: an aborted borrow retires its
        // statements and result sets exactly as a closed one does, rather
        // than leaving them to answer from the driver on a session this
        // handle can no longer claim. The seal runs ahead of the forward, as
        // in the base class's close path, so no handle can observe the flag
        // as live after the session has left service. Of the dispose calls
        // the facade above lets exactly one reach this chain, and setting the
        // flag is idempotent in any case.
        if (compare(ABORT_VAL, method)) {
            closeInvoked();
        }
        return super.invoke(proxy, method, args);
    }

    /**
     * Marks the borrow as ended, sealing every statement and result set handle this facade handed out so later calls are
     * refused. Called before the connection leaves the application - on close and, through {@link #invoke}, on abort -
     * so a handle cannot outlive the borrow and end up driving the session of the next borrower. This method does not
     * physically close the driver statements; that is done by the driver's connection close, or by
     * {@link org.apache.tomcat.jdbc.pool.interceptor.StatementFinalizer} when configured. A borrow that is abandoned
     * instead never runs this method, but the pool destroys an abandoned session rather than lending it again, so those
     * handles only fail at the driver.
     */
    @Override
    public void closeInvoked() {
        borrowEnded = true;
    }

    /**
     * Wraps the statement produced underneath in a handle that retires with the borrow.
     */
    @Override
    public Object createStatement(Object proxy, Method method, Object[] args, Object statement, long time) {
        try {
            String name = method.getName();
            Constructor<?> constructor = null;
            String sql = null;
            if (compare(CREATE_STATEMENT, name)) {
                constructor = getConstructor(CREATE_STATEMENT_IDX, Statement.class);
            } else if (compare(PREPARE_STATEMENT, name)) {
                constructor = getConstructor(PREPARE_STATEMENT_IDX, PreparedStatement.class);
                sql = (String) args[0];
            } else if (compare(PREPARE_CALL, name)) {
                constructor = getConstructor(PREPARE_CALL_IDX, CallableStatement.class);
                sql = (String) args[0];
            } else {
                // Not a statement creation - pass the object from beneath through
                return statement;
            }
            return constructor.newInstance(new Object[] { new StatementProxy(statement, sql, proxy) });
        } catch (Exception x) {
            // The facade's contract is that no unsealed driver handle leaves
            // it. Reaching this defensive branch means the proxy machinery
            // could not build the statement handle, so close the driver
            // statement rather than hand it to the application raw, and fail
            // the creation. The interceptor above this one closes the statement
            // when this hook throws as well, so the indirect path also leaves
            // no handle behind; closing here covers a direct caller too.
            closeDriverStatement(statement, x);
            IllegalStateException failure = new IllegalStateException(
                    MessageFactory.get("statementFacade.unableToCreateStatementProxy"));
            failure.initCause(x);
            throw failure;
        }
    }

    /**
     * Closes a driver statement that has not been sealed, adding a failure of the close to the failure that caused the
     * statement to be abandoned rather than masking it.
     *
     * @param statement the unsealed driver statement
     * @param cause     the failure being reported
     */
    private static void closeDriverStatement(Object statement, Throwable cause) {
        if (statement instanceof Statement) {
            try {
                ((Statement) statement).close();
            } catch (Throwable closeFailure) {
                ExceptionUtils.handleThrowable(closeFailure);
                if (closeFailure != cause) {
                    cause.addSuppressed(closeFailure);
                }
            }
        }
    }

    /**
     * Wraps a driver result set in a sealed handle. If the proxy machinery cannot build the handle, the driver result
     * set is closed rather than left open and unsealed, and the call fails with a controlled SQLException.
     *
     * @param result the driver result set the call produced
     * @param parent the statement handle the result set is read from
     *
     * @return the sealed result set handle
     *
     * @throws SQLException if the sealed handle cannot be built
     */
    private Object wrapResultSet(Object result, Object parent) throws SQLException {
        try {
            return getConstructor(RESULTSET_IDX, ResultSet.class)
                    .newInstance(new ResultSetProxy(result, parent));
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            try {
                ((ResultSet) result).close();
            } catch (Throwable closeFailure) {
                ExceptionUtils.handleThrowable(closeFailure);
                if (closeFailure != t) {
                    t.addSuppressed(closeFailure);
                }
            }
            SQLException failure = new SQLException(
                    MessageFactory.get("statementFacade.unableToCreateResultSetProxy"));
            failure.initCause(t);
            throw failure;
        }
    }

    /**
     * Proxy for a SQL statement. Refuses calls once the borrow that created it has ended, and seals every result set
     * the statement hands back.
     */
    protected class StatementProxy implements InvocationHandler {
        /**
         * The delegated statement, nulled once a close has been forwarded. A handle can be closed from a thread other
         * than the one invoking on it, so the field is read by more threads than the one that nulls it and, like the
         * borrow-ended flag, must be volatile to reach them at all.
         */
        protected volatile Object delegate;
        /**
         * The SQL query text.
         */
        protected final String query;
        /**
         * The pooled connection the statement was created from, as reported by
         * {@link java.sql.Statement#getConnection()}. It is the handle the application borrowed, so it stops working
         * once that borrow has been returned, while the driver's own connection would have gone on serving whoever
         * borrowed the connection next.
         */
        protected final Object connection;

        /**
         * Constructs a StatementProxy.
         *
         * @param parent     the parent statement
         * @param query      the SQL query text
         * @param connection the pooled connection the statement was created from
         */
        public StatementProxy(Object parent, String query, Object connection) {
            this.delegate = parent;
            this.query = query;
            this.connection = connection;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
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
            // Read the state once: whether the borrow has ended must not
            // change while the call is being answered, and the flag and the
            // delegate are two separate fields, so a call that read the
            // delegate as live can find it nulled by the next read - a
            // close from another thread, interrupting a statement this call
            // is blocking on, nulls it. A close that landed in between is
            // then answered as closed, on the missing delegate as much as
            // on the flag, rather than by invoking on a null object: that
            // is a NullPointerException, and the application is owed the
            // SQLException the closed contract promises. The window this
            // cannot close is the race itself: a call that reads the
            // delegate while it is still live goes to it, an outcome
            // indistinguishable from having simply outrun the close.
            boolean ended = borrowEnded;
            Object target = delegate;
            if (compare(CLOSE_VAL, method)) {
                if (target == null || ended) {
                    return null;
                }
            }
            if (compare(ISCLOSED_VAL, method)) {
                if (target == null || ended) {
                    return Boolean.TRUE;
                }
            }
            if (target == null) {
                throw new SQLException(MessageFactory.get("statementFacade.statementClosed"));
            }
            if (ended) {
                // The delegate is still open, but it is open on a session
                // that belongs to the next borrower now.
                throw new SQLException(MessageFactory.get("statementFacade.statementClosedConnectionReturned"));
            }
            // The connection a statement reports is the pooled handle the
            // application created it from, not the driver's own connection.
            // Forwarding the call instead hands the application a handle on
            // the physical session: it survives the borrow it came from and
            // bypasses every interceptor, so statements made through it
            // escape the close-on-return tracking and any statement cache.
            if (compare(GETCONNECTION_VAL, method)) {
                return connection;
            }

            Object result;
            try {
                // Invoke next, on the delegate this call read as live: the
                // snapshot keeps a concurrent close from replacing the
                // receiver with null between the guard and the call
                result = method.invoke(target, args);

                // Seal every result set the call hands back, whatever the
                // method that returned it was called. Matching the three
                // names the standard API gives them (executeQuery,
                // getResultSet, getGeneratedKeys) misses the result sets
                // declared to come back as an Object: a cursor OUT-parameter
                // reaches the application through
                // CallableStatement.getObject(...). The return type sees them
                // all, and a null result simply is no result set. The Wrapper
                // methods are kept out of this: unwrap() is the documented
                // way to reach the driver's own object, so what it hands back
                // stays the driver's object even when that object implements
                // ResultSet. ResultSetProxy mirrors this rule for the result
                // sets a result set itself hands out.
                if (result instanceof ResultSet && method.getDeclaringClass() != Wrapper.class) {
                    return wrapResultSet(result, proxy);
                }
            } catch (Throwable t) {
                if (t instanceof InvocationTargetException && t.getCause() != null) {
                    throw t.getCause();
                } else {
                    throw t;
                }
            }
            // Drop the driver reference; the null delegate is what answers
            // as closed from here on.
            if (compare(CLOSE_VAL, method)) {
                delegate = null;
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
            // Reads the live delegate rather than a snapshot: a description
            // says what the proxy holds as it is asked.
            StringBuffer buf = new StringBuffer(StatementProxy.class.getName());
            buf.append("[Proxy=");
            buf.append(hashCode());
            buf.append("; Query=");
            buf.append(query);
            buf.append("; Delegate=");
            buf.append(delegate);
            buf.append(']');
            return buf.toString();
        }
    }

    /**
     * Proxy for a ResultSet.
     */
    protected class ResultSetProxy implements InvocationHandler {

        private final Object parent;
        /**
         * The delegated result set, nulled once a close has been forwarded. Read by other threads than the one that
         * nulls it, so volatile, as in the statement proxy.
         */
        private volatile Object delegate;

        /**
         * Constructs a ResultSetProxy.
         *
         * @param delegate the ResultSet to delegate to
         * @param parent   the statement handle the result set was read from, which
         *                     {@link java.sql.ResultSet#getStatement()} answers; a result set a handle hands out as a
         *                     column value is parented to the same statement
         */
        public ResultSetProxy(Object delegate, Object parent) {
            this.delegate = delegate;
            this.parent = parent;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
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
            // Read the state once, both the borrow-ended flag and the
            // delegate, for the statement proxy's reason: a close from
            // another thread nulls the delegate, and a call that read the
            // delegate as live must not find it nulled at the forward,
            // where Method.invoke on a null receiver would answer with a
            // NullPointerException rather than the SQLException the closed
            // contract promises.
            boolean ended = borrowEnded;
            Object target = delegate;
            if (compare(CLOSE_VAL, method)) {
                if (target == null || ended) {
                    return null;
                }
            }
            if (compare(ISCLOSED_VAL, method)) {
                if (target == null || ended) {
                    return Boolean.TRUE;
                }
            }
            if (target == null) {
                throw new SQLException(MessageFactory.get("statementFacade.resultSetClosed"));
            }
            if (ended) {
                // The cursor is still open, but it is open on a session that
                // belongs to the next borrower now.
                throw new SQLException(MessageFactory.get("statementFacade.resultSetClosedConnectionReturned"));
            }

            if (compare(GET_STATEMENT, method)) {
                return parent;
            }

            Object result;
            try {
                // Invoke next, on the delegate this call read as live, as
                // in the statement proxy
                result = method.invoke(target, args);

                // The statement proxy's rule, mirrored one layer down: a
                // cursor-valued column - a nested cursor, or a REF cursor
                // selected as a column - arrives through a getObject(...)
                // overload, which comes back as an Object, and would
                // otherwise reach the application with nothing tying it to
                // this borrow. The new handle is parented to the statement,
                // as the statement proxy parents its result sets, so that
                // getStatement() keeps answering a Statement; the Wrapper
                // carve-out is the statement proxy's, for its reason there.
                if (result instanceof ResultSet && method.getDeclaringClass() != Wrapper.class) {
                    return wrapResultSet(result, parent);
                }
            } catch (Throwable t) {
                if (t instanceof InvocationTargetException && t.getCause() != null) {
                    throw t.getCause();
                } else {
                    throw t;
                }
            }
            // Drop the driver reference, as in the statement proxy.
            if (compare(CLOSE_VAL, method)) {
                delegate = null;
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
            // Reads the live delegate rather than a snapshot, as in the
            // statement proxy.
            return ResultSetProxy.class.getName() + "[Proxy=" + hashCode() + "; Delegate=" + delegate + ']';
        }
    }

}
